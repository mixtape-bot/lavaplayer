package com.sedmelluq.discord.lavaplayer.container.ogg

import com.sedmelluq.discord.lavaplayer.container.ogg.flac.OggFlacCodecHandler
import com.sedmelluq.discord.lavaplayer.container.ogg.opus.OggOpusCodecHandler
import com.sedmelluq.discord.lavaplayer.container.ogg.vorbis.OggVorbisCodecHandler
import com.sedmelluq.discord.lavaplayer.tools.io.DirectBufferStreamBroker
import java.io.IOException

/**
 * Track loader for an OGG packet stream. Automatically detects the track codec and loads the specific track handler.
 */
object OggTrackLoader {
    private val TRACK_PROVIDERS: List<OggCodecHandler> = listOf(
        OggOpusCodecHandler,
        OggFlacCodecHandler.INSTANCE,
        OggVorbisCodecHandler.INSTANCE,
    )

    private val MAXIMUM_FIRST_PACKET_LENGTH = TRACK_PROVIDERS.maxOf { it.maximumFirstPacketLength }

    /**
     * @param packetInputStream OGG packet input stream
     * @return The track handler detected from this packet input stream. Returns null if the stream ended.
     * @throws IOException           On read error
     * @throws IllegalStateException If the track uses an unknown codec.
     */
    fun loadTrackBlueprint(inputStream: OggPacketInputStream): OggTrackBlueprint? {
        return detectCodec(inputStream)?.let { it.handler.loadBlueprint(inputStream, it.broker) }
    }

    fun loadMetadata(inputStream: OggPacketInputStream): OggMetadata? {
        return detectCodec(inputStream)?.let { it.handler.loadMetadata(inputStream, it.broker) }
    }

    private fun detectCodec(stream: OggPacketInputStream): CodecDetection? {
        if (!stream.startNewTrack() || !stream.startNewPacket()) {
            return null
        }

        val broker = DirectBufferStreamBroker(1024)
        val maximumLength = MAXIMUM_FIRST_PACKET_LENGTH + 1

        if (!broker.consumeNext(stream, maximumLength, maximumLength)) {
            throw IOException("First packet is too large for any known OGG codec.")
        }

        val headerIdentifier = broker.buffer.getInt()
        for (provider in TRACK_PROVIDERS) {
            if (provider.isMatchingIdentifier(headerIdentifier)) {
                return CodecDetection(provider, broker)
            }
        }

        error("Unsupported track in OGG stream.")
    }

    data class CodecDetection(val handler: OggCodecHandler, val broker: DirectBufferStreamBroker)
}
