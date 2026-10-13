"""
Small offline synthesizer for the Rylween soundtrack.

Instruments are rendered as numpy arrays at SR, placed on a loop-length
mixer, and finished with a convolution reverb. Everything that rings past
the loop end (note tails, reverb) is folded back onto the loop start, so the
rendered file loops without a seam.
"""

import numpy as np
from scipy import signal
from scipy.io import wavfile

SR = 44100

_OFFSETS = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}


def note(name: str) -> int:
    """MIDI note number for a name such as "D5", "C#5" or "Bb3"."""
    letter, rest = name[0], name[1:]
    accidental = 0
    if rest.startswith("#"):
        accidental, rest = 1, rest[1:]
    elif rest.startswith("b"):
        accidental, rest = -1, rest[1:]
    return 12 * (int(rest) + 1) + _OFFSETS[letter] + accidental


def hz(midi: float) -> float:
    return 440.0 * 2.0 ** ((midi - 69) / 12.0)


def smoothstep(x):
    x = np.clip(x, 0.0, 1.0)
    return x * x * (3.0 - 2.0 * x)


def _times(seconds: float) -> np.ndarray:
    return np.arange(int(seconds * SR)) / SR


# --- instruments -----------------------------------------------------------


def harp(midi: int, velocity: float, rng: np.random.Generator, ring: float = 2.6) -> np.ndarray:
    """Plucked string (Karplus-Strong with a fractional-delay loop filter)."""
    f = hz(midi)
    period = SR / f
    n_delay = int(np.floor(period - 1.0))
    frac = period - 1.0 - n_delay
    loop = np.convolve([0.25, 0.5, 0.25], [1.0 - frac, frac])
    t60 = float(np.interp(midi, [40, 84], [4.5, 1.3]))
    gain = 10.0 ** (-3.0 / (f * t60))
    a = np.zeros(n_delay + len(loop))
    a[0] = 1.0
    a[n_delay:] -= gain * loop

    burst = rng.uniform(-1.0, 1.0, n_delay)
    shift = max(1, int(0.13 * n_delay))
    burst = burst - np.concatenate([np.zeros(shift), burst[:-shift]])
    b_lp, a_lp = signal.butter(1, (1200.0 + 4200.0 * velocity) / (SR / 2))
    burst = signal.lfilter(b_lp, a_lp, burst)
    burst -= burst.mean()

    excitation = np.zeros(int(ring * SR))
    excitation[:n_delay] = burst
    out = signal.lfilter([1.0], a, excitation)
    fade = int(0.08 * SR)
    out[-fade:] *= np.linspace(1.0, 0.0, fade)
    return out / max(1e-9, np.abs(out).max()) * velocity


def flute(midi: int, seconds: float, velocity: float, rng: np.random.Generator) -> np.ndarray:
    """Breathy wooden flute with delayed vibrato and an attack chiff."""
    f = hz(midi)
    release = 0.14
    t = _times(seconds + release)
    attack = 0.07
    env = smoothstep(t / attack) * (1.0 - 0.12 * np.clip((t - attack) / 1.5, 0.0, 1.0))
    env *= np.clip(1.0 - (t - seconds) / release, 0.0, 1.0)

    vib_rate = 5.0 + rng.uniform(-0.25, 0.25)
    vib_depth = 0.0045 * smoothstep((t - 0.22) / 0.4)
    inst_f = f * (1.0 + vib_depth * np.sin(2 * np.pi * vib_rate * t))
    inst_f *= 1.0 - 0.006 * np.exp(-t / 0.03)
    phase = 2 * np.pi * np.cumsum(inst_f) / SR
    tone = np.sin(phase) + 0.18 * np.sin(2 * phase) + 0.07 * np.sin(3 * phase) + 0.03 * np.sin(4 * phase)

    band = signal.butter(2, [f * 0.8, min(f * 6.0, 9000.0)], btype="band", fs=SR, output="sos")
    breath = signal.sosfilt(band, rng.standard_normal(len(t)))
    breath *= 0.07 + 0.35 * np.exp(-t / 0.03)
    return env * (0.8 * tone + breath) * velocity


def pad(midis: list[int], seconds: float, rng: np.random.Generator) -> tuple[np.ndarray, np.ndarray]:
    """Warm detuned-saw string pad, wide in stereo."""
    attack, release = 0.7, 0.9
    t = _times(seconds + release)
    left = np.zeros(len(t))
    right = np.zeros(len(t))
    for midi in midis:
        f = hz(midi)
        for target, cents in ((left, -6.0), (left, 2.0), (right, 6.0), (right, -2.0)):
            ff = f * 2.0 ** (cents / 1200.0)
            target += 2.0 * ((ff * t + rng.random()) % 1.0) - 1.0
    lowpass = signal.butter(4, 1100.0, fs=SR, output="sos")
    env = smoothstep(t / attack) * np.clip(1.0 - (t - seconds) / release, 0.0, 1.0)
    scale = env / (2.0 * len(midis))
    return signal.sosfilt(lowpass, left) * scale, signal.sosfilt(lowpass, right) * scale


def bass(midi: int, seconds: float, velocity: float) -> np.ndarray:
    """Soft bowed bass."""
    f = hz(midi)
    release = 0.3
    t = _times(seconds + release)
    env = smoothstep(t / 0.08) * (0.8 + 0.2 * np.exp(-t / 0.4))
    env *= np.clip(1.0 - (t - seconds) / release, 0.0, 1.0)
    phase = 2 * np.pi * f * t
    tone = np.sin(phase) + 0.45 * np.sin(2 * phase) + 0.2 * np.sin(3 * phase) + 0.08 * np.sin(4 * phase)
    return env * tone * velocity / 1.5


def drum(f_start: float, f_end: float, decay: float, click: float, rng: np.random.Generator) -> np.ndarray:
    """Frame drum: pitch-dropping membrane plus a short skin click."""
    t = _times(decay * 7.0)
    inst_f = f_end + (f_start - f_end) * np.exp(-t / 0.03)
    phase = 2 * np.pi * np.cumsum(inst_f) / SR
    body = np.sin(phase) * np.exp(-t / decay)
    body += 0.3 * np.sin(1.59 * phase + 0.3) * np.exp(-t / (decay * 0.5))
    lowpass = signal.butter(2, 3000.0, fs=SR, output="sos")
    skin = signal.sosfilt(lowpass, rng.standard_normal(len(t))) * np.exp(-t / 0.01) * click
    return (body + skin) * smoothstep(t / 0.001)


def shaker(rng: np.random.Generator) -> np.ndarray:
    t = _times(0.12)
    highpass = signal.butter(2, 5000.0, btype="high", fs=SR, output="sos")
    noise = signal.sosfilt(highpass, rng.standard_normal(len(t)))
    return noise * smoothstep(t / 0.006) * np.exp(-t / 0.03)


# --- mixing ----------------------------------------------------------------


def reverb_ir(rng: np.random.Generator, rt60: float = 1.9, length: float = 2.8, predelay: float = 0.018) -> np.ndarray:
    """Stereo hall impulse response with unit energy per channel."""
    t = _times(length)
    lowpass = signal.butter(2, 2500.0, fs=SR, output="sos")
    ir = np.zeros((2, len(t)))
    for ch in range(2):
        bright = rng.standard_normal(len(t)) * np.exp(-6.91 * t / (rt60 * 0.45))
        dark = signal.sosfilt(lowpass, rng.standard_normal(len(t))) * np.exp(-6.91 * t / rt60) * 1.6
        tail = (0.5 * bright + dark) * smoothstep((t - predelay) / 0.04)
        tail_rms = np.sqrt(np.sum(tail**2))
        for _ in range(10):
            delay = rng.uniform(0.006, 0.075)
            tail[int(delay * SR)] += rng.choice([-1.0, 1.0]) * rng.uniform(0.05, 0.12) * tail_rms * np.exp(-delay / 0.05)
        ir[ch] = tail / np.sqrt(np.sum(tail**2))
    return ir


def _pan(pan: float) -> tuple[float, float]:
    angle = (pan + 1.0) * np.pi / 4.0
    return np.cos(angle), np.sin(angle)


class LoopMixer:
    """Mixes sounds onto a loop of fixed length with a dry bus and a reverb send."""

    def __init__(self, seconds: float, tail: float = 5.0):
        self.loop = int(round(seconds * SR))
        size = self.loop + int(tail * SR)
        self.dry = np.zeros((2, size))
        self.send = np.zeros((2, size))

    def add(self, start: float, mono: np.ndarray, gain: float, pan: float = 0.0, reverb: float = 0.3) -> None:
        left, right = _pan(pan)
        self.add_stereo(start, mono * left, mono * right, gain, reverb)

    def add_stereo(self, start: float, left: np.ndarray, right: np.ndarray, gain: float, reverb: float = 0.3) -> None:
        i = int(round(start * SR))
        end = min(i + len(left), self.dry.shape[1])
        for ch, sound in enumerate((left, right)):
            part = sound[: end - i] * gain
            self.dry[ch, i:end] += part
            self.send[ch, i:end] += part * reverb

    def _folded(self, bus: np.ndarray) -> np.ndarray:
        out = bus[:, : self.loop].copy()
        tail = bus[:, self.loop :]
        out[:, : tail.shape[1]] += tail
        return out

    def render(self, ir: np.ndarray, wet: float = 0.5) -> np.ndarray:
        dry = self._folded(self.dry)
        send = self._folded(self.send)
        n = self.loop
        # Circular convolution is the steady-state reverb of an endlessly repeating loop.
        wet_bus = np.fft.irfft(np.fft.rfft(send, n) * np.fft.rfft(ir, n), n)
        mix = dry + wet * wet_bus
        highpass = signal.butter(2, 35.0, btype="high", fs=SR, output="sos")
        tiled = signal.sosfilt(highpass, np.tile(mix, 3), axis=1)
        return tiled[:, n : 2 * n]


def master(mix: np.ndarray, target_lufs: float = -18.0, peak_ceiling: float = 0.89) -> tuple[np.ndarray, float, float]:
    """Gains the loop to the loudness target, never past the peak ceiling."""
    import pyloudnorm

    meter = pyloudnorm.Meter(SR)
    loudness = meter.integrated_loudness(np.tile(mix, 2).T)
    gain = 10.0 ** ((target_lufs - loudness) / 20.0)
    gain = min(gain, peak_ceiling / np.abs(mix).max())
    out = mix * gain
    return out, meter.integrated_loudness(np.tile(out, 2).T), 20 * np.log10(np.abs(out).max())


def write_wav(path: str, mix: np.ndarray, rng: np.random.Generator) -> None:
    dither = (rng.random(mix.shape) - rng.random(mix.shape)) / 32768.0
    pcm = np.clip(np.round((mix + dither) * 32767.0), -32768, 32767).astype(np.int16)
    wavfile.write(path, SR, pcm.T)
