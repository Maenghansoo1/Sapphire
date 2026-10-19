package com.sapphire.hawe.core.net

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit.SECONDS

class ApiClient(
    private val baseUrl: String, private val tokens: TokenStore,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, SECONDS).readTimeout(20, SECONDS).writeTimeout(20, SECONDS).build(),
    val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false },
    private val onSessionExpired: () -> Unit = {},
) {
    private val refreshMutex = Mutex()
    private val mediaType = "application/json; charset=utf-8".toMediaType()
    private val uploadHttp = http.newBuilder().readTimeout(60, SECONDS).writeTimeout(60, SECONDS).build()

    sealed interface MultipartPart {
        data class Text(val name: String, val value: String) : MultipartPart
        data class Bytes(val name: String, val filename: String, val mime: String, val value: ByteArray) : MultipartPart
        data class FilePart(val name: String, val filename: String, val mime: String, val value: File) : MultipartPart
    }

    fun absoluteUrl(path: String): String = if (path.startsWith("http://") || path.startsWith("https://")) path
        else baseUrl.trimEnd('/') + "/" + path.trimStart('/')

    suspend inline fun <reified T> postMultipart(path: String, parts: List<MultipartPart>): T =
        decode(executeMultipart(path, parts))

    suspend inline fun <reified T> putMultipart(path: String, parts: List<MultipartPart>): T =
        decode(executeMultipart(path, parts, "PUT"))

    @PublishedApi internal suspend fun executeMultipart(path: String, parts: List<MultipartPart>,
        method: String = "POST"): Pair<Int, String> =
        executeRequest(method, path, true, true) {
            MultipartBody.Builder().setType(MultipartBody.FORM).apply {
                parts.forEach { part -> when (part) {
                    is MultipartPart.Text -> addFormDataPart(part.name, part.value)
                    is MultipartPart.Bytes -> addFormDataPart(part.name, part.filename,
                        part.value.toRequestBody(part.mime.toMediaType()))
                    is MultipartPart.FilePart -> addFormDataPart(part.name, part.filename,
                        part.value.asRequestBody(part.mime.toMediaType()))
                } }
            }.build()
        }

    suspend inline fun <reified T> get(path: String, auth: Boolean = true): T =
        decode(execute("GET", path, null, auth))

    suspend inline fun <reified B, reified T> post(path: String, body: B, auth: Boolean = true): T =
        decode(execute("POST", path, json.encodeToString(body), auth))

    suspend inline fun <reified B, reified T> put(path: String, body: B, auth: Boolean = true): T =
        decode(execute("PUT", path, json.encodeToString(body), auth))

    suspend inline fun <reified B, reified T> patch(path: String, body: B, auth: Boolean = true): T =
        decode(execute("PATCH", path, json.encodeToString(body), auth))

    suspend fun delete(path: String, auth: Boolean = true) {
        execute("DELETE", path, null, auth)
    }

    suspend inline fun <reified T> deleteReturning(path: String, auth: Boolean = true): T =
        decode(execute("DELETE", path, null, auth))

    @PublishedApi internal inline fun <reified T> decode(result: Pair<Int, String>): T = try {
        json.decodeFromString<T>(result.second)
    } catch (e: SerializationException) {
        throw ApiException(result.first, "INVALID_RESPONSE", "서버 응답을 읽을 수 없습니다")
    } catch (e: IllegalArgumentException) {
        throw ApiException(result.first, "INVALID_RESPONSE", "서버 응답을 읽을 수 없습니다")
    }

    @PublishedApi internal suspend fun execute(method: String, path: String, bodyJson: String?, auth: Boolean): Pair<Int, String> =
        executeRequest(method, path, auth, false) { bodyJson?.toRequestBody(mediaType) }

    @PublishedApi internal suspend fun executeRequest(method: String, path: String, auth: Boolean,
        upload: Boolean, body: () -> RequestBody?): Pair<Int, String> {
        val usedAccess = if (auth) tokens.accessToken else null
        val first = send(method, path, body, usedAccess, upload)
        if (first.first in 200..299) return first
        val error = error(first)
        if (auth && first.first == 401 && error.code == "TOKEN_EXPIRED") {
            val access = refreshOnce(usedAccess)
            val retry = send(method, path, body, access, upload)
            if (retry.first in 200..299) return retry
            if (retry.first == 401) expireSession(access)
            throw error(retry)
        }
        if (auth && first.first == 401 && error.code == "INVALID_TOKEN") expireSession(usedAccess)
        throw error
    }

    private suspend fun refreshOnce(usedAccess: String?): String = refreshMutex.withLock {
        val current = tokens.accessToken
        if (current != null && current != usedAccess) return@withLock current
        val refresh = tokens.refreshToken ?: run {
            expireSession(usedAccess)
            throw ApiException(401, "INVALID_TOKEN", "다시 로그인해 주세요")
        }
        val response = send("POST", "/api/auth/refresh",
            { json.encodeToString(RefreshRequestDto(refresh)).toRequestBody(mediaType) }, null, false)
        if (response.first !in 200..299) {
            val error = error(response)
            if (response.first == 401) expireSession(usedAccess)
            throw error
        }
        val updated = decode<TokenResponseDto>(response)
        tokens.save(updated.accessToken, updated.refreshToken)
        updated.accessToken
    }

    private fun expireSession(expectedAccess: String?) {
        val hadSession = synchronized(tokens) {
            if (tokens.accessToken != expectedAccess) return@synchronized false
            val present = tokens.accessToken != null || tokens.refreshToken != null
            if (present) tokens.clear()
            present
        }
        if (hadSession) onSessionExpired()
    }

    private fun error(response: Pair<Int, String>): ApiException {
        val body = runCatching { json.decodeFromString<ErrorBody>(response.second) }.getOrNull()
        return ApiException(response.first, body?.code ?: "HTTP_${response.first}",
            body?.message ?: "요청을 처리할 수 없습니다")
    }

    private suspend fun send(method: String, path: String, body: () -> RequestBody?, access: String?,
        upload: Boolean): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(absoluteUrl(path))
                .apply { if (!upload) header("Content-Type", "application/json; charset=utf-8") }
                .apply { if (access != null) header("Authorization", "Bearer $access") }
                .method(method, body()).build()
            val call = (if (upload) uploadHttp else http).newCall(request)
            val cancellation = currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
                if (cause is CancellationException) call.cancel()
            }
            try {
                call.execute().use { it.code to (it.body?.string() ?: "") }
            } catch (e: IOException) {
                currentCoroutineContext().ensureActive()
                throw NetworkException(e)
            } finally {
                cancellation?.dispose()
            }
        }
}
