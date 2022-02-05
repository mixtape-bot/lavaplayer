package com.sedmelluq.discord.lavaplayer.tools.io

import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools
import com.sedmelluq.discord.lavaplayer.tools.http.ExtendedHttpClientBuilder
import com.sedmelluq.discord.lavaplayer.tools.json.JsonBrowser
import com.sedmelluq.lava.common.tools.exception.FriendlyException
import mu.KotlinLogging
import org.apache.http.*
import org.apache.http.client.CookieStore
import org.apache.http.client.RedirectStrategy
import org.apache.http.client.config.CookieSpecs
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.entity.ContentType
import org.apache.http.impl.client.BasicCookieStore
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.protocol.HttpContext
import org.apache.http.util.EntityUtils
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URISyntaxException
import javax.net.ssl.SSLException

/**
 * Tools for working with HttpClient
 */
object HttpClientTools {
    private val SUCCESS_STATUS_CODES = listOf(HttpStatus.SC_OK, HttpStatus.SC_PARTIAL_CONTENT, HttpStatus.SC_NON_AUTHORITATIVE_INFORMATION)
    private val REDIRECT_STATUS_CODES = listOf(HttpStatus.SC_MOVED_PERMANENTLY, HttpStatus.SC_MOVED_TEMPORARILY, HttpStatus.SC_SEE_OTHER, HttpStatus.SC_TEMPORARY_REDIRECT)

    private val log = KotlinLogging.logger {  }

    val DEFAULT_REQUEST_CONFIG = RequestConfig.custom()
        .setConnectTimeout(3000)
        .setCookieSpec(CookieSpecs.STANDARD)
        .build()

    val NO_COOKIES_REQUEST_CONFIG = RequestConfig.custom()
        .setConnectTimeout(3000)
        .setCookieSpec(CookieSpecs.IGNORE_COOKIES)
        .build()

    /**
     * @return An HttpClientBuilder which uses the same cookie store for all clients
     */
    fun createSharedCookiesHttpBuilder(): HttpClientBuilder =
        createHttpBuilder(DEFAULT_REQUEST_CONFIG)

    /**
     * @return Default HTTP interface manager with thread-local context
     */
    fun createDefaultThreadLocalManager(): HttpInterfaceManager =
        ThreadLocalHttpInterfaceManager(createSharedCookiesHttpBuilder(), DEFAULT_REQUEST_CONFIG)

    /**
     * @return HTTP interface manager with thread-local context, ignores cookies
     */
    fun createCookielessThreadLocalManager(): HttpInterfaceManager =
        ThreadLocalHttpInterfaceManager(createHttpBuilder(NO_COOKIES_REQUEST_CONFIG), NO_COOKIES_REQUEST_CONFIG)

    private fun createHttpBuilder(requestConfig: RequestConfig): HttpClientBuilder {
        val cookieStore: CookieStore = BasicCookieStore()
        return ExtendedHttpClientBuilder()
            .setDefaultCookieStore(cookieStore)
            .setRetryHandler(NoResponseRetryHandler)
            .setDefaultRequestConfig(requestConfig)
    }

    /**
     * @param response Http response to get the header value from.
     * @param name     Name of the header.
     * @return Value if header was present, null otherwise.
     */
    fun getHeaderValue(response: HttpResponse?, name: String): String? =
        response?.getFirstHeader(name)?.value

    fun getRawContentType(response: HttpResponse): String? =
        response.getFirstHeader(HttpHeaders.CONTENT_TYPE)?.value

    fun hasJsonContentType(response: HttpResponse): Boolean =
        getRawContentType(response)?.startsWith(ContentType.APPLICATION_JSON.mimeType) == true

    /**
     * @param requestUrl URL of the original request.
     * @param response   Response object.
     * @return A redirect location if the status code indicates a redirect and the Location header is present.
     */
    fun getRedirectLocation(requestUrl: String, response: HttpResponse): String? {
        if (!isRedirectStatus(response.statusLine.statusCode)) {
            return null
        }

        val location = response.getFirstHeader("Location")?.value
            ?: return null

        return try {
            URI(requestUrl).resolve(location).toString()
        } catch (e: URISyntaxException) {
            log.debug(e) { "Failed to parse URI." }
            location
        }
    }

    fun isRedirectStatus(statusCode: Int): Boolean =
        REDIRECT_STATUS_CODES.contains(statusCode)

    /**
     * @param statusCode The status code of a response.
     * @return True if this status code indicates a success with a response body
     */
    @JvmStatic
    fun isSuccessWithContent(statusCode: Int): Boolean =
        SUCCESS_STATUS_CODES.contains(statusCode)

    /**
     * @param response The response.
     * @param context  Additional string to include in exception message.
     * @return True if this status code indicates a success with a response body
     */
    @JvmStatic
    @Throws(IOException::class)
    fun assertSuccessWithContent(response: HttpResponse, context: String) {
        val statusCode = response.statusLine.statusCode
        if (!isSuccessWithContent(statusCode)) {
            throw IOException("Invalid status code for $context: $statusCode")
        }
    }

    @Throws(IOException::class)
    fun assertJsonContentType(response: HttpResponse) {
        if (!hasJsonContentType(response)) {
            throw ExceptionTools.throwWithDebugInfo(
                log,
                "Expected JSON content type, got " + getRawContentType(response),
                "responseContent",
                EntityUtils.toString(response.entity),
                null
            )
        }
    }

    /**
     * @param exception Exception to check.
     *
     * @return True if retrying to connect after receiving this exception is likely to succeed.
     */
    fun isRetryableNetworkException(exception: Throwable): Boolean {
        return isConnectionResetException(exception) ||
            isSocketTimeoutException(exception) ||
            isIncorrectSslShutdownException(exception) ||
            isPrematureEndException(exception) ||
            isRetryableConscryptException(exception) ||
            isRetryableNestedSslException(exception)
    }

    fun isConnectionResetException(exception: Throwable): Boolean =
        ((exception is SocketException || exception is SSLException) && exception.message == "Connection reset")

    fun isSocketTimeoutException(exception: Throwable): Boolean =
        ((exception is SocketTimeoutException || exception is SSLException) && exception.message == "Read timed out")

    fun isIncorrectSslShutdownException(exception: Throwable): Boolean =
        exception is SSLException && exception.message == "SSL peer shut down incorrectly"

    fun isPrematureEndException(exception: Throwable): Boolean =
        exception is ConnectionClosedException && exception.message?.startsWith("Premature end of Content-Length") == true

    fun isRetryableNestedSslException(exception: Throwable): Boolean =
        exception is SSLException && isRetryableNetworkException(exception.cause ?: exception)

    fun isRetryableConscryptException(exception: Throwable?): Boolean {
        if (exception is SSLException) {
            val message = exception.message
            if (message?.contains("I/O error during system call") == true) {
                return message.contains("No error") || message.contains("Connection reset by peer") || message.contains("Connection timed out")
            }
        }

        return false
    }

    /**
     * Executes an HTTP request and returns the response as a JsonBrowser instance.
     *
     * @param httpInterface HTTP interface to use for the request.
     * @param request       Request to perform.
     * @return Response as a JsonBrowser instance. null in case of 404.
     * @throws IOException On network error or for non-200 response code.
     */
    @Throws(IOException::class)
    fun fetchResponseAsJson(httpInterface: HttpInterface, request: HttpUriRequest): JsonBrowser? {
        httpInterface.execute(request).use { response ->
            val statusCode = response.statusLine.statusCode
            if (statusCode == HttpStatus.SC_NOT_FOUND) {
                return null
            } else if (!isSuccessWithContent(statusCode)) {
                throw FriendlyException("Server responded with an error.", FriendlyException.Severity.SUSPICIOUS,
                    IllegalStateException("Response code from channel info is $statusCode"))
            }

            val json: String = EntityUtils.toString(response.entity, Charsets.UTF_8)
            return JsonBrowser.parse(json)
        }
    }

    /**
     * Executes an HTTP request and returns the response as an array of lines.
     *
     * @param httpInterface HTTP interface to use for the request.
     * @param request       Request to perform.
     * @param name          Name of the operation to include in exception messages.
     * @return Array of lines from the response
     * @throws IOException On network error or for non-200 response code.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun fetchResponseLines(httpInterface: HttpInterface, request: HttpUriRequest, name: String): List<String> {
        httpInterface.execute(request).use { response ->
            val statusCode = response.statusLine.statusCode
            if (!isSuccessWithContent(statusCode)) {
                throw IOException("Unexpected response code $statusCode from $name")
            }

            return DataFormatTools.streamToLines(response.entity.content)
        }
    }

    /**
     * A redirect strategy which does not follow any redirects.
     */
    class NoRedirectsStrategy : RedirectStrategy {
        override fun isRedirected(request: HttpRequest, response: HttpResponse, context: HttpContext): Boolean = false
        override fun getRedirect(request: HttpRequest, response: HttpResponse, context: HttpContext): HttpUriRequest? = null
    }

    internal object NoResponseRetryHandler : DefaultHttpRequestRetryHandler() {
        override fun retryRequest(exception: IOException, executionCount: Int, context: HttpContext): Boolean {
            val retry = super.retryRequest(exception, executionCount, context)
            return if (!retry && exception is NoHttpResponseException && executionCount < 5) true else retry
        }
    }
}
