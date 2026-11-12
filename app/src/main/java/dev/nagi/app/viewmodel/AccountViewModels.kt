package dev.nagi.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.nagi.app.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountViewModel @Inject constructor(private val manager: AccountManager, prefs: PreferenceStore) : ViewModel() {
    val account = manager.state
    val preferences = prefs.preferences.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReadingPreferences())
    init { initialize() }
    fun initialize() = viewModelScope.launch { manager.initialize() }
    fun switchAccount(id: String) = action { manager.switchAccount(id) }
    fun logout() = action { manager.logout() }
    private fun action(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) {  }
    }
}

data class LoginState(val ready: Boolean = false, val checking: Boolean = false, val error: String? = null)

@HiltViewModel
class LoginViewModel @Inject constructor(private val manager: AccountManager) : ViewModel() {
    private val _state = MutableStateFlow(LoginState())
    val state = _state.asStateFlow()
    private var attempt: LoginAttempt? = null
    private var preparation: Job? = null
    private var capture: Job? = null
    init { prepare() }
    fun prepare() {
        if (preparation?.isActive == true || capture?.isActive == true) return
        _state.value = LoginState()
        preparation = viewModelScope.launch {
            try {
                attempt = manager.beginLogin()
                _state.value = LoginState(ready = true)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { _state.value = LoginState(error = "无法准备登录环境，请重试") }
        }
    }
    fun onPageLoaded(url: String) = capture(url, showError = false)
    fun finish(url: String) = capture(url, showError = true)
    private fun capture(url: String, showError: Boolean) {
        val current = attempt ?: return
        if (!_state.value.ready || capture?.isActive == true) return
        val previous = _state.value
        _state.value = previous.copy(checking = true, error = if (showError) null else previous.error)
        capture = viewModelScope.launch {
            try {
                if (manager.captureAndSave(current, url)) {
                    attempt = null
                    _state.value = LoginState(ready = true)
                } else {
                    _state.value = LoginState(ready = true, error = if (showError) "尚未检测到一致的登录凭证，请完成网页验证后刷新再试" else previous.error)
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { _state.value = LoginState(ready = true, error = "无法安全保存登录凭证，请重试") }
        }
    }
    override fun onCleared() {
        attempt?.let(manager::discardLogin)
        super.onCleared()
    }
}
