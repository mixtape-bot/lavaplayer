package com.sedmelluq.discord.lavaplayer.container.matroska

import com.sedmelluq.discord.lavaplayer.container.common.AacPacketRouter
import com.sedmelluq.discord.lavaplayer.container.matroska.format.MatroskaFileTrack
import com.sedmelluq.discord.lavaplayer.natives.aac.AacDecoder
import com.sedmelluq.discord.lavaplayer.track.playback.AudioProcessingContext
import mu.KotlinLogging
import net.sourceforge.jaad.aac.Decoder
import java.nio.ByteBuffer


/**
 * Consumes AAC track data from a matroska file.
 *
 * @param context Configuration and output information for processing
 * @param track   The MP4 audio track descriptor
 */
class MatroskaAacTrackConsumer(context: AudioProcessingContext, override val track: MatroskaFileTrack) :
    MatroskaTrackConsumer {
    companion object {
        private val log = KotlinLogging.logger { }
    }

    private lateinit var inputBuffer: ByteBuffer
    private val packetRouter = AacPacketRouter(context)

    override fun initialize() {
        log.debug { "Initialising AAC track with expected frequency ${track.audio.samplingFrequency} and channel count ${track.audio.channels}." }
    }

    override fun seekPerformed(requestedTimecode: Long, providedTimecode: Long) {
        packetRouter.seekPerformed(requestedTimecode, providedTimecode)
    }

    @Throws(InterruptedException::class)
    override fun flush() {
        packetRouter.flush()
    }

    @Throws(InterruptedException::class)
    override fun consume(data: ByteBuffer) {
        if (packetRouter.nativeDecoder == null) {
            packetRouter.nativeDecoder = AacDecoder()
            inputBuffer = ByteBuffer.allocateDirect(4096)
        }

        if (configureDecoder(packetRouter.nativeDecoder!!)) {
            processInput(data)
        } else {
            if (packetRouter.embeddedDecoder == null) {
                packetRouter.embeddedDecoder = Decoder.create(track.codecPrivate)
                inputBuffer = ByteBuffer.allocate(4096)
            }

            processInput(data)
        }
    }

    private fun processInput(data: ByteBuffer) {
        while (data.hasRemaining()) {
            val chunk = data.remaining().coerceAtMost(inputBuffer.capacity())
            val chunkBuffer = data.duplicate()
            chunkBuffer.limit(chunkBuffer.position() + chunk)

            inputBuffer.clear()
            inputBuffer.put(chunkBuffer)
            inputBuffer.flip()

            packetRouter.processInput(inputBuffer)
            data.position(chunkBuffer.position())
        }
    }

    override fun close() {
        packetRouter.close()
    }

    private fun configureDecoder(decoder: AacDecoder): Boolean {
        return decoder.configure(track.codecPrivate) == 0
    }
}
