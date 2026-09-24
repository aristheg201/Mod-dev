"""Bounded PNG codec for 8-bit RGB/RGBA resources and framebuffer captures.

Standard library only. Verification never repairs or ignores a corrupt chunk.
"""
from __future__ import annotations
from dataclasses import dataclass
import struct
import zlib

SIGNATURE = b"\x89PNG\r\n\x1a\n"
MAX_PIXELS = 16_777_216
MAX_BYTES = 128 * 1024 * 1024

@dataclass(frozen=True)
class Image:
    width: int
    height: int
    channels: int
    rows: tuple[bytes, ...]


def chunk(kind: bytes, body: bytes) -> bytes:
    return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body) & 0xFFFFFFFF)


def paeth(a: int, b: int, c: int) -> int:
    p = a + b - c
    da, db, dc = abs(p - a), abs(p - b), abs(p - c)
    return a if da <= db and da <= dc else b if db <= dc else c


def decode(data: bytes) -> Image:
    if len(data) > MAX_BYTES or not data.startswith(SIGNATURE):
        raise ValueError("invalid PNG signature or file size")
    pos, header = 8, None
    payload = bytearray()
    seen_data = ended_data = seen_end = seen_palette = False
    while pos < len(data):
        if pos + 12 > len(data):
            raise ValueError("truncated PNG chunk header")
        size = struct.unpack_from(">I", data, pos)[0]
        kind = data[pos + 4:pos + 8]
        end = pos + size + 12
        if size > 0x7FFFFFFF or end > len(data):
            raise ValueError("truncated or oversized PNG chunk")
        if not all(65 <= c <= 90 or 97 <= c <= 122 for c in kind) or kind[2] & 32:
            raise ValueError("invalid PNG chunk type")
        body = data[pos + 8:pos + 8 + size]
        crc = struct.unpack_from(">I", data, pos + 8 + size)[0]
        if crc != zlib.crc32(kind + body) & 0xFFFFFFFF:
            raise ValueError(f"bad PNG CRC in {kind.decode('ascii')}")
        if header is None and kind != b"IHDR":
            raise ValueError("IHDR must be first")
        if seen_data and kind != b"IDAT":
            ended_data = True
        if kind == b"IHDR":
            if header is not None or size != 13:
                raise ValueError("duplicate or malformed IHDR")
            w, h, depth, color, compression, filtering, interlace = struct.unpack(">IIBBBBB", body)
            if not (0 < w <= 0x7FFFFFFF and 0 < h <= 0x7FFFFFFF) or w * h > MAX_PIXELS:
                raise ValueError("invalid or oversized PNG dimensions")
            if depth != 8 or color not in (2, 6) or compression or filtering or interlace:
                raise ValueError("expected non-interlaced 8-bit RGB/RGBA PNG")
            header = w, h, 3 if color == 2 else 4
        elif kind == b"PLTE":
            if seen_palette or seen_data or not size or size % 3 or size > 768:
                raise ValueError("invalid PLTE")
            seen_palette = True
        elif kind == b"IDAT":
            if ended_data:
                raise ValueError("nonconsecutive IDAT chunks")
            seen_data = True
            payload.extend(body)
        elif kind == b"IEND":
            if size or not seen_data:
                raise ValueError("invalid IEND or missing IDAT")
            if end != len(data):
                raise ValueError("trailing data after IEND")
            seen_end = True
        elif not kind[0] & 32:
            raise ValueError(f"unknown critical PNG chunk {kind.decode('ascii')}")
        pos = end
        if seen_end:
            break
    if not seen_end or header is None:
        raise ValueError("missing IHDR/IDAT/IEND")
    w, h, channels = header
    stride = w * channels
    expected = h * (stride + 1)
    inflater = zlib.decompressobj()
    try:
        raw = inflater.decompress(bytes(payload), expected + 1)
    except zlib.error as exc:
        raise ValueError(f"invalid PNG zlib stream: {exc}") from exc
    if len(raw) != expected or not inflater.eof or inflater.unused_data or inflater.unconsumed_tail:
        raise ValueError("PNG zlib stream truncated, oversized, or has trailing data")
    previous, rows, cursor = bytes(stride), [], 0
    for _ in range(h):
        mode = raw[cursor]
        row = raw[cursor + 1:cursor + 1 + stride]
        cursor += stride + 1
        if mode > 4:
            raise ValueError(f"unknown PNG filter {mode}")
        if mode:
            reconstructed = bytearray(stride)
            for x, value in enumerate(row):
                left = reconstructed[x - channels] if x >= channels else 0
                up = previous[x]
                corner = previous[x - channels] if x >= channels else 0
                predictor = left if mode == 1 else up if mode == 2 else (left + up) // 2 if mode == 3 else paeth(left, up, corner)
                reconstructed[x] = (value + predictor) & 255
            row = bytes(reconstructed)
        rows.append(row)
        previous = row
    return Image(w, h, channels, tuple(rows))


def encode(width: int, height: int, rgba: bytes) -> bytes:
    if width <= 0 or height <= 0 or width * height > MAX_PIXELS or len(rgba) != width * height * 4:
        raise ValueError("invalid RGBA dimensions/pixel count")
    stride = width * 4
    raw = b"".join(b"\0" + rgba[y * stride:(y + 1) * stride] for y in range(height))
    header = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    return SIGNATURE + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
