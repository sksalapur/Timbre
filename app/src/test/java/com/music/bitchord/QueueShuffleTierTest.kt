package com.music.bitchord

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.EXTRA_EXPECTED_CURRENT_ENTRY_ID
import com.music.bitchord.playback.EXTRA_EXPECTED_UPCOMING_HASH
import com.music.bitchord.playback.EXTRA_REORDER_FROM
import com.music.bitchord.playback.EXTRA_REORDER_ORDER
import com.music.bitchord.playback.QueueShuffle
import com.music.bitchord.playback.canonicalIndex
import com.music.bitchord.playback.queueEntryId
import com.music.bitchord.playback.queueTier
import com.music.bitchord.playback.toMediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class QueueShuffleTierTest {

    private fun testSong(
        id: String,
        tier: QueueTier = QueueTier.CONTEXT,
        entryId: String = "entry-$id",
    ) = Song(
        videoId = id,
        title = "Title $id",
        artist = "Artist $id",
        thumbnailUrl = null,
        queueTier = tier,
        queueEntryId = entryId,
    )

    private class MockPlayerState(
        val items: MutableList<MediaItem>,
        var currentIndex: Int = 0,
    ) {
        val player: Player = java.lang.reflect.Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getMediaItemCount" -> items.size
                "getMediaItemAt" -> items[args[0] as Int]
                "getCurrentMediaItemIndex" -> currentIndex
                "getCurrentMediaItem" -> items.getOrNull(currentIndex)
                "getCurrentPosition" -> 0L
                "isPlaying" -> false
                "getPlaybackState" -> Player.STATE_READY
                "replaceMediaItems" -> {
                    val from = args[0] as Int
                    val to = args[1] as Int
                    @Suppress("UNCHECKED_CAST")
                    val newItems = args[2] as List<MediaItem>
                    for (i in (to - 1) downTo from) {
                        items.removeAt(i)
                    }
                    items.addAll(from, newItems)
                    null
                }
                "setMediaItems" -> {
                    @Suppress("UNCHECKED_CAST")
                    val newItems = args[0] as List<MediaItem>
                    val startIndex = args[1] as Int
                    items.clear()
                    items.addAll(newItems)
                    currentIndex = startIndex
                    null
                }
                "seekToNextMediaItem" -> {
                    if (currentIndex < items.size - 1) currentIndex++
                    null
                }
                "seekToPreviousMediaItem" -> {
                    if (currentIndex > 0) currentIndex--
                    null
                }
                "prepare" -> null
                "play" -> null
                else -> null
            }
        } as Player
    }

    @Before
    fun setUp() {
        val editor = java.lang.reflect.Proxy.newProxyInstance(
            android.content.SharedPreferences.Editor::class.java.classLoader,
            arrayOf(android.content.SharedPreferences.Editor::class.java),
        ) { proxy, method, _ ->
            if (method.returnType == android.content.SharedPreferences.Editor::class.java) proxy else null
        }
        val prefs = java.lang.reflect.Proxy.newProxyInstance(
            android.content.SharedPreferences::class.java.classLoader,
            arrayOf(android.content.SharedPreferences::class.java),
        ) { _, method, _ ->
            if (method.name == "edit") editor else null
        } as android.content.SharedPreferences
        val field = com.music.bitchord.data.settings.AppSettings::class.java.getDeclaredField("prefs")
        field.isAccessible = true
        field.set(com.music.bitchord.data.settings.AppSettings, prefs)

        QueueShuffle.setEnabled(false)
        QueueShuffle.canonicalContext = emptyList()
    }

    @Test
    fun `startingOrder preserves USER_QUEUE items at front and shuffles context`() {
        val selected = testSong("selected", tier = QueueTier.CONTEXT)
        val u1 = testSong("u1", tier = QueueTier.USER_QUEUE)
        val u2 = testSong("u2", tier = QueueTier.USER_QUEUE)
        val c1 = testSong("c1", tier = QueueTier.CONTEXT)
        val c2 = testSong("c2", tier = QueueTier.CONTEXT)
        val c3 = testSong("c3", tier = QueueTier.CONTEXT)
        val a1 = testSong("a1", tier = QueueTier.AUTOPLAY)

        val input = listOf(selected, u1, u2, c1, c2, c3, a1)
        val result = QueueShuffle.startingOrder(input, startIndex = 0)

        assertEquals("selected", result[0].videoId)
        assertEquals("u1", result[1].videoId)
        assertEquals("u2", result[2].videoId)

        // Context items are between indices 3 and 5 inclusive
        val resultContextIds = result.subList(3, 6).map { it.videoId }.toSet()
        assertEquals(setOf("c1", "c2", "c3"), resultContextIds)

        // Autoplay at index 6
        assertEquals("a1", result[6].videoId)
    }

    @Test
    fun `restoreOrder deterministically restores duplicate songs using unique queueEntryIds`() {
        val entry1 = "uuid-1"
        val entry2 = "uuid-2"
        val original = listOf(entry1, entry2)
        val shuffled = listOf(entry2, entry1)

        val restoredIndices = QueueShuffle.restoreOrder(shuffled, original)
        val restoredEntries = restoredIndices.map { shuffled[it] }

        assertEquals(original, restoredEntries)
    }

    @Test
    fun `restoreOrder restores canonical sequence immediately after current track without resurrecting history`() {
        val collection = (1..10).map { "track-$it" }
        // Current track is track-7. History holds track-1..track-6 (not in upcoming).
        // Upcoming contains shuffled remaining unplayed context tracks:
        val upcomingShuffled = listOf("track-9", "track-8", "track-10")

        val restoredIndices = QueueShuffle.restoreOrder(
            upcoming = upcomingShuffled,
            original = collection,
            currentId = "track-7",
        )
        val restored = restoredIndices.map { upcomingShuffled[it] }

        // Must restore to 8, 9, 10 immediately after 7. Played history (1..6) must NOT be resurrected.
        assertEquals(listOf("track-8", "track-9", "track-10"), restored)
    }

    @Test
    fun `restoreOrder restores strictly forward after current track and does not wrap around to beginning`() {
        val collection = (1..10).map { "track-$it" }
        // Current track is track-7. History holds track-1..track-6.
        // Upcoming contains shuffled forward tracks:
        val upcomingShuffled = listOf("track-10", "track-8", "track-9")

        val restoredIndices = QueueShuffle.restoreOrder(
            upcoming = upcomingShuffled,
            original = collection,
            currentId = "track-7",
        )
        val restored = restoredIndices.map { upcomingShuffled[it] }

        // Must restore strictly forward starting after track-7 to [8, 9, 10].
        // Does NOT wrap around to the beginning (tracks 1..6 are never visited).
        assertEquals(listOf("track-8", "track-9", "track-10"), restored)
    }

    @Test
    fun `restoreOrder falls back predictably when currentId is null or not in original`() {
        val collection = (1..10).map { "track-$it" }
        val upcomingShuffled = listOf("track-9", "track-8", "track-10")

        val restoredIndicesUnknown = QueueShuffle.restoreOrder(
            upcoming = upcomingShuffled,
            original = collection,
            currentId = "unknown-seed",
        )
        val restoredUnknown = restoredIndicesUnknown.map { upcomingShuffled[it] }
        assertEquals(listOf("track-8", "track-9", "track-10"), restoredUnknown)

        val restoredIndicesNull = QueueShuffle.restoreOrder(
            upcoming = upcomingShuffled,
            original = collection,
            currentId = null,
        )
        val restoredNull = restoredIndicesNull.map { upcomingShuffled[it] }
        assertEquals(listOf("track-8", "track-9", "track-10"), restoredNull)
    }

    @Test
    fun `new shuffle session preserves unconsumed IDs and does not duplicate history tracks into upcoming`() {
        val songs = (1..5).map { testSong("track-$it", QueueTier.CONTEXT, "id-$it") }
        val items = songs.mapIndexed { idx, s ->
            QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = "id-${idx + 1}", newCanonicalIndex = idx)
        }.toMutableList()
        QueueShuffle.setCanonicalContext(items.toList())

        // Start playback at index 2 (track-3). History = [track-1 (id-1), track-2 (id-2)].
        // Upcoming = [track-4 (id-4), track-5 (id-5)].
        val state = MockPlayerState(items, currentIndex = 2)
        QueueShuffle.setEnabled(false)

        // Turn Shuffle ON
        QueueShuffle.toggle(state.player)
        assertTrue(QueueShuffle.enabled.value)

        // History items 0 and 1 must retain their exact original IDs and remain in history
        assertEquals("id-1", state.items[0].queueEntryId)
        assertEquals("id-2", state.items[1].queueEntryId)

        // Current item (track-3) stays current
        assertEquals("id-3", state.items[2].queueEntryId)
        assertEquals(2, state.items[2].canonicalIndex)

        // Total timeline size remains 5 (no duplicate songs injected into upcoming)
        assertEquals(5, state.items.size)
        val upcoming = state.items.drop(3)
        assertEquals(2, upcoming.size)

        // Canonical indices present in upcoming must be exactly unconsumed tracks 3 and 4
        val upcomingCanonicalIndices = upcoming.map { it.canonicalIndex }.toSet()
        assertEquals(setOf(3, 4), upcomingCanonicalIndices)

        // Unconsumed items (cIdx 3 and 4) must preserve their existing IDs
        val unconsumed4 = upcoming.first { it.canonicalIndex == 3 }
        val unconsumed5 = upcoming.first { it.canonicalIndex == 4 }
        assertEquals("id-4", unconsumed4.queueEntryId)
        assertEquals("id-5", unconsumed5.queueEntryId)

        // All IDs across the entire queue are completely unique
        val allIds = state.items.map { it.queueEntryId }
        assertEquals(state.items.size, allIds.toSet().size)
    }

    @Test
    fun `duplicate song occurrences have distinct canonical indices and only current occurrence is excluded`() {
        val songA1 = testSong("song-A", QueueTier.CONTEXT, "id-A1")
        val songB = testSong("song-B", QueueTier.CONTEXT, "id-B")
        val songA2 = testSong("song-A", QueueTier.CONTEXT, "id-A2")
        val songC = testSong("song-C", QueueTier.CONTEXT, "id-C")

        val rawList = listOf(songA1, songB, songA2, songC)
        val items = rawList.mapIndexed { idx, s ->
            QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = s.queueEntryId, newCanonicalIndex = idx)
        }.toMutableList()
        QueueShuffle.setCanonicalContext(items.toList())

        // Playback starts at index 0 (song-A occurrence 1, canonicalIndex 0)
        val state = MockPlayerState(items, currentIndex = 0)
        QueueShuffle.setEnabled(false)

        QueueShuffle.toggle(state.player)
        assertTrue(QueueShuffle.enabled.value)

        // Current item is song-A (id-A1, canonicalIndex 0)
        assertEquals("id-A1", state.items[0].queueEntryId)
        assertEquals(0, state.items[0].canonicalIndex)

        // Upcoming has 3 items: song-B (cIdx 1), song-A occurrence 2 (cIdx 2), song-C (cIdx 3)
        val upcoming = state.items.drop(1)
        assertEquals(3, upcoming.size)

        val upcomingCanonicalIndices = upcoming.map { it.canonicalIndex }
        assertTrue(upcomingCanonicalIndices.contains(1))
        assertTrue(upcomingCanonicalIndices.contains(2))
        assertTrue(upcomingCanonicalIndices.contains(3))

        // Occurrence 2 of song-A was unconsumed, so it retained its original ID "id-A2"
        val occurrence2 = upcoming.first { it.canonicalIndex == 2 }
        assertEquals("id-A2", occurrence2.queueEntryId)
        assertEquals("song-A", occurrence2.mediaId)
    }

    @Test
    fun `shuffle OFF restores strictly forward canonical tracks without wrap-around and without resurrecting history`() {
        val songs = (1..10).map { testSong("track-$it", QueueTier.CONTEXT, "id-$it") }
        val items = songs.mapIndexed { idx, s ->
            QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = "id-${idx + 1}", newCanonicalIndex = idx)
        }.toMutableList()
        QueueShuffle.setCanonicalContext(items.toList())

        // Current track is track-7 (index 6, canonicalIndex 6).
        // History: tracks 1..6 (cIdx 0..5).
        // Upcoming: tracks 8..10 (cIdx 7..9).
        val state = MockPlayerState(items, currentIndex = 6)
        QueueShuffle.setEnabled(false)

        // Turn Shuffle ON
        QueueShuffle.toggle(state.player)
        assertTrue(QueueShuffle.enabled.value)
        assertEquals(6 + 1 + 3, state.items.size) // 6 history + 1 current + 3 upcoming = 10 (no duplicates!)

        // Turn Shuffle OFF
        QueueShuffle.toggle(state.player)
        assertTrue(!QueueShuffle.enabled.value)

        // Current track remains track-7
        assertEquals("id-7", state.items[6].queueEntryId)
        assertEquals(6, state.items[6].canonicalIndex)

        // History remains untouched: tracks 1..6
        for (i in 0..5) {
            assertEquals("id-${i + 1}", state.items[i].queueEntryId)
            assertEquals(i, state.items[i].canonicalIndex)
        }

        // Upcoming must be restored strictly forward: [track-8, track-9, track-10]
        val upcoming = state.items.drop(7)
        assertEquals(3, upcoming.size)
        assertEquals(listOf(7, 8, 9), upcoming.map { it.canonicalIndex })
        assertEquals(listOf("id-8", "id-9", "id-10"), upcoming.map { it.queueEntryId })
    }

    @Test
    fun `end of collection shuffle ON does not duplicate history tracks into upcoming`() {
        val songs = (1..5).map { testSong("track-$it", QueueTier.CONTEXT, "id-$it") }
        val items = songs.mapIndexed { idx, s ->
            QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = "id-${idx + 1}", newCanonicalIndex = idx)
        }.toMutableList()
        QueueShuffle.setCanonicalContext(items.toList())

        // Playing at track-5 (final track, index 4). Upcoming is empty.
        val state = MockPlayerState(items, currentIndex = 4)
        QueueShuffle.setEnabled(false)

        // Turn Shuffle ON
        QueueShuffle.toggle(state.player)
        assertTrue(QueueShuffle.enabled.value)

        // Current track remains track-5
        assertEquals("id-5", state.items[4].queueEntryId)
        assertEquals(4, state.items[4].canonicalIndex)

        // Upcoming remains empty (history tracks 1..4 are NOT duplicated into upcoming)
        val upcoming = state.items.drop(5)
        assertEquals(0, upcoming.size)
        assertEquals(5, state.items.size)

        // Toggling Shuffle OFF restores to strictly forward (which is empty after track-5)
        QueueShuffle.toggle(state.player)
        assertTrue(!QueueShuffle.enabled.value)
        assertEquals(5, state.items.size)
        assertEquals(0, state.items.drop(5).size)
    }

    @Test
    fun `album and playlist parity with identical provenance and lifecycle`() {
        fun runLifecycle(sourceType: PlaybackSourceType, sourceName: String): List<String> {
            val songs = (1..6).map {
                testSong("track-$it", QueueTier.CONTEXT, "id-$it").copy(
                    playbackSource = sourceName,
                    playbackSourceType = sourceType,
                )
            }
            val items = songs.mapIndexed { idx, s ->
                QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = "id-${idx + 1}", newCanonicalIndex = idx)
            }.toMutableList()
            QueueShuffle.setCanonicalContext(items.toList())
            val state = MockPlayerState(items, currentIndex = 2) // playing track-3
            QueueShuffle.setEnabled(false)

            // Turn ON
            QueueShuffle.toggle(state.player)
            val onCanonicalIndices = state.items.drop(3).mapNotNull { it.canonicalIndex }.sorted().joinToString()

            // Turn OFF
            QueueShuffle.toggle(state.player)
            val offCanonicalIndices = state.items.drop(3).mapNotNull { it.canonicalIndex }.joinToString()

            return listOf(onCanonicalIndices, offCanonicalIndices)
        }

        val albumResult = runLifecycle(PlaybackSourceType.BROWSE, "Album Master")
        val playlistResult = runLifecycle(PlaybackSourceType.QUEUE, "Playlist Favorites")

        // Both Album and Playlist produce identical canonical index sets on Shuffle ON and identical sequences on Shuffle OFF
        assertEquals(albumResult[0], playlistResult[0])
        assertEquals(albumResult[1], playlistResult[1])
        assertEquals("3, 4, 5", albumResult[0])
        assertEquals("3, 4, 5", albumResult[1])
    }

    @Test
    fun `identifying current track falls back to queueEntryId if canonicalIndex extra is missing`() {
        val songs = (1..4).map { testSong("track-$it", QueueTier.CONTEXT, "id-$it") }
        val canonicalItems = songs.mapIndexed { idx, s ->
            QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = "id-${idx + 1}", newCanonicalIndex = idx)
        }
        QueueShuffle.setCanonicalContext(canonicalItems)

        // Create player items where current track has NO canonical index extra, only queueEntryId
        val playerItems = songs.mapIndexed { idx, s ->
            if (idx == 1) {
                // track-2 with missing canonicalIndex
                QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = "id-2", newCanonicalIndex = null)
            } else {
                QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = "id-${idx + 1}", newCanonicalIndex = idx)
            }
        }.toMutableList()

        val state = MockPlayerState(playerItems, currentIndex = 1) // playing track-2
        QueueShuffle.setEnabled(false)

        // Turn Shuffle ON: current track must be correctly identified via queueEntryId fallback
        QueueShuffle.toggle(state.player)
        assertTrue(QueueShuffle.enabled.value)

        // Current track remains track-2
        assertEquals("id-2", state.items[1].queueEntryId)

        // Upcoming has unconsumed tracks (tracks 3, 4 with canonical indices 2, 3), and history track-1 is not duplicated
        val upcoming = state.items.drop(2)
        assertEquals(2, upcoming.size)
        val upcomingCanonicalIndices = upcoming.map { it.canonicalIndex }.toSet()
        assertEquals(setOf(2, 3), upcomingCanonicalIndices)
    }

    @Test
    fun `reproduce album alternating skip and shuffle toggle`() {
        val albumSongs = (1..10).map { testSong("track-$it", QueueTier.CONTEXT, "entry-$it") }
        val items = albumSongs.map { it.toMediaItem() }.toMutableList()
        val state = MockPlayerState(items, currentIndex = 0)

        // Start with shuffle off
        QueueShuffle.setEnabled(false)

        repeat(20) { step ->
            state.player.seekToNextMediaItem()
            // When reaching near the end of album (e.g. index 8), simulate AutoPlay appending tracks with stable unique IDs
            if (state.currentIndex >= 8 && state.items.size == 10) {
                val autoplay = (1..5).map { testSong("autoplay-$it", QueueTier.AUTOPLAY, "entry-auto-$it").toMediaItem() }
                state.items.addAll(autoplay)
            }
            QueueShuffle.toggle(state.player)

            // Invariants:
            // 1. All queueEntryIds remain distinct across the queue
            val entryIds = state.items.map { it.queueEntryId ?: it.mediaId }
            assertEquals("All queue entry IDs must remain unique at step $step", state.items.size, entryIds.toSet().size)
        }
    }

    @Test
    fun `header shuffle button flow establishes canonical context and restores strictly forward on shuffle OFF`() {
        val songs = (1..6).map { testSong("track-$it", QueueTier.CONTEXT, "entry-$it") }
        // Simulate tapping header Shuffle button:
        QueueShuffle.enableForNextQueue()
        assertTrue(QueueShuffle.enabled.value)

        // Random pick selects index 2 (track-3)
        val selectedIndex = 2
        val startingOrder = QueueShuffle.startingOrder(songs, selectedIndex)

        // 1. Lead track is track-3 at index 0
        assertEquals("track-3", startingOrder[0].videoId)

        // 2. Remaining 5 context tracks are shuffled
        val restIds = startingOrder.drop(1).map { it.videoId }.toSet()
        assertEquals(setOf("track-1", "track-2", "track-4", "track-5", "track-6"), restIds)

        // Set up player as if playSongs loaded this queue
        val items = startingOrder.mapIndexed { idx, s ->
            val originalCanonicalIndex = songs.indexOfFirst { it.videoId == s.videoId }
            QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = "live-$idx", newCanonicalIndex = originalCanonicalIndex)
        }.toMutableList()

        val state = MockPlayerState(items, currentIndex = 0) // playing track-3 (canonicalIndex 2)

        // 3. User turns Shuffle OFF while playing track-3
        QueueShuffle.toggle(state.player)
        assertTrue(!QueueShuffle.enabled.value)

        // Current track remains track-3 (canonicalIndex 2) at index 2
        assertEquals(2, state.currentIndex)
        assertEquals("track-3", state.items[2].mediaId)
        assertEquals(2, state.items[2].canonicalIndex)

        // Preceding history is restored: [track-1, track-2]
        assertEquals(listOf("track-1", "track-2"), state.items.take(2).map { it.mediaId })
        assertEquals(listOf(0, 1), state.items.take(2).map { it.canonicalIndex })

        // Upcoming is restored strictly forward: [track-4, track-5, track-6]
        val upcoming = state.items.drop(3)
        assertEquals(listOf("track-4", "track-5", "track-6"), upcoming.map { it.mediaId })
        assertEquals(listOf(3, 4, 5), upcoming.map { it.canonicalIndex })

        // Total collection has all 6 tracks preserved!
        assertEquals(6, state.items.size)

        // 4. Hitting Prev steps backwards through preceding tracks
        state.player.seekToPreviousMediaItem()
        assertEquals(1, state.currentIndex)
        assertEquals("track-2", state.items[state.currentIndex].mediaId)

        state.player.seekToPreviousMediaItem()
        assertEquals(0, state.currentIndex)
        assertEquals("track-1", state.items[state.currentIndex].mediaId)
    }

    @Test
    fun `getCanonicalIndex and restore fall back cleanly to mediaId when item has a distinct or fresh queueEntryId`() {
        val songs = (1..5).map { testSong("track-$it", QueueTier.CONTEXT, "canonical-id-$it") }
        val canonicalItems = songs.mapIndexed { idx, s ->
            QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = s.queueEntryId, newCanonicalIndex = idx)
        }
        QueueShuffle.setCanonicalContext(canonicalItems)

        // Simulating an active player item playing track-3 where queueEntryId was regenerated fresh or missing
        val freshActiveItem = QueueShuffle.withQueueMetadata(
            testSong("track-3", QueueTier.CONTEXT, "fresh-id-3").toMediaItem(),
            newEntryId = "fresh-id-3",
            newCanonicalIndex = null,
        )

        val resolvedIndex = QueueShuffle.getCanonicalIndex(freshActiveItem)
        assertEquals(2, resolvedIndex)

        // Set up player with fresh active item at current position
        val otherItems = listOf(1, 2, 4, 5).map {
            QueueShuffle.withQueueMetadata(testSong("track-$it", QueueTier.CONTEXT, "other-id-$it").toMediaItem(), newEntryId = "other-id-$it")
        }
        val playerList = mutableListOf(otherItems[0], otherItems[1], freshActiveItem, otherItems[2], otherItems[3])
        val state = MockPlayerState(playerList, currentIndex = 2)
        QueueShuffle.setEnabled(true)

        // Turning shuffle OFF restores canonical collection around track-3
        QueueShuffle.toggle(state.player)
        assertEquals(false, QueueShuffle.enabled.value)
        assertEquals(2, state.currentIndex)
        assertEquals("track-3", state.items[2].mediaId)
        assertEquals(listOf("track-1", "track-2"), state.items.take(2).map { it.mediaId })
        assertEquals(listOf("track-4", "track-5"), state.items.drop(3).map { it.mediaId })
    }

    @Test
    fun `user-queued duplicate of album song restores context continuing from last played context track`() {
        // Album has 25 tracks: track-0 to track-24
        val albumSongs = (0..24).map { testSong("track-$it", QueueTier.CONTEXT, "id-$it") }
        val canonicalItems = albumSongs.mapIndexed { idx, s ->
            QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = s.queueEntryId, newCanonicalIndex = idx)
        }
        QueueShuffle.setCanonicalContext(canonicalItems)

        // Context playback was at #5 (history has track-0..5)
        val historyContext = canonicalItems.take(6)

        // Current item is a user-queued copy of album track #20
        val userQueuedItem = QueueShuffle.withQueueMetadata(
            testSong("track-20", QueueTier.USER_QUEUE, "user-entry-track-20").toMediaItem(),
            newEntryId = "user-entry-track-20",
            newCanonicalIndex = null,
            newTier = QueueTier.USER_QUEUE,
        )

        // Upcoming has remaining context tracks (tracks 6..24, including album's track #20)
        val upcomingContext = canonicalItems.drop(6)

        // Player items: history (0..5) + userQueuedItem (at index 6) + upcomingContext (tracks 6..24)
        val playerList = (historyContext + listOf(userQueuedItem) + upcomingContext).toMutableList()
        val state = MockPlayerState(playerList, currentIndex = 6)
        QueueShuffle.setEnabled(true)

        // Turn Shuffle OFF
        QueueShuffle.toggle(state.player)
        assertEquals(false, QueueShuffle.enabled.value)

        // Current track remains user-queued track-20 at index 6
        assertEquals(6, state.currentIndex)
        val current = state.items[state.currentIndex]
        assertEquals("track-20", current.mediaId)
        assertEquals(QueueTier.USER_QUEUE, current.queueTier)
        assertEquals("user-entry-track-20", current.queueEntryId)

        // History contains all 6 context tracks played up to #5
        assertEquals(6, state.items.take(6).size)
        assertEquals((0..5).map { "track-$it" }, state.items.take(6).map { it.mediaId })
        assertEquals((0..5).toList(), state.items.take(6).map { it.canonicalIndex })

        // Upcoming continues from track-6 onwards!
        val upcoming = state.items.drop(7)
        assertEquals("track-6", upcoming.first().mediaId)
        assertEquals(6, upcoming.first().canonicalIndex)
        assertEquals(QueueTier.CONTEXT, upcoming.first().queueTier)

        // Album's own copy of track-20 is preserved in upcoming as CONTEXT
        val albumCopy20 = upcoming.first { it.canonicalIndex == 20 }
        assertEquals("track-20", albumCopy20.mediaId)
        assertEquals("id-20", albumCopy20.queueEntryId)
        assertEquals(QueueTier.CONTEXT, albumCopy20.queueTier)

        // Total items = 6 history + 1 current + 19 upcoming = 26 items
        assertEquals(26, state.items.size)
    }

    @Test
    fun `double tap while command in flight is dropped and inFlight resets properly`() {
        val songs = (1..5).map { testSong("track-$it", QueueTier.CONTEXT, "id-$it") }
        val items = songs.mapIndexed { idx, s ->
            QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = s.queueEntryId, newCanonicalIndex = idx)
        }.toMutableList()
        val state = MockPlayerState(items, currentIndex = 0)
        QueueShuffle.setEnabled(false)

        // Simulate an in-flight command
        QueueShuffle.inFlight.set(true)

        // A second toggle tap while inFlight is true must return immediately without altering state
        QueueShuffle.toggle(state.player)
        assertEquals(false, QueueShuffle.enabled.value)
        assertEquals(true, QueueShuffle.inFlight.get())

        // Once inFlight is cleared, toggle works normally
        QueueShuffle.inFlight.set(false)
        QueueShuffle.toggle(state.player)
        assertEquals(true, QueueShuffle.enabled.value)
        assertEquals(false, QueueShuffle.inFlight.get())
    }

    @Test
    fun `size mismatch in reorder returns false and leaves flags unchanged`() {
        val songs = (1..3).map { testSong("track-$it", QueueTier.CONTEXT, "id-$it") }
        val items = songs.map { it.toMediaItem() }.toMutableList()
        val state = MockPlayerState(items, currentIndex = 0)
        QueueShuffle.setEnabled(false)

        // Empty order returns false
        val emptyResult = QueueShuffle.reorder(state.player, from = 1, order = intArrayOf())
        assertEquals(false, emptyResult)

        // Out of bounds order returns false
        val overflowResult = QueueShuffle.reorder(state.player, from = 1, order = IntArray(10) { it })
        assertEquals(false, overflowResult)

        // reorderFromCommand with mismatched size returns false
        val commandResult = QueueShuffle.reorderFromCommand(
            player = state.player,
            from = 1,
            order = IntArray(50) { it },
        )
        assertEquals(false, commandResult)

        // Shuffle flags remain unchanged
        assertEquals(false, QueueShuffle.enabled.value)
    }

    @Test
    fun `reorderFromCommand upcoming hash matches when tail grows from autoplay`() {
        val songs = (0..3).map { testSong("track-$it", QueueTier.CONTEXT, "id-$it") }
        val items = songs.map { it.toMediaItem() }.toMutableList()
        val state = MockPlayerState(items, currentIndex = 0)

        // Snapshot had 3 upcoming items (indices 1..3)
        val upcoming = items.drop(1)
        var expectedHash = 1
        for (item in upcoming) {
            val id = item.queueEntryId ?: item.mediaId
            expectedHash = 31 * expectedHash + id.hashCode()
        }

        // Autoplay appends 2 new items to the tail before the command executes
        val auto1 = testSong("auto-1", QueueTier.AUTOPLAY, "id-auto-1").toMediaItem()
        val auto2 = testSong("auto-2", QueueTier.AUTOPLAY, "id-auto-2").toMediaItem()
        state.items.add(auto1)
        state.items.add(auto2)
        assertEquals(6, state.items.size)

        // Command was prepared for the original 3 upcoming items with permutation [2, 0, 1]
        val order = intArrayOf(2, 0, 1)

        // Hash matches the first order.size upcoming items, so reorder succeeds despite queue growth
        val ok = QueueShuffle.reorderFromCommand(
            player = state.player,
            from = 1,
            order = order,
            expectedCurrentEntryId = state.items[0].queueEntryId ?: state.items[0].mediaId,
            expectedUpcomingHash = expectedHash,
            hasUpcomingHash = true,
        )
        assertEquals(true, ok)

        // Permuted slice (indices 1..3) is reordered: [track-3, track-1, track-2]
        assertEquals("track-3", state.items[1].mediaId)
        assertEquals("track-1", state.items[2].mediaId)
        assertEquals("track-2", state.items[3].mediaId)

        // Tail items appended by autoplay remain in place untouched
        assertEquals("auto-1", state.items[4].mediaId)
        assertEquals("auto-2", state.items[5].mediaId)
        assertEquals(6, state.items.size)
    }
}


