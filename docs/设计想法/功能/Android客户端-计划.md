# Android 客户端实施计划(套壳 + 原生提醒 + 桌面卡片)

> **状态**:**P0–P2 已落地并上线生产(V11.74–V11.80)**(含站内 `/app` 下载页与 APK 分发);剩真机验收、P3/P4 与补口项(明细见文末「实施差异」)
> **本文是 Android 端实施的唯一口径**:目标、选型、分期、接口契约、表结构草案、验收标准、明确不做。
> **索引**:`docs/需求设计说明书.md` §9.1(规划表行)、`docs/设计想法/README.md`(进行中计划)。
> **动工前必读**:`AGENTS.md`(硬性禁令 / 后端规范 / 前端规范)、`docs/需求设计说明书.md` §2.3 运行环境、§4.3.6 日程提醒、§4.4.4 聊天室;`docs/踩坑速查.md` §2.4(401)、§6(令牌)、§7.2(PWA 缓存)。
> **落地后**:本文移入 `docs/设计想法/完成/`,实现差异写进本文件末尾「实施差异」段,并按轻量收尾更新 `docs/daily/` + 代码地图 + `VERSION`。

---

## 0. 一页摘要

### 0.1 目标(按优先级)

1. **手机上装一个能正常用的 ihomy**:现有 Web 功能(内容/相册/记账/聊天/游戏/工具箱)全部可用。
2. **提醒能真正响**:不打开 App 也能在设定时刻收到系统通知,点通知能直接完成/跳转。
3. **手机/平板桌面小卡片**:今日概览、今日提醒、纪念日倒计时、相册回忆。
4. **顺手把「手机优势」用起来**:分享到家庭、相机直传、保险箱指纹解锁、快捷方式、通知快捷回复。

### 0.2 结论

**Capacitor 套壳 + 少量原生 Kotlin 模块**,不重写前端。

需要新写的原生代码只有三块:

| 原生模块 | 作用 | 为什么不能用 Vue |
|---|---|---|
| App Widget(Jetpack Glance) | 桌面小卡片 | 卡片由系统渲染 RemoteViews,跑不了 WebView/JS |
| AlarmManager + LocalNotifications | 到点响铃 | WebView 后台被冻结,`setTimeout` 不可靠 |
| Intent 接收 / BiometricPrompt / Camera | 分享接收、指纹、拍照 | Web 层拿不到这些系统能力 |

其余全部复用现有 REST + WebSocket。

### 0.3 分期

| 阶段 | 内容 | 估算 | 交付物 |
|---|---|---|---|
| **P0** | 套壳适配(能装能跑) | 3–5 天 | 可侧载 APK,登录/首页/相册/聊天可用 |
| **P1** | 提醒闭环(能响能勾) | 1–1.5 周 | 系统通知 + 通知内完成 + 系统日历兜底 |
| **P2** | 桌面卡片(能看能点) | 1–2 周 | 4 张 Glance 卡片 + WorkManager 同步 |
| **P3** | 高价值增量 | 2–3 周(可挑) | 分享/相机/指纹/快捷方式/快捷回复 |
| **P4** | 可选 | 按需 | 自动备份/地理围栏/平板看板/推送/手表 |

**P0–P2 ≈ 3~4 周**即覆盖用户的核心诉求。

### 0.4 关键决策(后续不要重新讨论)

| # | 决策 | 理由 | 被否备选 |
|---|---|---|---|
| D1 | 用 Capacitor,不重写 | 30+ 模块、Element Plus 深度定制,重写成本高一个数量级 | 全原生 Kotlin / Flutter / RN |
| D2 | 打包本地 `dist`,不用 `server.url` 远程模式 | 秒开、弱网可用、不受服务端发布节奏影响 | 远程加载(热更新但依赖网络且商店审核风险) |
| D3 | 一期**不自建推送**,只做本地通知 | 提醒时刻已知,本地闹钟就够;推送只对「别人发的消息」有意义 | FCM(国内无 GMS)、ntfy(二期再评估) |
| D4 | 循环提醒的「下一次时间」**只由服务端算** | `RepeatSchedule` 是唯一源,客户端复刻农历必然出两套口径 | 客户端自己推算 |
| D5 | 分享 APK 侧载,不以 Play 上架为前置 | 家庭内部使用;纯 WebView 壳在 Play 有被拒风险 | 先上架 |
| D6 | 卡片数据层用 **长时不轮换的 `type=WIDGET` 令牌** | 照抄现有 `WALLPAPER` 令牌机制,避免与 WebView 抢刷新令牌 | 卡片复用 refresh token(会互踢) |

---

## 1. 目标与非目标

### 1.1 目标

- 手机上是一个**独立 App**(图标、启动页、无浏览器地址栏),不是「浏览器加到主屏」。
- 提醒在**锁屏/后台/杀进程后**仍能按时响(在系统允许的范围内;见 §8 风险 R1)。
- 桌面卡片**不打开 App 也能看到今天该做什么**。
- 家庭内容生产的入口更短:分享 → 入相册、拍照 → 直传、通知 → 直接回复。

### 1.2 非目标(明确不做)

- ❌ 不做 iOS 首发(架构不阻塞,但本期不实现、不验收)。
- ❌ 不重写 UI,不引入第二套设计语言。
- ❌ 不做账号体系变更(沿用双 token JWT)。
- ❌ 不做离线编辑(只做离线**浏览/响铃**,写操作需联网)。
- ❌ 不做应用商店首发(侧载即可,后续想上架再说)。
- ❌ 不做「杀掉 App 后仍常驻后台」的保活(违背项目求稳原则,见 §10)。

---

## 2. 现状盘点

### 2.1 可以直接复用的资产

| 能力 | 现有实现 | 复用方式 |
|---|---|---|
| 日程/提醒数据 | `backend/.../controller/ScheduleController.java` 的 `/schedule/events`、`/schedule/upcoming`;`service/ScheduleService.java` 的 `collectOccurrences` | P1 新增 `occurrences` 接口**复用同一个 `collectOccurrences`**,不另写取数逻辑 |
| 重复与农历判定 | `common/RepeatSchedule`、`common/AnniversaryDate` | **只在服务端调用**,客户端零实现 |
| 提醒完成 | `ReminderService.toggleDone(id, familyId, userId, date)`,接口 `toggle-done?date=` | 通知按钮直接调它 |
| 站内通知 | `service/NotificationService.java` + `notification` 表 | 保持不变;系统通知是**另一条通道**,两者并存 |
| 实时消息 | `websocket/ChatWebSocketHandler.java`(协议 v2,`/api/ws/chat?token=`) | 前台实时聊天/通话/对战直接可用 |
| 鉴权与续期 | `api/request.js`(401 自动续期 + `refreshPromise` 单飞);`security/JwtUtils.java` | P0 只改 baseURL 与令牌落盘位置 |
| 长存令牌范式 | `JwtUtils.generateWallpaperToken`(`type=WALLPAPER`,不轮换)+ `AuthService.refresh` 的 `wallpaper` 分支 | 卡片令牌照抄这一套 |
| 移动端 UI | `composables/useDevice.js`、`layouts/MobileLayout.vue`、`utils/navModules.js` | 直接使用;`navModules.js` 仍是导航唯一数据源 |
| 版本号 | 根 `VERSION` → `vite.config.js` 的 `__APP_VERSION__` | App 内版本比对用 |
| 文件下载 | `/files/**` 由 nginx 托管;`FileAccessMapper` 控制游客可见性 | 相机上传、分享入库都走现有上传接口 |

### 2.2 三个硬缺口(必须补)

1. **没有任何系统级提醒/推送**。
   `ReminderService.processDue()` 每 5 分钟扫一次,只在**服务端**往 `notification` 表写一行;前端连 Service Worker 推送都没接过(`frontend` 里 grep 不到 `pushManager`/`showNotification`)。
   → **App 不打开,提醒永远不会响**。这是 P1 要解决的核心。
2. **提醒的时间语义是「服务器墙上时间」**。
   `family_reminder.remind_date DATE` + `remind_time TIME`,不带时区(`schema.sql` L1104)。
   → 一期按「墙上时间」处理(家庭成员同一时区),接口返回 `timeZone` 字段为未来留口;见 §6.4.6。
3. **前端是相对路径 `/api` + localStorage 存令牌**。
   `api/request.js` L10 `baseURL: '/api'`;`stores/user.js` 把 token 写 localStorage。
   → 套壳后 origin 变成 `https://localhost`,`/api` 会打到自己身上;localStorage 在 WebView 里等同明文。见 §6.2。

### 2.3 必须遵守的项目约定

- 含中文的源码/文档一律走 Read/Write/Edit 工具,禁止 PowerShell 读写(踩坑速查 §1.1)。
- 新增接口:Controller 只做校验,Service 单实现,Mapper 只 `BaseMapper`,自定义 SQL 进 XML;写接口加 `@OperationLog`。
- 新增 `@RequirePermission` 前 auth_code 必须进 `sys_auth` + `sys_role_auth` 种子(踩坑速查 §2.14)。
- 表结构变更要**双文件**(`schema.sql` + 幂等 `migrations.sql`),并同步 AGENTS 表数 / 需求 §6.2 清单 / README 表数口径——否则 `doc-drift-check.sh` 会红。
- 前端新增可见文本一律走 i18n;用户可见文案不写实现细节(AGENTS 前端规范 10 / 22)。
- 不许改 `FileService` 存储实现;不许把游戏存档另存 DB(与本计划无关但同类禁令)。

---

## 3. 技术选型

### 3.1 候选对比

| 方案 | 桌面卡片 | 提醒可靠性 | 前端改动 | 需要新写 | 结论 |
|---|---|---|---|---|---|
| PWA 加强(Web Push + Share Target) | ❌ 系统不支持 | 中(依赖浏览器 SW 存活) | 小 | Web Push 后端 | 排除(卡片是核心诉求) |
| TWA / Bubblewrap(Chrome 容器) | ❌ 系统不支持 | 中高 | 极小 | 无 | 备选,但满足不了卡片 |
| **Capacitor 套壳** | ✅ 原生写 | 高 | 小(baseURL/令牌/返回键) | 3 个原生模块 | ✅ **采用** |
| 全原生 Kotlin | ✅ | 最高 | 全部重写 | 全部 | 成本不划算 |
| Flutter / RN | ✅ | 高 | 全部重写 | 全部 | 同上 |

### 3.2 结论

**Capacitor(Capacitor 7 或 npm 最新稳定大版本)+ Vue 现有产物**。
要求 Node ≥ 22.12、JDK 21,与现有工具链一致(`frontend/package.json` engines、`backend/pom.xml`)。

### 3.3 为什么不是 TWA / 全原生

- **TWA 换不来卡片**:TWA 本质仍是浏览器页面,App Widget 必须由原生代码渲染,这条路直接封死。
- **全原生不划算**:前端有 30+ 页面、Element Plus 深度定制、4 个独立产品页、模拟器/电子书/PDF 等重前端模块,重写等于重做产品。
- **Capacitor 的上限已经够用**:卡片、通知、相机、指纹、分享、快捷方式都有成熟通路,唯一代价是「卡片数据层要写 Kotlin」——而那只是 3~4 个 GET 接口的 DTO。

---

## 4. 总体架构

### 4.1 分层

```
┌─────────────────────────────────────────────────────────┐
│ Android 原生(只做「Web 做不到」的三件事)                 │
│  ├─ App Widget:Jetpack Glance(RemoteViews)             │
│  ├─ WorkManager:每 30 分钟同步卡片数据 + 补注册闹钟     │
│  ├─ AlarmManager/LocalNotifications:到点响铃           │
│  └─ Intent / Biometric / Camera 桥接                    │
├─────────────────────────────────────────────────────────┤
│ Capacitor WebView:现有 Vue SPA(几乎原样)              │
│  ├─ 页面/路由/状态(复用)                               │
│  ├─ api/request.js:baseURL 可配 + 令牌走 Keystore        │
│  └─ 原生能力薄封装:src/native/*(懒加载)                │
├─────────────────────────────────────────────────────────┤
│ Spring Boot /api(现有)                                  │
│  ├─ /schedule/occurrences(新增:闹钟与卡片的唯一数据源) │
│  ├─ /auth/widget-token(新增:卡片长存只读令牌)          │
│  └─ 其余接口不改                                        │
└─────────────────────────────────────────────────────────┘
```

### 4.2 代码落位

| 位置 | 内容 | 备注 |
|---|---|---|
| `frontend/capacitor.config.ts` | Capacitor 配置 | 新增 |
| `frontend/android/` | `npx cap add android` 生成的原生工程 | 入库;`local.properties`/`*.keystore`/`keystore.properties` 进 `.gitignore` |
| `frontend/android/app/src/main/java/top/ihomy/app/widget/` | Glance 卡片 + WorkManager | 新增,包名 `top.ihomy.app` |
| `frontend/src/native/` | 原生能力薄封装(通知/分享/相机/生物识别/版本) | 新增,全部 `await import()` 懒加载,不进首屏 |
| `frontend/src/utils/platform.js` | `isNative()` / `isAndroid()` / `getAppVersion()` | 新增 |
| `backend/.../controller/ScheduleController.java` | 加 `occurrences` | 修改 |
| `backend/.../service/AuthService.java` + `security/JwtUtils.java` | 加 `type=WIDGET` 令牌 | 修改 |
| `backend/.../controller/HealthController.java` 或新增 | `/public/app/latest` | 修改/新增 |

**原生能力必须懒加载**:`src/native/*` 不能在 `main.js` 静态导入,否则 Web 端要多背 Capacitor 运行时代码。

### 4.3 三条数据流

**A. 提醒同步(写一次,响一个月)**

```
App 打开/回到前台 ─┐
WorkManager 每日  ─┼→ GET /schedule/occurrences?from=today&to=+30d
通知被点击完成   ─┘        ↓
                    与本地已注册列表对账(按 key 比对)
                        ↓
              新增 → LocalNotifications.schedule
              消失/变更 → cancel + 重排
                        ↓
               结果缓存到 Preferences(离线也响)
```

**B. 卡片刷新(不经过 WebView)**

```
WorkManager(30min)/用户点刷新/App 内改动后主动触发
        ↓
Kotlin OkHttp + type=WIDGET 令牌 → GET /schedule/occurrences + /weather?...
        ↓
写 DataStore(结构化快照 + 更新时间)
        ↓
Glance 渲染 RemoteViews(读 DataStore,不读网络)
```

**C. 分享入库(外部 → 家庭)**

```
微信/相册选「分享到 ihomy」
        ↓ ACTION_SEND / ACTION_SEND_MULTIPLE
原生接收 → 复制到 cacheDir → 唤起 App 深链 /share?type=photo
        ↓
前端上传页(复用现有接口)→ /album 或 /diary 或 /storage
```

---

## 5. 分期计划

### P0 套壳适配(3–5 天)

**目标**:能装、能登录、能正常用,先暴露 UI 适配问题。

**改动清单**

1. `frontend/package.json` 加依赖:
   `@capacitor/core`、`@capacitor/cli`、`@capacitor/android`、`@capacitor/app`、`@capacitor/keyboard`、`@capacitor/status-bar`、`@capacitor/network`、`@capacitor/filesystem`、`@capacitor/share`、`@capacitor/local-notifications`、`@capacitor/camera`。
   脚本:`app:sync` = `npm run build -- --mode app && npx cap sync android`;`app:open` = `npx cap open android`。
2. **API 基址可配**:`api/request.js` 改 `baseURL: import.meta.env.VITE_API_BASE || '/api'`。
   新增 `frontend/.env.app`(`VITE_API_BASE=https://ihomy.top/api`)。
   ⚠ **不要用 `.env.local`**(会内联进生产 bundle,AGENTS 硬性禁令)。
3. **后端 CORS 放行 WebView origin**:`application.yml` 的 `app.cors-allowed-origins` 追加 `https://localhost`(Capacitor 默认 `androidScheme: https` + `hostname: localhost`)。
   > 备选:开 `CapacitorHttp` 让请求走原生、彻底绕开 CORS。**不推荐**——上传上限 500MB,原生 HTTP 会把 body 全量进内存。
4. **令牌落 Keystore**:写一个原生插件 `SecureToken`(EncryptedSharedPreferences),`stores/user.js` 在 native 下改走它,localStorage 只放非敏感信息。
   简易替代:`@aparajita/capacitor-secure-storage`(先评估再引,别引两个)。
5. **返回键**:`@capacitor/app` 监听 `backButton` → 有历史就 `router.back()`,在首页二次确认退出;弹出层打开时先关弹层。
6. **系统栏与安全区**:status bar 配色跟随主题两轴;`index.html` 已有 `viewport-fit=cover`,补 `env(safe-area-inset-*)` 的内边距校验(底部 Tab 栏最容易压到手势条)。
7. **文件下载/打开**:PDF/电子书/视频走 `@capacitor/filesystem` + 系统应用打开;`window.open` 的链接改成 `Browser.open`(或 `@capacitor/app-launcher`)。
8. **版本号**:`utils/platform.js` 用 `App.getInfo()` 取 `appVersion`,Web 端回落 `__APP_VERSION__`。
9. **签名与构建**:`frontend/android/keystore.properties` + `*.jks` 一律 `.gitignore`;`assembleRelease` 产出 APK。
10. **发布**:服务器 `uploads/files/` 放 APK,新增 `/app` 下载页(复用 `report_product_stat` 打点),后端 `/public/app/latest` 返回版本与下载地址。

**验收标准**

- [ ] `assembleRelease` 出包,安装后图标/启动页正常,无白屏。
- [ ] 登录/退出/切换家庭正常;杀进程重开仍保持登录。
- [ ] 首页/相册上传/记账/聊天(WS)/电子书/游戏至少各跑一次。
- [ ] 软键盘弹出不遮挡输入框;底部 Tab 不被手势条压住。
- [ ] 断网 → 有友好提示,不出现无限转圈。
- [ ] Android 返回键行为符合预期,不出现「一按就退出」。

### P1 提醒闭环(1–1.5 周)

**目标**:到点必响,点通知能完成,循环提醒不跑偏。

**改动清单**

1. 后端新增 `GET /schedule/occurrences`(契约见 §7.1.1)。
2. 原生通知封装 `src/native/reminders.js`:
   - `sync()`:拉接口 → 与本地对账 → 批量 `schedule` / `cancel`。
   - 通知 `id` 由服务端 `key` 稳定哈希(§6.4.3)。
   - 只注册未来 **30 天**、最多 **200 条**,超出按时间取前 200。
   - 静默时段:沿用服务端 06:00–23:00 口径(§6.4.5)。
3. 权限引导页:通知权限(Android 13+)、精确闹钟权限(Android 12+)、电池优化白名单(国内 OEM 必做,引导用户手动设置)。
4. 通知动作:`完成` / `稍后 10 分钟`;点击 → 深链到 `/reminder?date=YYYY-MM-DD`。
5. 触发时机:App 启动、回到前台(`appStateChange`)、每天 WorkManager 兜底、完成操作后立即重排。
6. **系统日历导出(兜底)**:把未来 90 天的提醒/纪念日/还款日写进本地日历账户「ihomy」,用系统闹钟保证可靠性(§6.6.4)。
7. 后端 `/public/app/latest` 与 App 内降级提示。

**验收标准**

- [ ] 新建「5 分钟后」提醒 → 锁屏能响,点击进对应日期。
- [ ] 每日/每周/每月/每年(含农历闰月)提醒:连续 3 天验证日期正确(与网页端日程页一致)。
- [ ] 在 App 内把提醒勾完成 → 未触发的不再响。
- [ ] 飞行模式 + 杀进程:已注册的提醒仍能响(本地缓存生效)。
- [ ] 时区/夏令时切换(手动改设备时间)不出现提前/延后一天。
- [ ] 通知权限被拒时有明确引导,不是静默失败。

### P2 桌面卡片(1–2 周)

**目标**:不打开 App 也能看到「今天要做什么」。

**改动清单**

1. `frontend/android/app/src/main/java/top/ihomy/app/widget/`:
   - `TodayWidget`(4×2 今日概览)
   - `ReminderWidget`(4×2 今日提醒,可勾选)
   - `CountdownWidget`(2×2 纪念日倒计时)
   - `AlbumWidget`(4×2 相册回忆)
   - `WidgetRepository`(OkHttp + `type=WIDGET` 令牌 + DataStore 缓存)
   - `WidgetWorker`(WorkManager 30 分钟 + 网络约束)
   - `WidgetBridgePlugin`(`notifyWidgetsChanged()`,App 内改动后即时刷新)
2. 后端新增 `POST /auth/widget-token`(§7.2)。
3. 卡片点击:深链到站内路由(用 `appUrlOpen` 统一处理)。
4. 平板:`targetCellWidth/Height` + `minResizeWidth/Height`,提供 2×2 / 4×2 / 4×4 三档尺寸。

**验收标准**

- [ ] 四张卡片在「Pixel 启动器 / 小米 / 华为」三类启动器上都能添加、不崩。
- [ ] 断网时显示上次缓存 + 「最后更新于 X 分钟前」,不显示空白/错误。
- [ ] 点「完成」→ 后台调接口成功 → 卡片与 App 内状态一致。
- [ ] 深色模式下文字与背景对比度达标(≥4.5:1)。
- [ ] 平板与手机各一张截图进 `docs/daily/`。
- [ ] 卡片内存占用 < 20MB,刷新不触发 ANR。

### P3 高价值增量(2–3 周,按需挑)

| 能力 | 说明 | 关键实现 |
|---|---|---|
| 分享到家庭 ⭐ | 微信/相册/浏览器 → 相册/日记/文件/愿望 | `ACTION_SEND` / `ACTION_SEND_MULTIPLE` + 自定义 Capacitor 插件 |
| 保险箱指纹解锁 ⭐ | 进 `/vault` 前 BiometricPrompt,可配「5 分钟免验」 | `@aparajita/capacitor-biometric-auth` + 一个 Gate 组件 |
| 相机直传 | 拍照进相册、拍单据进记账 | `@capacitor/camera` + 现有上传接口 |
| 通知快捷回复 | 聊天通知直接回一句 | `RemoteInput` + 现有 WS/REST |
| 桌面快捷方式 | 长按图标:新建提醒/记一笔/聊天/拍照 | `shortcuts.xml` + 深链 |
| 快速设置磁贴 | 下拉栏一键「记一笔」/「加提醒」 | `TileService` |
| 语音记账 | 说「买菜 35」直接落账 | 系统 `SpeechRecognizer`(可离线)+ 现有记账接口 |

### P4 可选

- 相册自动备份(MediaStore + WorkManager;需后端 `content_photo.checksum` 幂等去重)
- 地理围栏提醒(`family_reminder` 加经纬度;权限敏感、耗电)
- 平板常亮看板(家里挂墙:今日日程/天气/相册/`iot` 控制)
- 自建推送(ntfy / UnifiedPush;一期明确不做)
- Wear OS(日程 + 勾选完成)
- 适老化简易模式 / 儿童设备限时

---

## 6. 详细设计

### 6.1 工程与构建

- `capacitor.config.ts` 关键项:
  ```ts
  {
    appId: 'top.ihomy.app',
    appName: 'ihomy',
    webDir: 'dist',
    android: { allowMixedContent: false, captureInput: true, webContentsDebuggingEnabled: false },
    server: { androidScheme: 'https' },   // 默认 hostname localhost → origin https://localhost
  }
  ```
- 构建顺序:`npm run build -- --mode app` → `npx cap sync android` → `./gradlew assembleRelease`。
- **包体积**:现有首屏初始 JS 约 1.5MB;`public/emulatorjs`、`public/ruffle` 会被打进 APK。
  先打包量体积;**超过 30MB 时**改为「游戏资源按需从生产域名加载」(需处理绝对 URL 与版本一致性),或只在游戏首次进入时下载。
- 原生工程入库,但 `*.jks`、`keystore.properties`、`local.properties`、`build/`、`.gradle/` 进 `.gitignore`。

### 6.2 网络与鉴权

**origin 变化**:WebView 内页面 origin 是 `https://localhost`,而 API 在 `https://ihomy.top/api` → **跨源**。

处理:

1. 前端 `baseURL` 走 `VITE_API_BASE`(P0)。
2. 后端 `app.cors-allowed-origins` 追加 `https://localhost`(必须;否则预检失败)。
3. `/files/**` 的图片在 WebView 里是跨源 `<img src>`——**不需要 CORS**(图片非 CORS 请求),但 `canvas` 读像素会污染;若某处要读像素(如照片裁剪),改用 `crossOrigin` + 服务端 `Access-Control-Allow-Origin`。
4. WebSocket:`ws://`/`wss://` 由 `stores/chat.js` 按当前 origin 推导 → **必须改为按 API 基址推导**,否则会连到 `wss://localhost`。这是 P0 最容易漏的一处。

**令牌存储**:

| 项 | Web | App |
|---|---|---|
| accessToken(2h) | localStorage | Keystore(或内存 + Keystore 备份) |
| refreshToken(7d, 轮换) | localStorage | **仅 Keystore** |
| 用户信息 | localStorage | localStorage(非敏感) |

- 续期仍用现有 `refreshPromise` 单飞;`request.js` 不动逻辑。
- 侧载 APK 无商店保护,Keystore 是必要项。

### 6.3 路由与深链

- 现状:`router/index.js` 是 `createWebHistory()`。
- **风险**:Capacitor 的本地服务在「冷启动到一个子路径」或「硬刷新子路径」时可能 404(没有服务器兜底)。
- 处理顺序:
  1. 先保持 history 模式实测:冷启动(start_url `/`)、站内跳转、浏览器返回。
  2. 若 404:在 `MainActivity` 的 `WebViewClient` 里对**无扩展名的路径**回落 `index.html`(不改前端,Web 版 URL 不变)。
  3. 仍不行才给 App 构建切 hash 模式(`createWebHashHistory`),但会影响深链格式,**作为最后手段**。
- 深链统一入口:`@capacitor/app` 的 `appUrlOpen` → `router.push(path)`;通知/卡片/快捷方式都走同一个函数。

### 6.4 通知与闹钟

#### 6.4.1 数据来源

只调 `/schedule/occurrences`(§7.1.1),**不碰** `family_reminder` 表、不实现任何日期算法。

#### 6.4.2 注册策略

- 窗口:今天 00:00 → +30 天。
- 上限:200 条(超出按时间升序取前 200,并在下次同步时补齐)。
- 每条一个 `LocalNotification`,`schedule.at` 用**设备本地时间**(墙上时间)。
- `allowWhileIdle: true`(Doze 下尽量触发),`smallIcon` 用单色通知图标。

#### 6.4.3 对账算法(幂等)

```
本地表: key → { notificationId, date, time, title }
远端表: key = kind + ':' + id + ':' + date

for key in 远端:
    if 本地无 → schedule(id=hash(key))
    elif 时间/标题变了 → cancel + schedule(同 id)
for key in 本地 且 不在远端:
    cancel(id)
```

- `notificationId` = `FNV-1a(key) & 0x7FFFFFFF`;冲突时本地表顺延 +1(概率极低,但必须处理,否则会误取消别人)。
- 本地表存 Preferences,杀进程后仍可对账。

#### 6.4.4 完成与稍后

- 通知动作 `done` → 后台调 `POST /reminder/{id}/toggle-done?date=YYYY-MM-DD` → 成功后 `cancel` 后续同 key。
- 动作 `snooze` → 本地重排 10 分钟后,**不回写服务端**(避免污染 `family_reminder_check` 语义)。
- 一次性提醒 `kind=REMINDER` 完成后服务端 `done=1`,下次同步自然消失。

#### 6.4.5 静默时段

服务端 `ReminderService.processDue` 有 06:00–23:00 静默。客户端**同一口径**:
- 落在 23:00–06:00 的提醒,通知延后到 06:00 发,标题加「(昨晚)」。
- 若用户明确要深夜提醒,后续加「允许打扰」开关(不在 P1)。

#### 6.4.6 时区

- 一期:**墙上时间**,不做跨时区。
- 接口返回 `timeZone: "Asia/Shanghai"`,客户端**不转换**,直接按 `date`+`time` 构造本地 `Date`。
- 若将来有异地家庭成员,再升级为服务端返回带偏移的 `at`;本期不预埋客户端逻辑。

#### 6.4.7 权限

| 权限 | 何时要 | 说明 |
|---|---|---|
| `POST_NOTIFICATIONS` | Android 13+ | 运行时申请,拒绝后引导去设置 |
| `SCHEDULE_EXACT_ALARM` | Android 12+ | Android 14+ 默认拒绝,需跳 `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` |
| 忽略电池优化 | 国内 OEM 必做 | **只用引导文字**,不静默申请(Play 政策敏感;虽侧载,但保持干净) |

### 6.5 桌面卡片

#### 6.5.1 技术

- **Jetpack Glance**(Compose 风格 API,编译成 RemoteViews)。
- 数据层:`OkHttp` + `kotlinx.serialization` + `DataStore`;不走 WebView。
- 刷新:`WorkManager` 周期任务(最短 15 分钟,实际定 30 分钟)+ `ACTION_APPWIDGET_UPDATE` + App 主动触发。
- 计时类(倒计时)用 RemoteViews 原生控件,**不要用定时刷新**(系统最小间隔 30 分钟)。

#### 6.5.2 卡片规格

| 卡片 | 尺寸 | 内容 | 点击 |
|---|---|---|---|
| 今日概览 | 4×2(可拉 4×4) | 日期+农历、天气、最近 3 条日程、未读数 | 进 `/` |
| 今日提醒 | 4×2(可拉 4×4) | 提醒清单 + 勾选圈 | 勾选=后台完成;标题=进 `/reminder` |
| 纪念日倒计时 | 2×2 | 下一个纪念日 + 剩余天数 | 进 `/anniversary` |
| 相册回忆 | 4×2 | 最新/随机家庭照片 | 进 `/album` |

#### 6.5.3 数据与鉴权

- 卡片用 `type=WIDGET` 令牌(§7.2),存 Keystore。
- 不可用(`WIDGET` 令牌失效 / 未登录)→ 卡片显示「打开 App 登录」,不显示错误堆栈。
- 未登录/游客 → 显示演示家庭公开内容(与 Web 一致),不报错。

#### 6.5.4 平板

`targetCellWidth/Height`(API 31+)让用户可拉伸;`minResizeWidth/Height` 定下限;
`4×4` 大卡默认展示更多条目(手机 3 条、平板 8 条)。

### 6.6 分享 / 相机 / 生物识别 / 其他

#### 6.6.1 分享接收

```xml
<intent-filter>
  <action android:name="android.intent.action.SEND" />
  <category android:name="android.intent.category.DEFAULT" />
  <data android:mimeType="image/*" />
  <data android:mimeType="text/plain" />
</intent-filter>
```

- 图片:拷到 `cacheDir/share/` → 深链 `/share?type=photo` → 前端展示确认页(选相册/日记)再上传。
- 文本/链接:深链 `/share?type=text&v=...`(URL 需 `encodeURIComponent`,长度上限 2KB)。
- 多图(`ACTION_SEND_MULTIPLE`)需自己写插件读取 `ClipData`。

#### 6.6.2 相机

`@capacitor/camera` → 得到 Base64 或临时文件 → 复用现有上传接口。
大图必须走文件流上传,**不要 Base64 塞 JSON**(500MB 上限与内存约束,AGENTS 后端规范 10)。

#### 6.6.3 生物识别

- 进入 `/vault` 前 `BiometricPrompt`;失败回落到登录密码(现有「查看密码二次验证」逻辑,§4.5.9)。
- 「5 分钟免验」存内存,不进 localStorage。
- 注意:保险箱业务口径不变,**客户端只是加一道门**,不是新权限体系。

#### 6.6.4 系统日历导出

- 用 `CalendarContract` 建本地日历账户「ihomy」(需 `CALLER_IS_SYNCADAPTER=true`)。
- 只**单向导出**:提醒/纪念日/还款日 → 日历;不回读手机日历(二期再议)。
- 好处:即使 OEM 杀后台,系统日历闹钟仍会响 → **这是国内环境的可靠性兜底**。
- 去重:事件 `UID`/`SYNC_DATA1` 写 `ihomy:<kind>:<id>:<date>`,重导入先删后插。

### 6.7 版本与更新

- `GET /api/public/app/latest` → `{ version, url, notes, minSupported }`。
- 启动时比对 `App.getInfo().version`(或 `__APP_VERSION__`):
  - 低于 `minSupported` → 阻塞弹窗(只留「去更新」)。
  - 低于 `version` → 可关闭的提示。
- 下载:`DownloadManager` → `FileProvider` → `ACTION_VIEW` 安装(需 `REQUEST_INSTALL_PACKAGES`)。
- 需要 `?v=` 或文件名带版本号,避免 CDN/浏览器缓存旧包(踩坑速查 §7.2 同源问题)。

---

## 7. 后端改动清单

> 全部变更仍遵守:Controller 只校验、Service 单实现、Mapper 只 `BaseMapper`、写接口加 `@OperationLog`。

### 7.1 接口

#### 7.1.1 `GET /api/schedule/occurrences`(P1 必做)

**用途**:闹钟注册与卡片渲染的唯一数据源。

**参数**:`from`(必填,`YYYY-MM-DD`)、`to`(必填,含首尾,**≤ 92 天**)。

**实现**:在 `ScheduleService` 里复用私有 `collectOccurrences(familyId, from, to, false)`,新增一层「补 `key` / `link` / `timeZone`」的封装;`ScheduleController` 加一个只读方法。

**响应**:

```json
{
  "code": 0,
  "data": {
    "timeZone": "Asia/Shanghai",
    "truncated": false,
    "items": [
      {
        "key": "REMINDER:12:2026-11-09",
        "kind": "REMINDER",
        "id": 12,
        "title": "交水电费",
        "date": "2026-11-09",
        "time": "20:00",
        "repeat": "MONTHLY",
        "done": false,
        "readOnly": false,
        "link": "/reminder?date=2026-11-09"
      }
    ]
  }
}
```

**口径约定(必须与网页端一致)**:

- `kind` 取值沿用现有:`ANNIVERSARY` / `REMINDER` / `TASK` / `PLAN` / `PLAN_TASK` / `LOAN` / `FINANCE` / `ITEM_EXPIRY`。
- **循环事项不合并**(与 `/schedule/upcoming` 相反——那个接口为「近期提醒」做了去重,这里要做闹钟,每期都要)。
- 提醒/任务 `done=true` 的不返回。
- `PLAN_TASK` 不返回(与 `upcoming` 一致)。
- 计划托管的提醒(`plan_id` 非空)已由 `PLAN` 代表,不重复。
- 超过 500 条截断,`truncated=true`(客户端据此提示「还有更多」)。
- 游客返回空列表(提醒需要登录态才有意义)。

#### 7.1.2 `GET /api/public/app/latest`(P0/P1)

```json
{ "code": 0, "data": { "version": "V11.74", "url": "/files/app/ihomy-V11.74.apk",
                       "notes": "首个 Android 版", "minSupported": "V11.74" } }
```

免登录;`url` 走 `/files` 或新目录,带 `?v=` 防缓存。

### 7.2 新增令牌类型 `type=WIDGET`(P2)

照抄 `WALLPAPER` 范式:

- `JwtUtils.generateWidgetToken(userId, username)`:有效期 30 天,`type=WIDGET`。
- `AuthService.refresh`:允许 `WIDGET` 滑动续期,**不轮换拉黑**(与 `wallpaper` 分支并列)。
- `POST /api/auth/widget-token`(需登录):返回 `{ token }`。
- **最小权限**:`JwtAuthenticationFilter` 放行后,`WIDGET` 令牌**只允许读** `/schedule/**`、`/weather/**`、`/reminder`(GET)、`/anniversary`(GET)、`/public/**`;写接口一律 401。
  > 宁可白名单,不要黑名单。卡片只需要读。
- 登出**不吊销** `WIDGET`(同 `WALLPAPER` 理由),但重置密码/禁用账号要连带失效(检查 `sys_user.status`,与现有登录一致)。

### 7.3 表结构草案(仅 P4 需要,一期不建)

> ⚠ 建表会让表数从 92 变 93,必须同步 `AGENTS.md` 表数、需求 §6.2 清单、`README.md`/`docs/README.md` 表数口径,否则 `doc-drift-check.sh` 直接红。
> 一期用「设备 id 存在 Preferences」即可,P4 做推送/多设备管理再建表。

```sql
CREATE TABLE `sys_app_device` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`      BIGINT       NOT NULL COMMENT '所属用户ID',
  `device_id`    VARCHAR(64)  NOT NULL COMMENT '客户端生成的稳定设备标识',
  `platform`     VARCHAR(20)  NOT NULL DEFAULT 'ANDROID' COMMENT 'ANDROID/IOS',
  `device_name`  VARCHAR(100) DEFAULT NULL COMMENT '设备名(型号)',
  `app_version`  VARCHAR(20)  DEFAULT NULL COMMENT 'App 版本号',
  `push_token`   VARCHAR(255) DEFAULT NULL COMMENT '推送通道令牌(未接推送时为 NULL)',
  `last_seen_at` DATETIME     DEFAULT NULL COMMENT '最后活跃时间',
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_device` (`user_id`, `device_id`),
  KEY `idx_user_seen` (`user_id`, `last_seen_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='App 设备表';
```

若做相册自动备份,再补:

```sql
ALTER TABLE `content_photo` ADD COLUMN `checksum` VARCHAR(64) DEFAULT NULL COMMENT '内容哈希(自动备份幂等用)';
CREATE UNIQUE INDEX `uk_family_checksum` ON `content_photo` (`family_id`, `checksum`);
```

### 7.4 配置项

```yaml
app:
  cors-allowed-origins: https://ihomy.top,https://localhost
  android:
    latest-version: V11.74        # 与根 VERSION 同步
    min-supported-version: V11.74
```

---

## 8. 风险与对策

| # | 风险 | 影响 | 对策 |
|---|---|---|---|
| R1 | **国内 OEM 杀后台** | 提醒不准(最大落地风险) | ①系统日历单向导出兜底 ②引导加入自启动/电池白名单 ③WorkManager 每日补同步 ④明确告知用户「不保证秒级」 |
| R2 | Android 14 精确闹钟默认拒绝 | 分钟级提醒延后 | 引导开启 `SCHEDULE_EXACT_ALARM`;未授权时降级为 inexact 并在设置页显示状态 |
| R3 | Play「纯 WebView 壳」被拒 | 无法上架 | 侧载分发为主;真要上架时以「卡片/通知/相机」原生能力论证 |
| R4 | 卡片刷新频率受限(≥15–30 分钟) | 不能做实时倒计时 | 倒计时用 RemoteViews 原生控件;文案写「截至 HH:mm」 |
| R5 | WebView 后台音视频被冻结 | 视频通话体验差 | P3 再做;需要前台服务 + 画中画 |
| R6 | 令牌存 localStorage 明文 | 侧载被逆向 | Keystore;`type=WIDGET` 令牌最小权限 + 可吊销 |
| R7 | 与 Web 端抢 refresh token | 偶发互踢 | App 内沿用 `refreshPromise` 单飞;卡片用独立 `WIDGET` 令牌;现有 60s 宽限已兜底 |
| R8 | `ws://` 推导到 localhost | 聊天连不上 | §6.2 第 4 条,P0 必查 |
| R9 | history 模式深链 404 | 冷启动白屏 | §6.3 三级方案,先用 WebViewClient 兜底 |
| R10 | APK 体积过大(模拟器/Ruffle 资源) | 安装意愿低 | 先量;>30MB 时游戏资源改按需下载 |
| R11 | 500MB 上传在 WebView 里内存爆 | 大视频上传失败 | 保持 WebView 原生网络(不用 CapacitorHttp);或改 `filesystem` 分片 |
| R12 | 时区/夏令时 | 提醒偏移一天 | 一期墙上时间;接口返回 `timeZone` 留口 |
| R13 | 表数变更漏同步文档 | CI 红 | §7.3 的红字提醒;收尾跑 `doc-drift-check.sh` |

---

## 9. 测试与验收

### 9.1 真机矩阵

| 维度 | 至少覆盖 |
|---|---|
| Android 版本 | 10(最低)、13(通知权限)、14+ (精确闹钟) |
| OEM | 原生(Pixel/模拟器)、小米、华为(或 OPPO/vivo) |
| 屏幕 | 手机 6.1"、平板 10"+ |
| 形态 | 竖屏、横屏、分屏 |
| 主题 | 光尘浅色、暖居深色 |

### 9.2 必过用例(P0–P2)

1. 冷启动 → 首页 3 秒内出内容。
2. 断网冷启动 → 有缓存页/提示,不白屏。
3. 登录 → 杀进程 → 重开仍登录;令牌过期自动续期不跳登录页。
4. 相册上传 3 张照片(含 HEIC)、1 个 100MB+ 视频。
5. 聊天发送/接收、断线重连、切后台再回来恢复。
6. 提醒:新建 → 锁屏响 → 点完成 → 网页端同步显示已完成。
7. 循环提醒:农历每年提醒连续两天校验日期。
8. 卡片:添加/删除/调整尺寸/深色模式/断网。
9. 分享:从系统相册分享 2 张图 → 进相册成功。
10. 返回键、软键盘、下拉刷新、长按选择不误触。

---

## 10. 明确不做 / 容易跑偏的地方

- ❌ **客户端自己算循环提醒/农历**。`RepeatSchedule`、`AnniversaryDate` 是唯一源;客户端只消费 `occurrences` 的 `date`/`time`。
- ❌ **把提醒闹钟存服务端表**。闹钟是设备本地状态,服务端只给「事实」(日程条目)。
- ❌ **卡片自己去聚合业务**。卡片只渲染 `occurrences`,不做分类/换算/推算。
- ❌ **为了卡片便利新增一套权限或放宽家庭隔离**。卡片令牌也要过家庭校验。
- ❌ **启用 `server.url` 远程模式**做「热更新」(D2)。
- ❌ **一期做推送服务**。没有推送不影响「提醒」这个核心诉求。
- ❌ **申请 `USE_EXACT_ALARM`**(Google 限定闹钟/日历类,政策风险);用 `SCHEDULE_EXACT_ALARM` + 用户授权。
- ❌ **用 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 静默申请**,只做文字引导。
- ❌ **把游戏资源/存档塞进 APK 或另存 DB**。

---

## 11. 开放问题(开工前需拍板)

| # | 问题 | 待定项 | 建议 |
|---|---|---|---|
| Q1 | 包名 | `top.ihomy.app` | 直接采用;一旦发布不宜改 |
| Q2 | 分发方式 | 自建下载页 / 内网分发 | 自建 `/app` 页 + 版本检查 |
| Q3 | 最低 Android 版本 | 10 / 11 | 10(minSdk 29),覆盖更老平板 |
| Q4 | 游戏资源是否进包 | 进包 / 按需下载 | 先量体积再定 |
| Q5 | 是否做系统日历导出 | 做 / 不做 | **建议做**,是 R1 最有效的兜底 |
| Q6 | 卡片首批数量 | 3 / 4 张 | 3 张(概览/提醒/倒计时),相册回忆二期 |
| Q7 | 是否需要 iOS 同期 | 否 | 否;但原生模块尽量与平台解耦 |

---

## 12. 开工清单(P0 第一天)

```bash
# 1. 确认工作区干净,避免与其他会话冲突
git status

# 2. 装依赖
cd frontend
npm i -D @capacitor/cli
npm i @capacitor/core @capacitor/android @capacitor/app @capacitor/keyboard \
      @capacitor/status-bar @capacitor/network @capacitor/filesystem \
      @capacitor/share @capacitor/camera @capacitor/local-notifications

# 3. 初始化 + 生成原生工程
npx cap init ihomy top.ihomy.app --web-dir dist
npm run build
npx cap add android

# 4. 同步
npx cap sync android
npx cap open android
```

随后按 §5-P0 的 10 条逐项改。

**收尾(每次交付后)**:
1. 追加 `docs/daily/YYYY-MM-DD.md` 变更说明(含真机截图路径)。
2. `bash scripts/gen-code-map.sh`(新增原生目录不影响,但前端新增文件会被收录)。
3. `bash scripts/doc-drift-check.sh` 全绿。
4. 更新根 `VERSION`(次位 +1),本版收尾提交打 tag。
5. 全部落地后:本文移入 `docs/设计想法/完成/`,需求 §9.1 行移到「已完成」段,§2.3 运行环境表改口径。

---

## 实施差异

> 落地过程中与本文不一致的地方记在这里(格式:`P? — 原计划 / 实际 / 原因`)。

- **P0 依赖清单** — 原计划装 `@capacitor/local-notifications`、`@capacitor/camera` / 未装 / 二者只在 P1(通知)与 P2(卡片/拍照)用,P0 装上会白白往 manifest 里塞相机与通知权限。
- **P0 构建脚本** — 原计划 `app:sync = npm run build -- --mode app && npx cap sync android` / 实际拆成 `app:build`(先跑 `copy-ruffle.mjs`、`copy-emulatorjs.mjs` 再 `vite build --mode app`)与 `app:sync`(= `app:build` + `cap sync`) / 生产构建脚本本身就带这两个 copy 步骤,漏掉会让 ruffle 与 EmulatorJS 静态资源缺失。
- **P0 `--mode app` 与 PWA** — 未提及 / app 模式下不挂 `vite-plugin-pwa` / 壳内不需要 Service Worker,而旧 SW 预缓存的 `index.html` 会顶掉升级后新 APK 的壳(与 踩坑速查 §7.2 同类)。
- **P0 令牌存储** — 原计划自写原生插件 `SecureToken` / 用 `@aparajita/capacitor-secure-storage` 8.0.1(计划已给出该替代项) / 底层同为 EncryptedSharedPreferences(Android Keystore),少维护一套 Kotlin 插件;不启 `CapacitorHttp`。
- **P0 CORS 与 Cookie** — 原计划只追加 `https://localhost` / 另外改了 `AuthCookie`:对 `Origin: https://localhost` 下发 `SameSite=None; Secure`,并在 `MainActivity` 显式 `setAcceptThirdPartyCookies(webView, true)` / 壳内 origin 是 `https://localhost`,相对 `ihomy.top` 属跨站;私有照片/电子书靠 `ihomy_at` cookie 鉴权,`SameSite=Lax` 不随 `<img>`/`<video>` 子资源发送会一律 403。Web 端仍是 Lax,CSRF 面不变。
- **P0 文件地址** — 计划只提到「`/files/**` 图片不需要 CORS,只有读像素才需要」 / 实际新增 `frontend/src/utils/assetUrl.js`:axios 响应拦截器与 WS 消息里 `/files/...` 统一补服务端 origin / DB 存的是相对路径,壳内会解析成 `https://localhost/files/...` 直接 404(比 CORS 更早失败)。
- **P0 版本号** — 原计划在 `utils/platform.js` 里用 `App.getInfo()` / 实现在 `src/native/version.js`(动态 import),`platform.js` 只加 `isNativeApp/isAndroidApp/getWebVersion`;`app/build.gradle` 读仓库根 `VERSION` 生成 `versionName`/`versionCode` / `platform.js` 被首屏引用,静态 import `@capacitor/app` 会把原生运行时拉进首屏包;gradle 读同一个 `VERSION` 才能保证页脚、运维页、安装包三处一致(`versionCode = 主*1000 + 次`)。
- **P0 文件下载/打开** — 原计划「`window.open` 改成 `Browser.open`」 / 实际统一接管:`window.open` 替换 + 捕获 `<a download>`/跨域 `<a>`,站内路由留 WebView、`/files/**` 与常见文档/音视频走 `Filesystem.downloadFile`(带 Bearer)+ `Share` 交给系统「打开方式」、其余外链走 `Browser.open` / 全站 7 处 `window.open` 和多处 `<a download>` 逐个改容易漏,统一出口更稳。
- **P0 §6.1 配置项** — 原文 `android: { captureInput: true }` / 未写 / `captureInput` 是 Capacitor 4 的旧配置,Capacitor 8 无此语义(`Keyboard.resize: native` 才是对应项)。
- **P0 minSdk** — 原文未定 / `minSdkVersion = 29`(Android 10) / 与家庭实际设备下限一致,EncryptedSharedPreferences 在该版本行为稳定。
- **P0 gradle 分发包** — 原文按官方 `services.gradle.org` / wrapper 改指 `mirrors.cloud.tencent.com/gradle/gradle-8.14.3-bin.zip`(版本不变,仅换下载源) / 官方地址 307 跳 GitHub Releases,本机 PKIX 校验失败拉不下包。
- **P0 第 10 条「发布」** — 原文含「APK 放服务器 + `/app` 下载页 + `GET /public/app/latest`」 / 本轮未做 / 需要部署窗口与生产 `uploads` 写权限,等套壳验收通过后再上;本轮只交付可侧载 APK。
- **P0 §6.3 路由** — 原文「先保持 history 实测,404 再兜底」 / 采用 history 模式 + `capacitor.config.json` 显式 `server.html5mode: true` / Capacitor 的本地服务本来就把「无扩展名路径」回落 `index.html`,写明防误改;真机冷启动仍需按验收表实测。


### P1 实施差异(2026-11-10,V11.77)

- **P1 权限引导** — 原计划「权限引导页(通知/精确闹钟/电池白名单三项)」/ 实际:通知权限被拒时一次性引导弹窗(系统设置路径指引),精确闹钟未授权时降级 inexact 并打告警日志;/plan 设置页内的状态展示与电池白名单引导后移 P2 / 页面级 UI 优先级低于闭环本身,一次弹窗已满足「不静默失败」。
- **P1 深链格式** — 原计划 `/reminder?date=YYYY-MM-DD` / 实际 `occurrences.link` 直接给 `/plan?date=`(路由表里 /reminder 重定向会丢 date 参数);通知点击与外部 appUrlOpen 统一走 `native/deeplink.js`。
- **P1 WorkManager 每日兜底(§5-P1 第5条)→ 移至 P2** — 原生 Worker 要脱离 WebView 独立拉接口,需要一份不轮换的只读凭证;这正是 P2 `type=WIDGET` 令牌的设计用途,届时卡片数据层与每日兜底共用同一 Kotlin HTTP 栈,避免先写一版用完即弃的凭证 hack。
- **P1 系统日历导出兜底(§5-P1 第6条)→ 移至 P2** — 需要 WRITE_CALENDAR 运行时权限 + CalendarProvider 写入插件;与卡片同一批原生工作一起做,统一出真机验收。
- **P1 验收口径** — 锁屏响铃/权限弹窗/注册对账已在模拟器实测通过;通知点击深链与「完成」动作的完整 UI 路径、循环提醒连续 3 天、真机矩阵留待真机阶段(模拟器上插件桥在自动化并发下会变慢挂起,应用本身无恙——物理机上不存在 CDP 并发评估)。

### P2 实施差异(2026-11-10,V11.78)

- **P2 Jetpack Glance → RemoteViews 直写(Java)** — 原计划 Glance / 实际三张卡用经典 RemoteViews 直写 / Glance 需要引入 Kotlin + Compose 双工具链,而 Glance 的编译产物本来就是 RemoteViews;三张静态布局卡直写更稳、零新增依赖(仅 WorkManager),产物一致。
- **P2 卡片「勾选=后台完成」→ v1 只读** — 原计划今日提醒卡可勾选 / 实际 v1 点击进 App 完成 / §7.2 的最小权限(WIDGET 只读、写接口 401)与卡片写回完成状态冲突;不给卡片写权限是安全取舍,勾选功能留待后续版本(短效完成令牌或专用动作端点)。
- **P2 今日概览内容精简** — 原计划「日期+农历、天气、最近 3 条、未读数」/ 实际 v1「日期+最近 3 条日程」/ 客户端禁算农历(§10);天气需要定位参数与令牌覆盖,未读需要额外接口,均为增量,后补不破架构。
- **P2 深链格式** — 卡片点击 `ihomy://plan?date=...` 自定义 scheme;`deeplink.js` 对其手动剥前缀(Chromium 的 URL 解析器把 `//plan` 塞进 pathname,不可靠,实测冷启动丢路径后修复)。launch intent 由 manifest 的 VIEW intent-filter 承接,`@capacitor/app` 的 appUrlOpen → deeplink.js 统一路由。
- **P2 registerPlugin 时序** — 自定义 Capacitor 插件必须在 `MainActivity` 的 `super.onCreate` **之前** `registerPlugin`(桥在 super 里构建,晚了 JS 侧报 plugin not implemented)。
- **P2 通知「每日 WorkManager 兜底」口径修订** — 卡片 Worker(30 分钟)只刷卡片数据;提醒闹钟的注册重排仍靠 JS 同步(App 启动/回前台/完成后)+ 插件内置的开机重排。若真机长期未开 App 出现漏响,再做原生侧重排(需要 WIDGET 令牌覆盖 occurrences 读权限——已具备)。
- **P2 验收口径** — 机制链路全部实测(启动器枚举 3 张卡、令牌→Worker→缓存、occurrences 数据正确含服务端 days、深链冷启动落 /plan?date、无崩溃);卡片摆放与视觉效果是真机 30 秒手动步骤,真机阶段补截图进 docs/daily/。

### 发布链路实施差异(2026-11-10,V11.80)

- **P0 第 10 条「发布」→ 已落地(原「本轮未做」)** — `/public/app/latest` 早于 P1 交付;本轮补齐:站内 `/app` 下载页(公开路由)、`ProductStatService.PRODUCTS` 增加 `app`(计数下载 + 访问打点 + 运维页卡片)、线上 `external.yml` 的 `app.android.*` 三个键、APK 上架。
- **APK 目录口径** — 计划写「服务器 `uploads/files/` 放 APK」/ 实际放 `{upload-dir}/app/`(`/opt/ihomy/uploads/app/ihomy-V11.80.apk`) / `/files/**` 被 `WebMvcConfig` 整体映射到上传根目录,前缀吃掉一层;放成 `uploads/files/app/` 直链 404(白名单与 302 都正常,极易误判)。同坑记入 踩坑速查 §12.2。
- **下载入口拆成两条** — 页面上「下载安装包」走 `/public/product/download?page=app&file=...`(先计数再 302,保住下载统计);App 内更新检查直接用 `/public/app/latest` 里的 `/files/app/*` 直链 / 统计口径只覆盖落地页转化,App 自身更新不该再算一次下载。
- **APK 体积 51.8 MB(54,288,580 字节)** — 计划风险表阈值 30 MB 已超,主因仍是 `public/emulatorjs`(≈30 MB)+ `public/ruffle`(≈28 MB)原样进 assets;Q4「进包 or 游戏资源按需下载」仍待拍板,不影响本轮发布。
- **`min-supported-version` 与线上唯一包对齐** — 设为 V11.80(旧包已从服务器删除),只有一版可用时留低值会给出「能装但下不到」的死链。

