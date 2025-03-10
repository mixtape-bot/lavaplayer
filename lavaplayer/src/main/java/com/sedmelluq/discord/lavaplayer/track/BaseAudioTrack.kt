package com.sedmelluq.discord.lavaplayer.track

import com.sedmelluq.discord.lavaplayer.manager.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.ItemSourceManager
import com.sedmelluq.discord.lavaplayer.track.marker.TrackMarker
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame
import com.sedmelluq.discord.lavaplayer.track.playback.AudioTrackExecutor
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame
import com.sedmelluq.discord.lavaplayer.track.playback.PrimordialAudioTrackExecutor
import com.sedmelluq.lava.track.info.AudioTrackInfo
import kotlinx.atomicfu.atomic
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Abstract base for all audio tracks with an executor
 *
 * @param info Track info
 */
abstract class BaseAudioTrack(final override val info: AudioTrackInfo) : InternalAudioTrack {
    internal var accurateDuration by atomic(0L)

    private val initialExecutor = PrimordialAudioTrackExecutor(info)
    private var executorAssigned by atomic(false)

    @Volatile
    private var _activeExecutor: AudioTrackExecutor? = null

    @Volatile
    override var userData: Any? = null

    override val activeExecutor: AudioTrackExecutor
        get() = _activeExecutor ?: initialExecutor

    override val sourceManager: ItemSourceManager?
        get() = null

    override val state: AudioTrackState?
        get() = activeExecutor.state

    override val identifier: String
        get() = info.identifier

    override val isSeekable: Boolean
        get() = !info.isStream

    override var position: Long
        get() = activeExecutor.position
        set(position) {
            activeExecutor.position = position
        }

    override val duration: Long
        get() {
            val accurate = accurateDuration
            return if (accurate == 0L) info.length else accurate
        }

    override fun assignExecutor(executor: AudioTrackExecutor, applyPrimordialState: Boolean) {
        _activeExecutor = if (!executorAssigned) {
            executorAssigned = true
            if (applyPrimordialState) {
                initialExecutor.applyStateToExecutor(executor)
            }

            executor
        } else {
            error("Cannot play the same instance of a track twice, use track.makeClone().")
        }
    }

    override fun createLocalExecutor(playerManager: AudioPlayerManager?): AudioTrackExecutor? = null

    override fun stop() = activeExecutor.stop()

    override fun setMarker(marker: TrackMarker?) = activeExecutor.setMarker(marker)

    override fun makeClone(): AudioTrack? = makeShallowClone().also { it.userData = userData }

    override fun makeShallowClone(): AudioTrack = throw UnsupportedOperationException()

    @Suppress("UNCHECKED_CAST")
    override fun <T> getUserData(klass: Class<T>?): T? {
        val data = userData
        return if (data != null && klass!!.isAssignableFrom(data.javaClass)) data as? T else null
    }

    /* audio frame provider */
    override fun provide(): AudioFrame? = activeExecutor.provide()

    @Throws(TimeoutException::class, InterruptedException::class)
    override fun provide(timeout: Long, unit: TimeUnit): AudioFrame? = activeExecutor.provide(timeout, unit)

    override fun provide(targetFrame: MutableAudioFrame): Boolean = activeExecutor.provide(targetFrame)

    @Throws(TimeoutException::class, InterruptedException::class)
    override fun provide(targetFrame: MutableAudioFrame, timeout: Long, unit: TimeUnit): Boolean =
        activeExecutor.provide(targetFrame, timeout, unit)

    /* kotlin */
    override fun toString(): String =
        "${this::class.simpleName}(info=$info, position=$position, sourceManager=$sourceManager)"
}
