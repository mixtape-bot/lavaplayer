package com.sedmelluq.discord.lavaplayer.container.adts

import com.sedmelluq.discord.lavaplayer.container.MediaContainerDetection
import com.sedmelluq.discord.lavaplayer.container.MediaContainerDetectionResult
import com.sedmelluq.discord.lavaplayer.container.MediaContainerHints
import com.sedmelluq.discord.lavaplayer.container.MediaContainerProbe
import com.sedmelluq.discord.lavaplayer.container.adts.AdtsContainerProbe
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream
import com.sedmelluq.discord.lavaplayer.track.AudioReference
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo
import com.sedmelluq.discord.lavaplayer.track.info.AudioTrackInfoBuilder.Companion.create
import mu.KotlinLogging
import org.slf4j.LoggerFactory
import java.io.IOException

/**
 * Container detection probe for ADTS stream format.
 */
object AdtsContainerProbe : MediaContainerProbe {
    private val log = KotlinLogging.logger {  }

    override val name: String = "adts"

    @Throws(IOException::class)
    override fun probe(reference: AudioReference, inputStream: SeekableInputStream): MediaContainerDetectionResult? {
        val reader = AdtsStreamReader(inputStream)
        if (reader.findPacketHeader(MediaContainerDetection.STREAM_SCAN_DISTANCE) == null) {
            return null
        }

        log.debug { "Track ${reference.identifier} is an ADTS stream." }
        return MediaContainerDetectionResult.supportedFormat(this, null, create(reference, inputStream).build())
    }

    override fun createTrack(parameters: String?, trackInfo: AudioTrackInfo, inputStream: SeekableInputStream): AudioTrack =
        AdtsAudioTrack(trackInfo, inputStream)

    override fun matchesHints(hints: MediaContainerHints?): Boolean {
        val invalidMimeType = hints!!.mimeType != null && !"audio/aac".equals(hints.mimeType, ignoreCase = true)
        val invalidFileExtension = hints.fileExtension != null && !"aac".equals(hints.fileExtension, ignoreCase = true)
        return hints.isPresent && !invalidMimeType && !invalidFileExtension
    }
}
