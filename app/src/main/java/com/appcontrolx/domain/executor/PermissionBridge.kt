package com.appcontrolx.domain.executor

import android.content.Context
import android.content.SharedPreferences
import com.appcontrolx.data.model.ExecutionMode
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridge for detecting and managing execution mode (Root/Shizuku/None).
 * 
 * Responsibilities:
 * - Detect available execution modes with priority: Root > Shizuku > None
 * - Persist selected mode to preferences
 * - Provide mode status observation
 * 
 * Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6
 */
@Singleton
class PermissionBridge @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val PREFS_NAME = "appcontrolx_prefs"
        private const val KEY_EXECUTION_MODE = "execution_mode"
        
        private const val MODE_ROOT = "root"
        private const val MODE_SHIZUKU = "shizuku"
        private const val MODE_NONE = "none"
        
        const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001
    }
    
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    private val _currentMode = MutableStateFlow<ExecutionMode>(ExecutionMode.None)
    
    /**
     * Observable flow of current execution mode.
     */
    val currentMode: Flow<ExecutionMode> = _currentMode.asStateFlow()
    
    /**
     * Get the current execution mode value.
     */
    val mode: ExecutionMode
        get() = _currentMode.value
    
    /**
     * Detect and return the best available execution mode.
     * 
     * Priority order:
     * 1. If user has explicitly selected a mode, use that (Requirement 1.6)
     * 2. Otherwise, auto-detect: Root > Shizuku > None (Requirements 1.1, 1.2, 1.3, 1.4)
     * 
     * @param forceDetect If true, ignore saved preference and re-detect
     * @return The detected or saved ExecutionMode
     */
    suspend fun detectMode(forceDetect: Boolean = false): ExecutionMode = withContext(Dispatchers.IO) {
        // Check saved preference first (Requirement 1.5, 1.6)
        if (!forceDetect) {
            val savedMode = getSavedMode()
            if (savedMode != null) {
                _currentMode.value = savedMode
                return@withContext savedMode
            }
        }
        
        // Auto-detect mode with priority: Root > Shizuku > None
        val detectedMode = detectAvailableMode()
        _currentMode.value = detectedMode
        
        // Persist detected mode (Requirement 1.5)
        if (detectedMode != ExecutionMode.None) {
            saveMode(detectedMode)
        }
        
        detectedMode
    }

    
    /**
     * Auto-detect available execution mode.
     * Priority: Root > Shizuku > None (Requirements 1.1, 1.2, 1.3)
     */
    private suspend fun detectAvailableMode(): ExecutionMode {
        // 1. Check root first (highest priority) - Requirement 1.1, 1.3
        if (checkRootNow()) {
            return ExecutionMode.Root
        }
        
        // 2. Check Shizuku - Requirement 1.2
        if (isShizukuReady()) {
            return ExecutionMode.Shizuku
        }
        
        // 3. Fallback to None (View-Only) - Requirement 1.4
        return ExecutionMode.None
    }
    
    /**
     * Check if root access is available NOW (blocking check).
     * 
     * This performs an actual root shell test to verify root access.
     * Use this during initial setup or explicit verification.
     * 
     * Requirement 1.1
     * 
     * @return true if root access is available and working
     */
    suspend fun checkRootNow(): Boolean = withContext(Dispatchers.IO) {
        try {
            // First try quick check
            if (Shell.isAppGrantedRoot() == true) {
                return@withContext true
            }
            
            // Try to execute a command to verify root
            val result = Shell.cmd("id").exec()
            
            if (result.isSuccess) {
                val output = result.out.joinToString("\n")
                // Check if we're running as root (uid=0)
                if (output.contains("uid=0")) {
                    return@withContext true
                }
            }
            
            // Alternative: try su shell directly
            val suShell = Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(10)
                .build("su")
            
            suShell.isRoot
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Quick non-blocking check if root is available.
     * Uses cached state from libsu.
     * 
     * @return true if root appears to be available
     */
    fun isRootAvailable(): Boolean {
        return try {
            Shell.isAppGrantedRoot() == true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check if Shizuku service is running (binder is alive).
     * 
     * @return true if Shizuku service is running
     */
    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check if Shizuku permission is granted.
     * 
     * @return true if Shizuku permission is granted
     */
    fun isShizukuPermissionGranted(): Boolean {
        return try {
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check if Shizuku is fully ready (running AND permission granted).
     * 
     * Requirement 1.2
     * 
     * @return true if Shizuku is ready to use
     */
    fun isShizukuReady(): Boolean {
        return isShizukuAvailable() && isShizukuPermissionGranted()
    }
    
    /**
     * Request Shizuku permission.
     * Call this when Shizuku is available but permission is not granted.
     * 
     * @param requestCode Request code for the permission callback
     */
    fun requestShizukuPermission(requestCode: Int = SHIZUKU_PERMISSION_REQUEST_CODE) {
        try {
            if (isShizukuAvailable() && !isShizukuPermissionGranted()) {
                Shizuku.requestPermission(requestCode)
            }
        } catch (e: Exception) {
            // Ignore - Shizuku not ready
        }
    }

    
    /**
     * Manually set the execution mode.
     * Use this when user explicitly selects a mode in settings.
     * 
     * Requirement 1.6
     * 
     * @param mode The mode to set
     */
    fun setMode(mode: ExecutionMode) {
        saveMode(mode)
        _currentMode.value = mode
    }
    
    /**
     * Clear saved mode preference.
     * Next detectMode() call will auto-detect.
     */
    fun clearSavedMode() {
        prefs.edit().remove(KEY_EXECUTION_MODE).apply()
    }
    
    /**
     * Save execution mode to preferences.
     * Requirement 1.5
     */
    private fun saveMode(mode: ExecutionMode) {
        val modeString = when (mode) {
            ExecutionMode.Root -> MODE_ROOT
            ExecutionMode.Shizuku -> MODE_SHIZUKU
            ExecutionMode.None -> MODE_NONE
        }
        prefs.edit().putString(KEY_EXECUTION_MODE, modeString).apply()
    }
    
    /**
     * Get saved execution mode from preferences.
     * 
     * @return The saved mode, or null if not set
     */
    private fun getSavedMode(): ExecutionMode? {
        val savedMode = prefs.getString(KEY_EXECUTION_MODE, null) ?: return null
        
        return when (savedMode) {
            MODE_ROOT -> ExecutionMode.Root
            MODE_SHIZUKU -> ExecutionMode.Shizuku
            MODE_NONE -> ExecutionMode.None
            else -> null
        }
    }
    
    /**
     * Verify that the current mode is still available.
     * Call this on app resume to detect mode loss.
     * 
     * @return true if current mode is still available
     */
    suspend fun verifyCurrentMode(): Boolean = withContext(Dispatchers.IO) {
        when (_currentMode.value) {
            ExecutionMode.Root -> checkRootNow()
            ExecutionMode.Shizuku -> isShizukuReady()
            ExecutionMode.None -> true // View-only is always available
        }
    }
    
    /**
     * Re-detect mode if current mode is no longer available.
     * 
     * @return The new mode after re-detection
     */
    suspend fun refreshMode(): ExecutionMode {
        val isCurrentModeValid = verifyCurrentMode()
        
        return if (isCurrentModeValid) {
            _currentMode.value
        } else {
            // Current mode lost, re-detect
            clearSavedMode()
            detectMode(forceDetect = true)
        }
    }
}
