package com.appcontrolx.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyValidatorTest {
    
    @Test
    fun `isCritical should return true for system UI`() {
        assertTrue(SafetyValidator.isCritical("com.android.systemui"))
    }
    
    @Test
    fun `isCritical should return true for settings`() {
        assertTrue(SafetyValidator.isCritical("com.android.settings"))
    }
    
    @Test
    fun `isCritical should return false for regular app`() {
        assertFalse(SafetyValidator.isCritical("com.example.myapp"))
    }
    
    @Test
    fun `isForceStopOnly should return true for security apps`() {
        assertTrue(SafetyValidator.isForceStopOnly("com.google.android.gms"))
    }
    
    @Test
    fun `isForceStopOnly should return false for regular app`() {
        assertFalse(SafetyValidator.isForceStopOnly("com.example.myapp"))
    }
    
    @Test
    fun `validate should block critical packages`() {
        val packages = listOf("com.android.systemui", "com.example.app")
        val result = SafetyValidator.validate(packages)
        
        assertFalse(result.canProceed)
        assertTrue(result.blocked.contains("com.android.systemui"))
    }
    
    @Test
    fun `validate should allow regular packages`() {
        val packages = listOf("com.example.app1", "com.example.app2")
        val result = SafetyValidator.validate(packages)
        
        assertTrue(result.canProceed)
        assertTrue(result.blocked.isEmpty())
    }
    
    @Test
    fun `validate should warn about system packages`() {
        val packages = listOf("com.android.calculator2")
        val result = SafetyValidator.validate(packages)
        
        assertTrue(result.canProceed)
        assertTrue(result.warnings.isNotEmpty())
    }
}
