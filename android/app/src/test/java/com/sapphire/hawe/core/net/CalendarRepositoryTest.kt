package com.sapphire.hawe.core.net

import com.sapphire.hawe.core.model.MissionType
import com.sapphire.hawe.core.model.Stamp
import java.time.Instant
import java.time.YearMonth
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class CalendarRepositoryTest {
    @Test fun mapsCalendarDaysAndStamps() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"month":"2026-10","days":[
                {"date":"2026-10-01","missions":[{"missionId":101,"media":"DRAW","content":"기분을 날씨로",
                  "submittedCount":4,"memberCount":5,"stamp":"GOOD"}]},
                {"date":"2026-10-02","missions":[{"missionId":102,"media":"PHOTO","content":"오늘의 하늘",
                  "submittedCount":3,"memberCount":5,"stamp":"PENALTY"}]},
                {"date":"2026-10-03","missions":[{"missionId":103,"media":"TEXT","content":"삼행시",
                  "submittedCount":1,"memberCount":5,"stamp":"LATE"}]},
                {"date":"2026-10-12","missions":[{"missionId":104,"media":"PHOTO","content":"오늘 한마디",
                  "submittedCount":0,"memberCount":5,"stamp":null}]}]}"""))
            val records = CalendarRepository(ApiClient(server.url("/").toString(), InMemoryTokenStore()))
                .month(7L, YearMonth.of(2026, 10))

            assertEquals("/api/groups/7/calendar?month=2026-10", server.takeRequest().path)
            assertEquals(listOf(101L, 102L, 103L, 104L), records.map { it.missionID })
            val good = records[0]
            // 서울 10월 1일 0시 = UTC 9월 30일 15시
            assertEquals(Instant.parse("2026-09-30T15:00:00Z"), good.date)
            assertEquals(MissionType.DRAW, good.type)
            assertEquals("기분을 날씨로", good.topic)
            assertEquals(4, good.doneCount)
            assertEquals(5, good.memberCount)
            assertEquals(Stamp.GOOD, good.stamp)
            assertTrue(good.didSubmit)
            assertEquals(Stamp.PENALTY, records[1].stamp)
            assertFalse(records[1].didSubmit)
            assertEquals(MissionType.WRITE, records[2].type)
            assertTrue(records[2].didSubmit)
            assertNull(records[3].stamp)
            assertFalse(records[3].didSubmit)
        }
    }

    @Test fun skipsUnknownMediaAndStamp() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"month":"2026-10","days":[
                {"date":"2026-10-01","missions":[
                  {"missionId":101,"media":"SLIDER","content":"슬라이더","submittedCount":1,"memberCount":2,"stamp":"GOOD"},
                  {"missionId":103,"media":"VOICE","content":"예전 음성 미션","submittedCount":1,"memberCount":2,"stamp":"GOOD"},
                  {"missionId":104,"media":"DICE","content":"모르는 매체","submittedCount":1,"memberCount":2,"stamp":"GOOD"},
                  {"missionId":102,"media":"PHOTO","content":"사진","submittedCount":1,"memberCount":2,"stamp":"NEW_STAMP"}]}]}"""))
            val records = CalendarRepository(ApiClient(server.url("/").toString(), InMemoryTokenStore()))
                .month(7L, YearMonth.of(2026, 10))

            // 없앤 슬라이더·예전 음성·모르는 매체는 건너뛴다
            assertEquals(listOf(102L), records.map { it.missionID })
            assertNull(records[0].stamp)
        }
    }

    @Test fun mapsStatsByMedia() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"completedMissions":42,"streakDays":5,"groupCount":3,
                "byMedia":{"DRAW":10,"PHOTO":15,"TEXT":9,"VOICE":8,"SLIDER":1}}"""))  // 예전 음성·슬라이더 기록은 건너뛴다
            val stats = CalendarRepository(ApiClient(server.url("/").toString(), InMemoryTokenStore())).stats()

            assertEquals("/api/me/stats", server.takeRequest().path)
            assertEquals(42, stats.completedMissions)
            assertEquals(5, stats.streakDays)
            assertEquals(3, stats.groupCount)
            assertEquals(mapOf(MissionType.DRAW to 10, MissionType.PHOTO to 15, MissionType.WRITE to 9), stats.byType)
        }
    }

    @Test fun propagatesServerErrors() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(403)
                .setBody("""{"code":"NOT_GROUP_MEMBER","message":"그룹 멤버가 아닙니다"}"""))
            val error = runCatching {
                CalendarRepository(ApiClient(server.url("/").toString(), InMemoryTokenStore()))
                    .month(7L, YearMonth.of(2026, 10))
            }.exceptionOrNull()

            assertTrue(error is ApiException)
            assertEquals("NOT_GROUP_MEMBER", (error as ApiException).code)
        }
    }
}
