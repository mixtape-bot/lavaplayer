package com.sedmelluq.discord.lavaplayer.manager

import com.sedmelluq.discord.lavaplayer.manager.event.AudioEvent
import com.sedmelluq.discord.lavaplayer.manager.event.AudioEventListener
import com.sedmelluq.discord.lavaplayer.manager.event.TrackEndEvent
import com.sedmelluq.discord.lavaplayer.manager.event.TrackStartEvent
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.InternalCoroutinesApi
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Triggers cleanup checks on all active audio players at a fixed interval.
 *
 * @param scheduler        Scheduler to use for the cleanup check task
 * @param cleanupThreshold Threshold for player cleanup
 */
@OptIn(InternalCoroutinesApi::class)
class AudioPlayerLifecycleManager(
    private val scheduler: ScheduledExecutorService,
    private val cleanupThreshold: () -> Long
) : Runnable, AudioEventListener {
    companion object {
        private const val CHECK_INTERVAL: Long = 10000
    }

    private val activePlayers = ConcurrentHashMap<AudioPlayer, AudioPlayer>()
    private var scheduledTask by atomic<ScheduledFuture<*>?>(null)

    /**
     * initialize the scheduled task.
     */
    init {
        val task = scheduler.scheduleAtFixedRate(this, CHECK_INTERVAL, CHECK_INTERVAL, TimeUnit.MILLISECONDS)
        if (scheduledTask == null) {
            scheduledTask = task
            task.cancel(false)
        }
    }

    /**
     * Stop the scheduled task.
     */
    fun shutdown() {
        scheduledTask?.cancel(false)
        scheduledTask = null
    }

    override suspend fun onEvent(event: AudioEvent) {
        when (event) {
            is TrackStartEvent -> activePlayers[event.player] = event.player
            is TrackEndEvent -> activePlayers.remove(event.player)
        }
    }

    override fun run() {
        for (player in activePlayers.keys) {
            player.checkCleanup(cleanupThreshold())
        }
    }
}
