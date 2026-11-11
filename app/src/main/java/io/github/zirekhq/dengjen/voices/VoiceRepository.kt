package io.github.zirekhq.dengjen.voices

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** What the voice manager shows for one voice. */
sealed interface InstallState {
    data object Idle : InstallState
    data class Downloading(val progress: Progress) : InstallState
    data class Failed(val reason: String) : InstallState
}

/**
 * The app's single source of voice state, shared by the voice manager screen and the TTS service.
 * Network and disk work runs on a background executor; listeners are called on the main thread.
 */
class VoiceRepository private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val root = File(context.filesDir, "voices")
    private val cacheFile = File(root, ".catalogue/voices.json")
    val store = VoiceStore(root, HttpTransport())

    private val executor: ExecutorService = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())
    private val listeners = mutableSetOf<() -> Unit>()
    private val states = ConcurrentHashMap<String, InstallState>()
    private val cancels = ConcurrentHashMap<String, Boolean>()

    @Volatile
    var catalogue: Catalogue? = null
        private set

    /** Kurmanji voices from the catalogue, or none until it loads. */
    fun kurmanjiVoices(): List<CatalogueVoice> = catalogue?.byLanguage(LANGUAGE).orEmpty().sortedBy { it.key }

    fun state(key: String): InstallState = states[key] ?: InstallState.Idle

    fun installed(): List<String> = store.installed()

    /** The voice the engine speaks with when the caller names none: the chosen one, else the first installed. */
    fun defaultVoice(): String? {
        val installed = installed()
        return prefs.getString(KEY_DEFAULT, null)?.takeIf { it in installed } ?: installed.firstOrNull()
    }

    fun setDefaultVoice(key: String) {
        prefs.edit().putString(KEY_DEFAULT, key).apply()
        notifyListeners()
    }

    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    /** Loads the cached catalogue at once, then refreshes it from the network. */
    fun loadCatalogue(onDone: (Boolean) -> Unit) {
        executor.execute {
            if (catalogue == null) catalogue = readCache()
            val fresh = try {
                val text = HttpTransport().open(Catalogue.URL, 0).use { it.body.readBytesLimited(Catalogue.MAX_BYTES) }
                Catalogue.parse(text).also {
                    logSkipped(it)
                    writeCache(text)
                }
            } catch (e: IOException) {
                Log.w(TAG, "could not fetch the voice catalogue", e)
                null
            } catch (e: CatalogueException) {
                Log.w(TAG, "rejected the voice catalogue: ${e.message}")
                null
            }
            if (fresh != null) catalogue = fresh
            main.post {
                onDone(fresh != null || catalogue != null)
                notifyListeners()
            }
        }
    }

    fun install(voice: CatalogueVoice) {
        if (state(voice.key) is InstallState.Downloading) return
        cancels[voice.key] = false
        update(voice.key, InstallState.Downloading(Progress(0, voice.totalBytes)))
        executor.execute {
            var lastPercent = -1
            val result = try {
                store.install(
                    voice,
                    Catalogue.FILES_BASE_URL,
                    onProgress = { progress ->
                        if (progress.percent != lastPercent) {
                            lastPercent = progress.percent
                            update(voice.key, InstallState.Downloading(progress))
                        }
                    },
                    cancelled = { cancels[voice.key] == true },
                )
                InstallState.Idle
            } catch (e: DownloadCancelledException) {
                InstallState.Idle
            } catch (e: IOException) {
                InstallState.Failed(e.message ?: e.javaClass.simpleName)
            }
            update(voice.key, result)
        }
    }

    fun cancel(key: String) {
        cancels[key] = true
    }

    fun remove(key: String, onDone: (IOException?) -> Unit) {
        executor.execute {
            val error = try {
                store.remove(key)
                null
            } catch (e: IOException) {
                e
            }
            main.post {
                onDone(error)
                notifyListeners()
            }
        }
    }

    private fun update(key: String, state: InstallState) {
        states[key] = state
        main.post { notifyListeners() }
    }

    private fun logSkipped(catalogue: Catalogue) {
        catalogue.skipped.forEach { (key, reason) -> Log.i(TAG, "skipped catalogue voice $key: $reason") }
    }

    private fun notifyListeners() {
        listeners.toList().forEach { it() }
    }

    private fun readCache(): Catalogue? = try {
        cacheFile.takeIf { it.isFile }?.let { Catalogue.parse(it.readText()) }
    } catch (e: IOException) {
        null
    } catch (e: CatalogueException) {
        null
    }

    /** Best effort: a failed write keeps the old cache and never discards the fresh catalogue. */
    private fun writeCache(text: String) {
        val tmp = File(cacheFile.path + ".tmp")
        try {
            cacheFile.parentFile?.mkdirs()
            tmp.writeText(text)
            if (!tmp.renameTo(cacheFile)) tmp.delete()
        } catch (e: IOException) {
            tmp.delete()
        }
    }

    companion object {
        const val LANGUAGE = "ku"
        private const val PREFS = "voices"
        private const val KEY_DEFAULT = "default_voice"
        private const val TAG = "DengjenVoices"

        @Volatile
        private var instance: VoiceRepository? = null

        fun get(context: Context): VoiceRepository =
            instance ?: synchronized(this) {
                instance ?: VoiceRepository(context.applicationContext).also { instance = it }
            }
    }
}

/** Reads a UTF-8 body, refusing one larger than [limit] bytes. */
internal fun java.io.InputStream.readBytesLimited(limit: Int): String {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        if (out.size() + read > limit) throw IOException("response exceeds $limit bytes")
        out.write(buffer, 0, read)
    }
    return out.toString(Charsets.UTF_8.name())
}
