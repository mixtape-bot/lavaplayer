package com.sedmelluq.discord.lavaplayer.source.local

import com.sedmelluq.discord.lavaplayer.tools.extensions.toRuntimeException
import com.sedmelluq.discord.lavaplayer.tools.io.ExtendedBufferedInputStream
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream
import com.sedmelluq.lava.track.info.AudioTrackInfoProvider
import mu.KotlinLogging
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.channels.FileChannel

/**
 * Seekable input stream implementation for local files.
 *
 * @param file File to create a stream for.
 */
class LocalSeekableInputStream(file: File) : SeekableInputStream(file.length(), 0) {
    companion object {
        private val log = KotlinLogging.logger {  }
    }

    private var inputStream: FileInputStream
    private var channel: FileChannel
    private var bufferedStream: ExtendedBufferedInputStream

    override var position: Long = 0
        private set

    override val trackInfoProviders: List<AudioTrackInfoProvider>
        get() = emptyList()

    init {
        try {
            inputStream = FileInputStream(file)
            bufferedStream = ExtendedBufferedInputStream(inputStream)
            channel = inputStream.channel
        } catch (e: FileNotFoundException) {
            throw e.toRuntimeException()
        }
    }

    @Throws(IOException::class)
    override fun read(): Int {
        val result = bufferedStream.read()
        if (result >= 0) {
            position++
        }

        return result
    }

    @Throws(IOException::class)
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val read = bufferedStream.read(b, off, len)
        position += read.toLong()

        return read
    }

    @Throws(IOException::class)
    override fun skip(n: Long): Long {
        val skipped = bufferedStream.skip(n)
        position += skipped

        return skipped
    }

    @Throws(IOException::class)
    override fun close() {
        try {
            channel.close()
        } catch (e: IOException) {
            log.debug("Failed to close channel", e)
        }
    }

    @Throws(IOException::class)
    override fun seekHard(position: Long) {
        channel.position(position)
        this.position = position
        bufferedStream.discardBuffer()
    }

    @Throws(IOException::class)
    override fun available(): Int = bufferedStream.available()

    @Synchronized
    @Throws(IOException::class)
    override fun reset(): Unit = throw IOException("mark/reset not supported")

    override fun markSupported(): Boolean = false

    override fun canSeekHard(): Boolean = true
}
