"""Regressions: Android's light fullscreen prompt must not count as native Splash."""
import importlib.util
from pathlib import Path
import struct
import tempfile
import unittest
import zlib

source=Path(__file__).resolve().parents[1]/"ci"/"splash_frame_guard.py"
spec=importlib.util.spec_from_file_location("splash_frame_guard",source)
guard=importlib.util.module_from_spec(spec)
spec.loader.exec_module(guard)


def png(color,filter_type=0):
    def chunk(kind,data):
        return (struct.pack(">I",len(data))+kind+data+
                struct.pack(">I",zlib.crc32(kind+data)&0xffffffff))
    width=40
    raw=bytearray(bytes(color)*width)
    if filter_type==1:
        for i in range(len(raw)-1,-1,-1):
            raw[i]=(raw[i]-(raw[i-3] if i>=3 else 0))&255
    row=bytes([filter_type])+bytes(raw)
    return (guard.SIGNATURE+
            chunk(b"IHDR",struct.pack(">IIBBBBB",width,1,8,2,0,0,0))+
            chunk(b"IDAT",zlib.compress(row))+chunk(b"IEND",b""))


class SplashFrameGuardTest(unittest.TestCase):
    def test_genuine_dark_splash_accepts_native_gradient(self):
        with tempfile.TemporaryDirectory() as temp:
            path=Path(temp)/"splash.png"
            path.write_bytes(png((19,48,87)))
            self.assertTrue(guard.is_native_dark_splash(path))
            self.assertEqual(guard.first_row_pixel(path),(19,48,87))

    def test_system_immersive_tutorial_is_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            path=Path(temp)/"overlay.png"
            path.write_bytes(png((238,237,244)))
            self.assertFalse(guard.is_native_dark_splash(path))

    def test_sub_filtered_png_is_supported(self):
        with tempfile.TemporaryDirectory() as temp:
            path=Path(temp)/"sub.png"
            path.write_bytes(png((21,55,90),1))
            self.assertEqual(guard.first_row_pixel(path),(21,55,90))

    def test_invalid_file_does_not_pass(self):
        with tempfile.TemporaryDirectory() as temp:
            path=Path(temp)/"not-png"
            path.write_bytes(b"not a screenshot")
            with self.assertRaises(ValueError):
                guard.is_native_dark_splash(path)


if __name__=="__main__":
    unittest.main()
