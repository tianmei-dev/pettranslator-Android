package com.example.pettranslator.privacy

import android.app.Activity
import android.content.Context
import android.os.Process
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.pettranslator.data.recognition.YamnetRuntime
import java.lang.ref.WeakReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.privacyConsentStore by preferencesDataStore("privacy_consent")

private class AndroidConsentStore(context: Context) : ConsentStore {
    private val store = context.applicationContext.privacyConsentStore
    private val decision = stringPreferencesKey("decision")
    private val version = stringPreferencesKey("policy_version")
    private val acceptedAt = longPreferencesKey("accepted_at_ms")
    private val revokedAt = longPreferencesKey("revoked_at_ms")

    override suspend fun read(): ConsentRecord {
        val values = store.data.first()
        return ConsentRecord(ConsentDecision.entries.firstOrNull { it.name == values[decision] }
            ?: ConsentDecision.Unknown, values[version], values[acceptedAt], values[revokedAt])
    }
    override suspend fun write(record: ConsentRecord) {
        // edit returns only after the dedicated consent file has been atomically persisted.
        store.edit { values ->
            values[decision] = record.decision.name
            if (record.policyVersion == null) values.remove(version) else values[version] = record.policyVersion
            if (record.acceptedAtMs == null) values.remove(acceptedAt) else values[acceptedAt] = record.acceptedAtMs
            if (record.revokedAtMs == null) values.remove(revokedAt) else values[revokedAt] = record.revokedAtMs
        }
    }
}

/** Process lifetime owns persistence and shutdown; Activity recreation cannot interrupt withdrawal. */
class AndroidPrivacyConsent private constructor(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var activity = WeakReference<Activity>(null)
    private val controller = PrivacyConsentController(AndroidConsentStore(context),
        stopControlledWork = { YamnetRuntime.shutdownForPrivacyRevocation() },
        exitApp = { finishProcess() },
        audit = { Log.i("PrivacyConsent", it) })
    val state = controller.state

    fun attach(activity: Activity) { this.activity = WeakReference(activity) }
    fun initialize() { scope.launch { controller.initialize() } }
    fun agree() { scope.launch { controller.agree() } }
    fun decline() { scope.launch { controller.decline() } }
    fun declineAndExit() {
        scope.launch {
            controller.decline()
            if (state.value.phase == ConsentPhase.Denied && !state.value.busy && state.value.error == null) {
                exitWithoutConsent()
            }
        }
    }
    fun revoke() { scope.launch { controller.revoke() } }
    fun exitWithoutConsent() {
        if (!state.value.busy && state.value.phase in setOf(ConsentPhase.Required, ConsentPhase.Denied)) {
            PrivacyGate.block()
            finishProcess()
        }
    }
    private fun finishProcess() {
        Log.i("PrivacyConsent", "privacy_exit_process")
        try { activity.get()?.finishAndRemoveTask() }
        finally { Process.killProcess(Process.myPid()) }
    }
    companion object {
        @Volatile private var instance: AndroidPrivacyConsent? = null
        fun get(context: Context): AndroidPrivacyConsent = instance ?: synchronized(this) {
            instance ?: AndroidPrivacyConsent(context.applicationContext).also { instance = it }
        }
    }
}
