package com.helix.browser.ui.adapter

import android.app.DownloadManager
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.helix.browser.R
import com.helix.browser.databinding.ItemDownloadBinding
import com.helix.browser.ui.DownloadItem

class DownloadsAdapter(
    private val onItemClick: (Long) -> Unit
) : ListAdapter<DownloadItem, DownloadsAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDownloadBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemDownloadBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: DownloadItem) {
            binding.title.text = item.title
            val context = binding.root.context
            val statusText = when (item.status) {
                DownloadManager.STATUS_SUCCESSFUL ->
                    context.getString(R.string.download_complete)
                DownloadManager.STATUS_RUNNING -> {
                    val pct = if (item.totalBytes > 0) (item.downloadedBytes * 100 / item.totalBytes).toInt() else 0
                    context.getString(R.string.download_in_progress, pct)
                }
                DownloadManager.STATUS_FAILED ->
                    context.getString(R.string.download_failed)
                DownloadManager.STATUS_PAUSED ->
                    context.getString(R.string.download_paused)
                else ->
                    context.getString(R.string.download_pending)
            }
            // Status color
            val statusColor = when (item.status) {
                DownloadManager.STATUS_SUCCESSFUL -> context.getColor(R.color.green_secure)
                DownloadManager.STATUS_FAILED -> context.getColor(R.color.warning_red)
                DownloadManager.STATUS_RUNNING -> context.getColor(R.color.info_blue)
                else -> context.getColor(R.color.text_secondary)
            }
            binding.status.setTextColor(statusColor)
            binding.status.text = statusText
            if (item.totalBytes > 0) {
                binding.size.text = formatSize(item.totalBytes)
            }
            if (item.status == DownloadManager.STATUS_SUCCESSFUL) {
                binding.root.setOnClickListener { onItemClick(item.id) }
            }
        }

        private fun formatSize(bytes: Long): String {
            return when {
                bytes >= 1024 * 1024 * 1024 -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
                bytes >= 1024 * 1024 -> "%.2f MB".format(bytes / (1024.0 * 1024))
                bytes >= 1024 -> "%.2f KB".format(bytes / 1024.0)
                else -> "$bytes B"
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<DownloadItem>() {
            override fun areItemsTheSame(old: DownloadItem, new: DownloadItem) = old.id == new.id
            override fun areContentsTheSame(old: DownloadItem, new: DownloadItem) = old == new
        }
    }
}
