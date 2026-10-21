#!/usr/bin/env bash
# gen-code-map.sh — 生成 docs/代码地图.md(功能↔文件索引,给人和 AI 定位代码用)
#
# 为什么不手写:历史手写索引(变更归档目录、AGENTS 模块表)已多次漏登/滞后。
# 本脚本只从代码事实抽取,改了代码重跑即可,漂移由 --check 在 CI 拦住。
#
# 抽取来源(全部只读):
#   Controller: @Tag(name=...) + @RequestMapping + @RequirePermission/@OperationLog 计数
#   数据层:     entity/@TableName ↔ resources/mapper/*.xml(自定义 SQL 条数)
#   页面:       router/index.js 的 path/name/component(单行与多行对象都支持)
#   API 分组:   api/index.js 的 export const xxxApi 块
#
# 用法:
#   bash scripts/gen-code-map.sh          # 写入 docs/代码地图.md
#   bash scripts/gen-code-map.sh --check  # 只比对,有漂移退出码 1(CI 用)
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

OUT="docs/代码地图.md"
JAVA="backend/src/main/java/com/ihomy"
XML="backend/src/main/resources/mapper"
ROUTER="frontend/src/router/index.js"
API="frontend/src/api/index.js"
TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT

sql_count() { # 自定义 SQL 语句数(<select/<insert/<update/<delete)
  local n=0 line
  while IFS= read -r line; do
    [[ $line =~ \<(select|insert|update|delete) ]] && n=$((n + 1))
  done < "$1"
  echo "$n"
}

{
  echo "# 代码地图(自动生成,勿手改)"
  echo
  echo "> 由 \`bash scripts/gen-code-map.sh\` 从代码事实生成;改了代码/路由/实体就重跑,\`--check\` 无漂移才算过(CI)。"
  echo "> 用法:\`grep -in <关键词> docs/代码地图.md\` 先定位文件,再读该文件。改什么该动哪几层见根目录 \`AGENTS.md\`「任务 → 先读哪几个文件」。"
  echo
  echo "## 后端接口层(Controller)"
  echo
  echo "| 模块(@Tag) | Controller | 路径前缀 | 权限码/操作日志 |"
  echo "|---|---|---|---|"
  while IFS= read -r f; do
    cls="${f##*/}"; cls="${cls%.java}"
    tag=""; pre=""; perm=0; op=0
    while IFS= read -r line; do
      [[ -z $tag && $line =~ @Tag\(name\ =\ \"([^\"]*)\" ]] && tag="${BASH_REMATCH[1]}"
      [[ -z $pre && $line =~ @RequestMapping\(\"([^\"]*)\" ]] && pre="${BASH_REMATCH[1]}"
      [[ $line == *@RequirePermission* ]] && perm=$((perm + 1))
      [[ $line == *@OperationLog* ]] && op=$((op + 1))
    done < "$f"
    printf '| %s | %s | %s | %s/%s |\n' "${tag:-—}" "$cls" "${pre:-—}" "$perm" "$op"
  done < <(find "$JAVA/controller" -name '*Controller.java' | sort)

  echo
  echo "## 数据层(表 ↔ 实体 ↔ Mapper XML)"
  echo
  echo "| 表 | 实体 | Mapper XML |"
  echo "|---|---|---|"
  while IFS= read -r f; do
    cls="${f##*/}"; cls="${cls%.java}"
    tbl=""
    while IFS= read -r line; do
      [[ $line =~ @TableName\(\"([^\"]*)\" ]] && { tbl="${BASH_REMATCH[1]}"; break; }
    done < "$f"
    [[ -z $tbl ]] && continue
    xf="$XML/${cls}Mapper.xml"
    [[ -f $xf ]] && sqlc="$(sql_count "$xf")" || sqlc="—"
    printf '| %s | %s | %s |\n' "$tbl" "$cls" "$sqlc"
  done < <(find "$JAVA/entity" -name '*.java' | sort)

  echo
  echo "## 仅有自定义 SQL、无实体的 Mapper XML"
  echo
  echo "| XML | 语句数 |"
  echo "|---|---|"
  for xf in "$XML"/*.xml; do
    cls="${xf##*/}"; cls="${cls%.xml}"
    [[ -f "$JAVA/entity/${cls%Mapper}.java" ]] && continue
    printf '| %s | %s |\n' "$cls" "$(sql_count "$xf")"
  done

  echo
  echo "## 前端页面(路由 → 视图)"
  echo
  echo "| 路径 | 路由名 | 视图 | 可见性 |"
  echo "|---|---|---|---|"
  # 路由对象可能是单行,也可能拆成多行;先归一成「一行一条路由」再取值
  while IFS= read -r line; do
    path=""; name=""; view=""
    [[ $line =~ path:\ \'([^\']*)\' ]] && path="${BASH_REMATCH[1]}"
    [[ $line =~ name:\ \'([^\']*)\' ]] && name="${BASH_REMATCH[1]}"
    [[ $line =~ import\(\'@/views/([^\']*)\'\) ]] && view="${BASH_REMATCH[1]}"
    [[ -z $name ]] && continue
    if [[ $line == *'public: true'* ]]; then vis="公开"
    elif [[ $line == *'requiresAuth: true'* ]]; then vis="登录"
    else vis="—"; fi
    printf '| %s | %s | %s | %s |\n' "$path" "$name" "${view:-—}" "$vis"
  done < <(awk '
    buf == "" {
      if ($0 ~ /path: .*},?[[:space:]]*$/) { print; next }
      if ($0 ~ /^[[:space:]]*\{[[:space:]]*$/) { buf = $0; next }
      next
    }
    { buf = buf " " $0; if ($0 ~ /^[[:space:]]*\}[,]?[[:space:]]*$/) { print buf; buf = "" } }
  ' "$ROUTER")

  echo
  echo "## 前端 API 分组(api/index.js)"
  echo
  echo "| Api 对象 | 起始行 | 端点数 |"
  echo "|---|---|---|"
  awk '
    /^export const [A-Za-z]+Api = \{/ { name=$3; start=NR; c=0; next }
    /^\}/ { if (name != "") { printf "| %s | L%d | %d |\n", name, start, c; name="" } next }
    { if (name != "") { c += gsub(/request\.(get|post|put|delete)\(/, "&") } }
  ' "$API"
} > "$TMP"

if [[ "${1:-}" == "--check" ]]; then
  if [[ ! -f "$OUT" ]]; then
    echo "[FAIL] 缺 $OUT,bash scripts/gen-code-map.sh 生成后提交"
    exit 1
  fi
  if diff -q "$OUT" "$TMP" >/dev/null; then
    echo "[OK] $OUT 与代码一致"
  else
    echo "[FAIL] $OUT 已漂移,重跑 bash scripts/gen-code-map.sh;差异前 20 行:"
    diff "$OUT" "$TMP" | head -20
    exit 1
  fi
else
  cp "$TMP" "$OUT"
  echo "已生成 $OUT($(grep -c '^|' "$OUT") 行表格)"
fi
