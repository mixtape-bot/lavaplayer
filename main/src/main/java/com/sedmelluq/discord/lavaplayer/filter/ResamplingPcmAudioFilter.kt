package com.sedmelluq.discord.lavaplayer.filter

import com.sedmelluq.discord.lavaplayer.manager.AudioConfiguration
import com.sedmelluq.discord.lavaplayer.natives.samplerate.SampleRateConverter
import com.sedmelluq.discord.lavaplayer.natives.samplerate.SampleRateConverter.ResamplingType

/**
 * Filter which resamples audio to the specified sample rate
 *
 * @param configuration Configuration to use
 * @param channels      Number of channels in input data
 * @param downstream    Next filter in chain
 * @param sourceRate    Source sample rate
 * @param targetRate    Target sample rate
 */
class ResamplingPcmAudioFilter(
    configuration: AudioConfiguration, channels: Int, private val downstream: FloatPcmAudioFilter,
    sourceRate: Int, targetRate: Int
) : FloatPcmAudioFilter {
    companion object {
        private const val BUFFER_SIZE = 4096

        @JvmField
        val RESAMPLING_VALUES: MutableMap<ResamplingQuality, ResamplingType> = mutableMapOf(
            ResamplingQuality.HIGH to ResamplingType.SINC_MEDIUM_QUALITY,
            ResamplingQuality.MEDIUM to ResamplingType.SINC_FASTEST,
            ResamplingQuality.LOW to ResamplingType.LINEAR
        )

        private fun getResamplingType(quality: ResamplingQuality): ResamplingType? {
            return RESAMPLING_VALUES[quality]
        }
    }

    private val progress = SampleRateConverter.Progress()
    private val outputSegments: Array<FloatArray> = Array(channels) { FloatArray(BUFFER_SIZE) }
    private val converters: Array<SampleRateConverter>

    init {
        val type = getResamplingType(configuration.resamplingQuality)
        converters = Array(channels) { SampleRateConverter(type, 1, sourceRate, targetRate) }
    }

    override fun seekPerformed(requestedTime: Long, providedTime: Long) {
        converters.forEach(SampleRateConverter::reset)
    }

    @Throws(InterruptedException::class)
    override fun flush() {
        // Nothing to do.
    }

    override fun close() {
        converters.forEach(SampleRateConverter::close)
    }

    @Throws(InterruptedException::class)
    override fun process(input: Array<FloatArray>, offset: Int, length: Int) {
        var inputOffset = offset
        var remainingLength = length

        do {
            for (i in input.indices) {
                converters[i].process(input[i], inputOffset, remainingLength, outputSegments[i], 0, BUFFER_SIZE, false, progress)
            }

            inputOffset += progress.inputUsed
            remainingLength -= progress.inputUsed
            if (progress.outputGenerated > 0) {
                downstream.process(outputSegments, 0, progress.outputGenerated)
            }
        } while (remainingLength > 0 || progress.outputGenerated == BUFFER_SIZE)
    }
}
