package com.appcontrolx.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager
import com.appcontrolx.BuildConfig
import com.appcontrolx.R
import com.appcontrolx.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        Timber.d("MainActivity created")
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
        val whatsNew = """
            |• MVVM Architecture with Hilt DI
            |• Crash reporting with Firebase
            |• Optimized release build
            |• Smart app caching
            |• Status badges
            |• Tools tab with hidden settings
            |• Activity Launcher
            |• Batch operations
            |• Enhanced security
        """.trimMargin()
        
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.whats_new_title, BuildConfig.VERSION_NAME))
            .setMessage(whatsNew)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
