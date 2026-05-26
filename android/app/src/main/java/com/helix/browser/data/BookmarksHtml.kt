package com.helix.browser.data

import android.text.Html
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Reads and writes the Netscape bookmark file format used by every major
 * desktop browser (Chrome, Firefox, Edge, Safari). The format is forgiving
 * HTML — we emit a minimal flat list and parse with a regex that targets the
 * stable `<A HREF="…">title</A>` skeleton; folder nesting is intentionally
 * flattened on import, since Helix does not currently expose folders.
 */
object BookmarksHtml {

    private val LINK_RE = Regex(
        "<a\\s+[^>]*href=\"([^\"]+)\"[^>]*>([^<]*)</a>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
    )

    fun export(out: OutputStream, bookmarks: List<Bookmark>) {
        out.writer(Charsets.UTF_8).use { w ->
            w.write("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n")
            w.write("<!-- This is an automatically generated file. -->\n")
            w.write("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n")
            w.write("<TITLE>Bookmarks</TITLE>\n")
            w.write("<H1>Bookmarks</H1>\n")
            w.write("<DL><p>\n")
            val df = SimpleDateFormat("yyyyMMdd", Locale.US)
            for (b in bookmarks) {
                val addDate = (b.timestamp / 1000).toString()
                val title = escapeHtml(b.title.ifBlank { b.url })
                val url = escapeHtml(b.url)
                w.write("    <DT><A HREF=\"$url\" ADD_DATE=\"$addDate\">$title</A>\n")
            }
            w.write("</DL><p>\n")
            // Touch df so SimpleDateFormat is treated as used in case we later
            // add LAST_MODIFIED — keeps the import side symmetrical.
            df.format(Date(0))
        }
    }

    data class Imported(val title: String, val url: String)

    fun import(input: InputStream): List<Imported> {
        val text = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val out = ArrayList<Imported>()
        for (m in LINK_RE.findAll(text)) {
            val url = unescapeHtml(m.groupValues[1]).trim()
            if (url.isEmpty() || !(url.startsWith("http://") || url.startsWith("https://"))) continue
            val title = unescapeHtml(m.groupValues[2]).trim().ifBlank { url }
            out.add(Imported(title = title, url = url))
        }
        return out
    }

    private fun escapeHtml(s: String): String = s
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun unescapeHtml(s: String): String {
        // Cover the four entities we emit plus &nbsp; that other browsers add.
        @Suppress("DEPRECATION")
        return Html.fromHtml(s).toString()
    }
}
