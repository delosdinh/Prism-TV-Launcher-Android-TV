"""Prism TV wallpapers: soft light fields with a prism's spectral beam (Python 3, NumPy, Pillow).

Usage: python generate_wallpapers.py OUTPUT_DIR  -> wall_prism_blue.jpg

Prism Light, Prism Dusk and Prism Night come from the brand's boot artwork instead:
tools/branding/render_prism_waves.js.
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

W, H = 1920, 1080


def field(blobs, base):
    """Large soft colour blobs mixed over a base colour (all colours 0..1 RGB)."""
    y, x = np.mgrid[0:H, 0:W].astype(np.float64)
    x /= W
    y /= H
    img = np.ones((H, W, 3)) * np.array(base)
    for cx, cy, r, colour, strength in blobs:
        d2 = ((x - cx) * 1.6) ** 2 + (y - cy) ** 2
        w = strength * np.exp(-d2 / (2 * r * r))
        img = img * (1 - w[..., None]) + np.array(colour) * w[..., None]
    return img


def beam(img, angle, offset, width, strength, mix=False):
    """A soft spectral band (the light through the prism) crossing the image."""
    y, x = np.mgrid[0:H, 0:W].astype(np.float64)
    x /= W
    y /= H
    t = np.cos(angle) * (y - offset) - np.sin(angle) * (x - 0.5)
    hues = [(1.0, .36, .42), (1.0, .62, .25), (1.0, .88, .35), (.45, .9, .55), (.35, .72, 1.0), (.55, .45, 1.0)]
    for i, colour in enumerate(hues):
        centre = (i - 2.5) * width / 5
        w = strength * np.exp(-((t - centre) ** 2) / (2 * (width / 7) ** 2))
        if mix:  # light backgrounds: tint towards the colour instead of adding light
            img = img * (1 - w[..., None]) + np.array(colour) * 0.92 * w[..., None]
        else:
            img = img + np.array(colour) * w[..., None] * 0.55
    return img


def grain(img, seed, amount=0.012):
    rng = np.random.default_rng(seed)
    return img + rng.normal(0, amount, img.shape)


def save(img, path):
    img = np.clip(img, 0, 1)
    pil = Image.fromarray((img * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.2))
    pil.save(path, quality=86, optimize=True, progressive=True)


def main(out):
    out.mkdir(parents=True, exist_ok=True)
    blue = field([(.15, .2, .4, (.16, .42, .86), .95), (.85, .2, .35, (.32, .72, .98), .85),
                  (.55, .95, .45, (.04, .16, .42), .9), (.95, .85, .3, (.18, .6, .78), .6)], (.07, .25, .58))
    save(grain(beam(blue, .52, .53, .18, .34), 4), out / "wall_prism_blue.jpg")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: python generate_wallpapers.py OUTPUT_DIR")
    main(Path(sys.argv[1]))
