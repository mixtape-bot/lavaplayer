package com.sedmelluq.discord.lavaplayer.container

import com.sedmelluq.discord.lavaplayer.track.AudioReference
import com.sedmelluq.lava.track.info.AudioTrackInfo

/**
 * Result of audio container detection.
 */
class MediaContainerDetectionResult private constructor(
    val reference: AudioReference?,
    /**
     * Track info for the detected file.
     */
    val trackInfo: AudioTrackInfo?,
    /**
     * The reason why this track is not supported.
     */
    val unsupportedReason: String?,
    private val containerProbe: MediaContainerProbe?,
    private val probeSettings: String?,
) {
    companion object {
        /**
         * An unknown format result.
         */
        val UNKNOWN_FORMAT = MediaContainerDetectionResult(null, null, null, null, null)

        /**
         * Creates a result ofr an unsupported file of a known container.
         *
         * @param probe  Probe of the container
         * @param reason The reason why this track is not supported
         */
        fun unsupportedFormat(probe: MediaContainerProbe, reason: String): MediaContainerDetectionResult {
            return MediaContainerDetectionResult(null, null, reason, probe, null)
        }

        /**
         * Creates a load result referring to another item.
         *
         * @param probe     Probe of the container
         * @param reference Reference to another item
         */
        fun refer(probe: MediaContainerProbe?, reference: AudioReference): MediaContainerDetectionResult {
            return MediaContainerDetectionResult(reference, null, null, probe, null)
        }

        /**
         * Creates a load result for supported file.
         *
         * @param probe     Probe of the container
         * @param trackInfo Track info for the file
         */
        fun supportedFormat(
            probe: MediaContainerProbe,
            trackInfo: AudioTrackInfo,
            settings: String? = null,
        ): MediaContainerDetectionResult {
            return MediaContainerDetectionResult(null, trackInfo, null, probe, settings)
        }
    }

    /**
     * @return If the container this file uses was detected. In case this returns true, the container probe is non-null.
     */
    val isContainerDetected: Boolean
        get() = containerProbe != null

    /**
     * @return The probe for the container of the file
     */
    val containerDescriptor: MediaContainerDescriptor
        get() = MediaContainerDescriptor(containerProbe!!, probeSettings)

    /**
     * @return Whether this specific file is supported. If this returns true, the track info is non-null. Otherwise
     * the reason why this file is not supported can be retrieved via getUnsupportedReason().
     */
    val isSupportedFile: Boolean
        get() = isContainerDetected && unsupportedReason == null
}
