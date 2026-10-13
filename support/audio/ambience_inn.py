"""
Inn: a calm common room in the evening.

A fireplace crackling to one side, two quiet conversations, cutlery on
plates, bowls set down, a wooden cup now and then, the innkeeper's steps
on creaky floorboards, and the old building settling.

Usage: ambience_inn.py OUT_DIR
"""

import sys
from pathlib import Path

import numpy as np

import ambience
import synth

LOOP = 70.0


def main(out_dir: Path) -> None:
    rng = np.random.default_rng(6230)
    mixer = synth.LoopMixer(LOOP, tail=8.0)
    n = mixer.loop

    people = [("m", 0.9, -0.45, 4200.0, 0.0), ("f", 0.8, -0.3, 4200.0, 0.0), ("m", 0.7, 0.35, 3600.0, 0.0), ("m", 0.6, 0.5, 3200.0, 0.0), ("f", 0.45, 0.1, 2600.0, 0.0)]
    left, right = ambience.crowd(rng, n, people, talkativeness=(0.45, 0.75), pitch=0.98)
    mixer.add_stereo(0.0, left, right, 0.11, reverb=0.5)

    mixer.add_stereo(0.0, ambience.fire_bed(rng, n, 1.1), ambience.fire_bed(rng, n, 1.1), 0.0126, reverb=0.25)
    ambience.add_crackle(mixer, rng, LOOP, 0.28, 0.09, pan=0.55, reverb=0.25)

    for at in ambience.scatter(rng, LOOP, 3.5, min_gap=0.8):
        mixer.add(at, ambience.cutlery(rng), 0.038 * rng.uniform(0.4, 1.0), pan=rng.uniform(-0.7, 0.7), reverb=0.35)
    for at in ambience.scatter(rng, LOOP, 9.0, min_gap=3.0):
        mixer.add(at, ambience.plate(rng), 0.044 * rng.uniform(0.5, 1.0), pan=rng.uniform(-0.6, 0.6), reverb=0.35)
    for at in ambience.scatter(rng, LOOP, 13.0, min_gap=4.0):
        mixer.add(at, ambience.mug_clink(rng, True), 0.05 * rng.uniform(0.5, 1.0), pan=rng.uniform(-0.6, 0.6), reverb=0.35)

    for at in ambience.scatter(rng, LOOP, 16.0, min_gap=6.0, margin=4.0):
        pan = rng.uniform(-0.5, 0.5)
        for k in range(int(rng.integers(3, 7))):
            step_at = at + k * rng.uniform(0.5, 0.62)
            mixer.add(step_at, ambience.footstep_wood(rng), 0.06, pan=pan, reverb=0.4)
            if rng.random() < 0.5:
                mixer.add(step_at + 0.015, ambience.floor_creak(rng), 0.035, pan=pan, reverb=0.4)
            pan += rng.uniform(-0.12, 0.12)
    for at in ambience.scatter(rng, LOOP, 19.0, min_gap=7.0):
        mixer.add(at, ambience.creak(rng, rng.uniform(0.6, 1.2), (18.0, 30.0, 22.0), (180, 420, 900)), 0.03, pan=rng.uniform(-0.8, 0.8), reverb=0.5)

    ambience.finish("inn", mixer, rng, out_dir, rt60=0.9, wet=0.5)


if __name__ == "__main__":
    main(Path(sys.argv[1]))
