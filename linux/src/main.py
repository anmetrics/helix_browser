#!/usr/bin/env python3
"""Helix Browser - Fast, Secure, Private Web Browser for Linux"""

import os
import sys
import gi
gi.require_version('Gtk', '3.0')
gi.require_version('WebKit2', '4.0')
from gi.repository import Gtk, Gio, GLib, Gdk
from browser_window import BrowserWindow


class HelixBrowserApp(Gtk.Application):
    def __init__(self):
        super().__init__(
            application_id="com.helix.browser.linux",
            flags=Gio.ApplicationFlags.HANDLES_OPEN
        )
        self.connect("activate", self.on_activate)
        self.connect("open", self.on_open)

    def do_startup(self):
        Gtk.Application.do_startup(self)
        self._load_css()

    def _load_css(self):
        # Force minimal GTK theme so Ubuntu/GNOME theme doesn't override our styles
        settings = Gtk.Settings.get_default()
        if settings:
            settings.set_property("gtk-theme-name", "Adwaita")
            settings.set_property("gtk-application-prefer-dark-theme", True)

        css_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "style.css")
        if os.path.exists(css_path):
            provider = Gtk.CssProvider()
            provider.load_from_path(css_path)
            screen = Gdk.Screen.get_default()
            if screen:
                Gtk.StyleContext.add_provider_for_screen(
                    screen, provider, Gtk.STYLE_PROVIDER_PRIORITY_USER + 100
                )

    def on_activate(self, app):
        win = BrowserWindow(application=app)
        win.show_all()

    def on_open(self, app, files, n_files, hint):
        win = BrowserWindow(application=app)
        if files:
            win.load_url(files[0].get_uri())
        win.show_all()


def main():
    app = HelixBrowserApp()
    return app.run(sys.argv)


if __name__ == "__main__":
    sys.exit(main())
