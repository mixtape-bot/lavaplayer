package lavaplayer.demo

import com.sedmelluq.discord.lavaplayer.format.AudioPlayerInputStream
import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats
import com.sedmelluq.discord.lavaplayer.manager.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.manager.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.manager.event.TrackEndEvent
import com.sedmelluq.discord.lavaplayer.manager.event.TrackStartEvent
import com.sedmelluq.discord.lavaplayer.source.common.SourceRegistry
import com.sedmelluq.discord.lavaplayer.tools.extensions.on
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.collection.AudioTrackCollection
import com.sedmelluq.discord.lavaplayer.track.loader.DelegatedItemLoadResultHandler
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import okio.Buffer
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine

suspend fun main(): Unit = coroutineScope {
    val manager: AudioPlayerManager = DefaultAudioPlayerManager()
    manager.configuration.outputFormat = StandardAudioDataFormats.COMMON_PCM_S16_BE

    SourceRegistry.registerRemoteSources(manager)

    /* create player */
    val player = manager.createPlayer()

    // do some more bullshit lol
    val stream = AudioPlayerInputStream.createStream(player, manager.configuration.outputFormat, 10000L, false)
    val line = AudioSystem.getLine(DataLine.Info(SourceDataLine::class.java, stream.format)) as SourceDataLine
    line.open(stream.format)
    line.start()

    var polling: Boolean
    player.on<TrackStartEvent> {
        println("playing ${track.info.uri}")

        val buffer = Buffer()
        launch {
            polling = true
            while (polling) {
                if (player.playingTrack == null) {
                    continue
                }

                /* write the chunk */
                val chunk = stream.readNBytes(StandardAudioDataFormats.COMMON_PCM_S16_BE.maximumChunkSize)
                val chunkSize = chunk.size.toLong()

                buffer.write(chunk)

                /* get the size of the written chunk */
                if (chunkSize <= 0) {
                    break
                }

                /* write the buffer value to the data line */
                val byteCount = chunkSize.coerceAtMost(buffer.size())
                val bytes = buffer.readByteArray(byteCount)
                line.write(bytes, 0, byteCount.toInt())
            }
        }
    }

    player.on<TrackEndEvent> {
        polling = false
    }

    // load items.
    val itemLoader = manager.items.createItemLoader("ytsearch:suburban scoiopath afourteen teenage disaster")
    itemLoader.resultHandler = DelegatedItemLoadResultHandler(
        { track: AudioTrack? -> player.playTrack(track) },
        { playlist: AudioTrackCollection -> player.playTrack(playlist.tracks[0]) },
        null,
        null
    )
    itemLoader.loadAsync()
}
