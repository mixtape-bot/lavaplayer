package com.sedmelluq.discord.lavaplayer.track.encoder

import com.sedmelluq.discord.lavaplayer.source.ItemSourceManager
import com.sedmelluq.discord.lavaplayer.tools.extensions.toRuntimeException
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.lava.common.tools.extensions.readNullableUTF
import com.sedmelluq.lava.common.tools.extensions.writeNullableUTF
import com.sedmelluq.lava.common.tools.io.MessageInput
import com.sedmelluq.lava.common.tools.io.MessageOutput
import com.sedmelluq.lava.track.info.AudioTrackInfo
import java.io.*

abstract class DefaultTrackEncoder : TrackEncoder {
    /**
     * The list of enabled source managers.
     */
    abstract val sourceManagers: List<ItemSourceManager>

    @Throws(IOException::class)
    override fun encodeTrack(stream: MessageOutput, track: AudioTrack, version: Int) {
        val output = stream.startMessage()
        output.write(version)

        /* encode the track's info. */
        val encoder = AudioTrackInfo.VERSION_ENCODERS[version]
            ?: error("Encoder for track info version: $version was not found.")

        encoder.encode(track.info, output)

        /* encode per-source details */
        encodeTrackDetails(output, track, version)

        /* encode the track position (???) */
        output.writeLong(track.position)

        stream.commitMessage(TrackEncoder.TRACK_INFO_VERSIONED)
    }

    /**
     * Encodes an audio track to the provided byte stream. Does not include AudioTrackInfo in the buffer.
     *
     * @param output  The [DataOutput] to write to
     * @param track   The [AudioTrackInfo] to encode
     * @param version The version of the track to encode
     *
     * @throws IOException if something went wrong while writing the track.
     */
    @Throws(IOException::class)
    fun encodeTrackDetails(output: DataOutput, track: AudioTrack, version: Int) {
        val sourceManager = track.sourceManager
        output.writeNullableUTF(sourceManager?.sourceName)

        sourceManager?.encodeTrack(track, output, version)
    }

    /**
     * Encodes an audio track to a byte array. Does not include AudioTrackInfo in the buffer.
     *
     * @param track   The track to encode
     * @param version The version of the track to encode
     *
     * @return The bytes of the encoded data
     */
    fun encodeTrackDetails(track: AudioTrack, version: Int): ByteArray {
        return try {
            val byteOutput = ByteArrayOutputStream()
            val output: DataOutput = DataOutputStream(byteOutput)
            encodeTrackDetails(output, track, version)

            byteOutput.toByteArray()
        } catch (e: IOException) {
            throw e.toRuntimeException()
        }
    }


    @Throws(IOException::class)
    override fun decodeTrack(stream: MessageInput): DecodedTrackHolder? {
        val input = stream.nextMessage()
            ?: return null

        val version = AudioTrackInfo.getVersion(stream, input)

        /* read versioned track info */
        val decoder = AudioTrackInfo.VERSION_ENCODERS[version]
            ?: error("Decoder for track info version: $version was not found.")

        val trackInfo = decoder.decode(input)
            ?: return null

        /* read per-source bytes */
        return DecodedTrackHolder(decodeTrackDetails(input, trackInfo, version)).also {
            /* skip any remaining bytes. */
            stream.skipRemainingBytes()
        }
    }

    /**
     * Decodes an audio track from the provided byte stream. Does not include AudioTrackInfo in the buffer.
     *
     * @param output  The [DataOutput] to write to
     * @param track   The [AudioTrackInfo] to encode
     * @param version The version of the track to encode
     *
     * @throws IOException if something went wrong while writing the track.
     */
    @Throws(IOException::class)
    fun decodeTrackDetails(input: DataInput, trackInfo: AudioTrackInfo, version: Int): AudioTrack? {
        val sourceName = input.readNullableUTF()
            ?: return null

        val sourceManager = sourceManagers.find { it.sourceName == sourceName }
            ?: return null

        return sourceManager.decodeTrack(trackInfo, input, version)
    }

    /**
     * Decodes an audio track from a byte array.
     *
     * @param trackInfo Track info for the track to decode
     * @param buffer    Byte array containing the encoded track
     * @return Decoded audio track
     */
    fun decodeTrackDetails(buffer: ByteArray, trackInfo: AudioTrackInfo, version: Int): AudioTrack? {
        return try {
            val input = DataInputStream(ByteArrayInputStream(buffer))
            decodeTrackDetails(input, trackInfo, version)
        } catch (e: IOException) {
            throw e.toRuntimeException()
        }
    }
}
