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
}
