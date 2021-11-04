package com.sedmelluq.discord.lavaplayer.container.flac

import com.sedmelluq.discord.lavaplayer.container.flac.FlacMetadataBlockType.Companion.find
import com.sedmelluq.discord.lavaplayer.tools.io.BitBufferReader
import java.nio.ByteBuffer

/**
 * A header of FLAC metadata.
 *
 * @param data The raw header data
 */
class FlacMetadataHeader(data: ByteArray?) {
    companion object {
        const val LENGTH = 4
    }

    /**
     * If this header is for the last metadata block. If this is true, then the current metadata block is followed by
     * frames.
     */
    val isLastBlock: Boolean

    /**
     * The block type of this header.
     */
    val blockType: FlacMetadataBlockType

    /**
     * Length of the block, current header excluded
     */
    val blockLength: Int

    init {
        val bitReader = BitBufferReader(ByteBuffer.wrap(data))
        isLastBlock = bitReader.asInteger(1) == 1
        blockType = find(bitReader.asInteger(7))
        blockLength = bitReader.asInteger(24)
    }

    override fun toString(): String {
        return "FlacMetadataHeader(isLastBlock=$isLastBlock, blockType=$blockType, blockLength=$blockLength)"
    }
}
