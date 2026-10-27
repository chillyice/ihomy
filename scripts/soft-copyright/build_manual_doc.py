# -*- coding: utf-8 -*-
"""Build the 60-page documentation PDF required by a Chinese software copyright filing.

The body reuses docs-gen/manual_part*.json (the maintained user manual).
Per the filing rules the submitted file carries the first 30 and the last 30
pages of the full manual; every page except the last must hold >= 30 lines.

Run:  python scripts/soft-copyright/build_manual_doc.py
Output: docs/软著/ihomy-用户操作手册-V10.73.pdf
"""
from __future__ import annotations

import html
import json
import shutil
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT_DIR = ROOT / "docs" / "软著"
GEN_DIR = ROOT / "scripts" / "docs-gen"
SOFTWARE_NAME = "ihomy家庭内容共享平台"
DOC_TITLE = "用户操作手册"
FRONT_PAGES = 30
BACK_PAGES = 30
MIN_LINES = 30


def version() -> str:
    return (ROOT / "VERSION").read_text(encoding="utf-8").strip()


def load_chapters() -> list[dict]:
    chapters: list[dict] = []
    for i in (1, 2, 3):
        data = json.loads((GEN_DIR / ("manual_part%d.json" % i)).read_text(encoding="utf-8"))
        chapters.extend(data["chapters"])
    chapters.sort(key=lambda c: int(c["number"]))
    return chapters


def esc(text: str) -> str:
    return html.escape(str(text), quote=False).replace("\n", "<br>")


def render_table(t: dict) -> str:
    widths = t.get("widths") or []
    rows = t.get("rows") or []
    if not rows:
        return ""
    cols = "".join('<col style="width:%d%%">' % w for w in widths)
    head = "".join("<th>%s</th>" % esc(x) for x in rows[0])
    body = "".join(
        "<tr>%s</tr>" % "".join("<td>%s</td>" % esc(x) for x in row) for row in rows[1:]
    )
    cap = '<caption>%s</caption>' % esc(t["caption"]) if t.get("caption") else ""
    return (
        "<table>%s<colgroup>%s</colgroup><thead><tr>%s</tr></thead><tbody>%s</tbody></table>"
        % (cap, cols, head, body)
    )


def render_section(s: dict) -> str:
    parts = ['<h3>%s %s</h3>' % (esc(s["number"]), esc(s["title"]))]
    if s.get("purpose"):
        parts.append('<p class="purpose">%s</p>' % esc(s["purpose"]))
    for p in s.get("paragraphs") or []:
        parts.append('<p class="body">%s</p>' % esc(p))
    steps = s.get("steps") or []
    if steps:
        parts.append('<ol class="steps">%s</ol>' % "".join("<li>%s</li>" % esc(x) for x in steps))
    for t in s.get("tables") or []:
        parts.append(render_table(t))
    for n in s.get("notes") or []:
        parts.append('<div class="note"><span class="tag">提示</span>%s</div>' % esc(n))
    return "".join(parts)


def render_chapter(c: dict) -> str:
    parts = ['<h2>%s %s</h2>' % (esc(c["number"]), esc(c["title"]))]
    if c.get("intro"):
        parts.append('<p class="chapter-intro">%s</p>' % esc(c["intro"]))
    for s in c.get("sections") or []:
        parts.append(render_section(s))
    return "".join(parts)


def render_html(chapters: list[dict], ver: str, out: Path) -> None:
    overview = "".join(
        "<li>%s %s</li>" % (esc(c["number"]), esc(c["title"])) for c in chapters
    )
    meta_rows = [
        ("软件名称", SOFTWARE_NAME),
        ("版本号", ver),
        ("文档类型", DOC_TITLE),
        ("适用对象", "家庭家长、成员、孩童与运维管理员"),
        ("运行环境", "电脑浏览器、安卓与 iOS（PWA）"),
        ("文档用途", "说明各功能的使用场景、操作步骤与注意事项"),
    ]
    meta = "".join("<tr><th>%s</th><td>%s</td></tr>" % (esc(k), esc(v)) for k, v in meta_rows)

    css = """
    @page { size: A4; }
    html, body { margin: 0; padding: 0; }
    body {
      font-family: "SimSun", serif; font-size: 10.5pt; line-height: 1.5; color: #111;
    }
    .doc-head { margin: 0 0 5mm; }
    .soft-name { font-family: "SimHei", sans-serif; font-size: 20pt; font-weight: bold; letter-spacing: 1pt; }
    .doc-title { font-family: "SimHei", sans-serif; font-size: 26pt; font-weight: bold; margin-top: 2mm; }
    .doc-ver { font-size: 11pt; color: #444; margin-top: 1.5mm; }
    table.meta { margin: 4mm 0 3mm; }
    .scope-title { font-family: "SimHei", sans-serif; font-size: 11pt; margin: 3mm 0 1.5mm; }
    ul.scope { margin: 0; padding-left: 5mm; columns: 2; column-gap: 12mm; font-size: 10pt; }
    ul.scope li { margin: 0 0 0.8mm; break-inside: avoid; }
    h2 {
      font-family: "SimHei", sans-serif; font-size: 15pt; font-weight: bold;
      margin: 7mm 0 2.5mm; padding-bottom: 1.5mm; border-bottom: 0.8pt solid #aaa;
      break-after: avoid;
    }
    h2:first-of-type { margin-top: 3mm; }
    h3 { font-family: "SimHei", sans-serif; font-size: 11.5pt; margin: 4mm 0 1.5mm; break-after: avoid; }
    p { margin: 0 0 1.2mm; text-align: justify; orphans: 2; widows: 2; }
    p.body { text-indent: 2em; }
    p.purpose { color: #222; }
    p.chapter-intro { text-indent: 2em; color: #222; }
    ol.steps { margin: 1mm 0 2mm; padding: 0; list-style: none; counter-reset: step; }
    ol.steps li { position: relative; padding-left: 8.5mm; margin-bottom: 1.1mm; text-align: justify; }
    ol.steps li::before {
      position: absolute; left: 0; top: 0;
      content: "（" counter(step) "）"; counter-increment: step;
    }
    table { width: 100%; border-collapse: collapse; margin: 1.5mm 0 3mm; font-size: 9.5pt; line-height: 1.45; }
    caption { caption-side: top; text-align: left; font-size: 9pt; color: #555; padding-bottom: 1mm; }
    th, td { border: 0.5pt solid #888; padding: 1.2mm 2mm; vertical-align: top; text-align: left; }
    th { background: #e8eeec; font-family: "SimHei", sans-serif; font-weight: normal; }
    thead { display: table-header-group; }
    tr { break-inside: avoid; }
    .note {
      margin: 1.5mm 0 2.5mm; padding: 1.8mm 2.5mm; background: #f4f7f6;
      border-left: 1.2mm solid #7d9d92; font-size: 9.8pt; line-height: 1.5; break-inside: avoid;
    }
    .note .tag { font-family: "SimHei", sans-serif; color: #356155; margin-right: 1.5mm; }
    """

    head = (
        '<div class="doc-head">'
        '<div class="soft-name">%s</div>'
        '<div class="doc-title">%s</div>'
        '<div class="doc-ver">版本 %s</div>'
        "</div>"
        % (esc(SOFTWARE_NAME), esc(DOC_TITLE), esc(ver))
    )
    meta_table = '<table class="meta"><colgroup><col style="width:22%%"><col style="width:78%%"></colgroup><tbody>%s</tbody></table>' % meta
    scope = '<div class="scope-title">内容概览</div><ul class="scope">%s</ul>' % overview
    body = "".join(render_chapter(c) for c in chapters)
    doc = (
        '<!DOCTYPE html>\n<html lang="zh-CN">\n<head>\n<meta charset="utf-8">\n'
        "<title>%s %s</title>\n<style>%s</style>\n</head>\n<body>\n%s%s%s%s</body>\n</html>\n"
        % (esc(SOFTWARE_NAME), esc(DOC_TITLE), css, head, meta_table, scope, body)
    )
    out.write_text(doc, encoding="utf-8")


def header_footer(ver: str) -> tuple[str, str]:
    header = (
        '<div style="width:100%;font-size:9pt;font-family:SimSun,serif;color:#333;'
        'display:flex;justify-content:space-between;padding:0 2mm;">'
        f"<span>{SOFTWARE_NAME} {ver}</span><span>{DOC_TITLE}</span>"
        '<span>第 <span class="pageNumber"></span> 页 / 共 <span class="totalPages"></span> 页</span>'
        "</div>"
    )
    footer = (
        '<div style="width:100%;font-size:8pt;font-family:SimSun,serif;color:#666;text-align:center;">'
        f"{SOFTWARE_NAME} {ver} · {DOC_TITLE}</div>"
    )
    return header, footer


def page_lines(pdf: Path) -> list[int]:
    from pypdf import PdfReader

    reader = PdfReader(str(pdf))
    return [len([x for x in (p.extract_text() or "").splitlines() if x.strip()]) for p in reader.pages]


def main() -> None:
    ver = version()
    chapters = load_chapters()
    n_sec = sum(len(c.get("sections") or []) for c in chapters)
    print("chapters=%d sections=%d version=%s" % (len(chapters), n_sec, ver))

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    tmp = Path(tempfile.mkdtemp(prefix="ihomy-manual-"))
    html_path = tmp / "manual.html"
    render_html(chapters, ver, html_path)

    header, footer = header_footer(ver)
    options = {
        "paperWidth": 8.27,
        "paperHeight": 11.69,
        "marginTop": 0.71,
        "marginBottom": 0.55,
        "marginLeft": 0.59,
        "marginRight": 0.59,
        "displayHeaderFooter": True,
        "headerTemplate": header,
        "footerTemplate": footer,
        "printBackground": True,
        "preferCSSPageSize": True,
    }
    opt_path = tmp / "print.json"
    opt_path.write_text(json.dumps(options, ensure_ascii=False), encoding="utf-8")

    node = shutil.which("node")
    if not node:
        raise SystemExit("node not found")
    full = tmp / "full.pdf"
    subprocess.run(
        [node, str(ROOT / "scripts/soft-copyright/print_pdf.mjs"), str(html_path), str(full), str(opt_path)],
        check=True,
    )

    from pypdf import PdfReader, PdfWriter

    reader = PdfReader(str(full))
    total = len(reader.pages)
    counts = page_lines(full)
    print("full manual pages=%d lines/page min=%d max=%d" % (total, min(counts), max(counts)))
    if total < FRONT_PAGES:
        raise SystemExit("manual too short: %d pages" % total)

    if total > FRONT_PAGES + BACK_PAGES:
        picked = list(range(FRONT_PAGES)) + list(range(total - BACK_PAGES, total))
    else:
        picked = list(range(total))

    out = OUT_DIR / ("ihomy-%s-%s.pdf" % (DOC_TITLE, ver))
    writer = PdfWriter()
    for idx in picked:
        writer.add_page(reader.pages[idx])
    with out.open("wb") as fh:
        writer.write(fh)

    picked_counts = [counts[i] for i in picked]
    print("submitted pages=%d (first %d + last %d of %d)" % (len(picked), FRONT_PAGES, BACK_PAGES, total))

    bad = []
    for pos, (idx, n) in enumerate(zip(picked, picked_counts), 1):
        is_last = pos == len(picked)
        if n < MIN_LINES + 2 and not is_last:  # +2 keeps header and footer in the count
            bad.append((idx + 1, n))
    if bad:
        print("  [WARN] pages below %d body lines: %s" % (MIN_LINES, bad[:20]))
    else:
        print("  OK: every page except the last carries >= %d lines" % MIN_LINES)

    print("verify %s" % out)
    if len(PdfReader(str(out)).pages) != len(picked):
        raise SystemExit("page count mismatch after extraction")
    print("OK: %d pages" % len(picked))


if __name__ == "__main__":
    main()
