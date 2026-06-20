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
        //
        // SECURITY: the extracted markup is page-controlled and therefore
        // untrusted. We sanitize it inside a *detached* document BEFORE it
        // ever touches the live DOM (assigning untrusted innerHTML to the
        // live document and stripping afterwards is unsafe: inline event
        // handlers and javascript:/data: URLs would already be live, and
        // <img onerror> fires immediately on assignment). title/byline are
        // HTML-escaped, and a restrictive CSP is added as defense-in-depth so
        // even an escaping miss cannot execute script.
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

              // Escape text destined for an HTML context.
              function esc(s){
                return String(s == null ? '' : s)
                  .replace(/&/g, '&amp;')
                  .replace(/</g, '&lt;')
                  .replace(/>/g, '&gt;')
                  .replace(/"/g, '&quot;')
                  .replace(/'/g, '&#39;');
              }

              var title = document.title || ((document.querySelector('h1')||{}).innerText) || '';
              var byline = ((document.querySelector('[rel=author], .byline, .author')||{}).innerText) || '';
              var rawContent = best ? best.innerHTML : (document.body ? document.body.innerHTML : '');

              // --- Sanitize untrusted markup in a detached document ---
              // Whitelist of structural/text elements that are safe to keep.
              var ALLOWED_TAGS = {
                A:1, ABBR:1, ARTICLE:1, B:1, BLOCKQUOTE:1, BR:1, CAPTION:1, CITE:1,
                CODE:1, DD:1, DIV:1, DL:1, DT:1, EM:1, FIGCAPTION:1, FIGURE:1,
                H1:1, H2:1, H3:1, H4:1, H5:1, H6:1, HR:1, I:1, IMG:1, LI:1, MARK:1,
                OL:1, P:1, PRE:1, Q:1, S:1, SECTION:1, SMALL:1, SPAN:1, STRONG:1,
                SUB:1, SUP:1, TABLE:1, TBODY:1, TD:1, TFOOT:1, TH:1, THEAD:1,
                TIME:1, TR:1, U:1, UL:1
              };
              // Per-element attribute whitelist; everything else (incl. all on* handlers) is dropped.
              var ALLOWED_ATTRS = {
                A: { href:1, title:1 },
                IMG: { src:1, alt:1, title:1, width:1, height:1 },
                TD: { colspan:1, rowspan:1 },
                TH: { colspan:1, rowspan:1 }
              };
              function safeUrl(value){
                var v = String(value || '').replace(/[\u0000-\u001F\u007F\s]/g, '').toLowerCase();
                // Block javascript:, data:, vbscript:, blob:, file: and similar.
                if (/^(javascript|data|vbscript|blob|file):/i.test(v)) return false;
                return true;
              }
              function sanitize(root){
                // Walk a snapshot of descendants (live collection mutates as we remove).
                var nodes = root.querySelectorAll('*');
                for (var i = nodes.length - 1; i >= 0; i--){
                  var el = nodes[i];
                  var tag = el.tagName;
                  if (!ALLOWED_TAGS[tag]){
                    el.parentNode && el.parentNode.removeChild(el);
                    continue;
                  }
                  var allowed = ALLOWED_ATTRS[tag] || {};
                  var attrs = el.attributes;
                  for (var j = attrs.length - 1; j >= 0; j--){
                    var name = attrs[j].name;
                    var lname = name.toLowerCase();
                    var keep = !!allowed[lname];
                    if (keep && (lname === 'href' || lname === 'src')){
                      keep = safeUrl(attrs[j].value);
                    }
                    if (!keep) el.removeAttribute(name);
                  }
                }
                return root;
              }

              // DOMParser keeps the markup inert (no scripts run, no network) until sanitized.
              var doc = new DOMParser().parseFromString(
                '<div id="__helix_holder__">' + rawContent + '</div>', 'text/html');
              var holder = doc.getElementById('__helix_holder__') || doc.body;
              sanitize(holder);
              var cleanContent = holder ? holder.innerHTML : '';

              document.documentElement.innerHTML =
                '<head><meta name="viewport" content="width=device-width, initial-scale=1">' +
                "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; img-src * data:; style-src 'unsafe-inline'; font-src *\">" +
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
                '<h1>' + esc(title) + '</h1>' +
                (byline ? '<div class="byline">' + esc(byline) + '</div>' : '') +
                cleanContent +
                '</article></body>';
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
