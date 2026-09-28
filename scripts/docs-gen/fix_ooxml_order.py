# -*- coding: utf-8 -*-
"""Normalise OOXML property-element order inside a .docx.

Word enforces the schema's child order and offers to "repair" a document that
violates it, while LibreOffice silently tolerates it. Two sources can break the
order here: our own property writers, and the skill's add_toc_placeholders.py
(it writes <w:ind> before <w:tabs>/<w:spacing> in every TOC entry).

This pass reorders the children of every property container into schema order and
removes duplicate single-occurrence children, in place. It is idempotent.

Usage: python fix_ooxml_order.py <file.docx> [more.docx ...]
"""
import shutil
import sys
import tempfile
import zipfile
from pathlib import Path

from lxml import etree

W = "{http://schemas.openxmlformats.org/wordprocessingml/2006/main}"

# ECMA-376 child order for the property containers we emit
ORDERS = {
    "pPr": ["pStyle", "keepNext", "keepLines", "pageBreakBefore", "framePr",
            "widowControl", "numPr", "suppressLineNumbers", "pBdr", "shd", "tabs",
            "suppressAutoHyphens", "kinsoku", "wordWrap", "overflowPunct",
            "topLinePunct", "autoSpaceDE", "autoSpaceDN", "bidi", "adjustRightInd",
            "snapToGrid", "spacing", "ind", "contextualSpacing", "mirrorIndents",
            "suppressOverlap", "jc", "textDirection", "textAlignment",
            "textboxTightWrap", "outlineLvl", "divId", "cnfStyle", "rPr", "sectPr",
            "pPrChange"],
    "rPr": ["rStyle", "rFonts", "b", "bCs", "i", "iCs", "caps", "smallCaps",
            "strike", "dstrike", "outline", "shadow", "emboss", "imprint",
            "noProof", "snapToGrid", "vanish", "webHidden", "color", "spacing",
            "w", "kern", "position", "sz", "szCs", "highlight", "u", "effect",
            "bdr", "shd", "fitText", "vertAlign", "rtl", "cs", "em", "lang",
            "eastAsianLayout", "specVanish", "oMath", "rPrChange"],
    "tblPr": ["tblStyle", "tblpPr", "tblOverlap", "bidiVisual",
              "tblStyleRowBandSize", "tblStyleColBandSize", "tblW", "jc",
              "tblCellSpacing", "tblInd", "tblBorders", "shd", "tblLayout",
              "tblCellMar", "tblLook", "tblCaption", "tblDescription",
              "tblPrChange"],
    "tcPr": ["cnfStyle", "tcW", "gridSpan", "hMerge", "vMerge", "tcBorders",
             "shd", "noWrap", "tcMar", "textDirection", "tcFitText", "vAlign",
             "hideMark", "cellIns", "cellDel", "cellMerge", "tcPrChange"],
    "trPr": ["cnfStyle", "divId", "gridBefore", "gridAfter", "wBefore", "wAfter",
             "cantSplit", "trHeight", "tblHeader", "tblCellSpacing", "jc",
             "hidden", "ins", "del", "trPrChange"],
    "sectPr": ["footnotePr", "endnotePr", "type", "pgSz", "pgMar", "paperSrc",
               "pgBorders", "lnNumType", "pgNumType", "cols", "formProt",
               "vAlign", "noEndnote", "titlePg", "textDirection", "bidi",
               "rtlGutter", "docGrid", "printerSettings", "sectPrChange"],
}


def normalise(root, order):
    """Reorder + dedupe children of every container named in ORDERS."""
    fixed = 0
    for kind, spec in ORDERS.items():
        for el in root.iter(W + kind):
            kids = list(el)
            if not kids:
                continue
            changed = False

            # drop duplicates of single-occurrence children, keeping the last
            seen_last = {}
            for c in kids:
                t = etree.QName(c).localname
                if t in spec:
                    seen_last[t] = c
            for c in kids:
                t = etree.QName(c).localname
                if t in spec and seen_last.get(t) is not c:
                    el.remove(c)
                    changed = True
            kids = list(el)

            # stable sort into schema order; unknown children stay next to their
            # predecessor so nothing meaningful is relocated
            keys, last = [], -1
            for c in kids:
                t = etree.QName(c).localname
                if t in spec:
                    last = spec.index(t)
                    keys.append(last)
                else:
                    keys.append(last + 0.5)
            pairs = sorted(zip(keys, range(len(kids)), kids),
                           key=lambda x: (x[0], x[1]))
            if [k for _, _, k in pairs] != kids:
                changed = True
                for _, _, c in pairs:
                    el.append(c)
            if changed:
                fixed += 1
    return fixed


def process(path):
    path = Path(path)
    with zipfile.ZipFile(path) as z:
        items = [(i, z.read(i.filename)) for i in z.infolist()]

    fixed_total = 0
    out = []
    for info, data in items:
        if info.filename.startswith("word/") and info.filename.endswith(".xml"):
            try:
                root = etree.fromstring(data)
            except etree.XMLSyntaxError:
                out.append((info, data))
                continue
            n = normalise(root, None)
            if n:
                fixed_total += n
                data = etree.tostring(root, xml_declaration=True,
                                      encoding="UTF-8", standalone=True)
        out.append((info, data))

    tmp = Path(tempfile.mkdtemp()) / path.name
    with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as z:
        for info, data in out:
            zi = zipfile.ZipInfo(info.filename, date_time=info.date_time)
            zi.compress_type = info.compress_type
            zi.external_attr = info.external_attr
            z.writestr(zi, data)
    shutil.move(str(tmp), str(path))
    return fixed_total


def main():
    rc = 0
    for arg in sys.argv[1:]:
        n = process(arg)
        print("%-28s normalised %d property container(s)" % (Path(arg).name, n))
    return rc


if __name__ == "__main__":
    sys.exit(main())
