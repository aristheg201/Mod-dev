#!/usr/bin/env python3
"""Generate canonical pixel textures into build output, never repair source assets.

--check is read-only; --jar checks the exact packaged resource bytes.
"""
from __future__ import annotations
import argparse
from collections import Counter
from pathlib import Path
import random
import shutil
import sys
import zipfile
from svhub_png import decode, encode

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "src/main/resources"
PREFIX = "assets/svhub/textures/gui/generated/"
OUTPUT = ROOT / "build/generated/ui-resources"

class Canvas:
    def __init__(self, width, height, color):
        self.width, self.height = width, height
        self.pixels = bytearray(bytes(color) * width * height)

    def rect(self, x, y, width, height, color):
        x0, y0 = max(0, x), max(0, y)
        x1, y1 = min(self.width, x + width), min(self.height, y + height)
        if x1 <= x0 or y1 <= y0:
            return
        line = bytes(color) * (x1 - x0)
        for row in range(y0, y1):
            start = (row * self.width + x0) * 4
            self.pixels[start:start + len(line)] = line

    def png(self):
        return encode(self.width, self.height, bytes(self.pixels))


def background(seed, forest):
    canvas = Canvas(512, 288, (7, 16, 22, 255))
    rng = random.Random(seed)
    for y in range(0, 288, 8):
        band = y // 8
        color = (8 + band, 25 + band, 27 + band, 255) if forest else (7 + band, 16 + band, 26 + band, 255)
        canvas.rect(0, y, 512, 8, color)
    for index in range(90):
        x, y = rng.randrange(512), rng.randrange(176)
        size = 2 if index % 11 == 0 else 1
        canvas.rect(x, y, size, size, (74, 122, 112, 255) if forest else (78, 117, 140, 255))
    for layer, base_y in enumerate((235, 264, 288)):
        for x in range(0, 512, 8):
            height = 16 + rng.randrange(40) + layer * 8
            color = (12, 39 - layer * 6, 35 - layer * 4, 255) if forest else (10, 29 - layer * 4, 40 - layer * 6, 255)
            canvas.rect(x, base_y - height, 8, 288 - base_y + height, color)
    if forest:
        for x in (4, 27, 57, 441, 472, 502):
            height = 58 + rng.randrange(72)
            canvas.rect(x, 288 - height, 4, height, (19, 46, 36, 255))
            for layer in range(6):
                span = 7 + layer * 3
                canvas.rect(x - span, 280 - height + layer * 9, span * 2 + 4, 10, (20 + layer, 53 - layer * 3, 44 - layer * 2, 255))
    else:
        canvas.rect(431, 34, 21, 21, (79, 116, 132, 255))
        canvas.rect(427, 38, 29, 13, (79, 116, 132, 255))
        canvas.rect(423, 30, 22, 20, (10, 19, 29, 255))
    return canvas.png()


def sparkle_strip():
    canvas = Canvas(1024, 64, (0, 0, 0, 0))
    for frame in range(16):
        pulse = frame if frame <= 8 else 16 - frame
        radius, alpha = 2 + pulse, min(255, 32 + pulse * 27)
        center = frame * 64 + 32
        canvas.rect(center - radius, 31, radius * 2 + 1, 3, (110, 217, 202, alpha))
        canvas.rect(center - 1, 32 - radius, 3, radius * 2 + 1, (110, 217, 202, alpha))
        canvas.rect(center - 2, 30, 5, 5, (241, 245, 243, alpha))
        if pulse >= 4:
            for dx, dy in ((-12, -10), (12, 10)):
                canvas.rect(center + dx, 32 + dy, 2, 2, (226, 190, 98, alpha // 2))
    return canvas.png()


def generated():
    return {"home_bg.png": background(20260916, False), "wiki_bg.png": background(20260917, True), "sparkle_strip.png": sparkle_strip()}


def verify(resources, expected):
    errors = []
    if not resources:
        errors.append("no bundled SVHub PNG assets found")
    for name, data in sorted(resources.items()):
        try:
            image = decode(data)
            print(f"PNG OK {name}: {image.width}x{image.height}")
        except ValueError as exc:
            errors.append(f"{name}: {exc}")
    for name, data in expected.items():
        actual = resources.get(PREFIX + name)
        if actual is None:
            errors.append(f"missing generated asset: {name}")
        else:
            try:
                if decode(actual) != decode(data):
                    errors.append(f"generated asset differs from recipe: {name}")
            except ValueError:
                pass  # The same strict decode failure is already recorded above.
    return errors


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--jar", type=Path)
    parser.add_argument("--output", type=Path, default=OUTPUT)
    options = parser.parse_args(argv)
    expected = generated()
    generated_root = options.output / PREFIX
    if not options.check and options.jar is None:
        # Gradle can execute this task incrementally without a clean. Remove the
        # previous recipe output so deleted/renamed generated textures cannot be
        # silently repackaged from a stale build directory.
        if generated_root.exists():
            shutil.rmtree(generated_root)
        for name, data in expected.items():
            path = generated_root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
    source = {p.relative_to(RESOURCES).as_posix(): p.read_bytes() for p in (RESOURCES / "assets/svhub").rglob("*.png")}
    rendered = {p.relative_to(options.output).as_posix(): p.read_bytes()
                for p in (options.output / "assets/svhub").rglob("*.png")}
    errors = verify(source, {}) + verify(rendered, expected)
    errors += [f"generated asset must not be shadowed by source resource: {name}"
               for name in source.keys() & rendered.keys()]
    expected_rendered = {PREFIX + name for name in expected}
    errors += [f"unexpected stale generated asset: {name}" for name in rendered if name not in expected_rendered]
    resources = source | rendered
    if options.jar is not None:
        with zipfile.ZipFile(options.jar) as archive:
            names = archive.namelist()
            errors += [f"duplicate packaged asset: {n}" for n, count in Counter(names).items() if count > 1 and n.startswith("assets/svhub/")]
            packaged = {n: archive.read(n) for n in names if n.startswith("assets/svhub/") and n.endswith(".png")}
        errors += ["JAR: " + error for error in verify(packaged, expected)]
        for name, data in resources.items():
            if packaged.get(name) != data:
                errors.append(f"JAR: resource differs from source: {name}")
    for error in errors:
        print("ERROR: " + error, file=sys.stderr)
    return int(bool(errors))

if __name__ == "__main__":
    raise SystemExit(main())
