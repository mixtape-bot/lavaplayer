package com.sedmelluq.discord.lavaplayer.container.mpeg

import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream
import com.sedmelluq.discord.lavaplayer.track.BaseAudioTrack
import com.sedmelluq.discord.lavaplayer.track.playback.AudioProcessingContext
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor
import com.sedmelluq.lava.common.tools.exception.FriendlyException
import com.sedmelluq.lava.common.tools.exception.friendlyError
import com.sedmelluq.lava.common.tools.exception.wrapUnfriendlyException
import com.sedmelluq.lava.track.info.AudioTrackInfo
import mu.KotlinLogging

/**
 * Audio track that handles the processing of MP4 format
 *
 * @param trackInfo   Track info
 * @param inputStream Input stream for the MP4 file
 */
open class MpegAudioTrack(trackInfo: AudioTrackInfo, private val inputStream: SeekableInputStream? = null) : BaseAudioTrack(trackInfo) {
    companion object {
        private val log = KotlinLogging.logger { }
    }

    override suspend fun process(executor: LocalAudioTrackExecutor) {
        checkNotNull(inputStream) {
            "Unable to process audio track without an input stream."
        }

        val file = MpegFileLoader(inputStream)
        file.parseHeaders()

        val trackConsumer = loadAudioTrack(file, executor.processingContext)
        try {
            val fileReader = file.loadReader(trackConsumer)
                ?: friendlyError("Unknown MP4 format.", FriendlyException.Severity.SUSPICIOUS)

            accurateDuration = fileReader.duration
            executor.executeProcessingLoop(
                readExecutor = fileReader::provideFrames,
                seekExecutor = fileReader::seekToTimecode
            )
        } finally {
            trackConsumer.close()
        }
    }

    protected fun loadAudioTrack(file: MpegFileLoader, context: AudioProcessingContext): MpegTrackConsumer {
        var trackConsumer: MpegTrackConsumer? = null
        var success = false
        return try {
            trackConsumer = selectAudioTrack(file.trackList, context)
                ?: friendlyError("The audio codec used in the track is not supported.", FriendlyException.Severity.SUSPICIOUS)

            log.debug { "Starting to play track with codec ${trackConsumer.track.codecName}" }

            trackConsumer.initialise()
            success = true
            trackConsumer
        } catch (e: Exception) {
            throw e.wrapUnfriendlyException("Something went wrong when loading an MP4 format track.", FriendlyException.Severity.FAULT)
        } finally {
            if (!success && trackConsumer != null) {
                trackConsumer.close()
            }
        }
    }

    private fun selectAudioTrack(tracks: List<MpegTrackInfo>, context: AudioProcessingContext): MpegTrackConsumer? {
        return tracks
            .firstOrNull { it.handler == MpegConstants.AUDIO_HANDLER && it.codecName == MpegConstants.CODEC_NAME }
            ?.let { MpegAacTrackConsumer(context, it) }
    }
}
