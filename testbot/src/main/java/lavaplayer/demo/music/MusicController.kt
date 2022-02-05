package lavaplayer.demo.music

import com.sedmelluq.discord.lavaplayer.filter.equalizer.EqualizerFactory
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeItemSourceManager
import com.sedmelluq.discord.lavaplayer.tools.PlayerLibrary
import com.sedmelluq.discord.lavaplayer.tools.extensions.addListener
import com.sedmelluq.discord.lavaplayer.tools.extensions.source
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.collection.AudioTrackCollection
import com.sedmelluq.discord.lavaplayer.track.marker.TrackMarker
import com.sedmelluq.discord.lavaplayer.track.marker.TrackMarkerHandler.MarkerState
import com.sedmelluq.discord.lavaplayer.track.loader.ItemLoadResultAdapter
import com.sedmelluq.lava.common.tools.exception.FriendlyException
import com.sedmelluq.lava.common.tools.io.MessageInput
import com.sedmelluq.lava.common.tools.io.MessageOutput
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.runBlocking
import lavaplayer.demo.BotApplicationManager
import lavaplayer.demo.BotGuildContext
import lavaplayer.demo.MessageDispatcher
import lavaplayer.demo.controller.BotCommandHandler
import lavaplayer.demo.controller.BotController
import lavaplayer.demo.controller.BotControllerFactory
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.managers.AudioManager
import net.iharder.Base64
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.function.Consumer
import kotlin.math.max
import kotlin.system.measureNanoTime
import kotlin.time.ExperimentalTime

class MusicController(private val manager: BotApplicationManager, private val guild: Guild) : BotController {
    companion object {
        private val log = LoggerFactory.getLogger(MusicController::class.java)

        private val BASS_BOOST = floatArrayOf(
            0.2f, 0.15f, 0.1f, 0.05f, 0.0f, -0.05f, -0.1f, -0.1f, -0.1f, -0.1f, -0.1f,
            -0.1f, -0.1f, -0.1f, -0.1f
        )

        private fun connectToFirstVoiceChannel(audioManager: AudioManager) {
            if (!audioManager.isConnected && !audioManager.isAttemptingToConnect) {
                for (voiceChannel in audioManager.guild.voiceChannels) {
                    if ("Testing" == voiceChannel.name) {
                        audioManager.openAudioConnection(voiceChannel)
                        return
                    }
                }

                for (voiceChannel in audioManager.guild.voiceChannels) {
                    audioManager.openAudioConnection(voiceChannel)
                    return
                }
            }
        }

        private fun connectToInvokerVc(message: Message, audioManager: AudioManager) {
            val member = message.member
                ?: return connectToFirstVoiceChannel(audioManager)

            val voiceState = member.voiceState
                ?: return connectToFirstVoiceChannel(audioManager)

            val voiceChannel = voiceState.channel
                ?: return connectToFirstVoiceChannel(audioManager)

            audioManager.openAudioConnection(voiceChannel)
        }
    }

    private val player = manager.playerManager.createPlayer()
    private val outputChannel = atomic<TextChannel?>(null)
    private val scheduler = MusicScheduler(player, GlobalDispatcher(), manager.executorService)
    private val equalizer = EqualizerFactory()

    init {
        guild.audioManager.sendingHandler = AudioPlayerSendHandler(player)
        player.addListener(scheduler)
    }

    @BotCommandHandler
    private fun add(message: Message, identifier: String) {
        addTrack(message, identifier, now = false, first = false)
    }

    @BotCommandHandler
    private fun first(message: Message, identifier: String) {
        addTrack(message, identifier, now = false, first = true)
    }

    @BotCommandHandler
    private fun firstNow(message: Message, identifier: String) {
        addTrack(message, identifier, now = true, first = true)
    }

    @BotCommandHandler
    private fun now(message: Message, identifier: String) {
        addTrack(message, identifier, now = true, first = false)
    }

    @BotCommandHandler
    private fun hex(pageCount: Int) {
        manager.playerManager.source<YoutubeItemSourceManager>()?.playlistPageCount = pageCount
    }

    @BotCommandHandler
    private fun serialize(message: Message) {
        val baos = ByteArrayOutputStream()
        val outputStream = MessageOutput(baos)
        manager.playerManager.encodeTrack(outputStream, player.playingTrack ?: return)

        for (track in scheduler.drainQueue()) {
            manager.playerManager.encodeTrack(outputStream, track)
        }

        outputStream.finish()
        message.channel.sendFile(Base64.encodeBytes(baos.toByteArray()).encodeToByteArray(), "track.txt").queue()
    }

    @BotCommandHandler
    private fun deserialize(message: Message, content: String) {
        outputChannel.value = message.channel as TextChannel
        connectToInvokerVc(message, guild.audioManager)

        val bytes = Base64.decode(content)
        val inputStream = MessageInput(ByteArrayInputStream(bytes))

        while (true) {
            val track = manager.playerManager.decodeTrack(inputStream)?.decodedTrack
                ?: break

            scheduler.addToQueue(track)
        }
    }

    @BotCommandHandler
    private fun eqsetup() {
        manager.playerManager.configuration.filterHotSwapEnabled = true
        player.setFrameBufferDuration(500)
    }

    @BotCommandHandler
    private fun eqstart() {
        player.setFilterFactory(equalizer)
    }

    @BotCommandHandler
    private fun eqstop() {
        player.setFilterFactory(null)
    }

    @BotCommandHandler
    private fun eqband(band: Int, value: Float) {
        equalizer.setGain(band, value)
    }

    @BotCommandHandler
    private fun eqhighbass(diff: Float) {
        for (i in BASS_BOOST.indices) {
            equalizer.setGain(i, BASS_BOOST[i] + diff)
        }
    }

    @BotCommandHandler
    private fun eqlowbass(diff: Float) {
        for (i in BASS_BOOST.indices) {
            equalizer.setGain(i, -BASS_BOOST[i] + diff)
        }
    }

    @BotCommandHandler
    private fun volume(volume: Int) {
        player.volume = volume
    }

    @BotCommandHandler
    private fun skip() {
        scheduler.skip()
    }

    @BotCommandHandler
    private fun stop() {
        player.stopTrack()
    }

    @BotCommandHandler
    private fun forward(duration: Int) {
        forPlayingTrack { it.position = it.position + duration }
    }

    @BotCommandHandler
    private fun back(duration: Int) {
        forPlayingTrack { it.position = max(0, it.position - duration) }
    }

    @BotCommandHandler
    private fun pause() {
        player.isPaused = true
    }

    @BotCommandHandler
    private fun resume() {
        player.isPaused = false
    }

    @BotCommandHandler
    private fun duration(message: Message) {
        forPlayingTrack { message.channel.sendMessage("Duration is " + it.duration).queue() }
    }

    @BotCommandHandler
    private fun queue(message: Message) {
        val queue = buildString {
            appendLine("```")
            forPlayingTrack { track ->
                appendLine("- ${track.info.title}")
                if (scheduler.queue.isNotEmpty()) {
                    appendLine()
                }
            }

            append(scheduler.queue
                .withIndex()
                .joinToString("\n") { (index, track) -> "${index + 1}. ${track.info.title}" })
            append("\n```")
        }

        message.channel
            .sendMessage(queue)
            .queue()
    }

    @BotCommandHandler
    private fun seek(position: Long) {
        forPlayingTrack { it.position = position }
    }

    @BotCommandHandler
    private fun pos(message: Message) {
        forPlayingTrack { message.channel.sendMessage("Position is " + it.position).queue() }
    }

    @BotCommandHandler
    private fun marker(message: Message, position: Long, text: String) {
        forPlayingTrack {
            it.setMarker(TrackMarker(position) { state: MarkerState ->
                message.channel.sendMessage("Trigger [" + text + "] cause [" + state.name + "]").queue()
            })
        }
    }

    @BotCommandHandler
    private fun unmark() {
        forPlayingTrack { track -> track.setMarker(null) }
    }

    @BotCommandHandler
    private fun version(message: Message) {
        message.channel.sendMessage(PlayerLibrary.VERSION).queue()
    }

    @BotCommandHandler
    private fun leave() {
        guild.audioManager.closeAudioConnection()
    }

    @OptIn(ExperimentalTime::class)
    private fun addTrack(message: Message, identifier: String, now: Boolean, first: Boolean) {
        outputChannel.value = message.channel as TextChannel

        val itemLoader = manager.playerManager.items.createItemLoader(identifier)
        itemLoader.resultHandler = object : ItemLoadResultAdapter() {
            override fun onTrackLoad(track: AudioTrack) {
                connectToInvokerVc(message, guild.audioManager)

                message.channel
                    .sendMessage("Starting now: " + track.info.title + " (length " + track.duration + ")")
                    .queue()

                if (now) {
                    scheduler.playNow(track, true)
                } else {
                    scheduler.addToQueue(track)
                }
            }

            override fun onCollectionLoad(collection: AudioTrackCollection) {
                connectToInvokerVc(message, guild.audioManager)

                val tracks: List<AudioTrack> = collection.tracks
                message.channel.sendMessage("Loaded playlist: " + collection.name + " (" + tracks.size + ")").queue()

                var selected = collection.selectedTrack
                if (selected != null) {
                    message.channel.sendMessage("Selected track from playlist: " + selected.info.title).queue()
                } else {
                    selected = tracks[0]
                    message.channel.sendMessage("Added first track from playlist: " + selected.info.title).queue()
                }

                if (now) {
                    scheduler.playNow(selected, true)
                } else {
                    scheduler.addToQueue(selected)
                }

                if (!first) {
                    val queued = 10.coerceAtMost(collection.tracks.size)
                    for (i in 0 until queued) {
                        if (tracks[i] !== selected) {
                            scheduler.addToQueue(tracks[i])
                        }
                    }

                    message.channel
                        .sendMessage("Added the first $queued tracks from playlist: ${collection.name}")
                        .queue()
                }
            }

            override fun noMatches() {
                message.channel.sendMessage("Nothing found for $identifier").queue()
            }

            override fun onLoadFailed(exception: FriendlyException) {
                message.channel.sendMessage("Failed with message: " + exception.message + " (" + exception.javaClass.simpleName + ")")
                    .queue()
            }
        }

        runBlocking {
            val took = measureNanoTime { itemLoader.load() }
            log.info("Search for '$identifier' took ${took / 1_000_000}ms")
        }
    }

    private fun forPlayingTrack(operation: TrackOperation) {
        val track = player.playingTrack
        if (track != null) {
            operation.execute(track)
        }
    }

    class Factory : BotControllerFactory<MusicController> {
        override fun getControllerClass(): Class<MusicController> =
            MusicController::class.java

        override fun create(manager: BotApplicationManager, state: BotGuildContext, guild: Guild): MusicController =
            MusicController(manager, guild)
    }

    private fun interface TrackOperation {
        fun execute(track: AudioTrack)
    }

    private inner class GlobalDispatcher : MessageDispatcher {
        override fun sendMessage(message: String, success: Consumer<Message>, failure: Consumer<Throwable>) {
            outputChannel.value?.sendMessage(message)?.queue(success, failure)
        }

        override fun sendMessage(message: String) {
            outputChannel.value?.sendMessage(message)?.queue()
        }
    }
}
