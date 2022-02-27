package com.sedmelluq.discord.lavaplayer.container.ogg

import com.sedmelluq.discord.lavaplayer.container.MediaContainerDetection
import com.sedmelluq.discord.lavaplayer.container.MediaContainerDetectionResult
import com.sedmelluq.discord.lavaplayer.container.MediaContainerHints
import com.sedmelluq.discord.lavaplayer.container.MediaContainerProbe
import com.sedmelluq.discord.lavaplayer.tools.extensions.create
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream
import com.sedmelluq.discord.lavaplayer.track.AudioReference
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.lava.track.info.AudioTrackInfo
import com.sedmelluq.lava.track.info.AudioTrackInfoBuilder
import mu.KotlinLogging
import java.io.IOException

/**
 * Container detection probe for OGG stream.
 */
object OggContainerProbe : MediaContainerProbe {
    private val log = KotlinLogging.logger {  }

    override val name: String = "ogg"

    @Throws(IOException::class)
    override fun probe(reference: AudioReference, inputStream: SeekableInputStream): MediaContainerDetectionResult? {
        if (!MediaContainerDetection.checkNextBytes(inputStream, OggPacketInputStream.OGG_PAGE_HEADER)) {
            return null
        }

        log.debug { "Track ${reference.identifier} is an OGG stream." }

        val infoBuilder = AudioTrackInfoBuilder.create(reference, inputStream).setIsStream(true)
        try {
            collectStreamInformation(inputStream, infoBuilder)
        } catch (e: Exception) {
            log.warn(e) { "Failed to collect additional information on OGG stream." }
        }

        return MediaContainerDetectionResult.supportedFormat(this, infoBuilder.build(), null)
    }

    override fun matchesHints(hints: MediaContainerHints?): Boolean = false

    override fun createTrack(parameters: String?, trackInfo: AudioTrackInfo, inputStream: SeekableInputStream): AudioTrack {
        return OggAudioTrack(trackInfo, inputStream)
    }

    @Throws(IOException::class)
    private fun collectStreamInformation(stream: SeekableInputStream, infoBuilder: AudioTrackInfoBuilder) {
        val packetInputStream = OggPacketInputStream(stream, false)
        val metadata = OggTrackLoader.loadMetadata(packetInputStream)
        metadata?.let { infoBuilder.apply(it) }
    }
}
