package com.zoujiapeng.watchinstrument.model

import kotlin.math.pow

object NoteMath {
    private val names = arrayOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")

    fun midiToFrequency(midiNote: Int): Double =
        440.0 * 2.0.pow((midiNote - 69) / 12.0)

    fun noteName(midiNote: Int): String {
        val safe = midiNote.coerceIn(0, 127)
        return "${names[safe % 12]}${safe / 12 - 1}"
    }

    fun clampMidi(midiNote: Int): Int = midiNote.coerceIn(0, 127)
}
