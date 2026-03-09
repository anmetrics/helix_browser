#!/usr/bin/env python3
"""Helix Browser - Fast, Secure, Private Web Browser for Linux"""

import sys
import gi
gi.require_version('Gtk', '4.0')
gi.require_version('Adw', '1')
gi.require_version('WebKit', '6.0')
from gi.repository import Gtk, Adw, Gio, GLib
from browser_window import BrowserWindow


class HelixBrowserApp(Adw.Application):
    def __init__(self):
        super().__init__(
            application_id="com.helix.browser.linux",
            flags=Gio.ApplicationFlags.HANDLES_OPEN
        )
        self.connect("activate", self.on_activate)
        self.connect("open", self.on_open)

        # Load CSS
        self._load_css()

    def _load_css(self):
        import os
        css_path = os.path.join(os.path.dirname(__file__), "style.css")
        if os.path.exists(css_path):
            provider = Gtk.CssProvider()
            provider.load_from_path(css_path)
            Gtk.StyleContext.add_provider_for_display(
                Gdk.Display.get_default() if hasattr(Gdk := __import__('gi').repository.Gdk, 'Display') else None,
                provider,
                Gtk.STYLE_PROVIDER_PRIORITY_APPLICATION
            )

    def on_activate(self, app):
        win = BrowserWindow(application=app)
        win.present()

    def on_open(self, app, files, n_files, hint):
        win = BrowserWindow(application=app)
        if files:
            win.load_url(files[0].get_uri())
        win.present()


def main():
    app = HelixBrowserApp()
    return app.run(sys.argv)


if __name__ == "__main__":
    sys.exit(main())
