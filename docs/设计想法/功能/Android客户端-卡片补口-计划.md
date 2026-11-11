# Android 客户端卡片补口计划(今日卡农历/天气 + 提醒卡可勾选)

> **状态**:代码已落(V11.89),后端 350 测试全绿 + `gradlew :app:compileReleaseJavaWithJavac` 通过;**模拟器机制验证与真机视觉/手感均待做**(装包清单见 §5)。
> 前两批计划(`Android客户端-计划.md` / `Android客户端-能力扩展-计划.md`)遗留的「代码补口」里,本文只装两个低成本项:**今日概览卡加农历/天气**与**提醒卡可勾选**。原生侧闹钟重排(条件项,真机漏响才做)与相册回忆卡不在本文。

---

## 0. 一页摘要

- **今日卡**:标题保持公历,新增一行副标题「农历十月廿二 · 立冬 · 12° 晴」(农历/节气来自 `/public/calendar`,天气来自 `/public/weather`,两接口本就在 WIDGET 白名单内,**后端零新增接口**)。
- **提醒卡**:每行右侧加圆圈按钮,点击直接勾选/取消完成。安全面只开一个洞:白名单对 `POST ^/reminder/\d+/toggle-done$` 这一条精确路径放行,且服务端强制 **WIDGET 令牌只准勾今天**、家庭隔离沿用 `ReminderService.toggleDone` 现有校验。
- 勾选完成后**整轮重新拉取** occurrences(完成项被服务端过滤,行自然消失),不做本地假乐观更新。

## 1. 现状核实(开发前逐处读过,行号为 2026-11-11 时点)

| # | 事实 | 位置 |
|---|---|---|
| 1 | 今日卡 v1 注释明确「农历/天气不做」 | `frontend/android/app/src/main/java/top/ihomy/app/widget/TodayWidgetProvider.java:11` |
| 2 | 提醒卡 v1 注释「完成在 App 内做——WIDGET 令牌只读」 | `ReminderWidgetProvider.java:11` |
| 3 | 白名单仅 GET:`/schedule/` `/weather/` `/public/` `/anniversary/` `/reminder` `/reminder/list` `/notification/unread-count` `/chat/unread` | `backend/src/main/java/com/ihomy/security/JwtAuthenticationFilter.java:119-127` |
| 4 | WIDGET 令牌放行后正常注入 LoginUser(familyId 快照)→ `/public/weather` 可解析家庭偏好位置,无需定位权限 | `JwtAuthenticationFilter.java:49-71` |
| 5 | Worker 每 30 分钟拉 `/schedule/occurrences`(窗口 30 天)并缓存,失败保旧缓存 | `widget/WidgetWorker.java:42-63`、`WidgetRepository.java:59-83` |
| 6 | occurrences 的 `key = kind:id:date`;done=true 与 PLAN_TASK 被过滤;TASK=循环提醒、REMINDER=一次性,同源 `family_reminder` | `ScheduleService.java:140-166, 224-255` |
| 7 | 勾选端点已存在且双端共用:POST `/reminder/{id}/toggle-done?date=`,服务层按 familyId 校验,一次性翻 done 列、循环按 (id,date) 插删 | `ReminderController.java:68-74`、`ReminderService.java:110-131` |
| 8 | 农历唯一来源 `/public/calendar?month=YYYY-MM`,返回 `{month, days:[{date,lunarText,lunarMonthText,term,festivals,...}]}`(免登录) | `PublicController.java:142-146`、`CalendarService.java:85-100`、`CalendarDay.java:21-31` |
| 9 | 天气 `/public/weather` 返回 `{temp,text,city,...}`,Key 未配返回 data=null | `PublicController.java:149-154`、`QWeatherProvider.java:60-64` |
| 10 | 卡片行是单 TextView(w_row1..4),无逐行点击;点击卡片整体深链 | `widget_reminder.xml`、`BaseWidgetProvider.java:88-96` |
| 11 | 过滤器白名单已有测试基建(MockHttpServletRequest 直跑 filter) | `backend/src/test/java/com/ihomy/security/WidgetTokenTest.java` |

## 2. 关键决策(定了不再重新讨论)

- **D1 不新增业务端点**:勾选复用 `POST /reminder/{id}/toggle-done`。专用动作端点等于再抄一遍家族/类型校验;最小写面 = 「这一条路径 + 只准今天 + 家庭内」,与原计划的「专用动作端点」同级但代码更少。
- **D2 农历/天气不进 occurrences**:occurrences 是桌面闹钟与 App 的共用数据源,保持纯日程域;Worker 另拉两个已白名单的 `/public` 只读接口,后端数据零改动。
- **D3 客户端继续禁算农历**(第一批 D4 沿用);天气直接展示服务端 `temp`/`text`,客户端零换算。
- **D4 勾选后整轮重拉**:服务端本来就过滤 done,重拉即消失;失败保持旧行,下一轮自愈。不做本地假乐观更新(避免缓存与真实状态分叉)。
- **D5 失败不弹 Toast**:Worker/后台上下文的 Toast 在 Android 11+ 受限;失败静默,行保持原样。
- **D6 一次性提醒同样可勾**(翻 done 列,与 App 内口径一致);卡片只见未完成项,不存在「勾掉再取消」的展示歧义。

## 3. 后端改动清单

| # | 文件 | 改动 |
|---|---|---|
| 1 | `JwtAuthenticationFilter.java` | `widgetAllowed()` 增加 POST 分支:仅 `^/reminder/\d+/toggle-done$` 放行,其余 POST 仍 401;WIDGET 分支补 `request.setAttribute("ihomy.widgetToken", true)` 供控制器识别 |
| 2 | `ReminderController.java` | `toggleDone` 增加 `HttpServletRequest` 参数:带 widgetToken 标记时 `date` 必须为服务器今天,否则 400(文案不写实现,见前端规范 22) |
| 3 | `WidgetTokenTest.java` | 新增:POST toggle 精确路径放行并注入登录态;其它 POST 路径仍 401;`/reminder/1/toggle-done/extra` 之类变体 401 |

安全说明:WIDGET 令牌为 30 天不轮换 bearer,开启此例外后其可写面 = 本家庭「当天提醒完成态」一项;令牌泄露的最大新增损失是把当天提醒勾掉/取消,不涉及内容读取之外的任何数据(白名单其余路径仍 GET-only,`/files` 与通知正文照旧拒绝)。

## 4. 原生改动清单(Android,Java + 布局)

| # | 文件 | 改动 |
|---|---|---|
| 1 | `WidgetRepository.java` | ①`fetchTodayInfo()`:`GET /public/calendar?month=`(设备当月)取今天的 lunarText/lunarMonthText/term/festivals + `GET /public/weather` 取 temp/text(两者独立 try,失败各自置空);②`toggleDone(id,date)`:POST toggle(沿用 Bearer,非 2xx 抛错);③TodayInfo 的缓存读写 |
| 2 | `WidgetPrefs.java` | 新键 `cache_today_info`(全局单份,与按 widgetId 的 occurrences 缓存分开) |
| 3 | `WidgetWorker.java` | `doWork()` 开头处理输入 data 的 `toggle_key`(校验 kind∈{REMINDER,TASK}、date=设备今天,调 toggleDone;失败记日志不中断)→ 正常拉 occurrences + TodayInfo → 缓存 → `Widgets.updateAll` |
| 4 | `WidgetActionReceiver.java`(新) | 接收 `top.ihomy.app.action.WIDGET_TOGGLE` 广播,校验 key 形状后 enqueue 带 `toggle_key` 的一次性 Worker(广播内不做网络);Manifest 注册 `exported=false` |
| 5 | `ReminderWidgetProvider.java` | 行渲染:文字 + 右侧圆圈(`setImageViewResource`);圆圈 `setOnClickPendingIntent` → 广播(key extra,requestCode=key.hashCode());文字区仍走卡片深链 |
| 6 | `TodayWidgetProvider.java` | 副标题渲染:读 TodayInfo,`info.date == 设备今天` 才显示「农历{月}{日}[ · 节气][ · 天气]」,过期/缺失隐藏 |
| 7 | `widget_reminder.xml` | 每行改 LinearLayout(TextView weight=1 + ImageView 22dp 圆圈) |
| 8 | `widget_today.xml` | 标题下加 `w_subtitle`(11sp 次要色,单行省略) |
| 9 | `res/drawable/widget_check_ring.xml`(新) | 椭圆描边 shape(RemoteViews 对 vector 支持不稳,shape 最稳) |

## 5. 验收

**模拟器/自动化(本轮)**

1. 后端 `mvnw -B clean package` 全绿(含新增 WidgetTokenTest 用例)。
2. `gradlew :app:compileReleaseJavaWithJavac` 编译通过。
3. 模拟器装 debug 包:今日卡出现农历副标题;提醒卡点圆圈后该行在下一轮刷新消失;断网点圆圈行不消失、无崩溃;天气 Key 未配时副标题只有农历。

**真机(遗留给真机验收清单,同前两批一起)**

4. 锁屏/桌面实际点击手感、圆圈视觉、深链不受影响;切换家庭后旧令牌勾选返回 401、行保持。

## 6. 明确不做 / 边界

- ❌ 本地乐观更新、失败 Toast、勾选动画(RemoteViews 无动画能力)。
- ❌ 倒计时卡、今日卡行点击行为改动(仍整体深链)。
- ❌ `type=WIDGET` 白名单再扩任何其它写路径或 `/files`。
- ❌ 服务端为卡片新增聚合端点(D2)。

## 7. 发布注意

- 含原生改动必须重新出 APK(`npm run app:sync` → gradle assembleRelease),`deploy.ps1` 走全量;线上 `app.android.latest-version` 与 VERSION 同步。
- Web 侧零改动(令牌签发、occurrences、勾选接口都是既有契约),纯 Web 发版不触发本计划内容。

## 8. 实施差异

> 落地过程中与本文不一致的地方记在这里(格式:原计划 / 实际 / 原因)。

- **验收 §5-3(模拟器机制验证)→ 本轮未跑** — 原计划模拟器装 debug 包验「副标题出现/圆圈勾选后行消失/断网保持」 / 实际只做到编译级(Java 编译通过 + 白名单/控制器单测),模拟器与真机验证和前两批遗留清单一起做 / 模拟器验证属装包后步骤,本轮交付口径为「代码就绪」。
- **提醒卡行隐藏粒度** — 原计划只提「行内加圆圈」 / 实际每行包一层 LinearLayout(w_wrap1..4),空行隐藏整层 / 单 TextView 加 ImageView 后,行高由 wrapper 的 weight 决定,隐藏内层控件会留空行。
