package com.helix.browser.data

import java.io.InputStream
import java.io.OutputStream

/**
 * Reads and writes the Netscape bookmark file format used by every major
 * desktop browser (Chrome, Firefox, Edge, Safari). The format is forgiving
 * HTML built from nested `<DL>` lists where `<DT><H3>` opens a folder and
 * `<DT><A HREF="…">title</A>` is a bookmark.
 *
 * Two import surfaces are provided:
 *  - [import] returns a FLAT list of every link (folders flattened). This is
 *    the safe, structure-agnostic view used by tests and any caller that does
 *    not model folders.
 *  - [importTree] preserves the folder hierarchy as a tree of [Node]s so
 *    folders round-trip with [export].
 *
 * [export] emits the nested format from a flat `List<Bookmark>` by rebuilding
 * the parent/child tree from each row's [Bookmark.parentId].
 */
object BookmarksHtml {

    private val LINK_RE = Regex(
        "<a\\s+[^>]*href=\"([^\"]+)\"[^>]*>([^<]*)</a>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
    )

    // --- Export ------------------------------------------------------------

    fun export(out: OutputStream, bookmarks: List<Bookmark>) {
        // Group children by parent so we can recurse. Treat a parentId that
        // points at a non-folder / missing row as root, mirroring the DAO's
        // orphan handling, so nothing is silently dropped on export.
        val folderIds = bookmarks.asSequence().filter { it.isFolder }.map { it.id }.toHashSet()
        val byParent = HashMap<Long?, MutableList<Bookmark>>()
        for (b in bookmarks) {
            val key = b.parentId?.takeIf { it in folderIds }
            byParent.getOrPut(key) { ArrayList() }.add(b)
        }

        out.writer(Charsets.UTF_8).use { w ->
            w.write("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n")
            w.write("<!-- This is an automatically generated file. -->\n")
            w.write("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n")
            w.write("<TITLE>Bookmarks</TITLE>\n")
            w.write("<H1>Bookmarks</H1>\n")
            w.write("<DL><p>\n")
            writeChildren(w, byParent, parentId = null, indent = 1)
            w.write("</DL><p>\n")
        }
    }

    private fun writeChildren(
        w: java.io.Writer,
        byParent: Map<Long?, List<Bookmark>>,
        parentId: Long?,
        indent: Int
    ) {
        val pad = "    ".repeat(indent)
        for (b in byParent[parentId].orEmpty()) {
            val addDate = (b.timestamp / 1000).toString()
            if (b.isFolder) {
                val name = escapeHtml(b.title.ifBlank { "" })
                w.write("$pad<DT><H3 ADD_DATE=\"$addDate\">$name</H3>\n")
                w.write("$pad<DL><p>\n")
                writeChildren(w, byParent, b.id, indent + 1)
                w.write("$pad</DL><p>\n")
            } else {
                val title = escapeHtml(b.title.ifBlank { b.url })
                val url = escapeHtml(b.url)
                w.write("$pad<DT><A HREF=\"$url\" ADD_DATE=\"$addDate\">$title</A>\n")
            }
        }
    }

    // --- Flat import (folders flattened) -----------------------------------

    data class Imported(val title: String, val url: String)

    fun import(input: InputStream): List<Imported> {
        val text = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val out = ArrayList<Imported>()
        for (m in LINK_RE.findAll(text)) {
            val url = unescapeHtml(m.groupValues[1]).trim()
            if (!isImportableUrl(url)) continue
            val title = unescapeHtml(m.groupValues[2]).trim().ifBlank { url }
            out.add(Imported(title = title, url = url))
        }
        return out
    }

    // --- Tree import (folders preserved) -----------------------------------

    sealed class Node {
        data class Folder(val title: String, val children: List<Node>) : Node()
        data class Link(val title: String, val url: String) : Node()
    }

    /**
     * Parse the document into a forest of [Node]s preserving folder nesting.
     * The parser scans the stable structural tokens (`<DT><H3>`, `<DL`, `</DL>`,
     * `<A HREF>`) with a small stack instead of a full HTML parser, which keeps
     * it forgiving of the wildly inconsistent whitespace real exports contain.
     * A legacy flat file (links with no folders) yields a flat list of
     * [Node.Link]s at the root.
     */
    fun importTree(input: InputStream): List<Node> {
        val text = input.bufferedReader(Charsets.UTF_8).use { it.readText() }

        val root = ArrayList<Node>()
        // Stack of (folder title, children-being-collected). The bottom frame is
        // the root list. A pending folder waits for its opening <DL>.
        val stack = ArrayDeque<MutableList<Node>>()
        stack.addLast(root)
        val pendingFolders = ArrayDeque<String>()

        var i = 0
        val n = text.length
        while (i < n) {
            val lt = text.indexOf('<', i)
            if (lt < 0) break
            val gt = text.indexOf('>', lt + 1)
            if (gt < 0) break
            val tag = text.substring(lt, gt + 1)
            val lower = tag.lowercase()

            when {
                lower.startsWith("<h3") -> {
                    // Folder title runs until the closing </H3>.
                    val end = indexOfIgnoreCase(text, "</h3>", gt + 1)
                    val raw = if (end > 0) text.substring(gt + 1, end) else ""
                    pendingFolders.addLast(unescapeHtml(raw).trim())
                    i = if (end > 0) end + "</h3>".length else gt + 1
                }
                lower.startsWith("<dl") -> {
                    // Open a new folder scope. If a folder header just preceded
                    // this <DL>, attach the new child list to that folder.
                    val children = ArrayList<Node>()
                    val title = pendingFolders.removeLastOrNull()
                    if (title != null) {
                        stack.last().add(Node.Folder(title, children))
                        stack.addLast(children)
                    } else {
                        // <DL> with no preceding <H3> (e.g. the outermost list).
                        // Reuse the current scope so its links land at this level.
                        stack.addLast(stack.last())
                    }
                    i = gt + 1
                }
                lower.startsWith("</dl") -> {
                    if (stack.size > 1) stack.removeLast()
                    i = gt + 1
                }
                lower.startsWith("<a") -> {
                    val end = indexOfIgnoreCase(text, "</a>", gt + 1)
                    val anchor = if (end > 0) text.substring(lt, end + "</a>".length) else tag
                    val m = LINK_RE.find(anchor)
                    if (m != null) {
                        val url = unescapeHtml(m.groupValues[1]).trim()
                        if (isImportableUrl(url)) {
                            val label = unescapeHtml(m.groupValues[2]).trim().ifBlank { url }
                            stack.last().add(Node.Link(label, url))
                        }
                    }
                    i = if (end > 0) end + "</a>".length else gt + 1
                }
                else -> i = gt + 1
            }
        }
        return root
    }

    private fun isImportableUrl(url: String): Boolean =
        url.isNotEmpty() && (url.startsWith("http://") || url.startsWith("https://"))

    private fun indexOfIgnoreCase(haystack: String, needle: String, from: Int): Int =
        haystack.indexOf(needle, from, ignoreCase = true)

    private fun escapeHtml(s: String): String = s
        // Strip line breaks so each bookmark stays on a single <DT> line.
        .replace('\n', ' ')
        .replace('\r', ' ')
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun unescapeHtml(s: String): String {
        // Targeted entity decoding only. We intentionally do NOT use
        // Html.fromHtml here because it parses <b>, <font>, etc. and would
        // execute embedded markup; bookmark titles must be plain text.
        if ('&' !in s) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '&') {
                val end = s.indexOf(';', i + 1)
                if (end > 0 && end - i <= 10) {
                    val ent = s.substring(i + 1, end)
                    val replacement = when {
                        ent == "amp" -> "&"
                        ent == "quot" -> "\""
                        ent == "apos" -> "'"
                        ent == "lt" -> "<"
                        ent == "gt" -> ">"
                        ent == "nbsp" -> " "
                        ent.startsWith("#x") || ent.startsWith("#X") ->
                            ent.substring(2).toIntOrNull(16)?.takeIf { it in 0x20..0xFFFF }
                                ?.let { Character.toString(it.toChar()) }
                        ent.startsWith("#") ->
                            ent.substring(1).toIntOrNull()?.takeIf { it in 0x20..0xFFFF }
                                ?.let { Character.toString(it.toChar()) }
                        else -> null
                    }
                    if (replacement != null) {
                        sb.append(replacement); i = end + 1; continue
                    }
                }
            }
            sb.append(c); i++
        }
        return sb.toString()
    }
}
