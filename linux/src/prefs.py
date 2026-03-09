"""Preferences manager for Helix Browser"""
import json
import os


class Prefs:
    _DEFAULTS = {
        "search_engine": "google",
        "homepage": "https://www.google.com",
        "is_ad_block": True,
        "is_save_history": True,
        "is_desktop_mode": False,
        "is_block_trackers": True,
        "is_block_third_party_cookies": True,
        "is_do_not_track": True,
        "is_https_upgrade": True,
        "is_block_fingerprinting": True,
        "is_block_popups": True,
        "is_restore_tabs": True,
        "default_zoom": 100,
    }

    def __init__(self):
        config_dir = os.path.join(os.path.expanduser("~"), ".config", "helix-browser")
        os.makedirs(config_dir, exist_ok=True)
        self._path = os.path.join(config_dir, "prefs.json")
        self._data = dict(self._DEFAULTS)
        self._load()

    def _load(self):
        if os.path.exists(self._path):
            try:
                with open(self._path) as f:
                    saved = json.load(f)
                    self._data.update(saved)
            except Exception:
                pass

    def _save(self):
        with open(self._path, "w") as f:
            json.dump(self._data, f, indent=2)

    def __getattr__(self, name):
        if name.startswith("_") or name in ("_data", "_path", "_load", "_save"):
            return super().__getattribute__(name)
        if name in self._data:
            return self._data[name]
        raise AttributeError(f"No pref '{name}'")

    def __setattr__(self, name, value):
        if name.startswith("_"):
            super().__setattr__(name, value)
        else:
            self._data[name] = value
            self._save()
