package com.sedmelluq.discord.lavaplayer.track

import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor
import com.sedmelluq.lava.track.info.AudioTrackInfo
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Audio track which delegates its processing to another track. The delegate does not have to be known when the
 * track is created, but is passed when processDelegate() is called.
 *
 * @param trackInfo Track info
 */
abstract class DelegatedAudioTrack(trackInfo: AudioTrackInfo) : BaseAudioTrack(trackInfo) {
    private var delegate: InternalAudioTrack? = null
    private val mutex = Mutex()

    override val duration: Long
        get() {
            return runBlocking {
                delegate?.duration ?: mutex.withLock(this@DelegatedAudioTrack) { delegate?.duration ?: super.duration }
            }
        }

    suspend fun processDelegate(delegate: InternalAudioTrack, localExecutor: LocalAudioTrackExecutor) {
        mutex.withLock(this) {
            this.delegate = delegate

            delegate.assignExecutor(localExecutor, false)
            delegate.process(localExecutor)
        }
    }
}
