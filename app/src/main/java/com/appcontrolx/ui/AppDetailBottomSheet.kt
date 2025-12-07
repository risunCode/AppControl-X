package com.appcontrolx.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.appcontrolx.R
import com.appcontrolx.databinding.BottomSheetAppDetailBinding
import com.appcontrolx.executor.CommandExecutor
import com.appcontrolx.executor.RootExecutor
import com.appcontrolx.model.AppInfo
import com.appcontrolx.model.ExecutionMode
import com.appcontrolx.rollback.ActionLog
import com.appcontrolx.rollback.RollbackManager

import com.appcontrolx.service.BatteryPolicyManager
import com.appcontrolx.service.PermissionBridge
import com.appcontrolx.utils.SafetyValidator
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


class AppDetailBottomSheet : BottomSheetDialogFragment() {
    
    private var _binding: BottomSheetAppDetailBinding? = null
    private val binding get() = _binding!!
    
    private var appInfo: AppInfo? = null
    private var executor: CommandExecutor? = null
    private var policyManager: BatteryPolicyManager? = null
    private var rollbackManager: RollbackManager? = null
    private var executionMode: ExecutionMode = ExecutionMode.None

    
    var onActionCompleted: (() -> Unit)? = null
    
    companion object {
        const val TAG = "AppDetailBottomSheet"
        private const val ARG_PACKAGE_NAME = "package_name"
        private const val ARG_APP_NAME = "app_name"
        private const val ARG_IS_ENABLED = "is_enabled"
        private const val ARG_IS_SYSTEM = "is_system"
        private const val ARG_IS_RUNNING = "is_running"
        private const val ARG_IS_STOPPED = "is_stopped"
        private const val ARG_IS_BG_RESTRICTED = "is_bg_restricted"
        
        fun newInstance(app: AppInfo): AppDetailBottomSheet {
            return AppDetailBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_PACKAGE_NAME, app.packageName)
                    putString(ARG_APP_NAME, app.appName)
                    putBoolean(ARG_IS_ENABLED, app.isEnabled)
                    putBoolean(ARG_IS_SYSTEM, app.isSystemApp)
                    putBoolean(ARG_IS_RUNNING, app.isRunning)
                    putBoolean(ARG_IS_STOPPED, app.isStopped)
                    putBoolean(ARG_IS_BG_RESTRICTED, app.isBackgroundRestricted)
                }
            }
        }
    }
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetAppDetailBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupExecutor()
        loadAppInfo()
        loadBatteryStatus()
        setupButtons()
    }
    
    private fun setupExecutor() {
        executionMode = PermissionBridge(requireContext()).detectMode()
        if (executionMode is ExecutionMode.Root) {
            executor = RootExecutor()
            policyManager = BatteryPolicyManager(executor!!)
            rollbackManager = RollbackManager(requireContext(), executor!!)
        }
    }
    
    private fun loadAppInfo() {
        val packageName = arguments?.getString(ARG_PACKAGE_NAME) ?: return
        val appName = arguments?.getString(ARG_APP_NAME) ?: packageName
        val isEnabled = arguments?.getBoolean(ARG_IS_ENABLED, true) ?: true
        val isSystem = arguments?.getBoolean(ARG_IS_SYSTEM, false) ?: false
        
        try {
            val pm = requireContext().packageManager
            val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            val appIcon = pm.getApplicationIcon(packageName)
            
            binding.ivAppIcon.setImageDrawable(appIcon)
            binding.tvAppName.text = appName
            binding.tvPackageName.text = packageName
            binding.tvVersion.text = getString(R.string.detail_version_format, 
                packageInfo.versionName ?: "Unknown", packageInfo.longVersionCode)
            
            // App type
            binding.tvAppType.text = if (isSystem) getString(R.string.detail_system_app) 
                                     else getString(R.string.detail_user_app)
            binding.tvAppType.setTextColor(resources.getColor(
                if (isSystem) R.color.status_neutral else R.color.status_positive, null))
            
            // App size
            val appFile = File(packageInfo.applicationInfo.sourceDir)
            binding.tvAppSize.text = formatFileSize(appFile.length())
            
            // Install path
            binding.tvInstallPath.text = packageInfo.applicationInfo.sourceDir
            
            // Target SDK
            binding.tvTargetSdk.text = getString(R.string.detail_sdk_format, 
                packageInfo.applicationInfo.targetSdkVersion)
            
            // Min SDK
            binding.tvMinSdk.text = getString(R.string.detail_sdk_format,
                packageInfo.applicationInfo.minSdkVersion)
            
            // Permissions count
            val permCount = packageInfo.requestedPermissions?.size ?: 0
            binding.tvPermissions.text = getString(R.string.detail_permissions_count, permCount)
            
            // Update button text based on state
            binding.btnToggleEnable.text = if (isEnabled) getString(R.string.action_disable) 
                                           else getString(R.string.action_enable)
            
            // Store for actions
            appInfo = AppInfo(
                packageName = packageName,
                appName = appName,
                icon = appIcon,
                isSystemApp = isSystem,
                isEnabled = isEnabled
            )
            
        } catch (e: Exception) {
            Toast.makeText(context, R.string.error_load_app_info, Toast.LENGTH_SHORT).show()
            dismiss()
        }
    }
    
    private fun loadBatteryStatus() {
        val packageName = arguments?.getString(ARG_PACKAGE_NAME) ?: return
        
        lifecycleScope.launch {
            // Load detailed background state via root commands
            val (runInBg, runAnyInBg) = withContext(Dispatchers.IO) {
                val exec = executor
                if (exec != null) {
                    val runInBgResult = exec.execute("appops get $packageName RUN_IN_BACKGROUND")
                    val runAnyInBgResult = exec.execute("appops get $packageName RUN_ANY_IN_BACKGROUND")
                    
                    val runInBgOutput = runInBgResult.getOrNull() ?: runInBgResult.exceptionOrNull()?.message ?: ""
                    val runAnyInBgOutput = runAnyInBgResult.getOrNull() ?: runAnyInBgResult.exceptionOrNull()?.message ?: ""
                    
                    Pair(
                        parseAppOpsOutput(runInBgOutput),
                        parseAppOpsOutput(runAnyInBgOutput)
                    )
                } else {
                    Pair("No Root", "No Root")
                }
            }
            
            updateBatteryStatusUI(runInBg, runAnyInBg)
        }
    }
    
    private fun parseAppOpsOutput(output: String): String {
        // Output format: "RUN_IN_BACKGROUND: allow" or "RUN_IN_BACKGROUND: allow; time=..."
        val lowerOutput = output.lowercase()
        return when {
            lowerOutput.contains(": ignore") || lowerOutput.contains(":ignore") -> "ignore"
            lowerOutput.contains(": deny") || lowerOutput.contains(":deny") -> "deny"
            lowerOutput.contains(": allow") || lowerOutput.contains(":allow") -> "allow"
            lowerOutput.contains(": default") || lowerOutput.contains(":default") -> "default"
            lowerOutput.contains("no operations") -> "default"
            output.isBlank() -> "error"
            else -> output.trim().take(20) // Show raw output for debug
        }
    }
    
    private fun updateBatteryStatusUI(runInBg: String, runAnyInBg: String) {
        // RUN_IN_BACKGROUND
        binding.tvRunInBg.text = runInBg
        binding.tvRunInBg.setTextColor(resources.getColor(
            if (runInBg == "ignore" || runInBg == "deny") R.color.status_negative 
            else R.color.status_positive, null))
        
        // RUN_ANY_IN_BACKGROUND
        binding.tvRunAnyInBg.text = runAnyInBg
        binding.tvRunAnyInBg.setTextColor(resources.getColor(
            if (runAnyInBg == "ignore" || runAnyInBg == "deny") R.color.status_negative 
            else R.color.status_positive, null))
    }
    
    private fun setupButtons() {
        val packageName = arguments?.getString(ARG_PACKAGE_NAME) ?: return
        val isEnabled = arguments?.getBoolean(ARG_IS_ENABLED, true) ?: true
        val hasMode = executionMode !is ExecutionMode.None
        
        val allowedActions = SafetyValidator.getAllowedActions(packageName)
        val isForceStopOnly = SafetyValidator.isForceStopOnly(packageName)
        val isCritical = SafetyValidator.isCritical(packageName)
        
        // Update freeze/unfreeze button text based on current state
        binding.btnToggleEnable.text = if (isEnabled) getString(R.string.action_freeze) 
                                       else getString(R.string.action_unfreeze)
        
        // Set button enabled states
        setButtonEnabled(binding.btnForceStop, hasMode && !isCritical)
        setButtonEnabled(binding.btnToggleEnable, hasMode && SafetyValidator.AllowedAction.FREEZE in allowedActions)
        setButtonEnabled(binding.btnRestrictBg, hasMode && SafetyValidator.AllowedAction.RESTRICT_BACKGROUND in allowedActions)
        setButtonEnabled(binding.btnAllowBg, hasMode && SafetyValidator.AllowedAction.RESTRICT_BACKGROUND in allowedActions)
        setButtonEnabled(binding.btnClearCache, hasMode && !isCritical)
        setButtonEnabled(binding.btnClearData, hasMode && !isCritical)
        setButtonEnabled(binding.btnUninstall, hasMode && SafetyValidator.AllowedAction.UNINSTALL in allowedActions)
        
        binding.btnForceStop.setOnClickListener {
            if (isCritical) { showProtectedWarning(); return@setOnClickListener }
            executeActionWithLoading(getString(R.string.action_force_stop), { 
                policyManager?.forceStop(appInfo!!.packageName) 
            }, "FORCE_STOP")
        }
        
        binding.btnToggleEnable.setOnClickListener {
            if (isForceStopOnly || isCritical) { showProtectedWarning(); return@setOnClickListener }
            val app = appInfo ?: return@setOnClickListener
            val actionName = if (app.isEnabled) getString(R.string.action_freeze) else getString(R.string.action_unfreeze)
            val logAction = if (app.isEnabled) "FREEZE" else "UNFREEZE"
            executeActionWithLoading(actionName, {
                if (app.isEnabled) policyManager?.freezeApp(app.packageName)
                else policyManager?.unfreezeApp(app.packageName)
            }, logAction)
        }
        
        binding.btnRestrictBg.setOnClickListener {
            if (isForceStopOnly || isCritical) { showProtectedWarning(); return@setOnClickListener }
            executeBackgroundAction(getString(R.string.action_restrict_bg), "RESTRICT_BACKGROUND") {
                policyManager?.restrictBackground(appInfo!!.packageName)
            }
        }
        
        binding.btnAllowBg.setOnClickListener {
            if (isForceStopOnly || isCritical) { showProtectedWarning(); return@setOnClickListener }
            executeBackgroundAction(getString(R.string.action_allow_bg), "ALLOW_BACKGROUND") {
                policyManager?.allowBackground(appInfo!!.packageName)
            }
        }
        
        binding.btnClearCache.setOnClickListener {
            if (isCritical) { showProtectedWarning(); return@setOnClickListener }
            executeActionWithLoading(getString(R.string.action_clear_cache), {
                executor?.execute("pm clear --cache-only ${appInfo!!.packageName}")?.map { }
            }, "CLEAR_CACHE")
        }
        
        binding.btnClearData.setOnClickListener {
            if (isCritical) { showProtectedWarning(); return@setOnClickListener }
            executeActionWithLoading(getString(R.string.action_clear_data), {
                executor?.execute("pm clear ${appInfo!!.packageName}")?.map { }
            }, "CLEAR_DATA")
        }
        
        binding.btnUninstall.setOnClickListener {
            if (isForceStopOnly || isCritical) { showProtectedWarning(); return@setOnClickListener }
            executeActionWithLoading(getString(R.string.action_uninstall), { 
                policyManager?.uninstallApp(appInfo!!.packageName) 
            }, "UNINSTALL")
        }
        
        binding.btnLaunchApp.setOnClickListener { launchApp() }
        binding.btnOpenSettings.setOnClickListener { openAppSettings() }
    }
    
    private fun launchApp() {
        val packageName = appInfo?.packageName ?: return
        try {
            val intent = requireContext().packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                startActivity(intent)
            } else {
                Toast.makeText(context, R.string.error_no_launcher, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, R.string.error_launch_failed, Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showProtectedWarning() {
        Toast.makeText(context, R.string.error_protected_app, Toast.LENGTH_SHORT).show()
    }
    
    private fun executeActionWithLoading(actionName: String, action: suspend () -> Result<Unit>?, logActionName: String? = null) {
        if (policyManager == null) {
            Toast.makeText(context, R.string.error_mode_required_message, Toast.LENGTH_SHORT).show()
            return
        }
        
        binding.progressBar.visibility = View.VISIBLE
        binding.tvActionStatus.visibility = View.VISIBLE
        binding.tvActionStatus.text = getString(R.string.action_processing, actionName)
        setButtonsEnabled(false)
        
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { action() }
                val success = result?.isSuccess == true
                
                // Log action - always log for all action types
                appInfo?.let { app ->
                    rollbackManager?.let { rm ->
                        val finalLogAction = logActionName ?: actionName.uppercase().replace(" ", "_")
                        withContext(Dispatchers.IO) {
                            rm.logAction(ActionLog(
                                action = finalLogAction,
                                packages = listOf(app.packageName),
                                success = success,
                                message = if (success) null else "Failed"
                            ))
                        }
                    }
                }
                
                if (success) {
                    binding.tvActionStatus.text = getString(R.string.action_completed, actionName)
                    binding.tvActionStatus.setTextColor(resources.getColor(R.color.status_positive, null))
                    onActionCompleted?.invoke()
                    
                    // Delay then dismiss
                    kotlinx.coroutines.delay(800)
                    dismiss()
                } else {
                    binding.tvActionStatus.text = getString(R.string.action_failed_name, actionName)
                    binding.tvActionStatus.setTextColor(resources.getColor(R.color.status_negative, null))
                    setButtonsEnabled(true)
                }
            } catch (e: Exception) {
                binding.tvActionStatus.text = e.message ?: getString(R.string.error_unknown)
                binding.tvActionStatus.setTextColor(resources.getColor(R.color.status_negative, null))
                setButtonsEnabled(true)
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }
    
    private fun executeBackgroundAction(actionName: String, logActionName: String, action: suspend () -> Result<Unit>?) {
        if (policyManager == null) {
            Toast.makeText(context, R.string.error_mode_required_message, Toast.LENGTH_SHORT).show()
            return
        }
        
        binding.progressBar.visibility = View.VISIBLE
        binding.tvActionStatus.visibility = View.VISIBLE
        binding.tvActionStatus.text = getString(R.string.action_processing, actionName)
        setButtonsEnabled(false)
        
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { action() }
                val success = result?.isSuccess == true
                
                // Log action
                appInfo?.let { app ->
                    rollbackManager?.let { rm ->
                        withContext(Dispatchers.IO) {
                            rm.logAction(ActionLog(
                                action = logActionName,
                                packages = listOf(app.packageName),
                                success = success,
                                message = if (success) null else "Failed"
                            ))
                        }
                    }
                }
                
                if (success) {
                    // Refresh background status display
                    loadBatteryStatus()
                    
                    binding.tvActionStatus.text = getString(R.string.action_completed, actionName)
                    binding.tvActionStatus.setTextColor(resources.getColor(R.color.status_positive, null))
                    onActionCompleted?.invoke()
                    setButtonsEnabled(true)
                } else {
                    binding.tvActionStatus.text = getString(R.string.action_failed_name, actionName)
                    binding.tvActionStatus.setTextColor(resources.getColor(R.color.status_negative, null))
                    setButtonsEnabled(true)
                }
            } catch (e: Exception) {
                binding.tvActionStatus.text = e.message ?: getString(R.string.error_unknown)
                binding.tvActionStatus.setTextColor(resources.getColor(R.color.status_negative, null))
                setButtonsEnabled(true)
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }
    
    private fun setButtonsEnabled(enabled: Boolean) {
        binding.btnForceStop.isEnabled = enabled
        binding.btnToggleEnable.isEnabled = enabled
        binding.btnRestrictBg.isEnabled = enabled
        binding.btnAllowBg.isEnabled = enabled
        binding.btnClearCache.isEnabled = enabled
        binding.btnClearData.isEnabled = enabled
        binding.btnUninstall.isEnabled = enabled
        binding.btnLaunchApp.isEnabled = enabled
        binding.btnOpenSettings.isEnabled = enabled
    }
    
    private fun setButtonEnabled(button: MaterialButton, enabled: Boolean) {
        button.isEnabled = enabled
        button.alpha = if (enabled) 1.0f else 0.5f
    }
    
    private fun openAppSettings() {
        val packageName = appInfo?.packageName ?: return
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            })
        } catch (e: Exception) {
            Toast.makeText(context, R.string.error_open_settings, Toast.LENGTH_SHORT).show()
        }
    }
    

    
    private fun formatFileSize(size: Long): String = when {
        size >= 1024 * 1024 * 1024 -> String.format("%.2f GB", size / (1024.0 * 1024 * 1024))
        size >= 1024 * 1024 -> String.format("%.2f MB", size / (1024.0 * 1024))
        size >= 1024 -> String.format("%.2f KB", size / 1024.0)
        else -> "$size B"
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
