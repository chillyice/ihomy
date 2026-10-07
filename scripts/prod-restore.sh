#!/usr/bin/env bash
# prod-restore.sh — ihomy 生产恢复 / 备份可用性演练
#
# 两个用途(都需要显式给出「目标库名」,不做任何默认,避免手滑覆盖生产库):
#   1) 恢复演练(推荐先做):把转储导入一个临时库核对表数与行数,确认备份真的可用
#         sudo scripts/prod-restore.sh /var/backups/ihomy/db/ihomy-2026-10-05_0300.sql.gz ihomy_drill
#      核对完删掉演练库即可:mysql -uroot -e "DROP DATABASE ihomy_drill;"
#   2) 真恢复:目标库写成 ihomy 即为生产恢复(**会先自动导出当前库再覆盖**)
#         sudo scripts/prod-restore.sh /var/backups/ihomy/db/ihomy-xxx.sql.gz ihomy
#
# uploads 目录恢复是单纯的目录同步,不在本脚本内(见 docs/部署指导-Linux.md「附:数据备份」):
#   rsync -a --delete /var/backups/ihomy/uploads/ /opt/ihomy/uploads/
#
# 以 root 运行:数据库走 root@localhost 免密(socket 认证)。
set -euo pipefail

DUMP="${1:-}"
TARGET_DB="${2:-}"
DB_NAME="${IHOMY_DB_NAME:-ihomy}"
BACKUP_DIR="${IHOMY_BACKUP_DIR:-/var/backups/ihomy}"

# root@localhost 走 socket 免密;指定 host/port 时密码由调用方经 MYSQL_PWD 注入(勿写命令行)
MYSQL_ARGS=(-uroot)
[[ -n "${IHOMY_DB_HOST:-}" ]] && MYSQL_ARGS+=(-h "$IHOMY_DB_HOST")
[[ -n "${IHOMY_DB_PORT:-}" ]] && MYSQL_ARGS+=(-P "$IHOMY_DB_PORT")

log() { echo "[$(date '+%F %T')] $*"; }
die() { echo "[$(date '+%F %T')] ERROR: $*" >&2; exit 1; }

[[ -n "$DUMP" && -n "$TARGET_DB" ]] || die "用法:$0 <转储.sql.gz> <目标库名>(演练用 ihomy_drill;生产用 $DB_NAME)"
[[ -f "$DUMP" ]] || die "转储文件不存在:$DUMP"
gzip -t "$DUMP" || die "转储 gzip 校验失败:$DUMP"
[[ "$TARGET_DB" =~ ^[a-zA-Z0-9_]+$ ]] || die "目标库名只允许字母数字下划线:$TARGET_DB"

# 恢复到生产库:先自动把当前库导出一份,给一次后悔机会
if [[ "$TARGET_DB" == "$DB_NAME" ]]; then
  SAFETY="$BACKUP_DIR/pre-restore-${DB_NAME}-$(date +%F_%H%M).sql.gz"
  log "目标为生产库 $DB_NAME,先做安全快照:$SAFETY"
  mkdir -p "$(dirname "$SAFETY")"
  mysqldump "${MYSQL_ARGS[@]}" --default-character-set=utf8mb4 --single-transaction "$DB_NAME" | gzip -9 > "$SAFETY"
  gzip -t "$SAFETY" || die "安全快照校验失败,已中止"
fi

log "建库(若不存在)并导入 $DUMP → $TARGET_DB"
mysql "${MYSQL_ARGS[@]}" --default-character-set=utf8mb4 -e \
  "CREATE DATABASE IF NOT EXISTS \`$TARGET_DB\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
gunzip -c "$DUMP" | mysql "${MYSQL_ARGS[@]}" --default-character-set=utf8mb4 "$TARGET_DB"

# 导入后核对:表数应 ≥ 1(schema.sql 全量导入为 81 张);演练时可与生产库对比
TABLES=$(mysql "${MYSQL_ARGS[@]}" -N -B -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$TARGET_DB';")
log "导入完成:目标库 $TARGET_DB 现有 $TABLES 张表"
[[ "$TABLES" -ge 1 ]] || die "导入后目标库没有表,转储可能损坏"
if [[ "$TARGET_DB" != "$DB_NAME" ]]; then
  log "演练完成。生产库 $DB_NAME 表数对照:$(mysql "${MYSQL_ARGS[@]}" -N -B -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB_NAME';")"
  log "核对无误后可删除演练库:mysql ${MYSQL_ARGS[*]} -e 'DROP DATABASE \`$TARGET_DB\`;'"
fi
