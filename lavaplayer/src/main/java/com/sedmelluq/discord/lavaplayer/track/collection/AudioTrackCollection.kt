package com.sedmelluq.discord.lavaplayer.track.collection

import com.sedmelluq.discord.lavaplayer.track.AudioItem
import com.sedmelluq.discord.lavaplayer.track.AudioTrack

interface AudioTrackCollection : AudioItem {
    /**
     * The name of this audio track collection.
     */
    val name: String

    /**
     * List of tracks in this collection
     */
    val tracks: MutableList<AudioTrack>

    /**
     * The track that is explicitly selected, may be null. This same instance occurs in the track list.
     */
    val selectedTrack: AudioTrack?
}
