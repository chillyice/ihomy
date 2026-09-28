# -*- coding: utf-8 -*-
"""Shared docx building toolkit.

Implements the document house rules:
  * A4 portrait, cover section with zero margins
  * cover recipe R1 (full-page coloured wrapper table, left-aligned text)
  * 3-zone page numbering: cover (hidden) -> front matter (roman) -> body (arabic)
  * Profile A fonts: SimHei headings / SimSun body / Times New Roman for latin
  * body line spacing 1.3, CJK first-line indent 2 chars on prose paragraphs
  * tables: percentage column widths, repeating header row, cantSplit, cell margins
"""
from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Pt, Twips

PAGE_W, PAGE_H = 11906, 16838
BODY_MARGIN = dict(top=1440, bottom=1440, left=1701, right=1417)
# An intermediate section's sectPr must live in a paragraph, so the cover always
# has a trailing empty paragraph after the wrapper table. Let the wrapper fill the
# whole page and collapse that paragraph (1 twip, 1 pt, shaded) as well: renderers
# do not paint paragraph shading on a section-break paragraph, so anything it
# occupied would show as a white strip at the foot of the cover.
COVER_H = PAGE_H
COVER_BREAK_TWIPS = 1

PALETTES = {
    "DS-1": {
        "bg": "0B1C2C",
        "accent": "529286",
        "cover": {"titleColor": "FFFFFF", "subtitleColor": "B0B8C0", "metaColor": "90989F", "footerColor": "687078"},
        "table": {"headerBg": "529286", "headerText": "FFFFFF", "innerLine": "BECFCC", "surface": "E8ECEB"},
    },
    "DM-1": {
        "bg": "162235",
        "accent": "37DCF2",
        "cover": {"titleColor": "FFFFFF", "subtitleColor": "B0B8C0", "metaColor": "90989F", "footerColor": "687078"},
        "table": {"headerBg": "1B6B7A", "headerText": "FFFFFF", "innerLine": "C8DDE2", "surface": "EDF3F5"},
    },
    "GO-1": {
        "bg": "1A2330",
        "accent": "D4875A",
        "cover": {"titleColor": "FFFFFF", "subtitleColor": "B0B8C0", "metaColor": "90989F", "footerColor": "687078"},
        "table": {"headerBg": "D4875A", "headerText": "FFFFFF", "innerLine": "DDD0C8", "surface": "F8F0EB"},
    },
    "WM-1": {
        "bg": "F4F1E9",
        "accent": "FF6A3B",
        "cover": {"titleColor": "15857A", "subtitleColor": "606060", "metaColor": "707070", "footerColor": "A0A0A0"},
        "table": {"headerBg": "15857A", "headerText": "FFFFFF", "innerLine": "D5D0C8", "surface": "F0EDE5"},
    },
}

PPR_ORDER = [
    "w:pStyle", "w:keepNext", "w:keepLines", "w:pageBreakBefore", "w:framePr",
    "w:widowControl", "w:numPr", "w:suppressLineNumbers", "w:pBdr", "w:shd",
    "w:tabs", "w:suppressAutoHyphens", "w:kinsoku", "w:wordWrap",
    "w:overflowPunct", "w:topLinePunct", "w:autoSpaceDE", "w:autoSpaceDN",
    "w:bidi", "w:adjustRightInd", "w:snapToGrid", "w:spacing", "w:ind",
    "w:contextualSpacing", "w:mirrorIndents", "w:suppressOverlap", "w:jc",
    "w:textDirection", "w:textAlignment", "w:textboxTightWrap", "w:outlineLvl",
    "w:divId", "w:cnfStyle", "w:rPr", "w:sectPr", "w:pPrChange",
]
TRPR_ORDER = [
    "w:cnfStyle", "w:divId", "w:gridBefore", "w:gridAfter", "w:wBefore",
    "w:wAfter", "w:cantSplit", "w:trHeight", "w:tblHeader", "w:tblCellSpacing",
    "w:jc", "w:hidden",
]
TCPR_ORDER = [
    "w:cnfStyle", "w:tcW", "w:gridSpan", "w:hMerge", "w:vMerge", "w:tcBorders",
    "w:shd", "w:noWrap", "w:tcMar", "w:textDirection", "w:tcFitText",
    "w:vAlign", "w:hideMark",
]
SECTPR_ORDER = [
    "w:footnotePr", "w:endnotePr", "w:type", "w:pgSz", "w:pgMar", "w:paperSrc",
    "w:pgBorders", "w:lnNumType", "w:pgNumType", "w:cols", "w:formProt",
    "w:vAlign", "w:noEndnote", "w:titlePg", "w:textDirection", "w:bidi",
    "w:rtlGutter", "w:docGrid", "w:printerSettings", "w:sectPrChange",
]
TBLPR_ORDER = [
    "w:tblStyle", "w:tblpPr", "w:tblOverlap", "w:bidiVisual",
    "w:tblStyleRowBandSize", "w:tblStyleColBandSize", "w:tblW", "w:jc",
    "w:tblCellSpacing", "w:tblInd", "w:tblBorders", "w:shd", "w:tblLayout",
    "w:tblCellMar", "w:tblLook", "w:tblCaption", "w:tblDescription",
    "w:tblPrChange",
]
RPR_ORDER = [
    "w:rStyle", "w:rFonts", "w:b", "w:bCs", "w:i", "w:iCs", "w:caps",
    "w:smallCaps", "w:strike", "w:dstrike", "w:outline", "w:shadow", "w:emboss",
    "w:imprint", "w:noProof", "w:snapToGrid", "w:vanish", "w:webHidden",
    "w:color", "w:spacing", "w:w", "w:kern", "w:position", "w:sz", "w:szCs",
    "w:highlight", "w:u", "w:effect", "w:bdr", "w:shd", "w:fitText",
    "w:vertAlign", "w:rtl", "w:cs", "w:em", "w:lang", "w:eastAsianLayout",
    "w:specVanish", "w:oMath", "w:rPrChange",
]


def _insert_ordered(parent, element, order):
    tag = element.tag.split("}")[-1]
    idx = order.index("w:" + tag) if ("w:" + tag) in order else len(order)
    for child in parent:
        ctag = "w:" + child.tag.split("}")[-1]
        cidx = order.index(ctag) if ctag in order else len(order)
        if cidx > idx:
            child.addprevious(element)
            return element
    parent.append(element)
    return element


def _el(tag, **attrs):
    e = OxmlElement(tag)
    for k, v in attrs.items():
        e.set(qn("w:" + k), str(v))
    return e


def set_run(run, size, bold=False, color=None, east="SimSun", latin="Times New Roman", italic=False):
    run.font.size = Pt(size)
    run.font.bold = bold
    run.font.italic = italic
    if color:
        run.font.color.rgb = __import__("docx").shared.RGBColor.from_string(color)
    rPr = run._r.get_or_add_rPr()
    rFonts = rPr.find(qn("w:rFonts"))
    if rFonts is None:
        rFonts = OxmlElement("w:rFonts")
        rPr.insert(0, rFonts)
    rFonts.set(qn("w:ascii"), latin)
    rFonts.set(qn("w:hAnsi"), latin)
    rFonts.set(qn("w:cs"), latin)
    rFonts.set(qn("w:eastAsia"), east)
    # CJK complex-script size (must follow w:sz, so insert in schema order)
    szCs = rPr.find(qn("w:szCs"))
    if szCs is None:
        szCs = _insert_ordered(rPr, OxmlElement("w:szCs"), RPR_ORDER)
    szCs.set(qn("w:val"), str(int(size * 2)))
    return run


def set_para_border(paragraph, edge, color, sz=6, space=8):
    pPr = paragraph._p.get_or_add_pPr()
    pBdr = pPr.find(qn("w:pBdr"))
    if pBdr is None:
        pBdr = _insert_ordered(pPr, OxmlElement("w:pBdr"), PPR_ORDER)
    for e in pBdr.findall(qn("w:" + edge)):
        pBdr.remove(e)
    el = _el("w:" + edge, val="single", sz=sz, space=space, color=color)
    # w:pBdr order is top,left,bottom,right,between,bar
    order = ["top", "left", "bottom", "right", "between", "bar"]
    idx = order.index(edge)
    for child in pBdr:
        ct = child.tag.split("}")[-1]
        if order.index(ct) > idx:
            child.addprevious(el)
            return
    pBdr.append(el)


def shade(element, fill):
    """Shade a paragraph or table cell."""
    if element.tag.endswith("}tc"):
        parent = element.find(qn("w:tcPr"))
        if parent is None:
            parent = _insert_ordered(element, OxmlElement("w:tcPr"), ["w:tcPr"])
        holder = parent
        order = TCPR_ORDER
    else:
        parent = element.get_or_add_pPr()
        holder = parent
        order = PPR_ORDER
    for e in holder.findall(qn("w:shd")):
        holder.remove(e)
    shd = _el("w:shd", val="clear", color="auto", fill=fill)
    _insert_ordered(holder, shd, order)


def set_cell_width_pct(cell, pct):
    tcPr = cell._tc.get_or_add_tcPr()
    for e in tcPr.findall(qn("w:tcW")):
        tcPr.remove(e)
    _insert_ordered(tcPr, _el("w:tcW", w=int(pct * 50), type="pct"), TCPR_ORDER)


def set_cell_margins(table, top=50, bottom=50, start=90, end=90):
    tblPr = table._tbl.tblPr
    for e in tblPr.findall(qn("w:tblCellMar")):
        tblPr.remove(e)
    mar = OxmlElement("w:tblCellMar")
    for tag, val in (("w:top", top), ("w:start", start), ("w:bottom", bottom), ("w:end", end)):
        mar.append(_el(tag, w=val, type="dxa"))
    _insert_ordered(tblPr, mar, TBLPR_ORDER)


def set_table_width_pct(table, pct=5000):
    """Replace the table's own tblW — a second tblW element would be invalid."""
    tblPr = table._tbl.tblPr
    for e in tblPr.findall(qn("w:tblW")):
        tblPr.remove(e)
    _insert_ordered(tblPr, _el("w:tblW", w=pct, type="pct"), TBLPR_ORDER)


def table_borders(table, inner_line, header_bg=None):
    tblPr = table._tbl.tblPr
    for e in tblPr.findall(qn("w:tblBorders")):
        tblPr.remove(e)
    b = OxmlElement("w:tblBorders")
    b.append(_el("w:top", val="single", sz=6, space=0, color=inner_line))
    b.append(_el("w:left", val="none", sz=0, space=0, color="auto"))
    b.append(_el("w:bottom", val="single", sz=6, space=0, color=inner_line))
    b.append(_el("w:right", val="none", sz=0, space=0, color="auto"))
    b.append(_el("w:insideH", val="single", sz=4, space=0, color=inner_line))
    b.append(_el("w:insideV", val="none", sz=0, space=0, color="auto"))
    _insert_ordered(tblPr, b, TBLPR_ORDER)


def set_row_cant_split(row):
    trPr = row._tr.get_or_add_trPr()
    if trPr.find(qn("w:cantSplit")) is None:
        _insert_ordered(trPr, OxmlElement("w:cantSplit"), TRPR_ORDER)


def set_row_height(row, twips, rule="exact"):
    trPr = row._tr.get_or_add_trPr()
    for e in trPr.findall(qn("w:trHeight")):
        trPr.remove(e)
    _insert_ordered(trPr, _el("w:trHeight", val=twips, hRule=rule), TRPR_ORDER)


def set_header_row(row):
    trPr = row._tr.get_or_add_trPr()
    if trPr.find(qn("w:tblHeader")) is None:
        _insert_ordered(trPr, OxmlElement("w:tblHeader"), TRPR_ORDER)


def no_borders(table):
    tblPr = table._tbl.tblPr
    for e in tblPr.findall(qn("w:tblBorders")):
        tblPr.remove(e)
    b = OxmlElement("w:tblBorders")
    for tag in ("top", "left", "bottom", "right", "insideH", "insideV"):
        b.append(_el("w:" + tag, val="none", sz=0, space=0, color="auto"))
    _insert_ordered(tblPr, b, TBLPR_ORDER)


def fixed_layout(table):
    tblPr = table._tbl.tblPr
    for e in tblPr.findall(qn("w:tblLayout")):
        tblPr.remove(e)
    _insert_ordered(tblPr, _el("w:tblLayout", type="fixed"), TBLPR_ORDER)


def set_page_numbering(section, start=None, fmt=None):
    sectPr = section._sectPr
    for e in sectPr.findall(qn("w:pgNumType")):
        sectPr.remove(e)
    if start is None and fmt is None:
        return
    el = OxmlElement("w:pgNumType")
    if start is not None:
        el.set(qn("w:start"), str(start))
    if fmt is not None:
        el.set(qn("w:fmt"), fmt)
    _insert_ordered(sectPr, el, SECTPR_ORDER)


def add_page_field(paragraph, switch="arabic"):
    """Insert a PAGE field (no total-pages denominator)."""
    def make_run():
        r = OxmlElement("w:r")
        rPr = OxmlElement("w:rPr")
        rFonts = _el("w:rFonts", ascii="Times New Roman", hAnsi="Times New Roman", eastAsia="SimSun")
        rPr.append(rFonts)
        rPr.append(_el("w:sz", val="18"))
        rPr.append(_el("w:szCs", val="18"))
        rPr.append(_el("w:color", val="808080"))
        r.append(rPr)
        return r

    r1 = make_run()
    f1 = _el("w:fldChar", fldCharType="begin")
    r1.append(f1)
    instr = OxmlElement("w:instrText")
    instr.set(qn("xml:space"), "preserve")
    instr.text = " PAGE \\* %s \\* MERGEFORMAT " % switch
    r1.append(instr)
    r2 = make_run()
    r2.append(_el("w:fldChar", fldCharType="separate"))
    t = OxmlElement("w:t")
    t.text = "1"
    r2.append(t)
    r3 = make_run()
    r3.append(_el("w:fldChar", fldCharType="end"))
    p = paragraph._p
    for r in (r1, r2, r3):
        p.append(r)


def toc_field(paragraph, levels="1-2"):
    def make_run():
        r = OxmlElement("w:r")
        return r

    r1 = make_run()
    r1.append(_el("w:fldChar", fldCharType="begin"))
    instr = OxmlElement("w:instrText")
    instr.set(qn("xml:space"), "preserve")
    instr.text = ' TOC \\o "%s" \\h \\z \\u ' % levels
    r1.append(instr)
    r2 = make_run()
    r2.append(_el("w:fldChar", fldCharType="separate"))
    t = OxmlElement("w:t")
    t.text = "请在 Word 中右键目录并选择「更新域」以生成目录与页码。"
    r2.append(t)
    r3 = make_run()
    r3.append(_el("w:fldChar", fldCharType="end"))
    p = paragraph._p
    for r in (r1, r2, r3):
        p.append(r)


class DocBuilder:
    def __init__(self, palette_key):
        self.doc = Document()
        self.pal = PALETTES[palette_key]
        self._setup_styles()
        self._toc_levels = "1-2"

    # ---------- styles ----------
    def _setup_styles(self):
        st = self.doc.styles

        normal = st["Normal"]
        normal.font.size = Pt(12)
        normal.font.name = "Times New Roman"
        rPr = normal.element.get_or_add_rPr()
        rFonts = rPr.find(qn("w:rFonts"))
        if rFonts is None:
            rFonts = OxmlElement("w:rFonts")
            rPr.insert(0, rFonts)
        rFonts.set(qn("w:ascii"), "Times New Roman")
        rFonts.set(qn("w:hAnsi"), "Times New Roman")
        rFonts.set(qn("w:eastAsia"), "SimSun")
        pPr = normal.element.get_or_add_pPr()
        for e in pPr.findall(qn("w:spacing")):
            pPr.remove(e)
        _insert_ordered(pPr, _el("w:spacing", line=312, lineRule="auto", after=120), PPR_ORDER)
        for e in pPr.findall(qn("w:jc")):
            pPr.remove(e)
        _insert_ordered(pPr, _el("w:jc", val="both"), PPR_ORDER)

        specs = [("Heading 1", 16, 360, 200), ("Heading 2", 15, 300, 150), ("Heading 3", 14, 240, 120)]
        for name, size, before, after in specs:
            s = st[name]
            s.font.size = Pt(size)
            s.font.bold = True
            s.font.color.rgb = __import__("docx").shared.RGBColor.from_string("000000")
            s.font.name = "SimHei"
            rPr = s.element.get_or_add_rPr()
            rFonts = rPr.find(qn("w:rFonts"))
            if rFonts is None:
                rFonts = OxmlElement("w:rFonts")
                rPr.insert(0, rFonts)
            rFonts.set(qn("w:ascii"), "Times New Roman")
            rFonts.set(qn("w:hAnsi"), "Times New Roman")
            rFonts.set(qn("w:eastAsia"), "SimHei")
            pPr = s.element.get_or_add_pPr()
            for tag in ("w:spacing", "w:ind", "w:keepNext", "w:outlineLvl"):
                for e in pPr.findall(qn(tag)):
                    pPr.remove(e)
            _insert_ordered(pPr, OxmlElement("w:keepNext"), PPR_ORDER)
            _insert_ordered(
                pPr,
                _el("w:spacing", before=before, after=after, line=int(size * 23), lineRule="atLeast"),
                PPR_ORDER,
            )
            _insert_ordered(pPr, _el("w:ind", firstLine=0, left=0), PPR_ORDER)
            lvl = int(name[-1]) - 1
            _insert_ordered(pPr, _el("w:outlineLvl", val=lvl), PPR_ORDER)

    # ---------- cover ----------
    def build_cover(self, title, subtitle, english_label, meta_lines, footer_left, footer_right, title_pt):
        doc = self.doc
        sec = doc.sections[0]
        sec.page_width, sec.page_height = Twips(PAGE_W), Twips(PAGE_H)
        sec.top_margin = sec.bottom_margin = sec.left_margin = sec.right_margin = 0
        set_page_numbering(sec)

        P = self.pal
        pad_l, pad_r = 1200, 800

        tbl = doc.add_table(rows=1, cols=1)
        tbl.alignment = WD_TABLE_ALIGNMENT.LEFT
        no_borders(tbl)
        fixed_layout(tbl)
        set_table_width_pct(tbl)
        row = tbl.rows[0]
        set_row_height(row, COVER_H, "exact")
        cell = row.cells[0]
        set_cell_width_pct(cell, 100)
        set_cell_margins(tbl, top=0, bottom=0, start=0, end=0)
        shade(cell._tc, P["bg"])

        cell.paragraphs[0]._p.getparent().remove(cell.paragraphs[0]._p)
        kids = []

        # vertical budget
        title_lines = self._split_title(title, title_pt, PAGE_W - pad_l - pad_r - 300)
        fixed = 400 + (len(meta_lines) * 300 if meta_lines else 0)
        content_h = (
            len(title_lines) * (title_pt * 23 + 200)
            + (12 * 23 + 600 if subtitle else 0)
            + (9 * 23 + 600 if english_label else 0)
            + (len(meta_lines) * (10 * 23 + 100))
            + fixed
            + 3 * 300
        )
        usable = COVER_H - 1200
        remaining = max(usable - content_h, 400)
        top_sp = max(int(remaining * 0.45), 400)
        bot_sp = max(int(remaining * 0.45), 800)

        def newp():
            p = OxmlElement("w:p")
            cell._tc.append(p)
            from docx.text.paragraph import Paragraph
            return Paragraph(p, cell)

        newp()
        self._cover_set_spacing(cell, top_sp)

        if english_label:
            p = newp()
            self._style_cover_para(p, indent_left=pad_l)
            self._cover_set_spacing(cell, 0, after=500)
            run = p.add_run(english_label)
            set_run(run, 9, color=P["accent"], east="SimHei", latin="Calibri")
            rPr = run._r.get_or_add_rPr()
            sp = rPr.find(qn("w:spacing"))
            if sp is None:
                sp = OxmlElement("w:spacing")
                rPr.append(sp)
            sp.set(qn("w:val"), "40")
            set_para_border(p, "bottom", P["accent"], sz=6, space=8)

        for i, line in enumerate(title_lines):
            p = newp()
            self._style_cover_para(p, indent_left=pad_l)
            self._cover_set_spacing(
                cell, 0, after=(100 if i < len(title_lines) - 1 else 300), line=int(title_pt * 23)
            )
            run = p.add_run(line)
            set_run(run, title_pt, bold=True, color=P["cover"]["titleColor"], east="SimHei", latin="Arial")

        if subtitle:
            p = newp()
            self._style_cover_para(p, indent_left=pad_l)
            self._cover_set_spacing(cell, 0, after=800)
            run = p.add_run(subtitle)
            set_run(run, 12, color=P["cover"]["subtitleColor"], east="Microsoft YaHei", latin="Arial")

        for line in meta_lines or []:
            p = newp()
            self._style_cover_para(p, indent_left=pad_l + 200)
            self._cover_set_spacing(cell, 0, after=80)
            run = p.add_run(line)
            set_run(run, 12, color=P["cover"]["metaColor"], east="Microsoft YaHei", latin="Arial")
            set_para_border(p, "left", P["accent"], sz=8, space=12)

        p = newp()
        self._style_cover_para(p)
        self._cover_set_spacing(cell, bot_sp)

        p = newp()
        self._style_cover_para(p, indent_left=pad_l, indent_right=pad_r)
        self._cover_set_spacing(cell, 200)
        run = p.add_run("%s                                        %s" % (footer_left or "", footer_right or ""))
        set_run(run, 8, color=P["cover"]["footerColor"], east="SimSun", latin="Arial")
        set_para_border(p, "top", P["accent"], sz=2, space=8)

    # helpers that operate on the most recently appended cover paragraph
    def _cover_set_spacing(self, cell, before=None, after=None, line=None):
        from docx.text.paragraph import Paragraph

        p = Paragraph(cell._tc.findall(qn("w:p"))[-1], cell)
        pPr = p._p.get_or_add_pPr()
        for e in pPr.findall(qn("w:spacing")):
            pPr.remove(e)
        attrs = {}
        if before is not None:
            attrs["before"] = before
        if after is not None:
            attrs["after"] = after
        if line is not None:
            attrs["line"] = line
            attrs["lineRule"] = "atLeast"
        _insert_ordered(pPr, _el("w:spacing", **attrs), PPR_ORDER)
        return p

    def _style_cover_para(self, p, indent_left=None, indent_right=None):
        pPr = p._p.get_or_add_pPr()
        if indent_left is not None or indent_right is not None:
            attrs = {}
            if indent_left is not None:
                attrs["left"] = indent_left
            if indent_right is not None:
                attrs["right"] = indent_right
            _insert_ordered(pPr, _el("w:ind", **attrs), PPR_ORDER)
        return p

    @staticmethod
    def _split_title(title, pt, avail):
        cpl = max(int(avail / (pt * 20)), 2)
        if len(title) <= cpl:
            return [title]
        break_after = set("，。、；：！？的与和及之在于为-_—–·/ \t")
        lines, rem = [], title
        while len(rem) > cpl:
            at = -1
            for i in range(cpl, max(int(cpl * 0.6), 1) - 1, -1):
                if i < len(rem) and rem[i - 1] in break_after:
                    at = i
                    break
            if at == -1:
                at = cpl
                if (
                    at < len(rem)
                    and rem[at - 1] not in break_after
                    and rem[at] not in break_after
                    and "\u4e00" <= rem[at - 1] <= "\u9fff"
                    and "\u4e00" <= rem[at] <= "\u9fff"
                ):
                    at -= 1
            lines.append(rem[:at].strip())
            rem = rem[at:].strip()
        if rem:
            lines.append(rem)
        if len(lines) > 1 and len(lines[-1]) <= 2:
            last = lines.pop()
            lines[-1] += last
        return lines[:3]

    # ---------- sections ----------
    def finish_cover_section(self):
        """Close the cover section; the break paragraph is collapsed to nothing."""
        sec = self.doc.add_section(WD_SECTION.NEW_PAGE)
        set_page_numbering(self.doc.sections[0])
        body = self.doc.element.body
        # the paragraph python-docx inserted to carry the previous sectPr
        paras = body.findall(qn("w:p"))
        if paras:
            p = paras[-1]
            pPr = p.get_or_add_pPr()
            for e in pPr.findall(qn("w:spacing")):
                pPr.remove(e)
            _insert_ordered(
                pPr,
                _el("w:spacing", line=COVER_BREAK_TWIPS, lineRule="exact", before=0, after=0),
                PPR_ORDER,
            )
            rPr = OxmlElement("w:rPr")
            rPr.append(_el("w:sz", val="2"))
            rPr.append(_el("w:szCs", val="2"))
            _insert_ordered(pPr, rPr, PPR_ORDER)
            shade(p, self.pal["bg"])
        return sec

    def setup_front_matter(self, sec, toc_title="目  录", toc_note=None):
        sec.page_width, sec.page_height = Twips(PAGE_W), Twips(PAGE_H)
        sec.top_margin, sec.bottom_margin = Twips(BODY_MARGIN["top"]), Twips(BODY_MARGIN["bottom"])
        sec.left_margin, sec.right_margin = Twips(BODY_MARGIN["left"]), Twips(BODY_MARGIN["right"])
        set_page_numbering(sec, start=1, fmt="upperRoman")
        sec.footer.is_linked_to_previous = False
        fp = sec.footer.paragraphs[0]
        fp.alignment = WD_ALIGN_PARAGRAPH.CENTER
        add_page_field(fp, "ROMAN")

        p = self.doc.add_paragraph()
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p.paragraph_format.space_before = Pt(24)
        p.paragraph_format.space_after = Pt(18)
        set_run(p.add_run(toc_title), 16, bold=True, east="SimHei")

        p = self.doc.add_paragraph()
        toc_field(p, self._toc_levels)

        p = self.doc.add_paragraph()
        p.paragraph_format.space_before = Pt(10)
        set_run(
            p.add_run(
                toc_note
                or "说明：目录由域代码生成。首次打开时若页码为空，请在目录上右键选择「更新域」并选「更新整个目录」。"
            ),
            9,
            italic=True,
            color="888888",
            east="SimSun",
        )

    def start_body(self):
        sec = self.doc.add_section(WD_SECTION.NEW_PAGE)
        sec.page_width, sec.page_height = Twips(PAGE_W), Twips(PAGE_H)
        sec.top_margin, sec.bottom_margin = Twips(BODY_MARGIN["top"]), Twips(BODY_MARGIN["bottom"])
        sec.left_margin, sec.right_margin = Twips(BODY_MARGIN["left"]), Twips(BODY_MARGIN["right"])
        set_page_numbering(sec, start=1, fmt="decimal")
        sec.footer.is_linked_to_previous = False
        fp = sec.footer.paragraphs[0]
        fp.alignment = WD_ALIGN_PARAGRAPH.CENTER
        add_page_field(fp, "arabic")
        return sec

    # ---------- content ----------
    def h1(self, text):
        return self.doc.add_heading(text, level=1)

    def h2(self, text):
        return self.doc.add_heading(text, level=2)

    def h3(self, text):
        return self.doc.add_heading(text, level=3)

    def _body_spacing(self, para, line=312, after=120, before=None):
        pPr = para._p.get_or_add_pPr()
        for e in pPr.findall(qn("w:spacing")):
            pPr.remove(e)
        attrs = {"line": line, "lineRule": "auto", "after": after}
        if before is not None:
            attrs["before"] = before
        _insert_ordered(pPr, _el("w:spacing", **attrs), PPR_ORDER)
        return para

    def p(self, text, indent=True, size=12, bold=False):
        para = self.doc.add_paragraph()
        self._body_spacing(para)
        if indent:
            pPr = para._p.get_or_add_pPr()
            _insert_ordered(pPr, _el("w:ind", firstLine=480, firstLineChars=200), PPR_ORDER)
        set_run(para.add_run(text), size, bold=bold)
        return para

    def steps(self, items):
        for i, s in enumerate(items, 1):
            para = self.doc.add_paragraph()
            self._body_spacing(para, after=60)
            pPr = para._p.get_or_add_pPr()
            _insert_ordered(pPr, _el("w:ind", left=480, hanging=240), PPR_ORDER)
            set_run(para.add_run("（%d）%s" % (i, s)), 12)
        if items:
            self._body_spacing(self.doc.paragraphs[-1], after=140)

    def notes(self, items, label="提示"):
        for s in items:
            para = self.doc.add_paragraph()
            self._body_spacing(para, after=60)
            pPr = para._p.get_or_add_pPr()
            _insert_ordered(pPr, _el("w:ind", left=480, hanging=240), PPR_ORDER)
            set_run(para.add_run("【%s】" % label), 12, bold=True, east="SimHei")
            set_run(para.add_run(s), 12)
        if items:
            self._body_spacing(self.doc.paragraphs[-1], after=140)

    def caption(self, text):
        para = self.doc.add_paragraph()
        para.alignment = WD_ALIGN_PARAGRAPH.CENTER
        para.paragraph_format.space_before = Pt(6)
        para.paragraph_format.space_after = Pt(3)
        para.paragraph_format.keep_with_next = True
        set_run(para.add_run(text), 10.5, bold=True, east="SimHei")
        return para

    def table(self, rows, widths, caption=None, header=True, size=10.5):
        if caption:
            self.caption(caption)
        ncols = len(widths)
        tbl = self.doc.add_table(rows=len(rows), cols=ncols)
        tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
        fixed_layout(tbl)
        no_borders(tbl)
        table_borders(tbl, self.pal["table"]["innerLine"])
        set_cell_margins(tbl)
        set_table_width_pct(tbl)
        for r_i, row_data in enumerate(rows):
            row = tbl.rows[r_i]
            set_row_cant_split(row)
            if header and r_i == 0:
                set_header_row(row)
            for c_i in range(ncols):
                cell = row.cells[c_i]
                set_cell_width_pct(cell, widths[c_i])
                text = str(row_data[c_i]) if c_i < len(row_data) else ""
                para = cell.paragraphs[0]
                para.paragraph_format.space_before = Pt(1)
                para.paragraph_format.space_after = Pt(1)
                para.paragraph_format.line_spacing = 1.3
                first = True
                for seg in text.split("\n"):
                    if not first:
                        para = cell.add_paragraph()
                        para.paragraph_format.space_before = Pt(1)
                        para.paragraph_format.space_after = Pt(1)
                        para.paragraph_format.line_spacing = 1.3
                    first = False
                    if header and r_i == 0:
                        set_run(para.add_run(seg), size, bold=True, color=self.pal["table"]["headerText"], east="SimHei")
                    else:
                        set_run(para.add_run(seg), size)
                if header and r_i == 0:
                    shade(cell._tc, self.pal["table"]["headerBg"])
                elif r_i % 2 == 0:
                    shade(cell._tc, self.pal["table"]["surface"])
        if rows:
            self.doc.add_paragraph().paragraph_format.space_after = Pt(0)
        return tbl

    def kv_block(self, pairs, caption=None, key_w=24, size=10.5):
        rows = [["项目", "说明"]] + [[k, v] for k, v in pairs]
        return self.table(rows, [key_w, 100 - key_w], caption=caption, size=size)

    def page_break(self):
        self.doc.add_page_break()

    def save(self, path):
        self.doc.save(str(path))
