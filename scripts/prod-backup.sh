#!/usr/bin/env bash
# prod-backup.sh — ihomy 生产备份(MySQL 逻辑备份 + uploads 目录同步)
#
# 由 systemd timer 每日 03:00 触发(见 config/systemd/ihomy-backup.{service,timer}),
# 以 root 运行:数据库走 root@localhost 免密(socket 认证),uploads 目录可直接读。
#
# 设计要点:
#   - 数据库:mysqldump 单事务一致性快照(--single-transaction),含存储过程/触发器/事件;
#     gzip 后立即 gzip -t 校验,损坏即非 0 退出(systemd 记为失败,可从 journalctl 看到)。
#   - uploads:rsync **只增不删**(刻意不加 --delete)——即使源目录误删,备份里仍有历史文件;
#     代价是会被应用移除的孤儿文件长期留在备份中(家庭规模下可接受)。
#   - 保留:数据库转储保留 RETENTION_DAYS 天;uploads 目录为单份「只增不减」镜像,不轮转。
#   - 成功标记:每次成功写 $BACKUP_DIR/.last-success,便于巡检判断备份是否按期执行。
#
# 环境变量(均可覆盖,默认值即生产约定):
#   IHOMY_BACKUP_DIR(默认 /var/backups/ihomy)
#   IHOMY_UPLOADS_DIR(默认 /opt/ihomy/uploads)
#   IHOMY_DB_NAME(默认 ihomy)
#   IHOMY_BACKUP_RETENTION_DAYS(默认 14)
#   IHOMY_DB_HOST / IHOMY_DB_PORT(默认空 = 本机 socket;仅本机演练或异地库时才需指定)
#
# 用法:sudo /opt/ihomy/scripts/prod-backup.sh
set -euo pipefail

BACKUP_DIR="${IHOMY_BACKUP_DIR:-/var/backups/ihomy}"
UPLOADS_DIR="${IHOMY_UPLOADS_DIR:-/opt/ihomy/uploads}"
DB_NAME="${IHOMY_DB_NAME:-ihomy}"
RETENTION_DAYS="${IHOMY_BACKUP_RETENTION_DAYS:-14}"

# root@localhost 走 socket 免密;指定 host/port 时密码由调用方经 MYSQL_PWD 注入(勿写命令行)
MYSQL_ARGS=(-uroot)
[[ -n "${IHOMY_DB_HOST:-}" ]] && MYSQL_ARGS+=(-h "$IHOMY_DB_HOST")
[[ -n "${IHOMY_DB_PORT:-}" ]] && MYSQL_ARGS+=(-P "$IHOMY_DB_PORT")

DB_DIR="$BACKUP_DIR/db"
UP_DIR="$BACKUP_DIR/uploads"
STAMP="$(date +%F_%H%M)"
OUT="$DB_DIR/${DB_NAME}-${STAMP}.sql.gz"

log() { echo "[$(date '+%F %T')] $*"; }
die() { echo "[$(date '+%F %T')] ERROR: $*" >&2; exit 1; }

[[ -d "$UPLOADS_DIR" ]] || die "上传目录不存在:$UPLOADS_DIR"
mkdir -p "$DB_DIR" "$UP_DIR"

# ---------------------------------------------------------------- 1. 数据库
log "mysqldump → $OUT"
# --events/--routines/--triggers:schema.sql 无这些对象,但存量库可能手工加过,一并带上
mysqldump "${MYSQL_ARGS[@]}" --default-character-set=utf8mb4 \
  --single-transaction --routines --triggers --events \
  "$DB_NAME" | gzip -9 > "$OUT"

# 空库/连不上时 mysqldump 可能产出空文件或极小的错误文本,体积下限兜底
MIN_BYTES=1024
SIZE=$(stat -c %s "$OUT" 2>/dev/null || stat -f %z "$OUT")
[[ "$SIZE" -ge "$MIN_BYTES" ]] || die "转储体积异常(${SIZE}B < ${MIN_BYTES}B),疑似备份失败"
gzip -t "$OUT" || die "转储 gzip 校验失败:$OUT"
log "数据库备份完成($(du -h "$OUT" | cut -f1))"

# ---------------------------------------------------------------- 2. uploads(只增不删)
log "rsync uploads → $UP_DIR(只增不删)"
rsync -a "$UPLOADS_DIR/" "$UP_DIR/"
log "uploads 同步完成($(du -sh "$UP_DIR" | cut -f1))"

# ---------------------------------------------------------------- 3. 保留策略
log "清理 ${RETENTION_DAYS} 天前的数据库转储"
find "$DB_DIR" -maxdepth 1 -type f -name '*.sql.gz' -mtime +"$RETENTION_DAYS" -print -delete

# ---------------------------------------------------------------- 4. 成功标记
date '+%F %T' > "$BACKUP_DIR/.last-success"
log "备份完成;成功标记已更新:$BACKUP_DIR/.last-success"
