package com.sedmelluq.discord.lavaplayer.container.mp3

import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream
import com.sedmelluq.discord.lavaplayer.track.BaseAudioTrack
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor
import com.sedmelluq.lava.track.info.AudioTrackInfo
import mu.KotlinLogging

/**
 * Audio track that handles an MP3 stream
 *
 * @param trackInfo   Track info
 * @param inputStream Input stream for the MP3 file
 */
class Mp3AudioTrack(trackInfo: AudioTrackInfo?, private val inputStream: SeekableInputStream) : BaseAudioTrack(trackInfo!!) {
    companion object {
        private val log = KotlinLogging.logger { }
    }

    @Throws(Exception::class)
    override suspend fun process(executor: LocalAudioTrackExecutor) {
        Mp3TrackProvider(executor.processingContext, inputStream).use { provider ->
            provider.parseHeaders()

            log.debug { "Starting to play MP3 track $identifier" }
            executor.executeProcessingLoop(
                readExecutor = provider::provideFrames,
                seekExecutor = provider::seekToTimecode
            )
        }
    }
}
