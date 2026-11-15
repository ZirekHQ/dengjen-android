package io.github.zirekhq.dengjen.tts

import io.github.zirekhq.dengjen.android.AudioInfo
import io.github.zirekhq.dengjen.android.DengjenException
import io.github.zirekhq.dengjen.android.Voice
import java.io.File

/**
 * Keeps one voice loaded, so each utterance does not reload the model. One lock covers using and
 * closing the voice, so the native voice is never released while it speaks.
 */
class LoadedVoice {
    private val lock = Any()
    private var key: String? = null
    private var voice: Voice? = null
    private var info: AudioInfo? = null

    @Volatile
    private var speaking: Voice? = null

    // Set by close, so a preload that runs after the service is destroyed does not load a voice
    // nobody will close.
    private var closed = false

    /** Runs [block] with the voice for [key], loading it first if another voice is loaded. */
    fun <T> use(key: String, config: File, block: (Voice, AudioInfo) -> T): T = synchronized(lock) {
        val (loaded, loadedInfo) = loadLocked(key, config)
        speaking = loaded
        try {
            block(loaded, loadedInfo)
        } finally {
            speaking = null
        }
    }

    /**
     * Loads the voice for [key] ahead of the first utterance, unless it is loaded already or [close]
     * has run. A [use] that arrives meanwhile waits for this load instead of starting its own.
     */
    fun preload(key: String, config: File) {
        synchronized(lock) {
            if (!closed) loadLocked(key, config)
        }
    }

    /**
     * Safe from any thread; stops a realtime synthesis and is a no-op otherwise. The binding's
     * HandleGuard serialises cancel against close and refuses a closed voice, so a cancel that races
     * [close] never reaches the freed native voice; it only throws, which is ignored here.
     */
    fun cancel() {
        try {
            speaking?.cancel()
        } catch (e: DengjenException) {
            // The voice was closed between reading it and cancelling: nothing is left to stop.
        }
    }

    /** Waits for an utterance in progress, so call [cancel] and stop its handler first. */
    fun close() = synchronized(lock) {
        closed = true
        closeLocked()
    }

    private fun loadLocked(key: String, config: File): Pair<Voice, AudioInfo> {
        val current = voice
        val audio = info
        if (current != null && audio != null && this.key == key) return current to audio
        closeLocked()
        val fresh = Voice.load(config.path)
        val freshInfo = fresh.audioInfo()
        voice = fresh
        info = freshInfo
        this.key = key
        return fresh to freshInfo
    }

    private fun closeLocked() {
        voice?.close()
        voice = null
        info = null
        key = null
    }
}
