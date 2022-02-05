package com.sedmelluq.discord.lavaplayer.container.mpeg

import java.nio.channels.ReadableByteChannel

/**
 * Consumer for the data of one MP4 track
 */
interface MpegTrackConsumer {
    /**
     * The associated MP4 track
     */
    val track: MpegTrackInfo

    /**
     * Initialise the consumer, called before first consume()
     */
    fun initialise()

    /**
     * Indicates that the next frame is not a direct continuation of the previous one
     *
     * @param requestedTimecode Timecode in milliseconds to which the seek was requested to
     * @param providedTimecode  Timecode in milliseconds to which the seek was actually performed to
     */
    fun seekPerformed(requestedTimecode: Long, providedTimecode: Long)

    /**
     * Indicates that no more input is coming. Flush any buffers to output.
     *
     * @throws InterruptedException When interrupted externally (or for seek/stop).
     */
    @Throws(InterruptedException::class)
    fun flush()

    /**
     * Consume one chunk from the track
     *
     * @param channel Byte channel to consume from
     * @param length  Length of the chunk in bytes
     * @throws InterruptedException When interrupted externally (or for seek/stop).
     */
    @Throws(InterruptedException::class)
    fun consume(channel: ReadableByteChannel, length: Int)

    /**
     * Free all resources
     */
    fun close()
}
