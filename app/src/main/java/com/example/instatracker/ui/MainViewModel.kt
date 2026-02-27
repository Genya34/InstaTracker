package com.example.instatracker.ui

import android.app.Application
import androidx.lifecycle.*
import com.example.instatracker.data.*
import kotlinx.coroutines.launch

data class AccountSelection(val accountId: Long, val listType: String)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getInstance(application).followerDao()

    val accounts: LiveData<List<Account>> = dao.getAllAccounts()

    private val _selection = MutableLiveData<AccountSelection>()

    val currentListType: String get() = _selection.value?.listType ?: "followers"

    private val _currentAccount = MutableLiveData<Account?>()
    val currentAccount: LiveData<Account?> = _currentAccount

    // Снимки автоматически обновляются при смене selection — без observeForever!
    val snapshots: LiveData<List<Snapshot>> = _selection.switchMap { sel ->
        dao.getSnapshots(sel.accountId, sel.listType)
    }

    // Изменения автоматически пересчитываются при смене списка снимков
    val changes: LiveData<ChangeResult?> = snapshots.switchMap { list ->
        liveData {
            if (list.size < 2) {
                emit(null)
            } else {
                val oldSet = dao.getFollowerUsernames(list[1].id).toSet()
                val newSet = dao.getFollowerUsernames(list[0].id).toSet()
                emit(
                    ChangeResult(
                        newUsers = (newSet - oldSet).sorted(),
                        goneUsers = (oldSet - newSet).sorted(),
                        oldSnapshot = list[1],
                        newSnapshot = list[0]
                    )
                )
            }
        }
    }

    private val _nonMutual = MutableLiveData<NonMutualResult?>()
    val nonMutual: LiveData<NonMutualResult?> = _nonMutual

    private val _status = MutableLiveData<String>()
    val status: LiveData<String> = _status

    fun addAccount(username: String, note: String = "") {
        viewModelScope.launch {
            val clean = username.trim().removePrefix("@").lowercase()
            if (clean.isBlank()) {
                _status.value = "ВВЕДИТЕ ИМЯ ПОЛЬЗОВАТЕЛЯ"
                return@launch
            }
            dao.insertAccount(Account(username = clean, note = note))
            _status.value = "► @$clean ДОБАВЛЕН"
        }
    }

    fun deleteAccount(account: Account) {
        viewModelScope.launch {
            dao.deleteAccount(account)
            _status.value = "► АККАУНТ УДАЛЁН"
        }
    }

    fun selectAccount(accountId: Long, listType: String) {
        _selection.value = AccountSelection(accountId, listType)
        viewModelScope.launch {
            _currentAccount.value = dao.getAccountById(accountId)
        }
    }

    fun createSnapshot(usernames: List<String>, label: String = "") {
        val sel = _selection.value ?: return
        viewModelScope.launch {
            val clean = usernames
                .map { it.trim().removePrefix("@").lowercase() }
                .filter { it.isNotBlank() }
                .distinct()

            if (clean.isEmpty()) {
                _status.value = "СПИСОК ПУСТ"
                return@launch
            }

            val typeLabel = if (sel.listType == "followers") "ПОДПИСЧИКИ" else "ПОДПИСКИ"
            val snapshot = Snapshot(
                accountId = sel.accountId,
                listType = sel.listType,
                count = clean.size,
                label = label.ifBlank { "$typeLabel СНИМОК (${clean.size})" }
            )
            val id = dao.insertSnapshot(snapshot)
            dao.insertFollowers(clean.map { Follower(snapshotId = id, username = it) })
            _status.value = "► СОХРАНЕНО: ${clean.size}"
        }
    }

    fun deleteSnapshot(s: Snapshot) {
        viewModelScope.launch {
            dao.deleteSnapshot(s)
            _status.value = "► УДАЛЕНО"
        }
    }

    fun computeNonMutual(accountId: Long) {
        viewModelScope.launch {
            val followersSnap = dao.getLatestSnapshot(accountId, "followers")
            val followingSnap = dao.getLatestSnapshot(accountId, "following")

            if (followersSnap == null || followingSnap == null) {
                _nonMutual.value = null
                _status.value = if (followersSnap == null)
                    "► СНАЧАЛА СОБЕРИТЕ ПОДПИСЧИКОВ"
                else
                    "► СНАЧАЛА СОБЕРИТЕ ПОДПИСКИ"
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
}

class MainViewModelFactory(
    private val app: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        MainViewModel(app) as T
}
