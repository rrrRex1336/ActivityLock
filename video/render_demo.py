#!/usr/bin/env python3
"""Calm, minimal Locky demo video. Vertical 1080x1920, 18 s."""
from __future__ import annotations

import argparse
import math
import wave
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
DOCS = ROOT / "docs"
OUT = ROOT / ".tmp" / "demo"
FRAMES = OUT / "frames"
OUT.mkdir(parents=True, exist_ok=True)
FRAMES.mkdir(parents=True, exist_ok=True)

W, H = 1080, 1920
FPS = 30
DURATION = 18.0
N = int(FPS * DURATION)

FONT_DIR = Path("/usr/share/fonts/noto-cjk")
F_BOLD = str(FONT_DIR / "NotoSansCJK-Bold.ttc")
F_MED = str(FONT_DIR / "NotoSansCJK-Medium.ttc")
F_REG = str(FONT_DIR / "NotoSansCJK-Regular.ttc")

SURFACE = "#FBF7FE"
SURFACE_2 = "#F0E8F4"
PRIMARY = "#6750A4"
PRIMARY_SOFT = "#EADDFF"
ON_SURFACE = "#1D1B20"
ON_VARIANT = "#625B71"
DARK = "#141218"

_fonts = {}


def font(path, size):
    key = (path, size)
    if key not in _fonts:
        _fonts[key] = ImageFont.truetype(path, size)
    return _fonts[key]


def clamp01(x):
    return max(0.0, min(1.0, x))


def ease(x):
    x = clamp01(x)
    return x * x * (3 - 2 * x)


def fade(t, start, end):
    return ease((t - start) / max(0.001, end - start))


def load(path):
    return Image.open(path).convert("RGB")


SHOT_MAIN = load(DOCS / "screenshot-main.png")
SHOT_DARK = load(DOCS / "screenshot-main-night.png")
SHOT_EN = load(DOCS / "screenshot-main-en.png")
SHOT_CONTROL = load(DOCS / "screenshot-control.png")
ICON = Image.open(DOCS / "icon.png").convert("RGBA")

PAN_W = W
PAN_H = 2280


def crop_pan(img, top):
    im = img.resize((PAN_W, PAN_H), Image.Resampling.LANCZOS)
    top = max(0, min(PAN_H - H, int(top)))
    return im.crop((0, top, W, top + H))


def rounded_pill(draw, box, fill):
    draw.rounded_rectangle(box, radius=(box[3] - box[1]) // 2, fill=fill)


def draw_caption(img: Image.Image, text: str, alpha: float):
    if alpha <= 0:
        return
    overlay = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(overlay)
    f = font(F_MED, 29)
    tw = d.textlength(text, font=f)
    box_w = int(tw + 56)
    box_h = 62
    x = (W - box_w) // 2
    y = 84
    a = int(235 * alpha)
    rounded_pill(d, (x, y, x + box_w, y + box_h), (18, 16, 22, a))
    d.text((x + 28, y + 13), text, font=f, fill=(255, 252, 255, a))
    img.alpha_composite(overlay)


def base_light():
    img = Image.new("RGB", (W, H), SURFACE)
    d = ImageDraw.Draw(img)
    d.ellipse((640, -340, 1480, 500), fill=PRIMARY_SOFT)
    d.ellipse((-380, 1320, 420, 2120), fill=SURFACE_2)
    return img.convert("RGBA")


def intro(t):
    local = t
    img = base_light()
    # icon and text enter gently
    e = ease(clamp01(local / 1.1))
    a = int(255 * e)
    icon = ICON.resize((220, 220), Image.Resampling.LANCZOS)
    ix = (W - 220) // 2
    iy = int(525 - (1 - e) * 24)
    # soft shadow under the icon
    sh = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    sd = ImageDraw.Draw(sh)
    sd.rounded_rectangle((ix + 8, iy + 20, ix + 212, iy + 224), radius=46,
                         fill=(33, 0, 93, int(50 * e)))
    sh = sh.filter(ImageFilter.GaussianBlur(18))
    img.alpha_composite(sh)
    im = icon.copy()
    im.putalpha(im.getchannel("A").point(lambda v: int(v * e)))
    img.alpha_composite(im, (ix, iy))
    d = ImageDraw.Draw(img)
    if a:
        title = "Locky"
        d.text(((W - d.textlength(title, font=font(F_BOLD, 86))) / 2, 810), title,
               font=font(F_BOLD, 86), fill=(*hex_rgb(ON_SURFACE), a))
        sub = "锁定游戏，只留下节奏。"
        d.text(((W - d.textlength(sub, font=font(F_MED, 31))) / 2, 930), sub,
               font=font(F_MED, 31), fill=(*hex_rgb(ON_VARIANT), a))
        small = "ROOT · MATERIAL YOU"
        d.text(((W - d.textlength(small, font=font(F_MED, 20))) / 2, 1010), small,
               font=font(F_MED, 20), fill=(*hex_rgb(PRIMARY), int(a * 0.8)))
    # fade out to the app shot
    fo = 1.0 - fade(t, 2.15, 2.65)
    if fo < 1:
        img = Image.blend(img, Image.new("RGBA", (W, H), (*hex_rgb(SURFACE), 255)), fo)
    return img.convert("RGB")


def shot_main(t):
    local = t - 2.4
    # 5.4 s pan from top to slightly lower, with a slow build
    p = ease(clamp01(local / 5.4))
    top = 0 + p * 330
    frame = crop_pan(SHOT_MAIN, top).convert("RGBA")
    draw_caption(frame, "选择游戏，一键锁定", fade(t, 2.8, 3.3) * (1 - fade(t, 7.3, 7.8)))
    return frame.convert("RGB")


def shot_control(t):
    local = t - 7.8
    p = ease(clamp01(local / 4.0))
    top = 240 + p * 110
    frame = crop_pan(SHOT_CONTROL, top).convert("RGBA")
    draw_caption(frame, "悬浮圆点 · 一键退出或切换", fade(t, 8.2, 8.8) * (1 - fade(t, 11.6, 12.0)))
    return frame.convert("RGB")


def shot_intl(t):
    local = t - 11.9
    p = ease(clamp01(local / 2.3))
    img = crop_pan(SHOT_EN, 0 + p * 120).convert("RGBA")
    draw_caption(img, "English · 中文 · Material You", fade(t, 12.2, 12.7) * (1 - fade(t, 14.1, 14.5)))
    return img.convert("RGB")


def outro(t):
    local = t - 14.3
    e = ease(clamp01(local / 1.0))
    img = base_light()
    d = ImageDraw.Draw(img)
    icon = ICON.resize((150, 150), Image.Resampling.LANCZOS)
    im = icon.copy()
    im.putalpha(im.getchannel("A").point(lambda v: int(v * e)))
    img.alpha_composite(im, ((W - 150) // 2, 570))
    a = int(255 * e)
    d = ImageDraw.Draw(img)
    if a:
        name = "Locky"
        d.text(((W - d.textlength(name, font=font(F_BOLD, 76))) / 2, 760), name,
               font=font(F_BOLD, 76), fill=(*hex_rgb(ON_SURFACE), a))
        sub = "Root only. Zero gesture noise."
        d.text(((W - d.textlength(sub, font=font(F_MED, 26))) / 2, 870), sub,
               font=font(F_MED, 26), fill=(*hex_rgb(ON_VARIANT), a))
        zh = "锁定游戏，只留下节奏。"
        d.text(((W - d.textlength(zh, font=font(F_REG, 24))) / 2, 920), zh,
               font=font(F_REG, 24), fill=(*hex_rgb(ON_VARIANT), a))
        box = (150, 1030, 930, 1110)
        d.rounded_rectangle(box, radius=40, fill=(*hex_rgb(PRIMARY_SOFT), int(a * 0.8)))
        url = "github.com/rrrRex1336/ActivityLock"
        d.text(((W - d.textlength(url, font=font(F_MED, 26))) / 2, 1052), url,
               font=font(F_MED, 26), fill=(*hex_rgb(PRIMARY), a))
    # gentle fade to the light surface, then end
    fo = 1.0 - fade(t, 17.15, 17.65)
    if fo < 1:
        img = Image.blend(img, Image.new("RGBA", (W, H), (*hex_rgb(SURFACE), 255)), fo)
    return img.convert("RGB")


SCENES = [
    (0.0, 2.4, intro),
    (2.4, 7.8, shot_main),
    (7.8, 11.9, shot_control),
    (11.9, 14.3, shot_intl),
    (14.3, 18.0, outro),
]


def frame_at(t):
    idx = 0
    for i, (start, end, fn) in enumerate(SCENES):
        if start <= t < end:
            idx = i
            break
    img = SCENES[idx][2](t)
    # Tiny crossfades between scenes to avoid hard cuts.
    for b in (2.4, 7.8, 11.9, 14.3):
        if abs(t - b) < 0.18:
            left = idx - 1 if t < b else idx
            right = idx if t < b else idx + 1
            if 0 <= left < len(SCENES) and 0 <= right < len(SCENES):
                p = ease((t - (b - 0.18)) / 0.36)
                img = Image.blend(SCENES[left][2](t), SCENES[right][2](t), p)
            break
    return img


def hex_rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def preview(times):
    for t in times:
        p = OUT / f"preview-{t:05.2f}.png"
        frame_at(t).save(p)
        print(p)


def frames():
    for i in range(N):
        p = FRAMES / f"{i:05d}.png"
        if not p.exists():
            frame_at(i / FPS).save(p)
    print("frames complete")


def audio():
    sr = 48000
    n = int(sr * DURATION)
    t = np.arange(n) / sr
    out = np.zeros((n, 2), dtype=np.float64)
    rng = np.random.default_rng(11)

    progression = [
        (130.81, 196.00, 261.63, 329.63),  # Cmaj7
        (110.00, 164.81, 220.00, 261.63),  # Am7
        (87.31, 130.81, 174.61, 220.00),   # Fmaj7
        (98.00, 146.83, 196.00, 246.94),   # G
    ]

    # Very soft ambient pad with slow chord changes.
    bar = 4.5
    for bi in range(int(DURATION / bar) + 1):
        start = bi * bar
        length = min(bar * 1.1, DURATION - start)
        if length <= 0:
            break
        m = int(length * sr)
        tt = np.arange(m) / sr
        env = np.ones(m)
        a = int(0.8 * sr)
        r = int(0.8 * sr)
        if a > 0:
            env[:min(a, m)] = np.linspace(0, 1, min(a, m))
        if r > 0:
            env[-min(r, m):] *= np.linspace(1, 0, min(r, m))
        chord = progression[bi % len(progression)]
        for j, freq in enumerate(chord):
            amp = 0.035 if j < 2 else 0.022
            sig = (amp * np.sin(2 * np.pi * freq * tt)
                   + amp * 0.35 * np.sin(2 * np.pi * freq * 2 * tt))
            pan = -0.3 + j * 0.2
            seg_l = sig * env * (1 - max(0.0, pan) * 0.25)
            seg_r = sig * env * (1 + min(0.0, pan) * 0.25)
            s = int(start * sr)
            e = min(n, s + m)
            if e > s:
                out[s:e, 0] += seg_l[:e - s]
                out[s:e, 1] += seg_r[:e - s]

    # Airy high shimmer.
    for bi in range(int(DURATION / 1.5) + 1):
        start = bi * 1.5 + 0.3
        if start >= DURATION - 1:
            continue
        m = int(1.1 * sr)
        tt = np.arange(m) / sr
        env = np.exp(-tt * 3.0) * 0.012
        sig = np.sin(2 * np.pi * 1046.5 * tt) * env
        s = int(start * sr)
        e = min(n, s + m)
        if e > s:
            out[s:e, 0] += sig[:e - s] * 0.7
            out[s:e, 1] += sig[:e - s] * 0.4

    # Master fades and a gentle noise floor.
    out *= np.clip(t / 1.5, 0, 1)[:, None] * np.clip((DURATION - t) / 1.8, 0, 1)[:, None]
    peak = np.max(np.abs(out))
    if peak > 0:
        out = out / peak * 0.55
    pcm = (np.clip(out, -1, 1) * 32767).astype(np.int16)
    path = OUT / "audio.wav"
    with wave.open(str(path), "wb") as wf:
        wf.setnchannels(2)
        wf.setsampwidth(2)
        wf.setframerate(sr)
        wf.writeframes(pcm.tobytes())
    print(path)


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview", nargs="*", type=float)
    ap.add_argument("--frames", action="store_true")
    ap.add_argument("--audio", action="store_true")
    args = ap.parse_args()
    if args.preview:
        preview(args.preview)
    if args.frames:
        frames()
    if args.audio:
        audio()
