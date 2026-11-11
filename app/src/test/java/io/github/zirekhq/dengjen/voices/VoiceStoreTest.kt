package io.github.zirekhq.dengjen.voices

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

class VoiceStoreTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val key = "ku_TR-berfin-medium"
    private val model = ByteArray(1000) { (it % 251).toByte() }
    private val config = """{"espeak": {"voice": "ku"}}""".toByteArray()
    private val files = mapOf(
        "ku/ku_TR/berfin/medium/$key.onnx" to model,
        "ku/ku_TR/berfin/medium/$key.onnx.json" to config,
    )
    private val voice = Catalogue.parse("{${catalogueJson(key, "ku", files)}}").get(key)!!

    /** Serves [files] by URL; `cut` truncates a body to simulate a dropped connection. */
    private class FakeTransport(private val files: Map<String, ByteArray>) : Transport {
        val requests = mutableListOf<Pair<String, Long>>()
        var cut: Int? = null
        var honourRange = true

        override fun open(url: String, offset: Long): Response {
            requests += url to offset
            val bytes = files[url.removePrefix("$BASE/")] ?: throw IOException("404 $url")
            val start = if (honourRange) offset.toInt() else 0
            val end = cut?.let { minOf(bytes.size, start + it) } ?: bytes.size
            return Response(honourRange && offset > 0, ByteArrayInputStream(bytes.copyOfRange(start, end)))
        }
    }

    private fun store(transport: Transport) = VoiceStore(temp.root, transport)

    @Test
    fun installsIntoTheLinuxLayout() {
        val store = store(FakeTransport(files))
        val progress = mutableListOf<Progress>()
        store.install(voice, BASE, { progress += it }, { false })
        assertTrue(store.isInstalled(key))
        assertEquals(listOf(key), store.installed())
        assertEquals(File(temp.root, "piper/$key/$key.onnx.json"), store.configFile(key))
        assertTrue(model.contentEquals(File(temp.root, "piper/$key/$key.onnx").readBytes()))
        assertEquals(Progress(voice.totalBytes, voice.totalBytes), progress.last())
        assertFalse(File(temp.root, ".staging/piper/$key").exists())
    }

    @Test
    fun resumesADownloadThatStoppedEarly() {
        val transport = FakeTransport(files).apply { cut = 400 }
        val store = store(transport)
        assertThrows(IOException::class.java) { store.install(voice, BASE, {}, { false }) }
        assertFalse(store.isInstalled(key))
        transport.cut = null
        store.install(voice, BASE, {}, { false })
        assertTrue(store.isInstalled(key))
        assertEquals(400L, transport.requests.last { it.first.endsWith(".onnx") }.second)
        assertTrue(model.contentEquals(File(temp.root, "piper/$key/$key.onnx").readBytes()))
    }

    @Test
    fun restartsWhenTheServerIgnoresTheRange() {
        val transport = FakeTransport(files).apply { cut = 400 }
        val store = store(transport)
        assertThrows(IOException::class.java) { store.install(voice, BASE, {}, { false }) }
        transport.cut = null
        transport.honourRange = false
        store.install(voice, BASE, {}, { false })
        assertTrue(model.contentEquals(File(temp.root, "piper/$key/$key.onnx").readBytes()))
    }

    @Test
    fun rejectsAFileThatFailsItsDigest() {
        val tampered = files + ("ku/ku_TR/berfin/medium/$key.onnx" to ByteArray(1000))
        val store = store(FakeTransport(tampered))
        val error = assertThrows(IOException::class.java) { store.install(voice, BASE, {}, { false }) }
        assertTrue(error.message!!.contains("digest"))
        assertFalse(store.isInstalled(key))
    }

    @Test
    fun rejectsAFileLargerThanTheCatalogueSays() {
        val bigger = files + ("ku/ku_TR/berfin/medium/$key.onnx" to ByteArray(2000))
        val store = store(FakeTransport(bigger))
        assertThrows(IOException::class.java) { store.install(voice, BASE, {}, { false }) }
        assertFalse(store.isInstalled(key))
    }

    @Test
    fun cancellingStopsTheDownload() {
        val store = store(FakeTransport(files))
        assertThrows(DownloadCancelledException::class.java) { store.install(voice, BASE, {}, { true }) }
        assertFalse(store.isInstalled(key))
    }

    @Test
    fun reinstallReplacesAndRemoveDeletes() {
        val store = store(FakeTransport(files))
        store.install(voice, BASE, {}, { false })
        store.install(voice, BASE, {}, { false })
        assertTrue(store.isInstalled(key))
        store.remove(key)
        assertFalse(store.isInstalled(key))
        assertEquals(emptyList<String>(), store.installed())
        store.remove(key)
    }

    private companion object {
        const val BASE = "https://files.test"
    }
}
