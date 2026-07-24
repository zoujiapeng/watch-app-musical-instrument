package com.zoujiapeng.watchinstrument.audio

import com.zoujiapeng.watchinstrument.model.InstrumentType
import com.zoujiapeng.watchinstrument.model.Percussion

internal object VoiceFactory {
    fun create(id: Long, spec: VoiceSpec, sampleRate: Int): Voice = when (spec.instrument) {
        InstrumentType.PIANO -> PianoVoice(id, spec.midiNote, spec.velocity, sampleRate)
        InstrumentType.GUITAR -> PluckedStringVoice(id, spec.midiNote, spec.velocity, sampleRate, bright = true)
        InstrumentType.DRUMS -> DrumVoice(
            id = id,
            sound = spec.percussion ?: Percussion.SNARE,
            velocity = spec.velocity,
            sampleRate = sampleRate,
        )
        InstrumentType.BASS -> BassVoice(id, spec.midiNote, spec.velocity, sampleRate)
        InstrumentType.XYLOPHONE -> MalletVoice(id, spec.midiNote, spec.velocity, sampleRate)
        InstrumentType.SYNTH -> SynthVoice(id, spec.midiNote, spec.velocity, spec.waveform, sampleRate)
    }
}
