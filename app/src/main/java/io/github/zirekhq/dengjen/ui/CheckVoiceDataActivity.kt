package io.github.zirekhq.dengjen.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import io.github.zirekhq.dengjen.tts.Languages
import io.github.zirekhq.dengjen.voices.VoiceRepository

/** Answers `ACTION_CHECK_TTS_DATA`: Kurmanji is available once a voice is installed. */
class CheckVoiceDataActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val language = "${Languages.ISO3_LANGUAGE}-${Languages.ISO3_COUNTRY}"
        val installed = VoiceRepository.get(this).installed().isNotEmpty()
        val available = if (installed) arrayListOf(language) else arrayListOf()
        val missing = if (installed) arrayListOf() else arrayListOf(language)
        val data = Intent()
            .putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES, available)
            .putStringArrayListExtra(TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES, missing)
        setResult(
            if (installed) TextToSpeech.Engine.CHECK_VOICE_DATA_PASS else TextToSpeech.Engine.CHECK_VOICE_DATA_FAIL,
            data,
        )
        finish()
    }
}
