package com.sedmelluq.discord.lavaplayer.source.youtube

import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegAudioTrack
import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegFileLoader
import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegTrackConsumer
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools.extractBetween
import com.sedmelluq.discord.lavaplayer.tools.Units
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream
import com.sedmelluq.discord.lavaplayer.track.playback.AudioProcessingContext
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor
import com.sedmelluq.lava.common.tools.exception.FriendlyException
import com.sedmelluq.lava.common.tools.exception.friendlyError
import com.sedmelluq.lava.track.info.AudioTrackInfo
import org.apache.http.HttpStatus
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.utils.URIBuilder
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException

/**
 * YouTube segmented MPEG stream track. The base URL always gives the latest chunk. Every chunk contains the current
 * sequence number in it, which is used to get the sequence number of the next segment. This is repeated until YouTube
 * responds to a segment request with 204.
 *
 * @param trackInfo     Track info
 * @param httpInterface HTTP interface to use for loading segments
 * @param signedUrl     URI of the base stream with signature resolved
 */
class YoutubeMpegStreamAudioTrack(
    trackInfo: AudioTrackInfo?,
    private val httpInterface: HttpInterface,
    private val signedUrl: URI
) : MpegAudioTrack(trackInfo!!) {
    companion object {
        private val streamingRequestConfig = RequestConfig.custom().setConnectTimeout(10000).build()
        private const val EMPTY_RETRY_THRESHOLD_MS: Long = 400
        private const val EMPTY_RETRY_INTERVAL_MS: Long = 50
    }

    init {
        // YouTube does not return a segment until it is ready, this might trigger a connection timeout otherwise.
        httpInterface.context.requestConfig = streamingRequestConfig
    }

    override suspend fun process(executor: LocalAudioTrackExecutor) =
        executor.executeProcessingLoop({ execute(executor) }, null)

    @Throws(InterruptedException::class)
    private fun execute(localExecutor: LocalAudioTrackExecutor) {
        val state = TrackState(signedUrl)
        try {
            while (!state.finished) {
                processNextSegmentWithRetry(localExecutor, state)
                state.relativeSequence++
            }
        } finally {
            state.trackConsumer?.close()
        }
    }

    @Throws(InterruptedException::class)
    private fun processNextSegmentWithRetry(localExecutor: LocalAudioTrackExecutor, state: TrackState) {
        if (processNextSegment(localExecutor, state)) {
            return
        }

        // First attempt gave empty result, possibly because the stream is not yet finished, but the next segment is just
        // not ready yet. Keep retrying at EMPTY_RETRY_INTERVAL_MS intervals until EMPTY_RETRY_THRESHOLD_MS is reached.
        val waitStart = System.currentTimeMillis()
        var iterationStart = waitStart
        while (!processNextSegment(localExecutor, state)) {
            // EMPTY_RETRY_THRESHOLD_MS is the maximum time between the end of the first attempt and the beginning of the last
            // attempt, to avoid retry being skipped due to response coming slowly.
            if (iterationStart - waitStart >= EMPTY_RETRY_THRESHOLD_MS) {
                state.finished = true
                break
            } else {
                Thread.sleep(EMPTY_RETRY_INTERVAL_MS)
                iterationStart = System.currentTimeMillis()
            }
        }
    }

    @Throws(InterruptedException::class)
    private fun processNextSegment(
        localExecutor: LocalAudioTrackExecutor,
        state: TrackState
    ): Boolean {
        val segmentUrl = getNextSegmentUrl(state)
        try {
            YoutubePersistentHttpStream(httpInterface, segmentUrl, Units.CONTENT_LENGTH_UNKNOWN).use { stream ->
                if (stream.checkStatusCode() == HttpStatus.SC_NO_CONTENT || stream.contentLength == 0L) {
                    return false
                }

                /* If we were redirected, use that URL as a base for the next segment URL. Otherwise, we will likely get redirected
                   again on every other request, which is inefficient (redirects across domains, the original URL is always
                   closing the connection, whereas the final URL is keep-alive). */

                state.baseUrl = httpInterface.finalLocation
                processSegmentStream(stream, localExecutor.processingContext, state)

                stream.releaseConnection()
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

        return true
    }

    @Throws(InterruptedException::class)
    private fun processSegmentStream(stream: SeekableInputStream, context: AudioProcessingContext, state: TrackState) {
        val file = MpegFileLoader(stream)
        file.parseHeaders()

        state.absoluteSequence = extractAbsoluteSequenceFromEvent(file.lastEventMessage)
        if (state.trackConsumer == null) {
            state.trackConsumer = loadAudioTrack(file, context)
        }

        val fileReader = file.loadReader(state.trackConsumer)
            ?: friendlyError("Unknown MP4 format.", FriendlyException.Severity.SUSPICIOUS)

        fileReader.provideFrames()
    }

    private fun getNextSegmentUrl(state: TrackState): URI {
        val builder = URIBuilder(state.baseUrl)
            .setParameter("rn", state.relativeSequence.toString())
            .setParameter("rbuf", "0")

        if (state.absoluteSequence != null) {
            builder.setParameter("sq", (state.absoluteSequence!! + 1).toString())
        }

        return try {
            builder.build()
        } catch (e: URISyntaxException) {
            throw RuntimeException(e)
        }
    }

    private fun extractAbsoluteSequenceFromEvent(data: ByteArray?): Long? {
        val message = data?.decodeToString()
            ?: return null

        return extractBetween(message, "Sequence-Number: ", "\r\n")?.toLongOrNull()
    }

    private class TrackState(var baseUrl: URI?) {
        var relativeSequence: Long = 0
        var absoluteSequence: Long? = null
        var trackConsumer: MpegTrackConsumer? = null
        var finished = false
    }
}
