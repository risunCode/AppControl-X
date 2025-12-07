package com.appcontrolx.ui

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.appcontrolx.R
import com.appcontrolx.databinding.BottomSheetBatchProgressBinding
import com.appcontrolx.databinding.ItemBatchAppBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class BatchProgressBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetBatchProgressBinding? = null
    private val binding get() = _binding

    private var actionName = ""
    private var appNames = listOf<String>()
    private var packageNames = listOf<String>()
    private var showCacheSize = false
    private var onExecute: (suspend (String) -> Result<Unit>)? = null
    private var onComplete: ((successCount: Int, failCount: Int) -> Unit)? = null
    
    private val adapter = BatchAppAdapter()
    private var executionJob: Job? = null
    private var isCancelled = false

    companion object {
        const val TAG = "BatchProgressBottomSheet"

        fun newInstance(
            actionName: String,
            appNames: List<String>,
            packageNames: List<String>,
            onExecute: suspend (String) -> Result<Unit>,
            onComplete: (successCount: Int, failCount: Int) -> Unit
        ): BatchProgressBottomSheet {
            return BatchProgressBottomSheet().apply {
                this.actionName = actionName
                this.appNames = appNames
                this.packageNames = packageNames
                this.showCacheSize = actionName == "CLEAR_CACHE" || actionName == "CLEAR_DATA"
                this.onExecute = onExecute
                this.onComplete = onComplete
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = BottomSheetBatchProgressBinding.inflate(inflater, container, false)
        return _binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        initListThenExecute()
    }

    private fun setupUI() {
        val b = binding ?: return
        
        b.tvAction.text = actionName.replace("_", " ")
        b.progressBar.max = packageNames.size
        b.progressBar.progress = 0
        b.tvProgress.text = getString(R.string.batch_preparing)
        b.tvCountdown.text = ""
        
        // Setup RecyclerView
        b.recyclerView.layoutManager = LinearLayoutManager(context)
        b.recyclerView.adapter = adapter
        
        b.btnCancel.setOnClickListener {
            isCancelled = true
            executionJob?.cancel()
            dismiss()
        }
    }

    private fun initListThenExecute() {
        executionJob = lifecycleScope.launch {
            // Init list first (with cache size if needed)
            val items = withContext(Dispatchers.IO) {
                appNames.mapIndexed { index, name ->
                    val pkg = packageNames[index]
                    val sizeInfo = if (showCacheSize) getCacheSize(pkg) else null
                    BatchAppItem(name, pkg, BatchStatus.PENDING, sizeInfo)
                }
            }
            
            if (isCancelled) return@launch
            
            adapter.submitList(items)
            
            // Small delay for UI to render list
            delay(100)
            
            // Execute immediately
            executeActions()
        }
    }

    private fun getCacheSize(packageName: String): String? {
        return try {
            val pm = requireContext().packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val dataDir = File(appInfo.dataDir)
            val cacheDir = File(dataDir, "cache")
            
            val cacheSize = if (cacheDir.exists()) getFolderSize(cacheDir) else 0L
            val totalSize = getFolderSize(dataDir)
            
            if (actionName == "CLEAR_CACHE") {
                formatSize(cacheSize)
            } else {
                formatSize(totalSize)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getFolderSize(dir: File): Long {
        var size = 0L
        try {
            dir.walkTopDown().forEach { file ->
                if (file.isFile) size += file.length()
            }
        } catch (e: Exception) {
            // Permission denied or other error
        }
        return size
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    private suspend fun executeActions() {
        val b = binding ?: return
        var successCount = 0
        var failCount = 0

        packageNames.forEachIndexed { index, pkg ->
            if (isCancelled) return

            b.tvProgress.text = getString(R.string.batch_progress, index + 1, packageNames.size)
            
            // Update status to processing & auto scroll
            adapter.updateStatus(index, BatchStatus.PROCESSING)
            b.recyclerView.smoothScrollToPosition(index)
            
            val result = onExecute?.invoke(pkg) ?: Result.failure(Exception("No executor"))
            
            if (result.isSuccess) {
                successCount++
                adapter.updateStatus(index, BatchStatus.SUCCESS)
            } else {
                failCount++
                adapter.updateStatus(index, BatchStatus.FAILED)
            }
            
            b.progressBar.progress = index + 1
            delay(50) // Small delay for UI
        }

        // Complete - invoke callback immediately to save log
        onComplete?.invoke(successCount, failCount)
        
        b.tvProgress.text = if (failCount == 0) {
            getString(R.string.batch_all_success, successCount)
        } else {
            getString(R.string.batch_partial_success, successCount, failCount)
        }
        b.btnCancel.text = getString(R.string.btn_close)
        b.btnCancel.setOnClickListener { dismiss() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // Data classes
    enum class BatchStatus { PENDING, PROCESSING, SUCCESS, FAILED }
    
    data class BatchAppItem(
        val appName: String,
        val packageName: String,
        var status: BatchStatus,
        val sizeInfo: String? = null
    )

    // Adapter
    inner class BatchAppAdapter : RecyclerView.Adapter<BatchAppAdapter.ViewHolder>() {
        private val items = mutableListOf<BatchAppItem>()

        fun submitList(list: List<BatchAppItem>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        fun updateStatus(index: Int, status: BatchStatus) {
            if (index in items.indices) {
                items[index].status = status
                notifyItemChanged(index)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemBatchAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(private val binding: ItemBatchAppBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(item: BatchAppItem) {
                // Show app name with size info if available
                binding.tvAppName.text = if (item.sizeInfo != null && item.status == BatchStatus.PENDING) {
                    "${item.appName} (${item.sizeInfo})"
                } else {
                    item.appName
                }
                
                val (statusText, statusColor) = when (item.status) {
                    BatchStatus.PENDING -> "-" to R.color.on_surface_secondary
                    BatchStatus.PROCESSING -> "..." to R.color.status_neutral
                    BatchStatus.SUCCESS -> getString(R.string.log_status_success) to R.color.status_positive
                    BatchStatus.FAILED -> getString(R.string.log_status_failed) to R.color.status_negative
                }
                
                binding.tvStatus.text = statusText
                binding.tvStatus.setTextColor(resources.getColor(statusColor, null))
            }
        }
    }
}
