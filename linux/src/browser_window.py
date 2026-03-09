"""Main browser window for Helix Browser on Linux"""

import gi
gi.require_version('Gtk', '4.0')
gi.require_version('Adw', '1')
gi.require_version('WebKit', '6.0')
from gi.repository import Gtk, Adw, WebKit, Gio, GLib, Gdk
from url_utils import UrlUtils
from prefs import Prefs
from ad_block import AdBlockEngine
from privacy_manager import PrivacyManager
from tab_manager import TabManager, BrowserTab
from database import Database
import json


class BrowserWindow(Adw.ApplicationWindow):
    def __init__(self, **kwargs):
        super().__init__(**kwargs, default_width=1200, default_height=800, title="Helix Browser")

        self.prefs = Prefs()
        self.ad_block = AdBlockEngine()
        self.privacy = PrivacyManager()
        self.tab_manager = TabManager(self.prefs)
        self.db = Database()
        self._current_webview = None

        self._build_ui()
        self._setup_shortcuts()

        # Create initial tab
        self._new_tab()

    def _build_ui(self):
        # Main vertical box
        main_box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL)
        self.set_content(main_box)

        # Header bar with navigation
        self.header = Adw.HeaderBar()
        self.header.add_css_class("helix-toolbar")

        # Navigation buttons
        nav_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=2)
        self.back_btn = Gtk.Button(icon_name="go-previous-symbolic", tooltip_text="Quay lại")
        self.back_btn.connect("clicked", lambda b: self._go_back())
        self.forward_btn = Gtk.Button(icon_name="go-next-symbolic", tooltip_text="Tiến tới")
        self.forward_btn.connect("clicked", lambda b: self._go_forward())
        self.reload_btn = Gtk.Button(icon_name="view-refresh-symbolic", tooltip_text="Tải lại")
        self.reload_btn.connect("clicked", lambda b: self._reload())
        self.home_btn = Gtk.Button(icon_name="go-home-symbolic", tooltip_text="Trang chủ")
        self.home_btn.connect("clicked", lambda b: self.load_url(self.prefs.homepage))
        nav_box.append(self.back_btn)
        nav_box.append(self.forward_btn)
        nav_box.append(self.reload_btn)
        nav_box.append(self.home_btn)
        self.header.pack_start(nav_box)

        # Address bar
        self.url_entry = Gtk.Entry()
        self.url_entry.set_placeholder_text("Tìm kiếm hoặc nhập địa chỉ")
        self.url_entry.set_hexpand(True)
        self.url_entry.add_css_class("helix-address-bar")
        self.url_entry.connect("activate", self._on_url_activate)
        self.header.set_title_widget(self.url_entry)

        # Right side buttons
        right_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=2)
        self.bookmark_btn = Gtk.Button(icon_name="starred-symbolic", tooltip_text="Dấu trang (Ctrl+D)")
        self.bookmark_btn.connect("clicked", lambda b: self._toggle_bookmark())

        self.tabs_btn = Gtk.Button(label="1", tooltip_text="Tab")
        self.tabs_btn.add_css_class("helix-tab-count")

        menu_btn = Gtk.MenuButton(icon_name="open-menu-symbolic")
        menu_btn.set_menu_model(self._build_menu())

        right_box.append(self.bookmark_btn)
        right_box.append(self.tabs_btn)
        right_box.append(menu_btn)
        self.header.pack_end(right_box)

        main_box.append(self.header)

        # Tab bar
        self.tab_bar_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=1)
        self.tab_bar_box.add_css_class("helix-tab-bar")
        tab_scroll = Gtk.ScrolledWindow(hscrollbar_policy=Gtk.PolicyType.AUTOMATIC, vscrollbar_policy=Gtk.PolicyType.NEVER)
        tab_scroll.set_child(self.tab_bar_box)
        tab_scroll.set_max_content_height(36)

        tab_bar_container = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL)
        tab_bar_container.append(tab_scroll)
        tab_scroll.set_hexpand(True)
        new_tab_btn = Gtk.Button(icon_name="list-add-symbolic", tooltip_text="Tab mới (Ctrl+T)")
        new_tab_btn.connect("clicked", lambda b: self._new_tab())
        new_tab_btn.add_css_class("helix-new-tab-btn")
        tab_bar_container.append(new_tab_btn)
        main_box.append(tab_bar_container)

        # Progress bar
        self.progress_bar = Gtk.ProgressBar()
        self.progress_bar.add_css_class("helix-progress")
        main_box.append(self.progress_bar)

        # WebView container
        self.webview_container = Gtk.Box(orientation=Gtk.Orientation.VERTICAL)
        self.webview_container.set_vexpand(True)
        main_box.append(self.webview_container)

        # Find bar
        self.find_bar = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=4)
        self.find_bar.add_css_class("helix-find-bar")
        self.find_bar.set_visible(False)
        self.find_entry = Gtk.Entry(placeholder_text="Tìm trên trang...")
        self.find_entry.set_hexpand(True)
        self.find_entry.connect("activate", self._on_find)
        find_next = Gtk.Button(icon_name="go-down-symbolic")
        find_next.connect("clicked", lambda b: self._find_next())
        find_prev = Gtk.Button(icon_name="go-up-symbolic")
        find_prev.connect("clicked", lambda b: self._find_prev())
        find_close = Gtk.Button(icon_name="window-close-symbolic")
        find_close.connect("clicked", lambda b: self.find_bar.set_visible(False))
        self.find_bar.append(self.find_entry)
        self.find_bar.append(find_prev)
        self.find_bar.append(find_next)
        self.find_bar.append(find_close)
        main_box.append(self.find_bar)

    def _build_menu(self):
        menu = Gio.Menu()
        menu.append("Tab mới", "win.new-tab")
        menu.append("Tab ẩn danh mới", "win.new-incognito-tab")
        section2 = Gio.Menu()
        section2.append("Lịch sử", "win.show-history")
        section2.append("Dấu trang", "win.show-bookmarks")
        section2.append("Tìm trên trang", "win.find")
        menu.append_section(None, section2)
        section3 = Gio.Menu()
        section3.append("Cài đặt", "win.settings")
        menu.append_section(None, section3)

        # Actions
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
        controller = Gtk.ShortcutController()
        shortcuts = [
            ("Ctrl+T", lambda *a: self._new_tab()),
            ("Ctrl+W", lambda *a: self._close_current_tab()),
            ("Ctrl+L", lambda *a: self.url_entry.grab_focus()),
            ("Ctrl+R", lambda *a: self._reload()),
            ("Ctrl+F", lambda *a: self._toggle_find()),
            ("Ctrl+D", lambda *a: self._toggle_bookmark()),
        ]
        # GTK4 shortcut setup would go here
        self.add_controller(controller)

    # MARK: - Tab Management

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
        active = self.tab_manager.active_tab
        if active and not active.is_pinned:
            self.tab_manager.close_tab(active.id)
            new_active = self.tab_manager.active_tab
            if new_active:
                self._switch_to_tab(new_active)
            self._update_tab_bar()

    def _switch_to_tab(self, tab):
        self.tab_manager.switch_to_tab(tab.id)
        # Remove current webview
        child = self.webview_container.get_first_child()
        if child:
            self.webview_container.remove(child)
        # Add tab's webview
        if tab.webview:
            self.webview_container.append(tab.webview)
            self._current_webview = tab.webview
        self.url_entry.set_text(tab.url if not tab.url.startswith("helix://") else "")
        self._update_nav_buttons()
        self._update_tab_bar()

    def _update_tab_bar(self):
        # Clear existing tabs
        child = self.tab_bar_box.get_first_child()
        while child:
            next_child = child.get_next_sibling()
            self.tab_bar_box.remove(child)
            child = next_child

        for tab in self.tab_manager.tabs:
            is_active = tab.id == self.tab_manager.active_tab_id
            btn = Gtk.Button()
            btn.add_css_class("helix-tab")
            if is_active:
                btn.add_css_class("active")
            if tab.is_incognito:
                btn.add_css_class("incognito")

            box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=4)
            if tab.is_pinned:
                pin_icon = Gtk.Image.new_from_icon_name("view-pin-symbolic")
                box.append(pin_icon)
            label = Gtk.Label(label=tab.title[:20] if tab.title else "Tab mới")
            label.set_ellipsize(3)  # PANGO_ELLIPSIZE_END
            label.set_max_width_chars(18)
            box.append(label)

            if not tab.is_pinned:
                close_btn = Gtk.Button(icon_name="window-close-symbolic")
                close_btn.add_css_class("helix-tab-close")
                tab_id = tab.id
                close_btn.connect("clicked", lambda b, tid=tab_id: self._close_tab_by_id(tid))
                box.append(close_btn)

            btn.set_child(box)
            tab_ref = tab
            btn.connect("clicked", lambda b, t=tab_ref: self._switch_to_tab(t))
            self.tab_bar_box.append(btn)

        self.tabs_btn.set_label(str(len(self.tab_manager.tabs)))

    def _close_tab_by_id(self, tab_id):
        if len(self.tab_manager.tabs) <= 1:
            return
        tab = next((t for t in self.tab_manager.tabs if t.id == tab_id), None)
        if tab and not tab.is_pinned:
            self.tab_manager.close_tab(tab_id)
            if self.tab_manager.active_tab:
                self._switch_to_tab(self.tab_manager.active_tab)
            self._update_tab_bar()

    # MARK: - WebView

    def _create_webview(self, incognito=False):
        if incognito:
            ctx = WebKit.WebContext.new_ephemeral()
            webview = WebKit.WebView.new_with_context(ctx)
        else:
            webview = WebKit.WebView()

        settings = webview.get_settings()
        settings.set_enable_javascript(True)
        settings.set_enable_developer_extras(True)
        settings.set_allow_modal_dialogs(True)
        settings.set_enable_media_stream(True)
        settings.set_enable_mediasource(True)

        if self.prefs.is_desktop_mode:
            settings.set_user_agent("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15")

        webview.set_vexpand(True)
        webview.set_hexpand(True)

        # Connect signals
        webview.connect("load-changed", self._on_load_changed)
        webview.connect("notify::title", self._on_title_changed)
        webview.connect("notify::uri", self._on_uri_changed)
        webview.connect("notify::estimated-load-progress", self._on_progress)
        webview.connect("decide-policy", self._on_decide_policy)

        # Apply privacy scripts via user content manager
        ucm = webview.get_user_content_manager()
        if self.prefs.is_block_fingerprinting:
            self.privacy.inject_anti_fingerprinting(ucm)
        if self.prefs.is_block_trackers:
            self.privacy.inject_tracker_blocking(ucm)
        if self.prefs.is_do_not_track:
            self.privacy.inject_do_not_track(ucm)

        return webview

    def _on_load_changed(self, webview, event):
        if event == WebKit.LoadEvent.FINISHED:
            self.progress_bar.set_fraction(0)
            self._update_nav_buttons()
            tab = self.tab_manager.active_tab
            if tab and self.prefs.is_save_history and not tab.is_incognito:
                uri = webview.get_uri() or ""
                title = webview.get_title() or ""
                if uri and not uri.startswith("helix://"):
                    self.db.add_history(title, uri)

    def _on_title_changed(self, webview, param):
        tab = self.tab_manager.active_tab
        if tab:
            tab.title = webview.get_title() or UrlUtils.get_display_url(tab.url)
            self._update_tab_bar()

    def _on_uri_changed(self, webview, param):
        uri = webview.get_uri() or ""
        tab = self.tab_manager.active_tab
        if tab:
            tab.url = uri
        if not uri.startswith("helix://"):
            self.url_entry.set_text(uri)
        self._update_ssl_icon(uri)

    def _on_progress(self, webview, param):
        self.progress_bar.set_fraction(webview.get_estimated_load_progress())

    def _on_decide_policy(self, webview, decision, decision_type):
        if decision_type == WebKit.PolicyDecisionType.NAVIGATION_ACTION:
            nav = decision.get_navigation_action()
            req = nav.get_request()
            uri = req.get_uri()

            # HTTPS upgrade
            if self.prefs.is_https_upgrade and uri and uri.startswith("http://"):
                decision.ignore()
                webview.load_uri(uri.replace("http://", "https://", 1))
                return True

            # Ad blocking
            if self.prefs.is_ad_block and self.ad_block.should_block(uri):
                decision.ignore()
                return True

        return False

    # MARK: - Navigation

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

    # MARK: - Find

    def _toggle_find(self):
        visible = not self.find_bar.get_visible()
        self.find_bar.set_visible(visible)
        if visible:
            self.find_entry.grab_focus()

    def _on_find(self, entry):
        text = entry.get_text()
        if self._current_webview and text:
            finder = self._current_webview.get_find_controller()
            finder.search(text, WebKit.FindOptions.CASE_INSENSITIVE | WebKit.FindOptions.WRAP_AROUND, 100)

    def _find_next(self):
        if self._current_webview:
            self._current_webview.get_find_controller().search_next()

    def _find_prev(self):
        if self._current_webview:
            self._current_webview.get_find_controller().search_previous()

    # MARK: - Bookmarks

    def _toggle_bookmark(self):
        tab = self.tab_manager.active_tab
        if not tab:
            return
        if self.db.is_bookmarked(tab.url):
            self.db.remove_bookmark(tab.url)
            self.bookmark_btn.set_icon_name("non-starred-symbolic")
        else:
            self.db.add_bookmark(tab.title, tab.url)
            self.bookmark_btn.set_icon_name("starred-symbolic")

    # MARK: - Dialogs

    def _show_history(self):
        pass  # Would show history dialog

    def _show_bookmarks(self):
        pass  # Would show bookmarks dialog

    def _show_settings(self):
        from settings_dialog import SettingsDialog
        dialog = SettingsDialog(self.prefs, transient_for=self)
        dialog.present()

    # MARK: - Start Page

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
</div></body></html>"""
