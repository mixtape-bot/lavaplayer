package com.sedmelluq.discord.lavaplayer.filter.equalizer

import com.sedmelluq.discord.lavaplayer.filter.FloatPcmAudioFilter
import com.sedmelluq.discord.lavaplayer.format.AudioDataFormat
import kotlin.math.max
import kotlin.math.min

/**
 * An equalizer PCM filter. Applies the equalizer with configuration specified by band multipliers (either set
 * externally or using [Equalizer.setGain]).
 *
 * @param channelCount    Number of channels in the input.
 * @param next            The next filter in the chain.
 * @param bandMultipliers The band multiplier values. Keeps using this array internally, so the values can be changed
 * externally.
 */
class Equalizer @JvmOverloads constructor(
    channelCount: Int,
    private val next: FloatPcmAudioFilter,
    bandMultipliers: FloatArray = FloatArray(EqualizerConstants.BAND_COUNT)
) : EqualizerConfiguration(bandMultipliers), FloatPcmAudioFilter {
    companion object {
        /**
         * @param format Audio output format.
         * @return `true` if the output format is compatible for the equalizer (based on sample rate).
         */
        fun isCompatible(format: AudioDataFormat): Boolean =
            format.sampleRate == EqualizerConstants.SAMPLE_RATE
    }

    /**
     * Number of bands in the equalizer.
     */
    private val channels = List(channelCount) { ChannelProcessor(bandMultipliers) }

    override fun process(input: Array<FloatArray>, offset: Int, length: Int) {
        for (channelIndex in channels.indices) {
            channels[channelIndex].process(input[channelIndex], offset, offset + length)
        }

        next.process(input, offset, length)
    }

    override fun seekPerformed(requestedTime: Long, providedTime: Long) {
        for (channel in channels) {
            channel.reset()
        }
    }

    override fun flush() {
        // Nothing to do here.
    }

    override fun close() {
        // Nothing to do here.
    }

    private class ChannelProcessor(private val bandMultipliers: FloatArray) {
        private val history = FloatArray(EqualizerConstants.BAND_COUNT * 6)
        private var current: Int = 0
        private var minusOne: Int = 2
        private var minusTwo: Int = 1

        fun process(samples: FloatArray, startIndex: Int, endIndex: Int) {
            for (sampleIndex in startIndex until endIndex) {
                val sample = samples[sampleIndex]
                var result = sample * 0.25f

                for (bandIndex in 0 until EqualizerConstants.BAND_COUNT) {
                    val x = bandIndex * 6
                    val y = x + 3

                    val (beta, alpha, gamma) = EqualizerConstants.COEFFICIENTS[bandIndex]
                    val bandResult =
                        alpha * (sample - history[x + minusTwo]) +
                        gamma * history[y + minusOne] -
                        beta * history[y + minusTwo]

                    history[x + current] = sample
                    history[y + current] = bandResult

                    result += bandResult * bandMultipliers[bandIndex]
                }

                samples[sampleIndex] = min(max(result * 4.0f, -1.0f), 1.0f)

                if (++current == 3) {
                    current = 0
                }

                if (++minusOne == 3) {
                    minusOne = 0
                }

                if (++minusTwo == 3) {
                    minusTwo = 0
                }
            }
        }

        fun reset() {
            history.fill(0.0f)
        }
    }
}
