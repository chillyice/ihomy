#!/usr/bin/env bash
# doc-index.sh — 从 docs/变更归档.md 生成 docs/归档索引.md(小节索引 + 文件反查)
#
# 为什么不手写:归档顶部的手写目录已至少 3 次漏登/滞后(209/161/225 三个数字互不相符)。
# 索引一律生成:正文新增 `#####` 小节后重跑本脚本即可;--check 在 CI 拦住漂移。
#
# 用法:
#   bash scripts/doc-index.sh          # 写入 docs/归档索引.md
#   bash scripts/doc-index.sh --check  # 只比对,有漂移退出码 1
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

SRC="docs/变更归档.md"
OUT="docs/归档索引.md"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

[[ -f "$SRC" ]] || { echo "[FAIL] 缺 $SRC"; exit 1; }

# 源文件是 CRLF,统一成 LF 再按行号切(行号不变:每行都以 CRLF 结尾)
tr -d '\r' < "$SRC" > "$TMP/src.md"

# 一遍扫完:小节表 + 版本归属 + 每节引用到的代码路径(单进程,避免逐节 sed/grep 的进程开销)
awk -v secs="$TMP/sec.tsv" -v vers="$TMP/ver.tsv" -v refs="$TMP/ref.tsv" '
  function scan(s,   p, key) {
    while (match(s, /(backend|frontend|scripts|config|test)\/[A-Za-z0-9_.\/-]+/)) {
      p = substr(s, RSTART, RLENGTH)
      sub(/[.,;:、)]+$/, "", p)
      key = p SUBSEP start
      if (!(key in seen)) { seen[key] = 1; print p "\t" start >> refs }
      s = substr(s, RSTART + RLENGTH)
    }
  }
  /^##### / {
    start = NR
    title = substr($0, 7)
    print start "\t" title >> secs
    if (match(title, /V[0-9]+\.[0-9]+/)) v = substr(title, RSTART, RLENGTH); else v = "功能域"
    print v "\t" start >> vers
    next
  }
  start { scan($0) }
' "$TMP/src.md"

COUNT="$(wc -l < "$TMP/sec.tsv" | tr -d ' ')"
REFS="$(cut -f1 "$TMP/ref.tsv" 2>/dev/null | sort -u | wc -l | tr -d ' ')"

{
  echo "# 归档索引(自动生成,勿手改)"
  echo
  echo "> 由 \`bash scripts/doc-index.sh\` 从 \`docs/变更归档.md\` 生成;新增小节后重跑,\`--check\` 无漂移才算过(CI)。"
  echo "> 共 **$COUNT 小节**,被引用代码文件 $REFS 个。左列行号是 \`docs/变更归档.md\` 中的位置。"
  echo
  echo "## 小节(文件顺序)"
  echo
  echo "| 行 | 小节 |"
  echo "|---|---|"
  awk -F'\t' '{ printf "| %s | %s |\n", $1, $2 }' "$TMP/sec.tsv"
  echo
  echo "## 按版本"
  echo
  echo "| 版本 | 小节数 | 行号 |"
  echo "|---|---|---|"
  awk -F'\t' '{ n[$1]++; l[$1] = l[$1] " " $2 } END { for (v in n) printf "%s\t%s\t%d\t%s\n", (v == "功能域" ? 0 : 1), v, n[v], l[v] }' "$TMP/ver.tsv" \
    | sort -t$'\t' -k1,1n -k2,2V \
    | awk -F'\t' '{ printf "| %s | %d |%s |\n", $2, $3, $4 }'
  echo
  echo "## 文件 → 小节(反查「这个文件以前怎么改的」)"
  echo
  echo "| 文件 | 小节行号 |"
  echo "|---|---|"
  sort -u -t$'\t' -k1,1 -k2,2n "$TMP/ref.tsv" 2>/dev/null \
    | awk -F'\t' '{ if ($1 != cur) { if (cur != "") printf "| %s |%s |\n", cur, ls; cur = $1; ls = "" } ls = ls " " $2 } END { if (cur != "") printf "| %s |%s |\n", cur, ls }'
} > "$TMP/out.md"

if [[ "${1:-}" == "--check" ]]; then
  if [[ ! -f "$OUT" ]]; then
    echo "[FAIL] 缺 $OUT,bash scripts/doc-index.sh 生成后提交"
    exit 1
  fi
  if diff -q "$OUT" "$TMP/out.md" >/dev/null; then
    echo "[OK] $OUT 与归档一致($COUNT 小节)"
  else
    echo "[FAIL] $OUT 已漂移,重跑 bash scripts/doc-index.sh;差异前 20 行:"
    diff "$OUT" "$TMP/out.md" | head -20
    exit 1
  fi
else
  cp "$TMP/out.md" "$OUT"
  echo "已生成 $OUT($COUNT 小节 / $REFS 个文件)"
fi
