"""
"Tavern": a merry drinking-song waltz for taverns and inns, A major, 3/4 at 120 BPM.

Oom-pah-pah: plucked bass on the downbeat, lute chucks on beats two and
three, a light bodhran. The whistle sings the tune; on the repeat a fiddle
joins in harmony and the tambourine comes in.

Form (34 bars, ~51 s, loops back to the intro):
  intro  2 bars  vamp on A
  A      8 bars  whistle tune
  B      8 bars  second strain from F#m
  A      8 bars  tune with fiddle harmony, tambourine
  B      8 bars  second strain with fiddle harmony

Usage: tavern.py OUT_DIR
"""

import sys
from pathlib import Path

import numpy as np

import music_instruments as mi
import synth
from score import Arrangement, chord_tone_below, parse, play_sustained, render
from synth import note

BPM = 120
EIGHTH = 60.0 / BPM / 2
BAR_UNITS = 6
BAR = BAR_UNITS * EIGHTH

MELODY_A = parse(
    "E5:2 A5:2 C#6:2 | D6:3 C#6:1 B5:2 | C#6:2 A5:2 E5:2 | B5:4 E5:2 | "
    "E5:2 A5:2 C#6:2 | D6:2 F#6:2 D6:2 | C#6:2 A5:2 G#5:2 | A5:6",
    BAR_UNITS,
)
MELODY_B = parse(
    "F#5:2 A5:2 C#6:2 | D6:2 A5:2 F#5:2 | E5:2 A5:2 C#6:2 | B5:2 G#5:2 E5:2 | "
    "F#5:2 G#5:1 A5:1 C#6:2 | D6:2 C#6:1 B5:1 A5:2 | G#5:2 B5:2 E6:2 | A5:6",
    BAR_UNITS,
)
A_CHORDS = ["A", "D", "A", "E", "A", "D", "A E", "A"]
B_CHORDS = ["F#m", "D", "A", "E", "F#m", "D", "E", "A"]
CHORDS = {
    # chuck voicing, bass root
    "A": ("A3 C#4 E4", "A2"),
    "D": ("A3 D4 F#4", "D3"),
    "E": ("G#3 B3 E4", "E2"),
    "F#m": ("A3 C#4 F#4", "F#2"),
}


def main(out_dir: Path) -> None:
    rng = np.random.default_rng(1200)
    sections = [
        dict(chords=["A", "A"]),
        dict(chords=A_CHORDS, melody=MELODY_A),
        dict(chords=B_CHORDS, melody=MELODY_B),
        dict(chords=A_CHORDS, melody=MELODY_A, fiddle=True, tambourine=True),
        dict(chords=B_CHORDS, melody=MELODY_B, fiddle=True, tambourine=True),
    ]
    total = sum(len(s["chords"]) for s in sections)
    arrangement = Arrangement(total * BAR)
    boom = synth.drum(115.0, 64.0, 0.16, 0.6, rng)
    tap = synth.drum(230.0, 175.0, 0.05, 1.1, rng)

    bar_index = 0
    for section in sections:
        start = bar_index * BAR
        for i, chord_name in enumerate(section["chords"]):
            bar_start = start + i * BAR
            names = chord_name.split()
            arrangement.add("bass", bar_start + rng.uniform(-0.004, 0.004), mi.gut_bass(note(CHORDS[names[0]][1]), 0.85, rng, ring=1.0), 0.45, reverb=0.12)
            for unit in (2, 4):
                chuck = CHORDS[names[min(unit // 4, len(names) - 1)]][0]
                for k, name in enumerate(chuck.split()):
                    velocity = float(np.clip(0.55 + rng.uniform(-0.05, 0.05), 0.2, 1.0))
                    arrangement.add("lute", bar_start + unit * EIGHTH + 0.012 * k + rng.uniform(-0.004, 0.004), mi.lute(note(name), velocity, rng, ring=0.8), 0.5, pan=-0.3, reverb=0.25)
            for unit, sound, level in ((0, boom, 0.3), (2, tap, 0.12), (4, tap, 0.12)):
                arrangement.add("drums", bar_start + unit * EIGHTH + rng.uniform(-0.004, 0.004), sound, level, reverb=0.15)
            if section.get("tambourine"):
                arrangement.add("tambourine", bar_start + 2 * EIGHTH, mi.tambourine(0.8, rng), 0.09, pan=0.45, reverb=0.2)
        if "melody" in section:
            melody = section["melody"]
            play_sustained(arrangement, "whistle", melody, start, EIGHTH, synth.flute, rng, 0.27, pan=0.15, reverb=0.25, velocity=0.9, legato=0.03)
            if section.get("fiddle"):
                harmony = []
                for midi, at, length in melody:
                    chord_name = section["chords"][at // BAR_UNITS].split()
                    chord = chord_name[min((at % BAR_UNITS) // 4, len(chord_name) - 1)]
                    pcs = tuple(note(n) % 12 for n in CHORDS[chord][0].split())
                    harmony.append((chord_tone_below(midi, pcs) - 12, at, length))
                play_sustained(arrangement, "fiddle", harmony, start + 0.008, EIGHTH,
                               lambda m, s, v, r: mi.bowed(m, s, v, r, attack=0.05, release=0.15, vibrato=0.005, brightness=4.5),
                               rng, 0.14, pan=-0.25, reverb=0.3, velocity=0.7, legato=0.04)
        bar_index += len(section["chords"])

    render(arrangement, "tavern", out_dir, rng, rt60=1.1, wet=0.3, notes="A major, 3/4, 120 BPM")


if __name__ == "__main__":
    main(Path(sys.argv[1]))
