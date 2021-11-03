package com.sedmelluq.discord.lavaplayer.format

import com.sedmelluq.discord.lavaplayer.format.transcoder.AudioChunkDecoder
import com.sedmelluq.discord.lavaplayer.format.transcoder.AudioChunkEncoder
import com.sedmelluq.discord.lavaplayer.manager.AudioConfiguration

/**
 * Describes the format for audio with fixed chunk size.
 *
 * @param channelCount     Number of channels.
 * @param sampleRate       Sample rate (frequency).
 * @param chunkSampleCount Number of samples in one chunk.
 */
abstract class AudioDataFormat(
    /**
     * Number of channels.
     */
    @JvmField
    val channelCount: Int,
    /**
     * Sample rate (frequency).
     */
    @JvmField
    val sampleRate: Int,
    /**
     * Number of samples in one chunk.
     */
    @JvmField
    val chunkSampleCount: Int
) {
    /**
     * The name of the codec.
     */
    abstract val codecName: String

    /**
     * Generally expected average size of a frame in this format.
     */
    abstract val expectedChunkSize: Int

    /**
     * Maximum size of a frame in this format.
     */
    abstract val maximumChunkSize: Int

    /**
     * Total number of samples in one frame.
     */
    val totalSampleCount: Int
        get() = chunkSampleCount * channelCount

    /**
     * The duration in milliseconds of one frame in this format.
     */
    val frameDuration: Long
        get() = chunkSampleCount * 1000L / sampleRate

    /**
     * Byte array representing a frame of silence in this format.
     */
    abstract val silenceBytes: ByteArray

    /**
     * @return Decoder to convert data in this format to short PCM.
     */
    abstract fun createDecoder(): AudioChunkDecoder

    /**
     * @param configuration Configuration to use for encoding.
     * @return Encoder to convert data in short PCM format to this format.
     */
    abstract fun createEncoder(configuration: AudioConfiguration): AudioChunkEncoder

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        if (other == null || javaClass != other.javaClass) {
            return false
        }

        val that = other as AudioDataFormat
        if (channelCount != that.channelCount) {
            return false
        }

        if (sampleRate != that.sampleRate) {
            return false
        }

        return if (chunkSampleCount != that.chunkSampleCount) false else codecName == that.codecName
    }

    override fun hashCode(): Int {
        var result = channelCount
        result = 31 * result + sampleRate
        result = 31 * result + chunkSampleCount
        result = 31 * result + codecName.hashCode()
        return result
    }
}
