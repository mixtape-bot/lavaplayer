package com.sedmelluq.discord.lavaplayer.container.mp3

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
 * Container detection probe for MP3 format.
 */
object Mp3ContainerProbe : MediaContainerProbe {
    private val log = KotlinLogging.logger { }
    private val ID3_TAG = intArrayOf(0x49, 0x44, 0x33)

    override val name: String = "mp3"

    @Throws(IOException::class)
    override fun probe(reference: AudioReference, inputStream: SeekableInputStream): MediaContainerDetectionResult? {
        if (!MediaContainerDetection.checkNextBytes(inputStream, ID3_TAG)) {
            val frameHeader = ByteArray(4)
            val frameReader = Mp3FrameReader(inputStream, frameHeader)
            if (!frameReader.scanForFrame(MediaContainerDetection.STREAM_SCAN_DISTANCE, false)) {
                return null
            }

            inputStream.seek(0)
        }

        log.debug { "Track ${reference.identifier} is an MP3 file." }
        return Mp3TrackProvider(null, inputStream).use { file ->
            file.parseHeaders()

            val trackInfo = AudioTrackInfoBuilder.create(reference, inputStream) {
                apply(file)
                isStream = !file.isSeekable
            }

            MediaContainerDetectionResult.supportedFormat(this, trackInfo.build(), null)
        }
    }

    override fun createTrack(parameters: String?, trackInfo: AudioTrackInfo, inputStream: SeekableInputStream): AudioTrack =
        Mp3AudioTrack(trackInfo, inputStream)

    override fun matchesHints(hints: MediaContainerHints?): Boolean {
        val invalidMimeType = hints!!.mimeType != null && !"audio/mpeg".equals(hints.mimeType, ignoreCase = true)
        val invalidFileExtension = hints.fileExtension != null && !"mp3".equals(hints.fileExtension, ignoreCase = true)
        return hints.isPresent && !invalidMimeType && !invalidFileExtension
    }
}
