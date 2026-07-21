package com.zoujiapeng.watchinstrument.recording

import com.zoujiapeng.watchinstrument.model.InstrumentType
import com.zoujiapeng.watchinstrument.model.Percussion
import com.zoujiapeng.watchinstrument.model.Waveform

enum class PerformanceAction {
    ON,
    OFF,
}

data class PerformanceEvent(
    val atMs: Long,
    val action: PerformanceAction,
    val voiceKey: Long,
    val midiNote: Int? = null,
    val percussion: Percussion? = null,
    val waveform: Waveform = Waveform.SAW,
    val velocity: Float = 1f,
)

data class PerformanceRecording(
    val id: String,
    val instrument: InstrumentType,
    val createdAtEpochMs: Long,
    val durationMs: Long,
    val events: List<PerformanceEvent>,
)
