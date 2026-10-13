"""
Tavern: a busy, warm taproom.

A wordless crowd of fourteen talkers at different distances, bursts of
laughter now and then, wooden and pewter mugs, a crackling hearth,
footsteps on creaky boards, the odd chair scrape. In the corner a lute
player picks an original dance tune in A dorian (3/4, ~104 BPM), played
twice per loop with rests between.

Usage: ambience_tavern.py OUT_DIR
"""

import sys
from pathlib import Path

import numpy as np

import ambience
import synth
import voice

LOOP = 76.0
EIGHTH = 60.0 / 104 / 2

TUNE = (
    "A4:2 B4:1 C5:2 B4:1 | A4:2 E4:1 A4:3 | G4:2 A4:1 B4:2 G4:1 | E4:3 D4:3 | "
    "A4:2 B4:1 C5:2 D5:1 | E5:2 D5:1 C5:2 B4:1 | A4:2 G4:1 E4:2 G4:1 | A4:6 | "
    "E5:2 F#5:1 G5:2 E5:1 | D5:2 B4:1 G4:3 | C5:2 D5:1 E5:2 C5:1 | B4:3 A4:3 | "
    "E5:2 D5:1 C5:2 B4:1 | A4:2 B4:1 C5:2 E5:1 | D5:2 B4:1 G4:2 B4:1 | A4:6"
)
BASS = ["A2", "A2", "G2", "E2", "A2", "A2", "E2", "A2", "C3", "G2", "C3", "E2", "A2", "A2", "G2", "A2"]


def lute(mixer: synth.LoopMixer, rng: np.random.Generator, start: float, gain: float) -> None:
    bars = [bar.split() for bar in TUNE.split("|")]
    for b, bar in enumerate(bars):
        assert sum(int(token.split(":")[1]) for token in bar) == 6, f"bar {b + 1} does not fill 3/4"
        bar_start = start + b * 6 * EIGHTH
        at = bar_start
        for token in bar:
            name, eighths = token.split(":")
            when = at + rng.uniform(-0.012, 0.012)
            mixer.add(when, ambience.lowpass(synth.harp(synth.note(name), rng.uniform(0.7, 0.95), rng, ring=1.4), 3200.0), gain, pan=0.55, reverb=0.5)
            at += int(eighths) * EIGHTH
        for pos in (0, 4):
            mixer.add(bar_start + pos * EIGHTH, ambience.lowpass(synth.harp(synth.note(BASS[b]), 0.6, rng, ring=1.6), 2000.0), gain * 0.55, pan=0.5, reverb=0.5)


def main(out_dir: Path) -> None:
    rng = np.random.default_rng(5120)
    mixer = synth.LoopMixer(LOOP, tail=8.0)
    n = mixer.loop

    people = []
    for i in range(14):
        sex = "f" if i % 3 == 2 else "m"
        distance = rng.uniform(0.0, 1.0)
        people.append((sex, 1.0 - 0.6 * distance, rng.uniform(-0.8, 0.8), 5000.0 - 2500.0 * distance, 0.0))
    left, right = ambience.crowd(rng, n, people, talkativeness=(0.6, 1.1), pitch=1.06)
    mixer.add_stereo(0.0, left, right, 0.11, reverb=0.7)

    for at in ambience.scatter(rng, LOOP, 14.0, min_gap=6.0, margin=3.0):
        for k in range(int(rng.integers(1, 4))):
            laugh = ambience.lowpass(voice.laugh(rng, "f" if rng.random() < 0.35 else "m"), rng.uniform(2500.0, 3800.0))
            mixer.add(at + rng.uniform(0.0, 0.35), laugh, 0.018 * rng.uniform(0.6, 1.0), pan=rng.uniform(-0.7, 0.7), reverb=0.8)

    for at in ambience.scatter(rng, LOOP, 2.6, min_gap=0.4):
        wooden = rng.random() < 0.6
        level = rng.uniform(0.25, 1.0) * (0.06 if wooden else 0.035)
        pan = rng.uniform(-0.8, 0.8)
        mixer.add(at, ambience.mug_clink(rng, wooden), level, pan=pan, reverb=0.5)
        if rng.random() < 0.15:
            mixer.add(at + rng.uniform(0.01, 0.03), ambience.mug_clink(rng, wooden), level * 0.8, pan=pan, reverb=0.5)

    mixer.add_stereo(0.0, ambience.fire_bed(rng, n), ambience.fire_bed(rng, n), 0.0075, reverb=0.3)
    ambience.add_crackle(mixer, rng, LOOP, 0.35, 0.065, pan=-0.6, reverb=0.3)

    for at in ambience.scatter(rng, LOOP, 11.0, min_gap=4.0, margin=3.0):
        pan = rng.uniform(-0.6, 0.6)
        for k in range(int(rng.integers(3, 7))):
            step_at = at + k * rng.uniform(0.48, 0.58)
            mixer.add(step_at, ambience.footstep_wood(rng), 0.05, pan=pan, reverb=0.5)
            if rng.random() < 0.35:
                mixer.add(step_at + 0.02, ambience.floor_creak(rng), 0.025, pan=pan, reverb=0.5)
            pan += rng.uniform(-0.1, 0.1)

    for at in ambience.scatter(rng, LOOP, 20.0, min_gap=8.0, margin=2.0):
        t = ambience.times(0.4)
        scrape = ambience.creak(rng, 0.4, (90.0, 140.0, 110.0), (400, 900, 1700, 2600)) * 0.6 + ambience.bandpass(ambience.white(rng, 0.4), 500, 2500) * np.sin(np.pi * t / 0.4) ** 2 * 0.2
        mixer.add(at, scrape, 0.03, pan=rng.uniform(-0.7, 0.7), reverb=0.5)

    lute(mixer, rng, 4.0, 0.11)
    lute(mixer, rng, 4.0 + 16 * 6 * EIGHTH + 9.5, 0.1)
    ambience.finish("tavern", mixer, rng, out_dir, rt60=1.1, wet=0.6)


if __name__ == "__main__":
    main(Path(sys.argv[1]))
