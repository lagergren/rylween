"""
"Title": the game's main theme (the night tune's melody) stated grandly.
D dorian, 3/4 at 76 BPM.

Form (28 bars, ~66 s, loops back to the intro):
  intro  2 bars  timpani roll and choir swell over harp
  A      8 bars  horn states the theme over harp, strings and bowed bass
  B      8 bars  flute takes the F-major lift, horn answers below, choir joins
  A'     8 bars  tutti: horn with flute an octave above, choir, timpani
  coda   2 bars  bell and a fading timpani roll lead back to the intro

Usage: title.py OUT_DIR
"""

import sys
from pathlib import Path

import numpy as np

import music_instruments as mi
import synth
from score import Arrangement, chord_tone_below, play_sustained, render
from synth import note
from wilderness_night import CHORDS, MELODY_A, MELODY_B

BPM = 76
EIGHTH = 60.0 / BPM / 2
BAR = 6 * EIGHTH
A_CHORDS = ["Dm", "G", "Dm", "C", "Dm", "G", "Am", "Dm"]
B_CHORDS = ["F", "C", "G", "Dm", "F", "C", "G", "A"]
HARP_PATTERN = [0, 1, 2, 3, 2, 1]
HARP_ACCENTS = [0.85, 0.55, 0.65, 0.6, 0.55, 0.5]


def events(melody: list[tuple[str, int]]) -> list[tuple[int, int, int]]:
    out, unit = [], 0
    for name, length in melody:
        out.append((note(name), unit, length))
        unit += length
    return out


def pitch_classes(chord: str) -> tuple[int, ...]:
    return tuple(note(n) % 12 for n in CHORDS[chord][0].split())


def main(out_dir: Path) -> None:
    rng = np.random.default_rng(76)
    sections = [("intro", ["Dm", "Dm"]), ("A", A_CHORDS), ("B", B_CHORDS), ("A'", A_CHORDS), ("coda", ["Dm", "Dm"])]
    total = sum(len(chords) for _, chords in sections)
    arrangement = Arrangement(total * BAR, tail=6.0)
    melody_a, melody_b = events(MELODY_A), events(MELODY_B)

    def roll(start: float, seconds: float, midi: int, rising: bool) -> None:
        hits = int(seconds / 0.085)
        for i in range(hits):
            level = (i + 1) / hits if rising else 1.0 - i / hits
            velocity = 0.25 + 0.6 * level
            arrangement.add("timpani", start + i * 0.085 + rng.uniform(-0.006, 0.006), mi.timpani(midi, velocity, rng), 0.12 * (0.3 + level), reverb=0.35)

    bar_index = 0
    for name, chords in sections:
        start = bar_index * BAR
        for i, chord in enumerate(chords):
            bar_start = start + i * BAR
            voicing, bass_note, pad_notes = CHORDS[chord]
            harp_notes = [note(n) for n in voicing.split()]
            for pos, (index, accent) in enumerate(zip(HARP_PATTERN, HARP_ACCENTS)):
                velocity = float(np.clip(accent + rng.uniform(-0.05, 0.05), 0.2, 1.0))
                arrangement.add("harp", bar_start + pos * EIGHTH + rng.uniform(-0.006, 0.006), synth.harp(harp_notes[index], velocity, rng), 0.7, pan=-0.3, reverb=0.4)
            arrangement.add("bass", bar_start, mi.bowed(note(bass_note), BAR * 0.98, 0.7, rng, attack=0.2, brightness=2.5), 0.22, reverb=0.25)
            if name in ("A", "B", "A'", "coda"):
                left, right = synth.pad([note(n) for n in pad_notes.split()], BAR, rng)
                arrangement.add_stereo("strings", bar_start, left, right, 0.32, reverb=0.45)
            if name in ("B", "A'", "intro", "coda"):
                vowel = "ah" if name in ("B", "intro") else "oh"
                left, right = mi.choir([note(n) - 12 for n in pad_notes.split()], BAR, rng, vowel=vowel)
                arrangement.add_stereo("choir", bar_start, left, right, 0.5 if name != "intro" else 0.35 * (i + 1), reverb=0.5)
            if name == "A'":
                pcs = {note(n) % 12 for n in voicing.split()}
                timp = note("D2") if 2 in pcs else note("A2") if 9 in pcs else None
                if timp:
                    arrangement.add("timpani", bar_start, mi.timpani(timp, 0.8, rng), 0.3, reverb=0.35)
        if name == "intro":
            roll(start, 2 * BAR - 0.1, note("D2"), rising=True)
        elif name == "coda":
            arrangement.add("bell", start, mi.bell(note("D4"), 0.8, rng, kind="church"), 0.16, pan=0.1, reverb=0.5)
            arrangement.add("bell", start + BAR, mi.bell(note("A3"), 0.6, rng, kind="church"), 0.12, pan=-0.1, reverb=0.5)
            roll(start, 2 * BAR - 0.3, note("D2"), rising=False)
        elif name == "A":
            play_sustained(arrangement, "horn", melody_a, start, EIGHTH, mi.horn, rng, 0.46, pan=0.1, reverb=0.4, velocity=0.75, octave=-1)
        elif name == "B":
            play_sustained(arrangement, "flute", melody_b, start, EIGHTH, synth.flute, rng, 0.3, pan=0.2, reverb=0.4, velocity=0.9)
            answer = []
            for bar, chord in enumerate(chords):
                first = next(m for m, at, _ in melody_b if at == bar * 6)
                answer.append((chord_tone_below(first - 12, pitch_classes(chord)), bar * 6, 6))
            play_sustained(arrangement, "horn", answer, start, EIGHTH, mi.horn, rng, 0.24, pan=-0.15, reverb=0.45, velocity=0.6)
        elif name == "A'":
            play_sustained(arrangement, "horn", melody_a, start, EIGHTH, mi.horn, rng, 0.46, pan=0.1, reverb=0.4, velocity=0.85, octave=-1)
            play_sustained(arrangement, "flute", melody_a, start, EIGHTH, synth.flute, rng, 0.2, pan=0.25, reverb=0.4, velocity=0.85)
        bar_index += len(chords)

    render(arrangement, "title", out_dir, rng, rt60=2.4, ir_length=3.4, wet=0.55, notes="D dorian, 3/4, 76 BPM")


if __name__ == "__main__":
    main(Path(sys.argv[1]))
