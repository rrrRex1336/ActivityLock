#!/usr/bin/env python3
"""Render the Locky promo. Requires Pillow and numpy.

Usage:
  python3 video/render_promo.py --preview 1.5 6.0 11.0 16.0 21.0 26.0
  python3 video/render_promo.py --frames
  python3 video/render_promo.py --audio
"""
from __future__ import annotations

import argparse
import math
import os
import wave
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
DOCS = ROOT / "docs"
OUT = ROOT / ".tmp" / "video"
FRAMES = OUT / "frames"
OUT.mkdir(parents=True, exist_ok=True)
FRAMES.mkdir(parents=True, exist_ok=True)

W, H = 1920, 1080
FPS = 30
DURATION = 28.0
NFRAMES = int(FPS * DURATION)

FONT_DIR = Path("/usr/share/fonts/noto-cjk")
FONT_BLACK = str(FONT_DIR / "NotoSansCJK-Black.ttc")
FONT_BOLD = str(FONT_DIR / "NotoSansCJK-Bold.ttc")
FONT_MED = str(FONT_DIR / "NotoSansCJK-Medium.ttc")
FONT_REG = str(FONT_DIR / "NotoSansCJK-Regular.ttc")

DARK = "#141218"
DARK_2 = "#1D1B20"
DARK_3 = "#2B2930"
LIGHT = "#FDF7FF"
LIGHT_2 = "#F3EDF7"
PRIMARY = "#6750A4"
PRIMARY_LIGHT = "#EADDFF"
ON_PRIMARY_LIGHT = "#21005D"
ON_DARK = "#F5EFF7"
ON_DARK_MUTED = "#CAC4D0"
ON_LIGHT = "#1D1B20"
ON_LIGHT_MUTED = "#625B71"
ON_PRIMARY = "#FFFFFF"

FONT_CACHE: dict[tuple[str, int], ImageFont.FreeTypeFont] = {}


def font(path: str, size: int) -> ImageFont.FreeTypeFont:
    key = (path, size)
    if key not in FONT_CACHE:
        FONT_CACHE[key] = ImageFont.truetype(path, size)
    return FONT_CACHE[key]


def ease_out_cubic(x: float) -> float:
    x = max(0.0, min(1.0, x))
    return 1.0 - (1.0 - x) ** 3


def ease_in_out(x: float) -> float:
    x = max(0.0, min(1.0, x))
    return x * x * (3.0 - 2.0 * x)


def lerp(a: float, b: float, p: float) -> float:
    return a + (b - a) * p


def clamp01(x: float) -> float:
    return max(0.0, min(1.0, x))


def draw_tracked(draw: ImageDraw.ImageDraw, xy, text, fnt, fill, tracking=2):
    x, y = xy
    for ch in text:
        draw.text((x, y), ch, font=fnt, fill=fill)
        w = draw.textlength(ch, font=fnt)
        x += w + tracking


def text_block(draw, xy, lines, fnt, fill, line_gap=12):
    x, y = xy
    for line in lines:
        draw.text((x, y), line, font=fnt, fill=fill)
        y += fnt.size + line_gap


def round_image(im: Image.Image, radius: int) -> Image.Image:
    mask = Image.new("L", im.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, im.width - 1, im.height - 1),
                                           radius=radius, fill=255)
    out = Image.new("RGBA", im.size, (0, 0, 0, 0))
    out.paste(im.convert("RGBA"), (0, 0), mask)
    return out


def make_phone(screenshot: Path, screen_w: int = 404) -> Image.Image:
    shot = Image.open(screenshot).convert("RGB")
    screen_h = round(screen_w * shot.height / shot.width)
    shot = shot.resize((screen_w, screen_h), Image.Resampling.LANCZOS)
    border = 12
    radius = 40
    frame_w, frame_h = screen_w + border * 2, screen_h + border * 2
    pad = 50
    frame = Image.new("RGBA", (frame_w + pad * 2, frame_h + pad * 2), (0, 0, 0, 0))
    shadow = Image.new("RGBA", frame.size, (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    sd.rounded_rectangle((pad + 4, pad + 18, pad + frame_w - 4, pad + frame_h + 10),
                         radius=radius + border, fill=(0, 0, 0, 100))
    shadow = shadow.filter(ImageFilter.GaussianBlur(24))
    frame.alpha_composite(shadow)
    fd = ImageDraw.Draw(frame)
    fd.rounded_rectangle((pad, pad, pad + frame_w, pad + frame_h),
                         radius=radius + border, fill=(10, 10, 12, 255))
    fd.rounded_rectangle((pad + 4, pad + 4, pad + frame_w - 4, pad + frame_h - 4),
                         radius=radius + border - 4, fill=(28, 27, 31, 255))
    screen = round_image(shot, radius)
    frame.alpha_composite(screen, (pad + border, pad + border))
    # soft edge highlight
    fd.rounded_rectangle((pad + 1, pad + 1, pad + frame_w - 1, pad + frame_h - 1),
                         radius=radius + border, outline=(255, 255, 255, 32), width=2)
    return frame


ICON = Image.open(DOCS / "icon.png").convert("RGBA")
PHONE_MAIN = make_phone(DOCS / "screenshot-main.png")
PHONE_DARK = make_phone(DOCS / "screenshot-main-night.png")
PHONE_EN = make_phone(DOCS / "screenshot-main-en.png")
PHONE_CONTROL = make_phone(DOCS / "screenshot-control.png")


def background(kind: str, t: float) -> Image.Image:
    if kind == "intro":
        img = Image.new("RGB", (W, H), DARK_2)
        d = ImageDraw.Draw(img)
        d.ellipse((-300, 520, 1100, 1920), fill="#4F378B")
        d.ellipse((1260, -420, 2300, 620), fill=DARK_3)
        d.rounded_rectangle((1250, 730, 1780, 1020), radius=50, fill="#2B2930")
    elif kind == "light":
        img = Image.new("RGB", (W, H), LIGHT)
        d = ImageDraw.Draw(img)
        d.ellipse((1330, -260, 2350, 760), fill=PRIMARY_LIGHT)
        d.ellipse((-420, 700, 520, 1640), fill=LIGHT_2)
    elif kind == "dark":
        img = Image.new("RGB", (W, H), DARK)
        d = ImageDraw.Draw(img)
        d.ellipse((1330, -260, 2350, 760), fill="#2B2930")
        d.ellipse((-380, 700, 520, 1600), fill="#211F26")
    elif kind == "locale":
        img = Image.new("RGB", (W, H), LIGHT)
        d = ImageDraw.Draw(img)
        d.ellipse((1240, -360, 2320, 720), fill=PRIMARY_LIGHT)
        d.rounded_rectangle((-140, 760, 700, 1320), radius=80, fill=LIGHT_2)
    else:  # outro
        img = Image.new("RGB", (W, H), ON_PRIMARY_LIGHT)
        d = ImageDraw.Draw(img)
        d.ellipse((-500, 500, 900, 1900), fill="#4F378B")
        d.ellipse((1400, -500, 2500, 600), fill="#6750A4")
    return img


def paste_centered(base: Image.Image, im: Image.Image, x: int, y: int,
                   alpha: float = 1.0, scale: float = 1.0):
    if scale != 1.0:
        im = im.resize((max(1, int(im.width * scale)), max(1, int(im.height * scale))),
                       Image.Resampling.LANCZOS)
    if alpha <= 0:
        return
    if alpha < 1:
        im = im.copy()
        a = im.getchannel("A").point(lambda v: int(v * alpha))
        im.putalpha(a)
    base.alpha_composite(im, (int(x), int(y)))


def render_intro(t: float) -> Image.Image:
    img = background("intro", t).convert("RGBA")
    u = clamp01(t / 3.6)
    enter = ease_out_cubic(clamp01(t / 1.2))
    icon_scale = lerp(0.72, 1.0, enter)
    icon_alpha = ease_out_cubic(clamp01(t / 0.8))
    icon = ICON.resize((300, 300), Image.Resampling.LANCZOS)
    ix = int(lerp(150, 180, enter))
    iy = int(lerp(360, 390, enter) - 20 * math.sin(u * math.pi))
    paste_centered(img, icon, ix, iy, alpha=icon_alpha, scale=icon_scale / 1.0)

    fg = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(fg)
    a1 = int(255 * ease_out_cubic(clamp01((t - 0.25) / 0.8)))
    a2 = int(255 * ease_out_cubic(clamp01((t - 0.55) / 0.8)))
    a3 = int(255 * ease_out_cubic(clamp01((t - 0.85) / 0.8)))
    if a1:
        draw_tracked(d, (560, 310), "MATERIAL 3  ·  ROOT", font(FONT_BOLD, 25),
                     (*hex_rgb(PRIMARY_LIGHT), a1), 4)
    if a2:
        d.text((560, 360), "Locky", font=font(FONT_BLACK, 172), fill=(*hex_rgb(ON_DARK), a2))
    if a3:
        d.text((566, 570), "Lock in. Play on.", font=font(FONT_MED, 50), fill=(*hex_rgb(ON_DARK), a3))
        d.text((566, 650), "专注打歌，不误触。", font=font(FONT_REG, 36), fill=(*hex_rgb(ON_DARK_MUTED), a3))
    img.alpha_composite(fg)
    fade_out = 1.0 - ease_in_out(clamp01((t - 3.25) / 0.35))
    if fade_out < 1:
        img = Image.blend(Image.new("RGBA", (W, H), (*hex_rgb(DARK), 255)), img, fade_out)
    return img.convert("RGB")


def shot_phone(img, t, start, end, phone, x, y, local_in=None, local_out=None,
               label=None):
    pass


def render_one(t: float) -> Image.Image:
    # 3.6 .. 8.8
    u = clamp01((t - 3.6) / 5.2)
    img = background("light", t).convert("RGBA")
    enter = ease_out_cubic(clamp01((t - 3.6) / 0.85))
    px = int(lerp(1510, 1390, enter))
    py = int(lerp(120, 70, enter) + 8 * math.sin((t - 3.6) * 0.8))
    paste_centered(img, PHONE_MAIN, px, py, alpha=enter)
    fg = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(fg)
    a = int(255 * enter)
    d.text((125, 268), "01  /  FOCUS LOCK", font=font(FONT_BOLD, 25), fill=(*hex_rgb(PRIMARY), a))
    d.multiline_text((122, 320), "Pick a game.\nLock it in.", font=font(FONT_BLACK, 92),
                     fill=(*hex_rgb(ON_LIGHT), a), spacing=8)
    d.text((126, 575), "选择音游，一键锁定。", font=font(FONT_MED, 38), fill=(*hex_rgb(ON_LIGHT_MUTED), a))
    d.text((126, 652), "Gesture navigation off. Fullscreen on.", font=font(FONT_REG, 29),
           fill=(*hex_rgb(ON_LIGHT_MUTED), a))
    add_meta(d, a, 1)
    img.alpha_composite(fg)
    return img.convert("RGB")


def render_two(t: float) -> Image.Image:
    u = clamp01((t - 8.8) / 5.2)
    img = background("dark", t).convert("RGBA")
    enter = ease_out_cubic(clamp01((t - 8.8) / 0.85))
    px = int(lerp(120, 140, enter))
    py = int(lerp(150, 72, enter) + 7 * math.sin((t - 8.8) * 0.85))
    paste_centered(img, PHONE_DARK, px, py, alpha=enter)
    fg = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(fg)
    a = int(255 * enter)
    d.text((780, 268), "02  /  ROOT POWER", font=font(FONT_BOLD, 25), fill=(*hex_rgb(PRIMARY_LIGHT), a))
    d.multiline_text((780, 320), "No more\naccidental swipes.", font=font(FONT_BLACK, 86),
                     fill=(*hex_rgb(ON_DARK), a), spacing=8)
    d.text((784, 580), "强制三键导航 + 沉浸全屏。", font=font(FONT_MED, 38), fill=(*hex_rgb(ON_DARK_MUTED), a))
    d.text((784, 656), "The navigation overlay is switched at system level.", font=font(FONT_REG, 27),
           fill=(*hex_rgb(ON_DARK_MUTED), a))
    add_meta(d, a, 2)
    img.alpha_composite(fg)
    return img.convert("RGB")


def render_three(t: float) -> Image.Image:
    u = clamp01((t - 14.0) / 5.4)
    img = background("dark", t).convert("RGBA")
    enter = ease_out_cubic(clamp01((t - 14.0) / 0.85))
    px = int(lerp(1380, 1280, enter))
    py = int(lerp(122, 72, enter) + 7 * math.sin((t - 14) * 0.75))
    paste_centered(img, PHONE_CONTROL, px, py, alpha=enter)
    fg = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(fg)
    a = int(255 * enter)
    d.text((124, 268), "03  /  IN-GAME", font=font(FONT_BOLD, 25), fill=(*hex_rgb(PRIMARY_LIGHT), a))
    d.multiline_text((122, 320), "Unlock or switch\nwithout leaving.", font=font(FONT_BLACK, 86),
                     fill=(*hex_rgb(ON_DARK), a), spacing=8)
    d.text((126, 560), "悬浮圆点 · 一键退出 / 切换", font=font(FONT_MED, 38), fill=(*hex_rgb(ON_DARK_MUTED), a))
    d.text((126, 636), "A draggable bubble keeps controls in reach.", font=font(FONT_REG, 27),
           fill=(*hex_rgb(ON_DARK_MUTED), a))
    add_meta(d, a, 3)
    img.alpha_composite(fg)
    return img.convert("RGB")


def render_locale(t: float) -> Image.Image:
    u = clamp01((t - 19.4) / 4.2)
    img = background("locale", t).convert("RGBA")
    enter = ease_out_cubic(clamp01((t - 19.4) / 0.8))
    en = PHONE_EN.resize((int(PHONE_EN.width * 0.78), int(PHONE_EN.height * 0.78)),
                         Image.Resampling.LANCZOS)
    zh = PHONE_MAIN.resize((int(PHONE_MAIN.width * 0.78), int(PHONE_MAIN.height * 0.78)),
                           Image.Resampling.LANCZOS)
    x_en = int(lerp(850, 790, enter) + 16 * math.sin(u * math.pi))
    x_zh = int(lerp(1260, 1210, enter) - 16 * math.sin(u * math.pi))
    y = 120
    paste_centered(img, en, x_en, y, alpha=enter)
    paste_centered(img, zh, x_zh, y + 30, alpha=ease_out_cubic(clamp01((t - 19.6) / 0.8)))
    fg = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(fg)
    a = int(255 * enter)
    d.text((120, 268), "04  /  YOUR LANGUAGE", font=font(FONT_BOLD, 25), fill=(*hex_rgb(PRIMARY), a))
    d.multiline_text((118, 320), "At home in\nany language.", font=font(FONT_BLACK, 90),
                     fill=(*hex_rgb(ON_LIGHT), a), spacing=8)
    d.text((122, 560), "English · 中文", font=font(FONT_MED, 40), fill=(*hex_rgb(ON_LIGHT_MUTED), a))
    d.text((122, 642), "Material You dynamic color, everywhere.", font=font(FONT_REG, 27),
           fill=(*hex_rgb(ON_LIGHT_MUTED), a))
    d.text((850, 70), "EN", font=font(FONT_BOLD, 24), fill=(*hex_rgb(PRIMARY), int(a * 0.9)))
    d.text((1320, 70), "中文", font=font(FONT_MED, 24), fill=(*hex_rgb(PRIMARY), int(a * 0.9)))
    add_meta(d, a, 4)
    img.alpha_composite(fg)
    return img.convert("RGB")


def render_outro(t: float) -> Image.Image:
    local = t - 23.6
    img = background("outro", t).convert("RGBA")
    enter = ease_out_cubic(clamp01(local / 1.0))
    icon = ICON.resize((150, 150), Image.Resampling.LANCZOS)
    paste_centered(img, icon, 146, 160, alpha=enter)
    fg = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(fg)
    a = int(255 * enter)
    d.text((330, 160), "Locky", font=font(FONT_BLACK, 150), fill=(*hex_rgb(ON_DARK), a))
    d.text((336, 360), "Root only. Zero gesture noise.", font=font(FONT_MED, 42),
           fill=(*hex_rgb(ON_DARK), a))
    d.text((336, 428), "锁定游戏，只留下节奏。", font=font(FONT_REG, 32), fill=(*hex_rgb(ON_DARK_MUTED), a))
    d.rounded_rectangle((334, 520, 1470, 650), radius=28, fill=hex_rgb(DARK_2) + (int(215 * enter),))
    d.text((372, 552), "github.com/rrrRex1336/ActivityLock", font=font(FONT_MED, 38),
           fill=(*hex_rgb(ON_DARK), a))
    d.text((372, 608), "Download the APK from GitHub Releases", font=font(FONT_REG, 26),
           fill=(*hex_rgb(ON_DARK_MUTED), a))
    add_meta(d, a, 5)
    img.alpha_composite(fg)
    fade = 1.0 - ease_in_out(clamp01((t - 27.4) / 0.55))
    if fade < 1:
        img = Image.blend(Image.new("RGBA", (W, H), (*hex_rgb(DARK_2), 255)), img, fade)
    return img.convert("RGB")


def add_meta(d, alpha, index):
    a = int(alpha * 0.6)
    d.text((W - 170, H - 82), f"LOCKY  ·  0{index}", font=font(FONT_BOLD, 22),
           fill=(*hex_rgb(ON_DARK_MUTED), a) if index in (2, 3, 5) else (*hex_rgb(ON_LIGHT_MUTED), a))


def hex_rgb(h: str) -> tuple[int, int, int]:
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


SCENES = [
    (0.0, 3.6, render_intro),
    (3.6, 8.8, render_one),
    (8.8, 14.0, render_two),
    (14.0, 19.4, render_three),
    (19.4, 23.6, render_locale),
    (23.6, 28.0, render_outro),
]


def frame_at(t: float) -> Image.Image:
    idx = 0
    for i, (start, end, fn) in enumerate(SCENES):
        if start <= t < end:
            idx = i
            break
    base = SCENES[idx][2](t)
    # 0.45s cross-dissolve around boundaries
    for b in [3.6, 8.8, 14.0, 19.4, 23.6]:
        if abs(t - b) < 0.225:
            prev = idx - 1 if idx > 0 else idx
            nxt = idx + 1 if idx + 1 < len(SCENES) else idx
            if t < b:
                a, bb = prev, idx
            else:
                a, bb = idx, nxt
            if a == bb:
                break
            p = ease_in_out((t - (b - 0.225)) / 0.45)
            ia = SCENES[a][2](t).convert("RGB")
            ib = SCENES[bb][2](t).convert("RGB")
            base = Image.blend(ia, ib, p)
            break
    return base


def render_preview(times):
    for t in times:
        img = frame_at(t)
        path = OUT / f"preview-{t:05.2f}.png"
        img.save(path)
        print(path)


def render_frames():
    for i in range(NFRAMES):
        path = FRAMES / f"{i:05d}.png"
        if path.exists():
            continue
        frame_at(i / FPS).save(path)
        if i % 60 == 0:
            print(f"frame {i}/{NFRAMES}", flush=True)
    print("frames complete")


def synth_audio():
    sr = 48000
    duration = DURATION
    n = int(sr * duration)
    rng = np.random.default_rng(7)
    t = np.arange(n) / sr
    audio = np.zeros((n, 2), dtype=np.float64)

    def add_at(sig, start, pan=0.0):
        s = max(0, int(start * sr))
        e = min(n, s + len(sig))
        if e <= s:
            return
        seg = sig[:e - s]
        audio[s:e, 0] += seg * (1.0 - max(0.0, pan) * 0.25)
        audio[s:e, 1] += seg * (1.0 + min(0.0, pan) * 0.25)

    def fade_env(length, attack=0.02, release=0.12):
        m = max(1, int(length * sr))
        env = np.ones(m)
        a = min(m, int(attack * sr))
        r = min(m, int(release * sr))
        if a > 0:
            env[:a] = np.linspace(0.0, 1.0, a)
        if r > 0:
            env[-r:] *= np.linspace(1.0, 0.0, r)
        return env

    bpm = 112
    beat = 60.0 / bpm
    bar = beat * 4
    chords = [
        (220.00, 261.63, 329.63),  # Am
        (174.61, 220.00, 261.63),  # F
        (261.63, 329.63, 392.00),  # C
        (196.00, 246.94, 293.66),  # G
    ]
    bass_roots = [110.00, 87.31, 130.81, 98.00]

    # Warm detuned pad.
    for bi in range(int(duration / bar) + 1):
        start = bi * bar
        if start >= duration:
            break
        length = min(bar * 1.06, duration - start)
        m = int(length * sr)
        tt = np.arange(m) / sr
        env = fade_env(length, 0.35, 0.55)
        gain = 0.55 if start < 3.6 else (1.0 if start < 23.6 else 0.7)
        chord = chords[bi % 4]
        for freq in chord:
            for detune in (-0.006, 0.0, 0.006):
                f = freq * (1 + detune)
                sig = (0.032 * np.sin(2 * np.pi * f * tt)
                       + 0.015 * np.sin(2 * np.pi * f * 2 * tt))
                add_at(sig * env * gain, start, detune * 40)

    # Kick, hats, clap.
    for bi in range(int(duration / beat) + 1):
        start = bi * beat
        if start < 3.6 or start >= 23.6:
            continue
        kt = np.arange(int(0.18 * sr)) / sr
        freq = 120 * np.exp(-kt * 30) + 42
        phase = 2 * np.pi * np.cumsum(freq) / sr
        kick = np.sin(phase) * np.exp(-kt * 18) * 0.42
        add_at(kick, start)

        for off in (0.0, beat / 2):
            h_len = 0.035 if off == 0 else 0.055
            ht = np.arange(int(h_len * sr)) / sr
            noise = rng.normal(0, 1, len(ht))
            hat = noise * np.exp(-ht * 95) * (0.035 if off == 0 else 0.055)
            add_at(hat, start + off, 0.2)

        if bi % 4 in (1, 3):
            ct = np.arange(int(0.12 * sr)) / sr
            noise = rng.normal(0, 1, len(ct))
            clap = noise * np.exp(-ct * 45) * 0.09
            add_at(clap, start, -0.1)

    # Bass eighths.
    for bi in range(int(duration / (beat / 2)) + 1):
        start = bi * (beat / 2)
        if start < 3.6 or start >= 23.6:
            continue
        chord_idx = int(start / bar) % 4
        freq = bass_roots[chord_idx]
        length = beat / 2 * 0.85
        tt = np.arange(int(length * sr)) / sr
        sig = (0.10 * np.sin(2 * np.pi * freq * tt)
               + 0.045 * np.sin(2 * np.pi * freq * 2 * tt))
        sig *= np.exp(-tt * 7.0)
        add_at(sig, start)

    # Arpeggio.
    arp_notes = [220.00, 261.63, 329.63, 440.00]
    step = beat / 4
    for bi in range(int(duration / step) + 1):
        start = bi * step
        if start < 3.6 or start >= 23.6:
            continue
        chord_idx = int(start / bar) % 4
        cycle = [0, 1, 2, 1, 3, 2, 1, 2]
        note = arp_notes[(cycle[bi % 8] + chord_idx) % 4]
        if chord_idx == 1:
            note *= 0.8
        elif chord_idx == 3:
            note *= 0.9
        length = step * 0.7
        tt = np.arange(int(length * sr)) / sr
        sig = 0.035 * np.sin(2 * np.pi * note * tt) * np.exp(-tt * 18)
        add_at(sig, start, math.sin(bi * 1.7) * 0.4)

    # Transition whooshes.
    for b in (3.6, 8.8, 14.0, 19.4, 23.6):
        length = 0.8
        start = max(0.0, b - 0.35)
        m = int(length * sr)
        st = np.arange(m) / sr
        noise = rng.normal(0, 1, m)
        noise = np.diff(np.concatenate(([0.0], noise)))
        sig = noise * np.exp(-st * 5.0) * 0.055
        add_at(sig, start, 0.0)

    # Master fades and peak normalization.
    fade_in = np.clip(t / 1.2, 0, 1)
    fade_out = np.clip((duration - t) / 1.8, 0, 1)
    audio *= (fade_in * fade_out)[:, None]
    peak = np.max(np.abs(audio))
    if peak > 0:
        audio = audio / peak * 0.72
    pcm = np.clip(audio, -1, 1)
    pcm = (pcm * 32767).astype(np.int16)
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
        render_preview(args.preview)
    if args.frames:
        render_frames()
    if args.audio:
        synth_audio()
