package com.music.bitchord.playback

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import com.music.bitchord.data.sources.SourceStream
import com.music.bitchord.data.sources.StreamFormat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

/**
 * Which copy of a track is being served, remembered for as long as the track
 * is being served from it.
 *
 * Persisted across app restarts for Lossless/FLAC streams so re-opening the
 * app or replaying a recent song streams directly from the saved FLAC URL
 * in 0ms without falling back to lossy Opus or waiting 5-6s for re-resolution.
 */
object StreamChoice {

    private class Choice(
        val stream: SourceStream,
        val at: Long,
        val wallClockAt: Long,
        val substituted: Boolean,
    )

    private val chosen = ConcurrentHashMap<String, Choice>()
    private var prefs: SharedPreferences? = null
    private val json = Json { ignoreUnknownKeys = true }

    fun init(context: Context) {
        prefs = context.getSharedPreferences("bitchord_stream_choices", Context.MODE_PRIVATE)
        loadStoredChoices()
    }

    @Serializable
    private data class StoredChoice(
        val videoId: String,
        val url: String,
        val codec: String? = null,
        val kbps: Int? = null,
        val sampleRateHz: Int? = null,
        val bitDepth: Int? = null,
        val headers: Map<String, String> = emptyMap(),
        val belowRequest: Boolean = false,
        val durationSec: Int? = null,
        val sourceConfigId: String? = null,
        val substituted: Boolean = false,
        val savedAtEpochMs: Long = 0L,
    )

    private fun loadStoredChoices() {
        val p = prefs ?: return
        val now = System.currentTimeMillis()
        val all = runCatching { p.all }.getOrNull() ?: return
        for ((key, value) in all) {
            val raw = value as? String ?: continue
            val stored = runCatching { json.decodeFromString(StoredChoice.serializer(), raw) }.getOrNull()
            if (stored != null) {
                if (now - stored.savedAtEpochMs <= PERSISTENT_TTL_MS) {
                    val stream = SourceStream(
                        url = stored.url,
                        format = StreamFormat(
                            codec = stored.codec,
                            kbps = stored.kbps,
                            sampleRateHz = stored.sampleRateHz,
                            bitDepth = stored.bitDepth,
                        ),
                        headers = stored.headers,
                        belowRequest = stored.belowRequest,
                        durationSec = stored.durationSec,
                        sourceConfigId = stored.sourceConfigId,
                    )
                    chosen[key] = Choice(
                        stream = stream,
                        at = SystemClock.elapsedRealtime(),
                        wallClockAt = stored.savedAtEpochMs,
                        substituted = stored.substituted,
                    )
                } else {
                    p.edit().remove(key).apply()
                }
            }
        }
    }

    private fun persistChoice(videoId: String, stream: SourceStream, substituted: Boolean) {
        val p = prefs ?: return
        runCatching {
            val item = StoredChoice(
                videoId = videoId,
                url = stream.url,
                codec = stream.format.codec,
                kbps = stream.format.kbps,
                sampleRateHz = stream.format.sampleRateHz,
                bitDepth = stream.format.bitDepth,
                headers = stream.headers,
                belowRequest = stream.belowRequest,
                durationSec = stream.durationSec,
                sourceConfigId = stream.sourceConfigId,
                substituted = substituted,
                savedAtEpochMs = System.currentTimeMillis(),
            )
            val encoded = json.encodeToString(StoredChoice.serializer(), item)
            p.edit().putString(videoId, encoded).apply()
        }
    }

    /**
     * The stream already serving [videoId], or null if this is the first read
     * for it — or the last one was long enough ago that its URL is no longer
     * worth trusting.
     */
    fun of(videoId: String): SourceStream? {
        val choice = chosen[videoId] ?: return null
        val now = SystemClock.elapsedRealtime()
        val wallNow = System.currentTimeMillis()
        if (now - choice.at > TTL_MS && wallNow - choice.wallClockAt > PERSISTENT_TTL_MS) {
            chosen.remove(videoId)
            prefs?.edit()?.remove(videoId)?.apply()
            return null
        }
        return choice.stream
    }

    /**
     * Records [stream] as the one copy of [videoId] this play is reading.
     */
    fun remember(videoId: String, stream: SourceStream, substituted: Boolean) {
        if (chosen.size >= MAX_REMEMBERED) {
            val now = SystemClock.elapsedRealtime()
            chosen.entries.removeIf { now - it.value.at > TTL_MS }
            if (chosen.size >= MAX_REMEMBERED) {
                chosen.minByOrNull { it.value.at }?.let { chosen.remove(it.key) }
            }
        }
        chosen[videoId] = Choice(
            stream = stream,
            at = SystemClock.elapsedRealtime(),
            wallClockAt = System.currentTimeMillis(),
            substituted = substituted,
        )
        if (substituted || stream.format.isLossless == true) {
            persistChoice(videoId, stream, substituted)
        }
    }

    /**
     * Whether the copy currently serving [videoId] came from a source standing
     * in for YouTube.
     */
    /**
     * Checks if a lossy choice was recorded very recently (within [windowMs]).
     * Used by PlaybackService to distinguish an immediate re-read of a just-resolved
     * stream from a stale lossy choice that should be re-evaluated.
     */
    fun isRecentLossyChoice(videoId: String, windowMs: Long = 8_000L): Boolean {
        val choice = chosen[videoId] ?: return false
        if (choice.stream.format.isLossless == true) return false
        val elapsed = SystemClock.elapsedRealtime() - choice.at
        return elapsed < windowMs
    }

    fun isSubstitute(videoId: String): Boolean = chosen[videoId]?.substituted == true

    /**
     * Releases [videoId] to be resolved afresh.
     */
    fun forget(videoId: String) {
        chosen.remove(videoId)
        prefs?.edit()?.remove(videoId)?.apply()
    }

    private val refusedSubstitutes = ConcurrentHashMap<String, Long>()

    fun refuseSubstitutes(videoId: String) {
        if (refusedSubstitutes.size >= MAX_REMEMBERED) refusedSubstitutes.clear()
        refusedSubstitutes[videoId] = SystemClock.elapsedRealtime()
    }

    fun substitutesRefused(videoId: String): Boolean {
        val at = refusedSubstitutes[videoId] ?: return false
        if (SystemClock.elapsedRealtime() - at <= REFUSAL_MS) return true
        refusedSubstitutes.remove(videoId)
        return false
    }

    /** In-memory active window. */
    private const val TTL_MS = 15 * 60 * 1000L

    /** 12 hours persistence for lossless URLs across app closes and process death. */
    private const val PERSISTENT_TTL_MS = 12 * 60 * 60 * 1000L

    private const val REFUSAL_MS = 10 * 60 * 1000L

    private const val MAX_REMEMBERED = 64
}
