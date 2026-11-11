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

    @Test
    fun rejectsATraversingPath() {
        val bad = catalogueJson("ku_TR-x-medium", "ku", mapOf("ku/../../etc/x.onnx" to ByteArray(1)))
        assertThrows(CatalogueException::class.java) { Catalogue.parse("{$bad}") }
    }

    @Test
    fun rejectsAnAbsolutePath() {
        val bad = catalogueJson("ku_TR-x-medium", "ku", mapOf("/etc/x.onnx" to ByteArray(1)))
        assertThrows(CatalogueException::class.java) { Catalogue.parse("{$bad}") }
    }

    @Test
    fun rejectsAFileWithoutADigest() {
        val bad = """{"k": {"key": "k", "name": "n", "language": {"code": "ku_TR"}, "quality": "low",
            "files": {"a/k.onnx": {"size_bytes": 1}}}}"""
        assertThrows(CatalogueException::class.java) { Catalogue.parse(bad) }
    }

    @Test
    fun rejectsAMismatchedInnerKey() {
        val bad = ku.replaceFirst("\"key\": \"ku_TR-berfin-medium\"", "\"key\": \"other\"")
        assertThrows(CatalogueException::class.java) { Catalogue.parse("{$bad}") }
    }

    @Test
    fun rejectsTwoFilesWithTheSameName() {
        val bad = catalogueJson("ku_TR-x-medium", "ku", mapOf("a/x.onnx" to ByteArray(1), "b/x.onnx" to ByteArray(1)))
        assertThrows(CatalogueException::class.java) { Catalogue.parse("{$bad}") }
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
