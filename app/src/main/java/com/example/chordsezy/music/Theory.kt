package com.example.chordsezy.music

/** A spelled note, e.g. F♯ = letter 3 (F), pitch class 6. */
data class Note(val letter: Int, val pitchClass: Int) {
    val name: String
        get() {
            val natural = NATURAL_PITCH[letter]
            val accidental = when ((pitchClass - natural + 12) % 12) {
                0 -> ""
                1 -> "♯"
                2 -> "𝄪"
                11 -> "♭"
                10 -> "𝄫"
                else -> "?"
            }
            return LETTERS[letter] + accidental
        }

    companion object {
        val LETTERS = arrayOf("C", "D", "E", "F", "G", "A", "B")
        val NATURAL_PITCH = intArrayOf(0, 2, 4, 5, 7, 9, 11)
    }
}

enum class Quality(val label: String, val suffix: String, val intervals: List<Int>, val intervalNames: List<String>) {
    MAJOR("Major", "", listOf(0, 4, 7), listOf("Root", "Major 3rd", "Perfect 5th")),
    MINOR("Minor", "m", listOf(0, 3, 7), listOf("Root", "Minor 3rd", "Perfect 5th")),
}

/** A chord built from a key's major scale. [degree] is 1..7. */
data class Chord(
    val roman: String,
    val degree: Int,
    val quality: Quality,
    val notes: List<Note>,
) {
    val root: Note get() = notes[0]
    val name: String get() = root.name + quality.suffix
    val notesText: String get() = notes.joinToString(" – ") { it.name }

    fun contains(pitchClass: Int) = notes.any { it.pitchClass == pitchClass }

    companion object {
        /** Builds a triad on [root], spelling the 3rd and 5th with the right letters. */
        fun build(root: Note, quality: Quality, roman: String = "", degree: Int = 0): Chord {
            val notes = listOf(0, 1, 2).map { i ->
                Note((root.letter + i * 2) % 7, (root.pitchClass + quality.intervals[i]) % 12)
            }
            return Chord(roman, degree, quality, notes)
        }

        /** All 24 major and minor chords, using the usual spelling for each root. */
        val ALL_TRIADS: List<Chord> by lazy {
            val majorRoots = Key.ALL.map { it.tonic }
            val minorRoots = listOf(
                Note(0, 0), Note(0, 1), Note(1, 2), Note(2, 3), Note(2, 4), Note(3, 5),
                Note(3, 6), Note(4, 7), Note(4, 8), Note(5, 9), Note(6, 10), Note(6, 11),
            ) // C, C♯, D, E♭, E, F, F♯, G, G♯, A, B♭, B
            majorRoots.map { build(it, Quality.MAJOR) } + minorRoots.map { build(it, Quality.MINOR) }
        }
    }

    /** MIDI note numbers for playback: bass root + triad around middle C. */
    fun midiNotes(): List<Int> {
        val rootMidi = 48 + root.pitchClass // C3..B3
        val triad = quality.intervals.map { rootMidi + 12 + it }
        return listOf(rootMidi) + triad
    }
}

/** A "chord family" = the key. Its six chords in scale order: I, ii, iii, IV, V, vi. */
data class Key(val tonic: Note, val label: String) {

    /** The 7 notes of the major scale, spelled with consecutive letters. */
    val scale: List<Note> by lazy {
        val steps = intArrayOf(0, 2, 4, 5, 7, 9, 11)
        (0 until 7).map { i ->
            Note((tonic.letter + i) % 7, (tonic.pitchClass + steps[i]) % 12)
        }
    }

    val chords: List<Chord> by lazy {
        listOf(
            chord("I", 1, Quality.MAJOR),
            chord("ii", 2, Quality.MINOR),
            chord("iii", 3, Quality.MINOR),
            chord("IV", 4, Quality.MAJOR),
            chord("V", 5, Quality.MAJOR),
            chord("vi", 6, Quality.MINOR),
        )
    }

    private fun chord(roman: String, degree: Int, quality: Quality): Chord {
        val i = degree - 1
        val notes = listOf(scale[i], scale[(i + 2) % 7], scale[(i + 4) % 7])
        return Chord(roman, degree, quality, notes)
    }

    /** Chords of this family that contain the note. */
    fun chordsContaining(pitchClass: Int): List<Chord> = chords.filter { it.contains(pitchClass) }

    /** Other major/minor chords (not in this family) that contain the note. */
    fun otherChordsContaining(pitchClass: Int): List<Chord> {
        val inFamily = chords.map { it.root.pitchClass to it.quality }.toSet()
        return Chord.ALL_TRIADS.filter {
            it.contains(pitchClass) && (it.root.pitchClass to it.quality) !in inFamily
        }
    }

    companion object {
        /** The 12 chord families, using the most common spelling for each key. */
        val ALL: List<Key> = listOf(
            Key(Note(0, 0), "C"),
            Key(Note(1, 1), "D♭"),   // C♯/D♭
            Key(Note(1, 2), "D"),
            Key(Note(2, 3), "E♭"),   // D♯/E♭
            Key(Note(2, 4), "E"),
            Key(Note(3, 5), "F"),
            Key(Note(3, 6), "F♯"),   // F♯/G♭
            Key(Note(4, 7), "G"),
            Key(Note(5, 8), "A♭"),   // G♯/A♭
            Key(Note(5, 9), "A"),
            Key(Note(6, 10), "B♭"),  // A♯/B♭
            Key(Note(6, 11), "B"),
        )

        val ALT_NAMES = mapOf(1 to "C♯", 3 to "D♯", 6 to "G♭", 8 to "G♯", 10 to "A♯")

        fun byIndex(i: Int): Key = ALL[i.coerceIn(0, 11)]
    }
}

data class TimeSignature(val beats: Int, val unit: Int) {
    val label: String get() = "$beats/$unit"

    companion object {
        val ALL = listOf(
            TimeSignature(2, 4),
            TimeSignature(3, 4),
            TimeSignature(4, 4),
            TimeSignature(5, 4),
            TimeSignature(6, 8),
        )
    }
}
