package dev.nagi.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.nagi.app.data.AccountManager
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
    private val client: NgaClient, private val accounts: AccountManager,
) : ViewModel() {
    private val _state = MutableStateFlow(NotificationsState())
    val state = _state.asStateFlow()
    private var request: Job? = null
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
                    _state.value = NotificationsState(account.revision, account.userId)
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
                if (current()) _state.value = NotificationsState(owner.revision, owner.userId,
                    feed.items.sortedByDescending { it.createdAt }, feed.unreadCount, loaded = true)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (current()) _state.value = _state.value.copy(loading = false, error = userMessage(error))
            }
        }
    }
}
