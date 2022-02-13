package com.sedmelluq.discord.lavaplayer.source.soundcloud

import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3TrackProvider
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream
import com.sedmelluq.discord.lavaplayer.track.playback.AudioProcessingContext
import java.io.IOException

class SoundCloudMp3SegmentDecoder(private val nextStreamProvider: () -> SeekableInputStream) : SoundCloudSegmentDecoder {
    override fun prepareStream(beginning: Boolean) = Unit         // Nothing to do.

    override fun resetStream() = Unit // Nothing to do.

    override fun close() = Unit // Nothing to do.

    @Throws(InterruptedException::class, IOException::class)
    override fun playStream(context: AudioProcessingContext, startPosition: Long, desiredPosition: Long) {
        nextStreamProvider().use { stream ->
            Mp3TrackProvider(context, stream).use { trackProvider ->
                trackProvider.parseHeaders()
                trackProvider.provideFrames()
            }
        }
    }
}
