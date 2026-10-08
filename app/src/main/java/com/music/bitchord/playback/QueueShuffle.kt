package com.music.bitchord.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
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
 * The rearranging is applied in-place to the player's queue via [Player.replaceMediaItems]
 * so that currently playing playback is completely uninterrupted.
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

    fun clearCanonicalContext() {
        canonicalContext = emptyList()
        entryProvenance.clear()
        mediaIdProvenance.clear()
    }

    /**
     * Checks if the active [canonicalContext] matches the player's active queue.
     * If the current (or nearest) context item does not resolve via [entryProvenance]
     * (by its unique queueEntryId), the held context is stale (e.g. from a prior album)
     * and is discarded.
     */
    internal fun isCanonicalContextValid(player: Player): Boolean {
        if (canonicalContext.isEmpty()) return false
        val count = player.mediaItemCount
        val cur = player.currentMediaItemIndex
        if (cur !in 0 until count) return false

        val currentItem = player.getMediaItemAt(cur)
        val contextItem = if (currentItem.queueTier == QueueTier.CONTEXT) {
            currentItem
        } else {
            (cur - 1 downTo 0).firstNotNullOfOrNull { i ->
                player.getMediaItemAt(i).takeIf { it.queueTier == QueueTier.CONTEXT }
            } ?: (cur + 1 until count).firstNotNullOfOrNull { i ->
                player.getMediaItemAt(i).takeIf { it.queueTier == QueueTier.CONTEXT }
            }
        }

        val entryId = contextItem?.queueEntryId ?: return false
        return entryProvenance.containsKey(entryId)
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

    fun toggle(player: Player) {
        if (!isCanonicalContextValid(player)) {
            clearCanonicalContext()
        }
        if (_enabled.value) restore(player) else shuffle(player)
    }

    /**
     * Turns shuffle on without touching the current queue — for the Shuffle
     * button on an album or playlist page, where the queue it applies to is the
     * one about to replace this one. [playSongs] builds that one shuffled.
     */
    fun enableForNextQueue() {
        original = emptyList()
        clearCanonicalContext()
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
    private fun shuffle(player: Player) {
        if (!isCanonicalContextValid(player)) {
            clearCanonicalContext()
        }
        val items = player.queueItems()
        val currentIndex = player.currentMediaItemIndex
        val from = currentIndex + 1
        if (currentIndex !in items.indices || from >= items.size) {
            _enabled.value = true
            AppSettings.setShuffleEnabled(true)
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
        if (original.isEmpty()) {
            original = items.map { it.key() }
        }
        val userQueueIndices = upcoming.indices.filter { upcoming[it].queueTier == QueueTier.USER_QUEUE }
        val contextIndices = upcoming.indices.filter { upcoming[it].queueTier == QueueTier.CONTEXT }
        val autoplayIndices = upcoming.indices.filter { upcoming[it].queueTier == QueueTier.AUTOPLAY }

        val shuffledContext = if (contextIndices.size > 1) {
            avoidIdentityShuffle(contextIndices, contextIndices.shuffled())
        } else {
            contextIndices
        }

        val order = userQueueIndices + shuffledContext + autoplayIndices
        val ok = reorder(player, from, order.toIntArray())
        if (ok) {
            _enabled.value = true
            AppSettings.setShuffleEnabled(true)
        }
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
     *
     * Applied via two-segment [Player.replaceMediaItems] (tail first, then history)
     * skipping common prefixes and suffixes via [replaceChanged] so that the currently
     * playing item and preloaded next items are completely untouched: no gap, no rebuffer,
     * and no playback position reset.
     */
    private fun restore(player: Player) {
        if (!isCanonicalContextValid(player)) {
            clearCanonicalContext()
        }
        val items = player.queueItems()
        val currentIndex = player.currentMediaItemIndex
        val from = currentIndex + 1
        if (currentIndex !in items.indices || from >= items.size) {
            _enabled.value = false
            AppSettings.setShuffleEnabled(false)
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

        val resolvedCurrentIndex = if (isCurrentContext) {
            currentCanonicalIndex ?: getCanonicalIndex(currentItem)
        } else {
            currentCanonicalIndex
        }

        // Fallback for ad-hoc / radio queues without canonical context
        if (resolvedCurrentIndex == null || canonicalContext.isEmpty()) {
            val upcoming = items.drop(from)
            val currentId = currentItem.key()
            val restored = restoreOrder(
                upcoming = upcoming.map { it.key() },
                original = original,
                currentId = currentId,
            )
            val order = sections(restored, upcoming)
            reorder(player, from, order.toIntArray())
            original = emptyList()
            _enabled.value = false
            AppSettings.setShuffleEnabled(false)
            return
        }

        // Map all existing context MediaItems by canonicalIndex so we preserve existing instances & metadata
        val existingByCanonical = HashMap<Int, MediaItem>()
        for (i in items.indices) {
            if (i == currentIndex) continue
            val item = items[i]
            if (item.queueTier == QueueTier.CONTEXT) {
                val idx = getCanonicalIndex(item)
                if (idx != null && !existingByCanonical.containsKey(idx)) {
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

        // Canonical context following the current track (upcoming)
        val followingContext = followingRange.map { cIdx ->
            existingByCanonical[cIdx] ?: canonicalContext[cIdx]
        }

        // Played manual user queue tracks in history (before current track)
        val historyUserQueue = items.filterIndexed { idx, it -> idx < currentIndex && it.queueTier == QueueTier.USER_QUEUE }

        // Unplayed manual user queue tracks remain pinned immediately behind current track
        val userQueue = items.drop(currentIndex + 1).filter { it.queueTier == QueueTier.USER_QUEUE }

        // AutoPlay tracks remain at the tail
        val autoplay = items.drop(currentIndex + 1).filter { it.queueTier == QueueTier.AUTOPLAY }

        val newPlaylist = historyUserQueue + precedingContext + listOf(currentItem) + userQueue + followingContext + autoplay
        val newCurrentIndex = historyUserQueue.size + precedingContext.size

        val cur = player.currentMediaItemIndex
        val history = newPlaylist.subList(0, newCurrentIndex)
        val upcoming = newPlaylist.subList(newCurrentIndex + 1, newPlaylist.size)

        // Two-segment replace: tail first so cur remains valid, then history.
        // Current playing item is completely untouched: no gap, no rebuffer, no seek.
        // replaceChanged skips common prefix (preserving preloaded next item) and suffix.
        replaceChanged(player, cur + 1, player.mediaItemCount, upcoming)
        if (cur > 0 || history.isNotEmpty()) {
            replaceChanged(player, 0, cur, history)
        }

        _enabled.value = false
        AppSettings.setShuffleEnabled(false)
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
     * Replaces items in [player] from [from] to [to] with [new], skipping any unchanged
     * common prefix and common suffix to avoid discarding preloaded buffers (e.g. for the next item)
     * and avoiding redundant timeline change events.
     *
     * Returns true if any items were replaced, false if the range was already identical.
     */
    internal fun replaceChanged(player: Player, from: Int, to: Int, new: List<MediaItem>): Boolean {
        if (from < 0 || to < from || to > player.mediaItemCount) return false
        val old = (from until to).map { player.getMediaItemAt(it).key() }
        val n = new.map { it.key() }
        var p = 0
        while (p < old.size && p < n.size && old[p] == n[p]) p++
        var s = 0
        while (s < old.size - p && s < n.size - p &&
            old[old.size - 1 - s] == n[n.size - 1 - s]
        ) s++
        if (p == old.size && p == n.size) return false
        player.replaceMediaItems(from + p, to - s, new.subList(p, n.size - s))
        return true
    }

    internal fun MediaItem.key(): String = queueEntryId ?: mediaId

    /**
     * Rearranges the live queue from [from] onwards, [order] naming where each
     * slot's new occupant is standing now.
     */
    internal fun reorder(player: Player, from: Int, order: IntArray): Boolean {
        if (order.isEmpty() || from < 0 || from + order.size > player.mediaItemCount) return false
        val target = List(order.size) { player.getMediaItemAt(from + order[it]) }
        replaceChanged(player, from, from + order.size, target)
        return true
    }

    private fun Player.queueItems(): List<MediaItem> =
        List(mediaItemCount) { getMediaItemAt(it) }
}
