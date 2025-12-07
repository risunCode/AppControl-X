package com.appcontrolx.ui.adapter

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.appcontrolx.R
import com.appcontrolx.databinding.ItemAppActivityGroupBinding
import com.appcontrolx.databinding.ItemActivitySimpleBinding
import com.appcontrolx.ui.ActivityLauncherBottomSheet.ActivityItem
import com.appcontrolx.ui.ActivityLauncherBottomSheet.AppActivityGroup

class AppActivityAdapter(
    private val onAppClick: (AppActivityGroup) -> Unit,
    private val onActivityClick: (ActivityItem) -> Unit
) : ListAdapter<AppActivityGroup, AppActivityAdapter.AppViewHolder>(DiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppActivityGroupBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return AppViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class AppViewHolder(
        private val binding: ItemAppActivityGroupBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        private val activityAdapter = ActivitySimpleAdapter { activity ->
            onActivityClick(activity)
        }
        
        init {
            binding.rvActivities.layoutManager = LinearLayoutManager(binding.root.context)
            binding.rvActivities.adapter = activityAdapter
        }
        
        fun bind(group: AppActivityGroup) {
            if (group.appIcon != null) {
                binding.ivAppIcon.setImageDrawable(group.appIcon)
            }
            
            binding.tvAppName.text = group.appName
            binding.tvActivityCount.text = "${group.activities.size} activities"
            binding.ivExpand.rotation = if (group.isExpanded) 180f else 0f
            binding.rvActivities.visibility = if (group.isExpanded) View.VISIBLE else View.GONE
            
            if (group.isExpanded) {
                activityAdapter.submitList(group.activities)
            }
            
            binding.headerLayout.setOnClickListener {
                onAppClick(group)
            }
        }
    }
    
    class DiffCallback : DiffUtil.ItemCallback<AppActivityGroup>() {
        override fun areItemsTheSame(oldItem: AppActivityGroup, newItem: AppActivityGroup) =
            oldItem.packageName == newItem.packageName
        
        override fun areContentsTheSame(oldItem: AppActivityGroup, newItem: AppActivityGroup) =
            oldItem.packageName == newItem.packageName && oldItem.isExpanded == newItem.isExpanded
    }
}

class ActivitySimpleAdapter(
    private val onItemClick: (ActivityItem) -> Unit
) : ListAdapter<ActivityItem, ActivitySimpleAdapter.ViewHolder>(DiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemActivitySimpleBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class ViewHolder(
        private val binding: ItemActivitySimpleBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(item: ActivityItem) {
            binding.tvActivityName.text = item.shortName
            
            // Full activity path (e.g., com.android.settings/.Settings$ManageExternalSourcesActivity)
            val fullPath = item.activityName
            binding.tvFullPath.text = fullPath
            
            binding.tvExported.visibility = if (item.isExported) View.VISIBLE else View.GONE
            
            // Tap to launch
            binding.root.setOnClickListener {
                onItemClick(item)
            }
            
            // Long press to copy full path
            binding.root.setOnLongClickListener { view ->
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                val clipboard = view.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Activity", fullPath))
                Toast.makeText(view.context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
                true
            }
        }
    }
    
    class DiffCallback : DiffUtil.ItemCallback<ActivityItem>() {
        override fun areItemsTheSame(oldItem: ActivityItem, newItem: ActivityItem) =
            oldItem.activityName == newItem.activityName
        
        override fun areContentsTheSame(oldItem: ActivityItem, newItem: ActivityItem) =
            oldItem == newItem
    }
}
