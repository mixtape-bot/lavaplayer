package com.sedmelluq.discord.lavaplayer.manager

import com.sedmelluq.discord.lavaplayer.filter.PcmFilterFactory
import kotlinx.atomicfu.atomic

/**
 * Mutable resources of an audio player which may be applied in real-time.
 */
class AudioPlayerResources {
    private val _volumeLevel = atomic(100)
    private val _filterFactory = atomic<PcmFilterFactory?>(null)
    private val _frameBufferDuration = atomic<Int?>(null)

    /**
     * Volume level of the audio, see {@link AudioPlayer#setVolume(int)}. Applied in real-time.
     */
    var volumeLevel: Int by _volumeLevel

    /**
     * Current PCM filter factory. Applied in real-time.
     */
    var filterFactory: PcmFilterFactory? by _filterFactory

    /**
     * Current frame buffer size. If not set, the global default is used. Changing this only affects the next track that
     * is started.
     */
    var frameBufferDuration: Int? by _frameBufferDuration
}
