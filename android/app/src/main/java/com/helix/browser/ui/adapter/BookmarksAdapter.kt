package com.helix.browser.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.helix.browser.R
import com.helix.browser.data.Bookmark
import com.helix.browser.databinding.ItemBookmarkBinding

/**
 * Renders both folder rows and bookmark rows. Tapping a folder navigates into
 * it (via [onItemClick], which the Activity branches on [Bookmark.isFolder]);
 * tapping a bookmark opens it. The per-row overflow button and long-press both
 * surface the Edit / Move / Delete menu through [onOverflowClick].
 */
class BookmarksAdapter(
    private val onItemClick: (Bookmark) -> Unit,
    private val onOverflowClick: (View, Bookmark) -> Unit
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
            if (bookmark.isFolder) {
                binding.favicon.setImageResource(R.drawable.ic_folder)
                binding.url.visibility = View.GONE
            } else {
                binding.favicon.setImageResource(R.drawable.ic_bookmark_filled)
                binding.url.visibility = View.VISIBLE
                binding.url.text = bookmark.url
            }
            binding.root.setOnClickListener { onItemClick(bookmark) }
            binding.root.setOnLongClickListener {
                onOverflowClick(binding.btnOverflow, bookmark)
                true
            }
            binding.btnOverflow.setOnClickListener { onOverflowClick(it, bookmark) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Bookmark>() {
            override fun areItemsTheSame(old: Bookmark, new: Bookmark) = old.id == new.id
            override fun areContentsTheSame(old: Bookmark, new: Bookmark) = old == new
        }
    }
}
