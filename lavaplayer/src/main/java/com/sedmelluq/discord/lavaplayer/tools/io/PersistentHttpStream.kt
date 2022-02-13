package com.sedmelluq.discord.lavaplayer.tools.io

import com.sedmelluq.discord.lavaplayer.tools.Units
import com.sedmelluq.lava.track.info.AudioTrackInfoBuilder
import com.sedmelluq.lava.track.info.AudioTrackInfoProvider
import mu.KotlinLogging
import org.apache.http.HttpHeaders
import org.apache.http.HttpResponse
import org.apache.http.HttpStatus
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpGet
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI

/**
 * Use an HTTP endpoint as a stream, where the connection resetting is handled gracefully by reopening the connection
 * and using a closed stream will just reopen the connection.
 *
 * @param httpInterface The HTTP interface to use for requests
 * @param contentUrl    The URL of the resource
 * @param contentLength The length of the resource in bytes
 */
open class PersistentHttpStream(
    private val httpInterface: HttpInterface,
    protected val contentUrl: URI,
    contentLength: Long?,
) : SeekableInputStream(contentLength ?: Units.CONTENT_LENGTH_UNKNOWN, MAX_SKIP_DISTANCE), AutoCloseable {
    companion object {
        private val log = KotlinLogging.logger { }
        private const val MAX_SKIP_DISTANCE = 512L * 1024L

        private fun validateStatusCode(response: HttpResponse, returnOnServerError: Boolean): Boolean {
            val statusCode = response.statusLine.statusCode
            if (returnOnServerError && statusCode >= HttpStatus.SC_INTERNAL_SERVER_ERROR) {
                return false
            } else if (!HttpClientTools.isSuccessWithContent(statusCode)) {
                throw RuntimeException("Not success status code: $statusCode")
            }

            return true
        }
    }

    private var lastStatusCode = 0

    private var currentContent: InputStream? = null

    private val connectRequest: HttpGet
        get() {
            val request = HttpGet(connectUrl)
            if (position > 0 && useHeadersForRange()) {
                request.setHeader(HttpHeaders.RANGE, "bytes=$position-")
            }

            return request
        }

    protected open val connectUrl: URI?
        get() = contentUrl

    override var position: Long = 0

    override val trackInfoProviders: List<AudioTrackInfoProvider>
        get() = if (currentResponse != null) listOf(createIceCastHeaderProvider()) else emptyList()

    /**
     * The current HTTP response, or null if one isn't currently open.
     */
    var currentResponse: CloseableHttpResponse? = null
        private set

    /**
     * Connect and return status code or return last status code if already connected. This causes the internal status
     * code checker to be disabled, so non-success status codes will be returned instead of being thrown as they would
     * be otherwise.
     *
     * @return The status code when connecting to the URL
     * @throws IOException On IO error
     */
    @Throws(IOException::class)
    fun checkStatusCode(): Int {
        connect(true)
        return lastStatusCode
    }

    protected open fun useHeadersForRange(): Boolean =
        true

    override fun read(): Int =
        read0(true)

    override fun read(b: ByteArray, off: Int, len: Int): Int =
        read0(b, off, len, true)

    override fun skip(n: Long): Long =
        skip0(n, true)

    override fun available(): Int =
        available0(true)

    @Synchronized
    override fun reset(): Unit =
        throw IOException("mark/reset not supported")

    override fun markSupported(): Boolean =
        false

    override fun canSeekHard(): Boolean =
        contentLength != Units.CONTENT_LENGTH_UNKNOWN

    override fun seekHard(position: Long) {
        close()
        this.position = position
    }

    override fun close() {
        if (currentResponse != null) {
            try {
                currentResponse!!.close()
            } catch (e: IOException) {
                log.debug(e) { "Failed to close response." }
            }

            currentResponse = null
            currentContent = null
        }
    }

    /**
     * Detach from the current connection, making sure not to close the connection when the stream is closed.
     */
    fun releaseConnection() {
        if (currentContent != null) {
            try {
                currentContent!!.close()
            } catch (e: IOException) {
                log.debug(e) { "Failed to close response stream." }
            }
        }

        currentResponse = null
        currentContent = null
    }

    private fun connect(skipStatusCheck: Boolean) {
        if (currentResponse != null) {
            return
        }

        for (i in 1 downTo 0) {
            if (attemptConnect(skipStatusCheck, i > 0)) {
                break
            }
        }
    }

    private fun attemptConnect(skipStatusCheck: Boolean, retryOnServerError: Boolean): Boolean {
        currentResponse = httpInterface.execute(connectRequest)
        lastStatusCode = currentResponse!!.statusLine.statusCode
        if (!skipStatusCheck && !validateStatusCode(currentResponse!!, retryOnServerError)) {
            return false
        }

        if (currentResponse!!.entity == null) {
            currentContent = EmptyInputStream.INSTANCE
            contentLength = 0
            return true
        }

        currentContent = BufferedInputStream(currentResponse!!.entity.content)
        if (contentLength == Units.CONTENT_LENGTH_UNKNOWN) {
            val header = currentResponse!!.getFirstHeader("Content-Length")
            if (header != null) {
                contentLength = header.value.toLong()
            }
        }

        return true
    }

    private fun handleNetworkException(exception: IOException, attemptReconnect: Boolean) {
        if (!attemptReconnect || !HttpClientTools.isRetryableNetworkException(exception)) {
            throw exception
        }

        close()
        log.debug(exception) { "Encountered retryable exception on url $contentUrl." }
    }

    private fun read0(attemptReconnect: Boolean): Int {
        connect(false)
        return try {
            val result = currentContent!!.read()
            if (result >= 0) {
                position++
            }

            result
        } catch (e: IOException) {
            handleNetworkException(e, attemptReconnect)
            read0(false)
        }
    }

    private fun read0(b: ByteArray, off: Int, len: Int, attemptReconnect: Boolean): Int {
        connect(false)
        return try {
            val result = currentContent!!.read(b, off, len)
            if (result >= 0) {
                position += result.toLong()
            }

            result
        } catch (e: IOException) {
            handleNetworkException(e, attemptReconnect)
            read0(b, off, len, false)
        }
    }

    private fun skip0(n: Long, attemptReconnect: Boolean): Long {
        connect(false)
        return try {
            val result = currentContent!!.skip(n)
            if (result >= 0) {
                position += result
            }

            result
        } catch (e: IOException) {
            handleNetworkException(e, attemptReconnect)
            skip0(n, false)
        }
    }

    private fun available0(attemptReconnect: Boolean): Int {
        connect(false)

        return try {
            currentContent!!.available()
        } catch (e: IOException) {
            handleNetworkException(e, attemptReconnect)
            available0(false)
        }
    }

    private fun createIceCastHeaderProvider(): AudioTrackInfoProvider {
        val builder = AudioTrackInfoBuilder.empty()
        builder.title = HttpClientTools.getHeaderValue(currentResponse, "icy-description")
            ?: HttpClientTools.getHeaderValue(currentResponse, "icy-url")
        builder.author = HttpClientTools.getHeaderValue(currentResponse, "icy-name")

        return builder
    }
}
