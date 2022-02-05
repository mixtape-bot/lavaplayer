package com.sedmelluq.discord.lavaplayer.source.youtube.format

import org.apache.http.entity.ContentType

import java.net.URI
import java.net.URISyntaxException

/**
 * Describes an available media format for a track
 *
 * @param baseUrl       Base URL for the playback of this format
 * @param contentType   Content type of the format
 * @param bitrate       Bitrate of the format
 * @param contentLength Length in bytes of the media
 * @param signature     Cipher signature for this format
 * @param signatureKey  The key to use for deciphered signature in the final playback URL
 */
data class YoutubeTrackFormat(
    private val baseUrl: String,
    /**
     * Bitrate of the format
     */
    val bitrate: Long,
    /**
     * Mime type of the format
     */
    val contentType: ContentType,
    /**
     * Length in bytes of the media
     */
    val contentLength: Long,
    /**
     * Cipher signature for this format
     */
    val signature: String?,
    /**
     * The key to use for deciphered signature in the final playback URL
     */
    val signatureKey: String
) {
    /**
     * Format container and codec info
     */
    val info = YoutubeFormatInfo[contentType]

    /**
     * Base URL for the playback of this format
     */
    val url: URI
        get() = try {
            URI(baseUrl)
        } catch (e: URISyntaxException) {
            throw RuntimeException(e)
        }
}
