package com.example.chordsezy

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.chordsezy.audio.SoundEngine
import com.example.chordsezy.music.Chord
import com.example.chordsezy.music.Key
import com.example.chordsezy.music.Quality
import com.google.android.material.card.MaterialCardView

/** Screen 2: the six chords of the chosen family, with the notes in each chord. */
class ChordsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_KEY = "key_index"
        private const val STATE_NOTE = "selected_note"
    }

    private lateinit var key: Key
    private var keyIndex = 0
    private var selectedNote = 0
    private val notePills = ArrayList<TextView>()
    private val chordCards = ArrayList<MaterialCardView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_chords)
        applySystemBarPadding(findViewById(R.id.root))

        keyIndex = intent.getIntExtra(EXTRA_KEY, 0)
        key = Key.byIndex(keyIndex)

        findViewById<TextView>(R.id.title).text = getString(R.string.chord_family_of, key.label)
        findViewById<TextView>(R.id.scaleLabel).text = getString(R.string.scale_label, key.label)

        val scaleRow = findViewById<LinearLayout>(R.id.scaleRow)
        key.scale.forEachIndexed { i, note ->
            val pill = TextView(this).apply {
                text = "${note.name}\n${i + 1}"
                gravity = Gravity.CENTER
                textSize = 15f
                setPadding(0, dp(8), 0, dp(8))
                isClickable = true
                setOnClickListener { selectNote(i) }
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                    marginEnd = if (i < 6) dp(4) else 0
                }
            }
            scaleRow.addView(pill)
            notePills.add(pill)
        }

        // Chords in scale order: I, ii, iii, IV, V, vi
        val chordList = findViewById<LinearLayout>(R.id.chordList)
        for (chord in key.chords) {
            val card = chordCard(chord)
            chordCards.add(card)
            chordList.addView(card)
        }

        selectNote(savedInstanceState?.getInt(STATE_NOTE) ?: 0)

        findViewById<View>(R.id.buildLoop).setOnClickListener {
            startActivity(Intent(this, LooperActivity::class.java).putExtra(EXTRA_KEY, keyIndex))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_NOTE, selectedNote)
    }

    /** Shows every chord that contains scale note [index], and highlights those cards. */
    private fun selectNote(index: Int) {
        selectedNote = index
        val note = key.scale[index]
        val pc = note.pitchClass

        notePills.forEachIndexed { i, pill ->
            val selected = i == index
            pill.setBackgroundResource(if (selected) R.drawable.bg_pill_selected else R.drawable.bg_pill)
            pill.setTextColor(getColor(if (selected) R.color.black else R.color.text_primary))
        }

        val accent = getColor(R.color.accent)
        key.chords.forEachIndexed { i, chord ->
            val card = chordCards[i]
            val has = chord.contains(pc)
            card.strokeColor = if (has) accent else getColor(R.color.surface_high)
            card.strokeWidth = dp(if (has) 2 else 1)
            card.alpha = if (has) 1f else 0.55f
        }

        val panel = findViewById<LinearLayout>(R.id.notePanel)
        panel.removeAllViews()
        panel.addView(TextView(this).apply {
            text = getString(R.string.chords_with_note, note.name)
            textSize = 20f
            setTextColor(getColor(R.color.text_primary))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        panel.addView(sectionLabel(getString(R.string.in_family, key.label)))
        for (chord in key.chordsContaining(pc)) panel.addView(chordRow(chord, pc, showRoman = true))

        panel.addView(sectionLabel(getString(R.string.outside_family, note.name)))
        for (chord in key.otherChordsContaining(pc)) panel.addView(chordRow(chord, pc, showRoman = false))
    }

    private fun sectionLabel(text: String) = TextView(this).apply {
        this.text = text
        setTextAppearance(R.style.SectionLabel)
        setPadding(0, dp(14), 0, dp(4))
    }

    /** One line: "Am   A – C – E   vi · Minor", with the selected note in bold. Tap to hear. */
    private fun chordRow(chord: Chord, pc: Int, showRoman: Boolean): View {
        val color = getColor(if (chord.quality == Quality.MAJOR) R.color.major else R.color.minor)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
            isClickable = true
            setBackgroundResource(android.R.drawable.list_selector_background)
            setOnClickListener { SoundEngine.playChord(chord.midiNotes()) }
        }
        row.addView(TextView(this).apply {
            text = chord.name
            textSize = 18f
            setTextColor(color)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(dp(64), -2)
        })
        val notes = android.text.SpannableStringBuilder()
        chord.notes.forEachIndexed { i, n ->
            if (i > 0) notes.append(" – ")
            val start = notes.length
            notes.append(n.name)
            if (n.pitchClass == pc) {
                notes.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start, notes.length, 0)
                notes.setSpan(android.text.style.ForegroundColorSpan(getColor(R.color.accent)), start, notes.length, 0)
            }
        }
        row.addView(TextView(this).apply {
            text = notes
            textSize = 16f
            setTextColor(getColor(R.color.text_primary))
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        })
        row.addView(TextView(this).apply {
            text = if (showRoman) "${chord.roman} · ${chord.quality.label}" else chord.quality.label
            textSize = 12f
            setTextColor(getColor(R.color.text_secondary))
        })
        return row
    }

    override fun onStart() {
        super.onStart()
        SoundEngine.attach()
    }

    override fun onStop() {
        super.onStop()
        SoundEngine.detach()
    }

    private fun chordCard(chord: Chord): MaterialCardView {
        val isMajor = chord.quality == Quality.MAJOR
        val color = getColor(if (isMajor) R.color.major else R.color.minor)
        val card = MaterialCardView(this).apply {
            radius = dp(16f)
            setCardBackgroundColor(getColor(R.color.surface))
            strokeColor = getColor(R.color.surface_high)
            strokeWidth = dp(1)
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }

        // Header: roman numeral badge, chord name, quality
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = chord.roman
            textSize = 14f
            setTextColor(color)
            gravity = Gravity.CENTER
            setBackgroundResource(if (isMajor) R.drawable.bg_badge_major else R.drawable.bg_badge_minor)
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(32))
        })
        header.addView(TextView(this).apply {
            text = chord.name
            textSize = 26f
            setTextColor(getColor(R.color.text_primary))
            setPadding(dp(12), 0, dp(8), 0)
        })
        header.addView(TextView(this).apply {
            text = chord.quality.label
            textSize = 13f
            setTextColor(getColor(R.color.text_secondary))
        })
        column.addView(header)

        // The notes in the chord, big and clear
        column.addView(TextView(this).apply {
            text = chord.notesText
            textSize = 22f
            setTextColor(color)
            setPadding(0, dp(10), 0, dp(2))
        })
        // What each note is
        column.addView(TextView(this).apply {
            text = chord.notes.zip(chord.quality.intervalNames)
                .joinToString("   ·   ") { (n, label) -> "${n.name} = $label" }
            textSize = 12f
            setTextColor(getColor(R.color.text_secondary))
            setPadding(0, 0, 0, dp(10))
        })

        val piano = PianoView(this).apply {
            highlightColor = color
            val r = chord.root.pitchClass
            highlighted = chord.quality.intervals.map { r + it }.toSet()
        }
        column.addView(piano, LinearLayout.LayoutParams(-1, -2))

        card.addView(column)
        card.setOnClickListener { SoundEngine.playChord(chord.midiNotes()) }
        return card
    }
}
