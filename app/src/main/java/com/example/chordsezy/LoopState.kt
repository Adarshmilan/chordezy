package com.example.chordsezy

import com.example.chordsezy.audio.LoopSequence
import com.example.chordsezy.music.Key
import com.example.chordsezy.music.TimeSignature

/**
 * The loop you are building. Kept in memory for the whole app session.
 * Each step is one beat and stores the index (0..5) of a chord from the key's
 * chord family, or [EMPTY] meaning "keep the previous chord ringing".
 * Storing indexes (not chord names) means changing the key transposes the loop.
 */
object LoopState {
    const val EMPTY = -1
    const val MIN_BPM = 40
    const val MAX_BPM = 220
    const val MAX_BARS = 8

    var bpm = 90
    var timeSignature = TimeSignature(4, 4)
        private set
    var bars = 4
        private set
    var metronome = true
    var steps = IntArray(bars * timeSignature.beats) { EMPTY }
        private set

    val beatsPerBar get() = timeSignature.beats

    fun stepAt(bar: Int, beat: Int) = steps[bar * beatsPerBar + beat]

    fun setStep(bar: Int, beat: Int, chordIndex: Int) {
        steps[bar * beatsPerBar + beat] = chordIndex
    }

    fun changeTimeSignature(ts: TimeSignature) = resize(bars, ts)

    fun changeBars(newBars: Int) = resize(newBars.coerceIn(1, MAX_BARS), timeSignature)

    private fun resize(newBars: Int, ts: TimeSignature) {
        val old = steps
        val oldBeats = beatsPerBar
        val fresh = IntArray(newBars * ts.beats) { EMPTY }
        for (bar in 0 until minOf(bars, newBars)) {
            for (beat in 0 until minOf(oldBeats, ts.beats)) {
                fresh[bar * ts.beats + beat] = old[bar * oldBeats + beat]
            }
        }
        bars = newBars
        timeSignature = ts
        steps = fresh
    }

    fun clear() = steps.fill(EMPTY)

    /** Puts one chord on beat 1 of each bar, cycling through [pattern]. */
    fun fillPattern(pattern: IntArray) {
        clear()
        for (bar in 0 until bars) setStep(bar, 0, pattern[bar % pattern.size])
    }

    /** The chord that sounds on [step], following held beats back (wrapping around the loop). */
    fun soundingChordAt(step: Int): Int {
        for (i in 0 until steps.size) {
            val s = steps[(step - i + steps.size) % steps.size]
            if (s != EMPTY) return s
        }
        return EMPTY
    }

    fun toSequence(key: Key) = LoopSequence(
        bpm = bpm,
        beatsPerBar = beatsPerBar,
        steps = steps.map { if (it == EMPTY) null else key.chords[it].midiNotes() },
        metronome = metronome,
    )
}
