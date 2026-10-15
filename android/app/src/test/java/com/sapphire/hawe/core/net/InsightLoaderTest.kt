package com.sapphire.hawe.core.net

import com.sapphire.hawe.core.model.GroupInsight
import java.time.YearMonth
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InsightLoaderTest {
    private fun insight(stale: Boolean = false) = GroupInsight(YearMonth.of(2026, 10), stale, false,
        null, null, emptyList(), null, emptyList())

    @Test fun pollsWithBoundedDelayUntilReady() = runTest {
        var calls = 0
        val states = mutableListOf<InsightOutcome>()
        val task = async {
            loadInsight({ if (++calls <= 2) InsightResult.Generating(if (calls == 1) 1 else 9)
                else InsightResult.Ready(insight()) }, onProgress = states::add)
        }
        runCurrent(); assertEquals(1, calls)
        advanceTimeBy(1999); runCurrent(); assertEquals(1, calls)
        advanceTimeBy(1); runCurrent(); assertEquals(2, calls)
        advanceTimeBy(6000); task.await()
        assertEquals(3, calls)
        assertEquals(1, states.size)
        assertTrue(states.single() is InsightOutcome.Ready)
    }

    @Test fun stopsAfterEightCalls() = runTest {
        var calls = 0
        val states = mutableListOf<InsightOutcome>()
        loadInsight({ calls++; InsightResult.Generating(2) }, onProgress = states::add)
        assertEquals(8, calls)
        assertEquals(listOf(InsightOutcome.TimedOut), states)
    }

    @Test fun showsStaleImmediatelyThenReplacesOnce() = runTest {
        var calls = 0
        val states = mutableListOf<InsightOutcome>()
        loadInsight({ if (++calls == 1) InsightResult.Ready(insight(true)) else InsightResult.Ready(insight()) },
            onProgress = states::add)
        assertEquals(2, calls)
        assertEquals(InsightOutcome.Ready(insight(true), true), states[0])
        assertEquals(InsightOutcome.Ready(insight()), states[1])
    }

    @Test fun keepsStaleIfRefreshFails() = runTest {
        var calls = 0
        val states = mutableListOf<InsightOutcome>()
        loadInsight({ if (++calls == 1) InsightResult.Ready(insight(true)) else error("offline") },
            onProgress = states::add)
        assertEquals(2, calls)
        assertEquals(InsightOutcome.Ready(insight(true)), states.last())
    }

    @Test fun revokedMembershipDuringRefreshReachesScreen() = runTest {
        var calls = 0
        val failure = runCatching {
            loadInsight({ if (++calls == 1) InsightResult.Ready(insight(true))
                else throw ApiException(403, "NOT_GROUP_MEMBER", "탈퇴한 그룹") }) {}
        }.exceptionOrNull()
        assertEquals("NOT_GROUP_MEMBER", (failure as ApiException).code)
    }

    @Test fun cancellationStopsPolling() = runTest {
        var calls = 0
        val task = async { loadInsight({ calls++; InsightResult.Generating(3) }) {} }
        runCurrent(); assertEquals(1, calls)
        task.cancel()
        advanceTimeBy(10000); runCurrent()
        assertEquals(1, calls)
    }
}
