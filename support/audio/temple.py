"""
"Temple": serene and luminous, for the Temple of Thun and the churches.
G major, 4/4 at 54 BPM.

A choir sings a plain chant line over soft organ chords, then turns into a
four-part hymn; the loop closes on a plagal "amen" (C to G) with a church
bell, and a faint glockenspiel shimmer above the final chords.

Form (14 bars, ~62 s, loops back to the intro):
  intro  2 bars  organ, bell
  A      6 bars  choir chant in unison over organ chords
  B      4 bars  hymn: choir chords with the melody on top, organ doubling
  amen   2 bars  C to G, bell and glockenspiel

Usage: temple.py OUT_DIR
"""

import sys
from pathlib import Path

import numpy as np

import music_instruments as mi
from score import Arrangement, parse, play_plucked, render
from synth import note

BPM = 54
QUARTER = 60.0 / BPM
BAR = 4 * QUARTER

CHANT = parse("G4:2 A4:1 B4:1 | C5:2 B4:1 A4:1 | B4:2 G4:2 | E4:1 F#4:1 G4:1 A4:1 | A4:2 F#4:1 D4:1 | G4:4", 4)
CHANT_CHORDS = ["G", "C", "G", "Em", "D", "G"]
ORGAN = {
    "G": "G2 D3 G3 B3", "C": "C3 G3 C4 E4", "Em": "E2 B2 E3 G3", "D": "D3 A3 D4 F#4", "Am": "A2 E3 A3 C4",
}
# Hymn: (chord, choir voicing with the melody on top), two per bar
HYMN = [
    ("G", "G3 B3 D4 D5"), ("C", "C3 G3 E4 C5"),
    ("G", "G3 D4 G4 B4"), ("D", "D3 F#3 D4 A4"),
    ("Em", "E3 B3 E4 G4"), ("D", "D3 A3 F#4 A4"),
    ("G", "G3 D4 G4 B4"), ("G", "G3 D4 G4 B4"),
]
SHIMMER = parse("G5:1 B5:1 D6:1 G6:1 | D6:1 B5:1 G5:1 D5:1", 4)


def main(out_dir: Path) -> None:
    rng = np.random.default_rng(777)
    total_bars = 14
    arrangement = Arrangement(total_bars * BAR, tail=8.0)

    def organ(at: float, chord: str, seconds: float, gain: float = 0.34) -> None:
        left, right = mi.organ([note(n) for n in ORGAN[chord].split()], seconds, rng)
        arrangement.add_stereo("organ", at, left, right, gain, reverb=0.6)

    organ(0.0, "G", BAR)
    organ(BAR, "C", BAR / 2)
    organ(1.5 * BAR, "G", BAR / 2)
    arrangement.add("bell", 0.0, mi.bell(note("G3"), 0.75, rng, kind="church"), 0.22, pan=-0.2, reverb=0.7)

    start = 2 * BAR
    for bar, chord in enumerate(CHANT_CHORDS):
        organ(start + bar * BAR, chord, BAR, gain=0.26)
    for midi, at, length in CHANT:
        seconds = length * QUARTER - 0.06
        left, right = mi.choir([midi, midi - 12], seconds, rng, vowel="ah", voices=3, attack=0.18, release=0.5)
        arrangement.add_stereo("choir", start + at * QUARTER, left, right, 0.62, reverb=0.65)

    start = 8 * BAR
    for k, (chord, voicing) in enumerate(HYMN):
        at = start + k * 2 * QUARTER
        left, right = mi.choir([note(n) for n in voicing.split()], 2 * QUARTER - 0.05, rng, vowel="oh", voices=2, attack=0.25, release=0.6)
        arrangement.add_stereo("choir", at, left, right, 0.62, reverb=0.65)
        organ(at, chord, 2 * QUARTER, gain=0.22)

    start = 12 * BAR
    organ(start, "C", BAR)
    organ(start + BAR, "G", BAR)
    for at, chord, voicing in ((start, "C", "C3 G3 E4 C5"), (start + BAR, "G", "G3 D4 G4 B4")):
        left, right = mi.choir([note(n) for n in voicing.split()], BAR - 0.1, rng, vowel="ah", voices=2, attack=0.4, release=0.9)
        arrangement.add_stereo("choir", at, left, right, 0.55, reverb=0.65)
    arrangement.add("bell", start + BAR, mi.bell(note("G3"), 0.7, rng, kind="church"), 0.2, pan=0.2, reverb=0.7)
    play_plucked(arrangement, "glock", SHIMMER, start, QUARTER / 2, lambda m, v, r: mi.bell(m, v, r, kind="glock"), rng, 0.08, pan=0.35, reverb=0.7, velocity=0.6)

    render(arrangement, "temple", out_dir, rng, rt60=3.8, ir_length=5.0, predelay=0.04, wet=0.75, notes="G major, 4/4, 54 BPM")


if __name__ == "__main__":
    main(Path(sys.argv[1]))
