package lavaplayer.demo

import com.sedmelluq.discord.lavaplayer.format.AudioPlayerInputStream
import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats
import com.sedmelluq.discord.lavaplayer.manager.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.manager.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.common.SourceRegistry
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.collection.AudioTrackCollection
import com.sedmelluq.discord.lavaplayer.track.loader.DelegatedItemLoadResultHandler
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine

fun main() {
    val manager: AudioPlayerManager = DefaultAudioPlayerManager()
    manager.configuration.outputFormat = StandardAudioDataFormats.COMMON_PCM_S16_BE

    SourceRegistry.registerRemoteSources(manager)

    /* create player */
    val player = manager.createPlayer()

    // load items.
    val itemLoader = manager.items.createItemLoader("https://www.youtube.com/watch?v=R76_7N4gyDA")
    itemLoader.resultHandler = DelegatedItemLoadResultHandler(
        { track: AudioTrack? -> player.playTrack(track) },
        { playlist: AudioTrackCollection -> player.playTrack(playlist.tracks[0]) },
        null,
        null
    )
    itemLoader.loadAsync()

    // do some more bullshit lol
    val stream = AudioPlayerInputStream.createStream(player, manager.configuration.outputFormat, 10000L, false)
    val line = AudioSystem.getLine(DataLine.Info(SourceDataLine::class.java, stream.format)) as SourceDataLine
    line.open(stream.format)
    line.start()

    val buffer = ByteArray(StandardAudioDataFormats.COMMON_PCM_S16_BE.maximumChunkSize)
    var chunkSize: Int
    while (stream.read(buffer).also { chunkSize = it } >= 0) {
        line.write(buffer, 0, chunkSize)
    }
}
