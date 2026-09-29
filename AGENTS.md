# AGENTS.md — ihomy 项目规则

> 本文件供 ZCode(及 opencode 等)跨会话加载,记录项目**关键规则、约定与导航索引**。新会话启动自动读取,无需重复说明背景;修改后立即对所有新会话生效。
> **分工(务必遵守)**:本文件只留「结论级规定 + 索引」,目标 **≤45KB**(硬上限 60KB——超出会被截断且尾部最先丢失,所以宁缺勿滥)。四类内容各有归属,别再往本文件堆细节:

| 内容类型 | 去处 |
|---------|------|
| 功能需求现状(模块能力/接口/表结构/规划事项) | `docs/需求设计说明书.md`(唯一活文档;§4 功能、§6 数据库、§7 接口、§9 规划) |
| 实现记录(文件级改动表/设计决策/冒烟结论/live DB 同步 SQL) | `docs/变更归档.md` **末尾追加** `#####` 子节 |
| 踩坑成因、机制解释、维护清单、缓存失效矩阵、已知问题 | `docs/踩坑速查.md` |
| 前端 UI/交互/视觉规范细则 | `docs/UI设计提示词.md` |

> **⚠ Git 规定(必须遵守)**:非人工指令,不得主动提交代码(`git commit`/`git add -A`/`git push` 一律禁止)。`git add` 只能指定具体文件路径,禁止 `git add -A`/`git add .`。

> **⚠ 敏感数据规定(必须遵守)**:**生产**密码/密钥/私钥/token 一律不写入仓库文件——生产 DB 密码与 JWT 密钥走服务器 external.yml(不入 git,模板 `external.yml.template`);凭证台账在本地 `Linux部署指导.md` §〇(两平台共用)、开发账号明文在本地 `docs/新人上手指南.md`(均 .gitignore 忽略,不入 git);前端演示凭证走 `frontend/.env.development.local`(仅 vite dev 加载,生产构建不读取;**`.env.local` 所有模式都加载会内联进生产 bundle,禁用**)。schema.sql 为开发安全版已入库:仅含本机 Docker 开发固定凭证,生产凭证完全独立,部署时须 `ALTER USER` 改强密码。历史明文凭证清理+轮换见 docs/变更归档.md 敏感数据治理小节。

> **⚠ 多会话并行警示(必须遵守)**:**多会话同时改本仓时禁用 `git stash`**(path-limited `git stash push` 把共享文件 i18n/router 里**其他会话未提交的改动**一起回滚,现象是「刚加好的文案与路由突然消失」);**他人 stash 不 pop**,取自己那部分只读不写:`git show "stash@{0}:<path>"` 按块插回。也别并行 `npm run build`(争 `public/emulatorjs`、`dist/`)。

> **⚠ 工具链硬性规定(必须遵守;症状/真因/取证见 `docs/踩坑速查.md` §1)**:
> - 工作目录:`C:\Users\chill\OneDrive\WorkStation\Projects\ihomy`;每次读写前逐字核对路径,读不到文件先怀疑拼写。
> - **含中文的源码/配置/SQL/文档一律走 Read/Write/Edit 工具**,禁止 PowerShell `Get-Content`/`Set-Content`/`WriteAllText`(PS 5.1 默认 GBK 破坏 UTF-8 中文;`WriteAllText` 带 BOM 让 javac 报非法字符)。PowerShell 只用于 npm/mvn 构建与 HTTP 冒烟。
> - **脚本化改写文档会把 CRLF 换成 LF**:本仓 `core.autocrlf=true`,一律 `open(p,'rb')` 读 + `split(b'\r\n')` + `b'\r\n'.join()` 写;小改用 Edit。复核 `\n` 数减 `\r\n` 数 = 0。
> - **PowerShell 5.1 两坑**:原生命令的点号参数必须带引号(`'-h127.0.0.1'`,不加会被拆成主机 `127`);传给原生命令的参数里嵌双引号会被吃掉(用无引号模板 `{{.Config.Labels}}`)。**含中文的 .ps1 必须带 UTF-8 BOM**(无 BOM 按 GBK 解析直接语法错误;Write 工具默认无 BOM,新建后要补)。
> - **Git Bash 调 Windows 原生命令**:容器内路径参数一律加 `MSYS_NO_PATHCONV=1`(否则 `/tmp/x.sql` 被转成 Windows 路径,`docker cp`/`source` 静默失效报 `error: 2`)。
> - **打包 zip 用 Windows 自带 bsdtar**:`tar -a -c -f out.zip dir`;别用 `Compress-Archive`(条目用反斜杠分隔,非 Windows 解压工具会当成文件名)。
> - **写 MySQL 中文**:Write 写 UTF-8 无 BOM 的 SQL → `MSYS_NO_PATHCONV=1 docker cp` 进容器 → `docker exec ihomy-mysql mysql --default-character-set=utf8mb4 ihomy -e "source /tmp/x.sql"`;禁 PowerShell 管道喂 mysql。终端显示 `?` 是终端编码问题,不代表落库乱码。
> - **`mvnw.cmd` 必须在 `backend` 目录内调用**(wrapper 在当前目录找,在仓库根调会报找不到 maven-wrapper.properties);运行后端用完整路径单实例 `C:\Program Files\Java\jdk-21\bin\java.exe -jar target\ihomy-backend.jar`(javapath + 双实例会分流 8080 导致偶发 401/404/500);jar 锁按 PID 结束本地后端进程(命令行含 JetBrains/IntelliJ 的是 IDEA 本体,绝不能杀;**禁止 `taskkill /IM java.exe` 全杀**)。
> - **后端内存缓存直改 DB 不生效**:`HomeModuleService` 全局模块 `@PostConstruct` 预热进内存(无 TTL 无兜底刷新),直接 UPDATE `sys_home_module` 后必须重启后端才生效(或走模块管理接口 evict);同理适用于其他启动时预热的内存缓存。

## 项目概述

- **应用名**:ihomy(家庭共用软件)。家庭内部内容共享平台,PC 浏览器 / 安卓 / iOS(均 PWA)。
- **核心功能**:登录注册、博客、日记、相册、纪念日、留言板、放映厅、聊天室、积分商城、任务悬赏、提醒、家庭计划、愿望单、记账、家谱、书架、工具箱(脑图设计)、运维。首页模块化可扩展(新增功能只需插入一条 `sys_home_module` 记录)。
- **技术栈**:前端 Vue3 + Vite + ElementPlus + PWA(`ihomy-frontend`);后端 Spring Boot 3 + MyBatis-Plus + MySQL 8 + Redis(JWT 双 token + 验证码 + WebSocket),包 `com.ihomy`,主类 `IhomyApplication`,`ihomy-backend`。

## 工作目录

- 唯一工作目录 `C:\Users\chill\OneDrive\WorkStation\Projects\ihomy`(代码编辑 + 构建验证 + 部署文档均在此;已从双目录合并为单目录)。
- Nginx 静态根指向 `frontend/dist`,构建后直接生效(Ctrl+Shift+R 刷新浏览器)。
- 部署文档:`docs/部署指导-Linux.md` / `docs/部署指导-Windows.md`(入库脱敏版);本地 `Linux部署指导.md` 只剩 §〇凭证台账。

## 命名约定(务必遵守)

应用标识统一为 **ihomy**,以下不可改:

| 项 | 值 |
|----|----|
| Java 包 | `com.ihomy`(目录 `com/ihomy/`) |
| 主类 | `IhomyApplication`(文件 `IhomyApplication.java`) |
| Maven artifact / jar 名 | `ihomy-backend` |
| npm 包名 | `ihomy-frontend` |
| 数据库名 | `ihomy` |
| 应用连接账号 | `ihomy`(密码经 external.yml 注入,不入仓库) |
| Docker 容器名 | `ihomy-mysql` / `ihomy-redis` / `ihomy-backend` / `ihomy-nginx` |
| Windows 服务名 | `IhomyBackend` / `IhomyNginx` |
| 显示名 / PWA name / 页面标题 | `ihomy` |
| JWT 密钥前缀 | `ihomy-secret-key...` |

**业务概念不改(注意区分)**:`family` 表、`Family` 实体类、`family_id` 字段、`OWNER/MEMBER` 角色名 —— 这些是"家庭"业务领域概念,不是应用标识。

## 版本号规则(自 V10.0 起)

- **形态**:`V<主>.<次>`(如 `V10.0`,次位不补零)。**根目录 `VERSION` 文件是唯一事实来源**(一行纯文本、含 `V` 前缀):前端构建经 `define __APP_VERSION__` 注入,页脚与运维页「服务器状态」展示;**改版本只改这一处**。
- **进位**:每次可交付迭代次位 +1;**次位满 100 强制进位主版本**(`.99` → 主 +1、次归 0),或出现结构性里程碑(新增功能域/不兼容变更)时提前进位;禁止再出现 `V9.110` 这类三位次位。**`V1.0–V9.110` 历史编号与旧写法(`V5.6续`)冻结不改**,以免打断 `grep V9.x` 检索。
- **收尾**:同一版本登记 `docs/需求设计说明书.md` 修订记录一行 + `docs/变更归档.md` 末尾 `#####` 小节;收尾时更新 `VERSION` 并在该版本收尾提交上打 annotated tag `V<主>.<次>`(消息 = 日期 + 一句话摘要),随发布 `git push origin <tag>`。
- **不对齐代码制品**:`frontend/package.json` / `backend/pom.xml` 的版本字段**不参与**本规则(仍为 `1.0.0`/`1.0-SNAPSHOT`),避免与构建产物命名耦合。

## 数据库约定

- **root 仅用于初始化**:`mysql -uroot -p < backend/src/main/resources/schema.sql`(建库/建表/建账号/初始数据),执行一次;本地开发 `.\scripts\start-db.ps1`(Docker 首启自动导入)。schema.sql 为开发安全版(仅本机 Docker 凭证,生产须改独立强密码)。
- **业务运行用 `ihomy` 账号**:仅授予 `SELECT/INSERT/UPDATE/DELETE` on `ihomy.*`(最小权限,无 CREATE/ALTER/DROP)。application.yml 连接用 `ihomy`,**不要用 root 跑业务**。账号同时创建 `localhost` 与 `%` 两个 host。
- **生产 MySQL 密码策略**(轮换踩坑):启用 `validate_password` MEDIUM,密码必须含特殊字符(避开 `' " \ $ |`,建议 `!@%^&*-_+=.`),否则 `ALTER USER` 报 1819;开发 Docker MySQL 无此组件,同一密码 dev 能过 prod 被拒。详见踩坑速查 §2.7。
- **75 张表**,前缀分类:`sys_` 22 张(系统/账号/权限/配置/存储,含放映厅 `sys_media_server` + 成员播放档案 `sys_media_user_config`)、`report_` 3 张(报表/日志:report_ai / report_weather / report_system)、`family_` 28 张(家庭事务,含保险箱 `family_vault_item`、贷款 `family_loan`/`family_loan_event`)、`content_` 21 张(内容数据)、另 `game_info` 1 张(家庭小游戏,命名未加 family_ 前缀——遗留)。**完整表清单见 `docs/需求设计说明书.md` §6.2**。
  - **命名规则**:家庭事务业务表一律 `family_` 前缀;内容数据 `content_` 前缀;账号/权限/配置/存储保留 `sys_`;**报表/日志表一律 `report_` 前缀**。新增表必须遵守。前缀取最顶层祖先类别;上下级关系体现在表名(如 `sys_user_role`)。
- **结构变更双文件**(强制):同时更新 `schema.sql`(全量)与 `migrations.sql`(增量、**幂等**),并按 CI 同路径做空库全量导入实测——`migrations` 跑通 ≠ `schema.sql` 可用。详见踩坑速查 §7.4。
- **引用开源软件必须对接自动升级(强制)**:新增任何 npm/Maven 直接依赖或独立开源服务时,**必须同时在 `sys_oss_component` 台账登记一条**(component_type=NPM/MAVEN/SERVICE + package_ref + current_version + license + repo_url + managed_by),否则不会被版本跟踪覆盖。**升级闸门**:NPM/MAVEN 交 Renovate(`managed_by=RENOVATE`;`renovate.json` 的 `dependencyDashboardApproval` 只列 Dashboard 不开 PR),台账「AI 评估」(`OSS_UPGRADE_EVAL` 功能码)判断影响→勾选「生成升级 PR」触发开 PR;SERVICE 走 `managed_by=INTERNAL` 按 `deploy_type` 出方案,**其当前版本可按 `probe_type` 自动探测**(Nextcloud/Jellyfin 的探测地址自动取已接入的存储设备/放映厅配置,HA 需填地址 + 令牌;探不到保留人工值)。漏洞列 `vuln_count/vuln_severity` 预留(CVE 扫描不做,2026-09-29)。入口 `/ops/oss`(OPS),实现 `common/OssVersionUtil` + `service/OssComponentService`。
- **枚举不再用数字**:状态/类型字段一律大写英文单词(`PUBLISHED/DRAFT/PUBLIC/FAMILY/ACTIVE...`),含义存字典表 `sys_dict_item`,Java 常量集中于 `common/DictConst.java`,前端映射 `utils/dict.js`。**不要写回 0/1/2 判断**。
- **注意**:`content_blog/diary/photo/video/wish` 5 张内容表 `visibility` 列为 `VARCHAR(20) DEFAULT 'FAMILY'`(PRIVATE仅自己/FAMILY家庭可见/PUBLIC公开),schema.sql 与 live DB 已对齐(曾误写 TINYINT)。
- 权力 4 角色:OWNER/MEMBER/CHILD/GUEST + OPS(运维,不属任何家庭,绑定须含 `family_id=NULL` 的系统级行)。同一用户不同家庭可不同角色(`sys_user_role.family_id` 区别)。
- **新增带 `@RequirePermission` 接口前**:确保 auth_code 进 `sys_auth` + `sys_role_auth` 种子(OWNER 豁免,MEMBER 显式授权),否则 403。
- **索引规范**(强制):列表查询的 WHERE + ORDER BY 字段必须落在同一复合索引内。复合索引顺序:等值字段在前,范围/排序字段在后;`deleted` 进索引(逻辑删除几乎每查必带);**混向排序**(如 `status` 升序配 `created_at` 降序)纯升序复合索引仍 filesort,索引列须带 `DESC`(MySQL 8.0.13+,见踩坑速查 §8.4)。已建关键复合索引:`content_blog.idx_family_status_created(family_id,status,deleted,created_at)`、`content_diary.idx_family_created(family_id,deleted,created_at)`、`content_photo.idx_family_created(family_id,deleted,created_at)`、`family_notification.idx_receiver_read(receiver_id,is_read)`。新增表/接口前先 `EXPLAIN` 验证走索引。

## 代码结构

```
backend/ (Spring Boot 3, JDK 21, 包 com.ihomy)
  src/main/java/com/ihomy/
    IhomyApplication.java   # 主类 @MapperScan("com.ihomy.mapper")
    common/      # Result/ResultCode/BizException/GlobalExceptionHandler/DictConst/SolarUtil/AesUtil/UserNames/Loggers/Ips/ThirdPartyHttp
    config/      # SecurityConfig/CorsConfig/MybatisPlusConfig/Knife4jConfig/WebMvcConfig/WebSocketConfig/SqlStatementLog/ExternalConfigLoader/AsyncConfig
    security/    # JwtUtils/JwtAuthenticationFilter/LoginUser/SecurityHelper/OpsAccessFilter
    annotation/ aspect/ filter/  # @RequirePermission/@OperationLog;两个 Aspect;TraceIdFilter/AccessLogFilter/CaptureRequest{Request,Response}Wrapper
    entity/      # 68 个实体类(75 张表里 7 张关联/字典表无实体)
    mapper/      # 68 个 MyBatis-Plus BaseMapper(自定义 SQL 全放 resources/mapper/*.xml,接口不写注解,参数统一 @Param)
    service/     # 61 个服务类(单实现无接口层)
    controller/  # 42 个 Controller
    dto/         # 请求/响应 DTO(45 个)
    websocket/   # ChatWebSocketHandler(原生 WebSocket 聊天室)
  src/main/resources/
    application.yml     # 生产基线:端口8080 context-path=/api;MySQL 6306/Redis 6379;DB密码与JWT密钥留空必须由 external.yml 提供;file.upload-dir /opt/ihomy/uploads;logging.file.path /opt/ihomy/logs
    logback-spring.xml  # 三类日志分流(access/server/thirdparty,六要素 pattern,按天滚动)
    external.yml.template  # 外挂配置模板(IHOMY_CONFIG_PATH 覆盖密码/密钥/路径/captcha/天气,唯一开发生产差异机制)
    mapper/*.xml        # 每个 Mapper 一个同名 XML
    schema.sql          # 建库+建号+建表(75 张)+种子(开发安全版,已入库;与 migrations.sql 必须同步;本地由 start-db.ps1 自动导入)
  mvnw / mvnw.cmd       # Maven Wrapper
frontend/ (Vue3 + Vite + PWA + Element Plus + Pinia)
  src/
    api/          # request.js(axios+JWT+401 自动刷新) + index.js(38 个 Api 对象)
    stores/       # user.js(登录+权限) / app.js(首页聚合) / theme.js(主题两轴矩阵)
    router/       # 登录守卫 + scrollBehavior;56 条路由(53 条懒加载,另 3 条 redirect: /、/plant、兜底)
    i18n/ theme/  # vue-i18n 中英;主题两轴矩阵(暖居/光尘 × 晨/暮)
    utils/        # dict.js / diary.js / doodle.js(涂鸦引擎) / furnitureIcon.js / windowLight.js / useSunLight.js / useDragResize.js / password.js / loan.js(纯函数:密码生成与强度、贷款计算核心)
    composables/  # useDevice.js(设备检测) / useWeatherBg.js(天气 AI 生图氛围底图)
    components/   # AppSidebar/BackToTop/Breadcrumb/AvatarCropper/InstallPrompt/SiteFooter/SunLightLayer/LightTestConsole/SyncDialog/MediaPlayer(媒体引擎播放器)/Mobile*(移动端)/warm/(暖居外壳 WarmLayout+WarmHome)
    layouts/MobileLayout.vue  # 移动端壳
    styles/main.css # CSS 变量 + 全局样式 + 深色模式 + EP 组件覆写 + @media
    views/        # 59 个页面(Home/Login/Member/Settings/Anniversary/album/cinema(放映厅+详情)/diary/blog/points/task/reminder/plan/wish/book/chat/tree/cascade/ops/storage/item/kitchen/library/vault(保险箱)/tools(含贷款计算器)/games(含 PetLinkLink)/plant(花园)/kada(咔哒)/Wallpaper(壁纸屏))
    App.vue
  vite.config.js   # PWA + 代理 /api->8080 + manualChunks 分块 + ElementPlus 按需
wallpaper-engine/   # Wallpaper Engine 网页壁纸包(壳页顶层跳转线上 /wallpaper;theme/mode/lang 走查询参数、token 走 URL hash;不含构建产物、不进 dist)。导入步骤与机制见该目录 README
```

## 构建与验证命令

后端(在 backend 目录):
```powershell
.\mvnw.cmd -B clean package -DskipTests      # 编译+打jar,产物 target/ihomy-backend.jar
.\mvnw.cmd -B clean compile -DskipTests       # 仅编译验证
.\mvnw.cmd spring-boot:run                     # 开发运行(端口8080)
```
- 日志路径:生产 `/opt/ihomy/logs`(三子目录 access/server/thirdparty),开发由 external.yml 指定。
- 临时 Maven(本机未装 mvn):`C:\Users\chill\AppData\Local\Temp\opencode\apache-maven-3.9.9\bin\mvn.cmd`;`JAVA_HOME`:`C:\Program Files\Java\jdk-21`。
- 调用目录、单实例、jar 锁三条硬性规定见本文件顶部「工具链硬性规定」。

前端(在 frontend 目录):
```powershell
npm install        # 首次
npm run dev        # 开发(端口5173,代理/api到8080)
npm run build      # 生产构建,产物 dist/,含 PWA service worker
```

冒烟:登录 `POST /api/auth/login {email, password, captchaId, captchaCode:'qwer'}`(开发环境验证码固定 `qwer`,先 `GET /api/auth/captcha` 取 id),响应 code=0 即有 token。**新人环境初始化** `.\scripts\setup.ps1`(前置检查+生成 config\external.yml+起库+前端依赖,幂等;详见本地 `docs/新人上手指南.md`)。**CI**(`.github/workflows/ci.yml`)每次推送自动做前后端构建+compose 起库导入 schema+后端启动+登录冒烟。数据库重导:整库 `schema.sql`;增量建表/改表执行对应幂等 SQL 段。

**放映厅联调(Jellyfin,可选)**:`docker compose --profile jellyfin up -d jellyfin` → `bash scripts/dev-jellyfin-seed.sh`(容器内 ffmpeg 造测试片源)→ 首次访问 `http://localhost:8096` 走初始化向导 → 设置页-放映厅填地址与账号。接口检查 `IHOMY_TEST_PWD=<密码> python test/automation/media_engine_check.py`(36 项,`--read-only` 只跑读类)。细节与踩坑见踩坑速查 §5,生产上线(引擎放 NAS)见 `docs/部署指导-Linux.md` §12。

## 统一响应与鉴权

- 响应 `{code: 0, message: "success", data: ...}`;code != 0 = 失败。
- 登录:access token(2h)+ refresh(7d,Redis 黑名单登出失效)。普通 refresh **每次使用即轮换拉黑**(轮换宽限 60s:并发/多标签重复提交返回同一份新令牌;JWT 带 `jti` 防同秒签发撞值),登出把 access 与本次会话 refresh 一并吊销;壁纸走独立 `type=WALLPAPER` 令牌(`POST /auth/wallpaper-token` 签发,不轮换不拉黑)——**改回「所有 refresh 一律轮换拉黑」前必读踩坑速查 §6**。请求头 `Authorization: Bearer <token>`,axios 自动续期;仅连续 7 天不访问才需重新登录。**约定(踩坑)**:后端 `authenticationEntryPoint` 必须返回**真实 HTTP 401 状态码**(JSON 体照写),否则前端续期永不执行、用户每 2h 被登出(成因与共享 `refreshPromise` 重放机制见踩坑速查 §2.4)。
- 接口前缀 `/api`;Knife4j 文档 `http://localhost:8080/api/doc.html`。
- **权限模型**:`buildTokens` 返回 `permissions` 数组 + `isOps` 标志;前端 `userStore.hasPerm(code)`/`isOps`/`isPureOps`。OWNER 恒真,其余查 `SysRoleMapper.selectAuthCodesByUserAndFamily`。
- **OPS 隔离**:`OpsAccessFilter` 只放行 OPS 到 `/api/ops/**`+`/api/auth/**`,其余 403;非 OPS 访问 `/api/ops/**` 一律 403;支持复合角色(OWNER+OPS)。**⚠ 判 OPS 只认系统级绑定**(查 `sys_user_role.family_id IS NULL` + 5 分钟缓存):前端一律 `userStore.isOps`,**禁止 `hasPerm('ops:view')`**;OPS 角色的绑定必须带一条 `family_id=NULL` 的系统级行,否则 `isOps` 恒 false、运维页按「家长」渲染。成因见踩坑速查 §2.5。
- 点赞/评论/通知严格同家庭:`validateTarget` 校验内容 family_id 与用户一致,跨家庭返回 NOT_FOUND。
- 匿名(permitAll)接口按登录态返回空数据,不许 NPE(见踩坑速查 §2.6)。
- **防爆破 + 首登改密(V10.1)**:`/auth/login|captcha|register` 按 IP 限流、登录失败按「账号+IP」Redis 计数(超限 429);种子账号 admin/ops 带 `sys_user.must_change_password=1`,登录发的访问令牌带 `pwdChange` claim 时 `JwtAuthenticationFilter` 仅放行 `PUT /profile/password` 与登出(前端 `ChangePasswordDialog` 强制弹窗),需改密账号**不签发刷新令牌**。CORS 走白名单 `app.cors-allowed-origins`(external.yml),SQL 日志(`logging.level.mybatis.sql`)默认 warn。

## 功能模块清单(索引)

> 完整功能描述(Controller/Service/关键表/要点/接口清单)见 **docs/需求设计说明书.md** 第 4 章;历史踩坑、机制细节与 live DB 迁移 SQL 见 **docs/踩坑速查.md** 与 **docs/变更归档.md**。本节仅作导航索引。

| 域 | 模块 | 关键入口 |
|----|------|---------|
| 账号 | 注册/登录/验证码/密码找回/个人资料 | AuthController / ProfileController |
| 家庭 | 家庭管理/多家庭切换/成员/邀请码/入家申请 | FamilyController / AuthController / MemberController |
| 内容 | 博客 / 日记 / 相册照片 / 放映厅(媒体库+视频库) / 照片瀑布 / 愿望单 / 书架 | Blog / Diary / Album+Photo / Video / Media(媒体引擎) / Cascade / Wish / Library 各 Controller |
| 互动 | 点赞 / 评论 / 通知 / 聊天室 | Like / Comment / Notification / Chat Controller + ChatWebSocketHandler |
| 生活 | 纪念日 / 提醒 / 计划 / 任务 / 记账(含贷款记录页签) / 家谱 / 签到积分 / 背景音乐 / 家庭保险箱 | Anniversary / Reminder / Plan / Task / Points / Music / Vault 各 Controller |
| 游戏 | 花园植物养殖(全家共养一棵) / 小游戏库(导入 SWF/GBA + Flash 播放器 Ruffle + GBA 模拟器 EmulatorJS) / 宠物连连看 | FamilyPlant / GameInfo 各 Controller + FlashPlayer.vue + GbaPlayer.vue + PetLinkLink.vue |
| 基础 | 文件上传 / 存储管理 / 首页聚合 / 运维 / 开源组件台账 / 每日内容 / 操作日志 / 系统参数 | File / Storage / Home+Public / Ops+Oss / Daily / Log 各 Controller |
| 光影 | 太阳位置 / 日月与晨昏(三档晨昏+月相+月出月落,纯天文计算) / 体积光 / 台灯 / 天气 / 天气代理 / 天气详情 / 首页仪表盘 | SolarUtil+SunService(`/public/sun-info`) + windowLight.js + SunLightLayer.vue |
| 物品 | 物品定位 + 户型图 + AI 语义 | ItemController / ItemService / ItemAiService+AiService(设计决策见 需求 §4.8.1) |
| AI | 图片生成/语音识别 + AI 测试台 + 家庭级 AI 配置 + AI 调用统计 | AiService / FamilyAiConfigService / AiController + AiStatsService(`/ops/ai/**`) |
| 厨房 | 菜单/菜谱/食材 | RecipeController / RecipeService |
| 工具 | 工具箱聚合页 / 脑图设计(simple-mind-map) / AI 测试台 / 3D 光影实验台(`/tools/light-lab`) / 贷款计算器 + 贷款记录 / Flash 播放器 / GBA 播放器 | MindMapController / MindMapService(各工具多为纯前端页面,详见 需求 §4.12) |
| 系统 | i18n / 主题(暖居/光尘 × 晨/暮) / 字典 / 独立产品页(咔哒 `/kada`、壁纸屏 `/wallpaper`,均 `meta.standalone`) | i18n/ + theme/ + stores/theme.js + utils/dict.js + views/Kada.vue + views/Wallpaper.vue |
| 移动端 | 设备自适应 | useDevice.js + MobileLayout.vue + Mobile* 组件 |

**关键坑速查**:已外移到 **`docs/踩坑速查.md`**(后端 §2 / 前端 §3 / 天文 §4 / 媒体引擎 §5 / WE §6 / 部署 §7),动手前按关键词 grep 该文件。

## 设计规范(统一实现,避免多种方式)

### 后端规范

1. **Controller-Service-Mapper 三层**:Controller 仅参数校验+调 Service+返回 Result;Service 单实现无接口层;Mapper 接口仅 BaseMapper,自定义 SQL 全部放 `resources/mapper/*.xml`(接口不写 `@Select/@Update` 注解,参数统一 `@Param`)。
2. **统一响应**:`Result.ok(data)` / `Result.error(ResultCode.XXX)`;异常走 `BizException(ResultCode)` + `GlobalExceptionHandler`。
3. **权限**:`@RequirePermission("code")` + `RequirePermissionAspect`;OWNER 恒真;新增接口前确保 auth_code 进 `sys_auth`+`sys_role_auth` 种子。
4. **操作日志**:`@OperationLog` 注解 + `OperationLogAspect` 异步落库;含 traceId(`TraceIdFilter` 生成 16 位 UUID 短串,写入 MDC + 响应头 `X-Trace-Id`)。
5. **SQL 日志**:`mybatis-plus.log-impl=SqlStatementLog`(SLF4J 实现,由 `logging.level.mybatis.sql` 控制,默认 `warn` 静默);需要排查 SQL 时调到 `debug`。**禁止 `System.out.println` 打 SQL**(同步 I/O + 污染 stdout)。
6. **软删**:`@TableLogic deleted`;**物理删必须用自定义 XML DELETE 语句**(MP `deleteById` 实为 UPDATE)。目前照片/相册/视频/图书四处硬删。
7. **家庭隔离**:所有业务数据带 `family_id`;JWT familyId 为快照,refresh 时按优先级解析;跨家庭访问返回 NOT_FOUND。
8. **多家庭**:`sys_user_role.family_id` 区分;当前家庭存 Redis;`default_family_id` 用户设置的默认家庭。**`family_id=NULL` 表示系统级**(OPS 绑定等),别用占位值填充。
9. **N+1 禁令**(强制):列表接口禁止在 for 循环里 `selectById` 取关联字段(authorName/uploaderName/requesterName 等)。**必须先收集所有 userIds,用 `selectBatchIds` 批量查,内存 Map 回填**。参考 `ActivityFeedService.getFeed` / `CommentService.list` / `AnniversaryService.list` / `VideoService.list` 的 `batchUsers()` 写法。已批量化的:Book/Chat/FamilyPlan/Task/Points/ActivityFeed/Comment/Anniversary/Video + 家庭列表/入家申请/家庭 AI 功能绑定/歌单加曲(V10.2)。
10. **缓存规范**(强制):键 `ihomy:{domain}:{id}`;短 TTL(用户/权限/公开首页 5min);**变更点必须显式 invalidate(矩阵见踩坑速查 §9)**;不变数据走内存缓存(`sys_home_module` 全局预热 + 双检锁懒加载兜底;家庭模块按 familyId 缓存 `ConcurrentHashMap`,变更 evict;**不引 Caffeine**);敏感数据不缓存(成员视图 `/public/home`)。
11. **UPDATE 不先 select**(强制):回写冗余字段用 `LambdaUpdateWrapper.eq(...).set(...).update(null)`,不要 `selectById` 再 `updateById`;**计数类字段(点赞/评论数)用 `setSql("like_count = like_count + delta")` 原子增量,禁止「先 COUNT 再 SET」**(并发快照互相覆盖,见踩坑速查 §8.3),参考 `ContentLikeService.adjustCount`。**依赖 `updated_at` 的表更新必须用 `LambdaUpdateWrapper` 只 SET 业务字段并重查**(MP `updateById` 会回写实体旧 `updated_at`、抑制 `ON UPDATE CURRENT_TIMESTAMP`,见踩坑速查 §2.2)。
12. **文件上传流式**(强制):大文件(>1MB)禁止 `file.getBytes()` 全量入堆(生产 `-Xmx384m` 上传 200MB 即 OOM)。**用 `MultipartFile` 重载 + `transferTo` + `Files.copy` 兜底**。FileService 已提供 4 个流式重载(`upload`/`uploadVideo`/`uploadBook` 通用+图片+视频+电子书),Controller 必须传 `MultipartFile` 不调 `getBytes()`。
13. **JVM/连接池配置**(基线):`spring.threads.virtual.enabled: true`(JDK21 虚拟线程,Tomcat 自动用);HikariCP `maximum-pool-size: 20` + `minimum-idle: 5` + `connection-timeout: 3000`。
14. **日志规范**(强制,详见 `docs/设计想法/日志/日志规范.md`):三类文件 access(接口,`AccessLogFilter` 自动)/server(流程+SQL+ERROR)/thirdparty(三方,`ThirdPartyHttp` 封装),按天滚动保留 7 天;六要素 时间/级别/线程/[tid]/位置/内容;tid 贯穿 HTTP/WS/@Async/自管线程池(配 TaskDecorator);三方调用一律走 `ThirdPartyHttp.get()`(自定义方法走 `.request()`);**报错必须带堆栈** `log.error("xx, p={}", p, e)`(禁止 printStackTrace/只打 getMessage);级别 ERROR=人工/WARN=可恢复/INFO=关键/DEBUG=细节;**所有写接口必须 @OperationLog**(module 大写/operationType 标准词/description 中文;token 刷新/已读等高频噪音端点除外);新敏感字段进 `AccessLogFilter.SENSITIVE_JSON` 打码清单;运维「详细日志」`GET /ops/logs/trace?tid=` 按 tid 扫三类文件,排查方法见 `docs/设计想法/日志/日志问题分析方法.md`。

### 前端规范

1. **API 分组**:`api/index.js` 按模块导出 `xxxApi` 对象;统一走 `api/request.js`(axios+JWT+401 自动刷新)。
2. **状态管理**:Pinia;`stores/user.js`(登录+权限)、`stores/app.js`(首页聚合)。
3. **路由守卫**:`meta.public` 无需登录;`meta.ops` 需 `ops:view`;纯 OPS 账号只能访问 `/ops`。
   - **`meta.standalone`(独立产品页)**:挂「另一款产品的独立页」时加 `meta.standalone`——`App.vue` 走独立分支,只渲染 `<router-view :key="route.path">`,**不套** ihomy 外壳(光影层/侧栏/页脚/播放器/回顶);`onMounted` 见 standalone 直接 return,跳过 `appStore.init()` 与 `userStore.ensureUserInfo()`;**外壳必须等 `router.isReady()`(`routeReady` 标记)后再渲染**;**续期失败不许顶到登录页**(401 流程统一走 `redirectToLogin()`,当前路由 standalone 时直接返回)。已用:咔哒 `/kada`、壁纸屏 `/wallpaper`(需求 §4.14/§4.15)。成因见踩坑速查 §3.8。
4. **样式**:CSS 变量(`main.css`)+ 深色模式 `html.dark` 覆写;**不显式声明 serif 字体**,继承 body sans-serif;要能被类覆写的内联样式走 CSS 变量(踩坑速查 §3.4)。
5. **图标**:Element Plus `el-icon`(线性图标);**Setting/Monitor 图标用内联 SVG 替代**(复杂 path 在 100% 缩放触发子像素光栅化开销)。
6. **动画**:GSAP 入场;`transform: translateZ(0)` 隔离合成层;`contain: layout style` 隔离布局;避免 `background-attachment: fixed`(性能杀手)。
7. **毛玻璃**:`backdrop-filter: blur(24px) saturate(1.1)`;子元素 hover 用 `transform` 而非 `box-shadow`(避免触发 backdrop-filter 重算);**长开页/持续动画页别用 backdrop-filter**(踩坑速查 §3.10)。
8. **可拖拽面板**:`useDragResize` 组合式函数;位置/大小持久化 localStorage;**事件监听器按需挂载**(`onDragStart`/`onResizeStart` 时挂 `mousemove`/`mouseup`,`onMouseUp` 时移除,不要 `onMounted` 常驻——参考 `AvatarCropper.vue`)。
9. **光影层全局化**:`SunLightLayer` + `AppSidebar` + `SiteFooter` 在 `App.vue` 全局挂载;`useSunLight` provide/inject 共享状态。
10. **i18n**:所有用户可见文本用 `$t('key')`;中英双语(键结构必须 zh-CN/en 对齐);`utils/dict.js` 枚举映射。外壳文案命名空间:`warm.*`(暖居)/`sidebar.*`(侧栏)/`home.dashboard.*`(光尘首页)/`feed.*`(动态流)。
11. **共享逻辑抽 utils(禁止三份实现)**:动态流类型标签/摘要/相对时间走 `utils/feed.js`(`feedTypeLabel`/`feedSummary`/`formatFeedTime`),默认楼层走 `floorPlanGeom.pickDefaultFloor`,枚举文案走 `dictText(t,...)`;新增重复逻辑先找现成 util。
12. **可点击非按钮元素**:用全局指令 `v-a11y-click`(自动补 `role=button`/`tabindex=0` + Enter/Space 触发 click),不要逐处手写三行键盘处理;`<img>` 必须带 `alt`(装饰图 `alt=""`)。
13. **打包分块**(强制):`vite.config.js` 必须配 `build.rollupOptions.output.manualChunks` 拆分大 vendor(当前 `element-plus`/`gsap`/`vue-i18n`/`epubjs`/`pdfjs`/`simple-mind-map` 六块)。**public/ 下静态资源不得与 npm 包重复**。
14. **重型资源异步加载**(强制):字体包/CSS(如 `qweather-icons.css` 44.9KB)阻塞首屏的,必须 `import('...')` 异步加载,不要同步 `import`。
15. **动画优先级**(强制):持续型动画(钟摆/心跳/呼吸)优先级 **CSS `@keyframes` > GSAP 直接操作 DOM ref > `requestAnimationFrame` + 响应式 ref**。**禁止用 rAF 每帧写 Vue ref 触发响应式重渲染**。
16. **并行请求**(强制):多个独立的 `await xxxApi.foo()` 必须改 `Promise.all([a, b, c])` 并行(参考 `Home.vue loadAll` + `stores/app.js init`)。串行只在真有依赖时用。
17. **computed 纯函数**(强制):`computed` 内禁止 `Math.random()`/`Date.now()`/副作用,否则每次访问重算且视觉跳动。需要随机/一次性计算用 `ref` + `watch(source, immediate)` 生成(参考 `Home.vue polaroidLayout`)。
18. **路由懒加载**:页面路由全部 `() => import('./views/...')`,不写同步 import。
19. **全局 UI 样式统一**(强制):所有 EP 组件(el-dialog/ElMessageBox/ElMessage/popper/button/tag/badge/input)配色/圆角/尺寸/z-index 一律由 main.css 全局覆写,**禁止组件 scoped 重复定义**;完整值见 `docs/UI设计提示词.md` §11a/§3。**命令式 API(ElMessage/ElMessageBox/ElNotification/ElLoading)样式已在 main.js 显式引入**——新增命令式调用须确认样式已引入,否则裸 DOM 渲染不可见(踩坑速查 §3.15)。
20. **按钮/标签/角标/图标/圆角统一**(强制,main.css 全局覆写,禁止 scoped):按钮四类(主/次/幽灵/危险,浅深色**不同色值不共用**)、el-tag 半透明磨砂、el-badge 半透明黑、el-icon `stroke-width:2px`、圆角(button 12/input 10/card+dialog 14);完整色值见 `docs/UI设计提示词.md` §18a。
21. **页面统一规范**(强制):根容器 `class="page"`(禁 scoped 覆写 max-width/margin/padding);页面级 H1/H2 移除,分区标题 `.section-label`;工具栏 `class="page-toolbar card"`(`.tb-left` 筛选 size=small、`.tb-right` 按钮 gap 8px);多选交互 `.pick-badge` 对勾圆标+卡片描边(**禁左上 checkbox 角标**);详见 `docs/UI设计提示词.md` §11b。
22. **位图资产压缩入库**(强制):装饰性位图压缩后放 `frontend/src/assets/` 并 ESM 导入(构建出内容哈希名,配 nginx `expires 7d; immutable`),**不放 `public/`**(无哈希换图不刷新);宽度按实际渲染 2 倍封顶,带噪点先 3×3 中值滤波再压,输出渐进式 JPEG。参照相册封面 2560×1920 1.37MB→800×600 112KB。
23. **提示说明不写实现**(强制,后端同守):所有用户可见的提示/说明/占位符/空状态/tooltip(i18n 值与硬编码文案)及后端报错文案(`BizException` 消息)与接口文档摘要(`@Operation`——`/doc.html` 无需登录公开可见),只讲「用户能做什么/发生了什么」,**不写实现方式、内部逻辑、实体信息**(库名表名字段名、配置文件/配置键/环境变量、机制参数与内部枚举码当解释、内部节律如「每 30 分钟检查一次」、三方响应字段名);密钥类只说「保存后不再显示,请自行留底」。机制细节归代码注释与日志(`log.warn` 带上下文),不进提示。**给 LLM 的提示词例外**:输出 JSON 字段名/枚举码是解析契约必须保留,但描述性语句同样不用库表/内部词汇。

### 性能规范(强制规则)

> **已踩坑清单**(100% 缩放卡顿 / backdrop-filter 滚动炸弹 / rAF 写 Vue ref / 常驻事件监听器 / 同步 import 阻塞首屏 / 入口 chunk 过大 / `getBytes()` OOM / SQL 日志同步 I/O / N+1)与「不建议改」清单见 **`docs/踩坑速查.md` §8**,完整取证见 `docs/变更归档.md`「性能优化」与 `docs/UI设计提示词.md` §19。由此固化的强制规则:

- **动画优先级**:持续型动画 CSS `@keyframes` > GSAP 直接操作 DOM ref > `requestAnimationFrame`;**禁止 rAF 每帧写 Vue ref**(见前端规范 15)。
- **事件监听器按需挂载**:`onDragStart`/`onResizeStart` 时挂 `mousemove`/`mouseup`,`onMouseUp` 时移除,不要 `onMounted` 常驻。
- **重型资源异步加载**:字体包/大 CSS(如 `qweather-icons.css`)必须 `import('...')` 异步,不要同步 `import`。
- **入口 chunk 分块**:`vite.config.js` 必须配 `manualChunks`(element-plus/gsap/vue-i18n/epubjs/pdfjs/simple-mind-map)。
- **文件上传流式 / N+1 / SQL 日志**:见后端规范 12 / 9 / 5。

#### SQL/索引规范(强制)

- **列表查询必须走索引**:WHERE + ORDER BY 字段必须在同一复合索引内,避免全表扫 + filesort。
- **复合索引顺序**:等值字段在前,范围/排序字段在后。如 `idx_family_status_created(family_id, status, deleted, created_at)` 服务于 `WHERE family_id=? AND status=? AND deleted=0 ORDER BY created_at DESC`。
- **逻辑删除字段进索引**:`deleted` 几乎所有查询都带,放进复合索引避免回表过滤。
- **`ORDER BY RAND()` 慎用**:全表排序,大数据集慢。家庭照片/相册等小数据集(≤ 1000 行)可接受,加 `ponytail:` 注释说明。大数据集改 id 范围随机或预生成随机列表。
- **物理删必须 XML DELETE**(见后端规范 6);**UPDATE 不先 select**(见后端规范 11)。

#### 验证基线

- 后端编译/测试:`cd backend; .\mvnw.cmd -B clean package` → BUILD SUCCESS(`Tests run: 8`,纯逻辑不起 Spring 上下文);只求编译加 `-DskipTests`
- 前端测试:`cd frontend; npx vitest run` → 25 passed(utils 纯逻辑:loan/password/feed;CI 在构建前执行)
- 前端构建:`cd frontend; npm run build` → 入口 chunk ≈373KB(实测 **373.33KB/gzip 149.43KB**,V10.3 起;基线 358.10KB 的 +15KB 全为新增中英双语文案进共享入口 chunk;各功能页/pdfjs/simple-mind-map/epubjs/hls.js 均为独立异步 chunk 仅对应场景加载;历史数字见 docs/变更归档.md)
  - **⚠ 口径:vite 报的是「字符数」不是「字节数」**(实测)。入口 chunk vite 报 318.71KB,`wc -c` 却是 343,667 字节,`wc -m` 才是 318,707 字符——差值是中文注释/字符串的 UTF-8 多字节开销。**别拿 `ls -la` 的字节数跟这个基线比**(会误判成涨了 24KB);要比特字节就 `wc -c` 对 `wc -c`。gzip 那个数即压缩后真实字节数。
- 界面/交互验证:harness 不要放 `target/`;持续动画页面用页面内 `evaluate` 量几何、派发 `el.click()`,别用截图或真实点击(必超时,见踩坑速查 §6 本地 IAB/Playwright 条)。
- 接口测试:同级独立项目(不在本仓库)`cd ..\autotest_framework; .venv\Scripts\python.exe -m pytest -m api` → 37 passed;**CI(GitHub Actions)每次推送自动验证:前后端构建+compose 起库导入 schema+后端启动+登录冒烟**。

## 已实现变更归档(已外置)

> 历史归档已整体迁至 **`docs/变更归档.md`**(现约 613KB / **141 小节** = 开头 14 个**功能域**小节 + 其后 127 个版本/治理小节),内容原样保留。含文件级改动表、设计决策、踩坑记录与 live DB 同步 SQL。
> **该文件开头有章节目录**(或 `grep -n "^##### " docs/变更归档.md` 列全部小节);**检索历史实现/设计决策/live DB 迁移 SQL 时读该文件;新的变更归档继续追加到文件末尾**(新增 `#####` 子节),不要再写回 AGENTS.md。

## 文件存储策略

- **当前(开发期)本地磁盘存储**:`file.upload-dir`(生产 `/opt/ihomy/uploads` Linux,开发 external.yml 覆盖),Nginx `/files/` 托管;DB 存 `/files/...` URL,与物理根解耦。**不要主动改 FileService 存储实现**(除非明确要求接 NAS/OSS;未来优先 NFS 挂载,代码零改动,见 docs/部署指导-Linux.md 附录)。
- **统一目录结构**:相册→`pictures/{相册名}/{相册ID}_{时间戳}_{文件名}`、视频/海报→`videos/`、音乐→`music/`、电子书→`books/{yyyyMM}/`、通用/头像→`files/{yyyyMM}/`;FileService 提供流式重载(upload/uploadVideo/uploadBook)。
- **存储设备**:`sys_storage_device`(family_id 家庭级隔离,name/device_type SYSTEM|NAS|REMOTE|MOUNT|BAIDU|NEXTCLOUD|WEBDAV/root_path/status);百度网盘/WebDAV/Nextcloud 已接入,OSS/S3 暂缓;设备增删改/目录映射需 `storage:manage`(OWNER)。文件浏览 `/storage/files`、资源管理器 `browse`/`file`、写操作 mkdir/rename/batch(均 `storage:manage`+@OperationLog)、目录映射 `/storage/map`——功能现状见需求设计说明书 §4.6.2/§4.3.3。
- **硬删除策略**:照片/相册/视频/图书删除时**物理删 DB 记录+磁盘文件**(自定义 XML DELETE 绕过全局 logic-delete;`FileService.deleteByUrl` 按 URL 解析物理路径删文件,防越界);博客封面/头像/家庭封面/背景音乐/家谱照片删除时**未**连带删文件(孤儿文件,可接受)。

## 配置与加密

- **外挂配置**(唯一开发生产差异机制):`IHOMY_CONFIG_PATH` 环境变量指定 yml 路径,`ExternalConfigLoader`(EnvironmentPostProcessor)启动早期加载,最高优先级覆盖 application.yml;含 MySQL/Redis 密码、邮件 SMTP、天气四件套、JWT 密钥。**改配置前先核实环境变量实际指向**(曾指向旧路径陈旧副本;启动命令里显式设置最可靠)。模板 `backend/src/main/resources/external.yml.template`;external.yml 不入 git。
- **DB/Redis/邮件密码明文**(避免鸡生蛋:DB 未连上无法读盐值解密);**业务凭证 AES-GCM 加密**:`AesUtil`(PBKDF2WithHmacSHA256 派生密钥 100000 次 + GCM 128bit tag);密文 `ENC(Base64(iv+cipher+tag))`;盐值存 `sys_parameter`(key=`aes-salt`,首启自动生成,优先环境变量 `IHOMY_AES_SALT`)。
- **OPS 加密接口**:`GET /api/ops/crypto/encrypt?plaintext=` 生成密文,`/decrypt?ciphertext=ENC(xxx)` 验证(均 `ops:view`)。
- **三处可扩展点的扩展方式**(现状见需求设计说明书——天气多源 §4.7.5、首页仪表盘 §4.7.7、AI 模型接入 §4.6.9):天气源=门面 `WeatherService` + `WeatherProvider`(现为和风 `QWeatherProvider`,凭证表 `sys_weather_credential`),新增三步(WeatherConst.PROVIDERS + 实现 + 前端下拉),凭证走 WeatherController;首页组件=四档 snap 尺寸 + hover 推开邻居,布局键 `ihomy:dashboard:layout:v2`;AI=家庭级模型池 `sys_family_ai_model`(LLM/IMAGE/ASR/LOCAL,密钥 ENC)+ 按功能绑定 `sys_family_ai_feature`(6 功能,`FamilyAiConfigService.resolveForFeature/resolveChain` 主+兜底,同义词表 `sys_synonym`+SynonymService),**新家庭要用 AI 必须家长先加模型再按功能绑定**(找物/放物可绑 LOCAL 离线用)。
- **profile 化(废弃)**:不再用 application-dev.yml profile;application.yml 即生产基线,所有环境差异统一走 external.yml 覆盖。

## 部署约定(Linux 2GB 求稳)

- **求稳方案**:MySQL 本机部署(调优后 ~180MB)+ Redis 用 Docker(轻量、便于升级)。
- **每应用一用户,权利分散**:后端 Spring Boot 以 `ihomy` 应用用户运行(systemd `User=ihomy`);MySQL 用 apt 自动创建的 `mysql` 用户;Redis 容器隔离;Nginx 用 `www-data`。**除关键步骤外不用 root**:装包、建用户、systemd 管理、`/etc/` 配置、防火墙、certbot、执行 schema.sql(数据库 root)需 root;代码获取/构建/编辑 application.yml 由 `ihomy` 用户操作。
- **JVM 调优**(systemd ExecStart):`-Xmx384m -XX:MaxMetaspaceSize=192m -XX:+UseSerialGC -Xss512k`。
- **MySQL 调优**:`config/mysql/my.cnf` → `cp` 到 `/etc/mysql/conf.d/ihomy.cnf`,关键项 `performance_schema=OFF`(省 80-100MB)。MySQL 端口 6306。
- **SSH 端口**:生产服务器统一 **19068**(禁止 22);所有 ssh/scp 加 `-p 19068`/`-P 19068`;防火墙放行 19068、关闭 22。
- **Docker 安装源**:Ubuntu 用阿里云镜像源(`mirrors.cloud.aliyuncs.com/docker-ce`),固定版本 29.7.0;**Redis 镜像** `docker pull redis`;**Git 克隆**用 SSH 地址(ihomy 用户先生成 ed25519 key 加到 GitHub)。
- **⚠ 三条部署必查(重装/新服务器)**:① nginx `mime.types` 的 `.mjs` 映射必须存在(`application/javascript js mjs;`),否则书架 PDF 查看器/户型图 PDF 底图「加载失败」;② 站点 conf 的 `server{}` 必须有 gzip 四行,否则首屏多传约 336KB;③ 放映厅媒体引擎的**播放地址必须与站点同为 HTTPS**——站点是 HTTPS 而播放地址填 HTTP 时,浏览器按混合内容拦掉视频请求(直出与转码都放不出来),而 36 项接口断言全绿查不出(它们不走浏览器)。①②详见踩坑速查 §7.1/§7.3,③见 §5(第 10 条)与 `docs/部署指导-Linux.md` §12.2。
- **⚠ 部署新前端后老客户端仍跑旧 bundle**(PWA Service Worker 预缓存):访问新路由会被旧 bundle 的兜底路由重定向到 `/home`,看着像「新路由没发布」;**冒烟前先 unregister SW + 清 caches**,用户侧 Ctrl+Shift+R。辨认方法与取证见踩坑速查 §7.2。
- 详细步骤在 `docs/部署指导-Linux.md`(入库脱敏版;本地 `Linux部署指导.md` 只留凭证台账)。

## 规划事项(未实现)

> **这里只做指引,不记内容**。完整规划(P1-P4)、实施计划与验收口径见 **`docs/需求设计说明书.md` §9**;代码级优化待办见 **§9.2(36 项,分安全/性能/前端/工程化四组)** 与 **§9.3(21 项,V9.98–V9.106 新增代码复审 + §9.2 逐条复核)**。**启动任何规划项前先读该章对应小节**,不要凭本文件或记忆开工。实现新功能前先 `grep schema.sql + router/` 对照模块种子。
- **V9.110(2026-09-29)已完成 5 项 P1 代码级**:音频转写流式化(`AiController`/`AiService`/`ThirdPartyHttp.requestStreaming`)、Service Worker 排除 `/api/{vault,auth,ops,profile}` + 登出清 `api-cache`、上传黑名单补 xhtml/xml 等、登出/refresh 吊销(新增 `WALLPAPER` 令牌类型 + 60s 轮换宽限 + JWT `jti` 唯一化)、前后端自动化测试骨架(vitest + `spring-boot-starter-test`,CI 去掉 `-DskipTests`)。详见 `docs/变更归档.md` V9.110。
- 当前判断:**§9.2 剩余 P1** 为生产 Redis 无密码发布 `0.0.0.0`、`/files/**` 直连无鉴权与 nginx `nosniff`/CSP、`docs/项目文档/*.docx` 重生成;§9.2 的 P2 中 **A 组(安全与数据保护)、B 组(性能与数据一致性)、C 组(前端体验与规范)各 6/6/9 条已解决(V10.1 / V10.2 / V10.3)**,余下为 **D 组(工程化:台账漏登/版本漂移、CI 闸门、备份/健康检查等)**。**§9.3 的两条 P1 已收口**(踩坑速查入库、部署文档补媒体引擎章节 —— V9.108),余下 P2:媒体引擎令牌直出前端、媒体服务器地址未限目标(SSRF)、年利率上限与列精度冲突必 500。前端遗留(P3,见 §9.2 C 末尾):超大单文件拆分 + ESLint/Prettier 配置、纯展示页剩余可点击 div 的键盘处理。
- 功能侧还剩:P1 放映厅 **S4 收尾**(S1–S4 已落地 V9.106–V9.108:转码 HLS 回退/字幕轨/成员播放档案/NAS 上线文档;剩 Emby 实测、多家庭共用一台媒体服务器、转码清晰度与多音轨选择)、P2 智能家居中控(Home Assistant,分 S1-S3)、P2 保险箱主密码(前端零知识,不可逆 UX 变更,待定夺)、场景主题方向(3D 光影实验台 `/tools/light-lab` 为底座)。

## 文档清单

- `README.md`(项目简介,GitHub 展示,不含密码); `docs/README.md`(**文档索引**:每份文档一句话定位+新人阅读顺序)
- `docs/架构设计.md`(系统上下文/请求流转/模块分域/关键机制/部署拓扑)
- `docs/需求设计说明书.md` — **完整功能需求唯一活文档**(功能模块清单+数据库设计 75 表+接口设计+规划事项 §9+修订记录),随迭代持续更新
- `docs/变更归档.md` — 已实现变更归档(文件级改动表+设计决策+踩坑+live DB 同步 SQL);**开头有章节目录**,新变更追加到文件末尾
- `docs/踩坑速查.md` — **踩坑/机制细节/维护清单**(工具链/后端/前端/天文/媒体引擎/WE/部署/性能/缓存失效矩阵/已知问题);本文件凡写「详见踩坑速查 §x」者均指它
- `docs/UI设计提示词.md` — 沉浸式首页 UI 设计完整规格(可作为 AI 提示词重新生成)
- `docs/部署指导-Linux.md` / `docs/部署指导-Windows.md` — 生产部署全流程(systemd/NSSM/Nginx/Let's Encrypt/Docker Compose/备份/NAS,**脱敏入库版**,凭证一律占位符);本地 `Linux部署指导.md` 只剩 §〇凭证台账(不入 git)
- `docs/新人上手指南.md` — 新成员环境搭建+开发账号初始密码(**本地维护不入 git**);`docs/设计想法/` — 设计稿与探询记录(历史草稿、不再维护,唯 `日志/` 子目录仍在维护:日志规范/日志问题分析方法)
- `docs/项目文档/` — 成套 Word 交付物(数据库结构/接口/用户手册);**由代码与库结构生成,随 schema.sql/接口变更重新生成** `bash scripts/docs-gen/pipeline.sh`(改内容改源文件,不要手改 docx)
- `test/cases/功能测试用例.md` — 全站功能测试用例(341 条,32+ 域);测试资产统一在 `test/`(用例 `cases/`、接口自动化 `automation/`、UI 自动化 `ui-automation/`、报告 `reports/`、结果 `results/`,总览 `test/README.md`)
- `scripts/`:`setup.ps1`(新人一次性环境初始化,幂等)/ `start-all.ps1`(日常一键启动前后端,双击 `start.bat` 调用)/ `restart-all.ps1`(**一键重建重启**:停旧→`mvnw clean package`+`npm run build`→起新并轮询端口就绪;`-SkipBuild` 只停起)/ `start-db.ps1`(起 MySQL+Redis,首启自动导 schema.sql);根 `docker-compose.yml`(开发中间件,含健康检查);`.github/workflows/ci.yml`(CI);`config/mysql/my.cnf`(端口 6306 内存优化)
- 完整接口清单见 `docs/需求设计说明书.md` 第 7 章。代码事实以 `backend/src/main/java` + `resources/schema.sql` 为准,检索前先 `grep`。

## 环境检查(参考)

本机已装:JDK 21、Node 20、MySQL 8、python-docx。Maven 用 Wrapper(或 temp 目录 3.9.9)。Redis 本机可能未装(可用 Docker 或 Memurai)。
