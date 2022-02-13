package com.sedmelluq.discord.lavaplayer.container.flac

/**
 * Builder for FLAC track info.
 * @param streamInfo Stream info metadata block.
 */
class FlacTrackInfoBuilder(
    /**
     * Stream info metadata block.
     */
    val streamInfo: FlacStreamInfo
) {
    private val tags: MutableMap<String, String> = mutableMapOf()
    private var seekPointCount = 0
    private var seekPoints: List<FlacSeekPoint> = emptyList()

    /**
     * File position of the first frame
     */
    var firstFramePosition: Long = 0

    /**
     * @param seekPoints     Seek point array.
     * @param seekPointCount The number of seek points which are not placeholders.
     */
    fun setSeekPoints(seekPoints: List<FlacSeekPoint>, seekPointCount: Int) {
        this.seekPoints = seekPoints
        this.seekPointCount = seekPointCount
    }

    /**
     * @param key   Name of the tag
     * @param value Value of the tag
     */
    fun addTag(key: String, value: String) {
        tags[key] = value
    }

    /**
     * @return Track info object.
     */
    fun build(): FlacTrackInfo {
        return FlacTrackInfo(streamInfo, seekPoints, seekPointCount, tags, firstFramePosition)
    }
}
