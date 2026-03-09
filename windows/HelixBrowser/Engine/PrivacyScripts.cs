namespace HelixBrowser;

public static class PrivacyScripts
{
    public const string AntiFingerprintingJs = @"
(function() {
    const origToDataURL = HTMLCanvasElement.prototype.toDataURL;
    HTMLCanvasElement.prototype.toDataURL = function(type) {
        if (this.width <= 16 && this.height <= 16) return origToDataURL.apply(this, arguments);
        const ctx = this.getContext('2d');
        if (ctx) {
            const d = ctx.getImageData(0, 0, this.width, this.height);
            for (let i = 0; i < d.data.length; i += 4) d.data[i] ^= (Math.random() * 2 | 0);
            ctx.putImageData(d, 0, 0);
        }
        return origToDataURL.apply(this, arguments);
    };
    const origGetParam = WebGLRenderingContext.prototype.getParameter;
    WebGLRenderingContext.prototype.getParameter = function(p) {
        if (p === 37445) return 'Helix Graphics';
        if (p === 37446) return 'Helix Renderer';
        return origGetParam.apply(this, arguments);
    };
    Object.defineProperty(navigator, 'hardwareConcurrency', { get: () => 4 });
    Object.defineProperty(navigator, 'deviceMemory', { get: () => 8 });
    Object.defineProperty(navigator, 'doNotTrack', { get: () => '1' });
})();";

    public const string TrackerBlockingJs = @"
(function() {
    const blocked = ['google-analytics.com','googletagmanager.com','connect.facebook.net',
        'hotjar.com','mixpanel.com','segment.io','optimizely.com','scorecardresearch.com',
        'quantserve.com','newrelic.com','nr-data.net','fullstory.com','mouseflow.com',
        'crazyegg.com','clarity.ms','heapanalytics.com','amplitude.com'];
    const origFetch = window.fetch;
    window.fetch = function(input) {
        const url = typeof input === 'string' ? input : input.url;
        for (const d of blocked) { if (url && url.includes(d)) return Promise.reject(new TypeError('Blocked')); }
        return origFetch.apply(this, arguments);
    };
    const origXHR = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function(m, url) {
        for (const d of blocked) { if (url && url.includes(d)) return; }
        return origXHR.apply(this, arguments);
    };
})();";

    public const string YoutubeAdBlockJs = @"
(function() {
    if (!location.hostname.includes('youtube.com')) return;
    var style = document.createElement('style');
    style.textContent = '' +
        '.ytp-ad-module, .ytp-ad-overlay-container, .ytp-ad-text-overlay,' +
        '.ytp-ad-player-overlay, .ytp-ad-player-overlay-instream-info,' +
        '.ytp-ad-survey, .ytp-ad-image-overlay,' +
        '#player-ads, #masthead-ad,' +
        'ytd-promoted-sparkles-web-renderer, ytd-display-ad-renderer,' +
        'ytd-ad-slot-renderer, ytd-in-feed-ad-layout-renderer,' +
        'ytd-banner-promo-renderer, ytd-promoted-video-renderer,' +
        'ytd-compact-promoted-video-renderer, ytd-video-masthead-ad-v3-renderer,' +
        'ytd-primetime-promo-renderer, .ytd-mealbar-promo-renderer,' +
        'ytd-statement-banner-renderer, .ytp-suggested-action,' +
        'ytd-merch-shelf-renderer,' +
        'ytd-engagement-panel-section-list-renderer[target-id=""engagement-panel-ads""]' +
        '{ display: none !important; height: 0 !important; overflow: hidden !important; }' +
        '.ytp-ad-skip-button-slot { opacity: 1 !important; }';
    (document.head || document.documentElement).appendChild(style);

    function skipAds() {
        var skipBtns = document.querySelectorAll(
            '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, ' +
            'button.ytp-ad-skip-button-modern, .ytp-ad-skip-button-container button'
        );
        skipBtns.forEach(function(btn) { try { btn.click(); } catch(e) {} });
        var video = document.querySelector('video.html5-main-video, .html5-video-player video');
        if (video) {
            var adPlaying = document.querySelector('.ad-showing, .ad-interrupting');
            if (adPlaying) {
                video.playbackRate = 16;
                video.muted = true;
                if (video.duration && isFinite(video.duration)) video.currentTime = video.duration;
            }
        }
        document.querySelectorAll('.ytp-ad-overlay-container, .ytp-ad-text-overlay').forEach(function(el) { el.remove(); });
    }

    var adObserver = new MutationObserver(skipAds);
    adObserver.observe(document.documentElement, { childList: true, subtree: true, attributes: true, attributeFilter: ['class'] });
    setInterval(skipAds, 500);

    var adUrlPatterns = ['/api/stats/ads', '/pagead/', '/get_midroll_info', '/ptracking',
        '/api/stats/atr', 'googleads.g.doubleclick.net', 'imasdk.googleapis.com',
        'securepubads.g.doubleclick.net', '/youtubei/v1/log_event', 'play.google.com/log'];
    var origFetch = window.fetch;
    window.fetch = function(input) {
        var url = typeof input === 'string' ? input : (input && input.url ? input.url : '');
        if (adUrlPatterns.some(function(p) { return url.includes(p); })) return Promise.resolve(new Response('', {status: 200}));
        return origFetch.apply(this, arguments);
    };
    var origXhrOpen = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function(method, url) {
        if (typeof url === 'string' && adUrlPatterns.some(function(p) { return url.includes(p); })) { this._blocked = true; }
        return origXhrOpen.apply(this, arguments);
    };
    var origXhrSend = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.send = function() {
        if (this._blocked) { this._blocked = false; return; }
        return origXhrSend.apply(this, arguments);
    };
})();";

    public const string CosmeticAdBlockJs = @"
(function() {
    var style = document.createElement('style');
    style.textContent = '' +
        '[id*=""google_ads""], [class*=""google-ad""],' +
        '[id*=""ad-container""], [class*=""ad-container""],' +
        '[id*=""adBanner""], [class*=""adBanner""], [class*=""ad-banner""],' +
        'iframe[src*=""doubleclick""], iframe[src*=""googlesyndication""],' +
        'ins.adsbygoogle, div[data-ad], div[data-ad-slot],' +
        '.ad-wrapper, .ad-unit, .ad-slot, .ad-block, .ad-placement,' +
        '.sponsored-content, .sponsored-ad, .native-ad,' +
        'amp-ad, amp-sticky-ad, amp-auto-ads' +
        '{ display: none !important; }';
    (document.head || document.documentElement).appendChild(style);
})();";

    public const string PopupBlockerJs = @"
(function() {
    var adDomains = ['popads.net','popcash.net','propellerads.com','juicyads.com',
        'exoclick.com','trafficjunky.net','revcontent.com','mgid.com',
        'adsterra.com','hilltopads.net','clickadu.com','ad-maven.com',
        'googlesyndication.com','doubleclick.net','adnxs.com','taboola.com','outbrain.com'];
    function isAdUrl(url) {
        if (!url) return false;
        try { var u = new URL(url, location.href); return adDomains.some(function(d) { return u.hostname.includes(d); }); }
        catch(e) { return false; }
    }
    var origOpen = window.open;
    window.open = function(url) {
        if (isAdUrl(url)) return null;
        if (!navigator.userActivation || !navigator.userActivation.isActive) {
            if (url && url !== 'about:blank') return null;
        }
        return origOpen.apply(this, arguments);
    };
    document.addEventListener('click', function(e) {
        var target = e.target.closest ? e.target.closest('a') : null;
        if (target && target.href && isAdUrl(target.href)) { e.preventDefault(); e.stopPropagation(); }
    }, true);
})();";
}
