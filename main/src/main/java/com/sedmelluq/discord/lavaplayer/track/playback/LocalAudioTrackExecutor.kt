package com.sedmelluq.discord.lavaplayer.track.playback

import com.sedmelluq.discord.lavaplayer.manager.AudioConfiguration
import com.sedmelluq.discord.lavaplayer.manager.AudioPlayerResources
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools
import com.sedmelluq.discord.lavaplayer.tools.extensions.friendlyError
import com.sedmelluq.discord.lavaplayer.tools.extensions.rethrow
import com.sedmelluq.discord.lavaplayer.track.AudioTrackState
import com.sedmelluq.discord.lavaplayer.track.InternalAudioTrack
import com.sedmelluq.discord.lavaplayer.track.TrackStateListener
import com.sedmelluq.discord.lavaplayer.track.marker.TrackMarker
import com.sedmelluq.discord.lavaplayer.track.marker.TrackMarkerHandler.MarkerState
import com.sedmelluq.discord.lavaplayer.track.marker.TrackMarkerManager
import com.sedmelluq.lava.common.tools.exception.FriendlyException
import com.sedmelluq.lava.common.tools.exception.friendlyError
import com.sedmelluq.lava.common.tools.exception.wrapUnfriendlyException
import kotlinx.atomicfu.atomic
import mu.KotlinLogging
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Handles the execution and output buffering of an audio track.
 *
 * @param audioTrack      The audio track that this executor executes
 * @param configuration   Configuration to use for audio processing
 * @param resources       Mutable player resources (for example volume).
 * @param useSeekGhosting Whether to keep providing old frames continuing from the previous position during a seek
 * until frames from the new position arrive.
 * @param bufferDuration  The size of the frame buffer in milliseconds
 */
class LocalAudioTrackExecutor(
    private val audioTrack: InternalAudioTrack,
    configuration: AudioConfiguration,
    resources: AudioPlayerResources,
    private val useSeekGhosting: Boolean,
    bufferDuration: Int
) : AudioTrackExecutor {
    companion object {
        private val log = KotlinLogging.logger { }
    }

    /* atomics */
    private val _state = atomic(AudioTrackState.INACTIVE)
    private var queuedStop by atomic(false)
    private var queuedSeek by atomic(-1L)
    private var lastFrameTimecode by atomic(0L)
    private var playingThread by atomic<Thread?>(null)

    /* other */
    @Volatile
    private var trackException: Throwable? = null
    private val actionSynchronizer = Any()
    private val markerTracker = TrackMarkerManager()
    private var externalSeekPosition: Long = -1
    private var interruptableForSeek = false

    private val isPerformingSeek: Boolean
        get() = queuedSeek != -1L || useSeekGhosting && audioBuffer.hasClearOnInsert()

    override val audioBuffer = configuration.frameBufferFactory.create(bufferDuration, configuration.outputFormat) { queuedStop }
    override var position: Long
        get() = queuedSeek.takeUnless { it == -1L } ?: lastFrameTimecode
        set(timecode) {
            if (!audioTrack.isSeekable) {
                return
            }

            synchronized(actionSynchronizer) {
                queuedSeek = timecode.coerceAtLeast(0)
                if (!useSeekGhosting) {
                    audioBuffer.clear()
                }

                interruptForSeek()
            }
        }

    override var state: AudioTrackState by _state

    val processingContext = AudioProcessingContext(configuration, audioBuffer, resources, configuration.outputFormat)
    val stackTrace: Array<StackTraceElement>?
        get() {
            val thread = playingThread
            if (thread != null) {
                val trace = thread.stackTrace
                if (playingThread == thread) {
                    return trace
                }
            }

            return null
        }

    override fun failedBeforeLoad(): Boolean = trackException != null && !audioBuffer.hasReceivedFrames()

    override suspend fun execute(listener: TrackStateListener) {
        var interrupt: InterruptedException? = null
        if (Thread.interrupted()) {
            log.debug { "Cleared a stray interrupt." }
        }

        if (playingThread == null) {
            playingThread = Thread.currentThread()
            log.debug { "Starting to play track ${audioTrack.info.identifier} locally with listener $listener" }
            state = AudioTrackState.LOADING

            try {
                audioTrack.process(this)
                log.debug { "Playing track ${audioTrack.identifier} finished or was stopped." }
            } catch (e: Throwable) {
                // Temporarily clear the interrupted status, so it would not disrupt listener methods.
                interrupt = findInterrupt(e)

                if (interrupt != null && checkStopped()) {
                    log.debug { "Track ${audioTrack.identifier} was interrupted outside of execution loop." }
                } else {
                    audioBuffer.setTerminateOnEmpty()

                    val exception = e.wrapUnfriendlyException("Something broke when playing the track.", FriendlyException.Severity.FAULT)
                    trackException = exception

                    listener.onTrackException(audioTrack, exception)

                    log.friendlyError(exception) { "playback of ${audioTrack.identifier}" }

                    e.rethrow()
                }
            } finally {
                synchronized(actionSynchronizer) {
                    interrupt = interrupt ?: findInterrupt(null)
                    if (playingThread == Thread.currentThread()) {
                        playingThread = null
                    }

                    markerTracker.trigger(MarkerState.ENDED)
                    _state.value = AudioTrackState.FINISHED
                }

                if (interrupt != null) {
                    Thread.currentThread().interrupt()
                }
            }
        } else {
            log.warn { "Tried to start an already playing track ${audioTrack.identifier}" }
        }
    }

    override fun stop() {
        synchronized(actionSynchronizer) {
            val thread = playingThread
            if (thread != null) {
                log.debug { "Requesting stop for track ${audioTrack.identifier}" }
                if (!queuedStop) {
                    queuedStop = true
                }

                thread.interrupt()
            } else {
                log.debug { "Tried to stop track ${audioTrack.identifier} which is not playing." }
            }
        }
    }

    /**
     * @return True if the track has been scheduled to stop and then clears the scheduled stop bit.
     */
    fun checkStopped(): Boolean {
        if (queuedStop) {
            queuedStop = false
            state = AudioTrackState.STOPPING
            return true
        }

        return false
    }

    /**
     * Wait until all the frames from the frame buffer have been consumed. Keeps the buffering thread alive to keep it
     * interruptable for seeking until buffer is empty.
     *
     * @throws InterruptedException When interrupted externally (or for seek/stop).
     */
    @Throws(InterruptedException::class)
    fun waitOnEnd() {
        audioBuffer.setTerminateOnEmpty()
        audioBuffer.waitForTermination()
    }

    /**
     * Interrupt the buffering thread, either stop or seek should have been set beforehand.
     *
     * @return True if there was a thread to interrupt.
     */
    fun interrupt(): Boolean {
        synchronized(actionSynchronizer) {
            return playingThread?.interrupt() == null
        }
    }

    override fun setMarker(marker: TrackMarker?) {
        markerTracker[marker] = position
    }

    /**
     * Execute the read and seek loop for the track.
     *
     * @param readExecutor Callback for reading the track
     * @param seekExecutor Callback for performing a seek on the track, may be null on a non-seekable track
     */
    @JvmOverloads
    fun executeProcessingLoop(readExecutor: ReadExecutor, seekExecutor: SeekExecutor?, waitOnEnd: Boolean = true) {
        var proceed = true
        if (checkPendingSeek(seekExecutor) == SeekResult.EXTERNAL_SEEK) {
            return
        }

        while (proceed) {
            state = AudioTrackState.PLAYING
            proceed = false

            try {
                // An interrupt may have been placed while we were handling the previous one.
                if (Thread.interrupted() && !handlePlaybackInterrupt(null, seekExecutor)) {
                    break
                }

                setInterruptableForSeek(true)
                readExecutor.performRead()
                setInterruptableForSeek(false)

                if (seekExecutor != null && externalSeekPosition != -1L) {
                    val nextPosition = externalSeekPosition
                    externalSeekPosition = -1
                    performSeek(seekExecutor, nextPosition)
                    proceed = true
                } else if (waitOnEnd) {
                    waitOnEnd()
                }
            } catch (e: Exception) {
                setInterruptableForSeek(false)

                val interruption = findInterrupt(e)
                proceed = interruption?.let { handlePlaybackInterrupt(it, seekExecutor) }
                    ?: throw e.wrapUnfriendlyException("Something went wrong when decoding the track.", FriendlyException.Severity.FAULT)
            }
        }
    }

    private fun setInterruptableForSeek(state: Boolean) {
        synchronized(actionSynchronizer) { interruptableForSeek = state }
    }

    private fun interruptForSeek() {
        var interrupted = false
        synchronized(actionSynchronizer) {
            if (interruptableForSeek) {
                interruptableForSeek = false
                val nullable = playingThread?.interrupt()
                if (nullable == null) {
                    interrupted = true
                }
            }
        }

        if (interrupted) {
            log.debug { "Interrupting playing thread to perform a seek ${audioTrack.identifier}" }
        } else {
            log.debug { "Seeking on track ${audioTrack.identifier} while not in playback loop." }
        }
    }

    private fun handlePlaybackInterrupt(interruption: InterruptedException?, seekExecutor: SeekExecutor?): Boolean {
        Thread.interrupted()
        if (checkStopped()) {
            markerTracker.trigger(MarkerState.STOPPED)
            return false
        }

        val seekResult = checkPendingSeek(seekExecutor)
        return if (seekResult != SeekResult.NO_SEEK) {
            // Double-check, might have received a stop request while seeking
            if (checkStopped()) {
                markerTracker.trigger(MarkerState.STOPPED)
                false
            } else {
                seekResult == SeekResult.INTERNAL_SEEK
            }
        } else if (interruption != null) {
            Thread.currentThread().interrupt()
            friendlyError("The track was unexpectedly terminated.", FriendlyException.Severity.SUSPICIOUS, interruption)
        } else {
            true
        }
    }

    private fun findInterrupt(throwable: Throwable?): InterruptedException? {
        var exception = ExceptionTools.findDeepException(throwable, InterruptedException::class.java)
        if (exception == null) {
            val ioException = ExceptionTools.findDeepException(throwable, InterruptedIOException::class.java)
            if (ioException != null && (ioException.message == null || !ioException.message!!.contains("timed out"))) {
                exception = InterruptedException(ioException.message)
            }
        }

        return if (exception == null && Thread.interrupted()) InterruptedException() else exception
    }

    /**
     * Performs a seek if it scheduled.
     *
     * @param seekExecutor Callback for performing a seek on the track
     * @return True if a seek was performed
     */
    private fun checkPendingSeek(seekExecutor: SeekExecutor?): SeekResult {
        if (!audioTrack.isSeekable) {
            return SeekResult.NO_SEEK
        }

        var seekPosition: Long
        synchronized(actionSynchronizer) {
            seekPosition = queuedSeek
            if (seekPosition == -1L) {
                return SeekResult.NO_SEEK
            }

            log.debug { "Track ${audioTrack.identifier} interrupted for seeking to $seekPosition." }
            applySeekState(seekPosition)
        }

        return if (seekExecutor != null) {
            performSeek(seekExecutor, seekPosition)
            SeekResult.INTERNAL_SEEK
        } else {
            externalSeekPosition = seekPosition
            SeekResult.EXTERNAL_SEEK
        }
    }

    private fun performSeek(seekExecutor: SeekExecutor, seekPosition: Long) {
        try {
            seekExecutor.performSeek(seekPosition)
        } catch (e: Exception) {
            throw e.wrapUnfriendlyException("Something went wrong when seeking to a position.", FriendlyException.Severity.FAULT)
        }
    }

    private fun applySeekState(seekPosition: Long) {
        state = AudioTrackState.SEEKING
        if (useSeekGhosting) {
            audioBuffer.setClearOnInsert()
        } else {
            audioBuffer.clear()
        }

        queuedSeek = -1
        markerTracker.checkSeekTimecode(seekPosition)
    }

    override fun provide(): AudioFrame? {
        val frame = audioBuffer.provide()
        processProvidedFrame(frame)

        return frame
    }

    @Throws(TimeoutException::class, InterruptedException::class)
    override fun provide(timeout: Long, unit: TimeUnit): AudioFrame? {
        val frame = audioBuffer.provide(timeout, unit)
        processProvidedFrame(frame)
        return frame
    }

    override fun provide(targetFrame: MutableAudioFrame): Boolean {
        if (audioBuffer.provide(targetFrame)) {
            processProvidedFrame(targetFrame)
            return true
        }

        return false
    }

    @Throws(TimeoutException::class, InterruptedException::class)
    override fun provide(targetFrame: MutableAudioFrame, timeout: Long, unit: TimeUnit): Boolean {
        if (audioBuffer.provide(targetFrame, timeout, unit)) {
            processProvidedFrame(targetFrame)
            return true
        }

        return true
    }

    private fun processProvidedFrame(frame: AudioFrame?) {
        if (frame != null && !frame.isTerminator) {
            if (!isPerformingSeek) {
                markerTracker.checkPlaybackTimecode(frame.timecode)
            }

            lastFrameTimecode = frame.timecode
        }
    }

    private enum class SeekResult {
        NO_SEEK,
        INTERNAL_SEEK,
        EXTERNAL_SEEK
    }

    /**
     * Read executor, see method description
     */
    fun interface ReadExecutor {
        /**
         * Reads until interrupted or EOF.
         *
         * @throws InterruptedException When interrupted externally (or for seek/stop).
         */
        @Throws(Exception::class)
        fun performRead()
    }

    /**
     * Seek executor, see method description
     */
    fun interface SeekExecutor {
        /**
         * Perform a seek to the specified position
         *
         * @param position Position in milliseconds
         */
        @Throws(Exception::class)
        fun performSeek(position: Long)
    }
}
