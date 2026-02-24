package com.example.instatracker.ui

import android.app.Application
import androidx.lifecycle.*
import com.example.instatracker.data.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getInstance(application).followerDao()
    val snapshots: LiveData<List<Snapshot>> = dao.getAllSnapshots()

    private val _changes = MutableLiveData<ChangeResult?>()
    val changes: LiveData<ChangeResult?> = _changes

    private val _status = MutableLiveData<String>()
    val status: LiveData<String> = _status

    fun createSnapshot(usernames: List<String>, label: String = "") {
        viewModelScope.launch {
            val clean = usernames
                .map { it.trim().removePrefix("@").lowercase() }
                .filter { it.isNotBlank() }.distinct()

            if (clean.isEmpty()) {
                _status.value = "Список пуст"; return@launch
            }
            val snapshot = Snapshot(followerCount = clean.size,
                label = label.ifBlank { "Снимок (${clean.size})" })
            val id = dao.insertSnapshot(snapshot)
            dao.insertFollowers(clean.map { Follower(snapshotId = id, username = it) })
            _status.value = "Сохранено: ${clean.size} подписчиков"
            compareLastTwo()
        }
    }

    fun compareLastTwo() {
        viewModelScope.launch {
            val last = dao.getLastTwoSnapshots()
            if (last.size < 2) { _changes.value = null; return@launch }
            val oldSet = dao.getFollowerUsernames(last[1].id).toSet()
            val newSet = dao.getFollowerUsernames(last[0].id).toSet()
            _changes.value = ChangeResult(
                newFollowers = (newSet - oldSet).sorted(),
                unfollowers = (oldSet - newSet).sorted(),
                oldSnapshot = last[1], newSnapshot = last[0]
            )
        }
    }

    fun deleteSnapshot(s: Snapshot) {
        viewModelScope.launch {
            dao.deleteSnapshot(s)
            _status.value = "Удалено"
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
