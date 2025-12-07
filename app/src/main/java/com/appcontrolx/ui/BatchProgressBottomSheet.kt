package com.appcontrolx.ui

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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BatchProgressBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetBatchProgressBinding? = null
    private val binding get() = _binding

    private var actionName = ""
    private var appNames = listOf<String>()
    private var packageNames = listOf<String>()
    private var onExecute: (suspend (String) -> Result<Unit>)? = null
    private var onComplete: (() -> Unit)? = null
    
    private val adapter = BatchAppAdapter()
    private var executionJob: Job? = null
    private var isCancelled = false

    companion object {
        const val TAG = "BatchProgressBottomSheet"
        private const val COUNTDOWN_SECONDS = 3

        fun newInstance(
            actionName: String,
            appNames: List<String>,
            packageNames: List<String>,
            onExecute: suspend (String) -> Result<Unit>,
            onComplete: () -> Unit
        ): BatchProgressBottomSheet {
            return BatchProgressBottomSheet().apply {
                this.actionName = actionName
                this.appNames = appNames
                this.packageNames = packageNames
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
        startCountdown()
    }

    private fun setupUI() {
        val b = binding ?: return
        
        b.tvAction.text = actionName.replace("_", " ")
        b.progressBar.max = packageNames.size
        b.progressBar.progress = 0
        b.tvProgress.text = getString(R.string.batch_waiting)
        
        // Setup RecyclerView with app list
        adapter.submitList(appNames.mapIndexed { index, name -> 
            BatchAppItem(name, packageNames[index], BatchStatus.PENDING)
        })
        b.recyclerView.layoutManager = LinearLayoutManager(context)
        b.recyclerView.adapter = adapter
        
        b.btnCancel.setOnClickListener {
            isCancelled = true
            executionJob?.cancel()
            dismiss()
        }
    }

    private fun startCountdown() {
        executionJob = lifecycleScope.launch {
            // Countdown
            for (i in COUNTDOWN_SECONDS downTo 1) {
                if (isCancelled) return@launch
                binding?.tvCountdown?.text = getString(R.string.batch_countdown, i)
                delay(1000)
            }
            
            binding?.tvCountdown?.text = ""
            executeActions()
        }
    }

    private suspend fun executeActions() {
        val b = binding ?: return
        var successCount = 0
        var failCount = 0

        packageNames.forEachIndexed { index, pkg ->
            if (isCancelled) return

            b.tvProgress.text = getString(R.string.batch_progress, index + 1, packageNames.size)
            
            // Update status to processing
            adapter.updateStatus(index, BatchStatus.PROCESSING)
            
            val result = onExecute?.invoke(pkg) ?: Result.failure(Exception("No executor"))
            
            if (result.isSuccess) {
                successCount++
                adapter.updateStatus(index, BatchStatus.SUCCESS)
            } else {
                failCount++
                adapter.updateStatus(index, BatchStatus.FAILED)
            }
            
            b.progressBar.progress = index + 1
            delay(100) // Small delay for UI
        }

        // Complete
        b.tvProgress.text = if (failCount == 0) {
            getString(R.string.batch_all_success, successCount)
        } else {
            getString(R.string.batch_partial_success, successCount, failCount)
        }
        b.btnCancel.text = getString(R.string.btn_close)
        b.btnCancel.setOnClickListener {
            onComplete?.invoke()
            dismiss()
        }
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
        var status: BatchStatus
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
                binding.tvAppName.text = item.appName
                
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
