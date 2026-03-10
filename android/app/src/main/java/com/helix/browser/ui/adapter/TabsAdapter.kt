package com.helix.browser.ui.adapter

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
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
    private val activeTabId: String? = null
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

        val delay = (position * 50).toLong()

        val fadeIn = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f).setDuration(300)
        val slideUp = ObjectAnimator.ofFloat(view, "translationY", 60f, 0f).setDuration(350)
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 0.95f, 1f).setDuration(350)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 0.95f, 1f).setDuration(350)

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
            binding.tabTitle.text = tab.title.ifEmpty { "New Tab" }

            // Show clean domain in URL
            val displayUrl = try {
                java.net.URI(tab.url).host?.removePrefix("www.") ?: tab.url
            } catch (_: Exception) { tab.url }
            binding.tabUrl.text = displayUrl

            // Incognito
            val isIncognito = tab.isIncognito
            binding.incognitoIcon.visibility = if (isIncognito) View.VISIBLE else View.GONE
            binding.tabFavicon.visibility = if (isIncognito) View.GONE else View.VISIBLE
            binding.incognitoBadge.visibility = if (isIncognito) View.VISIBLE else View.GONE

            // Active tab indicator
            val isActive = tab.id == activeTabId
            binding.activeIndicator.visibility = if (isActive) View.VISIBLE else View.GONE
            if (isActive) {
                (binding.root as? com.google.android.material.card.MaterialCardView)?.apply {
                    strokeColor = binding.root.context.getColor(R.color.accent_purple)
                    strokeWidth = 2
                }
            } else {
                (binding.root as? com.google.android.material.card.MaterialCardView)?.apply {
                    strokeColor = binding.root.context.getColor(R.color.divider_color)
                    strokeWidth = 1
                }
            }

            // Favicon
            if (!isIncognito && tab.url.startsWith("http")) {
                val faviconUrl = "https://www.google.com/s2/favicons?domain=${
                    try { java.net.URI(tab.url).host } catch (_: Exception) { "" }
                }&sz=64"
                Glide.with(binding.root.context)
                    .load(faviconUrl)
                    .placeholder(R.drawable.ic_helix_logo)
                    .error(R.drawable.ic_helix_logo)
                    .circleCrop()
                    .into(binding.tabFavicon)
            }

            // Thumbnail
            if (tab.thumbnail != null) {
                Glide.with(binding.root.context)
                    .load(tab.thumbnail)
                    .into(binding.tabThumbnail)
            } else {
                binding.tabThumbnail.setImageDrawable(null)
            }

            binding.root.setOnClickListener { onTabClick(tab) }
            binding.btnCloseTab.setOnClickListener { onTabClose(tab) }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<BrowserTab>() {
            override fun areItemsTheSame(old: BrowserTab, new: BrowserTab) = old.id == new.id
            override fun areContentsTheSame(old: BrowserTab, new: BrowserTab) = old == new
        }
    }
}
