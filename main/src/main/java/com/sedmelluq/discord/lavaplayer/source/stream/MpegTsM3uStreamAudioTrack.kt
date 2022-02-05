package com.sedmelluq.discord.lavaplayer.source.stream

import com.sedmelluq.discord.lavaplayer.container.adts.AdtsAudioTrack
import com.sedmelluq.discord.lavaplayer.container.mpegts.MpegTsElementaryInputStream
import com.sedmelluq.discord.lavaplayer.container.mpegts.PesPacketInputStream
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor
import com.sedmelluq.lava.track.info.AudioTrackInfo
import java.io.InputStream

/**
 * @param trackInfo Track info
 */
abstract class MpegTsM3uStreamAudioTrack(trackInfo: AudioTrackInfo) : M3uStreamAudioTrack(trackInfo) {
    @Throws(Exception::class)
    override suspend fun processJoinedStream(localExecutor: LocalAudioTrackExecutor, stream: InputStream) {
        val elementaryInputStream = MpegTsElementaryInputStream(stream, MpegTsElementaryInputStream.ADTS_ELEMENTARY_STREAM)
        val pesPacketInputStream = PesPacketInputStream(elementaryInputStream)
        processDelegate(AdtsAudioTrack(info, pesPacketInputStream), localExecutor)
    }
}
