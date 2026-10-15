package com.sapphire.hawe.core.net

import com.sapphire.hawe.core.model.GroupInsight
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

sealed interface InsightOutcome {
    data class Ready(val insight: GroupInsight, val updating: Boolean = false) : InsightOutcome
    data object TimedOut : InsightOutcome
}

suspend fun loadInsight(load: suspend () -> InsightResult,
    delayFn: suspend (Long) -> Unit = ::delay, onProgress: (InsightOutcome) -> Unit) {
    repeat(8) { attempt ->
        when (val result = load()) {
            is InsightResult.Generating -> {
                if (attempt == 7) { onProgress(InsightOutcome.TimedOut); return }
                delayFn(result.retryAfterSeconds.coerceIn(2, 6) * 1000L)
            }
            is InsightResult.Ready -> {
                val first = result.insight
                onProgress(InsightOutcome.Ready(first, updating = first.stale))
                if (first.stale) {
                    try {
                        val refreshed = load()
                        onProgress(InsightOutcome.Ready(
                            if (refreshed is InsightResult.Ready && !refreshed.insight.stale)
                                refreshed.insight else first))
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        if (e is ApiException && (e.code == "NOT_GROUP_MEMBER" || e.code == "GROUP_NOT_FOUND")) throw e
                        onProgress(InsightOutcome.Ready(first))
                    }
                }
                return
            }
        }
    }
}
