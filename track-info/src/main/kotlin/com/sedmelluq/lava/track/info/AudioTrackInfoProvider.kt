package com.sedmelluq.lava.track.info

/**
 * Provider for audio track info.
 */
interface AudioTrackInfoProvider {
    /**
     * The track title, or `null` if this provider does not know it.
     */
    val title: String?

    /**
     * The track author, or `null` if this provider does not know it.
     */
    val author: String?

    /**
     * The track length in milliseconds, or `null` if this provider does not know it.
     */
    val length: Long?

    /**
     * The track identifier, or `null` if this provider does not know it.
     */
    val identifier: String?

    /**
     * The track URI, or `null` if this provider does not know it.
     */
    val uri: String?

    /**
     * The track Artwork URL, or `null` if this provider does not know it.
     */
    val artworkUrl: String?
}
