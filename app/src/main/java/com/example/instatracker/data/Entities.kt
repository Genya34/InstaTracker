package com.example.instatracker.data

import androidx.room.*

@Entity(tableName = "snapshots")
data class Snapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val followerCount: Int = 0,
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
    val newFollowers: List<String>,
    val unfollowers: List<String>,
    val oldSnapshot: Snapshot,
    val newSnapshot: Snapshot
)
