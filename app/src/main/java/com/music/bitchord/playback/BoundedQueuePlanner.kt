package com.music.bitchord.playback

import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song

/**
 * The player-facing shape of a bounded context queue.
 *
 * [canonicalContext] is deliberately the full, player-free registry list.  The
 * only CONTEXT rows represented by a materialized queue are [history],
 * [current], and the entries in [contextUpcoming].  The latter are logical
 * entries rather than a second copy of the canonical songs; their cycle id
 * keeps a repeat-all item distinct from the same song in the active cycle.
 *
 * A materialized queue assembled from this plan is always:
 * `history + current + userQueue + contextUpcoming + autoplay`.
 */
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
    val continuationState: ContinuationState = ContinuationState.EXHAUSTED,
    val repeatAll: Boolean = false,
) {
    /** Shorter name useful at call sites that do not need to stress the gate. */
    val contextExhausted: Boolean
        get() = contextTrulyExhausted

    val isContextTrulyExhausted: Boolean
        get() = contextTrulyExhausted

    val isContinuationPending: Boolean
        get() = continuationPending

    /** Alias for code that names the registry list after its songs. */
    val canonicalSongs: List<Song>
        get() = canonicalContext

    /** Compact alias for callers that call the registry simply `canonical`. */
    val canonical: List<Song>
        get() = canonicalContext

    /**
     * Materialize this plan as Songs without involving Media3 or Android.
     * Invalid logical entries are ignored defensively; builders validate the
     * canonical/window sizes, so a valid plan never takes this branch.
     */
    fun materializedSongs(): List<Song> = buildList(
        history.size + 1 + userQueue.size + contextUpcoming.size + autoplay.size,
    ) {
        addAll(history)
        add(current)
        addAll(userQueue)
        contextUpcoming.forEach { entry ->
            canonicalContext.getOrNull(entry.canonicalIndex)?.let { add(it.asContextMaterialized()) }
        }
        addAll(autoplay)
    }
}

/**
 * Pure Kotlin planning for the bounded CONTEXT portion of the player queue.
 *
 * This class only decides which logical entries should be materialized.  The
 * caller remains responsible for applying the returned bounded diff to its
 * player timeline.
 */
object BoundedQueuePlanner {

    /**
     * Build the initial bounded queue around [selectedIndex].
     *
     * The supplied [queueWindow] must already be started at the selected item.
     * Only the last [MAX_QUEUE_HISTORY] logical items before the cursor and at
     * most [WINDOW_AHEAD] logical items after it are materialized.
     */
    fun buildInitialPlan(
        canonicalContext: List<Song>,
        selectedIndex: Int,
        queueWindow: QueueWindow,
        preservedUserQueue: List<Song> = emptyList(),
        existingAutoplay: List<Song> = emptyList(),
        repeatAll: Boolean = false,
        continuationState: ContinuationState = ContinuationState.EXHAUSTED,
        contextSnapshot: ContextSnapshot? = null,
    ): BoundedContextPlan {
        require(canonicalContext.isNotEmpty()) { "canonicalContext must not be empty" }
        require(selectedIndex in canonicalContext.indices) {
            "selectedIndex $selectedIndex is outside canonicalContext"
        }
        require(queueWindow.canonicalSize == canonicalContext.size) {
            "QueueWindow and canonicalContext must have the same size"
        }
        require(queueWindow.currentCanonicalIndex == selectedIndex) {
            "QueueWindow must be started at selectedIndex"
        }

        val state = contextSnapshot?.continuationState ?: continuationState
        val logicalUpcoming = queueWindow.upcoming(WINDOW_AHEAD, repeatAll)
        return BoundedContextPlan(
            canonicalContext = canonicalContext.toList(),
            currentCanonicalIndex = selectedIndex,
            history = logicalHistory(canonicalContext, queueWindow),
            current = canonicalContext[selectedIndex].asContextEntry(),
            userQueue = preservedUserQueue.map { it.asUserQueueEntry() },
            contextUpcoming = logicalUpcoming,
            autoplay = existingAutoplay.map { it.asAutoplayEntry() },
            contextTrulyExhausted = logicalUpcoming.isEmpty() && state.isTerminal(),
            continuationPending = state.isPending(),
            continuationState = state,
            repeatAll = repeatAll,
        )
    }

    /** Build an initial plan directly from a registry snapshot. */
    fun buildInitialPlan(
        contextSnapshot: ContextSnapshot,
        selectedIndex: Int,
        queueWindow: QueueWindow,
        preservedUserQueue: List<Song> = emptyList(),
        existingAutoplay: List<Song> = emptyList(),
        repeatAll: Boolean = false,
    ): BoundedContextPlan = buildInitialPlan(
        canonicalContext = contextSnapshot.songs,
        selectedIndex = selectedIndex,
        queueWindow = queueWindow,
        preservedUserQueue = preservedUserQueue,
        existingAutoplay = existingAutoplay,
        repeatAll = repeatAll,
        contextSnapshot = contextSnapshot,
    )

    /** Positional overload for callers that provide the registry snapshot last. */
    fun buildInitialPlan(
        canonicalContext: List<Song>,
        selectedIndex: Int,
        queueWindow: QueueWindow,
        preservedUserQueue: List<Song> = emptyList(),
        existingAutoplay: List<Song> = emptyList(),
        contextSnapshot: ContextSnapshot,
        repeatAll: Boolean = false,
    ): BoundedContextPlan = buildInitialPlan(
        canonicalContext = canonicalContext,
        selectedIndex = selectedIndex,
        queueWindow = queueWindow,
        preservedUserQueue = preservedUserQueue,
        existingAutoplay = existingAutoplay,
        repeatAll = repeatAll,
        contextSnapshot = contextSnapshot,
    )

    /**
     * Reconcile the bounded upcoming section with the current logical window.
     *
     * [currentMaterializedContextIds] may be either a List or Set.  IDs are
     * matched against both video ids and queue-entry ids so the planner remains
     * useful during migration between the two queue identity forms.  When fewer
     * than [LOW_WATER] eligible logical CONTEXT rows are materialized, entries
     * are selected up to [WINDOW_AHEAD].  New entries are returned in logical
     * order; a caller can insert them immediately before AUTOPLAY without
     * disturbing USER_QUEUE or the AUTOPLAY tail.
     *
     * The operation is idempotent for the same logical window and materialized
     * IDs.  It never asks [QueueWindow] for more than [WINDOW_AHEAD] entries.
     */
    fun topUpPlan(
        plan: BoundedContextPlan,
        queueWindow: QueueWindow,
        currentMaterializedContextIds: Collection<String> = plan.contextUpcoming.mapNotNull {
            plan.canonicalContext.getOrNull(it.canonicalIndex)?.videoId
        },
        repeatAll: Boolean = plan.repeatAll,
        continuationState: ContinuationState = plan.continuationState,
        canonicalContext: List<Song> = plan.canonicalContext,
        contextSnapshot: ContextSnapshot? = null,
    ): BoundedContextPlan {
        require(canonicalContext.isNotEmpty()) { "canonicalContext must not be empty" }
        require(queueWindow.canonicalSize == canonicalContext.size) {
            "QueueWindow and canonicalContext must have the same size"
        }

        val state = contextSnapshot?.continuationState ?: continuationState
        val currentIndex = queueWindow.currentCanonicalIndex ?: plan.currentCanonicalIndex
        require(currentIndex in canonicalContext.indices) {
            "current canonical index is outside canonicalContext"
        }

        val logicalUpcoming = queueWindow.upcoming(WINDOW_AHEAD, repeatAll)
        val materializedIds = currentMaterializedContextIds.toHashSet()
        val eligibleMaterialized = logicalUpcoming.filter { entry ->
            canonicalContext.songAt(entry)?.hasAnyIdentityIn(materializedIds) == true
        }

        // At low water we keep the logical entries which are still physically
        // present, then append only the missing suffix.  At or above low water
        // the retained logical list is the whole operation, so there is no
        // churn and repeated calls return the same plan.
        val nextUpcoming = if (eligibleMaterialized.size < LOW_WATER) {
            val room = (WINDOW_AHEAD - eligibleMaterialized.size).coerceAtLeast(0)
            val alreadyRepresented = eligibleMaterialized
                .mapNotNull { canonicalContext.songAt(it) }
                .flatMapTo(HashSet()) { it.identities() }
            val additions = logicalUpcoming.asSequence()
                .filter { entry ->
                    val song = canonicalContext.songAt(entry) ?: return@filter false
                    song.identities().none { it in materializedIds || it in alreadyRepresented }
                }
                .take(room)
                .toList()
            eligibleMaterialized + additions
        } else {
            eligibleMaterialized
        }

        val nextCanonical = canonicalContext.toList()
        return plan.copy(
            canonicalContext = nextCanonical,
            currentCanonicalIndex = currentIndex,
            history = logicalHistory(nextCanonical, queueWindow),
            current = nextCanonical[currentIndex].asContextEntry(),
            userQueue = plan.userQueue.map { it.asUserQueueEntry() },
            contextUpcoming = nextUpcoming,
            autoplay = plan.autoplay.map { it.asAutoplayEntry() },
            contextTrulyExhausted = logicalUpcoming.isEmpty() && state.isTerminal(),
            continuationPending = state.isPending(),
            continuationState = state,
            repeatAll = repeatAll,
        )
    }

    /** Top-up overload that uses the registry snapshot as canonical source/state. */
    fun topUpPlan(
        plan: BoundedContextPlan,
        queueWindow: QueueWindow,
        contextSnapshot: ContextSnapshot,
        currentMaterializedContextIds: Collection<String> = plan.contextUpcoming.mapNotNull {
            plan.canonicalContext.getOrNull(it.canonicalIndex)?.videoId
        },
        repeatAll: Boolean = plan.repeatAll,
    ): BoundedContextPlan = topUpPlan(
        plan = plan,
        queueWindow = queueWindow,
        currentMaterializedContextIds = currentMaterializedContextIds,
        repeatAll = repeatAll,
        contextSnapshot = contextSnapshot,
        canonicalContext = contextSnapshot.songs,
    )

    /** Positional overload for a snapshot supplied after the materialized IDs. */
    fun topUpPlan(
        plan: BoundedContextPlan,
        queueWindow: QueueWindow,
        currentMaterializedContextIds: Collection<String>,
        contextSnapshot: ContextSnapshot,
        repeatAll: Boolean = plan.repeatAll,
    ): BoundedContextPlan = topUpPlan(
        plan = plan,
        queueWindow = queueWindow,
        currentMaterializedContextIds = currentMaterializedContextIds,
        repeatAll = repeatAll,
        contextSnapshot = contextSnapshot,
        canonicalContext = contextSnapshot.songs,
    )

    /** Number of history entries that must be trimmed from a logical prefix. */
    fun historyTrimCount(historySize: Int): Int =
        (historySize - MAX_QUEUE_HISTORY).coerceAtLeast(0)

    /**
     * AUTOPLAY is allowed only after the logical context has no next entry,
     * the registry is terminal, and Repeat All is disabled.  Physical queue
     * emptiness is intentionally not an input to this decision.
     */
    fun autoplayEligible(
        queueWindow: QueueWindow,
        continuationState: ContinuationState,
        repeatAll: Boolean = false,
    ): Boolean = !repeatAll &&
        queueWindow.upcoming(1, repeatAll).isEmpty() &&
        continuationState.isTerminal()

    /** Evaluate the gate when the caller already has the bounded logical slice. */
    fun autoplayEligible(
        logicalUpcoming: List<QueueWindowEntry>,
        continuationState: ContinuationState,
        repeatAll: Boolean = false,
    ): Boolean = !repeatAll && logicalUpcoming.isEmpty() && continuationState.isTerminal()

    /** Evaluate the same gate from a previously built plan. */
    fun autoplayEligible(
        plan: BoundedContextPlan,
        repeatAll: Boolean = plan.repeatAll,
    ): Boolean = !repeatAll &&
        plan.contextTrulyExhausted &&
        !plan.continuationPending &&
        plan.continuationState.isTerminal()

    /** Alias for callers that prefer predicate naming. */
    fun isAutoplayEligible(
        queueWindow: QueueWindow,
        continuationState: ContinuationState,
        repeatAll: Boolean = false,
    ): Boolean = autoplayEligible(queueWindow, continuationState, repeatAll)

    private fun logicalHistory(
        canonicalContext: List<Song>,
        queueWindow: QueueWindow,
    ): List<Song> {
        val current = queueWindow.currentCanonicalIndex ?: return emptyList()
        val indices: List<Int> = if (!queueWindow.isShuffleEnabled) {
            val first = (current - MAX_QUEUE_HISTORY).coerceAtLeast(0)
            (first until current).toList()
        } else {
            val cursor = queueWindow.cursor
            if (cursor <= 0) {
                emptyList()
            } else {
                val order = queueWindow.order
                val first = (cursor - MAX_QUEUE_HISTORY).coerceAtLeast(0)
                order.subList(first, cursor)
            }
        }
        return indices.mapNotNull { canonicalContext.getOrNull(it)?.asContextEntry() }
    }

    private fun Song.asContextEntry(): Song =
        if (queueTier == QueueTier.CONTEXT) this else copy(queueTier = QueueTier.CONTEXT)

    private fun Song.asUserQueueEntry(): Song =
        if (queueTier == QueueTier.USER_QUEUE) this else copy(queueTier = QueueTier.USER_QUEUE)

    private fun Song.asAutoplayEntry(): Song =
        if (queueTier == QueueTier.AUTOPLAY) this else copy(queueTier = QueueTier.AUTOPLAY)

    private fun List<Song>.songAt(entry: QueueWindowEntry): Song? =
        getOrNull(entry.canonicalIndex)

    private fun Song.identities(): Set<String> = buildSet {
        add(videoId)
        queueEntryId?.let(::add)
    }

    private fun Song.hasAnyIdentityIn(ids: Set<String>): Boolean =
        identities().any { it in ids }

    private fun ContinuationState.isPending(): Boolean =
        this == ContinuationState.AVAILABLE || this == ContinuationState.IN_FLIGHT

    private fun ContinuationState.isTerminal(): Boolean =
        this == ContinuationState.EXHAUSTED || this == ContinuationState.BLOCKED

}

private fun Song.asContextMaterialized(): Song =
    if (queueTier == QueueTier.CONTEXT) this else copy(queueTier = QueueTier.CONTEXT)

fun buildInitialPlan(
    canonicalContext: List<Song>,
    selectedIndex: Int,
    queueWindow: QueueWindow,
    preservedUserQueue: List<Song> = emptyList(),
    existingAutoplay: List<Song> = emptyList(),
    repeatAll: Boolean = false,
    continuationState: ContinuationState = ContinuationState.EXHAUSTED,
    contextSnapshot: ContextSnapshot? = null,
): BoundedContextPlan = BoundedQueuePlanner.buildInitialPlan(
    canonicalContext,
    selectedIndex,
    queueWindow,
    preservedUserQueue,
    existingAutoplay,
    repeatAll,
    continuationState,
    contextSnapshot,
)

fun buildInitialPlan(
    contextSnapshot: ContextSnapshot,
    selectedIndex: Int,
    queueWindow: QueueWindow,
    preservedUserQueue: List<Song> = emptyList(),
    existingAutoplay: List<Song> = emptyList(),
    repeatAll: Boolean = false,
): BoundedContextPlan = BoundedQueuePlanner.buildInitialPlan(
    contextSnapshot,
    selectedIndex,
    queueWindow,
    preservedUserQueue,
    existingAutoplay,
    repeatAll,
)

fun topUpPlan(
    plan: BoundedContextPlan,
    queueWindow: QueueWindow,
    currentMaterializedContextIds: Collection<String> = plan.contextUpcoming.mapNotNull {
        plan.canonicalContext.getOrNull(it.canonicalIndex)?.videoId
    },
    repeatAll: Boolean = plan.repeatAll,
    continuationState: ContinuationState = plan.continuationState,
    canonicalContext: List<Song> = plan.canonicalContext,
    contextSnapshot: ContextSnapshot? = null,
): BoundedContextPlan = BoundedQueuePlanner.topUpPlan(
    plan,
    queueWindow,
    currentMaterializedContextIds,
    repeatAll,
    continuationState,
    canonicalContext,
    contextSnapshot,
)

fun topUpPlan(
    plan: BoundedContextPlan,
    queueWindow: QueueWindow,
    currentMaterializedContextIds: Collection<String>,
    contextSnapshot: ContextSnapshot,
    repeatAll: Boolean = plan.repeatAll,
): BoundedContextPlan = BoundedQueuePlanner.topUpPlan(
    plan,
    queueWindow,
    contextSnapshot,
    currentMaterializedContextIds,
    repeatAll,
)

/** Top-level form for callers that do not need to name the planner object. */
fun buildInitialBoundedContextPlan(
    canonicalContext: List<Song>,
    selectedIndex: Int,
    queueWindow: QueueWindow,
    preservedUserQueue: List<Song> = emptyList(),
    existingAutoplay: List<Song> = emptyList(),
    repeatAll: Boolean = false,
    continuationState: ContinuationState = ContinuationState.EXHAUSTED,
    contextSnapshot: ContextSnapshot? = null,
): BoundedContextPlan = BoundedQueuePlanner.buildInitialPlan(
    canonicalContext,
    selectedIndex,
    queueWindow,
    preservedUserQueue,
    existingAutoplay,
    repeatAll,
    continuationState,
    contextSnapshot,
)

fun topUpBoundedContextPlan(
    plan: BoundedContextPlan,
    queueWindow: QueueWindow,
    currentMaterializedContextIds: Collection<String> = plan.contextUpcoming.mapNotNull {
        plan.canonicalContext.getOrNull(it.canonicalIndex)?.videoId
    },
    repeatAll: Boolean = plan.repeatAll,
    continuationState: ContinuationState = plan.continuationState,
    canonicalContext: List<Song> = plan.canonicalContext,
    contextSnapshot: ContextSnapshot? = null,
): BoundedContextPlan = BoundedQueuePlanner.topUpPlan(
    plan,
    queueWindow,
    currentMaterializedContextIds,
    repeatAll,
    continuationState,
    canonicalContext,
    contextSnapshot,
)

fun historyTrimCount(historySize: Int): Int =
    BoundedQueuePlanner.historyTrimCount(historySize)

fun autoplayEligible(
    queueWindow: QueueWindow,
    continuationState: ContinuationState,
    repeatAll: Boolean = false,
): Boolean = BoundedQueuePlanner.autoplayEligible(queueWindow, continuationState, repeatAll)

fun autoplayEligible(
    logicalUpcoming: List<QueueWindowEntry>,
    continuationState: ContinuationState,
    repeatAll: Boolean = false,
): Boolean = BoundedQueuePlanner.autoplayEligible(logicalUpcoming, continuationState, repeatAll)
