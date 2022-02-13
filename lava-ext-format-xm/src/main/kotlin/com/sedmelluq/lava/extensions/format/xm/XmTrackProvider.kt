package com.sedmelluq.lava.extensions.format.xm

import com.sedmelluq.discord.lavaplayer.filter.AudioPipelineFactory
import com.sedmelluq.discord.lavaplayer.filter.PcmFormat
import com.sedmelluq.discord.lavaplayer.track.playback.AudioProcessingContext
import ibxm.IBXM
import java.io.Closeable

public class XmTrackProvider(
    context: AudioProcessingContext,
    private val ibxm: IBXM,
) : Closeable {
    private val downstream = AudioPipelineFactory.create(context, PcmFormat(2, ibxm.sampleRate))

    public fun provideFrames()  {
        val buffer = IntArray(ibxm.mixBufferLength)
        val shortBuffer = ShortArray(ibxm.mixBufferLength)

        while (true) {
            val blockCount = ibxm.getAudio(buffer)
            if (blockCount <= 0) {
                break
            }

            for (i in 0 until ibxm.mixBufferLength) {
                shortBuffer[i] = buffer[i]
                    .coerceIn(Short.MIN_VALUE..Short.MAX_VALUE)
                    .toShort()
            }

            downstream.process(shortBuffer, 0, blockCount * 2);
        }
    }

    public override fun close() {
        downstream.close();
    }
}
