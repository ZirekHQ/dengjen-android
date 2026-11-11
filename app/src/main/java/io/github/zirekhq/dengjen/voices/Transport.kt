package io.github.zirekhq.dengjen.voices

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** A response body. `partial` is true when the server honoured the range request. */
class Response(val partial: Boolean, val body: InputStream) : AutoCloseable {
    override fun close() = body.close()
}

/** Fetches a URL from a byte offset. Kept behind an interface so tests need no network. */
fun interface Transport {
    @Throws(IOException::class)
    fun open(url: String, offset: Long): Response
}

class HttpTransport : Transport {
    override fun open(url: String, offset: Long): Response {
        if (!url.startsWith("https://")) throw IOException("refusing a non-https URL: $url")
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.instanceFollowRedirects = true
        if (offset > 0) connection.setRequestProperty("Range", "bytes=$offset-")
        val status = connection.responseCode
        if (status != HttpURLConnection.HTTP_OK && status != HttpURLConnection.HTTP_PARTIAL) {
            connection.disconnect()
            throw IOException("HTTP $status for $url")
        }
        return Response(status == HttpURLConnection.HTTP_PARTIAL, connection.inputStream)
    }

    private companion object {
        const val TIMEOUT_MS = 30_000
    }
}
