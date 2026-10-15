package com.sapphire.hawe.core.dev

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

enum class DevFault { INSIGHT_GENERATING, FEED_LOCKED, SERVER_ERROR, NETWORK_DOWN }

object DevFaults : Interceptor {
    private val remaining = mutableMapOf<DevFault, Int>()

    @Synchronized fun set(fault: DevFault, count: Int) { remaining[fault] = count.coerceAtLeast(0) }
    @Synchronized fun count(fault: DevFault): Int = remaining[fault] ?: 0
    @Synchronized fun clear() { remaining.clear() }

    @Synchronized private fun take(path: String, method: String): DevFault? {
        if (!DevMode.enabled || path.startsWith("/api/dev/")) return null
        return DevFault.entries.firstOrNull { fault ->
            (remaining[fault] ?: 0) > 0 && when (fault) {
                DevFault.INSIGHT_GENERATING -> method == "GET" && path.matches(Regex("/api/groups/\\d+/insights.*"))
                DevFault.FEED_LOCKED -> method == "GET" && path.matches(Regex("/api/missions/\\d+/submissions.*"))
                else -> true
            }
        }?.also { remaining[it] = (remaining[it] ?: 1) - 1 }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val fault = take(request.url.encodedPath, request.method) ?: return chain.proceed(request)
        if (fault == DevFault.NETWORK_DOWN) throw IOException("Developer network outage")
        val (status, body) = when (fault) {
            DevFault.INSIGHT_GENERATING -> {
                val group = request.url.pathSegments.getOrNull(2)?.toLongOrNull() ?: 0
                val month = request.url.queryParameter("month") ?: ""
                202 to """{"groupId":$group,"month":"$month","status":"GENERATING","retryAfterSeconds":2}"""
            }
            DevFault.FEED_LOCKED -> 423 to """{"code":"FEED_LOCKED","message":"개발자 오류 주입"}"""
            else -> 500 to """{"code":"INTERNAL_ERROR","message":"개발자 오류 주입"}"""
        }
        return Response.Builder().request(request).protocol(okhttp3.Protocol.HTTP_1_1).code(status)
            .message("Developer fault").body(body.toResponseBody("application/json".toMediaType())).build()
    }
}
