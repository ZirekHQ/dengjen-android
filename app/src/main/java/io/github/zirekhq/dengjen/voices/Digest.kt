package io.github.zirekhq.dengjen.voices

import java.io.File
import java.security.MessageDigest
import java.util.Locale

/** A file digest from the catalogue: SHA-256 when the catalogue has one, otherwise MD5. */
data class Digest(val algorithm: String, val hex: String) {
    fun isWellFormed(): Boolean = hex.length == hexLength && hex.all { it in '0'..'9' || it in 'a'..'f' }

    private val hexLength: Int get() = if (algorithm == SHA256) 64 else 32

    fun matches(file: File): Boolean = of(file) == hex

    private fun of(file: File): String {
        val md = MessageDigest.getInstance(algorithm)
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                md.update(buffer, 0, read)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val SHA256 = "SHA-256"
        private const val MD5 = "MD5"
        private const val BUFFER = 64 * 1024

        fun sha256(hex: String) = Digest(SHA256, hex.lowercase(Locale.ROOT))
        fun md5(hex: String) = Digest(MD5, hex.lowercase(Locale.ROOT))
    }
}
