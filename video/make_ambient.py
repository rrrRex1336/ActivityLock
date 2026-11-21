#!/usr/bin/env python3
"""Generate a 30 s soft ambient bed for the Locky demo (no drums, no voice)."""
import math
import wave
from pathlib import Path

import numpy as np

SR = 48000
DURATION = 30.0
n = int(SR * DURATION)
t = np.arange(n) / SR
out = np.zeros((n, 2), dtype=np.float64)
rng = np.random.default_rng(23)

# Warm, slowly changing chords.
progression = [
    (130.81, 196.00, 261.63, 329.63),  # Cmaj7
    (110.00, 164.81, 220.00, 261.63),  # Am7
    (87.31, 130.81, 174.61, 220.00),   # Fmaj7
    (98.00, 146.83, 196.00, 246.94),   # G
]

bar = 5.0
for bi in range(int(DURATION / bar) + 1):
    start = bi * bar
    if start >= DURATION:
        break
    length = min(bar * 1.15, DURATION - start)
    m = int(length * SR)
    tt = np.arange(m) / SR
    env = np.ones(m)
    a = int(1.2 * SR)
    r = int(1.2 * SR)
    if a:
        env[:min(a, m)] = np.linspace(0, 1, min(a, m))
    if r:
        env[-min(r, m):] *= np.linspace(1, 0, min(r, m))
    chord = progression[bi % 4]
    for j, freq in enumerate(chord):
        amp = 0.032 if j < 2 else 0.018
        sig = amp * np.sin(2 * np.pi * freq * tt) + amp * 0.28 * np.sin(2 * np.pi * freq * 2 * tt)
        pan = -0.35 + j * 0.23
        s = int(start * SR)
        e = min(n, s + m)
        if e <= s:
            continue
        seg = sig[:e - s] * env[:e - s]
        out[s:e, 0] += seg * (1 - max(0.0, pan) * 0.25)
        out[s:e, 1] += seg * (1 + min(0.0, pan) * 0.25)

# Very soft high shimmer, irregular but deterministic.
for i in range(18):
    start = 1.2 + i * 1.55
    if start >= DURATION - 1:
        break
    m = int(1.0 * SR)
    tt = np.arange(m) / SR
    env = np.exp(-tt * 3.2) * 0.009
    freq = [1046.50, 1318.51, 1567.98][i % 3]
    sig = np.sin(2 * np.pi * freq * tt) * env
    s = int(start * SR)
    e = min(n, s + m)
    if e <= s:
        continue
    pan = 0.25 if i % 2 else -0.3
    out[s:e, 0] += sig[:e - s] * (1 - max(0, pan) * 0.3)
    out[s:e, 1] += sig[:e - s] * (1 + min(0, pan) * 0.3)

# Barely-audible air / noise floor, gently filtered.
noise = rng.normal(0, 1, n)
for i in range(1, n):
    noise[i] = noise[i - 1] * 0.985 + noise[i] * 0.015
out[:, 0] += noise * 0.0028
out[:, 1] += np.roll(noise, 37) * 0.0024

out *= np.clip(t / 2.0, 0, 1)[:, None]
out *= np.clip((DURATION - t) / 2.8, 0, 1)[:, None]
peak = np.max(np.abs(out))
out = out / peak * 0.48
pcm = (np.clip(out, -1, 1) * 32767).astype(np.int16)
path = Path(__file__).resolve().parent / "locky-ambient.wav"
with wave.open(str(path), "wb") as wf:
    wf.setnchannels(2)
    wf.setsampwidth(2)
    wf.setframerate(SR)
    wf.writeframes(pcm.tobytes())
print(path, DURATION)
