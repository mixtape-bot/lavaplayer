package com.sedmelluq.discord.lavaplayer.source.youtube

import com.sedmelluq.discord.lavaplayer.tools.http.HttpContextFilter
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools
import com.sedmelluq.lava.common.tools.exception.friendlyCheck
import org.apache.commons.codec.digest.DigestUtils
import org.apache.http.HttpResponse
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.impl.client.BasicCookieStore

class YoutubeHttpContextFilter : HttpContextFilter {
    companion object {
        private const val ATTRIBUTE_RESET_RETRY = "isResetRetry"
        private const val PBJ_PARAMETER = "&pbj=1"

        var PAPISID = ""
        var PSID = ""
    }

    override fun onContextOpen(context: HttpClientContext) {
        var cookieStore = context.cookieStore
        if (cookieStore == null) {
            cookieStore = BasicCookieStore()
            context.cookieStore = cookieStore
        }

        // Reset cookies for each sequence of requests.
        cookieStore.clear()
    }

    override fun onContextClose(context: HttpClientContext) {

    }

    override fun onRequest(context: HttpClientContext, request: HttpUriRequest, isRepetition: Boolean) {
        if (!isRepetition) {
            context.removeAttribute(ATTRIBUTE_RESET_RETRY)
        }

        if (isPbjRequest(request)) {
            addPbjHeaders(request);
        }

        if (PAPISID.isNotBlank() && PSID.isNotBlank()) {
            val millis = System.currentTimeMillis()
            val SAPISIDHASH = DigestUtils.sha1Hex("$millis $PAPISID ${YoutubeConstants.YOUTUBE_ORIGIN}")

            request.setHeader("Cookie", "__Secure-3PAPISID=$PAPISID __Secure-3PSID=$PSID")
            request.setHeader("Authorization", "SAPISIDHASH ${millis}_$SAPISIDHASH")
        }

        request.setHeader("Origin", YoutubeConstants.YOUTUBE_ORIGIN)
    }

    override fun onRequestResponse(
        context: HttpClientContext,
        request: HttpUriRequest,
        response: HttpResponse,
    ): Boolean {
        friendlyCheck(response.statusLine.statusCode != 429) {
            "This IP address has been blocked by YouTube (429)."
        }

        return false
    }

    override fun onRequestException(context: HttpClientContext, request: HttpUriRequest, error: Throwable): Boolean {
        // Always retry once in case of connection reset exception.
        if (HttpClientTools.isConnectionResetException(error)) {
            if (context.getAttribute(ATTRIBUTE_RESET_RETRY) == null) {
                context.setAttribute(ATTRIBUTE_RESET_RETRY, true)
                return true
            }
        }

        return false
    }

    protected fun isPbjRequest(request: HttpUriRequest): Boolean =
        request.uri.rawQuery?.contains(PBJ_PARAMETER) ?: false

    protected fun addPbjHeaders(request: HttpUriRequest) {
        request.setHeader("user-agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/76.0.3809.100 Safari/537.36")
        request.setHeader("x-youtube-client-name", "1")
        request.setHeader("x-youtube-client-version", "2.20191008.04.01")
        request.setHeader("x-youtube-page-cl", "276511266")
        request.setHeader("x-youtube-page-label", "youtube.ytfe.desktop_20191024_3_RC0")
        request.setHeader("x-youtube-utc-offset", "0")
        request.setHeader("x-youtube-variants-checksum", "7a1198276cf2b23fc8321fac72aa876b")
        request.setHeader("accept-language", "en")
    }
}
