package com.appcontrolx.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.appcontrolx.R
import com.appcontrolx.databinding.FragmentSettingsBinding
import com.appcontrolx.service.PermissionBridge
import com.appcontrolx.ui.setup.SetupActivity
import com.appcontrolx.utils.Constants
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.appcontrolx.executor.RootExecutor
import com.appcontrolx.model.ExecutionMode
import com.appcontrolx.rollback.RollbackManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SettingsFragment : Fragment() {
    
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    
    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(requireContext()) }
    
    private val themeOptions = arrayOf(
        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
        AppCompatDelegate.MODE_NIGHT_NO,
        AppCompatDelegate.MODE_NIGHT_YES
    )
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupTheme()
        setupCurrentMode()
        setupSafetySettings()
        setupRollbackSettings()
        setupDataSettings()
    }
    
    private fun setupTheme() {
        updateThemeText()
        
        binding.itemTheme.setOnClickListener {
            val currentTheme = prefs.getInt(Constants.PREFS_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            val currentIndex = themeOptions.indexOf(currentTheme).coerceAtLeast(0)
            
            val themeNames = arrayOf(
                getString(R.string.theme_system),
                getString(R.string.theme_light),
                getString(R.string.theme_dark)
            )
            
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_theme)
                .setSingleChoiceItems(themeNames, currentIndex) { dialog, which ->
                    val selectedTheme = themeOptions[which]
                    prefs.edit().putInt(Constants.PREFS_THEME, selectedTheme).apply()
                    AppCompatDelegate.setDefaultNightMode(selectedTheme)
                    updateThemeText()
                    dialog.dismiss()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }
    
    private fun updateThemeText() {
        val currentTheme = prefs.getInt(Constants.PREFS_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        binding.tvCurrentTheme.text = when (currentTheme) {
            AppCompatDelegate.MODE_NIGHT_NO -> getString(R.string.theme_light)
            AppCompatDelegate.MODE_NIGHT_YES -> getString(R.string.theme_dark)
            else -> getString(R.string.theme_system)
        }
    }
    

    
    private fun setupCurrentMode() {
        updateModeDisplay()
        
        binding.btnChangeMode.setOnClickListener {
            showModeSelectionDialog()
        }
    }
    
    private fun updateModeDisplay() {
        val mode = PermissionBridge(requireContext()).detectMode()
        binding.tvCurrentMode.text = mode.displayName()
    }
    
    private fun showModeSelectionDialog() {
        val permissionBridge = PermissionBridge(requireContext())
        
        val modes = arrayOf(
            getString(R.string.mode_root),
            getString(R.string.mode_shizuku),
            getString(R.string.mode_view_only)
        )
        val modeValues = arrayOf(
            Constants.MODE_ROOT,
            Constants.MODE_SHIZUKU,
            Constants.MODE_NONE
        )
        
        val currentMode = prefs.getString(Constants.PREFS_EXECUTION_MODE, Constants.MODE_NONE)
        val currentIndex = modeValues.indexOf(currentMode).coerceAtLeast(0)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_change_mode_title)
            .setSingleChoiceItems(modes, currentIndex) { dialog, which ->
                val selectedMode = modeValues[which]
                
                when (selectedMode) {
                    Constants.MODE_ROOT -> {
                        dialog.dismiss()
                        checkAndSetRootMode(permissionBridge)
                    }
                    Constants.MODE_SHIZUKU -> {
                        if (!permissionBridge.isShizukuReady()) {
                            Toast.makeText(context, R.string.error_shizuku_not_available, Toast.LENGTH_SHORT).show()
                            return@setSingleChoiceItems
                        }
                        applyModeAndRestart(selectedMode)
                        dialog.dismiss()
                    }
                    else -> {
                        applyModeAndRestart(selectedMode)
                        dialog.dismiss()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    
    private fun checkAndSetRootMode(permissionBridge: PermissionBridge) {
        // Show checking dialog
        val progressDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.mode_root)
            .setMessage(R.string.btn_checking)
            .setCancelable(false)
            .create()
        progressDialog.show()
        
        lifecycleScope.launch {
            val hasRoot = withContext(Dispatchers.IO) {
                permissionBridge.checkRootNow()
            }
            
            progressDialog.dismiss()
            
            if (hasRoot) {
                Toast.makeText(context, R.string.root_granted, Toast.LENGTH_SHORT).show()
                applyModeAndRestart(Constants.MODE_ROOT)
            } else {
                Toast.makeText(context, R.string.error_root_not_available, Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun applyModeAndRestart(mode: String) {
        prefs.edit().putString(Constants.PREFS_EXECUTION_MODE, mode).apply()
        updateModeDisplay()
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_mode_changed)
            .setMessage(R.string.settings_restart_required)
            .setPositiveButton(R.string.settings_restart_now) { _, _ ->
                restartApp()
            }
            .setNegativeButton(R.string.settings_restart_later, null)
            .show()
    }
    
    private fun restartApp() {
        val intent = requireContext().packageManager
            .getLaunchIntentForPackage(requireContext().packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            requireActivity().finish()
            Runtime.getRuntime().exit(0)
        }
    }
    
    private fun setupSafetySettings() {
        binding.switchConfirmActions.isChecked = prefs.getBoolean(Constants.PREFS_CONFIRM_ACTIONS, true)
        binding.switchConfirmActions.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(Constants.PREFS_CONFIRM_ACTIONS, isChecked).apply()
        }
        
        binding.switchProtectSystem.isChecked = prefs.getBoolean(Constants.PREFS_PROTECT_SYSTEM, true)
        binding.switchProtectSystem.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(Constants.PREFS_PROTECT_SYSTEM, isChecked).apply()
        }
    }
    
    private fun setupRollbackSettings() {
        binding.switchAutoSnapshot.isChecked = prefs.getBoolean(Constants.PREFS_AUTO_SNAPSHOT, true)
        binding.switchAutoSnapshot.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(Constants.PREFS_AUTO_SNAPSHOT, isChecked).apply()
        }
        
        updateSnapshotCount()
        updateLogCount()
        
        binding.itemViewLogs.setOnClickListener {
            val bottomSheet = ActionLogBottomSheet.newInstance()
            bottomSheet.onLogCleared = { updateLogCount() }
            bottomSheet.show(childFragmentManager, ActionLogBottomSheet.TAG)
        }
        
        binding.itemClearSnapshots.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_clear_snapshots)
                .setMessage(R.string.settings_clear_snapshots_confirm)
                .setPositiveButton(R.string.confirm_yes) { _, _ ->
                    clearSnapshots()
                }
                .setNegativeButton(R.string.confirm_no, null)
                .show()
        }
    }
    
    private fun updateLogCount() {
        val mode = PermissionBridge(requireContext()).detectMode()
        if (mode is ExecutionMode.Root) {
            val executor = RootExecutor()
            val rm = RollbackManager(requireContext(), executor)
            val count = rm.getLogCount()
            binding.tvLogCount.text = getString(R.string.settings_log_count, count)
        } else {
            binding.tvLogCount.text = getString(R.string.log_no_mode)
        }
    }
    
    private fun setupDataSettings() {
        binding.itemResetSetup.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_reset_setup)
                .setMessage(R.string.settings_reset_setup_confirm)
                .setPositiveButton(R.string.confirm_yes) { _, _ ->
                    restartSetup()
                }
                .setNegativeButton(R.string.confirm_no, null)
                .show()
        }
    }
    
    private fun updateSnapshotCount() {
        val snapshotDir = File(requireContext().filesDir, "snapshots")
        val count = snapshotDir.listFiles()?.size ?: 0
        binding.tvSnapshotCount.text = getString(R.string.settings_snapshot_count, count)
    }
    
    private fun clearSnapshots() {
        val snapshotDir = File(requireContext().filesDir, "snapshots")
        snapshotDir.deleteRecursively()
        updateSnapshotCount()
        Toast.makeText(context, R.string.settings_snapshots_cleared, Toast.LENGTH_SHORT).show()
    }
    
    private fun restartSetup() {
        requireContext().getSharedPreferences(Constants.PREFS_NAME, 0)
            .edit().putBoolean(Constants.PREFS_SETUP_COMPLETE, false).apply()
        
        startActivity(Intent(requireContext(), SetupActivity::class.java))
        requireActivity().finish()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
