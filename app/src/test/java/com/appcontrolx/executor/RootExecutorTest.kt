package com.appcontrolx.executor

import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RootExecutorTest {
    
    private lateinit var executor: RootExecutor
    
    @Before
    fun setup() {
        executor = RootExecutor()
    }
    
    @Test
    fun `execute should reject dangerous rm commands`() {
        val result = executor.execute("rm -rf /")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }
    
    @Test
    fun `execute should reject commands with shell injection`() {
        val result = executor.execute("pm disable com.app; rm -rf /")
        assertTrue(result.isFailure)
    }
    
    @Test
    fun `execute should reject reboot commands`() {
        val result = executor.execute("reboot")
        assertTrue(result.isFailure)
    }
    
    @Test
    fun `execute should reject format commands`() {
        val result = executor.execute("format /data")
        assertTrue(result.isFailure)
    }
    
    @Test
    fun `executeBatch should reject if any command is blocked`() {
        val commands = listOf(
            "pm disable com.example.app",
            "rm -rf /"
        )
        val result = executor.executeBatch(commands)
        assertTrue(result.isFailure)
    }
    
    // Note: Actual execution tests require rooted device/emulator
    // These tests verify security validation only
}
