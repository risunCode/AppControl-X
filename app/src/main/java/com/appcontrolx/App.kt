package com.appcontrolx

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.appcontrolx.utils.Constants
import com.topjohnwu.superuser.Shell

class App : Application() {
    
    companion object {
        init {
            Shell.enableVerboseLogging = BuildConfig.DEBUG
            Shell.setDefaultBuilder(
                Shell.Builder.create()
                    .setFlags(Shell.FLAG_MOUNT_MASTER)
                    .setTimeout(10)
            )
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        applyTheme()
    }
    
    private fun applyTheme() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val theme = prefs.getInt(Constants.PREFS_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(theme)
    }
}
