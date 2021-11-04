package com.sedmelluq.discord.lavaplayer.container.matroska.format

/**
 * Matroska container element.
 */
open class MatroskaElement protected constructor(
    /**
     * @return The depth of the element in the element tree.
     */
    val level: Int
) {
    /**
     * The EBML code of this element.
     */
    open var id: Long = 0
        protected set

    /**
     * The type of this element, Unknown if not listed in the enum.
     */
    open var type: MatroskaElementType? = null
        protected set

    /**
     * The absolute position of this element in the file.
     */
    open var position: Long = 0
        protected set

    /**
     * The size of the header in bytes.
     */
    open var headerSize = 0
        protected set

    /**
     * Size of the payload in bytes.
     */
    open var dataSize = 0

    /**
     * @param type Element type.
     * @return True if this element is of the specified type.
     */
    fun `is`(type: MatroskaElementType): Boolean {
        return type.id == id
    }

    /**
     * @param dataType Element data type.
     * @return True if the type of the element uses the specified data type.
     */
    fun `is`(dataType: MatroskaElementType.DataType): Boolean {
        return dataType == type!!.dataType
    }

    /**
     * @param currentPosition Absolute position to check against.
     * @return The number of bytes from the specified position to the end of this element.
     */
    fun getRemaining(currentPosition: Long): Long {
        return position + headerSize + dataSize - currentPosition
    }

    /**
     * @return The absolute position of the data of this element.
     */
    val dataPosition: Long
        get() = position + headerSize

    /**
     * @return A frozen version of the element safe to keep for later use.
     */
    fun frozen(): MatroskaElement {
        val element = MatroskaElement(level)
        element.id = id
        element.type = type
        element.position = position
        element.headerSize = headerSize
        element.dataSize = dataSize

        return element
    }
}
