package com.appcontrolx

import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.appcontrolx.utils.Constants
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class App : Application() {
    
    companion object {
        private const val TAG = "AppControlX"
        
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
        Log.d(TAG, "App initialized")
    }
    
    private fun applyTheme() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val theme = prefs.getInt(Constants.PREFS_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(theme)
    }
}
