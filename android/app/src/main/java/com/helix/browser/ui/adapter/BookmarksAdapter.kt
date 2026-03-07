package com.helix.browser.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.helix.browser.data.Bookmark
import com.helix.browser.databinding.ItemBookmarkBinding

class BookmarksAdapter(
    private val onItemClick: (Bookmark) -> Unit,
    private val onDeleteClick: (Bookmark) -> Unit
) : ListAdapter<Bookmark, BookmarksAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBookmarkBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemBookmarkBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(bookmark: Bookmark) {
            binding.title.text = bookmark.title
            binding.url.text = bookmark.url
            binding.root.setOnClickListener { onItemClick(bookmark) }
            binding.btnDelete.setOnClickListener { onDeleteClick(bookmark) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Bookmark>() {
            override fun areItemsTheSame(old: Bookmark, new: Bookmark) = old.id == new.id
            override fun areContentsTheSame(old: Bookmark, new: Bookmark) = old == new
        }
    }
}
