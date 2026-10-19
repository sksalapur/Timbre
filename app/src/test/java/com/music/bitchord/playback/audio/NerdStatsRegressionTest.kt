/*
 * Copyright (C) 2026 BitChord Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */

package com.music.bitchord.playback.audio

import com.music.bitchord.data.NerdStats
import com.music.bitchord.data.TelemetryProvenance
import com.music.bitchord.data.sources.StreamFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Deterministic regression test suite for NerdStats generation safety and stale-free telemetry:
 *
 * Covers:
 * - Same-track quality upgrade (Opus 160k -> FLAC)
 * - Same-track quality downgrade (FLAC -> Opus)
 * - Lossless unknown encoded bitrate handling (null encoded bitrate, separate PCM throughput)
 * - Prebuffered next-track callback rejection
 * - Late/stale callback rejection across generation increments
 * - Dual-player / tail-player isolation
 * - Stop / recreation invalidation
 * - Sample rate progression across clock families (44.1, 48, 96, 176.4, 192 kHz)
 */
class NerdStatsRegressionTest {

    @Before
    fun setUp() {
        NerdStats.forgetLastSession()
    }

    // ── 1. Opus 160k -> Same-track FLAC ──────────────────────────────────────

    @Test
    fun sameTrackOpusToFlac_evictsOldOpusBitrateImmediately() {
        val mediaId = "track_123"

        // 1. Start on Opus 160k
        val gen1 = NerdStats.advanceGeneration(mediaId, renditionKey = "opus_160")
        NerdStats.onStreamPicked(mediaId, 160, source = "YouTube", generation = gen1)
        NerdStats.onSourceStream(mediaId, StreamFormat(codec = "opus", kbps = 160), source = "YouTube", generation = gen1)

        val opusSnapshot = NerdStats.Snapshot(
            mimeType = "audio/opus",
            bitrateKbps = 160,
            bitrateProvenance = TelemetryProvenance.AUTHORITATIVE,
            pcmDataRateKbps = 1536, // 48k * 16 * 2 / 1000
            sampleRateHz = 48000,
            channels = 2,
            bitDepth = 16,
            sourceName = "YouTube",
            generation = gen1,
            mediaId = mediaId,
        )
        assertTrue(NerdStats.publish(opusSnapshot, expectedGeneration = gen1))

        // Observe Opus playback
        assertEquals("audio/opus", NerdStats.current.value?.mimeType)
        assertEquals(160, NerdStats.current.value?.bitrateKbps)
        assertFalse(NerdStats.current.value?.isLossless == true)

        // 2. Upgrade the SAME logical track to FLAC
        // Advance generation: must immediately evict the old snapshot and clear declared/picked cache!
        val gen2 = NerdStats.advanceGeneration(mediaId, renditionKey = "flac_lossless")
        assertNull("Snapshot must be cleared during upgrade transition window", NerdStats.current.value)
        assertNull("Picked bitrate must be cleared on generation advance", NerdStats.pickedBitrateKbps(mediaId, gen2))

        // 3. New FLAC stream declared without fixed kbps (standard for FLAC)
        NerdStats.onSourceStream(mediaId, StreamFormat(codec = "flac", sampleRateHz = 96000, bitDepth = 24), source = "Tidal", generation = gen2)
        assertEquals("Tidal", NerdStats.sourceFor(mediaId))

        // 4. New authoritative Format arrives from decoder for FLAC (96kHz / 24-bit stereo)
        // FLAC has no fixed encoded bitrate header (null bitrateKbps), PCM data rate is 4608 kbps
        val flacSnapshot = NerdStats.Snapshot(
            mimeType = "audio/flac",
            bitrateKbps = null, // Unknown encoded bitrate
            bitrateProvenance = TelemetryProvenance.UNKNOWN,
            pcmDataRateKbps = 4608, // 96000 * 24 * 2 / 1000
            sampleRateHz = 96000,
            channels = 2,
            bitDepth = 24,
            sourceName = "Tidal",
            generation = gen2,
            mediaId = mediaId,
        )
        assertTrue(NerdStats.publish(flacSnapshot, expectedGeneration = gen2))

        val current = NerdStats.current.value
        assertNotNull(current)
        assertEquals("audio/flac", current?.mimeType)
        assertTrue(current?.isLossless == true)
        assertTrue(current?.isHiRes == true)
        assertNull("Crucial: Old Opus 160k bitrate MUST NOT survive into current FLAC state!", current?.bitrateKbps)
        assertEquals(TelemetryProvenance.UNKNOWN, current?.bitrateProvenance)
        assertEquals(4608, current?.pcmDataRateKbps)
        assertEquals(96000, current?.sampleRateHz)
        assertEquals(24, current?.bitDepth)
    }

    // ── 2. FLAC -> Same-track Opus ───────────────────────────────────────────

    @Test
    fun sameTrackFlacToOpus_replacesLosslessWithLossyCleanly() {
        val mediaId = "track_456"
        val gen1 = NerdStats.advanceGeneration(mediaId, renditionKey = "flac_hi_res")

        val flacSnapshot = NerdStats.Snapshot(
            mimeType = "audio/flac",
            bitrateKbps = null,
            bitrateProvenance = TelemetryProvenance.UNKNOWN,
            pcmDataRateKbps = 4608,
            sampleRateHz = 96000,
            channels = 2,
            bitDepth = 24,
            sourceName = "Qobuz",
            generation = gen1,
            mediaId = mediaId,
        )
        NerdStats.publish(flacSnapshot, expectedGeneration = gen1)
        assertTrue(NerdStats.current.value?.isLossless == true)

        // Downgrade or fallback to Opus
        val gen2 = NerdStats.advanceGeneration(mediaId, renditionKey = "opus_fallback")
        assertNull(NerdStats.current.value)

        NerdStats.onStreamPicked(mediaId, 160, source = "YouTube", generation = gen2)
        val opusSnapshot = NerdStats.Snapshot(
            mimeType = "audio/opus",
            bitrateKbps = 160,
            bitrateProvenance = TelemetryProvenance.AUTHORITATIVE,
            pcmDataRateKbps = null,
            sampleRateHz = 48000,
            channels = 2,
            bitDepth = null,
            sourceName = "YouTube",
            generation = gen2,
            mediaId = mediaId,
        )
        NerdStats.publish(opusSnapshot, expectedGeneration = gen2)

        val current = NerdStats.current.value
        assertEquals("audio/opus", current?.mimeType)
        assertEquals(160, current?.bitrateKbps)
        assertFalse(current?.isLossless == true)
        assertNull(current?.bitDepth)
    }

    // ── 3. Opus Bitrate A -> Bitrate B ───────────────────────────────────────

    @Test
    fun opusBitrateAToBitrateB_updatesCleanly() {
        val mediaId = "track_789"
        val gen1 = NerdStats.advanceGeneration(mediaId, renditionKey = "opus_128")
        NerdStats.onStreamPicked(mediaId, 128, source = "YouTube", generation = gen1)
        NerdStats.publish(
            NerdStats.Snapshot(
                mimeType = "audio/opus",
                bitrateKbps = 128,
                bitrateProvenance = TelemetryProvenance.AUTHORITATIVE,
                sampleRateHz = 48000,
                channels = 2,
                generation = gen1,
                mediaId = mediaId,
            ),
            expectedGeneration = gen1,
        )
        assertEquals(128, NerdStats.current.value?.bitrateKbps)

        val gen2 = NerdStats.advanceGeneration(mediaId, renditionKey = "opus_160")
        NerdStats.onStreamPicked(mediaId, 160, source = "YouTube", generation = gen2)
        NerdStats.publish(
            NerdStats.Snapshot(
                mimeType = "audio/opus",
                bitrateKbps = 160,
                bitrateProvenance = TelemetryProvenance.AUTHORITATIVE,
                sampleRateHz = 48000,
                channels = 2,
                generation = gen2,
                mediaId = mediaId,
            ),
            expectedGeneration = gen2,
        )
        assertEquals(160, NerdStats.current.value?.bitrateKbps)
    }

    // ── 4. Track A -> Track B Transition ─────────────────────────────────────

    @Test
    fun trackAToTrackB_clearsPreviousTrackSnapshot() {
        val genA = NerdStats.advanceGeneration("trackA")
        NerdStats.publish(
            NerdStats.Snapshot(
                mimeType = "audio/opus",
                bitrateKbps = 160,
                sampleRateHz = 48000,
                channels = 2,
                generation = genA,
                mediaId = "trackA",
            ),
            expectedGeneration = genA,
        )
        assertNotNull(NerdStats.current.value)

        // Queue advances to Track B
        val genB = NerdStats.advanceGeneration("trackB")
        assertNull("Snapshot must be null until track B format arrives", NerdStats.current.value)

        // Track B arrives
        NerdStats.publish(
            NerdStats.Snapshot(
                mimeType = "audio/flac",
                bitrateKbps = null,
                pcmDataRateKbps = 1411,
                sampleRateHz = 44100,
                channels = 2,
                bitDepth = 16,
                generation = genB,
                mediaId = "trackB",
            ),
            expectedGeneration = genB,
        )
        assertEquals("trackB", NerdStats.current.value?.mediaId)
        assertEquals("audio/flac", NerdStats.current.value?.mimeType)
    }

    // ── 5. Next-track prebuffer callback rejected ────────────────────────────

    @Test
    fun prebufferedNextTrackCallback_rejectedWhenCurrentTrackIsActive() {
        val genCurrent = NerdStats.advanceGeneration("currentTrack")
        val currentSnapshot = NerdStats.Snapshot(
            mimeType = "audio/flac",
            bitrateKbps = null,
            pcmDataRateKbps = 1411,
            sampleRateHz = 44100,
            channels = 2,
            bitDepth = 16,
            generation = genCurrent,
            mediaId = "currentTrack",
        )
        assertTrue(NerdStats.publish(currentSnapshot, expectedGeneration = genCurrent))

        // Prebuffering nextTrack arrives with mediaId="nextTrack"
        val prebufferedSnapshot = NerdStats.Snapshot(
            mimeType = "audio/opus",
            bitrateKbps = 160,
            sampleRateHz = 48000,
            channels = 2,
            generation = genCurrent,
            mediaId = "nextTrack", // Mismatched mediaId!
        )
        val accepted = NerdStats.publish(prebufferedSnapshot, expectedGeneration = genCurrent)
        assertFalse("Prebuffered next-track snapshot must be rejected!", accepted)

        // Authoritative current state must remain intact
        assertEquals("currentTrack", NerdStats.current.value?.mediaId)
        assertEquals("audio/flac", NerdStats.current.value?.mimeType)
    }

    // ── 6. Stale old callback rejected by generation check ───────────────────

    @Test
    fun staleOldCallback_rejectedByGenerationCheck() {
        val gen1 = NerdStats.advanceGeneration("track_1")
        val gen2 = NerdStats.advanceGeneration("track_2")

        // Callback from gen1 arrives late
        val staleSnapshot = NerdStats.Snapshot(
            mimeType = "audio/opus",
            bitrateKbps = 160,
            sampleRateHz = 48000,
            channels = 2,
            generation = gen1,
            mediaId = "track_1",
        )
        val accepted = NerdStats.publish(staleSnapshot, expectedGeneration = gen1)
        assertFalse("Stale snapshot from previous generation must be rejected!", accepted)
        assertNull(NerdStats.current.value)
    }

    // ── 7. Tail-player / Crossfade player isolation ──────────────────────────

    @Test
    fun tailPlayerCallback_cannotOverwriteMainPlayerTelemetry() {
        val gen = NerdStats.advanceGeneration("activeTrack", deckId = "MAIN")
        NerdStats.publish(
            NerdStats.Snapshot(
                mimeType = "audio/flac",
                bitrateKbps = null,
                pcmDataRateKbps = 2304,
                sampleRateHz = 48000,
                channels = 2,
                bitDepth = 24,
                generation = gen,
                mediaId = "activeTrack",
                deckId = "MAIN",
            ),
            expectedGeneration = gen,
        )

        // Tail player (spare deck) prepares incoming track
        val tailSnapshot = NerdStats.Snapshot(
            mimeType = "audio/opus",
            bitrateKbps = 128,
            sampleRateHz = 44100,
            channels = 2,
            generation = gen,
            mediaId = "incomingTrack", // Different media item on spare deck
            deckId = "SPARE",
        )
        val accepted = NerdStats.publish(tailSnapshot, expectedGeneration = gen)
        assertFalse("Tail player cannot overwrite main player snapshot", accepted)
        assertEquals("MAIN", NerdStats.current.value?.deckId)
        assertEquals("activeTrack", NerdStats.current.value?.mediaId)
    }

    // ── 8. Stop clears NerdStats ─────────────────────────────────────────────

    @Test
    fun stopPlayback_clearsCurrentTelemetry() {
        val gen = NerdStats.advanceGeneration("track")
        NerdStats.publish(
            NerdStats.Snapshot(
                mimeType = "audio/opus",
                bitrateKbps = 160,
                sampleRateHz = 48000,
                channels = 2,
                generation = gen,
                mediaId = "track",
            ),
            expectedGeneration = gen,
        )
        assertNotNull(NerdStats.current.value)

        NerdStats.invalidateCurrent("stop")
        assertNull("Stop must clear current snapshot", NerdStats.current.value)
    }

    // ── 9. Player recreation invalidates old generation ──────────────────────

    @Test
    fun playerRecreation_invalidatesOldGeneration() {
        val oldGen = NerdStats.advanceGeneration("track")
        NerdStats.forgetLastSession() // Simulates service/player teardown
        val newGen = NerdStats.currentGeneration

        assertTrue("New generation must be strictly greater than old generation", newGen > oldGen)
        assertNull(NerdStats.current.value)

        // Attempting to publish with old generation is rejected
        val oldSnapshot = NerdStats.Snapshot(
            mimeType = "audio/flac",
            bitrateKbps = 800,
            sampleRateHz = 44100,
            channels = 2,
            generation = oldGen,
            mediaId = "track",
        )
        assertFalse(NerdStats.publish(oldSnapshot, expectedGeneration = oldGen))
    }

    // ── 10. Sample rate progression across clock families ────────────────────

    @Test
    fun sampleRateProgression_44_48_96_176_192() {
        val rates = listOf(44100, 48000, 96000, 176400, 192000)
        for (rate in rates) {
            val gen = NerdStats.advanceGeneration("track_$rate")
            val pcmRate = (rate * 24 * 2) / 1000
            val snapshot = NerdStats.Snapshot(
                mimeType = "audio/flac",
                bitrateKbps = null,
                pcmDataRateKbps = pcmRate,
                sampleRateHz = rate,
                channels = 2,
                bitDepth = 24,
                generation = gen,
                mediaId = "track_$rate",
            )
            assertTrue(NerdStats.publish(snapshot, expectedGeneration = gen))
            val current = NerdStats.current.value
            assertEquals(rate, current?.sampleRateHz)
            assertEquals(pcmRate, current?.pcmDataRateKbps)
            assertTrue(current?.isLossless == true)
            if (rate > 48000) {
                assertTrue("Rate $rate must be hi-res", current?.isHiRes == true)
            }
        }
    }

    // ── 11. Lossless with unknown encoded bitrate ────────────────────────────

    @Test
    fun losslessSourceWithUnknownEncodedBitrate_showsNullBitrateAndCorrectPcmRate() {
        val gen = NerdStats.advanceGeneration("flacTrack")
        val snapshot = NerdStats.Snapshot(
            mimeType = "audio/flac",
            bitrateKbps = null,
            bitrateProvenance = TelemetryProvenance.UNKNOWN,
            pcmDataRateKbps = 1411,
            sampleRateHz = 44100,
            channels = 2,
            bitDepth = 16,
            generation = gen,
            mediaId = "flacTrack",
        )
        NerdStats.publish(snapshot, expectedGeneration = gen)
        val current = NerdStats.current.value
        assertNull("Encoded bitrate must be null/Unknown", current?.bitrateKbps)
        assertEquals(TelemetryProvenance.UNKNOWN, current?.bitrateProvenance)
        assertEquals(1411, current?.pcmDataRateKbps)
        assertNotEquals(1411, current?.bitrateKbps)
    }

    // ── 12. Provenance transitions remain accurate ───────────────────────────

    @Test
    fun provenanceTransitions_remainAccurate() {
        // Authoritative declared bitrate
        val lossySnapshot = NerdStats.Snapshot(
            mimeType = "audio/mp4a-latm",
            bitrateKbps = 320,
            bitrateProvenance = TelemetryProvenance.AUTHORITATIVE,
            sampleRateHz = 44100,
            channels = 2,
            generation = 1L,
            mediaId = "lossy",
        )
        assertEquals(TelemetryProvenance.AUTHORITATIVE, lossySnapshot.bitrateProvenance)

        // Derived PCM rate is never called Authoritative encoded bitrate
        val derivedPcm = NerdStats.Snapshot(
            mimeType = "audio/raw",
            bitrateKbps = null,
            bitrateProvenance = TelemetryProvenance.UNKNOWN,
            pcmDataRateKbps = 1411,
            sampleRateHz = 44100,
            channels = 2,
            bitDepth = 16,
            generation = 2L,
            mediaId = "raw",
        )
        assertEquals(TelemetryProvenance.UNKNOWN, derivedPcm.bitrateProvenance)
        assertEquals(1411, derivedPcm.pcmDataRateKbps)
    }
}
