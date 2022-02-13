package com.sedmelluq.lava.extensions.format.xm

import com.sedmelluq.discord.lavaplayer.container.MediaContainerDetection
import com.sedmelluq.discord.lavaplayer.container.MediaContainerDetectionResult
import com.sedmelluq.discord.lavaplayer.container.MediaContainerHints
import com.sedmelluq.discord.lavaplayer.container.MediaContainerProbe
import com.sedmelluq.discord.lavaplayer.tools.Units
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream
import com.sedmelluq.discord.lavaplayer.track.AudioReference
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.lava.track.info.AudioTrackInfo
import com.sedmelluq.lava.track.info.BasicAudioTrackInfo
import ibxm.Module
import mu.KotlinLogging

public object XmContainerProbe : MediaContainerProbe {
    private val log = KotlinLogging.logger {  }

    override val name: String = "xm"

    override fun matchesHints(hints: MediaContainerHints?): Boolean {
        return false
    }

    override fun probe(reference: AudioReference, inputStream: SeekableInputStream): MediaContainerDetectionResult? {
        val module: Module = inputStream
            .rewindAfter { it.runCatching { Module(this) } }
            .getOrNull()
            ?: return null

        log.debug { "Track ${reference.identifier} is a module." }
        return MediaContainerDetectionResult.supportedFormat(
            this,
            null,
            BasicAudioTrackInfo(
                module.songName,
                MediaContainerDetection.UNKNOWN_ARTIST,
                Units.DURATION_MS_UNKNOWN,
                reference.identifier!!,
                reference.identifier,
                null,
            )
        )
    }

    override fun createTrack(
        parameters: String?,
        trackInfo: AudioTrackInfo,
        inputStream: SeekableInputStream,
    ): AudioTrack {
        return XmAudioTrack(trackInfo, inputStream)
    }
}
