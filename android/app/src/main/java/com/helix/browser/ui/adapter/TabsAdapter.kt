package com.helix.browser.ui.adapter

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.helix.browser.R
import com.helix.browser.databinding.ItemTabBinding
import com.helix.browser.tabs.BrowserTab

class TabsAdapter(
    private val onTabClick: (BrowserTab) -> Unit,
    private val onTabClose: (BrowserTab) -> Unit,
    private val onTabLongPress: ((BrowserTab, View) -> Unit)? = null,
    var activeTabId: String? = null
) : ListAdapter<BrowserTab, TabsAdapter.TabViewHolder>(DIFF_CALLBACK) {

    private var lastAnimatedPosition = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        val binding = ItemTabBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TabViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        holder.bind(getItem(position))
        animateItem(holder.itemView, position)
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

    inner class TabViewHolder(private val binding: ItemTabBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(tab: BrowserTab) {
            val context = binding.root.context
            binding.tabTitle.text = tab.title.ifEmpty { context.getString(R.string.new_tab) }

            // Incognito
            val isIncognito = tab.isIncognito
            binding.incognitoIcon.visibility = if (isIncognito) View.VISIBLE else View.GONE
            binding.tabFavicon.visibility = if (isIncognito) View.GONE else View.VISIBLE
            binding.incognitoBadge.visibility = if (isIncognito) View.VISIBLE else View.GONE
            binding.pinIcon.visibility = if (tab.isPinned) View.VISIBLE else View.GONE

            // Active tab indicator — Chrome-style purple stroke around card
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

            // Favicon
            if (!isIncognito && tab.url.startsWith("http")) {
                val faviconUrl = "https://www.google.com/s2/favicons?domain=${
                    try { java.net.URI(tab.url).host } catch (_: Exception) { "" }
                }&sz=64"
                Glide.with(context)
                    .load(faviconUrl)
                    .placeholder(R.drawable.ic_helix_logo)
                    .error(R.drawable.ic_helix_logo)
                    .circleCrop()
                    .into(binding.tabFavicon)
            } else if (!isIncognito) {
                binding.tabFavicon.setImageResource(R.drawable.ic_helix_logo)
            }

            // Thumbnail
            if (tab.thumbnail != null) {
                Glide.with(context)
                    .load(tab.thumbnail)
                    .into(binding.tabThumbnail)
            } else {
                binding.tabThumbnail.setImageDrawable(null)
                binding.tabThumbnail.setBackgroundResource(R.color.surface_container)
            }

            binding.tabCardRoot.setOnClickListener { onTabClick(tab) }
            binding.btnCloseTab.setOnClickListener {
                // Animate close: fade + scale out
                binding.tabCardRoot.animate()
                    .alpha(0f)
                    .scaleX(0.85f)
                    .scaleY(0.85f)
                    .setDuration(180)
                    .withEndAction {
                        onTabClose(tab)
                    }.start()
            }
            binding.tabCardRoot.setOnLongClickListener { v ->
                onTabLongPress?.invoke(tab, v)
                onTabLongPress != null
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<BrowserTab>() {
            override fun areItemsTheSame(old: BrowserTab, new: BrowserTab) = old.id == new.id
            override fun areContentsTheSame(old: BrowserTab, new: BrowserTab) = old == new
        }
    }
}
