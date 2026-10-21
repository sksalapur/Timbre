package com.music.bitchord.data.stats

import android.content.Context
import android.net.Uri
import com.music.bitchord.BuildConfig
import com.music.bitchord.BitChordApplication
import com.music.bitchord.auth.AuthStore
import com.music.bitchord.auth.BrowserSession
import com.music.bitchord.auth.sessionsFromJson
import com.music.bitchord.auth.toJson
import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.SearchHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The whole of what this app knows about you, as one JSON file you own.
 *
 * Two errands, and they are the same errand: moving to a new phone, and being
 * able to see what is being kept. Everything Replay counts lives only on this
 * device, which is the point of it — and the flip side of that is that a factory
 * reset takes it with no way back, so there has to be a way out.
 *
 * ## What is in it
 *
 * Every preference except credentials (see [AppSettings.exportPrefs]), and every
 * month of listening. Not the audio cache, the downloads or the artwork: those
 * are megabytes that re-fetch themselves, and a backup that is mostly cache is
 * one nobody keeps.
 *
 * ## Typed values rather than a JSON object
 *
 * Preferences come off Android as `Map<String, Any?>` and go back the same way,
 * where the *type* decides which `put` is called. Written as plain JSON, `0.5`
 * comes back a Double and lands in a Float preference as a class-cast crash the
 * first time it is read — weeks later, in a settings screen, with nothing
 * pointing at the import. So each value carries its type and is parsed back into
 * exactly the type it left as, and anything unrecognised is skipped rather than
 * guessed at.
 */
object Backup {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    /** A suggested filename, dated so successive exports don't collide. */
    fun suggestedName(): String =
        "timbre-backup-${DateTimeFormatter.ofPattern("yyyy-MM-dd").format(
            Instant.now().atZone(ZoneId.systemDefault()),
        )}.json"

    /**
     * Writes a backup to [target], a document the user picked.
     *
     * Through the content resolver rather than a [java.io.File] because the
     * destination is wherever they chose — Drive, a USB stick, a folder this app
     * has no path to and no permission for. The picker grants access to that one
     * document and nothing else, which is the correct amount.
     */
    suspend fun exportTo(context: Context, target: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val buckets = ListeningStats.exportAll()
            val authStore = BitChordApplication.authStore
            val authBackup = if (authStore.cookie != null || authStore.sessions.isNotEmpty()) {
                AuthBackup(
                    cookie = authStore.activeSession?.cookie ?: authStore.cookie,
                    sessionsJson = authStore.sessions.toJson(),
                    activeAccountId = authStore.activeAccountId,
                    activeProfileId = authStore.activeProfileId,
                    channelPageId = authStore.channelPageId,
                    channelDataSyncId = authStore.channelDataSyncId,
                    channelName = authStore.channelName,
                    channelAuthUser = authStore.channelAuthUser,
                    discordToken = authStore.discordToken,
                )
            } else null
            val file = BackupFile(
                versionName = BuildConfig.VERSION_NAME,
                exportedAt = Instant.now().toString(),
                settings = AppSettings.exportPrefs().mapNotNull { (key, value) ->
                    PrefValue.of(value)?.let { key to it }
                }.toMap(),
                listening = buckets,
                sources = com.music.bitchord.data.sources.SourceRegistry.exportSources(),
                auth = authBackup,
            )
            val text = json.encodeToString(BackupFile.serializer(), file)
            context.contentResolver.openOutputStream(target, "wt")
                ?.use { it.write(text.toByteArray()) }
                ?: error("Couldn't open that file for writing")
            buckets.size
        }
    }

    /**
     * Reads [source] and replaces this device's settings and listening with it.
     *
     * Validated before anything is written: a file that isn't one of ours, or is
     * from a schema this build can't read, is refused whole. A half-applied
     * import is worse than a refused one — it leaves settings from two devices
     * mixed together with nothing to say which came from where.
     */
    suspend fun importFrom(context: Context, source: Uri): Result<Summary> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(source)
                ?.use { it.readBytes().decodeToString() }
                ?: error("Couldn't open that file")
            val file = runCatching { json.decodeFromString(BackupFile.serializer(), text) }
                .getOrElse { error("That doesn't look like a Timbre backup") }
            require(file.app == APP_TAG || file.app == "bitchord") { "That backup is from another app" }
            require(file.version <= SCHEMA_VERSION) {
                "That backup was written by a newer version of Timbre"
            }

            ListeningStats.importAll(file.listening)
            AppSettings.importPrefs(file.settings.mapValues { it.value.decoded() })
            if (file.sources.isNotEmpty()) {
                com.music.bitchord.data.sources.SourceRegistry.importSources(file.sources)
            }
            var accountRestored = false
            if (file.auth != null) {
                val authStore = BitChordApplication.authStore
                if (file.auth.cookie != null) {
                    authStore.cookie = file.auth.cookie
                }
                if (file.auth.sessionsJson != null) {
                    val importedSessions = sessionsFromJson(file.auth.sessionsJson)
                    if (importedSessions.isNotEmpty()) {
                        authStore.replaceSessions(importedSessions)
                    }
                }
                if (file.auth.activeAccountId != null || file.auth.activeProfileId != null) {
                    authStore.select(file.auth.activeAccountId.orEmpty(), file.auth.activeProfileId)
                }
                authStore.selectChannel(
                    pageId = file.auth.channelPageId,
                    dataSyncId = file.auth.channelDataSyncId,
                    name = file.auth.channelName,
                    authUser = file.auth.channelAuthUser,
                )
                if (file.auth.discordToken != null) {
                    authStore.discordToken = file.auth.discordToken
                }
                val activeCookie = authStore.activeSession?.cookie ?: file.auth.cookie
                if (activeCookie != null) {
                    BrowserSession.installGoogleCookies(activeCookie)
                    Innertube.cookie = activeCookie
                    val activeSession = authStore.activeSession
                    val profile = activeSession?.profiles?.firstOrNull { it.profileId == authStore.activeProfileId }
                    if (profile != null) {
                        Innertube.selectChannel(profile.pageId, profile.dataSyncId, profile.authUser)
                    }
                    CoroutineScope(Dispatchers.IO).launch {
                        runCatching { Innertube.ensureSessionScope() }
                    }
                    accountRestored = true
                }
            }
            // Shares AppSettings' preference file, so it has already been
            // overwritten by the line above — it just doesn't know yet.
            SearchHistory.reload()
            Summary(
                months = file.listening.size,
                settings = file.settings.size,
                sources = file.sources.count { it.kind.isUserAdded },
                from = file.versionName,
                at = file.exportedAt,
                accountRestored = accountRestored,
            )
        }
    }

    /** What an import turned out to contain, for the line shown afterwards. */
    data class Summary(
        val months: Int,
        val settings: Int,
        val from: String,
        val at: String,
        val sources: Int = 0,
        val accountRestored: Boolean = false,
    )

    private const val APP_TAG = "timbre"

    /**
     * Bump when the shape below stops being readable by an older build. A file
     * from a *newer* schema is refused rather than partially read; one from an
     * older schema is read as-is, since every field has a default.
     */
    private const val SCHEMA_VERSION = 1

    @Serializable
    data class AuthBackup(
        val cookie: String? = null,
        val sessionsJson: String? = null,
        val activeAccountId: String? = null,
        val activeProfileId: String? = null,
        val channelPageId: String? = null,
        val channelDataSyncId: String? = null,
        val channelName: String? = null,
        val channelAuthUser: String? = null,
        val discordToken: String? = null,
    )

    @Serializable
    data class BackupFile(
        val app: String = APP_TAG,
        val version: Int = SCHEMA_VERSION,
        /** Which build wrote it — for the reader, not for any logic here. */
        val versionName: String = "",
        val exportedAt: String = "",
        val settings: Map<String, PrefValue> = emptyMap(),
        val listening: List<StoredBucket> = emptyList(),
        val sources: List<com.music.bitchord.data.sources.SourceConfig> = emptyList(),
        val auth: AuthBackup? = null,
    )

    /** One preference, with the type it has to be restored as. */
    @Serializable
    data class PrefValue(
        val type: String,
        val value: String? = null,
        val values: List<String> = emptyList(),
    ) {
        fun decoded(): Any? = when (type) {
            BOOLEAN -> value?.toBooleanStrictOrNull()
            INT -> value?.toIntOrNull()
            LONG -> value?.toLongOrNull()
            FLOAT -> value?.toFloatOrNull()
            STRING -> value
            STRING_SET -> values.toSet()
            else -> null
        }

        companion object {
            /**
             * Numbers go out as strings on purpose. JSON has one number type and
             * every parser picks its own Kotlin type back off it, which is
             * exactly the ambiguity this whole class exists to remove.
             */
            fun of(value: Any?): PrefValue? = when (value) {
                is Boolean -> PrefValue(BOOLEAN, value.toString())
                is Int -> PrefValue(INT, value.toString())
                is Long -> PrefValue(LONG, value.toString())
                is Float -> PrefValue(FLOAT, value.toString())
                is String -> PrefValue(STRING, value)
                is Set<*> -> PrefValue(STRING_SET, values = value.filterIsInstance<String>())
                else -> null
            }

            private const val BOOLEAN = "bool"
            private const val INT = "int"
            private const val LONG = "long"
            private const val FLOAT = "float"
            private const val STRING = "string"
            private const val STRING_SET = "stringSet"
        }
    }
}
