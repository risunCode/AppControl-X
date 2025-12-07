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
            // Configure libsu for root shell
            Shell.enableVerboseLogging = BuildConfig.DEBUG
            Shell.setDefaultBuilder(
                Shell.Builder.create()
                    .setFlags(Shell.FLAG_REDIRECT_STDERR or Shell.FLAG_NON_ROOT_SHELL)
                    .setTimeout(30)
            )
        }
        
        /**
         * Get root shell - call this to request su permission
         */
        fun getRootShell(): Shell? {
            return try {
                val shell = Shell.Builder.create()
                    .setFlags(Shell.FLAG_REDIRECT_STDERR)
                    .setTimeout(30)
                    .build("su")
                if (shell.isRoot) shell else null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get root shell", e)
                null
            }
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
