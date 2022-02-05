package com.sedmelluq.discord.lavaplayer.source.common

import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager
import com.sedmelluq.discord.lavaplayer.track.AudioTrackFactory
import com.sedmelluq.discord.lavaplayer.track.collection.AudioTrackCollection

interface AudioTrackCollectionLoader {
    /**
     * Loads a new audio track collection with the provided [identifier]
     *
     * @param identifier The identifier to use
     * @param httpInterfaceManager
     * @param trackFactory
     */
    fun load(
        identifier: String,
        httpInterfaceManager: HttpInterfaceManager,
        trackFactory: AudioTrackFactory
    ): AudioTrackCollection?
}
