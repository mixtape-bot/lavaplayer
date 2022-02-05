package com.sedmelluq.lava.track.info

import com.sedmelluq.lava.track.TrackVersioning
import com.sedmelluq.lava.common.tools.io.MessageInput
import java.io.DataInput
import kotlin.experimental.and

/**
 * Meta info for an audio track
 */
interface AudioTrackInfo {
    companion object {
        val VERSION_ENCODERS = mutableMapOf(
            1 to AudioTrackInfoSerializer.v1,
            2 to AudioTrackInfoSerializer.v2
        )

        @JvmName("create")
        @JvmStatic
        operator fun invoke(build: AudioTrackInfoBuilder.() -> Unit): AudioTrackInfo {
            return AudioTrackInfoBuilder()
                .apply(build)
                .build()
        }

        @JvmStatic
        fun getVersion(stream: MessageInput, input: DataInput): Int {
            return if ((stream.messageFlags and TrackVersioning.TRACK_INFO_VERSIONED) != 0) {
                (input.readByte() and 0xFF.toByte()).toInt()
            } else {
                1
            }
        }
    }

    /**
     * Track title
     */
    val title: String

    /**
     * Track author, if known
     */
    val author: String

    /**
     * Length of the track in milliseconds, UnitConstants.DURATION_MS_UNKNOWN for streams
     */
    val length: Long

    /**
     * Audio source specific track identifier
     */
    val identifier: String

    /**
     * URL of the track, or local path to the file.
     */
    val uri: String?

    /**
     * URL of the artwork for this track.
     */
    val artworkUrl: String?

    /**
     * True if this track is a stream
     */
    val isStream: Boolean
}
