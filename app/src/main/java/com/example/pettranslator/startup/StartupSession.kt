package com.example.pettranslator.startup

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Presentation state for one process, independent of the authoritative privacy consent. */
class StartupSession(
    private val nowMs: () -> Long,
    private val durationMs: Long = 2_000L,
    private val audit: (String) -> Unit = {},
) {
    private var firstVisibleMs: Long? = null
    private val mutableFinished = MutableStateFlow(false)
    val finished = mutableFinished.asStateFlow()

    @Synchronized fun onVisibleFrame(): Long {
        if (mutableFinished.value) return 0L
        if (firstVisibleMs == null) {
            firstVisibleMs = nowMs()
            audit("splash_visible uptimeMs=${firstVisibleMs}")
        }
        return (durationMs - (nowMs() - checkNotNull(firstVisibleMs))).coerceAtLeast(0L)
    }
    @Synchronized fun finishIfElapsed(): Boolean {
        if (mutableFinished.value) return true
        val started = firstVisibleMs ?: return false
        val elapsed = nowMs() - started
        if (elapsed < durationMs) return false
        mutableFinished.value = true
        audit("splash_finished durationMs=$elapsed")
        return true
    }
}
