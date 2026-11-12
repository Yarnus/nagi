package dev.nagi.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.nagi.app.data.AccountManager
import dev.nagi.sdk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PostRatingState(
    val phase: ComposePhase,
    val rating: PostRating? = null,
    val scoreDelta: Int = 0,
    val pendingRating: PostRating? = null,
) {
    val enabled get() = phase == ComposePhase.Rejected || phase == ComposePhase.Confirmed
}

@HiltViewModel
class PostRatingViewModel @Inject constructor(
    private val client: NgaClient, private val accounts: AccountManager, private val saved: SavedStateHandle,
) : ViewModel() {
    private val owner = accounts.state.value
    private val prefix = "${owner.userId}:${owner.revision}:"
    private fun pending() = saved.get<ArrayList<String>>("ratingPending") ?: arrayListOf()
    private val _states = MutableStateFlow(pending().filter { it.startsWith(prefix) }.associate {
        it.removePrefix(prefix) to PostRatingState(ComposePhase.Unconfirmed)
    })
    val states = _states.asStateFlow()
    private val _feedback = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val feedback = _feedback.asSharedFlow()
    private val baselines = mutableMapOf<String, Long>()
    private val loadedRevisions = mutableMapOf<String, Long>()
    private fun current() = accounts.state.value.let { it.ready && !it.busy && it.userId == owner.userId && it.revision == owner.revision }
    init { viewModelScope.launch { accounts.state.collect { if (!current()) { _states.value = emptyMap(); baselines.clear(); loadedRevisions.clear() } } } }

    fun onPostsReloaded(topicId: String, revision: Long, postIds: List<String>) {
        if (!current() || revision <= (loadedRevisions[topicId] ?: 0)) return
        loadedRevisions[topicId] = revision
        val refreshed = postIds.mapTo(HashSet()) { "$topicId:$it" }
        _states.value = _states.value.mapValues { (key, state) ->
            if (key !in refreshed) state else {
                baselines[key] = (baselines[key] ?: 0) + 1
                if (state.phase == ComposePhase.Submitting) state.copy(scoreDelta = 0)
                else state.copy(phase = ComposePhase.Confirmed, scoreDelta = 0, pendingRating = null,
                    rating = state.rating.takeUnless { state.phase == ComposePhase.Unconfirmed })
            }
        }
        saved["ratingPending"] = ArrayList(pending().filterNot {
            it.startsWith(prefix) && it.removePrefix(prefix) in refreshed &&
                _states.value[it.removePrefix(prefix)]?.phase != ComposePhase.Submitting
        })
    }

    fun rate(topicId: String, postId: String, rating: PostRating) {
        if (!current() || topicId.toLongOrNull()?.let { it > 0 } != true || postId.toLongOrNull()?.let { it >= 0 } != true) return
        val key = "$topicId:$postId"
        val previous = _states.value[key] ?: PostRatingState(ComposePhase.Confirmed)
        if (!previous.enabled) return
        if (owner.userId == null) {
            _feedback.tryEmit("请先登录后再评价")
            return
        }
        val baseline = baselines[key] ?: 0
        _states.value += key to previous.copy(phase = ComposePhase.Submitting, pendingRating = rating)
        saved["ratingPending"] = ArrayList((pending() + (prefix + key)).distinct())
        viewModelScope.launch {
            val result = try { accounts.withCurrentRevision(owner.revision) { client.ratePost(topicId, postId, rating) } }
            catch (error: CancellationException) {
                if (current()) _states.value += key to _states.value.getValue(key).copy(phase = ComposePhase.Unconfirmed, pendingRating = null)
                throw error
            } catch (_: Exception) { WriteResult.Unconfirmed() }
            if (!current()) return@launch
            val retained = _states.value.getValue(key).copy(pendingRating = null)
            if (result !is WriteResult.Unconfirmed) saved["ratingPending"] = ArrayList(pending().filterNot { it == prefix + key })
            _states.value += key to when (result) {
                is WriteResult.Confirmed -> {
                    val receipt = result.ratingReceipt
                    retained.copy(phase = ComposePhase.Confirmed,
                        rating = if (receipt != null) receipt.currentRating else if (previous.rating == rating) null else rating,
                        scoreDelta = retained.scoreDelta + if ((baselines[key] ?: 0) == baseline) receipt?.delta ?: 0 else 0)
                }
                is WriteResult.Rejected -> {
                    _feedback.tryEmit("评价失败：${userMessage(result.reason)}")
                    retained.copy(phase = ComposePhase.Rejected)
                }
                is WriteResult.Unconfirmed -> {
                    _feedback.tryEmit("评价结果未确认，请刷新核对后再操作")
                    retained.copy(phase = ComposePhase.Unconfirmed)
                }
            }
        }
    }
}
