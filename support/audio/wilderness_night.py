"""
"Wilderness (night)": a wandering folk theme in D dorian, 3/4 at 88 BPM.

Form (32 bars, ~65 s, loops back to the intro):
  intro  4 bars  harp alone
  A      8 bars  flute melody, harp, bass, light frame drum
  B      8 bars  melody lifts to F major, pad and shaker join
  A'     8 bars  melody returns with a second flute below it
  outro  4 bars  harp, pad and bass turn back towards the intro

Usage: wilderness_night.py OUT_DIR
"""

import sys
from pathlib import Path

import numpy as np

import synth
from synth import note

BPM = 88
EIGHTH = 60.0 / BPM / 2
BAR = 6 * EIGHTH

# harp voicing (root, fifth, octave, third), bass note, pad notes
CHORDS = {
    "Dm": ("D3 A3 D4 F4", "D2", "D4 F4 A4"),
    "G": ("G2 D3 G3 B3", "G2", "D4 G4 B4"),
    "C": ("C3 G3 C4 E4", "C2", "C4 E4 G4"),
    "Am": ("A2 E3 A3 C4", "A2", "C4 E4 A4"),
    "F": ("F2 C3 F3 A3", "F2", "C4 F4 A4"),
    "A": ("A2 E3 A3 C#4", "A2", "C#4 E4 A4"),
}

MELODY_A = [
    ("D5", 2), ("E5", 1), ("F5", 2), ("E5", 1),
    ("D5", 2), ("B4", 1), ("G4", 3),
    ("A4", 2), ("D5", 1), ("F5", 2), ("A5", 1),
    ("G5", 3), ("E5", 2), ("C5", 1),
    ("D5", 2), ("E5", 1), ("F5", 2), ("G5", 1),
    ("A5", 2), ("G5", 1), ("F5", 1), ("E5", 1), ("D5", 1),
    ("E5", 2), ("C5", 1), ("A4", 2), ("C5", 1),
    ("D5", 6),
]

MELODY_B = [
    ("F5", 2), ("A5", 1), ("C6", 3),
    ("G5", 3), ("E5", 1), ("F5", 1), ("G5", 1),
    ("A5", 2), ("B5", 1), ("D6", 2), ("B5", 1),
    ("A5", 3), ("F5", 1), ("E5", 1), ("D5", 1),
    ("F5", 2), ("A5", 1), ("C6", 2), ("A5", 1),
    ("G5", 2), ("E5", 1), ("G5", 2), ("C6", 1),
    ("B5", 2), ("A5", 1), ("G5", 2), ("F5", 1),
    ("E5", 3), ("A4", 1), ("C#5", 2),
]

A_CHORDS = ["Dm", "G", "Dm", "C", "Dm", "G", "Am", "Dm"]
B_CHORDS = ["F", "C", "G", "Dm", "F", "C", "G", "A"]

# (eighth position, kind, gain) per bar
DRUMS_A = [(0, "boom", 0.8), (4, "tap", 0.4)]
DRUMS_B = [(0, "boom", 1.0), (2, "tap", 0.5), (3, "boom", 0.5), (4, "tap", 0.6)]
DRUMS_A2 = [(0, "boom", 0.9), (2, "tap", 0.45), (4, "tap", 0.55), (5, "tap", 0.3)]
DRUMS_FILL = [(0, "boom", 1.0), (2, "tap", 0.45), (3, "tap", 0.55), (4, "tap", 0.65), (5, "tap", 0.8)]

SECTIONS = [
    dict(chords=["Dm", "C", "Dm", "C"], harp=0.8),
    dict(chords=A_CHORDS, harp=0.85, melody=MELODY_A, bass=True, drums=DRUMS_A),
    dict(chords=B_CHORDS, harp=1.0, melody=MELODY_B, bass=True, drums=DRUMS_B, pad=True, shaker=True, fill=True),
    dict(chords=A_CHORDS, harp=0.95, melody=MELODY_A, bass=True, drums=DRUMS_A2, pad=True, harmony=True),
    dict(chords=["Dm", "C", "G", "A"], harp=0.75, bass=True, pad=True),
]

HARP_PATTERN = [0, 1, 2, 3, 2, 1]
HARP_ACCENTS = [0.9, 0.6, 0.7, 0.65, 0.6, 0.55]


def pitch_classes(chord: str) -> set[int]:
    return {note(name) % 12 for name in CHORDS[chord][0].split()}


def chord_tone_below(midi: int, chord: str) -> int:
    """Nearest chord tone a third to a sixth below the melody note."""
    tones = pitch_classes(chord)
    for down in range(3, 10):
        if (midi - down) % 12 in tones:
            return midi - down
    return midi - 12


def main(out_dir: Path) -> None:
    rng = np.random.default_rng(1207)
    bars = sum(len(section["chords"]) for section in SECTIONS)
    mixer = synth.LoopMixer(bars * BAR)
    boom = synth.drum(120.0, 62.0, 0.22, 0.6, rng)
    tap = synth.drum(260.0, 190.0, 0.07, 1.2, rng)
    drums = {"boom": (boom, 0.37), "tap": (tap, 0.18)}

    bar_index = 0
    for section in SECTIONS:
        start = bar_index * BAR
        for i, chord in enumerate(section["chords"]):
            bar_start = start + i * BAR
            voicing, bass_note, pad_notes = CHORDS[chord]
            harp_notes = [note(n) for n in voicing.split()]
            for pos, (index, accent) in enumerate(zip(HARP_PATTERN, HARP_ACCENTS)):
                velocity = float(np.clip(accent * section["harp"] + rng.uniform(-0.05, 0.05), 0.2, 1.0))
                when = bar_start + pos * EIGHTH + rng.uniform(-0.006, 0.006)
                mixer.add(max(0.0, when), synth.harp(harp_notes[index], velocity, rng), 0.8, pan=-0.3, reverb=0.35)
            if section.get("bass"):
                mixer.add(bar_start, synth.bass(note(bass_note), BAR * 0.97, 0.9), 0.19, pan=0.0, reverb=0.1)
            if section.get("pad"):
                left, right = synth.pad([note(n) for n in pad_notes.split()], BAR, rng)
                mixer.add_stereo(bar_start, left, right, 0.2, reverb=0.4)
            pattern = section.get("drums", [])
            if section.get("fill") and i == len(section["chords"]) - 1:
                pattern = DRUMS_FILL
            for pos, kind, gain in pattern:
                sound, level = drums[kind]
                mixer.add(bar_start + pos * EIGHTH + rng.uniform(-0.004, 0.004), sound, level * gain, reverb=0.2)
            if section.get("shaker"):
                for pos in range(6):
                    accent = 1.0 if pos in (0, 3) else 0.55
                    mixer.add(bar_start + pos * EIGHTH, synth.shaker(rng), 0.11 * accent, pan=0.4, reverb=0.25)

        if "melody" in section:
            elapsed = 0
            for name, eighths in section["melody"]:
                when = start + elapsed * EIGHTH
                midi = note(name)
                seconds = eighths * EIGHTH - 0.03
                velocity = 0.85 + 0.15 * (midi - 62) / 24
                onset = when + rng.uniform(-0.005, 0.005)
                mixer.add(onset, synth.flute(midi, seconds, velocity, rng), 0.3, pan=0.2, reverb=0.35)
                if section.get("harmony"):
                    chord = section["chords"][elapsed // 6]
                    below = chord_tone_below(midi, chord)
                    mixer.add(onset + 0.012, synth.flute(below, seconds, velocity * 0.8, rng), 0.17, pan=-0.25, reverb=0.4)
                elapsed += eighths
        bar_index += len(section["chords"])

    mix = mixer.render(synth.reverb_ir(rng), wet=0.5)
    mix, lufs, peak = synth.master(mix)
    out_dir.mkdir(parents=True, exist_ok=True)
    synth.write_wav(str(out_dir / "wilderness-night.wav"), mix, rng)
    synth.write_wav(str(out_dir / "wilderness-night-preview.wav"), np.tile(mix, 2), rng)
    print(f"bars={bars} loop={mixer.loop} samples ({mixer.loop / synth.SR:.2f} s) loudness={lufs:.1f} LUFS peak={peak:.1f} dBFS")


if __name__ == "__main__":
    main(Path(sys.argv[1]))
