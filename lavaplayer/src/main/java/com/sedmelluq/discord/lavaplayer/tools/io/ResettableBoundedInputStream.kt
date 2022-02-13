package com.sedmelluq.discord.lavaplayer.tools.io

import org.apache.commons.io.IOUtils
import java.io.IOException
import java.io.InputStream
import kotlin.math.min

/**
 * Bounded input stream where the limit can be set dynamically.
 *
 * @param delegate Underlying input stream.
 */
class ResettableBoundedInputStream(private val delegate: InputStream) : InputStream() {
    private var limit: Long = Long.MAX_VALUE
    private var position: Long = 0

    /**
     * Make this input stream return EOF after the specified number of bytes.
     *
     * @param limit Maximum number of bytes that can be read.
     */
    fun resetLimit(limit: Long) {
        position = 0
        this.limit = limit
    }

    @Throws(IOException::class)
    override fun available(): Int = min(limit - position, delegate.available().toLong()).toInt()

    @Throws(IOException::class)
    override fun close() = Unit // Nothing to do

    override fun markSupported(): Boolean = false

    @Throws(IOException::class)
    override fun read(): Int {
        if (position >= limit) {
            return IOUtils.EOF
        }

        val result = delegate.read()
        if (result != IOUtils.EOF) {
            position++
        }

        return result
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (position >= limit) {
            return IOUtils.EOF
        }

        val chunk = min(length.toLong(), limit - position).toInt()
        val read = delegate.read(buffer, offset, chunk)
        if (read == IOUtils.EOF) {
            return IOUtils.EOF
        }

        position += read.toLong()
        return read
    }

    @Throws(IOException::class)
    override fun skip(distance: Long): Long {
        val chunk = min(distance, limit - position).toInt()
        val skipped = delegate.skip(chunk.toLong())
        position += skipped

        return skipped
    }
}
