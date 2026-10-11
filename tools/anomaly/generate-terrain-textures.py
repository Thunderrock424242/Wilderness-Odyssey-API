#!/usr/bin/env python3
"""Author the anomaly terrain's deterministic, opaque 16px initial pixel art.

The source palette, strata and fracture paths are the editable art source.
Uses only Python's standard library; no downloaded textures or dependencies.
Run with --check to verify the checked-in PNGs without changing them.
"""

import argparse
from pathlib import Path
import struct
import zlib

SIZE = 16
TEXTURE_ROOT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/wildernessodysseyapi/textures/block"

STABLE = (89, 142, 164)
SPATIAL = (129, 105, 168)
CRITICAL = (151, 72, 73)
LONG_SEAM = [(3, 1), (4, 2), (4, 3), (5, 4), (5, 5), (6, 6), (7, 6), (7, 7),
             (8, 8), (8, 9), (9, 10), (10, 10), (10, 11), (11, 12), (11, 13), (12, 14)]
SHORT_SEAM = [(2, 12), (3, 11), (4, 11), (5, 10), (6, 10), (7, 9), (7, 8)]
BRANCH = [(5, 5), (4, 6), (3, 7), (3, 8)]


def noise(x, y, seed):
    value = (x * 374761393 + y * 668265263 + seed * 2246822519) & 0xffffffff
    value = ((value ^ (value >> 13)) * 1274126177) & 0xffffffff
    return value ^ (value >> 16)


def tint(color, amount):
    return tuple(max(0, min(255, channel + amount)) for channel in color)


def surface(kind, seed):
    pixels = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            grain = noise(x, y, seed) % 19 - 9
            clump = noise(x // 3, y // 3, seed + 17) % 17 - 8
            if kind == "stone":
                color = tint((67, 72, 74), grain + clump)
                if noise(x, y, seed + 4) % 23 == 0:
                    color = tint(color, 14)
            elif kind == "cliff":
                strata = ((y + (noise(x // 5, 0, seed) % 3)) % 5)
                color = tint((67, 70, 72), grain // 2 + clump + (-14 if strata == 0 else 5))
            elif kind == "dirt":
                color = tint((65, 48, 34), grain + clump)
                if noise(x, y, seed + 4) % 17 == 0:
                    color = tint((80, 73, 58), grain)
            elif kind == "grass_top":
                color = tint((60, 77, 43), grain + clump)
                if noise(x, y, seed + 4) % 11 == 0:
                    color = tint((85, 87, 57), grain)
            elif kind == "grass_side":
                grass_depth = 3 + noise(x // 2, 0, seed + 2) % 3
                color = tint((57, 72, 41) if y < grass_depth else (65, 48, 34), grain + clump)
            elif kind == "moss":
                color = tint((48, 66, 39), grain // 2 + clump)
                if noise(x // 2, y // 2, seed + 4) % 5 == 0:
                    color = tint((67, 80, 47), grain // 2)
            elif kind == "unstable":
                color = tint((45, 45, 48), grain + clump)
                if (x + y * 2) % 7 == 0:
                    color = tint(color, -7)
            elif kind == "crystal":
                facet = (x - y) % 9
                color = tint((36, 51, 63), grain // 2 + clump // 2 + (16 if facet < 2 else 0))
            elif kind == "energy":
                color = tint((33, 37, 48), grain // 2 + clump)
                if (x + y) % 8 == 0:
                    color = tint(color, 7)
            else:
                raise ValueError(kind)
            row.append(color)
        pixels.append(row)
    return pixels


def fracture(pixels, path, color, seed):
    # A dark, one-pixel shadow separates the hairline seam from natural material.
    for x, y in path:
        if x + 1 < SIZE:
            pixels[y][x + 1] = tint(pixels[y][x + 1], -16)
    for index, (x, y) in enumerate(path):
        pixels[y][x] = tint(color, -18 if (index + seed) % 4 == 0 else 0)


def png(pixels):
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xffffffff)

    raw = b"".join(b"\0" + bytes(channel for color in row for channel in (*color, 255)) for row in pixels)
    header = struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")


def textures():
    materials = [
        ("distorted_stone", "stone", 11, LONG_SEAM + BRANCH, SPATIAL),
        ("distorted_grass_top", "grass_top", 23, SHORT_SEAM, STABLE),
        ("distorted_grass_side", "grass_side", 31, SHORT_SEAM, STABLE),
        ("distorted_dirt", "dirt", 47, SHORT_SEAM, STABLE),
        ("distorted_moss", "moss", 59, SHORT_SEAM, STABLE),
        ("distorted_cliff_stone", "cliff", 71, LONG_SEAM, SPATIAL),
        ("unstable_rift_stone", "unstable", 89, LONG_SEAM + BRANCH, CRITICAL),
        ("anomaly_crystal_block", "crystal", 101, LONG_SEAM + BRANCH, STABLE),
        ("fractured_energy_block", "energy", 113, LONG_SEAM + BRANCH, SPATIAL),
    ]
    for name, kind, seed, path, color in materials:
        pixels = surface(kind, seed)
        fracture(pixels, path, color, seed)
        if kind == "energy":
            # Two isolated red junctions communicate a critical fault, not a red wash.
            pixels[7][7] = CRITICAL
            pixels[12][11] = CRITICAL
        yield name, png(pixels)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="verify existing PNGs without writing")
    args = parser.parse_args()
    if not args.check:
        TEXTURE_ROOT.mkdir(parents=True, exist_ok=True)
    failures = []
    count = 0
    for name, data in textures():
        path = TEXTURE_ROOT / (name + ".png")
        count += 1
        if args.check:
            if not path.is_file() or path.read_bytes() != data:
                failures.append(name)
        else:
            path.write_bytes(data)
    if failures:
        parser.exit(1, "Textures differ from their source palette: " + ", ".join(failures) + "\n")
    print(("Verified" if args.check else "Authored") + f" {count} anomaly terrain textures.")


if __name__ == "__main__":
    main()
