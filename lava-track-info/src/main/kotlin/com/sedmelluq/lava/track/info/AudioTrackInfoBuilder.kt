package com.sedmelluq.lava.track.info

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Builder for [AudioTrackInfo].
 */
class AudioTrackInfoBuilder() : AudioTrackInfoProvider {
    companion object {
        private const val UNKNOWN_TITLE = "Unknown title"
        private const val UNKNOWN_ARTIST = "Unknown artist"

        const val DURATION_MS_UNKNOWN = Long.MAX_VALUE

        @OptIn(ExperimentalContracts::class)
        inline fun apply(build: AudioTrackInfoBuilder.() -> Unit): AudioTrackInfoBuilder {
            contract {
                callsInPlace(build, InvocationKind.EXACTLY_ONCE)
            }

            return AudioTrackInfoBuilder()
                .apply(build)
        }

        /**
         * @return Empty instance of audio track builder.
         */
        @JvmStatic
        fun empty(): AudioTrackInfoBuilder = AudioTrackInfoBuilder()
    }

    override var title: String? = null
        set(value) { field = value ?: field }

    override var author: String? = null
        set(value) { field = value ?: field }

    override var length: Long? = null
        set(value) { field = value ?: field }

    override var identifier: String? = null
        set(value) { field = value ?: field }

    override var uri: String? = null
        set(value) { field = value ?: field }

    override var artworkUrl: String? = null
        set(value) { field = value ?: field }

    var isStream: Boolean? = null
        set(value) { field = value ?: field }

    fun setIsStream(stream: Boolean?): AudioTrackInfoBuilder {
        isStream = stream
        return this
    }

    /**
     * @param provider The track info provider to apply to the builder.
     * @return this
     */
    fun apply(provider: AudioTrackInfoProvider?): AudioTrackInfoBuilder {
        return if (provider == null) this else apply {
            title = provider.title
            author = provider.author
            length = provider.length
            identifier = provider.identifier
            uri = provider.uri
            artworkUrl = provider.artworkUrl
        }
    }


    /**
     * @return Audio track info instance.
     */
    fun build(): AudioTrackInfo {
        val length = length ?: DURATION_MS_UNKNOWN
        return BasicAudioTrackInfo(
            title!!,
            author!!,
            length,
            identifier!!,
            uri,
            artworkUrl,
            isStream ?: (length == DURATION_MS_UNKNOWN)
        )
    }
}
