package com.zoujiapeng.watchinstrument.audio

import com.zoujiapeng.watchinstrument.model.InstrumentType
import com.zoujiapeng.watchinstrument.model.Percussion
import com.zoujiapeng.watchinstrument.model.Waveform
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioSynthesisTest {
    @Test
    fun allVoiceFamiliesProduceFiniteSamples() {
        val specs = listOf(
            VoiceSpec(InstrumentType.PIANO, midiNote = 60),
            VoiceSpec(InstrumentType.GUITAR, midiNote = 52),
            VoiceSpec(InstrumentType.DRUMS, percussion = Percussion.SNARE),
            VoiceSpec(InstrumentType.BASS, midiNote = 40),
            VoiceSpec(InstrumentType.XYLOPHONE, midiNote = 72),
            VoiceSpec(InstrumentType.SYNTH, midiNote = 67, waveform = Waveform.TRIANGLE),
        )
        specs.forEachIndexed { index, spec ->
            val voice = VoiceFactory.create(index + 1L, spec, 48_000)
            val buffer = FloatArray(256)
            var peak = 0f
            repeat(50) {
                buffer.fill(0f)
                voice.render(buffer, buffer.size)
                assertTrue(buffer.all { it.isFinite() })
                peak = maxOf(peak, buffer.maxOf { kotlin.math.abs(it) })
            }
            assertTrue("${spec.instrument} should produce audible samples", peak > 0.0001f)
            voice.noteOff()
            repeat(8) {
                buffer.fill(0f)
                voice.render(buffer, buffer.size)
                assertTrue(buffer.all { it.isFinite() })
            }
        }
    }
}
