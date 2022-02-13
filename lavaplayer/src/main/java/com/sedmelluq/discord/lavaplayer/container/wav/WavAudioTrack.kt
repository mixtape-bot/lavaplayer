package com.sedmelluq.discord.lavaplayer.container.wav

import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream
import com.sedmelluq.discord.lavaplayer.track.BaseAudioTrack
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor
import com.sedmelluq.lava.track.info.AudioTrackInfo
import mu.KotlinLogging

/**
 * Audio track that handles a WAV stream
 *
 * @param trackInfo Track info
 * @param stream    Input stream for the WAV file
 */
class WavAudioTrack(trackInfo: AudioTrackInfo, private val stream: SeekableInputStream) : BaseAudioTrack(trackInfo) {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    @Throws(Exception::class)
    override suspend fun process(executor: LocalAudioTrackExecutor) {
        WavFileLoader(stream)
            .loadTrack(executor.processingContext)
            .use { trackProvider ->
                log.debug { "Starting to play WAV track $identifier" }
                executor.executeProcessingLoop(
                    readExecutor = trackProvider::provideFrames,
                    seekExecutor = trackProvider::seekToTimecode
                )
            }
    }
}
