package com.sedmelluq.discord.lavaplayer.container.matroska.format

/**
 * Mutable instance of [MatroskaElement] for reducing allocation rate during parsing.
 */
class MutableMatroskaElement(level: Int) : MatroskaElement(level) {
    override var id: Long = 0L

    override var type: MatroskaElementType? = null

    override var position: Long = 0

    override var headerSize: Int = 0

    override var dataSize: Int = 0
}
