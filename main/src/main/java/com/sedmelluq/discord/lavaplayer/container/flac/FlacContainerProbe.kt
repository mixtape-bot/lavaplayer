package com.sedmelluq.discord.lavaplayer.container.flac

import com.sedmelluq.discord.lavaplayer.container.MediaContainerDetection
import com.sedmelluq.discord.lavaplayer.container.MediaContainerDetection.Companion.UNKNOWN_ARTIST
import com.sedmelluq.discord.lavaplayer.container.MediaContainerDetection.Companion.UNKNOWN_TITLE
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
 * Container detection probe for MP3 format.
 */
object FlacContainerProbe : MediaContainerProbe {
    private const val TITLE_TAG = "TITLE"
    private const val ARTIST_TAG = "ARTIST"

    private val log = KotlinLogging.logger { }

    override val name: String = "flac"

    @Throws(IOException::class)
    override fun probe(reference: AudioReference, inputStream: SeekableInputStream): MediaContainerDetectionResult? {
        if (!MediaContainerDetection.checkNextBytes(inputStream, FlacFileLoader.FLAC_CC)) {
            return null
        }

        log.debug { "Track ${reference.identifier} is a FLAC file." }

        val fileInfo = FlacFileLoader(inputStream).parseHeaders()
        val trackInfo = AudioTrackInfoBuilder.create(reference, inputStream) {
            title = fileInfo.tags[TITLE_TAG] ?: UNKNOWN_TITLE
            author = fileInfo.tags[ARTIST_TAG] ?: UNKNOWN_ARTIST
            length = fileInfo.duration
        }.build()

        return MediaContainerDetectionResult.supportedFormat(this, null, trackInfo)
    }

    override fun matchesHints(hints: MediaContainerHints?): Boolean = false

    override fun createTrack(parameters: String?, trackInfo: AudioTrackInfo, inputStream: SeekableInputStream): AudioTrack {
        return FlacAudioTrack(trackInfo, inputStream)
    }
}
