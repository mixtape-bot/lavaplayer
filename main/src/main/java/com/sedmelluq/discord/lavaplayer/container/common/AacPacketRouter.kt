package com.sedmelluq.discord.lavaplayer.container.common

import com.sedmelluq.discord.lavaplayer.filter.AudioPipeline
import com.sedmelluq.discord.lavaplayer.filter.AudioPipelineFactory
import com.sedmelluq.discord.lavaplayer.filter.PcmFormat
import com.sedmelluq.discord.lavaplayer.natives.aac.AacDecoder
import com.sedmelluq.discord.lavaplayer.track.playback.AudioProcessingContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer

class AacPacketRouter(
    private val context: AudioProcessingContext,
    private val decoderConfigurer: (AacDecoder) -> Unit
) {
    private var initialRequestedTimecode: Long? = null
    private var initialProvidedTimecode: Long? = null
    private var outputBuffer: ShortBuffer? = null
    private var downstream: AudioPipeline? = null
    private var decoder: AacDecoder? = null

    @Throws(InterruptedException::class)
    fun processInput(inputBuffer: ByteBuffer?) {
        if (decoder == null) {
            decoder = AacDecoder()
            decoderConfigurer.invoke(decoder!!)
        }

        decoder!!.fill(inputBuffer!!)
        if (downstream == null) {
            val streamInfo = decoder!!.resolveStreamInfo()
            if (streamInfo != null) {
                downstream = AudioPipelineFactory.create(context, PcmFormat(streamInfo.channels, streamInfo.sampleRate))
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
            while (decoder!!.decode(outputBuffer!!, false)) {
                downstream?.process(outputBuffer!!)
                outputBuffer?.clear()
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

        if (decoder != null) {
            decoder!!.close()
            decoder = null
        }
    }

    @Throws(InterruptedException::class)
    fun flush() {
        if (downstream != null) {
            while (decoder!!.decode(outputBuffer!!, true)) {
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
            if (decoder != null) {
                decoder!!.close()
            }
        }
    }
}
