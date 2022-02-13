package com.sedmelluq.discord.lavaplayer.tools.io

import com.sedmelluq.discord.lavaplayer.tools.extensions.onComplete
import com.sedmelluq.lava.track.info.AudioTrackInfoProvider
import java.io.EOFException
import java.io.IOException
import java.io.InputStream

/**
 * An input stream that is seekable.
 *
 * @param contentLength   Total stream length
 * @param maxSkipDistance Maximum distance that should be skipped by reading and discarding
 */
abstract class SeekableInputStream(
    /**
     * @return Length of the stream
     */
    var contentLength: Long,
    /**
     * @return Maximum distance that this stream will skip without doing a direct seek on the underlying resource.
     */
    val maxSkipDistance: Long
) : InputStream() {
    abstract val trackInfoProviders: List<AudioTrackInfoProvider>

    /**
     * @return Current position in the stream
     */
    abstract val position: Long

    @Throws(IOException::class)
    protected abstract fun seekHard(position: Long)

    /**
     * @return `true` if it is possible to seek to an arbitrary position in this stream, even when it is behind
     * the current position.
     */
    abstract fun canSeekHard(): Boolean

    /**
     * Skip the specified number of bytes in the stream. The result is either that the requested number of bytes were
     * skipped or an EOFException was thrown.
     *
     * @param distance The number of bytes to skip
     * @throws IOException On IO error
     */
    @Throws(IOException::class)
    fun skipFully(distance: Long) {
        var current = position
        val target = current + distance

        while (current < target) {
            var skipped = skip(target - current)
            if (skipped == 0L) {
                if (read() == -1) {
                    throw EOFException("Cannot skip any further.")
                }

                skipped = 1
            }

            current += skipped
        }
    }

    /**
     * Seek to the specified position
     *
     * @param position The position to seek to
     * @throws IOException On a read error or if the position is beyond EOF
     */
    @Throws(IOException::class)
    fun seek(position: Long) {
        val current = this.position
        if (current == position) {
            return
        }

        when {
            current <= position && position - current <= maxSkipDistance ->
                skipFully(position - current)

            !canSeekHard() -> {
                if (current > position) {
                    seekHard(0)
                    skipFully(position)
                } else {
                    skipFully(position - current)
                }
            }

            else -> seekHard(position)
        }
    }

    /**
     * Seek to the position before invoking [block]
     *
     * @param block Code to run
     */
    fun <T> rewindAfter(block: (SeekableInputStream) -> T): T {
        val pos = position
        return runCatching(block)
            .onComplete { seek(pos) }
            .getOrThrow()
    }

}
