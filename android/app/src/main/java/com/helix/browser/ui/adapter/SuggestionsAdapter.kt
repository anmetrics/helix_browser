package com.helix.browser.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.helix.browser.databinding.ItemSuggestionBinding

class SuggestionsAdapter(
    private val onSuggestionClick: (String) -> Unit,
    private val onInsertClick: (String) -> Unit
) : RecyclerView.Adapter<SuggestionsAdapter.ViewHolder>() {

    private var items = listOf<Pair<String, String>>() // title, url

    fun submitList(list: List<Pair<String, String>>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSuggestionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(private val binding: ItemSuggestionBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Pair<String, String>) {
            binding.suggestionTitle.text = item.first
            binding.suggestionUrl.text = item.second
            binding.root.setOnClickListener { onSuggestionClick(item.second) }
            binding.btnInsertSuggestion.setOnClickListener { onInsertClick(item.second) }
        }
    }
}
