package com.sedmelluq.discord.lavaplayer.container.playlists

import com.sedmelluq.discord.lavaplayer.container.MediaContainerDetection
import com.sedmelluq.discord.lavaplayer.container.MediaContainerDetectionResult
import com.sedmelluq.discord.lavaplayer.container.MediaContainerHints
import com.sedmelluq.discord.lavaplayer.container.MediaContainerProbe
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools.streamToLines
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream
import com.sedmelluq.discord.lavaplayer.track.AudioReference
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo
import mu.KotlinLogging
import java.io.IOException

/**
 * Probe for a playlist containing the raw link without any format.
 */
object PlainPlaylistContainerProbe : MediaContainerProbe {
    private val log = KotlinLogging.logger {  }
    private val linkPattern = """^(?:https?|icy)://.*""".toPattern()

    override val name: String = "plain"

    @Throws(IOException::class)
    override fun probe(reference: AudioReference, inputStream: SeekableInputStream): MediaContainerDetectionResult? {
        if (!MediaContainerDetection.matchNextBytesAsRegex(inputStream, MediaContainerDetection.STREAM_SCAN_DISTANCE, linkPattern, Charsets.UTF_8)) {
            return null
        }

        log.debug { "Track ${reference.identifier} is a plain playlist file." }
        return loadFromLines(streamToLines(inputStream, Charsets.UTF_8))
    }

    override fun matchesHints(hints: MediaContainerHints?): Boolean = false

    override fun createTrack(parameters: String?, trackInfo: AudioTrackInfo, inputStream: SeekableInputStream) =
        throw UnsupportedOperationException()

    private fun loadFromLines(lines: List<String>): MediaContainerDetectionResult {
        for (line in lines) {
            val matcher = linkPattern.matcher(line)
            if (matcher.matches()) {
                return MediaContainerDetectionResult.refer(this, AudioReference(matcher.group(0), null))
            }
        }

        return MediaContainerDetectionResult.unsupportedFormat(this, "The playlist file contains no links.")
    }
}
