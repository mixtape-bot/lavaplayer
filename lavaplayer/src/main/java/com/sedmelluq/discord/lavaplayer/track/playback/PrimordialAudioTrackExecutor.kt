package com.sedmelluq.discord.lavaplayer.track.playback

import com.sedmelluq.discord.lavaplayer.track.AudioTrackState
import com.sedmelluq.discord.lavaplayer.track.TrackStateListener
import com.sedmelluq.discord.lavaplayer.track.marker.TrackMarker
import com.sedmelluq.discord.lavaplayer.track.marker.TrackMarkerManager
import com.sedmelluq.lava.track.info.AudioTrackInfo
import mu.KotlinLogging
import java.util.concurrent.TimeUnit

/**
 * Executor implementation which is used before a track has actually been executed. Saves the position and loop
 * information, which is applied to the actual executor when one is attached.
 */
class PrimordialAudioTrackExecutor(private val trackInfo: AudioTrackInfo) : AudioTrackExecutor {
    companion object {
        private val log = KotlinLogging.logger {  }
    }

    private val markerTracker: TrackMarkerManager = TrackMarkerManager()

    @Volatile
    override var position: Long = 0
        set(value) {
            field = value
            markerTracker.checkSeekTimecode(value)
        }

    override val audioBuffer: AudioFrameBuffer?
        get() = null

    override val state: AudioTrackState
        get() = AudioTrackState.INACTIVE

    override fun setMarker(marker: TrackMarker?) {
        markerTracker[marker] = position
    }

    override suspend fun execute(listener: TrackStateListener) = throw UnsupportedOperationException()
    override fun failedBeforeLoad(): Boolean = false
    override fun stop() = log.info("Tried to stop track ${trackInfo.identifier} which is not playing.")

    override fun provide(): AudioFrame? = provide(0, TimeUnit.MILLISECONDS)
    override fun provide(timeout: Long, unit: TimeUnit): AudioFrame? = null
    override fun provide(targetFrame: MutableAudioFrame): Boolean = false
    override fun provide(targetFrame: MutableAudioFrame, timeout: Long, unit: TimeUnit): Boolean = false

    /**
     * Apply the position and loop state that had been set on this executor to an actual executor.
     *
     * @param executor The executor to apply the state to
     */
    fun applyStateToExecutor(executor: AudioTrackExecutor) {
        if (position != 0L) {
            executor.position = position
        }

        executor.setMarker(markerTracker.remove())
    }
}
