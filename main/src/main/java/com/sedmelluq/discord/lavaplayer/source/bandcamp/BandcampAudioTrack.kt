package com.sedmelluq.discord.lavaplayer.source.bandcamp

import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor
import com.sedmelluq.lava.track.info.AudioTrackInfo
import mu.KotlinLogging
import org.apache.http.client.methods.HttpGet
import org.apache.http.util.EntityUtils
import java.io.IOException
import java.net.URI

/**
 * Audio track that handles processing Bandcamp tracks.
 *
 * @param trackInfo     Track info
 * @param sourceManager Source manager which was used to find this track
 */
class BandcampAudioTrack(
    trackInfo: AudioTrackInfo,
    override val sourceManager: BandcampItemSourceManager
) : DelegatedAudioTrack(trackInfo) {
    companion object {
        private val log = KotlinLogging.logger { }
    }

    @Throws(Exception::class)
    override suspend fun process(executor: LocalAudioTrackExecutor) {
        sourceManager.httpInterface.use { httpInterface ->
            log.debug { "Loading Bandcamp track page from URL: ${info.identifier}" }

            val trackMediaUrl = getTrackMediaUrl(httpInterface)
            PersistentHttpStream(httpInterface, URI(trackMediaUrl), null).use { stream ->
                log.debug { "Starting Bandcamp track from URL: $trackMediaUrl" }
                processDelegate(Mp3AudioTrack(info, stream), executor)
            }
        }
    }

    @Throws(IOException::class)
    private fun getTrackMediaUrl(httpInterface: HttpInterface): String {
        httpInterface.execute(HttpGet(info.uri)).use { response ->
            HttpClientTools.assertSuccessWithContent(response, "track page")
            val responseText = EntityUtils.toString(response.entity, Charsets.UTF_8)
            val trackList = sourceManager.readTrackListInformation(responseText)
            return trackList.trackInfo.first().file.url
        }
    }

    override fun makeShallowClone(): AudioTrack =
        BandcampAudioTrack(info, sourceManager)
}
