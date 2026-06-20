package com.helix.browser.ui.adapter

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.helix.browser.R
import com.helix.browser.databinding.ItemSuggestionBinding

/**
 * The kind of an omnibox suggestion row. Drives the leading icon and whether a
 * favicon is loaded, so the user can tell a "search the web" action from a
 * visited page, a bookmark, or a raw URL match at a glance (Chrome behavior).
 *
 * ANSWER is the special inline-answer card (calculator / unit conversion) that
 * is rendered at the very top, styled distinctly (accent glyph + tint), and
 * COPIES its result to the clipboard on tap instead of navigating. Answer rows
 * are computed locally and are never sent to the network.
 */
enum class SuggestionType { ANSWER, SEARCH, HISTORY, BOOKMARK, URL }

/**
 * A single omnibox suggestion. [primaryText] is the title (or the search phrase
 * for SEARCH rows); [secondaryText] is the URL/host shown beneath it.
 *
 * [navigateValue] is what gets loaded when the row is TAPPED — it is always a
 * ready-to-load value (a real search URL for SEARCH rows, the destination URL
 * otherwise), because the host's loadUrl() loads it verbatim. For ANSWER rows it
 * is NOT a URL: it holds the plain result text that the row copies to the
 * clipboard on tap (the adapter handles answer taps internally and never calls
 * onSuggestionClick for them, so loadUrl() is never invoked with it).
 *
 * [insertValue] is what the "insert into omnibox" arrow fills the address bar
 * with so the user can edit before searching: the raw search phrase for SEARCH
 * rows, the URL otherwise.
 *
 * [faviconUrl] is loaded for URL-bearing rows when available; SEARCH rows show
 * the search glyph instead.
 *
 * [inlineCompletion] is the trailing text the omnibox can grey-out behind the
 * user's typed prefix for Chrome-style inline autocomplete (e.g. user typed
 * "git", row is github.com -> "hub.com"). It is non-null ONLY on the single
 * best URL-bearing row whose host/URL startsWith the typed text (scheme/www
 * stripped, case-insensitive); it is always null for SEARCH rows and in
 * incognito. The host reads it via the dedicated [BrowserViewModel.inlineCompletion]
 * LiveData rather than scanning rows; this field exists so the value travels
 * with the row through DiffUtil and stays consistent with what is rendered.
 */
data class Suggestion(
    val type: SuggestionType,
    val primaryText: String,
    val secondaryText: String,
    val navigateValue: String,
    val insertValue: String = navigateValue,
    val faviconUrl: String? = null,
    val inlineCompletion: String? = null
)

/**
 * onSuggestionClick receives the row's ready-to-load [Suggestion.navigateValue];
 * onInsertClick receives [Suggestion.insertValue] (the editable phrase/URL).
 * Both stay String callbacks so the existing MainActivity wiring (loadUrl / set
 * address bar text) continues to compile and behave correctly.
 */
class SuggestionsAdapter(
    private val onSuggestionClick: (String) -> Unit,
    private val onInsertClick: (String) -> Unit
) : ListAdapter<Suggestion, SuggestionsAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSuggestionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.clear()
    }

    inner class ViewHolder(private val binding: ItemSuggestionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        // The leading icon is the first child of the row LinearLayout. It carries
        // no id in the shared layout, so it is resolved positionally rather than
        // via ViewBinding; guarded with a cast-safe lookup.
        private val leadingIcon: ImageView? = binding.root.getChildAt(0) as? ImageView

        fun bind(item: Suggestion) {
            binding.suggestionTitle.text = item.primaryText
            binding.suggestionUrl.text = item.secondaryText

            val isAnswer = item.type == SuggestionType.ANSWER

            val placeholderRes = when (item.type) {
                // No dedicated calculator glyph ships yet; ic_check reads as a
                // computed/confirmed result and is tinted with the brand accent
                // below so the answer card stands apart from search/history rows.
                SuggestionType.ANSWER -> R.drawable.ic_check
                SuggestionType.SEARCH -> R.drawable.ic_search
                SuggestionType.BOOKMARK -> R.drawable.ic_bookmark
                SuggestionType.HISTORY -> R.drawable.ic_history
                SuggestionType.URL -> R.drawable.ic_history
            }

            leadingIcon?.let { icon ->
                val ctx = icon.context
                val favicon = item.faviconUrl
                // ANSWER and SEARCH rows never load a favicon; only real URL-
                // bearing rows do (and only when a favicon URL is present).
                if (!isAnswer && item.type != SuggestionType.SEARCH && !favicon.isNullOrBlank()) {
                    // Real favicon for page/bookmark/url rows; type glyph as the
                    // placeholder/error so the row is never iconless.
                    icon.imageTintList = null
                    Glide.with(ctx)
                        .load(favicon)
                        .placeholder(placeholderRes)
                        .error(placeholderRes)
                        .into(icon)
                } else {
                    // Tinted vector glyph — clear any pending Glide request so a
                    // recycled row can't draw the previous item's favicon. The
                    // answer card uses the brand accent; everything else the
                    // muted tertiary tone.
                    Glide.with(ctx).clear(icon)
                    icon.setImageResource(placeholderRes)
                    val tintColor = if (isAnswer) R.color.accent_purple else R.color.text_tertiary
                    icon.imageTintList =
                        android.content.res.ColorStateList.valueOf(ctx.getColor(tintColor))
                }
            }

            // The answer title is accented so the result reads as the focal point
            // of the row; other rows keep the standard primary text color.
            binding.suggestionTitle.setTextColor(
                binding.suggestionTitle.context.getColor(
                    if (isAnswer) R.color.accent_purple else R.color.text_primary
                )
            )

            if (isAnswer) {
                // Tap copies the result to the clipboard (Chrome answer-card
                // behavior); it must never navigate, so onSuggestionClick is not
                // called here. navigateValue holds the plain result text.
                binding.root.setOnClickListener { copyAnswer(it.context, item.navigateValue) }
                // The "insert into omnibox" arrow has no meaning for an answer.
                binding.btnInsertSuggestion.setOnClickListener(null)
                binding.btnInsertSuggestion.visibility = View.GONE
            } else {
                binding.btnInsertSuggestion.visibility = View.VISIBLE
                binding.root.setOnClickListener { onSuggestionClick(item.navigateValue) }
                binding.btnInsertSuggestion.setOnClickListener { onInsertClick(item.insertValue) }
            }
        }

        // Copies [value] to the system clipboard and confirms with a toast. Fails
        // soft (logs, no crash) if the clipboard service is unavailable, e.g. on a
        // locked device. The label is a fixed, non-sensitive tag.
        private fun copyAnswer(context: Context, value: String) {
            if (value.isEmpty()) return
            try {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                if (cm == null) return
                cm.setPrimaryClip(ClipData.newPlainText("helix_answer", value))
                Toast.makeText(
                    context,
                    context.getString(R.string.answer_copied),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (t: Throwable) {
                android.util.Log.w("SuggestionsAdapter", "copy answer failed", t)
            }
        }

        fun clear() {
            leadingIcon?.let { Glide.with(it.context).clear(it) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Suggestion>() {
            override fun areItemsTheSame(old: Suggestion, new: Suggestion): Boolean =
                old.type == new.type && old.navigateValue == new.navigateValue

            override fun areContentsTheSame(old: Suggestion, new: Suggestion): Boolean =
                old == new
        }
    }
}
