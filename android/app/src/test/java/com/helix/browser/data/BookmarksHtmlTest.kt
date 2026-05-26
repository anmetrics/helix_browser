package com.helix.browser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class BookmarksHtmlTest {

    @Test fun `export then import round-trips basic bookmarks`() {
        val source = listOf(
            Bookmark(id = 1, title = "Example", url = "https://example.com", timestamp = 1_700_000_000_000),
            Bookmark(id = 2, title = "Kotlin Docs", url = "https://kotlinlang.org/docs", timestamp = 1_710_000_000_000)
        )
        val bytes = ByteArrayOutputStream().apply { BookmarksHtml.export(this, source) }.toByteArray()
        val parsed = BookmarksHtml.import(ByteArrayInputStream(bytes))

        assertEquals(2, parsed.size)
        assertEquals("Example", parsed[0].title)
        assertEquals("https://example.com", parsed[0].url)
        assertEquals("Kotlin Docs", parsed[1].title)
    }

    @Test fun `escapes html entities in title`() {
        val source = listOf(Bookmark(id = 1, title = "Foo & <Bar>", url = "https://x.test"))
        val bytes = ByteArrayOutputStream().apply { BookmarksHtml.export(this, source) }.toByteArray()
        val html = String(bytes)
        // Output must NOT contain raw < or & that would break the parent doc.
        assertTrue("got=$html", html.contains("Foo &amp; &lt;Bar&gt;"))

        val parsed = BookmarksHtml.import(ByteArrayInputStream(bytes))
        assertEquals(1, parsed.size)
        assertEquals("Foo & <Bar>", parsed[0].title)
    }

    @Test fun `strips newlines in title on export`() {
        val source = listOf(Bookmark(id = 1, title = "Multi\nline\ttitle", url = "https://x.test"))
        val html = ByteArrayOutputStream().apply { BookmarksHtml.export(this, source) }.toString()
        // Pull just the visible label between <A …>…</A> for this bookmark and
        // assert it has no line breaks (otherwise the Netscape parser splits
        // into bogus entries).
        val title = Regex("<A[^>]*>([^<]*)</A>").find(html)?.groupValues?.get(1) ?: ""
        assertTrue("got title=[$title]", '\n' !in title && '\r' !in title)
    }

    @Test fun `import drops non-http entries`() {
        val malicious = """
            <DL><p>
                <DT><A HREF="javascript:alert(1)">x</A>
                <DT><A HREF="https://safe.example">ok</A>
                <DT><A HREF="file:///etc/passwd">bad</A>
            </DL><p>
        """.trimIndent()
        val parsed = BookmarksHtml.import(ByteArrayInputStream(malicious.toByteArray()))
        assertEquals(1, parsed.size)
        assertEquals("https://safe.example", parsed[0].url)
    }

    @Test fun `import handles chrome-style nested DT and folders`() {
        val chromeExport = """
            <!DOCTYPE NETSCAPE-Bookmark-file-1>
            <Title>Bookmarks</Title>
            <H1>Bookmarks</H1>
            <DL><p>
                <DT><H3>Folder</H3>
                <DL><p>
                    <DT><A HREF="https://nested.example" ADD_DATE="1700000000">Nested</A>
                </DL><p>
                <DT><A HREF="https://top.example">Top</A>
            </DL><p>
        """.trimIndent()
        val parsed = BookmarksHtml.import(ByteArrayInputStream(chromeExport.toByteArray()))
        // Folders are flattened — both entries land in a single list.
        assertEquals(2, parsed.size)
        assertTrue(parsed.any { it.url == "https://nested.example" && it.title == "Nested" })
        assertTrue(parsed.any { it.url == "https://top.example" })
    }

    @Test fun `decoded entities do not execute html tags`() {
        // A bookmark exported with "<script>" in its title encodes as &lt;script&gt;.
        // unescape must not interpret these as actual markup.
        val src = """<DT><A HREF="https://x.test">&lt;script&gt;hi&lt;/script&gt;</A>"""
        val parsed = BookmarksHtml.import(ByteArrayInputStream(src.toByteArray()))
        assertEquals(1, parsed.size)
        assertEquals("<script>hi</script>", parsed[0].title)
    }

    @Test fun `numeric entities decode`() {
        val src = """<DT><A HREF="https://x.test">caf&#233;</A>"""
        val parsed = BookmarksHtml.import(ByteArrayInputStream(src.toByteArray()))
        assertEquals("café", parsed[0].title)
    }
}
