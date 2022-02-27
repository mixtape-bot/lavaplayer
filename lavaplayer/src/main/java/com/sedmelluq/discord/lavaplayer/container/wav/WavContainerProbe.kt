package com.sedmelluq.discord.lavaplayer.container.wav

import com.sedmelluq.discord.lavaplayer.container.MediaContainerDetection
import com.sedmelluq.discord.lavaplayer.container.MediaContainerDetectionResult
import com.sedmelluq.discord.lavaplayer.container.MediaContainerHints
import com.sedmelluq.discord.lavaplayer.container.MediaContainerProbe
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream
import com.sedmelluq.discord.lavaplayer.track.AudioReference
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.lava.track.info.AudioTrackInfo
import com.sedmelluq.lava.track.info.BasicAudioTrackInfo
import mu.KotlinLogging
import java.io.IOException

/**
 * Container detection probe for WAV format.
 */
object WavContainerProbe : MediaContainerProbe {
    private val log = KotlinLogging.logger { }

    override val name: String = "wav"

    @Throws(IOException::class)
    override fun probe(reference: AudioReference, inputStream: SeekableInputStream): MediaContainerDetectionResult? {
        if (!MediaContainerDetection.checkNextBytes(inputStream, WavFileLoader.WAV_RIFF_HEADER)) {
            return null
        }

        log.debug { "Track ${reference.identifier} is a WAV file." }
        val trackInfo = BasicAudioTrackInfo(
            title = reference.title ?: MediaContainerDetection.UNKNOWN_TITLE,
            author = MediaContainerDetection.UNKNOWN_ARTIST,
            length = WavFileLoader(inputStream).parseHeaders().duration,
            identifier = reference.identifier!!,
            uri = reference.identifier,
        )

        return MediaContainerDetectionResult.supportedFormat(this, trackInfo, null)
    }

    override fun matchesHints(hints: MediaContainerHints?): Boolean = false

    override fun createTrack(parameters: String?, trackInfo: AudioTrackInfo, inputStream: SeekableInputStream): AudioTrack =
        WavAudioTrack(trackInfo, inputStream)
}
