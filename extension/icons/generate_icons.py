#!/usr/bin/env python3
"""Generate the Wasted extension PNG icons (ink background + white clock + $).

Pure standard library (zlib/struct) — no external deps. Re-run after tweaks:
    python3 extension/icons/generate_icons.py
"""
import math
import struct
import zlib
import os

INK = (0x1A, 0x1A, 0x1A, 255)
WHITE = (255, 255, 255, 255)

# Geometry in the same 108-unit space as the Android adaptive icon, with the
# same 0.86 group scale about (54, 54). Loop arrow omitted for legibility at
# small sizes; clock + dollar carry the "time + money" idea.
def transform(x, y, size):
    gx = (x - 54) * 0.86 + 54
    gy = (y - 54) * 0.86 + 54
    s = size / 108.0
    return gx * s, gy * s


def make_buffer(size):
    return [[INK for _ in range(size)] for _ in range(size)]


def stamp(buf, size, px, py, rad):
    x0, x1 = max(0, int(px - rad - 1)), min(size - 1, int(px + rad + 1))
    y0, y1 = max(0, int(py - rad - 1)), min(size - 1, int(py + rad + 1))
    r2 = rad * rad
    for yy in range(y0, y1 + 1):
        for xx in range(x0, x1 + 1):
            dx, dy = xx + 0.5 - px, yy + 0.5 - py
            if dx * dx + dy * dy <= r2:
                buf[yy][xx] = WHITE


def stroke(buf, size, pts, width):
    rad = max(0.9, width / 2.0 * size / 108.0)
    prev = None
    for (x, y) in pts:
        px, py = transform(x, y, size)
        if prev is not None:
            steps = int(max(1, math.hypot(px - prev[0], py - prev[1]) / 0.6))
            for i in range(steps + 1):
                t = i / steps
                stamp(buf, size, prev[0] + (px - prev[0]) * t, prev[1] + (py - prev[1]) * t, rad)
        else:
            stamp(buf, size, px, py, rad)
        prev = (px, py)


def circle_pts(cx, cy, r, n=160):
    return [(cx + r * math.cos(2 * math.pi * i / n), cy + r * math.sin(2 * math.pi * i / n)) for i in range(n + 1)]


def cubic_pts(p0, p1, p2, p3, n=40):
    out = []
    for i in range(n + 1):
        t = i / n
        mt = 1 - t
        x = mt**3 * p0[0] + 3 * mt**2 * t * p1[0] + 3 * mt * t**2 * p2[0] + t**3 * p3[0]
        y = mt**3 * p0[1] + 3 * mt**2 * t * p1[1] + 3 * mt * t**2 * p2[1] + t**3 * p3[1]
        out.append((x, y))
    return out


def draw(size):
    buf = make_buffer(size)
    # Clock ring
    stroke(buf, size, circle_pts(60, 58, 22), 3.2)
    # Hands
    stroke(buf, size, [(60, 58), (60, 42)], 3.2)
    stroke(buf, size, [(60, 58), (73, 65)], 3.2)
    # Dollar bar
    stroke(buf, size, [(32, 18), (32, 52)], 3.6)
    # Dollar S
    s = cubic_pts((41, 25), (41, 19), (25, 19), (25, 27))
    s += cubic_pts((25, 27), (25, 34), (41, 34), (41, 42))
    s += cubic_pts((41, 42), (41, 50), (25, 50), (25, 43))
    stroke(buf, size, s, 3.6)
    return buf


def write_png(path, buf, size):
    raw = bytearray()
    for row in buf:
        raw.append(0)  # filter type 0
        for (r, g, b, a) in row:
            raw += bytes((r, g, b, a))
    comp = zlib.compress(bytes(raw), 9)

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    sig = b"\x89PNG\r\n\x1a\n"
    ihdr = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)
    with open(path, "wb") as f:
        f.write(sig + chunk(b"IHDR", ihdr) + chunk(b"IDAT", comp) + chunk(b"IEND", b""))


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    for size in (16, 48, 128):
        write_png(os.path.join(here, f"icon{size}.png"), draw(size), size)
        print(f"wrote icon{size}.png")


if __name__ == "__main__":
    main()
