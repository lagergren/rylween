"""
"Town": a lively folk jig in G mixolydian, 6/8 at 100 dotted-quarter BPM.

Tin-whistle melody over lute chucks, a plucked bass on the two main beats and
a bodhran playing the jig pulse. The flattened seventh (F natural) gives the
old-world modal color; the parts are the usual 8-bar AABB of a dance tune.

Form (42 bars, ~50 s, loops back to the intro):
  intro  2 bars  bodhran and lute vamp on G
  A      8 bars  whistle melody
  A      8 bars  repeated with a second whistle in harmony
  B      8 bars  the higher turn, tambourine joins
  B      8 bars  repeated in harmony
  A      8 bars  once more, ending on G

Usage: town.py OUT_DIR
"""

import sys
from pathlib import Path

import numpy as np

import music_instruments as mi
import synth
from score import Arrangement, chord_tone_below, parse, play_sustained, render
from synth import note

BPM = 100  # dotted quarters
EIGHTH = 60.0 / BPM / 3
BAR_UNITS = 6
BAR = BAR_UNITS * EIGHTH

TUNE_A = (
    "G5:2 B5:1 D6:2 B5:1 | A5:1 B5:1 A5:1 G5:2 D5:1 | F5:2 A5:1 C6:2 A5:1 | G5:1 A5:1 G5:1 E5:2 C5:1 | "
    "D5:1 G5:1 B5:1 D6:2 B5:1 | C6:1 B5:1 A5:1 B5:2 G5:1 | A5:1 F5:1 A5:1 C6:1 A5:1 F5:1 | "
)
MELODY_A = parse(TUNE_A + "G5:4 D5:2", BAR_UNITS)
MELODY_A_END = parse(TUNE_A + "G5:3 r:3", BAR_UNITS)
MELODY_B = parse(
    "E6:2 D6:1 C6:2 E6:1 | D6:2 B5:1 G5:2 B5:1 | C6:2 A5:1 F5:2 A5:1 | B5:1 A5:1 G5:1 D5:3 | "
    "E5:1 G5:1 C6:1 E6:2 C6:1 | D6:1 B5:1 G5:1 B5:2 D6:1 | C6:1 A5:1 F5:1 A5:1 G5:1 F5:1 | G5:4 D5:2",
    BAR_UNITS,
)
A_CHORDS = ["G", "G", "F", "C", "G", "G", "F", "G"]
B_CHORDS = ["C", "G", "F", "G", "C", "G", "F", "G"]
CHORDS = {
    # lute chuck voicing, bass root, bass fifth
    "G": ("G3 B3 D4", "G2", "D3"),
    "F": ("F3 A3 C4", "F2", "C3"),
    "C": ("G3 C4 E4", "C3", "G2"),
}
DRUM = [(0, "boom", 1.0), (1, "tap", 0.3), (2, "tap", 0.45), (3, "boom", 0.6), (4, "tap", 0.3), (5, "tap", 0.45)]


def main(out_dir: Path) -> None:
    rng = np.random.default_rng(1868)
    sections = [
        dict(chords=["G", "G"]),
        dict(chords=A_CHORDS, melody=MELODY_A),
        dict(chords=A_CHORDS, melody=MELODY_A, harmony=True),
        dict(chords=B_CHORDS, melody=MELODY_B, tambourine=True),
        dict(chords=B_CHORDS, melody=MELODY_B, harmony=True, tambourine=True),
        dict(chords=A_CHORDS, melody=MELODY_A_END),
    ]
    total = sum(len(s["chords"]) for s in sections)
    arrangement = Arrangement(total * BAR)
    boom = synth.drum(120.0, 66.0, 0.18, 0.7, rng)
    tap = synth.drum(240.0, 180.0, 0.05, 1.2, rng)
    drums = {"boom": boom, "tap": tap}

    bar_index = 0
    for section in sections:
        start = bar_index * BAR
        for i, chord in enumerate(section["chords"]):
            bar_start = start + i * BAR
            chuck, root, fifth = CHORDS[chord]
            for unit, midi, velocity in ((0, note(root), 0.85), (3, note(fifth), 0.7)):
                arrangement.add("bass", bar_start + unit * EIGHTH + rng.uniform(-0.004, 0.004), mi.gut_bass(midi, velocity, rng, ring=0.9), 0.4, reverb=0.1)
            for unit in (2, 5):
                for k, name in enumerate(chuck.split()):
                    velocity = float(np.clip(0.55 + rng.uniform(-0.05, 0.05), 0.2, 1.0))
                    when = bar_start + unit * EIGHTH + 0.012 * k + rng.uniform(-0.004, 0.004)
                    arrangement.add("lute", when, mi.lute(note(name), velocity, rng, ring=0.7), 0.52, pan=-0.3, reverb=0.25)
            for unit, kind, level in DRUM:
                gain = (0.3 if kind == "boom" else 0.13) * level
                arrangement.add("drums", bar_start + unit * EIGHTH + rng.uniform(-0.005, 0.005), drums[kind], gain, reverb=0.15)
            if section.get("tambourine"):
                for unit, level in ((0, 1.0), (3, 0.7)):
                    arrangement.add("tambourine", bar_start + unit * EIGHTH, mi.tambourine(0.8, rng), 0.1 * level, pan=0.45, reverb=0.2)
        if "melody" in section:
            melody = section["melody"]
            play_sustained(arrangement, "whistle", melody, start, EIGHTH, synth.flute, rng, 0.27, pan=0.15, reverb=0.25, velocity=0.9, legato=0.03)
            if section.get("harmony"):
                harmony = []
                for midi, at, length in melody:
                    if midi is not None:
                        pcs = tuple(note(n) % 12 for n in CHORDS[section["chords"][at // BAR_UNITS]][0].split())
                        harmony.append((chord_tone_below(midi, pcs), at, length))
                play_sustained(arrangement, "whistle2", harmony, start + 0.01, EIGHTH, synth.flute, rng, 0.15, pan=-0.2, reverb=0.3, velocity=0.75, legato=0.03)
        bar_index += len(section["chords"])

    render(arrangement, "town", out_dir, rng, rt60=1.3, wet=0.35, notes="G mixolydian, 6/8, 100 BPM")


if __name__ == "__main__":
    main(Path(sys.argv[1]))
