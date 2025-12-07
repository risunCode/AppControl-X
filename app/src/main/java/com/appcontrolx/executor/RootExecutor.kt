package com.appcontrolx.executor

import com.topjohnwu.superuser.Shell

class RootExecutor : CommandExecutor {
    
    companion object {
        // Whitelist of allowed command prefixes for security
        private val ALLOWED_COMMANDS = setOf(
            "pm disable",
            "pm enable",
            "pm uninstall",
            "pm clear",
            "pm list",
            "am force-stop",
            "appops set",
            "appops get",
            "cmd appops",
            "getprop",
            "dumpsys"
        )
        
        // Dangerous patterns that should never be executed
        private val BLOCKED_PATTERNS = listOf(
            "rm -rf /",
            "rm -rf /*",
            "format",
            "mkfs",
            "dd if=",
            "> /dev/",
            "reboot",
            "shutdown",
            "su -c",
            "chmod 777 /",
            "chown root /",
            "; rm",
            "&& rm",
            "| rm"
        )
    }
    
    private fun isCommandAllowed(command: String): Boolean {
        val trimmed = command.trim().lowercase()
        
        // Check for blocked patterns
        if (BLOCKED_PATTERNS.any { trimmed.contains(it.lowercase()) }) {
            return false
        }
        
        // Check if command starts with allowed prefix
        return ALLOWED_COMMANDS.any { trimmed.startsWith(it.lowercase()) }
    }
    
    override fun execute(command: String): Result<String> {
        // Security check
        if (!isCommandAllowed(command)) {
            return Result.failure(SecurityException("Command not allowed: $command"))
        }
        
        return try {
            // Build su shell and execute command
            val shell = Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(30)
                .build("su")
            
            if (!shell.isRoot) {
                return Result.failure(Exception("Root access not available - su denied"))
            }
            
            val result = shell.newJob().add(command).exec()
            if (result.isSuccess) {
                Result.success(result.out.joinToString("\n"))
            } else {
                val error = result.err.joinToString("\n").ifEmpty { "Command failed" }
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override fun executeBatch(commands: List<String>): Result<Unit> {
        // Security check all commands
        val blockedCommands = commands.filter { !isCommandAllowed(it) }
        if (blockedCommands.isNotEmpty()) {
            return Result.failure(SecurityException("Blocked commands: ${blockedCommands.joinToString()}"))
        }
        
        return try {
            // Build su shell
            val shell = Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(30)
                .build("su")
            
            if (!shell.isRoot) {
                return Result.failure(Exception("Root access not available - su denied"))
            }
            
            val job = shell.newJob()
            commands.forEach { job.add(it) }
            val result = job.exec()
            
            if (result.isSuccess) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(result.err.joinToString("\n")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
