package com.helix.browser.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric stubs Android framework classes (Uri.encode, Uri.parse) so we
// can verify URL handling logic without an emulator. Pinned to SDK 34
// because Robolectric 4.11 does not ship a 35 (Android 15) shadow set yet.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UrlUtilsTest {

    // --- isUrl / isSearchQuery ---

    @Test fun `bare domain is url`() = assertTrue(UrlUtils.isUrl("example.com"))
    @Test fun `subdomain is url`() = assertTrue(UrlUtils.isUrl("blog.example.com"))
    @Test fun `https url is url`() = assertTrue(UrlUtils.isUrl("https://example.com/path?q=1"))
    // Note: bare "localhost:8080" is intentionally classified as a search
    // query by the current heuristic (no dot, no scheme). Document with a
    // negative assertion so future refactors don't silently change behavior.
    @Test fun `bare localhost without scheme is not detected as url`() =
        assertFalse(UrlUtils.isUrl("localhost:8080"))
    @Test fun `single word is not url`() = assertFalse(UrlUtils.isUrl("hello"))
    @Test fun `multi word query is not url`() = assertFalse(UrlUtils.isUrl("how to code"))
    @Test fun `empty is not url`() = assertFalse(UrlUtils.isUrl(""))

    // --- buildSearchQuery ---

    @Test fun `google is default`() {
        val url = UrlUtils.buildSearchQuery("foo bar", "google")
        assertEquals("https://www.google.com/search?q=foo%20bar", url)
    }

    @Test fun `unknown engine falls back to google`() {
        val url = UrlUtils.buildSearchQuery("hi", "garbage")
        assertTrue(url.startsWith("https://www.google.com/search?q="))
    }

    @Test fun `duckduckgo url shape`() {
        val url = UrlUtils.buildSearchQuery("kotlin", "duckduckgo")
        assertEquals("https://duckduckgo.com/?q=kotlin", url)
    }

    @Test fun `custom engine with placeholder`() {
        val tpl = UrlUtils.CUSTOM_PREFIX + "https://x.test/?s=%s&hl=en"
        assertEquals("https://x.test/?s=helix&hl=en", UrlUtils.buildSearchQuery("helix", tpl))
    }

    @Test fun `custom engine without placeholder appends query`() {
        val tpl = UrlUtils.CUSTOM_PREFIX + "https://x.test/?s="
        assertEquals("https://x.test/?s=helix", UrlUtils.buildSearchQuery("helix", tpl))
    }

    @Test fun `query is url-encoded in custom engine too`() {
        val tpl = UrlUtils.CUSTOM_PREFIX + "https://x.test/?q=%s"
        val out = UrlUtils.buildSearchQuery("a b&c", tpl)
        assertTrue("got=$out", out.contains("a%20b%26c") || out.contains("a+b%26c"))
    }

    // --- formatUrl ---

    @Test fun `formatUrl preserves https`() {
        assertEquals("https://example.com", UrlUtils.formatUrl("https://example.com"))
    }

    @Test fun `formatUrl prepends https for bare domain`() {
        assertEquals("https://example.com", UrlUtils.formatUrl("example.com"))
    }

    @Test fun `formatUrl turns search text into search query`() {
        val out = UrlUtils.formatUrl("how to bake bread")
        assertTrue("got=$out", out.startsWith("https://"))
        assertTrue("got=$out", out.contains("search") || out.contains("q="))
    }

    // --- getDisplayUrl ---

    @Test fun `display url strips www`() {
        assertEquals("example.com", UrlUtils.getDisplayUrl("https://www.example.com/some/path"))
    }

    @Test fun `display url returns original on invalid`() {
        assertEquals("not a url", UrlUtils.getDisplayUrl("not a url"))
    }
}
