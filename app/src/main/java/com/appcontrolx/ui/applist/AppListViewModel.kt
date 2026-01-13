package com.appcontrolx.ui.applist

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appcontrolx.data.model.AppInfo
import com.appcontrolx.data.model.AppStatus
import com.appcontrolx.data.model.ExecutionMode
import com.appcontrolx.domain.executor.CommandExecutor
import com.appcontrolx.domain.executor.PermissionBridge
import com.appcontrolx.domain.executor.RootExecutor
import com.appcontrolx.domain.executor.ShizukuExecutor
import com.appcontrolx.domain.scanner.AppScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

/**
 * ViewModel for AppListFragment.
 * Handles app loading, filtering, and selection state.
 * 
 * Requirements: 8.3, 8.4, 8.5, 8.6
 */
@HiltViewModel
class AppListViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appScanner: AppScanner,
    private val permissionBridge: PermissionBridge,
    private val rootExecutor: RootExecutor,
    private val shizukuExecutor: ShizukuExecutor
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppListUiState())
    val uiState: StateFlow<AppListUiState> = _uiState.asStateFlow()

    // Cache for apps
    private var cachedUserApps: List<AppInfo>? = null
    private var cachedSystemApps: List<AppInfo>? = null

    // Package change receiver
    private var packageReceiver: BroadcastReceiver? = null
    private var isReceiverRegistered = false
    
    // Current executor based on mode
    private var executor: CommandExecutor? = null

    companion object {
        private const val LOAD_TIMEOUT_MS = 30000L
    }

    init {
        // Detect execution mode in coroutine
        viewModelScope.launch {
            val mode = permissionBridge.detectMode()
            _uiState.update { it.copy(executionMode = mode) }
            
            // Select executor based on mode
            executor = when (mode) {
                ExecutionMode.Root -> rootExecutor
                ExecutionMode.Shizuku -> shizukuExecutor
                ExecutionMode.None -> null
            }
            
            // Setup scanner with executor
            appScanner.setExecutor(executor, mode)
        }
    }


    /**
     * Load apps from scanner.
     * Uses cache if available.
     */
    fun loadApps(forceRefresh: Boolean = false) {
        val currentState = _uiState.value
        
        // Check cache first
        val cachedApps = if (currentState.showSystemApps) cachedSystemApps else cachedUserApps
        if (!forceRefresh && cachedApps != null) {
            _uiState.update { it.copy(allApps = cachedApps) }
            applyFilters()
            return
        }
        
        _uiState.update { it.copy(isLoading = true, error = null) }
        
        viewModelScope.launch {
            try {
                val apps = withTimeout(LOAD_TIMEOUT_MS) {
                    withContext(Dispatchers.IO) {
                        if (currentState.showSystemApps) {
                            appScanner.scanSystemApps()
                        } else {
                            appScanner.scanUserApps()
                        }
                    }
                }
                
                // Cache results
                if (currentState.showSystemApps) {
                    cachedSystemApps = apps
                } else {
                    cachedUserApps = apps
                }
                
                _uiState.update { it.copy(allApps = apps, isLoading = false) }
                applyFilters()
                
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        error = e.message ?: "Unknown error"
                    ) 
                }
            }
        }
    }

    /**
     * Refresh apps (force reload).
     */
    fun refreshApps() {
        _uiState.update { it.copy(isRefreshing = true) }
        clearCache()
        
        viewModelScope.launch {
            try {
                val currentState = _uiState.value
                val apps = withTimeout(LOAD_TIMEOUT_MS) {
                    withContext(Dispatchers.IO) {
                        appScanner.clearCache()
                        if (currentState.showSystemApps) {
                            appScanner.scanSystemApps()
                        } else {
                            appScanner.scanUserApps()
                        }
                    }
                }
                
                // Cache results
                if (currentState.showSystemApps) {
                    cachedSystemApps = apps
                } else {
                    cachedUserApps = apps
                }
                
                _uiState.update { it.copy(allApps = apps, isRefreshing = false, error = null) }
                applyFilters()
                
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isRefreshing = false, 
                        error = e.message ?: "Unknown error"
                    ) 
                }
            }
        }
    }

    /**
     * Handle search query change.
     * Requirement: 8.3
     */
    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilters()
    }

    /**
     * Handle system/user apps toggle.
     * Requirement: 8.5
     */
    fun onShowSystemAppsChanged(showSystem: Boolean) {
        _uiState.update { it.copy(showSystemApps = showSystem, selectedCount = 0) }
        loadApps()
    }

    /**
     * Handle status filter change.
     * Requirement: 8.4
     */
    fun onStatusFilterChanged(filter: StatusFilter) {
        _uiState.update { it.copy(statusFilter = filter) }
        applyFilters()
    }

    /**
     * Handle selection count change.
     */
    fun onSelectionChanged(count: Int) {
        _uiState.update { it.copy(selectedCount = count) }
    }


    /**
     * Apply search and status filters to the app list.
     * Requirements: 8.3, 8.4
     */
    private fun applyFilters() {
        val currentState = _uiState.value
        var filtered = currentState.allApps
        
        // Apply status filter
        filtered = when (currentState.statusFilter) {
            StatusFilter.ALL -> filtered
            StatusFilter.RUNNING -> filtered.filter { it.status == AppStatus.RUNNING }
            StatusFilter.STOPPED -> filtered.filter { it.status == AppStatus.STOPPED }
            StatusFilter.FROZEN -> filtered.filter { it.status == AppStatus.FROZEN }
            StatusFilter.RESTRICTED -> filtered.filter { it.status == AppStatus.RESTRICTED }
        }
        
        // Apply search filter
        if (currentState.searchQuery.isNotBlank()) {
            val query = currentState.searchQuery.lowercase()
            filtered = filtered.filter { app ->
                app.appName.lowercase().contains(query) ||
                app.packageName.lowercase().contains(query)
            }
        }
        
        _uiState.update { it.copy(filteredApps = filtered) }
    }

    /**
     * Clear cached apps.
     */
    private fun clearCache() {
        cachedUserApps = null
        cachedSystemApps = null
    }

    /**
     * Register package change broadcast receiver.
     */
    fun registerPackageReceiver() {
        if (isReceiverRegistered) return
        
        packageReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_PACKAGE_ADDED,
                    Intent.ACTION_PACKAGE_REMOVED,
                    Intent.ACTION_PACKAGE_CHANGED -> {
                        clearCache()
                        appScanner.invalidateCache()
                        loadApps(forceRefresh = true)
                    }
                }
            }
        }
        
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(packageReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(packageReceiver, filter)
        }
        isReceiverRegistered = true
    }

    /**
     * Unregister package change broadcast receiver.
     */
    fun unregisterPackageReceiver() {
        if (!isReceiverRegistered) return
        
        try {
            packageReceiver?.let { context.unregisterReceiver(it) }
        } catch (e: Exception) {
            // Ignore if not registered
        }
        packageReceiver = null
        isReceiverRegistered = false
    }

    override fun onCleared() {
        super.onCleared()
        unregisterPackageReceiver()
    }
}
