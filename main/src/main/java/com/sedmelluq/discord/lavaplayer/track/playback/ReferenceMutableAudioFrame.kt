package com.sedmelluq.discord.lavaplayer.track.playback

/**
 * Mutable audio frame which contains no dedicated buffer, but refers to a segment in a specified byte buffer.
 */
class ReferenceMutableAudioFrame : AbstractMutableAudioFrame() {
    /**
     * @return The underlying byte buffer.
     */
    lateinit var frameBuffer: ByteArray
        private set

    /**
     * Offset of the frame data in the underlying byte buffer.
     */
    var frameOffset = 0
        private set

    override var dataLength = 0
        private set

    /**
     * @return Offset of the end of frame data in the underlying byte buffer.
     */
    val frameEndOffset: Int
        get() = frameOffset + dataLength

    override val data: ByteArray
        get() = ByteArray(dataLength).also { this[it] }

    override fun getData(buffer: ByteArray, offset: Int) =
        System.arraycopy(frameBuffer, frameOffset, buffer, offset, dataLength)

    /**
     * @param frameBuffer See [.getFrameBuffer].
     * @param frameOffset See [.getFrameOffset].
     * @param frameLength See [.getDataLength].
     */
    fun setDataReference(frameBuffer: ByteArray, frameOffset: Int, frameLength: Int) {
        this.frameBuffer = frameBuffer
        this.frameOffset = frameOffset
        dataLength = frameLength
    }
}
