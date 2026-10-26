package com.example.pettranslator

import android.os.ParcelFileDescriptor
import android.view.WindowManager
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage

/** Test-only: wake an unsecured test device and prevent idle sleep in the test window. */
fun wakeTestDevice() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    listOf("input keyevent KEYCODE_WAKEUP", "wm dismiss-keyguard").forEach { command ->
        instrumentation.uiAutomation.executeShellCommand(command).use {
            ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
        }
    }
    instrumentation.runOnMainSync {
        ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).forEach {
            it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
