package com.sedmelluq.discord.lavaplayer.container.mp3

import com.sedmelluq.discord.lavaplayer.tools.Units.DURATION_MS_UNKNOWN
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream
import java.io.IOException

/**
 * Seeker for an MP3 stream, which actually does not allow seeking and reports UnitConstants.DURATION_MS_UNKNOWN as
 * duration.
 */
class Mp3StreamSeeker : Mp3Seeker {
    override val duration: Long
        get() = DURATION_MS_UNKNOWN

    override val isSeekable: Boolean
        get() = false

    @Throws(IOException::class)
    override fun seekAndGetFrameIndex(timecode: Long, inputStream: SeekableInputStream): Long {
        throw UnsupportedOperationException("Cannot seek on a stream.")
    }
}
