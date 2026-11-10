#!/usr/bin/env python3
"""Draw the Cal Budget icon: two bars on a calm background. No third-party libraries."""

import struct
import zlib
from pathlib import Path

OUT = Path(__file__).resolve().parents[1] / "src" / "icons"
PAPER = (0xE6, 0xF3, 0xF0, 255)
TEAL = (0x1F, 0x6F, 0x66, 255)


def chunk(tag, data):
    return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)


def write_png(path, width, height, pixels):
    raw = bytearray()
    stride = width * 4
    for y in range(height):
        raw.append(0)
        raw.extend(pixels[y * stride : (y + 1) * stride])
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b"")
    path.write_bytes(png)


def inside_round_rect(x, y, left, top, width, height, radius):
    right = left + width
    bottom = top + height
    if x < left or x >= right or y < top or y >= bottom:
        return 0
    cx = min(max(x, left + radius), right - radius)
    cy = min(max(y, top + radius), bottom - radius)
    dx = x - cx
    dy = y - cy
    distance = (dx * dx + dy * dy) ** 0.5
    if distance <= radius - 0.6:
        return 1
    if distance >= radius + 0.6:
        return 0
    return max(0, min(1, radius + 0.5 - distance))


def sample(nx, ny, inset):
    # nx, ny are 0..1 across the icon. Bars stay inside the safe area.
    color = PAPER
    bars = (
        (0.22 + inset, 0.40, 0.56 - inset * 2, 0.09),
        (0.28 + inset, 0.56, 0.44 - inset * 2, 0.09),
    )
    coverage = 0
    for left, top, width, height in bars:
        coverage = max(coverage, inside_round_rect(nx, ny, left, top, width, height, height / 2))
    if coverage <= 0:
        return color
    return tuple(round(PAPER[i] * (1 - coverage) + TEAL[i] * coverage) for i in range(4))


def render(size, inset):
    scale = 4
    big = size * scale
    acc = [[0, 0, 0, 0] for _ in range(size * size)]
    for y in range(big):
        for x in range(big):
            pixel = sample((x + 0.5) / big, (y + 0.5) / big, inset)
            cell = (y // scale) * size + (x // scale)
            for channel in range(4):
                acc[cell][channel] += pixel[channel]
    pixels = bytearray()
    samples = scale * scale
    for cell in acc:
        pixels.extend(round(channel / samples) for channel in cell)
    return pixels


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    write_png(OUT / "icon-192.png", 192, 192, render(192, 0))
    write_png(OUT / "icon-512.png", 512, 512, render(512, 0))
    write_png(OUT / "icon-maskable-512.png", 512, 512, render(512, 0.08))
    write_png(OUT / "apple-touch-icon.png", 180, 180, render(180, 0))
    write_png(OUT / "favicon-32.png", 32, 32, render(32, 0))


if __name__ == "__main__":
    main()
