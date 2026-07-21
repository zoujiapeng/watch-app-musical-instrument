package com.zoujiapeng.watchinstrument.recording

import com.zoujiapeng.watchinstrument.model.InstrumentType
import com.zoujiapeng.watchinstrument.model.Waveform
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingCodecTest {
    @Test
    fun recordingRoundTripsWithoutLosingEvents() {
        val original = PerformanceRecording(
            id = "test-id",
            instrument = InstrumentType.SYNTH,
            createdAtEpochMs = 123456L,
            durationMs = 800L,
            events = listOf(
                PerformanceEvent(
                    atMs = 0L,
                    action = PerformanceAction.ON,
                    voiceKey = 7L,
                    midiNote = 60,
                    waveform = Waveform.SQUARE,
                    velocity = 0.75f,
                ),
                PerformanceEvent(
                    atMs = 800L,
                    action = PerformanceAction.OFF,
                    voiceKey = 7L,
                ),
            ),
        )

        val decoded = RecordingCodec.decode(RecordingCodec.encode(original))
        assertEquals(original.id, decoded.id)
        assertEquals(original.instrument, decoded.instrument)
        assertEquals(original.durationMs, decoded.durationMs)
        assertEquals(original.events.size, decoded.events.size)
        assertEquals(Waveform.SQUARE, decoded.events.first().waveform)
        assertEquals(60, decoded.events.first().midiNote)
    }
}
