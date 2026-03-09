"""Settings dialog for Helix Browser (GTK3)"""

import gi
gi.require_version("Gtk", "3.0")
from gi.repository import Gtk

from prefs import Prefs
from database import Database


class SettingsDialog(Gtk.Dialog):
    def __init__(self, parent=None):
        super().__init__(
            title="Settings - Helix Browser",
            transient_for=parent,
            modal=True,
            destroy_with_parent=True,
        )
        self.set_default_size(500, 600)
        self.add_button("Close", Gtk.ResponseType.CLOSE)

        self.prefs = Prefs()
        self.db = Database.get_instance()

        notebook = Gtk.Notebook()
        content = self.get_content_area()
        content.pack_start(notebook, True, True, 0)

        notebook.append_page(self._build_general_page(), Gtk.Label(label="General"))
        notebook.append_page(self._build_privacy_page(), Gtk.Label(label="Privacy"))
        notebook.append_page(self._build_tabs_page(), Gtk.Label(label="Tabs"))
        notebook.append_page(self._build_about_page(), Gtk.Label(label="About"))

        self.show_all()

    def _make_section(self, title):
        box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=8)
        box.set_margin_top(12)
        box.set_margin_bottom(6)
        box.set_margin_start(16)
        box.set_margin_end(16)
        label = Gtk.Label()
        label.set_markup(f"<b>{title}</b>")
        label.set_halign(Gtk.Align.START)
        box.pack_start(label, False, False, 0)
        return box

    def _make_switch_row(self, label_text, key):
        row = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=8)
        row.set_margin_start(8)
        row.set_margin_end(8)
        label = Gtk.Label(label=label_text)
        label.set_halign(Gtk.Align.START)
        label.set_hexpand(True)
        switch = Gtk.Switch()
        switch.set_active(getattr(self.prefs, key, True))
        switch.connect("notify::active", lambda sw, _: setattr(self.prefs, key, sw.get_active()))
        switch.set_valign(Gtk.Align.CENTER)
        row.pack_start(label, True, True, 0)
        row.pack_end(switch, False, False, 0)
        return row

    def _build_general_page(self):
        page = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=4)

        # Search engine
        section = self._make_section("Search Engine")
        combo = Gtk.ComboBoxText()
        engines = ["Google", "DuckDuckGo", "Bing", "Brave", "Yahoo"]
        engine_keys = ["google", "duckduckgo", "bing", "brave", "yahoo"]
        for e in engines:
            combo.append_text(e)
        current = self.prefs.search_engine
        combo.set_active(engine_keys.index(current) if current in engine_keys else 0)
        combo.connect("changed", lambda c: setattr(self.prefs, "search_engine", engine_keys[c.get_active()]))
        section.pack_start(combo, False, False, 0)
        page.pack_start(section, False, False, 0)

        # Homepage
        section = self._make_section("Homepage")
        entry = Gtk.Entry()
        entry.set_text(self.prefs.homepage)
        entry.connect("changed", lambda e: setattr(self.prefs, "homepage", e.get_text()))
        section.pack_start(entry, False, False, 0)
        page.pack_start(section, False, False, 0)

        return page

    def _build_privacy_page(self):
        page = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=4)

        section = self._make_section("Privacy Protection")
        toggles = [
            ("Block Trackers", "is_block_trackers"),
            ("Block 3rd Party Cookies", "is_block_third_party_cookies"),
            ("Do Not Track", "is_do_not_track"),
            ("HTTPS Upgrade", "is_https_upgrade"),
            ("Block Fingerprinting", "is_block_fingerprinting"),
            ("Block Popups", "is_block_popups"),
            ("Block Ads", "is_ad_block"),
        ]
        for label, key in toggles:
            section.pack_start(self._make_switch_row(label, key), False, False, 2)
        page.pack_start(section, False, False, 0)

        # Clear data
        section = self._make_section("Clear Data")
        for label, callback in [
            ("Clear History", lambda b: self._confirm_clear("history")),
            ("Clear Bookmarks", lambda b: self._confirm_clear("bookmarks")),
            ("Clear Downloads", lambda b: self._confirm_clear("downloads")),
        ]:
            btn = Gtk.Button(label=label)
            btn.connect("clicked", callback)
            section.pack_start(btn, False, False, 2)
        page.pack_start(section, False, False, 0)

        return page

    def _build_tabs_page(self):
        page = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=4)
        section = self._make_section("Tab Behavior")
        section.pack_start(self._make_switch_row("Restore tabs on startup", "is_restore_tabs"), False, False, 2)
        section.pack_start(self._make_switch_row("Suspend inactive tabs", "is_suspend_inactive"), False, False, 2)
        page.pack_start(section, False, False, 0)
        return page

    def _build_about_page(self):
        page = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=12)
        page.set_margin_top(40)
        page.set_valign(Gtk.Align.CENTER)

        title = Gtk.Label()
        title.set_markup("<span size='xx-large' weight='bold'>Helix Browser</span>")
        page.pack_start(title, False, False, 0)

        ver = Gtk.Label(label="Version 3.0.0")
        ver.get_style_context().add_class("dim-label")
        page.pack_start(ver, False, False, 0)

        engine = Gtk.Label(label="Engine: WebKitGTK 2")
        engine.get_style_context().add_class("dim-label")
        page.pack_start(engine, False, False, 0)

        platform = Gtk.Label(label="Platform: Linux (GTK3)")
        platform.get_style_context().add_class("dim-label")
        page.pack_start(platform, False, False, 0)

        return page

    def _confirm_clear(self, data_type):
        dialog = Gtk.MessageDialog(
            transient_for=self,
            modal=True,
            message_type=Gtk.MessageType.WARNING,
            buttons=Gtk.ButtonsType.YES_NO,
            text=f"Clear all {data_type}?",
        )
        dialog.format_secondary_text("This action cannot be undone.")
        response = dialog.run()
        dialog.destroy()
        if response == Gtk.ResponseType.YES:
            if data_type == "history":
                self.db.clear_history()
            elif data_type == "bookmarks":
                self.db.clear_bookmarks()
            elif data_type == "downloads":
                self.db.clear_downloads()
