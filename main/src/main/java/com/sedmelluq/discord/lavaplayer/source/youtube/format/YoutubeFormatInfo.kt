package com.sedmelluq.discord.lavaplayer.source.youtube.format

import com.sedmelluq.discord.lavaplayer.container.Formats.CODEC_AAC_LC
import com.sedmelluq.discord.lavaplayer.container.Formats.CODEC_OPUS
import com.sedmelluq.discord.lavaplayer.container.Formats.CODEC_VORBIS
import com.sedmelluq.discord.lavaplayer.container.Formats.MIME_AUDIO_MP4
import com.sedmelluq.discord.lavaplayer.container.Formats.MIME_AUDIO_WEBM
import com.sedmelluq.discord.lavaplayer.container.Formats.MIME_VIDEO_MP4
import com.sedmelluq.discord.lavaplayer.container.Formats.MIME_VIDEO_WEBM
import org.apache.http.entity.ContentType

/**
 * The mime type and codec info of a YouTube track format.
 */
enum class YoutubeFormatInfo(
    /**
     * Mime type of the format
     */
    val mimeType: String,
    /**
     * Codec name of  the format
     */
    val codec: String
) {
    WEBM_OPUS(MIME_AUDIO_WEBM, CODEC_OPUS),
    WEBM_VORBIS(MIME_AUDIO_WEBM, CODEC_VORBIS),
    MP4_AAC_LC(MIME_AUDIO_MP4, CODEC_AAC_LC),
    WEBM_VIDEO_VORBIS(MIME_VIDEO_WEBM, CODEC_VORBIS),
    MP4VIDEO_AAC_LC(MIME_VIDEO_MP4, CODEC_AAC_LC);

    companion object {
        /**
         * Find a matching format info instance from a content type.
         *
         * @param contentType The content type to use for matching against known formats
         * @return The format info entry that matches the content type
         */
        operator fun get(contentType: ContentType): YoutubeFormatInfo? {
            val codec = contentType.getParameter("codecs")

            return values().find { it.mimeType == contentType.mimeType && it.codec == codec }        // Check accurate matches
                ?: values().find { it.mimeType == contentType.mimeType && codec.contains(it.codec) } // Check substring matches
        }
    }
}
