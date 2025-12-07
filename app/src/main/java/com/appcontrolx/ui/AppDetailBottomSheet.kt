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
import com.appcontrolx.service.BackgroundStatus
import com.appcontrolx.service.BatteryPolicyManager
import com.appcontrolx.service.PermissionBridge
import com.appcontrolx.utils.SafetyValidator
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppDetailBottomSheet : BottomSheetDialogFragment() {
    
    private var _binding: BottomSheetAppDetailBinding? = null
    private val binding get() = _binding!!
    
    private var appInfo: AppInfo? = null
    private var executor: CommandExecutor? = null
    private var policyManager: BatteryPolicyManager? = null
    private var executionMode: ExecutionMode = ExecutionMode.None
    private var currentBgStatus: BackgroundStatus = BackgroundStatus.DEFAULT
    
    var onActionCompleted: (() -> Unit)? = null
    
    companion object {
        const val TAG = "AppDetailBottomSheet"
        private const val ARG_PACKAGE_NAME = "package_name"
        private const val ARG_APP_NAME = "app_name"
        private const val ARG_IS_ENABLED = "is_enabled"
        private const val ARG_IS_SYSTEM = "is_system"
        
        fun newInstance(app: AppInfo): AppDetailBottomSheet {
            return AppDetailBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_PACKAGE_NAME, app.packageName)
                    putString(ARG_APP_NAME, app.appName)
                    putBoolean(ARG_IS_ENABLED, app.isEnabled)
                    putBoolean(ARG_IS_SYSTEM, app.isSystemApp)
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
        executionMode = PermissionBridge().detectMode()
        if (executionMode is ExecutionMode.Root) {
            executor = RootExecutor()
            policyManager = BatteryPolicyManager(executor!!)
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
            
            // Dates
            val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            binding.tvInstallDate.text = dateFormat.format(Date(packageInfo.firstInstallTime))
            binding.tvUpdateDate.text = dateFormat.format(Date(packageInfo.lastUpdateTime))
            
            // Status
            binding.tvStatus.text = if (isEnabled) getString(R.string.status_enabled) 
                                    else getString(R.string.status_disabled)
            binding.tvStatus.setTextColor(resources.getColor(
                if (isEnabled) R.color.status_positive else R.color.status_negative, null))
            
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
            currentBgStatus = withContext(Dispatchers.IO) {
                policyManager?.getBackgroundStatus(packageName) ?: BackgroundStatus.DEFAULT
            }
            
            updateBatteryStatusUI()
        }
    }
    
    private fun updateBatteryStatusUI() {
        val (text, color) = when (currentBgStatus) {
            BackgroundStatus.RESTRICTED -> Pair(
                getString(R.string.status_restricted),
                R.color.status_negative
            )
            BackgroundStatus.ALLOWED -> Pair(
                getString(R.string.status_allowed),
                R.color.status_positive
            )
            BackgroundStatus.DEFAULT -> Pair(
                getString(R.string.status_default),
                R.color.status_neutral
            )
        }
        
        binding.tvBatteryStatus.text = text
        binding.tvBatteryStatus.setTextColor(resources.getColor(color, null))
        
        // Update button text
        binding.btnToggleBackground.text = if (currentBgStatus == BackgroundStatus.RESTRICTED) {
            getString(R.string.action_allow_bg)
        } else {
            getString(R.string.action_restrict_bg)
        }
    }
    
    private fun setupButtons() {
        val packageName = arguments?.getString(ARG_PACKAGE_NAME) ?: return
        val hasMode = executionMode !is ExecutionMode.None
        
        val allowedActions = SafetyValidator.getAllowedActions(packageName)
        val isForceStopOnly = SafetyValidator.isForceStopOnly(packageName)
        val isCritical = SafetyValidator.isCritical(packageName)
        
        binding.btnForceStop.isEnabled = hasMode && !isCritical
        binding.btnToggleEnable.isEnabled = hasMode && SafetyValidator.AllowedAction.FREEZE in allowedActions
        binding.btnToggleBackground.isEnabled = hasMode && SafetyValidator.AllowedAction.RESTRICT_BACKGROUND in allowedActions
        binding.btnUninstall.isEnabled = hasMode && SafetyValidator.AllowedAction.UNINSTALL in allowedActions
        
        if (isForceStopOnly) {
            binding.btnToggleEnable.alpha = 0.5f
            binding.btnToggleBackground.alpha = 0.5f
            binding.btnUninstall.alpha = 0.5f
        }
        
        binding.btnForceStop.setOnClickListener {
            if (isCritical) { showProtectedWarning(); return@setOnClickListener }
            executeActionWithLoading(getString(R.string.action_force_stop)) { 
                policyManager?.forceStop(appInfo!!.packageName) 
            }
        }
        
        binding.btnToggleEnable.setOnClickListener {
            if (isForceStopOnly || isCritical) { showProtectedWarning(); return@setOnClickListener }
            val app = appInfo ?: return@setOnClickListener
            val actionName = if (app.isEnabled) getString(R.string.action_disable) else getString(R.string.action_enable)
            executeActionWithLoading(actionName) {
                if (app.isEnabled) policyManager?.freezeApp(app.packageName)
                else policyManager?.unfreezeApp(app.packageName)
            }
        }
        
        binding.btnToggleBackground.setOnClickListener {
            if (isForceStopOnly || isCritical) { showProtectedWarning(); return@setOnClickListener }
            val actionName = if (currentBgStatus == BackgroundStatus.RESTRICTED) 
                getString(R.string.action_allow_bg) else getString(R.string.action_restrict_bg)
            executeActionWithLoading(actionName) {
                if (currentBgStatus == BackgroundStatus.RESTRICTED) 
                    policyManager?.allowBackground(appInfo!!.packageName)
                else 
                    policyManager?.restrictBackground(appInfo!!.packageName)
            }
        }
        
        binding.btnUninstall.setOnClickListener {
            if (isForceStopOnly || isCritical) { showProtectedWarning(); return@setOnClickListener }
            executeActionWithLoading(getString(R.string.action_uninstall)) { 
                policyManager?.uninstallApp(appInfo!!.packageName) 
            }
        }
        
        binding.btnOpenSettings.setOnClickListener { openAppSettings() }
    }
    
    private fun showProtectedWarning() {
        Toast.makeText(context, R.string.error_protected_app, Toast.LENGTH_SHORT).show()
    }
    
    private fun executeActionWithLoading(actionName: String, action: suspend () -> Result<Unit>?) {
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
                if (result?.isSuccess == true) {
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
    
    private fun setButtonsEnabled(enabled: Boolean) {
        binding.btnForceStop.isEnabled = enabled
        binding.btnToggleEnable.isEnabled = enabled
        binding.btnToggleBackground.isEnabled = enabled
        binding.btnUninstall.isEnabled = enabled
        binding.btnOpenSettings.isEnabled = enabled
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
