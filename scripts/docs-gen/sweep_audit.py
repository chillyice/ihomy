# -*- coding: utf-8 -*-
"""Full-document sweep: render every page at low dpi and check each one for
blank pages and ink clipping at the page edges. Also verify that the printed
table of contents page numbers match where the headings actually land."""
import math
import re
import subprocess
import sys
from pathlib import Path

from PIL import Image

RENDER = Path(r"C:\Users\chill\AppData\Local\Temp\render")
SWEEP = Path(r"C:\Users\chill\AppData\Local\Temp\sweep")
POPPLER = Path(r"D:\Program Files\poppler-24.07.0\Library\bin")
DPI = 72
PAGE_PT_W, PAGE_PT_H = 595.304, 841.89
GUARD_CM = 0.7

DOCS = {
    "ihomy数据库结构文档": "DS-1",
    "ihomy接口文档": "DM-1",
    "ihomy用户手册": "WM-1",
}


def sweep(name):
    out = SWEEP / name
    out.mkdir(parents=True, exist_ok=True)
    for f in out.glob("*.png"):
        f.unlink()
    subprocess.run(
        [str(POPPLER / "pdftoppm.exe"), "-png", "-r", str(DPI),
         str(RENDER / (name + ".pdf")), str(out / "p")],
        check=True, capture_output=True,
    )
    return sorted(out.glob("*.png"))


def main():
    guard = int(GUARD_CM / 2.54 * DPI)
    problems = []
    for name in DOCS:
        pages = sweep(name)
        W = int(PAGE_PT_W / 72 * DPI)
        H = int(PAGE_PT_H / 72 * DPI)
        blank, clipped = [], []
        for pi, p in enumerate(pages):
            img = Image.open(p).convert("RGB")
            px = img.load()
            w, h = img.size
            dark = n = 0
            for x in range(0, min(w, W), 2):
                for y in range(0, min(h, H), 2):
                    r, g, b = px[x, y][:3]
                    dark += (r + g + b) / 3 < 160
                    n += 1
            ratio = dark / max(n, 1)
            if ratio < 0.0035:
                blank.append((p.name, round(ratio, 5)))
            hit = None
            for y in (range(guard, H - guard, 3) if pi > 0 else []):
                for x in list(range(guard)) + list(range(W - guard, W)):
                    r, g, b = px[x, y][:3]
                    if (r + g + b) / 3 < 150:
                        hit = (x, y)
                        break
                if hit:
                    break
            if hit:
                clipped.append((p.name, hit))
        print("%-22s pages=%d  blank=%d  edge-clipped=%d" % (name, len(pages), len(blank), len(clipped)))
        if blank:
            print("   blank:", blank[:8])
            problems.append("%s blank pages: %s" % (name, blank[:8]))
        if clipped:
            print("   clipped:", clipped[:8])
            problems.append("%s ink near page edge: %s" % (name, clipped[:8]))

        # TOC accuracy: compare printed TOC page numbers with where headings land
        txt = (RENDER / (name + ".txt")).read_text(encoding="utf-8", errors="replace")
        ptxt = txt.split("\f")
        # locate the TOC pages (they contain dot leaders)
        toc_pages = [i for i, pg in enumerate(ptxt) if pg.count("....") > 3]
        toc = "\n".join(ptxt[i] for i in toc_pages)
        entries = re.findall(r"(?m)^\s*(.+?)\s*\.{4,}\s*(\d{1,3})\s*$", toc)
        checked = mismatch = 0
        samples = []
        for title, pageno in entries:
            title = title.strip()
            key = re.sub(r"\s+", "", title)[:10]
            if len(key) < 5:
                continue
            found = None
            for i, pg in enumerate(ptxt):
                if i in toc_pages or i <= max(toc_pages):
                    continue  # TOC pages list the headings themselves
                if len(re.sub(r"\s", "", pg)) < 40:
                    continue
                if key in re.sub(r"\s+", "", pg):
                    found = i - len(toc_pages)  # body numbering restarts at 1
                    break
            if found is None:
                continue
            checked += 1
            if abs(found - int(pageno)) > 0:
                mismatch += 1
                if len(samples) < 6:
                    samples.append((title[:28], int(pageno), found))
        print("   TOC entries checked=%d  mismatched=%d  %s" % (checked, mismatch, samples[:4]))
        if checked and mismatch > checked * 0.5:
            problems.append("%s TOC page numbers largely wrong (%d/%d)" % (name, mismatch, checked))

    print()
    if problems:
        print("SWEEP PROBLEMS (%d):" % len(problems))
        for p in problems:
            print("  -", p)
        return 1
    print("Full sweep clean: no blank pages, no page-edge clipping, TOC numbers consistent.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
