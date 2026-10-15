package com.sapphire.hawe.core.dev

import com.sapphire.hawe.core.net.ApiClient
import com.sapphire.hawe.core.net.ApiException
import com.sapphire.hawe.core.net.InMemoryTokenStore
import com.sapphire.hawe.core.net.NetworkException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class DevFaultsTest {
    @Test fun faultsConsumeOnlyMatchingRequestsAndSkipDevApi() = runBlocking {
        DevMode.changeEnabled(true)
        DevFaults.clear()
        try {
            MockWebServer().use { server ->
                val api = ApiClient(server.url("/").toString(), InMemoryTokenStore(),
                    http = OkHttpClient.Builder().addInterceptor(DevFaults).build())
                DevFaults.set(DevFault.INSIGHT_GENERATING, 2)
                assertEquals("GENERATING", api.get<kotlinx.serialization.json.JsonObject>(
                    "/api/groups/7/insights?month=2026-10")["status"]?.toString()?.trim('"'))
                assertEquals(1, DevFaults.count(DevFault.INSIGHT_GENERATING))
                DevFaults.set(DevFault.FEED_LOCKED, 1)
                try { api.get<kotlinx.serialization.json.JsonObject>("/api/missions/8/submissions")
                    fail("expected 423") } catch (error: ApiException) { assertEquals("FEED_LOCKED", error.code) }
                server.enqueue(MockResponse().setBody("{}"))
                DevFaults.set(DevFault.SERVER_ERROR, 1)
                api.get<kotlinx.serialization.json.JsonObject>("/api/dev/status", auth = false)
                assertEquals(1, DevFaults.count(DevFault.SERVER_ERROR))
                try { api.get<kotlinx.serialization.json.JsonObject>("/api/groups")
                    fail("expected 500") } catch (error: ApiException) { assertEquals("INTERNAL_ERROR", error.code) }
                DevFaults.set(DevFault.NETWORK_DOWN, 1)
                try { api.get<kotlinx.serialization.json.JsonObject>("/api/groups")
                    fail("expected IO error") } catch (_: NetworkException) { }
                assertEquals(0, DevFaults.count(DevFault.NETWORK_DOWN))
                DevMode.changeEnabled(false)
                server.enqueue(MockResponse().setBody("{}"))
                api.get<kotlinx.serialization.json.JsonObject>("/api/groups")
            }
        } finally { DevFaults.clear(); DevMode.changeEnabled(false) }
        Unit
    }
}
