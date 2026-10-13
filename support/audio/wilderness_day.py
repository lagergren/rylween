"""
"Wilderness (day)": a striding wanderer's tune in F major, 4/4 at 104 BPM.

Renaissance/baroque flavor: a flute melody over an independent lute
counter-line and a stepwise walking bass, in clear 4- and 8-bar phrases.
The B section runs a circle-of-fifths sequence (Dm-Gm-C-F-Bb) with the lute
answering the flute in canon a twelfth below, one bar behind.

Form (34 bars, ~78 s, loops back to the intro):
  intro  2 bars  walking bass and lute broken chords
  A      8 bars  flute melody, lute counter-line, walking bass, frame drum
  A'     8 bars  the melody in running eighths over a sustained viola line
  B      8 bars  descending sequence, lute canon, soft pad and tambourine
  A''    8 bars  three voices: melody, lute counter-line and walking bass

Usage: wilderness_day.py OUT_DIR
"""

import sys
from pathlib import Path

import numpy as np

import music_instruments as mi
import synth
from score import Arrangement, bars, counterpoint_warnings, diatonic, fit_bass, parse, render
from synth import note

BPM = 104
EIGHTH = 60.0 / BPM / 2
BAR_UNITS = 8
BAR = BAR_UNITS * EIGHTH
F_MAJOR = (5, 7, 9, 10, 0, 2, 4)

MELODY_A = parse(
    "F5:2 G5:2 A5:2 C6:2 | Bb5:1 A5:1 G5:2 E5:2 C5:2 | D5:2 F5:2 A5:3 G5:1 | F5:2 D5:2 E5:2 C5:2 | "
    "F5:2 A5:2 C6:3 Bb5:1 | D6:2 C6:1 Bb5:1 F5:2 Bb5:2 | A5:2 G5:2 G5:1 F5:1 E5:2 | F5:4 r:2 C5:2",
    BAR_UNITS,
)
MELODY_A_RUNNING = parse(
    "F5:1 G5:1 A5:1 F5:1 C6:2 A5:2 | Bb5:1 A5:1 G5:1 F5:1 E5:2 G5:2 | F5:1 E5:1 D5:1 E5:1 F5:1 G5:1 A5:2 | "
    "Bb5:2 F5:2 G5:1 F5:1 E5:1 C5:1 | A5:1 Bb5:1 C6:1 A5:1 F5:2 C6:2 | D6:1 C6:1 Bb5:1 A5:1 G5:1 F5:1 D5:2 | "
    "G5:2 Bb5:2 E5:2 G5:1 E5:1 | F5:6 r:2",
    BAR_UNITS,
)
MELODY_B = parse(
    "A5:2 D6:2 C6:1 Bb5:1 A5:2 | G5:2 Bb5:2 A5:1 G5:1 F5:2 | G5:2 C6:2 Bb5:1 A5:1 G5:2 | F5:2 A5:2 G5:1 F5:1 E5:2 | "
    "F5:2 Bb5:2 A5:1 G5:1 F5:2 | D5:2 G5:2 F5:1 E5:1 D5:2 | C#5:2 E5:2 A5:2 G5:1 E5:1 | D5:2 F5:1 A5:1 G5:2 E5:1 C5:1",
    BAR_UNITS,
)
MELODY_A_LAST = bars(MELODY_A, 0, 7, BAR_UNITS) + [(m, s + 7 * BAR_UNITS, n) for m, s, n in parse("F5:6 r:2", BAR_UNITS)]

COUNTER_A = parse(
    "C5:2 Bb4:2 A4:2 F4:1 G4:1 | G4:2 E4:2 C5:2 G4:2 | F4:2 A4:2 D5:1 C5:1 Bb4:1 A4:1 | D4:2 F4:2 G4:2 E4:2 | "
    "A4:2 C5:2 A4:1 Bb4:1 C5:1 D5:1 | F4:2 A4:2 Bb4:2 D5:2 | D5:2 Bb4:2 C5:2 G4:2 | A4:2 F4:2 E4:1 F4:1 G4:2",
    BAR_UNITS,
)
VIOLA_A = parse("A4:4 F4:4 | G4:4 C5:4 | D5:4 A4:4 | D5:4 C5:4 | C5:4 A4:4 | Bb4:8 | Bb4:4 C5:4 | A4:8", BAR_UNITS)
# Bars 2-6 of the B counter-line answer the flute a twelfth below, one bar later.
CANON_B = [(m, s + BAR_UNITS, n) for m, s, n in diatonic(bars(MELODY_B, 0, 5, BAR_UNITS), -11, F_MAJOR)]
COUNTER_B = (
    parse("D4:2 F4:2 A4:2 F4:2", BAR_UNITS)
    + CANON_B
    + [(m, s + 6 * BAR_UNITS, n) for m, s, n in parse("A3:2 C#4:2 E4:3 G4:1 | F4:2 A4:2 E4:2 G4:2", BAR_UNITS)]
)
INTRO_LUTE = parse("A4:1 C5:1 F5:1 C5:1 A4:1 C5:1 F5:1 C5:1 | Bb4:1 D5:1 F5:1 D5:1 G4:1 C5:1 E5:1 C5:1", BAR_UNITS)

BASS_INTRO = parse("F2:2 G2:2 A2:2 F2:2 | Bb2:2 A2:2 G2:2 C3:2", BAR_UNITS)

CHORD_TONES = {
    "F": (5, 9, 0), "C": (0, 4, 7), "C7": (0, 4, 7, 10), "Dm": (2, 5, 9),
    "Bb": (10, 2, 5), "Gm": (7, 10, 2), "A7": (9, 1, 4, 7),
}
PAD = {
    "F": "C4 F4 A4", "C": "C4 E4 G4", "C7": "C4 E4 Bb4", "Dm": "D4 F4 A4",
    "Bb": "D4 F4 Bb4", "Gm": "D4 G4 Bb4", "A7": "C#4 E4 G4",
}
A_CHORDS = ["F", "C7", "Dm", "Bb C", "F", "Bb", "Gm C7", "F"]
B_CHORDS = ["Dm", "Gm", "C7", "F", "Bb", "Gm", "A7", "Dm C"]


def beat_chords(bar_chords: list[str]) -> list[tuple[int, ...]]:
    out = []
    for chord in bar_chords:
        names = chord.split()
        for name in names:
            out += [CHORD_TONES[name]] * (4 // len(names))
    return out


def walking_bass(bar_chords: list[str], *uppers: list) -> list:
    return fit_bass(beat_chords(bar_chords), list(uppers), F_MAJOR, 2, BAR_UNITS, note("Bb1"), note("D3"))


BASS_A = walking_bass(A_CHORDS, MELODY_A, COUNTER_A)
BASS_A_RUNNING = walking_bass(A_CHORDS, MELODY_A_RUNNING, VIOLA_A)
BASS_B = walking_bass(B_CHORDS, MELODY_B, COUNTER_B)
BASS_A_LAST = walking_bass(A_CHORDS, MELODY_A_LAST, COUNTER_A)

SECTIONS = [
    dict(name="intro", bars=2, bass=BASS_INTRO, counter=INTRO_LUTE),
    dict(name="A", bars=8, melody=MELODY_A, counter=COUNTER_A, bass=BASS_A, drums="light"),
    dict(name="A'", bars=8, melody=MELODY_A_RUNNING, viola=VIOLA_A, bass=BASS_A_RUNNING, drums="walk"),
    dict(name="B", bars=8, melody=MELODY_B, counter=COUNTER_B, bass=BASS_B, drums="walk", pad=B_CHORDS, tambourine=True),
    dict(name="A''", bars=8, melody=MELODY_A_LAST, counter=COUNTER_A, bass=BASS_A_LAST, drums="walk", pad=A_CHORDS, tambourine=True),
]


def check_counterpoint() -> None:
    warnings = []
    for label, upper, lower in [
        ("A flute/lute", MELODY_A, COUNTER_A),
        ("A' flute/viola", MELODY_A_RUNNING, VIOLA_A),
        ("B flute/lute", MELODY_B, COUNTER_B),
        ("A flute/bass", MELODY_A, BASS_A),
        ("A' flute/bass", MELODY_A_RUNNING, BASS_A_RUNNING),
        ("A' viola/bass", VIOLA_A, BASS_A_RUNNING),
        ("A'' flute/bass", MELODY_A_LAST, BASS_A_LAST),
        ("A'' lute/bass", COUNTER_A, BASS_A_LAST),
        ("B flute/bass", MELODY_B, BASS_B),
        ("A lute/bass", COUNTER_A, BASS_A),
        ("B lute/bass", COUNTER_B, BASS_B),
    ]:
        warnings += counterpoint_warnings(upper, lower, 2, BAR_UNITS, label)
    print("counterpoint:", "; ".join(warnings) if warnings else "clean")
    names = "C C# D Eb E F F# G Ab A Bb B".split()
    for label, line in [("A", BASS_A), ("A'", BASS_A_RUNNING), ("B", BASS_B), ("A''", BASS_A_LAST)]:
        print(f"bass {label}: " + " ".join(names[m % 12] + str(m // 12 - 1) for m, _, _ in line))


def main(out_dir: Path) -> None:
    check_counterpoint()
    rng = np.random.default_rng(2104)
    total_bars = sum(section["bars"] for section in SECTIONS)
    arrangement = Arrangement(total_bars * BAR)
    boom = synth.drum(110.0, 60.0, 0.2, 0.5, rng)
    tap = synth.drum(250.0, 185.0, 0.06, 1.0, rng)

    def jitter(amount: float = 0.006) -> float:
        return float(rng.uniform(-amount, amount))

    start_bar = 0
    for section in SECTIONS:
        start = start_bar * BAR
        for midi, unit, length in section.get("melody", []):
            if midi is None:
                continue
            velocity = 0.82 + 0.16 * (midi - 65) / 19 + jitter(0.03)
            seconds = length * EIGHTH - 0.035
            arrangement.add("flute", start + unit * EIGHTH + jitter(), synth.flute(midi, seconds, velocity, rng), 0.3, pan=0.15, reverb=0.3)
        for midi, unit, length in section.get("counter", []):
            accent = 1.0 if unit % 4 == 0 else 0.85
            velocity = float(np.clip(0.72 * accent + jitter(0.04), 0.3, 1.0))
            arrangement.add("lute", start + unit * EIGHTH + jitter(), mi.lute(midi, velocity, rng), 1.2, pan=-0.35, reverb=0.3)
        for midi, unit, length in section.get("viola", []):
            seconds = length * EIGHTH - 0.05
            arrangement.add("viola", start + unit * EIGHTH + jitter(), mi.bowed(midi, seconds, 0.55, rng, attack=0.18, brightness=3.0), 0.25, pan=0.4, reverb=0.4)
        for midi, unit, length in section.get("bass", []):
            velocity = float(np.clip((0.85 if unit % 4 == 0 else 0.72) + jitter(0.04), 0.3, 1.0))
            arrangement.add("bass", start + unit * EIGHTH + jitter(0.004), mi.gut_bass(midi, velocity, rng), 0.42, pan=0.0, reverb=0.12)
        for bar in range(section["bars"]):
            bar_start = start + bar * BAR
            if section.get("drums"):
                hits = [(0, boom, 0.32), (4, boom, 0.2)]
                if section["drums"] == "walk":
                    hits += [(2, tap, 0.13), (6, tap, 0.15)]
                for unit, sound, level in hits:
                    arrangement.add("drums", bar_start + unit * EIGHTH + jitter(0.004), sound, level, reverb=0.15)
            if section.get("tambourine"):
                for unit in (2, 6):
                    arrangement.add("tambourine", bar_start + unit * EIGHTH + jitter(0.004), mi.tambourine(0.8, rng), 0.09, pan=0.45, reverb=0.2)
            if section.get("pad"):
                chords = section["pad"][bar].split()
                for i, chord in enumerate(chords):
                    seconds = BAR / len(chords)
                    left, right = synth.pad([note(n) for n in PAD[chord].split()], seconds, rng)
                    arrangement.add_stereo("pad", bar_start + i * seconds, left, right, 0.2, reverb=0.4)
        start_bar += section["bars"]

    render(arrangement, "wilderness-day", out_dir, rng, rt60=1.6, wet=0.42, notes="F major, 4/4, 104 BPM")


if __name__ == "__main__":
    main(Path(sys.argv[1]))
