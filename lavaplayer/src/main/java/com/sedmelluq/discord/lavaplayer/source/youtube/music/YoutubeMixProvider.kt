package com.sedmelluq.discord.lavaplayer.source.youtube.music

import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeConstants
import com.sedmelluq.discord.lavaplayer.tools.ThumbnailTools
import com.sedmelluq.discord.lavaplayer.tools.extensions.parseMilliseconds
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface
import com.sedmelluq.discord.lavaplayer.tools.json.JsonBrowser
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackFactory
import com.sedmelluq.discord.lavaplayer.track.collection.AudioTrackCollection
import com.sedmelluq.discord.lavaplayer.track.collection.Playlist
import com.sedmelluq.lava.common.tools.exception.FriendlyException
import com.sedmelluq.lava.common.tools.exception.friendlyError
import com.sedmelluq.lava.track.info.BasicAudioTrackInfo
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import java.io.IOException

/**
 * Handles loading of YouTube mixes.
 */
class YoutubeMixProvider : YoutubeMixLoader {
    /**
     * Loads tracks from mix in parallel into a playlist entry.
     *
     * @param identifier ID of the mix
     * @param selectedVideoId Selected track, [AudioTrackCollection.selectedTrack] will return this.
     *
     * @return Playlist of the tracks in the mix.
     */
    override fun load(
        httpInterface: HttpInterface,
        identifier: String,
        selectedVideoId: String?,
        trackFactory: AudioTrackFactory
    ): AudioTrackCollection {
        var playlistTitle = "YouTube mix"
        val tracks: MutableList<AudioTrack> = mutableListOf()

        try {
            val post = HttpPost(YoutubeConstants.NEXT_URL)
            post.entity = StringEntity(YoutubeConstants.NEXT_PAYLOAD.format(selectedVideoId, identifier))

            httpInterface.execute(post).use { response ->
                HttpClientTools.assertSuccessWithContent(response, "mix response")

                val body = JsonBrowser.parse(response.entity.content)
                val playlist = body["contents"]["singleColumnWatchNextResults"]["playlist"]["playlist"]

                val title = playlist["title"]
                if (!title.isNull) {
                    playlistTitle = title.text!!
                }

                extractPlaylistTracks(playlist["contents"], tracks, trackFactory)
            }
        } catch (e: IOException) {
            friendlyError("Could not read mix page.", FriendlyException.Severity.SUSPICIOUS, e)
        }

        if (tracks.isEmpty()) {
            friendlyError("Could not find tracks from mix.", FriendlyException.Severity.SUSPICIOUS)
        }

        val selectedTrack = selectedVideoId?.let {
            findSelectedTrack(tracks, it)
        }

        return Playlist(playlistTitle, tracks, selectedTrack)
    }

    private fun extractPlaylistTracks(
        browser: JsonBrowser,
        tracks: MutableList<AudioTrack>,
        trackFactory: AudioTrackFactory,
        playlistId: String? = null
    ) {
        for (renderer in browser.values().map { it["playlistPanelVideoRenderer"] }) {
            if (!renderer.get("unplayableText").isNull) {
                return;
            }

            val videoId = renderer["videoId"].text
                ?: continue

            /* create the audio track. */
            val trackInfo = BasicAudioTrackInfo(
                title = renderer["title"]["runs"][0]["text"].text!!,
                author = renderer["longBylineText"]["runs"][0]["text"].text!!,
                length = renderer["lengthText"]["runs"][0]["text"].text!!.parseMilliseconds(),
                identifier = videoId,
                uri = "${YoutubeConstants.WATCH_MUSIC_URL_PREFIX}$videoId${playlistId?.let { "&list=$it" } ?: ""}",
                artworkUrl = ThumbnailTools.extractYouTube(renderer, videoId),
            )

            tracks.add(trackFactory.create(trackInfo))
        }
    }

    private fun findSelectedTrack(tracks: List<AudioTrack>, selectedVideoId: String): AudioTrack? =
        tracks.find { it.identifier == selectedVideoId }
}

