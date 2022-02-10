package com.sedmelluq.discord.lavaplayer.tools.extensions

import com.sedmelluq.discord.lavaplayer.manager.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.track.AudioReference
import com.sedmelluq.discord.lavaplayer.track.loader.ItemLoadResult
import com.sedmelluq.discord.lavaplayer.track.loader.ItemLoadResultAdapter
import kotlinx.coroutines.Deferred

fun AudioPlayerManager.loadItemAsync(
    identifier: String,
    handler: ItemLoadResultAdapter? = null,
): Deferred<ItemLoadResult> = loadItemAsync(AudioReference(identifier, null), handler)

fun AudioPlayerManager.loadItemAsync(
    reference: AudioReference,
    handler: ItemLoadResultAdapter? = null
): Deferred<ItemLoadResult> {
    val itemLoader = items.createItemLoader(reference)
    itemLoader.resultHandler = handler
    return itemLoader.loadAsync()
}
