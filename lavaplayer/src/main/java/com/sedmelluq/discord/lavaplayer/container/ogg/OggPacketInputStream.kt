package com.sedmelluq.discord.lavaplayer.container.ogg

import com.sedmelluq.discord.lavaplayer.container.MediaContainerDetection.Companion.checkNextBytes
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream
import com.sedmelluq.discord.lavaplayer.tools.io.StreamTools.readUntilEnd
import java.io.*
import kotlin.experimental.and
import kotlin.math.min

/**
 * This provides a stream for OGG packets where the stream is always bounded to the current packet, and the next packet
 * can be started with startNewPacket(). The same way it is bound to a specific track and the next track can be started
 * with startNewTrack() when the previous one has ended (startNewPacket() has returned false).
 *
 * @param inputStream    Input stream to read in as OGG packets
 * @param closeDelegated Whether closing this stream should close the inputStream as well
 */
class OggPacketInputStream(
    private val inputStream: SeekableInputStream,
    private val closeDelegated: Boolean,
) : InputStream() {
    companion object {
        private const val SHORT_SCAN = 10240
        private const val LONG_SCAN = 65307

        val OGG_PAGE_HEADER = intArrayOf(0x4F, 0x67, 0x67, 0x53)
    }

    private val dataInput: DataInput = DataInputStream(inputStream)
    private val segmentSizes: IntArray = IntArray(256)
    private var pageHeader: OggPageHeader? = null
    private var bytesLeftInPacket = 0
    private var packetContinues = false
    private var nextPacketSegmentIndex = 0
    private var state: State = State.TRACK_BOUNDARY

    val isPacketComplete: Boolean
        get() = state == State.PACKET_READ

    /**
     * Load the next track from the stream. This is only valid when the stream is in a track boundary state.
     *
     * @return True if next track is present in the stream, false if the stream has terminated.
     */
    fun startNewTrack(): Boolean {
        if (state == State.TERMINATED) {
            return false
        }

        check(state == State.TRACK_BOUNDARY) {
            "Cannot load the next track while the previous one has not been consumed."
        }

        pageHeader = null
        state = State.PACKET_BOUNDARY
        return true
    }

    /**
     * Load the next packet from the stream. This is only valid when the stream is in a packet boundary state.
     *
     * @return True if next packet is present in the track. State is PACKET_READ.
     * False if the track is finished. State is either TRACK_BOUNDARY or TERMINATED.
     * @throws IOException On read error.
     */
    @Throws(IOException::class)
    fun startNewPacket(): Boolean {
        if (state == State.TRACK_BOUNDARY) {
            return false
        }

        check(state == State.PACKET_BOUNDARY) {
            "Cannot start a new packet while the previous one has not been consumed."
        }

        if ((pageHeader == null || nextPacketSegmentIndex == pageHeader!!.segmentCount) && !loadNextNonEmptyPage()) {
            return false
        }

        return if (!Packet()) loadNextNonEmptyPage() else true
    }

    @Throws(IOException::class)
    private fun readPageHeader(): Boolean {
        if (!checkNextBytes(inputStream, OGG_PAGE_HEADER, false)) {
            if (inputStream.read() == -1) {
                return false
            }
            throw IllegalStateException("Stream is not positioned at a page header.")
        }

        check(dataInput.readByte() and 0xFF.toByte() == 0.toByte()) {
            "Unknown OGG stream version."
        }

        val flags: Byte = dataInput.readByte() and 0xFF.toByte()
        val position = java.lang.Long.reverseBytes(dataInput.readLong())
        val streamIdentifier = Integer.reverseBytes(dataInput.readInt())
        val pageSequence = Integer.reverseBytes(dataInput.readInt())
        val checksum = Integer.reverseBytes(dataInput.readInt())
        val segmentCount: Byte = dataInput.readByte() and 0xFF.toByte()
        val byteStreamPosition = inputStream.position - 27

        pageHeader = OggPageHeader(
            flags.toInt(),
            position,
            streamIdentifier,
            pageSequence,
            checksum,
            segmentCount.toInt(),
            byteStreamPosition
        )

        for (i in 0 until segmentCount) {
            segmentSizes[i] = (dataInput.readByte() and 0xFF.toByte()).toInt()
        }

        return true
    }

    /**
     * Load pages until a non-empty page is reached. Valid to call in states PACKET_BOUNDARY (page starts a new packet) or
     * PACKET_READ (page starts with a continuation).
     *
     * @return True if a page belonging to the same track was loaded, state is PACKET_READ.
     * False if the next page cannot be loaded because the current one ended the track, state is TRACK_BOUNDARY
     * or TERMINATED.
     * @throws IOException On read error.
     */
    @Throws(IOException::class)
    private fun loadNextNonEmptyPage(): Boolean {
        do {
            if (!loadNextPage()) {
                return false
            }
        } while (pageHeader!!.segmentCount == 0)

        return true
    }

    /**
     * Load the next page from the stream. Valid to call in states PACKET_BOUNDARY (page starts a new packet) or
     * PACKET_READ (page starts with a continuation).
     *
     * @return True if a page belonging to the same track was loaded, state is PACKET_READ.
     * False if the next page cannot be loaded because the current one ended the track, state is TRACK_BOUNDARY
     * or TERMINATED.
     * @throws IOException On read error.
     */
    @Throws(IOException::class)
    private fun loadNextPage(): Boolean {
        if (pageHeader != null && pageHeader!!.isLastPage) {
            check(!packetContinues) { "Track finished in the middle of a packet." }
            state = State.TRACK_BOUNDARY
            return false
        }

        if (!readPageHeader()) {
            check(!packetContinues) { "Stream ended in the middle of a packet." }
            return false
        }

        nextPacketSegmentIndex = 0
        state = State.PACKET_READ
        return true
    }

    /**
     *  the (remainder of the) current packet in the stream. This may be called either to  a new
     * packet or a continuation of the previous one. Call only in state PACKET_READ.
     *
     * @return Returns false if the remaining size of the packet was zero, state is PACKET_BOUNDARY.
     * Returns true if the d packet has any bytes in it, state is PACKET_READ.
     */
    private fun Packet(): Boolean {
        while (nextPacketSegmentIndex < pageHeader!!.segmentCount) {
            val size = segmentSizes[nextPacketSegmentIndex++]
            bytesLeftInPacket += size

            if (size < 255) {
                // Anything below 255 is also a packet end marker.
                if (bytesLeftInPacket == 0) {
                    // We reached packet end without getting any additional bytes, set state to packet boundary
                    state = State.PACKET_BOUNDARY
                    return false
                }

                // We reached packet end and got some more bytes.
                packetContinues = false
                return true
            }
        }

        // Packet does not end within this page.
        packetContinues = true
        return true
    }

    @Throws(IOException::class)
    override fun read(): Int {
        if (bytesLeftInPacket == 0) {
            return -1
        }

        val value = inputStream.read()
        if (value == -1) {
            return -1
        }

        if (--bytesLeftInPacket == 0) {
            continuePacket()
        }

        return value
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, initialOffset: Int, length: Int): Int {
        var currentOffset = initialOffset
        val maximumOffset = initialOffset + length

        // Terminates when we have read as much as we needed
        while (currentOffset < maximumOffset) {
            // If there is nothing left in the current packet, stream is in EOF state
            if (bytesLeftInPacket == 0) {
                return -1
            }

            // Limit the read size to the number of bytes that are definitely still left in the packet
            val chunk = min(maximumOffset - currentOffset, bytesLeftInPacket)
            val read = inputStream.read(buffer, currentOffset, chunk)
            if (read == -1) {
                // EOF in the underlying stream before the end of a packet. Throw an exception, the consumer should not need
                // to check for partial packets.
                throw EOFException("Underlying stream ended before the end of a packet.")
            }

            currentOffset += read
            bytesLeftInPacket -= read
            if (bytesLeftInPacket == 0) {
                // We got everything from our chunk of size min(leftInPacket, requested) and also exhausted the bytes that we
                // know the packet had left. Check if the packet continues, so we could continue fetching from the same packet.
                // Otherwise, bugger out.
                if (!continuePacket()) {
                    break
                }
            } else if (read < chunk) {
                // The underlying stream cannot provide more right now. Let it rest.
                return currentOffset - initialOffset
            }
        }

        return currentOffset - initialOffset
    }

    @Throws(IOException::class)
    override fun available(): Int {
        return if (state != State.PACKET_READ) {
            0
        } else {
            min(inputStream.available(), bytesLeftInPacket)
        }
    }

    @Throws(IOException::class)
    override fun close() {
        if (closeDelegated) {
            inputStream.close()
        }
    }

    /**
     * If it is possible to seek backwards on this stream, and the length of the stream is known, seeks to the end of the
     * track to determine the stream length both in bytes and samples.
     *
     * @param sampleRate Sample rate of the track in this stream.
     * @return OGG stream size information.
     * @throws IOException On read error.
     */
    @Throws(IOException::class)
    fun seekForSizeInfo(sampleRate: Int): OggStreamSizeInfo? {
        if (!inputStream.canSeekHard()) {
            return null
        }

        return inputStream.rewindAfter {
            scanForSizeInfo(SHORT_SCAN, sampleRate)
                ?: scanForSizeInfo(LONG_SCAN, sampleRate)
        }
    }

    @Throws(IOException::class)
    private fun scanForSizeInfo(tailLength: Int, sampleRate: Int): OggStreamSizeInfo? {
        if (pageHeader == null) {
            return null
        }

        val absoluteOffset = pageHeader!!.byteStreamPosition.coerceAtLeast(inputStream.contentLength - tailLength)
        inputStream.seek(absoluteOffset)

        val data = ByteArray(tailLength)
        val dataLength = readUntilEnd(inputStream, data, 0, data.size)
        return OggPageScanner(absoluteOffset, data, dataLength).scanForSizeInfo(pageHeader!!.byteStreamPosition, sampleRate)
    }

    /**
     * Process request for more bytes for the packet. Call only when the state is PACKET_READ.
     *
     * @return Returns false if no more bytes for the packet are available, state is PACKET_BOUNDARY.
     * Returns true if more bytes were fetched for this packet, state is PACKET_READ.
     * @throws IOException On read error.
     */
    @Throws(IOException::class)
    private fun continuePacket(): Boolean {
        if (!packetContinues) {
            // We have reached the end of the packet.
            state = State.PACKET_BOUNDARY
            return false
        }

        // Load more segments for this packet from the next page.
        return if (!loadNextNonEmptyPage()) {
            throw IllegalStateException("Track or stream end reached within an incomplete packet.")
        } else {
            Packet()
        }
    }

    private enum class State {
        TRACK_BOUNDARY, PACKET_BOUNDARY, PACKET_READ, TERMINATED
    }
}
