package dev.nagi.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Tracks in-flight image loads for the active share-card surface so export can wait briefly for
 * images to settle (success or final error) without blocking on discarded probe slots.
 */
internal class ImageLoadTracker {
    private val slotPending = mutableMapOf<String, MutableSet<Any>>()
    private var activeSlot: String? = null
    var hasPending by mutableStateOf(false)
        private set
    var settledRevision by mutableIntStateOf(0)
        private set

    fun setActiveSlot(slotId: String?) {
        if (activeSlot == slotId) return
        activeSlot = slotId
        refresh()
    }

    fun markPending(slotId: String, token: Any) {
        val set = slotPending.getOrPut(slotId) { mutableSetOf() }
        if (set.add(token) && slotId == activeSlot) {
            refresh()
        }
    }

    fun markSettled(slotId: String, token: Any) {
        val set = slotPending[slotId] ?: return
        if (set.remove(token)) {
            if (set.isEmpty()) slotPending.remove(slotId)
            if (slotId == activeSlot) {
                settledRevision++
                refresh()
            }
        }
    }

    /**
     * Suspends until all images in the active slot settle or [timeoutMillis] elapses.
     * Returns `true` if any pending image had to be awaited.
     */
    suspend fun awaitSettled(timeoutMillis: Long): Boolean {
        if (!hasPending) return false
        withTimeoutOrNull(timeoutMillis) {
            snapshotFlow { hasPending }.first { !it }
        }
        return true
    }

    private fun refresh() {
        val slot = activeSlot
        hasPending = slot != null && !slotPending[slot].isNullOrEmpty()
    }
}

internal data class SlotImageLoadTracker(
    val tracker: ImageLoadTracker,
    val slotId: String,
)

internal val LocalSlotImageLoadTracker = staticCompositionLocalOf<SlotImageLoadTracker?> { null }

@Composable
internal fun TrackSlotImageReadiness(
    token: Any,
    settled: Boolean,
) {
    val slotTracker = LocalSlotImageLoadTracker.current ?: return
    DisposableEffect(slotTracker, token, settled) {
        if (settled) {
            slotTracker.tracker.markSettled(slotTracker.slotId, token)
        } else {
            slotTracker.tracker.markPending(slotTracker.slotId, token)
        }
        onDispose {
            slotTracker.tracker.markSettled(slotTracker.slotId, token)
        }
    }
}
