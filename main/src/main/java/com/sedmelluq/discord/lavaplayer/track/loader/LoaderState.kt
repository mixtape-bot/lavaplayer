package com.sedmelluq.discord.lavaplayer.track.loader

import com.sedmelluq.discord.lavaplayer.track.AudioReference
import com.sedmelluq.discord.lavaplayer.track.loader.message.ItemLoaderMessages

data class LoaderState(
    /**
     * The audio reference that holds the identifier that a specific source manager should be able
     * to find a track with.
     */
    val reference: AudioReference,
    /**
     * Used for communicating between the item loader and source managers.
     */
    val messages: ItemLoaderMessages
) {
    companion object {
        val NONE = LoaderState(AudioReference.NO_TRACK, ItemLoaderMessages.None)
    }
}
