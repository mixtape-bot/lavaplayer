package com.sedmelluq.discord.lavaplayer.track.collection

import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("basic")
open class BasicAudioTrackCollection(
    override val name: String,
    override val tracks: MutableList<AudioTrack>,
    override val selectedTrack: AudioTrack? = null
) : AudioTrackCollection
