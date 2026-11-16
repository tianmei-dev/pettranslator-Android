package com.example.pettranslator.privacy

import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val CURRENT_PRIVACY_POLICY_VERSION = "2026-11-16-draft-1"

enum class ConsentDecision { Unknown, Accepted, Denied, Revoked }
data class ConsentRecord(
    val decision: ConsentDecision = ConsentDecision.Unknown,
    val policyVersion: String? = null,
    val acceptedAtMs: Long? = null,
    val revokedAtMs: Long? = null,
)
interface ConsentStore {
    suspend fun read(): ConsentRecord
    suspend fun write(record: ConsentRecord)
}
enum class ConsentPhase { Loading, Required, Granted, Denied, Revoking, Revoked }
data class ConsentUiState(val phase: ConsentPhase = ConsentPhase.Loading,
    val busy: Boolean = false, val error: String? = null)

/** Default-deny process gate. Entry points and actual work both use this same gate. */
object PrivacyGate {
    private val granted = AtomicBoolean(false)
    private val actionLock = Any()
    private val jobs = ConcurrentHashMap.newKeySet<Job>()

    fun isGranted(): Boolean = granted.get()
    fun requireConsent() { check(isGranted()) { "请先同意隐私政策" } }
    fun allow() { synchronized(actionLock) { granted.set(true) } }
    fun block() {
        // Close the gate before waiting for any in-flight work to release resources.
        granted.set(false)
        jobs.toList().forEach { it.cancel(CancellationException("Privacy consent withdrawn")) }
    }
    fun <T> withConsent(block: () -> T): T = synchronized(actionLock) {
        requireConsent()
        block()
    }
    fun trackCurrentJob(job: Job): Closeable {
        requireConsent()
        jobs.add(job)
        // Covers withdrawal between the first check and registration.
        if (!isGranted()) {
            jobs.remove(job)
            job.cancel(CancellationException("Privacy consent withdrawn"))
            requireConsent()
        }
        return Closeable { jobs.remove(job) }
    }
}

/** One state machine for first consent, refusal and withdrawal. No business storage is touched. */
class PrivacyConsentController(
    private val store: ConsentStore,
    private val stopControlledWork: suspend () -> Unit,
    private val exitApp: () -> Unit,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val audit: (String) -> Unit = {},
) {
    private val mutableState = MutableStateFlow(ConsentUiState())
    val state: StateFlow<ConsentUiState> = mutableState.asStateFlow()
    private val operation = AtomicBoolean(false)

    suspend fun initialize() {
        if (state.value.phase != ConsentPhase.Loading || !operation.compareAndSet(false, true)) return
        PrivacyGate.block()
        try {
            val record = store.read()
            val valid = record.decision == ConsentDecision.Accepted &&
                record.policyVersion == CURRENT_PRIVACY_POLICY_VERSION && (record.acceptedAtMs ?: 0) > 0
            if (valid) {
                PrivacyGate.allow()
                mutableState.value = ConsentUiState(ConsentPhase.Granted)
                audit("consent_restored")
            } else {
                mutableState.value = ConsentUiState(if (record.decision == ConsentDecision.Denied)
                    ConsentPhase.Denied else ConsentPhase.Required)
                audit("consent_required")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutableState.value = ConsentUiState(ConsentPhase.Required, error = "读取隐私授权状态失败，相关功能尚未启用。请重试或退出。")
            audit("consent_read_failed")
        } finally { operation.set(false) }
    }

    suspend fun agree() {
        if (state.value.phase !in setOf(ConsentPhase.Required, ConsentPhase.Denied) ||
            !operation.compareAndSet(false, true)) return
        mutableState.value = state.value.copy(busy = true, error = null)
        try {
            store.write(ConsentRecord(ConsentDecision.Accepted, CURRENT_PRIVACY_POLICY_VERSION, acceptedAtMs = now()))
            audit("consent_accepted_persisted")
            PrivacyGate.allow()
            mutableState.value = ConsentUiState(ConsentPhase.Granted)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            PrivacyGate.block()
            mutableState.value = ConsentUiState(ConsentPhase.Required, error = "保存隐私授权失败，尚未启用相关功能。请重试。")
            audit("consent_accept_failed")
        } finally { operation.set(false) }
    }

    suspend fun decline() {
        if (state.value.phase !in setOf(ConsentPhase.Required, ConsentPhase.Denied) ||
            !operation.compareAndSet(false, true)) return
        PrivacyGate.block()
        mutableState.value = ConsentUiState(ConsentPhase.Denied, busy = true)
        try {
            store.write(ConsentRecord(ConsentDecision.Denied, CURRENT_PRIVACY_POLICY_VERSION))
            mutableState.value = ConsentUiState(ConsentPhase.Denied)
            audit("consent_denied_persisted")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutableState.value = ConsentUiState(ConsentPhase.Denied, error = "保存未同意状态失败，相关功能仍保持关闭。请重试或退出。")
            audit("consent_deny_failed")
        } finally { operation.set(false) }
    }

    suspend fun revoke() {
        if (state.value.phase !in setOf(ConsentPhase.Granted, ConsentPhase.Revoking) ||
            !operation.compareAndSet(false, true)) return
        PrivacyGate.block()
        mutableState.value = ConsentUiState(ConsentPhase.Revoking, busy = true)
        audit("consent_gate_closed")
        try {
            store.write(ConsentRecord(ConsentDecision.Revoked, CURRENT_PRIVACY_POLICY_VERSION, revokedAtMs = now()))
            audit("consent_revoked_persisted")
            stopControlledWork()
            audit("controlled_work_stopped")
            mutableState.value = ConsentUiState(ConsentPhase.Revoked)
            exitApp()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutableState.value = ConsentUiState(ConsentPhase.Revoking,
                error = "撤销尚未完成，相关功能已暂停。请重试；未完成前不会恢复授权或显示撤销成功。")
            audit("consent_revoke_failed")
        } finally { operation.set(false) }
    }
}
