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
import okhttp3.RequestBody.Companion.toRequestBody
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

    suspend inline fun <reified T> get(path: String, auth: Boolean = true): T =
        decode(execute("GET", path, null, auth))

    suspend inline fun <reified B, reified T> post(path: String, body: B, auth: Boolean = true): T =
        decode(execute("POST", path, json.encodeToString(body), auth))

    suspend inline fun <reified B, reified T> patch(path: String, body: B, auth: Boolean = true): T =
        decode(execute("PATCH", path, json.encodeToString(body), auth))

    suspend fun delete(path: String, auth: Boolean = true) {
        execute("DELETE", path, null, auth)
    }

    @PublishedApi internal inline fun <reified T> decode(result: Pair<Int, String>): T = try {
        json.decodeFromString<T>(result.second)
    } catch (e: SerializationException) {
        throw ApiException(result.first, "INVALID_RESPONSE", "서버 응답을 읽을 수 없습니다")
    } catch (e: IllegalArgumentException) {
        throw ApiException(result.first, "INVALID_RESPONSE", "서버 응답을 읽을 수 없습니다")
    }

    @PublishedApi internal suspend fun execute(method: String, path: String, bodyJson: String?, auth: Boolean): Pair<Int, String> {
        val usedAccess = if (auth) tokens.accessToken else null
        val first = send(method, path, bodyJson, usedAccess)
        if (first.first in 200..299) return first
        val error = error(first)
        if (auth && first.first == 401 && error.code == "TOKEN_EXPIRED") {
            val access = refreshOnce(usedAccess)
            val retry = send(method, path, bodyJson, access)
            if (retry.first in 200..299) return retry
            if (retry.first == 401) expireSession()
            throw error(retry)
        }
        if (auth && first.first == 401 && error.code == "INVALID_TOKEN") expireSession()
        throw error
    }

    private suspend fun refreshOnce(usedAccess: String?): String = refreshMutex.withLock {
        val current = tokens.accessToken
        if (current != null && current != usedAccess) return@withLock current
        val refresh = tokens.refreshToken ?: run {
            expireSession()
            throw ApiException(401, "INVALID_TOKEN", "다시 로그인해 주세요")
        }
        val response = send("POST", "/api/auth/refresh", json.encodeToString(RefreshRequestDto(refresh)), null)
        if (response.first !in 200..299) {
            val error = error(response)
            if (response.first == 401) expireSession()
            throw error
        }
        val updated = decode<TokenResponseDto>(response)
        tokens.save(updated.accessToken, updated.refreshToken)
        updated.accessToken
    }

    private fun expireSession() {
        val hadSession = synchronized(tokens) {
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

    private suspend fun send(method: String, path: String, bodyJson: String?, access: String?): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(baseUrl.trimEnd('/') + path)
                .header("Content-Type", "application/json; charset=utf-8")
                .apply { if (access != null) header("Authorization", "Bearer $access") }
                .method(method, bodyJson?.toRequestBody(mediaType)).build()
            val call = http.newCall(request)
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
