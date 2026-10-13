"""Per-layer loudness of an ambience: re-renders it with every mixer call tagged by its call site."""
import importlib
import inspect
import sys

import numpy as np
import pyloudnorm

import ambience
import synth

stems = {}
original = synth.LoopMixer.add_stereo


def tagged(self, start, left, right, gain, reverb=0.3):
    frames = inspect.stack()[1:4]
    frame = next(f for f in frames if f.function not in ("add", "add_stereo"))
    tag = f"{frame.function}:{frame.lineno}"
    bus = stems.setdefault(tag, np.zeros_like(self.dry))
    i = int(round(start * synth.SR))
    end = min(i + len(left), bus.shape[1])
    bus[0, i:end] += left[: end - i] * gain
    bus[1, i:end] += right[: end - i] * gain
    original(self, start, left, right, gain, reverb)


def fake_finish(name, mixer, rng, out_dir, rt60, wet, predelay=0.02, target_lufs=-22.0, peak_ceiling=0.70, highpass_hz=70.0):
    mix = mixer.render(synth.reverb_ir(rng, rt60=rt60, length=min(rt60 * 1.5, 7.0), predelay=predelay), wet=wet)
    out, _, _ = synth.master(mix, target_lufs=target_lufs, peak_ceiling=peak_ceiling)
    gain = np.abs(out).max() / np.abs(mix).max()
    meter = pyloudnorm.Meter(synth.SR)
    for tag, bus in sorted(stems.items(), key=lambda kv: kv[0]):
        x = mixer._folded(bus) * gain
        loud = meter.integrated_loudness(np.tile(x, 2).T)
        print(f"  {tag:28s} {loud:6.1f} LUFS  peak {20 * np.log10(np.abs(x).max() + 1e-12):6.1f} dBFS")


synth.LoopMixer.add_stereo = tagged
ambience.finish = fake_finish
module = importlib.import_module(sys.argv[1])
module.ambience.finish = fake_finish
module.main(None)
