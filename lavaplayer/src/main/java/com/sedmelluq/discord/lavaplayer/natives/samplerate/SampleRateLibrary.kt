package com.sedmelluq.discord.lavaplayer.natives.samplerate

import com.sedmelluq.discord.lavaplayer.natives.ConnectorNativeLibLoader

internal class SampleRateLibrary private constructor() {
    external fun create(type: Int, channels: Int): Long

    external fun destroy(instance: Long)

    external fun reset(instance: Long)

    external fun process(
        instance: Long,
        input: FloatArray,
        inputOffset: Int,
        inputLength: Int,
        output: FloatArray,
        outputOffset: Int,
        outputLength: Int,
        endOfInput: Boolean,
        sourceRatio: Double,
        progress: IntArray
    ): Int

    companion object {
        @JvmStatic
        val instance: SampleRateLibrary
            get() {
                ConnectorNativeLibLoader.loadConnectorLibrary()
                return SampleRateLibrary()
            }
    }
}
