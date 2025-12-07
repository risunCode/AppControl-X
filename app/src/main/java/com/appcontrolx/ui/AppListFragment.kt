package com.appcontrolx.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
    private val binding get() = _binding!!
    
    private lateinit var adapter: AppListAdapter
    private lateinit var appFetcher: AppFetcher
    private var executor: CommandExecutor? = null
    private var policyManager: BatteryPolicyManager? = null
    private var rollbackManager: RollbackManager? = null
    
    private var showSystemApps = false
    private var executionMode: ExecutionMode = ExecutionMode.None
    
    companion object {
        private const val LOAD_TIMEOUT_MS = 30000L
        private const val ACTION_TIMEOUT_MS = 60000L
    }
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAppListBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupExecutionMode()
        appFetcher = AppFetcher(requireContext())
        
        setupRecyclerView()
        setupChips()
        setupSelectionBar()
        setupSelectAll()
        loadApps()
    }
    
    private fun setupExecutionMode() {
        executionMode = PermissionBridge().detectMode()
        
        if (executionMode is ExecutionMode.Root) {
            executor = RootExecutor()
            policyManager = BatteryPolicyManager(executor!!)
            rollbackManager = RollbackManager(requireContext(), executor!!)
        }
        // TODO: Add Shizuku executor
    }
    
    private fun setupRecyclerView() {
        adapter = AppListAdapter { selectedCount ->
            updateSelectionUI(selectedCount)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.setHasFixedSize(true)
    }
    
    private fun setupChips() {
        binding.chipUserApps.isChecked = true
        
        binding.chipUserApps.setOnClickListener {
            showSystemApps = false
            binding.chipUserApps.isChecked = true
            binding.chipSystemApps.isChecked = false
            adapter.deselectAll()
            loadApps()
        }
        
        binding.chipSystemApps.setOnClickListener {
            showSystemApps = true
            binding.chipSystemApps.isChecked = true
            binding.chipUserApps.isChecked = false
            adapter.deselectAll()
            loadApps()
        }
    }
    
    private fun setupSelectionBar() {
        binding.btnCloseSelection.setOnClickListener {
            adapter.deselectAll()
        }
        
        binding.btnAction.setOnClickListener {
            showActionSheet()
        }
    }
    
    private fun setupSelectAll() {
        binding.btnSelectAll.setOnClickListener {
            if (adapter.isAllSelected()) {
                adapter.deselectAll()
            } else {
                adapter.selectAll()
            }
            updateSelectAllButton()
        }
    }
    
    private fun updateSelectionUI(selectedCount: Int) {
        if (selectedCount > 0) {
            binding.selectionBar.visibility = View.VISIBLE
            binding.tvSelectedCount.text = getString(R.string.selected_count, selectedCount)
            
            // Disable action button if no execution mode
            binding.btnAction.isEnabled = executionMode !is ExecutionMode.None
            if (executionMode is ExecutionMode.None) {
                binding.btnAction.text = getString(R.string.mode_required)
            } else {
                binding.btnAction.text = getString(R.string.action_execute)
            }
        } else {
            binding.selectionBar.visibility = View.GONE
        }
        updateSelectAllButton()
    }
    
    private fun updateSelectAllButton() {
        binding.btnSelectAll.text = if (adapter.isAllSelected()) {
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
        
        if (validation.warnings.isNotEmpty()) {
            showWarningDialog(validation.warnings) {
                executeAction(action, validation.safe)
            }
        } else {
            executeAction(action, packages)
        }
    }
    
    private fun executeAction(action: ActionBottomSheet.Action, packages: List<String>) {
        val pm = policyManager ?: return
        val rm = rollbackManager
        
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            
            try {
                rm?.saveSnapshot(packages)
                
                val results = withTimeout(ACTION_TIMEOUT_MS) {
                    withContext(Dispatchers.IO) {
                        packages.map { pkg ->
                            val result = when (action) {
                                ActionBottomSheet.Action.FREEZE -> pm.freezeApp(pkg)
                                ActionBottomSheet.Action.UNFREEZE -> pm.unfreezeApp(pkg)
                                ActionBottomSheet.Action.UNINSTALL -> pm.uninstallApp(pkg)
                                ActionBottomSheet.Action.FORCE_STOP -> pm.forceStop(pkg)
                                ActionBottomSheet.Action.RESTRICT_BACKGROUND -> pm.restrictBackground(pkg)
                                ActionBottomSheet.Action.ALLOW_BACKGROUND -> pm.allowBackground(pkg)
                            }
                            pkg to result
                        }
                    }
                }
                
                val failCount = results.count { it.second.isFailure }
                
                rm?.logAction(ActionLog(
                    action = action.name,
                    packages = packages,
                    success = failCount == 0,
                    message = if (failCount > 0) "$failCount failed" else null
                ))
                
                if (failCount == 0) {
                    Toast.makeText(context, R.string.action_success, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, getString(R.string.action_failed, "$failCount apps"), Toast.LENGTH_SHORT).show()
                }
                
                adapter.deselectAll()
                loadApps()
                
            } catch (e: TimeoutCancellationException) {
                showErrorDialog(
                    getString(R.string.error_timeout_title),
                    getString(R.string.error_timeout_message)
                )
            } catch (e: Exception) {
                showErrorDialog(
                    getString(R.string.error_action_title),
                    e.message ?: getString(R.string.error_unknown)
                )
            } finally {
                binding.progressBar.visibility = View.GONE
            }
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
    
    private fun loadApps() {
        binding.progressBar.visibility = View.VISIBLE
        binding.emptyState.visibility = View.GONE
        binding.recyclerView.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                val apps = withTimeout(LOAD_TIMEOUT_MS) {
                    withContext(Dispatchers.IO) {
                        if (showSystemApps) appFetcher.getSystemApps()
                        else appFetcher.getUserApps()
                    }
                }
                
                if (apps.isEmpty()) {
                    showEmptyState(
                        getString(R.string.empty_no_apps_title),
                        getString(R.string.empty_no_apps_message)
                    )
                } else {
                    adapter.submitList(apps)
                    binding.tvAppCount.text = getString(R.string.app_count, apps.size)
                }
                
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
                binding.progressBar.visibility = View.GONE
            }
        }
    }
    
    private fun showEmptyState(title: String, message: String, showRetry: Boolean = false) {
        binding.recyclerView.visibility = View.GONE
        binding.emptyState.visibility = View.VISIBLE
        binding.tvEmptyTitle.text = title
        binding.tvEmptyMessage.text = message
        binding.btnRetry.visibility = if (showRetry) View.VISIBLE else View.GONE
        binding.btnRetry.setOnClickListener { loadApps() }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
