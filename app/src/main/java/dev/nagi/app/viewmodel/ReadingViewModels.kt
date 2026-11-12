package dev.nagi.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.nagi.app.data.*
import dev.nagi.sdk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

fun userMessage(error: Throwable): String = when (error) {
    is NgaException.Authentication -> "请先登录 NGA，或当前登录已过期"
    is NgaException.Restricted -> rejectionMessage(error.reason) ?: "NGA 限制了当前请求，请稍后重试"
    is NgaException.NotFound -> "未找到请求的内容"
    is NgaException.Protocol -> "无法识别 NGA 响应，请重试 (${error.code})"
    is NgaException.Business -> rejectionMessage(error.reason) ?: "NGA 拒绝了此次请求（未提供可识别的原因）"
    is NgaException.Http -> "NGA 服务暂时不可用 (${error.status})，请稍后重试"
    else -> "加载失败，请检查网络后重试"
}

private fun rejectionMessage(reason: NgaRejectionReason): String? = when (reason) {
    NgaRejectionReason.PERMISSION -> "当前账号或帖子不允许此操作，请检查版块权限、威望或帖子锁定状态"
    NgaRejectionReason.MUTED -> "当前账号被限制发言，请检查禁言状态"
    NgaRejectionReason.RATE_LIMIT -> "操作过于频繁，请稍后再试"
    NgaRejectionReason.CONTENT_LENGTH -> "正文长度不符合 NGA 要求，请调整后再试"
    NgaRejectionReason.CONTENT_FILTER -> "正文触发了 NGA 内容过滤，请检查内容"
    NgaRejectionReason.VERIFICATION -> "NGA 要求额外验证，请前往官方页面处理"
    NgaRejectionReason.INVALID_PARAMETERS -> "NGA 判定请求参数无效，请反馈此问题"
    NgaRejectionReason.DUPLICATE -> "NGA 提示重复提交，请先检查帖子，勿重复发送"
    NgaRejectionReason.UNKNOWN -> null
}

data class ListState<T>(val items: List<T> = emptyList(), val loading: Boolean = false, val error: String? = null, val page: Int = 0, val hasMore: Boolean = false)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val client: NgaClient,
    private val daos: ReadingDaoProvider,
    private val sessions: SessionStore,
    private val prefs: PreferenceStore,
    private val accounts: AccountManager,
) : ViewModel() {
    private val _forums = MutableStateFlow(ListState<Forum>())
    val forums = _forums.asStateFlow()
    private val owner = accounts.state.value
    private val dao = daos.forUser(owner.userId)
    val favorites = dao.favorites().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val forumFavorites = dao.forumFavorites().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val history = dao.history().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private var loading: Job? = null
    init { loadForums() }
    fun loadForums() {
        if (loading?.isActive == true) return
        loading = viewModelScope.launch {
            _forums.update { it.copy(loading = true, error = null) }
            try {
                if (sessions.snapshot().revision != owner.revision) throw CancellationException("Account changed")
                val forums = client.getForums()
                if (sessions.snapshot().revision != owner.revision) throw CancellationException("Account changed")
                _forums.value = ListState(forums)
            }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { _forums.update { it.copy(loading = false, error = userMessage(error)) } }
        }
    }
    fun removeFavorite(id: String) = viewModelScope.launch {
        accounts.withCurrentRevision(owner.revision) { dao.removeFavorite(id) }
    }
    fun toggleForumFavorite(forum: Forum) = viewModelScope.launch {
        accounts.withCurrentRevision(owner.revision) {
            dao.toggleForumFavorite(
                LocalForumFavorite(
                    forumKey = forum.key,
                    forumId = forum.id,
                    collectionId = forum.collectionId,
                    name = forum.name,
                    description = forum.description,
                    category = forum.category,
                    groupName = forum.group,
                ),
            )
        }
    }
    fun removeForumFavorite(key: String) = viewModelScope.launch {
        accounts.withCurrentRevision(owner.revision) { dao.removeForumFavorite(key) }
    }
    fun font(size: Int) = viewModelScope.launch { prefs.font(size) }
    fun lineHeight(size: Int) = viewModelScope.launch { prefs.lineHeight(size) }
    fun theme(value: String) = viewModelScope.launch { prefs.theme(value) }
}

@HiltViewModel
class TopicsViewModel @Inject constructor(
    private val client: NgaClient, private val saved: SavedStateHandle, private val accounts: AccountManager,
) : ViewModel() {
    private val owner = accounts.state.value
    private fun current() = accounts.state.value.let {
        it.ready && !it.busy && it.revision == owner.revision && it.userId == owner.userId
    }
    private val _state = MutableStateFlow(ListState<Topic>())
    val state = _state.asStateFlow()
    private val _sort = MutableStateFlow(TopicSort.entries.firstOrNull { it.name == saved.get<String>("sort") } ?: TopicSort.NEWEST)
    val sort = _sort.asStateFlow()
    fun changeSort(value: TopicSort) {
        if (_filterBusy.value || value == _sort.value) return
        _sort.value = value; saved["sort"] = value.name
        load(true)
    }
    private val _subforums = MutableStateFlow<List<Subforum>>(emptyList())
    val subforums = _subforums.asStateFlow()
    private val _filterBusy = MutableStateFlow(false)
    val filterBusy = _filterBusy.asStateFlow()
    private val _filterNotice = MutableStateFlow<String?>(null)
    val filterNotice = _filterNotice.asStateFlow()
    private val _filterLocked = MutableStateFlow(false)
    val filterLocked = _filterLocked.asStateFlow()
    private var writingSource: Pair<String, String?>? = null
    private val lockedSources = mutableSetOf<Pair<String, String?>>()
    private val _filterEpoch = MutableStateFlow(0)
    val filterEpoch = _filterEpoch.asStateFlow()

    fun setSubforumVisible(subforum: Subforum, visible: Boolean) {
        val parent = source ?: return
        if (!current()) return
        if (owner.userId == null) {
            _filterNotice.value = "请先登录后再设置子板块过滤"
            return
        }
        if (_filterBusy.value || _filterLocked.value || !subforum.filterable || parent.second != null) return
        _filterBusy.value = true
        writingSource = parent
        lockedSources.add(parent)
        request?.cancel()
        _state.update { it.copy(loading = false) }
        val version = ++generation
        viewModelScope.launch {
            try {
                if (!current()) return@launch
                val result = client.setSubforumVisibility(parent.first, subforum.filterId, visible)
                if (result is WriteResult.Rejected) lockedSources.remove(parent)
                if (!current() || version != generation || source != parent) return@launch
                when (result) {
                    is WriteResult.Confirmed -> {
                        _filterNotice.value = "设置已保存，正在刷新列表"
                        _state.value = ListState()
                        _filterLocked.value = true
                        _filterEpoch.value++
                        saved["pages"] = 1
                        _filterBusy.value = false
                        load(true)
                    }
                    is WriteResult.Rejected -> _filterNotice.value = userMessage(result.reason)
                    is WriteResult.Unconfirmed -> {
                        _filterLocked.value = true
                        _filterNotice.value = "设置结果未确认，请刷新核对后再操作"
                    }
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (!current() || version != generation || source != parent) return@launch
                _filterLocked.value = true
                _filterNotice.value = "设置结果未确认，请刷新核对后再操作"
            } finally {
                _filterBusy.value = false
                writingSource = null
                _filterLocked.value = source in lockedSources
            }
        }
    }

    private var request: Job? = null
    private var source: Pair<String, String?>? = null
    private var keyword: String? = null
    private var generation = 0L

    fun openForum(id: String, collection: String?) {
        if (source == id to collection && keyword == null) return
        source = id to collection; keyword = null; _state.value = ListState()
        _subforums.value = emptyList(); _filterNotice.value = null
        _filterLocked.value = (id to collection) in lockedSources
        val key = "$id:${collection.orEmpty()}"
        val pages = if (saved.get<String>("forum") == key) saved.get<Int>("pages") ?: 1 else 1
        saved["forum"] = key
        saved["searchKey"] = null
        loadPages(refresh = true, throughPage = pages)
    }
    fun clearSearch() {
        generation++
        request?.cancel()
        keyword = null; source = null
        saved["searchKey"] = null; saved["pages"] = 1
        _state.value = ListState()
    }
    fun search(value: String, forumId: String, collectionId: String? = null) {
        if (value.isBlank()) return
        val scope = forumId to collectionId
        if (keyword == value && source == scope && (_state.value.items.isNotEmpty() || _state.value.loading)) return
        keyword = value; source = scope; _state.value = ListState()
        val key = "$forumId:${collectionId.orEmpty()}:$value"
        val pages = if (saved.get<String>("searchKey") == key) saved.get<Int>("pages") ?: 1 else 1
        saved["searchKey"] = key
        loadPages(refresh = true, throughPage = pages)
    }
    fun load(refresh: Boolean = false) = loadPages(refresh, 1)

    private fun loadPages(refresh: Boolean, throughPage: Int) {
        if (_filterBusy.value && writingSource == source) return
        if (!refresh && (_filterLocked.value || _state.value.loading || !_state.value.hasMore)) return
        val next = if (refresh) 1 else _state.value.page + 1
        val source = source; val keyword = keyword; val order = _sort.value
        if (source == null && keyword == null) return
        val version = ++generation
        request?.cancel()
        request = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                var combined = if (refresh) emptyList() else _state.value.items
                var result: TopicPage? = null
                val lastPage = if (refresh) throughPage.coerceAtLeast(1) else next
                for (number in next..lastPage) {
                    val page = if (keyword != null) client.searchTopics(keyword, source!!.first, number, source.second) else client.getTopics(source!!.first, number, source.second, order)
                    ensureActive()
                    if (version != generation) return@launch
                    combined = (combined + page.items).distinctBy { it.id }
                    result = page
                    if (!page.hasMore) break
                }
                val page = requireNotNull(result)
                saved["pages"] = page.page
                if (keyword == null) {
                    _subforums.value = page.subforums
                    lockedSources.remove(source)
                    _filterLocked.value = false
                    _filterNotice.value = null
                }
                _state.value = ListState(combined, page = page.page, hasMore = page.hasMore)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { if (version == generation) _state.update { it.copy(loading = false, error = userMessage(error)) } }
        }
    }
}

data class ReaderState(
    val id: String = "", val data: PostPage? = null, val loading: Boolean = false,
    val error: String? = null, val position: ReadingPosition? = null, val authorOnly: Boolean = false,
    val more: List<PostPage> = emptyList(), val loadingMore: Boolean = false, val moreError: String? = null,
    val loadingPrevious: Boolean = false, val previousError: String? = null,
    val epoch: Int = 0, val targetFloor: Int? = null,
    val loadedRevision: Long = 0, val loadedPostIds: List<String> = emptyList(),
) {
    val pages: List<PostPage> = listOfNotNull(data) + more
    val items: List<Post> = pages.flatMap { it.items }
    val startPage: Int get() = data?.page ?: 0
    val endPage: Int get() = pages.lastOrNull()?.page ?: 0
    val hasMore: Boolean get() = pages.lastOrNull()?.hasMore == true
    val totalPages: Int get() = pages.maxOfOrNull { it.totalPages } ?: 0
    val canJumpFloor: Boolean get() = !authorOnly && (data?.pageSize ?: 0) > 0 && (data?.totalPages ?: 0) > 0
    val headerCount: Int get() = if (startPage > 1) 1 else 0

    fun itemIndex(page: Int, index: Int): Int? {
        var offset = 0
        for (loaded in pages) {
            if (loaded.page == page) return offset + index.coerceIn(0, (loaded.items.size - 1).coerceAtLeast(0))
            offset += loaded.items.size
        }
        return null
    }

    fun locate(listIndex: Int): Pair<Int, Int>? {
        var rest = (listIndex - headerCount).coerceAtLeast(0)
        for (loaded in pages) {
            if (rest < loaded.items.size) return loaded.page to rest
            rest -= loaded.items.size
        }
        return pages.lastOrNull()?.let { it.page to (it.items.size - 1).coerceAtLeast(0) }
    }
}

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val client: NgaClient,
    private val daos: ReadingDaoProvider,
    private val sessions: SessionStore,
    private val accounts: AccountManager,
) : ViewModel() {
    private val _state = MutableStateFlow(ReaderState())
    val state = _state.asStateFlow()
    private val owner = accounts.state.value
    private val dao = daos.forUser(owner.userId)
    val favorites = dao.favorites().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private var request: Job? = null
    private var moreRequest: Job? = null
    private var generation = 0L
    private var requestedPage: Int? = null
    private var requestedFloor: Int? = null
    private var requestedAuthorOnly = false
    private var latestPosition: ReadingPosition? = null
    fun open(id: String) {
        if (_state.value.id == id) {
            if (!_state.value.authorOnly) _state.update { it.copy(position = latestPosition ?: it.position, targetFloor = null) }
            return
        }
        requestedPage = null; requestedFloor = null; requestedAuthorOnly = false; latestPosition = null
        moreRequest?.cancel()
        _state.value = ReaderState(id = id, loadedRevision = _state.value.loadedRevision)
        load(restore = true)
    }
    fun load(page: Int? = null, authorOnly: Boolean? = null, restore: Boolean = false, floor: Int? = null) {
        val old = _state.value
        val version = ++generation
        request?.cancel()
        moreRequest?.cancel()
        request = viewModelScope.launch {
            _state.update { it.copy(loading = true, loadingMore = false, loadingPrevious = false, error = null) }
            try {
                if (sessions.snapshot().revision != owner.revision) throw CancellationException("Account changed")
                val position = if (restore) dao.position(old.id) else latestPosition ?: old.position
                val target = page ?: (if (old.error != null) requestedPage else old.data?.page) ?: position?.page ?: 1
                val filtered = authorOnly ?: if (old.error != null) requestedAuthorOnly else old.authorOnly
                val targetFloor = if (page != null || old.error == null) floor else requestedFloor
                requestedPage = target; requestedFloor = targetFloor; requestedAuthorOnly = filtered
                val author = old.data?.authorId?.takeIf { filtered }
                val data = client.getPosts(old.id, target, author)
                ensureActive()
                if (version != generation || sessions.snapshot().revision != owner.revision) return@launch
                try {
                    accounts.withCurrentRevision(owner.revision) {
                        dao.saveHistory(ReadingHistory(old.id, data.title, System.currentTimeMillis()))
                    }
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) {  }
                if (version != generation) return@launch
                _state.value = ReaderState(
                    old.id, data, position = position?.takeIf { it.page == target && !filtered && targetFloor == null },
                    authorOnly = filtered, epoch = old.epoch + 1, targetFloor = targetFloor,
                    loadedRevision = old.loadedRevision + 1, loadedPostIds = data.items.map { it.id },
                )
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { if (version == generation) _state.update { it.copy(loading = false, error = userMessage(error)) } }
        }
    }
    fun loadNext() {
        val state = _state.value
        if (state.data == null || state.loading || state.loadingMore || state.loadingPrevious || state.moreError != null || !state.hasMore) return
        fetchAdjacent(state, state.endPage + 1, prepend = false)
    }
    fun retryMore() {
        _state.update { it.copy(moreError = null) }
        loadNext()
    }
    fun loadPrevious() {
        val state = _state.value
        if (state.data == null || state.loading || state.loadingMore || state.loadingPrevious || state.startPage <= 1) return
        fetchAdjacent(state, state.startPage - 1, prepend = true)
    }
    private fun fetchAdjacent(state: ReaderState, page: Int, prepend: Boolean) {
        val version = generation
        moreRequest?.cancel()
        _state.update {
            if (prepend) it.copy(loadingPrevious = true, previousError = null)
            else it.copy(loadingMore = true, moreError = null)
        }
        moreRequest = viewModelScope.launch {
            try {
                if (sessions.snapshot().revision != owner.revision) throw CancellationException("Account changed")
                val author = state.data?.authorId?.takeIf { state.authorOnly }
                val result = client.getPosts(state.id, page, author)
                ensureActive()
                if (version != generation || sessions.snapshot().revision != owner.revision) return@launch
                _state.update { current ->
                    if (current.epoch != state.epoch || current.id != state.id || current.data == null) return@update current
                    val adjacent = if (prepend) current.startPage == page + 1 else current.endPage == page - 1
                    if (!adjacent) return@update current.copy(loadingMore = false, loadingPrevious = false)
                    val known = current.items.mapTo(HashSet()) { it.id }
                    val fresh = result.copy(items = result.items.filter { it.id !in known })
                    val updated = if (prepend) current.copy(data = fresh, more = listOf(current.data) + current.more, loadingPrevious = false, previousError = null)
                    else current.copy(more = current.more + fresh, loadingMore = false, moreError = null)
                    updated.copy(loadedRevision = current.loadedRevision + 1, loadedPostIds = fresh.items.map { it.id })
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (version == generation) _state.update {
                    if (prepend) it.copy(loadingPrevious = false, previousError = userMessage(error))
                    else it.copy(loadingMore = false, moreError = userMessage(error))
                }
            }
        }
    }
    fun jumpToPage(page: Int) {
        val state = _state.value
        val total = state.totalPages
        load(page.coerceAtLeast(1).let { if (total > 0) it.coerceAtMost(total) else it })
    }
    fun jumpToFloor(floor: Int) {
        val state = _state.value
        val data = state.data ?: return
        if (!state.canJumpFloor) return
        val target = floor.coerceIn(0, (data.total - 1).coerceAtLeast(0))
        if (state.items.any { it.floor == target }) {
            _state.update { it.copy(targetFloor = target, position = null, epoch = it.epoch + 1) }
            return
        }
        val page = data.pageOfFloor(target) ?: return
        load(page = if (state.totalPages > 0) page.coerceAtMost(state.totalPages) else page, floor = target)
    }
    suspend fun saveVisible(listIndex: Int, offset: Int, topicId: String = _state.value.id) {
        val state = _state.value
        val (page, index) = state.locate(listIndex) ?: return
        val onHeader = state.headerCount > 0 && listIndex < state.headerCount
        savePosition(page, if (onHeader) 0 else index, if (onHeader) 0 else offset, topicId)
    }
    suspend fun savePosition(page: Int, index: Int, offset: Int, topicId: String = _state.value.id) {
        val state = _state.value
        if (state.id != topicId || state.data == null || page !in state.startPage..state.endPage || state.authorOnly || state.loading) return
        accounts.withCurrentRevision(owner.revision) {
            val position = ReadingPosition(state.id, page, index, offset)
            dao.savePosition(position)
            latestPosition = position
        }
    }
    fun toggleFavorite() = viewModelScope.launch {
        val value = _state.value
        val data = value.data ?: return@launch
        accounts.withCurrentRevision(owner.revision) { dao.toggleFavorite(LocalFavorite(value.id, data.title)) }
    }
}
