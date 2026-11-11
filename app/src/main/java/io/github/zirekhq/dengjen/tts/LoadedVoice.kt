package io.github.zirekhq.dengjen.tts

import io.github.zirekhq.dengjen.android.AudioInfo
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

    /** Runs [block] with the voice for [key], loading it first if another voice is loaded. */
    fun <T> use(key: String, config: File, block: (Voice, AudioInfo) -> T): T = synchronized(lock) {
        val current = voice
        val audio = info
        val (loaded, loadedInfo) = if (current != null && audio != null && this.key == key) {
            current to audio
        } else {
            closeLocked()
            val fresh = Voice.load(config.path)
            val freshInfo = fresh.audioInfo()
            voice = fresh
            info = freshInfo
            this.key = key
            fresh to freshInfo
        }
        speaking = loaded
        try {
            block(loaded, loadedInfo)
        } finally {
            speaking = null
        }
    }

    /** Safe from any thread; stops a realtime synthesis and is a no-op otherwise. */
    fun cancel() {
        speaking?.cancel()
    }

    /** Waits for an utterance in progress, so call [cancel] and stop its handler first. */
    fun close() = synchronized(lock) { closeLocked() }

    private fun closeLocked() {
        voice?.close()
        voice = null
        info = null
        key = null
    }
}
