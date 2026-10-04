#!/usr/bin/env bash
# ihomy CI/本地:自动完成 Jellyfin 首次启动向导 + 建媒体库 + 触发扫描。
#
# 仅用于 CI 冒烟与本地一键联调(生产仍按 docs/部署指导-Linux.md §12 手工初始化):
#   docker compose --profile jellyfin up -d jellyfin
#   bash scripts/dev-jellyfin-seed.sh          # 先造测试片源与海报
#   bash scripts/ci-jellyfin-setup.sh          # 再建库并等扫描完成
#
# 依赖:curl + jq。成功后向 stdout 打印 JELLYFIN_USER / JELLYFIN_PASS(CI 追加到 $GITHUB_ENV)。
set -euo pipefail

BASE="${JELLYFIN_URL:-http://localhost:8096}"
USER_NAME="${JELLYFIN_USER:-ciadmin}"
USER_PASS="${JELLYFIN_PASS:-ci-jellyfin-2026}"
CLIENT='MediaBrowser Client="ihomy-ci", Device="ci", DeviceId="ihomy-ci", Version="1.0"'

wait_for() {  # $1=描述 $2=curl 成功条件(函数名)
  local what="$1" probe="$2"
  for _ in $(seq 1 90); do
    if "$probe" >/dev/null 2>&1; then return 0; fi
    sleep 2
  done
  echo "Jellyfin $what 超时(180s)" >&2
  return 1
}

info_ready() { curl -sf "$BASE/System/Info/Public"; }

wait_for "就绪" info_ready

# 首次启动向导(容器已有配置时这些调用会 4xx,忽略即可)
curl -s -X POST "$BASE/Startup/Configuration" -H 'Content-Type: application/json' \
  -d '{"UICulture":"en-US","MetadataCountryCode":"US","PreferredMetadataLanguage":"en"}' >/dev/null || true
# GET /Startup/User 会初始化并创建默认首个用户;POST /Startup/User 只改「已存在」的首个用户,缺这一步会 500
curl -s "$BASE/Startup/User" >/dev/null || true
curl -s -X POST "$BASE/Startup/User" -H 'Content-Type: application/json' \
  -d "{\"Name\":\"$USER_NAME\",\"Password\":\"$USER_PASS\"}" >/dev/null || true
curl -s -X POST "$BASE/Startup/RemoteAccess" -H 'Content-Type: application/json' \
  -d '{"EnableRemoteAccess":true,"EnableAutomaticPortMapping":false}' >/dev/null || true
curl -s -X POST "$BASE/Startup/Complete" >/dev/null || true

# 登录拿令牌(向导刚完成时认证服务可能还没就绪,重试)
auth() {
  curl -sf -X POST "$BASE/Users/AuthenticateByName" \
    -H "Authorization: $CLIENT" -H 'Content-Type: application/json' \
    -d "{\"Username\":\"$USER_NAME\",\"Pw\":\"$USER_PASS\"}"
}
wait_for "登录就绪" auth
AUTH="$(auth)"
TOKEN="$(echo "$AUTH" | jq -r '.AccessToken')"
USER_ID="$(echo "$AUTH" | jq -r '.User.Id')"
[ -n "$TOKEN" ] && [ "$TOKEN" != "null" ] || { echo "Jellyfin 登录失败: $AUTH" >&2; exit 1; }

# 建媒体库(已存在会 4xx,忽略);类型 movies/shows 对应 /media 下的两个目录
add_lib() {
  curl -s -X POST "$BASE/Library/VirtualFolders?name=$1&collectionType=$2&refreshLibrary=false" \
    -H "X-Emby-Token: $TOKEN" -H 'Content-Type: application/json' \
    -d "{\"LibraryOptions\":{\"PathInfos\":[{\"Path\":\"$3\"}]},\"Name\":\"$1\",\"CollectionType\":\"$2\"}" \
    >/dev/null || true
}
add_lib Movies movies /media/Movies
add_lib Shows shows /media/Shows

# 触发扫描,等电影/剧集/分集都出现且数量连续 3 次不变(数量是边扫边涨的,只看 >0 会在地毯还没扫完时提前放行)
curl -s -X POST "$BASE/Library/Refresh" -H "X-Emby-Token: $TOKEN" >/dev/null || true
LAST=""
STABLE=0
for _ in $(seq 1 120); do
  COUNTS="$(curl -sf "$BASE/Items/Counts?userId=$USER_ID" -H "X-Emby-Token: $TOKEN" || echo '{}')"
  MOV="$(echo "$COUNTS" | jq -r '.MovieCount // 0')"
  SER="$(echo "$COUNTS" | jq -r '.SeriesCount // 0')"
  EPI="$(echo "$COUNTS" | jq -r '.EpisodeCount // 0')"
  CUR="$MOV/$SER/$EPI"
  if [ "$MOV" -gt 0 ] && [ "$SER" -gt 0 ] && [ "$EPI" -gt 0 ]; then
    if [ "$CUR" = "$LAST" ]; then STABLE=$((STABLE + 1)); else STABLE=0; fi
    if [ "$STABLE" -ge 3 ]; then echo "媒体库扫描完成:movies=$MOV series=$SER episodes=$EPI"; break; fi
  fi
  LAST="$CUR"
  sleep 2
done

echo "JELLYFIN_USER=$USER_NAME"
echo "JELLYFIN_PASS=$USER_PASS"
