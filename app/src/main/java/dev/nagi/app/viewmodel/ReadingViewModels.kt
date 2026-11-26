package dev.nagi.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.nagi.app.data.*
import dev.nagi.sdk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val _rawCloudFavoriteForums = MutableStateFlow(ListState<Forum>())
    private val _cloudForumBusyKeys = MutableStateFlow<Set<String>>(emptySet())
    val cloudForumBusyKeys = _cloudForumBusyKeys.asStateFlow()
    private val _cloudForumUncertainKeys = MutableStateFlow<Set<String>>(emptySet())
    val cloudForumUncertainKeys = _cloudForumUncertainKeys.asStateFlow()
    private val _cloudForumNotice = MutableStateFlow<String?>(null)
    val cloudForumNotice = _cloudForumNotice.asStateFlow()
    private val _cloudForumFeedback = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val cloudForumFeedback = _cloudForumFeedback.asSharedFlow()
    private val owner = accounts.state.value
    val isSignedIn: Boolean get() = owner.userId != null
    private val dao = daos.forUser(owner.userId)
    private val cloudFavoriteOrders = dao.cloudForumFavoriteOrders()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val hasCustomCloudForumOrder: StateFlow<Boolean> = cloudFavoriteOrders
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val cloudFavoriteForums: StateFlow<ListState<Forum>> = combine(
        _rawCloudFavoriteForums,
        cloudFavoriteOrders,
    ) { state, orders ->
        state.copy(items = applyCloudForumOrder(state.items, orders))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ListState())
    val favorites = dao.favorites().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val history = dao.history().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val topicReadStates: StateFlow<Map<String, TopicReadState>> = dao.topicReadStates()
        .map { list -> list.associateBy { it.topicId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    private var loading: Job? = null
    private var cloudForumsLoading: Job? = null
    private val cloudForumWriteMutex = Mutex()
    private fun currentOwner() = accounts.state.value.let {
        it.ready && !it.busy && it.revision == owner.revision && it.userId == owner.userId
    }
    init {
        loadForums()
        if (owner.userId != null) loadCloudFavoriteForums()
    }
    fun releaseInactiveDatabases() {
        viewModelScope.launch(Dispatchers.IO) { daos.closeInactive(owner.userId) }
    }
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
    fun loadCloudFavoriteForums() {
        if (owner.userId == null) {
            _rawCloudFavoriteForums.value = ListState()
            return
        }
        if (cloudForumsLoading?.isActive == true) return
        cloudForumsLoading = viewModelScope.launch {
            _rawCloudFavoriteForums.update { it.copy(loading = true, error = null) }
            try {
                if (sessions.snapshot().revision != owner.revision) throw CancellationException("Account changed")
                val items = client.getFavoriteForums()
                if (sessions.snapshot().revision != owner.revision) throw CancellationException("Account changed")
                _rawCloudFavoriteForums.value = ListState(items)
                _cloudForumUncertainKeys.value = emptySet()
                _cloudForumNotice.value = null
            }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                _rawCloudFavoriteForums.update { it.copy(loading = false, error = userMessage(error)) }
            }
        }
    }
    fun setCloudForumFavorite(forum: Forum, favorite: Boolean) {
        if (owner.userId == null) {
            val msg = "请先登录 NGA 后再管理 NGA 收藏版块"
            _cloudForumNotice.value = msg
            _cloudForumFeedback.tryEmit(msg)
            return
        }
        val key = forum.key
        if (key in _cloudForumBusyKeys.value || key in _cloudForumUncertainKeys.value) return
        val target = forum.collectionId ?: forum.id
        if (target.toLongOrNull()?.let { it != 0L } != true) {
            val msg = "当前版块标识无效，无法加入 NGA 收藏"
            _cloudForumNotice.value = msg
            _cloudForumFeedback.tryEmit(msg)
            return
        }
        _cloudForumBusyKeys.update { it + key }
        viewModelScope.launch {
            try {
                cloudForumWriteMutex.withLock {
                    if (!currentOwner() || sessions.snapshot().revision != owner.revision) {
                        throw CancellationException("Account changed")
                    }
                    val result = try {
                        client.setForumFavorite(forum, favorite)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        WriteResult.Unconfirmed()
                    }
                    if (!currentOwner() || sessions.snapshot().revision != owner.revision) {
                        throw CancellationException("Account changed")
                    }
                    when (result) {
                        is WriteResult.Confirmed -> {
                            _cloudForumNotice.value = null
                            _cloudForumFeedback.tryEmit(if (favorite) "已加入 NGA 收藏版块" else "已取消 NGA 收藏版块")
                            cloudForumsLoading?.cancel()
                            _rawCloudFavoriteForums.update { current ->
                                val updated = if (favorite) {
                                    if (current.items.any { it.key == key }) current.items else current.items + forum
                                } else {
                                    current.items.filterNot { it.key == key }
                                }
                                current.copy(items = updated, loading = false, error = null)
                            }
                        }
                        is WriteResult.Rejected -> {
                            val msg = writeFeedback(userMessage(result.reason), result)
                            _cloudForumNotice.value = msg
                            _cloudForumFeedback.tryEmit(userMessage(result.reason))
                        }
                        is WriteResult.Unconfirmed -> {
                            _cloudForumUncertainKeys.update { it + key }
                            val msg = writeFeedback("NGA 收藏结果未确认，请刷新核对后再操作", result)
                            _cloudForumNotice.value = msg
                            _cloudForumFeedback.tryEmit("NGA 收藏结果未确认，请刷新核对")
                        }
                    }
                }
            } finally {
                _cloudForumBusyKeys.update { it - key }
            }
        }
    }
    fun toggleCloudForumFavorite(forum: Forum) {
        val isCloudFavorite = _rawCloudFavoriteForums.value.items.any { it.key == forum.key }
        setCloudForumFavorite(forum, !isCloudFavorite)
    }
    fun saveCloudFavoriteForumOrder(orderedKeys: List<String>) {
        if (owner.userId == null) return
        viewModelScope.launch {
            accounts.withCurrentRevision(owner.revision) {
                dao.replaceCloudForumFavoriteOrders(orderedKeys)
            }
        }
    }
    fun resetCloudFavoriteForumOrder() {
        if (owner.userId == null) return
        viewModelScope.launch {
            accounts.withCurrentRevision(owner.revision) {
                dao.clearCloudForumFavoriteOrders()
            }
        }
    }
    fun dismissCloudForumNotice() {
        _cloudForumNotice.value = null
    }
    fun removeFavorite(id: String) = viewModelScope.launch {
        accounts.withCurrentRevision(owner.revision) { dao.removeFavorite(id) }
    }
    fun removeHistory(topicId: String) = viewModelScope.launch {
        accounts.withCurrentRevision(owner.revision) { dao.removeHistory(topicId) }
    }
    fun clearHistory(clearUnfavoritedPositions: Boolean = false) = viewModelScope.launch {
        accounts.withCurrentRevision(owner.revision) {
            dao.clearHistory()
            if (clearUnfavoritedPositions) {
                dao.clearUnfavoritedPositions()
                dao.clearUnfavoritedReadStates()
            }
        }
    }
    fun font(size: Int) = viewModelScope.launch { prefs.font(size) }
    fun lineHeight(size: Int) = viewModelScope.launch { prefs.lineHeight(size) }
    fun resetTypography() = viewModelScope.launch { prefs.resetTypography() }
    fun theme(value: String) = viewModelScope.launch { prefs.theme(value) }
    fun setClipboardLinkDetection(enabled: Boolean) = viewModelScope.launch { prefs.clipboardLinkDetection(enabled) }
    fun setAutoCheckUpdates(enabled: Boolean) = viewModelScope.launch { prefs.autoCheckUpdates(enabled) }
    fun setNgaDomain(domain: NgaDomain) = viewModelScope.launch { prefs.ngaDomain(domain) }
    suspend fun resolveExternalTargetTitle(target: NgaExternalTarget): String? = when (target) {
        is NgaExternalTarget.Topic -> {
            val cached = favorites.value.firstOrNull { it.topicId == target.topicId }?.title?.trim()?.takeIf { it.isNotEmpty() }
                ?: dao.favorite(target.topicId)?.title?.trim()?.takeIf { it.isNotEmpty() }
                ?: history.value.firstOrNull { it.topicId == target.topicId }?.title?.trim()?.takeIf { it.isNotEmpty() }
                ?: dao.historyTitle(target.topicId)?.trim()?.takeIf { it.isNotEmpty() }
            if (cached != null) {
                cached
            } else if (!currentOwner() || sessions.snapshot().revision != owner.revision) {
                null
            } else {
                try {
                    val title = client.getPosts(target.topicId, page = 1).title.trim().takeIf { it.isNotEmpty() }
                    if (currentOwner() && sessions.snapshot().revision == owner.revision) title else null
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                }
            }
        }
        is NgaExternalTarget.Forum -> {
            val match = (_forums.value.items + cloudFavoriteForums.value.items).firstOrNull {
                it.id == target.forumId && it.collectionId.orEmpty() == target.collectionId.orEmpty()
            }
            match?.name?.trim()?.takeIf { it.isNotEmpty() }
        }
        is NgaExternalTarget.User -> null
    }
}

internal fun applyCloudForumOrder(
    ngaForums: List<Forum>,
    localOrders: List<CloudForumFavoriteOrder>,
): List<Forum> {
    if (ngaForums.isEmpty() || localOrders.isEmpty()) return ngaForums
    val orderMap = localOrders.associate { it.forumKey to it.sortOrder }
    val ranked = ArrayList<Pair<Int, Forum>>()
    val fallback = ArrayList<Forum>()
    for (forum in ngaForums) {
        val rank = orderMap[forum.key]
        if (rank != null) ranked.add(rank to forum) else fallback.add(forum)
    }
    ranked.sortBy { it.first }
    return ArrayList<Forum>(ngaForums.size).apply {
        ranked.forEach { add(it.second) }
        addAll(fallback)
    }
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
    private val noRecommendSources = mutableSetOf<Pair<String, String?>>()
    private val _recommendAvailable = MutableStateFlow(true)
    val recommendAvailable = _recommendAvailable.asStateFlow()
    init {
        viewModelScope.launch {
            accounts.state.collect {
                if (!current()) {
                    request?.cancel()
                    noRecommendSources.clear()
                    _recommendAvailable.value = true
                }
            }
        }
    }
    fun changeSort(value: TopicSort) {
        if (!current() || _filterBusy.value || value == _sort.value) return
        if (value == TopicSort.RECOMMENDED && !_recommendAvailable.value) return
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
        val target = id to collection
        source = target; keyword = null; _state.value = ListState()
        _subforums.value = emptyList(); _filterNotice.value = null
        _filterLocked.value = target in lockedSources
        val hasRecommend = target !in noRecommendSources
        _recommendAvailable.value = hasRecommend
        if (!hasRecommend && _sort.value == TopicSort.RECOMMENDED) {
            _sort.value = TopicSort.NEWEST
            saved["sort"] = TopicSort.NEWEST.name
        }
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
                val seenIds = combined.mapTo(HashSet(combined.size + 32)) { it.id }
                var result: TopicPage? = null
                val lastPage = if (refresh) throughPage.coerceAtLeast(1) else next
                for (number in next..lastPage) {
                    if (!current()) return@launch
                    val page = if (keyword != null) client.searchTopics(keyword, source!!.first, number, source.second) else client.getTopics(source!!.first, number, source.second, order)
                    ensureActive()
                    if (version != generation || !current()) return@launch
                    val fresh = page.items.filter { seenIds.add(it.id) }
                    if (fresh.isNotEmpty()) {
                        combined = combined + fresh
                    }
                    result = page
                    if (!page.hasMore) break
                }
                if (!current()) return@launch
                val page = requireNotNull(result)
                saved["pages"] = page.page
                if (keyword == null) {
                    _subforums.value = page.subforums
                    lockedSources.remove(source)
                    _filterLocked.value = false
                    _filterNotice.value = null
                    if (next == 1 && order == TopicSort.RECOMMENDED) {
                        if (combined.isEmpty()) {
                            noRecommendSources.add(source!!)
                            _recommendAvailable.value = false
                        } else {
                            noRecommendSources.remove(source!!)
                            _recommendAvailable.value = true
                        }
                    }
                }
                _state.value = ListState(combined, page = page.page, hasMore = page.hasMore)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { if (version == generation) _state.update { it.copy(loading = false, error = userMessage(error)) } }
        }
    }
}

data class MainPostShareState(
    val open: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val earlyReplies: List<Post> = emptyList(),
)

data class ReaderState(
    val id: String = "", val data: PostPage? = null, val loading: Boolean = false,
    val error: String? = null, val position: ReadingPosition? = null,
    val filterAuthorId: String? = null, val filterAuthorName: String? = null,
    val more: List<PostPage> = emptyList(), val loadingMore: Boolean = false, val moreError: String? = null,
    val loadingPrevious: Boolean = false, val previousError: String? = null,
    val epoch: Int = 0, val targetFloor: Int? = null,
    val loadedRevision: Long = 0, val loadedPostIds: List<String> = emptyList(),
    val mainPost: Post? = null,
) {
    val authorOnly: Boolean get() = filterAuthorId != null
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
    private var requestedReference: PostReference? = null
    private var requestedAuthorId: String? = null
    private var requestedAuthorName: String? = null
    private var latestPosition: ReadingPosition? = null
    private val _references = MutableStateFlow<Map<String, PostReferenceState>>(emptyMap())
    val references = _references.asStateFlow()
    private val referenceJobs = mutableMapOf<String, Job>()
    private var referenceGeneration = 0L
    private val _shareCard = MutableStateFlow(MainPostShareState())
    val shareCard = _shareCard.asStateFlow()
    private var mainPostRequest: Job? = null
    private var mainPostGeneration = 0L
    private fun currentReferenceOwner() = accounts.state.value.let {
        it.ready && !it.busy && it.revision == owner.revision && it.userId == owner.userId
    }
    init {
        viewModelScope.launch {
            accounts.state.collect {
                if (!currentReferenceOwner()) {
                    clearReferences()
                    closeShareCard()
                }
            }
        }
    }
    private fun clearReferences() {
        referenceGeneration++
        referenceJobs.values.forEach { it.cancel() }
        referenceJobs.clear()
        _references.value = emptyMap()
    }
    fun openShareCard() {
        val current = _state.value
        val topicId = current.id
        if (topicId.isBlank() || !currentReferenceOwner()) return
        val existing = current.mainPost ?: current.items.firstOrNull { it.floor == 0 }
        val hasPageOne = !current.authorOnly && (current.data?.page == 1 || current.items.any { it.floor == 1 })
        if (existing != null && hasPageOne) {
            if (current.mainPost == null) {
                _state.update { if (it.id == topicId && it.mainPost == null) it.copy(mainPost = existing) else it }
            }
            val early = current.items.filter { it.floor in 1..3 }.sortedBy { it.floor }
            _shareCard.value = MainPostShareState(open = true, earlyReplies = early)
            return
        }
        fetchMainPostForShare(topicId)
    }
    fun retryShareCard() {
        val current = _state.value
        val topicId = current.id
        if (topicId.isBlank() || !currentReferenceOwner()) return
        val hasPageOne = !current.authorOnly && (current.data?.page == 1 || current.items.any { it.floor == 1 })
        if (current.mainPost != null && hasPageOne) {
            val early = current.items.filter { it.floor in 1..3 }.sortedBy { it.floor }
            _shareCard.value = MainPostShareState(open = true, earlyReplies = early)
            return
        }
        fetchMainPostForShare(topicId)
    }
    fun closeShareCard() {
        mainPostGeneration++
        mainPostRequest?.cancel()
        mainPostRequest = null
        _shareCard.value = MainPostShareState()
    }
    private fun fetchMainPostForShare(topicId: String) {
        val version = ++mainPostGeneration
        mainPostRequest?.cancel()
        _shareCard.value = MainPostShareState(open = true, loading = true)
        mainPostRequest = viewModelScope.launch {
            try {
                if (sessions.snapshot().revision != owner.revision) throw CancellationException("Account changed")
                val page = client.getPosts(topicId, 1, null)
                ensureActive()
                if (version != mainPostGeneration || !currentReferenceOwner() || sessions.snapshot().revision != owner.revision) return@launch
                if (_state.value.id != topicId) return@launch
                val post = page.items.firstOrNull { it.floor == 0 } ?: throw NgaException.NotFound()
                val early = page.items.filter { it.floor in 1..3 }.sortedBy { it.floor }
                _state.update { if (it.id == topicId) it.copy(mainPost = post) else it }
                _shareCard.value = MainPostShareState(open = true, earlyReplies = early)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (version == mainPostGeneration && currentReferenceOwner() && _state.value.id == topicId) {
                    _shareCard.value = MainPostShareState(open = true, error = userMessage(error))
                }
            }
        }
    }
    fun resolveReference(reference: PostReference, retry: Boolean = false) {
        if (!currentReferenceOwner()) return
        val key = reference.key
        if (referenceJobs[key]?.isActive == true) return
        val known = _references.value[key]
        if (known != null && (!retry || known.error == null)) return
        val loaded = _state.value.items.firstOrNull { _state.value.id == reference.topicId && it.id == reference.postId }
        if (loaded != null) {
            _references.update { it + (key to PostReferenceState(post = loaded)) }
            return
        }
        _references.update { it + (key to PostReferenceState(loading = true)) }
        val version = referenceGeneration
        referenceJobs[key] = viewModelScope.launch {
            try {
                val page = client.getPostPage(reference)
                ensureActive()
                if (version != referenceGeneration || !currentReferenceOwner()) return@launch
                val post = page.items.firstOrNull { it.id == reference.postId } ?: throw NgaException.NotFound()
                _references.update { it + (key to PostReferenceState(post = post)) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (version == referenceGeneration && currentReferenceOwner()) {
                    _references.update { it + (key to PostReferenceState(error = userMessage(error))) }
                }
            }
        }
    }
    fun locateReference(reference: PostReference) {
        if (!currentReferenceOwner() || reference.topicId != _state.value.id) return
        val loaded = _state.value.items.firstOrNull { it.id == reference.postId }
        if (loaded != null && !_state.value.authorOnly) {
            locateLoadedFloor(loaded.floor)
        } else load(reference = reference, authorOnly = false)
    }
    private fun locateLoadedFloor(floor: Int) {
        moreRequest?.cancel()
        _state.update {
            it.copy(targetFloor = floor, position = null, epoch = it.epoch + 1,
                loadingMore = false, loadingPrevious = false)
        }
    }
    fun open(id: String) {
        if (_state.value.id == id) {
            if (!_state.value.authorOnly) _state.update { it.copy(position = latestPosition ?: it.position, targetFloor = null) }
            return
        }
        clearReferences()
        closeShareCard()
        requestedPage = null; requestedFloor = null; requestedReference = null; requestedAuthorId = null; requestedAuthorName = null; latestPosition = null
        moreRequest?.cancel()
        _state.value = ReaderState(id = id, loadedRevision = _state.value.loadedRevision)
        load(restore = true)
    }
    fun load(
        page: Int? = null,
        authorOnly: Boolean? = null,
        filterAuthorId: String? = null,
        filterAuthorName: String? = null,
        clearAuthorFilter: Boolean = false,
        restore: Boolean = false,
        floor: Int? = null,
        reference: PostReference? = null,
    ) {
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
                val hasExplicitAuthor = clearAuthorFilter || authorOnly != null || filterAuthorId != null
                val resolvedAuthorId = when {
                    clearAuthorFilter || authorOnly == false -> null
                    filterAuthorId != null -> filterAuthorId.takeIf { it.isNotBlank() }
                    authorOnly == true -> old.data?.authorId?.takeIf { it.isNotBlank() }
                    old.error != null -> requestedAuthorId
                    else -> old.filterAuthorId
                }
                val resolvedAuthorName = when {
                    resolvedAuthorId == null -> null
                    filterAuthorId != null -> filterAuthorName?.takeIf { it.isNotBlank() }
                    authorOnly == true -> null
                    old.error != null -> requestedAuthorName
                    else -> old.filterAuthorName
                }
                val filtered = resolvedAuthorId != null
                val targetFloor = if (page != null || old.error == null) floor else requestedFloor
                val targetReference = reference ?: requestedReference?.takeIf { old.error != null && page == null && !hasExplicitAuthor }
                requestedPage = target; requestedFloor = targetFloor; requestedReference = targetReference
                requestedAuthorId = resolvedAuthorId; requestedAuthorName = resolvedAuthorName
                val data = if (targetReference == null) client.getPosts(old.id, target, resolvedAuthorId) else client.getPostPage(targetReference)
                val resolvedFloor = targetReference?.let { ref -> data.items.firstOrNull { it.id == ref.postId }?.floor }
                ensureActive()
                if (version != generation || sessions.snapshot().revision != owner.revision) return@launch
                try {
                    accounts.withCurrentRevision(owner.revision) {
                        dao.saveHistory(ReadingHistory(old.id, data.title, System.currentTimeMillis()))
                    }
                    recordReadState(old.id, data, filtered)
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) {  }
                if (version != generation) return@launch
                clearReferences()
                val currentMainPost = _state.value.takeIf { it.id == old.id }?.mainPost
                val resolvedMainPost = data.items.firstOrNull { it.floor == 0 } ?: currentMainPost
                _state.value = ReaderState(
                    old.id, data, position = position?.takeIf { it.page == data.page && !filtered && targetFloor == null && targetReference == null },
                    filterAuthorId = resolvedAuthorId, filterAuthorName = resolvedAuthorName,
                    epoch = old.epoch + 1, targetFloor = resolvedFloor ?: targetFloor,
                    loadedRevision = old.loadedRevision + 1, loadedPostIds = data.items.map { it.id },
                    mainPost = resolvedMainPost,
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
                val author = state.filterAuthorId
                val result = client.getPosts(state.id, page, author)
                ensureActive()
                if (version != generation || sessions.snapshot().revision != owner.revision) return@launch
                if (author == null) {
                    try {
                        recordReadState(state.id, result, filtered = false)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) { }
                }
                _state.update { current ->
                    if (current.epoch != state.epoch || current.id != state.id || current.data == null) return@update current
                    val adjacent = if (prepend) current.startPage == page + 1 else current.endPage == page - 1
                    if (!adjacent) return@update current.copy(loadingMore = false, loadingPrevious = false)
                    val known = current.items.mapTo(HashSet()) { it.id }
                    val fresh = result.copy(items = result.items.filter { it.id !in known })
                    val updated = if (prepend) current.copy(data = fresh, more = listOf(current.data) + current.more, loadingPrevious = false, previousError = null)
                    else current.copy(more = current.more + fresh, loadingMore = false, moreError = null)
                    updated.copy(
                        loadedRevision = current.loadedRevision + 1,
                        loadedPostIds = fresh.items.map { it.id },
                        mainPost = result.items.firstOrNull { it.floor == 0 } ?: current.mainPost,
                    )
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (version == generation) _state.update {
                    if (it.epoch != state.epoch || it.id != state.id) it
                    else if (prepend) it.copy(loadingPrevious = false, previousError = userMessage(error))
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
            locateLoadedFloor(target)
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
        val previous = latestPosition
        if (previous != null && previous.topicId == state.id && previous.page == page && previous.index == index && previous.offset == offset) {
            return
        }
        accounts.withCurrentRevision(owner.revision) {
            val position = ReadingPosition(state.id, page, index, offset, updatedAt = System.currentTimeMillis())
            dao.savePosition(position)
            latestPosition = position
        }
    }
    private suspend fun recordReadState(topicId: String, page: PostPage, filtered: Boolean) {
        val knownReplies = if (!filtered) {
            maxOf((page.total - 1).coerceAtLeast(0), page.items.maxOfOrNull { it.floor } ?: 0)
        } else {
            null
        }
        val nowSeconds = System.currentTimeMillis() / 1000L
        accounts.withCurrentRevision(owner.revision) {
            dao.recordTopicRead(topicId, knownReplies, nowSeconds)
        }
    }
    fun toggleFavorite() = viewModelScope.launch {
        val value = _state.value
        val data = value.data ?: return@launch
        accounts.withCurrentRevision(owner.revision) { dao.toggleFavorite(LocalFavorite(value.id, data.title)) }
    }
}
