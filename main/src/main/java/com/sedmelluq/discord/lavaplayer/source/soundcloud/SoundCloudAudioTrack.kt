package com.sedmelluq.discord.lavaplayer.source.soundcloud

import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudHelper.loadPlaybackUrl
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor
import com.sedmelluq.lava.track.info.AudioTrackInfo
import mu.KotlinLogging
import java.io.IOException
import java.net.URI

/**
 * Audio track that handles processing SoundCloud tracks.
 *
 * @param trackInfo     Track info
 * @param sourceManager Source manager which was used to find this track
 */
class SoundCloudAudioTrack(
    trackInfo: AudioTrackInfo,
    override val sourceManager: SoundCloudItemSourceManager
) : DelegatedAudioTrack(trackInfo) {
    companion object {
        private val log = KotlinLogging.logger { }
    }

    @Throws(Exception::class)
    override suspend fun process(executor: LocalAudioTrackExecutor) {
        sourceManager.httpInterface.use { httpInterface ->
            playFromIdentifier(httpInterface, info.identifier, false, executor)
        }
    }

    @Throws(Exception::class)
    private suspend fun playFromIdentifier(
        httpInterface: HttpInterface,
        identifier: String,
        recursion: Boolean,
        localExecutor: LocalAudioTrackExecutor
    ) {
        val m3uInfo = sourceManager.formatHandler.getM3uInfo(identifier)
        if (m3uInfo != null) {
            return processDelegate(SoundCloudM3uAudioTrack(info, httpInterface, m3uInfo), localExecutor)
        }

        val mp3LookupUrl = sourceManager.formatHandler.getMp3LookupUrl(identifier)
        if (mp3LookupUrl != null) {
            val playbackUrl = loadPlaybackUrl(httpInterface, identifier.substring(2))
            return loadFromMp3Url(localExecutor, httpInterface, playbackUrl)
        }

        if (!recursion) {
            // Old "track ID" entry? Let's "load" it to get url.
            val track = sourceManager.loadFromTrackPage(info.uri)
                ?: return

            playFromIdentifier(httpInterface, track.identifier, true, localExecutor)
        }
    }

    @Throws(Exception::class)
    private suspend fun loadFromMp3Url(localExecutor: LocalAudioTrackExecutor, httpInterface: HttpInterface, trackUrl: String) {
        log.debug { "Starting SoundCloud track from URL: $trackUrl" }
        PersistentHttpStream(httpInterface, URI(trackUrl), null).use { stream ->
            if (!HttpClientTools.isSuccessWithContent(stream.checkStatusCode())) {
                throw IOException("Invalid status code for soundcloud stream: " + stream.checkStatusCode())
            }

            processDelegate(Mp3AudioTrack(info, stream), localExecutor)
        }
    }

    override fun makeShallowClone(): AudioTrack =
        SoundCloudAudioTrack(info, sourceManager)
}
