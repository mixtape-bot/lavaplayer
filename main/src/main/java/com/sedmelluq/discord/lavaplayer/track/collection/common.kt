package com.sedmelluq.discord.lavaplayer.track.collection

import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * This audio track collection represents a search result.
 */
@Serializable
@SerialName("search")
open class SearchResult(
    val query: String,
    override val tracks: MutableList<AudioTrack>,
    override val selectedTrack: AudioTrack? = null,
) : AudioTrackCollection {
    override val name: String = "Search result for: $query"
}

/**
 * Represents a (user) created track collection.
 */
@Serializable
@SerialName("playlist")
open class Playlist(
    override val name: String,
    override val tracks: MutableList<AudioTrack>,
    override val selectedTrack: AudioTrack? = null,
    val isAlbum: Boolean = false
) : AudioTrackCollection
