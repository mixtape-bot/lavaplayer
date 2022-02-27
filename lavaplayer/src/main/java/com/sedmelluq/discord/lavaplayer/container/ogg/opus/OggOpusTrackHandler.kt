package com.sedmelluq.discord.lavaplayer.container.ogg.opus

import com.sedmelluq.discord.lavaplayer.container.ogg.OggPacketInputStream
import com.sedmelluq.discord.lavaplayer.tools.io.DirectBufferStreamBroker
import com.sedmelluq.discord.lavaplayer.container.ogg.OggTrackHandler
import com.sedmelluq.discord.lavaplayer.container.common.OpusPacketRouter
import com.sedmelluq.discord.lavaplayer.track.playback.AudioProcessingContext
import java.lang.InterruptedException
import java.io.IOException
import java.lang.RuntimeException
import java.lang.UnsupportedOperationException

/**
 * OGG stream handler for Opus codec.
 */
class OggOpusTrackHandler
/**
 * @param packetInputStream OGG packet input stream
 * @param broker            Broker for loading stream data into direct byte buffer.
 * @param channelCount      Number of channels in the track.
 * @param sampleRate        Sample rate of the track.
 */(
    private val packetInputStream: OggPacketInputStream,
    private val broker: DirectBufferStreamBroker,
    private val channelCount: Int,
    private val sampleRate: Int
) : OggTrackHandler {
    private var opusPacketRouter: OpusPacketRouter? = null
    override fun initialize(context: AudioProcessingContext, timecode: Long, desiredTimecode: Long) {
        opusPacketRouter = OpusPacketRouter(context, sampleRate, channelCount)
        opusPacketRouter!!.seekPerformed(desiredTimecode, timecode)
    }

    @Throws(InterruptedException::class)
    override fun provideFrames() {
        try {
            while (packetInputStream.startNewPacket()) {
                broker.consumeNext(packetInputStream, Int.MAX_VALUE, Int.MAX_VALUE)
                val buffer = broker.buffer
                if (buffer.remaining() > 0) {
                    opusPacketRouter!!.process(buffer)
                }
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    override fun seekToTimecode(timecode: Long) {
        throw UnsupportedOperationException()
    }

    override fun close() {
        if (opusPacketRouter != null) {
            opusPacketRouter!!.close()
        }
    }
}
