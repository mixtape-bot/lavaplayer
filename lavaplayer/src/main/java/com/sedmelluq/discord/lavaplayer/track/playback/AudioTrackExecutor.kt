package com.sedmelluq.discord.lavaplayer.track.playback

import com.sedmelluq.discord.lavaplayer.track.AudioTrackState
import com.sedmelluq.discord.lavaplayer.track.TrackStateListener
import com.sedmelluq.discord.lavaplayer.track.marker.TrackMarker

/**
 * Executor which handles track execution and all operations on playing tracks.
 */
interface AudioTrackExecutor : AudioFrameProvider {
    /**
     * @return The audio buffer of this executor.
     */
    val audioBuffer: AudioFrameBuffer?

    /**
     * Timecode of the last played frame or in case a seek is in progress, the timecode of the frame being seeked to.
     */
    val position: Long

    /**
     * @return Current state of the executor
     */
    val state: AudioTrackState?

    /**
     * Updates the position of the current track.
     *
     * @param timecode The timecode
     */
    suspend fun updatePosition(timecode: Long)

    /**
     * Execute the track, which means that this thread will fill the frame buffer until the track finishes or is stopped.
     *
     * @param listener Listener for track state events
     */
    suspend fun execute(listener: TrackStateListener)

    /**
     * Stop playing the track, terminating the thread that is filling the frame buffer.
     */
    suspend fun stop()

    /**
     * Set track position marker.
     *
     * @param marker Track position marker to set.
     */
    suspend fun setMarker(marker: TrackMarker?)

    /**
     * @return True if this track threw an exception before it provided any audio.
     */
    fun failedBeforeLoad(): Boolean
}
