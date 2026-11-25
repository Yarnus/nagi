package dev.nagi.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.nagi.app.data.AccountManager
import dev.nagi.app.data.ReadingDaoProvider
import dev.nagi.sdk.NgaSmile
import dev.nagi.sdk.NgaSmiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecentSmilesState(
    val revision: Long = -1,
    val userId: String? = null,
    val smiles: List<NgaSmile> = emptyList(),
)

/** Per-account recently used emoticons, ordered most-recent first. */
@HiltViewModel
class RecentSmilesViewModel @Inject constructor(
    private val databases: ReadingDaoProvider,
    private val accounts: AccountManager,
) : ViewModel() {
    private val _state = MutableStateFlow(RecentSmilesState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            accounts.state.collectLatest { owner ->
                _state.value = RecentSmilesState(owner.revision, owner.userId)
                if (!owner.ready || owner.busy) return@collectLatest
                try {
                    databases.forUser(owner.userId).recentSmiles().collect { entries ->
                        if (current(owner.revision, owner.userId)) {
                            _state.value = _state.value.copy(
                                smiles = entries.mapNotNull { NgaSmiles.resolve(it.smileKey) },
                            )
                        }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // Keep empty recent list on unexpected local read errors without disrupting editing.
                }
            }
        }
    }

    private fun current(revision: Long, userId: String?) = accounts.state.value.let {
        it.ready && !it.busy && it.revision == revision && it.userId == userId
    }

    fun record(smile: NgaSmile) {
        val owner = accounts.state.value
        if (!owner.ready || owner.busy) return
        val revision = owner.revision
        val userId = owner.userId
        val timestamp = System.currentTimeMillis()
        viewModelScope.launch {
            try {
                accounts.withCurrentRevision(revision) {
                    databases.forUser(userId).recordRecentSmile(smile.key, timestamp)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Recording recent usage is best-effort and must not interrupt text insertion.
            }
        }
    }

    fun clear() {
        val owner = accounts.state.value
        if (!owner.ready || owner.busy) return
        val revision = owner.revision
        val userId = owner.userId
        viewModelScope.launch {
            try {
                accounts.withCurrentRevision(revision) {
                    databases.forUser(userId).clearRecentSmiles()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Best-effort local cleanup.
            }
        }
    }
}
