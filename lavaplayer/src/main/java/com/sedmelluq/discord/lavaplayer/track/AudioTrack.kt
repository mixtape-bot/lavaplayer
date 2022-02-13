package com.sedmelluq.discord.lavaplayer.track

import com.sedmelluq.discord.lavaplayer.source.ItemSourceManager
import com.sedmelluq.discord.lavaplayer.track.marker.TrackMarker
import com.sedmelluq.lava.track.info.AudioTrackInfo

/**
 * A playable audio track
 */
interface AudioTrack : AudioItem {
    /**
     * @return Track meta information
     */
    val info: AudioTrackInfo

    /**
     * @return The identifier of the track
     */
    val identifier: String

    /**
     * @return The current execution state of the track
     */
    val state: AudioTrackState?

    /**
     * @return True if the track is seekable.
     */
    val isSeekable: Boolean

    /**
     * @return Duration of the track in milliseconds
     */
    val duration: Long

    /**
     * The source manager which created this track. Null if not created by a source manager directly.
     */
    val sourceManager: ItemSourceManager?

    /**
     * The current position of this track in milliseconds.
     */
    var position: Long

    /**
     * Application specific data for this track.
     */
    var userData: Any?

    /**
     * Stop the track if it is currently playing
     */
    fun stop()

    /**
     * @return Clone of this track which does not share the execution state of this track
     */
    fun makeClone(): AudioTrack?

    /**
     * @return Clone of this track which does not share:
     * - the execution state of this track
     * - the share the user data of this track
     */
    fun makeShallowClone(): AudioTrack?

    /**
     * @param marker Track position marker to place
     */
    fun setMarker(marker: TrackMarker?)

    /**
     * @param klass The expected class of the user data (or a superclass of it).
     * @return Object previously stored with [.setUserData] if it is of the specified type. If it is set,
     * but with a different type, null is returned.
     */
    fun <T> getUserData(klass: Class<T>?): T?
}
