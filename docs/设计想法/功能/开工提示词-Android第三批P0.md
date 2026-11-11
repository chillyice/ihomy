# Android 第三批 · 开工提示词(P0 手感包)

> 复制本文件全文给新会话即可开工。
> 关联计划:`docs/设计想法/功能/Android客户端-交互与大屏-计划.md`(下称「计划」,H1–H6 均指该文件)。
> **本批只做手感,不做大屏**(T1–T7 属 P1,别顺手开工)。

## 0. 一句话目标

给壳内交互补上「点得到、有回应」:**触觉反馈(H1)**、**长按菜单(H2)**、**列表滑动(H3)**、**下拉刷新(H4)**、**分享出口(H5)**、**通知设置入口(H6)**。以纯前端为主,只有触觉按计划写一个约 30 行的原生插件;不碰后端、不改表、不新增 npm 依赖。

## 1. 开工前必读(按顺序,别跳)

1. `AGENTS.md` —— 硬性禁令、多会话规则、前端规范 8(拖拽/指针捕获)、15(动画优先级)、17(computed 纯函数)、20(页面/工具栏/横滑条)、24(横滑手势只做意图识别)。
2. 计划 —— §0.5 决策(D1–D8)、§2 现状、§5.1–§5.4(H1–H4 详细设计)、§5.6 的 H6、§8 风险、§11 开放问题(Q4 触觉默认开、Q6 分享入口两处)。
3. `docs/踩坑速查.md` §3.35(指针捕获只在真正拖动时做)、§3.61(横滑手势与滚动抢事件)、§3.70(横滑条不占布局盒)、§12(Android 壳)。
4. `docs/UI设计提示词.md` §18a(按钮/圆角)、§18e(表格操作列只用图标按钮)、§29(z 层级铁律与弹窗宽度)。
5. 代码现场:
   - 手势与拖拽:`frontend/src/composables/useDragResize.js`、`frontend/src/utils/dragInsert.js`、`frontend/src/layouts/MobileLayout.vue` 的 `onTouchStart`(现有横滑意图识别)。
   - 列表行:提醒 `frontend/src/views/schedule/ScheduleRow.vue`;账目 `frontend/src/views/book/BookDaily.vue`;通知 `frontend/src/views/notification/Notification.vue`。
   - 分享:`frontend/src/native/links.js`(已有 `Filesystem.downloadFile` + `Share.share` 链路)。
   - 照片:`frontend/src/components/PhotoViewer.vue`。
   - 设置页:`frontend/src/views/settings/AppSettings.vue`;本机数据清理清单 `frontend/src/views/settings/LightSettings.vue:135–151`。
   - 原生:`frontend/android/app/src/main/java/top/ihomy/app/MainActivity.java`、`widget/WidgetBridgePlugin.java`、`AndroidManifest.xml`。
   - 前端桥出口:`frontend/src/native/index.js`(所有原生能力必须动态 import,首屏零运行时)。

## 2. 多会话前置(必须先做,违反会返工)

- 先跑:`git status --short`、`git log --oneline -5`、`cat VERSION`。
- 当前事实(2026-11-11 交班时):计划与提示词已提交;`VERSION` 在 **V11.94** 上下浮动(另一会话同日发了通话权限修复),**HEAD 与版本号开工时以 `git log`/`cat VERSION` 实测为准**,不要照抄提示词里的数字。工作树里**他人未提交**的 `frontend/src/components/warm/WarmLayout.vue` 与 `frontend/src/utils/warmBgIdle.js` 删除是别的会话在做暖居背景,**不要碰、不要回滚、不要整文件重写**。
- 共享文件只做定向 `Edit`:`AppSettings.vue`、`main.css`、`i18n/zh-CN.js`、`i18n/en.js`、`Home.vue`。
- **禁止**:`git stash`、`git add -A`/`git add .`、并行 `npm run build`、并行出 APK、`taskkill /IM java.exe`。`git add` 只列具体文件。
- 版本号:**开工不占版本**;收尾时读当前 `VERSION` 再 +1(次位满 100 进位主版本)。别用开工快照的数字。

## 3. 范围与顺序(总估算 4–5 人日)

| 顺序 | 任务 | 估算 | 交付 |
|---|---|---|---|
| 1 | **H1** 触觉反馈 | 0.5 天 | `IhomyHaptics` 插件(或 Web 降级)+ `native/haptics.js` + 设置页开关 |
| 2 | **H2** 长按菜单 + **H3** 列表滑动 | 2 天 | `useRowGestures` 手势内核 + `LongPressMenu.vue` + 提醒/账目/通知三处接入 |
| 3 | **H4** 下拉刷新 | 0.5 天 | `usePullRefresh` + 首页/通知/相册三页接入 |
| 4 | **H5** 分享出口 | 1 天 | `native/shareOut.js` + 5 个入口 |
| 5 | **H6** 通知设置入口 | 0.5 天 | `WidgetBridgePlugin` 加一个方法 + 设置页按钮 |

**顺序是刻意的**:H1 先落地,后面每做一项都能顺手加触感;H2 与 H3 共用同一个手势内核,必须一起做。

## 4. 逐项实现要点

### H1 触觉反馈

**先做 10 分钟实测再决定实现路径**:

- 在壳内临时加一个按钮调 `navigator.vibrate(10)`(或 `adb shell am start` 后手工触发)。
- 若 WebView 能震、**且**系统「触感反馈」关掉后确实不再震 → 走 Web 方案:只加 `VIBRATE` 权限 + `utils/haptics.js`,不写插件;把结论写进实施差异。
- 若不尊重系统开关或静音模式仍震 → 按下面写插件(计划的默认路径)。

**原生**(包 `top.ihomy.app.haptics`):

- `HapticsPlugin.java`,`@CapacitorPlugin(name = "IhomyHaptics")`,一个方法 `play({ kind })`:
  - `tap` → `HapticFeedbackConstants.KEYBOARD_TAP`(轻)
  - `select` → `CLOCK_TICK` / `CONTEXT_CLICK`
  - `success` → `CONFIRM`
  - `warning` → `LONG_PRESS`
  - `error` → `REJECT`
  - 常量不存在或低版本 → `VibrationEffect.createOneShot(12/20/30/40ms, DEFAULT_AMPLITUDE)` 兜底。
- 进方法先判:`vibrator.hasVibrator()`;`Settings.System.getInt(resolver, HAPTIC_FEEDBACK_ENABLED, 1) == 0` → 直接 `resolve()`;`AudioManager.getRingerMode() == RINGER_MODE_SILENT` → 也 no-op(安静场景不打扰)。
- `MainActivity` 在 `super.onCreate` **之前** `registerPlugin(HapticsPlugin.class)`(晚注册 JS 侧报 plugin not implemented)。
- `AndroidManifest.xml` 加 `<uses-permission android:name="android.permission.VIBRATE" />`(normal 权限,不弹窗)。

**前端**:

- `src/native/haptics.js`:动态取插件,未注册时 `play()` 静默 no-op(Web 端零影响)。
- `src/utils/haptics.js`:统一出口 `haptic(kind)`,同 kind 80ms 节流,总开关读 `ihomy:haptics:v1`(localStorage)。
- **新键必须登记**到 `views/settings/LightSettings.vue` 的清理清单(该文件注释里列了「当前确实在写的键」)。
- 首批只接 6 处,不铺满:①提醒/任务勾选 ②长按触发 ③拖拽落位(`useWidgetDrag`/`useDragResize` 结束)④保存成功 ⑤表单校验失败 ⑥下拉刷新触发。
- 设置页「安卓客户端」段加「触觉反馈」开关(默认开),副标签说明「跟随系统触感设置」;中英 i18n 对齐。
- **禁止**:给滚动、hover、每次普通点击加震动。

**验收**:勾选提醒震一次;系统设置关「触感反馈」后不再震;静音模式下不震。

### H2 长按菜单 + H3 列表滑动(同一个内核)

**新增 `src/composables/useRowGestures.js`**(只做意图识别,不 `preventDefault`):

| 手势 | 规则 | 取消 |
|---|---|---|
| 长按 | `pointerdown` 起 480ms 定时器 | 位移 >10px / `pointerup` / `pointercancel` |
| 左右滑 | 水平位移 >36px 且 `|dx| > 2|dy|` | 松手 <60% 回弹 |

- **起手排除**(命中任一直接不接管):`closest('input, textarea, [contenteditable], .el-dialog, [data-no-gesture], [data-drag-handle]')`;父级 `overflow-x: auto/scroll` 且 `scrollWidth > clientWidth` 的容器(相册横条、表情条)。
- 行容器加 `touch-action: pan-y`(只让纵向滚动,横向留给手势),**不要**在 `touchmove` 里 `preventDefault`。
- 位移直接写 `el.style.transform`,**禁止 rAF 每帧写 Vue ref**;手势结束才提交状态。
- **长按后吞掉补发的 click**:长按打开菜单后,在 capture 阶段吞一次 `click`(记录时间戳,300ms 内),否则会同时进详情页(与「长按直填」同一类坑)。
- 同一时刻只允许一行处于展开/菜单状态。

**`components/ui/LongPressMenu.vue`**:

- `Teleport` 到 `body`,`position: fixed` 跟随指针,z-index 用 `var(--z-toast)`(不要写裸 2000)。
- 菜单项由 `items` 传入;`Esc` / 点空白关闭;键盘可 Tab/Enter。
- **单一菜单源**:行尾「更多」按钮用 `el-dropdown trigger="click"`,长按调同一个 `dropdownRef.handleOpen()`;不要维护两套菜单。
- 行尾按钮必须有 `aria-label`,可点击元素走 `v-a11y-click`(规范 12)。

**首批只接三处**(复用行上已有事件,不新写业务):

| 位置 | 长按/滑动项 |
|---|---|
| `ScheduleRow.vue` | 完成 / 推迟 1 小时 / 编辑 / 删除 |
| `BookDaily.vue` 账目行 | 复制摘要 / 编辑 / 删除 |
| `Notification.vue` 行 | 标为已读 / 删除 |

- 滑动:左滑露「删除」、右滑露「完成/已读」;删除类必须 `ElMessageBox.confirm` 二次确认。
- **不在**相册瀑布(`PhotoCascade.vue`)、横滑内容条、游戏容器启用。

**验收**:长按 480ms 弹菜单、移动 10px 取消;长按后不再触发点击进详情;相册横滑不受影响;滑动删除有确认。

### H4 下拉刷新

**新增 `src/composables/usePullRefresh.js`**:

- 只在容器 `scrollTop === 0` 且向下拖 >56px 触发;`|dy| > 2|dx|` 才算下拉。
- 指示器位移直接写 style;松手调用传入的 `refresh()`;最短转 300ms 再收,失败 `ElMessage` 提示。
- **白名单**:`Home.vue`、`Notification.vue`、`Album.vue`。
- **明确不接**:聊天(向上翻历史)、月历、全屏游戏、电子书。

**验收**:首页下拉出指示器并刷新;聊天向上翻历史不触发;横滑相册条不触发。

### H5 分享出口

**新增 `src/native/shareOut.js`**,复用 `links.js` 的既有链路:

```
shareText(text, title)
shareUrl(url, title)
shareFiles(urls, title)   // 逐张 Filesystem.downloadFile(Bearer) → Share.share({ files })
```

- 单文件上限 30MB(与相册备份同口径);多图**串行**下载(并发 1),落 `Directory.Cache/ihomy-share/`;文件名过 `filenameFrom` 同口径清洗。
- Web/非壳环境:`navigator.share` 可用则用,否则复制到剪贴板并提示。
- 入口 5 个:照片查看器(`PhotoViewer.vue`)、日记详情、博客详情、账目行「复制摘要」(text)、文件列表「分享」。
- **入口按权限渲染**:游客/无读权限不显示;不提供「复制直链」(私有文件靠 cookie,复制出去无意义且泄露)。
- 分享内容不做二次压缩、不改文件内容。

**验收**:照片查看器分享原图到微信;私有照片在无权限账号下看不到分享入口;多选 3 张串行下载后一次拉起分享面板。

### H6 通知设置入口

- **不新增插件**:在 `widget/WidgetBridgePlugin.java` 加一个 `openNotificationSettings()`:

```java
Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, getContext().getPackageName())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
getContext().startActivity(i);
call.resolve();
```

- `src/native/widgets.js` 暴露同名方法;`AppSettings.vue` 加「通知设置」按钮,副标签列出 4 条通道(提醒/家庭动态/每日天气/应用更新)。
- **不重建通道、不拆通道**(计划 D7):参数被系统冻结,重建会重置用户已调的设置。

**验收**:点按钮直达本 App 的系统通知页,4 条通道可见;关掉「每日天气」后不再收到天气通知。

## 5. 硬性规范(违反即返工)

- **原生**:包 `top.ihomy.app.<域>`;`registerPlugin` 必须在 `super.onCreate` 之前;插件方法只做系统调用,不写业务;日志用 `Log`,禁 `printStackTrace`。
- **前端**:新能力一律 `src/native/*.js` + `native/index.js` 动态 import;`isAndroidApp()` 守门;可见文本全走 `$t()`,中英键对齐,文案里 `{`/`}`/`@` 转义;改动文件跑 `prettier --write`。
- **手势**:只做意图识别、不 `preventDefault`;不在横滚容器/输入框/弹窗/拖拽手柄里接管;长按必须有等价按钮(D4)。
- **动画**:位移直接写 style;禁止 rAF 每帧写 ref;`computed` 内禁副作用。
- **不动**:后端接口、表结构、令牌白名单、通知通道、平板双栏(T1–T7)、全局搜索(S1)、老人/儿童模式(C1/C2)。
- **不加依赖**:不装 `@capacitor/haptics` 或任何新 npm 包。

## 6. 验证(每项做完都要过)

```powershell
cd frontend; npm run lint                  # 0 error 0 warning
cd frontend; npx vitest run                # 基线:最近一次 daily 为 967 passed,以开工时实测为准
cd frontend; npx prettier --write <改动文件>
cd frontend; npm run build                 # 注意:不与其他会话并行跑
# 含原生改动时
cd frontend; npm run app:sync
cd frontend\android; .\gradlew :app:compileReleaseJavaWithJavac
cd frontend\android; .\gradlew :app:assembleRelease    # 只在需要出包时
```

**真机验收清单**(模拟器只能验机制,触感/手势/分享必须真机):

1. 勾选提醒 → 一次轻触感;系统触感关闭后不再震;静音模式不震。
2. 长按提醒 480ms 弹菜单;移动 10px 取消;长按后不进详情;行尾「更多」能开同一菜单。
3. 左滑删账目有二次确认;相册横滑不触发删除。
4. 首页下拉刷新;聊天翻历史不触发。
5. 分享照片到微信;无权限账号看不到入口。
6. 设置页「通知设置」直达系统页,4 通道可见。

## 7. 交付与收尾

- 每完成 2–3 项做一次**轻量收尾**:追加当日 `docs/daily/` 变更小节 → `bash scripts/gen-code-map.sh` → `bash scripts/doc-drift-check.sh` 全绿 → `VERSION` 读当前值 +1 → `git add <具体文件>` → commit(只提交本次文件,不 push)。
- 含原生改动(本批 H1 / H6)的版本**必须重新出 APK**;发布时 `deploy.ps1` 走全量,并同步 `external.yml` 的 `latest-version`,否则应用内更新指向不存在的包。
- 真机没验过的项目**在 daily 里写「真机待验」**,不要写成已通过。

## 8. 明确不做 / 容易跑偏的地方

- ❌ 双栏、`isWide`、键盘快捷键、命令面板、全局搜索、拖拽投递(都是 P1/P2)。
- ❌ 老人模式、儿童模式、TTS、PiP(P3,未拍板)。
- ❌ 重建/拆分通知通道(D7);重建后用户已调设置会丢。
- ❌ 为分享新增后端接口或「复制直链」。
- ❌ 把长按/滑动做成全局拦截;只在三处列表行启用。
- ❌ 给所有点击都加触感;只接 6 个语义点。
- ❌ 顺手改 `WarmLayout.vue` / `warmBgIdle*`(别的会话在做)。
- ❌ 用 CSS `order` 重排、用 `v-html` 渲染菜单内容。

## 9. 开工会话第一件事(照着做)

```bash
cd "C:/Users/chill/OneDrive/WorkStation/Projects/ihomy"
git status --short
git log --oneline -5
cat VERSION
# 读计划 §5.1–§5.4 与 §8,再读 §2 代码现场
# 从 H1 的 10 分钟 WebView 实测开始
```
