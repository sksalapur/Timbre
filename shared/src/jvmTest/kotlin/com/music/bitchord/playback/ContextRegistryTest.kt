package com.music.bitchord.playback

import com.music.bitchord.data.model.Song
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContextRegistryTest {
    @Test
    fun registerDeduplicatesByVideoIdAndSnapshotIsImmutable() {
        val initial = mutableListOf(song("a"), song("a", title = "later"), song("b"))
        val registry = ContextRegistry()

        val registered = registry.register(
            contextToken = "context-a",
            sourceKey = "playlist:one",
            browseId = "VLone",
            songs = initial,
        )
        initial += song("c")

        assertEquals(listOf("a", "b"), registered.songs.map(Song::videoId))
        assertEquals(listOf("a", "b"), registry.snapshot()!!.songs.map(Song::videoId))
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (registered.songs as MutableList<Song>) += song("cannot-mutate")
        }
    }

    @Test
    fun continuationGrowthDeduplicatesPreservesOrderAndNotifiesOnce() {
        val growth = mutableListOf<ContextSnapshot>()
        val registry = ContextRegistry { growth += it }
        registry.register("context-a", "playlist:one", "VLone", listOf(song("a")), "page-1")

        val request = assertNotNull(registry.claimContinuation())
        val result = registry.complete(
            request,
            ContinuationPage(
                songs = listOf(song("a"), song("b"), song("b")),
                continuationToken = "page-2",
            ),
        )

        assertEquals(ContinuationResultStatus.COMPLETED, result.status)
        assertEquals(listOf("a", "b"), result.snapshot!!.songs.map(Song::videoId))
        assertEquals(ContinuationState.AVAILABLE, result.snapshot.continuationState)
        assertEquals(listOf("b"), result.addedSongs.map(Song::videoId))
        assertEquals(1, growth.size)
        assertEquals(2, growth.single().version)
        assertEquals("page-2", registry.claimContinuation()!!.continuationToken)
    }

    @Test
    fun staleCompletionAfterNewRegistrationCannotChangeNewContext() {
        val registry = ContextRegistry()
        registry.register("old", "playlist:old", "VLold", listOf(song("old")), "old-page")
        val oldRequest = assertNotNull(registry.claimContinuation())

        registry.register("new", "playlist:new", "VLnew", listOf(song("new")), null)
        val stale = registry.complete(
            oldRequest,
            ContinuationPage(listOf(song("must-not-append")), "next"),
        )

        assertEquals(ContinuationResultStatus.STALE_REQUEST, stale.status)
        assertEquals(listOf("new"), registry.snapshot()!!.songs.map(Song::videoId))
        assertEquals(ContinuationState.EXHAUSTED, registry.snapshot()!!.continuationState)
    }

    @Test
    fun duplicatePageIsBlockedWithDiagnosticAndCannotLoop() {
        val registry = ContextRegistry()
        registry.register("context-a", "playlist:one", "VLone", listOf(song("a")), "page-1")
        val request = assertNotNull(registry.claimContinuation())

        val rejected = registry.complete(
            request,
            ContinuationPage(listOf(song("a")), "page-2"),
        )

        assertEquals(ContinuationResultStatus.REJECTED_NO_PROGRESS, rejected.status)
        assertEquals(ContinuationDiagnostic.DUPLICATE_PAGE, rejected.diagnostic)
        assertEquals(ContinuationState.BLOCKED, rejected.snapshot!!.continuationState)
        assertEquals(ContinuationDiagnostic.DUPLICATE_PAGE, rejected.snapshot.diagnostic)
        assertNull(registry.claimContinuation())
        assertEquals(listOf("a"), registry.snapshot()!!.songs.map(Song::videoId))
    }

    @Test
    fun emptyPageIsBlockedAsNoProgress() {
        val registry = ContextRegistry()
        registry.register("context-a", "playlist:one", "VLone", listOf(song("a")), "page-1")
        val request = assertNotNull(registry.claimContinuation())

        val rejected = registry.complete(request, ContinuationPage(emptyList(), "page-2"))

        assertEquals(ContinuationResultStatus.REJECTED_NO_PROGRESS, rejected.status)
        assertEquals(ContinuationDiagnostic.NO_PROGRESS, rejected.diagnostic)
        assertEquals(ContinuationState.BLOCKED, registry.snapshot()!!.continuationState)
        assertNull(registry.claimContinuation())
    }

    @Test
    fun repeatedContinuationTokenIsRejectedEvenWhenPageHasNewSong() {
        val registry = ContextRegistry()
        registry.register("context-a", "playlist:one", "VLone", listOf(song("a")), "page-1")
        val request = assertNotNull(registry.claimContinuation())

        val rejected = registry.complete(
            request,
            ContinuationPage(listOf(song("b")), "page-1"),
        )

        assertEquals(ContinuationResultStatus.REJECTED_NO_PROGRESS, rejected.status)
        assertEquals(ContinuationDiagnostic.REPEATED_CONTINUATION, rejected.diagnostic)
        assertEquals(listOf("a"), registry.snapshot()!!.songs.map(Song::videoId))
        assertNull(registry.claimContinuation())
    }

    @Test
    fun onlyOneRequestCanBeInFlightAndFailureAllowsRetry() {
        val registry = ContextRegistry()
        registry.register("context-a", "playlist:one", "VLone", listOf(song("a")), "page-1")

        val first = assertNotNull(registry.claimContinuation())
        assertNull(registry.claimContinuation())

        val failed = registry.fail(first, IllegalStateException("network"))
        assertEquals(ContinuationResultStatus.FAILED, failed.status)
        assertEquals(ContinuationDiagnostic.REQUEST_FAILED, failed.diagnostic)

        val retry = assertNotNull(registry.claimContinuation())
        assertTrue(retry.requestId != first.requestId)
        val completed = registry.complete(retry, ContinuationPage(listOf(song("b")), null))
        assertEquals(ContinuationResultStatus.COMPLETED, completed.status)
        assertEquals(ContinuationState.EXHAUSTED, registry.snapshot()!!.continuationState)
    }

    @Test
    fun appendRejectsStaleContextAndGrowthCallbackCanBeAdded() {
        val growth = mutableListOf<ContextSnapshot>()
        val registry = ContextRegistry()
        val listenerId = registry.addGrowthListener { growth += it }
        registry.register("context-a", "playlist:one", "VLone", listOf(song("a")), null)

        val stale = registry.append("other-context", listOf(song("b")))
        assertEquals(AppendStatus.STALE_CONTEXT, stale.status)
        assertEquals(listOf("a"), registry.snapshot()!!.songs.map(Song::videoId))

        val appended = registry.append("context-a", listOf(song("b"), song("b")))
        assertEquals(AppendStatus.APPENDED, appended.status)
        assertEquals(listOf("a", "b"), registry.snapshot()!!.songs.map(Song::videoId))
        assertEquals(1, growth.size)
        assertTrue(registry.removeGrowthListener(listenerId))
        assertTrue(!registry.removeGrowthListener(listenerId))
    }

    private fun song(id: String, title: String = id) = Song(
        videoId = id,
        title = title,
        artist = "Artist",
        thumbnailUrl = null,
    )
}
