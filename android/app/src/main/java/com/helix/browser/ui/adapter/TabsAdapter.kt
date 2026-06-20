package com.helix.browser.ui.adapter

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.helix.browser.R
import com.helix.browser.databinding.ItemTabBinding
import com.helix.browser.tabs.BrowserTab
import kotlin.math.abs

// A row in the tab switcher: either a colored group header (spans the full grid
// width) or an individual tab card. The activity flattens the TabManager state
// into a List<TabListItem> and submits it; the adapter renders both view types.
sealed class TabListItem {
    abstract val stableId: String

    data class Header(
        val groupId: String,
        val name: String,
        val color: Int,
        val count: Int
    ) : TabListItem() {
        override val stableId: String get() = "header:$groupId"
    }

    data class TabItem(
        val tab: BrowserTab,
        // ARGB tint color when this tab belongs to a group, else null.
        val groupColor: Int?
    ) : TabListItem() {
        override val stableId: String get() = "tab:${tab.id}"
    }
}

class TabsAdapter(
    private val onTabClick: (BrowserTab) -> Unit,
    private val onTabClose: (BrowserTab) -> Unit,
    private val onTabLongPress: ((BrowserTab, View) -> Unit)? = null,
    // Selection-mode hooks. onSelectionToggle is invoked when a tab card is
    // tapped while in selection mode. onHeaderClick opens the group action sheet.
    private val onSelectionToggle: ((BrowserTab) -> Unit)? = null,
    private val onHeaderClick: ((TabListItem.Header) -> Unit)? = null,
    activeTabId: String? = null
) : ListAdapter<TabListItem, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    companion object {
        const val VIEW_TYPE_TAB = 0
        const val VIEW_TYPE_HEADER = 1

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<TabListItem>() {
            override fun areItemsTheSame(old: TabListItem, new: TabListItem) =
                old.stableId == new.stableId

            override fun areContentsTheSame(old: TabListItem, new: TabListItem): Boolean {
                if (old is TabListItem.Header && new is TabListItem.Header) {
                    return old.name == new.name && old.color == new.color && old.count == new.count
                }
                if (old is TabListItem.TabItem && new is TabListItem.TabItem) {
                    val o = old.tab
                    val n = new.tab
                    return o.title == n.title &&
                        o.url == n.url &&
                        o.isPinned == n.isPinned &&
                        o.isIncognito == n.isIncognito &&
                        old.groupColor == new.groupColor &&
                        System.identityHashCode(o.favicon) == System.identityHashCode(n.favicon) &&
                        System.identityHashCode(o.thumbnail) == System.identityHashCode(n.thumbnail)
                }
                return false
            }

            override fun getChangePayload(old: TabListItem, new: TabListItem): Any? {
                if (old !is TabListItem.TabItem || new !is TabListItem.TabItem) return null
                val o = old.tab
                val n = new.tab
                if (o.isIncognito != n.isIncognito || old.groupColor != new.groupColor) return null
                val payload = mutableSetOf<TabPayload>()
                if (o.title != n.title) payload.add(TabPayload.TITLE)
                if (o.url != n.url) payload.add(TabPayload.URL)
                if (o.favicon !== n.favicon) payload.add(TabPayload.FAVICON)
                if (o.thumbnail !== n.thumbnail) payload.add(TabPayload.THUMBNAIL)
                if (o.isPinned != n.isPinned) payload.add(TabPayload.PINNED)
                return if (payload.isEmpty()) null else payload.toList()
            }
        }

        private fun hostOf(url: String): String {
            return try {
                val host = java.net.URI(url).host ?: return ""
                host.removePrefix("www.")
            } catch (_: Exception) {
                ""
            }
        }

        // Deterministic letter-avatar: first letter of the host on a stable,
        // host-derived background color. No network, no PII leak.
        private fun letterAvatar(context: Context, host: String): Drawable {
            val sizePx = (40 * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val letter = host.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
            val bg = avatarColor(host)

            val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg }
            val radius = sizePx / 2f
            canvas.drawCircle(radius, radius, radius, circlePaint)

            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textAlign = Paint.Align.CENTER
                textSize = sizePx * 0.5f
                isFakeBoldText = true
            }
            val yOffset = (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(letter, radius, radius - yOffset, textPaint)

            return BitmapDrawable(context.resources, bitmap)
        }

        private val AVATAR_PALETTE = intArrayOf(
            0xFF7B68EE.toInt(), 0xFF26A69A.toInt(), 0xFFEF5350.toInt(),
            0xFF42A5F5.toInt(), 0xFFFFA726.toInt(), 0xFF66BB6A.toInt(),
            0xFFAB47BC.toInt(), 0xFFEC407A.toInt()
        )

        private fun avatarColor(host: String): Int {
            if (host.isEmpty()) return AVATAR_PALETTE[0]
            val index = abs(host.hashCode()) % AVATAR_PALETTE.size
            return AVATAR_PALETTE[index]
        }

        // Returns an ARGB color with the given alpha (0..255) applied to base RGB.
        private fun applyAlpha(base: Int, alpha: Int): Int {
            return (alpha shl 24) or (base and 0x00FFFFFF)
        }
    }

    // Changing the active tab must repaint exactly the affected rows with the
    // ACTIVE payload — a content-only refresh, no entrance animation. DiffUtil
    // cannot see this field (the ItemCallback is static), so we drive the
    // targeted rebinds here.
    var activeTabId: String? = activeTabId
        set(value) {
            if (field == value) return
            val previousId = field
            field = value
            indexOfTab(previousId).takeIf { it != RecyclerView.NO_POSITION }
                ?.let { notifyItemChanged(it, TabPayload.ACTIVE) }
            indexOfTab(value).takeIf { it != RecyclerView.NO_POSITION }
                ?.let { notifyItemChanged(it, TabPayload.ACTIVE) }
        }

    // Multi-select state. Driven by the activity; when active, taps toggle
    // selection instead of opening a tab, and a checkbox overlay is shown.
    var selectionMode: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            notifyTabRows()
        }

    private val selectedIds = HashSet<String>()

    fun setSelected(ids: Set<String>) {
        selectedIds.clear()
        selectedIds.addAll(ids)
        notifyTabRows()
    }

    private var lastAnimatedPosition = -1

    private fun indexOfTab(id: String?): Int {
        if (id == null) return RecyclerView.NO_POSITION
        return currentList.indexOfFirst { it is TabListItem.TabItem && it.tab.id == id }
    }

    private fun notifyTabRows() {
        // Targeted, animation-free refresh of every tab row's selection chrome.
        currentList.forEachIndexed { index, item ->
            if (item is TabListItem.TabItem) notifyItemChanged(index, TabPayload.SELECTION)
        }
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is TabListItem.Header -> VIEW_TYPE_HEADER
        is TabListItem.TabItem -> VIEW_TYPE_TAB
    }

    // GridLayoutManager span: headers occupy the whole row, tabs a single span.
    fun spanSizeLookup(spanCount: Int) = object : GridLayoutManager.SpanSizeLookup() {
        override fun getSpanSize(position: Int): Int {
            return if (position < itemCount && getItemViewType(position) == VIEW_TYPE_HEADER) spanCount else 1
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_HEADER) {
            HeaderViewHolder(parent.context)
        } else {
            val binding = ItemTabBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            TabViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is TabListItem.Header -> (holder as HeaderViewHolder).bind(item)
            is TabListItem.TabItem -> {
                (holder as TabViewHolder).bind(item)
                animateItem(holder.itemView, position)
            }
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty() || holder !is TabViewHolder) {
            onBindViewHolder(holder, position)
            return
        }
        val item = getItem(position) as? TabListItem.TabItem ?: run {
            onBindViewHolder(holder, position)
            return
        }
        // Payload elements arrive either as a single TabPayload (from
        // notifyItemChanged) or a List<TabPayload> (from getChangePayload).
        val changes = mutableSetOf<TabPayload>()
        for (p in payloads) {
            when (p) {
                is TabPayload -> changes.add(p)
                is Collection<*> -> p.filterIsInstanceTo(changes)
            }
        }
        if (changes.isEmpty()) {
            onBindViewHolder(holder, position)
            return
        }
        holder.bindPartial(item, changes)
    }

    private fun animateItem(view: View, position: Int) {
        if (position <= lastAnimatedPosition) return
        lastAnimatedPosition = position

        view.alpha = 0f
        view.translationY = 60f
        view.scaleX = 0.95f
        view.scaleY = 0.95f

        val delay = (position * 40).toLong()

        val fadeIn = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f).setDuration(280)
        val slideUp = ObjectAnimator.ofFloat(view, "translationY", 60f, 0f).setDuration(320)
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 0.95f, 1f).setDuration(320)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 0.95f, 1f).setDuration(320)

        AnimatorSet().apply {
            playTogether(fadeIn, slideUp, scaleX, scaleY)
            startDelay = delay
            interpolator = DecelerateInterpolator(1.5f)
            start()
        }
    }

    fun resetAnimations() {
        lastAnimatedPosition = -1
    }

    // ---- Header view (built programmatically; no dedicated XML) ----

    inner class HeaderViewHolder(context: Context) :
        RecyclerView.ViewHolder(LinearLayout(context)) {

        private val dot: View
        private val name: TextView

        init {
            val density = context.resources.displayMetrics.density
            fun dp(v: Int) = (v * density).toInt()

            val row = itemView as LinearLayout
            row.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(12), dp(14), dp(12), dp(6))
            row.isClickable = true
            row.isFocusable = true

            dot = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(12), dp(12))
            }
            name = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(8)
                }
                textSize = 13f
                setTextColor(context.getColor(R.color.text_primary))
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            val chevron = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(18), dp(18))
                setImageResource(R.drawable.ic_more_vert)
                setColorFilter(context.getColor(R.color.text_secondary))
                contentDescription = context.getString(R.string.tab_group_options)
            }
            row.addView(dot)
            row.addView(name)
            row.addView(chevron)
        }

        fun bind(header: TabListItem.Header) {
            val context = itemView.context
            dot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(header.color)
            }
            name.text = context.getString(
                R.string.tab_group_header_format, header.name, header.count
            )
            itemView.setOnClickListener { onHeaderClick?.invoke(header) }
        }
    }

    // ---- Tab card view ----

    inner class TabViewHolder(private val binding: ItemTabBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TabListItem.TabItem) {
            val tab = item.tab
            val isIncognito = tab.isIncognito
            binding.incognitoIcon.visibility = if (isIncognito) View.VISIBLE else View.GONE
            binding.tabFavicon.visibility = if (isIncognito) View.GONE else View.VISIBLE
            binding.incognitoBadge.visibility = if (isIncognito) View.VISIBLE else View.GONE

            bindTitle(tab)
            bindPin(tab)
            bindActiveState(tab)
            bindFavicon(tab)
            bindThumbnail(tab)
            bindGroupTint(item.groupColor)
            bindSelection(tab)

            binding.tabCardRoot.setOnClickListener {
                if (selectionMode) {
                    onSelectionToggle?.invoke(tab)
                } else {
                    onTabClick(tab)
                }
            }
            binding.btnCloseTab.setOnClickListener {
                if (selectionMode) {
                    onSelectionToggle?.invoke(tab)
                    return@setOnClickListener
                }
                binding.tabCardRoot.animate()
                    .alpha(0f)
                    .scaleX(0.85f)
                    .scaleY(0.85f)
                    .setDuration(180)
                    .withEndAction { onTabClose(tab) }
                    .start()
            }
            binding.tabCardRoot.setOnLongClickListener { v ->
                onTabLongPress?.invoke(tab, v)
                onTabLongPress != null
            }
        }

        fun bindPartial(item: TabListItem.TabItem, changes: Set<TabPayload>) {
            val tab = item.tab
            if (TabPayload.TITLE in changes) bindTitle(tab)
            if (TabPayload.URL in changes) bindFavicon(tab) // host-derived avatar
            if (TabPayload.FAVICON in changes) bindFavicon(tab)
            if (TabPayload.PINNED in changes) bindPin(tab)
            if (TabPayload.ACTIVE in changes) bindActiveState(tab)
            if (TabPayload.THUMBNAIL in changes) bindThumbnail(tab)
            if (TabPayload.SELECTION in changes) bindSelection(tab)
        }

        private fun bindTitle(tab: BrowserTab) {
            val context = binding.root.context
            binding.tabTitle.text = tab.title.ifEmpty {
                tab.url.takeIf { it.isNotBlank() }?.let { hostOf(it) }?.ifEmpty { null }
                    ?: context.getString(R.string.new_tab)
            }
        }

        private fun bindPin(tab: BrowserTab) {
            binding.pinIcon.visibility = if (tab.isPinned) View.VISIBLE else View.GONE
        }

        private fun bindActiveState(tab: BrowserTab) {
            val context = binding.root.context
            // In selection mode the selected-stroke takes precedence, so don't
            // fight over the card stroke here — bindSelection owns it.
            if (selectionMode) return
            val isActive = tab.id == activeTabId
            binding.tabCardRoot.apply {
                if (isActive) {
                    strokeColor = context.getColor(R.color.accent_purple)
                    strokeWidth = (3 * context.resources.displayMetrics.density).toInt()
                    setCardBackgroundColor(context.getColor(R.color.accent_purple_surface))
                } else {
                    strokeWidth = 0
                    setCardBackgroundColor(context.getColor(R.color.card_background))
                }
            }
        }

        // Translucent tint on the card header so grouped tabs read as a set even
        // when the header row is scrolled out of view.
        private fun bindGroupTint(groupColor: Int?) {
            if (groupColor != null) {
                binding.tabHeaderBar.setBackgroundColor(applyAlpha(groupColor, 0x33))
            } else {
                binding.tabHeaderBar.background = null
            }
        }

        private fun bindSelection(tab: BrowserTab) {
            val context = binding.root.context
            val selected = selectedIds.contains(tab.id)
            binding.selectionOverlay.visibility =
                if (selectionMode) View.VISIBLE else View.GONE
            binding.selectionCheck.visibility =
                if (selectionMode && selected) View.VISIBLE else View.GONE
            binding.btnCloseTab.visibility =
                if (selectionMode) View.INVISIBLE else View.VISIBLE

            if (selectionMode) {
                binding.tabCardRoot.apply {
                    if (selected) {
                        strokeColor = context.getColor(R.color.accent_purple)
                        strokeWidth = (3 * context.resources.displayMetrics.density).toInt()
                        setCardBackgroundColor(context.getColor(R.color.accent_purple_surface))
                    } else {
                        strokeWidth = 0
                        setCardBackgroundColor(context.getColor(R.color.card_background))
                    }
                }
            } else {
                // Leaving selection mode: restore the active-state chrome.
                bindActiveState(tab)
            }
        }

        private fun bindFavicon(tab: BrowserTab) {
            val context = binding.root.context
            if (tab.isIncognito) return
            // Privacy-first: render the locally captured favicon instead of a
            // third-party favicon endpoint (which would leak visited hosts).
            val favicon = tab.favicon
            if (favicon != null && !favicon.isRecycled) {
                Glide.with(context).clear(binding.tabFavicon)
                binding.tabFavicon.setImageBitmap(favicon)
            } else if (tab.url.startsWith("http")) {
                Glide.with(context).clear(binding.tabFavicon)
                binding.tabFavicon.setImageDrawable(letterAvatar(context, hostOf(tab.url)))
            } else {
                Glide.with(context).clear(binding.tabFavicon)
                binding.tabFavicon.setImageResource(R.drawable.ic_helix_logo)
            }
        }

        private fun bindThumbnail(tab: BrowserTab) {
            val context = binding.root.context
            val thumb = tab.thumbnail
            if (thumb != null && !thumb.isRecycled) {
                Glide.with(context).load(thumb).into(binding.tabThumbnail)
            } else {
                Glide.with(context).clear(binding.tabThumbnail)
                binding.tabThumbnail.setImageDrawable(null)
                binding.tabThumbnail.setBackgroundResource(R.color.surface_container)
            }
        }
    }

    // Identifies which displayed field changed, so onBindViewHolder can do a
    // partial in-place rebind instead of a full one.
    enum class TabPayload { TITLE, URL, FAVICON, PINNED, ACTIVE, THUMBNAIL, SELECTION }
}
