package com.sedmelluq.discord.lavaplayer.container.common

import com.sedmelluq.discord.lavaplayer.filter.AudioPipeline
import com.sedmelluq.discord.lavaplayer.filter.AudioPipelineFactory
import com.sedmelluq.discord.lavaplayer.filter.PcmFormat
import com.sedmelluq.discord.lavaplayer.natives.aac.AacDecoder
import com.sedmelluq.discord.lavaplayer.tools.extensions.into
import com.sedmelluq.discord.lavaplayer.track.playback.AudioProcessingContext
import mu.KotlinLogging
import net.sourceforge.jaad.aac.Decoder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer

class AacPacketRouter(
    private val context: AudioProcessingContext,
) {
    companion object {
        private val log = KotlinLogging.logger { }
    }

    private var initialRequestedTimecode: Long? = null
    private var initialProvidedTimecode: Long? = null
    private var outputBuffer: ShortBuffer? = null
    private var downstream: AudioPipeline? = null

    var nativeDecoder: AacDecoder? = null
    var embeddedDecoder: Decoder? = null

    @Throws(InterruptedException::class)
    fun processInput(inputBuffer: ByteBuffer) {
        if (embeddedDecoder == null) {
            nativeDecoder!!.fill(inputBuffer)
            if (downstream == null) {
                log.debug { "Using Native AAC Decoder" }

                val streamInfo = nativeDecoder!!.resolveStreamInfo()
                if (streamInfo != null) {
                    downstream = AudioPipelineFactory.create(
                        context = context,
                        inputFormat = PcmFormat(streamInfo.channels, streamInfo.sampleRate)
                    )

                    outputBuffer = ByteBuffer
                        .allocateDirect(2 * streamInfo.frameSize * streamInfo.channels)
                        .order(ByteOrder.nativeOrder())
                        .asShortBuffer()

                    if (initialRequestedTimecode != null) {
                        downstream?.seekPerformed(initialRequestedTimecode!!, initialProvidedTimecode!!)
                    }
                }
            }

            if (downstream != null) {
                while (nativeDecoder!!.decode(outputBuffer!!, false)) {
                    downstream?.process(outputBuffer!!)
                    outputBuffer?.clear()
                }
            }
        } else {
            if (downstream == null) {
                log.debug { "Using Embedded AAC Decoder" }
                downstream = AudioPipelineFactory.create(
                    context = context,
                    inputFormat = PcmFormat(
                        embeddedDecoder!!.audioFormat.channels,
                        embeddedDecoder!!.audioFormat.sampleRate.into()
                    )
                )

                if (initialRequestedTimecode != null) {
                    downstream?.seekPerformed(initialRequestedTimecode!!, initialProvidedTimecode!!)
                }
            }

            if (downstream != null) {
                downstream!!.process(embeddedDecoder!!.decodeFrame(inputBuffer.array()));
            }
        }
    }

    fun seekPerformed(requestedTimecode: Long, providedTimecode: Long) {
        if (downstream != null) {
            downstream!!.seekPerformed(requestedTimecode, providedTimecode)
        } else {
            initialRequestedTimecode = requestedTimecode
            initialProvidedTimecode = providedTimecode
        }

        if (nativeDecoder != null) {
            nativeDecoder!!.close()
            nativeDecoder = null
        }
    }

    @Throws(InterruptedException::class)
    fun flush() {
        if (downstream != null) {
            while (nativeDecoder!!.decode(outputBuffer!!, true)) {
                downstream!!.process(outputBuffer!!)
                outputBuffer!!.clear()
            }
        }
    }

    fun close() {
        try {
            if (downstream != null) {
                downstream!!.close()
            }
        } finally {
            if (nativeDecoder != null) {
                nativeDecoder!!.close()
            }
        }
    }
}
