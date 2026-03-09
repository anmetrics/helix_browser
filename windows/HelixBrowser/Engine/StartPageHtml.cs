namespace HelixBrowser;

public static class StartPageHtml
{
    public static string Generate() => @"<!DOCTYPE html>
<html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>
<style>
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:'Segoe UI',system-ui,sans-serif;background:#0A091E;color:#fff;display:flex;flex-direction:column;align-items:center;min-height:100vh;padding:60px 20px}
.logo{font-size:72px;margin-bottom:16px;background:linear-gradient(135deg,#8B8BFF,#FF7EB3);-webkit-background-clip:text;-webkit-text-fill-color:transparent}
h1{font-size:28px;font-weight:700;margin-bottom:6px}
.sub{color:#A0A0D0;font-size:14px;margin-bottom:40px}
.grid{display:grid;grid-template-columns:repeat(4,1fr);gap:20px;max-width:500px;margin-bottom:40px}
.fav{display:flex;flex-direction:column;align-items:center;gap:8px;cursor:pointer;text-decoration:none}
.fav-icon{width:56px;height:56px;border-radius:14px;background:rgba(255,255,255,0.04);border:1px solid rgba(255,255,255,0.06);display:flex;align-items:center;justify-content:center;font-size:24px;transition:all .2s}
.fav-icon:hover{background:rgba(255,255,255,0.08);transform:scale(1.08)}
.fav span{font-size:11px;color:#A0A0D0;font-weight:500}
</style></head><body>
<div class='logo'>🌐</div>
<h1>Helix Browser</h1>
<p class='sub'>Nhanh. An toàn. Riêng tư.</p>
<div class='grid'>
<a class='fav' href='https://www.google.com'><div class='fav-icon'>🔍</div><span>Google</span></a>
<a class='fav' href='https://www.facebook.com'><div class='fav-icon'>👥</div><span>Facebook</span></a>
<a class='fav' href='https://www.youtube.com'><div class='fav-icon'>▶️</div><span>YouTube</span></a>
<a class='fav' href='https://github.com'><div class='fav-icon'>💻</div><span>GitHub</span></a>
<a class='fav' href='https://twitter.com'><div class='fav-icon'>💬</div><span>Twitter</span></a>
<a class='fav' href='https://www.reddit.com'><div class='fav-icon'>📰</div><span>Reddit</span></a>
<a class='fav' href='https://www.wikipedia.org'><div class='fav-icon'>📖</div><span>Wikipedia</span></a>
<a class='fav' href='https://www.netflix.com'><div class='fav-icon'>🎬</div><span>Netflix</span></a>
</div>
</body></html>";
}
