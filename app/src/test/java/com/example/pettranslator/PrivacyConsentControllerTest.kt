package com.example.pettranslator

import com.example.pettranslator.privacy.CURRENT_PRIVACY_POLICY_VERSION
import com.example.pettranslator.privacy.ConsentDecision
import com.example.pettranslator.privacy.ConsentPhase
import com.example.pettranslator.privacy.ConsentRecord
import com.example.pettranslator.privacy.ConsentStore
import com.example.pettranslator.privacy.PrivacyConsentController
import com.example.pettranslator.privacy.PrivacyGate
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PrivacyConsentControllerTest {
    @Before fun blockGateBeforeTest() = PrivacyGate.block()
    @After fun blockGateAfterTest() = PrivacyGate.block()

    @Test fun unknownAndDeclinedConsentCannotInitializeOrProcessSdk() = runTest {
        val store = FakeStore()
        val controller = controller(store)
        val sdk = FakeControlledSdk()

        assertSdkBlocked(sdk)
        controller.initialize()
        assertEquals(ConsentPhase.Required, controller.state.value.phase)
        assertSdkBlocked(sdk)

        controller.decline()
        assertEquals(ConsentDecision.Denied, store.record.decision)
        assertEquals(ConsentPhase.Denied, controller.state.value.phase)
        assertEquals(1, store.writeAttempts)
        assertSdkBlocked(sdk)
    }

    @Test fun acceptedCurrentPolicyRestoresAfterControllerRestart() = runTest {
        val store = FakeStore()
        val first = controller(store, time = 1_234L)
        first.initialize()
        first.agree()

        assertEquals(ConsentPhase.Granted, first.state.value.phase)
        assertTrue(PrivacyGate.isGranted())
        assertEquals(ConsentDecision.Accepted, store.record.decision)
        assertEquals(CURRENT_PRIVACY_POLICY_VERSION, store.record.policyVersion)
        assertEquals(1_234L, store.record.acceptedAtMs ?: -1L)
        val sdk = FakeControlledSdk()
        sdk.initialize()
        sdk.process()
        assertEquals(1, sdk.initializations)
        assertEquals(1, sdk.processed)

        PrivacyGate.block()
        val restarted = controller(store)
        restarted.initialize()

        assertEquals(ConsentPhase.Granted, restarted.state.value.phase)
        assertTrue(PrivacyGate.isGranted())
        assertEquals(1, store.writeAttempts)
        sdk.process()
        assertEquals(2, sdk.processed)
    }

    @Test fun previousPolicyVersionRequiresFreshConsent() = runTest {
        val store = FakeStore(acceptedRecord().copy(policyVersion = "previous-policy"))
        PrivacyGate.allow()
        val controller = controller(store)

        controller.initialize()

        assertEquals(ConsentPhase.Required, controller.state.value.phase)
        assertEquals(0, store.writeAttempts)
        assertSdkBlocked(FakeControlledSdk())
    }

    @Test fun acceptanceWithoutPositiveTimestampDoesNotGrantConsent() = runTest {
        for (timestamp in listOf<Long?>(null, 0L, -1L)) {
            PrivacyGate.block()
            val store = FakeStore(acceptedRecord().copy(acceptedAtMs = timestamp))
            val controller = controller(store)

            controller.initialize()

            assertEquals("timestamp=$timestamp", ConsentPhase.Required, controller.state.value.phase)
            assertFalse("timestamp=$timestamp", PrivacyGate.isGranted())
            assertEquals(0, store.writeAttempts)
        }
    }

    @Test fun initializationReadFailureRequiresConsentAndBlocksGate() = runTest {
        val store = FakeStore().apply { readFailure = IOException("read failed") }
        PrivacyGate.allow()
        val controller = controller(store)

        controller.initialize()

        assertEquals(ConsentPhase.Required, controller.state.value.phase)
        assertNotNull(controller.state.value.error)
        assertEquals(0, store.writeAttempts)
        assertSdkBlocked(FakeControlledSdk())
    }

    @Test fun agreementWriteFailureNeverUnlocksOrExits() = runTest {
        val store = FakeStore().apply { writeFailure = IOException("write failed") }
        var exits = 0
        val controller = controller(store, exit = { exits++ })
        controller.initialize()

        controller.agree()

        assertNotEquals(ConsentPhase.Granted, controller.state.value.phase)
        assertNotNull(controller.state.value.error)
        assertEquals(ConsentDecision.Unknown, store.record.decision)
        assertEquals(1, store.writeAttempts)
        assertEquals(0, exits)
        assertSdkBlocked(FakeControlledSdk())
    }

    @Test fun declineWriteFailureKeepsGateBlockedWithoutExiting() = runTest {
        val store = FakeStore().apply { writeFailure = IOException("write failed") }
        var exits = 0
        val controller = controller(store, exit = { exits++ })
        controller.initialize()

        controller.decline()

        assertNotEquals(ConsentPhase.Granted, controller.state.value.phase)
        assertNotNull(controller.state.value.error)
        assertEquals(ConsentDecision.Unknown, store.record.decision)
        assertEquals(1, store.writeAttempts)
        assertEquals(0, exits)
        assertSdkBlocked(FakeControlledSdk())
    }

    @Test fun revokeCancelsTrackedJobThenPersistsStopsAndExits() = runTest {
        val events = mutableListOf<String>()
        val store = FakeStore(acceptedRecord())
        val trackedJob = Job()
        lateinit var controller: PrivacyConsentController
        store.beforeWrite = { record ->
            assertFalse(PrivacyGate.isGranted())
            assertTrue(trackedJob.isCancelled)
            assertEquals(ConsentDecision.Revoked, record.decision)
            events += "persist"
        }
        controller = controller(store, time = 2_345L, stop = {
            assertFalse(PrivacyGate.isGranted())
            assertEquals(ConsentDecision.Revoked, store.record.decision)
            events += "stop"
        }, exit = {
            assertFalse(PrivacyGate.isGranted())
            assertEquals(ConsentPhase.Revoked, controller.state.value.phase)
            events += "exit"
        })
        controller.initialize()
        val registration = PrivacyGate.trackCurrentJob(trackedJob)
        try {
            assertTrue(trackedJob.isActive)

            controller.revoke()

            assertEquals(listOf("persist", "stop", "exit"), events)
            assertEquals(2_345L, store.record.revokedAtMs ?: -1L)
            assertEquals(ConsentPhase.Revoked, controller.state.value.phase)
            assertTrue(trackedJob.isCancelled)
            assertSdkBlocked(FakeControlledSdk())
        } finally {
            registration.close()
            trackedJob.cancel()
        }
    }

    @Test fun revokeWriteFailureRemainsRevokingAndRetryCompletes() = runTest {
        val store = FakeStore(acceptedRecord()).apply { writeFailure = IOException("write failed") }
        var stops = 0
        var exits = 0
        val controller = controller(store, stop = { stops++ }, exit = { exits++ })
        controller.initialize()

        controller.revoke()

        assertEquals(ConsentPhase.Revoking, controller.state.value.phase)
        assertNotNull(controller.state.value.error)
        assertEquals(ConsentDecision.Accepted, store.record.decision)
        assertEquals(0, stops)
        assertEquals(0, exits)
        assertSdkBlocked(FakeControlledSdk())

        store.writeFailure = null
        controller.revoke()

        assertEquals(ConsentPhase.Revoked, controller.state.value.phase)
        assertEquals(ConsentDecision.Revoked, store.record.decision)
        assertEquals(2, store.writeAttempts)
        assertEquals(1, stops)
        assertEquals(1, exits)
        assertFalse(PrivacyGate.isGranted())
    }

    @Test fun stopFailureRemainsRevokingWithoutExitAndCanBeRetried() = runTest {
        val store = FakeStore(acceptedRecord())
        var failStop = true
        var stops = 0
        var exits = 0
        val controller = controller(store, stop = {
            stops++
            if (failStop) throw IOException("stop failed")
        }, exit = { exits++ })
        controller.initialize()

        controller.revoke()

        assertEquals(ConsentPhase.Revoking, controller.state.value.phase)
        assertNotNull(controller.state.value.error)
        assertEquals(ConsentDecision.Revoked, store.record.decision)
        assertEquals(1, stops)
        assertEquals(0, exits)
        assertSdkBlocked(FakeControlledSdk())

        failStop = false
        controller.revoke()

        assertEquals(ConsentPhase.Revoked, controller.state.value.phase)
        assertEquals(2, stops)
        assertEquals(1, exits)
        assertFalse(PrivacyGate.isGranted())
    }

    @Test fun concurrentRevokeConfirmationsWriteStopAndExitOnlyOnce() = runTest {
        val writeEntered = CompletableDeferred<Unit>()
        val releaseWrite = CompletableDeferred<Unit>()
        val store = FakeStore(acceptedRecord()).apply {
            beforeWrite = {
                writeEntered.complete(Unit)
                releaseWrite.await()
            }
        }
        var stops = 0
        var exits = 0
        val controller = controller(store, stop = { stops++ }, exit = { exits++ })
        controller.initialize()
        val first = launch { controller.revoke() }
        writeEntered.await()
        val duplicate = launch(start = CoroutineStart.UNDISPATCHED) { controller.revoke() }
        try {
            assertTrue("A duplicate confirmation must return while the first operation is busy", duplicate.isCompleted)
            assertEquals(1, store.writeAttempts)
            assertFalse(PrivacyGate.isGranted())
            assertEquals(0, stops)
            assertEquals(0, exits)
        } finally {
            releaseWrite.complete(Unit)
        }
        first.join()
        duplicate.join()
        controller.revoke()

        assertEquals(ConsentPhase.Revoked, controller.state.value.phase)
        assertEquals(1, store.writeAttempts)
        assertEquals(1, stops)
        assertEquals(1, exits)
    }

    @Test fun agreementIsIgnoredWhileLoadingAndGrantedButAllowedAfterDenial() = runTest {
        val store = FakeStore()
        val controller = controller(store)

        controller.agree()
        assertEquals(ConsentPhase.Loading, controller.state.value.phase)
        assertEquals(0, store.writeAttempts)
        assertFalse(PrivacyGate.isGranted())

        controller.initialize()
        controller.decline()
        assertEquals(ConsentPhase.Denied, controller.state.value.phase)
        controller.agree()
        assertEquals(ConsentPhase.Granted, controller.state.value.phase)
        assertTrue(PrivacyGate.isGranted())
        assertEquals(2, store.writeAttempts)

        controller.agree()
        assertEquals(2, store.writeAttempts)
    }

    @Test fun consentChangesLeaveBusinessSentinelsUntouched() = runTest {
        val store = FakeStore()
        val originalBusiness = store.businessSnapshot()
        var stopped = false
        val controller = controller(store, stop = { stopped = true })

        controller.initialize()
        assertEquals(originalBusiness, store.businessSnapshot())
        controller.decline()
        assertEquals(originalBusiness, store.businessSnapshot())
        controller.agree()
        assertEquals(originalBusiness, store.businessSnapshot())
        controller.revoke()

        assertTrue(stopped)
        assertEquals(ConsentPhase.Revoked, controller.state.value.phase)
        assertEquals(originalBusiness, store.businessSnapshot())
    }

    private fun controller(
        store: ConsentStore,
        time: Long = 1_000L,
        stop: suspend () -> Unit = {},
        exit: () -> Unit = {},
    ) = PrivacyConsentController(store, stopControlledWork = stop, exitApp = exit, now = { time })

    private fun acceptedRecord() = ConsentRecord(
        decision = ConsentDecision.Accepted,
        policyVersion = CURRENT_PRIVACY_POLICY_VERSION,
        acceptedAtMs = 100L,
    )

    private fun assertSdkBlocked(sdk: FakeControlledSdk) {
        assertFalse(PrivacyGate.isGranted())
        assertNotNull("SDK initialization must not enter its controlled block",
            runCatching { sdk.initialize() }.exceptionOrNull())
        assertNotNull("SDK processing must not enter its controlled block",
            runCatching { sdk.process() }.exceptionOrNull())
        assertEquals(0, sdk.initializations)
        assertEquals(0, sdk.processed)
    }

    private class FakeControlledSdk {
        var initializations = 0
            private set
        var processed = 0
            private set

        fun initialize() {
            PrivacyGate.withConsent { initializations++ }
        }

        fun process() {
            PrivacyGate.withConsent { processed++ }
        }
    }

    /** In-memory records only; sentinel paths are strings, never actual files or stores. */
    private class FakeStore(initial: ConsentRecord = ConsentRecord()) : ConsentStore {
        private val records = linkedMapOf<String, Any>(
            "privacy-consent" to initial,
            "pet_type" to "Dog",
            "recording_path" to "recordings/existing.pcm",
            "cache_marker" to "business-cache-kept",
        )
        val record: ConsentRecord get() = records.getValue("privacy-consent") as ConsentRecord
        var writeAttempts = 0
            private set
        var readFailure: IOException? = null
        var writeFailure: IOException? = null
        var beforeWrite: suspend (ConsentRecord) -> Unit = {}

        override suspend fun read(): ConsentRecord {
            readFailure?.let { throw it }
            return record
        }

        override suspend fun write(record: ConsentRecord) {
            writeAttempts++
            writeFailure?.let { throw it }
            beforeWrite(record)
            records["privacy-consent"] = record
        }

        fun businessSnapshot(): Map<String, Any> = records.filterKeys { it != "privacy-consent" }
    }
}
