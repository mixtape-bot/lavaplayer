package com.sedmelluq.discord.lavaplayer.container.matroska

import com.sedmelluq.discord.lavaplayer.container.matroska.format.MatroskaFileTrack
import com.sedmelluq.discord.lavaplayer.tools.extensions.closeWithWarnings
import com.sedmelluq.discord.lavaplayer.tools.extensions.toRuntimeException
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream
import com.sedmelluq.discord.lavaplayer.track.BaseAudioTrack
import com.sedmelluq.discord.lavaplayer.track.playback.AudioProcessingContext
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor
import com.sedmelluq.lava.track.info.AudioTrackInfo
import mu.KotlinLogging
import java.io.IOException

/**
 * Audio track that handles the processing of MKV and WEBM formats
 *
 * @param trackInfo   Track info
 * @param inputStream Input stream for the file
 */
class MatroskaAudioTrack(trackInfo: AudioTrackInfo, private val inputStream: SeekableInputStream) :
    BaseAudioTrack(trackInfo) {
    companion object {
        private val log = KotlinLogging.logger { }
    }

    override suspend fun process(executor: LocalAudioTrackExecutor) {
        val file = loadMatroskaFile()
        val trackConsumer = loadAudioTrack(file, executor.processingContext)
            ?: return

        try {
            executor.executeProcessingLoop(
                readExecutor = { file.provideFrames(trackConsumer) },
                seekExecutor = { file.seekToTimecode(trackConsumer.track.index, it) }
            )
        } finally {
            trackConsumer.closeWithWarnings()
        }
    }

    private fun loadMatroskaFile(): MatroskaStreamingFile {
        try {
            val file = MatroskaStreamingFile(inputStream)
            file.readFile()
            accurateDuration = file.duration.toLong()

            return file
        } catch (e: IOException) {
            throw e.toRuntimeException()
        }
    }

    private fun loadAudioTrack(file: MatroskaStreamingFile, context: AudioProcessingContext): MatroskaTrackConsumer? {
        var trackConsumer: MatroskaTrackConsumer? = null
        var success = false

        try {
            trackConsumer = checkNotNull(selectAudioTrack(file.trackList, context)) {
                "No supported audio tracks in the file."
            }

            log.debug { "Starting to play track with codec ${trackConsumer.track.codecId}" }
            trackConsumer.initialize()
            success = true
        } finally {
            if (!success && trackConsumer != null) {
                trackConsumer.closeWithWarnings()
            }
        }

        return trackConsumer
    }

    private fun selectAudioTrack(
        tracks: Array<MatroskaFileTrack>,
        context: AudioProcessingContext
    ): MatroskaTrackConsumer? {
        var trackConsumer: MatroskaTrackConsumer? = null
        for (track in tracks) {
            if (track.type != MatroskaFileTrack.Type.AUDIO) {
                continue
            }

            when (track.codecId) {
                MatroskaContainerProbe.OPUS_CODEC -> {
                    trackConsumer = MatroskaOpusTrackConsumer(context, track)
                    break
                }
                MatroskaContainerProbe.VORBIS_CODEC ->
                    trackConsumer = MatroskaVorbisTrackConsumer(context, track)
                MatroskaContainerProbe.AAC_CODEC ->
                    trackConsumer = MatroskaAacTrackConsumer(context, track)
            }
        }

        return trackConsumer
    }
}
