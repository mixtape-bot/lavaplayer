package com.sedmelluq.discord.lavaplayer.source.local

import com.sedmelluq.discord.lavaplayer.container.*
import com.sedmelluq.discord.lavaplayer.source.ProbingItemSourceManager
import com.sedmelluq.discord.lavaplayer.track.AudioItem
import com.sedmelluq.discord.lavaplayer.track.AudioReference
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.loader.LoaderState
import com.sedmelluq.lava.common.tools.exception.FriendlyException
import com.sedmelluq.lava.common.tools.exception.friendlyError
import com.sedmelluq.lava.track.info.AudioTrackInfo
import java.io.DataInput
import java.io.DataOutput
import java.io.File
import java.io.IOException

/**
 * Audio source manager that implements finding audio files from the local file system.
 */
class LocalItemSourceManager @JvmOverloads constructor(containerRegistry: MediaContainerRegistry? = MediaContainerRegistry.DEFAULT_REGISTRY) :
    ProbingItemSourceManager(containerRegistry!!) {
    override val sourceName: String
        get() = "local"

    override fun isTrackEncodable(track: AudioTrack): Boolean = true

    override suspend fun loadItem(state: LoaderState, reference: AudioReference): AudioItem? {
        val file = File(reference.identifier ?: return null)

        return if (file.exists() && file.isFile && file.canRead()) {
            handleLoadResult(detectContainerForFile(reference, file))
        } else {
            null
        }
    }

    override fun createTrack(trackInfo: AudioTrackInfo, containerDescriptor: MediaContainerDescriptor): AudioTrack =
        LocalAudioTrack(trackInfo, containerDescriptor, this)@Throws(IOException::class)

    override fun encodeTrack(track: AudioTrack, output: DataOutput, version: Int) =
        encodeTrackFactory((track as LocalAudioTrack).containerTrackFactory, output)

    @Throws(IOException::class)
    override fun decodeTrack(trackInfo: AudioTrackInfo, input: DataInput, version: Int): AudioTrack? =
        decodeTrackFactory(input)?.let { LocalAudioTrack(trackInfo, it, this) }

    private fun detectContainerForFile(reference: AudioReference, file: File): MediaContainerDetectionResult {
        try {
            LocalSeekableInputStream(file).use { inputStream ->
                val lastDotIndex = file.name.lastIndexOf('.')
                val fileExtension = if (lastDotIndex >= 0) file.name.substring(lastDotIndex + 1) else null

                return MediaContainerDetection(
                    containerRegistry,
                    reference,
                    inputStream,
                    MediaContainerHints.from(null, fileExtension)
                ).detectContainer()
            }
        } catch (e: IOException) {
            friendlyError("Failed to open file for reading.", FriendlyException.Severity.SUSPICIOUS, e)
        }
    }
}
