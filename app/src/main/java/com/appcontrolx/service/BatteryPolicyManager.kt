package com.appcontrolx.service

import com.appcontrolx.executor.CommandExecutor
import com.appcontrolx.utils.SafetyValidator

class BatteryPolicyManager(private val executor: CommandExecutor) {
    
    companion object {
        // Package name validation regex - only allow valid Android package names
        private val PACKAGE_NAME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")
    }
    
    private fun validatePackageName(packageName: String): Result<Unit> {
        // Check for valid package name format
        if (!PACKAGE_NAME_REGEX.matches(packageName)) {
            return Result.failure(IllegalArgumentException("Invalid package name format"))
        }
        
        // Check for shell injection attempts
        if (packageName.contains(";") || packageName.contains("&") || 
            packageName.contains("|") || packageName.contains("`") ||
            packageName.contains("$") || packageName.contains("'") ||
            packageName.contains("\"") || packageName.contains("\n")) {
            return Result.failure(SecurityException("Potential injection detected"))
        }
        
        // Check if package is protected
        if (SafetyValidator.isCritical(packageName)) {
            return Result.failure(SecurityException("Cannot modify protected package"))
        }
        
        return Result.success(Unit)
    }
    
    fun restrictBackground(packageName: String): Result<Unit> {
        validatePackageName(packageName).onFailure { return Result.failure(it) }
        val commands = listOf(
            "appops set $packageName RUN_IN_BACKGROUND ignore",
            "appops set $packageName RUN_ANY_IN_BACKGROUND ignore",
            "appops set $packageName WAKE_LOCK ignore"
        )
        return executor.executeBatch(commands)
    }
    
    fun allowBackground(packageName: String): Result<Unit> {
        validatePackageName(packageName).onFailure { return Result.failure(it) }
        val commands = listOf(
            "appops set $packageName RUN_IN_BACKGROUND allow",
            "appops set $packageName RUN_ANY_IN_BACKGROUND allow",
            "appops set $packageName WAKE_LOCK allow"
        )
        return executor.executeBatch(commands)
    }
    
    fun getBackgroundStatus(packageName: String): BackgroundStatus {
        val result = executor.execute("appops get $packageName RUN_IN_BACKGROUND")
        val output = result.getOrDefault("")
        return when {
            output.contains("ignore") -> BackgroundStatus.RESTRICTED
            output.contains("allow") -> BackgroundStatus.ALLOWED
            else -> BackgroundStatus.DEFAULT
        }
    }
    
    fun forceStop(packageName: String): Result<Unit> {
        validatePackageName(packageName).onFailure { return Result.failure(it) }
        return executor.execute("am force-stop $packageName").map { }
    }
    
    fun freezeApp(packageName: String): Result<Unit> {
        validatePackageName(packageName).onFailure { return Result.failure(it) }
        if (SafetyValidator.isForceStopOnly(packageName)) {
            return Result.failure(SecurityException("This app can only be force-stopped"))
        }
        return executor.execute("pm disable-user --user 0 $packageName").map { }
    }
    
    fun unfreezeApp(packageName: String): Result<Unit> {
        validatePackageName(packageName).onFailure { return Result.failure(it) }
        return executor.execute("pm enable $packageName").map { }
    }
    
    fun uninstallApp(packageName: String): Result<Unit> {
        validatePackageName(packageName).onFailure { return Result.failure(it) }
        if (SafetyValidator.isForceStopOnly(packageName)) {
            return Result.failure(SecurityException("This app cannot be uninstalled"))
        }
        return executor.execute("pm uninstall -k --user 0 $packageName").map { }
    }
    
    fun clearCache(packageName: String): Result<Unit> {
        validatePackageName(packageName).onFailure { return Result.failure(it) }
        return executor.execute("pm clear --cache-only $packageName").map { }
    }
    
    fun clearData(packageName: String): Result<Unit> {
        validatePackageName(packageName).onFailure { return Result.failure(it) }
        return executor.execute("pm clear $packageName").map { }
    }
}
