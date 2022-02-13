package com.sedmelluq.discord.lavaplayer.container.adts

import com.sedmelluq.discord.lavaplayer.track.BaseAudioTrack
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor
import com.sedmelluq.lava.track.info.AudioTrackInfo
import mu.KotlinLogging
import java.io.InputStream

/**
 * Audio track that handles an ADTS packet stream
 *
 * @param trackInfo   Track info
 * @param inputStream Input stream for the ADTS stream
 */
class AdtsAudioTrack(trackInfo: AudioTrackInfo?, private val inputStream: InputStream) : BaseAudioTrack(trackInfo!!) {
    companion object {
        private val log = KotlinLogging.logger { }
    }

    @Throws(Exception::class)
    override suspend fun process(executor: LocalAudioTrackExecutor) {
        AdtsStreamProvider(inputStream, executor.processingContext)
            .use { provider ->
                log.debug { "Starting to play ADTS stream $identifier" }
                executor.executeProcessingLoop({ provider.provideFrames() }, null)
            }
    }
}
