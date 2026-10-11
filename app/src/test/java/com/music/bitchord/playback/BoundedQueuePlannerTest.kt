package com.music.bitchord.playback

import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedQueuePlannerTest {

    @Test
    fun `initial plan keeps tier order and bounds history and upcoming context`() {
        val context = songs(80, "context-")
        val window = QueueWindow(context.size).startAt(30)
        val user = listOf(song("user-1"), song("user-2"))
        val autoplay = listOf(song("auto-1"), song("auto-2"))

        val plan = BoundedQueuePlanner.buildInitialPlan(
            canonicalContext = context,
            selectedIndex = 30,
            queueWindow = window,
            preservedUserQueue = user,
            existingAutoplay = autoplay,
        )

        assertEquals((5 until 30).map { "context-$it" }, plan.history.map(Song::videoId))
        assertEquals("context-30", plan.current.videoId)
        assertEquals((31..55).map { "context-$it" }, plan.contextUpcoming.map { context[it.canonicalIndex].videoId })
        assertEquals(MAX_QUEUE_HISTORY, plan.history.size)
        assertEquals(WINDOW_AHEAD, plan.contextUpcoming.size)
        assertEquals(listOf(QueueTier.CONTEXT, QueueTier.CONTEXT), plan.history.take(2).map(Song::queueTier))
        assertEquals(QueueTier.CONTEXT, plan.current.queueTier)
        assertEquals(listOf(QueueTier.USER_QUEUE, QueueTier.USER_QUEUE), plan.userQueue.map(Song::queueTier))
        assertEquals(listOf(QueueTier.AUTOPLAY, QueueTier.AUTOPLAY), plan.autoplay.map(Song::queueTier))

        val tiers = plan.materializedSongs().map(Song::queueTier)
        assertEquals(
            listOf(
                QueueTier.CONTEXT,
                QueueTier.USER_QUEUE,
                QueueTier.CONTEXT,
                QueueTier.AUTOPLAY,
            ),
            listOf(tiers.first(), tiers[MAX_QUEUE_HISTORY + 1], tiers[MAX_QUEUE_HISTORY + 3], tiers.last()),
        )
    }

    @Test
    fun `top up reaches target before autoplay and is idempotent`() {
        val context = songs(80, "context-")
        val window = QueueWindow(context.size).startAt(0)
        val plan = BoundedQueuePlanner.buildInitialPlan(
            context,
            selectedIndex = 0,
            queueWindow = window,
            preservedUserQueue = listOf(song("user")),
            existingAutoplay = listOf(song("auto")),
        )

        val lowWaterIds = (1..(LOW_WATER - 1)).map { "context-$it" }
        val topped = BoundedQueuePlanner.topUpPlan(plan, window, lowWaterIds)

        assertEquals(WINDOW_AHEAD, topped.contextUpcoming.size)
        assertEquals((1..WINDOW_AHEAD).map { "context-$it" }, topped.contextUpcoming.map { context[it.canonicalIndex].videoId })
        assertEquals(listOf("user"), topped.userQueue.map(Song::videoId))
        assertEquals(listOf("auto"), topped.autoplay.map(Song::videoId))

        val repeated = BoundedQueuePlanner.topUpPlan(topped, window, lowWaterIds)
        assertEquals(topped, repeated)

        val alreadyAboveLowWater = BoundedQueuePlanner.topUpPlan(
            topped,
            window,
            (1..LOW_WATER).map { "context-$it" },
        )
        assertEquals(LOW_WATER, alreadyAboveLowWater.contextUpcoming.size)
    }

    @Test
    fun `continuation pending prevents autoplay after materialized window ends`() {
        val context = listOf(song("only"))
        val window = QueueWindow(1).startAt(0)

        val pending = BoundedQueuePlanner.buildInitialPlan(
            canonicalContext = context,
            selectedIndex = 0,
            queueWindow = window,
            existingAutoplay = listOf(song("auto")),
            continuationState = ContinuationState.AVAILABLE,
        )

        assertTrue(pending.contextUpcoming.isEmpty())
        assertTrue(pending.continuationPending)
        assertFalse(pending.contextTrulyExhausted)
        assertFalse(BoundedQueuePlanner.autoplayEligible(pending))
        assertFalse(
            BoundedQueuePlanner.autoplayEligible(
                window,
                ContinuationState.IN_FLIGHT,
            ),
        )

        val exhausted = BoundedQueuePlanner.buildInitialPlan(
            canonicalContext = context,
            selectedIndex = 0,
            queueWindow = window,
            existingAutoplay = listOf(song("auto")),
            continuationState = ContinuationState.EXHAUSTED,
        )
        assertTrue(exhausted.contextTrulyExhausted)
        assertTrue(BoundedQueuePlanner.autoplayEligible(exhausted))
        assertFalse(BoundedQueuePlanner.autoplayEligible(exhausted, repeatAll = true))
    }

    @Test
    fun `large canonical contexts still expose bounded live sections`() {
        val context = songs(10_000, "large-")
        val window = QueueWindow(context.size).startAt(5_000)
        val plan = BoundedQueuePlanner.buildInitialPlan(context, 5_000, window)
        val topped = BoundedQueuePlanner.topUpPlan(
            plan,
            window,
            plan.contextUpcoming.take(LOW_WATER - 1).map { context[it.canonicalIndex].videoId },
        )

        assertEquals(MAX_QUEUE_HISTORY, plan.history.size)
        assertEquals(WINDOW_AHEAD, plan.contextUpcoming.size)
        assertEquals(WINDOW_AHEAD, topped.contextUpcoming.size)
        assertTrue(plan.materializedSongs().size <= MAX_QUEUE_HISTORY + 1 + WINDOW_AHEAD)
    }

    @Test
    fun `history trim count is bounded to twenty five`() {
        assertEquals(0, BoundedQueuePlanner.historyTrimCount(MAX_QUEUE_HISTORY))
        assertEquals(75, BoundedQueuePlanner.historyTrimCount(100))
    }

    private fun songs(count: Int, prefix: String): List<Song> =
        (0 until count).map { song("$prefix$it") }

    private fun song(id: String): Song = Song(
        videoId = id,
        title = id,
        artist = "Artist",
        thumbnailUrl = null,
    )
}
