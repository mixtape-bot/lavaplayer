package com.sedmelluq.discord.lavaplayer.track

import com.sedmelluq.lava.track.info.AudioTrackInfo

fun interface AudioTrackFactory {
    /**
     * Creates a new [AudioTrack] from the provided [AudioTrackInfo]
     *
     * @param info The info to create the new [AudioTrack] from.
     *
     * @return a new [AudioTrack]
     */
    fun create(info: AudioTrackInfo): AudioTrack
}
