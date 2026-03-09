"""URL utility functions for Helix Browser"""
from urllib.parse import quote_plus, urlparse


class UrlUtils:
    @staticmethod
    def is_url(input_str: str) -> bool:
        trimmed = input_str.strip()
        return (trimmed.startswith("http://") or trimmed.startswith("https://") or
                trimmed.startswith("ftp://") or ("." in trimmed and " " not in trimmed))

    @staticmethod
    def format_url(input_str: str, engine: str = "google") -> str:
        trimmed = input_str.strip()
        if not trimmed:
            return "helix://start"
        if trimmed.startswith("helix://"):
            return trimmed
        if trimmed.startswith(("http://", "https://", "file://", "about:")):
            return trimmed
        if UrlUtils.is_url(trimmed):
            return f"https://{trimmed}"
        return UrlUtils.build_search_query(trimmed, engine)

    @staticmethod
    def build_search_query(query: str, engine: str) -> str:
        encoded = quote_plus(query)
        engines = {
            "bing": f"https://www.bing.com/search?q={encoded}",
            "duckduckgo": f"https://duckduckgo.com/?q={encoded}",
            "yahoo": f"https://search.yahoo.com/search?p={encoded}",
            "brave": f"https://search.brave.com/search?q={encoded}",
        }
        return engines.get(engine.lower(), f"https://www.google.com/search?q={encoded}")

    @staticmethod
    def get_display_url(url: str) -> str:
        try:
            parsed = urlparse(url)
            host = parsed.hostname or url
            return host[4:] if host.startswith("www.") else host
        except Exception:
            return url

    @staticmethod
    def get_favicon_url(url: str) -> str:
        try:
            host = urlparse(url).hostname
            return f"https://www.google.com/s2/favicons?domain={host}&sz=64" if host else ""
        except Exception:
            return ""
