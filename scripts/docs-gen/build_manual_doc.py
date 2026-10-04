# -*- coding: utf-8 -*-
"""Build docs/项目文档/ihomy用户手册.docx from the generated content parts."""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from docx_kit import DocBuilder, app_version

ROOT = Path(r"C:\Users\chill\OneDrive\WorkStation\Projects\ihomy")
OUTDIR = ROOT / "docs" / "项目文档"
PARTS = [Path(__file__).parent / ("manual_part%d.json" % i) for i in (1, 2, 3)]


def load():
    chapters = []
    for p in PARTS:
        data = json.loads(p.read_text(encoding="utf-8"))
        chapters.extend(data["chapters"])
    chapters.sort(key=lambda c: int(c["number"]))
    return chapters


def validate(chapters):
    problems = []
    seen = set()
    for c in chapters:
        if c["number"] in seen:
            problems.append("duplicate chapter %s" % c["number"])
        seen.add(c["number"])
        for s in c["sections"]:
            for t in s.get("tables", []) or []:
                w = t.get("widths") or []
                if sum(w) != 100:
                    problems.append("%s widths sum %s != 100" % (s["number"], sum(w)))
                rows = t.get("rows") or []
                if not rows:
                    problems.append("%s empty table" % s["number"])
                    continue
                n = len(rows[0])
                for r in rows:
                    if len(r) != n:
                        problems.append("%s ragged row" % s["number"])
                        break
                if len(w) != n:
                    problems.append("%s width count %d != cols %d" % (s["number"], len(w), n))
    return problems


def main():
    chapters = load()
    problems = validate(chapters)
    if problems:
        print("VALIDATION PROBLEMS:")
        for p in problems:
            print("  -", p)
        raise SystemExit(1)

    n_sec = sum(len(c["sections"]) for c in chapters)
    n_tab = sum(len(s.get("tables") or []) for c in chapters for s in c["sections"])
    n_step = sum(len(s.get("steps") or []) for c in chapters for s in c["sections"])
    print("chapters=%d sections=%d tables=%d steps=%d" % (len(chapters), n_sec, n_tab, n_step))

    b = DocBuilder("WM-1")
    b.build_cover(
        title="ihomy 用户手册",
        subtitle="家庭共用软件 · 功能使用指南",
        english_label="USER MANUAL",
        meta_lines=[
            "适用版本：%s" % app_version(),
            "适用对象：家长、成员、孩童、访客与运维管理员",
            "覆盖范围：%d 章 %d 节，含操作步骤与使用提示" % (len(chapters), n_sec),
            "运行环境：电脑浏览器、安卓与 iOS 手机、平板",
        ],
        footer_left="ihomy 项目文档",
        footer_right="用户手册",
        title_pt=40,
    )
    b.finish_cover_section()
    b.setup_front_matter(b.doc.sections[1])
    b.start_body()

    b.h1("阅读说明")
    b.p("本手册面向 ihomy 的使用者，按功能模块介绍每个功能能做什么、怎么操作以及需要注意的地方。手册按从入门到进阶的顺序编排：前三章介绍账号、家庭与内容创作，这是每天都会用到的部分；后续章节按业务域分组，可以按需查阅。")
    b.p("阅读时请注意以下几点。手册中的操作步骤按界面上的实际按钮名称描述，步骤序号只表示先后顺序，不代表界面上的编号。功能是否可见取决于当前账号的角色与所在家庭，例如只有家长可以调整成员角色、上架积分商品与配置家庭封面。凡是涉及家庭内部数据的功能，看到的都只是自己所在家庭的内容。")
    b.p("每个章节的结构一致：先说明功能用途，再给出字段或选项说明，最后是需要留意的限制。遇到问题可以直接查阅最后一章的常见问题。")

    for c in chapters:
        b.h1("第%s章 %s" % (c["number"], c["title"]))
        if c.get("intro"):
            b.p(c["intro"])
        for s in c["sections"]:
            b.h2("%s %s" % (s["number"], s["title"]))
            if s.get("purpose"):
                b.p(s["purpose"], indent=False)
            for para in s.get("paragraphs") or []:
                b.p(para)
            for t in s.get("tables") or []:
                b.table(t["rows"], t["widths"], caption=t.get("caption"), size=10)
            if s.get("steps"):
                b.p("操作步骤：", indent=False, bold=True, size=12)
                b.steps(s["steps"])
            if s.get("notes"):
                b.notes(s["notes"])

    OUTDIR.mkdir(parents=True, exist_ok=True)
    out = OUTDIR / "ihomy用户手册.docx"
    b.save(out)
    print("written:", out, out.stat().st_size, "bytes")


if __name__ == "__main__":
    main()
