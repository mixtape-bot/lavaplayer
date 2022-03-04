package com.sedmelluq.discord.lavaplayer.manager

import com.sedmelluq.discord.lavaplayer.filter.PcmFilterFactory
import com.sedmelluq.discord.lavaplayer.manager.event.*
import com.sedmelluq.discord.lavaplayer.tools.extensions.keepInterrupted
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason.*
import com.sedmelluq.discord.lavaplayer.track.InternalAudioTrack
import com.sedmelluq.discord.lavaplayer.track.TrackStateListener
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrameProviderTools
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame
import com.sedmelluq.lava.common.tools.exception.FriendlyException
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

/**
 * An audio player that is capable of playing audio tracks and provides audio frames from the currently playing track.
 */
class DefaultAudioPlayer(
    private val manager: DefaultAudioPlayerManager,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : AudioPlayer, TrackStateListener, CoroutineScope {
    companion object {
        private val log = KotlinLogging.logger { }
    }

    private var _paused by atomic(false)
    private val trackSwitchLock = Mutex()
    private val resources = AudioPlayerResources()
    private val eventFlow = MutableSharedFlow<AudioEvent>(extraBufferCapacity = Int.MAX_VALUE)

    override val coroutineContext: CoroutineContext
        get() = dispatcher + SupervisorJob()

    override var volume: Int
        get() = resources.volumeLevel
        set(value) {
            resources.volumeLevel = value.coerceIn(0..1000)
        }

    override var isPaused: Boolean
        get() = _paused
        set(value) {
            if (_paused != value) {
                _paused = value
                if (value) {
                    dispatchEvent(PlayerPauseEvent(this))
                } else {
                    dispatchEvent(PlayerResumeEvent(this))
                    lastReceiveTime = System.nanoTime()
                }
            }
        }

    override val playingTrack: AudioTrack?
        get() = activeTrack

    override val events: Flow<AudioEvent>
        get() = eventFlow.buffer(UNLIMITED)

    @Volatile
    private var activeTrack: InternalAudioTrack? = null

    @Volatile
    private var lastRequestTime: Long = 0

    @Volatile
    private var lastReceiveTime: Long = 0

    @Volatile
    private var stuckEventSent: Boolean = false

    @Volatile
    private var shadowTrack: InternalAudioTrack? = null

    /**
     * @param track The track to start playing
     */
    override suspend fun playTrack(track: AudioTrack?) {
        startTrack(track, false)
    }

    /**
     * @param track       The track to start playing, passing null will stop the current track and return false
     * @param noInterrupt Whether to only start if nothing else is playing
     * @return True if the track was started
     */
    override suspend fun startTrack(track: AudioTrack?, noInterrupt: Boolean): Boolean {
        val newTrack = track as? InternalAudioTrack
        var previousTrack: InternalAudioTrack?

        trackSwitchLock.withLock {
            previousTrack = activeTrack
            if (noInterrupt && previousTrack != null) {
                return false
            }

            activeTrack = newTrack
            lastRequestTime = System.currentTimeMillis()
            lastReceiveTime = System.nanoTime()
            stuckEventSent = false
            if (previousTrack != null) {
                previousTrack!!.stop()
                dispatchEvent(TrackEndEvent(this, previousTrack!!, if (newTrack == null) STOPPED else REPLACED))

                shadowTrack = previousTrack
            }
        }

        if (newTrack == null) {
            shadowTrack = null
            return false
        }

        dispatchEvent(TrackStartEvent(this, newTrack))
        manager.executeTrack(this, newTrack, manager.configuration, resources)

        return true
    }

    /**
     * Stop currently playing track.
     */
    override suspend fun stopTrack() {
        stopWithReason(STOPPED)
    }

    override suspend fun provide(): AudioFrame? {
        return AudioFrameProviderTools.delegateToTimedProvide(this)
    }

    override suspend fun provide(timeout: Long, unit: TimeUnit): AudioFrame? {
        lateinit var track: InternalAudioTrack

        lastRequestTime = System.currentTimeMillis()
        if (timeout == 0L && isPaused) {
            return null
        }

        while (activeTrack?.also { track = it } != null) {
            var frame = if (timeout > 0) track.provide(timeout, unit) else track.provide()
            if (frame != null) {
                lastReceiveTime = System.nanoTime()
                shadowTrack = null

                if (frame.isTerminator) {
                    handleTerminator(track)
                    continue
                }
            } else if (timeout == 0L) {
                checkStuck(track)
                frame = provideShadowFrame()
            }

            return frame
        }

        return null
    }

    override suspend fun provide(targetFrame: MutableAudioFrame): Boolean {
        try {
            return provide(targetFrame, 0, TimeUnit.MILLISECONDS)
        } catch (e: Throwable) {
            e.keepInterrupted()
            throw RuntimeException(e)
        }
    }

    override suspend fun provide(targetFrame: MutableAudioFrame, timeout: Long, unit: TimeUnit): Boolean {
        lateinit var track: InternalAudioTrack

        lastRequestTime = System.currentTimeMillis()
        if (timeout == 0L && isPaused) {
            return false
        }

        while (activeTrack?.also { track = it } != null) {
            if (if (timeout > 0) track.provide(targetFrame, timeout, unit) else track.provide(targetFrame)) {
                lastReceiveTime = System.nanoTime()
                shadowTrack = null

                if (targetFrame.isTerminator) {
                    handleTerminator(track)
                    continue
                }

                return true
            } else if (timeout == 0L) {
                checkStuck(track)
                return provideShadowFrame(targetFrame)
            } else {
                return false
            }
        }

        return false
    }

    override fun setFilterFactory(factory: PcmFilterFactory?) {
        resources.filterFactory = factory
    }

    override fun setFrameBufferDuration(duration: Int) {
        resources.frameBufferDuration = duration.let { 200.coerceAtLeast(it) }
    }

    /**
     * Destroy the player and stop playing track.
     */
    override suspend fun destroy() {
        stopTrack()
        cancel()
    }

    override fun onTrackException(track: AudioTrack, exception: FriendlyException) {
        dispatchEvent(TrackExceptionEvent(this, track, exception))
    }

    override fun onTrackStuck(track: AudioTrack, thresholdMs: Long) {
        dispatchEvent(TrackStuckEvent(this, track, thresholdMs))
    }

    /**
     * Check if the player should be "cleaned up" - stopped due to nothing using it, with the given threshold.
     *
     * @param threshold Threshold in milliseconds to use
     */
    override suspend fun checkCleanup(threshold: Long) {
        val track = playingTrack
        if (track != null && System.currentTimeMillis() - lastRequestTime >= threshold) {
            log.debug { "Triggering cleanup on an audio player playing track $track" }
            stopWithReason(CLEANUP)
        }
    }

    override fun toString(): String {
        return "DefaultAudioPlayer(volume=$volume, isPaused=$isPaused${if (playingTrack != null) ", playingTrack=$playingTrack" else ""})"
    }

    private suspend fun stopWithReason(reason: AudioTrackEndReason) {
        shadowTrack = null

        trackSwitchLock.withLock {
            val previousTrack = activeTrack
            activeTrack = null
            if (previousTrack != null) {
                previousTrack.stop()
                dispatchEvent(TrackEndEvent(this, previousTrack, reason))
            }
        }
    }

    private suspend fun provideShadowFrame(): AudioFrame? {
        val shadow = shadowTrack
        var frame: AudioFrame? = null

        if (shadow != null) {
            frame = shadow.provide()
            if (frame != null && frame.isTerminator) {
                shadowTrack = null
                frame = null
            }
        }

        return frame
    }

    private suspend fun provideShadowFrame(targetFrame: MutableAudioFrame): Boolean {
        val shadow = shadowTrack
        if (shadow != null && shadow.provide(targetFrame)) {
            if (targetFrame.isTerminator) {
                shadowTrack = null
                return false
            }

            return true
        }

        return false
    }

    private fun dispatchEvent(event: AudioEvent) {
        launch {
            log.debug { "Firing an event with class ${event::class.qualifiedName}" }
            eventFlow.emit(event)
        }
    }

    private fun handleTerminator(track: InternalAudioTrack) {
        synchronized(trackSwitchLock) {
            if (activeTrack == track) {
                activeTrack = null

                dispatchEvent(
                    TrackEndEvent(this, track, if (track.activeExecutor.failedBeforeLoad()) LOAD_FAILED else FINISHED)
                )
            }
        }
    }

    private fun checkStuck(track: AudioTrack) {
        if (!stuckEventSent && System.nanoTime() - lastReceiveTime > manager.trackStuckThresholdNanos) {
            stuckEventSent = true

            val stackTrace = getStackTrace(track)
            val threshold = TimeUnit.NANOSECONDS.toMillis(manager.trackStuckThresholdNanos)

            dispatchEvent(TrackStuckEvent(this, track, threshold, stackTrace ?: emptyList()))
        }
    }

    private fun getStackTrace(track: AudioTrack): List<StackTraceElement>? {
        if (track is InternalAudioTrack) {
            val executor = track.activeExecutor as? LocalAudioTrackExecutor
            return executor?.stackTrace?.toList()
        }

        return null
    }
}
