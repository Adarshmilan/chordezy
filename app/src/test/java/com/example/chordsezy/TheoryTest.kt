package com.example.chordsezy

import com.example.chordsezy.music.Key
import org.junit.Assert.assertEquals
import org.junit.Test

class TheoryTest {
    private fun family(label: String) =
        Key.ALL.first { it.label == label }.chords.map { "${it.name}: ${it.notesText}" }

    @Test
    fun cFamily() {
        assertEquals(
            listOf("C: C – E – G", "Dm: D – F – A", "Em: E – G – B",
                "F: F – A – C", "G: G – B – D", "Am: A – C – E"),
            family("C"),
        )
    }

    @Test
    fun fFamilyUsesFlats() {
        assertEquals("B♭: B♭ – D – F", family("F")[3])
    }

    @Test
    fun eFamilyUsesSharps() {
        assertEquals("C♯m: C♯ – E – G♯", family("E")[5])
    }

    @Test
    fun chordsContainingC() {
        val c = Key.ALL.first { it.label == "C" }
        assertEquals(listOf("C", "F", "Am"), c.chordsContaining(0).map { it.name })
        assertEquals(listOf("A♭", "Cm", "Fm"), c.otherChordsContaining(0).map { it.name })
    }

    @Test
    fun loopStateResizeKeepsChords() {
        LoopState.clear()
        LoopState.setStep(1, 0, 2)
        LoopState.changeTimeSignature(com.example.chordsezy.music.TimeSignature(3, 4))
        assertEquals(2, LoopState.stepAt(1, 0))
        assertEquals(2, LoopState.soundingChordAt(5))
    }
}
