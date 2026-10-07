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

    /** Provenance mapping: queueEntryId (or mediaId) -> canonicalIndex. */
    private val entryProvenance = HashMap<String, Int>()

    fun setCanonicalContext(items: List<MediaItem>) {
        entryProvenance.clear()
        canonicalContext = items.mapIndexed { idx, item ->
            val entryId = item.queueEntryId ?: UUID.randomUUID().toString()
            val stamped = withQueueMetadata(item, newEntryId = entryId, newCanonicalIndex = idx)
            entryProvenance[entryId] = idx
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
            val byEntryId = canonicalContext.indexOfFirst { it.queueEntryId == entryId }
            if (byEntryId >= 0) return byEntryId
        }
        return entryProvenance[item.mediaId] ?: canonicalContext.indexOfFirst {
            it.mediaId == item.mediaId
        }.takeIf { it >= 0 }
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
        if (_enabled.value) restore(player) else shuffle(player)
        // Persist the new state so it survives app restarts.
        AppSettings.setShuffleEnabled(_enabled.value)
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
    private fun shuffle(player: Player) {
        val items = player.queueItems()
        val currentIndex = player.currentMediaItemIndex
        val from = currentIndex + 1
        if (currentIndex !in items.indices) {
            _enabled.value = true
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
        val userQueue = upcoming.filter { it.queueTier == QueueTier.USER_QUEUE }
        val autoplay = upcoming.filter { it.queueTier == QueueTier.AUTOPLAY }
        val unconsumedContext = upcoming.filter { it.queueTier == QueueTier.CONTEXT }

        // Randomize unconsumed upcoming context tracks without duplicating history tracks
        val shuffledContext = if (unconsumedContext.size > 1) {
            avoidIdentityShuffleMediaItems(unconsumedContext, unconsumedContext.shuffled())
        } else {
            unconsumedContext
        }

        val newUpcoming = userQueue + shuffledContext + autoplay
        player.replaceMediaItems(from, player.mediaItemCount, newUpcoming)
        _enabled.value = true
        AppSettings.setShuffleEnabled(true)
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
    private fun restore(player: Player) {
        val items = player.queueItems()
        val currentIndex = player.currentMediaItemIndex
        if (currentIndex !in items.indices) {
            _enabled.value = false
            AppSettings.setShuffleEnabled(false)
            return
        }

        val currentItem = items[currentIndex]
        val currentCanonicalIndex = getCanonicalIndex(currentItem)

        // If canonicalContext is empty, discover it from the player's context tracks
        if (canonicalContext.isEmpty()) {
            val contextItems = items.filter { it.queueTier == QueueTier.CONTEXT }
            if (contextItems.isNotEmpty()) {
                setCanonicalContext(contextItems)
            }
        }

        val resolvedCurrentIndex = currentCanonicalIndex ?: getCanonicalIndex(currentItem)

        if (resolvedCurrentIndex == null || canonicalContext.isEmpty()) {
            val from = currentIndex + 1
            val upcoming = items.drop(from)
            val currentId = currentItem.queueEntryId ?: currentItem.mediaId
            val restored = restoreOrder(
                upcoming = upcoming.map { it.queueEntryId ?: it.mediaId },
                original = original,
                currentId = currentId,
            )
            applyOrder(player, from, sections(restored, upcoming))
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

        // Unplayed manual user queue tracks remain pinned immediately behind current track
        val userQueue = items.drop(currentIndex + 1).filter { it.queueTier == QueueTier.USER_QUEUE }
        // AutoPlay tracks remain at the tail
        val autoplay = items.drop(currentIndex + 1).filter { it.queueTier == QueueTier.AUTOPLAY }

        // Canonical context preceding the current track (history)
        val precedingContext = (0 until resolvedCurrentIndex).map { cIdx ->
            existingByCanonical[cIdx] ?: canonicalContext[cIdx]
        }

        // Canonical context following the current track (upcoming)
        val followingContext = ((resolvedCurrentIndex + 1) until canonicalContext.size).map { cIdx ->
            existingByCanonical[cIdx] ?: canonicalContext[cIdx]
        }

        val newPlaylist = precedingContext + listOf(currentItem) + userQueue + followingContext + autoplay
        val newCurrentIndex = precedingContext.size // = resolvedCurrentIndex

        val isPlaying = runCatching { player.isPlaying }.getOrDefault(false)
        val currentPos = runCatching { player.currentPosition }.getOrDefault(0L)
        player.setMediaItems(newPlaylist, newCurrentIndex, currentPos)
        if (runCatching { player.playbackState }.getOrNull() == Player.STATE_IDLE) {
            player.prepare()
        }
        if (isPlaying) {
            player.play()
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
    private fun applyOrder(player: Player, from: Int, order: List<Int>) {
        if (order.isEmpty()) return
        if (player is MediaController) {
            player.sendCustomCommand(
                SessionCommand(ACTION_REORDER_QUEUE, Bundle.EMPTY),
                bundleOf(
                    EXTRA_REORDER_FROM to from,
                    EXTRA_REORDER_ORDER to order.toIntArray(),
                ),
            )
        } else {
            reorder(player, from, order.toIntArray())
        }
    }

    /** [applyOrder] as it arrives at the session — see [ACTION_REORDER_QUEUE]. */
    fun reorderFromCommand(player: Player, args: Bundle) {
        val from = args.getInt(EXTRA_REORDER_FROM, -1)
        val order = args.getIntArray(EXTRA_REORDER_ORDER) ?: return
        if (from >= 0) reorder(player, from, order)
    }

    /**
     * Refused outright, rather than applied as far as it goes, when the queue no
     * longer has room for it: the order was worked out against the queue as it
     * stood a moment ago, and a queue that has since lost tracks — AutoPlay
     * switched off, say — would have to be rearranged into fewer slots than the
     * permutation names. Dropping the tail of it would drop those tracks from
     * the queue, which is not what shuffling asked for.
     */
    private fun reorder(player: Player, from: Int, order: IntArray) {
        if (order.isEmpty() || from + order.size > player.mediaItemCount) return
        val target = List(order.size) { player.getMediaItemAt(from + order[it]) }
        player.replaceMediaItems(from, from + order.size, target)
    }

    private fun Player.queueItems(): List<MediaItem> =
        List(mediaItemCount) { getMediaItemAt(it) }
}
