"""
"Castle Zadraz": ominous and relentless, C minor, 4/4 at 76 BPM.

Low staccato strings hammer an ostinato that keeps touching the flat second
above each root. Timpani mark the bars and roll into each phrase, a pad adds
a minor-ninth cluster, and in the second part a horn sings a slow line over
dark harmony: the Neapolitan Db and the tritone-distant Gb.

Form (20 bars, ~63 s, loops back to the intro):
  intro  2 bars  ostinato and timpani
  A      8 bars  ostinato, timpani, cluster pad, bowed bass
  B      8 bars  horn melody, choir
  outro  2 bars  ostinato and a timpani roll

Usage: castle_zadraz.py OUT_DIR
"""

import sys
from pathlib import Path

import numpy as np

import music_instruments as mi
import synth
from score import Arrangement, parse, play_sustained, render
from synth import note

BPM = 76
EIGHTH = 60.0 / BPM / 2
BAR = 8 * EIGHTH
OSTINATO = [0, 0, 12, 0, 1, 0, -2, 0]

CHORDS = {
    # ostinato root, bass, pad
    "Cm": ("C3", "C2", "C3 G3 Db4 Eb4"),
    "Ab": ("Ab2", "Ab2", "Ab3 C4 Eb4"),
    "G": ("G2", "G2", "G3 B3 D4 Ab4"),
    "Db": ("Db3", "Db2", "Db3 F3 Ab3"),
    "Gb": ("Gb2", "Gb2", "Gb3 Bb3 Db4"),
}
A_CHORDS = ["Cm", "Cm", "Ab", "G", "Cm", "Cm", "Db", "G"]
B_CHORDS = ["Cm", "Db", "G", "G", "Cm", "Gb", "Ab", "Cm"]
HORN = parse("C4:4 Eb4:2 D4:2 | Db4:6 C4:2 | Ab3:4 G3:4 | G3:8 | C4:4 Eb4:2 F4:2 | Gb4:6 F4:2 | Eb4:4 Db4:4 | C4:8", 8)


def main(out_dir: Path) -> None:
    rng = np.random.default_rng(1313)
    sections = [("intro", ["Cm", "Cm"]), ("A", A_CHORDS), ("B", B_CHORDS), ("outro", ["Cm", "Cm"])]
    total = sum(len(chords) for _, chords in sections)
    arrangement = Arrangement(total * BAR, tail=6.0)

    def roll(start: float, seconds: float, midi: int) -> None:
        hits = int(seconds / 0.075)
        for i in range(hits):
            level = (i + 1) / hits
            arrangement.add("timpani", start + i * 0.075 + rng.uniform(-0.005, 0.005), mi.timpani(midi, 0.3 + 0.5 * level, rng), 0.1 * (0.4 + level), reverb=0.4)

    bar_index = 0
    for name, chords in sections:
        for i, chord in enumerate(chords):
            bar_start = (bar_index + i) * BAR
            root, bass, pad = CHORDS[chord]
            for unit, interval in enumerate(OSTINATO):
                accent = 1.0 if unit in (0, 4) else 0.75
                velocity = float(np.clip(accent * 0.8 + rng.uniform(-0.05, 0.05), 0.2, 1.0))
                arrangement.add("ostinato", bar_start + unit * EIGHTH + rng.uniform(-0.004, 0.004),
                                mi.staccato_strings(note(root) + interval, velocity, rng), 1.0, pan=-0.15, reverb=0.35)
            timp = note("C2") if chord in ("Cm", "Ab") else note("G2") if chord == "G" else None
            if timp:
                arrangement.add("timpani", bar_start, mi.timpani(timp, 0.85, rng), 0.32, reverb=0.4)
            if name in ("A", "B"):
                arrangement.add("bass", bar_start, mi.bowed(note(bass), BAR * 0.97, 0.7, rng, attack=0.25, brightness=2.0), 0.2, reverb=0.3)
            if name == "A":
                left, right = synth.pad([note(n) for n in pad.split()], BAR, rng)
                arrangement.add_stereo("pad", bar_start, left, right, 0.25, reverb=0.5)
            if name == "B":
                left, right = mi.choir([note(n) for n in pad.split()[:3]], BAR, rng, vowel="oh")
                arrangement.add_stereo("choir", bar_start, left, right, 0.38, reverb=0.55)
            if name in ("A", "B") and i in (3, 7):
                roll(bar_start + 2 * 2 * EIGHTH, 4 * EIGHTH - 0.05, note("G2"))
        if name == "B":
            play_sustained(arrangement, "horn", HORN, bar_index * BAR, EIGHTH, mi.horn, rng, 0.32, pan=0.15, reverb=0.45, velocity=0.8)
        if name == "outro":
            roll(bar_index * BAR + BAR, BAR - 0.1, note("C2"))
        bar_index += len(chords)

    render(arrangement, "castle-zadraz", out_dir, rng, rt60=2.6, ir_length=3.5, predelay=0.03, wet=0.6, notes="C minor, 4/4, 76 BPM")


if __name__ == "__main__":
    main(Path(sys.argv[1]))
