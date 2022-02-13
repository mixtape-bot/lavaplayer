package com.sedmelluq.discord.lavaplayer.container

interface TrackProvider {
    /**
     * Provide audio frames to the frame consumer until the end of the track or interruption.
     *
     * @throws InterruptedException When interrupted externally (or for seek/stop).
     */
    @Throws(InterruptedException::class)
    fun provideFrames()

    /**
     * Perform a seek to the given timecode (ms). On the next call to provideFrames, the seekPerformed method of frame
     * consumer is called with the position where it actually seeked to and the position where the seek was requested to
     * as arguments.
     *
     * @param timecode The timecode to seek to in milliseconds
     */
    fun seekToTimecode(timecode: Long)
}
