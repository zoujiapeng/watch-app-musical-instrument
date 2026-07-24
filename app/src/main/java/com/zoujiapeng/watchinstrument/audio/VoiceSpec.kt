package com.zoujiapeng.watchinstrument.audio

import com.zoujiapeng.watchinstrument.model.InstrumentType
import com.zoujiapeng.watchinstrument.model.Percussion
import com.zoujiapeng.watchinstrument.model.Waveform

data class VoiceSpec(
    val instrument: InstrumentType,
    val midiNote: Int = 60,
    val percussion: Percussion? = null,
    val waveform: Waveform = Waveform.SAW,
    val velocity: Float = 1f,
)
