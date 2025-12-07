package com.appcontrolx.ui

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.appcontrolx.R
import com.appcontrolx.databinding.BottomSheetActivityLauncherBinding
import com.appcontrolx.ui.adapter.AppActivityAdapter
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class ActivityLauncherBottomSheet : BottomSheetDialogFragment() {
    
    private var _binding: BottomSheetActivityLauncherBinding? = null
    private val binding get() = _binding
    
    private lateinit var adapter: AppActivityAdapter
    private var allAppGroups: List<AppActivityGroup> = emptyList()
    private var showSystemApps = false
    
    data class ActivityItem(
        val packageName: String,
        val activityName: String,
        val shortName: String,
        val isExported: Boolean
    )
    
    data class AppActivityGroup(
        val packageName: String,
        val appName: String,
        val appIcon: Drawable?,
        val isSystem: Boolean,
        val activities: List<ActivityItem>,
        var isExpanded: Boolean = false
    )
    
    companion object {
        const val TAG = "ActivityLauncherBottomSheet"
        fun newInstance() = ActivityLauncherBottomSheet()
    }
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = BottomSheetActivityLauncherBinding.inflate(inflater, container, false)
        return _binding?.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupChips()
        setupSearch()
        loadActivities()
    }
    
    private fun setupRecyclerView() {
        val b = binding ?: return
        adapter = AppActivityAdapter(
            onAppClick = { group ->
                toggleExpand(group)
            },
            onActivityClick = { activity ->
                launchActivity(activity)
            }
        )
        b.recyclerView.layoutManager = LinearLayoutManager(context)
        b.recyclerView.adapter = adapter
    }
    
    private fun toggleExpand(group: AppActivityGroup) {
        val index = allAppGroups.indexOfFirst { it.packageName == group.packageName }
        if (index >= 0) {
            allAppGroups = allAppGroups.toMutableList().apply {
                this[index] = this[index].copy(isExpanded = !this[index].isExpanded)
            }
            filterActivities()
        }
    }
    
    private fun setupChips() {
        val b = binding ?: return
        b.chipUserApps.isChecked = true
        
        b.chipUserApps.setOnClickListener {
            showSystemApps = false
            b.chipUserApps.isChecked = true
            b.chipSystemApps.isChecked = false
            filterActivities()
        }
        
        b.chipSystemApps.setOnClickListener {
            showSystemApps = true
            b.chipSystemApps.isChecked = true
            b.chipUserApps.isChecked = false
            filterActivities()
        }
    }
    
    private fun setupSearch() {
        val b = binding ?: return
        b.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                filterActivities(newText)
                return true
            }
        })
    }
    
    private fun loadActivities() {
        val b = binding ?: return
        b.progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            allAppGroups = withContext(Dispatchers.IO) {
                val pm = requireContext().packageManager
                val packages = pm.getInstalledPackages(PackageManager.GET_ACTIVITIES)
                
                packages.mapNotNull { pkg ->
                    val isSystem = (pkg.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                    val appName = pkg.applicationInfo.loadLabel(pm).toString()
                    val appIcon = try { pkg.applicationInfo.loadIcon(pm) } catch (e: Exception) { null }
                    
                    val activities = pkg.activities?.mapNotNull { activity ->
                        if (isValidActivity(activity)) {
                            ActivityItem(
                                packageName = pkg.packageName,
                                activityName = activity.name,
                                shortName = activity.name.substringAfterLast("."),
                                isExported = activity.exported
                            )
                        } else null
                    } ?: emptyList()
                    
                    if (activities.isNotEmpty()) {
                        AppActivityGroup(
                            packageName = pkg.packageName,
                            appName = appName,
                            appIcon = appIcon,
                            isSystem = isSystem,
                            activities = activities.sortedBy { it.shortName.lowercase() }
                        )
                    } else null
                }.sortedBy { it.appName.lowercase() }
            }
            
            b.progressBar.visibility = View.GONE
            Timber.d("Loaded ${allAppGroups.size} apps with activities")
            filterActivities()
        }
    }
    
    private fun isValidActivity(activity: ActivityInfo): Boolean {
        val name = activity.name.lowercase()
        
        // Skip internal/test activities
        if (name.contains("test") || name.contains("debug") || name.contains("internal")) {
            return false
        }
        
        // Skip non-activity components
        if (name.endsWith("receiver") || name.endsWith("service") || name.endsWith("provider")) {
            return false
        }
        
        return activity.exported || activity.labelRes != 0
    }
    
    private fun filterActivities(searchQuery: String? = null) {
        var filtered = allAppGroups.filter { it.isSystem == showSystemApps }
        
        // Apply search filter
        if (!searchQuery.isNullOrBlank()) {
            val query = searchQuery.lowercase()
            filtered = filtered.filter { group ->
                group.appName.lowercase().contains(query) ||
                group.packageName.lowercase().contains(query) ||
                group.activities.any { it.shortName.lowercase().contains(query) }
            }
        }
        
        adapter.submitList(filtered)
        
        val totalActivities = filtered.sumOf { it.activities.size }
        binding?.tvCount?.text = getString(R.string.activity_launcher_count, filtered.size, totalActivities)
    }
    
    private fun launchActivity(activity: ActivityItem) {
        try {
            val intent = Intent().apply {
                component = ComponentName(activity.packageName, activity.activityName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            Timber.d("Launched activity: ${activity.activityName}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to launch activity: ${activity.activityName}")
            Toast.makeText(context, R.string.tools_activity_launch_failed, Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
