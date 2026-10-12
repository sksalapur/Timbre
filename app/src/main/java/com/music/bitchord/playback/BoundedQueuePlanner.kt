package com.music.bitchord.playback

import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song

data class BoundedContextPlan(
    val canonicalContext: List<Song>,
    val currentCanonicalIndex: Int,
    val history: List<Song>,
    val current: Song,
    val userQueue: List<Song>,
    val contextUpcoming: List<QueueWindowEntry>,
    val autoplay: List<Song>,
    val contextTrulyExhausted: Boolean,
    val continuationPending: Boolean,
    val continuationState: ContinuationState,
    val repeatAll: Boolean,
) {
    fun materializedSongs(): List<Song> = buildList(
        history.size + 1 + userQueue.size + contextUpcoming.size + autoplay.size,
    ) {
        addAll(history)
        add(current)
        addAll(userQueue)
        contextUpcoming.forEach { entry ->
            canonicalContext.getOrNull(entry.canonicalIndex)?.let { song ->
                add(if (song.queueTier == QueueTier.CONTEXT) song else song.copy(queueTier = QueueTier.CONTEXT))
            }
        }
        addAll(autoplay)
    }
}

object BoundedQueuePlanner {
    fun buildInitialPlan(
        contextSnapshot: ContextSnapshot,
        selectedIndex: Int,
        queueWindow: QueueWindow,
        preservedUserQueue: List<Song> = emptyList(),
        existingAutoplay: List<Song> = emptyList(),
        repeatAll: Boolean = false,
    ): BoundedContextPlan {
        val canonical = contextSnapshot.songs
        require(canonical.isNotEmpty()) { "contextSnapshot must contain songs" }
        require(selectedIndex in canonical.indices) {
            "selectedIndex $selectedIndex is outside canonicalContext"
        }
        require(queueWindow.canonicalSize == canonical.size) {
            "QueueWindow and contextSnapshot must have the same size"
        }
        require(queueWindow.currentCanonicalIndex == selectedIndex) {
            "QueueWindow must be started at selectedIndex"
        }

        val state = contextSnapshot.continuationState
        val upcoming = queueWindow.upcoming(
            WINDOW_AHEAD,
            repeatAll = repeatAll && canRepeat(state),
        )
        val history = historyBefore(canonical, queueWindow)
        return BoundedContextPlan(
            canonicalContext = canonical.toList(),
            currentCanonicalIndex = selectedIndex,
            history = history,
            current = canonical[selectedIndex].asContext(),
            userQueue = preservedUserQueue.map { it.asUserQueue() },
            contextUpcoming = upcoming,
            autoplay = existingAutoplay.map { it.asAutoplay() },
            contextTrulyExhausted = upcoming.isEmpty() && state.isTerminal(),
            continuationPending = state.isPending(),
            continuationState = state,
            repeatAll = repeatAll,
        )
    }

    /**
     * Return only the suffix needed to refill [materialized] to the logical
     * window. A physical queue is eligible for mutation only below low water;
     * a stale or reordered prefix is left for the caller's explicit refresh.
     */
    fun topUpEntries(
        window: QueueWindow,
        materialized: List<QueueWindowEntry>,
        repeatAll: Boolean,
    ): List<QueueWindowEntry> {
        if (materialized.size >= LOW_WATER) return emptyList()
        val desired = window.upcoming(WINDOW_AHEAD, repeatAll)
        if (materialized.size > desired.size) return emptyList()
        if (materialized.indices.any { !sameEntry(materialized[it], desired[it]) }) {
            return emptyList()
        }
        return desired.drop(materialized.size)
    }

    fun autoplayEligible(
        window: QueueWindow,
        continuationState: ContinuationState,
        repeatAll: Boolean,
    ): Boolean = !repeatAll &&
        window.upcoming(1, repeatAll = false).isEmpty() &&
        continuationState.isTerminal()

    /** Repeat-all may cross a cycle boundary only after continuation is terminal. */
    fun canRepeat(continuationState: ContinuationState): Boolean = continuationState.isTerminal()

    private fun historyBefore(canonical: List<Song>, window: QueueWindow): List<Song> {
        val cursor = window.cursor
        val first = (cursor - MAX_QUEUE_HISTORY).coerceAtLeast(0)
        return window.order.subList(first, cursor).map { canonical[it].asContext() }
    }

    private fun sameEntry(left: QueueWindowEntry, right: QueueWindowEntry): Boolean =
        left.canonicalIndex == right.canonicalIndex && left.cycleId == right.cycleId

    private fun Song.asContext(): Song =
        if (queueTier == QueueTier.CONTEXT) this else copy(queueTier = QueueTier.CONTEXT)

    private fun Song.asUserQueue(): Song =
        if (queueTier == QueueTier.USER_QUEUE) this else copy(queueTier = QueueTier.USER_QUEUE)

    private fun Song.asAutoplay(): Song =
        if (queueTier == QueueTier.AUTOPLAY) this else copy(queueTier = QueueTier.AUTOPLAY)

    private fun ContinuationState.isPending(): Boolean =
        this == ContinuationState.AVAILABLE || this == ContinuationState.IN_FLIGHT

    private fun ContinuationState.isTerminal(): Boolean =
        this == ContinuationState.EXHAUSTED || this == ContinuationState.BLOCKED
}
