# 第三方开源小游戏

本目录整包收录第三方开源小游戏,`/games/embed/<slug>` 站内 iframe 直跑。只做最小改动(去外链字体/去分享浮层),游戏逻辑未改。

| 目录 | 游戏 | 出处 | 协议 |
|---|---|---|---|
| `xiangqi/` `reversi/` `sokoban/` `klotski/` `nonogram/` `match3/` `bubble/` `pinball/` `assets/` | 中国象棋 / 黑白棋 / 推箱子 / 华容道 / 数织 / 消消乐 / 泡泡龙 / 弹球台 | [litegame-hub/litegame-hub.github.io](https://github.com/litegame-hub/litegame-hub.github.io) | MIT © 2026 DongGuoTao |
| `trex/` | 小恐龙快跑 | [wayou/t-rex-runner](https://github.com/wayou/t-rex-runner)(源自 Chromium 的离线小恐龙) | BSD 3-Clause © 2022 牛さとみ / The Chromium Authors |

## 本地改动记录

- `trex/index.html`:去掉 Google Fonts 外链(离线可用、不发第三方请求)、去掉原作者的分享浮层与其引用脚本;`index.js`/`index.css`/贴图未改。
- litegame 系:仅复制 `index.html`,共享的 `assets/i18n.js` 一并保留(各页以 `../assets/i18n.js` 引用)。

## 新增一款的做法

1. 把整包静态文件放到 `public/mini-games/<slug>/`(入口必须是 `index.html`,只用相对路径)。
2. 在 `src/utils/games/embeddedGames.js` 加一行元数据,`src/views/games/Games.vue` 补一个图标。
3. i18n 补 `games.embedded.<slug>.title/desc` 中英文案。
4. 本文件补出处与协议。

> 协议要求:`public/` 下的静态文件不经后端鉴权,只放可公开分发的开源内容;GPL/AGPL 或未声明协议的项目不要放进来。
