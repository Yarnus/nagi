package dev.nagi.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.nagi.app.data.*
import dev.nagi.app.ui.components.isPublicUserId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UserTagsState(
    val revision: Long = -1, val userId: String? = null,
    val tags: Map<String, String> = emptyMap(), val loaded: Boolean = false,
    val saving: Boolean = false, val error: String? = null,
)

@HiltViewModel
class UserTagsViewModel @Inject constructor(
    private val databases: ReadingDaoProvider, private val accounts: AccountManager,
) : ViewModel() {
    private val _state = MutableStateFlow(UserTagsState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            accounts.state.collectLatest { owner ->
                _state.value = UserTagsState(owner.revision, owner.userId)
                if (!owner.ready || owner.busy) return@collectLatest
                try {
                    databases.forUser(owner.userId).userTags().collect { tags ->
                        if (current(owner.revision, owner.userId)) _state.value = _state.value.copy(
                            tags = tags.associate { it.userId to it.text }, loaded = true)
                    }
                } catch (error: CancellationException) { throw error }
                catch (error: Exception) {
                    if (current(owner.revision, owner.userId)) _state.value = _state.value.copy(error = userMessage(error))
                }
            }
        }
    }

    private fun current(revision: Long, userId: String?) = accounts.state.value.let {
        it.ready && !it.busy && it.revision == revision && it.userId == userId
    }

    fun save(owner: UserTagsState, targetId: String, input: String, done: () -> Unit) {
        if (!owner.loaded || _state.value.saving || !current(owner.revision, owner.userId) || !isPublicUserId(targetId)) return
        val text = input.trim()
        if (!validUserTag(text)) {
            _state.value = _state.value.copy(error = "标记最多 24 个字符，不能包含换行或控制字符")
            return
        }
        _state.value = _state.value.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                accounts.withCurrentRevision(owner.revision) {
                    val dao = databases.forUser(owner.userId)
                    if (text.isEmpty()) dao.removeUserTag(targetId) else dao.saveUserTag(LocalUserTag(targetId, text))
                }
                if (current(owner.revision, owner.userId)) {
                    _state.value = _state.value.copy(saving = false)
                    done()
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (current(owner.revision, owner.userId)) _state.value = _state.value.copy(saving = false, error = userMessage(error))
            }
        }
    }
}
