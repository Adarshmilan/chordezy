package com.example.chordsezy.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * What the looper should play.
 * [steps] has one entry per beat; an entry is the MIDI notes of a chord that starts on
 * that beat, or null if the previous chord keeps ringing.
 */
data class LoopSequence(
    val bpm: Int,
    val beatsPerBar: Int,
    val steps: List<List<Int>?>,
    val metronome: Boolean,
)

/**
 * Small synthesizer built on AudioTrack. Generates chord tones and metronome clicks
 * in real time, so beat timing is sample-accurate.
 */
object SoundEngine {
    private const val SR = 44100
    private const val BUFFER_FRAMES = 512
    private const val TABLE_SIZE = 4096

    private val sineTable = FloatArray(TABLE_SIZE) { sin(2.0 * PI * it / TABLE_SIZE).toFloat() }
    private val mainHandler = Handler(Looper.getMainLooper())

    private class Voice(
        freq: Double,
        val gain: Float,
        val sustain: Float,
        decaySeconds: Double,
        val isChord: Boolean,
        val partials: FloatArray,
    ) {
        val inc = freq * TABLE_SIZE / SR
        var phase = 0.0
        var env = 0f
        var attacking = true
        var released = false
        val decayCoef = exp(-1.0 / (decaySeconds * SR)).toFloat()
        val attackStep = 1f / (0.004f * SR)
        val releaseCoef = exp(-1.0 / (0.06 * SR)).toFloat()

        fun next(table: FloatArray): Float {
            if (attacking) {
                env += attackStep
                if (env >= 1f) { env = 1f; attacking = false }
            } else if (released) {
                env *= releaseCoef
            } else {
                env = sustain + (env - sustain) * decayCoef
            }
            var s = 0f
            for (h in partials.indices) {
                val p = ((phase * (h + 1)) % TABLE_SIZE).toInt()
                s += table[p] * partials[h]
            }
            phase += inc
            if (phase >= TABLE_SIZE * 64.0) phase -= TABLE_SIZE * 64.0
            return s * env * gain
        }

        val done: Boolean get() = !attacking && env < 0.0005f
    }

    private val commands = ConcurrentLinkedQueue<() -> Unit>()
    private val voices = ArrayList<Voice>()

    @Volatile private var running = false
    private var thread: Thread? = null
    private var track: AudioTrack? = null
    private var latencyMs = 0L

    // Sequencer state (touched only on the audio thread).
    private var sequence: LoopSequence? = null
    private var stepIndex = 0
    private var framesUntilBeat = 0L

    /** Called on the main thread with the beat index that is now sounding. -1 when stopped. */
    var onBeat: ((Int) -> Unit)? = null

    val isLooping: Boolean get() = loopingFlag
    @Volatile private var loopingFlag = false

    fun start() {
        if (running) return
        val minBuf = AudioTrack.getMinBufferSize(
            SR, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val bufBytes = maxOf(minBuf, BUFFER_FRAMES * 2 * 4)
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SR)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        latencyMs = (bufBytes / 2) * 1000L / SR
        track = t
        t.play()
        running = true
        thread = Thread({ renderLoop(t) }, "SoundEngine").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    private var users = 0

    /** Call from an Activity's onStart. */
    fun attach() {
        users++
    }

    /** Call from an Activity's onStop. Audio shuts down once no screen is visible. */
    fun detach() {
        users = (users - 1).coerceAtLeast(0)
        if (users == 0) shutdown()
    }

    fun shutdown() {
        stopLoop()
        running = false
        thread?.join(500)
        thread = null
        track?.let {
            try { it.stop() } catch (_: IllegalStateException) {}
            it.release()
        }
        track = null
    }

    /** Play a chord once (preview). */
    fun playChord(midiNotes: List<Int>) {
        start()
        commands.add {
            releaseChordVoices()
            addChordVoices(midiNotes, sustain = 0f, decay = 0.9)
        }
    }

    fun startLoop(seq: LoopSequence) {
        start()
        loopingFlag = true
        commands.add {
            releaseChordVoices()
            sequence = seq
            stepIndex = 0
            framesUntilBeat = 0
        }
    }

    /** Swap in changes (tempo, chords) while playing, without restarting from beat 1. */
    fun updateLoop(seq: LoopSequence) {
        if (!loopingFlag) return
        commands.add {
            val old = sequence
            sequence = seq
            if (old == null || stepIndex >= seq.steps.size) {
                stepIndex = 0
                framesUntilBeat = 0
            }
        }
    }

    fun stopLoop() {
        loopingFlag = false
        commands.add {
            sequence = null
            releaseChordVoices()
        }
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.post { onBeat?.invoke(-1) }
    }

    // ---------------- audio thread ----------------

    private fun renderLoop(t: AudioTrack) {
        val out = ShortArray(BUFFER_FRAMES)
        while (running) {
            while (true) {
                val c = commands.poll() ?: break
                c()
            }
            for (i in 0 until BUFFER_FRAMES) {
                tickSequencer()
                var mix = 0f
                val it = voices.iterator()
                while (it.hasNext()) {
                    val v = it.next()
                    mix += v.next(sineTable)
                    if (v.done) it.remove()
                }
                // soft clip
                val x = mix * 0.8f
                val y = x / (1f + kotlin.math.abs(x))
                out[i] = (y * 32000f).toInt().toShort()
            }
            t.write(out, 0, out.size)
        }
        voices.clear()
    }

    private fun tickSequencer() {
        val seq = sequence ?: return
        if (seq.steps.isEmpty()) return
        if (framesUntilBeat <= 0) {
            val step = stepIndex
            val notes = seq.steps[step]
            if (notes != null) {
                releaseChordVoices()
                addChordVoices(notes, sustain = 0.35f, decay = 0.7)
            }
            if (seq.metronome) {
                val accent = step % seq.beatsPerBar == 0
                voices.add(
                    Voice(
                        if (accent) 1760.0 else 1175.0,
                        if (accent) 0.45f else 0.3f,
                        0f, 0.012, false, floatArrayOf(1f)
                    )
                )
            }
            val beat = step
            mainHandler.postDelayed({ if (loopingFlag) onBeat?.invoke(beat) }, latencyMs)
            framesUntilBeat += (SR * 60L) / seq.bpm.coerceAtLeast(20)
            stepIndex = (stepIndex + 1) % seq.steps.size
        }
        framesUntilBeat--
    }

    private fun releaseChordVoices() {
        for (v in voices) if (v.isChord) v.released = true
    }

    private val chordPartials = floatArrayOf(1f, 0.45f, 0.2f, 0.1f)

    private fun addChordVoices(midiNotes: List<Int>, sustain: Float, decay: Double) {
        val gain = 0.55f / midiNotes.size.coerceAtLeast(1)
        for (m in midiNotes) {
            voices.add(Voice(midiToFreq(m), gain, sustain, decay, true, chordPartials))
        }
    }

    private fun midiToFreq(m: Int): Double = 440.0 * 2.0.pow((m - 69) / 12.0)
}
