package com.sedmelluq.discord.lavaplayer.container.ogg

import com.sedmelluq.discord.lavaplayer.tools.io.DirectBufferStreamBroker
import java.io.IOException

interface OggCodecHandler {
    fun isMatchingIdentifier(identifier: Int): Boolean
    val maximumFirstPacketLength: Int

    @Throws(IOException::class)
    fun loadBlueprint(stream: OggPacketInputStream?, broker: DirectBufferStreamBroker?): OggTrackBlueprint?

    @Throws(IOException::class)
    fun loadMetadata(stream: OggPacketInputStream?, broker: DirectBufferStreamBroker?): OggMetadata?
}
