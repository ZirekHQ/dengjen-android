package io.github.zirekhq.dengjen.tts

import android.media.AudioFormat
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.util.Log
import io.github.zirekhq.dengjen.android.AudioInfo
import io.github.zirekhq.dengjen.android.DengjenException
import io.github.zirekhq.dengjen.android.EventType
import io.github.zirekhq.dengjen.android.SynthesisMode
import io.github.zirekhq.dengjen.android.SynthesisParams
import io.github.zirekhq.dengjen.android.Voice
import io.github.zirekhq.dengjen.voices.VoiceRepository

/** The system TTS engine that TalkBack and other apps call. */
class DengjenTtsService : TextToSpeechService() {
    private lateinit var voices: VoiceRepository
    private val loaded = LoadedVoice()

    @Volatile
    private var stopped = false

    override fun onCreate() {
        voices = VoiceRepository.get(this)
        super.onCreate()
    }

    override fun onDestroy() {
        stopped = true
        loaded.cancel()
        loaded.close()
        super.onDestroy()
    }

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int =
        Languages.availability(lang, country, voices.installed().isNotEmpty())

    override fun onGetLanguage(): Array<String> = arrayOf(Languages.ISO3_LANGUAGE, Languages.ISO3_COUNTRY, "")

    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int =
        onIsLanguageAvailable(lang, country, variant)

    override fun onGetVoices(): List<android.speech.tts.Voice> = voices.installed().map(::systemVoice)

    override fun onIsValidVoiceName(voiceName: String?): Int =
        if (voiceName != null && voices.store.isInstalled(voiceName)) TextToSpeech.SUCCESS else TextToSpeech.ERROR

    override fun onLoadVoice(voiceName: String?): Int = onIsValidVoiceName(voiceName)

    override fun onGetDefaultVoiceNameFor(lang: String?, country: String?, variant: String?): String? =
        if (Languages.isKurmanji(lang)) voices.defaultVoice() else null

    override fun onStop() {
        stopped = true
        loaded.cancel()
    }

    override fun onSynthesizeText(request: SynthesisRequest, callback: SynthesisCallback) {
        stopped = false
        val key = request.voiceName?.takeIf { voices.store.isInstalled(it) } ?: voices.defaultVoice()
        if (key == null) {
            // Prompts callers such as TalkBack to offer the voice download.
            callback.error(TextToSpeech.ERROR_NOT_INSTALLED_YET)
            return
        }
        try {
            speak(key, request, callback)
        } catch (e: DengjenException) {
            Log.w(TAG, "synthesis failed", e)
            callback.error(TextToSpeech.ERROR_SYNTHESIS)
        }
    }

    private fun speak(key: String, request: SynthesisRequest, callback: SynthesisCallback) =
        loaded.use(key, voices.store.configFile(key)) { voice, info -> speak(voice, info, request, callback) }

    private fun speak(voice: Voice, info: AudioInfo, request: SynthesisRequest, callback: SynthesisCallback) {
        if (info.sampleWidth() != 2) {
            Log.w(TAG, "unsupported sample width ${info.sampleWidth()}")
            callback.error(TextToSpeech.ERROR_SYNTHESIS)
            return
        }
        if (callback.start(info.sampleRate(), AudioFormat.ENCODING_PCM_16BIT, info.numChannels()) != TextToSpeech.SUCCESS) {
            return
        }
        val text = request.charSequenceText?.toString().orEmpty()
        if (text.isBlank()) {
            callback.done()
            return
        }
        val params = SynthesisParams(
            SynthesisMode.LAZY,
            Prosody.engineRate(request.speechRate),
            Prosody.FULL_VOLUME,
            Prosody.enginePitch(request.pitch),
            0,
        )
        var failed = false
        voice.speak(text, params) { event ->
            when (event.type()) {
                EventType.SPEECH -> !stopped && deliver(event.data(), callback)
                EventType.ERROR -> {
                    Log.w(TAG, "synthesis error", event.error())
                    failed = true
                    false
                }
                else -> !stopped
            }
        }
        // After onStop the framework has already finished the request, so nothing more is reported.
        when {
            stopped -> Unit
            failed -> callback.error(TextToSpeech.ERROR_SYNTHESIS)
            else -> callback.done()
        }
    }

    /** Hands PCM to the framework in pieces no larger than it accepts. Returns false to stop synthesis. */
    private fun deliver(pcm: ByteArray?, callback: SynthesisCallback): Boolean {
        if (pcm == null) return true
        val max = callback.maxBufferSize
        var offset = 0
        while (offset < pcm.size) {
            if (stopped) return false
            val length = minOf(max, pcm.size - offset)
            if (callback.audioAvailable(pcm, offset, length) != TextToSpeech.SUCCESS) return false
            offset += length
        }
        return true
    }

    private fun systemVoice(key: String) = android.speech.tts.Voice(
        key,
        Languages.LOCALE,
        android.speech.tts.Voice.QUALITY_HIGH,
        android.speech.tts.Voice.LATENCY_NORMAL,
        false,
        emptySet(),
    )

    private companion object {
        const val TAG = "DengjenTts"
    }
}
