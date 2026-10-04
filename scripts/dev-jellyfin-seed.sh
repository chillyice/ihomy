#!/usr/bin/env bash
# ihomy 本地 Jellyfin 联调:造测试媒体库(电影/剧集 + NFO + 海报)到 .dev-media/
#
# 用法(Git Bash):
#   docker compose --profile jellyfin up -d jellyfin
#   bash scripts/dev-jellyfin-seed.sh
#
# 说明:片源用容器自带的 jellyfin-ffmpeg 现造(宿主不必装 ffmpeg),时长 20 秒足够跑通
# 扫描/播放链路;NFO 里有题材/导演/演员/制片商/地区,用来验证「本地元数据」读取路径
# (国内访问 TMDB 被污染,故不依赖在线刮削)。目录结构即 Jellyfin 标准约定:
#   电影=一个作品一个目录;剧集=作品目录/Season NN/季度内单集。
set -euo pipefail

# Windows Git Bash 会把传给 docker 的容器内绝对路径(/usr/...、/media/...)转成 Windows 路径,必须关掉
export MSYS_NO_PATHCONV=1

CONTAINER=ihomy-jellyfin
FFMPEG=/usr/lib/jellyfin-ffmpeg/ffmpeg
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MEDIA="$ROOT/.dev-media"

if ! docker ps --format '{{.Names}}' | grep -qx "$CONTAINER"; then
  echo "容器 $CONTAINER 未运行,先执行: docker compose --profile jellyfin up -d jellyfin" >&2
  exit 1
fi

# 宿主侧建目录 + 文本元数据(bash heredoc 按字节落盘,天然 UTF-8)
mkdir -p "$MEDIA/Movies/流浪地球 (2019)" "$MEDIA/Movies/疯狂动物城 (2016)" \
         "$MEDIA/Shows/家宴故事 (2021)/Season 01" "$MEDIA/Shows/家宴故事 (2021)/Season 02"

cat > "$MEDIA/Movies/流浪地球 (2019)/movie.nfo" <<'NFO'
<?xml version="1.0" encoding="utf-8" standalone="yes"?>
<movie>
  <title>流浪地球</title>
  <originaltitle>The Wandering Earth</originaltitle>
  <year>2019</year>
  <premiered>2019-02-05</premiered>
  <plot>太阳即将毁灭,人类在地球表面建造出巨大的推进器,寻找新家园。</plot>
  <genre>科幻</genre>
  <genre>灾难</genre>
  <genre>冒险</genre>
  <director>郭帆</director>
  <actor><name>吴京</name><role>刘培强</role><order>0</order></actor>
  <actor><name>屈楚萧</name><role>刘启</role><order>1</order></actor>
  <studio>中国电影股份有限公司</studio>
  <country>中国大陆</country>
  <rating>7.9</rating>
  <runtime>125</runtime>
</movie>
NFO

cat > "$MEDIA/Movies/疯狂动物城 (2016)/movie.nfo" <<'NFO'
<?xml version="1.0" encoding="utf-8" standalone="yes"?>
<movie>
  <title>疯狂动物城</title>
  <originaltitle>Zootopia</originaltitle>
  <year>2016</year>
  <premiered>2016-03-04</premiered>
  <plot>兔子朱迪成为动物城第一位兔子警官,与狐狸尼克联手破案。</plot>
  <genre>动画</genre>
  <genre>喜剧</genre>
  <genre>家庭</genre>
  <director>拜伦·霍华德</director>
  <actor><name>金妮弗·古德温</name><role>朱迪</role><order>0</order></actor>
  <studio>Walt Disney Pictures</studio>
  <country>美国</country>
  <rating>9.2</rating>
  <runtime>109</runtime>
</movie>
NFO

cat > "$MEDIA/Shows/家宴故事 (2021)/tvshow.nfo" <<'NFO'
<?xml version="1.0" encoding="utf-8" standalone="yes"?>
<tvshow>
  <title>家宴故事</title>
  <year>2021</year>
  <premiered>2021-06-01</premiered>
  <plot>一家三代人围绕一张餐桌发生的日常故事。</plot>
  <genre>剧情</genre>
  <genre>家庭</genre>
  <director>张一鸣</director>
  <actor><name>李大山</name><role>爷爷</role><order>0</order></actor>
  <studio>测试广播电视台</studio>
  <country>中国大陆</country>
  <rating>8.1</rating>
</tvshow>
NFO

# 片源 + 海报:容器内 ffmpeg 生成(20s h264+aac;海报取一帧 testsrc 静态图)
gen_video() {
  local out="$1"
  docker exec "$CONTAINER" "$FFMPEG" -y -hide_banner -loglevel error \
    -f lavfi -i "testsrc=duration=20:size=640x360:rate=15" \
    -f lavfi -i "sine=frequency=440:duration=20" \
    -c:v libx264 -preset ultrafast -pix_fmt yuv420p -c:a aac -shortest \
    "/media/$out"
}

gen_poster() {
  local out="$1"
  docker exec "$CONTAINER" "$FFMPEG" -y -hide_banner -loglevel error \
    -f lavfi -i "testsrc=size=400x600:rate=1" -frames:v 1 -q:v 4 "/media/$out"
}

gen_video "Movies/流浪地球 (2019)/流浪地球 (2019).mp4"
gen_video "Movies/疯狂动物城 (2016)/疯狂动物城 (2016).mp4"
gen_video "Shows/家宴故事 (2021)/Season 01/家宴故事 S01E01.mp4"
gen_video "Shows/家宴故事 (2021)/Season 01/家宴故事 S01E02.mp4"
gen_video "Shows/家宴故事 (2021)/Season 02/家宴故事 S02E01.mp4"

gen_poster "Movies/流浪地球 (2019)/poster.jpg"
gen_poster "Movies/疯狂动物城 (2016)/poster.jpg"
gen_poster "Shows/家宴故事 (2021)/poster.jpg"

echo "测试媒体已生成于 $MEDIA:"
find "$MEDIA" -type f | sed "s|$MEDIA|.|"
