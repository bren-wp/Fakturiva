#!/usr/bin/env python3
"""Reject Android's light fullscreen tutorial over the dark native Splash.

Standard-library PNG first-row pixel check: no visual-reference assets needed.
A passing color check is NOT evidence of 1:1 design parity. XML verification
and the twenty-screen test must still pass independently.
"""
from __future__ import annotations

import struct
import sys
import zlib
from pathlib import Path

SIGNATURE = b"\x89PNG\r\n\x1a\n"


def first_row_pixel(path: Path, x: int = 30) -> tuple[int, int, int]:
    data = path.read_bytes()
    if not data.startswith(SIGNATURE):
        raise ValueError("Invalid PNG")
    position = len(SIGNATURE)
    width = height = bit_depth = color_type = 0
    chunks = []
    while position + 12 <= len(data):
        size = struct.unpack_from(">I", data, position)[0]
        kind = data[position + 4:position + 8]
        if position + 12 + size > len(data):
            raise ValueError("Truncated PNG chunk")
        payload = data[position + 8:position + 8 + size]
        if kind == b"IHDR":
            width, height, bit_depth, color_type, compression, filtering, interlace = (
                struct.unpack(">IIBBBBB", payload))
            if bit_depth != 8 or color_type not in (2, 6) or (
                    compression or filtering or interlace):
                raise ValueError("Unsupported PNG format")
        elif kind == b"IDAT":
            chunks.append(payload)
        elif kind == b"IEND":
            break
        position += 12 + size
    if not chunks or width <= x or height <= 0:
        raise ValueError("Missing PNG data")
    bpp = 4 if color_type == 6 else 3
    scan = zlib.decompress(b"".join(chunks))
    length = width * bpp
    if len(scan) < length + 1 or scan[0] > 4:
        raise ValueError("Invalid PNG first row")
    filter_type = scan[0]
    row = bytearray(scan[1:length + 1])
    for index in range(length):
        left = row[index - bpp] if index >= bpp else 0
        correction = (left if filter_type in (1, 4)
                      else left // 2 if filter_type == 3 else 0)
        row[index] = (row[index] + correction) & 255
    return tuple(row[x * bpp:x * bpp + 3])


def is_native_dark_splash(path: Path) -> bool:
    red, green, blue = first_row_pixel(path)
    return red < 120 and green < 165 and blue < 190


def main() -> int:
    if len(sys.argv) != 2:
        print("Usage: splash_frame_guard.py screenshot.png", file=sys.stderr)
        return 2
    try:
        target = Path(sys.argv[1])
        pixel = first_row_pixel(target)
        if is_native_dark_splash(target):
            return 0
        print(f"::warning::Splash top-left RGB {pixel} is not dark; "
              "a fullscreen SystemUI tutorial or another overlay may be visible")
        return 1
    except (OSError, ValueError, zlib.error, struct.error) as error:
        print(f"::error::Cannot validate splash PNG: {error}")
        return 2


if __name__ == "__main__":
    sys.exit(main())
