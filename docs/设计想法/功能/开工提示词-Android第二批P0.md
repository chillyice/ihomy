# Android 第二批 · 开工提示词(P0 可靠性地基)

> 复制本文件全文给新会话即可开工。
> 关联计划:`docs/设计想法/功能/Android客户端-能力扩展-计划.md`(下称「计划」,本文所有编号 A1/A2/A3/A4 均指该文件)。

## 0. 一句话目标

给 ihomy Android 壳补三件「关掉界面还要继续跑」的能力:**崩溃能落盘(A3)**、**漏排的提醒能自愈(A1)**、**不打开 App 也能看到未读(A2)**;有余力再加**应用锁(A4)**。全部沿用「自写 Java 插件 + WorkManager」的既有范式,不引第三方原生包。

## 1. 开工前必读(按顺序,别跳)

1. `AGENTS.md` —— 硬性禁令、后端规范、前端规范 13/14(首屏不许进原生运行时)。
2. `docs/设计想法/功能/Android客户端-能力扩展-计划.md` —— §0 决策、§2 现状、§5.1–§5.4 详细设计、§6.2 白名单、§8 风险。
3. `docs/设计想法/功能/Android客户端-计划.md` 的「实施差异」段 —— 第一批踩过的坑(P0–P3),尤其 P2 registerPlugin 时序、提醒通道 `reminders-v2`。
4. `docs/踩坑速查.md` §12(Android 壳)、§6(令牌)、§2.4(401)。
5. 代码现场:`frontend/src/native/reminders.js`、`frontend/src/native/index.js`、`frontend/android/app/src/main/java/top/ihomy/app/widget/{WidgetWorker,WidgetPrefs,WidgetRepository,WidgetBridgePlugin}.java`、`frontend/android/app/src/main/java/top/ihomy/app/MainActivity.java`、`backend/src/main/java/com/ihomy/security/JwtAuthenticationFilter.java`。

## 2. 范围(四件事,按此顺序做)

| # | 任务 | 估算 | 交付 |
|---|---|---|---|
| **A3** | 崩溃与前端报错本地留存 | 1–2 天 | `IhomyDiagnostics` 插件 + 设置页「诊断信息」块 |
| **A1** | 提醒兜底自愈 | 1–2 天 | `StaleCheckWorker` + `reminders.js` 上报同步时间 |
| **A2** | 后台未读通知 | 1–2 天 | 扩 `WidgetWorker` + 后端白名单两行 + 设置页开关 |
| **A4** | 应用锁 | 1–2 天 | `appStateChange` 超时校验 + 保险箱页 `FLAG_SECURE` |

**顺序是刻意的**:先有 A3,后两项的真机问题才有证据可查。

## 3. 逐项实现要点

### A3 诊断回传(先做)

**新增原生**(包 `top.ihomy.app.diagnostics`):

- `CrashStore.java`:写 `<files>/diagnostics/crash-<ts>.txt`,内容 = 机型 / Android 版本 / App 版本(取 `BuildConfig` 或 `getPackageManager` 的 versionName)/ 堆栈;单文件 ≤64KB,滚动保留 5 份。
- `DiagnosticsPlugin.java`(`@CapacitorPlugin(name = "IhomyDiagnostics")`):
  - `report({ lines })` —— 存前端错误(滚动保留最近 200 条);
  - `list()` —— 返回崩溃文件与前端错误的条数/时间/摘要;
  - `read({ name })` —— 只允许读 `diagnostics/` 目录内的固定文件名,**必须挡 `../`**(照抄 `UpdaterPlugin` 的文件名白名单写法);
  - `clear()`。
- 安装 `Thread.setDefaultUncaughtExceptionHandler`:**写完后转调原 handler**(绝不吞崩溃)。
- `MainActivity`:在 `super.onCreate` **之前** `registerPlugin(DiagnosticsPlugin.class)`(第一批 P2 已踩过:晚了 JS 侧报 plugin not implemented)。

**前端新增** `frontend/src/native/diagnostics.js`:

- `window.onerror` / `unhandledrejection` / `console.error` 进内存环形缓冲 200 条;
- 定时(或 `appStateChange` 转后台时)flush 到 `report()`;
- 全部走动态 `import`,由 `native/index.js` 挂一行,首屏零影响。

**UI**:`frontend/src/views/settings/AppSettings.vue` 的「安卓客户端」段新增「诊断信息」块:显示崩溃份数 + 前端错误条数,「导出」用 `@capacitor/share` 把合并文本交给系统分享(用户自己发出),「清空」调 `clear()`。文案走 i18n,中英键对齐。

**验收**:`adb shell am crash top.ihomy.app` → `adb shell run-as top.ihomy.app ls files/diagnostics` 有文件;重启 App 后设置页可见并能导出。

### A1 提醒兜底自愈

**先明确缺口,别扩大**(计划 §5.1):重启手机由 `@capacitor/local-notifications` 自带的 `LocalNotificationRestoreReceiver` 恢复,**不用我们写开机重排**。真实缺口只有:

1. 注册窗口只到「今天 +30 天」、上限 200 条(`reminders.js` 的 `MAX_SCHEDULED`);
2. 网页端改了提醒而 App 没打开 → 本地旧闹钟会多响。

**做法(绝不在原生侧注册闹钟,计划 D3)**:

1. `WidgetBridgePlugin` 增一个方法 `setReminderSyncAt({ ts })` → 写进**我们自己的** `WidgetPrefs`(不要碰插件私有存储);`WidgetPrefs` 加 `reminderSyncAt/putReminderSyncAt` 与 `lastNudgeDate`。
2. `reminders.js` 在 `doSync()` 成功后调 `setReminderSyncAt({ ts: Date.now() })`。
3. 新增 `top.ihomy.app.reminder.StaleCheckWorker`(WorkManager,每日 1 次,约束有网),并在 `WidgetBridgePlugin.load()` 旁边以 `enqueueUniqueWork(KEEP)` 注册(幂等):
   - `now - reminderSyncAt > 7 天` 且 `lastNudgeDate != 今天` → GET `/schedule/occurrences?from=<today>&to=<+30d>`(WIDGET 令牌已放行 `/schedule/`,HTTP 栈复用 `WidgetRepository`);
   - 未来 3 天有 ≥1 条且没有已完成的 → 发一条通道 `reminders-v2` 的通知:「最近的日程等你确认」/「有 M 条日程,点一下打开 ihomy 校准提醒」,深链 `ihomy://plan?tab=today`;写 `lastNudgeDate`。
   - **这条通知不代表任何一条日程**,不参与对账,不占 200 条上限。
4. 复用通道时注意第一批踩过的坑:**通道参数创建后被系统冻结**,必须沿用 `reminders-v2` 与 `res/raw/ihomy_notify.wav`,不要新建静音通道。

**验收**:设备时间前移 9 天(或临时把阈值调成 1 天实测后改回)→ Worker 出提示;**点开进 App 后 `adb shell dumpsys alarm | grep TimedNotificationPublisher` 能看到新闹钟**;网页端改提醒时间后不开 App,本地不出现双响。

### A2 后台未读通知

**不新增周期任务**——直接扩 `WidgetWorker`(已是 30 分钟、已带 WIDGET 令牌):

1. `WidgetRepository` 加两个 GET:`/notification/unread-count`(返回数字)、`/chat/unread`(取其中的总数,看 `chatService.unread` 的返回结构)。
2. `WidgetWorker` 末尾:取两个数 → 与 `WidgetPrefs` 里的上次值比对 → **有增长且 >0** 才发一条通知:
   - 固定通知 id(后一条覆盖前一条,不堆栈)、`setOnlyAlertOnce(false)`;
   - 标题/正文走字符串资源,正文形如「聊天 2 条 · 通知 3 条」;
   - 两个 action:打开聊天(`ihomy://chat`)、打开通知(`ihomy://notification`);
   - **23:00–06:00 静默**(与提醒口径一致),静默期间不发但正常更新基线。
3. `WidgetPrefs` 存 `lastUnreadN/lastUnreadC` 与「开关」;设置页新增开关(默认开),关掉只跳过本分支,卡片刷新照旧。

**后端**(唯一必须动的地方,`JwtAuthenticationFilter.widgetAllowed()`):

```java
|| "/notification/unread-count".equals(path) || "/chat/unread".equals(path)
```

**必须精确到路径**:写 `/notification/` 前缀会把 `/notification/list`(含正文)也让给卡片令牌。改完补一条断言性注释。

**验收**:另一账号发消息 → 不开 App 约 30 分钟出通知;**再发一条不堆叠**(`adb shell dumpsys notification | grep ihomy` 只有一条);点开进 `/chat`;关掉设置开关后不再出现。

### A4 应用锁(有余力再做)

1. 复用已有 `IhomyBiometric`(`available/verify/cancel`),**不新增插件**。
2. `appStateChange` 记 `pausedAt`(存内存,不落 localStorage);回前台 `now - pausedAt > 5 分钟` → `verify()`;取消 → **回登录页**(不留在原页)。
3. `AppSettings` 开关默认关;本机无可用生物识别、或指纹被移除时自动关掉(照抄保险箱那段已有处理),否则会把用户永久挡在锁屏。
4. 最近任务缩略图:新增极小的 `IhomyScreen` 插件(`setSecure` / `setKeepOn`),**只在 `/vault` 页进出时切 `FLAG_SECURE`**,离开即清。全局开 `FLAG_SECURE` 会让截图/录屏全黑,禁止。

## 4. 硬性规范(违反即返工)

- **原生**:包 `top.ihomy.app.<域>`;插件 `@CapacitorPlugin(name = "IhomyXxx")`;`registerPlugin` 必须在 `super.onCreate` **之前**;文件读只允许白名单目录内 + 挡 `../`。
- **前端**:新能力一律 `src/native/*.js` + 由 `native/index.js` **动态 import**;`isAndroidApp()` 守门;可见文本全走 `$t()`,中英键对齐,`{`/`}`/`@` 要转义。
- **后端**:Controller 只校验 / Service 单实现 / 自定义 SQL 进 XML;本次除白名单外不改接口;写接口要 `@OperationLog`(本次预计没有写接口)。
- **不许**:①读写 `@capacitor/local-notifications` 的私有存储;②原生侧注册/重排闹钟;③常驻保活、前台服务常驻(除计划 §5.6-L2,本次不做);④`System.out.println` / `printStackTrace`;⑤首屏静态 import 任何 Capacitor 包。
- **多会话**:动手前先 `git status`;有他人未提交改动时不要整文件重写共享文件;不跑 `git stash`;不并行跑 `npm run build`。

## 5. 验证(每项做完都要过)

```powershell
cd frontend; npm run lint                 # 0 error 0 warning
cd frontend; npx vitest run               # 全绿(基线 928 passed,新增用例要说明)
cd frontend; npx prettier --write <改动文件>
cd backend;  .\mvnw.cmd -B clean package  # 白名单改动后必须编译
cd frontend; npm run app:sync             # 重新出 assets 后再打 APK
cd frontend\android; .\gradlew :app:assembleRelease
```

- 首屏基线不得增长:本次全部是原生侧与懒加载 JS,入口 chunk 应基本不变。
- 原生实测(模拟器 API 34 即可,`am crash` / `dumpsys alarm` / `dumpsys notification` 三条命令能覆盖大部分验收);真机缺项要明确列进「真机待验」。
- 文档闸门:`bash scripts/gen-code-map.sh`、`bash scripts/doc-drift-check.sh` 全 [OK]。

## 6. 收尾与发布

- 轻量收尾:追加 `docs/daily/YYYY-MM-DD.md` 当日小节(变更/决策/踩坑/验证/未验真机项)→ 重建代码地图 → drift check 全绿 → 更新根 `VERSION`(本次含原生与后端,**次位 +1**,即 V11.85)→ commit 指定文件(禁 `git add -A`)。
- **发布需明确指示**:含原生改动必须重新出 APK,`deploy.ps1` 走全量(后端白名单改了);APK 上传 `uploads/app/`,`external.yml` 的 `latest-version` 必须与 `VERSION` 同步,否则应用内更新会指向不存在的包。`git push` 与部署都要先问。
- APK 出包前确认 `webContentsDebuggingEnabled` 为 **false**(第一批的临时调试开关不能带进生产)。

## 7. 本次明确不做

- 不做相册自动备份(B1)、音乐 L1(B2)、看板(B3)、手柄(B5)——下一批。
- 不做保险箱 Autofill(C1,需先出安全评估)、不做推送通道(C2,另立计划)。
- 不做投屏 / 地理围栏 / 增量更新 / 扫码。
- 不碰第一批遗留的真机验收清单(那边 §9.2),只在本次涉及的功能上顺带真机核一遍。

## 8. 交付格式

每完成一项,先给一段**「改了什么 / 为什么这么选 / 验证证据」**(证据要具体:命令 + 关键输出行),再进下一项。不要一次性堆到最后汇报;不要在没验证前说「已完成」。
