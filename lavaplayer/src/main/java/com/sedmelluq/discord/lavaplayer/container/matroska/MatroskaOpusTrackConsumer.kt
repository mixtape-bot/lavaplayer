package com.sedmelluq.discord.lavaplayer.container.matroska

import com.sedmelluq.discord.lavaplayer.container.common.OpusPacketRouter
import com.sedmelluq.discord.lavaplayer.container.matroska.format.MatroskaFileTrack
import com.sedmelluq.discord.lavaplayer.track.playback.AudioProcessingContext
import java.nio.ByteBuffer

/**
 * Consumes OPUS track data from a matroska file.
 *
 * @param context Configuration and output information for processing
 * @param track   The associated matroska track
 */
class MatroskaOpusTrackConsumer(context: AudioProcessingContext, override val track: MatroskaFileTrack) : MatroskaTrackConsumer {
    private val opusPacketRouter = OpusPacketRouter(context, track.audio.samplingFrequency.toInt(), track.audio.channels)

    override fun seekPerformed(requestedTimecode: Long, providedTimecode: Long) {
        opusPacketRouter.seekPerformed(requestedTimecode, providedTimecode)
    }

    @Throws(InterruptedException::class)
    override fun flush() {
        opusPacketRouter.flush()
    }

    @Throws(InterruptedException::class)
    override fun consume(data: ByteBuffer) {
        opusPacketRouter.process(data)
    }

    override fun close() {
        opusPacketRouter.close()
    }
}
