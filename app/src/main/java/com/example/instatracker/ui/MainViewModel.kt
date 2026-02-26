package com.example.instatracker.ui

import android.app.Application
import androidx.lifecycle.*
import com.example.instatracker.data.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getInstance(application).followerDao()

    val accounts: LiveData<List<Account>> = dao.getAllAccounts()

    var currentAccountId: Long = 0
    var currentListType: String = "followers"

    private val _snapshots = MutableLiveData<List<Snapshot>>()
    val snapshots: LiveData<List<Snapshot>> = _snapshots

    private val _changes = MutableLiveData<ChangeResult?>()
    val changes: LiveData<ChangeResult?> = _changes

    private val _nonMutual = MutableLiveData<NonMutualResult?>()
    val nonMutual: LiveData<NonMutualResult?> = _nonMutual

    private val _status = MutableLiveData<String>()
    val status: LiveData<String> = _status

    private val _currentAccount = MutableLiveData<Account?>()
    val currentAccount: LiveData<Account?> = _currentAccount

    fun addAccount(username: String, note: String = "") {
        viewModelScope.launch {
            val clean = username.trim().removePrefix("@").lowercase()
            if (clean.isBlank()) {
                _status.value = "ENTER USERNAME"; return@launch
            }
            dao.insertAccount(Account(username = clean, note = note))
            _status.value = "► @$clean ADDED"
        }
    }

    fun deleteAccount(account: Account) {
        viewModelScope.launch {
            dao.deleteAccount(account)
            _status.value = "► ACCOUNT DELETED"
        }
    }

    fun selectAccount(accountId: Long, listType: String) {
        currentAccountId = accountId
        currentListType = listType
        viewModelScope.launch {
            _currentAccount.value = dao.getAccountById(accountId)
        }
        loadSnapshots()
    }

    fun loadSnapshots() {
        dao.getSnapshots(currentAccountId, currentListType).observeForever {
            _snapshots.value = it
        }
        compareLastTwo()
    }

    fun createSnapshot(usernames: List<String>, label: String = "") {
        viewModelScope.launch {
            val clean = usernames
                .map { it.trim().removePrefix("@").lowercase() }
                .filter { it.isNotBlank() }.distinct()

            if (clean.isEmpty()) {
                _status.value = "LIST IS EMPTY"; return@launch
            }

            val typeLabel = if (currentListType == "followers") "FOLLOWERS" else "FOLLOWING"
            val snapshot = Snapshot(
                accountId = currentAccountId,
                listType = currentListType,
                count = clean.size,
                label = label.ifBlank { "$typeLabel SNAPSHOT (${clean.size})" }
            )
            val id = dao.insertSnapshot(snapshot)
            dao.insertFollowers(clean.map { Follower(snapshotId = id, username = it) })
            _status.value = "► SAVED: ${clean.size}"
            loadSnapshots()
        }
    }

    fun compareLastTwo() {
        viewModelScope.launch {
            val last = dao.getLastTwoSnapshots(currentAccountId, currentListType)
            if (last.size < 2) { _changes.value = null; return@launch }
            val oldSet = dao.getFollowerUsernames(last[1].id).toSet()
            val newSet = dao.getFollowerUsernames(last[0].id).toSet()
            _changes.value = ChangeResult(
                newUsers = (newSet - oldSet).sorted(),
                goneUsers = (oldSet - newSet).sorted(),
                oldSnapshot = last[1], newSnapshot = last[0]
            )
        }
    }

    fun computeNonMutual(accountId: Long) {
        viewModelScope.launch {
            val followersSnap = dao.getLatestSnapshot(accountId, "followers")
            val followingSnap = dao.getLatestSnapshot(accountId, "following")

            if (followersSnap == null || followingSnap == null) {
                _nonMutual.value = null
                _status.value = if (followersSnap == null)
                    "► COLLECT FOLLOWERS FIRST" else "► COLLECT FOLLOWING FIRST"
                return@launch
            }

            val followers = dao.getFollowerUsernames(followersSnap.id).toSet()
            val following = dao.getFollowerUsernames(followingSnap.id).toSet()

            _nonMutual.value = NonMutualResult(
                fans = (followers - following).sorted(),
                notFollowingBack = (following - followers).sorted(),
                mutual = followers.intersect(following).sorted().toList(),
                followersCount = followers.size,
                followingCount = following.size
            )
        }
    }

    fun deleteSnapshot(s: Snapshot) {
        viewModelScope.launch {
            dao.deleteSnapshot(s)
            _status.value = "► DELETED"
            loadSnapshots()
        }
    }
}

class MainViewModelFactory(
    private val app: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        MainViewModel(app) as T
}
