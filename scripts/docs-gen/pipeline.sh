#!/usr/bin/env bash
# Regenerate docs/项目文档/*.docx, refresh the TOC fields, self-check, render and audit.
set -e
cd "$(dirname "$0")"
GEN="$(pwd)"
ROOT="$(cd ../.. && pwd)"
DOCS="$ROOT/docs/项目文档"

# docx skill helper scripts (add_toc_placeholders.py / postcheck.py)
S="${DOCX_SKILL_SCRIPTS:-$HOME/.zcode/cli/plugins/cache/zcode-plugins-official/documents/0.1.7/skills/docx/scripts}"
# LibreOffice + poppler, only needed for the render/audit step
SO="${SOFFICE:-/c/Users/chill/AppData/Local/Temp/lo-root/LibreOffice/program/soffice.exe}"
P="${POPPLER:-/d/Program Files/poppler-24.07.0/Library/bin}"
RENDER="${RENDER_DIR:-/c/Users/chill/AppData/Local/Temp/render}"

echo "===== parse sources ====="
python parse_schema.py
python parse_endpoints.py
python parse_dtos.py

echo "===== build documents ====="
python build_db_doc.py
python build_api_doc.py
python build_manual_doc.py

echo "===== inject TOC fields ====="
for f in "ihomy数据库结构文档" "ihomy接口文档" "ihomy用户手册"; do
  python "$S/add_toc_placeholders.py" "$DOCS/$f.docx" --auto 2>&1 | tail -1
done

echo "===== normalise OOXML element order (Word strictness) ====="
for f in "ihomy数据库结构文档" "ihomy接口文档" "ihomy用户手册"; do
  python fix_ooxml_order.py "$DOCS/$f.docx"
done
python validate_ooxml.py "$DOCS/ihomy数据库结构文档.docx" "$DOCS/ihomy接口文档.docx" "$DOCS/ihomy用户手册.docx"

echo "===== postcheck ====="
for f in "ihomy数据库结构文档" "ihomy接口文档" "ihomy用户手册"; do
  echo "--- $f ---"
  python "$S/postcheck.py" "$DOCS/$f.docx" 2>&1 | grep -E "Passed|❌|⚠️" | head -6
done

if [ ! -x "$SO" ] && [ ! -f "$SO" ]; then
  echo
  echo "LibreOffice not found at $SO — skipping render + pixel audit."
  echo "Install LibreOffice, then set SOFFICE=<path to soffice.exe> and re-run."
  exit 0
fi

echo "===== render ====="
taskkill //F //IM soffice.exe //IM soffice.bin >/dev/null 2>&1 || true
sleep 2
rm -rf "$RENDER"; mkdir -p "$RENDER/png"
cd "$DOCS"
"$SO" --headless --norestore \
  -env:UserInstallation=file:///C:/Users/chill/AppData/Local/Temp/lo-profile \
  --convert-to pdf --outdir "C:\Users\chill\AppData\Local\Temp\render" \
  "ihomy数据库结构文档.docx" "ihomy接口文档.docx" "ihomy用户手册.docx" >/dev/null 2>&1

cd "$RENDER"
for f in "ihomy数据库结构文档" "ihomy接口文档" "ihomy用户手册"; do
  "$P/pdftotext.exe" -layout "$f.pdf" "$f.txt"
  echo "$f: $("$P/pdfinfo.exe" "$f.pdf" | grep -i '^Pages' | tr -s ' ')"
done

# sampled pages for the pixel audit (cover / TOC / body / table-heavy)
render() {
  f="$1"; shift
  for pg in "$@"; do
    "$P/pdftoppm.exe" -png -r 100 -f "$pg" -l "$pg" "$f.pdf" "png/${f}-$(printf %03d "$pg")"
  done
}
render "ihomy数据库结构文档" 1 2 5 20 60
render "ihomy接口文档" 1 2 6 45 70
render "ihomy用户手册" 1 2 4 30 75

echo "===== pixel audit (sampled pages) ====="
cd "$GEN"
python visual_audit.py

echo "===== full sweep (every page + TOC page numbers) ====="
python sweep_audit.py
