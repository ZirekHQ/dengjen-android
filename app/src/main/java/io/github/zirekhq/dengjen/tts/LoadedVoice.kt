package io.github.zirekhq.dengjen.tts

import io.github.zirekhq.dengjen.android.AudioInfo
import io.github.zirekhq.dengjen.android.Voice
import java.io.File

/**
 * Keeps one voice loaded, so each utterance does not reload the model. Used only from the
 * service's synthesis thread, except [cancel].
 */
class LoadedVoice {
    private var key: String? = null
    private var voice: Voice? = null
    private var info: AudioInfo? = null

    fun get(key: String, config: File): Pair<Voice, AudioInfo> {
        val current = voice
        if (current != null && this.key == key) return current to info!!
        close()
        val loaded = Voice.load(config.path)
        val audio = loaded.audioInfo()
        voice = loaded
        info = audio
        this.key = key
        return loaded to audio
    }

    @Volatile
    private var speaking: Voice? = null

    fun markSpeaking(voice: Voice?) {
        speaking = voice
    }

    /** Safe from any thread; stops a realtime synthesis and is a no-op otherwise. */
    fun cancel() {
        speaking?.cancel()
    }

    fun close() {
        voice?.close()
        voice = null
        info = null
        key = null
    }
}
