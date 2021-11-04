package com.sedmelluq.discord.lavaplayer.container.ogg

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo
import com.sedmelluq.discord.lavaplayer.track.BaseAudioTrack
import com.sedmelluq.discord.lavaplayer.track.playback.AudioProcessingContext
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor
import org.slf4j.LoggerFactory
import java.io.IOException

/**
 * Audio track which handles an OGG stream.
 *
 * @param trackInfo   Track info
 * @param inputStream Input stream for the OGG stream
 */
class OggAudioTrack(trackInfo: AudioTrackInfo, private val inputStream: SeekableInputStream) : BaseAudioTrack(trackInfo) {
    override fun process(executor: LocalAudioTrackExecutor) {
        val packetInputStream = OggPacketInputStream(inputStream, false)
        log.debug("Starting to play an OGG stream track {}", identifier)

        executor.executeProcessingLoop({
            try {
                processTrackLoop(packetInputStream, executor.processingContext)
            } catch (e: IOException) {
                throw FriendlyException(
                    "Stream broke when playing OGG track.",
                    FriendlyException.Severity.SUSPICIOUS,
                    e
                )
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

    companion object {
        private val log = LoggerFactory.getLogger(OggAudioTrack::class.java)
    }
}
