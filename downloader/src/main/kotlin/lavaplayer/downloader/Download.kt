package lavaplayer.downloader

import com.github.kokorin.jaffree.ffmpeg.FFmpeg
import com.github.kokorin.jaffree.ffmpeg.PipeInput
import com.github.kokorin.jaffree.ffmpeg.UrlOutput
import com.sedmelluq.discord.lavaplayer.format.AudioPlayerInputStream
import com.sedmelluq.discord.lavaplayer.manager.AudioPlayer
import com.sedmelluq.discord.lavaplayer.manager.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.manager.event.AudioEventAdapter
import com.sedmelluq.discord.lavaplayer.tools.extensions.addListener
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason
import kotlinx.coroutines.*
import okio.Buffer
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.createFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.outputStream

data class Download(
    val outputFormat: String,
    val track: AudioTrack,
    val playerManager: AudioPlayerManager,
    val ffmpegArgs: List<String>,
    val dispatcher: CoroutineDispatcher = DEFAULT_DISPATCHER
) : CoroutineScope, AudioEventAdapter() {
    companion object {
        private val DEFAULT_DISPATCHER: CoroutineDispatcher = Executors
            .newCachedThreadPool()
            .asCoroutineDispatcher()

        private val log = LoggerFactory.getLogger(Download::class.java)
    }

    private lateinit var frameJob: Job
    private lateinit var player: AudioPlayer

    private var active: Boolean = false

    override val coroutineContext: CoroutineContext
        get() = dispatcher + SupervisorJob() + CoroutineName("Downloader")

    fun start() {
        log.info("Starting download for track $track")

        createPlayer()
        player.playTrack(track)
        startFrameJob()
    }

    override fun onTrackStart(player: AudioPlayer, track: AudioTrack) {
        log.info("Track \"${track.info.identifier}\" has started...")
    }

    override fun onTrackEnd(player: AudioPlayer, track: AudioTrack, endReason: AudioTrackEndReason) {
        log.info("Track \"${track.info.identifier}\" has ended, reason=$endReason")
        active = false
    }

    private fun createPlayer() {
        player = playerManager.createPlayer()
        player.addListener(this)
    }

    private fun cleanup() {
        if (!::player.isInitialized) {
            return
        }

        player.stopTrack()
        player.destroy()
        cancel()
    }

    private fun startFrameJob() {
        val file = Path.of("${track.info.title}.$outputFormat")

        active = true
        frameJob = launch {
            log.debug("Frame job is starting...")

            val stream = AudioPlayerInputStream.createStream(player, playerManager.configuration.outputFormat, 10000, true)
            val buffer = Buffer()
            var polled = false

            while (active) {
                if (player.playingTrack == null) {
                    if (polled) {
                        log.warn("Playing track is null even though frames were polled, did we not receive track end?")
                        break
                    }

                    continue
                }

                val chunk = withContext(Dispatchers.IO) {
                    stream.readNBytes(playerManager.configuration.outputFormat.maximumChunkSize)
                }

                if (chunk.isEmpty()) {
                    break
                }

                polled = true
                buffer.write(chunk)
            }

            log.debug("Frame job has finished.")
            if (outputFormat == "pcm") {
                startPcmOutput(buffer, file)
            } else {
                startFfmpegJob(buffer, file)
            }

            cleanup()
        }
    }

    private fun startPcmOutput(pcm: Buffer, output: Path) {
        log.info("Outputting PCM to file.")

        output.deleteIfExists()
        output.createFile()

        output.outputStream().use {
            it.write(pcm.readByteArray())
        }

        log.info("Finished outputting pcm to file.")
    }

    private fun startFfmpegJob(pcm: Buffer, output: Path) {
        log.info("FFmpeg job is starting...")

        val ffmpeg = FFmpeg.atPath()
            .addInput(PipeInput.pumpFrom(pcm.inputStream())
                .setFormat("s16be")
                .addArguments("-ac", "2")
                .addArguments("-ar", "48k"))
            .addOutput(UrlOutput.toPath(output))
            .setOverwriteOutput(true)

        if (ffmpegArgs.isNotEmpty()) {
            ffmpegArgs.forEach(ffmpeg::addArgument)
        }

        try {
            ffmpeg.execute()
            log.info("FFmpeg job has finished.")
        } catch (e: Exception) {
            log.error("Failed to execute ffmpeg", e)
        }
    }
}
