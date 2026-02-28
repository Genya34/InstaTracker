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

// Пост — одна запись на каждый обработанный пост
@Entity(
    tableName = "posts",
    foreignKeys = [ForeignKey(
        entity = Account::class,
        parentColumns = ["id"],
        childColumns = ["accountId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("accountId")]
)
data class Post(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val postUrl: String,        // например /p/ABC123/
    val timestamp: Long = System.currentTimeMillis(),
    val likeCount: Int = 0      // сколько лайков удалось собрать
)

// Кто лайкнул конкретный пост
@Entity(
    tableName = "post_likers",
    foreignKeys = [ForeignKey(
        entity = Post::class,
        parentColumns = ["id"],
        childColumns = ["postId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("postId")]
)
data class PostLiker(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val postId: Long,
    val username: String
)

// Итоговая статистика по одному пользователю — кто сколько раз лайкнул
data class LikerStat(
    val username: String,
    val likeCount: Int,      // в скольких постах из собранных лайкнул
    val totalPosts: Int      // всего постов в выборке
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
    val mutual: List<String>,
    val followersCount: Int,
    val followingCount: Int
)
