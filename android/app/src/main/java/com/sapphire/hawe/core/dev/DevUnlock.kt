package com.sapphire.hawe.core.dev

data class TapResult(val count: Int, val unlocked: Boolean, val message: String?)

class DevUnlock {
    private var lastTap = Long.MIN_VALUE
    private var count = 0

    fun registerTap(nowMs: Long, alreadyEnabled: Boolean): TapResult {
        if (alreadyEnabled) return TapResult(0, false, "이미 개발자 모드예요")
        count = if (lastTap != Long.MIN_VALUE && nowMs >= lastTap && nowMs - lastTap <= 3000) count + 1 else 1
        lastTap = nowMs
        if (count >= 10) {
            count = 0
            return TapResult(10, true, "개발자 모드가 켜졌어요")
        }
        return TapResult(count, false, if (count >= 5) "개발자 모드까지 ${10 - count}번" else null)
    }
}
