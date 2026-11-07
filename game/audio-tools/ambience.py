"""
Shared helpers for the ambient background loops.

Continuous beds (room tone, fire roar, wind, distant street) are built as
exactly periodic noise in the frequency domain, so they loop without a seam.
Discrete events (clinks, creaks, crackles, hammer blows) are scattered at
irregular times on a synth.LoopMixer, which folds their tails and the
reverb across the loop point.
"""

import subprocess
from pathlib import Path

import numpy as np
from scipy import signal

import synth
from synth import smoothstep

SR = synth.SR


def times(seconds: float) -> np.ndarray:
    return np.arange(int(round(seconds * SR))) / SR


def white(rng: np.random.Generator, seconds: float) -> np.ndarray:
    return rng.standard_normal(int(round(seconds * SR)))


def lowpass(x, hz, order=2):
    return signal.sosfilt(signal.butter(order, hz, fs=SR, output="sos"), x)


def highpass(x, hz, order=2):
    return signal.sosfilt(signal.butter(order, hz, btype="high", fs=SR, output="sos"), x)


def bandpass(x, lo, hi, order=2):
    return signal.sosfilt(signal.butter(order, [lo, hi], btype="band", fs=SR, output="sos"), x)


# --- periodic beds -------------------------------------------------------------


def periodic_noise(rng: np.random.Generator, n: int, lo: float | None = None, hi: float | None = None, tilt_db_per_octave: float = 0.0) -> np.ndarray:
    """Noise of length n that repeats exactly, shaped by 2nd-order band edges and a spectral tilt."""
    freqs = np.fft.rfftfreq(n, 1 / SR)
    f = np.maximum(freqs, 1.0)
    mag = np.ones(len(freqs))
    if hi:
        mag /= np.sqrt(1.0 + (f / hi) ** 4)
    if lo:
        mag *= (f / lo) ** 2 / np.sqrt(1.0 + (f / lo) ** 4)
    if tilt_db_per_octave:
        mag *= (f / 1000.0) ** (tilt_db_per_octave / 6.02)
    mag[0] = 0.0
    spectrum = mag * np.exp(1j * rng.uniform(0, 2 * np.pi, len(freqs)))
    x = np.fft.irfft(spectrum, n)
    return x / (x.std() + 1e-12)


def periodic_lfo(rng: np.random.Generator, n: int, max_hz: float) -> np.ndarray:
    """Smooth random wander (unit standard deviation) that repeats exactly every n samples."""
    freqs = np.fft.rfftfreq(n, 1 / SR)
    mag = np.exp(-((freqs / max_hz) ** 2))
    mag[0] = 0.0
    x = np.fft.irfft(mag * np.exp(1j * rng.uniform(0, 2 * np.pi, len(freqs))), n)
    return x / (x.std() + 1e-12)


def fire_bed(rng: np.random.Generator, n: int, size: float = 1.0) -> np.ndarray:
    """Low roar of a fire with slow flicker."""
    roar = periodic_noise(rng, n, lo=90.0, hi=380.0 * size)
    flicker = 0.65 + 0.25 * periodic_lfo(rng, n, 1.2) + 0.1 * periodic_lfo(rng, n, 7.0)
    return roar * np.clip(flicker, 0.2, None)


def room_tone(rng: np.random.Generator, n: int) -> np.ndarray:
    return periodic_noise(rng, n, lo=40.0, hi=900.0, tilt_db_per_octave=-3.0)


def wind_bed(rng: np.random.Generator, n: int, muffled: float = 1.0) -> np.ndarray:
    """Gusty wind: three noise bands whose levels wander, which moves the apparent pitch of the wind."""
    out = np.zeros(n)
    for lo, hi in ((150.0, 420.0), (300.0, 800.0), (600.0, 1500.0)):
        band = periodic_noise(rng, n, lo=lo, hi=hi * muffled)
        gust = np.clip(0.5 + 0.35 * periodic_lfo(rng, n, 0.12) + 0.15 * periodic_lfo(rng, n, 0.6), 0.05, None)
        out += band * gust
    return out / 3.0


def scatter(rng: np.random.Generator, loop: float, mean_gap: float, min_gap: float = 0.0, start: float | None = None, margin: float = 0.0) -> list[float]:
    """Irregular event times on a loop; the gap across the loop point respects min_gap too."""
    out = []
    t = rng.uniform(0.0, mean_gap) if start is None else start
    while t < loop - max(min_gap, margin):
        out.append(t)
        t += min_gap + rng.exponential(max(1e-3, mean_gap - min_gap))
    return out


def crowd(rng: np.random.Generator, n: int, voices: list[tuple[str, float, float, float, float]], talkativeness=(0.5, 1.0), pitch: float = 1.0) -> tuple[np.ndarray, np.ndarray]:
    """
    A murmuring crowd as a stereo loop: one talker per (sex, gain, pan,
    lowpass Hz, unused) entry. Each talker is muffled by distance first and
    then rotated to a random point on the loop, so the talkers' pauses fall at
    different places and the crowd never thins out at the loop point.
    """
    import voice

    left = np.zeros(n)
    right = np.zeros(n)
    reference = None
    for sex, gain, pan, cutoff, _ in voices:
        person = voice.talker(rng, n / SR, sex, talkativeness=rng.uniform(*talkativeness), pitch=pitch)
        person = lowpass(np.pad(person, (0, n - len(person))), cutoff, order=2)
        reference = reference or np.abs(person).max()
        person = np.roll(person, int(rng.integers(n))) / reference * gain
        angle = (pan + 1.0) * np.pi / 4.0
        left += person * np.cos(angle)
        right += person * np.sin(angle)
    return left, right


# --- event sounds ------------------------------------------------------------------


def modes(freqs, taus, amps, rng, seconds=None, attack=0.0004):
    t = times(seconds or max(taus) * 6)
    out = np.zeros(len(t))
    for f, tau, a in zip(freqs, taus, amps):
        if f < SR * 0.45:
            out += a * np.sin(2 * np.pi * f * t + rng.uniform(0, 2 * np.pi)) * np.exp(-t / tau)
    return out * smoothstep(t / attack)


def click(rng, tau=0.0008, hp=1500.0):
    t = times(tau * 10)
    return highpass(white(rng, tau * 10), hp) * np.exp(-t / tau)


def thump(f_start, f_end, tau, sweep=0.03):
    t = times(tau * 7)
    f = f_end + (f_start - f_end) * np.exp(-t / sweep)
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * smoothstep(t / 0.001) * np.exp(-t / tau)


def burst(rng, seconds, tau, lo=None, hi=None, attack=0.001):
    x = white(rng, seconds)
    if lo and hi:
        x = bandpass(x, lo, hi)
    elif hi:
        x = lowpass(x, hi)
    elif lo:
        x = highpass(x, lo)
    t = times(seconds)
    return x * smoothstep(t / attack) * np.exp(-t / tau)


def pop(rng, lo=900.0, hi=4500.0):
    """One crackle of burning wood."""
    x = bandpass(white(rng, 0.012), lo, hi) * np.exp(-times(0.012) / rng.uniform(0.0006, 0.0025))
    return x / (np.abs(x).max() + 1e-12)


def add_crackle(mixer, rng, loop, mean_gap, gain, pan=0.0, lo=900.0, hi=4500.0, reverb=0.2):
    for at in scatter(rng, loop, mean_gap):
        level = gain * rng.uniform(0.1, 1.0) ** 2
        mixer.add(at, pop(rng, lo, hi), level, pan + rng.uniform(-0.15, 0.15), reverb)
        if rng.random() < 0.12:
            for k in range(int(rng.integers(2, 6))):
                mixer.add(at + 0.01 + k * rng.uniform(0.008, 0.03), pop(rng, lo, hi), level * rng.uniform(0.3, 0.8), pan, reverb)


def wood_knock(rng, base=180.0, tau=0.1):
    return modes([base, base * 2.3, base * 4.4, base * 7.1], [tau, tau * 0.6, tau * 0.35, tau * 0.2], [1.0, 0.6, 0.3, 0.12], rng)


def creak(rng, seconds, rates=(35.0, 75.0, 50.0), resonances=(520, 1100, 1900, 3100), q=1.06):
    """Wood creak: stick-slip pulses at a wandering rate through resonant wood."""
    t = times(seconds)
    rate = np.interp(t / seconds, np.linspace(0, 1, len(rates)), rates)
    wander = lowpass(white(rng, seconds), 9.0)
    rate *= 1.0 + 0.1 * wander / (wander.std() + 1e-12)
    ph = np.cumsum(np.maximum(rate, 5.0)) / SR
    pulses = np.zeros(len(t))
    hits = np.nonzero(np.diff(np.floor(ph)) > 0)[0]
    pulses[hits] = rng.uniform(0.5, 1.0, len(hits))
    body = sum(bandpass(pulses, f / q, f * q) / (i + 1) for i, f in enumerate(resonances))
    env = smoothstep(t / (seconds * 0.2)) * smoothstep((seconds - t) / (seconds * 0.25))
    out = body * env
    return out / (np.abs(out).max() + 1e-12)


def floor_creak(rng):
    seconds = rng.uniform(0.35, 0.8)
    low, mid = rng.uniform(25, 40), rng.uniform(45, 80)
    return creak(rng, seconds, (low, mid, low * 1.2), (rng.uniform(250, 350), rng.uniform(600, 800), rng.uniform(1300, 1600)))


def footstep_wood(rng):
    heel = thump(rng.uniform(90, 120), 60.0, 0.03)
    knock = wood_knock(rng, rng.uniform(150, 210), 0.03)
    c = np.zeros(max(len(heel), len(knock)))
    c[: len(heel)] += heel * 0.8
    c[: len(knock)] += knock * 0.3
    return c


def coin(rng):
    base = rng.uniform(2700, 4200)
    ring = modes([base, base * 1.53, base * 2.21, base * 2.9], [0.22, 0.14, 0.09, 0.05], [1.0, 0.7, 0.4, 0.2], rng, seconds=0.6)
    ring[: int(0.004 * SR)] += click(rng, 0.0004, 3000.0)[: int(0.004 * SR)] * 0.6
    return ring / (np.abs(ring).max() + 1e-12)


def mug_clink(rng, wooden: bool):
    if wooden:
        x = wood_knock(rng, rng.uniform(320, 480), 0.04)
        ring = modes([rng.uniform(1300, 1700)], [0.02], [1.0], rng, seconds=0.12)
        x[: len(ring)] += 0.3 * ring
    else:
        x = modes([rng.uniform(1700, 2100), rng.uniform(4100, 4700), rng.uniform(6800, 7500)], [0.13, 0.07, 0.035], [1.0, 0.5, 0.25], rng)
    return x / (np.abs(x).max() + 1e-12)


def plate(rng):
    """A plate or bowl set down on a wooden table."""
    ring = modes([rng.uniform(1100, 1400), rng.uniform(2700, 3100), rng.uniform(4800, 5400)], [0.09, 0.05, 0.03], [1.0, 0.6, 0.3], rng, seconds=0.4)
    body = thump(140.0, 90.0, 0.02)
    ring[: len(body)] += body * 0.6
    return ring / (np.abs(ring).max() + 1e-12)


def cutlery(rng):
    """Knife or spoon on a plate: a couple of light metallic ticks with a short scrape."""
    out = np.zeros(int(0.35 * SR))
    at = 0
    for _ in range(int(rng.integers(1, 4))):
        tick = modes([rng.uniform(3000, 4500), rng.uniform(5500, 7000)], [0.02, 0.012], [1.0, 0.5], rng, seconds=0.08)
        out[at : at + len(tick)] += tick[: len(out) - at]
        at += int(rng.uniform(0.05, 0.12) * SR)
        if at >= len(out) - len(tick):
            break
    scrape = bandpass(white(rng, 0.08), 2500, 6000) * np.sin(np.pi * times(0.08) / 0.08) ** 2 * 0.3
    out[: len(scrape)] += scrape
    return out / (np.abs(out).max() + 1e-12)


def wooden_box(rng):
    """A crate set down, sometimes slid a little first."""
    out = np.zeros(int(0.9 * SR))
    at = 0
    if rng.random() < 0.5:
        slide = bandpass(white(rng, 0.35), 300, 1800) * np.sin(np.pi * times(0.35) / 0.35) ** 2 * 0.35
        out[: len(slide)] += slide
        at = len(slide) - int(0.05 * SR)
    hit = wood_knock(rng, rng.uniform(110, 160), 0.09)
    body = thump(100.0, 60.0, 0.06)
    out[at : at + len(hit)] += hit[: len(out) - at] * 0.8
    out[at : at + len(body)] += body[: len(out) - at] * 0.7
    return out / (np.abs(out).max() + 1e-12)


def chirp(rng, base):
    """A small bird: a fast up-down flick repeated a few times."""
    pieces = []
    for _ in range(int(rng.integers(2, 6))):
        seconds = rng.uniform(0.04, 0.08)
        t = times(seconds)
        f = base * (1.0 + 0.45 * np.sin(np.pi * t / seconds) ** 2) * (1.0 + 0.03 * np.sin(2 * np.pi * 60 * t))
        pieces.append(np.sin(2 * np.pi * np.cumsum(f) / SR) * np.sin(np.pi * t / seconds) ** 2)
        pieces.append(np.zeros(int(rng.uniform(0.02, 0.07) * SR)))
    return np.concatenate(pieces)


def bubble(rng, f0, f1, seconds=0.06):
    """One bubble bursting: a short sine gliding upward."""
    t = times(seconds)
    f = f0 + (f1 - f0) * smoothstep(t / seconds)
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * smoothstep(t / 0.003) * np.exp(-t / (seconds * 0.45))


def whoosh(rng, seconds, lo, hi, peak=0.5):
    """Air pushed through: band-passed noise swelling and fading."""
    t = times(seconds)
    return bandpass(white(rng, seconds), lo, hi) * np.exp(-(((t / seconds) - peak) / 0.28) ** 2)


_RISSET = [
    (0.56, 0.0, 1.0, 1.0), (0.56, 1.0, 0.67, 0.9), (0.92, 0.0, 1.0, 0.65), (0.92, 1.7, 1.8, 0.55),
    (1.19, 0.0, 2.67, 0.325), (1.7, 0.0, 1.67, 0.35), (2.0, 0.0, 1.46, 0.25), (2.74, 0.0, 1.33, 0.2),
    (3.0, 0.0, 1.33, 0.15), (3.76, 0.0, 1.0, 0.1), (4.07, 0.0, 1.33, 0.075),
]


def bell(rng, freq, seconds):
    """Church bell (Risset's inharmonic partial recipe)."""
    t = times(seconds)
    out = np.zeros(len(t))
    for ratio, offset, amp, dur in _RISSET:
        out += amp * np.sin(2 * np.pi * (freq * ratio + offset) * t + rng.uniform(0, 2 * np.pi)) * np.exp(-4.6 * t / (seconds * dur))
    out *= smoothstep(t / 0.002)
    return out / (np.abs(out).max() + 1e-12)


def hoof(rng, surface="cobble"):
    """One hoof on cobblestones: a hard knock with a stony tick."""
    out = np.zeros(int(0.15 * SR))
    knock = wood_knock(rng, rng.uniform(280, 360), 0.025)[: len(out)]
    out[: len(knock)] += knock
    tick = modes([rng.uniform(1600, 2200), rng.uniform(3200, 3900)], [0.01, 0.006], [1.0, 0.5], rng, seconds=0.05)
    out[: len(tick)] += tick * 0.5
    return out / (np.abs(out).max() + 1e-12)


# --- output ----------------------------------------------------------------------------


def finish(name: str, mixer: synth.LoopMixer, rng: np.random.Generator, out_dir: Path, rt60: float, wet: float, predelay: float = 0.02, target_lufs: float = -22.0, peak_ceiling: float = 0.70, highpass_hz: float = 70.0) -> None:
    """Renders with reverb, masters, writes WAV/MP3 and a spectrogram, and prints checks."""
    ir = synth.reverb_ir(rng, rt60=rt60, length=min(rt60 * 1.5, 7.0), predelay=predelay)
    mix = mixer.render(ir, wet=wet)
    # Rumble below the voices and fires only costs headroom; filter it circularly so the loop stays seamless.
    n = mix.shape[1]
    mix = signal.sosfilt(signal.butter(2, highpass_hz, btype="high", fs=SR, output="sos"), np.tile(mix, 3), axis=1)[:, n : 2 * n]
    if not np.all(np.isfinite(mix)):
        raise SystemExit(f"{name}: non-finite samples")
    mix, lufs, peak = synth.master(mix, target_lufs=target_lufs, peak_ceiling=peak_ceiling)
    out_dir.mkdir(parents=True, exist_ok=True)
    wav = out_dir / f"{name}.wav"
    synth.write_wav(str(wav), mix, rng)
    subprocess.run(["lame", "--silent", "-V", "5", str(wav), str(wav.with_suffix(".mp3"))], check=True)
    check_dir = out_dir / "check"
    check_dir.mkdir(exist_ok=True)
    subprocess.run(
        ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(wav), "-lavfi",
         "showspectrumpic=s=1400x500:legend=1:scale=log:fscale=log:stop=10000", str(check_dir / f"{name}.png")],
        check=True,
    )
    steps = np.abs(np.diff(np.concatenate([mix, mix[:, :1]], axis=1), axis=1))
    seam = steps[:, -1].max()
    print(
        f"{name:12s} {mixer.loop / SR:5.1f} s  {lufs:6.1f} LUFS  peak {peak:5.1f} dBFS  "
        f"seam step {seam:.4f} (p99 {np.percentile(steps, 99):.4f})  mp3 {wav.with_suffix('.mp3').stat().st_size / 1e6:.2f} MB"
    )
