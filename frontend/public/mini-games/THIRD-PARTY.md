# 第三方开源小游戏

本目录整包收录第三方开源小游戏,`/games/embed/<slug>` 站内 iframe 直跑。只做最小改动(去外链字体/去分享浮层),游戏逻辑未改。

| 目录 | 游戏 | 出处 | 协议 |
|---|---|---|---|
| `xiangqi/` `reversi/` `sokoban/` `klotski/` `nonogram/` `match3/` `bubble/` `assets/` | 中国象棋 / 黑白棋 / 推箱子 / 华容道 / 数织 / 消消乐 / 泡泡龙 | [litegame-hub/litegame-hub.github.io](https://github.com/litegame-hub/litegame-hub.github.io) | MIT © 2026 DongGuoTao |
| `trex/` | 小恐龙快跑 | [wayou/t-rex-runner](https://github.com/wayou/t-rex-runner)(源自 Chromium 的离线小恐龙) | BSD 3-Clause © 2022 牛さとみ / The Chromium Authors |
| `space-cadet/` | 三维弹球·太空军校生 | 引擎 [k4zmu2a/SpaceCadetPinball](https://github.com/k4zmu2a/SpaceCadetPinball)(反编译 Windows 自带的 `pinball.exe`),网页构建取 [alula/SpaceCadetPinball](https://github.com/alula/SpaceCadetPinball) 的 Emscripten 产物 | 引擎 MIT © k4zmu2a 及贡献者;原始美术/音效 © Microsoft / Cinematronics / Maxis,已取得授权在本站分发 |

## 本地改动记录

- `trex/index.html`:去掉 Google Fonts 外链(离线可用、不发第三方请求)、去掉原作者的分享浮层与其引用脚本;`index.js`/`index.css`/贴图未改。
- litegame 系:仅复制 `index.html`,共享的 `assets/i18n.js` 一并保留(各页以 `../assets/i18n.js` 引用)。
- `space-cadet/index.html`:重写外壳(去掉 Win95 假窗口与状态条),canvas 等比铺满、深色底;补触屏操作条(左/右挡板、发球、开新局)把合成按键事件投递到 SDL 注册的监听目标;引擎三件套 `SpaceCadetPinball.js` / `.wasm` / `.data` 原样未改。
  - 触屏合成按键**优先派发到 `document`**:该构建 SDL 的键盘监听实测挂在 document 上,`keyTargets` 里先注册的 window 收不到(事件不向下传),派发到 window 挡板不动。
  - 右挡板键码用 **X(88)**:该构建不认 `/`(191,按下无响应),`.`(190)与 X 同样有效,取与 Z 同排的 X;桌面文案同口径。
  - `.data`(2.18MB)是 Emscripten 文件包,内含 67 个原始素材:`PINBALL.DAT`(928KB,`PARTOUT(4.0)RESOURCE` 格式)、`table.bmp`、`PINBALL.MID`/`PINBALL2.MID`、60 个 `SOUND*.WAV`;解包脚本见下。

```python
# 从 SpaceCadetPinball.data 还原原始素材(文件表在 SpaceCadetPinball.js 末尾的 loadPackage(...) 里)
import json, os
js = open("SpaceCadetPinball.js", encoding="utf-8").read()
s = js.rindex("loadPackage(") + len("loadPackage(")
d = 0
for e, c in enumerate(js[s:], s):
    d += c == "("
    if c == ")":
        d -= 1
        if d == 0:
            break
meta = json.loads(js[s:e])
blob = open("SpaceCadetPinball.data", "rb").read()
os.makedirs("game_resources", exist_ok=True)
for f in meta["files"]:
    open(os.path.join("game_resources", os.path.basename(f["filename"])), "wb").write(blob[f["start"]:f["end"]])
```

## 新增一款的做法

1. 把整包静态文件放到 `public/mini-games/<slug>/`(入口必须是 `index.html`,只用相对路径)。
2. 在 `src/utils/games/embeddedGames.js` 加一行元数据,`src/views/games/Games.vue` 补一个图标。
3. i18n 补 `games.embedded.<slug>.title/desc` 中英文案。
4. 本文件补出处与协议。

> 协议要求:`public/` 下的静态文件不经后端鉴权,只放可公开分发的开源内容;GPL/AGPL 或未声明协议的项目不要放进来。
