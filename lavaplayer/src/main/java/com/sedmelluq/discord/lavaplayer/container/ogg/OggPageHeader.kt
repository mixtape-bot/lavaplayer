package com.sedmelluq.discord.lavaplayer.container.ogg

/**
 * Header of an OGG stream page.
 *
 * @param flags              Page flags.
 * @param absolutePosition   The absolute position in the number of samples of this packet relative to the track start.
 * @param streamIdentifier   Unique identifier of this track in the stream.
 * @param pageSequence       The index of the page within a track.
 * @param checksum           The checksum of the page.
 * @param segmentCount       Number of segments in the page.
 * @param byteStreamPosition The absolute position in bytes of this page in the stream.
 */
class OggPageHeader(
    flags: Int,
    /**
     * The absolute position in the number of samples of this packet relative to the track start.
     */
    val absolutePosition: Long,
    /**
     * Unique identifier of this track in the stream.
     */
    val streamIdentifier: Int,
    /**
     * The index of the page within a track.
     */
    val pageSequence: Int,
    /**
     * The checksum of the page.
     */
    val pageChecksum: Int,
    /**
     * Number of segments in the page.
     */
    @JvmField
    val segmentCount: Int,
    /**
     * The absolute position of the start of this page in the underlying bytestream.
     */
    @JvmField
    val byteStreamPosition: Long,
) {
    companion object {
        const val FLAG_CONTINUATION = 0x01
        const val FLAG_FIRST_PAGE = 0x02
        const val FLAG_LAST_PAGE = 0x04
    }

    /**
     * If this page starts in the middle of a packet that was left incomplete in the previous page.
     */
    val isContinuation: Boolean = (flags and FLAG_CONTINUATION) != 0

    /**
     * If this is the first page of the track.
     */
    val isFirstPage: Boolean = (flags and FLAG_FIRST_PAGE) != 0

    /**
     * If this is the last page of the track.
     */
    @get:JvmName("isLastPage")
    val isLastPage: Boolean = (flags and FLAG_LAST_PAGE) != 0
}
