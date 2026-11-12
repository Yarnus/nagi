package dev.nagi.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.nagi.app.data.AccountManager
import dev.nagi.sdk.NgaClient
import dev.nagi.sdk.NgaUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UserProfileState(val user: NgaUser? = null, val loading: Boolean = false, val error: String? = null)

@HiltViewModel
class UserProfileViewModel @Inject constructor(
    private val client: NgaClient, private val accounts: AccountManager, saved: SavedStateHandle,
) : ViewModel() {
    private val owner = accounts.state.value
    private val uid: String = checkNotNull(saved["uid"])
    private val _state = MutableStateFlow(UserProfileState())
    val state = _state.asStateFlow()
    private var request: Job? = null
    init { refresh() }
    fun refresh() {
        if (request?.isActive == true) return
        request = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            fun current() = accounts.state.value.let { it.revision == owner.revision && !it.busy }
            try {
                if (!current()) return@launch
                val user = client.getUser(uid)
                check(user.id == uid)
                if (current()) _state.value = UserProfileState(user)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (current()) _state.value = _state.value.copy(loading = false, error = userMessage(error))
            }
        }
    }
}
