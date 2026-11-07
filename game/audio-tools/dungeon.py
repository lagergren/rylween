"""
"Dungeon": slow and oppressive, E phrygian, 4/4 at 60 BPM.

A low E-B drone runs under a cello motif that keeps leaning on the
phrygian half step (F over E). In the second part a viola line rises above
it while a hushed "oo" choir fills in; deep drum strokes and a distant bell
sound far apart in a long, dark reverb.

Form (20 bars, 80 s, loops back to the start):
  A  8 bars  drone, cello motif, distant drum
  B  8 bars  viola line over the cello, choir pad
  C  4 bars  drone, drum and bell alone

Usage: dungeon.py OUT_DIR
"""

import sys
from pathlib import Path

import numpy as np

import music_instruments as mi
import synth
from score import Arrangement, parse, play_sustained, render
from synth import note

BPM = 60
QUARTER = 60.0 / BPM
BAR = 4 * QUARTER

CELLO = parse("E3:3 F3:1 | E3:4 | G3:2 F3:2 | E3:4 | A3:3 G3:1 | F3:2 E3:2 | D3:2 F3:2 | E3:4", 4)
VIOLA = parse("B3:3 C4:1 | B3:4 | D4:2 C4:2 | B3:4 | E4:3 D4:1 | C4:2 B3:2 | A3:2 C4:2 | B3:4", 4)
CHOIR = ["E3 G3 B3", "F3 A3 C4", "E3 G3 B3", "A3 C4 E4", "E3 G3 B3", "F3 A3 C4", "D3 F3 A3", "E3 G3 B3"]


def main(out_dir: Path) -> None:
    rng = np.random.default_rng(666)
    total_bars = 20
    arrangement = Arrangement(total_bars * BAR, tail=8.0)
    left, right = mi.drone([note("E2"), note("B2")], arrangement.mixer.loop, rng, cutoff=420.0, swell_cycles=5)
    arrangement.add_loop("drone", left, right, 0.4, reverb=0.4)

    deep = synth.drum(85.0, 44.0, 0.55, 0.25, rng)
    for bar in range(total_bars):
        bar_start = bar * BAR
        if bar % 2 == 0:
            arrangement.add("drums", bar_start + rng.uniform(-0.01, 0.01), deep, 0.42, reverb=0.6)
        if bar % 4 == 3:
            arrangement.add("drums", bar_start + 2.5 * QUARTER, deep, 0.25, reverb=0.6)
            arrangement.add("drums", bar_start + 3.0 * QUARTER, deep, 0.32, reverb=0.6)

    for section_start in (0, 8):
        play_sustained(arrangement, "cello", CELLO, section_start * BAR, QUARTER,
                       lambda m, s, v, r: mi.bowed(m, s, v, r, attack=0.35, release=0.6, vibrato=0.003, brightness=2.5),
                       rng, 0.45, pan=-0.2, reverb=0.55, velocity=0.75, legato=0.08)
    play_sustained(arrangement, "viola", VIOLA, 8 * BAR, QUARTER,
                   lambda m, s, v, r: mi.bowed(m, s, v, r, attack=0.4, release=0.7, vibrato=0.004, brightness=3.0),
                   rng, 0.36, pan=0.25, reverb=0.6, velocity=0.7, legato=0.08)
    for bar, chord in enumerate(CHOIR):
        left, right = mi.choir([note(n) for n in chord.split()], BAR, rng, vowel="oo", attack=1.2, release=1.5)
        arrangement.add_stereo("choir", (8 + bar) * BAR, left, right, 0.36, reverb=0.7)
    for bar in (16, 18):
        arrangement.add("bell", bar * BAR + 2 * QUARTER, mi.bell(note("E3"), 0.6, rng, kind="church"), 0.3, pan=-0.3, reverb=0.8)

    render(arrangement, "dungeon", out_dir, rng, rt60=3.6, ir_length=4.8, predelay=0.035, wet=0.75, notes="E phrygian, 4/4, 60 BPM")


if __name__ == "__main__":
    main(Path(sys.argv[1]))
