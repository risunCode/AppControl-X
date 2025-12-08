package com.appcontrolx.ui

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager
import com.appcontrolx.BuildConfig
import com.appcontrolx.R
import com.appcontrolx.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        Log.d("MainActivity", "MainActivity created")
        setupNavigation()
        showWhatsNewIfNeeded()
    }
    
    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        
        binding.bottomNavigation.setupWithNavController(navController)
    }
    
    private fun showWhatsNewIfNeeded() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val lastVersion = prefs.getInt("last_shown_version", 0)
        val currentVersion = BuildConfig.VERSION_CODE
        
        if (lastVersion < currentVersion) {
            showWhatsNewDialog()
            prefs.edit().putInt("last_shown_version", currentVersion).apply()
        }
    }
    
    private fun showWhatsNewDialog() {
        val updateLog = """
            |v1.1.0
            |• View more background ops in app detail
            |• Autostart Manager for all brands
            |• Bug fixes and improvements
            |
            |v1.0.0
            |• Freeze/Unfreeze apps
            |• Force Stop, Clear Cache/Data
            |• Restrict/Allow Background
            |• Batch operations
            |• Activity Launcher
            |• Action Logs with rollback
        """.trimMargin()
        
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.welcome_title))
            .setMessage(getString(R.string.welcome_message, BuildConfig.VERSION_NAME, updateLog))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
