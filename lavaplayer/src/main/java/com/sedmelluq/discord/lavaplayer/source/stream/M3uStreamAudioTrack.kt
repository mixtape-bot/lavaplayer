package com.sedmelluq.discord.lavaplayer.source.stream

import com.sedmelluq.discord.lavaplayer.tools.io.ChainedInputStream
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor
import com.sedmelluq.lava.track.info.AudioTrackInfo
import java.io.InputStream

/**
 * Audio track that handles processing M3U segment streams which using MPEG-TS wrapped ADTS codec.
 *
 * @param trackInfo Track info
 */
abstract class M3uStreamAudioTrack(trackInfo: AudioTrackInfo) : DelegatedAudioTrack(trackInfo) {
    protected abstract val segmentUrlProvider: M3uStreamSegmentUrlProvider

    protected abstract val httpInterface: HttpInterface

    @Throws(Exception::class)
    protected abstract suspend fun processJoinedStream(localExecutor: LocalAudioTrackExecutor, stream: InputStream)

    @Throws(Exception::class)
    override suspend fun process(executor: LocalAudioTrackExecutor) {
        httpInterface.use { httpInterface ->
            ChainedInputStream { segmentUrlProvider.getNextSegmentStream(httpInterface) }.use { chainedInputStream ->
                processJoinedStream(executor, chainedInputStream)
            }
        }
    }
}
