"""
Merchant: a quiet general store.

Room tone and the muffled street beyond the walls (a distant crowd, a cart
rumbling past once), the merchant's steps on creaky boards, coins counted
onto the counter, crates set down or slid, a sack rustling.

Usage: ambience_merchant.py OUT_DIR
"""

import sys
from pathlib import Path

import numpy as np

import ambience
import synth

LOOP = 66.0


def main(out_dir: Path) -> None:
    rng = np.random.default_rng(8450)
    mixer = synth.LoopMixer(LOOP, tail=8.0)
    n = mixer.loop

    mixer.add_stereo(0.0, ambience.room_tone(rng, n), ambience.room_tone(rng, n), 0.0024, reverb=0.0)
    people = [("m" if i % 3 else "f", rng.uniform(0.5, 1.0), rng.uniform(-0.9, 0.9), rng.uniform(600.0, 900.0), 0.0) for i in range(9)]
    left, right = ambience.crowd(rng, n, people, talkativeness=(0.6, 1.0), pitch=1.03)
    mixer.add_stereo(0.0, left, right, 0.031, reverb=0.2)

    cart_at = rng.uniform(20.0, 30.0)
    seconds = 9.0
    t = ambience.times(seconds)
    rumble = ambience.lowpass(ambience.white(rng, seconds), 220.0) * (1.0 + 0.5 * np.sin(2 * np.pi * 1.4 * t)) * np.exp(-(((t / seconds) - 0.5) / 0.25) ** 2)
    mixer.add(cart_at, rumble, 0.032, pan=0.0, reverb=0.1)
    for k in range(int(seconds * 2.2)):
        step = k / 2.2 + (0.12 if k % 2 else 0.0)
        level = np.exp(-(((step / seconds) - 0.5) / 0.25) ** 2)
        mixer.add(cart_at + step, ambience.lowpass(ambience.hoof(rng), 900.0), 0.048 * level, pan=-0.6 + 1.2 * step / seconds, reverb=0.1)

    for at in ambience.scatter(rng, LOOP, 14.0, min_gap=5.0, margin=4.0):
        pan = rng.uniform(-0.5, 0.5)
        for k in range(int(rng.integers(2, 6))):
            step_at = at + k * rng.uniform(0.5, 0.65)
            mixer.add(step_at, ambience.footstep_wood(rng), 0.05, pan=pan, reverb=0.35)
            if rng.random() < 0.55:
                mixer.add(step_at + 0.015, ambience.floor_creak(rng), 0.04, pan=pan, reverb=0.35)
            pan += rng.uniform(-0.12, 0.12)
    for at in ambience.scatter(rng, LOOP, 17.0, min_gap=6.0, margin=2.5):
        pan = rng.uniform(-0.3, 0.3)
        for k in range(int(rng.integers(3, 7))):
            mixer.add(at + k * rng.uniform(0.14, 0.3), ambience.coin(rng), 0.018 * rng.uniform(0.6, 1.0), pan=pan, reverb=0.3)
    for at in ambience.scatter(rng, LOOP, 16.0, min_gap=6.0):
        mixer.add(at, ambience.wooden_box(rng), 0.05 * rng.uniform(0.6, 1.0), pan=rng.uniform(-0.6, 0.6), reverb=0.35)
    for at in ambience.scatter(rng, LOOP, 21.0, min_gap=8.0):
        seconds = rng.uniform(0.5, 0.9)
        t = ambience.times(seconds)
        shape = np.abs(ambience.lowpass(ambience.white(rng, seconds), 14.0))
        rustle = ambience.bandpass(ambience.white(rng, seconds), 1200, 5000) * shape / (shape.max() + 1e-12) * np.sin(np.pi * t / seconds)
        mixer.add(at, rustle, 0.03, pan=rng.uniform(-0.6, 0.6), reverb=0.3)

    ambience.finish("merchant", mixer, rng, out_dir, rt60=0.7, wet=0.4)


if __name__ == "__main__":
    main(Path(sys.argv[1]))
