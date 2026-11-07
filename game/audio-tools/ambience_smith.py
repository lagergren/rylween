"""
Smith: a working forge.

The forge roars and crackles throughout. The smith works in bouts:
hammering hot iron (dull blows on the work, with light rebound taps
ringing on the anvil face between them), then reheating with a few pumps of
the creaking bellows, and once per loop quenching the piece with a hiss and
a seethe of bubbles. Blows keep a working pace with human unevenness.

Usage: ambience_smith.py OUT_DIR
"""

import sys
from pathlib import Path

import numpy as np

import ambience
import synth

LOOP = 72.0
ANVIL = (1.0, 2.255, 3.6, 5.07, 6.24)


def blow(rng: np.random.Generator) -> np.ndarray:
    """Hammer on hot iron: a heavy, dull strike with only a little anvil ring."""
    base = rng.uniform(560, 700)
    dull = ambience.modes([base, base * 2.1, base * 3.7], [0.06, 0.04, 0.025], [1.0, 0.6, 0.3], rng, seconds=0.5)
    ring = ambience.modes([1020.0 * r * rng.uniform(0.995, 1.005) for r in ANVIL], [0.45, 0.3, 0.2, 0.14, 0.1], [1.0, 0.7, 0.45, 0.3, 0.18], rng, seconds=0.5)
    body = ambience.thump(150.0, 80.0, 0.04)
    out = dull + 0.35 * ring
    out[: len(body)] += body * 0.9
    hit = ambience.click(rng, 0.0006, 1500.0)
    out[: len(hit)] += hit * 0.5
    return ambience.lowpass(out, 7000.0) / (np.abs(out).max() + 1e-12)


def tap(rng: np.random.Generator) -> np.ndarray:
    """Rebound tap on the bare anvil face: light and ringing."""
    ring = ambience.modes([1020.0 * r * rng.uniform(0.995, 1.005) for r in ANVIL], [0.7, 0.5, 0.35, 0.25, 0.18], [1.0, 0.8, 0.5, 0.3, 0.2], rng, seconds=1.2)
    hit = ambience.click(rng, 0.0005, 2500.0)
    ring[: len(hit)] += hit * 0.4
    return ambience.lowpass(ring, 7000.0) / (np.abs(ring).max() + 1e-12)


def hammering(mixer, rng, start: float, blows: int) -> float:
    at = start
    for i in range(blows):
        mixer.add(at, blow(rng), 0.106 * rng.uniform(0.8, 1.0), pan=0.15, reverb=0.4)
        if rng.random() < 0.7:
            mixer.add(at + rng.uniform(0.2, 0.26), tap(rng), 0.022 * rng.uniform(0.6, 1.0), pan=0.15, reverb=0.4)
        at += rng.uniform(0.52, 0.66) * (1.25 if i % 6 == 5 else 1.0)
    return at


def bellows(mixer, rng, start: float, pumps: int) -> float:
    at = start
    for _ in range(pumps):
        mixer.add(at, ambience.creak(rng, 0.35, (25.0, 45.0, 30.0), (220, 520, 1100)), 0.03, pan=-0.25, reverb=0.4)
        mixer.add(at + 0.15, ambience.whoosh(rng, 1.0, 180.0, 1300.0, 0.45), 0.05, pan=-0.2, reverb=0.4)
        mixer.add(at + 0.4, ambience.lowpass(ambience.white(rng, 1.2), 500.0) * np.exp(-((ambience.times(1.2) - 0.4) / 0.35) ** 2), 0.035, pan=-0.1, reverb=0.3)
        at += rng.uniform(1.4, 1.7)
    return at


def quench(mixer, rng, start: float) -> None:
    seconds = 2.2
    t = ambience.times(seconds)
    hiss = ambience.highpass(ambience.white(rng, seconds), 2500.0) * synth.smoothstep(t / 0.04) * np.exp(-t / 0.6)
    mixer.add(start, ambience.lowpass(hiss, 7500.0), 0.025, pan=0.3, reverb=0.4)
    for _ in range(40):
        f0 = rng.uniform(300, 900)
        at = start + rng.exponential(0.5)
        if at < start + 2.5:
            mixer.add(at, ambience.bubble(rng, f0, f0 * rng.uniform(1.6, 2.4), rng.uniform(0.03, 0.06)), 0.012 * rng.uniform(0.3, 1.0), pan=0.3, reverb=0.4)
    ambience.add_crackle(_Offset(mixer, start), rng, 1.5, 0.05, 0.03, pan=0.3, lo=2500.0, hi=7000.0, reverb=0.4)


class _Offset:
    """Lets helpers that place events on [0, loop) place them after `start` instead."""

    def __init__(self, mixer, start):
        self.mixer, self.start = mixer, start

    def add(self, at, sound, gain, pan=0.0, reverb=0.3):
        self.mixer.add(self.start + at, sound, gain, pan, reverb)


def main(out_dir: Path) -> None:
    rng = np.random.default_rng(7340)
    mixer = synth.LoopMixer(LOOP, tail=8.0)
    n = mixer.loop

    mixer.add_stereo(0.0, ambience.fire_bed(rng, n, 1.6), ambience.fire_bed(rng, n, 1.6), 0.015, reverb=0.3)
    ambience.add_crackle(mixer, rng, LOOP, 0.2, 0.05, pan=-0.1, reverb=0.3)
    mixer.add_stereo(0.0, ambience.room_tone(rng, n) * 0.004, ambience.room_tone(rng, n) * 0.004, 1.0, reverb=0.0)

    at = hammering(mixer, rng, 1.0, 22)
    at = bellows(mixer, rng, at + 1.6, 4)
    at = hammering(mixer, rng, at + 1.2, 17)
    quench(mixer, rng, at + 1.5)
    at = bellows(mixer, rng, at + 5.5, 5)
    at = hammering(mixer, rng, at + 1.0, 14)
    assert at < LOOP - 1.0, f"work runs past the loop: {at:.1f} s"
    ambience.finish("smith", mixer, rng, out_dir, rt60=1.0, wet=0.45)


if __name__ == "__main__":
    main(Path(sys.argv[1]))
