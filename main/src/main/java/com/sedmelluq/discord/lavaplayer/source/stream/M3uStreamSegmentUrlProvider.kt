package com.sedmelluq.discord.lavaplayer.source.stream

import com.sedmelluq.discord.lavaplayer.container.playlists.ExtendedM3uParser
import com.sedmelluq.discord.lavaplayer.tools.extensions.closeWithWarnings
import com.sedmelluq.discord.lavaplayer.tools.extensions.toRuntimeException
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface
import com.sedmelluq.lava.common.tools.exception.FriendlyException
import com.sedmelluq.lava.common.tools.exception.friendlyError
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpUriRequest
import java.io.IOException
import java.io.InputStream
import java.net.URI

/**
 * Provides track segment URLs for streams which use the M3U segment format. There is a base M3U containing the list of
 * different available streams. Those point to segment M3U urls, which always give the direct stream URLs of last X
 * segments. The segment provider fetches the stream for the next segment on each call to
 * [M3uStreamSegmentUrlProvider.getNextSegmentStream].
 */
abstract class M3uStreamSegmentUrlProvider {
    companion object {
        private const val SEGMENT_WAIT_STEP_MS: Long = 200

        protected fun createSegmentUrl(playlistUrl: String, segmentName: String): String =
            URI.create(playlistUrl).resolve(segmentName).toString()

        private fun parseSecondDuration(value: String): Long? =
            value.toDoubleOrNull()?.let { it * 1000.0 }?.toLong()
    }

    protected var lastSegment: SegmentInfo? = null

    /**
     * If applicable, extracts the quality information from the M3U directive which describes one stream in the root M3U.
     *
     * @param directiveLine Directive line with arguments.
     * @return The quality name extracted from the directive line.
     */
    protected abstract fun getQualityFromM3uDirective(directiveLine: ExtendedM3uParser.Line): String?

    @Throws(IOException::class)
    protected abstract fun fetchSegmentPlaylistUrl(httpInterface: HttpInterface): String?

    protected abstract fun createSegmentGetRequest(url: String): HttpUriRequest?

    /**
     * Logic for getting the URL for the next segment.
     *
     * @param httpInterface HTTP interface to use for any requests required to perform to find the segment URL.
     * @return The direct stream URL of the next segment.
     */
    protected fun getNextSegmentUrl(httpInterface: HttpInterface): String? {
        return try {
            val streamSegmentPlaylistUrl = fetchSegmentPlaylistUrl(httpInterface)
                ?: return null

            val startTime = System.currentTimeMillis()
            var nextSegment: SegmentInfo?

            while (true) {
                val segments = loadStreamSegmentsList(httpInterface, streamSegmentPlaylistUrl)

                nextSegment = chooseNextSegment(segments, lastSegment)
                if (nextSegment != null || !shouldWaitForSegment(startTime, segments)) {
                    break
                }

                Thread.sleep(SEGMENT_WAIT_STEP_MS)
            }

            if (nextSegment == null) {
                return null
            }

            lastSegment = nextSegment
            createSegmentUrl(streamSegmentPlaylistUrl, lastSegment!!.url!!)
        } catch (e: IOException) {
            friendlyError("Failed to get next part of the stream.", FriendlyException.Severity.SUSPICIOUS, e)
        } catch (e: InterruptedException) {
            throw e.toRuntimeException()
        }
    }

    /**
     * Fetches the input stream for the next segment in the M3U stream.
     *
     * @param httpInterface HTTP interface to use for any requests required to perform to find the segment URL.
     * @return Input stream of the next segment.
     */
    fun getNextSegmentStream(httpInterface: HttpInterface): InputStream? {
        val url = getNextSegmentUrl(httpInterface) ?: return null
        var response: CloseableHttpResponse? = null
        var success = false

        return try {
            response = httpInterface.execute(createSegmentGetRequest(url)!!)
            HttpClientTools.assertSuccessWithContent(response, "segment data URL")
            success = true
            response.entity.content
        } catch (e: IOException) {
            throw e.toRuntimeException()
        } finally {
            if (response != null && !success) {
                response.closeWithWarnings()
            }
        }
    }

    protected fun loadChannelStreamsList(lines: List<String>): List<ChannelStreamInfo> {
        var streamInfoLine: ExtendedM3uParser.Line? = null
        val streams = mutableListOf<ChannelStreamInfo>()

        for (lineText in lines) {
            val line = ExtendedM3uParser.parseLine(lineText)
            if (line.isData && streamInfoLine != null) {
                val quality = getQualityFromM3uDirective(streamInfoLine)
                if (quality != null) {
                    streams.add(ChannelStreamInfo(quality, line.lineData))
                }

                streamInfoLine = null
            } else if (line.isDirective && "EXT-X-STREAM-INF" == line.directiveName) {
                streamInfoLine = line
            }
        }

        return streams
    }

    @Throws(IOException::class)
    protected fun loadStreamSegmentsList(
        httpInterface: HttpInterface,
        streamSegmentPlaylistUrl: String?
    ): List<SegmentInfo> {
        val segments: MutableList<SegmentInfo> = ArrayList()
        var segmentInfo: ExtendedM3uParser.Line? = null
        for (lineText in HttpClientTools.fetchResponseLines(
            httpInterface,
            HttpGet(streamSegmentPlaylistUrl),
            "stream segments list"
        )) {
            val line = ExtendedM3uParser.parseLine(lineText)

            if (line.isDirective && "EXTINF" == line.directiveName) {
                segmentInfo = line
            }

            if (line.isData) {
                if (segmentInfo != null && segmentInfo.extraData!!.contains(",")) {
                    val fields = segmentInfo.extraData!!.split(",").toTypedArray()

                    segments.add(
                        SegmentInfo(
                            line.lineData, parseSecondDuration(
                                fields[0]
                            ), fields[1]
                        )
                    )
                } else {
                    segments.add(SegmentInfo(line.lineData, null, null))
                }
            }
        }
        return segments
    }

    protected fun chooseNextSegment(segments: List<SegmentInfo>, lastSegment: SegmentInfo?): SegmentInfo? {
        var selected: SegmentInfo? = null
        for (i in segments.indices.reversed()) {
            val current = segments[i]
            if (lastSegment?.url == current.url) {
                break
            }

            selected = current
        }

        return selected
    }

    private fun shouldWaitForSegment(startTime: Long, segments: List<SegmentInfo>): Boolean {
        if (segments.isNotEmpty()) {
            val sampleSegment = segments[0]
            if (sampleSegment.duration != null) {
                return System.currentTimeMillis() - startTime < sampleSegment.duration
            }
        }

        return false
    }

    protected data class ChannelStreamInfo(
        /**
         * Stream quality extracted from stream M3U directive.
         */
        val quality: String,
        /**
         * URL for stream segment list.
         */
        val url: String?
    )

    protected data class SegmentInfo(
        /**
         * URL of the segment.
         */
        val url: String?,
        /**
         * Duration of the segment in milliseconds. `null` if unknown.
         */
        val duration: Long?,
        /**
         * Name of the segment. `null` if unknown.
         */
        val name: String?
    )
}
