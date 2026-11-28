# AGENTS.md — ihomy 项目规则

> 跨会话自动加载。本文件**只放结论级规定 + 索引**,目标 **≤30KB**(硬上限 45KB:超了注入会被截断,尾部先丢)。详细内容各归其位(**收尾两级**:平时只追加 `docs/daily/`;明确说「归档」才全量梳理):

| 内容类型 | 去处 |
|---------|------|
| 功能现状(能力/表结构/接口/规划) | `docs/需求设计说明书.md`(§4 功能 / §6 数据库 / §7 接口 / §9 规划;**就地改写=当前事实**,不写时间/版本叙事) |
| 实现记录(改动/决策/live DB SQL) | 平时写当日 `docs/daily/`;全量归档时合并为 `docs/变更归档.md` 末尾 `#####` 小节(索引 `docs/归档索引.md`) |
| 踩坑成因、机制、维护清单、缓存失效矩阵 | `docs/踩坑速查.md`(本文件只留结论或 `§x` 指针) |
| UI/交互/视觉细则 | `docs/UI设计提示词.md` |
| 代码路径索引(功能 ↔ 文件) | `docs/代码地图.md`(**自动生成勿手改**:`bash scripts/gen-code-map.sh`) |

> 跨项目经验:`../经验库/README.md` —— 只读参考,改它回那个仓库改。

## 任务 → 先读哪几个文件

| 任务 | 顺序(先定位,再动手) |
|------|---------|
| 找某个功能在哪 | `grep -in <关键词> docs/代码地图.md` → `XxxController` / `Xxx.vue` / 表名 |
| 加/改接口 | 需求 §7 → `controller/` → `service/` → `resources/mapper/*.xml` → `api/index.js` |
| 加页面 | `router/index.js` → `views/**` → `utils/navModules.js`(移动端)→ i18n →(工具类)`tools/registry.js` |
| 改表结构 | `schema.sql` + `migrations.sql`(幂等)→ `entity/` → mapper XML → 需求 §6 |
| 加/调首页模块 | `sys_home_module` 种子 → `HomeModuleService`(改完重启)→ 移动端入口 |
| 加工具 | `tools/registry.js` → `utils/tools/` 内核 → `npm run tools:export` |
| 查历史 / 排线上问题 | `docs/归档索引.md` 反查 → 变更归档;线上先 踩坑速查 → 日志 `GET /api/ops/logs/trace?tid=` |
| 收尾归档 | `.agents/skills/archive/SKILL.md`:轻量=当日 `docs/daily/` → 代码地图 → commit;说「归档」才全量梳理四文档 |

## 硬性禁令(违反即返工)

> **Git**:非人工指令禁止 `git commit`/`git add -A`/`git add .`/`git push`;`git add` 只能指定具体文件。**例外(已授权)**:收尾流程(每日文档/全量归档)写完后自动 commit;`git push` 仍需指示。
> **敏感数据**:**生产**密码/密钥/token 一律不进仓库(生产 DB 密码与 JWT 密钥走服务器 `external.yml`,模板 `backend/src/main/resources/external.yml.template`);凭证台账在本地 `Linux部署指导.md` §〇,开发账号在本地 `docs/新人上手指南.md`(均 .gitignore);前端演示凭证走 `frontend/.env.development.local`(**`.env.local` 会内联进生产 bundle,禁用**)。`schema.sql` 是开发安全版,生产须 `ALTER USER` 改强密码。
> **多会话并行**:禁用 `git stash`(会回滚他人未提交改动);他人 stash 不 pop,只读(`git show "stash@{0}:<path>"`);不并行跑 `npm run build`(争 `public/emulatorjs`、`dist/`)。**动手前先 `git status`**:有他人未提交改动时,不整文件重写共享文件。
> **工具链**:
> - 工作目录 `C:\Users\chill\OneDrive\WorkStation\Projects\ihomy`;读写前逐字核对路径。
> - **含中文源码/配置/SQL/文档一律走 Read/Write/Edit**,禁 PowerShell `Get-Content`/`Set-Content`/`WriteAllText`(踩坑速查 §1.1);PowerShell 只用于构建与 HTTP 冒烟;脚本化改文档必须保留 CRLF。
> - PowerShell 5.1 传参见 踩坑速查 §1.3;Git Bash 调 Windows 原生命令加 `MSYS_NO_PATHCONV=1`;打包 zip 用 `tar -a -c -f out.zip dir`。
> - 写 MySQL 中文:Write UTF-8 无 BOM SQL → `MSYS_NO_PATHCONV=1 docker cp` → `docker exec ihomy-mysql mysql --default-character-set=utf8mb4 ihomy -e "source /tmp/x.sql"`。
> - **`mvnw.cmd` 必须在 `backend` 目录内调用**;后端单实例 `C:\Program Files\Java\jdk-21\bin\java.exe -jar target\ihomy-backend.jar`;jar 锁按 PID 结束(含 IntelliJ 的是 IDEA 本体,**禁 `taskkill /IM java.exe`**)。
> - **OneDrive 工作树 `mvn clean` 删不掉 `backend/target`**:用 `scripts/clean-build-target.ps1`(踩坑速查 §1.14)。
> - 内存缓存(如 `HomeModuleService`)**直改 DB 不生效**,须重启或 evict(踩坑速查 §2.1)。

## 项目概述

ihomy:家庭内部内容共享平台(PC / 安卓 / iOS,均 PWA),功能域见 需求 §4;首页模块化,**新增功能=插一条 `sys_home_module` 记录**。
技术栈:Vue3 + Vite + ElementPlus + PWA(`ihomy-frontend`);Spring Boot 4 + MyBatis-Plus + MySQL 8 + Redis(双 token JWT + 验证码 + WebSocket),包 `com.ihomy`,主类 `IhomyApplication`。Nginx 静态根 `frontend/dist`。

## 命名约定(不可改)

| 项 | 值 |
|----|----|
| Java 包 / 主类 | `com.ihomy` / `IhomyApplication` |
| Maven artifact / jar | `ihomy-backend` |
| npm 包名 | `ihomy-frontend` |
| 数据库 / 应用账号 | `ihomy` / `ihomy`(密码由 external.yml 注入) |
| 容器名 | `ihomy-mysql` / `ihomy-redis` / `ihomy-backend` / `ihomy-nginx` |
| Windows 服务名 | `IhomyBackend` / `IhomyNginx` |
| 显示名 / PWA name | `ihomy` |
| JWT 密钥前缀 | `ihomy-secret-key...` |

**业务概念不改**:`family` 表、`Family` 实体、`family_id` 字段、`OWNER/MEMBER` 角色名属于「家庭」业务领域。

## 版本号规则

- 形态 `V<主>.<次>`(次位不补零)。**根目录 `VERSION` 是唯一事实来源**(前端 `define __APP_VERSION__` 注入页脚与运维页);后端只经 `common/AppVersion` 读取,禁止私有 readVersion。
- 每次可交付迭代次位 +1;**次位满 100 强制进位**(`.99` → 主 +1、次归 0),结构性里程碑可提前进位;禁止三位次位。`V1.0–V9.110` 冻结不改。
- 收尾:更新 `VERSION` + 收尾提交打 annotated tag `V<主>.<次>`(消息=日期+一句话);`git push origin <tag>` 随发布。
- `frontend/package.json` / `backend/pom.xml` 版本字段不参与(仍 `1.0.0` / `1.0-SNAPSHOT`)。

## 数据库约定

- **root 仅初始化**:`mysql -uroot -p < schema.sql`(建库/建表/建账号/种子),只执行一次;本地 `.\scripts\start-db.ps1`。**业务运行用 `ihomy` 账号**(仅 DML on `ihomy.*`,无 DDL),建 `localhost` 与 `%` 两个 host。
- 生产 MySQL `validate_password` MEDIUM:密码含特殊字符(避开 `' " \ $ |`,建议 `!@%^&*-_+=.`),改密顺序 踩坑速查 §2.7。
- **119 张表**:`sys_` 35、`report_` 5、`family_` 52、`content_` 22、`game_` 5(清单 需求 §6.2);游戏域 `game_<code>_<module>_<entity>`,`game_info` 遗留未带前缀,口径 需求 §4.13。
- **结构变更双文件**:同时改 `schema.sql`(全量)与 `migrations.sql`(增量、**幂等**),并做空库全量导入实测(`migrations` 跑通 ≠ `schema.sql` 可用,踩坑速查 §7.4)。
- **枚举不用数字**:状态/类型一律大写英文(`PUBLISHED/PUBLIC/ACTIVE...`),含义在 `sys_dict_item`,Java 常量 `common/DictConst.java`,前端 `utils/dict.js`;**禁止 0/1/2 判断**。
- **可见范围四档** `PUBLIC > CIRCLE > FAMILY > PRIVATE`(顺序即界面顺序):`content_blog/diary/photo/video/wish/book`、`content_photo_album`、`game_info`、`family_item` 四档;`family_vault_item`、`sys_iot_device` 只两档(PRIVATE/FAMILY)。清单 需求 §6.4。
- 角色 OWNER/MEMBER/CHILD/GUEST + OPS(运维不属家庭,绑定须 `family_id=NULL` 系统级行);同一用户不同家庭可不同角色。
- **新增 `@RequirePermission` 接口前**:auth_code 必须进 `sys_auth` + `sys_role_auth` 种子(OWNER 豁免、MEMBER 显式授权);注解**只写方法上**,类级不生效(踩坑速查 §2.14)。
- **索引规范(强制)**:列表查询 WHERE+ORDER BY 落在同一复合索引,等值在前、范围/排序在后,`deleted` 进索引;混向排序给索引列加 `DESC`;新增表/接口先 `EXPLAIN`;`ORDER BY RAND()` 只用于 ≤1000 行并加 `ponytail:` 注释。已踩坑与「不建议改」见 踩坑速查 §8。
- **引用开源依赖必须登记** `sys_oss_component`;升级两条通道(台账手工步骤 / 可选自动 PR),**可升级判定必须 latest 严格高于 current**,预发布过滤,Maven 只认 repo1 `maven-metadata.xml`,MySQL 服务与 Connector/J 只跟 8.4 LTS。细则 需求 §4.6.10、踩坑速查 §7.14/§7.16。
- **记账**:余额不落库(`balance = 期初 + 收入 − 支出 − 转出 + 转入`,前后端 `utils/bookLedger.js` 同口径);账户列可空=历史未归属,只提示不迁移;预算只存额度;净资产 = 账户资金 + 理财持仓本金 − 贷款剩余本金。**周期账目与 CSV 都落 `family_book_record`**,日期推算唯一 `common/BookRecurringSchedule`(短月取月末、2/29 平年 2/28;补跑≤90 天),`last_generated_date` 条件抢占保证幂等;**「立即生成」不看已生成日**,按「同日同类型且(同分类 或 同金额且同账户)」跳过已记期次。口径 需求 §4.5.5、机制 踩坑速查 §8.5/§8.6。

## 代码结构

```
backend/ (Spring Boot 4, JDK 21, com.ihomy)
  src/main/java/com/ihomy/  common config security annotation aspect filter entity mapper service controller dto websocket
  src/main/resources/       application.yml(生产基线) external.yml.template mapper/*.xml schema.sql migrations.sql logback-spring.xml
frontend/src/               api/ stores/ router/ i18n/ theme/ utils/ composables/ components/ layouts/ styles/main.css views/ tools/registry.js
wallpaper-engine/           Wallpaper Engine 网页壁纸壳页(机制见该目录 README)
```

**具体路径查 `docs/代码地图.md`**(Controller 前缀 / 表↔实体↔XML / 路由↔视图 / api 分组,均由代码生成)。

## 构建与验证

**日常调试**:改完一部分就在仓库根跑 `node scripts/related-tests.mjs --run`——读 git 改动,前端按静态 import 关系挑 spec(页面/组件只取直接引用,utils/composables 两跳;i18n/样式/HTML 注入面等扫全仓的守门 spec 按改动内容触发),后端挑同名 `XxxTest` + 测试源码里引用该类名的测试类。手动挑时前端指定 spec、后端指定测试类或方法,手工按用例号选取;未命中时补查覆盖并说明验证范围(命令见 `test/README.md`)。**全量回归只在 CI、发布前或明确要求时跑**,平时不要 `npm test` / `-B test` 一把梭。

```powershell
cd backend;  .\mvnw.cmd -B clean package      # 编译+测试+打 jar → target/ihomy-backend.jar
cd backend;  .\mvnw.cmd -B clean package -DskipTests   # 只求编译
cd frontend; npm run dev                      # 5173,代理 /api → 8080
cd frontend; npm run lint; npx vitest run; npm run build
```
- **基线**:后端 531 tests / 70 suites;前端 lint 0 error/0 warning + vitest **1726 passed / 162 文件**（V14.64 实测） + `format:check`(历史遗留口径 踩坑速查 §1.13,**本次改动文件必须 `prettier --write` 后通过**);首屏初始 JS **43 chunk / 1481.00KiB**(V14.4 实测,按 `dist/assets/index-*.js` 静态 import 闭包求和);autotest `pytest -m api` 37 passed(仓库外 `..\autotest_framework`)。
- **冒烟**:`GET /api/auth/captcha` 取 id → `POST /api/auth/login {email,password,captchaId,captchaCode:'qwer'}`,code=0 即有 token。**健康检查** `/api/public/health`(UP→200 / DEGRADED→503)、`/api/ops/health`(OPS,含依赖耗时)。
- **闸门(CI 最先跑)**:`doc-drift-check.sh` + `gen-code-map.sh --check` + `doc-index.sh --check`;随后前后端构建、起库导 schema、登录冒烟。
- 本机 JDK 21、Node 24 LTS(最低 22.12)、MySQL 8;没装 mvn 用 `C:\Users\chill\AppData\Local\Temp\opencode\apache-maven-3.9.9\bin\mvn.cmd`,`JAVA_HOME=C:\Program Files\Java\jdk-21`;clean 被锁改跑 `-B test`。
- 文档生成/工具导出:`npm run tools:export`(+`:check`);Word 交付 `scripts/docs-gen/pipeline.sh`;放映厅 Jellyfin 见 需求 §4.3.4 / 踩坑速查 §5。

## 统一响应与鉴权

- 响应 `{code:0,message,data}`,code≠0 即失败;异常走 `BizException` + `GlobalExceptionHandler`。
- 双 token access 2h + refresh 7d;**refresh 每次使用即轮换拉黑**(60s 宽限返回同一份防多标签互踢);壁纸独立 `type=WALLPAPER`(不轮换,改回前必读 踩坑速查 §6)。
- **`authenticationEntryPoint` 必须返回真实 401**;前端统一 `redirectToLogin()`(§2.4)。
- **令牌过期预检(强制)**:请求前用 `jwt.isTokenExpired` 判断,过期且 refresh 有效先续期——只靠 401 会让 permitAll 接口永远拿不到登录态(`/auth/*` 跳过防递归,§3.20)。
- 权限:`buildTokens` 返回 `permissions` + `isOps`;前端 `hasPerm`/`isOps`/`isPureOps`,OWNER 恒真;**OPS 只认系统级绑定**(`family_id IS NULL`),一律 `userStore.isOps`,**禁 `hasPerm('ops:view')`**(§2.5)。
- 点赞/评论/通知准入「同家庭 or 已分享且非 PRIVATE」,其余 NOT_FOUND;匿名接口按登录态返回空数据,不许 NPE;跨家庭编辑/删除 404(id 可枚举,403 泄露存在性)。
- **未登录看到演示家庭(1 号,`is_default=1`+`is_public=1`)**:游客只取 PUBLIC,演示内容保持饱满,纪念日写接口 `invalidateHomeCache`(§7.9)。
- 防爆破:登录/验证码/注册按 IP 限流 + 失败计数(429);`must_change_password=1` 令牌带 `pwdChange`,只放行改密与登出;CORS 白名单 `app.cors-allowed-origins`;接口前缀 `/api`,SpringDoc `/api/doc.html`。

## 跨模块规则

- **首页/导航**:分组顺序 `content → life → family → system`;`sort_order` 决定组内(改完重启);**移动端导航唯一源 `utils/navModules.js`**。家谱并入「家庭成员」,提醒/纪念日/计划并入「日程提醒」,被并模块 `enabled=0`;**纪念日下次日期只由 `AnniversaryDate` 算**。**两套布局跟账号走**:localStorage + `sys_user_pref`(`home.layout.v3`/`home.warm.v3`),服务端为准覆盖;游客只写本机(需求 §4.7.7)。
- **可见性与分享**:判定唯一入口 `ContentAccessService`(新增类型只加 `ContentKind` + `load`);分享统一 `GET/PUT/DELETE /{type}/{id}/audience`,保险箱/智能家居/未知 404。**分享=写入时展开**:圈只用于发布弹窗,落库 `content_audience` 逐家庭行,退圈/解散按 `source_circle_id` 收回;每家庭≤3 圈、每圈≤6 家庭;**保险箱/账本/PRIVATE 永不进圈**;相册以 `content_photo_album.visibility` 为准;接收方只读(`sharedFrom`/`readOnly` 服务端落实)。
- **日程**:写侧分类唯一来源 `utils/scheduleType.classify({hasDate,repeat,remind})`(**后端不推断**,五类 需求 §4.3.6);循环完成按 (提醒,日期) 落 `family_reminder_check`,`toggle-done?date=` 带日期切换;判定统一 `RepeatSchedule.dueToday`;聚合只读 `GET /schedule/events?from=&to=`(≤62 天)/`upcoming`,新来源只加 `ScheduleSourceProvider`。月历全来自 `/public/calendar`,前端禁引农历库;**分享来的提醒只读且不进原生闹钟**。改日程先读 踩坑速查 §3.39–§3.42。
- **工具箱**:元数据唯一源 `tools/registry.js`;12 个开发者工具只在 `/bit-pouch/tools/<id>` 维护(`toolbox:false` + 旧路由重定向);在线目录 24 项另含 7 项只在线页维护(需求 §4.12)。
- **天文**:`SolarUtil` 唯一来源(`/public/sun-info`),前端只渲染,**禁再移植 NOAA/Meeus**;直射点**西移** 0.25°/min;`useSunLight` 30 分钟换新,**长开页面禁止把启动时那份当此刻**(§4.5)。
- **健康数据**:本人独占——读接口传别人 `userId` 一律 404,要给别人看只能本人 `POST /health/share` 生成 7 天只读快照(读链接只回冻结 payload,不查业务表,到期/手动删即物理删);报表锚**最新数据日** `asOf`,卡片目录 `common/HealthReportCard` + `views/health/reportCards.js` 同口径,告警口径 `common/HealthAlerts`(参考区间 WARN + 个人基线 ±2σ INFO),卡片偏好 `health.report.v1`;敏感指标入快照要本人同意且本次勾选(需求 §4.20)。
- **独立产品页**:`/kada`、`/bit-pouch`、`/wallpaper` 必须 `meta.standalone`(不套外壳、跳过 `appStore.init()`、401 不顶登录页、自带配色接管 `html`);**回站内页要补初始化**,否则首页全空(§3.32/§3.8)。
- **产品统计**:落 `report_product_stat` 日表(落地页打点 + `/public/product/download` 计数后 302),**禁止按 access 日志统计下载**;下载文件名必须纯文件名(`uploads/kada/` 平铺)。
- **成就**:目录内置代码单源 `common/AchievementCatalog`(分类/指标/阈值/成就分,前端 `utils/achievements.js` 同口径,**不入库、不许家长自定义**);`family_achievement` **只落已解锁行**(`uk_user_code`),未解锁进度实时聚合,**惰性结算**=进成就页 + 签到后各一次,游戏/内容不挂判定;游戏留痕 `family_game_record`(`game_code` 统一去 `game_` 前缀,单人/房间/对战三来源);**成就分只换 6 档等级,不发可消费积分**;成就属用户全局、家庭榜只取当前家庭成员。需求 §4.5.11。
- **运维管理**:11 个入口分「运行监控 / 日志与预警 / 服务用量 / 维护管理」四组,导航按面板保留筛选状态;资源总览 15 类可点开分页明细 `GET /ops/stats/records`(ops:view,复用 `OpsService` 资源目录,**用户/家庭记录返回 `name` 且名称在首列**);运维路由套暖居纸面,纯 OPS 用专用外壳。
- **计划提醒单向托管**:时间只存 `family_reminder`(`plan_id`),`family_plan` 不加时间列;写操作全走 `FamilyPlanService` 单事务;重复判定共用 `RepeatSchedule.dueToday`。
- **模拟器与存档**:`game_info.type` 存机种码(扩展名唯一源 `common/GameSystem`,前端 `utils/games/emulatorSystems.js` 同口径);**新增机种=三处同步**(GameSystem、前端注册表、`copy-emulatorjs.mjs`)。存档唯一源 `uploads/games/{游戏名}/saves/u{成员id}/`(**按成员隔离**),ROM 加载前预置最新档,30s/切后台/离开回写;浏览器 IndexedDB 不持久化。**离开/重开 ROM 必须先 `pause()`+`toggleMainLoop(0)` 再拆 DOM**(踩坑速查 §3.52);**禁止存档另存 DB**。细节 需求 §4.13.2。
- **小游戏与房间**:进度 `localStorage`(`ihomy:games:*`;**小院慢生活例外,存档在服务端**),校验在 `utils/games/*` 纯函数;`game-surface` 触摸守卫,长按必须 gesture 吞 click(§3.57);**十五个 H5 奖励统一 `POST /game/{code}/reward`**(双人八球/中国跳棋/五子棋/飞行棋无奖励端点);纸牌 `/games/cards` → `klondike|spider`,网页/静态游戏 `/games/web/{id}` iframe,桌面游戏走安装包托管;固定入口含**双人八球、中国跳棋、五子棋**(分类 `BOARD`/`CARD` 在 `common/GameCategory`);**数独 17 变体**,影响格唯一 `buildPeers()`;**一屏适配 `useGameView.js`**,全屏 `el-select` 必须 `:teleported="!isFullscreen"`(§3.67),测高禁 rAF(§3.68)。**一起玩 2~8 人房主权威**(`GameConst.ROOM_GAMES` 16 种,八球 PK 2 人):手牌不上服务端(`mj:public` 只放张数),退出必须落接口(房主 `end`、访客 `leave`,成员行物理删),房间局不发单机奖励;**对局页与单人模式一致,不挂房间条**(通话在聊天浮窗、托管在手牌上方、返回=回房间页,退出者席位转电脑),房主可 `kick` 请人(仅未开局/已结束);房间内另有**临时聊天室**(`RoomChatBuffer` 内存、WS `chat`/`chat:sync`,不落库)。**四种牌局(斗地主/掼蛋/保皇/够级)房间局走 `composables/useCardRoomPlay.js`**:房主唯一裁判,`xx:public` 只放各张数与出牌区、`xx:hand` 手牌点对点发本人、`xx:act` 意图由房主用引擎复验、`auto` 托管、`sync` 重连补发,35 秒不动作交电脑,座位 3/4/5/6 超出转观战(观战只看不坐,`watching` 隐藏操作区)。**统一外壳 `GameShell.vue` 是 26 个舞台页的唯一布局权威**(名单与守门 `views/games/gameShellAdoption.spec.js`;尺寸走 `100cqw/100cqh`,禁 `calc(100dvh - Npx)`);**手机横屏两栏** = 左栏按钮(纵向满宽)+ 底边计分芯片、游戏区跨两行,芯片**两列、半栏放不下自动退一列、纯文字无框**(与按钮形状区分)。细则 需求 §4.13.2、UI §43、坑 §14。
- **放置游戏服务端权威**:小院慢生活 `/games/homestead` 的进度只由服务端结算(`game_homestead_*` 4 张表),**客户端禁做本地收益模拟**(只按返回的 `serverNow` 插值进度环,60s 与切回前台拉 `/state` 对账);离线只结算当前动作、100% 效率、≤24h,技能升级按「升到下一级的剩余次数」分段推进;内容与数值唯一源 `resources/content/homestead/*.json` + `homestead/core/HomesteadEngine`(整数毫秒/基点、注入时刻、无随机);小院指数 = Σ等级×1000 + 铜板 + 库存卖价×50%,读取时现算不落列;升级积分服务端直发(日限 5 次),无通关奖励端点。需求 §4.13.2。
- **记账本**:整本按成员档位(`family_book_permission`:NONE/READ/WRITE/MANAGE,**无行=WRITE**),OWNER 恒 MANAGE;**不走 `@RequirePermission`**,用 `BookPermissionService.requireRead/requireWrite`;账户 chip **只客户端过滤本月明细**(`accountRecords`,不新增请求)。**贷款** `account_id`=初始还款账户,换账户走 `ACCOUNT_CHANGE`(**不生成转账**),无初始账户按首次绑定(`foldInitialAccountChange`);到期期次调 `POST /loan/repay/sync`,后端按 `(loan_id,period)` 幂等补插;调息落在还款区间内**该期按天分段计息**;**月供唯一源 `utils/loan.js`**。需求 §4.5.5/§4.12.4。
- **聊天室**:WS 协议 v2,消息六类(`common/ChatMsgType`,payload ≤4KB,渲染禁 v-html);连接由 `stores/chat.js` 全局单持有(退避重连 3→60s,登出清场);**同家庭或同亲友圈可私聊**(`sys_chat_dm_message`);浮窗键 `ihomy:chat:float:v1`;通话/对战信令 WS 定向,家庭隔离服务端校验;通话结束单点落 CALL 卡(Redis 幂等)。协议 踩坑速查 §11/§13。
- **家庭封面**:一字段三档——门牌(暖居 44×44 / 光尘 30×30)、识别(列表 56×56、移动端 44px)、对外(分享落地 16:9);`PUT /family.coverImage`:缺省/null=不改、空串=清空落 NULL、新值=换图删旧(`/files/` 前缀把关);`?hid=` 只经 `parseShareQuery` 校验后传 `/public/home`。
- **早安、移动端与屏保**：每日首屏 `MorningOverlay.vue`，场景取 `utils/morning.js`，底栏五槽位含拍照。全端只读屏保共用 `ihomy:screensaver:v1`，空闲 1–30 分钟触发，三种背景、五张卡片可选，点击或按键只唤醒。细则 UI §38/§40。

## 后端规范

1. **三层**:Controller 只校验+调 Service+返回 `Result`;Service 单实现无接口;Mapper 只有 `BaseMapper`,自定义 SQL 全放 `mapper/*.xml`(禁 `@Select/@Update`,参数 `@Param`);表单按列宽声明 `@Size`/`@Digits` + `@Valid`。
2. **权限与留痕**:`@RequirePermission` + 切面(OWNER 恒真);写接口必加 `@OperationLog`(module 大写/标准词/中文描述,高频端点除外);traceId 由 `TraceIdFilter` 写 MDC + `X-Trace-Id`。
3. **SQL 日志**:`SqlStatementLog`,`logging.level.mybatis.sql` 默认 `warn`(排查调 `debug`);**禁 `System.out.println`**。
4. **软删**:`@TableLogic deleted`;**物理删/读已删必须走自定义 XML**(MP `deleteById` 是 UPDATE);回收站/恢复/清理全用 XML。
5. **隔离与 N+1**:业务数据带 `family_id`(JWT 是快照,refresh 解析),跨家庭 NOT_FOUND,`NULL`=系统级;列表禁 for 循环 `selectById`,先 `selectBatchIds` 再 Map 回填。
6. **缓存与 UPDATE**:键 `ihomy:{domain}:{id}`,用户/权限/公开首页 TTL 5min,**变更点显式 invalidate**(矩阵 踩坑速查 §9);不变数据用内存缓存(预热+双检锁,按 familyId 分桶,**不引 Caffeine**);冗余字段用 `LambdaUpdateWrapper...update(null)`,**计数 `setSql("x = x + delta")`**,清空必须 `.set(col, null)`(§8.3/§2.2/§2.3)。
7. **上传与运行参数**:禁 `file.getBytes()` 全量入堆(生产 `-Xmx384m`),用 `MultipartFile` + `transferTo`;虚拟线程开启;HikariCP `maximum-pool-size:20` / `minimum-idle:5` / `connection-timeout:3000`。
8. **日志(强制,细则 `docs/设计想法/日志/`)**:access/server/thirdparty 按天留 7 天;六要素含 `[tid]` 贯穿 HTTP/WS/@Async;三方调用走 `ThirdPartyHttp`;**报错带堆栈** `log.error("xx, p={}", p, e)`(禁 printStackTrace);ERROR=人工/WARN=可恢复/INFO=关键;新敏感字段进 `AccessLogFilter.SENSITIVE_JSON`;**同类异常聚合预警** `LogAlertAppender` 挂 root+ACCESS+thirdparty(`app.log-alert.*`);`report_weather` 留 7 天,04:10 清理。

## 前端规范

1. **API 与状态**:`api/index.js` 按模块导出 `xxxApi`,统一走 `api/request.js`(JWT + 自动续期;拦截器**已解包返回 data**);Pinia `user.js`/`app.js`/`theme.js`。
2. **路由**:`meta.public` 免登录;`meta.ops` 需 OPS;纯 OPS 只进 `/ops`;全懒加载。
3. **样式与图标**:CSS 变量(`main.css`)+ `html.dark`;不声明 serif;需被类覆写的内联样式走变量(§3.4);图标 `el-icon`,Setting/Monitor 内联 SVG(`stroke-width:2px`)。
4. **动画与毛玻璃**:GSAP 入场;`translateZ(0)` 隔离合成层;`contain: layout style`;避免 `background-attachment: fixed`;毛玻璃 `blur(24px) saturate(1.1)`,hover 用 `transform`,**长开/持续动画页别用**(§3.10);持续动画 CSS `@keyframes` > GSAP > rAF,**禁 rAF 每帧写 ref**。
5. **可拖拽与手势**:`useDragResize`(位置尺寸持久化,监听按需挂载,**指针捕获只在真拖动时做** §3.35,手柄 Pointer Events + `touch-action:none` §3.61);移动横滑只识别意图不 `preventDefault`,起手在横滚容器/输入框/弹窗内不接管,切 Tab 记住 `scrollY`。
6. **光影全局化**:`SunLightLayer` + `AppSidebar` + `SiteFooter` 在 `App.vue` 挂载,`useSunLight` provide/inject。窗格、树影、桌沿物件共用投影遮罩与深度。**阴影深度 0–200 默认 130(70 即满深度)、羽化 0–100 默认 50**;修正档先用所选日表最高高度角标定正午构图(冬夏至纵深比 1.4 / 窗宽 95% / 春秋分 90%),日内按实际高度角与方位角沿入射射线拉长;真实档才用 `物高/tan α`。**页面与光影测试共用同一太阳场景**(控制台读正在绘制的日表+时隙,别各算一套);开窗窗扇的框条必须**合并轮廓后每束光线只擦一次**(否则接缝漏光);**手机光影只在 DEV/test/staging 开放**(`useDevice.lightAvailable`,生产网页与正式壳包隐藏开关)。新增桌沿物件按 `docs/桌沿物件接入规范.md` 登记有效高度与入口(手动拖动只在当前页面生效,不落盘)。
7. **i18n**:可见文本 `$t`,中英对齐;`en` 独立懒加载(`ensureLocale`);`{`/`}`/`@` 必须转义否则构建断整页;守门 `message-format.spec.js` + `hardcoded-cjk.spec.js`(保留项进 `ALLOW`)。
8. **共享逻辑**:重复逻辑先抽 `utils/`(动态流 `feed.js`、枚举 `dictText`、金额 `money.js`、日期 `date.js`);computed 禁 `Math.random()`/`Date.now()`/副作用,一次性计算用 `ref` + `watch(immediate)`。
9. **可点击非按钮与文案**:用 `v-a11y-click`;`<img>` 必须 `alt`;用户可见文案、`BizException`、`@Operation` 只讲用户能做什么,不写表字段/配置键/枚举码/节律。
10. **分块与异步**:Rolldown `codeSplitting.groups` 拆 element-plus/gsap/vue-i18n/epubjs/pdfjs/simple-mind-map/three;`includeDependenciesRecursively:false` 必须配 `strictExecutionOrder:true`(否则 `_ is not a function` §1.16);`public/` 不得与 npm 包重复;阻塞首屏的字体/CSS 用 `import()`。
11. **并行请求**:独立请求 `Promise.all`,串行只在真有依赖时。
12. **全局 UI 统一**:EP 配色/圆角/尺寸/z-index 一律 `main.css` 覆写;只用 `--z-*` 标尺(禁 ≥100 裸值,守门 `zLayers.spec.js`);遮罩 `--overlay-scrim`+`--overlay-blur`;**光影永远在弹窗之上**(`--z-deco` 270–290、`--z-atmos` 300–308 > `--z-overlay` 200);**新弹窗必须声明宽度或 `dialog-sm/md/lg/xl`**(UI §29、坑 §3.15/§3.25/§3.36)。
13. **按钮/表格**:四类按钮(浅深不共色值)、el-tag 磨砂、el-badge 半透明黑、圆角 button 12 / input 10 / card+dialog 14(UI §18a);**表格操作列只用图标按钮**,**行底色必须实色**(否则 fixed 列透出,UI §18e、坑 §3.69)。
14. **页面规范**:根容器 `class="page"`(禁 scoped 覆写 max-width/margin/padding);页面级 H1/H2 移除用 `.section-label`;工具栏 `page-toolbar card`,**单槽操作必须包 `.tb-right`**;多选 `.pick-badge`(**禁左上 checkbox**);**横向内容条不许让滚动条占布局盒**(隐原生条 + 边缘渐隐 + `horizontalWheel.js`,UI §11b、坑 §3.70)。
15. **位图资产**:装饰图放 `src/assets/` 并 ESM 导入,**不放 `public/`**;宽度按渲染 2 倍封顶(例外:演示素材、`kada-media/`);**`public/` 目录名不得与路由同名**;工具导出 `public/toolbox-data/`。
16. **富文本消毒**:一律经 `utils/markdown.js` 的 `renderMarkdown()` 再 `v-html`;**禁 `marked.parse` 直接进 `v-html`**,`marked` 只许该文件导入;守门 `htmlSink.spec.js`。

## 文件存储

- 本地磁盘:根 `file.upload-dir`(生产 `/opt/ihomy/uploads`),DB 存 `/files/...` URL,Nginx 托管;**不主动改 FileService 存储实现**(接 NAS 优先 NFS 挂载)。目录:相册 `pictures/{相册名}/`、视频海报 `videos/`、音乐 `music/`、电子书 `books/{yyyyMM}/`、通用 `files/{yyyyMM}/`。
- 存储设备 `sys_storage_device`(家庭级,SYSTEM/NAS/REMOTE/MOUNT/BAIDU/NEXTCLOUD/WEBDAV;OSS/S3 暂缓),写需 `storage:manage`。
- **删除=进回收站**(照片/相册/视频/图书置 `deleted=1`+`deleted_at`,文件保留;博客封面/头像/家庭封面/音乐/家谱照片不连带);`/recycle` 恢复/彻底删,每日 03:00 清理超 7 天。
- **`/files/**` 鉴权**:登录放行;游客按 URL 反查只放 `visibility='PUBLIC'`(清单 `mapper/FileAccessMapper.xml`,**新增带文件 URL 的表必须补一行**,保险箱不放);`/files/kada/**` 免登录;`/files/*` 带 nosniff+CSP;**nginx 必须反代,不得 alias 直出**(踩坑速查 §7.8);资源长缓存靠 `?v=` 破。

## 配置与加密

- **外挂配置是唯一开发/生产差异**:`IHOMY_CONFIG_PATH` 指向 yml,`ExternalConfigLoader` 启动早期加载覆盖 `application.yml`;**改配置前先核实它实际指向**。不用 profile,`application.yml` 即生产基线。
- DB/Redis 密码明文(避免鸡生蛋);业务凭证 AES-GCM(`AesUtil`,PBKDF2 100000 + GCM 128),密文 `ENC(Base64)`,盐 `sys_parameter(aes-salt)`;OPS 加解密 `GET /api/ops/crypto/encrypt|decrypt`。
- **可扩展点**:①天气 `WeatherService` + `WeatherProvider`(新增=常量+实现+前端下拉);②首页组件四档 snap + hover 推开(布局键见上);③AI 家庭模型池 `sys_family_ai_model` + 按功能绑定 `sys_family_ai_feature`(`resolveForFeature/resolveChain`)。

## 部署约定(Linux 2GB 求稳)

- MySQL 本机(~180MB)+ Redis 用 Docker;后端 `ihomy` 用户跑 systemd,MySQL 用 `mysql`,Nginx 用 `www-data`;除装包/建用户/systemd/`/etc/`/防火墙/证书/导 schema 外不用 root。JVM `-Xmx384m -XX:MaxMetaspaceSize=192m -XX:+UseSerialGC -Xss512k`;MySQL `performance_schema=OFF`,端口 6306。
- **Redis 只绑回环 + 强密码**:`-p 127.0.0.1:6379:6379` + `--requirepass`(写在**镜像名之后**);镜像固定 tag(当前 `redis:8.10.3`),缺 tag 经 daocloud 拉取后 `docker tag` 回原名;**改密顺序**:external.yml → 停后端 → 重建容器 → 起后端 → 冒烟(踩坑速查 §7.17)。
- SSH 统一 **19068**(禁 22),ssh/scp 带 `-p 19068`;配 fail2ban 与自动安全更新。证书已转 **acme.sh**(`/etc/ihomy-cert/`,crontab 续期),别再手改 certbot 路径(排障 踩坑速查 §7.18/§13.5)。
- 备份:`scripts/prod-backup.sh`(mysqldump 单事务 + gzip 校验 + uploads 只增不删,留 14 天)由 systemd timer 每日 03:00 跑;恢复 `prod-restore.sh <dump> <目标库>`。
- **站内备份与迁移**:策略是独立对象(`sys_backup_policy`:勾选模块 + 每天/每周/每月 + HH:mm + 可选保留天数 + 启停,可建多条),列表直接看最近执行/下次执行;模块↔表是显式映射(`BackupModule`,新增表必须归模块),`FILES` 模块带 uploads 硬链接增量;产物落 uploads 同级 `backups/`(**不进 nginx `/files/`**);**只导出,恢复按包内 `RESTORE.md` 在服务器做**,不做全库覆盖;家庭导出 `family:manage`,恢复需全新空实例(保留原始 ID)。细则 `docs/数据备份与迁移.md`。
- **生产只留构建产物**:`jar`、`dist`、`config/external.yml`(+`mysql/my.cnf`)、`uploads/`、`backups/`、`logs/`、`VERSION`、两个备份脚本;源码/docs/.git 不在服务器,构建上传走本地 `scripts/deploy.ps1`。
- **重装/新服务器四条必查**(nginx `mime.types` 有 `.mjs`、站点 conf 有 gzip、静态缓存排除 `/api/`+`/files/`、媒体地址同为 HTTPS:踩坑速查 §7.1/§7.3/§7.10);**部署新前端后老客户端仍跑旧 bundle(PWA 预缓存)**,冒烟前 unregister SW + 清 caches,不带哈希的运行时文件必须 `?v=` 破缓存(§7.2/§3.55)。
- **多端发版(强制)**:`VERSION` 唯一源,每端各有通道——网页/PWA `scripts/deploy.ps1`(dist)、Android `scripts/release-app.ps1`(APK;**`/public/app/latest` 以 `uploads/app/` 最高版本包为准,上传即生效**,`latest-notes` 仅在配置与磁盘一致时输出,`min-supported-version` 由配置)、iOS 走 App Store。**壳内前端打进 APK,只发 dist 客户端不会变**;`app:sync` 会覆盖 `dist`,发完必须 `npm run build` 复原。站内 `/public/version` 比对 bundle 并提示刷新。清单 `docs/部署指导-Linux.md` §8·补.5。

## 归档与规划

- **收尾分两级**(流程走 `/archive` 技能):
  - **轻量收尾(每次迭代后自动)**:更新 `VERSION` → 追加当日 `docs/daily/` → `gen-code-map.sh` → `doc-drift-check.sh` 全绿(漂移只做最小修补)→ commit。**不主动读改** 需求/AGENTS/踩坑速查/UI 提示词/变更归档。
  - **全量归档(仅明确说「归档」)**:`docs/daily/` 按类型分流进四文档 + 踩坑速查 → `doc-index.sh` 重建索引 → drift 全绿 → commit;处理完移入 `docs/daily/archived/`。
- `docs/变更归档.md` 的 **V10 之前条目已压缩为「标题 + 一句话摘要」**,细节查 git 历史;V10 起完整条目追加。
- **未实现规划的唯一去处是 需求 §9**(§9.1 产品级 / §9.2-§9.5 代码级);本章不记内容,启动规划项前先读对应小节。开工新功能前 `grep schema.sql + router/` 对照模块种子。

## 文档与服务

- 索引:`docs/README.md`(阅读顺序)、`docs/架构设计.md`、`docs/代码地图.md`、`docs/归档索引.md`、`test/cases/功能测试用例.md`。
- `scripts/`:构建/启动/文档生成/闸门/备份恢复/发版/软著等,清单 `docs/README.md`。
- **代码事实以 `backend/src/main/java` + `resources/schema.sql` 为准**;文档与代码冲突先改文档再改代码,或先读 `docs/踩坑速查.md` 确认不是已知取舍。
