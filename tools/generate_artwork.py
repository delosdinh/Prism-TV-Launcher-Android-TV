#!/usr/bin/env python3
"""
Procedurally generates the bundled hero / backdrop artwork used by Prism TV
Launcher's demo content and as fallback backdrops.

Everything here is original, generated art (no photos, no third-party IP),
so it is safe to ship in the APK.

    python3 tools/generate_artwork.py            # writes app/src/main/res/drawable-nodpi/hero_*.jpg

Requires: numpy, pillow
"""
import os
import sys

import numpy as np
from PIL import Image, ImageFilter

W, H = 1920, 1080
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "drawable-nodpi")


# --------------------------------------------------------------------------- noise helpers

def fbm2(h, w, octaves=6, base=3, persistence=0.5, seed=0, stretch_x=1.0, stretch_y=1.0):
    rng = np.random.default_rng(seed)
    out = np.zeros((h, w), np.float32)
    amp, total = 1.0, 0.0
    for o in range(octaves):
        gx = max(2, int(base * 2 ** o / stretch_x) + 2)
        gy = max(2, int(base * 2 ** o * h / w / stretch_y) + 2)
        grid = rng.random((gy, gx)).astype(np.float32)
        img = Image.fromarray(grid, mode="F").resize((w, h), Image.BICUBIC)
        out += amp * np.asarray(img, dtype=np.float32)
        total += amp
        amp *= persistence
    return out / total


def fbm1(w, octaves=7, base=3, persistence=0.55, seed=0, smooth=False):
    rng = np.random.default_rng(seed)
    x = np.linspace(0.0, 1.0, w)
    out = np.zeros(w, np.float64)
    amp, total = 1.0, 0.0
    for o in range(octaves):
        n = base * 2 ** o + 1
        pts = rng.random(n)
        t = x * (n - 1)
        i = np.clip(t.astype(int), 0, n - 2)
        f = t - i
        if smooth:
            f = f * f * (3 - 2 * f)
        out += amp * (pts[i] * (1 - f) + pts[i + 1] * f)
        total += amp
        amp *= persistence
    return out / total


def lerp(a, b, t):
    return a + (b - a) * t


def vgrad(stops):
    """Vertical gradient. stops: list of (pos 0..1, (r,g,b))."""
    y = np.linspace(0, 1, H)[:, None]
    img = np.zeros((H, W, 3), np.float32)
    for c in range(3):
        img[..., c] = np.interp(y, [s[0] for s in stops], [s[1][c] for s in stops])
    return img


def col(hexstr):
    hexstr = hexstr.lstrip("#")
    return np.array([int(hexstr[i:i + 2], 16) for i in (0, 2, 4)], np.float32)


def blend(base, color, alpha):
    a = alpha[..., None] if alpha.ndim == 2 else alpha
    return base * (1 - a) + color * a


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def mountain_layer(img, seed, y_base, amp, color_lit, color_shadow, haze, haze_amt,
                   snow=0.0, snow_color=None, sharp=1.0, octaves=8, base=3, light_dir=1.0,
                   texture=1.0, smooth=False):
    prof = fbm1(W, octaves=octaves, base=base, seed=seed, smooth=smooth)
    prof = (prof - prof.min()) / (prof.max() - prof.min() + 1e-6)
    prof = prof ** sharp
    ridge = y_base - amp * prof  # y of ridge for each x
    yy = np.arange(H)[:, None].astype(np.float32)
    # soft anti-aliased edge
    mask = np.clip((yy - ridge[None, :]) * 0.9, 0, 1)

    depth = np.clip((yy - ridge[None, :]) / (amp * 1.2 + 1), 0, 1)
    # ridge-facing light, fading out away from the ridge line
    slope = np.convolve(np.gradient(ridge), np.ones(15) / 15.0, mode='same') * light_dir
    slope2d = slope[None, :] * np.exp(-depth * 5.0)
    # 2D terrain for faces / gullies, lit from the side via its x-gradient
    terrain = fbm2(H, W, octaves=7, base=7, seed=seed + 7, stretch_y=1.6)
    tdx = np.gradient(terrain, axis=1) * W / 18.0 * light_dir
    light = np.clip(0.55 + np.clip(slope2d, -1.2, 1.2) * 0.45 + tdx * 0.9 * texture
                    + (terrain - 0.5) * 0.5 * texture - depth * 0.35, 0, 1)
    layer = color_shadow[None, None, :] * (1 - light[..., None]) + color_lit[None, None, :] * light[..., None]
    if snow > 0 and snow_color is not None:
        snow_amt = np.clip(1.15 - depth * (2.4 - snow) + (terrain - 0.5) * 1.6, 0, 1) * snow
        snow_amt *= np.clip(0.25 + light * 1.1, 0, 1)
        layer = blend(layer, snow_color, np.clip(snow_amt, 0, 1))
    # atmospheric haze, stronger near ridge base (fog pooling)
    fog = np.clip(haze_amt + depth * 0.0, 0, 1)
    layer = blend(layer, haze, np.full((H, W), fog, np.float32))
    return blend(img, layer, mask)


def stars(img, seed, density=0.0009, max_y=1.0, brightness=1.0):
    rng = np.random.default_rng(seed)
    n = int(W * H * density)
    xs = rng.integers(0, W, n)
    ys = rng.integers(0, int(H * max_y), n)
    b = rng.random(n) ** 3 * 255 * brightness
    s = np.zeros((H, W), np.float32)
    s[ys, xs] = b
    glow = np.asarray(Image.fromarray(np.clip(s, 0, 255).astype(np.uint8), "L")
                      .filter(ImageFilter.GaussianBlur(1.2)), np.float32) * 2.5
    s = np.maximum(s, glow)
    return np.clip(img + s[..., None], 0, 255)


def save(img, name, blur=0.0):
    im = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGB")
    if blur:
        im = im.filter(ImageFilter.GaussianBlur(blur))
    # subtle film grain
    grain = (np.random.default_rng(1).normal(0, 2.2, (H, W, 1))).astype(np.float32)
    arr = np.clip(np.asarray(im, np.float32) + grain, 0, 255).astype(np.uint8)
    path = os.path.join(OUT, name)
    Image.fromarray(arr).save(path, "JPEG", quality=86, optimize=True, progressive=True)
    print("wrote", path, os.path.getsize(path) // 1024, "KB")


# --------------------------------------------------------------------------- scenes

def glacier():
    sky = vgrad([(0.0, col("#3d5f8f")), (0.18, col("#6f86ad")), (0.30, col("#c9a3b4")),
                 (0.38, col("#f0b9a2")), (0.44, col("#e9d5cf")), (1.0, col("#9fb3cc"))])
    clouds = fbm2(H, W, octaves=6, base=3, seed=11, stretch_x=5.0)
    cl = smoothstep(0.52, 0.75, clouds) * (np.linspace(1, 0, H)[:, None] ** 1.5)
    sky = blend(sky, col("#f6c9b8"), cl * 0.75)
    sky = blend(sky, col("#ffe9dc"), smoothstep(0.66, 0.8, clouds) * (np.linspace(1, 0, H)[:, None] ** 2) * 0.6)
    img = sky
    haze = col("#b9c9dd")
    img = mountain_layer(img, 3, 470, 150, col("#d9e3f0"), col("#7d93b4"), haze, 0.55, snow=0.8,
                         snow_color=col("#eef3fa"), sharp=1.3, base=4)
    img = mountain_layer(img, 5, 600, 260, col("#e4ecf6"), col("#4f6a92"), col("#8fa6c4"), 0.25, snow=1.0,
                         snow_color=col("#f2f6fb"), sharp=1.6, base=3)
    img = mountain_layer(img, 9, 860, 300, col("#dfe8f3"), col("#38557f"), col("#6c87ad"), 0.12, snow=1.2,
                         snow_color=col("#f4f7fb"), sharp=1.2, base=2)
    img = mountain_layer(img, 17, 1120, 260, col("#d3dfee"), col("#2e4a72"), col("#5d7aa3"), 0.05, snow=1.4,
                         snow_color=col("#eef3f9"), sharp=1.0, base=2, light_dir=-1.0)
    # cool cinematic grade
    img = img * np.array([0.93, 0.98, 1.04], np.float32)
    save(img, "hero_glacier.jpg")


def aurora():
    sky = vgrad([(0.0, col("#030712")), (0.45, col("#07142a")), (0.62, col("#0b2a3a")), (1.0, col("#04080f"))])
    img = stars(sky, 21, density=0.0012, max_y=0.65)
    yy = np.linspace(0, 1, H)[:, None]
    xx = np.linspace(0, 1, W)[None, :]
    curtain_noise = fbm1(W, octaves=6, base=2, seed=31, smooth=True)
    center = 0.30 + 0.12 * np.sin(xx * 5.5 + 1.0) + (curtain_noise[None, :] - 0.5) * 0.25
    band = np.exp(-((yy - center) ** 2) / 0.006)
    rays = fbm2(H, W, octaves=5, base=60, seed=41, stretch_y=12.0)
    tall = np.exp(-np.clip(center - yy, 0, None) / 0.10) * (yy < center + 0.02)
    aur = band * 0.9 + tall * (rays ** 2) * 1.4
    aur *= smoothstep(0.0, 0.25, xx) * smoothstep(1.0, 0.75, xx) * 0.9 + 0.1
    img = img + aur[..., None] * col("#38f5a8")[None, None, :] * 0.85
    img = img + (tall * rays ** 3)[..., None] * col("#8f5cff")[None, None, :] * 0.55
    img = mountain_layer(img, 51, 700, 220, col("#16263a"), col("#070d17"), col("#0d1f2c"), 0.25, sharp=1.4, base=3)
    img = mountain_layer(img, 57, 800, 160, col("#0c1522"), col("#03060b"), col("#08121b"), 0.1, sharp=1.1, base=4)
    # lake reflection below y=800
    lake_top = 800
    refl = img[lake_top - (H - lake_top):lake_top][::-1].copy()
    ripple = fbm2(H - lake_top, W, octaves=4, base=6, seed=61, stretch_x=8.0)
    shift = ((ripple - 0.5) * 18).astype(int)
    rows = np.arange(H - lake_top)[:, None]
    cols = np.clip(np.arange(W)[None, :] + shift, 0, W - 1)
    refl = refl[rows, cols] * 0.55
    img[lake_top:] = refl + col("#02060b") * 0.3
    save(img, "hero_aurora.jpg")


def dunes():
    sky = vgrad([(0.0, col("#f2a65a")), (0.35, col("#f7c98b")), (0.5, col("#fbe3b8")), (1.0, col("#c9733a"))])
    sun_y, sun_x = 0.42 * H, 0.70 * W
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    d = np.sqrt((xx - sun_x) ** 2 + (yy - sun_y) ** 2)
    sky = sky + (np.exp(-d / 220.0) * 120)[..., None] * np.array([1.0, 0.85, 0.6], np.float32)
    sky = blend(sky, col("#fff6e6"), (d < 60).astype(np.float32) * 0.9)
    img = sky
    img = mountain_layer(img, 71, 560, 70, col("#e9a76a"), col("#b4683a"), col("#f2c08a"), 0.55, sharp=0.7, base=2, octaves=4, texture=0.3, smooth=True)
    for i, (yb, amp, lit, sh, hz, ha) in enumerate([
        (680, 160, "#f0a65e", "#8c4523", "#f3b47a", 0.35),
        (820, 200, "#eb9a52", "#6e3418", "#e9a26a", 0.18),
        (980, 230, "#e48f47", "#552611", "#d98d55", 0.06),
        (1130, 260, "#dc8640", "#43200f", "#c87d48", 0.0),
    ]):
        img = mountain_layer(img, 81 + i * 3, yb, amp, col(lit), col(sh), col(hz), ha, sharp=0.8, base=1, octaves=3, texture=0.25, smooth=True,
                             light_dir=-1.0)
    save(img, "hero_dunes.jpg")


def ocean():
    sky = vgrad([(0.0, col("#1d2b53")), (0.25, col("#5a3f7a")), (0.42, col("#e0679a")), (0.52, col("#ffb36b")),
                 (0.56, col("#ffd9a0")), (1.0, col("#1a1f3a"))])
    clouds = fbm2(H, W, octaves=6, base=3, seed=91, stretch_x=6.0)
    cl = smoothstep(0.55, 0.78, clouds) * (np.linspace(1, 0, H)[:, None] < 0.55)
    sky = blend(sky, col("#ff8f80"), cl * 0.6)
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    horizon = int(0.57 * H)
    sun_x, sun_y = 0.5 * W, horizon - 30
    d = np.sqrt((xx - sun_x) ** 2 + (yy - sun_y) ** 2)
    sky = sky + (np.exp(-d / 180.0) * 140)[..., None] * np.array([1.0, 0.75, 0.45], np.float32)
    sky = blend(sky, col("#fff3d6"), ((d < 70) & (yy < horizon)).astype(np.float32))
    img = sky
    sea = vgrad([(0.0, col("#000000")), (0.57, col("#ff9a63")), (0.6, col("#7a3d6a")), (1.0, col("#0b1230"))])
    waves = fbm2(H, W, octaves=5, base=10, seed=101, stretch_x=10.0)
    glint = np.exp(-((xx - sun_x) ** 2) / (2 * (60 + (yy - horizon) * 0.9) ** 2)) * smoothstep(0.55, 0.7, waves)
    sea = sea * (0.75 + waves[..., None] * 0.5) + glint[..., None] * np.array([255, 210, 150], np.float32) * 0.9
    m = (yy >= horizon).astype(np.float32)
    img = blend(img, sea, m)
    save(img, "hero_ocean.jpg")


def nebula():
    base = vgrad([(0.0, col("#05030d")), (1.0, col("#0a0618"))])
    n1 = fbm2(H, W, octaves=7, base=3, seed=111)
    n2 = fbm2(H, W, octaves=7, base=4, seed=121)
    n3 = fbm2(H, W, octaves=5, base=2, seed=131)
    img = base
    img = img + (smoothstep(0.45, 0.85, n1) ** 1.5)[..., None] * col("#7b2ff7")[None, None, :] * 0.9
    img = img + (smoothstep(0.5, 0.9, n2) ** 2)[..., None] * col("#ff4f9a")[None, None, :] * 0.75
    img = img + (smoothstep(0.55, 0.9, n3 * n1 * 1.6) ** 2)[..., None] * col("#3ad8ff")[None, None, :] * 0.8
    img = stars(img, 141, density=0.0016)
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    # planet limb lower-right
    cx, cy, r = W * 0.88, H * 1.25, H * 0.62
    d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2)
    planet = (d < r).astype(np.float32)
    shade = np.clip((cx - xx + (cy - yy) * 0.6) / r, 0, 1)
    pcol = col("#151a3a")[None, None, :] * (1 - shade[..., None]) + col("#4a5bd0")[None, None, :] * shade[..., None]
    bands = fbm2(H, W, octaves=4, base=4, seed=151, stretch_x=8.0)
    pcol = pcol * (0.8 + bands[..., None] * 0.4)
    img = blend(img, pcol, planet)
    rim = np.exp(-np.abs(d - r) / 6.0) * np.clip(shade * 1.4, 0, 1)
    img = img + rim[..., None] * col("#9fd0ff")[None, None, :]
    save(img, "hero_nebula.jpg")


def main():
    os.makedirs(OUT, exist_ok=True)
    which = sys.argv[1:] or ["glacier", "aurora", "dunes", "ocean", "nebula"]
    for name in which:
        globals()[name]()


if __name__ == "__main__":
    main()
