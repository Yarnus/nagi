package dev.nagi.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.nagi.sdk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

enum class SearchMode { FORUMS, TOPICS, USERS }

data class SearchState(
    val query: String = "",
    val submitted: String = "",
    val mode: SearchMode? = null,
    val forumId: String? = null,
    val collectionId: String? = null,
    val forumName: String? = null,
    val topics: List<Topic> = emptyList(),
    val users: List<NgaUser> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SearchViewModel @Inject constructor(private val client: NgaClient, private val saved: SavedStateHandle) : ViewModel() {
    private val _state = MutableStateFlow(SearchState(
        query = saved["query"] ?: "",
        submitted = saved["submitted"] ?: "",
        mode = saved.get<String>("mode")?.let { name -> SearchMode.entries.firstOrNull { it.name == name } },
        forumId = saved.get<String>("forumId")?.ifBlank { null },
        collectionId = saved.get<String>("collectionId")?.ifBlank { null },
        forumName = saved.get<String>("forumName")?.ifBlank { null },
    ))
    val state = _state.asStateFlow()
    private var request: Job? = null
    private var generation = 0L

    init {
        if (_state.value.mode != null && _state.value.submitted.isNotBlank()) load(true, saved["pages"] ?: 1)
    }

    fun editQuery(value: String) {
        if (value == _state.value.query) return
        generation++
        request?.cancel()
        saved["query"] = value
        saved["mode"] = null
        saved["pages"] = 1
        _state.value = _state.value.copy(query = value, mode = null, topics = emptyList(), users = emptyList(),
            loading = false, error = null, page = 0, hasMore = false)
    }

    fun submit(mode: SearchMode = _state.value.mode ?: SearchMode.TOPICS) {
        val query = _state.value.query.trim()
        if (query.isBlank()) return
        saved["submitted"] = query
        saved["mode"] = mode.name
        saved["pages"] = 1
        _state.value = _state.value.copy(submitted = query, mode = mode, topics = emptyList(), users = emptyList(), page = 0, hasMore = false)
        load(true)
    }

    fun scope(forum: Forum?) {
        saved["forumId"] = forum?.id
        saved["collectionId"] = forum?.collectionId
        saved["forumName"] = forum?.name
        _state.value = _state.value.copy(forumId = forum?.id, collectionId = forum?.collectionId, forumName = forum?.name)
        submit(SearchMode.TOPICS)
    }

    fun loadMore() = load(false)
    fun retry() = load(_state.value.page == 0)

    private fun load(refresh: Boolean, throughPage: Int = 1) {
        val source = _state.value
        val mode = source.mode ?: return
        if (!refresh && (source.loading || !source.hasMore)) return
        val version = ++generation
        request?.cancel()
        if (mode == SearchMode.FORUMS) {
            _state.value = source.copy(loading = false, error = null)
            return
        }
        request = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                if (mode == SearchMode.USERS) {
                    val user = if (source.submitted.all { it.isDigit() }) {
                        try { client.getUser(source.submitted) } catch (_: NgaException.NotFound) { null }
                    } else client.findUser(source.submitted)
                    ensureActive()
                    if (version == generation) _state.value = _state.value.copy(users = listOfNotNull(user), loading = false, page = 1)
                } else {
                    var combined = if (refresh) emptyList() else source.topics
                    val first = if (refresh) 1 else source.page + 1
                    for (number in first..if (refresh) throughPage.coerceAtLeast(1) else first) {
                        val page = client.searchTopics(source.submitted, source.forumId, number, source.collectionId)
                        ensureActive()
                        if (version != generation) return@launch
                        combined = (combined + page.items).distinctBy { it.id }
                        saved["pages"] = page.page
                        _state.value = _state.value.copy(topics = combined, page = page.page, hasMore = page.hasMore)
                        if (!page.hasMore) break
                    }
                    _state.value = _state.value.copy(loading = false)
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (version == generation) _state.value = _state.value.copy(loading = false,
                    error = if (error is NgaException.Restricted && mode == SearchMode.TOPICS)
                        "NGA 搜索需要登录及相应威望权限，或当前请求受限" else userMessage(error))
            }
        }
    }
}
