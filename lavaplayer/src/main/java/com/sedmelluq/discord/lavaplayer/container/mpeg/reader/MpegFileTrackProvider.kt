package com.sedmelluq.discord.lavaplayer.container.mpeg.reader

import com.sedmelluq.discord.lavaplayer.container.TrackProvider
import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegTrackConsumer

/**
 * Track provider for a type of MP4 file.
 */
interface MpegFileTrackProvider : TrackProvider {
    /**
     * @return Total duration of the file in milliseconds
     */
    val duration: Long

    /**
     * @param trackConsumer Track consumer which defines the track this will provide and the consumer for packets.
     * @return Returns true if it had enough information for initialisation.
     */
    fun initialize(trackConsumer: MpegTrackConsumer): Boolean
}
