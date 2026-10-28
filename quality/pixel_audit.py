#!/usr/bin/env python3
"""Full RGB pixel audit of original 1179x2556 refs vs genuine Android captures.

Usage:
  python3 quality/pixel_audit.py /path/to/reference/ekrani /path/to/screenshots --output pixel_audit.csv

Requirements: Pillow (QA tool only; not an Android runtime dependency).
This tool never modifies screenshots, never hides dynamic data, and preserves RGB.
"""
import argparse
import csv
from pathlib import Path
from PIL import Image, ImageChops, ImageStat

def check(ref, actual):
    with Image.open(ref) as left, Image.open(actual) as right:
        left=left.convert("RGB")
        right=right.convert("RGB")
        if left.size != right.size:
            return {"error": f"Dimensions: {left.size} vs {right.size}"}
        diff=ImageChops.difference(left, right)
        stats=ImageStat.Stat(diff)
        w,h=left.size
        rgb_mae=round(sum(stats.mean)/3,4)
        # Avoid pretending differing clocks or fixture content is a UI defect.
        central=diff.crop((25,100,w-25,h-150))
        center_stats=ImageStat.Stat(central)
        center_mae=round(sum(center_stats.mean)/3,4)
        total_pixels=w*h
        hist=diff.convert("RGB")
        nonzero=0
        for pixel in hist.getdata():
            if pixel!=(0,0,0):
                nonzero+=1
        return {
            "rgb_mae":rgb_mae,
            "main_content_mae":center_mae,
            "red_mae":round(stats.mean[0],4),
            "green_mae":round(stats.mean[1],4),
            "blue_mae":round(stats.mean[2],4),
            "mismatched_pixels_pct":round(100*nonzero/total_pixels,3)
        }

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("references",type=Path)
    parser.add_argument("captures",type=Path)
    parser.add_argument("--output",default="pixel_audit.csv",type=Path)
    args=parser.parse_args()
    rows=[]
    for reference in sorted(args.references.glob("*.png")):
        candidate=args.captures/reference.name
        if not candidate.exists():
            rows.append({"screen":reference.name,"error":"Screenshot missing"})
        else:
            rows.append({"screen":reference.name,**check(reference,candidate)})
    keys=["screen","rgb_mae","main_content_mae","red_mae","green_mae","blue_mae","mismatched_pixels_pct","error"]
    with args.output.open("w",newline="",encoding="utf-8") as out:
        writer=csv.DictWriter(out,fieldnames=keys)
        writer.writeheader()
        writer.writerows(rows)
    for row in rows:
        print(row)
    if not rows or any("error" in row for row in rows):
        raise SystemExit(2)

if __name__=="__main__":
    main()
