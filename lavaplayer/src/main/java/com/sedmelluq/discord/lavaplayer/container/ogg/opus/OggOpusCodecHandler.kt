package com.sedmelluq.discord.lavaplayer.container.ogg.opus

import com.sedmelluq.discord.lavaplayer.container.ogg.*
import com.sedmelluq.discord.lavaplayer.container.ogg.vorbis.VorbisCommentParser
import com.sedmelluq.discord.lavaplayer.tools.extensions.reverseBytes
import com.sedmelluq.discord.lavaplayer.tools.io.DirectBufferStreamBroker
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.experimental.and

/**
 * Loader for Opus track providers from an OGG stream.
 */
object OggOpusCodecHandler : OggCodecHandler {
    internal val OPUS_IDENTIFIER = ByteBuffer.wrap(byteArrayOf('O'.code.toByte(), 'p'.code.toByte(), 'u'.code.toByte(), 's'.code.toByte())).int
    private val HEAD_TAG_HALF = ByteBuffer.wrap(byteArrayOf('H'.code.toByte(), 'e'.code.toByte(), 'a'.code.toByte(), 'd'.code.toByte())).int
    private val OPUS_TAG_HALF = ByteBuffer.wrap(byteArrayOf('O'.code.toByte(), 'p'.code.toByte(), 'u'.code.toByte(), 's'.code.toByte())).int
    private val TAGS_TAG_HALF = ByteBuffer.wrap(byteArrayOf('T'.code.toByte(), 'a'.code.toByte(), 'g'.code.toByte(), 's'.code.toByte())).int
    private const val MAX_COMMENTS_SAVED_LENGTH = 1024 * 60 // 60 KB
    private const val MAX_COMMENTS_READ_LENGTH = 1024 * 1024 * 120 // 120 MB

    override fun isMatchingIdentifier(identifier: Int): Boolean {
        return identifier == OPUS_IDENTIFIER
    }

    override val maximumFirstPacketLength: Int
        get() = 276

    @Throws(IOException::class)
    override fun loadBlueprint(stream: OggPacketInputStream, broker: DirectBufferStreamBroker): OggTrackBlueprint {
        val firstPacket = broker.buffer
        verifyFirstPacket(firstPacket)
        loadCommentsHeader(stream, broker, true)

        val channelCount = firstPacket[9] and 0xFF.toByte()
        return Blueprint(broker, channelCount.toInt(), getSampleRate(firstPacket))
    }

    @Throws(IOException::class)
    override fun loadMetadata(stream: OggPacketInputStream, broker: DirectBufferStreamBroker): OggMetadata? {
        val firstPacket = broker.buffer
        verifyFirstPacket(firstPacket)
        loadCommentsHeader(stream, broker, false)

        return OggMetadata(
            parseTags(broker.buffer, broker.isTruncated),
            detectLength(stream, getSampleRate(firstPacket))
        )
    }

    private fun parseTags(tagBuffer: ByteBuffer, truncated: Boolean): Map<String, String> {
        return if (tagBuffer.int != OPUS_TAG_HALF || tagBuffer.int != TAGS_TAG_HALF) {
            emptyMap()
        } else {
            VorbisCommentParser.parse(tagBuffer, truncated)
        }
    }

    @Throws(IOException::class)
    private fun detectLength(stream: OggPacketInputStream, sampleRate: Int): Long? {
        val sizeInfo = stream.seekForSizeInfo(sampleRate)
            ?: return null

        return sizeInfo.totalSamples * 1000 / sizeInfo.sampleRate
    }

    private fun verifyFirstPacket(firstPacket: ByteBuffer) {
        check(firstPacket.getInt(4) == HEAD_TAG_HALF) { "First packet is not an OpusHead." }
    }

    private fun getSampleRate(firstPacket: ByteBuffer): Int {
        return firstPacket.getInt(12).reverseBytes()
    }

    @Throws(IOException::class)
    private fun loadCommentsHeader(stream: OggPacketInputStream, broker: DirectBufferStreamBroker, skip: Boolean) {
        check(stream.startNewPacket()) { "No OpusTags packet in track." }
        if (!broker.consumeNext(stream, if (skip) 0 else MAX_COMMENTS_SAVED_LENGTH, MAX_COMMENTS_READ_LENGTH)) {
            check(stream.isPacketComplete) { "Opus comments header packet longer than allowed." }
        }
    }

    private class Blueprint(
        private val broker: DirectBufferStreamBroker,
        private val channelCount: Int,
        private val sampleRate: Int,
    ) : OggTrackBlueprint {
        override fun loadTrackHandler(stream: OggPacketInputStream): OggTrackHandler {
            broker.clear()
            return OggOpusTrackHandler(stream, broker, channelCount, sampleRate)
        }
    }
}
