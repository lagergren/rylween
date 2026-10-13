"""
"Battle": driving but not grating, A minor, 4/4 at 138 BPM.

Staccato strings run a root-fifth-octave ostinato under tom drums and a
snare-like frame drum on 2 and 4. The horn states a fighting theme; the
flute answers with a quicker running motif while the horn holds chord
tones beneath it. Shaker and drums stay soft so the loop wears well over
many fights.

Form (28 bars, ~49 s, loops back to the intro):
  intro   2 bars  drums and ostinato
  A       8 bars  horn theme
  A'      8 bars  horn theme with harp doubling, timpani
  B       8 bars  flute motif over held horn tones and strings pad
  break   2 bars  drums, ostinato and timpani

Usage: battle.py OUT_DIR
"""

import sys
from pathlib import Path

import numpy as np

import music_instruments as mi
import synth
from score import Arrangement, chord_tone_below, parse, play_plucked, play_sustained, render
from synth import note

BPM = 138
EIGHTH = 60.0 / BPM / 2
BAR = 8 * EIGHTH

HORN = parse(
    "A4:3 E4:1 A4:2 B4:2 | C5:3 B4:1 A4:2 F4:2 | G4:3 A4:1 B4:2 D5:2 | C5:2 B4:2 A4:4 | "
    "A4:3 E4:1 A4:2 C5:2 | F5:3 E5:1 C5:2 A4:2 | B4:2 D5:2 G5:2 F5:1 D5:1 | E5:6 G#4:2",
    8,
)
FLUTE = parse(
    "D5:1 E5:1 F5:1 A5:1 D6:2 A5:2 | C6:1 B5:1 A5:1 E5:1 A5:2 C6:2 | A5:1 G5:1 F5:1 C5:1 F5:2 A5:2 | "
    "G#5:2 B5:2 E6:2 D6:1 B5:1 | D6:2 A5:1 F5:1 D5:2 F5:2 | E5:2 A5:1 C6:1 E6:2 C6:2 | "
    "F5:1 A5:1 C6:1 A5:1 G5:1 B5:1 D6:1 B5:1 | E6:4 B5:2 G#5:2",
    8,
)
A_CHORDS = ["Am", "F", "G", "Am", "Am", "F", "G", "E"]
B_CHORDS = ["Dm", "Am", "F", "E", "Dm", "Am", "F G", "E"]
CHORDS = {
    # ostinato root, chord pitch classes, pad
    "Am": ("A2", (9, 0, 4), "A3 C4 E4"),
    "F": ("F2", (5, 9, 0), "A3 C4 F4"),
    "G": ("G2", (7, 11, 2), "G3 B3 D4"),
    "E": ("E2", (4, 8, 11), "G#3 B3 E4"),
    "Dm": ("D2", (2, 5, 9), "A3 D4 F4"),
}
OSTINATO = [0, 0, 7, 0, 12, 0, 7, 0]


def main(out_dir: Path) -> None:
    rng = np.random.default_rng(138)
    sections = [
        dict(chords=["Am", "Am"]),
        dict(chords=A_CHORDS, horn=True),
        dict(chords=A_CHORDS, horn=True, harp=True, timpani=True),
        dict(chords=B_CHORDS, flute=True, pad=True),
        dict(chords=["Am", "E"], timpani=True),
    ]
    total = sum(len(s["chords"]) for s in sections)
    arrangement = Arrangement(total * BAR)
    tom = synth.drum(140.0, 75.0, 0.16, 0.8, rng)
    snare = synth.drum(320.0, 230.0, 0.07, 2.2, rng)
    drums = [(0, tom, 0.42), (2, snare, 0.25), (3, tom, 0.25), (4, tom, 0.35), (6, snare, 0.27), (7, snare, 0.09)]

    bar_index = 0
    for section in sections:
        start = bar_index * BAR
        for i, chord_name in enumerate(section["chords"]):
            bar_start = start + i * BAR
            names = chord_name.split()
            for unit, interval in enumerate(OSTINATO):
                root = CHORDS[names[min(unit // 4, len(names) - 1)]][0]
                velocity = float(np.clip((0.9 if unit % 2 == 0 else 0.7) + rng.uniform(-0.05, 0.05), 0.2, 1.0))
                arrangement.add("ostinato", bar_start + unit * EIGHTH + rng.uniform(-0.003, 0.003),
                                mi.staccato_strings(note(root) + interval, velocity, rng, length=0.12), 0.85, pan=-0.2, reverb=0.25)
            for unit, sound, level in drums:
                arrangement.add("drums", bar_start + unit * EIGHTH + rng.uniform(-0.003, 0.003), sound, level, reverb=0.2)
            for unit in range(8):
                arrangement.add("shaker", bar_start + unit * EIGHTH, synth.shaker(rng), 0.08 if unit % 2 == 0 else 0.05, pan=0.4, reverb=0.15)
            if section.get("timpani") and (i % 2 == 0 or len(section["chords"]) == 2):
                pcs = CHORDS[names[0]][1]
                timp = note("A2") if 9 in pcs else note("E2")
                arrangement.add("timpani", bar_start, mi.timpani(timp, 0.85, rng), 0.42, reverb=0.3)
            if section.get("pad"):
                for k, name in enumerate(names):
                    seconds = BAR / len(names)
                    left, right = synth.pad([note(n) for n in CHORDS[name][2].split()], seconds, rng)
                    arrangement.add_stereo("pad", bar_start + k * seconds, left, right, 0.22, reverb=0.35)
        if section.get("horn"):
            play_sustained(arrangement, "horn", HORN, start, EIGHTH, mi.horn, rng, 0.42, pan=0.1, reverb=0.3, velocity=0.85)
        if section.get("harp"):
            play_plucked(arrangement, "harp", HORN, start, EIGHTH, synth.harp, rng, 1.0, pan=-0.35, reverb=0.3, velocity=0.7, octave=1)
        if section.get("flute"):
            play_sustained(arrangement, "flute", FLUTE, start, EIGHTH, synth.flute, rng, 0.3, pan=0.2, reverb=0.3, velocity=0.9, legato=0.025)
            held = []
            for bar, chord_name in enumerate(section["chords"]):
                for half, name in enumerate(chord_name.split() if " " in chord_name else [chord_name, chord_name]):
                    at = bar * 8 + half * 4
                    above = next(m for m, s, n in FLUTE if s <= at < s + n)
                    held.append((chord_tone_below(above - 12, CHORDS[name][1]), at, 4))
            play_sustained(arrangement, "horn", held, start, EIGHTH, mi.horn, rng, 0.22, pan=-0.1, reverb=0.35, velocity=0.6)
        bar_index += len(section["chords"])

    render(arrangement, "battle", out_dir, rng, rt60=1.5, wet=0.35, notes="A minor, 4/4, 138 BPM")


if __name__ == "__main__":
    main(Path(sys.argv[1]))
