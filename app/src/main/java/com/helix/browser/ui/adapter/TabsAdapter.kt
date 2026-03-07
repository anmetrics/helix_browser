package com.helix.browser.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.helix.browser.databinding.ItemTabBinding
import com.helix.browser.tabs.BrowserTab

class TabsAdapter(
    private val onTabClick: (BrowserTab) -> Unit,
    private val onTabClose: (BrowserTab) -> Unit
) : ListAdapter<BrowserTab, TabsAdapter.TabViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        val binding = ItemTabBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TabViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TabViewHolder(private val binding: ItemTabBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(tab: BrowserTab) {
            binding.tabTitle.text = tab.title.ifEmpty { "New Tab" }
            binding.tabUrl.text = tab.url.ifEmpty { "about:blank" }
            binding.incognitoIcon.visibility = if (tab.isIncognito) android.view.View.VISIBLE else android.view.View.GONE
            tab.thumbnail?.let { binding.tabThumbnail.setImageBitmap(it) }
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
