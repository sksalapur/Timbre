package com.music.bitchord.playback

import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Shuffle as an edit to the queue, not a playback mode.
 *
 * ExoPlayer's own `shuffleModeEnabled` leaves the queue exactly as it is and
 * draws the next track from a hidden random order, so the queue panel shows
 * one running order while the player follows another — the user sees the album
 * listed in order and hears it jumping about. Toggling shuffle here rearranges
 * the queue itself and leaves playback strictly sequential: what the queue
 * shows is what plays, in that order.
 *
 * The order the queue was in beforehand is kept so the toggle can be undone.
 * The player's own shuffle mode is deliberately never enabled — it would
 * randomise on top of the order set here.
 *
 * User-queued tracks (USER_QUEUE) are NEVER shuffled: they reflect deliberate user
 * intent and remain pinned at the front. Context tracks (CONTEXT) and AutoPlay tracks
 * (AUTOPLAY) are shuffled among themselves.
 *
 * The rearranging itself is worked out as a permutation and applied to the
 * queue in one edit — see [applyOrder], which is where the size of the queue
 * stops mattering.
 */
object QueueShuffle {

    private val _enabled = MutableStateFlow(false)

    /** Whether the queue is currently held in shuffled order. */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()
    fun setEnabled(enabled: Boolean) {
        _enabled.value = enabled
    }
    /** Entry IDs in their pre-shuffle order. Empty while shuffle is off. */
    private var original: List<String> = emptyList()

    /** Canonical context MediaItems for the active album/playlist. */
    internal var canonicalContext: List<MediaItem> = emptyList()

    /** Provenance mapping: queueEntryId -> canonicalIndex. */
    private val entryProvenance = HashMap<String, Int>()

    /** Provenance mapping: mediaId -> first canonicalIndex. */
    private val mediaIdProvenance = HashMap<String, Int>()

    internal val inFlight = java.util.concurrent.atomic.AtomicBoolean(false)

    fun setCanonicalContext(items: List<MediaItem>) {
        entryProvenance.clear()
        mediaIdProvenance.clear()
        canonicalContext = items.mapIndexed { idx, item ->
            val entryId = item.queueEntryId ?: UUID.randomUUID().toString()
            val stamped = withQueueMetadata(item, newEntryId = entryId, newCanonicalIndex = idx)
            entryProvenance[entryId] = idx
            mediaIdProvenance.putIfAbsent(item.mediaId, idx)
            stamped
        }
    }

    fun getCanonicalIndex(item: MediaItem): Int? {
        val extraIndex = item.canonicalIndex
        if (extraIndex != null && extraIndex >= 0) return extraIndex
        val entryId = item.queueEntryId
        if (entryId != null) {
            val fromProv = entryProvenance[entryId]
            if (fromProv != null) return fromProv
        }
        return mediaIdProvenance[item.mediaId]
    }

    fun withQueueMetadata(
        item: MediaItem,
        newEntryId: String? = item.queueEntryId,
        newCanonicalIndex: Int? = item.canonicalIndex,
        newTier: QueueTier = item.queueTier,
    ): MediaItem {
        val currentExtras = item.mediaMetadata.extras ?: Bundle()
        val newExtras = Bundle(currentExtras).apply {
            if (newEntryId != null) putString(EXTRA_QUEUE_ENTRY_ID, newEntryId)
            if (newCanonicalIndex != null) putInt(EXTRA_CANONICAL_INDEX, newCanonicalIndex)
            putString(EXTRA_QUEUE_TIER, newTier.name)
        }
        val newMetadata = item.mediaMetadata.buildUpon().setExtras(newExtras).build()
        val newItem = item.buildUpon().setMediaMetadata(newMetadata).build()
        if (newEntryId != null) mediaItemEntryIds[newItem] = newEntryId
        if (newCanonicalIndex != null) mediaItemCanonicalIndices[newItem] = newCanonicalIndex
        mediaItemTiers[newItem] = newTier
        return newItem
    }

    fun toggle(player: Player, isRetry: Boolean = false) {
        if (!isRetry && !inFlight.compareAndSet(false, true)) return
        try {
            if (_enabled.value) restore(player, isRetry = isRetry) else shuffle(player, isRetry = isRetry)
        } catch (t: Throwable) {
            inFlight.set(false)
            throw t
        }
    }

    /**
     * Turns shuffle on without touching the current queue — for the Shuffle
     * button on an album or playlist page, where the queue it applies to is the
     * one about to replace this one. [playSongs] builds that one shuffled.
     */
    fun enableForNextQueue() {
        original = emptyList()
        _enabled.value = true
        AppSettings.setShuffleEnabled(true)
    }

    /**
     * The order a queue should go in when it is started while shuffle is on:
     * the track the user picked leads, the rest follow at random. The order it
     * arrived in is remembered, so turning shuffle off restores it.
     */
    fun startingOrder(songs: List<Song>, startIndex: Int): List<Song> {
        original = songs.map { it.queueEntryId ?: it.videoId }
        val contextSongs = songs.filter { it.queueTier == QueueTier.CONTEXT }
        if (contextSongs.isNotEmpty()) {
            setCanonicalContext(contextSongs.mapIndexed { idx, s ->
                val entryId = s.queueEntryId ?: UUID.randomUUID().toString()
                withQueueMetadata(s.toMediaItem(), newEntryId = entryId, newCanonicalIndex = idx)
            })
        }
        val rest = songs.filterIndexed { i, _ -> i != startIndex }
        val userQueue = rest.filter { it.queueTier == QueueTier.USER_QUEUE }
        val context = rest.filter { it.queueTier == QueueTier.CONTEXT }
        val shuffledContext = if (context.size > 1) {
            val s = context.shuffled()
            if (s == context) s.drop(1) + s.first() else s
        } else context
        val autoplay = rest.filter { it.queueTier == QueueTier.AUTOPLAY }.shuffled()
        return listOf(songs[startIndex]) + userQueue + shuffledContext + autoplay
    }

    /**
     * Rearranges unconsumed upcoming tracks after the playing track into a shuffled session.
     *
     * Invariants:
     * - The currently playing occurrence and preceding history occurrences remain untouched.
     * - Only unconsumed upcoming context tracks are shuffled among themselves.
     * - History tracks are NEVER reintroduced into upcoming, preventing phantom duplicates in the context queue.
     * - USER_QUEUE tracks are never shuffled and stay pinned at the front.
     * - AUTOPLAY tracks sit at the tail.
     */
    private fun shuffle(player: Player, isRetry: Boolean = false) {
        val items = player.queueItems()
        val currentIndex = player.currentMediaItemIndex
        val from = currentIndex + 1
        if (currentIndex !in items.indices || from >= items.size) {
            _enabled.value = true
            AppSettings.setShuffleEnabled(true)
            inFlight.set(false)
            return
        }

        // If canonicalContext is empty, discover it from the player's context tracks
        if (canonicalContext.isEmpty()) {
            val contextItems = items.filter { it.queueTier == QueueTier.CONTEXT }
            if (contextItems.isNotEmpty()) {
                setCanonicalContext(contextItems)
            }
        }

        val upcoming = items.drop(from)
        val userQueueIndices = upcoming.indices.filter { upcoming[it].queueTier == QueueTier.USER_QUEUE }
        val contextIndices = upcoming.indices.filter { upcoming[it].queueTier == QueueTier.CONTEXT }
        val autoplayIndices = upcoming.indices.filter { upcoming[it].queueTier == QueueTier.AUTOPLAY }

        val shuffledContext = if (contextIndices.size > 1) {
            avoidIdentityShuffle(contextIndices, contextIndices.shuffled())
        } else {
            contextIndices
        }

        val order = userQueueIndices + shuffledContext + autoplayIndices
        val currentItem = items[currentIndex]
        val currentEntryId = currentItem.queueEntryId ?: currentItem.mediaId

        var h = 1
        for (item in upcoming) {
            val id = item.queueEntryId ?: item.mediaId
            h = 31 * h + id.hashCode()
        }

        applyOrder(player, from, order, currentEntryId, h, targetShuffleState = true, isRetry = isRetry)
    }

    /**
     * Randomises one queue section but never returns its unchanged order when
     * at least two tracks can move.
     */
    internal fun avoidIdentityShuffleMediaItems(
        original: List<MediaItem>,
        shuffled: List<MediaItem>,
    ): List<MediaItem> {
        if (original.size <= 1 || shuffled != original) return shuffled
        return shuffled.drop(1) + shuffled.first()
    }

    private fun shuffledSection(indices: List<Int>): List<Int> =
        avoidIdentityShuffle(indices, indices.shuffled())

    internal fun avoidIdentityShuffle(original: List<Int>, shuffled: List<Int>): List<Int> {
        if (original.size <= 1 || shuffled != original) return shuffled
        return shuffled.drop(1) + shuffled.first()
    }

    /**
     * Restores the queue to the full canonical collection order around the active track.
     * Preceding canonical tracks are placed in history before current track.
     * User-queued tracks remain pinned immediately behind current track.
     * Following canonical tracks fill the upcoming context queue.
     */
    private fun restore(player: Player, isRetry: Boolean = false) {
        val items = player.queueItems()
        val currentIndex = player.currentMediaItemIndex
        val from = currentIndex + 1
        if (currentIndex !in items.indices || from >= items.size) {
            _enabled.value = false
            AppSettings.setShuffleEnabled(false)
            inFlight.set(false)
            return
        }

        val currentItem = items[currentIndex]
        val isCurrentContext = currentItem.queueTier == QueueTier.CONTEXT
        var currentCanonicalIndex: Int? = null

        // If currently playing track is CONTEXT, use its canonical index directly.
        // If USER_QUEUE or AUTOPLAY, bypass getCanonicalIndex so a queued copy of an album song
        // doesn't falsely resolve, and scan backward for the most recent CONTEXT item.
        if (isCurrentContext) {
            currentCanonicalIndex = getCanonicalIndex(currentItem)
        } else {
            for (i in (currentIndex - 1) downTo 0) {
                val itm = items[i]
                if (itm.queueTier == QueueTier.CONTEXT) {
                    val cIdx = getCanonicalIndex(itm)
                    if (cIdx != null) {
                        currentCanonicalIndex = cIdx
                        break
                    }
                }
            }
            if (currentCanonicalIndex == null) {
                // Forward scan if session began on USER_QUEUE before any CONTEXT played
                for (i in (currentIndex + 1) until items.size) {
                    val itm = items[i]
                    if (itm.queueTier == QueueTier.CONTEXT) {
                        val cIdx = getCanonicalIndex(itm)
                        if (cIdx != null) {
                            currentCanonicalIndex = cIdx - 1
                            break
                        }
                    }
                }
            }
        }

        // If canonicalContext is empty, discover it from the player's context tracks
        if (canonicalContext.isEmpty()) {
            val contextItems = items.filter { it.queueTier == QueueTier.CONTEXT }
            if (contextItems.isNotEmpty()) {
                setCanonicalContext(contextItems)
            }
        }

        val resolvedCurrentIndex = if (isCurrentContext) {
            currentCanonicalIndex ?: getCanonicalIndex(currentItem)
        } else {
            currentCanonicalIndex
        }

        // Fallback for ad-hoc / radio queues without canonical context
        if (resolvedCurrentIndex == null || canonicalContext.isEmpty()) {
            val upcoming = items.drop(from)
            val currentId = currentItem.queueEntryId ?: currentItem.mediaId
            val restored = restoreOrder(
                upcoming = upcoming.map { it.queueEntryId ?: it.mediaId },
                original = original,
                currentId = currentId,
            )
            val order = sections(restored, upcoming)
            var h = 1
            for (item in upcoming) {
                val id = item.queueEntryId ?: item.mediaId
                h = 31 * h + id.hashCode()
            }
            applyOrder(player, from, order, currentId, h, targetShuffleState = false, isRetry = isRetry)
            original = emptyList()
            return
        }

        // Map all existing context MediaItems by canonicalIndex so we preserve existing instances & metadata
        val existingByCanonical = HashMap<Int, MediaItem>()
        val existingIndicesByCanonical = HashMap<Int, Int>()
        for (i in items.indices) {
            if (i == currentIndex) continue
            val item = items[i]
            if (item.queueTier == QueueTier.CONTEXT) {
                val idx = getCanonicalIndex(item)
                if (idx != null && !existingIndicesByCanonical.containsKey(idx)) {
                    existingIndicesByCanonical[idx] = i
                    existingByCanonical[idx] = item
                }
            }
        }

        val precedingRange = if (isCurrentContext) {
            0 until resolvedCurrentIndex
        } else {
            0 until (resolvedCurrentIndex + 1).coerceAtLeast(0)
        }
        val followingRange = ((resolvedCurrentIndex + 1).coerceAtLeast(0)) until canonicalContext.size

        // Canonical context preceding the current track (history)
        val precedingContext = precedingRange.map { cIdx ->
            existingByCanonical[cIdx] ?: canonicalContext[cIdx]
        }
        val precedingIndices = precedingRange.mapNotNull { cIdx ->
            existingIndicesByCanonical[cIdx]
        }

        // Canonical context following the current track (upcoming)
        val followingContext = followingRange.map { cIdx ->
            existingByCanonical[cIdx] ?: canonicalContext[cIdx]
        }
        val followingIndices = followingRange.mapNotNull { cIdx ->
            existingIndicesByCanonical[cIdx]
        }

        // Played manual user queue tracks in history (before current track)
        val historyUserQueueIndices = items.indices.filter { it < currentIndex && items[it].queueTier == QueueTier.USER_QUEUE }
        val historyUserQueue = historyUserQueueIndices.map { items[it] }

        // Unplayed manual user queue tracks remain pinned immediately behind current track
        val userQueue = items.drop(currentIndex + 1).filter { it.queueTier == QueueTier.USER_QUEUE }
        val userQueueIndices = items.indices.filter { it > currentIndex && items[it].queueTier == QueueTier.USER_QUEUE }

        // AutoPlay tracks remain at the tail
        val autoplay = items.drop(currentIndex + 1).filter { it.queueTier == QueueTier.AUTOPLAY }
        val autoplayIndices = items.indices.filter { it > currentIndex && items[it].queueTier == QueueTier.AUTOPLAY }

        val newPlaylist = historyUserQueue + precedingContext + listOf(currentItem) + userQueue + followingContext + autoplay
        val newCurrentIndex = historyUserQueue.size + precedingContext.size
        val currentPos = runCatching { player.currentPosition }.getOrDefault(0L)
        val currentEntryId = currentItem.queueEntryId ?: currentItem.mediaId

        val fullOrder = if (precedingIndices.size == precedingContext.size && followingIndices.size == followingContext.size) {
            historyUserQueueIndices + precedingIndices + listOf(currentIndex) + userQueueIndices + followingIndices + autoplayIndices
        } else {
            emptyList()
        }

        if (fullOrder.isEmpty() && player is MediaController) {
            val upcoming = items.drop(from)
            val currentId = currentItem.queueEntryId ?: currentItem.mediaId
            val restored = restoreOrder(
                upcoming = upcoming.map { it.queueEntryId ?: it.mediaId },
                original = original,
                currentId = currentId,
            )
            val order = sections(restored, upcoming)
            var h = 1
            for (item in upcoming) {
                val id = item.queueEntryId ?: item.mediaId
                h = 31 * h + id.hashCode()
            }
            applyOrder(player, from, order, currentId, h, targetShuffleState = false, isRetry = isRetry)
            original = emptyList()
            return
        }

        applyOrder(
            player = player,
            from = 0,
            order = fullOrder,
            expectedCurrentEntryId = currentEntryId,
            upcomingHash = 0,
            targetShuffleState = false,
            newCurrentIndex = newCurrentIndex,
            currentPos = currentPos,
            fullItems = newPlaylist,
            isRetry = isRetry,
        )
    }

    /**
     * Where each of [upcoming] belongs once [original] is put back, as indices
     * into [upcoming].
     *
     * Each track still queued goes back to where it stood in the old order.
     * Whatever is left over was queued after the shuffle and was never part of
     * that order, so it keeps its place at the end. A track named by [original]
     * that has since been removed is simply skipped.
     *
     * When [currentId] is provided and found in [original], the restoration
     * begins strictly after the current track's position in [original] in
     * original forward positional order without wrapping around to the
     * beginning, preserving history and unplayed upcoming context items.
     */
    internal fun restoreOrder(
        upcoming: List<String>,
        original: List<String>,
        currentId: String? = null,
    ): List<Int> {
        val positions = HashMap<String, ArrayDeque<Int>>(upcoming.size)
        upcoming.forEachIndexed { index, id ->
            positions.getOrPut(id) { ArrayDeque() }.addLast(index)
        }
        val placed = BooleanArray(upcoming.size)
        val out = ArrayList<Int>(upcoming.size)

        val currentIndex = if (currentId != null) original.indexOf(currentId) else -1
        val searchOrder: Iterable<String> = if (currentIndex in 0 until original.size) {
            // Restore strictly forward after current track; do not wrap around to the beginning
            original.subList(currentIndex + 1, original.size)
        } else {
            original
        }

        for (id in searchOrder) {
            val index = positions[id]?.removeFirstOrNull() ?: continue
            placed[index] = true
            out += index
        }
        for (index in upcoming.indices) if (!placed[index]) out += index
        return out
    }

    /** [order] with sections strictly maintained: USER_QUEUE -> CONTEXT -> AUTOPLAY. */
    private fun sections(order: List<Int>, upcoming: List<MediaItem>): List<Int> =
        order.filter { upcoming[it].queueTier == QueueTier.USER_QUEUE } +
            order.filter { upcoming[it].queueTier == QueueTier.CONTEXT } +
            order.filter { upcoming[it].queueTier == QueueTier.AUTOPLAY }

    /**
     * Rearranges the live queue from [from] onwards, [order] naming where each
     * slot's new occupant is standing now.
     *
     * One edit, not a run of [Player.moveMediaItem], which is what this used to
     * do and what made shuffling a long playlist hang the app. Every move is a
     * separate trip across the session boundary and each one lands as a playlist
     * change: the service reserialises its queue snapshot to disk, the
     * notification's custom layout is rebuilt, and the whole timeline is
     * broadcast back for the UI to convert to songs and recompose from. One move
     * costs that once. A thousand-track shuffle is a thousand moves, so it cost
     * all of it a thousand times over, on the main thread, with nothing drawing
     * in between — which is an ANR, not a shuffle.
     *
     * A permutation rather than the rearranged items themselves because of what
     * a controller can see. Media3 strips a [MediaItem]'s `localConfiguration`
     * on the way out to a controller, so the items read back out of one have no
     * playback URI left on them; handing those to `replaceMediaItems` would send
     * the queue back with every upcoming track's URI missing. Indices survive
     * the trip intact, and the session applies them to the items it holds, which
     * never lost anything.
     */
    private fun applyOrder(
        player: Player,
        from: Int,
        order: List<Int>,
        expectedCurrentEntryId: String?,
        upcomingHash: Int,
        targetShuffleState: Boolean,
        newCurrentIndex: Int = -1,
        currentPos: Long = 0L,
        fullItems: List<MediaItem>? = null,
        isRetry: Boolean = false,
    ) {
        if (order.isEmpty() && fullItems == null) {
            _enabled.value = targetShuffleState
            AppSettings.setShuffleEnabled(targetShuffleState)
            inFlight.set(false)
            return
        }
        if (player is MediaController) {
            val future = player.sendCustomCommand(
                SessionCommand(ACTION_REORDER_QUEUE, Bundle.EMPTY),
                bundleOf(
                    EXTRA_REORDER_FROM to from,
                    EXTRA_REORDER_ORDER to order.toIntArray(),
                    EXTRA_EXPECTED_CURRENT_ENTRY_ID to expectedCurrentEntryId,
                    EXTRA_EXPECTED_UPCOMING_HASH to upcomingHash,
                    EXTRA_NEW_CURRENT_INDEX to newCurrentIndex,
                    EXTRA_CURRENT_POSITION to currentPos,
                ),
            )
            val executor = java.util.concurrent.Executor { command ->
                val looper = player.applicationLooper
                if (android.os.Looper.myLooper() == looper) {
                    command.run()
                } else {
                    android.os.Handler(looper).post(command)
                }
            }
            future.addListener({
                try {
                    val result = future.get()
                    if (result?.resultCode == androidx.media3.session.SessionResult.RESULT_SUCCESS) {
                        _enabled.value = targetShuffleState
                        AppSettings.setShuffleEnabled(targetShuffleState)
                        inFlight.set(false)
                    } else {
                        // Stale command rejected by service. Retry once if not already retried.
                        if (!isRetry) {
                            toggle(player, isRetry = true)
                        } else {
                            inFlight.set(false)
                        }
                    }
                } catch (_: Throwable) {
                    inFlight.set(false)
                }
            }, executor)
        } else {
            val ok = if (newCurrentIndex >= 0) {
                if (order.isNotEmpty() && (order.size > player.mediaItemCount || newCurrentIndex >= order.size)) {
                    false
                } else {
                    val target = fullItems ?: order.map { player.getMediaItemAt(it) }
                    val isPlaying = runCatching { player.isPlaying }.getOrDefault(false)
                    player.setMediaItems(target, newCurrentIndex, currentPos)
                    if (runCatching { player.playbackState }.getOrNull() == Player.STATE_IDLE) {
                        player.prepare()
                    }
                    if (isPlaying) {
                        player.play()
                    }
                    true
                }
            } else {
                reorder(player, from, order.toIntArray())
            }
            if (ok) {
                _enabled.value = targetShuffleState
                AppSettings.setShuffleEnabled(targetShuffleState)
            }
            inFlight.set(false)
        }
    }

    /** [applyOrder] as it arrives at the session — see [ACTION_REORDER_QUEUE]. */
    fun reorderFromCommand(player: Player, args: Bundle): Boolean {
        val from = args.getInt(EXTRA_REORDER_FROM, -1)
        val order = args.getIntArray(EXTRA_REORDER_ORDER) ?: return false
        val newCurrentIndex = args.getInt(EXTRA_NEW_CURRENT_INDEX, -1)
        val currentPos = args.getLong(EXTRA_CURRENT_POSITION, 0L)
        val expectedCurrentEntryId = args.getString(EXTRA_EXPECTED_CURRENT_ENTRY_ID)
        val expectedUpcomingHash = args.getInt(EXTRA_EXPECTED_UPCOMING_HASH, 0)
        val hasUpcomingHash = args.containsKey(EXTRA_EXPECTED_UPCOMING_HASH)
        return reorderFromCommand(
            player = player,
            from = from,
            order = order,
            newCurrentIndex = newCurrentIndex,
            currentPos = currentPos,
            expectedCurrentEntryId = expectedCurrentEntryId,
            expectedUpcomingHash = expectedUpcomingHash,
            hasUpcomingHash = hasUpcomingHash,
        )
    }

    fun reorderFromCommand(
        player: Player,
        from: Int,
        order: IntArray,
        newCurrentIndex: Int = -1,
        currentPos: Long = 0L,
        expectedCurrentEntryId: String? = null,
        expectedUpcomingHash: Int = 0,
        hasUpcomingHash: Boolean = false,
    ): Boolean {
        if (newCurrentIndex < 0) {
            if (from < 1 || from + order.size > player.mediaItemCount) return false

            // Guard 1: Current item must be immediately before 'from'
            if (player.currentMediaItemIndex != from - 1) return false

            // Guard 2: Current item's entry ID must match
            val currentItem = player.currentMediaItem ?: return false
            val currentEntryId = currentItem.queueEntryId ?: currentItem.mediaId
            if (expectedCurrentEntryId != null && currentEntryId != expectedCurrentEntryId) return false

            // Guard 3: Upcoming slice content hash must match (catches front-trim index shifts and autoplay swaps)
            if (hasUpcomingHash) {
                var h = 1
                for (i in from until (from + order.size)) {
                    val item = player.getMediaItemAt(i)
                    val id = item.queueEntryId ?: item.mediaId
                    h = 31 * h + id.hashCode()
                }
                if (h != expectedUpcomingHash) return false
            }

            return reorder(player, from, order)
        } else {
            if (order.size > player.mediaItemCount || newCurrentIndex >= order.size) return false
            if (order.any { it !in 0 until player.mediaItemCount }) return false

            // Guard: Current item's entry ID must match expected
            val currentItem = player.currentMediaItem ?: return false
            val currentEntryId = currentItem.queueEntryId ?: currentItem.mediaId
            if (expectedCurrentEntryId != null && currentEntryId != expectedCurrentEntryId) return false

            val target = order.map { player.getMediaItemAt(it) }
            val isPlaying = runCatching { player.isPlaying }.getOrDefault(false)
            player.setMediaItems(target, newCurrentIndex, currentPos)
            if (runCatching { player.playbackState }.getOrNull() == Player.STATE_IDLE) {
                player.prepare()
            }
            if (isPlaying) {
                player.play()
            }
            return true
        }
    }

    /**
     * Refused outright, rather than applied as far as it goes, when the queue no
     * longer has room for it: the order was worked out against the queue as it
     * stood a moment ago, and a queue that has since lost tracks — AutoPlay
     * switched off, say — would have to be rearranged into fewer slots than the
     * permutation names. Dropping the tail of it would drop those tracks from
     * the queue, which is not what shuffling asked for.
     */
    internal fun reorder(player: Player, from: Int, order: IntArray): Boolean {
        if (order.isEmpty() || from < 0 || from + order.size > player.mediaItemCount) return false
        val target = List(order.size) { player.getMediaItemAt(from + order[it]) }
        player.replaceMediaItems(from, from + order.size, target)
        return true
    }

    private fun Player.queueItems(): List<MediaItem> =
        List(mediaItemCount) { getMediaItemAt(it) }
}
