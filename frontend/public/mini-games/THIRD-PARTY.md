# 第三方开源小游戏

六款站内开源小游戏里,**五款已本地化为 Vue 组件**(玩在外壳里、跟站内主题与横竖屏配方走),
只剩三维弹球·太空军校生是整包静态页由 `/games/embed/space-cadet` 站内 iframe 直跑——它的引擎是
Emscripten 编译的 C++/wasm,没法用 Vue 重写。卡片元数据与站内路由不变,仍由
`src/utils/games/embeddedGames.js` 登记。

| 游戏 | 现状 | 出处 | 协议 |
|---|---|---|---|
| 中国象棋 / 黑白棋 / 消消乐 / 泡泡龙 | 已本地化:`src/views/games/embedded/{Xiangqi,Reversi,Match3,Bubble}Game.vue`,引擎按原文件逐字搬进 `src/utils/games/` | [litegame-hub/litegame-hub.github.io](https://github.com/litegame-hub/litegame-hub.github.io) | MIT © 2026 DongGuoTao |
| 小恐龙快跑 | 已本地化:`src/views/games/embedded/TrexGame.vue` + 引擎 `src/utils/games/trexRunner.js`,精灵图与音效在 `src/assets/games/trex/`(许可随素材:该目录 `LICENSE`) | [wayou/t-rex-runner](https://github.com/wayou/t-rex-runner)(源自 Chromium 的离线小恐龙) | BSD 3-Clause © 2022 牛さとみ / The Chromium Authors |
| 三维弹球·太空军校生 | 仍为整包静态页:`public/mini-games/space-cadet/`,iframe 直跑 | 引擎 [k4zmu2a/SpaceCadetPinball](https://github.com/k4zmu2a/SpaceCadetPinball)(反编译 Windows 自带的 `pinball.exe`),网页构建取 [alula/SpaceCadetPinball](https://github.com/alula/SpaceCadetPinball) 的 Emscripten 产物 | 引擎 MIT © k4zmu2a 及贡献者;原始美术/音效 © Microsoft / Cinematronics / Maxis,已取得授权在本站分发 |

## 本地化改了什么

- 游戏规则、判分、动画时序按原文件逐字保留(引擎层只补导出与少量适配:尺寸重算、音效注入、销毁清理),
  改的是外壳与界面:统一 `GameShell`(标题/规则/全屏/宽屏/横竖屏配方)、中英文案走 i18n、
  弹窗与开始/结算层换成站内组件、`localStorage` 最好成绩键名沿用原站。
- 素材不再从 `public/` 取:小恐龙的精灵图与音效改 ESM 导入(`src/assets/games/trex/`),
  符合「位图资产放 `src/assets/`,宽度按渲染 2 倍封顶」的规定。
- 小恐龙的 BSD 3-Clause 许可全文随素材留在 `src/assets/games/trex/LICENSE`;litegame 系的 MIT
  声明以本文件为准(MIT 要求保留版权与许可声明,移除静态副本后由这里承担)。
- 各组件文件头都写了出处与「只改外壳」的说明,便于日后对照原文件。

## 本地改动记录(space-cadet)

- `space-cadet/index.html`:重写外壳(去掉 Win95 假窗口与状态条),深色底;补触屏手柄(左/右挡板、发球、开新局)把合成按键事件投递到 SDL 注册的监听目标;引擎三件套 `SpaceCadetPinball.js` / `.wasm` / `.data` 原样未改。
  - **主画面与记分板拆成两块**:引擎只往一张 canvas 上画整张机台(`table.bmp` 642×525,底部 467–524 共 58 行是记分带),wasm 没有分数接口、拿不到 DOM 级记分控件,所以拆分是像素级的——
    - 给 canvas 固定的 642:525 比例(引擎铺满窗口,比例对上后画布坐标与位图坐标 1:1),`#field` 用 `overflow:hidden` 只露出上面 467 行,主画面零拷贝;
    - `#hud` 每帧把底带 `drawImage` 过来(尺寸按可用宽算,`imageSmoothingEnabled=false` 保持点阵字清晰);
    - 布局按可用空间反推:`fieldW = min(availW, (availH-预留)*642/525)`,`fieldH = fieldW*467/642`,`hudH = fieldW*58/642`;
    - 捕获需要 `preserveDrawingBuffer:true`,在引擎脚本之前包一层 `HTMLCanvasElement.prototype.getContext` 补上(否则读到的整帧全黑);
    - 兜底:引擎跑起来后探针发现底带一直全黑,就 `no-split` 退回整张机台,不至于只剩黑块。
  - **竖屏排布(触屏)**:记分板贴顶、主画面居中、手柄贴底一行(左挡板 / 发球+新局 / 右挡板,`clamp()` 按屏高放大);机台是 642:467 的横图,竖屏下宽度吃满后高度必然有余,`space-between` 把余量均分给上下两段。横屏改成两侧放手柄、机台居中不被遮挡。齿轮与键盘改绑在 `pointer: coarse` 下隐藏(手机上没用)。
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

1. **先判断能不能自研**:纯 JS/HTML 的整包按下面的本地化路线走成 Vue 组件;含 wasm 引擎
   (Emscripten/C++ 编译产物)的重写成本不现实,才退回整包静态页。
2. 本地化:引擎搬 `src/utils/games/`(保留原注释与结构)、界面写成 `src/views/games/embedded/XxxGame.vue`、
   素材进 `src/assets/games/`,在 `EmbedGame.vue` 的 `LOCAL_EMBEDDED` 登记 slug,
   并在 `views/games/gameShellAdoption.spec.js` 的 `MIGRATED` 里加文件名。
3. 整包静态页:放 `public/mini-games/<slug>/`(入口必须是 `index.html`,只用相对路径)。
4. 两条路线都要在 `src/utils/games/embeddedGames.js` 加一行元数据、`src/views/games/Games.vue` 补一个图标、
   i18n 补 `games.embedded.<slug>.title/desc` 中英文案,最后在本文件补出处与协议。

> 协议要求:`public/` 下的静态文件不经后端鉴权,只放可公开分发的开源内容;GPL/AGPL 或未声明协议的项目不要放进来。
