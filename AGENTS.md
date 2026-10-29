# AGENTS.md — ihomy 项目规则

> 跨会话自动加载。本文件**只放结论级规定 + 索引**,目标 **≤30KB**(硬上限 45KB:超了注入会被截断,尾部先丢)。详细内容各归其位,别写回本文件:

| 内容类型 | 去处 |
|---------|------|
| 功能现状(模块能力/表结构/接口/规划) | `docs/需求设计说明书.md`(§4 功能 / §6 数据库 / §7 接口 / §9 规划;**就地改写=当前事实**,不写时间/版本叙事) |
| 实现记录(逐次改动/设计决策/live DB 迁移 SQL) | `docs/变更归档.md` 末尾追加 `#####` 小节;索引由脚本生成到 `docs/归档索引.md` |
| 踩坑成因、机制解释、维护清单、缓存失效矩阵 | `docs/踩坑速查.md`(本文件只留一句结论或 `§x` 指针) |
| UI/交互/视觉细则 | `docs/UI设计提示词.md` |
| 代码路径索引(功能 ↔ 文件) | `docs/代码地图.md`(**自动生成,勿手改**:`bash scripts/gen-code-map.sh`) |

## 任务 → 先读哪几个文件

| 任务 | 顺序(先定位,再动手) |
|------|---------|
| 找某个功能在哪 | `grep -in <关键词> docs/代码地图.md` → `XxxController` / `Xxx.vue` / 表名 |
| 加/改接口 | 需求 §7 → `controller/XxxController.java` → `service/XxxService.java` → `resources/mapper/XxxMapper.xml` → `api/index.js` |
| 加页面 | `router/index.js` → `views/**` → `utils/navModules.js`(移动端)→ i18n `zh-CN.js`+`en.js` →(工具类)`tools/registry.js` |
| 改表结构 | `schema.sql` + `migrations.sql`(幂等)→ `entity/` → mapper XML → 需求 §6 |
| 加/调首页模块 | `sys_home_module` 种子 → `HomeModuleService`(内存缓存,改完重启)→ 移动端入口 |
| 加工具 | `tools/registry.js` → `utils/tools/` 内核 → `npm run tools:export` |
| 查历史怎么改的 | `docs/归档索引.md` 的「文件 → 小节」反查 → 对应 `docs/变更归档.md` 行号 |
| 排查线上问题 | `docs/踩坑速查.md` 按关键词 → 日志按 tid:`GET /api/ops/logs/trace?tid=` |
| 收尾归档 | `.agents/skills/archive/SKILL.md`:写文档 → 跑两个生成器 → drift check → commit |

## 硬性禁令(违反即返工)

> **Git**:非人工指令禁止 `git commit`/`git add -A`/`git add .`/`git push`;`git add` 只能指定具体文件。**例外(已授权)**:归档流程写完全部文档后自动 commit;`git push` 仍需指示。
> **敏感数据**:**生产**密码/密钥/token 一律不进仓库(生产 DB 密码与 JWT 密钥走服务器 `external.yml`,模板 `backend/src/main/resources/external.yml.template`);凭证台账在本地 `Linux部署指导.md` §〇,开发账号在本地 `docs/新人上手指南.md`(均 .gitignore);前端演示凭证走 `frontend/.env.development.local`(**`.env.local` 会内联进生产 bundle,禁用**)。`schema.sql` 是开发安全版,生产须 `ALTER USER` 改强密码。
> **多会话并行**:禁用 `git stash`(会把其他会话未提交的 i18n/router 改动一起回滚);他人 stash 不 pop,只读 (`git show "stash@{0}:<path>"`);不并行跑 `npm run build`(争 `public/emulatorjs`、`dist/`)。**动手前先 `git status`**:工作区有他人未提交改动时,不要整文件重写共享文件。
> **工具链**:
> - 工作目录 `C:\Users\chill\OneDrive\WorkStation\Projects\ihomy`;读写前逐字核对路径。
> - **含中文的源码/配置/SQL/文档一律走 Read/Write/Edit 工具**,禁止 PowerShell `Get-Content`/`Set-Content`/`WriteAllText`(成因见 踩坑速查 §1.1);PowerShell 只用于 npm/mvn 构建与 HTTP 冒烟。
> - 脚本化改文档必须保留 CRLF:`open(p,'rb')` + `split(b'\r\n')` + `b'\r\n'.join()`;复核 `\n` 数减 `\r\n` 数 = 0。
> - PowerShell 5.1:原生参数的点号开头要加引号(`'-h127.0.0.1'`);传参别嵌双引号;含中文的 `.ps1` 必须 UTF-8 BOM(踩坑速查 §1.3)。
> - Git Bash 调 Windows 原生命令:路径参数加 `MSYS_NO_PATHCONV=1`(否则 `/tmp/x.sql` 被转成 Windows 路径,`docker cp` 静默失效)。
> - 打包 zip 用 `tar -a -c -f out.zip dir`(别用 `Compress-Archive`)。
> - 写 MySQL 中文:Write 出 UTF-8 无 BOM 的 SQL → `MSYS_NO_PATHCONV=1 docker cp` 进容器 → `docker exec ihomy-mysql mysql --default-character-set=utf8mb4 ihomy -e "source /tmp/x.sql"`。
> - **`mvnw.cmd` 必须在 `backend` 目录内调用**;后端单实例运行 `C:\Program Files\Java\jdk-21\bin\java.exe -jar target\ihomy-backend.jar`(双实例分流 8080 会偶发 401/404/500);jar 锁按 PID 结束(含 IntelliJ 的是 IDEA 本体,不能杀;**禁 `taskkill /IM java.exe`**)。
> - **OneDrive 工作树 `mvn clean` 删不掉 `backend/target`**(目录带 ReadOnly+ReparsePoint,非进程占用):三个构建脚本已在 `mvnw clean` 前自动调 `scripts/clean-build-target.ps1`(清只读→强删→rd 兜底→改名);IDE/手工构建卡住时按咔哒 `Alt+Shift+I` 或直接跑该脚本(踩坑速查 §1.14)。
> - 内存缓存(如 `HomeModuleService`)**直改 DB 不生效**,须重启后端或走 evict 接口(踩坑速查 §2.1)。

## 项目概述

ihomy:家庭内部内容共享平台(PC / 安卓 / iOS,均 PWA)。功能域:登录注册、博客、日记、相册、纪念日、留言板、放映厅、聊天室、积分商城、任务悬赏、提醒、计划、愿望单、记账、家谱、书架、工具箱、运维等;首页模块化,**新增功能=插一条 `sys_home_module` 记录**。
技术栈:Vue3 + Vite + ElementPlus + PWA(`ihomy-frontend`);Spring Boot 4 + MyBatis-Plus + MySQL 8 + Redis(双 token JWT + 验证码 + WebSocket),包 `com.ihomy`,主类 `IhomyApplication`。
Nginx 静态根指向 `frontend/dist`,构建即生效。

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

**业务概念不改**:`family` 表、`Family` 实体、`family_id` 字段、`OWNER/MEMBER` 角色名属于「家庭」业务领域,不是应用标识。

## 版本号规则

- 形态 `V<主>.<次>`(次位不补零)。**根目录 `VERSION` 是唯一事实来源**(前端 `define __APP_VERSION__` 注入页脚与运维页);改版本只改这一处。
- 每次可交付迭代次位 +1;**次位满 100 强制进位主版本**(`.99` → 主 +1、次归 0),结构性里程碑可提前进位;禁止三位次位。`V1.0–V9.110` 历史编号冻结不改(保住 `grep V9.x`)。
- 收尾:更新 `VERSION` + 该版本收尾提交打 annotated tag `V<主>.<次>`(消息=日期+一句话);`git push origin <tag>` 随发布。
- `frontend/package.json` / `backend/pom.xml` 的版本字段不参与本规则(仍 `1.0.0` / `1.0-SNAPSHOT`)。

## 数据库约定

- **root 仅初始化**:`mysql -uroot -p < schema.sql`(建库/建表/建账号/种子),只执行一次;本地 `.\scripts\start-db.ps1`(Docker 首启自动导入)。**业务运行用 `ihomy` 账号**(仅 `SELECT/INSERT/UPDATE/DELETE` on `ihomy.*`,无 DDL),建 `localhost` 与 `%` 两个 host。
- 生产 MySQL `validate_password` MEDIUM:密码须含特殊字符(避开 `' " \ $ |`,建议 `!@%^&*-_+=.`),改密顺序见 踩坑速查 §2.7。
- **86 张表**,前缀:`sys_` 26(系统/账号/权限/配置/存储)、`report_` 5(报表/日志)、`family_` 33(家庭事务)、`content_` 21(内容数据)、`game_` 1(games 域)。完整清单见 需求 §6.2。
  - 命名:家庭事务 `family_`;内容 `content_`;账号/权限/配置/存储 `sys_`;报表/日志 `report_`;
  - 游戏域顶层前缀 `game_`,格式 `game_<code>_<module>_<entity>`(模块层仅当 ≥3 张表/独立聚合根时才建);注册表 `game_info`,小游戏页只读注册表,**加游戏=插一行不改组件**。家圃时光 V2 已迁独立仓 `home_garden_moments`,本仓只留集成(`game_info` + 游戏库入口)。
- **结构变更双文件**:同时改 `schema.sql`(全量)与 `migrations.sql`(增量、**幂等**),并按 CI 同路径做空库全量导入实测(`migrations` 跑通 ≠ `schema.sql` 可用,踩坑速查 §7.4)。
- **枚举不用数字**:状态/类型一律大写英文(`PUBLISHED/PUBLIC/ACTIVE...`),含义在 `sys_dict_item`,Java 常量在 `common/DictConst.java`,前端映射 `utils/dict.js`;**禁止写回 0/1/2 判断**。
- `content_blog/diary/photo/video/wish` 的 `visibility` 为 `VARCHAR(20) DEFAULT 'FAMILY'`(PRIVATE/FAMILY/PUBLIC),与 live DB 已对齐。
- 角色 OWNER/MEMBER/CHILD/GUEST + OPS(运维不属家庭,绑定须含 `family_id=NULL` 系统级行);同一用户在不同家庭可不同角色。
- **新增 `@RequirePermission` 接口前**:auth_code 必须进 `sys_auth` + `sys_role_auth` 种子(OWNER 豁免、MEMBER 显式授权);注解**只写方法上**,类级不生效(踩坑速查 §2.14)。
- **索引规范(强制)**:列表查询的 WHERE+ORDER BY 必须落在同一复合索引内,等值字段在前、范围/排序在后,`deleted` 进索引;混向排序给索引列加 `DESC`;新增表/接口先 `EXPLAIN`(踩坑速查 §8.4)。
- **引用开源依赖必须登记** `sys_oss_component`(NPM/MAVEN 交 Renovate;SERVICE 走内部升级方案);`current_version` 记锁定版本,不写区间下限。细则见 需求 §4.6.10。
- **记账账户余额不落库**:`balance = 期初 + 收入 − 支出 − 转出 + 转入`,前后端同一口径(前端 `utils/bookLedger.js` 可复算对账);`family_book_record` 三个账户列可空 = 历史未归属,只提示不迁移;总账 `byMonth` 只含有记录的月份、前端补 12 槽(踩坑速查 §8.5,功能口径见 需求 §4.5.5)。**预算同理只存额度**(`family_book_budget`,一个分类一条),当月已花/超支实时汇总;净资产 = 账户资金 + 理财持仓本金 − 贷款剩余本金(贷款走 `utils/loan.js` 唯一计算源)。

## 代码结构

```
backend/ (Spring Boot 4, JDK 21, com.ihomy)
  src/main/java/com/ihomy/  common/ config/ security/ annotation/ aspect/ filter/
                            entity/ mapper/ service/ controller/ dto/ websocket/
  src/main/resources/       application.yml(生产基线) external.yml.template
                            mapper/*.xml  schema.sql  migrations.sql  logback-spring.xml
frontend/src/               api/(index.js 分组,request.js 拦截) stores/ router/ i18n/ theme/
                            utils/(纯函数与组合逻辑) composables/ components/ layouts/
                            styles/main.css(全局样式与 EP 覆写) views/(页面) tools/(registry.js)
wallpaper-engine/           Wallpaper Engine 网页壁纸壳页(机制见该目录 README)
```

**具体文件路径查 `docs/代码地图.md`**(Controller 路径前缀 / 表↔实体↔XML / 路由↔视图 / api 分组,均由代码生成)。

## 构建与验证

```powershell
cd backend;  .\mvnw.cmd -B clean package      # 编译+测试+打 jar → target/ihomy-backend.jar
cd backend;  .\mvnw.cmd -B clean package -DskipTests   # 只求编译
cd frontend; npm run dev                      # 5173,代理 /api → 8080
cd frontend; npm run lint; npx vitest run; npm run build
```
- 本机没装 mvn 时:`C:\Users\chill\AppData\Local\Temp\opencode\apache-maven-3.9.9\bin\mvn.cmd`;`JAVA_HOME=C:\Program Files\Java\jdk-21`。
- 后端 clean 被运行中后端锁 jar 时改跑 `-B test`。
- **冒烟**:`GET /api/auth/captcha` 取 id → `POST /api/auth/login {email,password,captchaId,captchaCode:'qwer'}`(开发验证码固定 `qwer`),code=0 即有 token。
- **健康检查**:`GET /api/public/health`(免登录,UP→200 / DEGRADED→503,`deploy.ps1` 用它)、`GET /api/ops/health`(OPS,含依赖耗时)。
- **闸门(CI 最先跑)**:`bash scripts/doc-drift-check.sh` + `bash scripts/gen-code-map.sh --check` + `bash scripts/doc-index.sh --check`;随后前后端构建、起库导 schema、登录冒烟、Jellyfin 接口检查。
- 文档生成物 / 工具导出:`npm run tools:export`(+`:check`);Word 交付物用 `scripts/docs-gen/pipeline.sh`,勿手改 docx。
- 放映厅联调(Jellyfin 可选):`docker compose --profile jellyfin up -d jellyfin` → `bash scripts/dev-jellyfin-seed.sh` → `bash scripts/ci-jellyfin-setup.sh`;接口检查 `python test/automation/media_engine_check.py`(39 项)。细节见 踩坑速查 §5。

## 统一响应与鉴权

- 响应 `{code:0,message,data}`;code≠0 即失败。异常走 `BizException` + `GlobalExceptionHandler`。
- 双 token:access 2h + refresh 7d;**普通 refresh 每次使用即轮换拉黑**(60s 宽限返回同一份新令牌防多标签互踢),登出吊销两者;壁纸走独立 `type=WALLPAPER` 令牌(不轮换)。**改回「一律轮换」前必读 踩坑速查 §6**。
- **`authenticationEntryPoint` 必须返回真实 HTTP 401**(JSON 体照写);前端 401 统一走 `redirectToLogin()`(踩坑速查 §2.4)。
- **令牌过期预检(强制)**:`api/request.js` 发请求前用 `utils/jwt.js` 的 `isTokenExpired` 判断,过期且 refresh 有效就先续期——只靠 401 会让 permitAll 接口永远拿不到登录态(`/auth/*` 跳过预检防递归,踩坑速查 §3.20)。
- 权限:`buildTokens` 返回 `permissions` + `isOps`;前端 `userStore.hasPerm(code)` / `isOps` / `isPureOps`。OWNER 恒真。
- **OPS 隔离**:`OpsAccessFilter` 只放行 OPS 到 `/api/ops/**` + `/api/auth/**`;**判 OPS 只认系统级绑定**(`family_id IS NULL`,5 分钟缓存),前端一律 `userStore.isOps`,**禁止 `hasPerm('ops:view')`**(踩坑速查 §2.5)。
- 点赞/评论/通知严格同家庭(`validateTarget`),跨家庭返回 NOT_FOUND;匿名接口按登录态返回空数据,不许 NPE。
- **未登录看到的是演示家庭(1 号家庭,`is_default=1` + `is_public=1`)**;游客只取 PUBLIC;演示内容必须保持饱满;纪念日写接口要 `invalidateHomeCache`(踩坑速查 §7.9)。
- 防爆破:`/auth/login|captcha|register` 按 IP 限流 + 失败计数(超限 429);种子账号 `must_change_password=1` 时令牌带 `pwdChange` claim,只放行改密与登出、不签发 refresh;CORS 白名单 `app.cors-allowed-origins`;SQL 日志默认 `warn`。
- 接口前缀 `/api`;SpringDoc `http://localhost:8080/api/doc.html`。

## 跨模块规则

- **首页/导航**:分组顺序固定 `content → life → family → social → system`(内容/生活/家庭/成员/系统),`family` 组 = 物品定位/家庭公告/智能家居/工具箱;`sys_home_module.sort_order` 决定组内顺序,改完重启后端;**移动端导航唯一数据源 `utils/navModules.js`**——不入模块表的页面(设置/帮助/运维/天气)须在 `buildNavItems` 补虚拟入口;新增 category 必须同时登记 `NAV_GROUP_ORDER`,否则该组模块在导航里整组消失。移动首页只做动态流。**家谱已并入「家庭成员」页**(`?tab=tree`,旧 `/tree` 重定向),`tree` 模块种子置 `enabled=0` 防止重复入口。**提醒/纪念日已并入「家庭计划」页**(`/plan?tab=today|plan|anniversary`,旧 `/reminder`、`/anniversary` 重定向),两模块种子置 `enabled=0`;今日页签 = 今日计划 + 今日提醒,首页/暖居「今日」卡与它同源(`/plan/today` + `utils/agenda.js`)。
- **工具箱**:元数据唯一来源 `frontend/src/tools/registry.js`;12 个开发者工具(JSON/编码/进制/报文/子网/哈希/随机/时间/正则/文本/运维/JWT)只在 `/bit-pouch/tools/<id>` 维护界面,ihomy 旧 `/tools/<id>` 重定向,registry 标 `toolbox:false`;颜色/单位/图片/文件/PDF 两处入口共用内核。比特口袋在线目录(`tools/bitPouchTools.js`)共 24 项,与 bit_pouch `src/plugins/` 一一对应,另有 URL/UUID/数据格式/HTTP 报文/cURL/IP 段/MQTT 主题 7 项只在线页维护。
- **天文**:后端 `SolarUtil` 是唯一计算来源(随 `/public/sun-info` 下发),前端 `utils/terminator.js` 只做球面几何渲染,**禁止再移植 NOAA/Meeus**;`/public/sun-info` 只缓存 slots,直射点每次实时算。
- **独立产品页**:挂 `/kada`、`/bit-pouch`、`/wallpaper`、`/chillyice` 这类页必须加 `meta.standalone`(不套外壳、跳过 `appStore.init()`、401 不顶登录页、自带配色要接管 `html` 根类);**从独立页回站内页要补初始化**(`App.vue` 监听 standalone 变 false 时补 `appStore.init()`/`ensureUserInfo()`),否则首页与家庭名全空(踩坑速查 §3.32/§3.8)。
- **产品访问/下载统计**:咔哒与比特口袋的访问/下载落 `report_product_stat` 日表(落地页打点 + 安装包下载链接统一走 `GET /public/product/download` 计数后 302 到 `/files`),**禁止按 access 日志统计下载**——nginx `/files/` 静态直出,下载请求不进后端日志(踩坑速查 §7.11)。
- **计划提醒单向托管**:计划的「提醒时间」只存 `family_reminder`(由 `plan_id` 关联),`family_plan` 不加时间列;写操作全走 `FamilyPlanService` 单事务,计划→提醒单向同步(计划完成连带完成提醒,提醒勾完成不改计划);重复判定与「今日」聚合共用 `RepeatSchedule.dueToday`,前端不算。

## 后端规范

1. **三层**:Controller 只做校验+调 Service+返回 `Result`;Service 单实现无接口;Mapper 只有 `BaseMapper`,自定义 SQL 全放 `resources/mapper/*.xml`(接口不写 `@Select/@Update`,参数统一 `@Param`)。表单入参按列宽/精度声明 `@Size`/`@Digits` + `@Valid`。
2. **权限**:`@RequirePermission("code")` + `RequirePermissionAspect`;OWNER 恒真。
3. **操作日志**:写接口必加 `@OperationLog`(module 大写 / operationType 标准词 / description 中文;token 刷新、已读等高频端点除外);traceId 由 `TraceIdFilter` 写入 MDC + 响应头 `X-Trace-Id`。
4. **SQL 日志**:`mybatis-plus.log-impl=SqlStatementLog`,`logging.level.mybatis.sql` 默认 `warn`(排查时调 `debug`);**禁止 `System.out.println`**。
5. **软删**:`@TableLogic deleted`;**物理删/读已删数据必须走自定义 XML**(MP `deleteById` 是 UPDATE,常规查询自动过滤);回收站列表/恢复/超期清理全用 XML。
6. **家庭隔离**:业务数据带 `family_id`;JWT familyId 是快照,refresh 时解析;跨家庭一律 NOT_FOUND。`family_id=NULL` 表示系统级,别用占位值。
7. **N+1 禁令(强制)**:列表接口禁止 for 循环 `selectById` 取 authorName/uploaderName 等;先收集 id → `selectBatchIds` → 内存 Map 回填(参考 `ActivityFeedService.getFeed` / `CommentService.list`)。
8. **缓存**:键 `ihomy:{domain}:{id}`;用户/权限/公开首页 TTL 5min;**变更点必须显式 invalidate**(矩阵 踩坑速查 §9);不变数据用内存缓存(预热+双检锁,按 familyId 分桶,**不引 Caffeine**);敏感数据不缓存。
9. **UPDATE 不先 select(强制)**:回写冗余字段用 `LambdaUpdateWrapper.eq(...).set(...).update(null)`;**计数用 `setSql("x = x + delta")` 原子增量,禁止「先 COUNT 再 SET」**(踩坑速查 §8.3);依赖 `updated_at` 的更新只 SET 业务字段并重查(§2.2);清空字段必须 `.set(col, null)`(§2.3)。
10. **文件上传流式(强制)**:禁 `file.getBytes()` 全量入堆(生产 `-Xmx384m`),用 `MultipartFile` 重载 + `transferTo`(+`Files.copy` 兜底),Controller 传 `MultipartFile`。
11. **JVM/池**:虚拟线程开启;HikariCP `maximum-pool-size:20` / `minimum-idle:5` / `connection-timeout:3000`。
12. **日志(强制,细则见 `docs/设计想法/日志/`)**:access/server/thirdparty 三类文件,按天滚动留 7 天;六要素含 `[tid]`,tid 贯穿 HTTP/WS/@Async;三方调用一律走 `ThirdPartyHttp`;**报错必须带堆栈** `log.error("xx, p={}", p, e)`(禁 printStackTrace);级别 ERROR=人工/WARN=可恢复/INFO=关键/DEBUG=细节;新敏感字段进 `AccessLogFilter.SENSITIVE_JSON`;**同类异常聚合预警** `LogAlertAppender` 须挂 root + ACCESS + thirdparty(后两者 `additivity=false`),配置 `app.log-alert.*`,详见 需求 §4.6.4。

## 前端规范

1. **API 分组**:`api/index.js` 按模块导出 `xxxApi`,统一走 `api/request.js`(axios + JWT + 自动续期)。
2. **状态**:Pinia —— `stores/user.js`(登录/权限)、`stores/app.js`(首页聚合)、`stores/theme.js`(主题两轴)。
3. **路由**:`meta.public` 免登录;`meta.ops` 需 OPS;纯 OPS 只能进 `/ops`;页面全部懒加载 `() => import()`。
4. **样式**:CSS 变量(`main.css`)+ `html.dark` 覆写;不显式声明 serif;需被类覆写的内联样式走 CSS 变量(踩坑速查 §3.4)。
5. **图标**:Element Plus `el-icon`;Setting/Monitor 用内联 SVG(复杂 path 在 100% 缩放下光栅化卡)。`stroke-width:2px`。
6. **动画**:GSAP 入场;`transform: translateZ(0)` 隔离合成层;`contain: layout style`;避免 `background-attachment: fixed`。
7. **毛玻璃**:`backdrop-filter: blur(24px) saturate(1.1)`;hover 用 `transform` 不用 `box-shadow`;**长开页/持续动画页别用**(踩坑速查 §3.10)。
8. **可拖拽面板**:`useDragResize`;位置尺寸持久化 localStorage;**事件监听按需挂载**(拖拽开始挂 `mousemove`/`mouseup`,结束移除,别 `onMounted` 常驻)。**指针捕获只在真正拖动时做**:`setPointerCapture` 会把 pointerup 与 click 一起改派给捕获元素,`pointerdown` 就捕获会让容器内所有卡片/按钮点不动(踩坑速查 §3.35)。
9. **光影层全局化**:`SunLightLayer` + `AppSidebar` + `SiteFooter` 在 `App.vue` 挂载,`useSunLight` provide/inject。
10. **i18n**:所有可见文本 `$t('key')`,中英键结构对齐;**文案里字面量 `{`/`}`/`@` 必须转义**(`{'{名字}'}`),否则生产构建 `$t()` 抛错打断整页(dev 只打日志);守门 `i18n/message-format.spec.js` + 硬编码中文守门 `hardcoded-cjk.spec.js`(有意保留项进其 `ALLOW`)。
11. **共享逻辑抽 utils(禁三份实现)**:动态流类型/摘要/相对时间走 `utils/feed.js`;默认楼层 `floorPlanGeom.pickDefaultFloor`;枚举文案 `dictText(t,...)`;金额「元⇄分」走 `utils/money.js`、年月日换算走 `utils/date.js`(贷款/理财/记账本共用,`loan.js` 只再导出);新重复逻辑先找现成 util。
12. **可点击非按钮元素**:用 `v-a11y-click`(补 `role`/`tabindex` + Enter/Space),别手写三行;`<img>` 必须带 `alt`。
13. **打包分块(强制)**:`vite.config.js` 必配 `manualChunks`(element-plus/gsap/vue-i18n/epubjs/pdfjs/simple-mind-map);`public/` 静态资源不得与 npm 包重复。
14. **重型资源异步(强制)**:阻塞首屏的字体包/CSS 用 `import('...')`,别同步 import。
15. **动画优先级(强制)**:持续型动画 CSS `@keyframes` > GSAP 直接操作 DOM ref > rAF + 响应式 ref;**禁止 rAF 每帧写 Vue ref**。
16. **并行请求(强制)**:独立请求改 `Promise.all([...])`,串行只在真有依赖时。
17. **computed 纯函数(强制)**:内部禁止 `Math.random()`/`Date.now()`/副作用;随机或一次性计算用 `ref` + `watch(source, immediate)`。
18. **全局 UI 样式统一(强制)**:EP 组件(dialog/message/popper/button/tag/badge/input)配色/圆角/尺寸/z-index 一律由 `main.css` 覆写,**禁止 scoped 重复定义**;**层级只用 `main.css` 的 `--z-*` 标尺**(内容10/外壳60·70/光影65–100/浮起操作120/自建沉浸150/遮罩200/确认框210/popper240/toast260),组件内禁写 ≥100 的裸 z-index(守门 `styles/zLayers.spec.js`),遮罩统一 `--overlay-scrim`+`--overlay-blur`(细则 UI 提示词 §29);命令式 API 样式已在 `main.js` 引入(新增调用先确认,否则裸 DOM 不可见);**弹窗不依赖全局 `width:fit-content`**,长文弹窗按 UI 提示词 §18a 限宽收高,必要时 `append-to-body`(踩坑速查 §3.15/§3.25/§3.36)。
19. **按钮/标签/角标/圆角**:主/次/幽灵/危险四类(浅深色不共用色值)、el-tag 半透明磨砂、el-badge 半透明黑、圆角 button 12 / input 10 / card+dialog 14;完整值见 UI 提示词 §18a。
20. **页面规范(强制)**:根容器 `class="page"`(禁 scoped 覆写 max-width/margin/padding);页面级 H1/H2 移除,分区标题 `.section-label`;工具栏 `class="page-toolbar card"`(`.tb-left` size=small、`.tb-right` gap 8px);多选 `.pick-badge` 圆标+描边(**禁左上 checkbox**)。详见 UI 提示词 §11b。
21. **位图资产(强制)**:装饰图放 `frontend/src/assets/` 并 ESM 导入(内容哈希,nginx 长缓存),**不放 `public/`**;宽度按渲染 2 倍封顶,噪点图先 3×3 中值滤波,输出渐进 JPEG。例外:URL 存 DB 的演示素材走 `frontend/public/`;咔哒发布页整屏截图按固定路径放 `frontend/public/kada-media/`(重截覆盖同名文件);像素字体子集化 woff2 放 `src/assets/fonts/`。chillyice 舞台位图由 `scripts/art/chillyice_paint.py` 生成,换 AI 出图只覆盖同名 webp。**`public/` 目录名不得与 SPA 路由同名**(`public/tools/` → `dist/tools/` 让 `/tools` 403;导出放 `public/toolbox-data/`)。
22. **提示文案不写实现(强制,后端同守)**:用户可见文案、`BizException` 消息、`@Operation` 摘要(`/doc.html` 公开)**只讲用户能做什么/发生了什么**,不写库表字段/配置键/内部枚举码/内部节律。机制细节归注释与日志。
23. **富文本必须消毒(强制)**:用户可写内容渲染 HTML 一律经 `utils/markdown.js` 的 `renderMarkdown()`(marked + DOMPurify)再 `v-html`;**禁止把 `marked.parse` 直接给 `v-html`**,`marked` 只允许在该文件导入;守门 `utils/htmlSink.spec.js`。

## 性能(强制)

- **列表查询必须走索引**:WHERE + ORDER BY 落在同一复合索引(细则见「数据库约定」);新增表/接口先 `EXPLAIN`。
- **`ORDER BY RAND()` 慎用**:小数据集(≤1000 行)可接受并加 `ponytail:` 注释;大数据集改 id 范围随机。
- 已踩坑清单(100% 缩放卡顿 / backdrop-filter / rAF 写 ref / 常驻监听 / 同步 import / 入口 chunk / getBytes OOM / N+1)与「不建议改」见 踩坑速查 §8。

## 验证基线

- 后端全绿:`.\mvnw.cmd -B clean package`(140 tests,纯逻辑不起 Spring)。
- 前端:`npm run lint`(0 error 0 warning)+ `npx vitest run`(559 passed)+ `npm run format:check`(全树遗留未格式化:`views/BitPouch.vue`、`utils/packetDecode.js`+`.spec.js`、`views/bitpouch/panels/PacketPanel.vue`、`views/Chillyice.vue`、`views/ops/tabs/OpsKada.vue`;**本次改动的文件必须 `prettier --write` 后通过**)。
- 构建:入口 chunk 基线 **511.97KB / gzip 211.42KB**(V10.87);**vite 报的是字符数不是字节数**,别拿 `ls -la` 比;历史数字见 变更归档。
- 一致性:`bash scripts/doc-drift-check.sh` 全 [OK];`gen-code-map.sh --check`、`doc-index.sh --check` 无漂移。
- 接口测试在仓库外 `..\autotest_framework`(`pytest -m api`,37 passed)。

## 文件存储

- 本地磁盘:根 `file.upload-dir`(生产 `/opt/ihomy/uploads`),DB 存 `/files/...` URL,Nginx 托管。**不要主动改 FileService 存储实现**(接 NAS 优先 NFS 挂载,代码零改动)。
- 目录:相册 `pictures/{相册名}/{相册ID}_{时间戳}_{文件名}`、视频海报 `videos/`、音乐 `music/`、电子书 `books/{yyyyMM}/`、通用 `files/{yyyyMM}/`;FileService 提供流式重载。
- 存储设备 `sys_storage_device`(家庭级隔离,SYSTEM/NAS/REMOTE/MOUNT/BAIDU/NEXTCLOUD/WEBDAV;OSS/S3 暂缓),写操作需 `storage:manage`(OWNER)。
- **删除=进回收站**(照片/相册/视频/图书置 `deleted=1`+`deleted_at`,文件保留);`/recycle` 可恢复或彻底删(物理删 DB + 磁盘,自定义 XML 绕过 logic-delete);每日 03:00 清理超 7 天项。博客封面/头像/家庭封面/音乐/家谱照片删除不连带文件(孤儿文件可接受)。
- **`/files/**` 鉴权**:登录态放行;游客按 URL 反查只放 `visibility='PUBLIC'`(清单 `mapper/FileAccessMapper.xml`,**新增带文件 URL 的表必须补一行**,保险箱不放);`/files/kada/**` 免登录;`/files/*` 带 nosniff+CSP;**nginx 必须反代,不得 alias 直出**(踩坑速查 §7.8)。

## 配置与加密

- **外挂配置是唯一开发/生产差异**:`IHOMY_CONFIG_PATH` 指向 yml,`ExternalConfigLoader` 启动早期加载并覆盖 `application.yml`;**改配置前先核实该变量实际指向**。
- DB/Redis 密码明文(避免鸡生蛋);业务凭证 AES-GCM(`AesUtil`,PBKDF2 100000 次 + GCM 128bit),密文 `ENC(Base64)`,盐值存 `sys_parameter(aes-salt)`。
- OPS 加解密接口:`GET /api/ops/crypto/encrypt|decrypt`(`ops:view`)。
- **可扩展点**:①天气 = `WeatherService` 门面 + `WeatherProvider`(新增=常量+实现+前端下拉);②首页组件 = 四档 snap + hover 推开,布局键 `ihomy:dashboard:layout:v2`;③AI = 家庭模型池 `sys_family_ai_model` + 按功能绑定 `sys_family_ai_feature`(`resolveForFeature/resolveChain` 主+兜底)。
- 不用 profile;`application.yml` 即生产基线。

## 部署约定(Linux 2GB 求稳)

- MySQL 本机(~180MB)+ Redis 用 Docker。后端以 `ihomy` 用户跑 systemd,MySQL 用 `mysql`,Nginx 用 `www-data`;除装包/建用户/systemd/`/etc/`/防火墙/certbot/导 schema 外不用 root。
- JVM:`-Xmx384m -XX:MaxMetaspaceSize=192m -XX:+UseSerialGC -Xss512k`;MySQL `performance_schema=OFF`,端口 6306。
- **Redis 只绑回环 + 强密码**:`-p 127.0.0.1:6379:6379` + `--requirepass`(写在**镜像名之后**),密码同值进 external.yml;**改密顺序**:external.yml → 停后端 → 重建容器 → 起后端 → 冒烟。
- SSH 统一 **19068**(禁 22),所有 ssh/scp 带 `-p 19068`;配 fail2ban 与自动安全更新。
- 备份:`scripts/prod-backup.sh`(mysqldump 单事务 + gzip 校验 + uploads 只增不删,留 14 天)由 systemd timer 每日 03:00 跑;恢复 `scripts/prod-restore.sh <dump> <目标库>`(必须显式给库,恢复前自动快照)。
- **重装/新服务器四条必查**:① nginx `mime.types` 有 `.mjs`;② 站点 conf 有 gzip 四行;③ 静态缓存 location 负向断言排除 `/api/`、`/files/`(否则接口文档 404);④ 媒体引擎播放地址与站点同为 HTTPS(接口断言查不出)。细节 踩坑速查 §7.1/§7.3/§7.10。
- **部署新前端后老客户端仍跑旧 bundle(PWA 预缓存)**:冒烟前先 unregister SW + 清 caches;用户侧 Ctrl+Shift+R(踩坑速查 §7.2)。
- 详细步骤 `docs/部署指导-Linux.md` / `docs/部署指导-Windows.md`。

## 归档与规划

- 每次可交付迭代:更新 `VERSION` → 追加 `docs/变更归档.md` `#####` 小节 → 就地改写 需求 §4/§6/§7/§9 → 跑 `gen-code-map.sh` + `doc-index.sh` → `doc-drift-check.sh` 全绿 → commit。流程细节走 `/archive` 技能。
- **未实现规划的唯一去处是 需求 §9**(§9.1 产品级 / §9.2-§9.5 代码级待办);本章不记内容,启动任何规划项前先读对应小节。开工新功能前先 `grep schema.sql + router/` 对照模块种子。

## 文档与服务

- 索引:`docs/README.md`(每份文档一句话 + 阅读顺序);`docs/架构设计.md`(请求怎么走/关键机制);`docs/代码地图.md`(代码定位);`docs/归档索引.md`(历史反查);`test/cases/功能测试用例.md`(389 条)。
- `scripts/`:setup / start-all / restart-all / start-db / doc-drift-check / gen-code-map / doc-index / prod-backup / prod-restore / db_diff / ci-jellyfin-setup / docs-gen / soft-copyright(软著 PDF 生成)。
- 本机环境:JDK 21、Node 20、MySQL 8、python-docx;Maven 用 Wrapper(或 temp 3.9.9);Redis 未装时用 Docker/Memurai。
- **代码事实以 `backend/src/main/java` + `resources/schema.sql` 为准**;文档与代码冲突时先改文档再改代码,或先读 `docs/踩坑速查.md` 确认不是已知取舍。
