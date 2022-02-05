package com.sedmelluq.lava.extensions.iprotator

import com.sedmelluq.lava.extensions.iprotator.planner.AbstractRoutePlanner
import com.sedmelluq.lava.extensions.iprotator.tools.RateLimitException
import com.sedmelluq.discord.lavaplayer.tools.http.AbstractHttpContextFilter
import com.sedmelluq.discord.lavaplayer.tools.http.HttpContextFilter
import com.sedmelluq.discord.lavaplayer.tools.http.HttpContextRetryCounter
import mu.KotlinLogging
import org.apache.http.HttpResponse
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.client.protocol.HttpClientContext
import java.net.BindException

public class IpRotatorFilter @JvmOverloads constructor(
    delegate: HttpContextFilter?,
    private val isSearch: Boolean,
    private val routePlanner: AbstractRoutePlanner,
    private val retryLimit: Int,
    retryCountAttribute: String = RETRY_COUNT_ATTRIBUTE
) : AbstractHttpContextFilter(delegate) {
    public companion object {
        public const val RETRY_COUNT_ATTRIBUTE: String = "retry-counter"

        private val log = KotlinLogging.logger {}
    }

    private val retryCounter = HttpContextRetryCounter(retryCountAttribute)

    override fun onRequest(context: HttpClientContext, request: HttpUriRequest, isRepetition: Boolean) {
        retryCounter.handleUpdate(context, isRepetition)
        super.onRequest(context, request, isRepetition)
    }

    override fun onRequestException(context: HttpClientContext, request: HttpUriRequest, error: Throwable): Boolean {
        if (error is BindException) {
            log.warn {
                "Cannot assign requested address ${routePlanner.getLastAddress(context)}, marking address as failing and retry!"
            }

            routePlanner.markAddressFailing(context)
            return context.limitedRetry()
        }

        return super.onRequestException(context, request, error)
    }

    override fun onRequestResponse(
        context: HttpClientContext,
        request: HttpUriRequest,
        response: HttpResponse
    ): Boolean {
        if (isSearch) {
            if (response.isRateLimited) {
                if (routePlanner.shouldHandleSearchFailure()) {
                    log.warn { "Search rate-limit reached, marking address as failing and retry" }
                    routePlanner.markAddressFailing(context)
                }

                return context.limitedRetry()
            }
        } else if (response.isRateLimited) {
            log.warn { "Rate-limit reached, marking address ${routePlanner.getLastAddress(context)} as failing and retry" }
            routePlanner.markAddressFailing(context)
            return context.limitedRetry()
        }

        return super.onRequestResponse(context, request, response)
    }

    private val HttpResponse.isRateLimited: Boolean
        get() = statusLine.statusCode == 429

    private fun HttpClientContext.limitedRetry(): Boolean {
        return if (retryCounter.retryCountFor(this) >= retryLimit) {
            throw RateLimitException("Retry aborted, too many retries on rate-limit.")
        } else {
            true
        }
    }
}
