package io.github.zirekhq.dengjen.tts

import android.speech.tts.TextToSpeech
import java.util.Locale

/** The languages the engine answers for. Only Kurmanji: other languages need eSpeak data (ZirekHQ/dengjen#541). */
object Languages {
    const val ISO3_LANGUAGE = "kur"
    const val ISO3_COUNTRY = "TUR"

    /** Codes Android apps use for Kurmanji: ISO 639-1, 639-2 and the 639-3 code for Northern Kurdish. */
    private val LANGUAGE_CODES = setOf("ku", "kur", "kmr")
    private val COUNTRY_CODES = setOf("tr", "tur")

    val LOCALE: Locale = Locale.Builder().setLanguage("ku").setRegion("TR").build()

    fun isKurmanji(language: String?): Boolean =
        language != null && language.lowercase(Locale.ROOT) in LANGUAGE_CODES

    /**
     * The `TextToSpeech.LANG_*` answer for a language, country and variant, given whether a voice is
     * installed.
     */
    fun availability(language: String?, country: String?, installed: Boolean): Int = when {
        !isKurmanji(language) -> TextToSpeech.LANG_NOT_SUPPORTED
        !installed -> TextToSpeech.LANG_MISSING_DATA
        country.isNullOrEmpty() -> TextToSpeech.LANG_AVAILABLE
        country.lowercase(Locale.ROOT) in COUNTRY_CODES -> TextToSpeech.LANG_COUNTRY_AVAILABLE
        else -> TextToSpeech.LANG_AVAILABLE
    }
}
