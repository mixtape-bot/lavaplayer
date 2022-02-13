package com.sedmelluq.discord.lavaplayer.container.flac

import com.sedmelluq.discord.lavaplayer.tools.extensions.reverseBytes
import org.apache.commons.io.IOUtils
import java.io.DataInput
import java.io.IOException
import java.io.InputStream

/**
 * Handles reading one FLAC metadata blocks.
 */
object FlacMetadataReader {
    /**
     * Reads FLAC stream info metadata block.
     *
     * @param dataInput Data input where the block is read from
     * @return Stream information
     * @throws IOException On read error
     */
    @JvmStatic
    @Throws(IOException::class)
    fun readStreamInfoBlock(dataInput: DataInput): FlacStreamInfo {
        val header = readMetadataHeader(dataInput)
        check(header.blockType == FlacMetadataBlockType.StreamInfo) {
            "Wrong metadata block, should be stream info."
        }

        check(header.blockLength == FlacStreamInfo.LENGTH) {
            "Invalid stream info block size."
        }

        val streamInfoData = ByteArray(FlacStreamInfo.LENGTH)
        dataInput.readFully(streamInfoData)

        return FlacStreamInfo(streamInfoData, !header.isLastBlock)
    }

    @Throws(IOException::class)
    private fun readMetadataHeader(dataInput: DataInput): FlacMetadataHeader {
        val headerBytes = ByteArray(FlacMetadataHeader.LENGTH)
        dataInput.readFully(headerBytes)

        return FlacMetadataHeader(headerBytes)
    }

    /**
     * @param dataInput        Data input where the block is read from
     * @param inputStream      Input stream matching the data input
     * @param trackInfoBuilder Track info builder object where detected metadata is stored in
     * @return True if there are more metadata blocks available
     * @throws IOException On read error
     */
    @JvmStatic
    @Throws(IOException::class)
    fun readMetadataBlock(
        dataInput: DataInput,
        inputStream: InputStream,
        trackInfoBuilder: FlacTrackInfoBuilder
    ): Boolean {
        val header = readMetadataHeader(dataInput)
        when (header.blockType) {
            is FlacMetadataBlockType.Seektable -> readSeekTableBlock(dataInput, trackInfoBuilder, header.blockLength)
            is FlacMetadataBlockType.VorbisComment -> readCommentBlock(dataInput, inputStream, trackInfoBuilder)
            else -> IOUtils.skipFully(inputStream, header.blockLength.toLong())
        }

        return !header.isLastBlock
    }

    @Throws(IOException::class)
    private fun readCommentBlock(dataInput: DataInput, inputStream: InputStream, trackInfoBuilder: FlacTrackInfoBuilder) {
        val vendorLength = dataInput.readInt().reverseBytes()
        IOUtils.skipFully(inputStream, vendorLength.toLong())

        val listLength = dataInput.readInt().reverseBytes()
        for (i in 0 until listLength) {
            val itemLength = dataInput.readInt().reverseBytes()
            val textBytes = ByteArray(itemLength)
            dataInput.readFully(textBytes)

            val text = textBytes.decodeToString()
            val keyAndValue = text.split("=".toRegex(), 2).toTypedArray()
            if (keyAndValue.size > 1) {
                trackInfoBuilder.addTag(keyAndValue[0].uppercase(), keyAndValue[1])
            }
        }
    }

    @Throws(IOException::class)
    private fun readSeekTableBlock(dataInput: DataInput, trackInfoBuilder: FlacTrackInfoBuilder, length: Int) {
        var seekPointCount = 0
        val seekPoints = List(length / FlacSeekPoint.LENGTH) { i ->
            val sampleIndex = dataInput.readLong()
            if (sampleIndex != -1L) {
                seekPointCount = i + 1
            }

            FlacSeekPoint(
                sampleIndex = sampleIndex,
                byteOffset = dataInput.readLong(),
                sampleCount = dataInput.readUnsignedShort()
            )
        }

        trackInfoBuilder.setSeekPoints(seekPoints, seekPointCount)
    }
}
