package com.zoujiapeng.watchinstrument.model

object InstrumentLayouts {
    fun controls(instrument: InstrumentType, octave: Int): List<PlayableControl> = when (instrument) {
        InstrumentType.PIANO -> piano(octave)
        InstrumentType.GUITAR -> guitar(octave)
        InstrumentType.DRUMS -> drums()
        InstrumentType.BASS -> bass(octave)
        InstrumentType.XYLOPHONE -> xylophone(octave)
        InstrumentType.SYNTH -> synth(octave)
    }

    fun hitTest(
        controls: List<PlayableControl>,
        normalizedX: Float,
        normalizedY: Float,
    ): PlayableControl? = controls
        .asSequence()
        .filter { it.region.contains(normalizedX, normalizedY) }
        .maxByOrNull { it.zIndex }

    private fun piano(octave: Int): List<PlayableControl> {
        val base = (octave + 1) * 12
        val whiteOffsets = intArrayOf(0, 2, 4, 5, 7, 9, 11, 12)
        val controls = mutableListOf<PlayableControl>()
        val whiteWidth = 1f / whiteOffsets.size

        whiteOffsets.forEachIndexed { index, offset ->
            val note = NoteMath.clampMidi(base + offset)
            controls += PlayableControl(
                id = index,
                region = NormalizedRect(index * whiteWidth, 0f, (index + 1) * whiteWidth, 1f),
                label = NoteMath.noteName(note),
                midiNote = note,
            )
        }

        val blackKeys = listOf(
            Triple(0, 1, 8),
            Triple(1, 3, 9),
            Triple(3, 6, 10),
            Triple(4, 8, 11),
            Triple(5, 10, 12),
        )
        blackKeys.forEach { (leftWhite, offset, id) ->
            val center = (leftWhite + 1) * whiteWidth
            val width = whiteWidth * 0.62f
            val note = NoteMath.clampMidi(base + offset)
            controls += PlayableControl(
                id = id,
                region = NormalizedRect(center - width / 2f, 0f, center + width / 2f, 0.61f),
                label = NoteMath.noteName(note),
                midiNote = note,
                zIndex = 1,
            )
        }
        return controls
    }

    private fun guitar(octaveShift: Int): List<PlayableControl> {
        // High E to low E, matching the visual top-to-bottom string order.
        val tuning = intArrayOf(64, 59, 55, 50, 45, 40)
        return stringGrid(tuning, frets = 6, octaveShift = octaveShift)
    }

    private fun bass(octaveShift: Int): List<PlayableControl> {
        // G, D, A, E from top to bottom.
        val tuning = intArrayOf(43, 38, 33, 28)
        return stringGrid(tuning, frets = 5, octaveShift = octaveShift)
    }

    private fun stringGrid(
        tuning: IntArray,
        frets: Int,
        octaveShift: Int,
    ): List<PlayableControl> {
        val rowHeight = 1f / tuning.size
        val columnWidth = 1f / frets
        return buildList {
            tuning.forEachIndexed { row, openNote ->
                repeat(frets) { fret ->
                    val note = NoteMath.clampMidi(openNote + fret + octaveShift * 12)
                    add(
                        PlayableControl(
                            id = row * frets + fret,
                            region = NormalizedRect(
                                fret * columnWidth,
                                row * rowHeight,
                                (fret + 1) * columnWidth,
                                (row + 1) * rowHeight,
                            ),
                            label = NoteMath.noteName(note),
                            midiNote = note,
                        ),
                    )
                }
            }
        }
    }

    private fun drums(): List<PlayableControl> {
        val pads = listOf(
            Triple("KICK", Percussion.KICK, 0),
            Triple("SNARE", Percussion.SNARE, 1),
            Triple("HAT", Percussion.CLOSED_HAT, 2),
            Triple("CLAP", Percussion.CLAP, 3),
            Triple("TOM", Percussion.TOM, 4),
            Triple("CRASH", Percussion.CYMBAL, 5),
        )
        val gap = 0.028f
        val columns = 2
        val rows = 3
        val width = (1f - gap * (columns + 1)) / columns
        val height = (1f - gap * (rows + 1)) / rows
        return pads.mapIndexed { index, (label, sound, id) ->
            val column = index % columns
            val row = index / columns
            val left = gap + column * (width + gap)
            val top = gap + row * (height + gap)
            PlayableControl(
                id = id,
                region = NormalizedRect(left, top, left + width, top + height),
                label = label,
                percussion = sound,
            )
        }
    }

    private fun xylophone(octave: Int): List<PlayableControl> {
        val offsets = intArrayOf(0, 2, 4, 5, 7, 9, 11, 12)
        val base = (octave + 1) * 12
        val gap = 0.012f
        val width = (1f - gap * (offsets.size + 1)) / offsets.size
        return offsets.mapIndexed { index, offset ->
            val note = NoteMath.clampMidi(base + offset)
            val inset = index * 0.018f
            val left = gap + index * (width + gap)
            PlayableControl(
                id = index,
                region = NormalizedRect(left, inset, left + width, 1f - inset),
                label = NoteMath.noteName(note),
                midiNote = note,
            )
        }
    }

    private fun synth(octave: Int): List<PlayableControl> {
        val base = (octave + 1) * 12
        val columns = 3
        val rows = 4
        val gap = 0.022f
        val width = (1f - gap * (columns + 1)) / columns
        val height = (1f - gap * (rows + 1)) / rows
        return (0 until 12).map { index ->
            val row = index / columns
            val column = index % columns
            val left = gap + column * (width + gap)
            val top = gap + row * (height + gap)
            val note = NoteMath.clampMidi(base + index)
            PlayableControl(
                id = index,
                region = NormalizedRect(left, top, left + width, top + height),
                label = NoteMath.noteName(note),
                midiNote = note,
            )
        }
    }
}
