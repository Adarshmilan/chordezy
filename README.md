# Chordsezy 🎹

An Android app for learning chords. Pick a key, see the chords in its family and the notes inside each one, then build a chord progression and loop it at your own tempo.

## Features

### 1. Pick a chord family
A grid of all 12 keys: C, D♭, D, E♭, E, F, F♯, G, A♭, A, B♭, B. Tap one to open its chord family.

### 2. Learn the chords
- The key's **major scale**, with each note numbered 1–7.
- The **6 chords** of the family in scale order: **I, ii, iii, IV, V, vi** (3 major, 3 minor). Diminished chords are left out for now.
- Every chord shows:
  - its name and Roman numeral
  - **the notes in the chord** (e.g. G – B – D)
  - what each note is: Root, Major/Minor 3rd, Perfect 5th
  - a mini piano with the chord's keys lit up
- Notes are spelled correctly for each key (F major has B♭, not A♯).
- **Tap a note** in the scale to see every chord that contains it, both in this family and outside it. Example for C: C, F, Am, plus A♭, Cm, Fm.
- Tap any chord to hear it.

| Key | I | ii | iii | IV | V | vi |
|-----|---|----|-----|----|---|----|
| C   | C | Dm | Em  | F  | G | Am |
| G   | G | Am | Bm  | C  | D | Em |
| F   | F | Gm | Am  | B♭ | C | Dm |

### 3. Build a loop
- **Tempo:** 40–220 BPM (slider or −/+ buttons)
- **Time signature:** 2/4, 3/4, 4/4, 5/4, 6/8
- **Bars:** 1–8
- **Metronome** click on/off. Beat 1 of each bar clicks louder.
- Pick a chord, then tap beats in the grid to place it. A chord keeps ringing until the next one.
- **I–V–vi–IV** button fills in a common pop progression in one tap.
- **Play** repeats the loop. The current beat is highlighted and the bottom bar shows the chord and its notes.
- You can change the tempo or chords while the loop is playing.
- The loop stores chord positions (I, IV, V…) rather than chord names, so in another key the same progression plays transposed.

The sound is generated live inside the app (a small built-in synthesizer), so there are no audio files and no extra libraries.

## Getting started

**Requirements**
- Android Studio (recent version, with AGP 9.x support)
- Android phone or emulator running Android 7.0 (API 24) or newer

**Run it**
1. Clone the repo:
   ```bash
   git clone https://github.com/Adarshmilan/chordezy.git
   ```
2. Open the folder in Android Studio and let Gradle sync.
3. Connect a phone (with USB debugging on) or start an emulator.
4. Press **Run ▶**.

**Run the tests**
```bash
./gradlew testDebugUnitTest
```

## Project structure

```
app/src/main/java/com/example/chordsezy/
├── MainActivity.kt        # Screen 1: pick a chord family (key)
├── ChordsActivity.kt      # Screen 2: the 6 chords, their notes, chords per note
├── LooperActivity.kt      # Screen 3: tempo, time signature, chord grid, play loop
├── LoopState.kt           # The loop being built (tempo, bars, chords on each beat)
├── PianoView.kt           # Mini keyboard that highlights chord notes
├── Ui.kt                  # Small helpers (dp, system-bar padding)
├── music/Theory.kt        # Notes, scales, chords, correct note spelling
└── audio/SoundEngine.kt   # Real-time synth + sample-accurate loop sequencer

app/src/main/res/
├── layout/                # Screen layouts
├── drawable/              # Logo and shapes
└── values/                # Colors, theme, strings
```

**Built with:** Kotlin, Android Views (XML layouts), Material 3 components, and `AudioTrack` for sound.

## Ideas for later
- Diminished chords (vii°)
- 7th chords
- Save and load loops
- Guitar chord diagrams
