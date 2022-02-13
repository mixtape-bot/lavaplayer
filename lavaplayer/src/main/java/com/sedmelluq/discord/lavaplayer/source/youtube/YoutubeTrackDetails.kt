package com.sedmelluq.discord.lavaplayer.source.youtube

import com.sedmelluq.discord.lavaplayer.source.youtube.format.YoutubeTrackFormat
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface
import com.sedmelluq.lava.track.info.AudioTrackInfo

interface YoutubeTrackDetails {
    val trackInfo: AudioTrackInfo
    val playerScript: String?

    fun getFormats(httpInterface: HttpInterface, signatureResolver: YoutubeSignatureResolver): List<YoutubeTrackFormat>
}
