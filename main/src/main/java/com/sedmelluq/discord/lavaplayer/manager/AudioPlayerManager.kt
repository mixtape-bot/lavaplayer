package com.sedmelluq.discord.lavaplayer.manager

import com.sedmelluq.discord.lavaplayer.source.common.SourceRegistry
import com.sedmelluq.discord.lavaplayer.tools.io.BuilderConfigurator
import com.sedmelluq.discord.lavaplayer.tools.io.RequestConfigurator
import com.sedmelluq.discord.lavaplayer.track.encoder.TrackEncoder
import com.sedmelluq.discord.lavaplayer.track.loader.ItemLoaderFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

/**
 * Audio player manager which is used for creating audio players and loading tracks and playlists.
 */
interface AudioPlayerManager : TrackEncoder, SourceRegistry, CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + SupervisorJob()

    /**
     * The item loader factory for this player manager.
     */
    val items: ItemLoaderFactory

    /**
     * The length of the internal buffer for audio in milliseconds.
     */
    var frameBufferDuration: Int

    /**
     * Seek ghosting is the effect where while a seek is in progress, buffered audio from the previous location will be
     * served until seek is ready or the buffer is empty.
     */
    var isUsingSeekGhosting: Boolean

    /**
     * Audio processing configuration used for tracks executed by this manager.
     */
    val configuration: AudioConfiguration

    /**
     * Shut down the manager. All threads will be stopped, the manager cannot be used any further. All players created
     * with this manager will stop and all source managers registered to this manager will also be shut down.
     *
     *
     * Every thread created by the audio manager is a daemon thread, so calling this is not required for an application
     * to be able to gracefully shut down, however it should be called if the application continues without requiring this
     * manager any longer.
     */
    fun shutdown()

    /**
     * Enable reporting GC pause length statistics to log (warn level with lengths bad for latency, debug level otherwise)
     */
    fun enableGcMonitoring()

    /**
     * Sets the threshold for how long a track can be stuck until the TrackStuckEvent is sent out. A track is considered
     * to be stuck if the player receives requests for audio samples from the track, but the audio frame provider of that
     * track has been returning no data for the specified time.
     *
     * @param trackStuckThreshold The threshold in milliseconds.
     */
    fun setTrackStuckThreshold(trackStuckThreshold: Long)

    /**
     * Sets the threshold for clearing an audio player when it has not been queried for the specified amount of time.
     *
     * @param newValue The new threshold to use (in milliseconds).
     */
    fun setPlayerCleanupThreshold(newValue: Long)

    /**
     * @return New audio player.
     */
    fun createPlayer(): AudioPlayer

    /**
     * @param configurator Function used to reconfigure the request config of all sources which perform HTTP requests.
     * Applied to all current and future registered sources. Setting this while sources are already in
     * use will close all active connections, so this should be called before the sources have been
     * used.
     */
    fun setHttpRequestConfigurator(configurator: RequestConfigurator?)

    /**
     * @param configurator Function used to reconfigure the HTTP builder of all sources which perform HTTP requests.
     * Applied to all current and future registered sources. Setting this while sources are already in
     * use will close all active connections, so this should be called before the sources have been
     * used.
     */
    fun setHttpBuilderConfigurator(configurator: BuilderConfigurator?)
}
