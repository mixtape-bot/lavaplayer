package com.sedmelluq.discord.lavaplayer.container.matroska.format

import java.io.IOException
import java.nio.ByteBuffer

/**
 * Provides information and buffer to read from for a Matroska block.
 */
interface MatroskaBlock {
    /**
     * The timecode of this block relative to its cluster
     */
    val timecode: Int

    /**
     * The track number which this block is for
     */
    val trackNumber: Int

    /**
     * Whether this block is a keyframe
     */
    val isKeyFrame: Boolean

    /**
     * The number of frames in this block
     */
    val frameCount: Int

    /**
     * The reader must already be positioned at the frame that is to be read next.
     *
     * @param reader The reader to use to read the block contents into a buffer.
     * @param index  The index of the frame to get the buffer for.
     * @return A buffer where the range between position and limit contains the data for the specific frame. The contents
     * of this buffer are only valid until the next call to this method.
     * @throws IOException On read error.
     */
    @Throws(IOException::class)
    fun getNextFrameBuffer(reader: MatroskaFileReader, index: Int): ByteBuffer
}
