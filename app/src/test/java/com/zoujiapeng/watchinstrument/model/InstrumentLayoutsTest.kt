package com.zoujiapeng.watchinstrument.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstrumentLayoutsTest {
    @Test
    fun everyInstrumentProvidesPlayableControls() {
        InstrumentType.entries.forEach { instrument ->
            val controls = InstrumentLayouts.controls(instrument, instrument.defaultOctave)
            assertTrue("$instrument should have controls", controls.isNotEmpty())
            assertTrue(controls.all { it.midiNote != null || it.percussion != null })
        }
    }

    @Test
    fun everyControlStaysInsideNormalizedCanvas() {
        InstrumentType.entries.forEach { instrument ->
            InstrumentLayouts.controls(instrument, instrument.defaultOctave).forEach { control ->
                assertTrue(control.region.left in 0f..1f)
                assertTrue(control.region.top in 0f..1f)
                assertTrue(control.region.right in 0f..1f)
                assertTrue(control.region.bottom in 0f..1f)
                assertTrue(control.region.right > control.region.left)
                assertTrue(control.region.bottom > control.region.top)
            }
        }
    }

    @Test
    fun blackPianoKeyWinsOverWhiteKeyInOverlap() {
        val controls = InstrumentLayouts.controls(InstrumentType.PIANO, 4)
        val hit = InstrumentLayouts.hitTest(controls, 0.125f, 0.2f)
        assertNotNull(hit)
        assertEquals(1, hit?.zIndex)
        assertEquals(61, hit?.midiNote)
    }

    @Test
    fun drumLayoutContainsSixNamedPads() {
        val controls = InstrumentLayouts.controls(InstrumentType.DRUMS, 0)
        assertEquals(6, controls.size)
        assertEquals(Percussion.KICK, controls.first().percussion)
        assertEquals(Percussion.CYMBAL, controls.last().percussion)
    }
}
