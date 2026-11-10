# Android 第二批 · 开工提示词(P1 一口气:B1 收尾 + B2-L1 + B5 + B3)

> 复制本文件全文给新会话即可开工。
> 关联计划:`docs/设计想法/功能/Android客户端-能力扩展-计划.md`(下称「计划」,A/B 编号均指该文件)。
> **本批不是从零开工**:B1 代码已存在于当前工作树(未提交),先读 §2 再动手。

## 0. 一句话目标

把第二批剩下的四件事一次做完:**B1 相册自动备份收尾并出包**、**B2-L1 音乐锁屏播控**、**B5 游戏手柄**、**B3 平板看板/相框**;全部沿用既有壳内范式,不引第三方原生包。

## 1. 开工前必读(按顺序,别跳)

1. `AGENTS.md` —— 硬性禁令、后端规范 8/9/10、前端规范 13/14/15/17/20、Android 相关段落。
2. `docs/设计想法/功能/Android客户端-能力扩展-计划.md` —— §0 决策(D1–D6)、§2 现状、§5.5(B1)、§5.6(B2-L1)、§5.7(B3)、§5.8(B5)、§6、§7、§8 风险、§11 开放问题。
3. `docs/踩坑速查.md` §12(Android 壳)、§6(令牌)、§7.2(PWA 缓存)、§3.67/§3.68(游戏全屏)。
4. `docs/daily/2026-11-10.md` 的「Android 第二批 P0」小节与文末「未竟」——上一轮的真机取证方式、模拟器坑都在那里。
5. 代码现场:
   - B1:`backend/.../controller/{PhotoController,AlbumController}.java`、`service/AlbumService.java`、`dto/{AlbumBackupDTO,PhotoBackupResult}.java`、`mapper/{AlbumMapper,PhotoMapper}.xml`、`resources/{schema.sql,migrations.sql}`;`frontend/android/.../photo/PhotoBackupPlugin.java`;`frontend/src/native/photoBackup.js`;`frontend/src/views/settings/AppSettings.vue`。
   - B2:`frontend/src/components/MusicPlayer.vue`(全局播放器)与 `frontend/src/views/music/Music.vue`(页内试听弹窗)。
   - B5:`frontend/src/composables/useGameView.js`(所有小游戏共用)与 `frontend/src/views/games/*.vue` 的键盘映射。
   - B3:`frontend/src/views/Wallpaper.vue`(时钟/天气/照片/光影已齐全,可大量复用)、`frontend/src/native/screen.js` 的 `setKeepOn`、`frontend/src/router/index.js` 的 standalone 路由、`frontend/src/components/biometric` 或 `native/biometric.js`。

## 2. 多会话前置(必须先做,违反会返工)

- 先跑 `git status --short`、`git log --oneline -5`、`git tag --list "V11.8*"`。
- **当前工作树里至少有三拨未提交改动**:暖居背景(`WarmLayout.vue`/`warmBgIdle*`)、Android P0 验证(`VERSION`/`docs/daily`)、**B1 相册备份**。不要整文件重写共享文件,只做定向 `edit`。
- B1 已写文件(不要重写,只补漏):
  - 后端:`PhotoController#uploadBackup`、`AlbumController#backup`、`AlbumService#backupAlbum/addBackupPhoto`、`AlbumBackupDTO`、`PhotoBackupResult`、`Photo.checksum`、`Album.backupKey`、两个 mapper XML、`schema.sql` 的 `content_photo.checksum` + `content_photo_album.backup_key`、`migrations.sql` 末尾幂等迁移。
  - 原生:`photo/PhotoBackupPlugin.java`、`AndroidManifest.xml` 的两个读相册权限、`MainActivity` 的 `registerPlugin`、`src/debug/AndroidManifest.xml`(仅 debug 允许明文)。
  - 前端:`native/photoBackup.js`、`api/index.js` 的 `albumApi.backup`/`photoApi.backupUpload`、`AppSettings.vue` 的备份块、`i18n/{zh-CN,en}.js` 的 `appSettings.photoBackup*`、`native/index.js` 的一行动态 import。
- **分工建议**:
  - 若当前会话仍在改 B1:新会话先做 **B2-L1 + B5**(只碰音乐/游戏/`useGameView`,不碰 `AppSettings.vue` 与 i18n 的备份键),等 B1 提交后再做 B3 与 B1 收尾。
  - 若 B1 已提交:按 §3 顺序全做。
- **禁止**:`git stash`、`git add -A`/`git add .`、并行 `npm run build`、并行出 APK、并行 `mvn clean`。`git add` 只列具体文件。
- 版本号:`VERSION` 当前工作树是 `V11.87`(另一会话占用,HEAD 还是 `V11.85`)。先确认 `V11.87` 是否已被提交/发布/打 tag;已被占用就顺延到 `V11.88`,不要复用别人的版本号。

## 3. 范围与顺序(总估算 3–4.5 人日)

| 顺序 | 任务 | 估算 | 交付 |
|---|---|---|---|
| 1 | **B1 收尾**(代码已在,只补验证/文档/真机) | 0.5–1 天 | 能出包、能验收、文档闭环 |
| 2 | **B2-L1 音乐锁屏播控** | 0.5 天 | `navigator.mediaSession` + 锁屏/耳机键 |
| 3 | **B5 游戏手柄** | 1–2 天 | `useGamepad` + 全部小游戏可用 |
| 4 | **B3 平板看板/相框** | 1.5–2 天 | `/kiosk` 常亮页 + 防烧屏 + 长按退出 |

> B3 依赖「家里有闲置平板」这个前提(计划 Q4)。没有平板就跳过 B3,只交 B1/B2-L1/B5,并在当日文档写明延期原因。

## 4. B1 收尾(先做)

### 4.1 当前实现口径(已定,不要推翻)

- **只在前台跑**:App 启动、回前台、用户点「立即备份」时触发;没有 Worker/前台服务。原因:后台上传不能用 `type=WIDGET` 只读令牌(写接口不放行),另开写令牌通道超出本批安全面;计划 §5.5 写的 Worker 与实现不同,记进计划「实施差异」。
- **首次只备份近 90 天**(`DEFAULT_DAYS = 90`);「全部备份」二次确认。
- **幂等靠服务端 `checksum` 唯一键**:客户端算 SHA-256,服务端 `(family_id, checksum)` 命中即返回 `skipped=true`;命中回收站里的行也不复活。
- **备份相册按 `backup_key = android:<设备标识>` 找**,不按名字找;服务端建 `private` 相册,照片可见性 `FAMILY`;被删进回收站会恢复而不是重建。
- 单张 >30MB 跳过但游标照走;单张失败整轮停下,游标停在上一张成功处,下次续传。
- 不刷积分。

### 4.2 必须补齐(按此清单逐条销项)

1. **编译与单测**:`cd backend; .\mvnw.cmd -B clean package`(基线 337 tests)。前端 `npm run lint`、`npx vitest run`(基线 937)、改动文件 `prettier --write`。
2. **空库结构实测**:空库全量导入 `schema.sql`,再把 `migrations.sql` 跑两遍确认幂等;核对 `content_photo.checksum`、`content_photo_album.backup_key` 与两个唯一索引都在。表数仍为 92。
3. **前端可测性补强(建议做)**:把 `photoBackup.js` 里「90 天窗口起点 / 游标读写 / backupKey 生成」抽成无副作用纯函数并加 spec(参考 `utils/warmBgIdle.spec.js` 的写法),这样不用真机也能锁住口径。
4. **真机/模拟器验收**(至少跑完 1–5):
   - 授权后首次备份:90 天外的照片不上传;近 90 天进入「手机备份」相册。
   - 同一张照片再备份一次 → `skipped`,磁盘不新增文件(看 `content_photo` 行数与 `uploads/pictures/手机备份/`)。
   - 断网/杀进程后续传:已传的不重传,失败那张下次继续。
   - 应用里删除一张已备份照片再备份 → 不产生重复条目(命中回收站行,`skipped`)。
   - 关 Wi-Fi + 开「仅 Wi-Fi」→ 不备份并提示;`adb shell dumpsys battery set ac 0` + 开「仅充电」→ 不备份;恢复后能继续。
   - 权限被撤销(`adb shell pm revoke top.ihomy.app android.permission.READ_MEDIA_IMAGES`)→ 设置页提示重新授权,不崩。
   - Android 14 的「仅选择部分照片」授权要单独看一眼:当前只认 `granted`,如果部分授权会被当作全量,需在 UI 如实提示(补 `READ_MEDIA_VISUAL_USER_SELECTED` 或降级说明)。
5. **文档**:
   - `docs/daily/2026-11-10.md` 追加 B1 小节(变更/决策/接口/表/踩坑/验证/未验项)。
   - 计划 §5.5 的「实施差异」:Worker → 前台触发;`source_device_id` → `backup_key`;`content_photo_album` 也加了列(计划原只写 `content_photo`)。
   - `docs/需求设计说明书.md`:§4.3.3 补备份口径;§6.2 的 `content_photo`/`content_photo_album` 列说明;§7.4 补 `POST /album/backup` 与 `POST /album/{albumId}/photos/backup`。
   - `docs/设计想法/README.md` 的状态行、`docs/需求设计说明书.md` §9.1 的 Android 行按实际进度改写。
6. **出包**:`cd frontend; npm run app:sync; cd android; .\gradlew :app:assembleRelease`;确认 `webContentsDebuggingEnabled` 为 false、`VERSION`/`versionName` 一致。**含原生改动的版本必须重新出 APK,`deploy.ps1` 走全量。**

### 4.3 已知待补强(不阻塞本批,记进文档)

- `photoBackupPending` 是「窗口内照片总数」的近似,不是「减去已传」的精确待办数;文案已写「待备份」,如要精确需服务端计数接口。
- 备份相册目前只在设置页可达;相册页没有「自动备份」入口(可作为后续 P2)。
- HEIC/超大图、实况照片的 MOV 不在本批范围。
- 没有后台 Worker 是明确取舍,不要偷偷加前台服务。

## 5. B2-L1 音乐锁屏播控(0.5 天,纯前端)

目标:锁屏与耳机键能看到曲名并控制播放/暂停/上一首/下一首/拖动进度;不做后台续播(那是 L2,明确不做)。

实现:

1. 新建 `frontend/src/utils/mediaSession.js`(或 `composables/useMediaSession.js`),只做三件事:
   - `bindMediaSession(audioEl, track)`:设置 `navigator.mediaSession.metadata = new MediaMetadata({...})`,封面用绝对 URL(`new URL(url, location.origin).href`),没有封面就不传 `artwork`;
   - `updatePosition(audioEl)`:在 `timeupdate`/`durationchange` 调 `setPositionState({duration, position, playbackRate})`,节流到 1 秒一次;
   - `clearMediaSession()`:卸载时清 handlers 与 metadata。
2. `components/MusicPlayer.vue`(全局播放器,优先级最高)接入:
   - `metadata` 跟随 `currentTrack`/`playSrc`;
   - `setActionHandler`:`play`/`pause`/`previoustrack`/`nexttrack`/`seekto`/`seekbackward`/`seekforward`;
   - `audioEl` 的 `play`/`pause`/`ended` 同步 `navigator.mediaSession.playbackState`;
   - `onBeforeUnmount` 清理。
3. `views/music/Music.vue` 的试听弹窗(`<audio controls autoplay>`)也接一份;弹窗关闭时 `clearMediaSession()`,避免锁屏还显示已关闭的曲目。
4. 守卫:`'mediaSession' in navigator`、`typeof MediaMetadata !== 'undefined'`;Web/桌面浏览器不支持时静默跳过。
5. 不新增原生代码、不加权限、不改播放器结构。

验收:`dumpsys media_session` 能看到 ihomy 会话与曲名;锁屏出现播放控件;耳机键播放/暂停/切歌生效;锁屏拖动进度后页内进度跟随;切歌后 metadata 更新;关闭弹窗/离开页面会话清空。

## 6. B5 游戏手柄(1–2 天,纯前端)

目标:接上蓝牙/USB 手柄后,小游戏方向键与 ABXY 可用;模拟器只显示「已连接」提示,不重复派发(EmulatorJS 自带手柄)。

实现:

1. 新建 `frontend/src/composables/useGamepad.js`:
   - rAF 轮询 `navigator.getGamepads()`,**只派发 `KeyboardEvent`,不写任何响应式 ref**(AGENTS 前端规范 15/17);
   - 映射:左摇杆/十字键 → `ArrowUp/Down/Left/Right`;A → `Enter`,B → `Escape`,X → `Space`,Y → `KeyR`(可按游戏需要微调,映射表集中在一个常量里);
   - 死区 0.35–0.5,**边沿触发**(按钮从松到按只发一次,长按不连发;方向键可按游戏需要允许 120ms 连发);
   - 只在页面可见、当前焦点不是 `input/textarea/select/contenteditable`、没有弹窗时派发;`document.dispatchEvent(new KeyboardEvent('keydown', {key, code, bubbles:true}))`(气泡到 window,覆盖两类监听);
   - `onBeforeUnmount` 停 rAF、清状态。
2. 在 `useGameView.js` 里统一接入(所有小游戏都走它),并导出一个低频 `gamepadConnected` ref 供顶栏提示;`views/games/EmulatorPlayer.vue` 不接适配器,只显示「手柄由模拟器直接支持」。
3. UI:`GameTopActions.vue` 加一个仅在 `gamepadConnected` 时出现的提示标签(文案走 i18n);首次按手柄键才显示。
4. 纯函数化映射(`mapGamepadState(pads, prev)` → `{keys, nextPrev}`)并加 vitest spec,真机只验最终手感。

验收(真机/带手柄的模拟器):接上手柄进 2048/贪吃蛇/俄罗斯方块/连连看,方向键与 ABXY 可操作;断开后触屏立刻恢复;打字/输入框聚焦时手柄不误触;模拟器不出现双输入。

## 7. B3 平板看板/相框(1.5–2 天,可跳过)

目标:横屏平板进 `/kiosk` 后常亮,左侧大时钟+日期+天气,右侧今日日程,底部照片缓慢轮播;长按 3 秒 + 指纹退出;1 小时不烧屏。

实现:

1. 新建 `frontend/src/views/Kiosk.vue` + 路由 `{ path: '/kiosk', meta: { requiresAuth: true, standalone: true } }`:
   - **大量复用 `Wallpaper.vue`**:时钟定时器、`useSunLight` 天气、`publicApi.getHome()` 的照片/家庭名、`SunLightLayer`;不要重写一套。
   - 日程改用 `scheduleApi.upcoming(7)`(比首页 stats 更全),只展示时间/标题/来源,只读。
   - 照片 15–30 秒轮播,淡入淡出用 CSS transition,不用 GSAP 持续动画。
2. 常亮:`onMounted` 调 `native/screen.js` 的 `setKeepOn(true)`,`onBeforeUnmount` 必须 `setKeepOn(false)`(不进壳时是 no-op)。
3. 防烧屏:整块内容每 60 秒位移 ±8px(交替),用 CSS `transform` + `setInterval`,**禁止 rAF 每帧写 ref**;时钟文字也参与位移。
4. 退出:右下角隐形热区长按 3 秒 → `IhomyBiometric.verify()`;取消留在原地,成功 `router.replace('/')` 并 `setKeepOn(false)`;无生物识别时降级为「长按 3 秒 + 二次确认」。
5. 入口:`AppSettings.vue` 加「看板模式」按钮(仅壳内/横屏平板提示一次,不强制);不加入 `navModules.js`(避免手机导航多一个入口)。
6. 样式:长开页**禁用 `backdrop-filter`**(AGENTS 前端规范 7),配色走主题变量;`meta.standalone` 的根类与 `App.vue` 的 standalone 进出初始化按 AGENTS「独立产品页」规则处理。
7. 鉴权:standalone + requiresAuth 下 401 不会自动顶登录页,页面要给「登录后可显示家庭内容」的空态/入口,不能白屏。

验收:横屏平板进 `/kiosk` 常亮;`dumpsys window` 能看到 `KEEP_SCREEN_ON`;离开后消失;照片轮播、日程、时钟、天气都正常;长按 3 秒 + 指纹可退出;取消不退;1 小时后元素位置有位移(截图对比);无平板则跳过并在文档写明。

## 8. 硬性规范(违反即返工)

- 原生:包 `top.ihomy.app.<域>`;插件名 `IhomyXxx`;`registerPlugin` 在 `super.onCreate` **之前**;文件读写只允许白名单目录 + 挡 `../`。
- 前端:原生能力一律 `src/native/*.js` + `native/index.js` **动态 import**;`isAndroidApp()` 守门;可见文本全走 `$t()`,中英键对齐,`{`/`}`/`@` 转义;`computed` 内不写随机/时间/副作用。
- 后端:Controller 只校验 / Service 单实现 / 自定义 SQL 进 XML;写接口 `@OperationLog`;`@RequirePermission` 只写方法上且权限码必须进种子;UPDATE 不先 select,计数用原子增量。
- 禁止:①读写 `@capacitor/local-notifications` 私有存储;②原生注册闹钟;③常驻保活/前台服务(除 L2,本批不做);④`System.out.println`/`printStackTrace`;⑤首屏静态 import Capacitor;⑥`git add -A`/`git stash`/并行构建。
- 文档话术:用户可见文案只讲「能做什么/发生了什么」,不写表名/字段/内部枚举。

## 9. 验证(每一项做完都要过)

```powershell
cd frontend; npm run lint                 # 0 error 0 warning
cd frontend; npx vitest run               # 全绿(基线 937,新增用例要说明)
cd frontend; npx prettier --write <改动文件>
cd backend;  .\mvnw.cmd -B clean package  # B1 已改后端,必须编译
cd frontend; npm run app:sync
cd frontend\android; .\gradlew :app:assembleRelease
```

- 构建后看首屏入口 chunk 是否基本不变(B2/B5/B3 都是懒加载;若增长要说明原因)。
- 文档闸门:`bash scripts/gen-code-map.sh`、`bash scripts/doc-drift-check.sh` 全 [OK]。
- 模拟器/真机命令参考:权限 `dumpsys package`、通知 `dumpsys notification`、媒体会话 `dumpsys media_session`、常亮 `dumpsys window`、电量 `dumpsys battery set ac 0/1`。OneDrive 目录 Gradle 删不掉 `build` 时先 `cmd /c "rmdir /s /q frontend\android\app\build"`。

## 10. 收尾与发布

- 轻量收尾:追加 `docs/daily/2026-11-10.md` 当日小节(变更/决策/接口/表/踩坑/验证/未验真机项)→ 重建代码地图 → drift check 全绿 → `VERSION` 统一(见 §2 的版本核对)→ `git add` 指定文件 → commit。
- **发布需用户明确指示**:含原生改动必须重新出 APK,`deploy.ps1` 走全量;APK 上传 `uploads/app/`,`external.yml` 的 `latest-version` 必须与 `VERSION` 同步,否则应用内更新会指向不存在的包;`git push` 与部署前先问。
- 打 tag:`V<主>.<次>`,消息 = 日期 + 一句话;随发布 `git push origin <tag>`。

## 11. 本次明确不做

- 不做 B2-L2(原生 ExoPlayer + 前台服务)、C1 保险箱自动填充、C2 推送通道、投屏、地理围栏、增量更新、扫码。
- 不做 B1 的后台 Worker/常驻上传(安全面与 2GB 生产机都不允许);不把写接口加进 WIDGET 白名单。
- 不碰第一批的真机验收清单(那边 §9.2),只顺带核本次涉及的功能。
- 不新增权限(除 B1 已有的读相册两个);不动家庭隔离与权限模型。

## 12. 交付格式

每完成一项,先给一段**「改了什么 / 为什么这么选 / 验证证据」**(证据要具体:命令 + 关键输出行),再进下一项。不要一次性堆到最后汇报;不要在没验证前说「已完成」。真机未验的项单独列「真机待验」。
