package com.sedmelluq.discord.lavaplayer.tools.io

import com.sedmelluq.discord.lavaplayer.tools.Units
import com.sedmelluq.lava.track.info.AudioTrackInfoProvider
import org.apache.commons.io.input.CountingInputStream
import java.io.IOException
import java.io.InputStream

class NonSeekableInputStream(delegate: InputStream?) : SeekableInputStream(Units.CONTENT_LENGTH_UNKNOWN, 0) {
    private val delegate: CountingInputStream = CountingInputStream(delegate)

    override val position: Long
        get() = delegate.byteCount

    override val trackInfoProviders: List<AudioTrackInfoProvider>
        get() = emptyList()

    override fun seekHard(position: Long): Unit = throw UnsupportedOperationException()

    override fun canSeekHard(): Boolean = false

    @Throws(IOException::class)
    override fun read(): Int = delegate.read()

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = delegate.read(buffer, offset, length)

    @Throws(IOException::class)
    override fun close() = delegate.close()
}
