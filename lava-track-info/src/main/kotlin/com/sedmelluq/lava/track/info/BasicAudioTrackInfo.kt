package com.sedmelluq.lava.track.info

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("basic")
open class BasicAudioTrackInfo(
    override val title: String,
    override val author: String,
    override val length: Long,
    override val identifier: String,
    override val uri: String? = null,
    override val artworkUrl: String? = null,
    override val isStream: Boolean = false
) : AudioTrackInfo {
    companion object
}
