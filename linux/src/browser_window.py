"""Main browser window for Helix Browser on Linux (GTK3 + WebKit2)"""

import gi
gi.require_version('Gtk', '3.0')
gi.require_version('WebKit2', '4.0')
from gi.repository import Gtk, WebKit2, Gio, GLib, Gdk, Pango
from url_utils import UrlUtils
from prefs import Prefs
from ad_block import AdBlockEngine
from privacy_manager import PrivacyManager
from tab_manager import TabManager, BrowserTab
from database import Database


class BrowserWindow(Gtk.ApplicationWindow):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.set_default_size(1200, 800)
        self.set_title("Helix Browser")

        self.prefs = Prefs()
        self.ad_block = AdBlockEngine()
        self.privacy = PrivacyManager()
        self.tab_manager = TabManager()
        self.db = Database.get_instance()
        self._current_webview = None

        self._build_ui()
        self._setup_shortcuts()

        # Restore previous session or create new tab
        if self.prefs.is_restore_tabs and self.tab_manager.restore_session():
            for tab in self.tab_manager.tabs:
                webview = self._create_webview(tab.is_incognito)
                tab.webview = webview
                if tab.url and tab.url != "helix://start":
                    webview.load_uri(tab.url)
                else:
                    webview.load_html(self._start_page_html(), "helix://start")
            active = self.tab_manager.active_tab
            if active:
                self._switch_to_tab(active)
            self._update_tab_bar()
        else:
            self._new_tab()

        # Save session on close
        self.connect("delete-event", self._on_window_close)

    def _build_ui(self):
        main_box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL)
        self.add(main_box)

        # --- Header bar ---
        self.header = Gtk.HeaderBar()
        self.header.set_show_close_button(True)
        self.header.set_title("Helix Browser")
        self.header.get_style_context().add_class("helix-toolbar")
        self.set_titlebar(self.header)

        # Nav buttons
        nav_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=2)
        self.back_btn = Gtk.Button.new_from_icon_name("go-previous-symbolic", Gtk.IconSize.SMALL_TOOLBAR)
        self.back_btn.set_tooltip_text("Back")
        self.back_btn.connect("clicked", lambda b: self._go_back())
        self.forward_btn = Gtk.Button.new_from_icon_name("go-next-symbolic", Gtk.IconSize.SMALL_TOOLBAR)
        self.forward_btn.set_tooltip_text("Forward")
        self.forward_btn.connect("clicked", lambda b: self._go_forward())
        self.reload_btn = Gtk.Button.new_from_icon_name("view-refresh-symbolic", Gtk.IconSize.SMALL_TOOLBAR)
        self.reload_btn.set_tooltip_text("Reload")
        self.reload_btn.connect("clicked", lambda b: self._reload())
        self.home_btn = Gtk.Button.new_from_icon_name("go-home-symbolic", Gtk.IconSize.SMALL_TOOLBAR)
        self.home_btn.set_tooltip_text("Home")
        self.home_btn.connect("clicked", lambda b: self.load_url(self.prefs.homepage))
        nav_box.pack_start(self.back_btn, False, False, 0)
        nav_box.pack_start(self.forward_btn, False, False, 0)
        nav_box.pack_start(self.reload_btn, False, False, 0)
        nav_box.pack_start(self.home_btn, False, False, 0)
        self.header.pack_start(nav_box)

        # Address bar
        self.url_entry = Gtk.Entry()
        self.url_entry.set_placeholder_text("Search or enter URL")
        self.url_entry.set_hexpand(True)
        self.url_entry.get_style_context().add_class("helix-address-bar")
        self.url_entry.connect("activate", self._on_url_activate)
        self.header.set_custom_title(self.url_entry)

        # Right buttons
        right_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=2)
        self.bookmark_btn = Gtk.Button.new_from_icon_name("starred-symbolic", Gtk.IconSize.SMALL_TOOLBAR)
        self.bookmark_btn.set_tooltip_text("Bookmark (Ctrl+D)")
        self.bookmark_btn.connect("clicked", lambda b: self._toggle_bookmark())

        self.tabs_btn = Gtk.Button(label="1")
        self.tabs_btn.set_tooltip_text("Tabs")
        self.tabs_btn.get_style_context().add_class("helix-tab-count")

        menu_btn = Gtk.MenuButton()
        menu_btn.set_image(Gtk.Image.new_from_icon_name("open-menu-symbolic", Gtk.IconSize.SMALL_TOOLBAR))
        menu_btn.set_menu_model(self._build_menu())

        right_box.pack_start(self.bookmark_btn, False, False, 0)
        right_box.pack_start(self.tabs_btn, False, False, 0)
        right_box.pack_start(menu_btn, False, False, 0)
        self.header.pack_end(right_box)

        # --- Tab bar ---
        tab_bar_container = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL)
        tab_bar_container.get_style_context().add_class("helix-tab-bar")

        tab_scroll = Gtk.ScrolledWindow()
        tab_scroll.set_policy(Gtk.PolicyType.AUTOMATIC, Gtk.PolicyType.NEVER)
        tab_scroll.set_hexpand(True)
        self.tab_bar_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=1)
        tab_scroll.add(self.tab_bar_box)
        tab_bar_container.pack_start(tab_scroll, True, True, 0)

        new_tab_btn = Gtk.Button.new_from_icon_name("list-add-symbolic", Gtk.IconSize.SMALL_TOOLBAR)
        new_tab_btn.set_tooltip_text("New Tab (Ctrl+T)")
        new_tab_btn.connect("clicked", lambda b: self._new_tab())
        new_tab_btn.get_style_context().add_class("helix-new-tab-btn")
        tab_bar_container.pack_end(new_tab_btn, False, False, 0)

        main_box.pack_start(tab_bar_container, False, False, 0)

        # Progress bar
        self.progress_bar = Gtk.ProgressBar()
        self.progress_bar.get_style_context().add_class("helix-progress")
        main_box.pack_start(self.progress_bar, False, False, 0)

        # WebView container
        self.webview_container = Gtk.Box(orientation=Gtk.Orientation.VERTICAL)
        self.webview_container.set_vexpand(True)
        main_box.pack_start(self.webview_container, True, True, 0)

        # Find bar
        self.find_bar = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=4)
        self.find_bar.get_style_context().add_class("helix-find-bar")
        self.find_bar.set_no_show_all(True)
        self.find_bar.hide()
        self.find_entry = Gtk.Entry()
        self.find_entry.set_placeholder_text("Find on page...")
        self.find_entry.set_hexpand(True)
        self.find_entry.connect("activate", self._on_find)
        find_next = Gtk.Button.new_from_icon_name("go-down-symbolic", Gtk.IconSize.SMALL_TOOLBAR)
        find_next.connect("clicked", lambda b: self._find_next())
        find_prev = Gtk.Button.new_from_icon_name("go-up-symbolic", Gtk.IconSize.SMALL_TOOLBAR)
        find_prev.connect("clicked", lambda b: self._find_prev())
        find_close = Gtk.Button.new_from_icon_name("window-close-symbolic", Gtk.IconSize.SMALL_TOOLBAR)
        find_close.connect("clicked", lambda b: self.find_bar.hide())
        self.find_bar.pack_start(self.find_entry, True, True, 0)
        self.find_bar.pack_start(find_prev, False, False, 0)
        self.find_bar.pack_start(find_next, False, False, 0)
        self.find_bar.pack_start(find_close, False, False, 0)
        main_box.pack_end(self.find_bar, False, False, 0)

    def _build_menu(self):
        menu = Gio.Menu()
        menu.append("New Tab", "win.new-tab")
        menu.append("New Incognito Tab", "win.new-incognito-tab")
        section2 = Gio.Menu()
        section2.append("History", "win.show-history")
        section2.append("Bookmarks", "win.show-bookmarks")
        section2.append("Find on Page", "win.find")
        menu.append_section(None, section2)
        section3 = Gio.Menu()
        section3.append("Settings", "win.settings")
        menu.append_section(None, section3)

        for name, callback in [
            ("new-tab", lambda *a: self._new_tab()),
            ("new-incognito-tab", lambda *a: self._new_tab(incognito=True)),
            ("show-history", lambda *a: self._show_history()),
            ("show-bookmarks", lambda *a: self._show_bookmarks()),
            ("find", lambda *a: self._toggle_find()),
            ("settings", lambda *a: self._show_settings()),
        ]:
            action = Gio.SimpleAction(name=name)
            action.connect("activate", callback)
            self.add_action(action)

        return menu

    def _setup_shortcuts(self):
        accel = Gtk.AccelGroup()
        self.add_accel_group(accel)
        shortcuts = {
            "<Control>t": lambda *a: self._new_tab(),
            "<Control>w": lambda *a: self._close_current_tab(),
            "<Control>l": lambda *a: self.url_entry.grab_focus(),
            "<Control>r": lambda *a: self._reload(),
            "<Control>f": lambda *a: self._toggle_find(),
            "<Control>d": lambda *a: self._toggle_bookmark(),
        }
        for accel_str, callback in shortcuts.items():
            key, mods = Gtk.accelerator_parse(accel_str)
            accel.connect(key, mods, Gtk.AccelFlags.VISIBLE, lambda *a, cb=callback: cb())

    # --- Tab Management ---

    def _new_tab(self, url="helix://start", incognito=False):
        tab = self.tab_manager.create_tab(url, incognito)
        webview = self._create_webview(incognito)
        tab.webview = webview

        if url != "helix://start":
            webview.load_uri(UrlUtils.format_url(url, self.prefs.search_engine))
        else:
            webview.load_html(self._start_page_html(), "helix://start")

        self._switch_to_tab(tab)
        self._update_tab_bar()

    def _close_current_tab(self):
        if len(self.tab_manager.tabs) <= 1:
            return
        idx = self.tab_manager.active_tab_index
        active = self.tab_manager.active_tab
        if active and not active.is_pinned:
            self.tab_manager.close_tab(idx)
            new_active = self.tab_manager.active_tab
            if new_active:
                self._switch_to_tab(new_active)
            self._update_tab_bar()

    def _switch_to_tab(self, tab):
        idx = next((i for i, t in enumerate(self.tab_manager.tabs) if t.id == tab.id), -1)
        if idx >= 0:
            self.tab_manager.switch_to_tab(idx)
        # Remove current webview
        for child in self.webview_container.get_children():
            self.webview_container.remove(child)
        # Add tab's webview
        if tab.webview:
            self.webview_container.pack_start(tab.webview, True, True, 0)
            tab.webview.show()
            self._current_webview = tab.webview
        self.url_entry.set_text(tab.url if not tab.url.startswith("helix://") else "")
        self._update_nav_buttons()
        self._update_tab_bar()

    def _update_tab_bar(self):
        for child in self.tab_bar_box.get_children():
            self.tab_bar_box.remove(child)

        for i, tab in enumerate(self.tab_manager.tabs):
            is_active = (i == self.tab_manager.active_tab_index)
            btn = Gtk.Button()
            ctx = btn.get_style_context()
            ctx.add_class("helix-tab")
            if is_active:
                ctx.add_class("active")
            if tab.is_incognito:
                ctx.add_class("incognito")

            box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=4)
            if tab.is_pinned:
                pin_icon = Gtk.Image.new_from_icon_name("view-pin-symbolic", Gtk.IconSize.MENU)
                box.pack_start(pin_icon, False, False, 0)
            label = Gtk.Label(label=tab.title[:20] if tab.title else "New Tab")
            label.set_ellipsize(Pango.EllipsizeMode.END)
            label.set_max_width_chars(18)
            box.pack_start(label, True, True, 0)

            if not tab.is_pinned:
                close_btn = Gtk.Button.new_from_icon_name("window-close-symbolic", Gtk.IconSize.MENU)
                close_btn.get_style_context().add_class("helix-tab-close")
                tab_idx = i
                close_btn.connect("clicked", lambda b, idx=tab_idx: self._close_tab_by_index(idx))
                box.pack_end(close_btn, False, False, 0)

            btn.add(box)
            tab_ref = tab
            btn.connect("clicked", lambda b, t=tab_ref: self._switch_to_tab(t))
            self.tab_bar_box.pack_start(btn, False, False, 0)

        self.tabs_btn.set_label(str(len(self.tab_manager.tabs)))
        self.tab_bar_box.show_all()

    def _close_tab_by_index(self, idx):
        if len(self.tab_manager.tabs) <= 1:
            return
        tab = self.tab_manager.tabs[idx] if 0 <= idx < len(self.tab_manager.tabs) else None
        if tab and not tab.is_pinned:
            self.tab_manager.close_tab(idx)
            if self.tab_manager.active_tab:
                self._switch_to_tab(self.tab_manager.active_tab)
            self._update_tab_bar()

    # --- WebView ---

    def _create_webview(self, incognito=False):
        if incognito:
            ctx = WebKit2.WebContext.new_ephemeral()
            webview = WebKit2.WebView.new_with_context(ctx)
        else:
            webview = WebKit2.WebView()

        settings = webview.get_settings()
        settings.set_enable_javascript(True)
        settings.set_enable_developer_extras(True)
        settings.set_allow_modal_dialogs(True)
        settings.set_enable_media_stream(True)
        settings.set_enable_mediasource(True)

        if self.prefs.is_desktop_mode:
            settings.set_user_agent_with_application_details("HelixBrowser", "3.0")

        webview.set_vexpand(True)
        webview.set_hexpand(True)

        webview.connect("load-changed", self._on_load_changed)
        webview.connect("notify::title", self._on_title_changed)
        webview.connect("notify::uri", self._on_uri_changed)
        webview.connect("notify::estimated-load-progress", self._on_progress)
        webview.connect("decide-policy", self._on_decide_policy)

        # Inject privacy scripts
        ucm = webview.get_user_content_manager()
        all_scripts = self.privacy.get_all_scripts(self.prefs)
        if all_scripts.strip():
            script = WebKit2.UserScript(
                all_scripts,
                WebKit2.UserContentInjectedFrames.ALL_FRAMES,
                WebKit2.UserScriptInjectionTime.START,
                None, None
            )
            ucm.add_script(script)

        return webview

    def _on_load_changed(self, webview, event):
        if event == WebKit2.LoadEvent.FINISHED:
            self.progress_bar.set_fraction(0)
            self._update_nav_buttons()
            tab = self.tab_manager.active_tab
            if tab and self.prefs.is_save_history and not tab.is_incognito:
                uri = webview.get_uri() or ""
                title = webview.get_title() or ""
                if uri and not uri.startswith("helix://"):
                    self.db.add_history(uri, title)

    def _on_title_changed(self, webview, param):
        tab = self.tab_manager.active_tab
        if tab:
            tab.title = webview.get_title() or UrlUtils.get_display_url(tab.url)
            self._update_tab_bar()
            self.set_title(tab.title + " - Helix Browser")

    def _on_uri_changed(self, webview, param):
        uri = webview.get_uri() or ""
        tab = self.tab_manager.active_tab
        if tab:
            tab.url = uri
        if not uri.startswith("helix://"):
            self.url_entry.set_text(uri)
        self._update_ssl_icon(uri)

    def _on_progress(self, webview, param):
        progress = webview.get_estimated_load_progress()
        self.progress_bar.set_fraction(progress if progress < 1.0 else 0)

    def _on_decide_policy(self, webview, decision, decision_type):
        if decision_type == WebKit2.PolicyDecisionType.NAVIGATION_ACTION:
            nav = decision.get_navigation_action()
            req = nav.get_request()
            uri = req.get_uri()

            if self.prefs.is_https_upgrade and uri and uri.startswith("http://"):
                decision.ignore()
                webview.load_uri(uri.replace("http://", "https://", 1))
                return True

            if self.prefs.is_ad_block and self.ad_block.should_block(uri):
                decision.ignore()
                return True

        return False

    # --- Navigation ---

    def load_url(self, url):
        formatted = UrlUtils.format_url(url, self.prefs.search_engine)
        tab = self.tab_manager.active_tab
        if tab:
            tab.url = formatted
        if formatted.startswith("helix://"):
            if self._current_webview:
                self._current_webview.load_html(self._start_page_html(), "helix://start")
        elif self._current_webview:
            self._current_webview.load_uri(formatted)
        self.url_entry.set_text(formatted if not formatted.startswith("helix://") else "")

    def _on_url_activate(self, entry):
        text = entry.get_text().strip()
        if text:
            self.load_url(text)

    def _go_back(self):
        if self._current_webview and self._current_webview.can_go_back():
            self._current_webview.go_back()

    def _go_forward(self):
        if self._current_webview and self._current_webview.can_go_forward():
            self._current_webview.go_forward()

    def _reload(self):
        if self._current_webview:
            self._current_webview.reload()

    def _update_nav_buttons(self):
        if self._current_webview:
            self.back_btn.set_sensitive(self._current_webview.can_go_back())
            self.forward_btn.set_sensitive(self._current_webview.can_go_forward())

    def _update_ssl_icon(self, url):
        is_secure = url.startswith("https://")
        self.url_entry.set_icon_from_icon_name(
            Gtk.EntryIconPosition.PRIMARY,
            "channel-secure-symbolic" if is_secure else "dialog-information-symbolic"
        )

    # --- Find ---

    def _toggle_find(self):
        if self.find_bar.get_visible():
            self.find_bar.hide()
        else:
            self.find_bar.show_all()
            self.find_entry.grab_focus()

    def _on_find(self, entry):
        text = entry.get_text()
        if self._current_webview and text:
            finder = self._current_webview.get_find_controller()
            finder.search(text, WebKit2.FindOptions.CASE_INSENSITIVE | WebKit2.FindOptions.WRAP_AROUND, 100)

    def _find_next(self):
        if self._current_webview:
            self._current_webview.get_find_controller().search_next()

    def _find_prev(self):
        if self._current_webview:
            self._current_webview.get_find_controller().search_previous()

    # --- Bookmarks ---

    def _toggle_bookmark(self):
        tab = self.tab_manager.active_tab
        if not tab:
            return
        if self.db.is_bookmarked(tab.url):
            self.db.remove_bookmark(tab.url)
            self.bookmark_btn.set_image(Gtk.Image.new_from_icon_name("non-starred-symbolic", Gtk.IconSize.SMALL_TOOLBAR))
        else:
            self.db.add_bookmark(tab.url, tab.title)
            self.bookmark_btn.set_image(Gtk.Image.new_from_icon_name("starred-symbolic", Gtk.IconSize.SMALL_TOOLBAR))

    # --- Dialogs ---

    def _show_history(self):
        pass

    def _show_bookmarks(self):
        pass

    def _show_settings(self):
        from settings_dialog import SettingsDialog
        dialog = SettingsDialog(parent=self)
        dialog.run()
        dialog.destroy()

    # --- Session ---

    def _on_window_close(self, window, event):
        if self.prefs.is_restore_tabs:
            self.tab_manager.save_session()
        return False

    # --- Start Page ---

    def _start_page_html(self):
        return """<!DOCTYPE html>
<html><head><meta charset='utf-8'><style>
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:system-ui,sans-serif;background:#0A091E;color:#fff;display:flex;flex-direction:column;align-items:center;min-height:100vh;padding:60px 20px}
.logo{font-size:72px;margin-bottom:16px}
h1{font-size:28px;font-weight:700;margin-bottom:6px}
.sub{color:#A0A0D0;font-size:14px;margin-bottom:40px}
.grid{display:grid;grid-template-columns:repeat(4,1fr);gap:20px;max-width:500px}
.fav{display:flex;flex-direction:column;align-items:center;gap:8px;cursor:pointer;text-decoration:none}
.fav-icon{width:56px;height:56px;border-radius:14px;background:rgba(255,255,255,0.04);border:1px solid rgba(255,255,255,0.06);display:flex;align-items:center;justify-content:center;font-size:24px;transition:all .2s}
.fav-icon:hover{background:rgba(255,255,255,0.08);transform:scale(1.08)}
.fav span{font-size:11px;color:#A0A0D0}
</style></head><body>
<div class='logo'>&#127760;</div>
<h1>Helix Browser</h1>
<p class='sub'>Fast. Secure. Private.</p>
<div class='grid'>
<a class='fav' href='https://www.google.com'><div class='fav-icon'>G</div><span>Google</span></a>
<a class='fav' href='https://www.facebook.com'><div class='fav-icon'>f</div><span>Facebook</span></a>
<a class='fav' href='https://www.youtube.com'><div class='fav-icon'>&#9654;</div><span>YouTube</span></a>
<a class='fav' href='https://github.com'><div class='fav-icon'>&lt;/&gt;</div><span>GitHub</span></a>
<a class='fav' href='https://twitter.com'><div class='fav-icon'>X</div><span>Twitter</span></a>
<a class='fav' href='https://www.reddit.com'><div class='fav-icon'>R</div><span>Reddit</span></a>
<a class='fav' href='https://www.wikipedia.org'><div class='fav-icon'>W</div><span>Wikipedia</span></a>
<a class='fav' href='https://www.netflix.com'><div class='fav-icon'>N</div><span>Netflix</span></a>
</div></body></html>"""
