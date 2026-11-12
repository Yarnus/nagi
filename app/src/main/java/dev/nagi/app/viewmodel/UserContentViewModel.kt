package dev.nagi.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.nagi.app.data.AccountManager
import dev.nagi.sdk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class UserContentState(
    val topics: List<Topic> = emptyList(), val replies: List<UserReply> = emptyList(),
    val page: Int = 1, val hasMore: Boolean? = false, val loading: Boolean = false,
    val error: String? = null, val loaded: Boolean = false,
)

@HiltViewModel
class UserContentViewModel @Inject constructor(
    private val client: NgaClient, private val accounts: AccountManager, private val saved: SavedStateHandle,
) : ViewModel() {
    private val owner = accounts.state.value
    val userId: String = checkNotNull(saved["uid"])
    private val _selected = MutableStateFlow(saved.get<Boolean>("repliesSelected") ?: (saved.get<String>("kind") == "replies"))
    val selected = _selected.asStateFlow()
    val replies: Boolean get() = _selected.value
    private val cache = mutableMapOf<Boolean, UserContentState>()
    private val _state = MutableStateFlow(UserContentState())
    val state = _state.asStateFlow()
    private var request: Job? = null
    private var requestedPage = _state.value.page
    private val requestedPages = mutableMapOf<Boolean, Int>()
    private fun pageKey() = if (replies) "replyPage" else "topicPage"
    fun retry() = load(requestedPage)
    init { load(_state.value.page) }
    fun refresh() = load(1)
    fun next() {
        val current = _state.value
        if (current.loaded && !current.loading && current.error == null && current.hasMore != false) load(current.page + 1)
    }
    fun selectReplies(value: Boolean) {
        if (value == replies) return
        request?.cancel(); request = null
        cache[replies] = _state.value.copy(loading = false)
        _selected.value = value
        saved["repliesSelected"] = value
        _state.value = cache[value] ?: UserContentState()
        requestedPage = requestedPages[value] ?: _state.value.page
        if (!_state.value.loaded && _state.value.error == null) load(requestedPage)
    }
    private fun load(page: Int) {
        if (request?.isActive == true) return
        requestedPage = page
        val kind = replies
        requestedPages[kind] = page
        request = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                ensureOwner()
                val result = if (kind) {
                    val response = client.getUserReplies(userId, page)
                    UserContentState(replies = response.items, page = page, hasMore = response.hasMore, loaded = true)
                } else {
                    val response = client.getUserTopics(userId, page)
                    UserContentState(topics = response.items, page = page, hasMore = response.hasMore, loaded = true)
                }
                ensureActive()
                ensureOwner()
                saved["page"] = page
                saved[pageKey()] = page
                val previous = _state.value
                _state.value = if (page == 1) result else result.copy(
                    topics = (previous.topics + result.topics).distinctBy { it.id },
                    replies = (previous.replies + result.replies).distinctBy { it.topic.id to it.postId },
                )
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                ensureActive()
                ensureOwner()
                _state.update { it.copy(loading = false, error = userMessage(error)) }
            }
        }
    }
    private fun ensureOwner() {
        if (accounts.state.value.revision != owner.revision || accounts.state.value.busy) throw CancellationException("Account changed")
    }
}
