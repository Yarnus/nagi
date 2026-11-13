package dev.nagi.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.nagi.app.data.AccountManager
import dev.nagi.app.data.LocalNotification
import dev.nagi.app.data.ReadingDaoProvider
import dev.nagi.sdk.CommunityItem
import dev.nagi.sdk.NgaClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationsState(
    val revision: Long = -1, val userId: String? = null,
    val items: List<CommunityItem> = emptyList(), val unreadCount: Int? = null,
    val loading: Boolean = false, val loaded: Boolean = false, val error: String? = null,
)

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val client: NgaClient, private val accounts: AccountManager, private val databases: ReadingDaoProvider,
) : ViewModel() {
    private val _state = MutableStateFlow(NotificationsState())
    val state = _state.asStateFlow()
    private var request: Job? = null
    private var history: Job? = null
    private var foreground = false

    init {
        viewModelScope.launch {
            var wasAvailable = false
            accounts.state.collect { account ->
                val available = account.ready && !account.busy
                val changed = account.revision != _state.value.revision || account.userId != _state.value.userId
                if (!available || changed) {
                    request?.cancel()
                    request = null
                    history?.cancel()
                    history = null
                    _state.value = NotificationsState(account.revision, account.userId)
                }
                if (available && account.userId != null && history == null) {
                    history = viewModelScope.launch {
                        try {
                            databases.forUser(account.userId).notifications().collect { records ->
                                if (accounts.state.value.let { it.ready && !it.busy && it.revision == account.revision && it.userId == account.userId }) {
                                    val items = records.map { it.item() }
                                    _state.value = _state.value.copy(items = items, unreadCount = items.count { it.unread }, loaded = true)
                                }
                            }
                        } catch (error: CancellationException) { throw error }
                        catch (error: Exception) {
                            if (_state.value.revision == account.revision) _state.value = _state.value.copy(error = userMessage(error))
                        }
                    }
                }
                if (foreground && available && (!wasAvailable || changed)) refresh()
                wasAvailable = available
            }
        }
    }

    fun setForeground(value: Boolean) {
        if (foreground == value) return
        foreground = value
        if (value) refresh()
    }

    fun markRead(item: CommunityItem) = markReadIds(listOf(item.id))

    fun markAllRead() = markReadIds(_state.value.items.filter { it.unread }.map { it.id })

    private fun markReadIds(ids: List<String>) {
        val owner = accounts.state.value
        if (ids.isEmpty() || !owner.ready || owner.busy || owner.userId == null ||
            owner.revision != _state.value.revision || owner.userId != _state.value.userId) return
        viewModelScope.launch {
            try {
                accounts.withCurrentRevision(owner.revision) { databases.forUser(owner.userId).readNotifications(ids) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (accounts.state.value.revision == owner.revision) _state.value = _state.value.copy(error = userMessage(error))
            }
        }
    }

    fun refresh() {
        val owner = accounts.state.value
        if (!owner.ready || owner.busy || owner.userId == null || request?.isActive == true) return
        if (_state.value.revision != owner.revision || _state.value.userId != owner.userId) {
            _state.value = NotificationsState(owner.revision, owner.userId)
        }
        _state.value = _state.value.copy(loading = true, error = null)
        request = viewModelScope.launch {
            fun current() = accounts.state.value.let {
                it.ready && !it.busy && it.revision == owner.revision && it.userId == owner.userId
            }
            try {
                val feed = client.getNotificationFeed()
                if (!current()) return@launch
                accounts.withCurrentRevision(owner.revision) {
                    databases.forUser(owner.userId).receiveNotifications(feed.items.map(LocalNotification::received))
                }
                if (current()) _state.value = _state.value.copy(loading = false, loaded = true)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (current()) _state.value = _state.value.copy(loading = false, error = userMessage(error))
            }
        }
    }
}
