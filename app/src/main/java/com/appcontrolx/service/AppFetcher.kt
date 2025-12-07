package com.appcontrolx.service

import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.appcontrolx.model.AppInfo

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
        return try {
            activityManager.runningAppProcesses
                ?.map { it.pkgList.toList() }
                ?.flatten()
                ?.toSet()
                ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
    }
    
    private fun isBackgroundRestricted(packageName: String, uid: Int): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val mode = appOpsManager.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_RUN_IN_BACKGROUND,
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
