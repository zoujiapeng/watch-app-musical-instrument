package com.zoujiapeng.watchinstrument.model

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteMathTest {
    @Test
    fun concertAIs440Hz() {
        assertEquals(440.0, NoteMath.midiToFrequency(69), 0.0001)
    }

    @Test
    fun middleCNameAndFrequencyAreCorrect() {
        assertEquals("C4", NoteMath.noteName(60))
        assertEquals(261.6256, NoteMath.midiToFrequency(60), 0.001)
    }
}
