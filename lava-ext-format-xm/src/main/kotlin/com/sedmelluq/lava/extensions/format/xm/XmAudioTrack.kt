package com.sedmelluq.lava.extensions.format.xm

import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream
import com.sedmelluq.discord.lavaplayer.track.BaseAudioTrack
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor
import com.sedmelluq.lava.track.info.AudioTrackInfo
import mu.KotlinLogging

public class XmAudioTrack(
    info: AudioTrackInfo,
    private val inputStream: SeekableInputStream,
) : BaseAudioTrack(info) {
    public companion object {
        private val log = KotlinLogging.logger {  }
    }

    override suspend fun process(executor: LocalAudioTrackExecutor) {
        XmFileLoader(inputStream).loadTrack(executor.processingContext)
            .use { trackProvider ->
                log.debug { "Starting to play module: $identifier" }
                executor.executeProcessingLoop(trackProvider::provideFrames, null)
            }
    }
}
