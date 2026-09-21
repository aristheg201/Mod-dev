import contextlib
import importlib.util
import io
from pathlib import Path
import struct
import sys
import tempfile
import unittest
import zlib

TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))
from svhub_png import SIGNATURE, chunk, decode, encode, paeth


def module(name):
    spec = importlib.util.spec_from_file_location(name, TOOLS / (name + ".py"))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result

GEN = module("generate-ui-textures")
SMOKE = module("verify-visual-smoke")


def png(raw=b"\0\1\2\3\4", width=1, height=1, color=6, depth=8, extra=b"", payload=None):
    header = struct.pack(">IIBBBBB", width, height, depth, color, 0, 0, 0)
    return SIGNATURE + chunk(b"IHDR", header) + extra + chunk(b"IDAT", zlib.compress(raw) if payload is None else payload) + chunk(b"IEND", b"")


class PngContractTests(unittest.TestCase):
    def test_rgba_roundtrip(self):
        pixels = bytes((i * 13) % 256 for i in range(17 * 9 * 4))
        image = decode(encode(17, 9, pixels))
        self.assertEqual((17, 9, 4), (image.width, image.height, image.channels))
        self.assertEqual(pixels, b"".join(image.rows))

    def test_rgb(self):
        self.assertEqual((b"\1\2\3",), decode(png(b"\0\1\2\3", color=2)).rows)

    def test_all_five_filters(self):
        rows = (bytes(range(12)), bytes(range(23, 35)), bytes(range(123, 135)))
        for mode in range(5):
            with self.subTest(mode=mode):
                raw, previous = bytearray(), bytes(12)
                for row in rows:
                    raw.append(mode)
                    for x, value in enumerate(row):
                        left, up = row[x - 4] if x >= 4 else 0, previous[x]
                        corner = previous[x - 4] if x >= 4 else 0
                        raw.append((value - (0, left, up, (left + up) // 2, paeth(left, up, corner))[mode]) & 255)
                    previous = row
                self.assertEqual(rows, decode(png(bytes(raw), width=3, height=3)).rows)

    def test_corrupt_crc(self):
        data = bytearray(png()); data[-1] ^= 1
        with self.assertRaisesRegex(ValueError, "CRC"):
            decode(bytes(data))

    def test_corrupt_adler_with_valid_chunk_crc(self):
        payload = bytearray(zlib.compress(b"\0\1\2\3\4")); payload[-1] ^= 1
        with self.assertRaisesRegex(ValueError, "zlib"):
            decode(png(payload=bytes(payload)))

    def test_truncated_chunks(self):
        for length in (1, 7, 11, 15, 20):
            with self.subTest(length=length), self.assertRaises(ValueError):
                decode(png()[:-length])

    def test_unknown_critical_rejected(self):
        with self.assertRaisesRegex(ValueError, "critical"):
            decode(png(extra=chunk(b"ABCD", b"")))

    def test_unknown_ancillary_allowed(self):
        self.assertEqual(1, decode(png(extra=chunk(b"abCd", b"metadata"))).width)

    def test_invalid_chunk_type(self):
        with self.assertRaisesRegex(ValueError, "chunk type"):
            decode(png(extra=chunk(b"ab1d", b"")))

    def test_duplicate_ihdr(self):
        with self.assertRaisesRegex(ValueError, "duplicate"):
            decode(png(extra=chunk(b"IHDR", struct.pack(">IIBBBBB", 1, 1, 8, 6, 0, 0, 0))))

    def test_missing_iend(self):
        with self.assertRaisesRegex(ValueError, "IEND"):
            decode(png()[:-12])

    def test_trailing_file_bytes(self):
        with self.assertRaisesRegex(ValueError, "trailing"):
            decode(png() + b"garbage")

    def test_trailing_zlib_stream(self):
        with self.assertRaisesRegex(ValueError, "trailing"):
            decode(png(payload=zlib.compress(b"\0\1\2\3\4") + zlib.compress(b"second")))

    def test_nonconsecutive_idat(self):
        data = png()
        with self.assertRaisesRegex(ValueError, "nonconsecutive"):
            decode(data[:-12] + chunk(b"tEXt", b"x\0y") + chunk(b"IDAT", b"") + data[-12:])

    def test_dimension_limits(self):
        for w, h in ((0, 1), (1, 0), (0x7FFFFFFF, 0x7FFFFFFF), (20000, 20000)):
            with self.subTest(w=w, h=h), self.assertRaisesRegex(ValueError, "dimensions"):
                decode(png(width=w, height=h))

    def test_inflate_bomb(self):
        with self.assertRaisesRegex(ValueError, "oversized"):
            decode(png(payload=zlib.compress(b"\0" * 1_000_000)))

    def test_unknown_filter(self):
        with self.assertRaisesRegex(ValueError, "filter"):
            decode(png(b"\5\1\2\3\4"))

    def test_unsupported_depth(self):
        with self.assertRaisesRegex(ValueError, "8-bit"):
            decode(png(depth=16))

    def test_bad_signature(self):
        with self.assertRaisesRegex(ValueError, "signature"):
            decode(b"not an image")

    def test_encoder_buffer_size(self):
        with self.assertRaises(ValueError):
            encode(2, 2, b"short")

    def test_generated_recipes_deterministic(self):
        self.assertEqual(GEN.generated(), GEN.generated())

    def test_generated_dimensions_and_detail(self):
        for name, data in GEN.generated().items():
            image = decode(data)
            self.assertEqual((1024, 64) if name == "sparkle_strip.png" else (512, 288), (image.width, image.height))
            colors = {row[x:x + 4] for row in image.rows for x in range(0, len(row), 4)}
            self.assertGreater(len(colors), 16)

    def test_sparkle_alpha_and_frames(self):
        image = decode(GEN.generated()["sparkle_strip.png"])
        self.assertEqual(0, image.rows[0][3])
        self.assertNotEqual(b"".join(r[:256] for r in image.rows), b"".join(r[2048:2304] for r in image.rows))


class SmokeContractTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def image(self, pixels, w=640, h=360):
        path = self.root / "scene.png"
        path.write_bytes(encode(w, h, pixels))
        return path

    def check(self, path):
        with contextlib.redirect_stdout(io.StringIO()):
            return SMOKE.check_image(path)

    def test_black_capture(self):
        self.assertTrue(any("black" in e for e in self.check(self.image(b"\0\0\0\xff" * (640 * 360)))))

    def test_small_capture(self):
        self.assertTrue(any("small" in e for e in self.check(self.image(b"\x80\x80\x80\xff" * 16, 4, 4))))

    def test_missing_texture_checker(self):
        pixels = b"".join(b"\xff\0\xff\xff" if (x // 16 + y // 16) % 2 else b"\0\0\0\xff" for y in range(360) for x in range(640))
        self.assertTrue(any("checker" in e for e in self.check(self.image(pixels))))

    def test_nonblank_capture(self):
        pixels = bytes(v for y in range(360) for x in range(640) for v in (40 + x % 160, 35 + y % 170, 60 + (x + y) % 120, 255))
        self.assertEqual([], self.check(self.image(pixels)))

    def test_capture_crc(self):
        path = self.image(b"\x80\x80\x80\xff" * (640 * 360))
        data = bytearray(path.read_bytes()); data[-1] ^= 1; path.write_bytes(data)
        self.assertTrue(any("CRC" in e for e in self.check(path)))

    def test_missing_capture(self):
        self.assertTrue(self.check(self.root / "missing.png"))

    def test_unqualified_decode_log(self):
        path = self.root / "latest.log"; path.write_text("[Render thread/ERROR]: Could not load image\n")
        self.assertTrue(SMOKE.check_log(path))

    def test_log_regressions(self):
        for text in ("Failed to load texture: svhub:textures/a.png", "unknown PNG chunk type", "bad PNG CRC in IDAT", "invalid PNG zlib stream", "Failed to decode svhub:textures/wiki.png", "Minecraft has crashed!", "ReportedException", "Rendering screen", "OpenGL error", "[SVHub Visual Smoke] timed out"):
            with self.subTest(text=text):
                path = self.root / "latest.log"; path.write_text(text)
                self.assertTrue(SMOKE.check_log(path))

    def test_audio_warnings_not_rejected(self):
        path = self.root / "latest.log"; path.write_text("Failed to initialize audio device\nNarrator library flite is missing\n")
        self.assertEqual([], SMOKE.check_log(path))

    def test_missing_or_empty_required_log(self):
        path = self.root / "latest.log"
        self.assertTrue(SMOKE.check_log(path))
        path.write_text("")
        self.assertTrue(SMOKE.check_log(path))

    def test_cli_aggregates_errors(self):
        path = self.root / "latest.log"; path.write_text("Could not load image")
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()) as errors:
            result = SMOKE.main(["--log", str(path), str(self.root / "missing.png")])
        self.assertEqual(1, result)
        self.assertIn("Could not load image", errors.getvalue())
        self.assertIn("missing.png", errors.getvalue())

if __name__ == "__main__":
    unittest.main()
