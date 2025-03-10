package com.sedmelluq.discord.lavaplayer.source.youtube

import com.sedmelluq.discord.lavaplayer.tools.extensions.toRuntimeException
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream
import org.apache.http.client.utils.URIBuilder
import java.net.URI
import java.net.URISyntaxException

/**
 * A persistent HTTP stream implementation that uses the range parameter instead of HTTP headers for specifying
 * the start position at which to start reading on a new connection.
 *
 * @param httpInterface The HTTP interface to use for requests
 * @param contentUrl    The URL of the resource
 * @param contentLength The length of the resource in bytes
 */
class YoutubePersistentHttpStream(
    httpInterface: HttpInterface,
    contentUrl: URI,
    contentLength: Long
) : PersistentHttpStream(httpInterface, contentUrl, contentLength) {
    override val connectUrl: URI?
        get() {
            if (position < 0) {
                return contentUrl
            }

            try {
                return URIBuilder(contentUrl)
                    .addParameter("range", "$position-$contentLength")
                    .build()
            } catch (e: URISyntaxException) {
                throw e.toRuntimeException()
            }
        }

    override fun useHeadersForRange() =
        false

    override fun canSeekHard() =
        true
}
