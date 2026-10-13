"""
Shared helpers for the soundtrack scores: a compact melody notation with
bar checking, a loop mixer that also keeps per-instrument stems, a simple
counterpoint checker, and the render step that masters, encodes and
verifies each track.
"""

import json
import subprocess
from pathlib import Path

import numpy as np
import pyloudnorm

import synth
from synth import SR, note

Event = tuple[int | None, int, int]  # (midi or None for a rest, start unit, length in units)


def parse(text: str, bar_units: int) -> list[Event]:
    """Parses "D5:2 E5:1 F5:3 | r:2 ..." and checks that every bar adds up to bar_units."""
    events: list[Event] = []
    unit = bar_sum = 0
    bar = 1
    for token in text.split():
        if token == "|":
            if bar_sum != bar_units:
                raise ValueError(f"bar {bar} has {bar_sum} units, expected {bar_units}: {text[:60]}...")
            bar_sum = 0
            bar += 1
            continue
        name, length = token.split(":")
        events.append((None if name == "r" else note(name), unit, int(length)))
        unit += int(length)
        bar_sum += int(length)
    if bar_sum not in (0, bar_units):
        raise ValueError(f"last bar {bar} has {bar_sum} units, expected {bar_units}")
    return events


def transpose(events: list[Event], semitones: int) -> list[Event]:
    return [(None if m is None else m + semitones, s, n) for m, s, n in events]


def map_pitch_classes(events: list[Event], mapping: dict[int, int]) -> list[Event]:
    """Moves pitch classes, e.g. {5: 6, 0: 1} turns D dorian into D major."""
    return [(None if m is None else m + mapping.get(m % 12, 0), s, n) for m, s, n in events]


def bars(events: list[Event], first: int, count: int, bar_units: int) -> list[Event]:
    """The events of bars [first, first + count), re-based to start at unit 0."""
    lo, hi = first * bar_units, (first + count) * bar_units
    return [(m, s - lo, n) for m, s, n in events if lo <= s < hi]


def counterpoint_warnings(
    upper: list[Event], lower: list[Event], beat_units: int, bar_units: int, label: str, strong_beats: tuple[int, ...] = (0, 2)
) -> list[str]:
    """Flags dissonances on strong beats and parallel fifths/octaves between consecutive beats."""

    def sounding(events, unit):
        for m, s, n in events:
            if m is not None and s <= unit < s + n:
                return m
        return None

    end = max(s + n for _, s, n in upper + lower)
    warnings = []
    previous = None
    for unit in range(0, end, beat_units):
        hi, lo = sounding(upper, unit), sounding(lower, unit)
        if hi is None or lo is None:
            previous = None
            continue
        interval = (hi - lo) % 12
        where = f"{label} bar {unit // bar_units + 1} beat {(unit % bar_units) // beat_units + 1}"
        if hi < lo:
            warnings.append(f"{where}: voices cross")
        if interval in (1, 2, 6, 10, 11) and (unit % bar_units) // beat_units in strong_beats:
            warnings.append(f"{where}: dissonance {interval} semitones on a strong beat")
        if previous:
            p_hi, p_lo, p_interval = previous
            if interval == p_interval and interval in (0, 7) and hi != p_hi and lo != p_lo:
                warnings.append(f"{where}: parallel {'octaves' if interval == 0 else 'fifths'}")
        previous = (hi, lo, interval)
    return warnings


class Arrangement:
    """A synth.LoopMixer that also keeps a mono stem per instrument for level checks."""

    def __init__(self, seconds: float, tail: float = 6.0, offset: float = 0.03):
        self.mixer = synth.LoopMixer(seconds, tail)
        self.seconds = self.mixer.loop / SR
        # Shifting everything slightly keeps note onsets off the loop point.
        self.offset = offset
        self.stems: dict[str, np.ndarray] = {}

    def add(self, stem: str, start: float, mono: np.ndarray, gain: float, pan: float = 0.0, reverb: float = 0.3) -> None:
        angle = (pan + 1.0) * np.pi / 4.0
        self.add_stereo(stem, start, mono * np.cos(angle), mono * np.sin(angle), gain, reverb)

    def add_stereo(self, stem: str, start: float, left: np.ndarray, right: np.ndarray, gain: float, reverb: float = 0.3) -> None:
        start = (start + self.offset) % self.seconds
        self.mixer.add_stereo(start, left, right, gain, reverb)
        bus = self.stems.setdefault(stem, np.zeros(self.mixer.dry.shape[1], dtype=np.float32))
        i = int(round(start * SR))
        end = min(i + len(left), len(bus))
        bus[i:end] += ((left[: end - i] + right[: end - i]) * 0.5 * gain).astype(np.float32)

    def add_loop(self, stem: str, left: np.ndarray, right: np.ndarray, gain: float, reverb: float = 0.3) -> None:
        """Adds a texture that is exactly one loop long and periodic (drone, wind)."""
        self.add_stereo(stem, 0.0, left, right, gain, reverb)

    def folded_stem(self, stem: str) -> np.ndarray:
        bus = self.stems[stem]
        out = bus[: self.mixer.loop].astype(np.float64)
        tail = bus[self.mixer.loop :]
        out[: len(tail)] += tail
        return out


def render(
    arrangement: Arrangement,
    name: str,
    out_dir: Path,
    rng: np.random.Generator,
    rt60: float = 1.9,
    ir_length: float = 2.8,
    predelay: float = 0.018,
    wet: float = 0.5,
    notes: str = "",
) -> dict:
    """Mixes, masters to -18 LUFS, writes <name>.wav/.mp3 and a spectrogram, and checks the result."""
    out_dir.mkdir(parents=True, exist_ok=True)
    ir = synth.reverb_ir(rng, rt60=rt60, length=ir_length, predelay=predelay)
    raw = arrangement.mixer.render(ir, wet=wet)
    mix, lufs, peak = synth.master(raw)
    gain = float(np.abs(mix).max() / np.abs(raw).max())
    problems = []
    if not np.isfinite(mix).all():
        problems.append("non-finite samples")
    if peak > -1.0:
        problems.append(f"peak {peak:.1f} dBFS above -1")
    if abs(lufs + 18.0) > 0.5:
        problems.append(f"loudness {lufs:.1f} LUFS, not -18 (peak-limited)")
    steps = np.abs(np.diff(mix, axis=1)).max(axis=0)
    seam = float(np.abs(mix[:, 0] - mix[:, -1]).max())
    p99 = float(np.percentile(steps, 99))
    if seam > p99:
        problems.append(f"loop seam step {seam:.4f} above the 99th percentile step {p99:.4f}")

    meter = pyloudnorm.Meter(SR)
    stems = {}
    for stem in sorted(arrangement.stems):
        level = meter.integrated_loudness(arrangement.folded_stem(stem) * gain)
        stems[stem] = round(float(level), 1)

    wav = out_dir / f"{name}.wav"
    mp3 = out_dir / f"{name}.mp3"
    synth.write_wav(str(wav), mix, rng)
    subprocess.run(["lame", "--silent", "-V", "4", str(wav), str(mp3)], check=True)
    subprocess.run(
        ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(wav), "-lavfi",
         "showspectrumpic=s=1200x400:legend=1:scale=log:fscale=log:stop=8000", str(out_dir / f"{name}-spectrum.png")],
        check=True,
    )
    report = {
        "name": name,
        "seconds": round(arrangement.seconds, 2),
        "mp3_bytes": mp3.stat().st_size,
        "lufs": round(float(lufs), 1),
        "peak_dbfs": round(float(peak), 1),
        "seam_step": round(seam, 5),
        "p99_step": round(p99, 5),
        "stems_lufs": stems,
        "problems": problems,
        "notes": notes,
    }
    (out_dir / f"{name}.json").write_text(json.dumps(report, indent=2))
    stem_text = "  ".join(f"{k}={v}" for k, v in sorted(stems.items(), key=lambda kv: -kv[1]))
    print(f"{name}: {report['seconds']} s, {report['mp3_bytes'] / 1e6:.2f} MB, {lufs:.1f} LUFS, peak {peak:.1f} dBFS, "
          f"seam {seam:.4f} (p99 {p99:.4f})\n  stems: {stem_text}\n  problems: {problems or 'none'}")
    return report


def diatonic(events: list[Event], steps: int, scale: tuple[int, ...]) -> list[Event]:
    """Moves notes by scale steps within a scale given as ascending pitch classes from its tonic."""
    out = []
    for m, s, n in events:
        if m is None:
            out.append((m, s, n))
            continue
        octave, pc = divmod(m - scale[0], 12)
        index = next(i for i, p in enumerate(scale) if (p - scale[0]) % 12 == pc)
        octave_shift, new_index = divmod(index + steps, len(scale))
        out.append((scale[0] + 12 * (octave + octave_shift) + (scale[new_index] - scale[0]) % 12, s, n))
    return out


def _sounding(events: list[Event], unit: int) -> int | None:
    for m, s, n in events:
        if m is not None and s <= unit < s + n:
            return m
    return None


def fit_bass(
    chords: list[tuple[int, ...]],
    uppers: list[list[Event]],
    scale: tuple[int, ...],
    beat_units: int,
    bar_units: int,
    low: int,
    high: int,
    first: int | None = None,
    last: int | None = None,
) -> list[Event]:
    """
    Finds a mostly stepwise walking bass, one note per beat, under the given voices.

    chords holds one tuple of pitch classes per beat (root first, an optional
    seventh last). A Viterbi search scores each candidate on harmony (chord
    tones on strong beats, the root on downbeats), clashes with the upper
    voices, parallel fifths and octaves, and the size of each step.
    """
    beats = len(chords)
    per_bar = bar_units // beat_units
    candidates = []
    for beat, chord in enumerate(chords):
        pcs = set(scale) | set(chord)
        notes = [m for m in range(low, high + 1) if m % 12 in pcs]
        if beat == 0 and first is not None:
            notes = [first]
        if beat == beats - 1 and last is not None:
            notes = [last]
        candidates.append(notes)

    def local(beat: int, m: int) -> float:
        chord = chords[beat]
        position = beat % per_bar
        strong = position in (0, 2)
        cost = 0.0
        pc = m % 12
        if pc == chord[0]:
            cost += 0.0
        elif pc in chord[1:3]:
            cost += 2.5 if position == 0 else 0.4
        elif len(chord) > 3 and pc == chord[3]:
            cost += 2.5 if strong else 0.6
        else:
            cost += 9.0 if strong else 0.8
        unit = beat * beat_units
        for voice in uppers:
            hi = _sounding(voice, unit)
            if hi is None:
                continue
            if hi <= m + 2:
                cost += 10.0
            interval = (hi - m) % 12
            if interval in (1, 11):
                cost += 8.0 if strong else 2.5
            elif interval == 2:
                cost += 6.0 if strong else 1.0
        return cost

    def move(beat: int, a: int, b: int) -> float:
        leap = abs(b - a)
        cost = {0: 1.6, 1: 0.0, 2: 0.0, 3: 0.7, 4: 0.7, 5: 0.9, 7: 0.9, 12: 1.2}.get(leap, 2.5 if leap < 12 else 4.0)
        unit_a, unit_b = (beat - 1) * beat_units, beat * beat_units
        for voice in uppers:
            ha, hb = _sounding(voice, unit_a), _sounding(voice, unit_b)
            if ha is None or hb is None or ha == hb or a == b:
                continue
            ia, ib = (ha - a) % 12, (hb - b) % 12
            if ia == ib and ia in (0, 7):
                cost += 12.0
            elif ib in (0, 7) and (hb - ha) * (b - a) > 0 and abs(hb - ha) > 2:
                cost += 1.5  # hidden fifth/octave approached by leap in similar motion
        return cost

    best = [{m: (local(0, m), None) for m in candidates[0]}]
    for beat in range(1, beats):
        layer = {}
        for m in candidates[beat]:
            prev, total = min(((p, c + move(beat, p, m)) for p, (c, _) in best[-1].items()), key=lambda pc: pc[1])
            layer[m] = (total + local(beat, m), prev)
        best.append(layer)
    m = min(best[-1], key=lambda k: best[-1][k][0])
    line = [m]
    for beat in range(beats - 1, 0, -1):
        m = best[beat][m][1]
        line.append(m)
    line.reverse()
    return [(m, beat * beat_units, beat_units) for beat, m in enumerate(line)]


def play_sustained(arrangement: Arrangement, stem: str, events: list[Event], start: float, unit: float, instrument,
                   rng: np.random.Generator, gain: float, pan: float = 0.0, reverb: float = 0.3, velocity: float = 0.8,
                   legato: float = 0.035, jitter: float = 0.006, octave: int = 0) -> None:
    """Places a melody for an instrument called as instrument(midi, seconds, velocity, rng)."""
    for midi, at, length in events:
        if midi is None:
            continue
        v = float(np.clip(velocity + rng.uniform(-0.04, 0.04), 0.2, 1.0))
        sound = instrument(midi + 12 * octave, length * unit - legato, v, rng)
        arrangement.add(stem, start + at * unit + rng.uniform(-jitter, jitter), sound, gain, pan=pan, reverb=reverb)


def play_plucked(arrangement: Arrangement, stem: str, events: list[Event], start: float, unit: float, instrument,
                 rng: np.random.Generator, gain: float, pan: float = 0.0, reverb: float = 0.3, velocity: float = 0.75,
                 jitter: float = 0.006, octave: int = 0) -> None:
    """Places notes for an instrument called as instrument(midi, velocity, rng)."""
    for midi, at, length in events:
        if midi is None:
            continue
        v = float(np.clip(velocity + rng.uniform(-0.05, 0.05), 0.2, 1.0))
        arrangement.add(stem, start + at * unit + rng.uniform(-jitter, jitter), instrument(midi + 12 * octave, v, rng), gain, pan=pan, reverb=reverb)


def chord_tone_below(midi: int, chord_pcs: tuple[int, ...]) -> int:
    """Nearest chord tone a third to a sixth below a melody note."""
    for down in range(3, 10):
        if (midi - down) % 12 in chord_pcs:
            return midi - down
    return midi - 12
