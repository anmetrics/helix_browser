"""SQLite database for Helix Browser history, bookmarks, and downloads"""

import os
import sqlite3
import time
import threading


class Database:
    _instance = None
    _lock = threading.Lock()

    @classmethod
    def get_instance(cls):
        if cls._instance is None:
            with cls._lock:
                if cls._instance is None:
                    cls._instance = cls()
        return cls._instance

    def __init__(self):
        db_dir = os.path.join(os.path.expanduser("~"), ".config", "helix-browser")
        os.makedirs(db_dir, exist_ok=True)
        self._db_path = os.path.join(db_dir, "helix.db")
        self._conn = sqlite3.connect(self._db_path, check_same_thread=False)
        self._conn.row_factory = sqlite3.Row
        self._create_tables()

    def _create_tables(self):
        self._conn.executescript("""
            CREATE TABLE IF NOT EXISTS history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                url TEXT NOT NULL,
                title TEXT DEFAULT '',
                timestamp REAL NOT NULL,
                visit_count INTEGER DEFAULT 1
            );
            CREATE INDEX IF NOT EXISTS idx_history_url ON history(url);
            CREATE INDEX IF NOT EXISTS idx_history_ts ON history(timestamp DESC);

            CREATE TABLE IF NOT EXISTS bookmarks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                url TEXT NOT NULL UNIQUE,
                title TEXT DEFAULT '',
                created_at REAL NOT NULL,
                folder TEXT DEFAULT ''
            );
            CREATE INDEX IF NOT EXISTS idx_bookmarks_url ON bookmarks(url);

            CREATE TABLE IF NOT EXISTS downloads (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                url TEXT NOT NULL,
                filename TEXT NOT NULL,
                filepath TEXT DEFAULT '',
                filesize INTEGER DEFAULT 0,
                status TEXT DEFAULT 'pending',
                created_at REAL NOT NULL,
                completed_at REAL
            );
        """)
        self._conn.commit()

    # --- History ---

    def add_history(self, url: str, title: str = ""):
        with self._lock:
            existing = self._conn.execute(
                "SELECT id, visit_count FROM history WHERE url = ? ORDER BY timestamp DESC LIMIT 1",
                (url,)
            ).fetchone()
            if existing:
                self._conn.execute(
                    "UPDATE history SET title = ?, timestamp = ?, visit_count = ? WHERE id = ?",
                    (title, time.time(), existing["visit_count"] + 1, existing["id"])
                )
            else:
                self._conn.execute(
                    "INSERT INTO history (url, title, timestamp) VALUES (?, ?, ?)",
                    (url, title, time.time())
                )
            self._conn.commit()
            self._trim_history()

    def get_history(self, limit: int = 500) -> list[dict]:
        rows = self._conn.execute(
            "SELECT url, title, timestamp FROM history ORDER BY timestamp DESC LIMIT ?",
            (limit,)
        ).fetchall()
        return [dict(r) for r in rows]

    def search_history(self, query: str, limit: int = 100) -> list[dict]:
        q = f"%{query}%"
        rows = self._conn.execute(
            "SELECT url, title, timestamp FROM history WHERE url LIKE ? OR title LIKE ? ORDER BY timestamp DESC LIMIT ?",
            (q, q, limit)
        ).fetchall()
        return [dict(r) for r in rows]

    def delete_history_item(self, url: str):
        with self._lock:
            self._conn.execute("DELETE FROM history WHERE url = ?", (url,))
            self._conn.commit()

    def clear_history(self):
        with self._lock:
            self._conn.execute("DELETE FROM history")
            self._conn.commit()

    def _trim_history(self, max_items: int = 5000):
        count = self._conn.execute("SELECT COUNT(*) FROM history").fetchone()[0]
        if count > max_items:
            self._conn.execute(
                "DELETE FROM history WHERE id IN (SELECT id FROM history ORDER BY timestamp ASC LIMIT ?)",
                (count - max_items,)
            )
            self._conn.commit()

    # --- Bookmarks ---

    def add_bookmark(self, url: str, title: str = "", folder: str = "") -> bool:
        with self._lock:
            try:
                self._conn.execute(
                    "INSERT OR REPLACE INTO bookmarks (url, title, created_at, folder) VALUES (?, ?, ?, ?)",
                    (url, title, time.time(), folder)
                )
                self._conn.commit()
                return True
            except Exception:
                return False

    def remove_bookmark(self, url: str):
        with self._lock:
            self._conn.execute("DELETE FROM bookmarks WHERE url = ?", (url,))
            self._conn.commit()

    def is_bookmarked(self, url: str) -> bool:
        row = self._conn.execute("SELECT id FROM bookmarks WHERE url = ?", (url,)).fetchone()
        return row is not None

    def get_bookmarks(self, folder: str = "") -> list[dict]:
        if folder:
            rows = self._conn.execute(
                "SELECT url, title, created_at, folder FROM bookmarks WHERE folder = ? ORDER BY created_at DESC",
                (folder,)
            ).fetchall()
        else:
            rows = self._conn.execute(
                "SELECT url, title, created_at, folder FROM bookmarks ORDER BY created_at DESC"
            ).fetchall()
        return [dict(r) for r in rows]

    def search_bookmarks(self, query: str) -> list[dict]:
        q = f"%{query}%"
        rows = self._conn.execute(
            "SELECT url, title, created_at, folder FROM bookmarks WHERE url LIKE ? OR title LIKE ? ORDER BY created_at DESC",
            (q, q)
        ).fetchall()
        return [dict(r) for r in rows]

    def clear_bookmarks(self):
        with self._lock:
            self._conn.execute("DELETE FROM bookmarks")
            self._conn.commit()

    # --- Downloads ---

    def add_download(self, url: str, filename: str, filepath: str = "", filesize: int = 0) -> int:
        with self._lock:
            cursor = self._conn.execute(
                "INSERT INTO downloads (url, filename, filepath, filesize, status, created_at) VALUES (?, ?, ?, ?, 'downloading', ?)",
                (url, filename, filepath, filesize, time.time())
            )
            self._conn.commit()
            return cursor.lastrowid

    def update_download_status(self, download_id: int, status: str):
        with self._lock:
            completed = time.time() if status in ("completed", "failed", "cancelled") else None
            self._conn.execute(
                "UPDATE downloads SET status = ?, completed_at = ? WHERE id = ?",
                (status, completed, download_id)
            )
            self._conn.commit()

    def get_downloads(self, limit: int = 100) -> list[dict]:
        rows = self._conn.execute(
            "SELECT id, url, filename, filepath, filesize, status, created_at, completed_at FROM downloads ORDER BY created_at DESC LIMIT ?",
            (limit,)
        ).fetchall()
        return [dict(r) for r in rows]

    def clear_downloads(self):
        with self._lock:
            self._conn.execute("DELETE FROM downloads")
            self._conn.commit()

    def close(self):
        self._conn.close()
