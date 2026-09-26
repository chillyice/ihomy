# AGENTS.md — ihomy 项目规则

> 本文件供 ZCode(及 opencode 等)跨会话加载,记录项目关键规则、约定与当前事实。新会话启动时会自动读取,无需重复说明背景;修改后立即对所有新会话生效。
> **分工(2026-09-04 起)**:本文件只留规则+导航索引,目标 **≤60KB 保证完整注入上下文**(超出会被截断且尾部最先丢失);完整功能需求见 `docs/需求设计说明书.md`(活文档);历史实现归档见 `docs/变更归档.md`(新归档追加到该文件,不写回本文件)。

> **⚠ Git 规定(必须遵守)**:非人工指令,不得主动提交代码(`git commit`/`git add -A`/`git push` 一律禁止)。`git add` 只能指定具体文件路径,禁止 `git add -A`/`git add .`。

> **⚠ 敏感数据规定(必须遵守)**:**生产**密码/密钥/私钥/token 一律不写入仓库文件——生产 DB 密码与 JWT 密钥走服务器 external.yml(不入 git,模板 `external.yml.template`);凭证台账在本地 `Linux部署指导.md` §〇(两平台共用)、开发账号明文在本地 `docs/新人上手指南.md`(均 .gitignore 忽略,不入 git);前端演示凭证走 `frontend/.env.development.local`(仅 vite dev 加载,生产构建不读取;**`.env.local` 所有模式都加载会内联进生产 bundle,禁用**)。schema.sql 为开发安全版已入库(2026-09-07):仅含本机 Docker 开发固定凭证,生产凭证完全独立,部署时须 `ALTER USER` 改强密码。历史明文凭证清理+轮换见 docs/变更归档.md 敏感数据治理小节。

> **⚠ 路径拼写警示(遵守以防误写)**:
> - 工作目录绝对路径:`C:\Users\chill\OneDrive\WorkStation\Projects\ihomy`
> - 每次读/写/移动文件前先逐字核对路径;发现读不到文件时优先怀疑路径拼写而非文件不存在。
> - **⚠ 编码警示(必须遵守)**:含中文的源码/配置/SQL 一律走本工具的 Read/Write/Edit 读写,禁止用 PowerShell `Get-Content`/`Set-Content`/`WriteAllText` 读写(PS 5.1 默认 GBK 会破坏 UTF-8 中文,且 `[IO.File]::WriteAllText` 默认带 BOM 导致 javac 报非法字符)。PowerShell 仅用于:npm/mvn 构建、HTTP 冒烟。
> - **⚠ 脚本化改写文档会吃掉 CRLF(2026-09-26 踩坑)**:本仓 `core.autocrlf=true`、工作区 md 为 CRLF,而 `io.open(p,encoding='utf-8').read()` 默认把 `\r\n` 折成 `\n`,写回即把整份文件换成 LF(`git diff` 会警告 `LF will be replaced by CRLF`)。**批量/行级改写一律 `'rb'` 读 + `split(b'\r\n')` + `b'\r\n'.join()` 写**(零编码风险);小改用 Edit 工具(按文件原约定写)。复核:`open(p,'rb').read()` 的 `\n` 数减 `\r\n` 数应为 0。
> - **⚠ PowerShell 5.1 原生命令传参两坑**(2026-09-07 start-db.ps1 踩坑):① 不带引号的点号参数会被拆词(`mysqladmin ping -h127.0.0.1` 实际收到主机 `127`),必须写成 `'-h127.0.0.1'`;② 传给原生命令的参数里嵌双引号会被吃掉(`docker inspect --format '{{ index .X "key" }}'` 必然探测失败),改用无引号模板 `{{.Config.Labels}}` 或行为式判断。另:**.ps1 含中文必须带 UTF-8 BOM**(无 BOM 时 PS 5.1 按 GBK 解析直接语法错误);Write 工具默认无 BOM,新建中文 .ps1 后须补 BOM。
> - **⚠ Git Bash(MSYS)路径自动转换坑**(2026-09-08 踩坑):Git Bash 调 Windows 原生命令(docker.exe 等)时,参数里的 POSIX 风格路径会被自动转成 Windows 路径(`/tmp/x.sql` → `C:/Users/.../Temp/x.sql`),导致 `docker cp file ihomy-mysql:/tmp/` 与 `docker exec ... source /tmp/x.sql` 静默失效报 `error: 2`——**docker 容器内路径参数一律加 `MSYS_NO_PATHCONV=1` 前缀**(如 `MSYS_NO_PATHCONV=1 docker exec ihomy-mysql mysql -e "source /tmp/x.sql"`)。
> - **⚠ 后端内存缓存直改 DB 不生效**(2026-09-08 踩坑):`HomeModuleService` 全局模块 `@PostConstruct` 预热进内存(**无 TTL 无兜底刷新**),直接 UPDATE `sys_home_module` 不会失效——直改 DB 后必须重启后端才生效(或走模块管理接口触发 evict);同理适用于其他启动时预热的内存缓存。
> - **⚠ 打包 zip 别用 `Compress-Archive`**(2026-09-24 壁纸包踩坑):PowerShell 5.1 的 `Compress-Archive` 写出的条目用**反斜杠**分隔(`wallpaper-engine\index.html`),不符合 ZIP 规范(应正斜杠),Windows 资源管理器/7-Zip 能忍,但 Info-ZIP 等非 Windows 解压工具会把整条路径当成一个文件名。**用 Windows 自带 bsdtar**:`tar -a -c -f out.zip dir`(`-a` 按扩展名选格式)。
> - **⚠ 数据库写中文警示**:向 MySQL 写入含中文的 SQL 时,**禁止**用 PowerShell 管道 `Get-Content file.sql | docker exec -i mysql mysql ...`(PS 5.1 管道编码非 UTF-8 导致中文乱码)。**正确方式**:① 用本工具 Write 写 SQL 文件(UTF-8 无 BOM)→ `docker cp file.sql ihomy-mysql:/tmp/` → `docker exec ihomy-mysql mysql --default-character-set=utf8mb4 ihomy -e "source /tmp/file.sql"` → 清理临时文件;② 纯 ASCII SQL 可直接 `docker exec mysql -e "..."`;③ 远程用 `scp -P 19068 file.sql root@ihomy.top:/tmp/` → SSH 执行 `mysql -e "source /tmp/file.sql"`。终端显示中文为 `?` 是 GBK 终端问题,不代表存储乱码,用 `python -c "import subprocess; ..."` 验证。

## 项目概述

- **应用名**:ihomy(家庭共用软件)。家庭内部内容共享平台,PC 浏览器 / 安卓 / iOS(均 PWA)。
- **核心功能**:登录注册、博客、日记、相册、纪念日、留言板、放映厅、聊天室、积分商城、任务悬赏、提醒、家庭计划、愿望单、记账、家谱、书架、工具箱(脑图设计)、运维。首页模块化可扩展(后期新增功能只需插入一条 `sys_home_module` 记录)。
- **技术栈**:前端 Vue3 + Vite + ElementPlus + PWA(`ihomy-frontend`);后端 Spring Boot 3 + MyBatis-Plus + MySQL 8 + Redis(JWT 双 token + 验证码 + WebSocket),包 `com.ihomy`,主类 `IhomyApplication`,`ihomy-backend`。

## 工作目录

项目位于单一目录(已从双目录合并为单目录):

| 目录 | 用途 |
|------|------|
| `C:\Users\chill\OneDrive\WorkStation\Projects\ihomy` | 唯一工作目录(代码编辑 + 构建验证 + 部署文档) |

- 所有代码编辑、编译、构建验证都在此目录进行。
- 部署文档:流程在 `docs/部署指导-Linux.md`/`docs/部署指导-Windows.md`(入库脱敏版);本地 `Linux部署指导.md`(§〇凭证台账)也在此目录。
- Nginx 静态根指向 `frontend/dist`,构建后直接生效(Ctrl+Shift+R 刷新浏览器)。

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

## 数据库约定

- **root 仅用于初始化**:`mysql -uroot -p < backend/src/main/resources/schema.sql`(建库/建表/建账号/初始数据),执行一次;本地开发 `.\scripts\start-db.ps1`(Docker 首启自动导入)。schema.sql 为开发安全版(仅本机 Docker 开发凭证,生产须改独立强密码)。
- **业务运行用 `ihomy` 账号**:仅授予 `SELECT/INSERT/UPDATE/DELETE` on `ihomy.*`(最小权限,无 CREATE/ALTER/DROP)。application.yml 连接用 `ihomy`,**不要用 root 跑业务**。
- 账号同时创建 `localhost` 和 `%` 两个 host(本机/远程应用服务器都能连)。
- **生产 MySQL 密码策略(2026-09-07 轮换踩坑)**:生产库启用 `validate_password` MEDIUM(特殊字符/数字/大小写各≥1,长度≥8)——生成/轮换 DB 密码必须含特殊字符(避开 `' " \ $ |` 转义雷区,建议 `!@%^&*-_+=.`),否则 `ALTER USER` 报 1819;开发 Docker MySQL 无此组件,同一密码 dev 可用 prod 被拒。
- **70 张表**,前缀分类:`sys_` 20 张(系统/账号/权限/配置/存储)、`report_` 3 张(报表/日志:report_ai / report_weather / report_system)、`family_` 25 张(家庭事务)、`content_` 21 张(内容数据)、另 `game_info` 1 张(家庭小游戏,命名未加 family_ 前缀——遗留)。**完整表清单见 `docs/需求设计说明书.md` §6.2**。
  - **命名规则**:家庭事务业务表一律 `family_` 前缀;内容数据 `content_` 前缀;账号/权限/配置/存储保留 `sys_`;**报表/日志表一律 `report_` 前缀**(2026-09-17 V9.72 起,原 sys_weather_log→report_weather、sys_operation_log→report_system,新增 AI 调用日志 report_ai)。新增表必须遵守。前缀取最顶层祖先类别;上下级关系体现在表名(如 `sys_user_role`)。
- **引用开源软件必须对接自动升级(强制)**:新增任何 npm/Maven 直接依赖或独立开源服务时,**必须同时在 `sys_oss_component` 台账登记一条记录**(`component_type`=NPM/MAVEN/SERVICE + `package_ref` + `current_version` + `license` + `repo_url` + `managed_by`),否则不会被版本跟踪与升级覆盖。**升级闸门(V9.76)**:NPM/MAVEN 交 Renovate 管理(`managed_by=RENOVATE`,`renovate.json` `dependencyDashboardApproval` 检测模式只列 Dashboard 不开 PR)——台账「AI 评估」(`OSS_UPGRADE_EVAL` 功能码)判断升级影响→评估可行→「生成升级 PR」勾选 Dashboard 触发 Renovate 开 PR;SERVICE 独立服务仍台账内部维护(`managed_by=INTERNAL`),按 `deploy_type` 生成方案。漏洞扫描预留 `vuln_count/vuln_severity` 列。运维入口 `/ops/oss`(OPS 角色,`ops:view`),规则实现见 `common/OssVersionUtil` + `service/OssComponentService`。
- **枚举不再用数字**:状态/类型字段一律大写英文单词(`PUBLISHED/DRAFT/PUBLIC/FAMILY/ACTIVE...`),含义存字典表 `sys_dict_item`,Java 常量集中于 `common/DictConst.java`,前端映射 `utils/dict.js`。**不要写回 0/1/2 判断**。
- **注意**:`content_blog/diary/photo/video/wish` 5 张内容表 `visibility` 列为 `VARCHAR(20) DEFAULT 'FAMILY'`(PRIVATE仅自己/FAMILY家庭可见/PUBLIC公开),schema.sql 与 live DB 已对齐(曾误写 TINYINT)。
- 权力 4 角色:OWNER/MEMBER/CHILD/GUEST + OPS(运维,不属任何家庭)。同一用户不同家庭可不同角色(`sys_user_role.family_id` 区别)。
- **新增带 `@RequirePermission` 接口前**:确保 auth_code 进 `sys_auth` + `sys_role_auth` 种子(OWNER 豁免,MEMBER 显式授权),否则 403。
- **索引规范**(强制):列表查询的 WHERE + ORDER BY 字段必须落在同一复合索引内。复合索引顺序:等值字段在前,范围/排序字段在后;`deleted` 进索引(逻辑删除几乎每查必带)。已建关键复合索引:`content_blog.idx_family_status_created(family_id,status,deleted,created_at)`、`content_diary.idx_family_created(family_id,deleted,created_at)`、`content_photo.idx_family_created(family_id,deleted,created_at)`、`family_notification.idx_receiver_read(receiver_id,is_read)`。新增表/接口前先 `EXPLAIN` 验证走索引。

## 代码结构

```
backend/ (Spring Boot 3, JDK 21, 包 com.ihomy)
  src/main/java/com/ihomy/
    IhomyApplication.java   # 主类 @MapperScan("com.ihomy.mapper")
    common/      # Result/ResultCode/BizException/GlobalExceptionHandler/DictConst/SolarUtil/AesUtil/UserNames/Loggers/Ips/ThirdPartyHttp
    config/      # SecurityConfig/CorsConfig/MybatisPlusConfig/Knife4jConfig/WebMvcConfig/WebSocketConfig/SqlStatementLog/ExternalConfigLoader/WsHandshakeInterceptor/AsyncConfig
    security/    # JwtUtils/JwtAuthenticationFilter/LoginUser/SecurityHelper/OpsAccessFilter
    annotation/  # @RequirePermission / @OperationLog
    aspect/      # RequirePermissionAspect / OperationLogAspect
    filter/      # TraceIdFilter / AccessLogFilter / CaptureRequestWrapper / CaptureResponseWrapper
    entity/      # 63 个实体类(70 张表里 7 张关联/字典表无实体)
    mapper/      # 63 个 MyBatis-Plus BaseMapper(自定义 SQL 全放 resources/mapper/*.xml,接口不写注解,参数统一 @Param)
    service/     # 58 个 @Service(单实现无接口层)
    controller/  # 39 个 Controller
    dto/         # 请求/响应 DTO
    websocket/   # ChatWebSocketHandler(原生 WebSocket 聊天室)
  src/main/resources/
    application.yml     # 端口8080 context-path=/api;生产基线配置(MySQL 6306/Redis 6379;DB密码/JWT密钥留空,由 external.yml 提供);file.upload-dir /opt/ihomy/uploads;logging.file.path /opt/ihomy/logs
    logback-spring.xml  # 三类日志分流(access/server/thirdparty,六要素 pattern,按天滚动)
    external.yml.template  # 外挂配置模板(IHOMY_CONFIG_PATH 覆盖密码/密钥/路径/captcha/天气,唯一开发生产差异机制)
    mapper/*.xml        # 每个 Mapper 一个同名 XML
    schema.sql          # 建库+建号+建表(70 张)+种子(开发安全版,已入库;本地开发由 start-db.ps1 自动导入)
  mvnw / mvnw.cmd       # Maven Wrapper
frontend/ (Vue3 + Vite + PWA + Element Plus + Pinia)
  src/
    api/          # request.js(axios+JWT+401 自动刷新) + index.js(35 个 Api 对象)
    stores/       # user.js(登录+权限) / app.js(首页聚合) / theme.js(主题两轴矩阵)
    router/       # 登录守卫 + scrollBehavior;53 条路由(50 条懒加载,另 3 条 redirect: /、/plant、兜底)
    i18n/ theme/  # vue-i18n 中英;主题两轴矩阵(暖居/光尘 × 晨/暮)
    utils/        # dict.js / diary.js / doodle.js(涂鸦引擎) / furnitureIcon.js(家具类型图标) / windowLight.js / useSunLight.js / useDragResize.js
    composables/  # useDevice.js(设备检测) / useWeatherBg.js(天气 AI 生图氛围底图)
    components/   # AppSidebar/BackToTop/Breadcrumb/AvatarCropper/InstallPrompt/SiteFooter/SunLightLayer/LightTestConsole/SyncDialog/Mobile*(移动端)/warm/(暖居外壳 WarmLayout+WarmHome)
    layouts/MobileLayout.vue  # 移动端壳
    styles/main.css # CSS 变量 + 全局样式 + 深色模式 + EP 组件覆写 + @media
    views/        # 55 个页面(唯一视图文件计数;Home/Login/Member/Settings/Anniversary/album/cinema/diary/blog/points/task/reminder/plan/wish/book/chat/tree/cascade/ops/storage/item/kitchen/library/tools/games(Games+GamePlayer+PetLinkLink)/plant(花园)/kada(咔哒独立下载页)/Wallpaper(壁纸氛围屏))
    App.vue
  vite.config.js   # PWA + 代理 /api->8080 + manualChunks 分块 + ElementPlus 按需
wallpaper-engine/   # Wallpaper Engine 网页壁纸包(壳页顶层跳转线上 /wallpaper;WE 属性 theme/mode/lang 走查询参数、
                    # token 走 URL hash;不含构建产物,ihomy 改版自动跟随;不是前端源码也不是构建产物,不进 dist)。导入步骤见该目录 README
```

## 构建与验证命令

后端(在 backend 目录):
```powershell
.\mvnw.cmd -B clean package -DskipTests      # 编译+打jar,产物 target/ihomy-backend.jar
.\mvnw.cmd -B clean compile -DskipTests       # 仅编译验证
.\mvnw.cmd spring-boot:run                     # 开发运行(端口8080)
```
- 有 jar 锁先按 PID 结束本地后端进程(命令行含 JetBrains/IntelliJ 的是 **IDEA 本体,绝不能杀**;禁止 `taskkill /IM java.exe` 全杀)。日志路径:生产 `/opt/ihomy/logs`(三子目录 access/server/thirdparty),开发由 external.yml 指定。
- **⚠ `mvnw.cmd` 必须在 `backend` 目录内调用**(V9.94 踩坑):它在**当前目录**找 `.mvn/wrapper`,而 wrapper 在 `backend/` 下,在仓库根调会报「找不到 maven-wrapper.properties / 主类 MavenWrapperMain」——脚本里先 `Push-Location $Backend` 再调(见 `start-all.ps1`/`restart-all.ps1`)。
- 临时 Maven(本机未装 mvn):`C:\Users\chill\AppData\Local\Temp\opencode\apache-maven-3.9.9\bin\mvn.cmd`
- JAVA_HOME:`C:\Program Files\Java\jdk-21`(JDK 21 已装)
- 运行后端必须用完整路径单实例:`C:\Program Files\Java\jdk-21\bin\java.exe -jar target\ihomy-backend.jar`(javapath launcher + JDK 双实例会分流 8080 请求导致偶发 401/404/500)。

前端(在 frontend 目录):
```powershell
npm install        # 首次
npm run dev        # 开发(端口5173,代理/api到8080)
npm run build      # 生产构建,产物 dist/,含 PWA service worker
```

冒烟:登录 `POST /api/auth/login {email, password, captchaId, captchaCode:'qwer'}`(开发环境验证码固定 `qwer`,先 `GET /api/auth/captcha` 取 id),响应 code=0 即有 token。**新人环境初始化**:`.\scripts\setup.ps1`(前置检查+生成 config\external.yml+起库+前端依赖,幂等;详见本地 `docs/新人上手指南.md`)。**CI**:GitHub Actions(`.github/workflows/ci.yml`)每次推送自动做前后端构建+compose 起库导入 schema+后端启动+登录冒烟,验证仓库自给自足。数据库重导:整库 `schema.sql`;增量建表/改表直接执行对应 SQL 段(docker exec -i ihomy-mysql mysql -uroot -p<root密码> --default-character-set=utf8mb4)。

## 统一响应与鉴权

- 响应 `{code: 0, message: "success", data: ...}`;code != 0 = 失败。
- 登录:access token(2h)+ refresh(7d,Redis 黑名单登出失效)。请求头 `Authorization: Bearer <token>`,axios 自动续期。**约定(2026-09-01 踩坑)**:后端 `authenticationEntryPoint` 必须返回**真实 HTTP 401 状态码**(JSON 体照写)——前端 axios 只对 HTTP 401 触发 refresh 续期,只写 JSON 不设状态码(默认 200)会让续期永不执行,用户每 2h 被"未登录或登录已过期"登出;前端 `request.js` 用共享 `refreshPromise` 让并发 401 共享一次刷新后各自重放(`_retried` 防死循环)。续期时两个 token 都轮换(滑动续期),仅连续 7 天不访问才需重新登录。
- 接口前缀 `/api`;Knife4j 文档 `http://localhost:8080/api/doc.html`。
- **权限模型**:`buildTokens` 返回 `permissions` 数组 + `isOps` 标志;前端 `userStore.hasPerm(code)`/`isOps`/`isPureOps`。OWNER 恒真,其余查 `SysRoleMapper.selectAuthCodesByUserAndFamily`。
- **OPS 隔离**:`OpsAccessFilter` 只放行 OPS 到 `/api/ops/**`+`/api/auth/**`,其余 403;非 OPS 访问 /ops/** 一律 403;支持复合角色(OWNER+OPS)访问 `/api/ops/**`(查 **系统级 OPS 绑定 `sys_user_role.family_id IS NULL`**+5 分钟缓存)。**⚠ 判 OPS 只认系统级绑定(前端一律 `userStore.isOps`,禁止 `hasPerm('ops:view')`,V9.91)**:`ops:view` 只是权限码,家庭 OWNER 的角色授权里也带着它,但后端不放行 → 会白弹 403;OPS 角色的绑定必须带一条 `family_id=NULL` 的行(带家庭占位那条只用于登录时解析出 `role=OPS`),否则 `isOps` 恒 false、运维页按「家长」渲染。
- 点赞/评论/通知严格同家庭:`validateTarget` 校验内容 family_id 与用户一致,跨家庭返回 NOT_FOUND。

## 功能模块清单(索引)

> 完整功能描述(Controller/Service/关键表/要点/接口清单)见 **docs/需求设计说明书.md** 第 4 章;历史踩坑与 live DB 迁移 SQL 见 **docs/变更归档.md**。本节仅作导航索引。

| 域 | 模块 | 关键入口 |
|----|------|---------|
| 账号 | 注册/登录/验证码/密码找回/个人资料 | AuthController / ProfileController |
| 家庭 | 家庭管理/多家庭切换/成员/邀请码/入家申请 | FamilyController / AuthController / MemberController |
| 内容 | 博客 / 日记 / 相册照片 / 放映厅 / 照片瀑布 / 愿望单 / 书架 | Blog / Diary / Album+Photo / Video / Cascade / Wish / Library 各 Controller |
| 互动 | 点赞 / 评论 / 通知 / 聊天室 | Like / Comment / Notification / Chat Controller + ChatWebSocketHandler |
| 生活 | 纪念日 / 提醒 / 计划 / 任务 / 记账 / 家谱 / 签到积分 / 背景音乐 | Anniversary / Reminder / Plan / Task / Points / Music 各 Controller |
| 游戏 | 花园植物养殖(全家共养一棵)/ 小游戏库(导入 SWF/GBA + Flash 播放器 Ruffle + GBA 模拟器 EmulatorJS)/ 宠物连连看 H5(通关发积分) | FamilyPlant / GameInfo 各 Controller + FlashPlayer.vue + GbaPlayer.vue + PetLinkLink.vue |
| 基础 | 文件上传 / 存储管理 / 首页聚合 / 运维 / 开源组件台账 / 每日内容 / 操作日志 / 系统参数 | File / Storage / Home+Public / Ops+Oss / Daily / Log 各 Controller |
| 光影 | 太阳位置/**日月与晨昏(三档晨昏+月相+月出月落,纯天文计算)**/体积光/台灯/天气 / 天气代理 / 天气详情 / 首页仪表盘 | SolarUtil+SunService(`/public/sun-info` 含 288 时隙+全部日月时刻) + windowLight.js + SunLightLayer.vue |
| 物品 | 物品定位+户型图+AI 语义 | ItemController / ItemService / ItemAiService+AiService(设计决策见需求设计说明书 §4.8.1) |
| AI | 图片生成/语音识别接入+AI 测试台+家庭级 AI 配置+AI 调用统计 | AiService / FamilyAiConfigService / AiController(/ai/status、/ai/config、/ai/chat、/ai/image、/ai/transcribe)+ AiStatsService(/ops/ai/**) |
| 厨房 | 菜单/菜谱/食材 | RecipeController / RecipeService |
| 工具 | 工具箱聚合页/脑图设计(simple-mind-map,快照/回滚/协同轮询)/AI 测试台(/tools/ai-playground 临时)/3D 光影实验台(/tools/light-lab 临时,Three.js 太阳模拟+真实阴影,未来场景主题基础)/Flash 播放器(/tools/flash)/GBA 播放器(/tools/gba) | MindMapController / MindMapService |
| 系统 | i18n / 主题(暖居/光尘 × 晨/暮) / 字典 / 独立产品页(咔哒 Kada 下载页 `/kada`;壁纸氛围屏 `/wallpaper`,均 `meta.standalone`) | i18n/ + theme/(index.js)+stores/theme.js + utils/dict.js + views/Kada.vue + views/Wallpaper.vue |
| 移动端 | 设备自适应 | useDevice.js + MobileLayout.vue + Mobile* 组件 |

**关键坑速查**(实现细节详见 docs/变更归档.md):日记 date 兼容 `yyyy-MM-dd HH:mm`;纪念日 Hutool ChineseDate 月份 0-based 需 +1;家谱 null 字段须 `LambdaUpdateWrapper` 显式 SET;**MP `updateById` 会回写实体旧 `updated_at` 抑制 `ON UPDATE CURRENT_TIMESTAMP`**——依赖 updated_at 的表更新必须 LambdaUpdateWrapper 只 SET 业务字段并重查;**simple-mind-map 只内置 default 主题**(其余须 mindmapThemes.js defineTheme 注册);**脑图并发保存靠 update 乐观锁**(带 baseUpdatedAt,库中已刷新则 409);**脑图保存前 stripEmptyNodes 剥空叶子**;**EP dropdown 内嵌 hover 子菜单**用 visibility 延迟隐藏而非 display;物品户型图 hover 边加号阈值 6px、未设计楼层画布空白+引导、库内家具拖入画布替代「摆放」;**CSS `rotate()` 负角度在屏幕坐标(y 向下)里把元素下端往右摆(与直觉相反),要「右上→左下」须正角度;`animation` 简写覆盖同元素长写的 `animation-*`(如 delay),多粒子动画须用 `--var` 喂时长/相位**;**pdfjs-dist 统一 v6**(worker 用 `build/pdf.worker.min.mjs?url`,浏览器不用裸 iframe);**天气 AI 生图背景只有一份实现 `useWeatherBg`**(首页卡与暖居外壳同源、同一缓存键,不要再在 `Home.vue` 里写第二份——V9.86 已删掉那 115 行重复代码);**暖居照片卡牌扇形重叠时别用纯 CSS `:hover`**(命中的是 DOM 靠后那张而非视觉最上那张,须 JS `@mouseenter` 追踪索引再驱动类名);**要能被类覆写的内联样式走 CSS 变量**(transform 写死在 `:style` 里就无法被 hover 类覆盖,故卡牌位移/旋转/层级抽 `--dx/--dy/--rot/--z`);**`meta.standalone` 独立页的空壳期陷阱**——路由首次解析前 `currentRoute` 是 START_LOCATION(meta 为空),`v-if="route.meta.standalone"` 会先判为 false 而挂载整个 ihomy 外壳再卸载,独立页启动瞬间因此打出一批无关接口(含 OPS 权限 403 弹错);外壳一律等 `router.isReady()`(`routeReady` 标记)后再渲染;**待机计时类交互别只在 mousemove 里起算**(壁纸/副屏场景鼠标根本不动,须挂载后即起算;V9.93 起壁纸页已弃用待机浮现——照片轮播/纪念日/待办三小组件常驻,整卡可拖拽、pointerdown 固化 px 位置落 localStorage `ihomy:wallpaper:widget-pos:v1`,天气 AI 生图为常驻底图;V9.94 起小组件改**组件库可增删**——注册表 `WIDGET_DEFS`(6 类:照片/纪念日/待办/动态流/家庭计划/提醒)+ 角落「组件」面板启停落 `ihomy:wallpaper:widgets:v1`(读盘按注册表过滤未知 id,**新增组件 = 注册表加一条 + 模板加 `isOn('id')` 分支 + 样式补默认坐标**三步),**未启用的组件不取数**;壁纸页角落的「复制壁纸令牌」入口已删,令牌只在设置页一处;**长开页组件卡别用 backdrop-filter**——底下光影层持续动画会让磨砂每帧重算);**窄屏右下角浮层会压住左下角文字**(壁纸页控件 ≤768px 改挂右上角);**⚠ Wallpaper Engine 壁纸包(源在 `wallpaper-engine/`,机制/导入步骤/取证路径见该目录 `README.md`,踩坑细节见 docs/变更归档.md V9.88–V9.90)**:①**别用跨域 iframe**——WE 2.8.42 的 CEF 渲染 OOPIF 会崩渲染进程(现象「导入后编辑器预览整片纯灰」,但页面其实**已加载并执行**:CEF 缓存里有 chunk、localStorage 有 `ihomy-theme`,别误判成网络或脚本问题),改 `location.replace` 顶层跳转即绕过;②**WE 分次投递用户属性**——第一次只带 `project.json` 里声明的值,用户**改过**的值(壁纸令牌天生是)晚一拍才到,故壳页 `go()` **绝不能挂在第一次 `applyUserProperties` 上**(令牌会被整个丢掉,现象「复制令牌后没有效果」),改为「拿到令牌再等 400ms 就走、没拿到等到 3s 硬上限」;`theme/mode/lang` 走查询参数 `?theme=x&mode=y&lang=zh`、`token` 单独走 **URL hash**(不发给服务器、不进 nginx 日志),壁纸页另挂 `wallpaperPropertyListener` 双保险;③**WE 是「拷贝」导入**——改 `index.html` 就地覆盖 `myprojects\<项目名>\index.html` 再「文件→重启预览」,**`project.json` 千万别覆盖**(属性面板里粘的令牌存在那儿);重新拖导入会新建项目、令牌要重粘;④**壁纸令牌流程**=设置页复制 refresh token → WE 属性面板 → 页面 `bootstrapToken()` 调 `POST /auth/refresh` 换会话并落 WE 自己的 localStorage;后端 `AuthService.refresh` **不拉黑**旧 refresh token(只 `logout()` 才拉黑),故浏览器与壁纸可各持一份、各自滑动续期互不踢;⑤**本地 IAB/Playwright 点不动这个应用**(持续动画让「连续两帧稳定」的可操作性检查永不通过,`click()`/force/坐标点击均超时)——验证交互改用页面内 `el.click()` 派发真实 Vue handler,剪贴板用桩 `Object.defineProperty(navigator.clipboard,'writeText')` 断言写入内容(后台标签页 `document.hasFocus()===false`,真剪贴板必被拒并走「复制失败」兜底,别误判成 bug)。**⚠ 日月与晨昏(晨昏三档/月相/月出月落)走纯天文计算,不取天气 API**(V9.92):后端 `SolarUtil.astroTimes`/`moonPhaseInfo` → `GET /public/sun-info`(天气页「日月与晨昏」卡与日历页共用;和风 v1 `daily[].astro` 其实也带这些字段,实测与本地差 0~2 分钟,但**同一事实只留一份来源**,那 10 个透传字段已删,别再往 `daily` 里塞第二份);月亮时角**必须用地方恒星时**(`gmstDeg`),照搬太阳那套「距太阳正午分钟数」近似会错;当日不发生的事件(极昼极夜、月亮不升不落)**返回空串由前端隐藏**,别填占位假时刻;求解统一按「采样找穿越 + 二分/三分细化」(太阳阈值 -0.833°、三档晨昏 96°/102°/108° 天顶角、月亮 +0.125°)。

## 设计规范(统一实现,避免多种方式)

### 后端规范

1. **Controller-Service-Mapper 三层**:Controller 仅参数校验+调 Service+返回 Result;Service 单实现无接口层(V3.8 起);Mapper 接口仅 BaseMapper,自定义 SQL 全部放 `resources/mapper/*.xml`(接口不写 `@Select/@Update` 注解,参数统一 `@Param`)。
2. **统一响应**:`Result.ok(data)` / `Result.error(ResultCode.XXX)`;异常走 `BizException(ResultCode)` + `GlobalExceptionHandler`。
3. **权限**:`@RequirePermission("code")` + `RequirePermissionAspect`;OWNER 恒真;新增接口前确保 auth_code 进 `sys_auth`+`sys_role_auth` 种子。
4. **操作日志**:`@OperationLog` 注解 + `OperationLogAspect` 异步落库;含 traceId(`TraceIdFilter` 生成 16 位 UUID 短串,写入 MDC + 响应头 `X-Trace-Id`)。
5. **SQL 日志**:`mybatis-plus.log-impl=SqlStatementLog`(SLF4J 实现,由 `logging.level.mybatis.sql` 控制,默认 `warn` 静默);需要排查 SQL 时调到 `debug`。**禁止 `System.out.println` 打 SQL**(同步 I/O + 污染 stdout)。
6. **软删**:`@TableLogic deleted`;**物理删必须用自定义 XML DELETE 语句**(MP `deleteById` 实为 UPDATE)。目前照片/相册/视频/图书四处硬删。
7. **家庭隔离**:所有业务数据带 `family_id`;JWT familyId 为快照,refresh 时按优先级解析;跨家庭访问返回 NOT_FOUND。
8. **多家庭**:`sys_user_role.family_id` 区分;当前家庭存 Redis;`default_family_id` 用户设置的默认家庭。
9. **N+1 禁令**(强制):列表接口禁止在 for 循环里 `selectById` 取关联字段(authorName/uploaderName/requesterName 等)。**必须先收集所有 userIds,用 `selectBatchIds` 批量查,内存 Map 回填**。参考 `ActivityFeedService.getFeed` / `CommentService.list` / `AnniversaryService.list` / `VideoService.list` 的 `batchUsers()` 写法。已批量化的:Book/Chat/FamilyPlan/Task/Points/ActivityFeed/Comment/Anniversary/Video。
10. **缓存规范**(强制):键 `ihomy:{domain}:{id}`;短 TTL(用户/权限/公开首页 5min);变更点必须显式 invalidate(见下方「缓存失效矩阵」);不变数据走内存缓存(`sys_home_module` 全局 `@PostConstruct` 预热 + `globalLoaded` 双检锁懒加载兜底——预热失败不再致命;家庭模块按 familyId 缓存 `ConcurrentHashMap`,变更 evict;**不引 Caffeine**);敏感数据不缓存(成员视图 `/public/home` 含 stats/photos 不缓存)。
11. **UPDATE 不先 select**(强制):回写冗余字段(如 `like_count`)用 `LambdaUpdateWrapper.eq(id).set(field, value).update(null)`,不要 `selectById` 再 `updateById`(省一次查询)。参考 `ContentLikeService.syncCount`。
12. **文件上传流式**(强制):大文件(>1MB)禁止 `file.getBytes()` 全量入堆(生产 `-Xmx384m` 上传 200MB 即 OOM)。**用 `MultipartFile` 重载 + `transferTo` + `Files.copy` 兜底**。FileService 已提供 4 个流式重载(`upload`/`uploadVideo`/`uploadBook` 通用+图片+视频+电子书),Controller 必须传 `MultipartFile` 不调 `getBytes()`。
13. **JVM/连接池配置**(基线):`spring.threads.virtual.enabled: true`(JDK21 虚拟线程,Tomcat 自动用);HikariCP `maximum-pool-size: 20` + `minimum-idle: 5` + `connection-timeout: 3000`。
14. **日志规范**(强制,详见 `docs/设计想法/日志/日志规范.md`):三类文件 access(接口,`AccessLogFilter` 自动)/server(流程+SQL+ERROR)/thirdparty(三方,`ThirdPartyHttp` 封装),按天滚动保留 7 天;六要素 时间/级别/线程/[tid]/位置/内容;tid 贯穿 HTTP/WS/@Async/自管线程池(配 TaskDecorator);三方调用一律走 `ThirdPartyHttp.get()`(自定义方法走 `.request()`——JDK HttpURLConnection 不支持自定义方法);**报错必须带堆栈** `log.error("xx, p={}", p, e)`(禁止 printStackTrace/只打 getMessage);级别 ERROR=人工/WARN=可恢复/INFO=关键/DEBUG=细节;**所有写接口必须 @OperationLog**(module 大写/operationType 标准词/description 中文;token 刷新/已读等高频噪音端点除外);新敏感字段进 `AccessLogFilter.SENSITIVE_JSON` 打码清单;运维「详细日志」`GET /ops/logs/trace?tid=` 按 tid 扫三类文件,排查见 `docs/设计想法/日志/日志问题分析方法.md`。

### 前端规范

1. **API 分组**:`api/index.js` 按模块导出 `xxxApi` 对象;统一走 `api/request.js`(axios+JWT+401 自动刷新)。
2. **状态管理**:Pinia;`stores/user.js`(登录+权限)、`stores/app.js`(首页聚合)。
3. **路由守卫**:`meta.public` 无需登录;`meta.ops` 需 `ops:view`;纯 OPS 账号只能访问 `/ops`。
   - **`meta.standalone`(独立产品页,V9.84;V9.87 修首次渲染)**:在 ihomy 域名下挂「另一款产品的独立页」时给它加 `meta.standalone`——`App.vue` 走独立分支,只渲染 `<router-view :key="route.path">`,**不套** ihomy 外壳(光影层/侧栏/页脚/播放器/回顶);`onMounted` 见 standalone 直接 return,跳过 `appStore.init()` 与 `userStore.ensureUserInfo()`(不调 ihomy 后端接口)。**外壳必须等路由首次解析完成再渲染**(`routeReady`,见 `App.vue`):解析前 `currentRoute` 是 START_LOCATION(meta 为空),按「非独立页」渲染会让外壳先挂载再卸载,独立页启动瞬间打出一批无关接口(OPS 403 弹错/通知数/音乐/首页聚合)。同类页照此办理(已用:咔哒 Kada 下载页 `/kada`、壁纸氛围屏 `/wallpaper`,见需求设计说明书 §4.14/§4.15)。**续期失败不许顶到登录页**(V9.90):`api/request.js` 的 401 流程统一走 `redirectToLogin()`,当前路由 `meta.standalone` 时直接返回 —— 独立页没有 ihomy 登录页,顶过去会让「墙上的壁纸」变成 ihomy 登录页。
4. **样式**:CSS 变量(`main.css`)+ 深色模式 `html.dark` 覆写;**不显式声明 serif 字体**,继承 body sans-serif。
5. **图标**:Element Plus `el-icon`(线性图标);**Setting/Monitor 图标用内联 SVG 替代**(复杂 path 在 100% 缩放触发子像素光栅化开销,见性能优化博客 id=18)。
6. **动画**:GSAP 入场;`transform: translateZ(0)` 隔离合成层;`contain: layout style` 隔离布局;避免 `background-attachment: fixed`(性能杀手)。
7. **毛玻璃**:`backdrop-filter: blur(24px) saturate(1.1)`;子元素 hover 用 `transform` 而非 `box-shadow`(避免触发 backdrop-filter 重算)。
8. **可拖拽面板**:`useDragResize` 组合式函数;5 个面板各自实例;位置/大小持久化 localStorage;**事件监听器按需挂载**(`onDragStart`/`onResizeStart` 时挂 `mousemove`/`mouseup`,`onMouseUp` 时移除,不要 `onMounted` 常驻——参考 `AvatarCropper.vue` 的写法)。
9. **光影层全局化**:`SunLightLayer` + `AppSidebar` + `SiteFooter` 在 `App.vue` 全局挂载;`useSunLight` provide/inject 共享状态。
10. **i18n**:所有用户可见文本用 `$t('key')`;中英双语;`utils/dict.js` 枚举映射。
11. **打包分块**(强制):`vite.config.js` 必须配 `build.rollupOptions.output.manualChunks` 拆分大 vendor(当前 `element-plus`/`gsap`/`vue-i18n`/`epubjs`/`pdfjs`/`simple-mind-map` 六块)。**public/ 下静态资源不得与 npm 包重复**(已删 `public/qweather-icons/`,改走 `node_modules/qweather-icons/font/`)。
12. **重型资源异步加载**(强制):字体包/CSS(如 `qweather-icons.css` 44.9KB)阻塞首屏的,必须 `import('...')` 异步加载,不要同步 `import`。
13. **动画优先级**(强制):持续型动画(钟摆/心跳/呼吸)优先级 **CSS `@keyframes` > GSAP 直接操作 DOM ref > `requestAnimationFrame` + 响应式 ref**。**禁止用 rAF 每帧写 Vue ref 触发响应式重渲染**(参考 `useSunLight.js` 钟摆已改 CSS `@keyframes lampSwing`)。
14. **并行请求**(强制):多个独立的 `await xxxApi.foo()` 必须改 `Promise.all([a, b, c])` 并行(参考 `Home.vue loadAll` + `stores/app.js init`)。串行只在真有依赖时用。
15. **computed 纯函数**(强制):`computed` 内禁止 `Math.random()`/`Date.now()`/副作用,否则每次访问重算且视觉跳动。需要随机/一次性计算用 `ref` + `watch(source, immediate)` 生成(参考 `Home.vue polaroidLayout`)。
16. **路由懒加载**:50 条页面路由全部 `() => import('./views/...')`(另 3 条是 redirect),不写同步 `import Home from '@/views/Home.vue'`。
17. **全局 UI 样式统一**(强制):所有 EP 组件(el-dialog/ElMessageBox/ElMessage/popper/button/tag/badge/input)配色/圆角/尺寸/z-index 一律由 main.css 全局覆写,**禁止组件 scoped 重复定义**;完整值见 docs/UI设计提示词.md §11a/§3。**命令式 API(ElMessage/ElMessageBox/ElNotification/ElLoading)样式已在 main.js 显式引入**——unplugin 按需只覆盖模板组件,新增命令式调用须确认样式已引入,否则裸 DOM 渲染不可见。
18. **按钮/标签/角标/图标/圆角统一**(强制,main.css 全局覆写,禁止 scoped):按钮四类(主/次/幽灵/危险,浅深色**不同色值不共用**)、el-tag 半透明磨砂、el-badge 半透明黑、el-icon `stroke-width:2px`、圆角(button 12/input 10/card+dialog 14);完整色值见 docs/UI设计提示词.md §18a。
19. **页面统一规范**(强制):根容器 `class="page"`(禁 scoped 覆写 max-width/margin/padding);页面级 H1/H2 移除,分区标题 `.section-label`;工具栏 `class="page-toolbar card"`(`.tb-left` 筛选 size=small、`.tb-right` 按钮 gap 8px);多选交互 `.pick-badge` 对勾圆标+卡片描边(**禁左上 checkbox 角标**);详见 docs/UI设计提示词.md §11b。
20. **位图资产压缩入库**(强制):装饰性位图压缩后放 `frontend/src/assets/` 并 ESM 导入(构建出内容哈希名,配 nginx `expires 7d; immutable`),**不放 `public/`**(无哈希换图不刷新);宽度按实际渲染 2 倍封顶,带噪点先 3×3 中值滤波再压,输出渐进式 JPEG。参照相册封面 2560×1920 1.37MB→800×600 112KB。

### 性能规范(强制规则)

> **已踩坑清单**(100% 缩放卡顿 / backdrop-filter 滚动炸弹 / rAF 写 Vue ref / 常驻事件监听器 / 同步 import 阻塞首屏 / 入口 chunk 过大 / `getBytes()` OOM / SQL 日志同步 I/O / N+1)详见 `docs/变更归档.md`「性能优化」与 `docs/UI设计提示词.md` §19。由此固化的强制规则:

- **动画优先级**:持续型动画 CSS `@keyframes` > GSAP 直接操作 DOM ref > `requestAnimationFrame`;**禁止 rAF 每帧写 Vue ref**(见前端规范 13)。
- **事件监听器按需挂载**:`onDragStart`/`onResizeStart` 时挂 `mousemove`/`mouseup`,`onMouseUp` 时移除,不要 `onMounted` 常驻。
- **重型资源异步加载**:字体包/大 CSS(如 `qweather-icons.css`)必须 `import('...')` 异步,不要同步 `import`。
- **入口 chunk 分块**:`vite.config.js` 必须配 `manualChunks`(element-plus/gsap/vue-i18n/epubjs/pdfjs/simple-mind-map)。
- **文件上传流式 / N+1 / SQL 日志**:见后端规范 12 / 9 / 5。

#### SQL/索引规范(强制)

- **列表查询必须走索引**:WHERE + ORDER BY 字段必须在同一复合索引内,避免全表扫 + filesort。
- **复合索引顺序**:等值字段在前,范围/排序字段在后。如 `idx_family_status_created(family_id, status, deleted, created_at)` 服务于 `WHERE family_id=? AND status=? AND deleted=0 ORDER BY created_at DESC`。
- **逻辑删除字段进索引**:`deleted` 几乎所有查询都带,放进复合索引避免回表过滤。
- **`ORDER BY RAND()` 慎用**:全表排序,大数据集慢。家庭照片/相册等小数据集(≤ 1000 行)可接受,加 `ponytail:` 注释说明。大数据集改 id 范围随机或预生成随机列表。
- **物理删必须 XML DELETE**(见后端规范 6)。
- **UPDATE 不先 select**(见后端规范 11)。

#### 缓存失效矩阵(变更点 → invalidate)

| 变更场景 | 失效缓存 | 调用方法 |
|---------|---------|---------|
| `PUT /profile`(改昵称/头像) | `ihomy:user:{uid}` | `SecurityHelper.invalidateUser(uid)` |
| `PUT /member/{id}/role`(改角色) | `ihomy:perms:{uid}:{fid}` | `SecurityHelper.invalidatePerms(uid, fid)` |
| `DELETE /member/{id}`(移出成员) | `ihomy:perms:{uid}:{fid}` | `SecurityHelper.invalidatePerms(uid, fid)` |
| `POST /auth/family/switch`(切换家庭) | `ihomy:perms:{uid}:{newFid}` + `ihomy:user:{uid}` | `SecurityHelper.invalidatePerms(uid, newFid)` + `SecurityHelper.invalidateUser(uid)` |
| `POST /auth/join`(加入新家庭) | `ihomy:perms:{uid}:{newFid}` | `SecurityHelper.invalidatePerms(uid, newFid)` |
| `PUT /home/modules`(改模块配置) | `ihomy:home:pub:{fid}` + 内存 familyCache | `PublicController.invalidateHomeCache(fid)` + `HomeModuleService.updateConfig` 内 evict |
| `POST /home/modules`(新增模块) | 同上 | 同上 |
| 照片上传/删除 | `ihomy:home:pub:{fid}` | `PublicController.invalidateHomeCache(fid)` |
| 5min TTL 自然过期 | 所有 Redis 缓存 | 兜底机制 |

#### 不建议改(成本高/收益低)

- `TraceIdFilter` 用 `UUID.randomUUID()`(底层 SecureRandom)—— 单次 ~微秒,非瓶颈。
- `JwtUtils.parse` 每次重建 parser —— jjwt parser build ~微秒,QPS 上千才值得。
- BCrypt 密码加密 —— 安全要求,不可换。
- `StorageService.run` 单线程串行复制 —— 手动触发一次性任务,非热点。
- MyBatis-Plus 二级缓存 —— 默认未开(正确),二级缓存易脏数据,不推荐。
- `stores/app.js` 不加 sessionStorage 缓存(家庭数据可变,in-memory 已够)。
- `api/request.js` 不加请求去重/缓存(失效策略复杂,易脏数据)。
- `AppSidebar.vue:102` 23 个 EP 图标同步导入(每个 ~1-2KB,树摇后约 30KB,改动态反而增加运行时开销)。

#### 已知问题(待修复)

- **Edge 硬件加速整页频闪**(遗留,环境/驱动问题,非应用代码):切走再切回窗口(或开关硬件加速+重启 Edge)即恢复;Chrome 不复现。用户侧处置按序:开关硬件加速+重启 Edge → 更新显卡驱动 → 注册表禁 MPO(`OverlayTestMode`=5)→ 应用内关"毛玻璃"。详见 docs/变更归档.md「首页频闪排查与修复」。
- ~~**ElMessageBox 动画未生效**~~（2026-09-06 已定位并修复）：真因是命令式 API 的 EP 样式未被按需加载——弹窗以裸 DOM 渲染在文档流末尾（`.el-overlay` position:static）而非"动画问题"。已在 main.js 显式引入 message/message-box/notification/loading 四个组件样式；**新增命令式 API 调用时必须同步确认对应样式已引入**（模板按需加载不覆盖命令式调用）。
- ~~**未登录浏览家庭首页时 `GET /api/book/summary` 返回 500**~~（2026-09-24 V9.91 已修复）：该接口在 SecurityConfig 里本就对游客放行,后端改为匿名返回空摘要(游客看不到任何家庭数据),暖居外壳同时按登录态跳过取数(不再打一批注定 401 的请求)。详见 docs/变更归档.md「V9.91 三处线上缺陷修复」。
- ~~**OWNER 账号每页都弹一次「无权限访问」**~~（2026-09-24 V9.91 已修复）：口径统一为「判 OPS 只认系统级 OPS 绑定」——前端侧栏改判 `isOps`;OWNER 角色授权摘除 `ops:view`(`AuthService` 兜底 + schema/migrations);顺带修掉同源缺陷:运维账号因种子只建了带家庭占位的绑定而 `isOps=false`(运维页按「家长」渲染),migrations 补 `family_id=NULL` 的系统级绑定。详见 docs/变更归档.md 同节。
- ~~**生产 nginx 未压缩 JS/CSS/JSON**~~（2026-09-24 V9.91 已修复）：站点 conf 的 `server{}` 内补四行 gzip 并 reload(旧 conf 备份 `/etc/nginx/conf.d/ihomy.conf.bak-20260924`),实测入口 JS 343.7KB→143.2KB、主 CSS 174.2KB→38.3KB、`/api/public/home` JSON 7.2KB→1.8KB。详见 docs/变更归档.md 同节。

#### 验证基线

- 后端编译:`cd backend; .\mvnw.cmd -B clean compile -DskipTests` → BUILD SUCCESS
- 前端构建:`cd frontend; npm run build` → 入口 chunk ≈320.5KB(2026-09-25 V9.93 实测 320.49KB/gzip 128.60KB,较 V9.92 的 320.37KB 增 0.12KB=壁纸页 i18n 文案;V9.92 为 320.37KB/128.55KB=天气页日月晨昏的 i18n 文案与 dict 配色/emoji 映射,天气页自身是懒加载独立 chunk 21.90KB;旧基线 2026-09-08 V9.48 为 246.35KB/97.70KB;pdfjs 已隔离为独立异步 chunk ~483KB 仅 PDF 场景加载;simple-mind-map ~341KB 仅脑图编辑页加载;壁纸页独立 chunk 11.62KB/7.07KB 懒加载)
  - **⚠ 口径:vite 报的是「字符数」不是「字节数」**(2026-09-24 实测)。入口 chunk vite 报 318.71KB,`wc -c` 却是 343,667 字节,`wc -m` 才是 318,707 字符——差值是中文注释/字符串的 UTF-8 多字节开销。**别拿 `ls -la` 的字节数跟这个基线比**(会误判成涨了 24KB);要比特字节就 `wc -c` 对 `wc -c`。gzip 那个数即压缩后真实字节数。
- 接口测试:同级独立项目(不在本仓库)`cd ..\autotest_framework; .venv\Scripts\python.exe -m pytest -m api` → 37 passed;**CI(GitHub Actions,`.github/workflows/ci.yml`)每次推送自动验证:前后端构建+compose 起库导入 schema+后端启动+登录冒烟**

## 已实现变更归档(已外置)

> 历史归档已整体迁至 **`docs/变更归档.md`**(现约 478KB / **118 小节**:开头 14 个**功能域**小节——性能优化/首页仪表盘/音乐/光影/UI 规范/厨房/运维/UX/图书/移动端/播放器/博客/日记/相册×5/放映厅/天气/日志追溯/户型图等,其后 104 个**版本**小节按 V9.x 顺序追加),内容原样保留。含文件级改动表、设计决策、踩坑记录与 live DB 同步 SQL。
> **该文件开头有章节目录**(或 `grep -n "^##### " docs/变更归档.md` 列全部小节);**检索历史实现/设计决策/live DB 迁移 SQL 时读该文件;新的变更归档继续追加到文件末尾**(新增 `#####` 子节),不要再写回 AGENTS.md。

## 文件存储策略

- **当前(开发期)本地磁盘存储**:`file.upload-dir`(生产 `/opt/ihomy/uploads` Linux,开发 external.yml 覆盖),Nginx `/files/` 托管;DB 存 `/files/...` URL,与物理根解耦。**不要主动改 FileService 存储实现**(除非明确要求接 NAS/OSS;未来优先 NFS 挂载,代码零改动,见 docs/部署指导-Linux.md 附录)。
- **统一目录结构**:相册→`pictures/{相册名}/{相册ID}_{时间戳}_{文件名}`、视频/海报→`videos/`、音乐→`music/`、电子书→`books/{yyyyMM}/`、通用/头像→`files/{yyyyMM}/`;FileService 提供流式重载(upload/uploadVideo/uploadBook)。
- **存储设备**:`sys_storage_device`(family_id 家庭级隔离,name/device_type SYSTEM|NAS|REMOTE|MOUNT|BAIDU|NEXTCLOUD|WEBDAV/root_path/status);百度网盘/WebDAV/Nextcloud 已接入,OSS/S3 暂缓;设备增删改/目录映射需 `storage:manage`(OWNER)。文件浏览 `/storage/files`、资源管理器 `browse`/`file`、写操作 mkdir/rename/batch(均 storage:manage+@OperationLog)、目录映射 `/storage/map`——详见需求设计说明书 §4.6.2/§4.3.3。
- **硬删除策略**:照片/相册/视频/图书删除时**物理删 DB 记录+磁盘文件**(自定义 XML DELETE 绕过全局 logic-delete;`FileService.deleteByUrl` 按 URL 解析物理路径删文件,防越界);博客封面/头像/家庭封面/背景音乐/家谱照片删除时**未**连带删文件(孤儿文件,可接受)。

## 配置与加密

- **外挂配置**:`IHOMY_CONFIG_PATH` 环境变量指定 yml 路径,`ExternalConfigLoader`(EnvironmentPostProcessor)启动早期加载,最高优先级覆盖 application.yml;含 MySQL/Redis 密码、邮件 SMTP、天气四件套、JWT 密钥。**改配置前先核实环境变量实际指向**(曾指向旧路径陈旧副本;启动命令里显式设置最可靠)。模板 `backend/src/main/resources/external.yml.template`;external.yml 不入 git。
- **DB/Redis/邮件密码明文**(避免鸡生蛋:DB 未连上无法读盐值解密)。
- **业务凭证 AES-GCM 加密**:`AesUtil`(PBKDF2WithHmacSHA256 派生密钥 100000 次 + GCM 128bit tag);密文 `ENC(Base64(iv+cipher+tag))`;盐值存 `sys_parameter`(key=`aes-salt`,首启自动生成,优先环境变量 `IHOMY_AES_SALT`)。
- **天气多源(V9.53)**:`WeatherService` 门面 + `WeatherProvider` 接口(和风 `QWeatherProvider`);`sys_weather_credential` 加 provider+config_json;新增天气源三步(WeatherConst.PROVIDERS + 实现 + 前端下拉),凭证走 WeatherController。
- **首页组件(V9.53,§4.7.7)**:四档 snap 尺寸 + hover 放大推开邻居,默认 9 组件,天气 AI 生图底图,布局键 `ihomy:dashboard:layout:v2`。
- **AI 模型接入(模型池+按功能绑定)**:`sys_family_ai_model`(每家庭多条,类型 LLM/IMAGE/ASR/LOCAL,密钥 ENC)+ `sys_family_ai_feature`(每功能一行 feature_code→model_id 主+fallback 兜底,6 功能:找物/放物/对话/图片/天气生图/语音);`FamilyAiConfigService.resolveForFeature/resolveChain` 按功能解析主+兜底;物品找物/放物本地规则优先+LLM 兜底(ItemLocalParser);同义词表 `sys_synonym`+SynonymService;语音支持 OpenAI 兼容/百度短语音。**新家庭要用 AI 必须家长先加模型再按功能绑定(找物/放物可直接绑 LOCAL 离线用)**。详见需求设计说明书 §4.6.9。
- **OPS 加密接口**:`GET /api/ops/crypto/encrypt?plaintext=` 生成密文,`/decrypt?ciphertext=ENC(xxx)` 验证(均 ops:view)。
- **profile 化(废弃)**:不再用 application-dev.yml profile;application.yml 为生产基线(端口 8080/MySQL 6306/Redis 6379/DB 密码与 JWT 密钥留空必须由 external.yml 提供/`file.upload-dir /opt/ihomy/uploads`/虚拟线程/HikariCP 20/`mybatis.sql: warn`);所有环境差异统一走 external.yml 覆盖。

## 部署约定(Linux 2GB 求稳)

- **求稳方案(2GB 内存)**:MySQL 本机部署 + Redis 用 Docker。MySQL 调优后 ~180MB,本机部署无需 Docker daemon 为它常驻;Redis 轻量,Docker 化便于升级。
- **每应用一用户,权利分散**:后端 Spring Boot 以 `ihomy` 应用用户运行(systemd `User=ihomy`);MySQL 用 apt 自动创建的 `mysql` 用户;Redis 容器隔离;Nginx 用 `www-data`。
- **除关键步骤外不用 root**:装包、建用户、systemd 管理、`/etc/` 配置、防火墙、certbot、执行 schema.sql(数据库 root)需 root;代码获取/构建/编辑 application.yml 由 `ihomy` 用户操作。
- **JVM 调优**:systemd ExecStart 用 `-Xmx384m -XX:MaxMetaspaceSize=192m -XX:+UseSerialGC -Xss512k`。
- **MySQL 调优**:`config/mysql/my.cnf` → `cp` 到 `/etc/mysql/conf.d/ihomy.cnf`,关键项 `performance_schema=OFF`(省 80-100MB)。MySQL 端口 6306。
- **SSH 端口**:生产服务器 SSH 登录端口统一为 **19068**(禁止 22)。所有 ssh/scp 命令需加 `-p 19068`/`-P 19068`。防火墙放行 19068,关闭 22。
- **Docker 安装源**:Ubuntu 用阿里云镜像源(`mirrors.cloud.aliyuncs.com/docker-ce`),固定版本 29.7.0。
- **Redis 镜像**:`docker pull redis`(默认 latest)。**Git 克隆**:用 SSH 地址,ihomy 用户先生成 ed25519 key 并加到 GitHub。
- **nginx .mjs MIME(2026-09-08 坑)**:mime.types 默认无 mjs 映射,`.mjs` 服为 `application/octet-stream` 会被浏览器拒绝执行 module worker/动态 import——生产 `/etc/nginx/mime.types` 已改 `application/javascript js mjs;`,**重装/新服务器部署必须补**(否则书架 PDF 查看器/户型图 PDF 底图「加载失败」)。
- **⚠ 部署新前端后,老客户端仍在跑旧 bundle(PWA Service Worker 预缓存)**(2026-09-24 发布 V9.87 踩坑):workbox 预缓存(`workbox-precache-v2-https://ihomy.top/`)会把旧的 `index-*.js` 连同 SPA 外壳一起留在浏览器里,新构建要等 SW 更新+再访问才生效。**症状极易误判**:访问新路由(如 `/wallpaper`)被旧 bundle 的兜底路由 `/:pathMatch(.*)*` 接走重定向到 `/home`,看起来像「新路由没发布」——实际线上 dist 已是最新。**辨认方法**:页面里读当前入口脚本 hash(`document.querySelectorAll('script[src]')`)与构建产物名对比,旧的是 `index-vQqJcikP.js`(V9.86/316,224 字符)这种上一版 hash;直接看 `fetch(src).then(r=>r.text())` 是否含新路由字符串最准。**冒烟前先清**:`navigator.serviceWorker.getRegistrations()` 逐个 `unregister()` + `caches.keys()` 逐个 `caches.delete()` 再刷新,否则验收结论是错的。用户侧刷 Ctrl+Shift+R 即可。
- 详细步骤在 `docs/部署指导-Linux.md`(入库脱敏版;本地 `Linux部署指导.md` 只留凭证台账)。

## 规划事项(未实现)

> 完整清单(P1-P4)见 docs/需求设计说明书.md 第 9 章。优先级:P1 用户价值高且可行 / P2 锦上添花 / P3 结构性改动 / P4 依赖外部条件。实现新功能前先 `grep schema.sql + router/` 对照模块种子。
- **P1 放映厅 Jellyfin 集成**:方案已定稿,**启动时先重读 docs/变更归档.md「放映厅 Jellyfin 集成方案」**。
- **P2 智能家居中控(Home Assistant 集成)**:硬件协议层全归 HA,ihomy 只做数据沉淀与控制入口——S1 Paho 订阅 Mosquitto 入库 sys_iot_device/sys_iot_data+Redis 最新值、S2 HA REST 控制(long-lived token)、S3 前端中控页+物品定位户型图联动;详见 §9。
- **P2 密码管理器(家庭保险箱)**:家庭共享的账号密码保管箱,预期新增 `family_vault_item`(family_id 隔离 + AES-GCM 密文存储 + PRIVATE/FAMILY 可见范围)+ 分类/搜索/一键复制/密码生成器;明文查看/复制走 `@OperationLog` 审计 + 前端 10 秒自动清剪贴板;分期 S1 表+CRUD+加密存储、S2 用户主密码(前端零知识、忘记不可找回)、S3 明确不做浏览器扩展;详见 §9。
- **场景主题方向**:2D 沉浸场景主题(SceneHome.vue)已移除(暂缓后删除);3D 光影实验台(/tools/light-lab)作为未来场景主题底座,详见 §4.12.3/§9。

## 文档清单

- `README.md`(项目简介,GitHub 展示,不含密码); `docs/README.md`(文档索引:每份文档一句话定位+新人阅读顺序); `docs/架构设计.md`(系统上下文/请求流转/模块分域/关键机制/部署拓扑); `docs/部署指导-Linux.md` / `docs/部署指导-Windows.md`(生产部署全流程 systemd/NSSM/Nginx/Let's Encrypt/Docker Compose/备份/NAS,**脱敏入库版**,凭证一律占位符); `Linux部署指导.md`(本地,**只剩 §〇凭证台账**,两平台共用,不入 git); `docs/新人上手指南.md`(新成员环境搭建+开发账号初始密码;**本地维护不入 git**,给新成员时直接发该文件)
- `docs/需求设计说明书.md` — **完整功能需求唯一活文档**(功能模块清单+数据库设计 70 表+接口设计+规划事项+修订记录),随迭代持续更新;§4.8.1 含原户型图设计.md 并入的设计决策存档(2026-09-07,原文件已删)
- `docs/变更归档.md` — 已实现变更归档(按功能域的文件级改动表+设计决策+踩坑+live DB 同步 SQL);**开头有章节目录**,新变更追加到文件末尾
- `docs/设计想法/` — 设计稿与探询记录(主题/光尘·暖居·场景、功能/小游戏);目录索引见该目录 `README.md`,其中多数已落地为现行实现,属**历史草稿、不再维护**;**`日志/` 子目录是唯一例外**(放现行日志规范,见下两条,仍在维护)
- `docs/UI设计提示词.md` — 沉浸式首页 UI 设计完整规格(可作为 AI 提示词重新生成)
- `docs/设计想法/日志/日志规范.md` — 日志开发规范(三类文件/六要素/tid 规则/级别标准/三方调用/脱敏清单)
- `docs/设计想法/日志/日志问题分析方法.md` — 报错排查方法论(拿 tid → 详细日志页 → 四步分析;面向运维/业务人员)
- `test/cases/功能测试用例.md` — 全站功能测试用例(341 条,32+ 域,含脑图 48 条+执行记录+风险清单);测试资产统一收敛到 `test/` 目录(用例 `test/cases/`、接口自动化 `test/automation/`、UI 自动化+截图证据 `test/ui-automation/`、测试报告 `test/reports/`、测试结果 `test/results/`,总览见 `test/README.md`)
- `scripts/setup.ps1`(新人一次性环境初始化:前置软件检查+生成 config\external.yml+docker compose 起库+前端依赖,幂等)/ `start-all.ps1`(日常一键启动前后端,双击 `start.bat` 调用,设 `IHOMY_CONFIG_PATH` 环境变量)/ `restart-all.ps1`(**一键重建重启**:停旧服务→`mvnw clean package`+`npm run build`→起新并轮询端口就绪;`-SkipBuild` 只停起、秒级;与 start-all.ps1 同一套按端口反查属主的停进程逻辑)/ `start-db.ps1`(docker compose 起 MySQL+Redis,首启自动导 schema.sql,每次启动把 ihomy 账号密码对齐 external.yml,端口 6306/6379 与生产一致);根目录 `docker-compose.yml`(开发中间件定义,含健康检查,数据卷 ihomy-mysql-data); `.github/workflows/ci.yml`(GitHub Actions CI); `config/mysql/my.cnf`(端口 6306,内存优化,仅 Linux 本机部署用)
- 完整接口清单:见 `docs/需求设计说明书.md` 第 7 章。代码事实以 `backend/src/main/java` + `resources/schema.sql`(开发安全版,已入库) 为准,如需检索先 `grep` 再动手。

## 环境检查(参考)

本机已装:JDK 21、Node 20、MySQL 8、python-docx。Maven 用 Wrapper(或 temp 目录 3.9.9)。Redis 本机可能未装(可用 Docker 或 Memurai)。
