package com.zoujiapeng.watchinstrument.recording

import android.os.SystemClock
import com.zoujiapeng.watchinstrument.model.InstrumentType
import com.zoujiapeng.watchinstrument.model.PlayableControl
import com.zoujiapeng.watchinstrument.model.Waveform
import java.util.UUID

class PerformanceRecorder {
    private var instrument: InstrumentType? = null
    private var startedAtElapsedMs = 0L
    private var createdAtEpochMs = 0L
    private val events = mutableListOf<PerformanceEvent>()
    private val activeVoiceKeys = linkedSetOf<Long>()

    val isRecording: Boolean
        get() = instrument != null

    fun start(instrument: InstrumentType) {
        this.instrument = instrument
        startedAtElapsedMs = SystemClock.elapsedRealtime()
        createdAtEpochMs = System.currentTimeMillis()
        events.clear()
        activeVoiceKeys.clear()
    }

    fun noteOn(
        voiceKey: Long,
        control: PlayableControl,
        waveform: Waveform,
        velocity: Float,
    ) {
        if (!isRecording) return
        activeVoiceKeys += voiceKey
        events += PerformanceEvent(
            atMs = elapsed(),
            action = PerformanceAction.ON,
            voiceKey = voiceKey,
            midiNote = control.midiNote,
            percussion = control.percussion,
            waveform = waveform,
            velocity = velocity.coerceIn(0.05f, 1f),
        )
    }

    fun noteOff(voiceKey: Long) {
        if (!isRecording || voiceKey !in activeVoiceKeys) return
        activeVoiceKeys -= voiceKey
        events += PerformanceEvent(
            atMs = elapsed(),
            action = PerformanceAction.OFF,
            voiceKey = voiceKey,
        )
    }

    fun stop(): PerformanceRecording? {
        val recordingInstrument = instrument ?: return null
        val stoppedAt = elapsed()
        activeVoiceKeys.toList().forEach { key ->
            events += PerformanceEvent(
                atMs = stoppedAt,
                action = PerformanceAction.OFF,
                voiceKey = key,
            )
        }
        activeVoiceKeys.clear()
        instrument = null

        if (events.none { it.action == PerformanceAction.ON }) {
            events.clear()
            return null
        }
        return PerformanceRecording(
            id = UUID.randomUUID().toString(),
            instrument = recordingInstrument,
            createdAtEpochMs = createdAtEpochMs,
            durationMs = stoppedAt.coerceAtLeast(1L),
            events = events.toList(),
        ).also { events.clear() }
    }

    fun cancel() {
        instrument = null
        events.clear()
        activeVoiceKeys.clear()
    }

    private fun elapsed(): Long =
        (SystemClock.elapsedRealtime() - startedAtElapsedMs).coerceAtLeast(0L)
}
