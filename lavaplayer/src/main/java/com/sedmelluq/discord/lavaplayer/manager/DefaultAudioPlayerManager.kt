package com.sedmelluq.discord.lavaplayer.manager

import com.sedmelluq.discord.lavaplayer.source.ItemSourceManager
import com.sedmelluq.discord.lavaplayer.tools.GarbageCollectionMonitor
import com.sedmelluq.discord.lavaplayer.tools.extensions.addListener
import com.sedmelluq.discord.lavaplayer.tools.io.BuilderConfigurator
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable
import com.sedmelluq.discord.lavaplayer.tools.io.RequestConfigurator
import com.sedmelluq.discord.lavaplayer.track.InternalAudioTrack
import com.sedmelluq.discord.lavaplayer.track.TrackStateListener
import com.sedmelluq.discord.lavaplayer.track.encoder.DefaultTrackEncoder
import com.sedmelluq.discord.lavaplayer.track.loader.DefaultItemLoaderFactory
import com.sedmelluq.discord.lavaplayer.track.loader.ItemLoaderFactory
import com.sedmelluq.discord.lavaplayer.track.playback.AudioTrackExecutor
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor
import com.sedmelluq.lava.common.tools.DaemonThreadFactory
import com.sedmelluq.lava.common.tools.ExecutorTools
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * The default implementation of audio player manager.
 */
open class DefaultAudioPlayerManager : DefaultTrackEncoder(), AudioPlayerManager {
    companion object {
        private val DEFAULT_FRAME_BUFFER_DURATION = TimeUnit.SECONDS.toMillis(5)
        private val DEFAULT_CLEANUP_THRESHOLD = TimeUnit.MINUTES.toMillis(1)
    }

    // configuration
    private val cleanupThreshold = atomic<Long>(DEFAULT_CLEANUP_THRESHOLD)

    @Volatile
    private var httpConfigurator: RequestConfigurator? = null

    @Volatile
    private var httpBuilderConfigurator: BuilderConfigurator? = null

    // Executors
    protected val trackPlaybackService = ThreadPoolExecutor(1, Int.MAX_VALUE, 10, TimeUnit.SECONDS, SynchronousQueue(), DaemonThreadFactory("playback"))
    protected val trackPlaybackDispatcher = trackPlaybackService.asCoroutineDispatcher()
    protected val scheduledExecutorService = Executors.newScheduledThreadPool(1, DaemonThreadFactory("manager"))

    // Additional services
    protected val garbageCollectionMonitor = GarbageCollectionMonitor(scheduledExecutorService)
    protected val lifecycleManager = AudioPlayerLifecycleManager(scheduledExecutorService) { cleanupThreshold.value }

    // Configuration
    @Volatile
    override var isUsingSeekGhosting: Boolean = true
    override val configuration = AudioConfiguration()
    override val items: ItemLoaderFactory = DefaultItemLoaderFactory(this)

    @Volatile
    var trackStuckThresholdNanos: Long = TimeUnit.MILLISECONDS.toNanos(10000)

    @Volatile
    override var frameBufferDuration: Int = DEFAULT_FRAME_BUFFER_DURATION.toInt()
        set(value) {
            field = 200.coerceAtLeast(value)
        }

    override val sourceManagers: List<ItemSourceManager>
        get() = _sources

    private val _sources = mutableListOf<ItemSourceManager>()

    override fun shutdown() {
        /* disable gc monitoring */
        garbageCollectionMonitor.disable()

        /* shutdown other misc shit */
        items.shutdown()
        lifecycleManager.shutdown()

        /* shutdown source managers */
        sourceManagers.forEach(ItemSourceManager::shutdown)

        /* shutdown executors */
        ExecutorTools.shutdownExecutor(trackPlaybackService, "track playback")
        ExecutorTools.shutdownExecutor(scheduledExecutorService, "scheduled operations")

        /* cancel this coroutine scope. */
        cancel()
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ItemSourceManager> source(klass: Class<T>): T? =
        sourceManagers.firstOrNull { klass.isInstance(it) } as? T

    override fun enableGcMonitoring() =
        garbageCollectionMonitor.enable()

    override fun createPlayer(): AudioPlayer =
        DefaultAudioPlayer(this).also { it.addListener(lifecycleManager) }

    override fun registerSourceManager(sourceManager: ItemSourceManager) {
        _sources.add(sourceManager)
        if (sourceManager is HttpConfigurable) {
            val configurator = httpConfigurator
            if (configurator != null) {
                sourceManager.configureRequests(configurator)
            }

            val builderConfigurator = httpBuilderConfigurator
            if (builderConfigurator != null) {
                sourceManager.configureBuilder(builderConfigurator)
            }
        }
    }

    override fun setTrackStuckThreshold(trackStuckThreshold: Long) {
        trackStuckThresholdNanos = TimeUnit.MILLISECONDS.toNanos(trackStuckThreshold)
    }

    override fun setPlayerCleanupThreshold(newValue: Long) {
        cleanupThreshold.value = newValue
    }

    override fun setHttpRequestConfigurator(configurator: RequestConfigurator?) {
        httpConfigurator = configurator
        if (configurator != null) {
            sourceManagers
                .filterIsInstance<HttpConfigurable>()
                .forEach { it.configureRequests(configurator) }
        }
    }

    override fun setHttpBuilderConfigurator(configurator: BuilderConfigurator?) {
        httpBuilderConfigurator = configurator
        if (configurator != null) {
            sourceManagers
                .filterIsInstance<HttpConfigurable>()
                .forEach { it.configureBuilder(configurator) }
        }
    }

    /**
     * Executes an audio track with the given player and volume.
     *
     * @param listener      A listener for track state events
     * @param track         The audio track to execute
     * @param configuration The audio configuration to use for executing
     * @param resources     The resources used by the audio player
     */
    open fun executeTrack(
        listener: TrackStateListener,
        track: InternalAudioTrack,
        configuration: AudioConfiguration,
        resources: AudioPlayerResources
    ) {
        val executor = createExecutorForTrack(track, configuration, resources)
        launch(trackPlaybackDispatcher) {
            track.assignExecutor(executor, true)
            executor.execute(listener)
        }
    }

    protected open fun createExecutorForTrack(
        track: InternalAudioTrack,
        configuration: AudioConfiguration,
        resources: AudioPlayerResources
    ): AudioTrackExecutor {
        return track.createLocalExecutor(this)
            ?: return LocalAudioTrackExecutor(
                audioTrack = track,
                configuration = configuration,
                resources = resources,
                useSeekGhosting = isUsingSeekGhosting,
                bufferDuration = resources.frameBufferDuration ?: frameBufferDuration
            )
    }
}
