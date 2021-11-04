package com.sedmelluq.discord.lavaplayer.container.mpeg

import java.nio.channels.ReadableByteChannel

/**
 * No-op MP4 track consumer, for probing purposes.
 *
 * @param track Track info.
 */
class MpegNoopTrackConsumer(override val track: MpegTrackInfo) : MpegTrackConsumer {
    override fun initialise() = Unit
    override fun seekPerformed(requestedTimecode: Long, providedTimecode: Long) = Unit
    override fun flush() = Unit
    override fun consume(channel: ReadableByteChannel?, length: Int) = Unit
    override fun close() = Unit
}
