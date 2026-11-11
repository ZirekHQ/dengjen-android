package io.github.zirekhq.dengjen.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import io.github.zirekhq.dengjen.R
import io.github.zirekhq.dengjen.tts.Languages

/** Answers `ACTION_GET_SAMPLE_TEXT`, which the system settings use for "Listen to an example". */
class SampleTextActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val language = intent.getStringExtra("language")
        if (Languages.isKurmanji(language)) {
            setResult(TextToSpeech.LANG_AVAILABLE, Intent().putExtra(EXTRA_SAMPLE_TEXT, getString(R.string.sample_text)))
        } else {
            setResult(TextToSpeech.LANG_NOT_SUPPORTED)
        }
        finish()
    }

    private companion object {
        /** The framework reads this key; the constant is hidden in the SDK. */
        const val EXTRA_SAMPLE_TEXT = "sampleText"
    }
}
