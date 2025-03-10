package com.sedmelluq.discord.lavaplayer.manager.event

import com.sedmelluq.discord.lavaplayer.manager.AudioPlayer
import com.sedmelluq.discord.lavaplayer.track.AudioTrack

/**
 * Event that is fired when a track was started, but no audio frames from it have arrived in a long time, specified
 * by the threshold set via AudioPlayerManager.setTrackStuckThreshold().
 *
 * @param player      Audio player
 * @param track       Audio track where the exception occurred
 * @param thresholdMs The wait threshold that was exceeded for this event to trigger
 */
data class TrackStuckEvent(
    override val player: AudioPlayer,
    /**
     * Audio track where the exception occurred
     */
    val track: AudioTrack,
    /**
     * The wait threshold that was exceeded for this event to trigger
     */
    val thresholdMs: Long,
    val stackTrace: List<StackTraceElement> = emptyList()
) : AudioEvent
