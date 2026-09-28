# -*- coding: utf-8 -*-
"""Validate OOXML child-element ordering.

Word enforces the schema's element order strictly and will offer to "repair" a
document whose properties are out of order; LibreOffice is lenient, so a clean
PDF render does not prove Word compatibility. Check every properties element
against the order ECMA-376 defines.
"""
import sys
import zipfile
from pathlib import Path

from lxml import etree

W = "{http://schemas.openxmlformats.org/wordprocessingml/2006/main}"

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


def check(path):
    problems = []
    with zipfile.ZipFile(path) as z:
        parts = [n for n in z.namelist() if n.endswith(".xml") and n.startswith("word/")]
        for part in parts:
            root = etree.fromstring(z.read(part))
            for kind, order in ORDERS.items():
                for el in root.iter(W + kind):
                    seq = []
                    for child in el:
                        tag = etree.QName(child).localname
                        seq.append(tag)
                    idx = []
                    for t in seq:
                        if t in order:
                            idx.append(order.index(t))
                        else:
                            idx.append(-1)  # unknown: ignore
                    known = [i for i in idx if i >= 0]
                    if known != sorted(known):
                        problems.append((part, kind, seq))
    return problems


def main():
    total = 0
    for arg in sys.argv[1:]:
        p = Path(arg)
        probs = check(p)
        total += len(probs)
        print("%-26s %s" % (p.name, "OK — element order valid" if not probs else "OUT OF ORDER %d" % len(probs)))
        for part, kind, seq in probs[:5]:
            print("    %s <%s>: %s" % (part, kind, seq))
    print()
    print("out-of-order property elements: %d" % total)
    return 1 if total else 0


if __name__ == "__main__":
    sys.exit(main())
