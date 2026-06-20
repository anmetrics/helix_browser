package com.helix.browser.ui.adapter

import android.app.DownloadManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.helix.browser.R
import com.helix.browser.databinding.ItemDownloadBinding
import com.helix.browser.ui.DownloadItem

// Production download-row adapter.
//
// Hard rule: bind() sets EVERY mutable view property on every path, with no
// implicit "leave it as the recycled value" branch. RecyclerView reuses
// ViewHolders, so an unset click-listener / text / visibility from a previous
// item must never bleed through. This is the root cause of the original
// "tapping a running row opens the previous download" and "stale size" bugs.
class DownloadsAdapter(
    private val onOpen: (Long) -> Unit,
    private val onCancel: (Long) -> Unit,
    private val onRetry: (DownloadItem) -> Unit,
    private val onRemove: (DownloadItem) -> Unit,
    private val onShare: (Long) -> Unit
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
            val context = binding.root.context
            binding.title.text = item.title

            val isActive = item.status == DownloadManager.STATUS_RUNNING ||
                item.status == DownloadManager.STATUS_PENDING ||
                item.status == DownloadManager.STATUS_PAUSED

            // Status label.
            val statusText = when (item.status) {
                DownloadManager.STATUS_SUCCESSFUL ->
                    context.getString(R.string.download_complete)
                DownloadManager.STATUS_RUNNING -> {
                    val pct = percentOf(item)
                    context.getString(R.string.download_in_progress, pct)
                }
                DownloadManager.STATUS_FAILED ->
                    context.getString(R.string.download_failed)
                DownloadManager.STATUS_PAUSED ->
                    context.getString(R.string.download_paused)
                else ->
                    context.getString(R.string.download_pending)
            }
            val statusColor = when (item.status) {
                DownloadManager.STATUS_SUCCESSFUL -> context.getColor(R.color.green_secure)
                DownloadManager.STATUS_FAILED -> context.getColor(R.color.warning_red)
                DownloadManager.STATUS_RUNNING -> context.getColor(R.color.info_blue)
                else -> context.getColor(R.color.text_secondary)
            }
            binding.status.setTextColor(statusColor)
            binding.status.text = statusText

            // Size line: "downloaded / total" while active, total when known/complete.
            // ALWAYS assign so a recycled row never shows the previous item's size.
            val sizeText = when {
                isActive && item.totalBytes > 0 ->
                    context.getString(
                        R.string.download_size_progress,
                        formatSize(item.downloadedBytes),
                        formatSize(item.totalBytes)
                    )
                item.totalBytes > 0 -> formatSize(item.totalBytes)
                item.downloadedBytes > 0 -> formatSize(item.downloadedBytes)
                else -> ""
            }
            binding.size.text = sizeText
            // Hide the dot separator when there is no size text to flank.
            binding.statusSizeSeparator.visibility =
                if (sizeText.isEmpty()) View.GONE else View.VISIBLE

            // Determinate progress: only meaningful while active AND total known.
            if (isActive && item.totalBytes > 0) {
                binding.progressBar.visibility = View.VISIBLE
                binding.progressBar.isIndeterminate = false
                binding.progressBar.progress = percentOf(item)
            } else {
                binding.progressBar.visibility = View.GONE
            }

            // Row click: open ONLY when successful, otherwise explicitly clear the
            // listener so a recycled "open previous file" listener can't survive.
            if (item.status == DownloadManager.STATUS_SUCCESSFUL) {
                binding.root.setOnClickListener { onOpen(item.id) }
                binding.root.isClickable = true
            } else {
                binding.root.setOnClickListener(null)
                binding.root.isClickable = false
            }

            // Primary inline action button, reset on every bind.
            when (item.status) {
                DownloadManager.STATUS_RUNNING,
                DownloadManager.STATUS_PENDING,
                DownloadManager.STATUS_PAUSED -> {
                    binding.btnAction.visibility = View.VISIBLE
                    binding.btnAction.setImageResource(R.drawable.ic_close)
                    binding.btnAction.contentDescription =
                        context.getString(R.string.download_cancel)
                    binding.btnAction.setOnClickListener { onCancel(item.id) }
                }
                DownloadManager.STATUS_FAILED -> {
                    binding.btnAction.visibility = View.VISIBLE
                    binding.btnAction.setImageResource(R.drawable.ic_refresh)
                    binding.btnAction.contentDescription =
                        context.getString(R.string.download_retry)
                    binding.btnAction.setOnClickListener { onRetry(item) }
                }
                else -> {
                    binding.btnAction.visibility = View.GONE
                    binding.btnAction.setOnClickListener(null)
                }
            }

            // Overflow menu, always re-wired to the currently-bound item.
            binding.btnOverflow.setOnClickListener { anchor ->
                showOverflow(anchor, item)
            }
        }

        private fun showOverflow(anchor: View, item: DownloadItem) {
            val context = anchor.context
            val popup = PopupMenu(context, anchor)
            val menu = popup.menu

            when (item.status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    menu.add(0, MENU_OPEN, 0, context.getString(R.string.download_open))
                    menu.add(0, MENU_SHARE, 1, context.getString(R.string.share))
                    menu.add(0, MENU_REMOVE, 2, context.getString(R.string.download_remove))
                }
                DownloadManager.STATUS_FAILED -> {
                    menu.add(0, MENU_RETRY, 0, context.getString(R.string.download_retry))
                    menu.add(0, MENU_REMOVE, 1, context.getString(R.string.download_remove))
                }
                DownloadManager.STATUS_RUNNING,
                DownloadManager.STATUS_PENDING,
                DownloadManager.STATUS_PAUSED -> {
                    menu.add(0, MENU_CANCEL, 0, context.getString(R.string.download_cancel))
                }
                else -> {
                    menu.add(0, MENU_REMOVE, 0, context.getString(R.string.download_remove))
                }
            }

            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    MENU_OPEN -> { onOpen(item.id); true }
                    MENU_SHARE -> { onShare(item.id); true }
                    MENU_RETRY -> { onRetry(item); true }
                    MENU_CANCEL -> { onCancel(item.id); true }
                    MENU_REMOVE -> { onRemove(item); true }
                    else -> false
                }
            }
            popup.show()
        }

        private fun percentOf(item: DownloadItem): Int {
            if (item.totalBytes <= 0L) return 0
            val pct = (item.downloadedBytes * 100L / item.totalBytes).toInt()
            return pct.coerceIn(0, 100)
        }

        private fun formatSize(bytes: Long): String {
            val b = if (bytes < 0L) 0L else bytes
            return when {
                b >= 1024L * 1024 * 1024 -> "%.2f GB".format(b / (1024.0 * 1024 * 1024))
                b >= 1024L * 1024 -> "%.2f MB".format(b / (1024.0 * 1024))
                b >= 1024L -> "%.2f KB".format(b / 1024.0)
                else -> "$b B"
            }
        }
    }

    companion object {
        private const val MENU_OPEN = 1
        private const val MENU_SHARE = 2
        private const val MENU_RETRY = 3
        private const val MENU_CANCEL = 4
        private const val MENU_REMOVE = 5

        private val DIFF = object : DiffUtil.ItemCallback<DownloadItem>() {
            override fun areItemsTheSame(old: DownloadItem, new: DownloadItem) = old.id == new.id
            override fun areContentsTheSame(old: DownloadItem, new: DownloadItem) = old == new
        }
    }
}
