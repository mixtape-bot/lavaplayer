package com.sedmelluq.lava.extensions.format.xm

import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream
import com.sedmelluq.discord.lavaplayer.track.playback.AudioProcessingContext
import ibxm.Channel
import ibxm.IBXM
import ibxm.Module;


public class XmFileLoader(public val inputStream: SeekableInputStream) {
    public fun loadTrack(context: AudioProcessingContext): XmTrackProvider {
        val module = Module(inputStream)
        val ibxm = IBXM(module, context.outputFormat.sampleRate)
        ibxm.interpolation = Channel.SINC

        return XmTrackProvider(context, ibxm)
    }
}
