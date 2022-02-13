package com.sedmelluq.discord.lavaplayer.container.wav

import com.sedmelluq.discord.lavaplayer.container.TrackProvider
import com.sedmelluq.discord.lavaplayer.filter.AudioPipelineFactory.create
import com.sedmelluq.discord.lavaplayer.filter.PcmFormat
import com.sedmelluq.discord.lavaplayer.tools.extensions.toRuntimeException
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream
import com.sedmelluq.discord.lavaplayer.track.playback.AudioProcessingContext
import java.io.Closeable
import java.io.DataInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import kotlin.math.min

/**
 * A provider of audio frames from a WAV track.
 *
 * @param context     Configuration and output information for processing
 * @param inputStream Input stream to use
 * @param info        Information about the WAV file
 */
class WavTrackProvider(
    context: AudioProcessingContext?,
    private val inputStream: SeekableInputStream,
    private val info: WavFileInfo
) : Closeable, TrackProvider {
    companion object {
        private const val BLOCKS_IN_BUFFER = 4096
    }

    private val dataInput = DataInputStream(inputStream)
    private val downstream = create(context!!, PcmFormat(info.channelCount, info.sampleRate))
    private val buffer = if (info.padding > 0) ShortArray(info.channelCount * BLOCKS_IN_BUFFER) else null
    private val nioBuffer: ShortBuffer
    private val rawBuffer: ByteArray
    private val nextChunkBlocks: Int
        get() {
            val endOffset = info.startOffset + info.blockAlign * info.blockCount
            return min((endOffset - inputStream.position) / info.blockAlign, BLOCKS_IN_BUFFER.toLong()).toInt()
        }

    init {
        val byteBuffer = ByteBuffer
            .allocate(info.blockAlign * BLOCKS_IN_BUFFER)
            .order(ByteOrder.LITTLE_ENDIAN)

        rawBuffer = byteBuffer.array()
        nioBuffer = byteBuffer.asShortBuffer()
    }

    override fun seekToTimecode(timecode: Long) {
        try {
            val fileOffset = timecode * info.sampleRate / 1000L * info.blockAlign + info.startOffset
            inputStream.seek(fileOffset)
            downstream.seekPerformed(timecode, timecode)
        } catch (e: IOException) {
            throw e.toRuntimeException()
        }
    }

    @Throws(InterruptedException::class)
    override fun provideFrames() {
        try {
            var blockCount: Int
            while (nextChunkBlocks.also { blockCount = it } > 0) {
                if (buffer != null) {
                    processChunkWithPadding(blockCount)
                } else {
                    processChunk(blockCount)
                }
            }
        } catch (e: IOException) {
            throw e.toRuntimeException()
        }
    }

    /**
     * Free all resources associated to processing the track.
     */
    override fun close() {
        downstream.close()
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun processChunkWithPadding(blockCount: Int) {
        readChunkToBuffer(blockCount)
        val padding = info.padding / 2
        val sampleCount = blockCount * info.channelCount

        var indexInBlock = 0
        for (i in 0 until sampleCount) {
            buffer!![i] = nioBuffer.get()
            if (++indexInBlock == info.channelCount) {
                nioBuffer.position(nioBuffer.position() + padding)
                indexInBlock = 0
            }
        }

        downstream.process(buffer!!, 0, blockCount * info.channelCount)
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun processChunk(blockCount: Int) {
        readChunkToBuffer(blockCount)
        downstream.process(nioBuffer)
    }

    @Throws(IOException::class)
    private fun readChunkToBuffer(blockCount: Int) {
        val bytesToRead = blockCount * info.blockAlign
        dataInput.readFully(rawBuffer, 0, bytesToRead)
        nioBuffer.position(0)
        nioBuffer.limit(bytesToRead / 2)
    }
}
