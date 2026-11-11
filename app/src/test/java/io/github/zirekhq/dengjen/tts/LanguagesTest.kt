package io.github.zirekhq.dengjen.tts

import android.speech.tts.TextToSpeech
import org.junit.Assert.assertEquals
import org.junit.Test

class LanguagesTest {
    @Test
    fun kurmanjiCodesAreRecognised() {
        listOf("ku", "kur", "kmr", "KUR").forEach {
            assertEquals(it, TextToSpeech.LANG_AVAILABLE, Languages.availability(it, null, installed = true))
        }
    }

    @Test
    fun turkeyIsTheCountryVariant() {
        assertEquals(TextToSpeech.LANG_COUNTRY_AVAILABLE, Languages.availability("kur", "TUR", installed = true))
        assertEquals(TextToSpeech.LANG_COUNTRY_AVAILABLE, Languages.availability("ku", "TR", installed = true))
        assertEquals(TextToSpeech.LANG_AVAILABLE, Languages.availability("kur", "IRQ", installed = true))
    }

    @Test
    fun missingVoiceAsksForData() =
        assertEquals(TextToSpeech.LANG_MISSING_DATA, Languages.availability("kur", "TUR", installed = false))

    @Test
    fun otherLanguagesAreNotSupported() {
        assertEquals(TextToSpeech.LANG_NOT_SUPPORTED, Languages.availability("eng", "USA", installed = true))
        assertEquals(TextToSpeech.LANG_NOT_SUPPORTED, Languages.availability(null, null, installed = true))
        assertEquals(TextToSpeech.LANG_NOT_SUPPORTED, Languages.availability("ckb", "IRQ", installed = true))
    }
}
