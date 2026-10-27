# -*- coding: utf-8 -*-
"""Build the 60-page source-code PDF required by a Chinese software copyright filing.

Rules implemented (中国版权保护中心):
  * 60 pages: the first 30 pages and the last 30 pages of the source listing
  * at least 50 lines per page
  * header carries the software name + version, footer carries the page number

The listing is first-party source only (no node_modules / dist / generated data).
Run:  python scripts/soft-copyright/build_source_doc.py
Output: docs/软著/ihomy-源程序-V10.73.pdf
"""
from __future__ import annotations

import html
import re
import shutil
import subprocess
import tempfile
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT_DIR = ROOT / "docs" / "软著"
SOFTWARE_NAME = "ihomy家庭内容共享平台"
LINES_PER_PAGE = 50
PAGES = 60
MAX_COLS = 96

SKIP_NAMES = {"world-land-path.js"}  # generated map path data, not program logic
SKIP_SUFFIXES = {".spec.js", ".test.js"}


def rel(p: Path) -> str:
    return p.relative_to(ROOT).as_posix()


def version() -> str:
    return (ROOT / "VERSION").read_text(encoding="utf-8").strip()


def java_order(p: Path) -> tuple[int, str]:
    rel = p.relative_to(ROOT / "backend/src/main/java").as_posix()
    # The entry class leads the listing; the rest follows package order.
    return (0 if rel.endswith("IhomyApplication.java") else 1, rel.lower())


def collect() -> list[Path]:
    groups = [
        sorted((ROOT / "backend/src/main/java").rglob("*.java"), key=java_order),
        sorted((ROOT / "backend/src/main/resources/mapper").rglob("*.xml"), key=lambda p: p.as_posix().lower()),
        [ROOT / "frontend/index.html", ROOT / "frontend/vite.config.js"],
        sorted(
            (
                p
                for p in (ROOT / "frontend/src").rglob("*")
                if p.is_file() and p.suffix in {".js", ".vue", ".css", ".ts"}
            ),
            key=lambda p: p.as_posix().lower(),
        ),
    ]
    files: list[Path] = []
    for group in groups:
        for p in group:
            if not p.is_file():
                continue
            if p.name in SKIP_NAMES or any(p.name.endswith(s) for s in SKIP_SUFFIXES):
                continue
            files.append(p)
    return files


def banner(path: Path) -> str:
    line = "===== %s =====" % rel(path)
    suffix = path.suffix.lower()
    if suffix in {".java", ".js", ".ts"}:
        return "// " + line
    if suffix == ".css":
        return "/* " + line + " */"
    return "<!-- " + line + " -->"


def width(ch: str) -> int:
    return 2 if unicodedata.east_asian_width(ch) in {"W", "F"} else 1


def wrap(line: str) -> list[str]:
    """Hard-wrap a source line by display columns so nothing is clipped in print."""
    line = line.rstrip()
    if not line:
        return []
    if sum(width(c) for c in line) <= MAX_COLS:
        return [line]
    out: list[str] = []
    cur: list[str] = []
    cur_w = 0
    limit = MAX_COLS
    for ch in line:
        w = width(ch)
        if cur_w + w > limit:
            out.append("".join(cur))
            cur = ["    ", ch]  # continuation indent
            cur_w = 4 + w
            limit = MAX_COLS
        else:
            cur.append(ch)
            cur_w += w
    if cur:
        out.append("".join(cur))
    return out


def build_lines(files: list[Path]) -> list[str]:
    lines: list[str] = []
    for p in files:
        text = p.read_text(encoding="utf-8-sig", errors="ignore")
        lines.append(banner(p))
        for raw in text.replace("\r\n", "\n").replace("\r", "\n").split("\n"):
            raw = raw.expandtabs(4).rstrip()
            if not raw.strip():
                continue
            lines.extend(wrap(raw))
    return lines


def page_html(lines: list[str], page_no: int, total: int, ver: str) -> str:
    header = (
        '<div class="hd"><span>%s %s</span><span>第 %d 页 / 共 %d 页</span></div>'
        % (html.escape(SOFTWARE_NAME), html.escape(ver), page_no, total)
    )
    code = "\n".join(html.escape(x, quote=False) for x in lines)
    return (
        '<section class="page">\n%s\n<pre class="code">%s</pre>\n'
        '<div class="ft">源程序</div>\n</section>\n' % (header, code)
    )


def render_html(pages: list[list[str]], out: Path, ver: str) -> None:
    total = len(pages)
    css = """
    @page { size: A4; margin: 0; }
    html, body { margin: 0; padding: 0; }
    .page {
      width: 210mm; height: 297mm; box-sizing: border-box; position: relative;
      page-break-after: always; overflow: hidden;
    }
    .page:last-child { page-break-after: auto; }
    .hd {
      position: absolute; top: 8mm; left: 14mm; right: 14mm;
      display: flex; justify-content: space-between;
      font-family: "SimSun", serif; font-size: 9pt; color: #000;
    }
    .ft {
      position: absolute; bottom: 7mm; left: 14mm; right: 14mm;
      text-align: center; font-family: "SimSun", serif;
      font-size: 8pt; color: #555;
    }
    .code {
      position: absolute; top: 20mm; left: 14mm; right: 14mm;
      margin: 0; font-family: Consolas, "Courier New", "Microsoft YaHei", monospace;
      font-size: 9pt; line-height: 12.5pt; white-space: pre; color: #000;
    }
    """
    body = "".join(page_html(p, i + 1, total, ver) for i, p in enumerate(pages))
    doc = (
        '<!DOCTYPE html>\n<html lang="zh-CN">\n<head>\n<meta charset="utf-8">\n'
        "<title>%s %s 源程序</title>\n<style>%s</style>\n</head>\n<body>\n%s</body>\n</html>\n"
        % (html.escape(SOFTWARE_NAME), html.escape(ver), css, body)
    )
    out.write_text(doc, encoding="utf-8")


def pdf_pages(pdf: Path) -> int:
    from pypdf import PdfReader

    return len(PdfReader(str(pdf)).pages)


def verify(pdf: Path, pages: list[list[str]]) -> bool:
    from pypdf import PdfReader

    ok = True
    reader = PdfReader(str(pdf))
    if len(reader.pages) != len(pages):
        print("  [FAIL] page count %d != %d" % (len(reader.pages), len(pages)))
        return False
    for i, page in enumerate(pages, 1):
        if len(page) != LINES_PER_PAGE:
            print("  [FAIL] page %d carries %d lines" % (i, len(page)))
            ok = False
            continue
        text = reader.pages[i - 1].extract_text() or ""
        lines = [x for x in text.splitlines() if x.strip()]
        if len(lines) < LINES_PER_PAGE + 2:  # header + 50 code lines + footer
            print("  [FAIL] page %d extracted only %d lines" % (i, len(lines)))
            ok = False
        flat = re.sub(r"\s+", "", text)
        for token in (page[0], page[-1]):
            needle = re.sub(r"\s+", "", token)
            if needle and needle not in flat:
                print("  [FAIL] page %d: %s" % (i, needle[:40]))
                ok = False
    return ok


def main() -> None:
    ver = version()
    files = collect()
    lines = build_lines(files)
    head = LINES_PER_PAGE * (PAGES // 2)
    tail = LINES_PER_PAGE * (PAGES // 2)
    print("source files=%d listing lines=%d" % (len(files), len(lines)))
    if len(lines) < head + tail:
        raise SystemExit("listing shorter than %d lines" % (head + tail))

    selected = lines[:head] + lines[-tail:]
    pages = [selected[i : i + LINES_PER_PAGE] for i in range(0, len(selected), LINES_PER_PAGE)]
    print("pages=%d lines/page=%d (header: %s %s)" % (len(pages), LINES_PER_PAGE, SOFTWARE_NAME, ver))

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    tmp = Path(tempfile.mkdtemp(prefix="ihomy-src-"))
    html_path = tmp / "source.html"
    render_html(pages, html_path, ver)

    node = shutil.which("node")
    if not node:
        raise SystemExit("node not found")
    options = tmp / "print.json"
    options.write_text(
        '{"checkOverflow": true, "paperWidth": 8.27, "paperHeight": 11.69,'
        ' "marginTop": 0, "marginBottom": 0, "marginLeft": 0, "marginRight": 0,'
        ' "preferCSSPageSize": true, "printBackground": false}',
        encoding="utf-8",
    )
    out = OUT_DIR / ("ihomy-源程序-%s.pdf" % ver)
    subprocess.run(
        [node, str(ROOT / "scripts/soft-copyright/print_pdf.mjs"), str(html_path), str(out), str(options)],
        check=True,
    )
    print("verify %s" % out)
    if not verify(out, pages):
        raise SystemExit("verification failed")
    print("OK: %d pages, %d lines/page" % (pdf_pages(out), LINES_PER_PAGE))


if __name__ == "__main__":
    main()
