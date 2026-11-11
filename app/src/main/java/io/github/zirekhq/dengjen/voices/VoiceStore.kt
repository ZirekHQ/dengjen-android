package io.github.zirekhq.dengjen.voices

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class DownloadCancelledException : IOException("download cancelled")

/** Bytes downloaded so far out of the voice's total. */
data class Progress(val done: Long, val total: Long) {
    val percent: Int get() = if (total <= 0) 0 else ((done * 100) / total).toInt()
}

/**
 * Installed voices on disk, in the layout the Linux voice manager uses: `piper/<key>/<key>.onnx.json`
 * beside `<key>.onnx`. Downloads land in `.staging/` and the finished directory is renamed into place,
 * so a voice is either fully installed or absent. A download that stops early is resumed next time.
 */
class VoiceStore(private val root: File, private val transport: Transport) {
    private val voicesDir get() = File(root, MODEL_TYPE)
    private val stagingDir get() = File(root, ".staging/$MODEL_TYPE")
    private val oldDir get() = File(root, ".old/$MODEL_TYPE")
    private val trashDir get() = File(root, ".trash/$MODEL_TYPE")

    fun configFile(key: String): File = File(File(voicesDir, key), "$key.onnx.json")

    fun isInstalled(key: String): Boolean =
        configFile(key).isFile && File(File(voicesDir, key), "$key.onnx").isFile

    fun installed(): List<String> =
        voicesDir.listFiles().orEmpty().filter { it.isDirectory && isInstalled(it.name) }.map { it.name }.sorted()

    @Synchronized
    @Throws(IOException::class)
    fun install(
        voice: CatalogueVoice,
        baseUrl: String,
        onProgress: (Progress) -> Unit,
        cancelled: () -> Boolean,
    ) {
        val staging = File(stagingDir, voice.key)
        if (!staging.isDirectory && !staging.mkdirs()) throw IOException("cannot create $staging")
        var done = 0L
        for (file in voice.files) {
            val target = File(staging, file.name)
            if (!(target.isFile && target.length() == file.size && file.digest.matches(target))) {
                val before = done
                fetch(file, "$baseUrl/${file.path}", target, cancelled) { bytes ->
                    onProgress(Progress(before + bytes, voice.totalBytes))
                }
            }
            done += file.size
            onProgress(Progress(done, voice.totalBytes))
            checkCancelled(cancelled)
        }
        swapIn(voice.key, staging)
    }

    @Synchronized
    @Throws(IOException::class)
    fun remove(key: String) {
        val dir = File(voicesDir, key)
        if (!dir.exists()) return
        val parked = File(trashDir, "$key-${System.nanoTime()}")
        parked.parentFile?.mkdirs()
        if (!dir.renameTo(parked)) throw IOException("cannot remove $dir")
        parked.deleteRecursively()
    }

    private fun checkCancelled(cancelled: () -> Boolean) {
        if (cancelled()) throw DownloadCancelledException()
    }

    private fun fetch(file: VoiceFile, url: String, target: File, cancelled: () -> Boolean, onBytes: (Long) -> Unit) {
        // The partial file is named after the digest, so a part from an older catalogue is not resumed.
        val part = File(target.parentFile, "${file.name}.${file.digest.hex.take(PART_TAG)}.part")
        target.parentFile?.listFiles { f -> f.name.startsWith("${file.name}.") && f.name.endsWith(".part") && f != part }
            ?.forEach { it.delete() }
        if (part.length() > file.size) part.delete()
        val offset = part.length()
        if (offset < file.size || offset == 0L) {
            transport.open(url, offset).use { response ->
                val append = response.partial && offset > 0
                copy(response, part, append, file.size, cancelled, onBytes)
            }
        }
        if (part.length() != file.size) {
            throw IOException("${file.path} ended early (${part.length()} of ${file.size} bytes)")
        }
        if (!file.digest.matches(part)) {
            part.delete()
            throw IOException("${file.path} does not match its catalogue digest")
        }
        if (!part.renameTo(target)) throw IOException("cannot move ${file.path} into place")
    }

    private fun copy(
        response: Response,
        part: File,
        append: Boolean,
        limit: Long,
        cancelled: () -> Boolean,
        onBytes: (Long) -> Unit,
    ) {
        FileOutputStream(part, append).use { out ->
            var written = if (append) part.length() else 0L
            val buffer = ByteArray(BUFFER)
            while (true) {
                checkCancelled(cancelled)
                val read = response.body.read(buffer)
                if (read < 0) break
                if (written + read > limit) {
                    out.close()
                    part.delete()
                    throw IOException("${part.name} is larger than its catalogue size")
                }
                out.write(buffer, 0, read)
                written += read
                onBytes(written)
            }
        }
    }

    private fun swapIn(key: String, staging: File) {
        val target = File(voicesDir, key)
        val old = File(oldDir, key)
        old.deleteRecursively()
        voicesDir.mkdirs()
        if (target.exists()) {
            old.parentFile?.mkdirs()
            if (!target.renameTo(old)) throw IOException("cannot replace $target")
        }
        if (!staging.renameTo(target)) {
            old.renameTo(target)
            throw IOException("cannot install $key")
        }
        old.deleteRecursively()
    }

    companion object {
        const val MODEL_TYPE = "piper"
        private const val BUFFER = 64 * 1024
        private const val PART_TAG = 16
    }
}
