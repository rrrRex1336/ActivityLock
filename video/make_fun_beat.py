#!/usr/bin/env python3
"""20 s playful electronic bed for the fast Locky cut (no voice)."""
import wave
from pathlib import Path
import numpy as np

SR = 48000
DURATION = 20.0
BPM = 120.0
BEAT = 60.0 / BPM
n = int(SR * DURATION)
t = np.arange(n) / SR
out = np.zeros((n, 2), dtype=np.float64)
rng = np.random.default_rng(7)

def add(sig, at, gain=1.0, pan=0.0):
    s = int(at * SR)
    e = min(n, s + len(sig))
    if e <= s:
        return
    seg = sig[:e - s] * gain
    out[s:e, 0] += seg * (1 - max(0.0, pan) * 0.3)
    out[s:e, 1] += seg * (1 + min(0.0, pan) * 0.3)

def kick():
    m = int(0.26 * SR)
    tt = np.arange(m) / SR
    env = np.exp(-tt * 24)
    return np.sin(2 * np.pi * (50 + 90 * np.exp(-tt * 30)) * tt) * env

def clap():
    m = int(0.18 * SR)
    tt = np.arange(m) / SR
    env = np.exp(-tt * 38)
    noise = rng.normal(0, 1, m)
    # crude band-pass: difference of smoothed noise
    noise = np.diff(np.concatenate([[0], noise]))
    return noise * env * 0.35

def hat(open_=False):
    m = int((0.09 if open_ else 0.045) * SR)
    tt = np.arange(m) / SR
    env = np.exp(-tt * (35 if not open_ else 12))
    return rng.normal(0, 1, m) * env * 0.12

def pluck(freq, dur=0.24):
    m = int(dur * SR)
    tt = np.arange(m) / SR
    env = np.exp(-tt * 10)
    return (np.sin(2 * np.pi * freq * tt) + 0.35 * np.sin(2 * np.pi * freq * 2 * tt)) * env * 0.18

# 10 bars, 2 s each (120 BPM).
bars = int(DURATION / (BEAT * 4))
for b in range(bars + 1):
    base = b * BEAT * 4
    if base >= DURATION:
        break
    # Four-on-the-floor kick.
    for beat in range(4):
        at = base + beat * BEAT
        if at < DURATION:
            add(kick(), at, 0.9)
    # Clap on 2 and 4.
    for beat in (1, 3):
        at = base + beat * BEAT
        if at < DURATION:
            add(clap(), at, 0.85, 0.1)
    # Hi-hats on eighths.
    for i in range(8):
        at = base + i * BEAT / 2
        if at < DURATION:
            add(hat(i % 4 == 3), at, 0.55, -0.15)
    # Bass line: C, A, F, G — one note per bar.
    roots = [65.41, 55.00, 43.65, 49.00]
    root = roots[b % 4]
    for i in range(4):
        at = base + i * BEAT
        if at < DURATION:
            add(pluck(root / 2, 0.32), at, 1.0, -0.15)
    # Plucky upper melody, syncopated.
    scale = [261.63, 329.63, 392.00, 523.25]
    for i, offset in enumerate([0.0, 0.75, 1.5, 2.25, 3.0]):
        at = base + offset * BEAT
        if at < DURATION:
            add(pluck(scale[(b + i) % 4], 0.18), at, 0.7, 0.25)

# Gentle fade in/out; do not slam the first frame.
out *= np.clip(t / 0.15, 0, 1)[:, None]
out *= np.clip((DURATION - t) / 1.0, 0, 1)[:, None]
peak = np.max(np.abs(out))
out = out / peak * 0.62
pcm = (np.clip(out, -1, 1) * 32767).astype(np.int16)
path = Path(__file__).resolve().parent / 'locky-fun.wav'
with wave.open(str(path), 'wb') as wf:
    wf.setnchannels(2)
    wf.setsampwidth(2)
    wf.setframerate(SR)
    wf.writeframes(pcm.tobytes())
print(path, DURATION)
