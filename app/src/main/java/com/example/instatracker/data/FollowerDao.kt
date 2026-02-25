package com.example.instatracker.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface FollowerDao {

    @Insert
    suspend fun insertAccount(account: Account): Long

    @Delete
    suspend fun deleteAccount(account: Account)

    @Query("SELECT * FROM accounts ORDER BY username")
    fun getAllAccounts(): LiveData<List<Account>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getAccountById(id: Long): Account?

    @Insert
    suspend fun insertSnapshot(snapshot: Snapshot): Long

    @Delete
    suspend fun deleteSnapshot(snapshot: Snapshot)

    @Query("SELECT * FROM snapshots WHERE accountId = :accountId AND listType = :listType ORDER BY timestamp DESC")
    fun getSnapshots(accountId: Long, listType: String): LiveData<List<Snapshot>>

    @Query("SELECT * FROM snapshots WHERE accountId = :accountId AND listType = :listType ORDER BY timestamp DESC LIMIT 2")
    suspend fun getLastTwoSnapshots(accountId: Long, listType: String): List<Snapshot>

    @Query("SELECT * FROM snapshots WHERE accountId = :accountId AND listType = :listType ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestSnapshot(accountId: Long, listType: String): Snapshot?

    @Insert
    suspend fun insertFollowers(followers: List<Follower>)

    @Query("SELECT username FROM followers WHERE snapshotId = :snapshotId")
    suspend fun getFollowerUsernames(snapshotId: Long): List<String>
}
