package io.github.zirekhq.dengjen.voices

import java.security.MessageDigest

internal fun md5(bytes: ByteArray): String =
    MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }

internal fun catalogueJson(key: String, family: String, files: Map<String, ByteArray>): String {
    val fileEntries = files.entries.joinToString(",") { (path, bytes) ->
        """"$path": {"size_bytes": ${bytes.size}, "md5_digest": "${md5(bytes)}"}"""
    }
    return """
        "$key": {
          "key": "$key",
          "name": "${key.substringAfter('-').substringBefore('-')}",
          "language": {"code": "${family}_TR", "family": "$family", "name_english": "x"},
          "quality": "medium",
          "num_speakers": 1,
          "files": {$fileEntries}
        }
    """.trimIndent()
}
