"""
Voice synthesis for the ambiences: a sung chant voice and a babbling talker.

Both use one source-filter model rendered additively. The source is a
glottal-like harmonic series: a strong fundamental and a falling spectral
tilt, which gives a soft, breathy voice. A cascade of formant resonances
(the vocal tract) weights each harmonic, and pitch-synchronous aspiration
noise adds breath. Pitch, loudness and formants are smooth control tracks,
so vowels glide into each other the way they do in real singing and speech.
"""

import numpy as np
from scipy import signal

import synth

SR = synth.SR

# Formants F1..F5 in Hz (Peterson & Barney vowel averages; consonants are
# rough nasal and lateral murmurs used only as syllable onsets).
FEMALE = {
    "a": (850, 1220, 2810, 3800, 4950),
    "e": (610, 2330, 2990, 3900, 4950),
    "i": (310, 2790, 3310, 3900, 4950),
    "o": (590, 920, 2710, 3800, 4950),
    "u": (370, 950, 2670, 3800, 4950),
    "m": (300, 1150, 2500, 3600, 4800),
    "n": (300, 1700, 2600, 3600, 4800),
    "l": (380, 1400, 2900, 3800, 4900),
}
MALE = {
    "a": (730, 1090, 2440, 3300, 3750),
    "e": (530, 1840, 2480, 3300, 3750),
    "i": (270, 2290, 3010, 3300, 3750),
    "o": (570, 840, 2410, 3300, 3750),
    "u": (300, 870, 2240, 3300, 3750),
    "m": (260, 1000, 2200, 3200, 3700),
    "n": (260, 1500, 2300, 3200, 3700),
    "l": (330, 1200, 2500, 3300, 3750),
}
BANDWIDTHS = np.array([90.0, 110.0, 170.0, 250.0, 300.0])
NASALS = {"m", "n", "l"}


def formant_gain(freqs: np.ndarray, formants: np.ndarray) -> np.ndarray:
    """Magnitude of a cascade of unity-DC-gain resonances at each frequency."""
    f = freqs[:, None]
    big_f = formants
    response = big_f**2 / np.sqrt((big_f**2 - f**2) ** 2 + (BANDWIDTHS * f) ** 2)
    return np.prod(response, axis=1)


def _smooth_noise(rng: np.random.Generator, n: int, sr: int, hz: float) -> np.ndarray:
    x = signal.sosfiltfilt(signal.butter(2, hz, fs=sr, output="sos"), rng.standard_normal(n + 2 * sr))[sr : sr + n]
    return x / (x.std() + 1e-12)


def render_voice(
    f0: np.ndarray,
    amp: np.ndarray,
    formants: np.ndarray,
    ctrl_step: int,
    sr: int,
    rng: np.random.Generator,
    tilt: float = 1.4,
    h1_boost: float = 1.6,
    breath: float = 0.1,
    max_hz: float = 7000.0,
    tune_f1: bool = False,
) -> np.ndarray:
    """
    Renders a voice from audio-rate pitch `f0` (Hz) and loudness `amp`, and
    control-rate `formants` (one row of F1..F5 every `ctrl_step` samples).
    """
    n = len(f0)
    ctrl_idx = np.minimum(np.arange(len(formants)) * ctrl_step, n - 1)
    f0_ctrl = f0[ctrl_idx]
    formants = formants.copy()
    if tune_f1:
        # Sopranos raise F1 to sit on the fundamental when they sing above it.
        formants[:, 0] = np.maximum(formants[:, 0], f0_ctrl * 1.08)
    phase = 2 * np.pi * np.cumsum(f0) / sr
    out = np.zeros(n)
    top = min(max_hz, sr * 0.45)
    audio_idx = np.arange(n)
    for k in range(1, int(top / max(f0.min(), 40.0)) + 1):
        fk = k * f0_ctrl
        gain = formant_gain(fk, formants) * k ** (-tilt)
        gain *= np.clip((top - fk) / (0.12 * top), 0.0, 1.0)
        if k == 1:
            gain *= h1_boost
        if not gain.any():
            break
        out += np.interp(audio_idx, ctrl_idx, gain) * np.sin(k * phase)
    voiced_rms = np.sqrt(np.mean(out**2)) + 1e-12
    hi = min(6000.0, sr * 0.45)
    aspiration = signal.sosfilt(signal.butter(2, [1200.0, hi], btype="band", fs=sr, output="sos"), rng.standard_normal(n))
    aspiration *= (0.55 + 0.45 * np.cos(phase)) * breath * voiced_rms / (aspiration.std() + 1e-12)
    return (out + aspiration) * amp


def _ramps(tc: np.ndarray, start: np.ndarray, changes: list[tuple[float, float, np.ndarray]]) -> np.ndarray:
    """Builds a control track from a start value and (time, duration, delta) steps."""
    track = np.tile(np.asarray(start, float), (len(tc), 1))
    for when, duration, delta in changes:
        track += synth.smoothstep((tc - when) / duration)[:, None] * delta
    return track


# --- chant ---------------------------------------------------------------------


def sing_phrase(
    syllables: list[tuple[str, str, list[tuple[int, float]]]],
    rng: np.random.Generator,
    formant_scale: float = 1.0,
    detune_cents: float = 0.0,
    vibrato_cents: float = 16.0,
    vibrato_rate: float = 5.2,
    timing_jitter: float = 0.02,
    tilt: float = 1.2,
    h1_boost: float = 1.35,
    breath: float = 0.11,
    release: float = 0.35,
) -> np.ndarray:
    """
    One legato chant phrase for one singer. Each syllable is
    (onset consonant or "", vowel, [(midi, seconds), ...]); several notes on
    one syllable make a melisma.
    """
    notes = [(midi, seconds) for _, _, ns in syllables for midi, seconds in ns]
    boundaries = np.cumsum([0.0] + [seconds for _, seconds in notes])
    jitter = rng.uniform(-timing_jitter, timing_jitter, len(boundaries))
    jitter[0] = jitter[-1] = 0.0
    boundaries = np.maximum.accumulate(boundaries + jitter)
    syllable_starts = []
    index = 0
    for _, _, ns in syllables:
        syllable_starts.append(boundaries[index])
        index += len(ns)
    end = boundaries[-1]
    n = int((end + release) * SR)
    t = np.arange(n) / SR

    midi_track = np.full(n, float(notes[0][0]))
    for j in range(1, len(notes)):
        delta = notes[j][0] - notes[j - 1][0]
        if delta:
            midi_track += delta * synth.smoothstep((t - (boundaries[j] - 0.035)) / 0.075)
    last_boundary = boundaries[np.clip(np.searchsorted(boundaries, t, side="right") - 1, 0, len(boundaries) - 1)]
    depth = vibrato_cents * synth.smoothstep((t - last_boundary - 0.15) / 0.35)
    rate = vibrato_rate * (1.0 + 0.04 * _smooth_noise(rng, n, SR, 0.5))
    vibrato = depth * np.sin(2 * np.pi * np.cumsum(rate) / SR + rng.uniform(0, 2 * np.pi))
    drift = 3.0 * _smooth_noise(rng, n, SR, 1.5) + 0.8 * _smooth_noise(rng, n, SR, 25.0)
    f0 = synth.hz(midi_track + (detune_cents + vibrato + drift) / 100.0)

    amp = synth.smoothstep(t / 0.14) * np.clip((end + release - t) / release, 0.0, 1.0) ** 1.5
    amp *= 0.8 + 0.2 * np.sin(np.pi * np.clip(t / end, 0.0, 1.0))
    for (onset, _, _), start in zip(syllables[1:], syllable_starts[1:]):
        if onset:
            amp *= 1.0 - 0.55 * np.exp(-(((t - start - 0.025) / 0.04) ** 2))
        else:
            amp *= 1.0 - 0.28 * np.exp(-(((t - start) / 0.035) ** 2))
    amp *= 1.0 + 0.03 * _smooth_noise(rng, n, SR, 4.0)

    ctrl_step = 32
    tc = np.arange(n // ctrl_step + 1) * ctrl_step / SR
    first_onset, first_vowel, _ = syllables[0]
    start_shape = np.array(FEMALE[first_onset or first_vowel], float)
    changes = []
    current = start_shape
    if first_onset:
        target = np.array(FEMALE[first_vowel], float)
        changes.append((0.04, 0.07, target - current))
        current = target
    for (onset, vowel, _), start in zip(syllables[1:], syllable_starts[1:]):
        if onset:
            consonant = np.array(FEMALE[onset], float)
            changes.append((start - 0.03, 0.045, consonant - current))
            target = np.array(FEMALE[vowel], float)
            changes.append((start + 0.05, 0.07, target - consonant))
        else:
            target = np.array(FEMALE[vowel], float)
            changes.append((start - 0.04, 0.08, target - current))
        current = target
    formants = _ramps(tc, start_shape, changes) * formant_scale
    return render_voice(f0, amp, formants, ctrl_step, SR, rng, tilt=tilt, h1_boost=h1_boost, breath=breath, tune_f1=True)


def inhale(rng: np.random.Generator, seconds: float = 0.4) -> np.ndarray:
    t = np.arange(int(seconds * SR)) / SR
    x = signal.sosfilt(signal.butter(2, [700.0, 3500.0], btype="band", fs=SR, output="sos"), rng.standard_normal(len(t)))
    return x / (x.std() + 1e-12) * np.sin(np.pi * t / seconds) ** 2


# --- speech babble ------------------------------------------------------------------

_SPEECH_SR = 11025
_UPSAMPLE = SR // _SPEECH_SR
_VOWEL_WEIGHTS = {"a": 0.28, "e": 0.24, "i": 0.18, "o": 0.18, "u": 0.12}


def talker(
    rng: np.random.Generator,
    seconds: float,
    sex: str,
    talkativeness: float = 0.6,
    pitch: float = 1.0,
    max_hz: float = 5000.0,
) -> np.ndarray:
    """
    One person chatting without words: talk spurts of syllables with
    falling intonation, separated by pauses. The result starts and ends in a
    pause, so it can be rotated to any point on a loop without a cut.
    """
    sr = _SPEECH_SR
    n = int(round(seconds * sr))
    table = FEMALE if sex == "f" else MALE
    base_f0 = (rng.uniform(175, 235) if sex == "f" else rng.uniform(92, 135)) * pitch
    scale = rng.uniform(0.94, 1.06)
    ctrl_step = 16
    tc = np.arange(n // ctrl_step + 1) * ctrl_step / sr
    t = np.arange(n) / sr

    f0_ctrl = np.full(len(tc), base_f0)
    amp_ctrl = np.zeros(len(tc))
    formant_changes = []
    current = np.array(table["a"], float)
    start_shape = current.copy()
    unvoiced = np.zeros(n)
    noise = rng.standard_normal(n)
    hiss = signal.sosfilt(signal.butter(2, 3000.0, btype="high", fs=sr, output="sos"), noise)
    burst_band = signal.sosfilt(signal.butter(2, [1500.0, 4000.0], btype="band", fs=sr, output="sos"), noise)

    when = 0.2
    vowels, weights = zip(*_VOWEL_WEIGHTS.items())
    while True:
        spurt = float(np.clip(rng.lognormal(np.log(1.6), 0.5), 0.4, 4.5))
        if when + spurt > seconds - 0.3:
            break
        syllable = when
        rise = rng.uniform(1.08, 1.22)
        while syllable < when + spurt:
            length = rng.uniform(0.11, 0.26)
            u = (syllable - when) / spurt
            accent = rng.uniform(0.92, 1.1)
            target_f0 = base_f0 * (rise - (rise - 0.88) * u) * accent
            seg = (tc >= syllable) & (tc < syllable + length)
            f0_ctrl[seg] = target_f0
            level = rng.uniform(0.55, 1.0)
            local = (tc[seg] - syllable) / length
            amp_ctrl[seg] = level * synth.smoothstep(local / 0.18) * synth.smoothstep((1.0 - local) / 0.2)
            vowel = rng.choice(vowels, p=weights)
            target = np.array(table[vowel], float)
            formant_changes.append((syllable - 0.02, 0.05, target - current))
            current = target
            onset = rng.choice(["", "", "m", "n", "l", "s", "t", "k"])
            i0 = int(syllable * sr)
            if onset == "s":
                k = int(0.07 * sr)
                env = np.sin(np.pi * np.arange(k) / k) ** 2 * level * 0.12
                unvoiced[i0 : i0 + k] += hiss[i0 : i0 + k][: len(env)] * env[: len(unvoiced[i0 : i0 + k])]
                amp_ctrl[seg] *= synth.smoothstep((local - 0.25) / 0.2)
            elif onset in ("t", "k"):
                k = int(0.03 * sr)
                env = np.exp(-np.arange(k) / (0.008 * sr)) * level * 0.15
                unvoiced[i0 : i0 + k] += burst_band[i0 : i0 + k][: len(env)] * env[: len(unvoiced[i0 : i0 + k])]
                amp_ctrl[seg] *= synth.smoothstep((local - 0.12) / 0.15)
            elif onset in NASALS:
                amp_ctrl[seg] *= 1.0 - 0.45 * np.exp(-(((local - 0.05) / 0.12) ** 2))
            syllable += length
        when += spurt + rng.uniform(0.3, 2.6) / talkativeness

    f0_smooth = signal.sosfiltfilt(signal.butter(2, 18.0, fs=sr / ctrl_step, output="sos"), f0_ctrl)
    amp_smooth = signal.sosfiltfilt(signal.butter(2, 60.0, fs=sr / ctrl_step, output="sos"), amp_ctrl).clip(0.0, None)
    idx = np.arange(len(tc)) * ctrl_step
    f0 = np.interp(np.arange(n), idx, f0_smooth) * (1.0 + 0.01 * _smooth_noise(rng, n, sr, 20.0))
    amp = np.interp(np.arange(n), idx, amp_smooth)
    formants = _ramps(tc, start_shape, formant_changes) * scale
    voice = render_voice(f0, amp, formants, ctrl_step, sr, rng, tilt=0.8, h1_boost=0.95, breath=0.06, max_hz=max_hz)
    voice += unvoiced * (np.abs(voice).max() + 1e-12)
    return signal.resample_poly(voice, _UPSAMPLE, 1)[: int(round(seconds * SR))]


def laugh(rng: np.random.Generator, sex: str, count: int | None = None) -> np.ndarray:
    """A burst of "ha"s: aspirated onsets, falling pitch, fading out."""
    sr = _SPEECH_SR
    count = count or int(rng.integers(4, 9))
    spacing = rng.uniform(0.16, 0.21)
    seconds = spacing * count + 0.3
    n = int(seconds * sr)
    table = FEMALE if sex == "f" else MALE
    start_f0 = rng.uniform(300, 360) if sex == "f" else rng.uniform(170, 210)
    ctrl_step = 16
    tc = np.arange(n // ctrl_step + 1) * ctrl_step / sr
    amp_ctrl = np.zeros(len(tc))
    f0_ctrl = np.full(len(tc), start_f0)
    breath_env = np.zeros(n)
    t = np.arange(n) / sr
    for i in range(count):
        at = i * spacing * rng.uniform(0.95, 1.05)
        level = (0.6 + 0.4 * min(1.0, i / 1.5)) * 0.88**i
        seg = (tc >= at + 0.035) & (tc < at + 0.035 + 0.09)
        local = (tc[seg] - at - 0.035) / 0.09
        amp_ctrl[seg] = level * np.sin(np.pi * local) ** 0.7
        f0_ctrl[seg] = start_f0 * 0.965**i * (1.0 - 0.06 * local)
        breath_env += level * np.exp(-(((t - at - 0.02) / 0.025) ** 2))
    f0 = np.interp(np.arange(n), np.arange(len(tc)) * ctrl_step, signal.sosfiltfilt(signal.butter(2, 25.0, fs=sr / ctrl_step, output="sos"), f0_ctrl))
    amp = np.interp(np.arange(n), np.arange(len(tc)) * ctrl_step, amp_ctrl)
    formants = np.tile(np.array(table["a"], float) * rng.uniform(0.95, 1.05), (len(tc), 1))
    voice = render_voice(f0, amp, formants, ctrl_step, sr, rng, tilt=1.2, h1_boost=1.2, breath=0.25, max_hz=4500.0)
    aspiration = signal.sosfilt(signal.butter(2, [800.0, 4500.0], btype="band", fs=sr, output="sos"), rng.standard_normal(n))
    voice += aspiration / aspiration.std() * breath_env * (np.abs(voice).max() * 0.2)
    return signal.resample_poly(voice, _UPSAMPLE, 1)
