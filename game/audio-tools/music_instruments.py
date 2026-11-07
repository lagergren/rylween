"""
Extra soundtrack instruments built on synth.py: plucked lute and bass,
horn, bowed strings, choir, timpani, bells, tambourine, and loop-length
textures (drones, wind) that are periodic over the loop so they never seam.
"""

import numpy as np
from scipy import signal

from synth import SR, hz, smoothstep


def _t(seconds: float) -> np.ndarray:
    return np.arange(int(seconds * SR)) / SR


def _release_env(t: np.ndarray, seconds: float, attack: float, release: float) -> np.ndarray:
    return smoothstep(t / attack) * np.clip(1.0 - (t - seconds) / release, 0.0, 1.0)


def blep_saw(freq: np.ndarray, phase0: float = 0.0) -> np.ndarray:
    """Band-limited (polyBLEP) sawtooth for a per-sample frequency array."""
    dt = freq / SR
    phase = (phase0 + np.cumsum(dt) - dt[0]) % 1.0
    saw = 2.0 * phase - 1.0
    low = phase < dt
    x = phase[low] / dt[low]
    saw[low] -= x + x - x * x - 1.0
    high = phase > 1.0 - dt
    x = (phase[high] - 1.0) / dt[high]
    saw[high] -= x * x + x + x + 1.0
    return saw


def pluck(
    midi: float,
    velocity: float,
    rng: np.random.Generator,
    ring: float = 1.6,
    t60_low: float = 2.5,
    t60_high: float = 0.8,
    position: float = 0.2,
    brightness: float = 1.0,
) -> np.ndarray:
    """Karplus-Strong string with a fractional-delay loop filter (lute, gut bass)."""
    f = hz(midi)
    period = SR / f
    n_delay = int(np.floor(period - 1.0))
    frac = period - 1.0 - n_delay
    loop = np.convolve([0.25, 0.5, 0.25], [1.0 - frac, frac])
    t60 = float(np.interp(midi, [28, 84], [t60_low, t60_high]))
    gain = 10.0 ** (-3.0 / (f * t60))
    a = np.zeros(n_delay + len(loop))
    a[0] = 1.0
    a[n_delay:] -= gain * loop
    burst = rng.uniform(-1.0, 1.0, n_delay)
    shift = max(1, int(position * n_delay))
    burst = burst - np.concatenate([np.zeros(shift), burst[:-shift]])
    cutoff = min(0.95, (900.0 + 5000.0 * velocity * brightness) / (SR / 2))
    b_lp, a_lp = signal.butter(1, cutoff)
    burst = signal.lfilter(b_lp, a_lp, burst)
    burst -= burst.mean()
    excitation = np.zeros(int(ring * SR))
    excitation[:n_delay] = burst
    out = signal.lfilter([1.0], a, excitation)
    fade = int(0.06 * SR)
    out[-fade:] *= np.linspace(1.0, 0.0, fade)
    return out / max(1e-9, np.abs(out).max()) * velocity


def lute(midi: float, velocity: float, rng: np.random.Generator, ring: float = 1.6) -> np.ndarray:
    return pluck(midi, velocity, rng, ring=ring, t60_low=2.2, t60_high=0.9, position=0.18, brightness=1.0)


def gut_bass(midi: float, velocity: float, rng: np.random.Generator, ring: float = 1.4) -> np.ndarray:
    """Plucked low string for walking bass lines, with a little sine body for weight."""
    string = pluck(midi, velocity, rng, ring=ring, t60_low=1.6, t60_high=1.0, position=0.25, brightness=0.35)
    t = _t(ring)
    body = np.sin(2 * np.pi * hz(midi) * t) * np.exp(-t / 0.45) * smoothstep(t / 0.004) * velocity
    return 0.75 * string + 0.45 * body


def horn(midi: float, seconds: float, velocity: float, rng: np.random.Generator, release: float = 0.28) -> np.ndarray:
    """French-horn-like brass: two band-limited saws whose brightness follows the attack."""
    f = hz(midi)
    t = _t(seconds + release)
    env = _release_env(t, seconds, 0.09, release) * (0.85 + 0.15 * np.exp(-t / 0.3))
    vibrato = 0.0028 * smoothstep((t - 0.35) / 0.4) * np.sin(2 * np.pi * (4.6 + rng.uniform(-0.2, 0.2)) * t)
    scoop = -0.008 * np.exp(-t / 0.04)
    freq = f * (1.0 + vibrato + scoop)
    tone = 0.5 * (blep_saw(freq * 2 ** (-4 / 1200), rng.random()) + blep_saw(freq * 2 ** (4 / 1200), rng.random()))
    dark = signal.sosfilt(signal.butter(2, min(2.2 * f, 9000.0), fs=SR, output="sos"), tone)
    bright = signal.sosfilt(signal.butter(2, min(5.5 * f, 12000.0), fs=SR, output="sos"), tone)
    shine = smoothstep(t / 0.03) * velocity * (0.35 + 0.65 * np.exp(-t / 0.18))
    out = env * (dark * (1.0 - shine) + bright * shine)
    return out * velocity * 1.6


def bowed(
    midi: float,
    seconds: float,
    velocity: float,
    rng: np.random.Generator,
    attack: float = 0.14,
    release: float = 0.3,
    vibrato: float = 0.0035,
    brightness: float = 4.0,
) -> np.ndarray:
    """Bowed string (cello / viola / violin section voice)."""
    f = hz(midi)
    t = _t(seconds + release)
    env = _release_env(t, seconds, attack, release)
    vib = vibrato * smoothstep((t - 0.2) / 0.3) * np.sin(2 * np.pi * (5.2 + rng.uniform(-0.3, 0.3)) * t + rng.random() * 6.28)
    tone = blep_saw(f * (1.0 + vib), rng.random())
    tone = signal.sosfilt(signal.butter(2, min(brightness * f + 400.0, 9000.0), fs=SR, output="sos"), tone)
    body = signal.sosfilt(signal.butter(2, [250.0, 1200.0], btype="band", fs=SR, output="sos"), tone)
    return env * (0.8 * tone + 0.5 * body) * velocity


def staccato_strings(midi: float, velocity: float, rng: np.random.Generator, length: float = 0.16) -> np.ndarray:
    """Short détaché string-section note for ostinatos (three detuned voices)."""
    f = hz(midi)
    t = _t(length + 0.2)
    env = smoothstep(t / 0.012) * np.exp(-np.clip(t - 0.02, 0, None) / 0.09) * np.clip(1.0 - (t - length) / 0.2, 0.0, 1.0)
    tone = sum(blep_saw(np.full(len(t), f * 2 ** (c / 1200)), rng.random()) for c in (-7.0, 0.0, 6.0)) / 3.0
    tone = signal.sosfilt(signal.butter(2, min(5.0 * f + 500.0, 8000.0), fs=SR, output="sos"), tone)
    return env * tone * velocity


_VOWELS = {
    "ah": ((700.0, 1.0, 110.0), (1220.0, 0.5, 120.0), (2600.0, 0.22, 160.0)),
    "oh": ((450.0, 1.0, 90.0), (800.0, 0.55, 100.0), (2830.0, 0.12, 150.0)),
    "oo": ((320.0, 1.0, 80.0), (870.0, 0.3, 100.0), (2250.0, 0.08, 140.0)),
}


def choir(
    midis: list[float],
    seconds: float,
    rng: np.random.Generator,
    vowel: str = "ah",
    voices: int = 3,
    attack: float = 0.6,
    release: float = 0.9,
) -> tuple[np.ndarray, np.ndarray]:
    """Choir pad: detuned voices with independent vibrato through vowel formants, spread in stereo."""
    t = _t(seconds + release)
    left = np.zeros(len(t))
    right = np.zeros(len(t))
    for midi in midis:
        f = hz(midi)
        for v in range(voices):
            rate = 4.6 + rng.uniform(-0.5, 0.5)
            depth = 0.004 + rng.uniform(0.0, 0.002)
            drift = rng.uniform(-5.0, 5.0) / 1200.0
            vib = depth * smoothstep(t / 0.5) * np.sin(2 * np.pi * rate * t + rng.random() * 6.28)
            voice = blep_saw(f * (1.0 + drift + vib), rng.random())
            pan = (v / max(1, voices - 1)) if voices > 1 else 0.5
            left += voice * np.cos(pan * np.pi / 2)
            right += voice * np.sin(pan * np.pi / 2)
    out = []
    for ch in (left, right):
        shaped = np.zeros(len(t))
        for center, gain, width in _VOWELS[vowel]:
            band = signal.butter(2, [center - width, center + width], btype="band", fs=SR, output="sos")
            shaped += gain * signal.sosfilt(band, ch)
        out.append(shaped)
    env = _release_env(t, seconds, attack, release) / (len(midis) * voices) * 2.4
    return out[0] * env, out[1] * env


def timpani(midi: float, velocity: float, rng: np.random.Generator, ring: float = 3.0) -> np.ndarray:
    """Tuned kettle drum: the membrane's musical modes plus a felt-mallet thump."""
    f = hz(midi)
    t = _t(ring)
    glide = 1.0 + 0.012 * np.exp(-t / 0.05)
    out = np.zeros(len(t))
    for ratio, gain, decay in ((1.0, 1.0, 1.1), (1.5, 0.55, 0.7), (1.98, 0.32, 0.5), (2.44, 0.18, 0.35), (2.95, 0.1, 0.25)):
        phase = 2 * np.pi * np.cumsum(f * ratio * glide) / SR
        out += gain * np.sin(phase + rng.random()) * np.exp(-t / decay)
    thump = signal.sosfilt(signal.butter(2, 400.0, fs=SR, output="sos"), rng.standard_normal(len(t))) * np.exp(-t / 0.025)
    out = (out + 1.5 * thump * velocity) * smoothstep(t / 0.002)
    fade = int(0.1 * SR)
    out[-fade:] *= np.linspace(1.0, 0.0, fade)
    return out / max(1e-9, np.abs(out).max()) * velocity


_BELLS = {
    # (ratio to the struck note, gain, decay seconds)
    "church": ((0.5, 0.5, 6.0), (1.0, 1.0, 3.5), (1.19, 0.6, 2.5), (1.5, 0.35, 2.0), (2.0, 0.5, 1.6), (2.5, 0.2, 1.0), (3.0, 0.15, 0.8), (4.07, 0.08, 0.5)),
    "glock": ((1.0, 1.0, 1.6), (2.76, 0.25, 0.4), (5.4, 0.1, 0.15)),
    "bowl": ((1.0, 1.0, 7.0), (1.003, 0.8, 7.0), (2.71, 0.35, 3.0), (2.716, 0.3, 3.0), (5.15, 0.12, 1.2)),
}


def bell(midi: float, velocity: float, rng: np.random.Generator, kind: str = "glock", ring: float | None = None) -> np.ndarray:
    """Struck metal: church bell, glockenspiel/celesta, or singing bowl."""
    partials = _BELLS[kind]
    ring = ring or max(decay for _, _, decay in partials) * 2.2
    f = hz(midi)
    t = _t(ring)
    out = np.zeros(len(t))
    for ratio, gain, decay in partials:
        if f * ratio < SR / 2.2:
            out += gain * np.sin(2 * np.pi * f * ratio * t + rng.random() * 6.28) * np.exp(-t / decay)
    attack = 0.0015 if kind != "bowl" else 0.25
    out *= smoothstep(t / attack)
    fade = int(0.1 * SR)
    out[-fade:] *= np.linspace(1.0, 0.0, fade)
    return out / max(1e-9, np.abs(out).max()) * velocity


def tambourine(velocity: float, rng: np.random.Generator) -> np.ndarray:
    t = _t(0.22)
    noise = rng.standard_normal(len(t))
    jingles = np.zeros(len(t))
    for center in (4300.0, 6100.0, 7900.0, 10200.0):
        band = signal.butter(2, [center * 0.94, center * 1.06], btype="band", fs=SR, output="sos")
        jingles += signal.sosfilt(band, noise)
    return jingles * smoothstep(t / 0.003) * np.exp(-t / 0.06) * velocity * 2.0


def drip(midi: float, velocity: float, rng: np.random.Generator) -> np.ndarray:
    """Water drop: a sine that bends upward as the bubble closes."""
    f = hz(midi)
    t = _t(0.35)
    rise = 1.0 + 0.45 * smoothstep(t / 0.045)
    phase = 2 * np.pi * np.cumsum(f * rise) / SR
    tone = np.sin(phase) * np.exp(-t / 0.07) * smoothstep(t / 0.0015)
    click = signal.sosfilt(signal.butter(2, 3000.0, btype="high", fs=SR, output="sos"), rng.standard_normal(len(t))) * np.exp(-t / 0.002) * 0.15
    return (tone + click) * velocity


# --- loop-length textures (periodic over the loop, so they never seam) ------


def _loop_freq(f: float, loop: int) -> float:
    """Nearest frequency with a whole number of cycles in the loop."""
    return max(1, round(f * loop / SR)) * SR / loop


def drone(midis: list[float], loop: int, rng: np.random.Generator, cutoff: float = 700.0, swell_cycles: int = 3) -> tuple[np.ndarray, np.ndarray]:
    """Sustained saw drone filtered circularly, with a slow periodic swell."""
    t = np.arange(loop) / SR
    out = []
    for _ in range(2):
        x = np.zeros(loop)
        for midi in midis:
            for cents in (-5.0, 5.0):
                f = _loop_freq(hz(midi) * 2 ** (cents / 1200), loop)
                x += blep_saw(np.full(loop, f), rng.random())
        spectrum = np.fft.rfft(x)
        freqs = np.fft.rfftfreq(loop, 1 / SR)
        spectrum /= np.sqrt(1.0 + (freqs / cutoff) ** 4)
        x = np.fft.irfft(spectrum, loop)
        swell = 0.75 + 0.25 * np.sin(2 * np.pi * swell_cycles * t / (loop / SR) + rng.random() * 6.28)
        out.append(x * swell / (2 * len(midis)))
    return out[0], out[1]


def wind(loop: int, rng: np.random.Generator, bands=((280.0, 1.0, 3), (650.0, 0.55, 5), (1400.0, 0.25, 7))) -> tuple[np.ndarray, np.ndarray]:
    """Periodic wind: noise bands shaped in the frequency domain, each swelling at its own slow rate."""
    t = np.arange(loop) / SR
    seconds = loop / SR
    freqs = np.fft.rfftfreq(loop, 1 / SR)
    out = []
    for _ in range(2):
        x = np.zeros(loop)
        for center, gain, cycles in bands:
            spectrum = np.fft.rfft(rng.standard_normal(loop))
            spectrum *= np.exp(-0.5 * (np.log(np.maximum(freqs, 1.0) / center) / 0.35) ** 2)
            band = np.fft.irfft(spectrum, loop)
            band /= np.sqrt(np.mean(band**2))
            swell = 0.55 + 0.45 * np.sin(2 * np.pi * cycles * t / seconds + rng.random() * 6.28)
            x += gain * band * swell**2
        out.append(x)
    return out[0], out[1]


def organ(midis: list[float], seconds: float, rng: np.random.Generator, release: float = 0.35) -> tuple[np.ndarray, np.ndarray]:
    """Soft pipe-organ chord: additive 8', 4', 2 2/3' and 2' ranks with a gentle chiff, slightly chorused."""
    t = _t(seconds + release)
    env = _release_env(t, seconds, 0.07, release)
    left = np.zeros(len(t))
    right = np.zeros(len(t))
    for midi in midis:
        f = hz(midi)
        for ratio, gain in ((1.0, 1.0), (2.0, 0.45), (3.0, 0.18), (4.0, 0.2)):
            for target, cents in ((left, -2.0), (right, 2.0)):
                target += gain * np.sin(2 * np.pi * f * ratio * 2 ** (cents / 1200) * t + rng.random() * 6.28)
    chiff = signal.sosfilt(signal.butter(2, [1500.0, 4000.0], btype="band", fs=SR, output="sos"), rng.standard_normal(len(t))) * np.exp(-t / 0.03) * 0.15
    scale = env / (1.8 * len(midis))
    return (left + chiff) * scale, (right + chiff) * scale
