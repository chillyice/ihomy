#!/usr/bin/env bash
# doc-drift-check.sh — 文档/代码一致性闸门
#
# 目的:把 AGENTS.md「必须遵守」里那些**可机器判定**的约定,固化成 CI 闸门,
# 防止「代码改了、文档/种子没跟上」这类漂移静默存在(历史上踩过:
# family_music 早已重构为 content_music,实体类却留在仓库里;模块种子加了行、路由忘加)。
#
# 覆盖检查(全部只读、无副作用、可本地随时跑):
#   1. VERSION 唯一事实来源:单行、形如 V<主>.<次>(次位不补零)
#   2. 表数一致性:AGENTS.md「**N 张表**」+ 各前缀计数 / 需求 §6.2 标题 ↔ schema.sql 实际建表数
#   3. 实体 ↔ 表:每个 @TableName 的表必须在 schema.sql 里存在(反向允许,关联/字典表无实体)
#   4. Mapper 接口不写 SQL 注解(@Select/@Insert/@Update/@Delete 一律放 XML)
#   5. 每个 *Controller.java 必须带 @Tag(文档生成按它出章,缺了会静默丢章)
#   6. @RequirePermission("code") 的 code 必须在 schema.sql 的 sys_auth 种子里
#   7. 启用的首页模块种子(path, enabled=1)必须有对应前端路由
#
# 用法:bash scripts/doc-drift-check.sh     # 退出码非 0 即有漂移
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

SCHEMA="backend/src/main/resources/schema.sql"
REQ="docs/需求设计说明书.md"
AGENTS="AGENTS.md"
ROUTER="frontend/src/router/index.js"
JAVA_DIR="backend/src/main/java/com/ihomy"

FAIL=0
pass() { printf '  [OK]   %s\n' "$1"; }
fail() { printf '  [FAIL] %s\n' "$1"; FAIL=1; }

# 去掉 java 块注释与行注释(注释里的 @注解 示例不算违例)
strip_java_comments() { perl -0pe 's{/\*.*?\*/}{}gs; s{//[^\n]*}{}g' "$1"; }

echo "== 文档/代码一致性检查(doc-drift-check)=="

# ---------------------------------------------------------------- 1. VERSION
echo "[1] VERSION 唯一事实来源"
V_RAW="$(cat VERSION 2>/dev/null || true)"
V_LINES="$(wc -l < VERSION 2>/dev/null | tr -d ' ' || echo 0)"
if [[ "$V_RAW" =~ ^V[0-9]+\.[0-9]+$ && "$V_LINES" -le 1 ]]; then
  pass "VERSION = $V_RAW"
else
  fail "VERSION 必须单行且形如 V<主>.<次>(现状:'$V_RAW',行数 $V_LINES)"
fi

# ------------------------------------------------------- 2. 表数一致性
echo "[2] 表数一致性(AGENTS / 需求 §6.2 ↔ schema.sql)"
TABLES="$(grep -cE '^CREATE TABLE `' "$SCHEMA" || true)"
AG_TOTAL="$(grep -m1 -oE '\*\*[0-9]+ 张表\*\*' "$AGENTS" | grep -oE '[0-9]+' || true)"
RQ_TOTAL="$(grep -m1 -oE '表清单\([0-9]+ 张' "$REQ" | grep -oE '[0-9]+' || true)"
[[ "$AG_TOTAL" == "$TABLES" ]] && pass "AGENTS.md 表数 $AG_TOTAL" \
  || fail "AGENTS.md 写 $AG_TOTAL 张表, schema.sql 实际 $TABLES 张"
[[ "$RQ_TOTAL" == "$TABLES" ]] && pass "需求 §6.2 表数 $RQ_TOTAL" \
  || fail "需求 §6.2 写 $RQ_TOTAL 张表, schema.sql 实际 $TABLES 张"

# 其余「当前口径」文档:凡写「N 表」或「N 张表」都必须等于实际表数
# (需求修订记录/变更归档里合法存在历史数字,故不扫;架构设计含「无实体的 N 张表」等另义数字,也不扫)
for doc in README.md docs/README.md docs/部署指导-Linux.md docs/部署指导-Windows.md; do
  [[ -f "$doc" ]] || continue
  bad="$(grep -oE '[0-9]+ 张?表' "$doc" | grep -oE '[0-9]+' | grep -vx "$TABLES" | sort -u | tr '\n' ' ' || true)"
  [[ -z "$bad" ]] && pass "$doc 表数口径一致($TABLES)" \
    || fail "$doc 出现非当前表数:${bad}(实际 $TABLES 张)"
done

# 各前缀计数(AGENTS.md「数据库约定」)
for prefix in sys report family content; do
  decl="$(grep -m1 -oE "\`${prefix}_\` [0-9]+" "$AGENTS" | grep -oE '[0-9]+' || true)"
  actual="$(grep -oE '^CREATE TABLE `[^`]+`' "$SCHEMA" | sed 's/.*`\(.*\)`/\1/' | grep -cE "^${prefix}_" || true)"
  [[ "$decl" == "$actual" ]] && pass "AGENTS ${prefix}_ 前缀 $decl 张" \
    || fail "AGENTS 写 ${prefix}_ $decl 张, 实际 $actual 张"
done
game_decl="$(grep -m1 -oE 'game_info\` [0-9]+' "$AGENTS" | grep -oE '[0-9]+' || true)"
if [[ -z "$game_decl" ]]; then game_decl="$(grep -m1 -oE 'game_info[^0-9]{0,4}[0-9]+' "$AGENTS" | grep -oE '[0-9]+' || true)"; fi
[[ "$game_decl" == "1" ]] && pass "AGENTS game_info 1 张" || fail "AGENTS 写 game_info $game_decl 张, 实际 1 张"

# ------------------------------------------------------- 3. 实体 ↔ 表
echo "[3] 实体 @TableName ↔ schema.sql 建表"
grep -rhoE '@TableName\("[^"]+"\)' "$JAVA_DIR" 2>/dev/null \
  | grep -oE '"[^"]+"' | tr -d '"' | sort -u > /tmp/_ddc_ents.txt
grep -oE '^CREATE TABLE `[^`]+`' "$SCHEMA" | sed 's/.*`\(.*\)`/\1/' | sort -u > /tmp/_ddc_tabs.txt
MISSING_ENT="$(comm -23 /tmp/_ddc_ents.txt /tmp/_ddc_tabs.txt || true)"
if [[ -z "$MISSING_ENT" ]]; then
  pass "全部 $(wc -l < /tmp/_ddc_ents.txt | tr -d ' ') 个 @TableName 都能在建表语句中找到"
else
  fail "以下 @TableName 在 schema.sql 无对应建表(表已改名/删除?):"
  printf '         %s\n' $MISSING_ENT
fi

# ------------------------------------------------------- 4. Mapper 不写 SQL 注解
echo "[4] Mapper 接口不写 SQL 注解(自定义 SQL 一律放 XML)"
SQL_ANNO=""
while IFS= read -r f; do
  hits="$(strip_java_comments "$f" | grep -nE '@(Select|Insert|Update|Delete)[[:space:]]*\(' || true)"
  [[ -n "$hits" ]] && SQL_ANNO+="$f: $hits"$'\n'
done < <(find "$JAVA_DIR/mapper" -name '*.java' 2>/dev/null)
if [[ -z "$SQL_ANNO" ]]; then
  pass "mapper 接口无 @Select/@Insert/@Update/@Delete"
else
  fail "存在 SQL 注解,请移入 resources/mapper/*.xml:"
  printf '%s' "$SQL_ANNO"
fi

# ------------------------------------------------------- 5. Controller @Tag
echo "[5] 每个 Controller 必须带 @Tag"
NO_TAG=""
while IFS= read -r f; do
  # 先落变量再匹配:pipefail 下 `perl | grep -q` 会因 grep 提前退出给 perl 发 SIGPIPE(141),
  # 让整条管线非 0 —— 会把「有 @Tag」随机误判成缺失
  src="$(strip_java_comments "$f")"
  grep -qE '@Tag[[:space:]]*\(' <<<"$src" || NO_TAG+="$(basename "$f") "
done < <(find "$JAVA_DIR/controller" -name '*Controller.java' 2>/dev/null)
[[ -z "$NO_TAG" ]] && pass "全部 Controller 都带 @Tag" || fail "缺 @Tag: $NO_TAG"

# ------------------------------------------------------- 6. 权限码已种子化
echo "[6] @RequirePermission 权限码已在 sys_auth 种子"
# 权限码出现在 schema.sql 的 sys_auth INSERT 值里(形如 ('xxx:yyy', ...)
grep -oE "\('[a-z]+:[a-z_]+'" "$SCHEMA" | tr -d "('" | sort -u > /tmp/_ddc_seeded.txt
grep -rhoE '@RequirePermission\("[^"]+"\)' "$JAVA_DIR" 2>/dev/null \
  | grep -oE '"[^"]+"' | tr -d '"' | sort -u > /tmp/_ddc_used.txt
NOT_SEEDED="$(comm -23 /tmp/_ddc_used.txt /tmp/_ddc_seeded.txt || true)"
if [[ -z "$NOT_SEEDED" ]]; then
  pass "代码用到的全部 $(wc -l < /tmp/_ddc_used.txt | tr -d ' ') 个权限码都已种子化"
else
  fail "以下权限码未进 sys_auth 种子(新增接口前须补种子,否则 403):"
  printf '         %s\n' $NOT_SEEDED
fi

# ------------------------------------------------------- 7. 首页模块 → 路由
echo "[7] 启用的首页模块(path, enabled=1)必须有前端路由"
MOD_MISSING=""
while read -r path enabled; do
  [[ "$enabled" != "1" ]] && continue
  grep -qE "path:[[:space:]]*'${path}'" "$ROUTER" || MOD_MISSING+="$path "
done < <(awk '/INSERT INTO `sys_home_module`/{f=1} f{print} f&&/;[[:space:]]*$/{f=0}' "$SCHEMA" \
  | sed -nE "s/^\(\s*'[^']*'\s*,\s*'[^']*'\s*,\s*'[^']*'\s*,\s*'([^']+)'\s*,.*,[[:space:]]*([01])[[:space:]]*\)[,;]?[[:space:]]*$/\1 \2/p")
[[ -z "$MOD_MISSING" ]] && pass "启用模块的 path 都有对应路由" || fail "模块种子有 path 但路由缺失: $MOD_MISSING"

echo
if [[ "$FAIL" -eq 0 ]]; then
  echo "== 通过:未发现文档/代码漂移 =="
else
  echo "== 未通过:请修复上述漂移(改完重跑本脚本)=="
fi
exit "$FAIL"
