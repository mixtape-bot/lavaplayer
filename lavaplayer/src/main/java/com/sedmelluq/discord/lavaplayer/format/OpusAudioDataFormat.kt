package com.sedmelluq.discord.lavaplayer.format

import com.sedmelluq.discord.lavaplayer.format.transcoder.AudioChunkDecoder
import com.sedmelluq.discord.lavaplayer.format.transcoder.AudioChunkEncoder
import com.sedmelluq.discord.lavaplayer.format.transcoder.OpusChunkDecoder
import com.sedmelluq.discord.lavaplayer.format.transcoder.OpusChunkEncoder
import com.sedmelluq.discord.lavaplayer.manager.AudioConfiguration

/**
 * An [AudioDataFormat] for OPUS.
 *
 * @param channelCount     Number of channels.
 * @param sampleRate       Sample rate (frequency).
 * @param chunkSampleCount Number of samples in one chunk.
 */
class OpusAudioDataFormat(
    channelCount: Int,
    sampleRate: Int,
    chunkSampleCount: Int
) : AudioDataFormat(channelCount, sampleRate, chunkSampleCount) {
    companion object {
        const val CODEC_NAME = "OPUS"
        private val SILENT_OPUS_FRAME = byteArrayOf(0xFC.toByte(), 0xFF.toByte(), 0xFE.toByte())
    }

    override val maximumChunkSize: Int = 32 + 1536 * chunkSampleCount / 960

    override val expectedChunkSize: Int = 32 + 512 * chunkSampleCount / 960

    override val codecName: String
        get() = CODEC_NAME

    override val silenceBytes: ByteArray
        get() = SILENT_OPUS_FRAME

    override fun createDecoder(): AudioChunkDecoder =
        OpusChunkDecoder(this)

    override fun createEncoder(configuration: AudioConfiguration): AudioChunkEncoder =
        OpusChunkEncoder(configuration, this)

    override fun equals(other: Any?): Boolean =
        this === other || other != null && javaClass == other.javaClass && super.equals(other)
}
