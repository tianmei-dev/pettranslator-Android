package com.example.pettranslator

import com.example.pettranslator.startup.StartupSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupSessionTest {
    @Test fun waitingForAuthorizationDoesNotConsumeVisibleStartupTime() {
        val clock = FakeClock()
        val session = StartupSession(nowMs = { clock.nowMs })

        clock.advanceBy(30_000L)
        assertFalse(session.finishIfElapsed())
        assertFalse(session.finished.value)

        // The caller reports its first visible frame only after authorization completes.
        assertEquals(2_000L, session.onVisibleFrame())
        assertFalse(session.finishIfElapsed())
        assertFalse(session.finished.value)
        clock.advanceBy(1_000L)
        assertEquals(1_000L, session.onVisibleFrame())
    }

    @Test fun completesAtTwoSecondBoundaryAndAuditsCompletionOnlyOnce() {
        val clock = FakeClock()
        val audit = mutableListOf<String>()
        val session = StartupSession(nowMs = { clock.nowMs }, audit = { audit += it })
        assertEquals(2_000L, session.onVisibleFrame())
        val auditCountBeforeCompletion = audit.size

        clock.advanceBy(1_999L)
        assertEquals(1L, session.onVisibleFrame())
        assertFalse(session.finishIfElapsed())
        assertFalse(session.finished.value)
        assertEquals(auditCountBeforeCompletion, audit.size)

        clock.advanceBy(1L)
        assertTrue(session.finishIfElapsed())
        assertTrue(session.finished.value)
        assertEquals(auditCountBeforeCompletion + 1, audit.size)

        assertTrue(session.finishIfElapsed())
        assertEquals(0L, session.onVisibleFrame())
        assertEquals(auditCountBeforeCompletion + 1, audit.size)
    }

    @Test fun recreatedActivityReusesSessionWithoutRestartingDeadline() {
        val clock = FakeClock()
        val retainedSession = StartupSession(nowMs = { clock.nowMs })
        assertEquals(2_000L, retainedSession.onVisibleFrame())
        clock.advanceBy(650L)

        // A replacement Activity obtains the same process-owned session.
        val recreatedActivitySession = retainedSession
        assertEquals(1_350L, recreatedActivitySession.onVisibleFrame())
        clock.advanceBy(1_349L)
        assertFalse(recreatedActivitySession.finishIfElapsed())
        assertEquals(1L, recreatedActivitySession.onVisibleFrame())

        clock.advanceBy(1L)
        assertTrue(recreatedActivitySession.finishIfElapsed())
        assertTrue(retainedSession.finished.value)
    }

    @Test fun completedSessionDoesNotWaitAgainOnWarmResume() {
        val clock = FakeClock()
        val audit = mutableListOf<String>()
        val session = StartupSession(nowMs = { clock.nowMs }, audit = { audit += it })
        session.onVisibleFrame()
        clock.advanceBy(2_000L)
        assertTrue(session.finishIfElapsed())
        val auditCountAfterCompletion = audit.size

        clock.advanceBy(60_000L)
        assertEquals(0L, session.onVisibleFrame())
        assertTrue(session.finished.value)
        assertTrue(session.finishIfElapsed())
        assertEquals(auditCountAfterCompletion, audit.size)
    }

    @Test fun newProcessSessionRequiresItsOwnTwoVisibleSeconds() {
        val clock = FakeClock()
        val previousProcessSession = StartupSession(nowMs = { clock.nowMs })
        previousProcessSession.onVisibleFrame()
        clock.advanceBy(2_000L)
        assertTrue(previousProcessSession.finishIfElapsed())

        clock.advanceBy(60_000L)
        val newProcessSession = StartupSession(nowMs = { clock.nowMs })
        assertFalse(newProcessSession.finished.value)
        assertFalse(newProcessSession.finishIfElapsed())
        assertEquals(2_000L, newProcessSession.onVisibleFrame())

        clock.advanceBy(1_999L)
        assertFalse(newProcessSession.finishIfElapsed())
        assertFalse(newProcessSession.finished.value)
        clock.advanceBy(1L)
        assertTrue(newProcessSession.finishIfElapsed())
        assertTrue(newProcessSession.finished.value)
        assertTrue(previousProcessSession.finished.value)
    }

    private class FakeClock(var nowMs: Long = 0L) {
        fun advanceBy(durationMs: Long) {
            nowMs += durationMs
        }
    }
}
