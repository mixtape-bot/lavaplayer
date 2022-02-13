package com.sedmelluq.discord.lavaplayer.container.flac

/**
 * Represents a metadata block header type.
 * see [Flac Format](https://xiph.org/flac/format.html#metadata_block_header)
 */
sealed class FlacMetadataBlockType(val value: Int) {
    companion object {
        val values = listOf(
            StreamInfo,
            Padding,
            Application,
            Seektable,
            VorbisComment,
            Cuesheet,
            Picture
        )

        @JvmStatic
        fun find(value: Int): FlacMetadataBlockType {
            return values.find { it.value == value } ?: Unknown(value)
        }
    }

    object StreamInfo : FlacMetadataBlockType(0)

    object Padding : FlacMetadataBlockType(1)

    object Application : FlacMetadataBlockType(2)

    object Seektable : FlacMetadataBlockType(3)

    object VorbisComment : FlacMetadataBlockType(4)

    object Cuesheet : FlacMetadataBlockType(5)

    object Picture : FlacMetadataBlockType(6)

    class Unknown(value: Int) : FlacMetadataBlockType(value)

    override fun toString(): String {
        return "${this::class.simpleName}(value=$value)"
    }
}
