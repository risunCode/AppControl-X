package com.appcontrolx.ui.adapter

import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.appcontrolx.R
import com.appcontrolx.databinding.ItemAppBinding
import com.appcontrolx.model.AppInfo

class AppListAdapter(
    private val onSelectionChanged: (Int) -> Unit,
    private val onInfoClick: (AppInfo) -> Unit
) : ListAdapter<AppInfo, AppListAdapter.AppViewHolder>(AppDiffCallback()) {
    
    private val selectedPackages = mutableSetOf<String>()
    private var isSelectionMode = false
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    fun getSelectedApps(): List<AppInfo> = currentList.filter { selectedPackages.contains(it.packageName) }
    
    fun getSelectedCount(): Int = selectedPackages.size
    
    fun isInSelectionMode(): Boolean = isSelectionMode
    
    fun selectAll() {
        isSelectionMode = true
        currentList.forEach { selectedPackages.add(it.packageName) }
        notifyDataSetChanged()
        onSelectionChanged(selectedPackages.size)
    }
    
    fun deselectAll() {
        selectedPackages.clear()
        isSelectionMode = false
        notifyDataSetChanged()
        onSelectionChanged(0)
    }
    
    fun isAllSelected(): Boolean = selectedPackages.size == currentList.size && currentList.isNotEmpty()
    
    fun getAppName(packageName: String): String? {
        return currentList.find { it.packageName == packageName }?.appName
    }
    
    private fun toggleSelection(packageName: String) {
        if (selectedPackages.contains(packageName)) {
            selectedPackages.remove(packageName)
            if (selectedPackages.isEmpty()) {
                isSelectionMode = false
            }
        } else {
            selectedPackages.add(packageName)
            isSelectionMode = true
        }
        onSelectionChanged(selectedPackages.size)
    }
    
    private fun startSelection(packageName: String) {
        isSelectionMode = true
        selectedPackages.add(packageName)
        notifyDataSetChanged()
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
                
                val isSelected = selectedPackages.contains(app.packageName)
                
                // Visual selection state - change card appearance
                cardApp.isChecked = isSelected
                cardApp.strokeWidth = if (isSelected) 
                    root.context.resources.getDimensionPixelSize(R.dimen.stroke_selected) 
                    else root.context.resources.getDimensionPixelSize(R.dimen.stroke_normal)
                cardApp.strokeColor = if (isSelected)
                    root.context.getColor(R.color.primary)
                    else root.context.getColor(R.color.outline)
                cardApp.setCardBackgroundColor(
                    if (isSelected) root.context.getColor(R.color.selected_bg)
                    else root.context.getColor(R.color.surface)
                )
                
                // Disabled/Frozen state - dim the item
                root.alpha = if (app.isEnabled) 1f else 0.6f
                
                // Status badges
                val hasAnyBadge = app.isFrozen || app.isRunning || app.isStopped || app.isBackgroundRestricted
                statusContainer.visibility = if (hasAnyBadge) View.VISIBLE else View.GONE
                
                tvStatusFrozen.visibility = if (app.isFrozen) View.VISIBLE else View.GONE
                tvStatusRunning.visibility = if (app.isRunning && !app.isFrozen) View.VISIBLE else View.GONE
                tvStatusStopped.visibility = if (app.isStopped && !app.isFrozen && !app.isRunning) View.VISIBLE else View.GONE
                tvStatusRestricted.visibility = if (app.isBackgroundRestricted) View.VISIBLE else View.GONE
                
                // Tap: if in selection mode -> toggle, else -> show info
                cardApp.setOnClickListener {
                    if (isSelectionMode) {
                        toggleSelection(app.packageName)
                        notifyItemChanged(bindingAdapterPosition)
                    } else {
                        onInfoClick(app)
                    }
                }
                
                // Long press: start selection mode
                cardApp.setOnLongClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    if (!isSelectionMode) {
                        startSelection(app.packageName)
                    } else {
                        toggleSelection(app.packageName)
                        notifyItemChanged(bindingAdapterPosition)
                    }
                    true
                }
                
                // Info button always shows info
                btnInfo.setOnClickListener {
                    onInfoClick(app)
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
