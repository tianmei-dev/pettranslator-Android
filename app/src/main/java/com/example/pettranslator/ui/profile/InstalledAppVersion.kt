package com.example.pettranslator.ui.profile

import android.content.Context
import android.content.pm.PackageManager

/** Reads the installed package so every profile surface shows the same version. */
@Suppress("DEPRECATION")
internal fun installedAppVersion(context: Context): String? = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
        ?.takeIf { it.isNotBlank() }
} catch (_: PackageManager.NameNotFoundException) {
    null
}
