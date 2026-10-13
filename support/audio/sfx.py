"""
Procedural sound effects for Adventurers of Rylween.

Every effect is built from a handful of primitives: noise through moving
filters (swishes, squelches, wind), decaying sine partials (metal, wood,
glass, bells), pitch sweeps (thumps, zaps, bubbles) and scattered grains
(coins, chain mail, crackle, dice). Effects are mono; render_sfx.py
normalizes and encodes them.
"""

import numpy as np
from scipy import signal

import synth
from synth import smoothstep

SR = synth.SR


# --- primitives ------------------------------------------------------------


def times(seconds: float) -> np.ndarray:
    return np.arange(int(round(seconds * SR))) / SR


def white(rng: np.random.Generator, seconds: float) -> np.ndarray:
    return rng.standard_normal(int(round(seconds * SR)))


def lowpass(x: np.ndarray, hz: float, order: int = 2) -> np.ndarray:
    return signal.sosfilt(signal.butter(order, hz, fs=SR, output="sos"), x)


def highpass(x: np.ndarray, hz: float, order: int = 2) -> np.ndarray:
    return signal.sosfilt(signal.butter(order, hz, btype="high", fs=SR, output="sos"), x)


def bandpass(x: np.ndarray, lo: float, hi: float, order: int = 2) -> np.ndarray:
    return signal.sosfilt(signal.butter(order, [lo, hi], btype="band", fs=SR, output="sos"), x)


def svf(x: np.ndarray, cutoff, q: float = 0.707, mode: str = "band") -> np.ndarray:
    """State-variable filter whose cutoff may change every sample (unity-peak band output)."""
    n = len(x)
    g = np.tan(np.pi * np.clip(np.broadcast_to(np.asarray(cutoff, float), (n,)), 20.0, SR * 0.45) / SR)
    k = 1.0 / q
    a1 = 1.0 / (1.0 + g * (g + k))
    a2 = g * a1
    a3 = g * a2
    xs, a1s, a2s, a3s = x.tolist(), a1.tolist(), a2.tolist(), a3.tolist()
    out = [0.0] * n
    ic1 = ic2 = 0.0
    for i in range(n):
        v3 = xs[i] - ic2
        v1 = a1s[i] * ic1 + a2s[i] * v3
        v2 = ic2 + a2s[i] * ic1 + a3s[i] * v3
        ic1 = 2.0 * v1 - ic1
        ic2 = 2.0 * v2 - ic2
        if mode == "low":
            out[i] = v2
        elif mode == "band":
            out[i] = k * v1
        else:
            out[i] = xs[i] - k * v1 - v2
    return np.array(out)


def env_decay(seconds: float, tau: float, attack: float = 0.001) -> np.ndarray:
    t = times(seconds)
    return smoothstep(t / attack) * np.exp(-t / tau)


def env_bell(seconds: float, peak: float, width: float) -> np.ndarray:
    """Gaussian swell centered at `peak` (fraction of the sound)."""
    u = times(seconds) / seconds
    return np.exp(-(((u - peak) / width) ** 2))


def phase_of(freqs: np.ndarray) -> np.ndarray:
    return 2 * np.pi * np.cumsum(freqs) / SR


def slow_random(rng: np.random.Generator, seconds: float, hz: float) -> np.ndarray:
    """Smooth random wander with unit standard deviation."""
    x = lowpass(white(rng, seconds), hz, order=2)
    return x / (x.std() + 1e-12)


def thump(f_start: float, f_end: float, tau: float, sweep: float = 0.03, seconds: float | None = None) -> np.ndarray:
    t = times(seconds or tau * 7)
    f = f_end + (f_start - f_end) * np.exp(-t / sweep)
    return np.sin(phase_of(f)) * smoothstep(t / 0.001) * np.exp(-t / tau)


def modes(freqs, taus, amps, rng: np.random.Generator, seconds: float | None = None, attack: float = 0.0004) -> np.ndarray:
    """Struck object: a sum of exponentially decaying sine partials."""
    t = times(seconds or max(taus) * 6)
    out = np.zeros(len(t))
    for f, tau, a in zip(freqs, taus, amps):
        if f < SR * 0.45:
            out += a * np.sin(2 * np.pi * f * t + rng.uniform(0, 2 * np.pi)) * np.exp(-t / tau)
    return out * smoothstep(t / attack)


def click(rng: np.random.Generator, tau: float = 0.0008, hp: float = 1500.0) -> np.ndarray:
    return highpass(white(rng, tau * 10), hp) * np.exp(-times(tau * 10) / tau)


def burst(rng, seconds, tau, lo=None, hi=None, attack=0.001) -> np.ndarray:
    x = white(rng, seconds)
    if lo and hi:
        x = bandpass(x, lo, hi)
    elif hi:
        x = lowpass(x, hi)
    elif lo:
        x = highpass(x, lo)
    return x * env_decay(seconds, tau, attack)


def flange(x: np.ndarray, base_ms: float = 1.0, depth_ms: float = 4.0, rate: float = 0.6, mix: float = 0.8) -> np.ndarray:
    n = np.arange(len(x))
    d = (base_ms + depth_ms * (0.5 + 0.5 * np.sin(2 * np.pi * rate * n / SR))) * SR / 1000.0
    return x + mix * np.interp(n - d, n, x, left=0.0)


def room(x: np.ndarray, rng: np.random.Generator, rt60: float = 0.5, wet: float = 0.15, tone: float = 5000.0) -> np.ndarray:
    t = times(rt60 * 1.3)
    ir = lowpass(white(rng, rt60 * 1.3), tone) * np.exp(-6.91 * t / rt60) * smoothstep((t - 0.004) / 0.01)
    ir /= np.sqrt(np.sum(ir**2))
    wet_part = signal.fftconvolve(x, ir)
    out = np.zeros(len(wet_part))
    out[: len(x)] = x
    return out + wet * wet_part


class Canvas:
    """Fixed-length mono buffer that sounds are dropped onto."""

    def __init__(self, seconds: float):
        self.buf = np.zeros(int(round(seconds * SR)))

    def add(self, at: float, sound: np.ndarray, gain: float = 1.0) -> "Canvas":
        i = int(round(at * SR))
        end = min(len(self.buf), i + len(sound))
        if end > i:
            self.buf[i:end] += sound[: end - i] * gain
        return self


# --- tonal building blocks ---------------------------------------------------

_RISSET = [
    (0.56, 0.0, 1.0, 1.0), (0.56, 1.0, 0.67, 0.9), (0.92, 0.0, 1.0, 0.65), (0.92, 1.7, 1.8, 0.55),
    (1.19, 0.0, 2.67, 0.325), (1.7, 0.0, 1.67, 0.35), (2.0, 0.0, 1.46, 0.25), (2.74, 0.0, 1.33, 0.2),
    (3.0, 0.0, 1.33, 0.15), (3.76, 0.0, 1.0, 0.1), (4.07, 0.0, 1.33, 0.075),
]


def bell(freq: float, seconds: float, rng: np.random.Generator) -> np.ndarray:
    """Church bell (Risset's inharmonic partial recipe)."""
    t = times(seconds)
    out = np.zeros(len(t))
    for ratio, offset, amp, dur in _RISSET:
        f = freq * ratio + offset
        if f < SR * 0.45:
            out += amp * np.sin(2 * np.pi * f * t + rng.uniform(0, 2 * np.pi)) * np.exp(-4.6 * t / (seconds * dur))
    return out * smoothstep(t / 0.001) / 10.0


def chime(freq: float, seconds: float, rng: np.random.Generator) -> np.ndarray:
    """Tuned glockenspiel bar: clear pitch with bright inharmonic overtones."""
    return modes(
        [freq, freq * 2.76, freq * 5.40, freq * 8.93],
        [seconds * 0.45, seconds * 0.18, seconds * 0.08, seconds * 0.04],
        [1.0, 0.35, 0.12, 0.05],
        rng,
        seconds=seconds,
    )


def saw(freq, seconds: float, max_hz: float = 6000.0, rng=None) -> np.ndarray:
    """Band-limited sawtooth; `freq` may be a per-sample array."""
    n = int(round(seconds * SR))
    f = np.broadcast_to(np.asarray(freq, float), (n,))
    ph = phase_of(f) + (rng.uniform(0, 2 * np.pi) if rng is not None else 0.0)
    out = np.zeros(n)
    for k in range(1, int(max_hz / max(1.0, f.max())) + 1):
        out += np.sin(k * ph) / k
    return out


def horn(freq: float, seconds: float, rng: np.random.Generator, attack: float = 0.08, release: float = 0.15, glide_from: float | None = None) -> np.ndarray:
    """Brass-like horn: gets brighter as it gets louder, with delayed vibrato."""
    t = times(seconds + release)
    env = smoothstep(t / attack) * np.clip(1.0 - (t - seconds) / release, 0.0, 1.0)
    f = np.full(len(t), freq)
    if glide_from:
        f = freq + (glide_from - freq) * np.exp(-t / 0.05)
    f = f * (1.0 + 0.004 * smoothstep((t - 0.25) / 0.3) * np.sin(2 * np.pi * 5.2 * t))
    ph = phase_of(f)
    out = np.zeros(len(t))
    for k in range(1, 40):
        if freq * k > 7000:
            break
        out += np.sin(k * ph) * np.exp(-(k - 1) * (0.6 - 0.38 * env)) / k
    out = lowpass(out * env, 2600.0)
    out += lowpass(white(rng, seconds + release), 1800.0) * env * 0.02
    return out / (np.abs(out).max() + 1e-12)


def choir(midis: list[int], seconds: float, rng: np.random.Generator, attack: float = 0.35, release: float = 0.5, vowel=(800, 1150, 2900)) -> np.ndarray:
    """Formant-filtered voices singing an "ah"."""
    total = seconds + release
    t = times(total)
    voices = np.zeros(len(t))
    for midi in midis:
        f = synth.hz(midi)
        for cents in (-8.0, 0.0, 7.0):
            vib = 1.0 + 0.003 * np.sin(2 * np.pi * rng.uniform(4.6, 5.6) * t + rng.uniform(0, 6.3))
            voices += saw(f * 2 ** (cents / 1200) * vib, total, max_hz=5000.0, rng=rng)
    formants = sum(g * bandpass(voices, fc / 1.18, fc * 1.18) for fc, g in zip(vowel, (1.0, 0.55, 0.22)))
    env = smoothstep(t / attack) * np.clip(1.0 - (t - seconds) / release, 0.0, 1.0)
    out = formants * env
    return out / (np.abs(out).max() + 1e-12)


def stab(midis: list[int], seconds: float, rng: np.random.Generator) -> np.ndarray:
    """Short orchestral string stab whose filter closes as it decays."""
    t = times(seconds)
    out = np.zeros(len(t))
    for midi in midis:
        for cents in (-6.0, 6.0):
            out += saw(synth.hz(midi) * 2 ** (cents / 1200), seconds, rng=rng)
    cutoff = 600.0 + 4200.0 * np.exp(-t / 0.12)
    out = svf(out, cutoff, 0.9, "low") * smoothstep(t / 0.006) * np.exp(-t / (seconds * 0.4))
    return out / (np.abs(out).max() + 1e-12)


def harp_notes(names: list[str], step: float, rng: np.random.Generator, velocity: float = 0.8, ring: float = 1.6) -> np.ndarray:
    c = Canvas(step * len(names) + ring)
    for i, name in enumerate(names):
        c.add(i * step, synth.harp(synth.note(name), velocity, rng, ring=ring))
    return c.buf


# --- material sounds -----------------------------------------------------------


def swish(rng, seconds=0.3, f_lo=350.0, f_hi=2600.0, q=1.6, weight=0.5, peak=0.42) -> np.ndarray:
    """Air displaced by a swung weapon: band-passed noise sweeping up and back down."""
    env = env_bell(seconds, peak, 0.2)
    center = f_lo + (f_hi - f_lo) * env_bell(seconds, peak + 0.03, 0.22)
    air = svf(white(rng, seconds), center, q, "band")
    whoom = svf(white(rng, seconds), 120.0 + 260.0 * env, 0.8, "low")
    return (air + weight * whoom) * env


def squelch(rng, seconds=0.16, start=2500.0, end=260.0, q=6.0, tau=0.09) -> np.ndarray:
    """Wet flesh: a resonant low-pass snapping shut."""
    t = times(seconds)
    cutoff = end + (start - end) * np.exp(-t / (seconds * 0.25))
    return svf(white(rng, seconds), cutoff, q, "low") * smoothstep(t / 0.002) * np.exp(-t / tau)


def wet_grains(rng, seconds=0.16, count=24) -> np.ndarray:
    c = Canvas(seconds + 0.01)
    for _ in range(count):
        at = rng.exponential(seconds * 0.3)
        if at < seconds:
            grain = svf(white(rng, 0.004), rng.uniform(900, 2800), 4.0, "band") * env_decay(0.004, 0.001)
            c.add(at, grain, rng.uniform(0.05, 0.22) * np.exp(-at / (seconds * 0.5)))
    return c.buf


def wood_knock(rng, base=180.0, tau=0.1) -> np.ndarray:
    return modes([base, base * 2.3, base * 4.4, base * 7.1], [tau, tau * 0.6, tau * 0.35, tau * 0.2], [1.0, 0.6, 0.3, 0.12], rng)


def metal_hit(rng, base=620.0, tau=0.25, ratios=(1.0, 2.32, 3.82, 5.5, 7.86)) -> np.ndarray:
    taus = [tau * f for f in (1.0, 0.72, 0.5, 0.36, 0.25)][: len(ratios)]
    amps = [1.0, 0.8, 0.55, 0.35, 0.2][: len(ratios)]
    ring = modes([base * r * rng.uniform(0.99, 1.01) for r in ratios], taus, amps, rng)
    return Canvas(len(ring) / SR).add(0.0, ring).add(0.0, click(rng, 0.0006, 2000.0), 1.2).buf


def metal_grain(rng) -> np.ndarray:
    """One tiny ring of chain mail, a buckle or a coin edge."""
    return modes(
        [rng.uniform(3000, 7500) for _ in range(3)],
        [rng.uniform(0.005, 0.03) for _ in range(3)],
        [1.0, rng.uniform(0.3, 0.8), rng.uniform(0.2, 0.5)],
        rng,
        seconds=0.12,
    )


def coin(rng) -> np.ndarray:
    base = rng.uniform(2700, 4200)
    ring = modes([base, base * 1.53, base * 2.21, base * 2.9], [0.22, 0.14, 0.09, 0.05], [1.0, 0.7, 0.4, 0.2], rng, seconds=0.6)
    return Canvas(0.6).add(0.0, ring).add(0.0, click(rng, 0.0004, 3000.0), 0.6).buf


def ceramic(rng) -> np.ndarray:
    return modes([rng.uniform(2000, 2400), rng.uniform(4600, 5200), rng.uniform(7600, 8400)], [0.05, 0.03, 0.015], [1.0, 0.5, 0.25], rng, seconds=0.25)


def chain_jingle(rng, seconds=0.7, count=55, peaks=((0.25, 0.12), (0.65, 0.15))) -> np.ndarray:
    c = Canvas(seconds + 0.15)
    for _ in range(count):
        center, width = peaks[rng.integers(len(peaks))]
        at = float(np.clip(rng.normal(center, width), 0.0, 1.0)) * seconds
        c.add(at, metal_grain(rng), rng.uniform(0.15, 0.6))
    return c.buf


def creak(rng, seconds, rates=(35.0, 75.0, 50.0), resonances=(520, 1100, 1900, 3100), jitter=0.25, q=1.18) -> np.ndarray:
    """Wood creak: stick-slip pulses at a wandering rate through resonant wood."""
    t = times(seconds)
    rate = np.interp(t / seconds, np.linspace(0, 1, len(rates)), rates)
    rate *= 1.0 + jitter * 0.4 * slow_random(rng, seconds, 9.0)
    ph = np.cumsum(np.maximum(rate, 5.0)) / SR
    pulses = np.zeros(len(t))
    hits = np.nonzero(np.diff(np.floor(ph)) > 0)[0]
    pulses[hits] = rng.uniform(0.5, 1.0, len(hits))
    body = sum(bandpass(pulses, f / q, f * q) / np.sqrt(i + 1) for i, f in enumerate(resonances))
    friction = bandpass(white(rng, seconds), 1500, 5000) * 0.02
    env = smoothstep(t / (seconds * 0.15)) * smoothstep((seconds - t) / (seconds * 0.2))
    return (body + friction) * env


def rustle(rng, seconds=0.6, lo=1500.0, hi=6000.0) -> np.ndarray:
    t = times(seconds)
    shape = np.abs(slow_random(rng, seconds, 14.0)) * smoothstep(t / 0.05) * smoothstep((seconds - t) / 0.15)
    return bandpass(white(rng, seconds), lo, hi) * shape


def bubble(rng, f0: float, f1: float, seconds: float = 0.06) -> np.ndarray:
    t = times(seconds)
    f = f0 + (f1 - f0) * smoothstep(t / seconds)
    return np.sin(phase_of(f)) * smoothstep(t / 0.003) * np.exp(-t / (seconds * 0.45))


def crackle(rng, seconds, count, lo=900.0, hi=4500.0) -> np.ndarray:
    c = Canvas(seconds + 0.02)
    for _ in range(count):
        pop = svf(white(rng, 0.006), rng.uniform(lo, hi), 3.0, "band") * env_decay(0.006, rng.uniform(0.0006, 0.002))
        c.add(rng.uniform(0.0, seconds), pop, rng.uniform(0.1, 1.0) ** 2)
    return c.buf


def cymbal(rng, seconds: float = 1.4) -> np.ndarray:
    t = times(seconds)
    hiss = highpass(white(rng, seconds), 3500.0) * np.exp(-t / (seconds * 0.3))
    ring = modes([rng.uniform(3300, 9000) for _ in range(10)], [seconds * rng.uniform(0.15, 0.3) for _ in range(10)], [rng.uniform(0.2, 0.6) for _ in range(10)], rng, seconds=seconds)
    return (hiss * 0.6 + ring * 0.15) * smoothstep(t / 0.002)


def rumble(rng, seconds: float, cutoff: float = 150.0, shake: float = 0.0, attack: float = 0.3, release: float = 0.6) -> np.ndarray:
    t = times(seconds)
    x = lowpass(white(rng, seconds), cutoff) * (0.6 + 0.4 * np.abs(slow_random(rng, seconds, 6.0)))
    if shake:
        x *= 1.0 + 0.5 * np.sin(2 * np.pi * shake * t)
    return x * smoothstep(t / attack) * smoothstep((seconds - t) / release)


def drip(rng, f0: float = 700.0, f1: float = 1900.0) -> np.ndarray:
    t = times(0.08)
    f = f0 + (f1 - f0) * smoothstep(t / 0.018)
    return np.sin(phase_of(f)) * smoothstep(t / 0.001) * np.exp(-t / 0.03)


def chirp(rng, base: float) -> np.ndarray:
    """One bird call: a fast up-down frequency flick repeated a few times."""
    c = Canvas(0.7)
    at = 0.0
    for _ in range(rng.integers(3, 6)):
        seconds = rng.uniform(0.05, 0.08)
        t = times(seconds)
        f = base * (1.0 + 0.45 * np.sin(np.pi * t / seconds) ** 2) * (1.0 + 0.03 * np.sin(2 * np.pi * 60 * t))
        c.add(at, np.sin(phase_of(f)) * np.sin(np.pi * t / seconds) ** 2)
        at += seconds + rng.uniform(0.02, 0.06)
    return c.buf


def wind(rng, seconds: float, center: float = 600.0, spread: float = 250.0, q: float = 5.0) -> np.ndarray:
    t = times(seconds)
    return svf(white(rng, seconds), center + spread * slow_random(rng, seconds, 0.8), q, "band") * smoothstep(t / (seconds * 0.3)) * smoothstep((seconds - t) / (seconds * 0.4))


def footstep(rng, surface: str = "dirt") -> np.ndarray:
    c = Canvas(0.3)
    heel = {"stone": thump(140, 90, 0.02), "swamp": thump(80, 55, 0.04), "desert": thump(80, 60, 0.025)}.get(surface, thump(95, 65, 0.03))
    c.add(0.0, heel, 0.8)
    if surface == "stone":
        c.add(0.0, modes([rng.uniform(650, 800), rng.uniform(1500, 1800)], [0.012, 0.008], [1.0, 0.5], rng, seconds=0.06), 0.5)
        c.add(0.0, click(rng, 0.0007, 1500.0), 0.5)
        for _ in range(4):
            c.add(rng.uniform(0.0, 0.02), burst(rng, 0.006, 0.0015, 2000, 6000), 0.25)
    elif surface == "grass":
        c.add(0.0, burst(rng, 0.09, 0.03, 1800, 6000, attack=0.01), 0.35)
    elif surface == "forest":
        c.add(0.0, burst(rng, 0.1, 0.035, 1200, 5000, attack=0.01), 0.3)
        c.add(0.01, crackle(rng, 0.06, 6, 1500.0, 5000.0), 0.5)
    elif surface == "desert":
        c.add(0.0, burst(rng, 0.14, 0.05, 2500, 9000, attack=0.02), 0.3)
    elif surface == "swamp":
        c.add(0.0, squelch(rng, 0.18, 1400.0, 250.0, 5.0, 0.08), 0.6)
        for _ in range(3):
            f0 = rng.uniform(250, 450)
            c.add(rng.uniform(0.05, 0.16), bubble(rng, f0, f0 * 2.0, 0.05), 0.2)
    else:
        for _ in range(8):
            c.add(rng.uniform(0.0, 0.03), burst(rng, 0.006, 0.0015, 1500, 4500), rng.uniform(0.15, 0.35))
    return c.buf


def dice_roll(rng, dice: int = 3) -> np.ndarray:
    c = Canvas(1.4)
    for _ in range(dice):
        at, gap, amp = rng.uniform(0.0, 0.05), rng.uniform(0.08, 0.11), 1.0
        while amp > 0.06 and at < 1.2:
            hit = modes([rng.uniform(1100, 1400), rng.uniform(2500, 2900), rng.uniform(4000, 4500)], [0.02, 0.012, 0.007], [1.0, 0.5, 0.25], rng, seconds=0.08)
            c.add(at, hit, amp * 0.8).add(at, click(rng, 0.0005, 1500.0), amp * 0.5).add(at, wood_knock(rng, 210.0, 0.03), amp * 0.25)
            at += gap * rng.uniform(0.85, 1.1)
            gap *= 0.78
            amp *= 0.72
    return c.buf


def voice_burst(rng, seconds: float, f0: float, f1: float, formants_from, formants_to) -> np.ndarray:
    """One shouted syllable for crowd sounds: a gliding pitch through moving formants."""
    t = times(seconds)
    u = smoothstep(t / seconds)
    f = f0 + (f1 - f0) * np.sin(np.pi * np.clip(t / seconds, 0, 1) * 0.8)
    source = saw(f * (1.0 + 0.01 * slow_random(rng, seconds, 10.0)), seconds, 5000.0, rng) + 0.15 * white(rng, seconds)
    out = np.zeros(len(t))
    for (a, b), gain in zip(zip(formants_from, formants_to), (1.0, 0.6, 0.25)):
        out += gain * svf(source, a + (b - a) * u, 6.0, "band")
    return out * smoothstep(t / 0.04) * smoothstep((seconds - t) / (seconds * 0.4))


# --- combat: player weapons --------------------------------------------------------


def miss(rng, variant: int) -> np.ndarray:
    seconds, f_lo, f_hi, weight = [(0.28, 350.0, 2600.0, 0.5), (0.34, 300.0, 2100.0, 0.7), (0.22, 400.0, 3200.0, 0.35), (0.46, 220.0, 1600.0, 0.9)][variant]
    return room(swish(rng, seconds, f_lo, f_hi, 1.6, weight), rng, 0.35, 0.08)


def slash(rng, variant: int) -> np.ndarray:
    c = Canvas(0.5)
    c.add(0.0, swish(rng, 0.12, 600.0, 3500.0, 1.8, 0.2), 0.35)
    hit = 0.07
    c.add(hit, click(rng, 0.0008, 2000.0), 0.6)
    t = times(0.2)
    cut = svf(white(rng, 0.2), 2500.0 + 2600.0 * np.exp(-t / 0.05), 2.0, "band") * smoothstep(t / 0.002) * np.exp(-t / [0.06, 0.08, 0.05][variant])
    c.add(hit, cut, 0.5)
    c.add(hit + 0.004, squelch(rng, 0.18, [1800.0, 2200.0, 1500.0][variant], 350.0, 3.0, 0.07), 0.9)
    c.add(hit, thump([95.0, 105.0, 85.0][variant], 50.0, 0.09), 0.8)
    return room(c.buf, rng, 0.35, 0.08)


def chop(rng, variant: int) -> np.ndarray:
    c = Canvas(0.6)
    c.add(0.0, swish(rng, 0.14, 250.0, 1800.0, 1.4, 0.7), 0.4)
    hit = 0.08
    c.add(hit, thump([100.0, 90.0][variant], 42.0, 0.14), 1.0)
    c.add(hit, modes([rng.uniform(190, 230), rng.uniform(480, 560), rng.uniform(900, 1050)], [0.04, 0.025, 0.015], [1.0, 0.6, 0.3], rng, seconds=0.2), 0.4)
    c.add(hit + 0.003, squelch(rng, 0.2, 1500.0, 250.0, 4.0, 0.08), 0.8)
    c.add(hit + 0.01, wet_grains(rng, 0.14, 16), 0.7)
    return room(c.buf, rng, 0.35, 0.08)


def blunt(rng, variant: int) -> np.ndarray:
    c = Canvas(0.6)
    c.add(0.0, swish(rng, 0.1, 250.0, 1400.0, 1.4, 0.6), 0.3)
    hit = 0.06
    c.add(hit, thump([130.0, 115.0, 145.0][variant], 45.0, 0.16), 1.0)
    c.add(hit, burst(rng, 0.2, 0.05, hi=900.0), 0.6)
    for _ in range([5, 7, 3][variant]):
        c.add(hit + rng.uniform(0.0, 0.035), burst(rng, 0.004, 0.0012, 1800, 3200), 0.3)
    return room(c.buf, rng, 0.35, 0.08)


def meat(rng, variant: int) -> np.ndarray:
    """The satisfying connect: low impact, wet squelch and a spray of wet grains."""
    c = Canvas(0.55)
    c.add(0.0, click(rng, 0.0007, 1200.0), 0.4)
    c.add(0.0, thump([110.0, 100.0][variant], 48.0, 0.12), 0.9)
    c.add(0.003, squelch(rng, 0.2, [2500.0, 3000.0][variant], 250.0, 6.0, 0.1), 0.8)
    c.add(0.01, wet_grains(rng, 0.16, [24, 32][variant]), 1.0)
    return room(c.buf, rng, 0.3, 0.06)


def pierce(rng, variant: int) -> np.ndarray:
    c = Canvas(0.4)
    c.add(0.0, swish(rng, 0.08, 1200.0, 4200.0, 2.2, 0.1), 0.3)
    c.add(0.05, click(rng, 0.0005, 2500.0), 0.7)
    c.add(0.052, squelch(rng, 0.1, [3200.0, 2700.0][variant], 600.0, 5.0, 0.05), 0.8)
    c.add(0.05, thump([120.0, 100.0][variant], 70.0, 0.05 if variant == 0 else 0.08), 0.5 if variant == 0 else 0.8)
    return room(c.buf, rng, 0.3, 0.07)


def flight(rng, seconds=0.16, f_lo=2500.0, f_hi=6500.0) -> np.ndarray:
    """Arrow or dart in the air."""
    return swish(rng, seconds, f_lo, f_hi, 3.0, 0.05, peak=0.6)


def twang(rng, note: str = "G2", ring: float = 0.35) -> np.ndarray:
    return Canvas(ring).add(0.0, synth.harp(synth.note(note), 1.0, rng, ring=ring)).add(0.0, click(rng, 0.0008, 800.0), 0.4).buf


def arrow_hit(rng) -> np.ndarray:
    c = Canvas(0.5)
    c.add(0.0, thump(140.0, 70.0, 0.05), 0.8).add(0.0, click(rng, 0.0005, 2500.0), 0.6)
    c.add(0.002, squelch(rng, 0.1, 2800.0, 500.0, 5.0, 0.05), 0.6)
    t = times(0.3)
    c.add(0.0, np.sin(2 * np.pi * 180 * t) * (1.0 + 0.3 * np.sin(2 * np.pi * 22 * t)) * np.exp(-t / 0.08), 0.25)
    return c.buf


def ground_thud(rng) -> np.ndarray:
    c = Canvas(0.4)
    c.add(0.0, thump(110.0, 70.0, 0.04), 0.6)
    for _ in range(6):
        c.add(rng.uniform(0.0, 0.03), burst(rng, 0.006, 0.0015, 1200, 4000), 0.2)
    return c.buf


def ranged(rng, weapon: str, hit: bool) -> np.ndarray:
    c = Canvas(1.0)
    if weapon == "bow":
        c.add(0.0, twang(rng, "G2"), 0.6).add(0.02, flight(rng), 0.6)
        land = 0.2
    elif weapon == "crossbow":
        c.add(0.0, metal_hit(rng, 2600.0, 0.02, (1.0, 1.59)), 0.5).add(0.0, twang(rng, "C3", 0.25), 0.6).add(0.0, wood_knock(rng, 260.0, 0.03), 0.4)
        c.add(0.015, flight(rng, 0.12, 3000.0, 7000.0), 0.6)
        land = 0.15
    elif weapon == "sling":
        c.add(0.0, swish(rng, 0.2, 400.0, 1600.0, 1.6, 0.3), 0.4).add(0.18, swish(rng, 0.16, 500.0, 2000.0, 1.6, 0.3), 0.5)
        c.add(0.33, click(rng, 0.001, 900.0), 0.4).add(0.34, flight(rng, 0.14, 1200.0, 3000.0), 0.4)
        land = 0.5
    else:
        c.add(0.0, flight(rng, 0.12, 2000.0, 5500.0), 0.6)
        land = 0.12
    if hit:
        impact = blunt(rng, 2)[int(0.06 * SR):] if weapon == "sling" else (pierce(rng, 0)[int(0.05 * SR):] if weapon == "dart" else arrow_hit(rng))
        c.add(land, impact, 0.9)
    else:
        c.add(land, swish(rng, 0.14, 2000.0, 5000.0, 2.5, 0.05), 0.4).add(land + 0.1, ground_thud(rng), 0.6)
    return room(c.buf, rng, 0.4, 0.08)


def fight(rng) -> np.ndarray:
    c = Canvas(1.2)
    c.add(0.0, thump(75.0, 62.0, 0.5, sweep=0.02), 0.9).add(0.0, burst(rng, 0.05, 0.012, hi=2500.0), 0.3)
    c.add(0.05, ready_blade(rng), 0.5)
    return room(c.buf, rng, 0.8, 0.15)


def defend(rng) -> np.ndarray:
    c = Canvas(0.8)
    c.add(0.0, rustle(rng, 0.3, 600.0, 3500.0), 0.4)
    c.add(0.05, chain_jingle(rng, 0.25, 12, ((0.4, 0.2),)), 0.3)
    c.add(0.25, thump(100.0, 60.0, 0.06), 0.7).add(0.25, burst(rng, 0.08, 0.02, 300, 1500), 0.3)
    return room(c.buf, rng, 0.4, 0.1)


def battle_cry(rng) -> np.ndarray:
    c = Canvas(1.8)
    c.add(0.0, horn(synth.hz(50), 0.42, rng), 0.8)
    c.add(0.4, horn(synth.hz(57), 1.0, rng, glide_from=synth.hz(55)), 1.0)
    return room(c.buf, rng, 1.2, 0.3)


def run_away(rng) -> np.ndarray:
    c = Canvas(1.4)
    at, gap = 0.0, 0.17
    for i in range(7):
        c.add(at, lowpass(footstep(rng, "dirt"), 5000.0 - 550.0 * i), 0.9 * 0.8**i)
        at += gap
        gap *= 0.95
    c.add(0.0, swish(rng, 0.3, 300.0, 1200.0, 1.2, 0.6), 0.3)
    return room(c.buf, rng, 0.5, 0.12)


def run_fail(rng) -> np.ndarray:
    c = Canvas(1.2)
    c.add(0.0, footstep(rng, "dirt"), 0.8).add(0.17, footstep(rng, "dirt"), 0.8)
    t = times(0.06)
    c.add(0.3, svf(white(rng, 0.06), 900.0 + 1500.0 * t / 0.06, 1.5, "band") * np.sin(np.pi * t / 0.06), 0.5)
    c.add(0.36, thump(90.0, 40.0, 0.15), 1.0)
    c.add(0.36, burst(rng, 0.5, 0.15, hi=700.0, attack=0.01), 0.35)
    return room(c.buf, rng, 0.5, 0.12)


# --- combat: monsters and outcomes -------------------------------------------------------


def claw(rng) -> np.ndarray:
    c = Canvas(0.45)
    t = times(0.05)
    for i in range(3):
        stroke = svf(white(rng, 0.05), 2500.0 + 3000.0 * t / 0.05, 3.0, "band") * np.sin(np.pi * t / 0.05) ** 2
        c.add(i * 0.045, stroke, 0.7)
    c.add(0.03, thump(100.0, 55.0, 0.07), 0.8).add(0.03, burst(rng, 0.05, 0.02, 800, 1800), 0.4)
    return room(c.buf, rng, 0.3, 0.07)


def bite(rng) -> np.ndarray:
    c = Canvas(0.45)
    for at in (0.0, 0.11):
        c.add(at, modes([rng.uniform(3000, 3400), rng.uniform(4800, 5300)], [0.012, 0.008], [1.0, 0.6], rng, seconds=0.05), 0.6)
        c.add(at, thump(90.0, 55.0, 0.06), 0.7)
        for _ in range(6):
            c.add(at + rng.uniform(0, 0.04), burst(rng, 0.005, 0.0015, 1500, 3500), 0.25)
    c.add(0.11, squelch(rng, 0.14, 2000.0, 300.0, 4.0, 0.06), 0.5)
    return room(c.buf, rng, 0.3, 0.07)


def monster_hit(rng) -> np.ndarray:
    c = Canvas(0.5)
    c.add(0.0, swish(rng, 0.12, 200.0, 1100.0, 1.3, 0.9), 0.35)
    c.add(0.08, thump(85.0, 42.0, 0.12), 1.0).add(0.08, burst(rng, 0.12, 0.03, hi=800.0), 0.5)
    return room(c.buf, rng, 0.35, 0.08)


def armor_clank(rng) -> np.ndarray:
    c = Canvas(1.0)
    c.add(0.0, metal_hit(rng, 620.0, 0.25), 0.7).add(0.0, thump(110.0, 55.0, 0.08), 0.8)
    c.add(0.0, chain_jingle(rng, 0.25, 14, ((0.2, 0.2),)), 0.4)
    return room(c.buf, rng, 0.4, 0.1)


def monster_miss(rng) -> np.ndarray:
    return room(swish(rng, 0.38, 220.0, 1500.0, 1.3, 0.9), rng, 0.4, 0.1)


def groan(rng, seconds=0.75, f0=135.0, f1=62.0) -> np.ndarray:
    t = times(seconds)
    f = f1 + (f0 - f1) * np.exp(-t / (seconds * 0.5))
    voice = saw(f * (1.0 + 0.02 * slow_random(rng, seconds, 12.0)), seconds, max_hz=4000.0, rng=rng)
    voice = bandpass(voice, 380, 650) + 0.6 * bandpass(voice, 750, 1100) + 0.1 * highpass(white(rng, seconds), 1500)
    return voice * smoothstep(t / 0.08) * smoothstep((seconds - t) / (seconds * 0.5))


def monster_death(rng) -> np.ndarray:
    c = Canvas(1.4)
    c.add(0.0, groan(rng), 0.45)
    c.add(0.45, thump(90.0, 40.0, 0.18), 1.0).add(0.45, burst(rng, 0.12, 0.03, hi=1200.0), 0.5)
    c.add(0.6, thump(110.0, 55.0, 0.08), 0.45).add(0.5, burst(rng, 0.6, 0.2, hi=600.0, attack=0.03), 0.3)
    return room(c.buf, rng, 0.5, 0.12)


def monster_dissolve(rng) -> np.ndarray:
    seconds = 1.2
    t = times(seconds)
    air = svf(white(rng, seconds), 400.0 + 5000.0 * smoothstep(t / seconds), 3.0, "band")
    sparkle = sum(np.sin(2 * np.pi * rng.uniform(1800, 5200) * t + rng.uniform(0, 6)) * (0.5 + 0.5 * np.sin(2 * np.pi * rng.uniform(6, 14) * t)) for _ in range(8)) / 8
    out = (air * 0.8 + sparkle * 0.3) * smoothstep(t / 0.05) * smoothstep((seconds - t) / (seconds * 0.7))
    return room(out, rng, 1.2, 0.3)


def member_death(rng) -> np.ndarray:
    c = Canvas(3.0)
    c.add(0.0, bell(196.0, 2.8, rng), 1.0)
    t = times(2.6)
    drone = (saw(synth.hz(38), 2.6, 1500.0, rng) + 0.7 * saw(synth.hz(45), 2.6, 1500.0, rng)) * smoothstep(t / 0.4) * smoothstep((2.6 - t) / 1.4)
    c.add(0.0, lowpass(drone, 700.0), 0.25)
    return room(c.buf, rng, 2.0, 0.3)


def encounter(rng) -> np.ndarray:
    c = Canvas(1.4)
    c.add(0.0, stab([50, 53, 57, 62], 0.5, rng), 0.7)
    c.add(0.0, thump(80.0, 70.0, 0.5, sweep=0.02), 0.8).add(0.0, burst(rng, 0.05, 0.01, hi=2500.0), 0.3)
    return room(c.buf, rng, 1.2, 0.25)


def ambush(rng) -> np.ndarray:
    c = Canvas(2.0)
    c.add(0.0, stab([50, 51, 57, 62, 63], 0.6, rng), 0.9).add(0.0, cymbal(rng, 1.6), 0.6).add(0.0, thump(65.0, 32.0, 0.4), 1.0)
    return room(c.buf, rng, 1.4, 0.3)


def victory(rng) -> np.ndarray:
    c = Canvas(3.2)
    for i, (midi, seconds) in enumerate([(62, 0.16), (66, 0.16), (69, 0.16), (74, 1.3)]):
        c.add(i * 0.18, horn(synth.hz(midi), seconds, rng), 0.7)
    c.add(0.0, harp_notes(["D4", "F#4", "A4", "D5", "F#5", "A5"], 0.09, rng, 0.7), 0.5)
    for i in range(6):
        c.add(i * 0.06, thump(75.0, 70.0, 0.1, sweep=0.01), 0.15 + 0.07 * i)
    c.add(0.54, thump(80.0, 72.0, 0.7, sweep=0.01), 0.7).add(0.54, cymbal(rng, 1.6), 0.25)
    return room(c.buf, rng, 1.5, 0.3)


def defeat(rng) -> np.ndarray:
    c = Canvas(4.0)
    for i, (name, seconds) in enumerate([("A4", 0.4), ("F4", 0.4), ("E4", 0.4), ("D4", 1.6)]):
        c.add(i * 0.45, synth.flute(synth.note(name), seconds, 0.8, rng), 0.8)
    t = times(3.2)
    c.add(0.0, lowpass(saw(synth.hz(38), 3.2, 1200.0, rng), 500.0) * smoothstep(t / 0.6) * smoothstep((3.2 - t) / 1.5), 0.3)
    c.add(1.35, bell(147.0, 2.5, rng), 0.5)
    return room(c.buf, rng, 1.8, 0.3)


def level_up(rng) -> np.ndarray:
    c = Canvas(2.6)
    c.add(0.0, harp_notes(["D4", "F#4", "A4", "D5", "F#5", "A5", "D6"], 0.06, rng, 0.85, 1.8), 0.7)
    for i, name in enumerate(["A5", "D6", "F#6", "A6"]):
        c.add(0.42 + i * 0.07, chime(synth.hz(synth.note(name)), 1.4, rng), 0.25)
    c.add(0.3, choir([62, 66, 69, 74], 1.2, rng, attack=0.3, release=0.7), 0.35)
    return room(c.buf, rng, 1.6, 0.3)


def quest_item(rng) -> np.ndarray:
    c = Canvas(3.0)
    for i, (midi, seconds) in enumerate([(62, 0.25), (69, 0.25), (74, 1.4)]):
        c.add(i * 0.28, horn(synth.hz(midi), seconds, rng), 0.6)
    for i, name in enumerate(["D6", "A6", "D7"]):
        c.add(0.56 + i * 0.09, chime(synth.hz(synth.note(name)), 1.6, rng), 0.2)
    c.add(0.56, thump(80.0, 72.0, 0.7, sweep=0.01), 0.6).add(0.4, choir([62, 69, 74, 78], 1.4, rng, 0.25, 0.8), 0.3)
    return room(c.buf, rng, 1.6, 0.3)


# --- spells and magic items ------------------------------------------------------------


def cast(rng, seconds=0.45, f0=180.0, f1=900.0) -> np.ndarray:
    """Spell wind-up: rising filtered air and a gliding tone."""
    t = times(seconds)
    rise = smoothstep(t / seconds)
    air = svf(white(rng, seconds), 300.0 + 3500.0 * rise, 4.0, "band") * rise
    glide = np.sin(phase_of(f0 + (f1 - f0) * rise)) * rise * 0.25
    return (air + glide) * smoothstep((seconds - t) / 0.03)


def fire(rng) -> np.ndarray:
    c = Canvas(1.6)
    c.add(0.0, cast(rng, 0.3, 150.0, 500.0), 0.5)
    seconds = 1.2
    t = times(seconds)
    roar = lowpass(white(rng, seconds), 700.0) * (0.7 + 0.3 * slow_random(rng, seconds, 18.0)) * smoothstep(t / 0.08) * np.exp(-t / 0.5)
    whoosh = svf(white(rng, seconds), 300.0 + 2600.0 * env_bell(seconds, 0.12, 0.15), 1.2, "band") * env_bell(seconds, 0.15, 0.2)
    c.add(0.25, roar, 1.0).add(0.25, whoosh, 0.7).add(0.3, crackle(rng, 0.9, 40), 0.5).add(0.3, thump(70.0, 40.0, 0.2), 0.6)
    return room(c.buf, rng, 0.8, 0.2)


def frost(rng) -> np.ndarray:
    c = Canvas(1.8)
    c.add(0.0, cast(rng, 0.3, 600.0, 1800.0), 0.4)
    c.add(0.2, highpass(white(rng, 1.0), 5000.0) * env_bell(1.0, 0.35, 0.3), 0.25)
    for _ in range(45):
        c.add(0.2 + rng.uniform(0.0, 0.8), modes([rng.uniform(2500, 7500)], [rng.uniform(0.02, 0.15)], [1.0], rng, seconds=0.5), rng.uniform(0.05, 0.25))
    c.add(1.05, click(rng, 0.0006, 1500.0), 0.9)
    c.add(1.05, modes([rng.uniform(2800, 3300), rng.uniform(4700, 5400), rng.uniform(6800, 7600)], [0.3, 0.2, 0.12], [1.0, 0.7, 0.4], rng), 0.4)
    c.add(1.05, crackle(rng, 0.12, 15, 2500.0, 7000.0), 0.5)
    return room(c.buf, rng, 1.2, 0.25)


def confusion(rng) -> np.ndarray:
    seconds = 1.6
    t = times(seconds)
    env = smoothstep(t / 0.3) * smoothstep((seconds - t) / 0.6)
    swirl = np.zeros(len(t))
    for _ in range(3):
        base = rng.uniform(500, 900)
        f = base * (1.0 + 0.25 * np.sin(2 * np.pi * rng.uniform(3, 6) * t + rng.uniform(0, 6)))
        swirl += np.sin(phase_of(f))
    c = Canvas(2.2)
    c.add(0.0, flange(swirl * env / 3, 1.0, 6.0, 1.3), 0.5)
    for _ in range(7):
        c.add(rng.uniform(0.1, 1.3), chime(rng.uniform(900, 2200), 0.6, rng), 0.12)
    return room(c.buf, rng, 1.2, 0.3)


def death_strike(rng) -> np.ndarray:
    c = Canvas(2.8)
    t = times(0.7)
    c.add(0.0, lowpass(white(rng, 0.7), 900.0) * smoothstep(t / 0.7) ** 3, 0.7)
    c.add(0.7, bell(98.0, 2.0, rng), 0.9).add(0.7, thump(55.0, 30.0, 0.35), 1.0)
    c.add(1.2, thump(60.0, 48.0, 0.09), 0.5).add(1.45, thump(58.0, 46.0, 0.09), 0.3)
    return room(c.buf, rng, 1.8, 0.3)


def hold(rng) -> np.ndarray:
    seconds = 1.6
    t = times(seconds)
    rise = smoothstep(t / 0.5)
    tone = sum(np.sin(phase_of(base * (0.4 + 0.6 * rise))) for base in (880.0, 1320.0, 1760.0)) / 3
    tremolo = 0.5 + 0.5 * np.cos(2 * np.pi * np.maximum(0.0, 9.0 - 9.0 * t / seconds) * t)
    out = tone * tremolo * smoothstep(t / 0.1) * smoothstep((seconds - t) / 0.5)
    c = Canvas(2.0)
    c.add(0.0, out, 0.4).add(0.5, click(rng, 0.0006, 1500.0), 0.8).add(0.5, metal_hit(rng, 3100.0, 0.4, (1.0, 1.5, 2.1)), 0.25)
    return room(c.buf, rng, 1.2, 0.3)


def anti_magic(rng) -> np.ndarray:
    seconds = 1.0
    t = times(seconds)
    sweep = svf(saw(np.full(len(t), 110.0), seconds, 6000.0, rng), 5000.0 * np.exp(-t / 0.25) + 150.0, 4.0, "band") * smoothstep(t / 0.02) * np.exp(-t / 0.4)
    c = Canvas(1.4)
    c.add(0.0, sweep, 0.6).add(0.25, thump(90.0, 50.0, 0.12), 0.8)
    t2 = times(0.18)
    c.add(0.0, np.sin(phase_of(300.0 + 2500.0 * t2 / 0.18)) * smoothstep(t2 / 0.18) * 0.3, 0.6)
    return room(c.buf, rng, 0.8, 0.2)


def heal(rng) -> np.ndarray:
    c = Canvas(2.2)
    for i, name in enumerate(["D5", "F#5", "A5", "D6"]):
        c.add(i * 0.09, chime(synth.hz(synth.note(name)), 1.3, rng), 0.35)
    c.add(0.05, choir([62, 66, 69], 1.0, rng, 0.35, 0.6), 0.45)
    t = times(1.4)
    c.add(0.0, svf(white(rng, 1.4), 1500.0 + 5000.0 * smoothstep(t / 1.4), 3.0, "band") * env_bell(1.4, 0.4, 0.3), 0.15)
    return room(c.buf, rng, 1.5, 0.3)


def restoration(rng) -> np.ndarray:
    c = Canvas(3.2)
    c.add(0.0, heal(rng), 0.8)
    c.add(0.6, choir([57, 62, 66, 69, 74], 1.6, rng, 0.5, 0.9), 0.5)
    for i, name in enumerate(["A5", "D6", "F#6", "A6", "D7"]):
        c.add(0.9 + i * 0.08, chime(synth.hz(synth.note(name)), 1.4, rng), 0.2)
    return room(c.buf, rng, 1.6, 0.3)


def rebirth(rng) -> np.ndarray:
    c = Canvas(4.0)
    c.add(0.0, bell(165.0, 3.5, rng), 0.7)
    c.add(0.3, choir([50, 57, 62, 66], 1.4, rng, 0.8, 0.3), 0.5)
    c.add(1.7, choir([55, 62, 67, 71], 1.2, rng, 0.25, 0.9), 0.6)
    for i, name in enumerate(["G5", "B5", "D6", "G6"]):
        c.add(1.7 + i * 0.08, chime(synth.hz(synth.note(name)), 1.6, rng), 0.25)
    return room(c.buf, rng, 2.2, 0.35)


def mana(rng) -> np.ndarray:
    c = Canvas(2.0)
    t = times(0.9)
    c.add(0.0, svf(white(rng, 0.9), 600.0 + 3000.0 * smoothstep(t / 0.9), 5.0, "band") * env_bell(0.9, 0.5, 0.3), 0.3)
    for i, name in enumerate(["E5", "A5", "B5", "E6", "A6"]):
        c.add(0.2 + i * 0.07, chime(synth.hz(synth.note(name)), 1.1, rng), 0.25)
    c.add(0.25, synth.pad([64, 69, 71, 76], 0.8, rng)[0], 0.6)
    return room(c.buf, rng, 1.4, 0.3)


def banish_evil(rng) -> np.ndarray:
    c = Canvas(3.2)
    c.add(0.0, cast(rng, 0.35, 300.0, 1400.0), 0.4)
    c.add(0.35, highpass(white(rng, 0.2), 1500.0) * env_decay(0.2, 0.025, 0.0003), 1.0).add(0.35, burst(rng, 0.25, 0.06, 200, 8000), 0.6)
    c.add(0.42, rumble(rng, 2.0, 180.0, attack=0.05, release=1.4), 1.4)
    c.add(1.4, chime(synth.hz(86), 1.5, rng), 0.25).add(1.48, chime(synth.hz(93), 1.3, rng), 0.2)
    return room(c.buf, rng, 1.6, 0.25)


def revelation(rng) -> np.ndarray:
    c = Canvas(2.2)
    for i, gain in enumerate((1.0, 0.45, 0.2)):
        t = times(0.4)
        c.add(i * 0.38, np.sin(2 * np.pi * 1760 * t) * env_decay(0.4, 0.12, 0.003), 0.4 * gain)
    for i, name in enumerate(["D6", "F#6", "A6"]):
        c.add(1.0 + i * 0.06, chime(synth.hz(synth.note(name)), 0.9, rng), 0.15)
    return room(c.buf, rng, 1.0, 0.3)


def teleport(rng) -> np.ndarray:
    seconds = 1.3
    t = times(seconds)
    arc = np.sin(np.pi * t / seconds)
    tone = np.sin(phase_of(200.0 + 1400.0 * arc**2)) * arc
    air = flange(svf(white(rng, seconds), 600.0 + 4000.0 * arc, 2.0, "band"), 1.0, 5.0, 1.6) * arc
    sparkle = sum(np.sin(2 * np.pi * rng.uniform(2000, 6000) * t) * (0.5 + 0.5 * np.sin(2 * np.pi * rng.uniform(5, 12) * t)) for _ in range(6)) / 6 * arc
    return room(tone * 0.3 + air * 0.6 + sparkle * 0.2, rng, 1.0, 0.25)


def earthquake(rng) -> np.ndarray:
    c = Canvas(3.6)
    c.add(0.0, rumble(rng, 3.2, 110.0, shake=9.0, attack=0.6, release=1.2), 1.4)
    c.add(0.8, crackle(rng, 1.6, 40, 300.0, 1500.0), 0.6)
    for _ in range(8):
        c.add(rng.uniform(1.0, 2.8), wood_knock(rng, rng.uniform(120, 260), 0.05), rng.uniform(0.2, 0.5))
    c.add(1.6, thump(50.0, 28.0, 0.4), 0.8)
    return room(c.buf, rng, 1.4, 0.25, tone=2500.0)


def blindness(rng) -> np.ndarray:
    c = Canvas(1.8)
    c.add(0.0, highpass(white(rng, 0.15), 2000.0) * env_decay(0.15, 0.03, 0.001), 0.8)
    t = times(1.4)
    c.add(0.0, (np.sin(2 * np.pi * 3600 * t) + 0.6 * np.sin(2 * np.pi * 4870 * t)) * np.exp(-t / 0.35), 0.25)
    c.add(0.3, lowpass(white(rng, 1.2), 300.0) * env_bell(1.2, 0.4, 0.35), 0.4)
    return room(c.buf, rng, 1.0, 0.25)


def drums_of_death(rng) -> np.ndarray:
    c = Canvas(4.0)
    at, gap = 0.0, 0.3
    for i in range(12):
        c.add(at, thump(95.0, 70.0, 0.18, sweep=0.015), 0.3 + 0.05 * i).add(at, burst(rng, 0.04, 0.01, hi=1800.0), 0.1 + 0.02 * i)
        at += gap
        gap *= 0.84
    c.add(at, thump(60.0, 30.0, 0.6), 1.0).add(at, cymbal(rng, 1.4), 0.3).add(at + 0.05, bell(87.0, 2.2, rng), 0.7)
    return room(c.buf, rng, 1.6, 0.3)


def kaels_axe(rng) -> np.ndarray:
    c = Canvas(2.6)
    c.add(0.0, swish(rng, 0.55, 150.0, 1400.0, 1.2, 1.0), 0.8)
    c.add(0.45, thump(55.0, 28.0, 0.5), 1.0).add(0.45, metal_hit(rng, 700.0, 0.8), 0.5)
    c.add(0.45, svf(white(rng, 1.2), 3000.0 * np.exp(-times(1.2) / 0.15) + 200.0, 0.8, "low") * env_decay(1.2, 0.3), 0.8)
    c.add(0.55, crackle(rng, 0.9, 30, 600.0, 3000.0), 0.4)
    return room(c.buf, rng, 1.4, 0.3)


def explosion(rng) -> np.ndarray:
    seconds = 2.0
    t = times(seconds)
    blast = svf(white(rng, seconds), 150.0 + 3500.0 * np.exp(-t / 0.08), 0.8, "low") * env_decay(seconds, 0.45, 0.002)
    c = Canvas(2.4)
    c.add(0.0, blast, 1.0).add(0.0, thump(60.0, 28.0, 0.5), 1.0).add(0.05, crackle(rng, 1.2, 50, 800.0, 4000.0), 0.4)
    return room(c.buf, rng, 1.6, 0.3)


def holy_hand_grenade(rng) -> np.ndarray:
    c = Canvas(3.4)
    c.add(0.0, choir([62, 66, 69, 74, 78], 0.55, rng, 0.06, 0.25, vowel=(700, 1200, 2700)), 0.7)
    c.add(0.95, explosion(rng), 1.0)
    return c.buf


def flute_of_madness(rng) -> np.ndarray:
    seconds = 1.3
    t = times(seconds)
    f = 1900.0 * (1.0 + 0.18 * np.sin(2 * np.pi * 7.5 * t) + 0.12 * slow_random(rng, seconds, 5.0)) * (1.0 + 0.3 * smoothstep(t / seconds))
    screech = (np.sin(phase_of(f)) + 0.3 * np.sin(2 * phase_of(f))) * smoothstep(t / 0.05)
    screech += bandpass(white(rng, seconds), 1500, 6000) * 0.15
    c = Canvas(1.8)
    c.add(0.0, screech * smoothstep((seconds - t) / 0.02), 0.35)
    c.add(seconds, crackle(rng, 0.12, 20, 800.0, 4000.0), 0.8).add(seconds, wood_knock(rng, 420.0, 0.04), 0.6)
    return room(c.buf, rng, 0.8, 0.2)


def bless(rng) -> np.ndarray:
    c = Canvas(1.8)
    c.add(0.0, cast(rng, 0.35, 300.0, 1200.0), 0.35)
    for i, name in enumerate(["G5", "B5", "D6", "G6", "B6"]):
        c.add(0.15 + i * 0.06, chime(synth.hz(synth.note(name)), 1.0, rng), 0.3)
    return room(c.buf, rng, 1.2, 0.3)


def poison(rng) -> np.ndarray:
    c = Canvas(1.4)
    c.add(0.0, highpass(white(rng, 1.1), 2500.0) * env_bell(1.1, 0.35, 0.35), 0.2)
    for _ in range(14):
        f0 = rng.uniform(250, 600)
        c.add(rng.uniform(0.0, 1.0), bubble(rng, f0, f0 * rng.uniform(1.6, 2.4), rng.uniform(0.04, 0.08)), rng.uniform(0.15, 0.4))
    return room(c.buf, rng, 0.6, 0.2)


def fizzle(rng) -> np.ndarray:
    c = Canvas(1.0)
    t = times(0.5)
    c.add(0.0, np.sin(phase_of(600.0 - 450.0 * smoothstep(t / 0.5))) * np.exp(-t / 0.18), 0.3)
    c.add(0.0, crackle(rng, 0.4, 12), 0.4).add(0.05, burst(rng, 0.4, 0.12, 600, 3000, attack=0.02), 0.3)
    return room(c.buf, rng, 0.6, 0.2)


def monster_cast(rng) -> np.ndarray:
    c = Canvas(1.4)
    c.add(0.0, groan(rng, 0.7, 90.0, 70.0), 0.35)
    t = times(0.6)
    c.add(0.0, svf(white(rng, 0.6), 200.0 + 2000.0 * smoothstep(t / 0.6), 5.0, "band") * smoothstep(t / 0.6), 0.5)
    return room(c.buf, rng, 1.2, 0.25)


# --- items and equipment ----------------------------------------------------------------


def ready_blade(rng) -> np.ndarray:
    c = Canvas(1.3)
    c.add(0.0, click(rng, 0.001, 900.0), 0.4)
    t = times(0.32)
    c.add(0.02, svf(white(rng, 0.32), 2500.0 + 4500.0 * smoothstep(t / 0.32), 6.0, "band") * smoothstep(t / 0.32) ** 1.5, 0.6)
    c.add(0.32, metal_hit(rng, 2140.0, 0.9, (1.0, 1.565, 2.28, 3.14, 4.0)), 0.35)
    return room(c.buf, rng, 0.6, 0.12)


def unready_blade(rng) -> np.ndarray:
    c = Canvas(0.8)
    t = times(0.3)
    c.add(0.0, svf(white(rng, 0.3), 6000.0 - 3500.0 * smoothstep(t / 0.3), 6.0, "band") * np.sin(np.pi * t / 0.3), 0.5)
    c.add(0.3, wood_knock(rng, 380.0, 0.05), 0.6).add(0.3, thump(160.0, 100.0, 0.03), 0.6).add(0.3, metal_grain(rng), 0.3)
    return room(c.buf, rng, 0.5, 0.12)


def ready_axe(rng) -> np.ndarray:
    c = Canvas(1.0)
    c.add(0.0, creak(rng, 0.25, (55.0, 100.0, 70.0), (800, 1700, 3000)), 0.45)
    c.add(0.12, swish(rng, 0.28, 200.0, 1200.0, 1.3, 0.9), 0.5)
    c.add(0.42, thump(130.0, 85.0, 0.04), 0.6).add(0.42, metal_hit(rng, 1250.0, 0.35, (1.0, 2.4, 3.9)), 0.2)
    return room(c.buf, rng, 0.5, 0.12)


def unready_axe(rng) -> np.ndarray:
    c = Canvas(0.8)
    c.add(0.0, swish(rng, 0.2, 200.0, 900.0, 1.3, 0.8), 0.3)
    c.add(0.2, thump(110.0, 60.0, 0.08), 0.8).add(0.2, metal_hit(rng, 1150.0, 0.2, (1.0, 2.4)), 0.2)
    return room(c.buf, rng, 0.5, 0.12)


def ready_blunt(rng) -> np.ndarray:
    c = Canvas(0.9)
    c.add(0.0, creak(rng, 0.25, (60.0, 110.0, 80.0), (900, 1800, 3200)), 0.5)
    c.add(0.15, swish(rng, 0.25, 220.0, 1100.0, 1.3, 0.8), 0.5)
    c.add(0.4, thump(140.0, 90.0, 0.04), 0.6).add(0.4, burst(rng, 0.03, 0.008, 800, 3000), 0.3)
    return room(c.buf, rng, 0.5, 0.12)


def unready_blunt(rng) -> np.ndarray:
    c = Canvas(0.8)
    c.add(0.0, creak(rng, 0.2, (70.0, 100.0, 70.0), (900, 1800, 3200)), 0.4)
    c.add(0.2, thump(110.0, 60.0, 0.09), 0.9).add(0.2, burst(rng, 0.08, 0.02, hi=1500.0), 0.4)
    return room(c.buf, rng, 0.5, 0.12)


def ready_polearm(rng) -> np.ndarray:
    c = Canvas(1.2)
    c.add(0.0, swish(rng, 0.3, 200.0, 1000.0, 1.2, 0.9), 0.4)
    c.add(0.3, wood_knock(rng, 140.0, 0.12), 0.8).add(0.3, thump(90.0, 60.0, 0.06), 0.6)
    c.add(0.32, metal_hit(rng, 1450.0, 0.5, (1.0, 2.2, 3.6)), 0.25)
    return room(c.buf, rng, 0.6, 0.15)


def unready_polearm(rng) -> np.ndarray:
    c = Canvas(1.0)
    c.add(0.0, wood_knock(rng, 150.0, 0.08), 0.7).add(0.18, wood_knock(rng, 175.0, 0.06), 0.5).add(0.3, wood_knock(rng, 165.0, 0.04), 0.3)
    c.add(0.0, metal_hit(rng, 1400.0, 0.2, (1.0, 2.2)), 0.15)
    return room(c.buf, rng, 0.5, 0.12)


def ready_bow(rng) -> np.ndarray:
    c = Canvas(1.3)
    c.add(0.0, creak(rng, 0.35, (40.0, 70.0, 55.0), (420, 950, 1700)), 0.5).add(0.38, twang(rng, "A2", 0.9), 0.6)
    return room(c.buf, rng, 0.5, 0.12)


def ready_crossbow(rng) -> np.ndarray:
    c = Canvas(1.0)
    for i in range(7):
        c.add(i * 0.06, metal_hit(rng, rng.uniform(2300, 2700), 0.015, (1.0, 1.7)), 0.35).add(i * 0.06, wood_knock(rng, 300.0, 0.015), 0.2)
    c.add(0.5, metal_hit(rng, 1900.0, 0.05, (1.0, 1.59, 2.14)), 0.6).add(0.5, wood_knock(rng, 240.0, 0.04), 0.5)
    return room(c.buf, rng, 0.5, 0.12)


def ready_sling(rng) -> np.ndarray:
    c = Canvas(0.8)
    c.add(0.0, rustle(rng, 0.3, 800.0, 3500.0), 0.4).add(0.2, swish(rng, 0.25, 400.0, 1800.0, 1.6, 0.3), 0.35)
    return room(c.buf, rng, 0.4, 0.1)


def ready_dart(rng) -> np.ndarray:
    c = Canvas(0.6)
    for at in (0.0, 0.07, 0.16):
        c.add(at, metal_grain(rng), 0.5)
    c.add(0.0, rustle(rng, 0.2), 0.2)
    return room(c.buf, rng, 0.4, 0.1)


def unready_ranged(rng) -> np.ndarray:
    c = Canvas(0.8)
    c.add(0.0, rustle(rng, 0.35, 800.0, 3500.0), 0.4).add(0.3, wood_knock(rng, 260.0, 0.05), 0.5)
    return room(c.buf, rng, 0.4, 0.1)


def ready_armor_metal(rng) -> np.ndarray:
    c = Canvas(1.3)
    c.add(0.0, chain_jingle(rng, 0.8, 70), 0.8).add(0.1, creak(rng, 0.3, (50.0, 90.0, 60.0), (700, 1500, 2600)), 0.35)
    c.add(0.9, metal_hit(rng, 1900.0, 0.05, (1.0, 2.7)), 0.4).add(1.02, metal_hit(rng, 2100.0, 0.05, (1.0, 2.7)), 0.35)
    return room(c.buf, rng, 0.5, 0.12)


def unready_armor_metal(rng) -> np.ndarray:
    c = Canvas(1.4)
    c.add(0.0, metal_hit(rng, 2000.0, 0.05, (1.0, 2.7)), 0.4).add(0.12, metal_hit(rng, 1850.0, 0.05, (1.0, 2.7)), 0.35)
    c.add(0.25, chain_jingle(rng, 0.45, 35, ((0.5, 0.25),)), 0.6)
    c.add(0.75, metal_hit(rng, 480.0, 0.25), 0.5).add(0.75, thump(100.0, 50.0, 0.12), 0.9).add(0.75, chain_jingle(rng, 0.3, 30, ((0.1, 0.15),)), 0.7)
    return room(c.buf, rng, 0.5, 0.12)


def ready_armor_leather(rng) -> np.ndarray:
    c = Canvas(1.1)
    c.add(0.0, rustle(rng, 0.6), 0.4).add(0.15, creak(rng, 0.35, (45.0, 80.0, 55.0), (600, 1300, 2400)), 0.45)
    c.add(0.75, metal_hit(rng, 2300.0, 0.03, (1.0, 2.6)), 0.3)
    return room(c.buf, rng, 0.5, 0.12)


def unready_armor_leather(rng) -> np.ndarray:
    c = Canvas(1.0)
    c.add(0.0, metal_hit(rng, 2200.0, 0.03, (1.0, 2.6)), 0.3).add(0.1, rustle(rng, 0.5), 0.4)
    c.add(0.55, thump(90.0, 55.0, 0.07), 0.6).add(0.55, burst(rng, 0.15, 0.05, 400, 2500), 0.3)
    return room(c.buf, rng, 0.5, 0.12)


def ready_cloak(rng) -> np.ndarray:
    c = Canvas(1.4)
    c.add(0.0, rustle(rng, 0.8, 500.0, 3500.0), 0.5).add(0.2, swish(rng, 0.4, 300.0, 1300.0, 1.2, 0.6), 0.3)
    for i, name in enumerate(["A5", "E6"]):
        c.add(0.6 + i * 0.1, chime(synth.hz(synth.note(name)), 0.8, rng), 0.1)
    return room(c.buf, rng, 0.8, 0.2)


def unready_cloak(rng) -> np.ndarray:
    c = Canvas(0.9)
    c.add(0.0, rustle(rng, 0.55, 500.0, 3500.0), 0.5).add(0.5, burst(rng, 0.2, 0.06, 300, 2000, attack=0.01), 0.3)
    return room(c.buf, rng, 0.5, 0.12)


def ready_sphere(rng) -> np.ndarray:
    seconds = 1.5
    t = times(seconds)
    swell = smoothstep(t / 0.6) * smoothstep((seconds - t) / 0.6)
    hum = (np.sin(2 * np.pi * 110 * t) + 0.5 * np.sin(2 * np.pi * 220.5 * t) + 0.25 * np.sin(2 * np.pi * 331 * t)) * swell
    vwoom = svf(saw(np.full(len(t), 55.0), seconds, 4000.0, rng), 200.0 + 2500.0 * env_bell(seconds, 0.4, 0.25), 6.0, "band") * swell
    c = Canvas(2.0)
    c.add(0.0, hum, 0.35).add(0.0, vwoom, 0.5).add(0.6, chime(synth.hz(86), 1.2, rng), 0.25)
    return room(c.buf, rng, 1.2, 0.25)


def unready_sphere(rng) -> np.ndarray:
    seconds = 1.0
    t = times(seconds)
    fall = 1.0 - 0.5 * smoothstep(t / seconds)
    hum = (np.sin(phase_of(220.0 * fall)) + 0.5 * np.sin(phase_of(441.0 * fall))) * smoothstep(t / 0.05) * smoothstep((seconds - t) / 0.7)
    return room(hum * 0.5, rng, 0.8, 0.2)


def potion(rng) -> np.ndarray:
    c = Canvas(1.6)
    t = times(0.06)
    c.add(0.0, np.sin(phase_of(900.0 - 600.0 * smoothstep(t / 0.06))) * np.exp(-t / 0.02), 0.5).add(0.0, click(rng, 0.0006, 1000.0), 0.5)
    for i in range(4):
        at = 0.35 + i * 0.2
        f0 = rng.uniform(170, 230)
        c.add(at, bubble(rng, f0, f0 * 2.6, 0.07), 0.6).add(at + 0.03, thump(90.0, 70.0, 0.03), 0.3)
    c.add(1.2, thump(80.0, 55.0, 0.05), 0.5).add(1.2, bubble(rng, 140.0, 220.0, 0.08), 0.3)
    return room(c.buf, rng, 0.4, 0.1)


def scroll(rng) -> np.ndarray:
    c = Canvas(1.6)
    c.add(0.0, crackle(rng, 0.5, 60, 2000.0, 7000.0), 0.4).add(0.0, rustle(rng, 0.5), 0.3)
    c.add(0.5, bless(rng), 0.4)
    return c.buf


def torch(rng) -> np.ndarray:
    c = Canvas(1.6)
    for at in (0.0, 0.22):
        c.add(at, click(rng, 0.0007, 2500.0), 0.7).add(at, crackle(rng, 0.05, 10, 3000.0, 8000.0), 0.5)
    c.add(0.35, swish(rng, 0.35, 200.0, 1100.0, 1.2, 0.9), 0.6)
    t = times(1.0)
    c.add(0.45, lowpass(white(rng, 1.0), 500.0) * (0.6 + 0.4 * np.abs(slow_random(rng, 1.0, 6.0))) * smoothstep(t / 0.2) * smoothstep((1.0 - t) / 0.4), 0.4)
    c.add(0.45, crackle(rng, 0.9, 25), 0.4)
    return room(c.buf, rng, 0.8, 0.2)


def pickup(rng) -> np.ndarray:
    c = Canvas(0.5)
    c.add(0.0, rustle(rng, 0.18), 0.4).add(0.12, metal_grain(rng), 0.4).add(0.15, coin(rng), 0.15)
    return room(c.buf, rng, 0.4, 0.1)


def drop(rng) -> np.ndarray:
    c = Canvas(0.5)
    c.add(0.0, thump(120.0, 70.0, 0.06), 0.8).add(0.0, burst(rng, 0.1, 0.03, 500, 3000), 0.35)
    return room(c.buf, rng, 0.4, 0.1)


def offer(rng) -> np.ndarray:
    c = Canvas(1.6)
    c.add(0.0, swish(rng, 0.25, 500.0, 2200.0, 1.5, 0.3), 0.4).add(0.2, rustle(rng, 0.2), 0.35)
    c.add(0.35, choir([62, 69], 0.8, rng, 0.3, 0.5), 0.3)
    return room(c.buf, rng, 1.2, 0.25)


def gold_gain(rng) -> np.ndarray:
    c = Canvas(1.3)
    at = 0.0
    for i in range(16):
        c.add(at, coin(rng), 0.5 * 0.92**i)
        at += rng.exponential(0.04)
    return room(c.buf, rng, 0.5, 0.12)


def gold_spend(rng) -> np.ndarray:
    c = Canvas(1.0)
    for at in (0.0, 0.12, 0.2):
        c.add(at, coin(rng), 0.45).add(at, wood_knock(rng, 300.0, 0.02), 0.25)
    return room(c.buf, rng, 0.5, 0.12)


def not_enough_gold(rng) -> np.ndarray:
    c = Canvas(0.7)
    c.add(0.0, wood_knock(rng, 150.0, 0.07), 0.8).add(0.16, wood_knock(rng, 120.0, 0.09), 0.8)
    return room(c.buf, rng, 0.4, 0.1)


def meal(rng) -> np.ndarray:
    c = Canvas(2.0)
    for _ in range(9):
        c.add(rng.uniform(0.0, 1.5), ceramic(rng), rng.uniform(0.15, 0.4))
    for _ in range(4):
        c.add(rng.uniform(0.2, 1.6), metal_grain(rng), 0.2)
    return room(c.buf, rng, 0.6, 0.15)


def drink(rng) -> np.ndarray:
    c = Canvas(1.6)
    for at in (0.0, 0.012):
        c.add(at, modes([rng.uniform(1700, 2000), rng.uniform(4200, 4600), rng.uniform(7000, 7400)], [0.15, 0.08, 0.04], [1.0, 0.5, 0.25], rng), 0.5)
    for i in range(3):
        f0 = rng.uniform(160, 220)
        c.add(0.4 + i * 0.22, bubble(rng, f0, f0 * 2.4, 0.08), 0.5)
    c.add(1.15, thump(80.0, 55.0, 0.05), 0.4)
    return room(c.buf, rng, 0.8, 0.25)


def anvil(rng) -> np.ndarray:
    c = Canvas(2.2)
    for at, gain in ((0.0, 1.0), (0.55, 0.8)):
        c.add(at, metal_hit(rng, 980.0, 1.2, (1.0, 2.255, 3.6, 5.07, 6.24)), 0.6 * gain).add(at, thump(150.0, 90.0, 0.04), 0.5 * gain)
    return room(c.buf, rng, 1.0, 0.2)


def crowd_cheer(rng) -> np.ndarray:
    c = Canvas(2.4)
    for _ in range(22):
        f0 = rng.uniform(110, 240)
        c.add(rng.uniform(0.0, 0.18), voice_burst(rng, rng.uniform(0.6, 1.0), f0, f0 * rng.uniform(1.3, 1.6), (rng.uniform(450, 600), rng.uniform(1700, 2000), 2600), (rng.uniform(700, 850), rng.uniform(1100, 1300), 2500)), rng.uniform(0.3, 0.7))
    for _ in range(40):
        c.add(rng.uniform(0.3, 1.9), burst(rng, 0.02, 0.005, 900, 3500), rng.uniform(0.2, 0.5))
    return room(c.buf, rng, 0.9, 0.3)


def thrown_out(rng) -> np.ndarray:
    c = Canvas(1.6)
    c.add(0.0, creak(rng, 0.3, (30.0, 55.0, 40.0), (300, 700, 1400, 2300)), 0.5)
    c.add(0.35, swish(rng, 0.25, 200.0, 900.0, 1.2, 0.9), 0.4).add(0.55, thump(85.0, 40.0, 0.15), 1.0).add(0.55, burst(rng, 0.5, 0.15, hi=800.0, attack=0.01), 0.35)
    c.add(0.9, wood_knock(rng, 110.0, 0.15), 0.9).add(0.9, thump(70.0, 45.0, 0.12), 0.7)
    return room(c.buf, rng, 0.7, 0.15)


# --- exploration ------------------------------------------------------------------------------


def chest_open(rng, treasure: bool = False) -> np.ndarray:
    c = Canvas(2.2 if treasure else 1.4)
    c.add(0.0, metal_hit(rng, 2900.0, 0.02, (1.0, 1.59, 2.14)), 0.6)
    c.add(0.08, creak(rng, 0.85, (35.0, 75.0, 48.0)), 0.8)
    c.add(0.95, wood_knock(rng, 140.0, 0.12), 0.7).add(0.95, thump(90.0, 60.0, 0.07), 0.5)
    if treasure:
        for i, name in enumerate(["D6", "E6", "F#6", "A6", "B6", "D7"]):
            c.add(0.85 + i * 0.06, chime(synth.hz(synth.note(name)), 0.9, rng), 0.18)
    return room(c.buf, rng, 0.6, 0.15)


def chest_close(rng) -> np.ndarray:
    c = Canvas(0.9)
    c.add(0.0, creak(rng, 0.3, (55.0, 80.0, 60.0)), 0.6)
    c.add(0.3, wood_knock(rng, 130.0, 0.14), 1.0).add(0.3, thump(80.0, 50.0, 0.1), 0.8).add(0.38, metal_hit(rng, 2700.0, 0.02, (1.0, 1.59)), 0.4)
    return room(c.buf, rng, 0.6, 0.15)


def locked(rng) -> np.ndarray:
    c = Canvas(0.6)
    at = 0.0
    for _ in range(5):
        c.add(at, metal_hit(rng, rng.uniform(2300, 3000), 0.02, (1.0, 1.59, 2.14)), rng.uniform(0.3, 0.6)).add(at, wood_knock(rng, 220.0, 0.02), 0.2)
        at += rng.uniform(0.05, 0.09)
    return room(c.buf, rng, 0.5, 0.12)


def trap_gas(rng) -> np.ndarray:
    c = Canvas(1.8)
    c.add(0.0, click(rng, 0.0008, 1500.0), 0.6)
    seconds = 1.6
    t = times(seconds)
    c.add(0.05, highpass(white(rng, seconds), 2200.0) * smoothstep(t / 0.15) * smoothstep((seconds - t) / 0.9), 0.4)
    c.add(0.05, rumble(rng, seconds, 200.0, attack=0.3, release=0.6), 0.5)
    return room(c.buf, rng, 0.8, 0.2)


def trap_pit(rng) -> np.ndarray:
    c = Canvas(1.6)
    c.add(0.0, click(rng, 0.0008, 1500.0), 0.6).add(0.0, wood_knock(rng, 200.0, 0.05), 0.5)
    t = times(0.3)
    c.add(0.05, svf(white(rng, 0.3), 1200.0 - 800.0 * smoothstep(t / 0.3), 1.5, "band") * np.sin(np.pi * t / 0.3), 0.4)
    c.add(0.1, swish(rng, 0.4, 200.0, 900.0, 1.2, 0.8), 0.4)
    c.add(0.5, thump(85.0, 40.0, 0.15), 1.0).add(0.5, crackle(rng, 0.15, 20, 500.0, 3000.0), 0.5).add(0.52, squelch(rng, 0.15, 2000.0, 300.0, 4.0, 0.06), 0.4)
    return room(c.buf, rng, 1.0, 0.25)


def trap_boulder(rng) -> np.ndarray:
    c = Canvas(2.6)
    seconds = 1.6
    t = times(seconds)
    rolling = lowpass(white(rng, seconds), 220.0) * (1.0 + 0.6 * np.sin(phase_of(3.0 + 6.0 * t / seconds))) * smoothstep(t / seconds) ** 1.5
    c.add(0.0, rolling, 1.0).add(seconds, thump(55.0, 28.0, 0.45), 1.0).add(seconds, crackle(rng, 0.6, 30, 400.0, 2500.0), 0.5)
    return room(c.buf, rng, 1.2, 0.25, tone=3000.0)


def trap_disarmed(rng) -> np.ndarray:
    c = Canvas(1.2)
    for at in (0.0, 0.12, 0.2):
        c.add(at, metal_hit(rng, rng.uniform(2600, 3200), 0.015, (1.0, 1.7)), 0.4)
    c.add(0.4, chime(synth.hz(81), 0.8, rng), 0.25).add(0.5, chime(synth.hz(88), 0.8, rng), 0.25)
    return room(c.buf, rng, 0.6, 0.15)


def door_open(rng) -> np.ndarray:
    c = Canvas(1.6)
    c.add(0.0, metal_hit(rng, 2400.0, 0.03, (1.0, 1.59, 2.14)), 0.5).add(0.1, creak(rng, 1.1, (22.0, 45.0, 30.0), (300, 700, 1400, 2300)), 0.8)
    return room(c.buf, rng, 0.7, 0.15)


def door_slam(rng) -> np.ndarray:
    c = Canvas(1.6)
    c.add(0.0, creak(rng, 0.3, (25.0, 45.0, 30.0), (200, 480, 1000, 1700)), 0.5)
    c.add(0.3, wood_knock(rng, 85.0, 0.2), 1.0).add(0.3, thump(55.0, 35.0, 0.25), 1.0).add(0.4, metal_hit(rng, 1600.0, 0.05, (1.0, 1.59)), 0.4)
    return room(c.buf, rng, 1.4, 0.3)


def stairs(rng, down: bool) -> np.ndarray:
    c = Canvas(1.6)
    for i in range(4):
        cutoff = (6000.0 - 1100.0 * i) if down else (2500.0 + 1100.0 * i)
        c.add(i * 0.27, lowpass(footstep(rng, "stone"), cutoff), (0.9 - 0.12 * i) if down else (0.55 + 0.12 * i))
    return room(c.buf, rng, 1.4 if down else 0.8, 0.35 if down else 0.2)


def bump(rng) -> np.ndarray:
    return Canvas(0.4).add(0.0, thump(80.0, 50.0, 0.08), 0.8).add(0.0, burst(rng, 0.06, 0.015, 300, 1500), 0.3).buf


def enter_town(rng) -> np.ndarray:
    c = Canvas(3.6)
    c.add(0.0, creak(rng, 0.9, (20.0, 38.0, 26.0), (260, 600, 1250, 2100)), 0.5)
    for at in (0.7, 1.6):
        c.add(at, lowpass(bell(262.0, 2.0, rng), 3500.0), 0.6)
    return room(c.buf, rng, 1.6, 0.35)


def leave_town(rng) -> np.ndarray:
    c = Canvas(1.6)
    c.add(0.0, creak(rng, 0.8, (24.0, 40.0, 28.0), (260, 600, 1250, 2100)), 0.6).add(0.8, wood_knock(rng, 95.0, 0.2), 1.0).add(0.8, thump(60.0, 40.0, 0.15), 0.8)
    return room(c.buf, rng, 1.0, 0.25)


def enter_dungeon(rng) -> np.ndarray:
    c = Canvas(3.0)
    c.add(0.0, rumble(rng, 1.4, 450.0, attack=0.2, release=0.3), 0.8).add(1.35, thump(55.0, 32.0, 0.45), 1.0)
    return room(c.buf, rng, 2.4, 0.45, tone=2500.0)


def enter_cave(rng) -> np.ndarray:
    c = Canvas(3.2)
    c.add(0.0, wind(rng, 1.8, 450.0, 300.0, 3.0), 0.6)
    for at, f in ((0.5, 800.0), (1.1, 1050.0), (1.6, 720.0)):
        c.add(at, drip(rng, f, f * 2.6), 0.35)
    return room(c.buf, rng, 2.6, 0.5, tone=3500.0)


def enter_castle(rng) -> np.ndarray:
    c = Canvas(3.0)
    at = 0.0
    while at < 1.3:
        c.add(at, metal_grain(rng), 0.4).add(at, metal_hit(rng, rng.uniform(900, 1200), 0.03, (1.0, 2.4)), 0.15)
        at += rng.uniform(0.035, 0.05)
    c.add(1.35, explosion(rng)[: int(1.6 * SR)], 0.6).add(1.35, wood_knock(rng, 70.0, 0.3), 1.0)
    return room(c.buf, rng, 1.6, 0.35)


def hunt(rng, success: bool) -> np.ndarray:
    c = Canvas(1.6)
    c.add(0.0, rustle(rng, 0.4, 1500.0, 6000.0), 0.3).add(0.4, ranged(rng, "bow", success), 0.9)
    if success:
        c.add(0.75, thump(70.0, 45.0, 0.12), 0.5).add(0.75, rustle(rng, 0.4, 1500.0, 6000.0), 0.3)
    return c.buf


def camp(rng) -> np.ndarray:
    seconds = 2.0
    t = times(seconds)
    c = Canvas(2.2)
    c.add(0.0, swish(rng, 0.45, 200.0, 900.0, 1.2, 0.9), 0.5)
    c.add(0.2, lowpass(white(rng, seconds), 350.0) * (0.6 + 0.4 * np.abs(slow_random(rng, seconds, 4.0))) * smoothstep(t / 0.4) * smoothstep((seconds - t) / 0.6), 0.5)
    c.add(0.3, crackle(rng, 1.7, 45), 0.5)
    return c.buf


def lullaby(rng) -> np.ndarray:
    c = Canvas(2.6)
    for i, name in enumerate(["A5", "F5", "D5"]):
        c.add(i * 0.4, chime(synth.hz(synth.note(name)), 1.4, rng), 0.4)
    c.add(0.0, synth.pad([62, 65, 69], 1.4, rng)[0], 0.5)
    return room(c.buf, rng, 1.5, 0.3)


def cold_night(rng) -> np.ndarray:
    return room(wind(rng, 2.6, 650.0, 250.0, 6.0) + 0.4 * wind(rng, 2.6, 1300.0, 400.0, 8.0), rng, 1.2, 0.25)


def wait_hour(rng) -> np.ndarray:
    c = Canvas(2.2)
    c.add(0.0, wind(rng, 1.8, 900.0, 300.0, 1.5), 0.3).add(0.6, chime(synth.hz(69), 1.4, rng), 0.2)
    return room(c.buf, rng, 1.0, 0.2)


def dawn(rng) -> np.ndarray:
    c = Canvas(3.0)
    c.add(0.0, synth.pad([62, 66, 69, 74], 2.0, rng)[0], 0.3)
    for at, base, gain in ((0.3, 3800.0, 0.25), (0.9, 4600.0, 0.2), (1.5, 3500.0, 0.25), (2.0, 5000.0, 0.15)):
        c.add(at, chirp(rng, base), gain)
    return room(c.buf, rng, 1.2, 0.25)


def nightfall(rng) -> np.ndarray:
    c = Canvas(3.2)
    for at, seconds in ((0.2, 0.5), (1.0, 0.22), (1.3, 0.45)):
        t = times(seconds)
        hoot = (np.sin(phase_of(390.0 * (1.0 - 0.06 * t / seconds))) + 0.15 * lowpass(white(rng, seconds), 900.0)) * np.sin(np.pi * t / seconds) ** 1.5
        c.add(at, hoot, 0.4)
    for start in (0.0, 1.6):
        at = start
        for _ in range(6):
            t = times(0.025)
            c.add(at, np.sin(2 * np.pi * 4500 * t) * np.sin(np.pi * t / 0.025), 0.06)
            at += 0.045
    return room(c.buf, rng, 1.6, 0.3)


def hunger(rng) -> np.ndarray:
    seconds = 1.4
    t = times(seconds)
    growl = svf(white(rng, seconds), 260.0 - 120.0 * smoothstep(t / seconds) + 30.0 * slow_random(rng, seconds, 6.0), 8.0, "band")
    growl *= (0.6 + 0.4 * np.sin(2 * np.pi * (18.0 + 6.0 * slow_random(rng, seconds, 2.0)) * t)) * smoothstep(t / 0.15) * smoothstep((seconds - t) / 0.5)
    return room(growl, rng, 0.4, 0.08)


def fatigue(rng) -> np.ndarray:
    c = Canvas(2.0)
    c.add(0.0, synth.flute(synth.note("A4"), 0.45, 0.6, rng), 0.4).add(0.45, synth.flute(synth.note("F4"), 0.9, 0.5, rng), 0.4)
    c.add(0.0, burst(rng, 1.2, 0.5, 300, 1500, attack=0.3), 0.1)
    return room(c.buf, rng, 1.0, 0.2)


def journal(rng) -> np.ndarray:
    c = Canvas(1.2)
    at = 0.0
    for _ in range(6):
        seconds = rng.uniform(0.05, 0.11)
        t = times(seconds)
        c.add(at, bandpass(white(rng, seconds), 2500, 7500) * np.sin(np.pi * t / seconds) ** 0.7 * (0.6 + 0.4 * np.abs(slow_random(rng, seconds, 80.0))), rng.uniform(0.3, 0.6))
        at += seconds + rng.uniform(0.03, 0.1)
    return room(c.buf, rng, 0.4, 0.08)


def riddle(rng, right: bool) -> np.ndarray:
    c = Canvas(1.6)
    if right:
        c.add(0.0, chime(synth.hz(86), 1.2, rng), 0.4).add(0.12, chime(synth.hz(93), 1.2, rng), 0.4)
    else:
        c.add(0.0, stab([45, 46, 51], 0.7, rng), 0.6).add(0.0, thump(60.0, 35.0, 0.3), 0.6)
    return room(c.buf, rng, 1.2, 0.3)


# --- party and interface ------------------------------------------------------------------------


def harp_pluck(rng, names: list[str], step: float, velocity: float = 0.7) -> np.ndarray:
    return harp_notes(names, step, rng, velocity, ring=0.9)


def ui_move(rng) -> np.ndarray:
    return Canvas(0.05).add(0.0, modes([2200.0, 5100.0], [0.008, 0.004], [1.0, 0.3], rng, seconds=0.05), 0.6).add(0.0, click(rng, 0.0004, 3000.0), 0.3).buf


def ui_select(rng) -> np.ndarray:
    return harp_pluck(rng, ["D6", "A6"], 0.05, 0.6)[: int(0.5 * SR)]


def ui_back(rng) -> np.ndarray:
    return harp_pluck(rng, ["A6", "D6"], 0.05, 0.5)[: int(0.5 * SR)]


def ui_error(rng) -> np.ndarray:
    t = times(0.16)
    buzz = lowpass(np.sign(np.sin(2 * np.pi * 110 * t)), 900.0) * smoothstep(t / 0.005) * smoothstep((0.16 - t) / 0.03)
    return Canvas(0.3).add(0.0, buzz, 0.4).add(0.0, wood_knock(rng, 160.0, 0.04), 0.6).buf


def ui_page(rng) -> np.ndarray:
    t = times(0.07)
    return bandpass(white(rng, 0.07), 1800, 6000) * np.sin(np.pi * t / 0.07) ** 2


def save(rng) -> np.ndarray:
    c = Canvas(1.5)
    c.add(0.0, harp_pluck(rng, ["D5", "A5"], 0.1), 0.7).add(0.1, chime(synth.hz(81), 1.2, rng), 0.25)
    return room(c.buf, rng, 1.0, 0.25)


def load(rng) -> np.ndarray:
    return room(Canvas(1.5).add(0.0, harp_pluck(rng, ["A4", "D5", "F5", "A5"], 0.07), 0.7).buf, rng, 1.0, 0.25)


def title_start(rng) -> np.ndarray:
    names = ["D4", "E4", "F4", "G4", "A4", "B4", "C5", "D5", "E5", "F5", "G5", "A5", "B5", "C6", "D6"]
    return room(Canvas(2.4).add(0.0, harp_notes(names, 0.035, rng, 0.75, 1.6), 0.6).buf, rng, 1.4, 0.3)


def quit_game(rng) -> np.ndarray:
    return room(Canvas(1.8).add(0.0, harp_pluck(rng, ["A5", "F5", "D5"], 0.16, 0.6), 0.7).buf, rng, 1.2, 0.3)


def character_created(rng) -> np.ndarray:
    c = Canvas(1.8)
    c.add(0.0, harp_pluck(rng, ["D5", "F#5", "A5"], 0.07, 0.75), 0.6).add(0.2, chime(synth.hz(86), 1.2, rng), 0.3)
    return room(c.buf, rng, 1.2, 0.3)
