package com.appcontrolx.model

import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val isSystemApp: Boolean,
    val isEnabled: Boolean,
    val isRunning: Boolean = false,
    val isStopped: Boolean = false,
    val isBackgroundRestricted: Boolean = false
) {
    val isFrozen: Boolean get() = !isEnabled
}
