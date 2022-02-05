package com.sedmelluq.discord.lavaplayer.container.mpeg

import com.sedmelluq.discord.lavaplayer.container.common.AacPacketRouter
import com.sedmelluq.discord.lavaplayer.natives.aac.AacDecoder
import com.sedmelluq.discord.lavaplayer.tools.extensions.toRuntimeException
import com.sedmelluq.discord.lavaplayer.track.playback.AudioProcessingContext
import mu.KotlinLogging
import net.sourceforge.jaad.aac.Decoder
import org.apache.commons.io.IOUtils
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.ClosedByInterruptException
import java.nio.channels.ReadableByteChannel


/**
 * Handles processing MP4 AAC frames. Passes the decoded frames to the specified frame consumer. Currently, only AAC LC
 * format is supported, although the underlying decoder can handler other types as well.
 *
 * @param context Configuration and output information for processing
 * @param track   The MP4 audio track descriptor
 */
class MpegAacTrackConsumer(context: AudioProcessingContext, override val track: MpegTrackInfo) : MpegTrackConsumer {
    companion object {
        private val log = KotlinLogging.logger {  }
    }

    private var inputBuffer = ByteBuffer.allocateDirect(4096)
    private val packetRouter = AacPacketRouter(context)

    override fun initialise() {
        log.debug { "Initializing AAC track with expected frequency ${track.sampleRate} and channel count ${track.channelCount}." }
    }

    override fun seekPerformed(requestedTimecode: Long, providedTimecode: Long) {
        packetRouter.seekPerformed(requestedTimecode, providedTimecode)
    }

    override fun flush() {
        packetRouter.flush()
    }

    override fun consume(channel: ReadableByteChannel, length: Int) {
        if (packetRouter.nativeDecoder == null) {
            packetRouter.nativeDecoder = AacDecoder()
            inputBuffer = ByteBuffer.allocateDirect(4096)
        }

        if (configureDecoder(packetRouter.nativeDecoder!!)) {
            processInput(channel, length)
        } else {
            if (packetRouter.embeddedDecoder == null) {
                if (track.decoderConfig != null) {
                    packetRouter.embeddedDecoder = Decoder.create(track.decoderConfig)
                } else {
                    packetRouter.embeddedDecoder = Decoder.create(AacDecoder.AAC_LC, track.sampleRate, track.channelCount)
                }

                inputBuffer = ByteBuffer.allocate(4096)
            }
            processInput(channel, length)
        }
    }

    fun processInput(channel: ReadableByteChannel, length: Int) {
        var remaining = length
        while (remaining > 0) {
            val chunk = remaining.coerceAtMost(inputBuffer.capacity())
            inputBuffer.clear()
            inputBuffer.limit(chunk)

            try {
                IOUtils.readFully(channel, inputBuffer)
            } catch (e: ClosedByInterruptException) {
                log.trace(e) { "Interrupt received while reading channel" }
                Thread.currentThread().interrupt()
                throw InterruptedException()
            } catch (e: IOException) {
                throw e.toRuntimeException()
            }

            inputBuffer.flip()
            packetRouter.processInput(inputBuffer)
            remaining -= chunk
        }
    }

    override fun close() {
        packetRouter.close()
    }

    private fun configureDecoder(decoder: AacDecoder): Boolean {
        val error = if (track.decoderConfig != null) {
            decoder.configure(track.decoderConfig)
        } else {
            decoder.configure(AacDecoder.AAC_LC, track.sampleRate, track.channelCount)
        }

        return error == 0
    }
}
