"""
Hut: a cozy one-room hut in the wilderness.

A small fire crackling, a pot simmering on it, gusty wind outside muffled
by the walls, the timbers creaking now and then, and once in a while a
shutter rattling in a gust.

Usage: ambience_hut.py OUT_DIR
"""

import sys
from pathlib import Path

import numpy as np

import ambience
import synth

LOOP = 62.0


def main(out_dir: Path) -> None:
    rng = np.random.default_rng(10670)
    mixer = synth.LoopMixer(LOOP, tail=8.0)
    n = mixer.loop

    mixer.add_stereo(0.0, ambience.fire_bed(rng, n, 0.9), ambience.fire_bed(rng, n, 0.9), 0.025, reverb=0.2)
    ambience.add_crackle(mixer, rng, LOOP, 0.22, 0.1, pan=0.2, reverb=0.2)
    wind_l = ambience.lowpass(ambience.wind_bed(rng, n, muffled=0.6), 900.0)
    wind_r = ambience.lowpass(ambience.wind_bed(rng, n, muffled=0.6), 900.0)
    mixer.add_stereo(0.0, wind_l, wind_r, 0.042, reverb=0.0)

    for at in ambience.scatter(rng, LOOP, 0.32, min_gap=0.06):
        f0 = rng.uniform(220.0, 520.0)
        mixer.add(at, ambience.bubble(rng, f0, f0 * rng.uniform(1.5, 2.2), rng.uniform(0.04, 0.08)), 0.024 * rng.uniform(0.3, 1.0), pan=0.25, reverb=0.2)

    for at in ambience.scatter(rng, LOOP, 12.0, min_gap=5.0):
        mixer.add(at, ambience.creak(rng, rng.uniform(0.5, 1.1), (16.0, 28.0, 20.0), (160, 380, 820)), 0.035, pan=rng.uniform(-0.8, 0.8), reverb=0.3)
    for at in ambience.scatter(rng, LOOP, 24.0, min_gap=12.0, margin=1.0):
        pan = rng.choice([-0.7, 0.7])
        for k in range(int(rng.integers(3, 6))):
            mixer.add(at + k * rng.uniform(0.06, 0.11), ambience.wood_knock(rng, rng.uniform(260, 340), 0.03), 0.02 * rng.uniform(0.5, 1.0), pan=pan, reverb=0.3)

    ambience.finish("hut", mixer, rng, out_dir, rt60=0.5, wet=0.35)


if __name__ == "__main__":
    main(Path(sys.argv[1]))
