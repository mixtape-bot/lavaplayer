package com.sedmelluq.discord.lavaplayer.natives.vorbis

import com.sedmelluq.lava.common.natives.NativeResourceHolder
import java.nio.ByteBuffer

/**
 * A wrapper around the native methods of AacDecoder, which uses libvorbis native library.
 */
class VorbisDecoder : NativeResourceHolder() {
    private val library: VorbisDecoderLibrary = VorbisDecoderLibrary.instance
    private val instance: Long = library.create()

    /**
     * Get the number of channels, valid only after initialisation.
     *
     * @return Number of channels
     */
    var channelCount = 0
        private set

    /**
     * Initialize the decoder by passing in identification and setup header data. See
     * https://xiph.org/vorbis/doc/Vorbis_I_spec.html#x1-170001.2.6 for definitions. The comment header is not required as
     * it is not actually used for decoding setup.
     *
     * @param infoBuffer  Identification header, including the 'vorbis' string.
     * @param setupBuffer Setup header (also known as codebook header), including the 'vorbis' string.
     */
    fun initialize(infoBuffer: ByteBuffer, setupBuffer: ByteBuffer) {
        checkNotReleased()
        require(infoBuffer.isDirect && setupBuffer.isDirect) {
            "Buffer argument must be a direct buffer."
        }

        val initialized = library.initialise(
            instance,
            infoBuffer = infoBuffer,
            infoOffset = infoBuffer.position(),
            infoLength = infoBuffer.remaining(),
            setupBuffer = setupBuffer,
            setupOffset = setupBuffer.position(),
            setupLength = setupBuffer.remaining()
        )

        check(initialized) {
            "Could not initialize library."
        }

        channelCount = library.getChannelCount(instance)
    }

    /**
     * Provide input for the decoder
     *
     * @param buffer Buffer with the input
     */
    fun input(buffer: ByteBuffer) {
        checkNotReleased()
        require(buffer.isDirect) {
            "Buffer argument must be a direct buffer."
        }

        val result = library.input(instance, buffer, buffer.position(), buffer.remaining())
        check(result == 0) {
            "Passing input failed with error $result."
        }

        buffer.position(buffer.position() + buffer.remaining())
    }

    /**
     * Fetch output from the decoder
     *
     * @param channels Channel buffers to fetch the output to
     * @return The number of samples fetched for each channel
     */
    fun output(channels: Array<FloatArray>): Int {
        checkNotReleased()
        check(channels.size == channelCount) {
            "Invalid channel float buffer length"
        }

        val result = library.output(instance, channels, channels[0].size)
        check(result >= 0) {
            "Retrieving output failed"
        }

        return result
    }

    override fun freeResources() {
        library.destroy(instance)
    }
}
