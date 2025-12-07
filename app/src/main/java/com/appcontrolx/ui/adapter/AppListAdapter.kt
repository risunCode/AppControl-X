package com.appcontrolx.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.appcontrolx.databinding.ItemAppBinding
import com.appcontrolx.model.AppInfo

class AppListAdapter(
    private val onSelectionChanged: (Int) -> Unit
) : ListAdapter<AppInfo, AppListAdapter.AppViewHolder>(AppDiffCallback()) {
    
    private val selectedPackages = mutableSetOf<String>()
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    fun getSelectedApps(): List<AppInfo> = currentList.filter { selectedPackages.contains(it.packageName) }
    
    fun getSelectedCount(): Int = selectedPackages.size
    
    fun selectAll() {
        currentList.forEach { selectedPackages.add(it.packageName) }
        notifyDataSetChanged()
        onSelectionChanged(selectedPackages.size)
    }
    
    fun deselectAll() {
        selectedPackages.clear()
        notifyDataSetChanged()
        onSelectionChanged(0)
    }
    
    fun isAllSelected(): Boolean = selectedPackages.size == currentList.size && currentList.isNotEmpty()
    
    private fun toggleSelection(packageName: String) {
        if (selectedPackages.contains(packageName)) {
            selectedPackages.remove(packageName)
        } else {
            selectedPackages.add(packageName)
        }
        onSelectionChanged(selectedPackages.size)
    }
    
    inner class AppViewHolder(
        private val binding: ItemAppBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(app: AppInfo) {
            binding.apply {
                ivAppIcon.setImageDrawable(app.icon)
                tvAppName.text = app.appName
                tvPackageName.text = app.packageName
                
                // Set checked state without triggering listener
                checkbox.setOnCheckedChangeListener(null)
                checkbox.isChecked = selectedPackages.contains(app.packageName)
                
                // Disabled state
                root.alpha = if (app.isEnabled) 1f else 0.5f
                
                checkbox.setOnCheckedChangeListener { _, _ ->
                    toggleSelection(app.packageName)
                }
                
                root.setOnClickListener {
                    checkbox.isChecked = !checkbox.isChecked
                }
            }
        }
    }
    
    class AppDiffCallback : DiffUtil.ItemCallback<AppInfo>() {
        override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            return oldItem.packageName == newItem.packageName
        }
        
        override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            return oldItem.packageName == newItem.packageName && 
                   oldItem.isEnabled == newItem.isEnabled
        }
    }
}
