#!/usr/bin/env python3
"""Reject corrupt/blank/missing-texture captures and render/resource log failures.

Negative acceptance checks do not prove composition quality. Inspect captures and
run independent layout/ownership assertions as well.
"""
from __future__ import annotations
import argparse
from collections import Counter
import math
from pathlib import Path
import re
import sys
from svhub_png import decode

LOG_ERRORS = re.compile(
    r"Failed to load texture:\s*svhub:|Could not load image|PNG not supported|"
    r"unknown PNG chunk type|bad PNG CRC|invalid PNG|corrupt PNG|PNG decode failure|"
    r"(?:failed|unable) to (?:decode|read|load)[^\n]*svhub:[^\n]*(?:texture|\.png)|"
    r"Minecraft has crashed!|ReportedException|Rendering screen|OpenGL error|Visual Smoke[^\n]*timed out",
    re.IGNORECASE,
)


def check_log(path):
    try:
        lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    except OSError as exc:
        return [f"{path}: cannot read required runtime log: {exc}"]
    if not any(line.strip() for line in lines):
        return [f"{path}: runtime log is empty"]
    return [f"{path.name}:{i}: {line}" for i, line in enumerate(lines, 1) if LOG_ERRORS.search(line)]


def pixels(path):
    image = decode(path.read_bytes())
    w, h, bpp = image.width, image.height, image.channels
    colors, luminance = Counter(), []
    magenta = dark = 0
    for y in range(0, h, max(1, h // 180)):
        row = image.rows[y]
        for x in range(0, w, max(1, w // 320)):
            r, g, b = row[x * bpp:x * bpp + 3]
            colors[(r, g, b)] += 1
            luminance.append((.2126 * r + .7152 * g + .0722 * b) / 255.0)
            magenta += int(r >= 220 and g <= 45 and b >= 180)
            dark += int(r <= 28 and g <= 28 and b <= 28)
    count = len(luminance)
    mean = sum(luminance) / count
    stdev = math.sqrt(sum((v - mean) ** 2 for v in luminance) / count)
    return w, h, mean, stdev, colors.most_common(1)[0][1] / count, len(colors), magenta / count, dark / count


def check_image(path):
    try:
        w, h, mean, stdev, dominant, unique, magenta, dark = pixels(path)
    except (OSError, ValueError) as exc:
        return [f"{path.name}: {exc}"]
    print(f"{path.name}: {w}x{h} mean={mean:.4f} stdev={stdev:.4f} dominant={dominant:.4f} unique_sampled={unique} missing_magenta={magenta:.4f}")
    failures = []
    if w < 640 or h < 360:
        failures.append(f"{path.name}: unexpectedly small framebuffer")
    if mean <= .04:
        failures.append(f"{path.name}: framebuffer is effectively black")
    if stdev <= .035:
        failures.append(f"{path.name}: framebuffer lacks visible scene contrast")
    if dominant >= .85:
        failures.append(f"{path.name}: one flat color dominates the framebuffer")
    if unique <= 64:
        failures.append(f"{path.name}: too few sampled colors for a rendered scene")
    if magenta >= .012 and dark >= .04:
        failures.append(f"{path.name}: missing-texture magenta/black checker signature detected")
    return failures


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--log", type=Path, default=Path("run/logs/latest.log"))
    parser.add_argument("images", nargs="+", type=Path)
    options = parser.parse_args(argv)
    failures = check_log(options.log)
    for path in options.images:
        failures += check_image(path)
    for failure in failures:
        print("ERROR: " + failure, file=sys.stderr)
    return int(bool(failures))

if __name__ == "__main__":
    raise SystemExit(main())
