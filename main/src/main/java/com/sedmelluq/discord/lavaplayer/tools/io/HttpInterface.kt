package com.sedmelluq.discord.lavaplayer.tools.io

import com.sedmelluq.discord.lavaplayer.tools.extensions.rethrow
import com.sedmelluq.discord.lavaplayer.tools.http.HttpContextFilter
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.impl.client.CloseableHttpClient
import java.io.Closeable
import java.io.IOException
import java.net.URI

/**
 * An HTTP interface for performing HTTP requests in one specific thread. This also means it is not thread safe and should
 * not be used in a thread it was not obtained in. For multi-thread use [HttpInterfaceManager.get],
 * should be called in each thread separately.
 *
 * @param httpClient  The http client instance used.
 * @param context     The http context instance used.
 * @param ownedClient True if the client should be closed when this instance is closed.
 * @param filter
 */
class HttpInterface(
    /**
     * Http client instance used by this instance.
     */
    val httpClient: CloseableHttpClient,
    /**
     * Http client context used by this interface.
     */
    val context: HttpClientContext,
    private val ownedClient: Boolean,
    private val filter: HttpContextFilter
) : Closeable {

    private var lastRequest: HttpUriRequest? = null
    private var available = true

    /**
     * The final URL after redirects for the last processed request. Original URL if no redirects were performed.
     * Null if no requests have been executed. Undefined state if last request threw an exception.
     */
    val finalLocation: URI?
        get() {
            val redirectLocations = context.redirectLocations
            return if (redirectLocations != null && !redirectLocations.isEmpty()) {
                redirectLocations[redirectLocations.size - 1]
            } else {
                if (lastRequest != null) lastRequest!!.uri else null
            }
        }


    /**
     * Acquire exclusive use of this instance. This is released by calling close.
     *
     * @return True if this instance was not exclusively used when this method was called.
     */
    fun acquire(): Boolean {
        if (!available) {
            return false
        }

        filter.onContextOpen(context)
        available = false
        return true
    }

    /**
     * Executes the given query using the client and context stored in this instance.
     *
     * @param request The request to execute.
     * @return Closeable response from the server.
     * @throws IOException On network error.
     */
    @Throws(IOException::class)
    fun execute(request: HttpUriRequest): CloseableHttpResponse {
        var isRepeated = false
        while (true) {
            filter.onRequest(context, request, isRepeated)
            try {
                val response = httpClient.execute(request, context)
                lastRequest = request

                if (!filter.onRequestResponse(context, request, response)) {
                    return response
                }
            } catch (e: Throwable) {
                if (!filter.onRequestException(context, request, e)) {
                    when (e) {
                        is Error -> throw e
                        is RuntimeException -> throw e
                        is IOException -> throw e
                        else -> throw RuntimeException(e)
                    }
                } else {
                    e.rethrow()
                }
            }

            isRepeated = true
        }
    }

    @Throws(IOException::class)
    override fun close() {
        available = true
        filter.onContextClose(context)

        if (ownedClient) {
            httpClient.close()
        }
    }
}
