package com.sedmelluq.discord.lavaplayer.tools.extensions

import com.sedmelluq.discord.lavaplayer.container.MediaContainerDetection.Companion.UNKNOWN_ARTIST
import com.sedmelluq.discord.lavaplayer.container.MediaContainerDetection.Companion.UNKNOWN_TITLE
import com.sedmelluq.discord.lavaplayer.tools.Units
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream
import com.sedmelluq.discord.lavaplayer.track.AudioReference
import com.sedmelluq.lava.track.info.AudioTrackInfoBuilder

/**
 * Creates an instance of an audio track builder based on an audio reference and a stream.
 *
 * @param reference Audio reference to use as the starting point for the builder.
 * @param stream    Stream to get additional data from.
 * @return An instance of the builder with the reference and track info providers from the stream pre-applied.
 */
@JvmOverloads
fun AudioTrackInfoBuilder.Companion.create(
    reference: AudioReference?,
    stream: SeekableInputStream?,
    build: AudioTrackInfoBuilder.() -> Unit = {}
) = apply {
    author = UNKNOWN_ARTIST
    title = UNKNOWN_TITLE
    length = Units.DURATION_MS_UNKNOWN

    apply(reference)
    if (stream != null) {
        for (provider in stream.trackInfoProviders) {
            apply(provider)
        }
    }

    apply(build)
}
