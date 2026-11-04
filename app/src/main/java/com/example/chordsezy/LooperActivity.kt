package com.example.chordsezy

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.chordsezy.audio.SoundEngine
import com.example.chordsezy.music.Key
import com.example.chordsezy.music.Quality
import com.example.chordsezy.music.TimeSignature
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider

/**
 * Screen 3: set tempo and time signature, place chords on beats, and loop it.
 */
class LooperActivity : AppCompatActivity() {

    private lateinit var key: Key
    private lateinit var tempoValue: TextView
    private lateinit var tempoSlider: Slider
    private lateinit var barsValue: TextView
    private lateinit var barsContainer: LinearLayout
    private lateinit var playButton: MaterialButton
    private lateinit var nowPlaying: TextView

    private var cells: List<TextView> = emptyList()
    private var brush = 0          // chord index 0..5, or LoopState.EMPTY to erase
    private var playing = false
    private var currentStep = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_looper)
        applySystemBarPadding(findViewById(R.id.root))

        key = Key.byIndex(intent.getIntExtra(ChordsActivity.EXTRA_KEY, 0))
        findViewById<TextView>(R.id.title).text = getString(R.string.looper_title, key.label)

        tempoValue = findViewById(R.id.tempoValue)
        tempoSlider = findViewById(R.id.tempoSlider)
        barsValue = findViewById(R.id.barsValue)
        barsContainer = findViewById(R.id.barsContainer)
        playButton = findViewById(R.id.playButton)
        nowPlaying = findViewById(R.id.nowPlaying)

        setupTempo()
        setupTimeSignatures()
        setupBars()
        setupPalette()

        findViewById<MaterialSwitch>(R.id.metronomeSwitch).apply {
            isChecked = LoopState.metronome
            setOnCheckedChangeListener { _, checked ->
                LoopState.metronome = checked
                pushUpdate()
            }
        }
        findViewById<MaterialButton>(R.id.presetButton).setOnClickListener {
            // I – V – vi – IV : indexes into key.chords (I, ii, iii, IV, V, vi)
            LoopState.fillPattern(intArrayOf(0, 4, 5, 3))
            renderCells()
            pushUpdate()
        }
        findViewById<MaterialButton>(R.id.clearButton).setOnClickListener {
            LoopState.clear()
            renderCells()
            pushUpdate()
        }
        playButton.setOnClickListener { if (playing) stop() else play() }

        buildGrid()
        updateNowPlaying()
    }

    override fun onStart() {
        super.onStart()
        SoundEngine.attach()
        SoundEngine.onBeat = { step -> onBeat(step) }
    }

    override fun onStop() {
        super.onStop()
        stop()
        SoundEngine.onBeat = null
        SoundEngine.detach()
    }

    // ---------------- setup ----------------

    private fun setupTempo() {
        tempoSlider.value = LoopState.bpm.toFloat()
        tempoSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) setTempo(value.toInt())
        }
        findViewById<MaterialButton>(R.id.tempoDown).setOnClickListener { setTempo(LoopState.bpm - 1) }
        findViewById<MaterialButton>(R.id.tempoUp).setOnClickListener { setTempo(LoopState.bpm + 1) }
        tempoValue.text = getString(R.string.bpm_value, LoopState.bpm)
    }

    private fun setTempo(bpm: Int) {
        LoopState.bpm = bpm.coerceIn(LoopState.MIN_BPM, LoopState.MAX_BPM)
        tempoValue.text = getString(R.string.bpm_value, LoopState.bpm)
        if (tempoSlider.value.toInt() != LoopState.bpm) tempoSlider.value = LoopState.bpm.toFloat()
        pushUpdate()
    }

    private fun setupTimeSignatures() {
        val group = findViewById<ChipGroup>(R.id.tsGroup)
        for (ts in TimeSignature.ALL) {
            val chip = makeChip(ts.label, getColor(R.color.accent), Color.BLACK)
            group.addView(chip)
            if (ts == LoopState.timeSignature) chip.isChecked = true
            chip.setOnCheckedChangeListener { _, checked ->
                if (checked && ts != LoopState.timeSignature) {
                    LoopState.changeTimeSignature(ts)
                    restructure()
                }
            }
        }
    }

    private fun setupBars() {
        barsValue.text = LoopState.bars.toString()
        findViewById<MaterialButton>(R.id.barsDown).setOnClickListener { changeBars(-1) }
        findViewById<MaterialButton>(R.id.barsUp).setOnClickListener { changeBars(+1) }
    }

    private fun changeBars(delta: Int) {
        LoopState.changeBars(LoopState.bars + delta)
        barsValue.text = LoopState.bars.toString()
        restructure()
    }

    private fun setupPalette() {
        val group = findViewById<ChipGroup>(R.id.paletteGroup)
        key.chords.forEachIndexed { i, chord ->
            val color = colorFor(i)
            val chip = makeChip("${chord.name}  (${chord.notesText})", color, Color.BLACK)
            group.addView(chip)
            if (i == brush) chip.isChecked = true
            chip.setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    brush = i
                    if (!playing) SoundEngine.playChord(chord.midiNotes())
                }
            }
        }
        val erase = makeChip(getString(R.string.erase), getColor(R.color.text_secondary), Color.BLACK)
        group.addView(erase)
        erase.setOnCheckedChangeListener { _, checked -> if (checked) brush = LoopState.EMPTY }
    }

    private fun makeChip(label: String, checkedBg: Int, checkedText: Int): Chip = Chip(this).apply {
        text = label
        isCheckable = true
        isCheckedIconVisible = false
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        chipBackgroundColor = ColorStateList(states, intArrayOf(checkedBg, getColor(R.color.surface_high)))
        setTextColor(ColorStateList(states, intArrayOf(checkedText, getColor(R.color.text_primary))))
        chipStrokeWidth = 0f
    }

    // ---------------- grid ----------------

    /** Time signature or bar count changed: rebuild grid and restart the loop if playing. */
    private fun restructure() {
        buildGrid()
        if (playing) {
            currentStep = -1
            SoundEngine.startLoop(LoopState.toSequence(key))
        }
    }

    private fun buildGrid() {
        barsContainer.removeAllViews()
        val list = ArrayList<TextView>()
        val beats = LoopState.beatsPerBar
        for (bar in 0 until LoopState.bars) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) }
            }
            row.addView(TextView(this).apply {
                text = (bar + 1).toString()
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(getColor(R.color.text_secondary))
                layoutParams = LinearLayout.LayoutParams(dp(24), -2)
            })
            for (beat in 0 until beats) {
                val cell = TextView(this).apply {
                    gravity = Gravity.CENTER
                    textSize = if (beats > 5) 13f else 15f
                    maxLines = 1
                    layoutParams = LinearLayout.LayoutParams(0, dp(56), 1f).apply {
                        marginStart = dp(3)
                        marginEnd = dp(3)
                    }
                    setOnClickListener { onCellTapped(bar, beat) }
                    setOnLongClickListener {
                        val c = LoopState.soundingChordAt(bar * beats + beat)
                        if (c != LoopState.EMPTY) SoundEngine.playChord(key.chords[c].midiNotes())
                        true
                    }
                }
                row.addView(cell)
                list.add(cell)
            }
            barsContainer.addView(row)
        }
        cells = list
        renderCells()
    }

    private fun onCellTapped(bar: Int, beat: Int) {
        val current = LoopState.stepAt(bar, beat)
        val next = if (brush == LoopState.EMPTY || current == brush) LoopState.EMPTY else brush
        LoopState.setStep(bar, beat, next)
        if (next != LoopState.EMPTY && !playing) SoundEngine.playChord(key.chords[next].midiNotes())
        renderCells()
        pushUpdate()
    }

    private fun renderCells() {
        val steps = LoopState.steps
        val beats = LoopState.beatsPerBar
        cells.forEachIndexed { i, cell ->
            val v = steps[i]
            val bg = GradientDrawable().apply { cornerRadius = dp(10f) }
            if (v != LoopState.EMPTY) {
                cell.text = key.chords[v].name
                cell.setTextColor(Color.BLACK)
                bg.setColor(colorFor(v))
            } else {
                val held = LoopState.soundingChordAt(i)
                if (held != LoopState.EMPTY) {
                    cell.text = "–"
                    cell.setTextColor(colorFor(held))
                    bg.setColor(dimColorFor(held))
                } else {
                    cell.text = ((i % beats) + 1).toString()
                    cell.setTextColor(getColor(R.color.text_secondary))
                    bg.setColor(getColor(R.color.surface_high))
                }
            }
            if (i == currentStep) {
                bg.setStroke(dp(3), getColor(R.color.playing))
            } else if (i % beats == 0) {
                bg.setStroke(dp(1), getColor(R.color.text_secondary))
            }
            cell.background = bg
        }
    }

    private fun colorFor(chordIndex: Int): Int = getColor(
        if (key.chords[chordIndex].quality == Quality.MAJOR) R.color.major else R.color.minor
    )

    private fun dimColorFor(chordIndex: Int): Int = getColor(
        if (key.chords[chordIndex].quality == Quality.MAJOR) R.color.major_dim else R.color.minor_dim
    )

    // ---------------- playback ----------------

    private fun play() {
        playing = true
        currentStep = -1
        playButton.text = getString(R.string.stop)
        SoundEngine.startLoop(LoopState.toSequence(key))
    }

    private fun stop() {
        if (!playing) return
        playing = false
        playButton.text = getString(R.string.play)
        SoundEngine.stopLoop()
        currentStep = -1
        renderCells()
        updateNowPlaying()
    }

    /** Send edits to the running loop without restarting it. */
    private fun pushUpdate() {
        if (playing) SoundEngine.updateLoop(LoopState.toSequence(key))
    }

    private fun onBeat(step: Int) {
        if (!playing || step >= cells.size) return
        currentStep = step
        renderCells()
        updateNowPlaying()
    }

    private fun updateNowPlaying() {
        val c = if (currentStep >= 0) LoopState.soundingChordAt(currentStep) else LoopState.EMPTY
        nowPlaying.text = if (c != LoopState.EMPTY) {
            val chord = key.chords[c]
            val beats = LoopState.beatsPerBar
            "${getString(R.string.bar_n, currentStep / beats + 1)} · beat ${currentStep % beats + 1}\n" +
                getString(R.string.now_playing, chord.name, chord.notesText)
        } else {
            "${LoopState.timeSignature.label} · ${LoopState.bpm} BPM"
        }
    }
}
