package com.sedmelluq.discord.lavaplayer.container.playlists

import com.sedmelluq.discord.lavaplayer.source.stream.M3uStreamSegmentUrlProvider
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface
import mu.KotlinLogging
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpUriRequest
import java.io.IOException

class HlsStreamSegmentUrlProvider(
    private val streamListUrl: String?,
    @field:Volatile private var segmentPlaylistUrl: String?,
) : M3uStreamSegmentUrlProvider() {
    companion object {
        private val log = KotlinLogging.logger { }

        @JvmStatic
        fun findHlsEntryUrl(lines: List<String>): String? =
            HlsStreamSegmentUrlProvider(null, null)
                .loadChannelStreamsList(lines)
                .firstOrNull()?.url
    }

    override fun getQualityFromM3uDirective(directiveLine: ExtendedM3uParser.Line): String =
        "default"

    override fun createSegmentGetRequest(url: String): HttpUriRequest =
        HttpGet(url)

    @Throws(IOException::class)
    override fun fetchSegmentPlaylistUrl(httpInterface: HttpInterface): String? {
        if (segmentPlaylistUrl != null) {
            return segmentPlaylistUrl
        }

        val request: HttpUriRequest = HttpGet(streamListUrl)
        val streams = loadChannelStreamsList(
            HttpClientTools.fetchResponseLines(httpInterface, request, "HLS stream list")
        )

        check(streams.isNotEmpty()) {
            "No streams listed in HLS stream list."
        }

        val (quality, url) = streams[0]
        log.debug { "Chose stream with quality $quality from url $url" }

        segmentPlaylistUrl = url
        return segmentPlaylistUrl
    }
}
