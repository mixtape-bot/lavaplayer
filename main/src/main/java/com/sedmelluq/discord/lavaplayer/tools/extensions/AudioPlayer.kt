package com.sedmelluq.discord.lavaplayer.tools.extensions

import com.sedmelluq.discord.lavaplayer.manager.AudioPlayer
import com.sedmelluq.discord.lavaplayer.manager.event.AudioEvent
import com.sedmelluq.discord.lavaplayer.manager.event.AudioEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import mu.KotlinLogging

@PublishedApi
internal val audioPlayerOnLog = KotlinLogging.logger("AudioPlayer.on")

/**
 * Add a listener to events from this player.
 *
 * @param listener New listener
 */
fun AudioPlayer.addListener(listener: AudioEventListener): Job {
    return on<AudioEvent>(this) { listener.onEvent(this) }
}

inline fun <reified T : AudioEvent> AudioPlayer.on(
    scope: CoroutineScope = this,
    noinline block: suspend T.() -> Unit
): Job {
    return events
        .filterIsInstance<T>()
        .onEach { event ->
            launch {
                event
                    .runCatching { block() }
                    .onFailure { audioPlayerOnLog.error(it) { "Error occurred while handling event ${event::class.qualifiedName}" } }
            }
        }
        .launchIn(scope)
}
