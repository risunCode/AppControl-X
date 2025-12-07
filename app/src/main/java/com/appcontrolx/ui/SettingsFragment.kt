package com.appcontrolx.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.appcontrolx.R
import com.appcontrolx.databinding.FragmentSettingsBinding
import com.appcontrolx.service.PermissionBridge
import com.appcontrolx.ui.setup.SetupActivity
import com.appcontrolx.utils.Constants
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
        val mode = PermissionBridge().detectMode()
        binding.tvCurrentMode.text = mode.displayName()
        
        binding.btnChangeMode.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_change_mode_title)
                .setMessage(R.string.settings_change_mode_message)
                .setPositiveButton(R.string.confirm_yes) { _, _ ->
                    restartSetup()
                }
                .setNegativeButton(R.string.confirm_no, null)
                .show()
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
