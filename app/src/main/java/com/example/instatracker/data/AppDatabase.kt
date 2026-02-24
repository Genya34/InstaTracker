package com.example.instatracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Snapshot::class, Follower::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun followerDao(): FollowerDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(ctx: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    ctx.applicationContext,
                    AppDatabase::class.java,
                    "insta_tracker.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
