package com.sedmelluq.discord.lavaplayer.container.mpeg

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
 * Container detection probe for MP4 format.
 */
object MpegContainerProbe : MediaContainerProbe {
    private val log = KotlinLogging.logger { }
    private val ISO_TAG = intArrayOf(0x00, 0x00, 0x00, -1, 0x66, 0x74, 0x79, 0x70)

    override val name: String = "mp4"

    @Throws(IOException::class)
    override fun probe(reference: AudioReference, inputStream: SeekableInputStream): MediaContainerDetectionResult? {
        if (!MediaContainerDetection.checkNextBytes(inputStream, ISO_TAG)) {
            return null
        }

        log.debug { "Track ${reference.identifier} is an MP4 file." }
        val file = MpegFileLoader(inputStream)
        file.parseHeaders()

        val audioTrack = getSupportedAudioTrack(file)
            ?: return MediaContainerDetectionResult.unsupportedFormat(this, "No supported audio formats in MP4 file.")

        val fileReader = file.loadReader(MpegNoopTrackConsumer(audioTrack))
            ?: return MediaContainerDetectionResult.unsupportedFormat(this, "MP4 file uses an unsupported format.")

        val trackInfo = AudioTrackInfoBuilder.create(reference, inputStream) {
            title = file.getTextMetadata("Title")
            author = file.getTextMetadata("Artist")
            length = fileReader.duration
        }

        return MediaContainerDetectionResult.supportedFormat(this, null, trackInfo.build())
    }

    override fun matchesHints(hints: MediaContainerHints?): Boolean = false

    override fun createTrack(
        parameters: String?,
        trackInfo: AudioTrackInfo,
        inputStream: SeekableInputStream
    ): AudioTrack =
        MpegAudioTrack(trackInfo, inputStream)

    private fun getSupportedAudioTrack(file: MpegFileLoader): MpegTrackInfo? {
        return file.trackList.firstOrNull { it.handler == MpegConstants.AUDIO_HANDLER && it.codecName == MpegConstants.CODEC_NAME }
    }
}
