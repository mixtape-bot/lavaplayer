package com.sedmelluq.discord.lavaplayer.tools.extensions

import com.sedmelluq.discord.lavaplayer.track.collection.AudioTrackCollection
import com.sedmelluq.discord.lavaplayer.track.collection.Playlist
import com.sedmelluq.discord.lavaplayer.track.collection.SearchResult

val AudioTrackCollection.isPlaylist: Boolean
    get() = this is Playlist

val AudioTrackCollection.isSearchResult: Boolean
    get() = this is SearchResult
