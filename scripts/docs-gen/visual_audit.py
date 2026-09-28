# -*- coding: utf-8 -*-
"""Programmatic visual audit of the rendered documents.

No image-capable reviewer was available (both visual-judge providers are down),
so this measures the rendered pixels instead of eyeballing them:

  * cover pages  -> the background must reach all four page edges
  * body pages   -> ink must not approach the page edge (clipping / overflow),
                    and must not intrude into the top margin
  * all pages    -> ink coverage, to catch blank or near-blank pages
  * extracted text -> residual markdown / placeholder / undefined-value artifacts

Known-intended exceptions, not reported as failures:
  * the footer page number sits inside the bottom margin
  * table-of-contents pages place their page numbers at a wider right tab stop
"""
import re
import sys
from pathlib import Path

from PIL import Image

RENDER = Path(r"C:\Users\chill\AppData\Local\Temp\render")
DOCS = {
    "ihomy数据库结构文档": "DS-1",
    "ihomy接口文档": "DM-1",
    "ihomy用户手册": "WM-1",
}
BG = {"DS-1": (11, 28, 44), "DM-1": (22, 34, 53), "WM-1": (244, 241, 233)}
DPI = 100
CM = DPI / 2.54
# A4 in PostScript points; pdftoppm rounds up, so the last raster row/column can
# lie outside the real page and is padded with white. Measure only the true area.
PAGE_PT_W, PAGE_PT_H = 595.304, 841.89
USABLE_W = int(PAGE_PT_W / 72 * DPI)
USABLE_H = int(PAGE_PT_H / 72 * DPI)
LEFT_M, RIGHT_M, TOP_M, BOTTOM_M = int(3.0 * CM), int(2.5 * CM), int(2.54 * CM), int(2.54 * CM)
FOOTER_BAND = int(1.6 * CM)   # footer line centre sits ~1.1-1.4 cm above the page foot
EDGE_GUARD = int(0.8 * CM)    # nothing may come closer than this to a page edge


def is_dark(px, x, y, thr=150):
    r, g, b = px[x, y][:3]
    return (r + g + b) / 3 < thr


def ink_ratio(img):
    px = img.load()
    w, h = img.size
    dark = n = 0
    for x in range(0, w, 2):
        for y in range(0, h, 2):
            r, g, b = px[x, y][:3]
            dark += (r + g + b) / 3 < 160
            n += 1
    return dark / max(n, 1)


def cover_edge_white(img, bg):
    w, h = img.size
    px = img.load()
    bg_is_white = sum(bg) / 3 > 240
    band = 3
    W = min(w, USABLE_W)
    H = min(h, USABLE_H)
    bad = total = 0
    coords = []
    rows = list(range(band)) + list(range(H - band, H))
    cols = list(range(band)) + list(range(W - band, W))
    for y in rows:
        for x in range(W):
            total += 1
            if not bg_is_white and sum(px[x, y][:3]) / 3 > 245:
                bad += 1
                coords.append((x, y))
    for x in cols:
        for y in range(H):
            total += 1
            if not bg_is_white and sum(px[x, y][:3]) / 3 > 245:
                bad += 1
                coords.append((x, y))
    return (bad / max(total, 1)), coords


def body_probe(img):
    """Return the ink extents and any page-edge / top-margin intrusions."""
    w, h = img.size
    px = img.load()
    minx, maxx, maxy = w, 0, 0
    for y in range(TOP_M, h - FOOTER_BAND, 5):
        for x in range(w - 1, -1, -1):
            if is_dark(px, x, y):
                maxx = max(maxx, x)
                break
        for x in range(w):
            if is_dark(px, x, y):
                minx = min(minx, x)
                break
    for y in range(h - 1, -1, -1):
        if any(is_dark(px, x, y) for x in range(0, w, 3)):
            maxy = y
            break
    edge_hits = []
    for y in range(EDGE_GUARD, h - EDGE_GUARD, 4):
        for x in list(range(EDGE_GUARD)) + list(range(w - EDGE_GUARD, w)):
            if is_dark(px, x, y):
                edge_hits.append((x, y))
                break
    top_hits = []
    for y in range(EDGE_GUARD, TOP_M - 8):
        for x in range(EDGE_GUARD, w - EDGE_GUARD):
            if is_dark(px, x, y):
                top_hits.append((x, y))
                break
    return minx, maxx, maxy, edge_hits, top_hits


def main():
    failures, notes = [], []
    for name, pal in DOCS.items():
        print("=== %s ===" % name)
        for p in sorted((RENDER / "png").glob(name + "*.png")):
            page = int(re.search(r"-(\d{3})-\d+\.png$", p.name).group(1))
            img = Image.open(p).convert("RGB")
            r = ink_ratio(img)
            if page == 1:
                frac, coords = cover_edge_white(img, BG[pal])
                if frac > 0.001:
                    ymax = max((c[1] for c in coords), default=0)
                    xmax = max((c[0] for c in coords), default=0)
                    print("  p1  cover: %d/%d edge pixels are white (%.2f%%), furthest at (%d,%d), page=%s"
                          % (len(coords), len(coords), frac * 100, xmax, ymax, img.size))
                    failures.append("%s cover: background does not reach the page edge (%d white edge pixels, %.2f%%)"
                                    % (name, len(coords), frac * 100))
                else:
                    print("  p1  cover: background covers all edges (%.3f%% white)" % (frac * 100))
            else:
                minx, maxx, maxy, edge_hits, top_hits = body_probe(img)
                flags = []
                if edge_hits:
                    flags.append("EDGE %s" % edge_hits[:3])
                    failures.append("%s p%d ink within %.1f cm of the page edge: %s"
                                    % (name, page, EDGE_GUARD / CM, edge_hits[:3]))
                if top_hits:
                    flags.append("TOP-MARGIN %s" % top_hits[:3])
                    failures.append("%s p%d ink inside the top margin: %s" % (name, page, top_hits[:3]))
                if r < 0.004:
                    flags.append("NEAR-BLANK")
                    failures.append("%s p%d looks blank (ink %.4f)" % (name, page, r))
                over = maxx - (img.size[0] - RIGHT_M)
                print("  p%-3d ink=%.3f  x=[%d,%d] (text area right edge %d, +%dpx)  last-y=%d  %s"
                      % (page, r, minx, maxx, img.size[0] - RIGHT_M, over, maxy,
                         " ".join(flags) if flags else "ok"))
        txt = (RENDER / (name + ".txt")).read_text(encoding="utf-8", errors="replace")
        for label, pat in {
            "markdown pipe table": r"\|.*\|",
            "markdown bold": r"\*\*",
            "markdown heading": r"(?m)^\s*#{1,6}\s",
            "placeholder/undefined token": r"【Please fill|TBD|待补充|略】|undefined|\bNaN\b|\bNone\b",
            "raw escaped quote": r'\\"',
            "html tag": r"</?\w+>",
        }.items():
            m = re.findall(pat, txt)
            if m:
                print("    TEXT-ARTIFACT %s x%d e.g. %r" % (label, len(m), m[0][:70]))
                failures.append("%s text artifact: %s x%d" % (name, label, len(m)))
        notes.append("%s: %d rendered pages checked" % (name, len(re.findall(r"\f", txt))))
    print()
    if failures:
        print("FAILURES (%d):" % len(failures))
        for f in failures:
            print("  -", f)
        return 1
    print("All programmatic visual checks passed (cover edge coverage, no page-edge")
    print("or top-margin intrusion, no blank pages, no text artifacts).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
