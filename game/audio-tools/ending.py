"""
"Ending": the main theme, slow and triumphant, moved from D dorian into D major.
3/4 at 66 BPM.

The night tune's melody returns with F and C raised to F# and C#, so the
same line now resolves in a bright major key. Horn and flute carry it over
harp, strings and choir; bells and timpani crown the final cadence.

Form (28 bars, ~76 s, loops back to the intro):
  intro  2 bars  harp and strings, choir swells in
  A      8 bars  horn states the theme in major
  B      8 bars  flute takes the lift, choir and horn below
  A'     8 bars  tutti with flute an octave above the horn, timpani
  coda   2 bars  held D major, church bells, a soft timpani roll

Usage: ending.py OUT_DIR
"""

import sys
from pathlib import Path

import numpy as np

import music_instruments as mi
import synth
from score import Arrangement, chord_tone_below, map_pitch_classes, play_sustained, render
from synth import note
from wilderness_night import MELODY_A, MELODY_B

BPM = 66
EIGHTH = 60.0 / BPM / 2
BAR = 6 * EIGHTH
TO_MAJOR = {5: 1, 0: 1}  # F -> F#, C -> C#

CHORDS = {
    # harp voicing, bass, pad
    "D": ("D3 A3 D4 F#4", "D2", "D4 F#4 A4"),
    "G": ("G2 D3 G3 B3", "G2", "D4 G4 B4"),
    "A7": ("A2 E3 G3 C#4", "A2", "C#4 E4 G4"),
    "A": ("A2 E3 A3 C#4", "A2", "C#4 E4 A4"),
    "F#m": ("F#2 C#3 F#3 A3", "F#2", "C#4 F#4 A4"),
    "Em": ("E3 B3 E4 G4", "E2", "E4 G4 B4"),
}
A_CHORDS = ["D", "G", "D", "A7", "D", "G", "A", "D"]
B_CHORDS = ["F#m", "Em", "G", "D", "F#m", "A7", "G", "A"]
HARP_PATTERN = [0, 1, 2, 3, 2, 1]
HARP_ACCENTS = [0.85, 0.55, 0.65, 0.6, 0.55, 0.5]


def events(melody: list[tuple[str, int]]) -> list[tuple[int, int, int]]:
    out, unit = [], 0
    for name, length in melody:
        out.append((note(name), unit, length))
        unit += length
    return map_pitch_classes(out, TO_MAJOR)


def main(out_dir: Path) -> None:
    rng = np.random.default_rng(1984)
    sections = [("intro", ["D", "D"]), ("A", A_CHORDS), ("B", B_CHORDS), ("A'", A_CHORDS), ("coda", ["D", "D"])]
    total = sum(len(chords) for _, chords in sections)
    arrangement = Arrangement(total * BAR, tail=8.0)
    melody_a, melody_b = events(MELODY_A), events(MELODY_B)

    bar_index = 0
    for name, chords in sections:
        start = bar_index * BAR
        for i, chord in enumerate(chords):
            bar_start = start + i * BAR
            voicing, bass, pad = CHORDS[chord]
            harp_notes = [note(n) for n in voicing.split()]
            for pos, (index, accent) in enumerate(zip(HARP_PATTERN, HARP_ACCENTS)):
                velocity = float(np.clip(accent + rng.uniform(-0.05, 0.05), 0.2, 1.0))
                arrangement.add("harp", bar_start + pos * EIGHTH + rng.uniform(-0.006, 0.006), synth.harp(harp_notes[index], velocity, rng), 0.7, pan=-0.3, reverb=0.45)
            arrangement.add("bass", bar_start, mi.bowed(note(bass), BAR * 0.98, 0.7, rng, attack=0.25, brightness=2.5), 0.22, reverb=0.3)
            left, right = synth.pad([note(n) for n in pad.split()], BAR, rng)
            arrangement.add_stereo("strings", bar_start, left, right, 0.3, reverb=0.5)
            if name != "A":
                level = 0.5 if name != "intro" else 0.3 * (i + 1)
                left, right = mi.choir([note(n) - 12 for n in pad.split()], BAR, rng, vowel="ah")
                arrangement.add_stereo("choir", bar_start, left, right, level, reverb=0.55)
            if name == "A'" and i % 2 == 0:
                timp = note("D2") if chord in ("D", "G") else note("A2")
                arrangement.add("timpani", bar_start, mi.timpani(timp, 0.75, rng), 0.36, reverb=0.4)
        if name == "A":
            play_sustained(arrangement, "horn", melody_a, start, EIGHTH, mi.horn, rng, 0.46, pan=0.1, reverb=0.45, velocity=0.75, octave=-1)
        elif name == "B":
            play_sustained(arrangement, "flute", melody_b, start, EIGHTH, synth.flute, rng, 0.3, pan=0.2, reverb=0.45, velocity=0.9)
            answer = []
            for bar, chord in enumerate(chords):
                first = next(m for m, at, _ in melody_b if at == bar * 6)
                pcs = tuple(note(n) % 12 for n in CHORDS[chord][0].split())
                answer.append((chord_tone_below(first - 12, pcs), bar * 6, 6))
            play_sustained(arrangement, "horn", answer, start, EIGHTH, mi.horn, rng, 0.24, pan=-0.15, reverb=0.5, velocity=0.6)
        elif name == "A'":
            play_sustained(arrangement, "horn", melody_a, start, EIGHTH, mi.horn, rng, 0.46, pan=0.1, reverb=0.45, velocity=0.85, octave=-1)
            play_sustained(arrangement, "flute", melody_a, start, EIGHTH, synth.flute, rng, 0.2, pan=0.25, reverb=0.45, velocity=0.85)
        elif name == "coda":
            for k, (pitch, level) in enumerate((("D4", 0.8), ("A4", 0.6), ("F#4", 0.6), ("D5", 0.5))):
                arrangement.add("bells", start + k * 1.5 * EIGHTH * 2, mi.bell(note(pitch), level, rng, kind="church"), 0.22, pan=0.3 - 0.2 * k, reverb=0.6)
            hits = int((2 * BAR - 0.4) / 0.09)
            for k in range(hits):
                level = 1.0 - k / hits
                arrangement.add("timpani", start + k * 0.09, mi.timpani(note("D2"), 0.25 + 0.4 * level, rng), 0.08 * (0.3 + level), reverb=0.4)
        bar_index += len(chords)

    render(arrangement, "ending", out_dir, rng, rt60=2.4, ir_length=3.4, wet=0.55, notes="D major, 3/4, 66 BPM")


if __name__ == "__main__":
    main(Path(sys.argv[1]))
