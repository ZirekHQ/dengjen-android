package io.github.zirekhq.dengjen.voices

import org.json.JSONException
import org.json.JSONObject
import java.util.Locale

/** One file of a catalogue voice, with the digest it must match. */
data class VoiceFile(val path: String, val size: Long, val digest: Digest) {
    val name: String get() = path.substringAfterLast('/')
}

data class CatalogueVoice(
    val key: String,
    val name: String,
    val languageCode: String,
    val languageFamily: String,
    val quality: String,
    val files: List<VoiceFile>,
) {
    val totalBytes: Long get() = files.sumOf { it.size }

    /** The catalogue name as people read it: `berfin_renas` becomes "Berfin Renas". */
    val displayName: String
        get() = name.split('_').filter { it.isNotEmpty() }
            .joinToString(" ") { part -> part.replaceFirstChar { it.titlecase(Locale.ROOT) } }
            .ifEmpty { key }
}

class CatalogueException(message: String) : Exception(message)

/**
 * The Piper `voices.json` catalogue, the same one `dengjen voices install` reads on Linux. A voice
 * with an unsafe key or path, a missing digest or a name clash is left out and listed in [skipped],
 * so one bad entry upstream (or a key this app does not accept, such as a non-ASCII one) cannot hide
 * every other voice. Only a catalogue that is too big or is not a JSON object is rejected whole.
 */
class Catalogue private constructor(
    private val voices: Map<String, CatalogueVoice>,
    /** Voices left out, by key, with the reason. */
    val skipped: Map<String, String>,
) {
    fun get(key: String): CatalogueVoice? = voices[key]

    /** Voices whose language code or family matches, such as `ku` or `ku_TR`. */
    fun byLanguage(language: String): List<CatalogueVoice> {
        val query = language.replace('-', '_')
        return voices.values.filter {
            it.languageCode.equals(query, ignoreCase = true) ||
                it.languageFamily.equals(query, ignoreCase = true)
        }
    }

    val size: Int get() = voices.size

    companion object {
        const val MAX_BYTES = 4 * 1024 * 1024
        const val URL = "https://huggingface.co/rhasspy/piper-voices/raw/main/voices.json"
        const val FILES_BASE_URL = "https://huggingface.co/rhasspy/piper-voices/resolve/main"

        private val KEY = Regex("[A-Za-z0-9][A-Za-z0-9_.-]*")
        private const val RESERVED_NAME = "voice.json"

        fun parse(text: String): Catalogue {
            if (text.length > MAX_BYTES) throw CatalogueException("catalogue exceeds the size limit")
            val root = try {
                JSONObject(text)
            } catch (e: JSONException) {
                throw CatalogueException(e.message ?: "malformed catalogue")
            }
            val skipped = LinkedHashMap<String, String>()
            val parsed = LinkedHashMap<String, CatalogueVoice>()
            for (key in root.keys()) {
                try {
                    val json = root.optJSONObject(key) ?: throw CatalogueException("not an object")
                    parsed[key] = parseVoice(key, json)
                } catch (e: CatalogueException) {
                    skipped[key] = e.message ?: "invalid voice"
                }
            }
            // Keys name directories, so two that differ only in case would share one on some file systems.
            parsed.keys.groupBy { it.lowercase(Locale.ROOT) }.values.filter { it.size > 1 }.flatten().forEach {
                parsed.remove(it)
                skipped[it] = "another voice key differs only in case"
            }
            return Catalogue(parsed, skipped)
        }

        private fun parseVoice(key: String, json: JSONObject): CatalogueVoice = try {
            val inner = json.getString("key")
            if (inner != key) throw CatalogueException("does not match its inner key $inner")
            if (!KEY.matches(key) || key.contains("..")) throw CatalogueException("unsafe voice key")
            val language = json.getJSONObject("language")
            val filesJson = json.getJSONObject("files")
            val files = filesJson.keys().asSequence().sorted().map { parseFile(it, filesJson.getJSONObject(it)) }.toList()
            checkNames(files)
            CatalogueVoice(
                key = key,
                name = json.getString("name"),
                languageCode = language.getString("code"),
                languageFamily = language.optString("family"),
                quality = json.getString("quality"),
                files = files,
            )
        } catch (e: JSONException) {
            throw CatalogueException(e.message ?: "malformed voice")
        }

        private fun parseFile(path: String, json: JSONObject): VoiceFile {
            checkPath(path)
            val size = json.getLong("size_bytes")
            if (size < 0) throw CatalogueException("$path has a negative size")
            val digest = json.optString("sha256_digest").takeIf { it.isNotEmpty() }?.let { Digest.sha256(it) }
                ?: json.optString("md5_digest").takeIf { it.isNotEmpty() }?.let { Digest.md5(it) }
                ?: throw CatalogueException("$path has no digest")
            if (!digest.isWellFormed()) throw CatalogueException("$path has a malformed digest")
            return VoiceFile(path, size, digest)
        }

        private fun checkPath(path: String) {
            val parts = path.split('/')
            val unsafe = path.isEmpty() || path.startsWith("/") || path.contains('\\') ||
                parts.any { it.isEmpty() || it == "." || it == ".." }
            if (unsafe) throw CatalogueException("unsafe file path $path")
        }

        private fun checkNames(files: List<VoiceFile>) {
            val seen = HashSet<String>()
            for (file in files) {
                val name = file.name.lowercase(Locale.ROOT)
                if (name == RESERVED_NAME || !seen.add(name)) {
                    throw CatalogueException("${file.path} collides with another file or a reserved name")
                }
            }
        }
    }
}
