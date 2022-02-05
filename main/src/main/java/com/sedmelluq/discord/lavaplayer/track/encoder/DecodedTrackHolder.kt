package com.sedmelluq.discord.lavaplayer.track.encoder

import com.sedmelluq.discord.lavaplayer.track.AudioTrack

/**
 * The result of decoding a track.
 *
 * @param decodedTrack The decoded track
 */
@JvmInline
value class DecodedTrackHolder(
    /**
     * The decoded track. This may be null if there was a track to decode, but the decoding could not be performed because
     * of an older serialization version or because the track source it used is not loaded.
     */
    @JvmField
    val decodedTrack: AudioTrack?
)
