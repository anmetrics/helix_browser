package com.helix.browser.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Bookmark::class, HistoryItem::class],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Register every schema migration here. Bumping `version` above without
        // adding the matching Migration will throw IllegalStateException at
        // startup — that is intentional. NEVER fall back to destructive
        // migration in production; that silently wipes user bookmarks/history.

        // v1 -> v2: bookmark folders. Adds two nullable/defaulted columns so
        // every existing row stays a valid root-level, non-folder bookmark.
        // Pure additive ALTER TABLEs — no data is read or rewritten, so this is
        // safe and instantaneous even on large bookmark sets.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bookmarks ADD COLUMN isFolder INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE bookmarks ADD COLUMN parentId INTEGER")
            }
        }

        private val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "helix_browser.db"
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    // Fallback only on downgrade (developer rolling back versionCode);
                    // upgrades MUST go through an explicit Migration above.
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
