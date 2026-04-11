package com.helix.browser.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.helix.browser.R
import com.helix.browser.utils.LocaleHelper
import com.helix.browser.utils.Prefs

class LanguageActivity : BaseActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: LanguageAdapter
    private var languages = listOf<LanguageItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_language)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        recyclerView = findViewById(R.id.languageList)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Handle Safe Area (Edge-to-Edge)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.appBar)) { v, insets ->
            val statusInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusInsets.top)
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(recyclerView) { v, insets ->
            val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.updatePadding(bottom = navInsets.bottom + 16) // Extra padding for list end
            insets
        }

        val currentLang = Prefs.getLanguage(this)
        
        // Priority items: System Default first, then English
        val systemItem = LanguageItem("system", getString(R.string.system_default), "")
        val englishItem = LanguageItem("en", "English", "English")
        
        val otherLanguages = listOf(
            LanguageItem("vi", "Tiếng Việt", "Vietnamese"),
            LanguageItem("ja", "日本語", "Japanese"),
            LanguageItem("ko", "한국어", "Korean"),
            LanguageItem("es", "Español", "Spanish"),
            LanguageItem("fr", "Français", "French"),
            LanguageItem("zh-rCN", "中文", "Chinese (Simplified)"),
            LanguageItem("de", "Deutsch", "German"),
            LanguageItem("pt", "Português", "Portuguese"),
            LanguageItem("ru", "Русский", "Russian"),
            LanguageItem("ar", "العربية", "Arabic"),
            LanguageItem("hi", "हिन्दी", "Hindi"),
            LanguageItem("th", "ไทย", "Thai"),
            LanguageItem("id", "Bahasa Indonesia", "Indonesian")
        ).sortedBy { it.nativeName }

        languages = listOf(systemItem, englishItem) + otherLanguages
        adapter = LanguageAdapter(languages, currentLang) { selectedLang ->
            LocaleHelper.setLocale(this, selectedLang)
            
            // Re-trigger global apply by restarting MainActivity with clear task
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
            
            // Set result to notify SettingsActivity (if it wants to finish too)
            setResult(RESULT_OK)
            finish()
        }
        recyclerView.adapter = adapter
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_language, menu)
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.queryHint = getString(R.string.search_hint)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                adapter.filter(newText ?: "")
                return true
            }
        })
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    data class LanguageItem(val code: String, val nativeName: String, val englishName: String)

    inner class LanguageAdapter(
        private val allItems: List<LanguageItem>,
        private val currentCode: String,
        private val onSelected: (String) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private var filteredItems = mutableListOf<Any>()
        private var isFiltering = false

        init {
            prepareData("")
        }

        private fun prepareData(query: String) {
            filteredItems.clear()
            if (query.isEmpty()) {
                isFiltering = false
                // Group by categories
                val systemItems = allItems.filter { it.code == "system" || it.code == "en" }
                val otherItems = allItems.filter { it.code != "system" && it.code != "en" }

                if (systemItems.isNotEmpty()) {
                    filteredItems.add(getString(R.string.recommended_languages))
                    filteredItems.addAll(systemItems)
                }
                if (otherItems.isNotEmpty()) {
                    filteredItems.add(getString(R.string.all_languages))
                    filteredItems.addAll(otherItems)
                }
            } else {
                isFiltering = true
                val lowerQuery = query.lowercase()
                filteredItems.addAll(allItems.filter {
                    it.nativeName.lowercase().contains(lowerQuery) ||
                    it.englishName.lowercase().contains(lowerQuery) ||
                    it.code.lowercase().contains(lowerQuery)
                })
            }
        }

        inner class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val container: View = view.findViewById(R.id.itemContainer)
            val name: TextView = view.findViewById(R.id.langName)
            val subName: TextView = view.findViewById(R.id.langSubName)
            val check: ImageView = view.findViewById(R.id.langCheck)
        }

        inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.headerTitle)
        }

        fun filter(query: String) {
            prepareData(query)
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int): Int {
            return if (filteredItems[position] is String) 0 else 1
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == 0) {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_language_header, parent, false)
                HeaderViewHolder(view)
            } else {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_language, parent, false)
                ItemViewHolder(view)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val data = filteredItems[position]
            if (holder is HeaderViewHolder && data is String) {
                holder.title.text = data
            } else if (holder is ItemViewHolder && data is LanguageItem) {
                val isSelected = data.code == currentCode
                
                holder.name.text = data.nativeName
                holder.subName.text = data.englishName
                holder.subName.visibility = if (data.englishName.isEmpty()) View.GONE else View.VISIBLE
                holder.check.visibility = if (isSelected) View.VISIBLE else View.GONE
                
                holder.container.setBackgroundResource(
                    if (isSelected) R.drawable.bg_language_item_selected 
                    else R.drawable.bg_language_item
                )
                
                holder.itemView.setOnClickListener { onSelected(data.code) }
            }
        }

        override fun getItemCount() = filteredItems.size
    }
}
