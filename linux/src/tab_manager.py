"""Tab management for Helix Browser"""

import json
import os
import time
from dataclasses import dataclass, field, asdict
from typing import Optional


@dataclass
class BrowserTab:
    id: str = ""
    url: str = "helix://start"
    title: str = "Tab mới"
    is_pinned: bool = False
    is_muted: bool = False
    is_incognito: bool = False
    is_suspended: bool = False
    group_id: Optional[str] = None
    group_name: Optional[str] = None
    last_access: float = 0.0
    created_at: float = 0.0

    def __post_init__(self):
        if not self.id:
            import uuid
            self.id = str(uuid.uuid4())
        if not self.created_at:
            self.created_at = time.time()
        if not self.last_access:
            self.last_access = time.time()


@dataclass
class TabGroup:
    id: str
    name: str
    color: str = "#8B8BFF"


class TabManager:
    def __init__(self):
        self.tabs: list[BrowserTab] = []
        self.active_tab_index: int = -1
        self.groups: list[TabGroup] = []
        self._session_path = os.path.join(
            os.path.expanduser("~"), ".config", "helix-browser", "session.json"
        )
        self._on_tab_changed = None
        self._on_tabs_updated = None

    def set_callbacks(self, on_tab_changed=None, on_tabs_updated=None):
        self._on_tab_changed = on_tab_changed
        self._on_tabs_updated = on_tabs_updated

    @property
    def active_tab(self) -> Optional[BrowserTab]:
        if 0 <= self.active_tab_index < len(self.tabs):
            return self.tabs[self.active_tab_index]
        return None

    def create_tab(self, url: str = "helix://start", is_incognito: bool = False) -> BrowserTab:
        tab = BrowserTab(url=url, is_incognito=is_incognito)
        self.tabs.append(tab)
        self.active_tab_index = len(self.tabs) - 1
        self._notify_tabs_updated()
        self._notify_tab_changed()
        return tab

    def close_tab(self, index: int):
        if index < 0 or index >= len(self.tabs):
            return
        self.tabs.pop(index)
        if len(self.tabs) == 0:
            self.create_tab()
            return
        if self.active_tab_index >= len(self.tabs):
            self.active_tab_index = len(self.tabs) - 1
        elif self.active_tab_index > index:
            self.active_tab_index -= 1
        self._notify_tabs_updated()
        self._notify_tab_changed()

    def switch_to_tab(self, index: int):
        if 0 <= index < len(self.tabs):
            self.active_tab_index = index
            self.tabs[index].last_access = time.time()
            self._notify_tab_changed()

    def move_tab(self, from_index: int, to_index: int):
        if 0 <= from_index < len(self.tabs) and 0 <= to_index < len(self.tabs):
            tab = self.tabs.pop(from_index)
            self.tabs.insert(to_index, tab)
            if self.active_tab_index == from_index:
                self.active_tab_index = to_index
            self._notify_tabs_updated()

    def pin_tab(self, index: int):
        if 0 <= index < len(self.tabs):
            self.tabs[index].is_pinned = not self.tabs[index].is_pinned
            self._notify_tabs_updated()

    def mute_tab(self, index: int):
        if 0 <= index < len(self.tabs):
            self.tabs[index].is_muted = not self.tabs[index].is_muted
            self._notify_tabs_updated()

    def add_to_group(self, index: int, group_name: str, color: str = "#8B8BFF"):
        if 0 <= index < len(self.tabs):
            import uuid
            group_id = None
            for g in self.groups:
                if g.name == group_name:
                    group_id = g.id
                    break
            if not group_id:
                group_id = str(uuid.uuid4())
                self.groups.append(TabGroup(id=group_id, name=group_name, color=color))
            self.tabs[index].group_id = group_id
            self.tabs[index].group_name = group_name
            self._notify_tabs_updated()

    def remove_from_group(self, index: int):
        if 0 <= index < len(self.tabs):
            self.tabs[index].group_id = None
            self.tabs[index].group_name = None
            self._notify_tabs_updated()

    def close_tabs_to_right(self, index: int):
        if 0 <= index < len(self.tabs):
            self.tabs = self.tabs[:index + 1]
            if self.active_tab_index > index:
                self.active_tab_index = index
            self._notify_tabs_updated()
            self._notify_tab_changed()

    def close_other_tabs(self, index: int):
        if 0 <= index < len(self.tabs):
            tab = self.tabs[index]
            self.tabs = [tab]
            self.active_tab_index = 0
            self._notify_tabs_updated()
            self._notify_tab_changed()

    def duplicate_tab(self, index: int):
        if 0 <= index < len(self.tabs):
            source = self.tabs[index]
            self.create_tab(url=source.url)

    def suspend_inactive_tabs(self, timeout_seconds: int = 1800):
        now = time.time()
        for i, tab in enumerate(self.tabs):
            if i != self.active_tab_index and not tab.is_pinned and not tab.is_suspended:
                if now - tab.last_access > timeout_seconds:
                    tab.is_suspended = True

    def search_tabs(self, query: str) -> list[tuple[int, BrowserTab]]:
        query = query.lower()
        results = []
        for i, tab in enumerate(self.tabs):
            if query in tab.title.lower() or query in tab.url.lower():
                results.append((i, tab))
        return results

    def save_session(self):
        try:
            os.makedirs(os.path.dirname(self._session_path), exist_ok=True)
            data = {
                "tabs": [
                    {
                        "url": t.url,
                        "title": t.title,
                        "is_pinned": t.is_pinned,
                        "is_muted": t.is_muted,
                        "group_id": t.group_id,
                        "group_name": t.group_name,
                    }
                    for t in self.tabs
                    if not t.is_incognito
                ],
                "active_index": self.active_tab_index,
                "groups": [asdict(g) for g in self.groups],
            }
            with open(self._session_path, "w") as f:
                json.dump(data, f, indent=2)
        except Exception as e:
            print(f"Failed to save session: {e}")

    def restore_session(self) -> bool:
        try:
            if not os.path.exists(self._session_path):
                return False
            with open(self._session_path, "r") as f:
                data = json.load(f)
            self.groups = [
                TabGroup(**g) for g in data.get("groups", [])
            ]
            for tab_data in data.get("tabs", []):
                tab = BrowserTab(
                    url=tab_data.get("url", "helix://start"),
                    title=tab_data.get("title", "Tab mới"),
                    is_pinned=tab_data.get("is_pinned", False),
                    is_muted=tab_data.get("is_muted", False),
                    group_id=tab_data.get("group_id"),
                    group_name=tab_data.get("group_name"),
                )
                self.tabs.append(tab)
            active = data.get("active_index", 0)
            if self.tabs:
                self.active_tab_index = min(active, len(self.tabs) - 1)
            return len(self.tabs) > 0
        except Exception as e:
            print(f"Failed to restore session: {e}")
            return False

    def _notify_tab_changed(self):
        if self._on_tab_changed:
            self._on_tab_changed()

    def _notify_tabs_updated(self):
        if self._on_tabs_updated:
            self._on_tabs_updated()
