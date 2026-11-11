package io.github.zirekhq.dengjen.voices

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class CatalogueTest {
    private val ku = catalogueJson(
        "ku_TR-berfin-medium",
        "ku",
        mapOf(
            "ku/ku_TR/berfin/medium/ku_TR-berfin-medium.onnx" to ByteArray(3) { 1 },
            "ku/ku_TR/berfin/medium/ku_TR-berfin-medium.onnx.json" to "{}".toByteArray(),
        ),
    )
    private val en = catalogueJson("en_US-amy-low", "en", mapOf("en/amy/en_US-amy-low.onnx" to ByteArray(1)))

    @Test
    fun parsesVoicesAndFiltersByLanguage() {
        val catalogue = Catalogue.parse("{$ku,$en}")
        assertEquals(2, catalogue.size)
        val voices = catalogue.byLanguage("ku")
        assertEquals(listOf("ku_TR-berfin-medium"), voices.map { it.key })
        assertEquals(5L, voices.single().totalBytes)
        assertEquals(listOf("ku_TR-berfin-medium.onnx", "ku_TR-berfin-medium.onnx.json"), voices.single().files.map { it.name })
        assertEquals(1, catalogue.byLanguage("ku-TR").size)
        assertNull(catalogue.get("missing"))
    }

    /** Parses [bad] next to the valid Kurmanji voice and returns why [key] was skipped. */
    private fun skipReason(bad: String, key: String): String? {
        val catalogue = Catalogue.parse("{$ku,$bad}")
        assertEquals(listOf("ku_TR-berfin-medium"), catalogue.byLanguage("ku").map { it.key })
        assertNull(catalogue.get(key))
        return catalogue.skipped[key]
    }

    @Test
    fun skipsATraversingPath() {
        val bad = catalogueJson("ku_TR-x-medium", "ku", mapOf("ku/../../etc/x.onnx" to ByteArray(1)))
        assertEquals("unsafe file path ku/../../etc/x.onnx", skipReason(bad, "ku_TR-x-medium"))
    }

    @Test
    fun skipsAnAbsolutePath() {
        val bad = catalogueJson("ku_TR-x-medium", "ku", mapOf("/etc/x.onnx" to ByteArray(1)))
        assertEquals("unsafe file path /etc/x.onnx", skipReason(bad, "ku_TR-x-medium"))
    }

    @Test
    fun skipsAFileWithoutADigest() {
        val bad = """"k": {"key": "k", "name": "n", "language": {"code": "ku_TR"}, "quality": "low",
            "files": {"a/k.onnx": {"size_bytes": 1}}}"""
        assertEquals("a/k.onnx has no digest", skipReason(bad, "k"))
    }

    @Test
    fun skipsAMismatchedInnerKey() {
        val bad = en.replaceFirst("\"key\": \"en_US-amy-low\"", "\"key\": \"other\"")
        assertEquals("does not match its inner key other", skipReason(bad, "en_US-amy-low"))
    }

    @Test
    fun skipsTwoFilesWithTheSameName() {
        val bad = catalogueJson("ku_TR-x-medium", "ku", mapOf("a/x.onnx" to ByteArray(1), "b/x.onnx" to ByteArray(1)))
        assertEquals("b/x.onnx collides with another file or a reserved name", skipReason(bad, "ku_TR-x-medium"))
    }

    @Test
    fun skipsAVoiceThatIsNotAnObject() {
        assertEquals("not an object", skipReason(""""k": 1""", "k"))
    }

    @Test
    fun skipsANonAsciiKey() {
        // The live catalogue has pt_PT-tugão-medium; it must not hide the Kurmanji voice.
        val bad = catalogueJson("pt_PT-tugão-medium", "pt", mapOf("pt/tugao.onnx" to ByteArray(1)))
        assertEquals("unsafe voice key", skipReason(bad, "pt_PT-tugão-medium"))
    }

    @Test
    fun skipsKeysThatDifferOnlyInCase() {
        val upper = catalogueJson("EN_US-amy-low", "en", mapOf("en/upper/EN_US-amy-low.onnx" to ByteArray(1)))
        val catalogue = Catalogue.parse("{$ku,$en,$upper}")
        assertEquals(1, catalogue.size)
        assertEquals(setOf("en_US-amy-low", "EN_US-amy-low"), catalogue.skipped.keys)
    }

    @Test
    fun displaysTheNameInWords() {
        val voice = Catalogue.parse("{$ku}").get("ku_TR-berfin-medium")!!
        assertEquals("Berfin", voice.displayName)
        val two = Catalogue.parse("{${ku.replace("\"name\": \"berfin\"", "\"name\": \"berfin_renas\"")}}")
        assertEquals("Berfin Renas", two.get("ku_TR-berfin-medium")!!.displayName)
    }

    @Test
    fun rejectsMalformedJson() {
        assertThrows(CatalogueException::class.java) { Catalogue.parse("{") }
    }

    @Test
    fun prefersSha256OverMd5() {
        val sha = "a".repeat(64)
        val json = """{"k": {"key": "k", "name": "n", "language": {"code": "ku_TR"}, "quality": "low",
            "files": {"a/k.onnx": {"size_bytes": 1, "md5_digest": "${"b".repeat(32)}", "sha256_digest": "$sha"}}}}"""
        assertEquals(Digest.sha256(sha), Catalogue.parse(json).get("k")!!.files.single().digest)
    }
}
