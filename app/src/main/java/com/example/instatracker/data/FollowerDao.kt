package com.example.instatracker.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface FollowerDao {
    @Insert
    suspend fun insertSnapshot(snapshot: Snapshot): Long

    @Delete
    suspend fun deleteSnapshot(snapshot: Snapshot)

    @Query("SELECT * FROM snapshots ORDER BY timestamp DESC")
    fun getAllSnapshots(): LiveData<List<Snapshot>>

    @Query("SELECT * FROM snapshots WHERE id = :id")
    suspend fun getSnapshotById(id: Long): Snapshot?

    @Insert
    suspend fun insertFollowers(followers: List<Follower>)

    @Query("SELECT username FROM followers WHERE snapshotId = :snapshotId")
    suspend fun getFollowerUsernames(snapshotId: Long): List<String>

    @Query("SELECT * FROM snapshots ORDER BY timestamp DESC LIMIT 2")
    suspend fun getLastTwoSnapshots(): List<Snapshot>
}
