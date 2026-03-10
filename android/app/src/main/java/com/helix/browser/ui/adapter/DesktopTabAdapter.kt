package com.helix.browser.ui.adapter

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.helix.browser.R
import com.helix.browser.tabs.BrowserTab

class DesktopTabAdapter(
    private val onTabSelected: (BrowserTab) -> Unit,
    private val onTabClosed: (BrowserTab) -> Unit
) : ListAdapter<BrowserTab, DesktopTabAdapter.TabViewHolder>(TabDiffCallback()) {

    var currentTabId: String? = null
        set(value) {
            val previousId = field
            field = value
            val previousIndex = currentList.indexOfFirst { it.id == previousId }
            val newIndex = currentList.indexOfFirst { it.id == value }
            if (previousIndex != -1) notifyItemChanged(previousIndex)
            if (newIndex != -1) notifyItemChanged(newIndex)
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_tab_desktop, parent, false)
        return TabViewHolder(view)
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        val tab = getItem(position)
        holder.bind(tab, tab.id == currentTabId)
    }

    inner class TabViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTabTitle)
        private val ivFavicon: ImageView = itemView.findViewById(R.id.ivTabFavicon)
        private val btnClose: ImageButton = itemView.findViewById(R.id.btnTabClose)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onTabSelected(getItem(position))
                }
            }
            btnClose.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onTabClosed(getItem(position))
                }
            }
        }

        fun bind(tab: BrowserTab, isActive: Boolean) {
            itemView.isSelected = isActive
            tvTitle.text = tab.title.takeIf { it.isNotBlank() } ?: tab.url.takeIf { it.isNotBlank() } ?: itemView.context.getString(R.string.new_tab)

            if (isActive) {
                tvTitle.typeface = Typeface.DEFAULT_BOLD
            } else {
                tvTitle.typeface = Typeface.DEFAULT
            }
            
            if (tab.favicon != null) {
                ivFavicon.setImageBitmap(tab.favicon)
            } else {
                Glide.with(itemView.context)
                    .load(R.drawable.ic_desktop)
                    .transform(RoundedCorners(4))
                    .into(ivFavicon)
            }
        }
    }

    class TabDiffCallback : DiffUtil.ItemCallback<BrowserTab>() {
        override fun areItemsTheSame(oldItem: BrowserTab, newItem: BrowserTab): Boolean {
            return oldItem.id == newItem.id
        }

        @android.annotation.SuppressLint("DiffUtilEquals")
        override fun areContentsTheSame(oldItem: BrowserTab, newItem: BrowserTab): Boolean {
            return oldItem.title == newItem.title &&
                   oldItem.url == newItem.url &&
                   // Suppress DiffUtilEquals by using identity check for Bitmap, 
                   // or just compare reference since we update the instance on change.
                   oldItem.favicon === newItem.favicon
        }
    }
}
