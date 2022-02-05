package com.sedmelluq.lava.track.info

import com.sedmelluq.lava.common.tools.extensions.readNullableUTF
import com.sedmelluq.lava.common.tools.extensions.writeNullableUTF
import java.io.DataInput
import java.io.DataOutput

interface AudioTrackInfoSerializer {

    /**
     * Attempts to read the [AudioTrackInfo] within [input]
     *
     * @param input The input to read.
     */
    fun decode(input: DataInput): AudioTrackInfo?

    /**
     * Writes the provided [info] to the provided [output]
     *
     * @parma info   The [AudioTrackInfo] to write.
     * @param output The [DataOutput] to write to.
     */
    fun encode(info: AudioTrackInfo, output: DataOutput)

    /**
     * Serializer for AudioTrackInfo v1
     */
    object v1 : AudioTrackInfoSerializer {
        override fun decode(input: DataInput): AudioTrackInfo {
            return BasicAudioTrackInfo(
                title = input.readUTF(),
                author = input.readUTF(),
                length = input.readLong(),
                identifier = input.readUTF(),
                isStream = input.readBoolean()
            )
        }

        override fun encode(info: AudioTrackInfo, output: DataOutput) {
            output.writeUTF(info.title)
            output.writeUTF(info.author)
            output.writeLong(info.length)
            output.writeUTF(info.identifier)
            output.writeBoolean(info.isStream)
        }
    }

    /**
     * Serializer for AudioTrackInfo v2
     */
    object v2 : AudioTrackInfoSerializer {
        override fun decode(input: DataInput): AudioTrackInfo {
            return BasicAudioTrackInfo(
                title = input.readUTF(),
                author = input.readUTF(),
                length = input.readLong(),
                identifier = input.readUTF(),
                uri = input.readNullableUTF(),
                artworkUrl = input.readNullableUTF(),
                isStream = input.readBoolean()
            )
        }

        override fun encode(info: AudioTrackInfo, output: DataOutput) {
            output.writeUTF(info.title)
            output.writeUTF(info.author)
            output.writeLong(info.length)
            output.writeUTF(info.identifier)
            output.writeNullableUTF(info.uri)
            output.writeNullableUTF(info.artworkUrl)
            output.writeBoolean(info.isStream)
        }
    }
}
