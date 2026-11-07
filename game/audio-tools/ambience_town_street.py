"""
Town street: the main street outside the shops.

A distant crowd going about its business, a light breeze, sparrows, two
horse carts passing on the cobbles (hooves, wheel rumble, creaking
timber, moving across the stereo field), faint taps from the smithy down
the lane, and a far-off church bell tolling three times once per loop.

Usage: ambience_town_street.py OUT_DIR
"""

import sys
from pathlib import Path

import numpy as np

import ambience
import synth

LOOP = 80.0


def cart(mixer, rng, start: float, seconds: float, direction: float, distance: float) -> None:
    t = ambience.times(seconds)
    presence = np.exp(-(((t / seconds) - 0.5) / 0.22) ** 2)
    wheels = ambience.lowpass(ambience.white(rng, seconds), 260.0) * (1.0 + 0.6 * np.maximum(0.0, np.sin(2 * np.pi * 1.6 * t)) ** 4) * presence
    mixer.add(start, wheels, 0.05 * distance, pan=0.0, reverb=0.4)
    cutoff = 2500.0 * distance + 900.0
    trot = 2.3
    for k in range(int(seconds * trot)):
        when = k / trot + (0.11 if k % 2 else 0.0) + rng.uniform(-0.01, 0.01)
        level = np.exp(-(((when / seconds) - 0.5) / 0.22) ** 2) * rng.uniform(0.75, 1.0)
        pan = direction * (-0.8 + 1.6 * when / seconds)
        mixer.add(start + when, ambience.lowpass(ambience.hoof(rng), cutoff), 0.05 * distance * level, pan=pan, reverb=0.45)
    for when in np.sort(rng.uniform(0.25, 0.75, 5)) * seconds:
        mixer.add(start + when, ambience.creak(rng, rng.uniform(0.25, 0.5), (40.0, 70.0, 50.0), (300, 700, 1400)), 0.02 * distance, pan=direction * (-0.8 + 1.6 * when / seconds), reverb=0.45)


def main(out_dir: Path) -> None:
    rng = np.random.default_rng(9560)
    mixer = synth.LoopMixer(LOOP, tail=8.0)
    n = mixer.loop

    people = [("f" if i % 3 == 0 else "m", rng.uniform(0.4, 1.0), rng.uniform(-0.9, 0.9), rng.uniform(1500.0, 3200.0), 0.0) for i in range(16)]
    left, right = ambience.crowd(rng, n, people, talkativeness=(0.5, 1.0), pitch=1.04)
    mixer.add_stereo(0.0, left, right, 0.085, reverb=0.6)
    mixer.add_stereo(0.0, ambience.wind_bed(rng, n), ambience.wind_bed(rng, n), 0.01, reverb=0.0)

    for at in ambience.scatter(rng, LOOP, 3.2, min_gap=0.8):
        mixer.add(at, ambience.chirp(rng, rng.uniform(3400.0, 5200.0)), 0.017 * rng.uniform(0.4, 1.0), pan=rng.uniform(-0.9, 0.9), reverb=0.35)

    cart(mixer, rng, rng.uniform(8.0, 14.0), 10.0, 1.0, 1.6)
    cart(mixer, rng, rng.uniform(46.0, 54.0), 11.0, -1.0, 1.05)

    bell_at = rng.uniform(30.0, 36.0)
    for k in range(3):
        mixer.add(bell_at + k * 2.6, ambience.lowpass(ambience.bell(rng, 196.0, 5.0), 1500.0), 0.03, pan=0.45, reverb=1.2)

    for at in ambience.scatter(rng, LOOP, 18.0, min_gap=9.0, margin=4.0):
        for k in range(int(rng.integers(4, 9))):
            ring = ambience.modes([1020.0 * r for r in (1.0, 2.255, 3.6)], [0.5, 0.3, 0.2], [1.0, 0.6, 0.3], rng, seconds=1.0)
            mixer.add(at + k * rng.uniform(0.55, 0.7), ambience.lowpass(ring, 2500.0), 0.0056, pan=-0.7, reverb=0.9)

    ambience.finish("town-street", mixer, rng, out_dir, rt60=1.3, wet=0.4)


if __name__ == "__main__":
    main(Path(sys.argv[1]))
