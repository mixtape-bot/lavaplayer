package lavaplayer.demo.music

import com.sedmelluq.discord.lavaplayer.manager.AudioPlayer
import com.sedmelluq.discord.lavaplayer.manager.event.AudioEventAdapter
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason
import kotlinx.atomicfu.atomic
import lavaplayer.demo.MessageDispatcher
import net.dv8tion.jda.api.entities.Message
import java.util.concurrent.BlockingDeque
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class MusicScheduler(
    private val player: AudioPlayer,
    private val messageDispatcher: MessageDispatcher,
    executorService: ScheduledExecutorService
) : AudioEventAdapter(), Runnable {
    private var boxMessage: Message? by atomic(null)
    private var creatingBoxMessage: Boolean by atomic(false)

    val queue: BlockingDeque<AudioTrack> = LinkedBlockingDeque()

    init {
        executorService.scheduleAtFixedRate(this, 3000L, 15000L, TimeUnit.MILLISECONDS)
    }

    fun addToQueue(audioTrack: AudioTrack) {
        queue.addLast(audioTrack)
        startNextTrack(true)
    }

    fun drainQueue(): List<AudioTrack> {
        val drainedQueue: MutableList<AudioTrack> = mutableListOf()
        queue.drainTo(drainedQueue)
        return drainedQueue
    }

    fun playNow(audioTrack: AudioTrack, clearQueue: Boolean) {
        if (clearQueue) {
            queue.clear()
        }
        queue.addFirst(audioTrack)
        startNextTrack(false)
    }

    fun skip() {
        startNextTrack(false)
    }

    private fun startNextTrack(noInterrupt: Boolean) {
        val next = queue.pollFirst()
        if (next != null) {
            if (!player.startTrack(next, noInterrupt)) {
                queue.addFirst(next)
            }
        } else {
            player.stopTrack()
            messageDispatcher.sendMessage("Queue finished.")
        }
    }

    override fun onTrackStart(player: AudioPlayer, track: AudioTrack) {
        updateTrackBox(true)
    }

    override fun onTrackEnd(player: AudioPlayer, track: AudioTrack, endReason: AudioTrackEndReason) {
        if (endReason.mayStartNext) {
            startNextTrack(true)
            messageDispatcher.sendMessage(String.format("Track %s finished.", track.info.title))
        }
    }

    override fun onTrackStuck(player: AudioPlayer, track: AudioTrack, thresholdMs: Long) {
        messageDispatcher.sendMessage(String.format("Track %s got stuck, skipping.", track.info.title))
        startNextTrack(false)
    }

    override fun onPlayerResume(player: AudioPlayer) {
        updateTrackBox(false)
    }

    override fun onPlayerPause(player: AudioPlayer) {
        updateTrackBox(false)
    }

    private fun updateTrackBox(newMessage: Boolean) {
        val track = player.playingTrack
        if (track == null || newMessage) {
            boxMessage?.delete()?.queue()
            boxMessage = null
        }

        if (track != null) {
            val message = boxMessage
            val box = TrackBoxBuilder.buildTrackBox(80, track, player.isPaused, player.volume)
            if (message != null) {
                message.editMessage(box).queue()
            } else {
                if (!creatingBoxMessage) {
                    creatingBoxMessage = true
                    messageDispatcher.sendMessage(box,
                        { boxMessage = it; creatingBoxMessage = false },
                        { creatingBoxMessage = false }
                    )
                }
            }
        }
    }

    override fun run() {
        updateTrackBox(false)
    }
}
