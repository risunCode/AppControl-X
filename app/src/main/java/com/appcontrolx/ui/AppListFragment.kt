package com.appcontrolx.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.appcontrolx.R
import com.appcontrolx.databinding.FragmentAppListBinding
import com.appcontrolx.executor.CommandExecutor
import com.appcontrolx.executor.RootExecutor
import com.appcontrolx.model.AppInfo
import com.appcontrolx.model.ExecutionMode
import com.appcontrolx.rollback.ActionLog
import com.appcontrolx.rollback.RollbackManager
import com.appcontrolx.service.AppFetcher
import com.appcontrolx.service.BatteryPolicyManager
import com.appcontrolx.service.PermissionBridge
import com.appcontrolx.ui.adapter.AppListAdapter
import com.appcontrolx.utils.SafetyValidator
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class AppListFragment : Fragment() {
    
    private var _binding: FragmentAppListBinding? = null
    private val binding get() = _binding
    
    private lateinit var adapter: AppListAdapter
    private lateinit var appFetcher: AppFetcher
    private var executor: CommandExecutor? = null
    private var policyManager: BatteryPolicyManager? = null
    private var rollbackManager: RollbackManager? = null
    
    private var showSystemApps = false
    private var executionMode: ExecutionMode = ExecutionMode.None
    private var currentSearchQuery: String = ""
    
    // App cache - persists until package change detected
    private var cachedUserApps: List<AppInfo>? = null
    private var cachedSystemApps: List<AppInfo>? = null
    
    // Package change receiver
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_PACKAGE_ADDED,
                Intent.ACTION_PACKAGE_REMOVED,
                Intent.ACTION_PACKAGE_CHANGED -> {
                    clearCache()
                    if (_binding != null) loadApps()
                }
            }
        }
    }

    companion object {
        private const val LOAD_TIMEOUT_MS = 30000L
        private const val ACTION_TIMEOUT_MS = 60000L
    }
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAppListBinding.inflate(inflater, container, false)
        return _binding!!.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupExecutionMode()
        appFetcher = AppFetcher(requireContext())
        
        setupHeader()
        setupRecyclerView()
        setupSwipeRefresh()
        setupSearch()
        setupChips()
        setupSelectionBar()
        setupSelectAll()
        registerPackageReceiver()
        loadApps()
    }
    
    private fun setupSearch() {
        val b = binding ?: return
        
        b.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                currentSearchQuery = s?.toString()?.trim() ?: ""
                filterApps()
            }
        })
        
        b.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                filterApps()
                true
            } else false
        }
    }
    
    private fun filterApps() {
        val cachedApps = if (showSystemApps) cachedSystemApps else cachedUserApps
        if (cachedApps == null) return
        
        val filtered = if (currentSearchQuery.isBlank()) {
            cachedApps
        } else {
            val query = currentSearchQuery.lowercase()
            cachedApps.filter { app ->
                app.appName.lowercase().contains(query) ||
                app.packageName.lowercase().contains(query)
            }
        }
        
        displayApps(filtered)
    }
    
    private fun setupExecutionMode() {
        executionMode = PermissionBridge(requireContext()).detectMode()
        
        if (executionMode is ExecutionMode.Root) {
            executor = RootExecutor()
            policyManager = BatteryPolicyManager(executor!!)
            rollbackManager = RollbackManager(requireContext(), executor!!)
        }
        // TODO: Add Shizuku executor support
    }
    
    private fun setupHeader() {
        val b = binding ?: return
        val modeText = when (executionMode) {
            is ExecutionMode.Root -> "ROOT"
            is ExecutionMode.Shizuku -> "SHIZUKU"
            else -> "VIEW ONLY"
        }
        b.tvModeIndicator.text = modeText
    }
    
    private fun setupRecyclerView() {
        val b = binding ?: return
        adapter = AppListAdapter(
            onSelectionChanged = { selectedCount ->
                updateSelectionUI(selectedCount)
            },
            onInfoClick = { app ->
                showAppDetail(app)
            }
        )
        b.recyclerView.layoutManager = LinearLayoutManager(context)
        b.recyclerView.adapter = adapter
        b.recyclerView.setHasFixedSize(true)
    }
    
    private fun setupSwipeRefresh() {
        val b = binding ?: return
        b.swipeRefresh.setOnRefreshListener {
            loadApps(forceRefresh = true)
        }
        b.swipeRefresh.setColorSchemeResources(
            R.color.primary,
            R.color.secondary,
            R.color.tertiary
        )
    }
    
    private fun showAppDetail(app: AppInfo) {
        val bottomSheet = AppDetailBottomSheet.newInstance(app)
        bottomSheet.onActionCompleted = {
            clearCache()
            loadApps()
        }
        bottomSheet.show(childFragmentManager, AppDetailBottomSheet.TAG)
    }
    
    private fun setupChips() {
        val b = binding ?: return
        b.chipUserApps.isChecked = true
        
        b.chipUserApps.setOnClickListener {
            showSystemApps = false
            b.chipUserApps.isChecked = true
            b.chipSystemApps.isChecked = false
            adapter.deselectAll()
            loadApps()
        }
        
        b.chipSystemApps.setOnClickListener {
            showSystemApps = true
            b.chipSystemApps.isChecked = true
            b.chipUserApps.isChecked = false
            adapter.deselectAll()
            loadApps()
        }
    }
    
    private fun setupSelectionBar() {
        val b = binding ?: return
        b.btnCloseSelection.setOnClickListener {
            adapter.deselectAll()
        }
        
        b.btnAction.setOnClickListener {
            showActionSheet()
        }
    }
    
    private fun setupSelectAll() {
        val b = binding ?: return
        b.btnSelectAll.setOnClickListener {
            if (adapter.isAllSelected()) {
                adapter.deselectAll()
            } else {
                adapter.selectAll()
            }
            updateSelectAllButton()
        }
    }

    private fun updateSelectionUI(selectedCount: Int) {
        val b = binding ?: return
        
        if (selectedCount > 0) {
            b.selectionBar.visibility = View.VISIBLE
            b.tvSelectedCount.text = getString(R.string.selected_count, selectedCount)
            
            b.btnAction.isEnabled = executionMode !is ExecutionMode.None
            if (executionMode is ExecutionMode.None) {
                b.btnAction.text = getString(R.string.mode_required)
            } else {
                b.btnAction.text = getString(R.string.action_execute)
            }
        } else {
            b.selectionBar.visibility = View.GONE
        }
        updateSelectAllButton()
    }
    
    private fun updateSelectAllButton() {
        val b = binding ?: return
        b.btnSelectAll.text = if (adapter.isAllSelected()) {
            getString(R.string.btn_deselect_all)
        } else {
            getString(R.string.btn_select_all)
        }
    }
    
    private fun showActionSheet() {
        val selectedApps = adapter.getSelectedApps()
        if (selectedApps.isEmpty()) return
        
        if (executionMode is ExecutionMode.None) {
            showModeRequiredDialog()
            return
        }
        
        val bottomSheet = ActionBottomSheet.newInstance(selectedApps.size)
        bottomSheet.onActionSelected = { action ->
            handleAction(action, selectedApps)
        }
        bottomSheet.show(childFragmentManager, ActionBottomSheet.TAG)
    }
    
    private fun showModeRequiredDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.error_mode_required_title)
            .setMessage(R.string.error_mode_required_message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
    
    private fun handleAction(action: ActionBottomSheet.Action, apps: List<AppInfo>) {
        val packages = apps.map { it.packageName }
        
        val validation = SafetyValidator.validate(packages)
        if (!validation.canProceed) {
            showBlockedWarning(validation.blocked)
            return
        }
        
        val filteredPackages = if (action != ActionBottomSheet.Action.FORCE_STOP) {
            val forceStopOnly = packages.filter { SafetyValidator.isForceStopOnly(it) }
            if (forceStopOnly.isNotEmpty()) {
                showForceStopOnlyWarning(forceStopOnly)
            }
            packages.filter { !SafetyValidator.isForceStopOnly(it) }
        } else {
            packages
        }
        
        if (filteredPackages.isEmpty()) {
            Toast.makeText(context, R.string.error_no_valid_apps, Toast.LENGTH_SHORT).show()
            return
        }
        
        if (validation.warnings.isNotEmpty()) {
            showWarningDialog(validation.warnings) {
                executeAction(action, filteredPackages.filter { it !in validation.warnings })
            }
        } else {
            executeAction(action, filteredPackages)
        }
    }
    
    private fun showForceStopOnlyWarning(packages: List<String>) {
        Toast.makeText(context, 
            getString(R.string.warning_force_stop_only, packages.size), 
            Toast.LENGTH_LONG).show()
    }
    
    private fun getStatusText(action: ActionBottomSheet.Action, success: Boolean): String {
        return if (success) {
            when (action) {
                ActionBottomSheet.Action.FREEZE -> "Frozen"
                ActionBottomSheet.Action.UNFREEZE -> "Enabled"
                ActionBottomSheet.Action.UNINSTALL -> "Uninstalled"
                ActionBottomSheet.Action.FORCE_STOP -> "Stopped"
                ActionBottomSheet.Action.RESTRICT_BACKGROUND -> "Restricted"
                ActionBottomSheet.Action.ALLOW_BACKGROUND -> "Allowed"
                ActionBottomSheet.Action.CLEAR_CACHE -> "Cache Cleared"
                ActionBottomSheet.Action.CLEAR_DATA -> "Data Cleared"
            }
        } else {
            "Failed"
        }
    }

    private fun executeAction(action: ActionBottomSheet.Action, packages: List<String>) {
        val pm = policyManager ?: return
        val rm = rollbackManager
        
        // Get app names for display
        val appNames = packages.map { pkg -> 
            adapter.getAppName(pkg) ?: pkg.substringAfterLast(".")
        }
        
        // Show new BottomSheet with countdown
        val bottomSheet = BatchProgressBottomSheet.newInstance(
            actionName = action.name,
            appNames = appNames,
            packageNames = packages,
            onExecute = { pkg ->
                withContext(Dispatchers.IO) {
                    when (action) {
                        ActionBottomSheet.Action.FREEZE -> pm.freezeApp(pkg)
                        ActionBottomSheet.Action.UNFREEZE -> pm.unfreezeApp(pkg)
                        ActionBottomSheet.Action.UNINSTALL -> pm.uninstallApp(pkg)
                        ActionBottomSheet.Action.FORCE_STOP -> pm.forceStop(pkg)
                        ActionBottomSheet.Action.RESTRICT_BACKGROUND -> pm.restrictBackground(pkg)
                        ActionBottomSheet.Action.ALLOW_BACKGROUND -> pm.allowBackground(pkg)
                        ActionBottomSheet.Action.CLEAR_CACHE -> executor?.execute("pm clear --cache-only $pkg")?.map { } ?: Result.failure(Exception("No executor"))
                        ActionBottomSheet.Action.CLEAR_DATA -> executor?.execute("pm clear $pkg")?.map { } ?: Result.failure(Exception("No executor"))
                    }
                }
            },
            onComplete = {
                adapter.deselectAll()
                clearCache()
                loadApps(forceRefresh = true)
            }
        )
        bottomSheet.show(childFragmentManager, BatchProgressBottomSheet.TAG)
        
        // Save snapshot before action
        lifecycleScope.launch {
            rm?.saveSnapshot(packages)
        }
    }
    
    private fun showBlockedWarning(blocked: List<String>) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.error_blocked_title)
            .setMessage(getString(R.string.error_blocked_message, blocked.joinToString("\n")))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
    
    private fun showWarningDialog(warnings: List<String>, onProceed: () -> Unit) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.warning_title)
            .setMessage(getString(R.string.warning_system_apps, warnings.joinToString("\n")))
            .setPositiveButton(R.string.confirm_yes) { _, _ -> onProceed() }
            .setNegativeButton(R.string.confirm_no, null)
            .show()
    }
    
    private fun showErrorDialog(title: String, message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
    
    private fun clearCache() {
        cachedUserApps = null
        cachedSystemApps = null
    }

    private fun loadApps(forceRefresh: Boolean = false) {
        val b = binding ?: return
        
        // Check cache first - only refresh on package change or manual refresh
        val cachedApps = if (showSystemApps) cachedSystemApps else cachedUserApps
        if (!forceRefresh && cachedApps != null) {
            filterApps() // Apply current search filter
            b.swipeRefresh.isRefreshing = false
            return
        }
        
        b.progressBar.visibility = View.VISIBLE
        b.emptyState.visibility = View.GONE
        b.recyclerView.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                val apps = withTimeout(LOAD_TIMEOUT_MS) {
                    withContext(Dispatchers.IO) {
                        if (showSystemApps) appFetcher.getSystemApps()
                        else appFetcher.getUserApps()
                    }
                }
                
                // Cache the results - persists until package change
                if (showSystemApps) {
                    cachedSystemApps = apps
                } else {
                    cachedUserApps = apps
                }
                
                filterApps() // Apply current search filter
                
            } catch (e: TimeoutCancellationException) {
                showEmptyState(
                    getString(R.string.error_timeout_title),
                    getString(R.string.error_load_timeout_message),
                    showRetry = true
                )
            } catch (e: Exception) {
                showEmptyState(
                    getString(R.string.error_load_title),
                    e.message ?: getString(R.string.error_unknown),
                    showRetry = true
                )
            } finally {
                binding?.progressBar?.visibility = View.GONE
                binding?.swipeRefresh?.isRefreshing = false
            }
        }
    }
    
    private fun displayApps(apps: List<AppInfo>) {
        val b = binding ?: return
        
        if (apps.isEmpty()) {
            if (currentSearchQuery.isNotBlank()) {
                // No search results
                showEmptyState(
                    getString(R.string.search_no_results),
                    "\"$currentSearchQuery\""
                )
            } else {
                showEmptyState(
                    getString(R.string.empty_no_apps_title),
                    getString(R.string.empty_no_apps_message)
                )
            }
        } else {
            b.emptyState.visibility = View.GONE
            b.recyclerView.visibility = View.VISIBLE
            adapter.submitList(apps)
            b.tvAppCount.text = getString(R.string.app_count, apps.size)
        }
    }
    
    private fun showEmptyState(title: String, message: String, showRetry: Boolean = false) {
        val b = binding ?: return
        
        b.recyclerView.visibility = View.GONE
        b.emptyState.visibility = View.VISIBLE
        b.tvEmptyTitle.text = title
        b.tvEmptyMessage.text = message
        b.btnRetry.visibility = if (showRetry) View.VISIBLE else View.GONE
        b.btnRetry.setOnClickListener { loadApps() }
    }
    
    private fun registerPackageReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        requireContext().registerReceiver(packageReceiver, filter)
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        try {
            requireContext().unregisterReceiver(packageReceiver)
        } catch (e: Exception) {
            // Receiver not registered
        }
        _binding = null
    }
}
