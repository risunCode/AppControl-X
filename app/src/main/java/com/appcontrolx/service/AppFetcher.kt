package com.appcontrolx.service

import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.appcontrolx.model.AppInfo
import com.topjohnwu.superuser.Shell

class AppFetcher(private val context: Context) {
    
    private val activityManager by lazy {
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }
    
    private val appOpsManager by lazy {
        context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    }
    
    fun getAllApps(): List<AppInfo> {
        val pm = context.packageManager
        val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
        val runningPackages = getRunningPackages()
        
        return packages.mapNotNull { pkg ->
            try {
                val appInfo = pkg.applicationInfo
                AppInfo(
                    packageName = pkg.packageName,
                    appName = appInfo.loadLabel(pm).toString(),
                    icon = appInfo.loadIcon(pm),
                    isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    isEnabled = appInfo.enabled,
                    isRunning = runningPackages.contains(pkg.packageName),
                    isStopped = (appInfo.flags and ApplicationInfo.FLAG_STOPPED) != 0,
                    isBackgroundRestricted = isBackgroundRestricted(pkg.packageName, appInfo.uid)
                )
            } catch (e: Exception) {
                null
            }
        }.sortedBy { it.appName.lowercase() }
    }
    
    private fun getRunningPackages(): Set<String> {
        val running = mutableSetOf<String>()
        
        // Method 1: Try root command first (most accurate)
        try {
            if (Shell.isAppGrantedRoot() == true) {
                val result = Shell.cmd("ps -A -o NAME").exec()
                if (result.isSuccess) {
                    result.out.forEach { line ->
                        val processName = line.trim()
                        if (processName.isNotEmpty() && processName.contains(".")) {
                            running.add(processName)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Root not available, continue with other methods
        }
        
        // Method 2: runningAppProcesses (limited on Android 10+, but still useful)
        try {
            activityManager.runningAppProcesses?.forEach { process ->
                process.pkgList?.forEach { pkg ->
                    running.add(pkg)
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        
        // Method 3: getRunningServices (deprecated but still works)
        try {
            @Suppress("DEPRECATION")
            activityManager.getRunningServices(Int.MAX_VALUE)?.forEach { service ->
                running.add(service.service.packageName)
            }
        } catch (e: Exception) {
            // Ignore
        }
        
        return running
    }
    
    private fun isBackgroundRestricted(packageName: String, uid: Int): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // RUN_IN_BACKGROUND is hidden API, use string directly
                val mode = appOpsManager.unsafeCheckOpNoThrow(
                    "android:run_in_background",
                    uid,
                    packageName
                )
                mode == AppOpsManager.MODE_IGNORED
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    fun getUserApps(): List<AppInfo> = getAllApps().filter { !it.isSystemApp }
    
    fun getSystemApps(): List<AppInfo> = getAllApps().filter { it.isSystemApp }
}
