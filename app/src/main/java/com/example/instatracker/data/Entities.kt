package com.example.instatracker.data

import androidx.room.*

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val note: String = ""
)

@Entity(tableName = "snapshots")
data class Snapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val listType: String,
    val timestamp: Long = System.currentTimeMillis(),
    val count: Int = 0,
    val label: String = ""
)

@Entity(
    tableName = "followers",
    foreignKeys = [ForeignKey(
        entity = Snapshot::class,
        parentColumns = ["id"],
        childColumns = ["snapshotId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("snapshotId")]
)
data class Follower(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val snapshotId: Long,
    val username: String
)

data class ChangeResult(
    val newUsers: List<String>,
    val goneUsers: List<String>,
    val oldSnapshot: Snapshot,
    val newSnapshot: Snapshot
)

data class NonMutualResult(
    val fans: List<String>,
    val notFollowingBack: List<String>,
    val mutualCount: Int,
    val followersCount: Int,
    val followingCount: Int
)
