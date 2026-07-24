package com.zoujiapeng.watchinstrument.model

import com.zoujiapeng.watchinstrument.R

enum class InstrumentType(
    val id: String,
    val titleRes: Int,
    val defaultOctave: Int,
    val supportsOctave: Boolean,
) {
    PIANO("piano", R.string.instrument_piano, 4, true),
    GUITAR("guitar", R.string.instrument_guitar, 0, true),
    DRUMS("drums", R.string.instrument_drums, 0, false),
    BASS("bass", R.string.instrument_bass, 0, true),
    XYLOPHONE("xylophone", R.string.instrument_xylophone, 5, true),
    SYNTH("synth", R.string.instrument_synth, 4, true),
    ;

    companion object {
        fun fromId(id: String?): InstrumentType =
            entries.firstOrNull { it.id == id } ?: PIANO
    }
}

enum class Waveform {
    SINE,
    SAW,
    SQUARE,
    TRIANGLE,
    ;

    fun next(): Waveform = entries[(ordinal + 1) % entries.size]
}

enum class Percussion {
    KICK,
    SNARE,
    CLOSED_HAT,
    CLAP,
    TOM,
    CYMBAL,
    METRONOME_LOW,
    METRONOME_HIGH,
}
