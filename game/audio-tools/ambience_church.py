"""
Church: a small choir of women chanting in a stone church.

An original plainchant in D dorian (mode 1: final D, reciting tone A, with
the occasional B flat). Notes flow in free rhythm: plain syllables are
longer, melismas quicker, and phrase endings are held. Sopranos carry the
chant. The altos join in unison or sing parallel organum a fourth or a fifth
below. Syllables are wordless pseudo-Latin vowels with soft m/n/l onsets. A
distant bell sounds once per loop, and the choir breathes between phrases.

Form (~70 s loop): six phrases. 1 cantor sopranos, 2 full unison, 3 organum
at the fifth, 4 organum at the fourth, 5 sopranos alone, 6 full choir with
organum at the fifth closing on D.

Usage: ambience_church.py OUT_DIR
"""

import sys
from pathlib import Path

import numpy as np

import ambience
import synth
import voice

SYLLABIC = 0.58
MELISMA = 0.36
MEDIUM = 0.95
FINAL = 2.1

PHRASES = [
    ("sopranos", 0, "a:D4,F4 le:G4 lu:A4- a:A4 ne:A4 mi:G4,A4,Bb4,A4 o:G4 la:F4 e:G4 a:A4_"),
    ("all", 0, "na:A4 e:C5 lo:Bb4,A4 mi:G4 a:A4 le:F4,G4,F4 u:E4 a:D4_"),
    ("all", -7, "a:F4 la:A4 e:C5 lo:D5- mi:C5,D5,C5,Bb4,A4 ne:A4 a:G4,A4 i:Bb4,A4 a:G4 lu:A4_"),
    ("all", -5, "a:A4 me:G4 a:F4,G4,A4 lo:F4 e:E4 na:D4- mi:F4 a:G4,A4 le:G4,F4 u:E4,F4,E4 a:D4_"),
    ("sopranos", 0, "o:A4 la:C5,D5 e:D5 a:C5,Bb4,C5,D5 lo:A4- mi:G4,Bb4,A4 a:G4,F4 e:G4 lu:A4_"),
    ("all", -7, "a:F4,G4,A4 ne:A4 la:G4,F4 e:G4,A4,G4,F4 mu:E4 a:D4- a:E4,F4,E4 e:D4_"),
]

SOPRANOS = [  # formant scale, detune cents, vibrato cents, vibrato rate, pan, level
    (1.05, -5.0, 15.0, 5.3, -0.35, 1.0),
    (1.07, 4.0, 12.0, 5.0, 0.1, 0.9),
    (1.02, 7.0, 17.0, 5.6, 0.4, 0.85),
    (1.04, -2.0, 13.0, 4.9, -0.1, 0.9),
]
ALTOS = [
    (0.98, 3.0, 14.0, 5.1, -0.45, 0.8),
    (0.96, -6.0, 16.0, 4.8, 0.3, 0.75),
    (1.0, 1.0, 12.0, 5.4, 0.0, 0.7),
]


def parse(phrase: str, transpose: int = 0) -> list[tuple[str, str, list[tuple[int, float]]]]:
    """'la:D4,F4 e:A4_' -> [(onset, vowel, [(midi, seconds), ...]), ...]"""
    syllables = []
    for token in phrase.split():
        sound, notes = token.split(":")
        onset, vowel = (sound[0], sound[1]) if len(sound) == 2 else ("", sound)
        names = notes.split(",")
        parsed = []
        for name in names:
            seconds = SYLLABIC if len(names) == 1 else MELISMA
            if name.endswith("_"):
                name, seconds = name[:-1], FINAL
            elif name.endswith("-"):
                name, seconds = name[:-1], MEDIUM
            parsed.append((synth.note(name) + transpose, seconds))
        syllables.append((onset, vowel, parsed))
    return syllables


def phrase_seconds(syllables) -> float:
    return sum(seconds for _, _, notes in syllables for _, seconds in notes)


def main(out_dir: Path) -> None:
    rng = np.random.default_rng(4410)
    gaps = [1.5, 1.3, 1.6, 1.4, 1.5, 2.4]
    starts = []
    at = 0.5
    for (_, _, text), gap in zip(PHRASES, gaps):
        starts.append(at)
        at += phrase_seconds(parse(text)) + gap
    loop = at
    mixer = synth.LoopMixer(loop, tail=8.0)
    n = mixer.loop

    reference = None
    for (group, organum, text), start in zip(PHRASES, starts):
        melody = parse(text)
        singers = [(s, 0) for s in SOPRANOS]
        if group == "all":
            singers += [(a, organum) for a in ALTOS]
        for (scale, detune, vib, rate, pan, level), shift in singers:
            line = parse(text, shift) if shift else melody
            alto = (scale, detune, vib, rate, pan, level) in ALTOS
            sung = voice.sing_phrase(
                line, rng, formant_scale=scale, detune_cents=detune + rng.uniform(-2, 2), vibrato_cents=vib,
                vibrato_rate=rate, tilt=1.28 if alto else 1.18, breath=0.12 if alto else 0.1,
            )
            reference = reference or np.abs(sung).max()
            onset = start + rng.uniform(-0.03, 0.04)
            mixer.add(onset, sung / reference, 0.32 * level, pan=pan, reverb=1.0)
            if rng.random() < 0.5:
                mixer.add(onset - 0.45, voice.inhale(rng, 0.38), 0.004, pan=pan, reverb=0.6)

    bell_at = starts[3] - 1.2
    mixer.add(bell_at, ambience.lowpass(ambience.bell(rng, 147.0, 6.0), 1800.0), 0.05, pan=0.25, reverb=1.4)
    air = ambience.room_tone(rng, n)
    mixer.add_stereo(0.0, air * 0.0025, ambience.room_tone(rng, n) * 0.0025, 1.0, reverb=0.0)
    ambience.finish("church", mixer, rng, out_dir, rt60=4.8, wet=1.0, predelay=0.035)


if __name__ == "__main__":
    main(Path(sys.argv[1]))
