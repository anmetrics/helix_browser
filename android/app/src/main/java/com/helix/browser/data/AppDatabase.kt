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
    version = 1,
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
        //
        // Example (uncomment when bumping to v2):
        // private val MIGRATION_1_2 = object : Migration(1, 2) {
        //     override fun migrate(db: SupportSQLiteDatabase) {
        //         db.execSQL("ALTER TABLE bookmarks ADD COLUMN folderId INTEGER")
        //     }
        // }
        private val ALL_MIGRATIONS: Array<Migration> = emptyArray()

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
