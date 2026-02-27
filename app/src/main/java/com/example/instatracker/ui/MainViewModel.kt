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

    // Снимки автоматически обновляются при смене selection
    val snapshots: LiveData<List<Snapshot>> = _selection.switchMap { sel ->
        dao.getSnapshots(sel.accountId, sel.listType)
    }

    // Изменения автоматически пересчитываются при смене снимков
    val changes: LiveData<ChangeResult?> = snapshots.switchMap { list ->
        liveData {
            if (list.size < 2) {
                emit(null)
            } else {
                try {
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
                } catch (e: Exception) {
                    _error.postValue("Ошибка при сравнении снимков: ${e.message}")
                    emit(null)
                }
            }
        }
    }

    // Статистика автоматически пересчитывается при смене selection
    val nonMutual: LiveData<NonMutualResult?> = _selection.switchMap { sel ->
        liveData {
            try {
                val followersSnap = dao.getLatestSnapshot(sel.accountId, "followers")
                val followingSnap = dao.getLatestSnapshot(sel.accountId, "following")

                if (followersSnap == null || followingSnap == null) {
                    emit(null)
                    return@liveData
                }

                val followers = dao.getFollowerUsernames(followersSnap.id).toSet()
                val following = dao.getFollowerUsernames(followingSnap.id).toSet()

                emit(
                    NonMutualResult(
                        fans = (followers - following).sorted(),
                        notFollowingBack = (following - followers).sorted(),
                        mutual = followers.intersect(following).sorted().toList(),
                        followersCount = followers.size,
                        followingCount = following.size
                    )
                )
            } catch (e: Exception) {
                _error.postValue("Ошибка при загрузке статистики: ${e.message}")
                emit(null)
            }
        }
    }

    private val _status = MutableLiveData<String>()
    val status: LiveData<String> = _status

    // Отдельный канал для ошибок — чтобы отличать их от обычных статусов
    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    // ──────────────────────────────────────
    // Аккаунты
    // ──────────────────────────────────────

    fun addAccount(username: String, note: String = "") {
        viewModelScope.launch {
            try {
                val clean = username.trim().removePrefix("@").lowercase()
                if (clean.isBlank()) {
                    _status.value = "ВВЕДИТЕ ИМЯ ПОЛЬЗОВАТЕЛЯ"
                    return@launch
                }
                dao.insertAccount(Account(username = clean, note = note))
                _status.value = "► @$clean ДОБАВЛЕН"
            } catch (e: Exception) {
                _error.value = "Не удалось добавить аккаунт: ${e.message}"
            }
        }
    }

    fun deleteAccount(account: Account) {
        viewModelScope.launch {
            try {
                dao.deleteAccount(account)
                _status.value = "► АККАУНТ УДАЛЁН"
            } catch (e: Exception) {
                _error.value = "Не удалось удалить аккаунт: ${e.message}"
            }
        }
    }

    fun selectAccount(accountId: Long, listType: String) {
        _selection.value = AccountSelection(accountId, listType)
        viewModelScope.launch {
            try {
                _currentAccount.value = dao.getAccountById(accountId)
            } catch (e: Exception) {
                _error.value = "Не удалось загрузить аккаунт: ${e.message}"
            }
        }
    }

    // Вызывается когда нужна только статистика — без смены типа списка
    fun selectAccountForStats(accountId: Long) {
        _selection.value = AccountSelection(accountId, "followers")
        viewModelScope.launch {
            try {
                _currentAccount.value = dao.getAccountById(accountId)
            } catch (e: Exception) {
                _error.value = "Не удалось загрузить аккаунт: ${e.message}"
            }
        }
    }

    // ──────────────────────────────────────
    // Снимки
    // ──────────────────────────────────────

    fun createSnapshot(usernames: List<String>, label: String = "") {
        val sel = _selection.value ?: return
        viewModelScope.launch {
            try {
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
            } catch (e: Exception) {
                _error.value = "Не удалось сохранить снимок: ${e.message}"
            }
        }
    }

    fun deleteSnapshot(s: Snapshot) {
        viewModelScope.launch {
            try {
                dao.deleteSnapshot(s)
                _status.value = "► УДАЛЕНО"
            } catch (e: Exception) {
                _error.value = "Не удалось удалить снимок: ${e.message}"
            }
        }
    }
}

// Фабрика вынесена в companion object — создаётся один раз, используется везде
class MainViewModelFactory(
    private val app: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        MainViewModel(app) as T

    companion object {
        // Единственный экземпляр фабрики на всё приложение
        @Volatile
        private var instance: MainViewModelFactory? = null

        fun getInstance(app: Application): MainViewModelFactory {
            return instance ?: synchronized(this) {
                instance ?: MainViewModelFactory(app).also { instance = it }
            }
        }
    }
}
