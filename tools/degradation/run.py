#!/usr/bin/env python3
"""
Developer degradation harness (TESTING.md) — NOT shipped in the app.

Given a few source bill images with an IPS QR, it generates a matrix of
degraded variants and measures how far a photo can degrade before IPS-QR
decoding fails. Its output tells us (a) the empirical quality threshold and
(b) the exact user-facing guidance copy for the camera/photo screens, so that
guidance stays grounded in data rather than guesswork.

This tool is a dev aid. It must NEVER be part of the shipped app and must not
pull any proprietary engine into the build. It uses only OpenCV + pyzbar
(both permissive, dev-only) and reads local sample images.

Usage:
    python tools/degradation/run.py --input samples/ --out report.csv
"""
from __future__ import annotations

import argparse
import csv
import glob
import os
import sys

# Variant matrix (see TESTING.md). Kept explicit so the report is reproducible.
DOWNSCALE = [1.00, 0.75, 0.50, 0.35, 0.25]
BLUR_RADIUS = [0, 1, 2, 3, 4, 6]           # Gaussian px
ROTATION = [-15, -8, 0, 8, 15]             # degrees
PERSPECTIVE = ["none", "mild", "strong"]   # keystone
SHADOW = ["even", "soft", "harsh"]         # brightness gradient


def _lazy_imports():
    try:
        import cv2  # noqa: F401
        import numpy  # noqa: F401
        from pyzbar import pyzbar  # noqa: F401
    except ImportError as e:  # pragma: no cover - dev tool
        sys.exit(
            "This dev harness needs opencv-python, numpy and pyzbar:\n"
            "    pip install opencv-python numpy pyzbar\n"
            f"(missing: {e.name})"
        )
    import cv2
    import numpy as np
    from pyzbar import pyzbar
    return cv2, np, pyzbar


def variants(cv2, np, img):
    """Yield (label_dict, degraded_image) across the full matrix."""
    import numpy as _np  # noqa
    h, w = img.shape[:2]
    for ds in DOWNSCALE:
        small = cv2.resize(img, (max(1, int(w * ds)), max(1, int(h * ds))))
        for blur in BLUR_RADIUS:
            b = small if blur == 0 else cv2.GaussianBlur(small, (0, 0), blur)
            for rot in ROTATION:
                m = cv2.getRotationMatrix2D((b.shape[1] / 2, b.shape[0] / 2), rot, 1.0)
                r = cv2.warpAffine(b, m, (b.shape[1], b.shape[0]), borderValue=(255, 255, 255))
                # perspective / shadow are represented as labels here; extend as needed
                for persp in PERSPECTIVE:
                    for shadow in SHADOW:
                        yield (
                            {"downscale": ds, "blur": blur, "rotation": rot,
                             "perspective": persp, "shadow": shadow},
                            r,
                        )


def decodes_ips(pyzbar, image) -> bool:
    for code in pyzbar.decode(image):
        try:
            if code.data.decode("utf-8", "ignore").startswith("K:PR"):
                return True
        except Exception:
            pass
    return False


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", required=True, help="folder of source bill images")
    ap.add_argument("--out", default="report.csv")
    args = ap.parse_args()

    cv2, np, pyzbar = _lazy_imports()

    images = sorted(
        glob.glob(os.path.join(args.input, "*.png"))
        + glob.glob(os.path.join(args.input, "*.jpg"))
    )
    if not images:
        sys.exit(f"no images in {args.input}")

    rows, ok, total = [], 0, 0
    for path in images:
        src = cv2.imread(path)
        if src is None:
            continue
        for label, deg in variants(cv2, np, src):
            decoded = decodes_ips(pyzbar, deg)
            total += 1
            ok += 1 if decoded else 0
            rows.append({"image": os.path.basename(path), **label, "decoded": int(decoded)})

    with open(args.out, "w", newline="") as f:
        writer = csv.DictWriter(
            f, fieldnames=["image", "downscale", "blur", "rotation",
                           "perspective", "shadow", "decoded"])
        writer.writeheader()
        writer.writerows(rows)

    rate = (ok / total) if total else 0.0
    print(f"variants: {total}  decoded: {ok}  rate: {rate:.1%}  -> {args.out}")
    print("Record the measured threshold and the camera guidance copy in TESTING.md.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
