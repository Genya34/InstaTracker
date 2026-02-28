package com.example.instatracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Account::class,
        Snapshot::class,
        Follower::class,
        Post::class,
        PostLiker::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun followerDao(): FollowerDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        // Миграция с версии 1 на версию 2 — добавляем таблицы постов и лайков
        // Благодаря миграции старые данные пользователя не удалятся
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS posts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        accountId INTEGER NOT NULL,
                        postUrl TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        likeCount INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (accountId) REFERENCES accounts(id) ON DELETE CASCADE
                    )
                """)
                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS index_posts_accountId ON posts(accountId)
                """)
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS post_likers (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        postId INTEGER NOT NULL,
                        username TEXT NOT NULL,
                        FOREIGN KEY (postId) REFERENCES posts(id) ON DELETE CASCADE
                    )
                """)
                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS index_post_likers_postId ON post_likers(postId)
                """)
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "instatracker.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
