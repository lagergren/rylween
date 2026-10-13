"""
"Cave": natural and ambient rather than a tune, A minor pentatonic, free time.

Wind moves through the passages in slow swells over a deep A-E drone. Water
drips fall at irregular moments on pentatonic pitches, so together they make
a sparse melody, and singing bowls ring out now and then. Once per loop a
distant flute echoes the opening of the main theme. Everything sits in a
very large reverb.

Length 72 s, loops seamlessly (wind and drone are periodic over the loop).

Usage: cave.py OUT_DIR
"""

import sys
from pathlib import Path

import numpy as np

import music_instruments as mi
import synth
from score import Arrangement, parse, play_sustained, render
from synth import note

SECONDS = 72.0
DRIP_PITCHES = ["E5", "G5", "A5", "C6", "D6", "E6", "G6", "A6"]
ECHO = parse("A4:2 B4:1 C5:2 B4:1 | A4:6", 6)


def main(out_dir: Path) -> None:
    rng = np.random.default_rng(4410)
    arrangement = Arrangement(SECONDS, tail=9.0)
    loop = arrangement.mixer.loop

    left, right = mi.wind(loop, rng)
    arrangement.add_loop("wind", left, right, 0.05, reverb=0.3)
    left, right = mi.drone([note("A1"), note("E2")], loop, rng, cutoff=300.0, swell_cycles=3)
    arrangement.add_loop("drone", left, right, 0.22, reverb=0.5)

    when = 1.2
    previous = None
    while when < SECONDS - 0.5:
        choices = [p for p in DRIP_PITCHES if p != previous]
        pitch = str(rng.choice(choices))
        previous = pitch
        level = rng.uniform(0.55, 1.0)
        pan = rng.uniform(-0.7, 0.7)
        arrangement.add("drips", when, mi.drip(note(pitch), level, rng), 0.4, pan=pan, reverb=0.85)
        if rng.random() < 0.25:
            arrangement.add("drips", when + rng.uniform(0.12, 0.3), mi.drip(note(pitch) + 5, level * 0.6, rng), 0.4, pan=pan, reverb=0.85)
        when += rng.uniform(1.1, 4.2)

    for at, pitch in ((6.0, "A3"), (30.0, "E3"), (54.0, "A3")):
        arrangement.add("bowls", at, mi.bell(note(pitch), 0.7, rng, kind="bowl"), 0.28, pan=rng.uniform(-0.4, 0.4), reverb=0.7)

    play_sustained(arrangement, "flute", ECHO, 38.0, 0.42, synth.flute, rng, 0.22, pan=-0.35, reverb=0.9, velocity=0.6, legato=0.06)

    render(arrangement, "cave", out_dir, rng, rt60=5.0, ir_length=6.5, predelay=0.045, wet=0.9, notes="A minor pentatonic, ambient")


if __name__ == "__main__":
    main(Path(sys.argv[1]))
