package com.example.instatracker.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface FollowerDao {

    // ── Аккаунты ──────────────────────────────────────────────────────────

    @Insert
    suspend fun insertAccount(account: Account): Long

    @Delete
    suspend fun deleteAccount(account: Account)

    @Query("SELECT * FROM accounts ORDER BY username")
    fun getAllAccounts(): LiveData<List<Account>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getAccountById(id: Long): Account?

    // ── Снимки ────────────────────────────────────────────────────────────

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

    // ── Подписчики ────────────────────────────────────────────────────────

    @Insert
    suspend fun insertFollowers(followers: List<Follower>)

    @Query("SELECT username FROM followers WHERE snapshotId = :snapshotId")
    suspend fun getFollowerUsernames(snapshotId: Long): List<String>

    // ── Посты ─────────────────────────────────────────────────────────────

    @Insert
    suspend fun insertPost(post: Post): Long

    @Delete
    suspend fun deletePost(post: Post)

    // Все посты аккаунта, свежие сверху
    @Query("SELECT * FROM posts WHERE accountId = :accountId ORDER BY timestamp DESC")
    fun getPosts(accountId: Long): LiveData<List<Post>>

    // Удалить все посты аккаунта — перед новым сбором
    @Query("DELETE FROM posts WHERE accountId = :accountId")
    suspend fun deleteAllPosts(accountId: Long)

    // ── Лайкнувшие ────────────────────────────────────────────────────────

    @Insert
    suspend fun insertPostLikers(likers: List<PostLiker>)

    @Query("SELECT username FROM post_likers WHERE postId = :postId")
    suspend fun getLikers(postId: Long): List<String>

    // Статистика: кто сколько раз лайкнул посты аккаунта
    // Считаем уникальные лайки по каждому пользователю
    @Query("""
        SELECT pl.username, COUNT(DISTINCT pl.postId) as likeCount,
               (SELECT COUNT(*) FROM posts WHERE accountId = :accountId) as totalPosts
        FROM post_likers pl
        INNER JOIN posts p ON pl.postId = p.id
        WHERE p.accountId = :accountId
        GROUP BY pl.username
        ORDER BY likeCount DESC
    """)
    suspend fun getLikerStats(accountId: Long): List<LikerStat>
}
