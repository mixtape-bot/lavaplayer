package com.sedmelluq.discord.lavaplayer.container.wav

import com.sedmelluq.discord.lavaplayer.container.MediaContainerDetection
import com.sedmelluq.discord.lavaplayer.tools.extensions.reverseBytes
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream
import com.sedmelluq.discord.lavaplayer.track.playback.AudioProcessingContext
import java.io.DataInput
import java.io.DataInputStream
import java.io.IOException
import kotlin.experimental.and

/**
 * Loads either WAV header information or a WAV track provider from a stream.
 *
 * @param inputStream Input stream to read the WAV data from. This must be positioned right before WAV RIFF header.
 */
class WavFileLoader(private val inputStream: SeekableInputStream) {
    companion object {
        val WAV_RIFF_HEADER = intArrayOf(0x52, 0x49, 0x46, 0x46, -1, -1, -1, -1, 0x57, 0x41, 0x56, 0x45)
    }

    /**
     * Parses the headers of the file.
     *
     * @return Format description of the WAV file
     * @throws IOException On read error
     */
    @Throws(IOException::class)
    fun parseHeaders(): WavFileInfo {
        check(MediaContainerDetection.checkNextBytes(inputStream, WAV_RIFF_HEADER, false)) {
            "Not a WAV header."
        }

        val builder = InfoBuilder()
        val dataInput: DataInput = DataInputStream(inputStream)

        while (true) {
            val chunkName = readChunkName(dataInput)
            val chunkSize = dataInput.readInt().reverseBytes().toLong()

            if ("fmt " == chunkName) {
                readFormatChunk(builder, dataInput)
                if (chunkSize > 16) {
                    inputStream.skipFully(chunkSize - 16)
                }
            } else if ("data" == chunkName) {
                builder.sampleAreaSize = chunkSize
                builder.startOffset = inputStream.position
                return builder.build()
            } else {
                inputStream.skipFully(chunkSize)
            }
        }
    }

    @Throws(IOException::class)
    private fun readChunkName(dataInput: DataInput): String {
        val buffer = ByteArray(4)
        dataInput.readFully(buffer)

        return String(buffer, Charsets.US_ASCII)
    }

    @Throws(IOException::class)
    private fun readFormatChunk(builder: InfoBuilder, dataInput: DataInput) {
        builder.audioFormat = (dataInput.readShort().reverseBytes() and 0xFFFF.toShort()).toInt()
        builder.channelCount = (dataInput.readShort().reverseBytes() and 0xFFFF.toShort()).toInt()
        builder.sampleRate = dataInput.readInt().reverseBytes()

        // Skip bitrate
        dataInput.readInt()
        builder.blockAlign = (dataInput.readShort().reverseBytes() and 0xFFFF.toShort()).toInt()
        builder.bitsPerSample = (dataInput.readShort().reverseBytes() and 0xFFFF.toShort()).toInt()
    }

    /**
     * initialize a WAV track stream.
     *
     * @param context Configuration and output information for processing
     * @return The WAV track stream which can produce frames.
     * @throws IOException On read error
     */
    @Throws(IOException::class)
    fun loadTrack(context: AudioProcessingContext?): WavTrackProvider =
        WavTrackProvider(context, inputStream, parseHeaders())

    private class InfoBuilder {
        var audioFormat = 0
        var channelCount = 0
        var sampleRate = 0
        var bitsPerSample = 0
        var blockAlign = 0
        var sampleAreaSize = 0L
        var startOffset = 0L

        fun build(): WavFileInfo {
            validateFormat()
            validateAlignment()
            return WavFileInfo(
                channelCount = channelCount,
                sampleRate = sampleRate,
                bitsPerSample = bitsPerSample,
                blockAlign = blockAlign,
                blockCount = sampleAreaSize / blockAlign,
                startOffset = startOffset
            )
        }

        private fun validateFormat() {
            check(audioFormat == 1) {
                "Invalid audio format $audioFormat, must be 1 (PCM)"
            }

            check(channelCount in 1..16) {
                "Invalid channel count: $channelCount"
            }
        }

        private fun validateAlignment() {
            val minimumBlockAlign = channelCount * (bitsPerSample shr 3)

            check(!(blockAlign < minimumBlockAlign || blockAlign > minimumBlockAlign + 32)) {
                "Block align is not valid: $blockAlign"
            }

            check(blockAlign % (bitsPerSample shr 3) == 0) {
                "Block align is not a multiple of bits per sample: $blockAlign"
            }
        }
    }
}
