package dev.nagi.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.nagi.app.data.AccountManager
import dev.nagi.sdk.NgaClient
import dev.nagi.sdk.NgaUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class AccountIdentity(
    val revision: Long = -1,
    val userId: String? = null,
    val user: NgaUser? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AccountIdentityViewModel @Inject constructor(manager: AccountManager, client: NgaClient) : ViewModel() {
    private val reload = MutableStateFlow(0L)
    fun retry() { reload.value++ }
    val identity = combine(manager.state.map { it.revision to it.userId }.distinctUntilChanged(), reload) { account, _ -> account }
        .flatMapLatest { (revision, uid) ->
            flow {
                emit(AccountIdentity(revision, uid, loading = uid != null))
                if (uid != null) {
                    try {
                        val user = client.getUser(uid)
                        check(user.id == uid)
                        emit(AccountIdentity(revision, uid, user))
                    } catch (error: CancellationException) { throw error }
                    catch (_: Exception) {
                        emit(AccountIdentity(revision, uid, error = "无法加载账号资料"))
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AccountIdentity())
}
