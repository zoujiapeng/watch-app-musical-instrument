package com.zoujiapeng.watchinstrument.settings

import android.content.Context
import com.zoujiapeng.watchinstrument.model.InstrumentType
import com.zoujiapeng.watchinstrument.model.Waveform

class AppPreferences(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("watch_instrument_preferences", Context.MODE_PRIVATE)

    var masterVolume: Float
        get() = preferences.getFloat(KEY_MASTER_VOLUME, 0.8f).coerceIn(0f, 1f)
        set(value) = preferences.edit().putFloat(KEY_MASTER_VOLUME, value.coerceIn(0f, 1f)).apply()

    var hapticsEnabled: Boolean
        get() = preferences.getBoolean(KEY_HAPTICS, true)
        set(value) = preferences.edit().putBoolean(KEY_HAPTICS, value).apply()

    var noteLabelsEnabled: Boolean
        get() = preferences.getBoolean(KEY_NOTE_LABELS, true)
        set(value) = preferences.edit().putBoolean(KEY_NOTE_LABELS, value).apply()

    var keepScreenOn: Boolean
        get() = preferences.getBoolean(KEY_KEEP_SCREEN_ON, true)
        set(value) = preferences.edit().putBoolean(KEY_KEEP_SCREEN_ON, value).apply()

    var defaultBpm: Int
        get() = preferences.getInt(KEY_DEFAULT_BPM, 100).coerceIn(40, 240)
        set(value) = preferences.edit().putInt(KEY_DEFAULT_BPM, value.coerceIn(40, 240)).apply()

    var beatsPerBar: Int
        get() = preferences.getInt(KEY_BEATS_PER_BAR, 4).coerceIn(2, 6)
        set(value) = preferences.edit().putInt(KEY_BEATS_PER_BAR, value.coerceIn(2, 6)).apply()

    var lastInstrument: InstrumentType
        get() = InstrumentType.fromId(preferences.getString(KEY_LAST_INSTRUMENT, InstrumentType.PIANO.id))
        set(value) = preferences.edit().putString(KEY_LAST_INSTRUMENT, value.id).apply()

    var waveform: Waveform
        get() = preferences.getString(KEY_WAVEFORM, Waveform.SAW.name)
            ?.let { runCatching { Waveform.valueOf(it) }.getOrNull() }
            ?: Waveform.SAW
        set(value) = preferences.edit().putString(KEY_WAVEFORM, value.name).apply()

    fun octave(instrument: InstrumentType): Int {
        val fallback = instrument.defaultOctave
        return preferences.getInt("octave_${instrument.id}", fallback)
            .coerceIn(octaveRange(instrument))
    }

    fun setOctave(instrument: InstrumentType, octave: Int) {
        preferences.edit()
            .putInt("octave_${instrument.id}", octave.coerceIn(octaveRange(instrument)))
            .apply()
    }

    fun reset() {
        preferences.edit().clear().apply()
    }

    companion object {
        private const val KEY_MASTER_VOLUME = "master_volume"
        private const val KEY_HAPTICS = "haptics"
        private const val KEY_NOTE_LABELS = "note_labels"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_DEFAULT_BPM = "default_bpm"
        private const val KEY_BEATS_PER_BAR = "beats_per_bar"
        private const val KEY_LAST_INSTRUMENT = "last_instrument"
        private const val KEY_WAVEFORM = "waveform"

        fun octaveRange(instrument: InstrumentType): IntRange = when (instrument) {
            InstrumentType.PIANO -> 2..6
            InstrumentType.GUITAR -> -1..1
            InstrumentType.DRUMS -> 0..0
            InstrumentType.BASS -> -1..1
            InstrumentType.XYLOPHONE -> 3..6
            InstrumentType.SYNTH -> 2..6
        }
    }
}
