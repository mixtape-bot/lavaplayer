package com.sedmelluq.discord.lavaplayer.container.mpeg

/**
 * Codec information for an MP4 track
 *
 * @param trackId        ID of the track
 * @param handler        Handler type (soun for audio)
 * @param codecName      Name of the codec
 * @param channelCount   Number of audio channels
 * @param sampleRate     Sample rate for audio
 * @param decoderConfig
 */
data class MpegTrackInfo(
    /**
     * ID of the track
     */
    @JvmField val trackId: Int,
    /**
     * Handler type (soun for audio)
     */
    @JvmField val handler: String?,
    /**
     * Name of the codec
     */
    @JvmField val codecName: String?,
    /**
     * Number of audio channels
     */
    @JvmField val channelCount: Int,
    /**
     * Sample rate for audio
     */
    @JvmField val sampleRate: Int,
    @JvmField val decoderConfig: ByteArray?
) {
    /**
     * Helper class for constructing a track info instance.
     */
    class Builder {
        var trackId = 0
        var handler: String? = null
        var codecName: String? = null
        var channelCount = 0
        var sampleRate = 0
        var decoderConfig: ByteArray? = null

        /**
         * @return The final track info
         */
        fun build(): MpegTrackInfo {
            return MpegTrackInfo(trackId, handler, codecName, channelCount, sampleRate, decoderConfig)
        }
    }
}
