# -*- coding: utf-8 -*-
"""Parse dto/*.java (records and POJOs) into field metadata."""
import json
import re
from pathlib import Path

ROOT = Path(r"C:\Users\chill\OneDrive\WorkStation\Projects\ihomy")
DTO = ROOT / "backend" / "src" / "main" / "java" / "com" / "ihomy" / "dto"
OUT = Path(__file__).parent / "dtos.json"

FIELD_RE = re.compile(
    r"^\s*(?:@[\w.]+(?:\([^)]*\))?\s*)*(?:private|protected|public)\s+"
    r"(?:final\s+)?([\w.<>\[\],\s]+?)\s+(\w+)\s*(?:=[^;]*)?;",
    re.M,
)


def split_record_params(body):
    parts, cur, depth = [], [], 0
    for ch in body:
        if ch == "<" or ch == "(":
            depth += 1
            cur.append(ch)
        elif ch == ">" or ch == ")":
            depth -= 1
            cur.append(ch)
        elif ch == "," and depth == 0:
            parts.append("".join(cur))
            cur = []
        else:
            cur.append(ch)
    if cur:
        parts.append("".join(cur))
    return [p.strip() for p in parts if p.strip()]


def strip_anns(s):
    return re.sub(r"@[\w.]+(?:\s*\((?:[^()]|\([^()]*\))*\))?", " ", s).strip()


def main():
    dtos = []
    for f in sorted(DTO.glob("*.java")):
        text = f.read_text(encoding="utf-8")
        name = f.stem
        fields = []

        rec = re.search(r"public\s+record\s+%s\s*\(([^)]*(?:\([^)]*\)[^)]*)*)\)" % name, text, re.S)
        if rec:
            for p in split_record_params(rec.group(1)):
                p = strip_anns(p)
                p = re.sub(r"^final\s+", "", p)
                m = re.match(r"^([\w.<>\[\],\s]+?)\s+(\w+)$", p)
                if m:
                    fields.append({"type": m.group(1).strip(), "name": m.group(2)})
            kind = "record"
        else:
            body_m = re.search(r"class\s+%s[^{]*\{" % name, text)
            body = text[body_m.end():] if body_m else text
            # cut at the closing brace of the class (approximate: last '}')
            for m in FIELD_RE.finditer(body):
                ftype = " ".join(m.group(1).split())
                if "static" in ftype:
                    continue
                fields.append({"type": ftype, "name": m.group(2)})
            kind = "class"

        # javadoc / line comment per field
        for fl in fields:
            cm = re.search(r"//\s*(.+)$", text, re.M)
        if fields:
            dtos.append({"name": name, "kind": kind, "fields": fields})

    OUT.write_text(json.dumps({"dtos": dtos}, ensure_ascii=False, indent=1), encoding="utf-8")
    print("dtos:", len(dtos), "fields:", sum(len(d["fields"]) for d in dtos))
    empty = [d["name"] for d in dtos if not d["fields"]]
    if empty:
        print("no fields parsed:", empty)
    for d in dtos[:5]:
        print("  ", d["name"], d["kind"], [(x["name"], x["type"]) for x in d["fields"]][:4])


if __name__ == "__main__":
    main()
