package com.music.bitchord.playback.cast

import android.app.ActivityManager
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.music.bitchord.BuildConfig
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A file record of what casting did, and of how the app died, that survives the
 * process and can be handed to someone.
 *
 * Cast failures happen on a listener's TV and a listener's phone, never on the
 * one with a cable in it, and a crash on connecting takes logcat's evidence
 * with it. So every line is appended to a file in the app's private storage the
 * moment it is written (that copy is the one that is always there), and a copy
 * of each file is mirrored to `Documents/BitChord/logs/` where a file manager,
 * a USB cable or a share sheet can reach it.
 *
 * ### Permission
 *
 * None on Android 10 and up: the mirror is written through MediaStore, which
 * lets an app create its own files under Documents. Android 9 and below would
 * need the legacy storage grant; without it the mirror is skipped and the
 * private copy is all there is. (A bare `/sdcard/BitChord` is out of reach for
 * an app on Android 11+ without all-files access, which is not worth asking
 * for here.)
 *
 * ### Files
 *
 *  - `cast.log` — every cast step, newest at the bottom, trimmed from the top.
 *  - `crash.log` — the stack of every uncaught exception, from any thread, and
 *    the system's own record of how earlier runs ended.
 *
 * Stream URLs carry tokens, so they are logged without their query string.
 */
object CastLog {
    private const val TAG = "BitChordCast"
    private const val CAST_FILE = "cast.log"
    private const val CRASH_FILE = "crash.log"
    private const val MAX_BYTES = 1_000_000L
    private const val MIRROR_DELAY_MS = 1_500L
    private const val RELATIVE_PATH = "Documents/BitChord/logs"

    private val clock = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private val io = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "cast-log").apply { isDaemon = true }
    }
    private val mirrorQueued = AtomicBoolean(false)
    private val mirrorUris = HashMap<String, android.net.Uri>()

    @Volatile
    private var app: Context? = null

    @Volatile
    private var dir: File? = null

    /** Where the mirror landed, for the one line that says so. */
    @Volatile
    var publicLocation: String = "$RELATIVE_PATH (not written yet)"
        private set

    /** Opens the files and writes the header. Idempotent; call from `Application.onCreate`. */
    fun init(context: Context) {
        if (app != null) return
        val appContext = context.applicationContext
        app = appContext
        dir = File(appContext.filesDir, "logs").also { it.mkdirs() }
        installCrashHandler()
        write(
            CAST_FILE,
            "──── session start ────\n" +
                "BitChord ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.APPLICATION_ID}" +
                " debug=${BuildConfig.DEBUG}\n" +
                "device ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})" +
                " · abi ${Build.SUPPORTED_ABIS.joinToString()}\n",
        )
        io.execute { recordPreviousExits(appContext) }
    }

    fun d(message: String) = log('D', message, null)

    fun i(message: String) = log('I', message, null)

    fun w(message: String, error: Throwable? = null) = log('W', message, error)

    fun e(message: String, error: Throwable? = null) = log('E', message, error)

    /** A stream URL fit to write down: where it points, never the credentials after the `?`. */
    fun url(url: String?): String {
        if (url == null) return "null"
        val query = url.indexOf('?')
        return if (query < 0) url else "${url.substring(0, query)}?…(${url.length - query - 1} chars)"
    }

    /** Forces the mirror out now; for the moments just before something might go wrong. */
    fun flush() {
        io.execute { mirrorAll() }
    }

    private fun log(level: Char, message: String, error: Throwable?) {
        val thread = Thread.currentThread().name
        val text = buildString {
            append(clock.format(Date())).append(' ').append(level).append('/').append(thread).append(' ').append(message)
            if (error != null) append('\n').append(Log.getStackTraceString(error))
            append('\n')
        }
        when (level) {
            'D' -> Log.d(TAG, message, error)
            'I' -> Log.i(TAG, message, error)
            'W' -> Log.w(TAG, message, error)
            else -> Log.e(TAG, message, error)
        }
        write(CAST_FILE, text)
    }

    private fun write(name: String, text: String) {
        if (dir == null) return
        io.execute {
            appendTo(name, text)
            queueMirror()
        }
    }

    private fun appendTo(name: String, text: String) {
        val file = File(dir ?: return, name)
        try {
            if (file.length() > MAX_BYTES) trimTop(file)
            file.appendText(text)
        } catch (t: Throwable) {
            Log.w(TAG, "could not append to $name", t)
        }
    }

    /** Keeps the newest half, starting on a whole line. */
    private fun trimTop(file: File) {
        val keep = MAX_BYTES / 2
        val tail = RandomAccessFile(file, "r").use { raf ->
            raf.seek(raf.length() - keep)
            ByteArray(keep.toInt()).also { raf.readFully(it) }
        }
        val firstLine = tail.indexOf('\n'.code.toByte()).plus(1)
        file.writeBytes(tail.copyOfRange(firstLine, tail.size))
    }

    // ── The copy a person can reach ──────────────────────────────────────────

    private fun queueMirror() {
        if (!mirrorQueued.compareAndSet(false, true)) return
        io.schedule({
            mirrorQueued.set(false)
            mirrorAll()
        }, MIRROR_DELAY_MS, TimeUnit.MILLISECONDS)
    }

    private fun mirrorAll() {
        val context = app ?: return
        val source = dir ?: return
        for (name in listOf(CAST_FILE, CRASH_FILE)) {
            val file = File(source, name)
            if (!file.exists()) continue
            try {
                mirror(context, file)
            } catch (t: Throwable) {
                Log.w(TAG, "could not mirror $name to $RELATIVE_PATH", t)
            }
        }
    }

    private fun mirror(context: Context, file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val uri = mirrorUris[file.name] ?: findOrCreate(context, file.name).also { mirrorUris[file.name] = it }
            try {
                resolver.openOutputStream(uri, "wt")!!.use { out -> file.inputStream().use { it.copyTo(out) } }
            } catch (gone: Exception) {
                // The row was deleted by hand, or belongs to a previous install.
                mirrorUris.remove(file.name)
                val fresh = findOrCreate(context, file.name)
                mirrorUris[file.name] = fresh
                resolver.openOutputStream(fresh, "wt")!!.use { out -> file.inputStream().use { it.copyTo(out) } }
            }
            publicLocation = "$RELATIVE_PATH/"
        } else {
            val target = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "BitChord/logs")
            target.mkdirs()
            file.copyTo(File(target, file.name), overwrite = true)
            publicLocation = target.path
        }
    }

    private fun findOrCreate(context: Context, name: String): android.net.Uri {
        val resolver = context.contentResolver
        val files = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        resolver.query(
            files,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
            arrayOf(name, "$RELATIVE_PATH/"),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) return android.content.ContentUris.withAppendedId(files, cursor.getLong(0))
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH)
        }
        return resolver.insert(files, values) ?: error("MediaStore refused $name")
    }

    // ── Crashes ──────────────────────────────────────────────────────────────

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val report = buildString {
                    append("──── CRASH ").append(clock.format(Date())).append(" ────\n")
                    append("thread ").append(thread.name).append('\n')
                    append(Log.getStackTraceString(error)).append('\n')
                }
                // Straight to disk on this thread: the process is about to die
                // and the executor may never get to run again.
                appendTo(CRASH_FILE, report)
                appendTo(CAST_FILE, report)
                mirrorAll()
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /**
     * What the system recorded about earlier runs ending — crashes the handler
     * could not see (native), ANRs, the OOM killer. Each is written once.
     */
    private fun recordPreviousExits(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        try {
            val prefs = context.getSharedPreferences("cast_log", Context.MODE_PRIVATE)
            val seen = prefs.getLong("last_exit", 0L)
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val exits = manager.getHistoricalProcessExitReasons(null, 0, 8).filter { it.timestamp > seen && it.processName.startsWith(context.packageName) }
            if (exits.isEmpty()) return
            val text = exits.sortedBy { it.timestamp }.joinToString("") {
                "${clock.format(Date(it.timestamp))} previous run (${it.processName}) ended: " +
                    "${reasonName(it.reason)} status=${it.status} importance=${it.importance} ${it.description.orEmpty()}\n"
            }
            appendTo(CRASH_FILE, text)
            appendTo(CAST_FILE, text)
            prefs.edit().putLong("last_exit", exits.maxOf { it.timestamp }).apply()
        } catch (t: Throwable) {
            Log.w(TAG, "could not read earlier exit reasons", t)
        }
    }

    private fun reasonName(reason: Int): String = when (reason) {
        ApplicationExitInfoReasons.CRASH -> "CRASH"
        ApplicationExitInfoReasons.CRASH_NATIVE -> "CRASH_NATIVE"
        ApplicationExitInfoReasons.ANR -> "ANR"
        ApplicationExitInfoReasons.LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfoReasons.SIGNALED -> "SIGNALED"
        ApplicationExitInfoReasons.EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        ApplicationExitInfoReasons.USER_REQUESTED -> "USER_REQUESTED"
        ApplicationExitInfoReasons.EXIT_SELF -> "EXIT_SELF"
        else -> "reason=$reason"
    }

    /** The constants, spelled out so this file compiles without an API-30 guard on each. */
    private object ApplicationExitInfoReasons {
        const val EXIT_SELF = 1
        const val SIGNALED = 2
        const val LOW_MEMORY = 3
        const val CRASH = 4
        const val CRASH_NATIVE = 5
        const val ANR = 6
        const val EXCESSIVE_RESOURCE_USAGE = 7
        const val USER_REQUESTED = 10
    }
}
