package com.sedmelluq.discord.lavaplayer.container.flac

import com.sedmelluq.discord.lavaplayer.container.MediaContainerDetection
import com.sedmelluq.discord.lavaplayer.container.flac.FlacMetadataReader.readMetadataBlock
import com.sedmelluq.discord.lavaplayer.container.flac.FlacMetadataReader.readStreamInfoBlock
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream
import com.sedmelluq.discord.lavaplayer.track.playback.AudioProcessingContext
import java.io.DataInput
import java.io.DataInputStream
import java.io.IOException

/**
 * Loads either FLAC header information or a FLAC track object from a stream.
 *
 * @param inputStream Input stream to read the FLAC data from. This must be positioned right before FLAC FourCC.
 */
class FlacFileLoader(private val inputStream: SeekableInputStream) {
    companion object {
        val FLAC_CC = intArrayOf(0x66, 0x4C, 0x61, 0x43)
    }

    private val dataInput: DataInput = DataInputStream(inputStream)

    /**
     * Read all metadata from a FLAC file. Stream position is at the beginning of the first frame after this call.
     *
     * @return FLAC track information
     * @throws IOException On IO Error
     */
    @Throws(IOException::class)
    fun parseHeaders(): FlacTrackInfo {
        check(MediaContainerDetection.checkNextBytes(inputStream, FLAC_CC, false)) {
            "Not a FLAC file"
        }

        val trackInfoBuilder = FlacTrackInfoBuilder(readStreamInfoBlock(dataInput))
        readMetadataBlocks(trackInfoBuilder)
        trackInfoBuilder.firstFramePosition = inputStream.position

        return trackInfoBuilder.build()
    }

    /**
     * Initialise a FLAC track stream.
     *
     * @param context Configuration and output information for processing
     * @return The FLAC track stream which can produce frames.
     * @throws IOException On IO error
     */
    @Throws(IOException::class)
    fun loadTrack(context: AudioProcessingContext): FlacTrackProvider {
        return FlacTrackProvider(context, parseHeaders(), inputStream)
    }

    @Throws(IOException::class)
    private fun readMetadataBlocks(trackInfoBuilder: FlacTrackInfoBuilder) {
        var hasMoreBlocks = trackInfoBuilder.streamInfo.hasMetadataBlocks
        while (hasMoreBlocks) {
            hasMoreBlocks = readMetadataBlock(dataInput, inputStream, trackInfoBuilder)
        }
    }
}
