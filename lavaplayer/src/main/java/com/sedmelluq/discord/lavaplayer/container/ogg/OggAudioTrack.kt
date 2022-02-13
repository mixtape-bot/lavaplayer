package com.sedmelluq.discord.lavaplayer.container.ogg

import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream
import com.sedmelluq.discord.lavaplayer.track.BaseAudioTrack
import com.sedmelluq.discord.lavaplayer.track.playback.AudioProcessingContext
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor
import com.sedmelluq.lava.common.tools.exception.FriendlyException
import com.sedmelluq.lava.common.tools.exception.friendlyError
import com.sedmelluq.lava.track.info.AudioTrackInfo
import mu.KotlinLogging
import java.io.IOException

/**
 * Audio track which handles an OGG stream.
 *
 * @param trackInfo   Track info
 * @param inputStream Input stream for the OGG stream
 */
class OggAudioTrack(trackInfo: AudioTrackInfo, private val inputStream: SeekableInputStream) : BaseAudioTrack(trackInfo) {
    companion object {
        private val log = KotlinLogging.logger {  }
    }

    override suspend fun process(executor: LocalAudioTrackExecutor) {
        val packetInputStream = OggPacketInputStream(inputStream, false)
        log.debug { "Starting to play an OGG stream track $identifier" }

        executor.executeProcessingLoop({
            try {
                processTrackLoop(packetInputStream, executor.processingContext)
            } catch (e: IOException) {
                friendlyError("Stream broke when playing OGG track.", FriendlyException.Severity.SUSPICIOUS, e)
            }
        }, null, true)
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun processTrackLoop(packetInputStream: OggPacketInputStream, context: AudioProcessingContext) {
        var blueprint: OggTrackBlueprint? = OggTrackLoader.loadTrackBlueprint(packetInputStream)
            ?: throw IOException("Stream terminated before the first packet.")

        while (blueprint != null) {
            blueprint.loadTrackHandler(packetInputStream).use { handler ->
                handler.initialise(context, 0, 0)
                handler.provideFrames()
            }

            blueprint = OggTrackLoader.loadTrackBlueprint(packetInputStream)
        }
    }
}
