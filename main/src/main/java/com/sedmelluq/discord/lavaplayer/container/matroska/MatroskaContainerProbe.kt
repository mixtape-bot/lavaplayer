package com.sedmelluq.discord.lavaplayer.container.matroska

import com.sedmelluq.discord.lavaplayer.container.MediaContainerDetection
import com.sedmelluq.discord.lavaplayer.container.MediaContainerDetectionResult
import com.sedmelluq.discord.lavaplayer.container.MediaContainerHints
import com.sedmelluq.discord.lavaplayer.container.MediaContainerProbe
import com.sedmelluq.discord.lavaplayer.container.matroska.format.MatroskaFileTrack
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream
import com.sedmelluq.discord.lavaplayer.track.AudioReference
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo
import mu.KotlinLogging
import java.io.IOException

/**
 * Container detection probe for matroska format.
 */
object MatroskaContainerProbe : MediaContainerProbe {
    const val OPUS_CODEC = "A_OPUS"
    const val VORBIS_CODEC = "A_VORBIS"
    const val AAC_CODEC = "A_AAC"

    private val EBML_TAG = intArrayOf(0x1A, 0x45, 0xDF, 0xA3)
    private val supportedCodecs = listOf(OPUS_CODEC, VORBIS_CODEC, AAC_CODEC)
    private val log = KotlinLogging.logger { }

    override val name: String = "matroska/webm"

    @Throws(IOException::class)
    override fun probe(reference: AudioReference, inputStream: SeekableInputStream): MediaContainerDetectionResult? {
        if (!MediaContainerDetection.checkNextBytes(inputStream, EBML_TAG)) {
            return null
        }

        log.debug("Track ${reference.identifier} is a matroska file.")

        val file = MatroskaStreamingFile(inputStream)
        file.readFile()

        if (!hasSupportedAudioTrack(file)) {
            return MediaContainerDetectionResult.unsupportedFormat(
                this,
                "No supported audio tracks present in the file."
            )
        }

        val trackInfo = AudioTrackInfo(
            title = MediaContainerDetection.UNKNOWN_TITLE,
            author = MediaContainerDetection.UNKNOWN_ARTIST,
            length = file.duration.toLong(),
            identifier = reference.identifier!!,
            uri = reference.identifier,
            artworkUrl = null,
            isStream = false
        )

        return MediaContainerDetectionResult.supportedFormat(this, null, trackInfo)
    }

    override fun matchesHints(hints: MediaContainerHints?): Boolean = false

    override fun createTrack(parameters: String?, trackInfo: AudioTrackInfo, inputStream: SeekableInputStream): AudioTrack =
        MatroskaAudioTrack(trackInfo, inputStream)

    private fun hasSupportedAudioTrack(file: MatroskaStreamingFile): Boolean {
        return file.trackList.any { track ->
            track.type == MatroskaFileTrack.Type.AUDIO && supportedCodecs.contains(
                track.codecId
            )
        }
    }
}
