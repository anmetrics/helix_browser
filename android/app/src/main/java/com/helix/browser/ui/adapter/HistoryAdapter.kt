package com.helix.browser.ui.adapter

import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.helix.browser.R
import com.helix.browser.data.HistoryItem
import com.helix.browser.databinding.ItemHistoryBinding
import com.helix.browser.utils.UrlUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Renders browsing history grouped into Today / Yesterday / "Mon, Jan 12" date
// sections (like flagship browsers) instead of a flat timestamp-per-row list, and
// shows a clean host (UrlUtils.getDisplayUrl) as the secondary line with a short
// relative time instead of the raw full URL.
//
// The public contract (constructor + submitList(List<HistoryItem>)) is preserved
// so callers keep submitting a flat, timestamp-DESC ordered List<HistoryItem>; the
// adapter derives the section headers internally.
class HistoryAdapter(
    private val onItemClick: (HistoryItem) -> Unit,
    private val onDeleteClick: (HistoryItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // Internal row model: either a date section header or a history entry.
    private sealed class Row {
        data class Header(val label: String, val dayKey: Long) : Row()
        data class Entry(val item: HistoryItem) : Row()
    }

    private val differ = AsyncListDiffer(this, DIFF)

    // Accepts the flat list from callers and expands it into header + entry rows.
    // The input is expected to be ordered by timestamp DESC (as produced by
    // HistoryDao); grouping relies on that ordering for correct section breaks but
    // tolerates out-of-order input (a new header is emitted whenever the calendar
    // day changes from the previous row).
    fun submitList(items: List<HistoryItem>) {
        differ.submitList(buildRows(items))
    }

    override fun getItemViewType(position: Int): Int = when (differ.currentList[position]) {
        is Row.Header -> VIEW_TYPE_HEADER
        is Row.Entry -> VIEW_TYPE_ENTRY
    }

    override fun getItemCount(): Int = differ.currentList.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_HEADER) {
            HeaderViewHolder(createHeaderView(parent))
        } else {
            val binding = ItemHistoryBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            EntryViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = differ.currentList[position]) {
            is Row.Header -> (holder as HeaderViewHolder).bind(row.label)
            is Row.Entry -> (holder as EntryViewHolder).bind(row.item)
        }
    }

    // Build a section header TextView programmatically (no dedicated layout
    // resource) so the adapter stays self-contained. Styling mirrors the app's
    // secondary-text section style used elsewhere.
    private fun createHeaderView(parent: ViewGroup): TextView {
        val ctx = parent.context
        val res = ctx.resources
        val density = res.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()
        return TextView(ctx).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(dp(16), dp(16), dp(16), dp(6))
            setTextColor(ctx.getColor(R.color.text_secondary))
            textSize = 13f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }
    }

    private inner class HeaderViewHolder(private val text: TextView) :
        RecyclerView.ViewHolder(text) {
        fun bind(label: String) {
            text.text = label
        }
    }

    private inner class EntryViewHolder(private val binding: ItemHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: HistoryItem) {
            binding.title.text = if (item.title.isNotBlank()) {
                item.title
            } else {
                UrlUtils.getDisplayUrl(item.url)
            }
            // Show a clean host instead of the raw full URL so long URLs do not
            // truncate to noise.
            binding.url.text = UrlUtils.getDisplayUrl(item.url)
            // Headers carry the date; per-row time is just the wall-clock time.
            binding.time.text = timeFormat.format(Date(item.timestamp))
            binding.root.setOnClickListener { onItemClick(item) }
            binding.btnDelete.setOnClickListener { onDeleteClick(item) }
        }
    }

    // Group consecutive entries by calendar day, inserting a header row before
    // each new day. dayKey is a stable identifier (days since epoch in the local
    // time zone) used for diffing and to detect day boundaries.
    private fun buildRows(items: List<HistoryItem>): List<Row> {
        if (items.isEmpty()) return emptyList()
        val rows = ArrayList<Row>(items.size + 8)
        var lastDayKey = Long.MIN_VALUE
        for (item in items) {
            val dayKey = dayKeyFor(item.timestamp)
            if (dayKey != lastDayKey) {
                rows.add(Row.Header(sectionLabel(item.timestamp), dayKey))
                lastDayKey = dayKey
            }
            rows.add(Row.Entry(item))
        }
        return rows
    }

    // Number of whole local days between the epoch and the given timestamp. Using
    // the local time-zone offset (not raw UTC division) so "Today"/"Yesterday"
    // boundaries align with the user's midnight.
    private fun dayKeyFor(timestamp: Long): Long {
        val tzOffset = TimeZone.getDefault().getOffset(timestamp).toLong()
        return (timestamp + tzOffset) / DAY_MS
    }

    // Human-readable section label: Today / Yesterday / a formatted date.
    private fun sectionLabel(timestamp: Long): String {
        val app = appContextOrNull()
        val itemKey = dayKeyFor(timestamp)
        val todayKey = dayKeyFor(System.currentTimeMillis())
        return when {
            itemKey == todayKey -> app?.getString(R.string.history_section_today) ?: "Today"
            itemKey == todayKey - 1 ->
                app?.getString(R.string.history_section_yesterday) ?: "Yesterday"
            else -> sectionDateFormat.format(Date(timestamp))
        }
    }

    // The app context is needed to resolve localized Today/Yesterday strings. We
    // pull it lazily from the static holder; if unavailable we fall back to the
    // English literals so a header is never left blank.
    private fun appContextOrNull() = try {
        com.helix.browser.HelixApp.instance
    } catch (e: Exception) {
        null
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ENTRY = 1
        private const val DAY_MS = 24L * 60L * 60L * 1000L

        // Per-row wall-clock time (e.g. "14:05").
        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        // Date used for older section headers (e.g. "Mon, Jan 12").
        private val sectionDateFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())

        private val DIFF = object : DiffUtil.ItemCallback<Row>() {
            override fun areItemsTheSame(old: Row, new: Row): Boolean = when {
                old is Row.Header && new is Row.Header -> old.dayKey == new.dayKey
                old is Row.Entry && new is Row.Entry -> old.item.id == new.item.id
                else -> false
            }

            override fun areContentsTheSame(old: Row, new: Row): Boolean = when {
                old is Row.Header && new is Row.Header -> old.label == new.label
                // Compare content fields explicitly (avoid faviconUrl-only churn
                // and never compare Bitmap-bearing fields; HistoryItem has none).
                old is Row.Entry && new is Row.Entry ->
                    old.item.id == new.item.id &&
                        old.item.title == new.item.title &&
                        old.item.url == new.item.url &&
                        old.item.timestamp == new.item.timestamp
                else -> false
            }
        }
    }
}
