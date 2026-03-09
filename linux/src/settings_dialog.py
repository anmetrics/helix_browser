"""Settings dialog for Helix Browser"""

import gi
gi.require_version("Gtk", "4.0")
gi.require_version("Adw", "1")
from gi.repository import Gtk, Adw

from prefs import Prefs
from database import Database


class SettingsDialog(Adw.PreferencesWindow):
    def __init__(self, parent=None):
        super().__init__(
            title="Cài đặt",
            transient_for=parent,
            modal=True,
            default_width=600,
            default_height=700,
        )
        self.prefs = Prefs.get_instance()
        self.db = Database.get_instance()

        self._build_general_page()
        self._build_privacy_page()
        self._build_tabs_page()
        self._build_about_page()

    def _build_general_page(self):
        page = Adw.PreferencesPage(title="Chung", icon_name="preferences-system-symbolic")
        self.add(page)

        # Search engine
        group = Adw.PreferencesGroup(title="Công cụ tìm kiếm")
        page.add(group)

        search_row = Adw.ComboRow(title="Công cụ tìm kiếm mặc định")
        engines = Gtk.StringList.new(["Google", "DuckDuckGo", "Bing", "Brave", "Yahoo"])
        search_row.set_model(engines)

        engine_map = {"google": 0, "duckduckgo": 1, "bing": 2, "brave": 3, "yahoo": 4}
        current = self.prefs.search_engine
        search_row.set_selected(engine_map.get(current, 0))
        search_row.connect("notify::selected", self._on_search_engine_changed)
        group.add(search_row)

        # Homepage
        home_group = Adw.PreferencesGroup(title="Trang chủ")
        page.add(home_group)

        home_row = Adw.EntryRow(title="URL trang chủ")
        home_row.set_text(self.prefs.homepage)
        home_row.connect("changed", self._on_homepage_changed)
        home_group.add(home_row)

    def _build_privacy_page(self):
        page = Adw.PreferencesPage(title="Quyền riêng tư", icon_name="security-high-symbolic")
        self.add(page)

        # Tracker stats
        stats_group = Adw.PreferencesGroup(title="Thống kê bảo mật")
        page.add(stats_group)

        from privacy_manager import PrivacyManager
        pm = PrivacyManager.get_instance()
        stats_row = Adw.ActionRow(
            title=f"{pm.trackers_blocked} trình theo dõi đã bị chặn",
            icon_name="shield-safe-symbolic",
        )
        stats_group.add(stats_row)

        # Privacy toggles
        privacy_group = Adw.PreferencesGroup(title="Bảo vệ quyền riêng tư")
        page.add(privacy_group)

        toggles = [
            ("is_block_trackers_enabled", "Chặn trình theo dõi", "Chặn quảng cáo và trình theo dõi trên web"),
            ("is_block_third_party_cookies_enabled", "Chặn cookie bên thứ ba", "Ngăn trang web theo dõi qua cookie"),
            ("is_do_not_track_enabled", "Gửi Do Not Track", "Yêu cầu trang web không theo dõi bạn"),
            ("is_https_upgrade_enabled", "Nâng cấp HTTPS", "Tự động dùng kết nối an toàn khi có thể"),
            ("is_block_fingerprinting_enabled", "Chống dấu vân tay", "Bảo vệ khỏi kỹ thuật fingerprinting"),
            ("is_block_popups_enabled", "Chặn popup", "Chặn cửa sổ bật lên không mong muốn"),
            ("is_block_autoplay_enabled", "Chặn tự phát", "Chặn video/audio tự động phát"),
        ]

        for key, title, subtitle in toggles:
            row = Adw.SwitchRow(title=title, subtitle=subtitle)
            row.set_active(getattr(self.prefs, key, True))
            row.connect("notify::active", self._make_toggle_handler(key))
            privacy_group.add(row)

        # Clear data
        clear_group = Adw.PreferencesGroup(title="Xóa dữ liệu")
        page.add(clear_group)

        clear_history_row = Adw.ActionRow(title="Xóa lịch sử duyệt web", activatable=True)
        clear_history_row.add_suffix(Gtk.Image.new_from_icon_name("go-next-symbolic"))
        clear_history_row.connect("activated", self._on_clear_history)
        clear_group.add(clear_history_row)

        clear_bookmarks_row = Adw.ActionRow(title="Xóa tất cả dấu trang", activatable=True)
        clear_bookmarks_row.add_suffix(Gtk.Image.new_from_icon_name("go-next-symbolic"))
        clear_bookmarks_row.connect("activated", self._on_clear_bookmarks)
        clear_group.add(clear_bookmarks_row)

        clear_downloads_row = Adw.ActionRow(title="Xóa lịch sử tải xuống", activatable=True)
        clear_downloads_row.add_suffix(Gtk.Image.new_from_icon_name("go-next-symbolic"))
        clear_downloads_row.connect("activated", self._on_clear_downloads)
        clear_group.add(clear_downloads_row)

    def _build_tabs_page(self):
        page = Adw.PreferencesPage(title="Tab", icon_name="tab-new-symbolic")
        self.add(page)

        group = Adw.PreferencesGroup(title="Hành vi tab")
        page.add(group)

        restore_row = Adw.SwitchRow(
            title="Khôi phục tab khi khởi động",
            subtitle="Mở lại các tab từ phiên trước"
        )
        restore_row.set_active(getattr(self.prefs, "is_restore_tabs_enabled", True))
        restore_row.connect("notify::active", self._make_toggle_handler("is_restore_tabs_enabled"))
        group.add(restore_row)

        suspend_row = Adw.SwitchRow(
            title="Tạm ngưng tab không hoạt động",
            subtitle="Giải phóng bộ nhớ cho tab lâu không dùng"
        )
        suspend_row.set_active(getattr(self.prefs, "is_suspend_inactive_enabled", True))
        suspend_row.connect("notify::active", self._make_toggle_handler("is_suspend_inactive_enabled"))
        group.add(suspend_row)

        confirm_row = Adw.SwitchRow(
            title="Xác nhận khi đóng nhiều tab",
            subtitle="Hỏi trước khi đóng cửa sổ có nhiều tab"
        )
        confirm_row.set_active(getattr(self.prefs, "is_confirm_close_multiple", True))
        confirm_row.connect("notify::active", self._make_toggle_handler("is_confirm_close_multiple"))
        group.add(confirm_row)

    def _build_about_page(self):
        page = Adw.PreferencesPage(title="Giới thiệu", icon_name="help-about-symbolic")
        self.add(page)

        group = Adw.PreferencesGroup()
        page.add(group)

        version_row = Adw.ActionRow(title="Phiên bản", subtitle="3.0.0")
        group.add(version_row)

        engine_row = Adw.ActionRow(title="Engine", subtitle="WebKitGTK")
        group.add(engine_row)

        platform_row = Adw.ActionRow(title="Nền tảng", subtitle="Linux (GTK4 + libadwaita)")
        group.add(platform_row)

    def _make_toggle_handler(self, key):
        def handler(row, _):
            setattr(self.prefs, key, row.get_active())
        return handler

    def _on_search_engine_changed(self, row, _):
        engines = ["google", "duckduckgo", "bing", "brave", "yahoo"]
        idx = row.get_selected()
        if 0 <= idx < len(engines):
            self.prefs.search_engine = engines[idx]

    def _on_homepage_changed(self, row):
        self.prefs.homepage = row.get_text()

    def _on_clear_history(self, _):
        dialog = Adw.MessageDialog(
            transient_for=self,
            heading="Xóa lịch sử?",
            body="Hành động này không thể hoàn tác.",
        )
        dialog.add_response("cancel", "Hủy")
        dialog.add_response("delete", "Xóa")
        dialog.set_response_appearance("delete", Adw.ResponseAppearance.DESTRUCTIVE)
        dialog.connect("response", self._on_clear_history_response)
        dialog.present()

    def _on_clear_history_response(self, dialog, response):
        if response == "delete":
            self.db.clear_history()

    def _on_clear_bookmarks(self, _):
        dialog = Adw.MessageDialog(
            transient_for=self,
            heading="Xóa tất cả dấu trang?",
            body="Hành động này không thể hoàn tác.",
        )
        dialog.add_response("cancel", "Hủy")
        dialog.add_response("delete", "Xóa")
        dialog.set_response_appearance("delete", Adw.ResponseAppearance.DESTRUCTIVE)
        dialog.connect("response", self._on_clear_bookmarks_response)
        dialog.present()

    def _on_clear_bookmarks_response(self, dialog, response):
        if response == "delete":
            self.db.clear_bookmarks()

    def _on_clear_downloads(self, _):
        dialog = Adw.MessageDialog(
            transient_for=self,
            heading="Xóa lịch sử tải xuống?",
            body="Hành động này không thể hoàn tác.",
        )
        dialog.add_response("cancel", "Hủy")
        dialog.add_response("delete", "Xóa")
        dialog.set_response_appearance("delete", Adw.ResponseAppearance.DESTRUCTIVE)
        dialog.connect("response", self._on_clear_downloads_response)
        dialog.present()

    def _on_clear_downloads_response(self, dialog, response):
        if response == "delete":
            self.db.clear_downloads()
