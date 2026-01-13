package com.appcontrolx

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.google.android.material.color.DynamicColors
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for AppControlX.
 * 
 * Handles:
 * - Hilt dependency injection initialization
 * - Root shell configuration via libsu
 * - Material 3 dynamic colors support (Android 12+)
 * - Theme (Light/Dark/System) application
 * 
 * Requirements: 8.7, 8.8 - Material 3 design with dynamic colors and dark mode support
 */
@HiltAndroidApp
class App : Application() {
    
    companion object {
        private const val TAG = "AppControlX"
        private const val PREFS_THEME = "theme"
        private const val PREFS_DYNAMIC_COLORS = "dynamic_colors"
        
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
        
        // Apply Material 3 dynamic colors (Android 12+)
        // This extracts colors from the user's wallpaper for a personalized theme
        applyDynamicColors()
        
        // Apply user's theme preference (Light/Dark/System)
        applyTheme()
        
        Log.d(TAG, "App initialized with Material 3 theming")
    }
    
    /**
     * Apply Material 3 dynamic colors if available (Android 12+).
     * 
     * Dynamic colors extract the dominant colors from the user's wallpaper
     * and apply them throughout the app for a cohesive, personalized experience.
     * 
     * On devices running Android 11 or below, the app falls back to the
     * static Material 3 color scheme defined in colors.xml.
     */
    private fun applyDynamicColors() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val useDynamicColors = prefs.getBoolean(PREFS_DYNAMIC_COLORS, true)
        
        if (useDynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Apply dynamic colors to all activities
            DynamicColors.applyToActivitiesIfAvailable(this)
            Log.d(TAG, "Dynamic colors applied")
        } else {
            Log.d(TAG, "Using static Material 3 color scheme")
        }
    }
    
    /**
     * Apply the user's theme preference.
     * 
     * Supports:
     * - MODE_NIGHT_FOLLOW_SYSTEM: Follow system dark mode setting (default)
     * - MODE_NIGHT_NO: Always use light theme
     * - MODE_NIGHT_YES: Always use dark theme
     */
    private fun applyTheme() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val theme = prefs.getInt(PREFS_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(theme)
        
        val themeName = when (theme) {
            AppCompatDelegate.MODE_NIGHT_NO -> "Light"
            AppCompatDelegate.MODE_NIGHT_YES -> "Dark"
            else -> "System"
        }
        Log.d(TAG, "Theme applied: $themeName")
    }
}
