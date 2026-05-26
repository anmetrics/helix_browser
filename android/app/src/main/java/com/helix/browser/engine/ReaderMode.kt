package com.helix.browser.engine

import android.webkit.WebView

/**
 * Reader mode: extracts the primary article from a page and replaces the
 * DOM with a minimal, distraction-free presentation. The extractor uses a
 * lightweight heuristic (best paragraph density inside <article>/<main>
 * candidates) rather than bundling Mozilla Readability.js, to keep APK size
 * down and avoid licensing complexity.
 *
 * Usage:
 *   ReaderMode.enter(webView, isDarkTheme)
 *   ReaderMode.exit(webView)       // reloads the original URL
 *
 * The injected script keeps the original document in a global so we can
 * detect if reader mode is currently active via [isActive].
 */
object ReaderMode {

    private const val JS_TAG = "__helix_reader__"

    fun enter(webView: WebView, dark: Boolean) {
        val bg = if (dark) "#0F0F0F" else "#FAFAFA"
        val fg = if (dark) "#EDEDED" else "#1A1A1A"
        val accent = if (dark) "#7B68EE" else "#5B4DC9"
        // Heuristic extractor: pick the node with the highest paragraph
        // character count. Mirrors the core idea of Readability without
        // its full DOM scoring system.
        val script = """
            (function(){
              if (window.$JS_TAG) return; // already active
              window.$JS_TAG = { html: document.documentElement.outerHTML, title: document.title };
              function score(node){
                if (!node) return 0;
                var ps = node.querySelectorAll('p');
                var total = 0;
                for (var i=0;i<ps.length;i++){ total += (ps[i].innerText||'').length; }
                return total;
              }
              var candidates = document.querySelectorAll('article, main, [role="main"], .post, .article, .entry, .content, #content, #main');
              var best = null, bestScore = 0;
              candidates.forEach(function(c){ var s = score(c); if (s > bestScore){ best = c; bestScore = s; } });
              if (!best || bestScore < 200) best = document.body;
              var title = document.title || (document.querySelector('h1')||{}).innerText || '';
              var byline = (document.querySelector('[rel=author], .byline, .author')||{}).innerText || '';
              var content = best ? best.innerHTML : document.body.innerHTML;
              document.documentElement.innerHTML =
                '<head><meta name="viewport" content="width=device-width, initial-scale=1">' +
                '<style>' +
                '  html,body{margin:0;padding:0;background:$bg;color:$fg;font-family:-apple-system,Georgia,serif;font-size:18px;line-height:1.7;}' +
                '  .helix-reader{max-width:680px;margin:0 auto;padding:48px 20px 80px;}' +
                '  .helix-reader h1{font-size:28px;line-height:1.25;margin:0 0 8px;color:$fg;}' +
                '  .helix-reader .byline{color:#888;font-size:14px;margin-bottom:32px;}' +
                '  .helix-reader p{margin:0 0 18px;}' +
                '  .helix-reader a{color:$accent;text-decoration:none;border-bottom:1px solid $accent;}' +
                '  .helix-reader img,.helix-reader video{max-width:100%;height:auto;border-radius:8px;margin:12px 0;}' +
                '  .helix-reader pre,.helix-reader code{background:#222;color:#ddd;padding:8px;border-radius:6px;overflow:auto;font-size:14px;}' +
                '  .helix-reader blockquote{border-left:3px solid $accent;margin:16px 0;padding:4px 16px;color:#aaa;}' +
                '  .helix-reader h2,.helix-reader h3{margin-top:32px;}' +
                '</style></head>' +
                '<body><article class="helix-reader">' +
                '<h1>' + title + '</h1>' +
                (byline ? '<div class="byline">' + byline + '</div>' : '') +
                content +
                '</article></body>';
              // Strip scripts in extracted content for safety.
              document.querySelectorAll('script').forEach(function(s){ s.remove(); });
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    fun exit(webView: WebView) {
        // Cleanest way to restore the page is to reload it; restoring innerHTML
        // would leave React/SPA state torn. Reload also rehydrates listeners.
        webView.evaluateJavascript("delete window.$JS_TAG;", null)
        webView.reload()
    }

    fun isActive(webView: WebView, callback: (Boolean) -> Unit) {
        webView.evaluateJavascript("!!window.$JS_TAG") { v -> callback(v == "true") }
    }
}
