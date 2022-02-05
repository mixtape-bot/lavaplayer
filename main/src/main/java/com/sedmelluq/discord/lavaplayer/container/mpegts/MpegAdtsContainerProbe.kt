package com.sedmelluq.discord.lavaplayer.container.mpegts

import com.sedmelluq.discord.lavaplayer.container.MediaContainerDetectionResult
import com.sedmelluq.discord.lavaplayer.container.MediaContainerHints
import com.sedmelluq.discord.lavaplayer.container.MediaContainerProbe
import com.sedmelluq.discord.lavaplayer.container.adts.AdtsStreamReader
import com.sedmelluq.discord.lavaplayer.tools.extensions.create
import com.sedmelluq.discord.lavaplayer.tools.io.SavedHeadSeekableInputStream
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream
import com.sedmelluq.discord.lavaplayer.track.AudioReference
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.lava.track.info.AudioTrackInfo
import com.sedmelluq.lava.track.info.AudioTrackInfoBuilder
import mu.KotlinLogging
import java.io.IOException

object MpegAdtsContainerProbe : MediaContainerProbe {
    private val log = KotlinLogging.logger {  }

    override val name: String = "mpegts-adts"

    @Throws(IOException::class)
    override fun probe(reference: AudioReference, inputStream: SeekableInputStream): MediaContainerDetectionResult? {
        val head = if (inputStream is SavedHeadSeekableInputStream) inputStream else null
        head?.setAllowDirectReads(false)

        val tsStream = MpegTsElementaryInputStream(inputStream, MpegTsElementaryInputStream.ADTS_ELEMENTARY_STREAM)
        val pesStream = PesPacketInputStream(tsStream)
        val reader = AdtsStreamReader(pesStream)
        try {
            if (reader.findPacketHeader() != null) {
                log.debug { "Track ${reference.identifier} is an MPEG-TS stream with an ADTS track." }
                val trackInfo = AudioTrackInfoBuilder.create(reference, inputStream)
                    .apply(tsStream.loadedMetadata)
                    .build()

                return MediaContainerDetectionResult.supportedFormat(this, null, trackInfo)
            }
        } catch (ignored: IndexOutOfBoundsException) {
            // TS stream read too far and still did not find required elementary stream - SavedHeadSeekableInputStream throws
            // this because we disabled reads past the loaded "head".
        } finally {
            head?.setAllowDirectReads(true)
        }

        return null
    }

    override fun matchesHints(hints: MediaContainerHints?): Boolean =
        "ts".equals(hints!!.fileExtension, ignoreCase = true)

    override fun createTrack(parameters: String?, trackInfo: AudioTrackInfo, inputStream: SeekableInputStream): AudioTrack =
        MpegAdtsAudioTrack(trackInfo, inputStream)
}
