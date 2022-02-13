package com.sedmelluq.discord.lavaplayer.container.playlists

import com.sedmelluq.discord.lavaplayer.container.*
import com.sedmelluq.discord.lavaplayer.source.http.HttpItemSourceManager
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools
import com.sedmelluq.discord.lavaplayer.tools.extensions.create
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools.NoRedirectsStrategy
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream
import com.sedmelluq.discord.lavaplayer.tools.io.ThreadLocalHttpInterfaceManager
import com.sedmelluq.discord.lavaplayer.track.AudioReference
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.lava.track.info.AudioTrackInfo
import com.sedmelluq.lava.track.info.AudioTrackInfoBuilder
import mu.KotlinLogging
import java.io.IOException

/**
 * Probe for M3U playlist.
 */
class M3uPlaylistContainerProbe : MediaContainerProbe {
    companion object {
        private val log = KotlinLogging.logger {  }

        private const val TYPE_HLS_OUTER = "hls-outer"
        private const val TYPE_HLS_INNER = "hls-inner"

        private val M3U_HEADER_TAG = intArrayOf('#'.code, 'E'.code, 'X'.code, 'T'.code, 'M'.code, '3'.code, 'U'.code)
        private val M3U_ENTRY_TAG = intArrayOf('#'.code, 'E'.code, 'X'.code, 'T'.code, 'I'.code, 'N'.code, 'F'.code)
    }

    private val httpInterfaceManager: HttpInterfaceManager = ThreadLocalHttpInterfaceManager(
        HttpClientTools
            .createSharedCookiesHttpBuilder()
            .setRedirectStrategy(NoRedirectsStrategy()),
        HttpClientTools.DEFAULT_REQUEST_CONFIG
    )

    override val name: String
        get() = "m3u"

    override fun matchesHints(hints: MediaContainerHints?): Boolean {
        return false
    }

    @Throws(IOException::class)
    override fun probe(reference: AudioReference, inputStream: SeekableInputStream): MediaContainerDetectionResult? {
        if (
            !MediaContainerDetection.checkNextBytes(inputStream, M3U_HEADER_TAG) &&
            !MediaContainerDetection.checkNextBytes(inputStream, M3U_ENTRY_TAG)
        ) {
            return null
        }

        log.debug { "Track $reference.identifier is an M3U playlist file." }

        val lines = DataFormatTools.streamToLines(inputStream)

        val hlsStreamUrl = HlsStreamSegmentUrlProvider.findHlsEntryUrl(lines)
        if (hlsStreamUrl != null) {
            val infoBuilder = AudioTrackInfoBuilder.create(reference, inputStream)
            val httpReference = HttpItemSourceManager.getAsHttpReference(reference)

            return if (httpReference != null) {
                infoBuilder.identifier = httpReference.identifier
                MediaContainerDetectionResult.supportedFormat(this, TYPE_HLS_OUTER, infoBuilder.build())
            } else {
                val reference = AudioReference(
                    identifier = hlsStreamUrl,
                    title = infoBuilder.title,
                    containerDescriptor = MediaContainerDescriptor(this, TYPE_HLS_INNER)
                )

                MediaContainerDetectionResult.refer(this, reference)
            }
        }

        return loadSingleItemPlaylist(lines)
            ?: MediaContainerDetectionResult.unsupportedFormat(this, "The playlist file contains no links.")
    }

    override fun createTrack(parameters: String?, trackInfo: AudioTrackInfo, inputStream: SeekableInputStream, ): AudioTrack {
        return when (parameters) {
            TYPE_HLS_INNER -> HlsStreamTrack(trackInfo, trackInfo.identifier, httpInterfaceManager, true)
            TYPE_HLS_OUTER -> HlsStreamTrack(trackInfo, trackInfo.identifier, httpInterfaceManager, false)
            else -> throw IllegalArgumentException("Unsupported parameters: $parameters")
        }
    }

    private fun loadSingleItemPlaylist(lines: List<String>): MediaContainerDetectionResult? {
        var trackTitle: String? = null
        for (line in lines) {
            if (line.startsWith("#EXTINF")) {
                trackTitle = extractTitleFromInfo(line)
            } else if (!line.startsWith("#") && line.isNotEmpty()) {
                if (line.startsWith("http://") || line.startsWith("https://") || line.startsWith("icy://")) {
                    return MediaContainerDetectionResult.refer(this, AudioReference(line.trim { it <= ' ' }, trackTitle))
                }

                trackTitle = null
            }

        }
        return null
    }

    private fun extractTitleFromInfo(infoLine: String): String? {
        val splitInfo = infoLine.split(",".toRegex(), 2)
        return if (splitInfo.size == 2) splitInfo[1] else null
    }
}
