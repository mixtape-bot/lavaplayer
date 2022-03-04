package com.sedmelluq.discord.lavaplayer.track.playback

import com.sedmelluq.discord.lavaplayer.format.AudioDataFormat
import com.sedmelluq.discord.lavaplayer.tools.extensions.wait
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Common parts of a frame buffer which are not likely to depend on the specific implementation.
 */
abstract class AbstractAudioFrameBuffer protected constructor(protected val format: AudioDataFormat) : AudioFrameBuffer {
    @JvmField
    protected val synchronizer: Any = Any()
    
    @JvmField
    protected val lock = Mutex()

    @JvmField
    @Volatile
    protected var locked: Boolean = false

    @JvmField
    @Volatile
    protected var receivedFrames: Boolean = false

    @JvmField
    protected var terminated: Boolean = false

    @JvmField
    protected var terminateOnEmpty: Boolean = false

    @JvmField
    protected var clearOnInsert: Boolean = false

    @Throws(InterruptedException::class)
    override suspend fun waitForTermination() {
        lock.withLock {
            while (!terminated) {
                // TODO: make sure this has the same result as Object#wait()?
                Thread.sleep(0L)
            }
        }
    }

    override suspend fun setTerminateOnEmpty() {
        lock.withLock {
            if (clearOnInsert) {
                clear()
                clearOnInsert = false
            }

            if (!terminated) {
                terminateOnEmpty = true
                signalWaiters()
            }
        }
    }

    override suspend fun setClearOnInsert() {
        lock.withLock {
            clearOnInsert = true
            terminateOnEmpty = false
        }
    }

    override fun lockBuffer() {
        locked = true
    }

    override fun hasClearOnInsert(): Boolean = clearOnInsert
    override fun hasReceivedFrames(): Boolean = receivedFrames

    protected abstract suspend fun signalWaiters()
}
