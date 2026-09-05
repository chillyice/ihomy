# AGENTS.md — ihomy 项目规则

> 本文件供 ZCode(及 opencode 等)跨会话加载,记录项目关键规则、约定与当前事实。新会话启动时会自动读取,无需重复说明背景;修改后立即对所有新会话生效。
> **分工(2026-09-04 起)**:本文件只留规则+导航索引,目标 **≤60KB 保证完整注入上下文**(超出会被截断且尾部最先丢失);完整功能需求见 `docs/需求设计说明书.md`(活文档);历史实现归档见 `docs/变更归档.md`(新归档追加到该文件,不写回本文件)。

> **⚠ Git 规定(必须遵守)**:非人工指令,不得主动提交代码(`git commit`/`git add -A`/`git push` 一律禁止)。`git add` 只能指定具体文件路径,禁止 `git add -A`/`git add .`。

> **⚠ 路径拼写警示(遵守以防误写)**:
> - 工作目录绝对路径:`C:\Users\chill\OneDrive\WorkStation\Projects\ihomy`
> - 每次读/写/移动文件前先逐字核对路径;发现读不到文件时优先怀疑路径拼写而非文件不存在。
> - **⚠ 编码警示(必须遵守)**:含中文的源码/配置/SQL 一律走本工具的 Read/Write/Edit 读写,禁止用 PowerShell `Get-Content`/`Set-Content`/`WriteAllText` 读写(PS 5.1 默认 GBK 会破坏 UTF-8 中文,且 `[IO.File]::WriteAllText` 默认带 BOM 导致 javac 报非法字符)。PowerShell 仅用于:npm/mvn 构建、HTTP 冒烟。
> - **⚠ 数据库写中文警示**:向 MySQL 写入含中文的 SQL 时,**禁止**用 PowerShell 管道 `Get-Content file.sql | docker exec -i mysql mysql ...`(PS 5.1 管道编码非 UTF-8 导致中文乱码)。**正确方式**:① 用本工具 Write 写 SQL 文件(UTF-8 无 BOM)→ `docker cp file.sql ihomy-mysql:/tmp/` → `docker exec ihomy-mysql mysql --default-character-set=utf8mb4 ihomy -e "source /tmp/file.sql"` → 清理临时文件;② 纯 ASCII SQL 可直接 `docker exec mysql -e "..."`;③ 远程用 `scp -P 19068 file.sql root@ihomy.top:/tmp/` → SSH 执行 `mysql -e "source /tmp/file.sql"`。终端显示中文为 `?` 是 GBK 终端问题,不代表存储乱码,用 `python -c "import subprocess; ..."` 验证。

## 项目概述

- **应用名**:ihomy(家庭共用软件)。家庭内部内容共享平台,PC 浏览器 / 安卓 / iOS(均 PWA)。
- **核心功能**:登录注册、博客、日记、相册、纪念日、留言板、放映厅、聊天室、积分商城、任务悬赏、提醒、家庭计划、愿望单、记账、家谱、书架、运维。首页模块化可扩展(后期新增功能只需插入一条 `sys_home_module` 记录)。
- **技术栈**:前端 Vue3 + Vite + ElementPlus + PWA(`ihomy-frontend`);后端 Spring Boot 3 + MyBatis-Plus + MySQL 8 + Redis(JWT 双 token + 验证码 + WebSocket),包 `com.ihomy`,主类 `IhomyApplication`,`ihomy-backend`。

## 工作目录

项目位于单一目录(已从双目录合并为单目录):

| 目录 | 用途 |
|------|------|
| `C:\Users\chill\OneDrive\WorkStation\Projects\ihomy` | 唯一工作目录(代码编辑 + 构建验证 + 部署文档) |

- 所有代码编辑、编译、构建验证都在此目录进行。
- 部署指导文档 `Windows部署指导.md`、`Linux部署指导.md` 也在此目录。
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
| 应用连接账号 | `ihomy`(密码默认 `Ihomy@2026`,需改) |
| Docker 容器名 | `ihomy-mysql` / `ihomy-redis` / `ihomy-backend` / `ihomy-nginx` |
| Windows 服务名 | `IhomyBackend` / `IhomyNginx` |
| 显示名 / PWA name / 页面标题 | `ihomy` |
| JWT 密钥前缀 | `ihomy-secret-key...` |

**业务概念不改(注意区分)**:`family` 表、`Family` 实体类、`family_id` 字段、`OWNER/MEMBER` 角色名 —— 这些是"家庭"业务领域概念,不是应用标识。

## 数据库约定

- **root 仅用于初始化**:`mysql -uroot -p < backend/src/main/resources/schema.sql`,执行一次(建库、建表、创建 ihomy 账号、初始数据)。
- **业务运行用 `ihomy` 账号**:仅授予 `SELECT/INSERT/UPDATE/DELETE` on `ihomy.*`(最小权限,无 CREATE/ALTER/DROP)。application.yml 连接用 `ihomy`,**不要用 root 跑业务**。
- 账号同时创建 `localhost` 和 `%` 两个 host(本机/远程应用服务器都能连)。
- **59 张表**,前缀分类:`sys_` 18 张(系统/账号/权限/配置/日志/天气/存储)、`family_` 22 张(家庭事务)、`content_` 19 张(内容数据)。**完整表清单见 `docs/需求设计说明书.md` §6.2**。
  - **命名规则**:家庭事务业务表一律 `family_` 前缀;内容数据 `content_` 前缀;账号/权限/配置/日志/天气/存储保留 `sys_`。新增表必须遵守。前缀取最顶层祖先类别;上下级关系体现在表名(如 `sys_user_role`)。
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
    entity/      # 52 个实体类(8 张关联/字典表无实体)
    mapper/      # MyBatis-Plus BaseMapper(自定义 SQL 全放 resources/mapper/*.xml,接口不写注解,参数统一 @Param)
    service/     # 43 个 @Service(单实现无接口层)
    controller/  # 32 个 Controller
    dto/         # 请求/响应 DTO
    websocket/   # ChatWebSocketHandler(原生 WebSocket 聊天室)
  src/main/resources/
    application.yml     # 端口8080 context-path=/api;生产基线配置(MySQL 6306/Redis 6379);file.upload-dir /opt/ihomy/uploads;logging.file.path /opt/ihomy/logs
    logback-spring.xml  # 三类日志分流(access/server/thirdparty,六要素 pattern,按天滚动)
    external.yml.template  # 外挂配置模板(IHOMY_CONFIG_PATH 覆盖密码/密钥/路径/captcha/天气,唯一开发生产差异机制)
    mapper/*.xml        # 每个 Mapper 一个同名 XML
    schema.sql          # 建库+建号+建表(59张)+种子
  mvnw / mvnw.cmd       # Maven Wrapper
frontend/ (Vue3 + Vite + PWA + Element Plus + Pinia)
  src/
    api/          # request.js(axios+JWT+401 自动刷新) + index.js(30 个 Api 对象)
    stores/       # user.js(登录+权限) / app.js(首页聚合)
    router/       # 登录守卫 + scrollBehavior;36 个路由(懒加载)
    i18n/ theme/  # vue-i18n 中英;明暗主题(只 light/dark)
    utils/        # dict.js / diary.js / doodle.js(涂鸦引擎) / windowLight.js / useSunLight.js / useDragResize.js
    composables/  # useDevice.js(设备检测)
    components/   # AppSidebar/BackToTop/Breadcrumb/AvatarCropper/InstallPrompt/SiteFooter/SunLightLayer/LightTestConsole/SyncDialog/Mobile*(移动端)
    layouts/MobileLayout.vue  # 移动端壳
    styles/main.css # CSS 变量 + 全局样式 + 深色模式 + EP 组件覆写 + @media
    views/        # 28 个页面(Home/Login/Member/Settings/Anniversary/album/cinema/diary/blog/points/task/reminder/plan/wish/book/chat/tree/cascade/ops/storage/item/kitchen/library)
    App.vue
  vite.config.js   # PWA + 代理 /api->8080 + manualChunks 分块 + ElementPlus 按需
```

## 构建与验证命令

后端(在 backend 目录):
```powershell
.\mvnw.cmd -B clean package -DskipTests      # 编译+打jar,产物 target/ihomy-backend.jar
.\mvnw.cmd -B clean compile -DskipTests       # 仅编译验证
.\mvnw.cmd spring-boot:run                     # 开发运行(端口8080)
```
- 有 jar 锁先 `taskkill /F /IM java.exe` 再打包(运行中 java 锁定日志文件导致 clean 失败)。日志路径:生产 `/opt/ihomy/logs`(三类子目录 access/server/thirdparty),开发 `D:\WorkSpace\ihomy\logs`(external.yml 覆盖,同样三子目录)。
- 临时 Maven(本机未装 mvn):`C:\Users\chill\AppData\Local\Temp\opencode\apache-maven-3.9.9\bin\mvn.cmd`
- JAVA_HOME:`C:\Program Files\Java\jdk-21`(JDK 21 已装)
- 运行后端必须用完整路径单实例:`C:\Program Files\Java\jdk-21\bin\java.exe -jar target\ihomy-backend.jar`(javapath launcher + JDK 双实例会分流 8080 请求导致偶发 401/404/500)。

前端(在 frontend 目录):
```powershell
npm install        # 首次
npm run dev        # 开发(端口5173,代理/api到8080)
npm run build      # 生产构建,产物 dist/,含 PWA service worker
```

冒烟:登录 `POST /api/auth/login {email, password, captchaId, captchaCode:'qwer'}`(开发环境验证码固定 `qwer`,先 `GET /api/auth/captcha` 取 id),响应 code=0 即有 token。数据库重导:整库 `schema.sql`;增量建表/改表直接执行对应 SQL 段(docker exec -i ihomy_mysql mysql -uroot -p<root密码> --default-character-set=utf8mb4)。

## 统一响应与鉴权

- 响应 `{code: 0, message: "success", data: ...}`;code != 0 = 失败。
- 登录:access token(2h)+ refresh(7d,Redis 黑名单登出失效)。请求头 `Authorization: Bearer <token>`,axios 自动续期。**约定(2026-09-01 踩坑)**:后端 `authenticationEntryPoint` 必须返回**真实 HTTP 401 状态码**(JSON 体照写)——前端 axios 只对 HTTP 401 触发 refresh 续期,只写 JSON 不设状态码(默认 200)会让续期永不执行,用户每 2h 被"未登录或登录已过期"登出;前端 `request.js` 用共享 `refreshPromise` 让并发 401 共享一次刷新后各自重放(`_retried` 防死循环)。续期时两个 token 都轮换(滑动续期),仅连续 7 天不访问才需重新登录。
- 接口前缀 `/api`;Knife4j 文档 `http://localhost:8080/api/doc.html`。
- **权限模型**:`buildTokens` 返回 `permissions` 数组 + `isOps` 标志;前端 `userStore.hasPerm(code)`/`isOps`/`isPureOps`。OWNER 恒真,其余查 `SysRoleMapper.selectAuthCodesByUserAndFamily`。
- **OPS 隔离**:`OpsAccessFilter` 只放行 OPS 到 `/api/ops/**`+`/api/auth/**`,其余 403;非 OPS 访问 /ops/** 一律 403;支持复合角色(OWNER+OPS)访问 `/api/ops/**`(查 OPS 角色绑定+5 分钟缓存)。
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
| 基础 | 文件上传 / 存储管理 / 首页聚合 / 运维 / 每日内容 / 操作日志 / 系统参数 | File / Storage / Home+Public / Ops / Daily / Log 各 Controller |
| 光影 | 太阳位置/体积光/台灯/天气 / 天气代理 / 天气详情 / 首页仪表盘 | SolarUtil+SunService + windowLight.js + SunLightLayer.vue |
| 物品 | 物品定位+户型图 | ItemController / ItemService(详见 docs/户型图设计.md) |
| 厨房 | 菜单/菜谱/食材 | RecipeController / RecipeService |
| 系统 | i18n / 主题 / 字典 | i18n/ + theme/ + utils/dict.js |
| 移动端 | 设备自适应 | useDevice.js + MobileLayout.vue + Mobile* 组件 |

**关键坑速查**(实现细节,详见 docs):日记 date 兼容 `yyyy-MM-dd HH:mm`;纪念日 Hutool ChineseDate 月份 0-based 需 +1;家谱 null 字段须 `LambdaUpdateWrapper` 显式 SET;相册分享 token + Knuth 混淆;博客新建家庭注入 9 默认分类;物品户型图 2 期完成(裁剪/粘合删原房保持最后防家具入库覆盖、端点识别/字号/光标屏幕恒定;**hover 边加号阈值 6px**——原 12px 太大导致 hover 显示但 click 被 `justDragged` 拦截;库内家具拖入画布替代「摆放」+「请选择房间」按钮)、3 期 AI 语义待做。

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
10. **缓存规范**(强制):
    - **缓存键**:`ihomy:{domain}:{id}`(如 `ihomy:user:1`、`ihomy:perms:1:1`、`ihomy:home:pub:1`)。
    - **短 TTL**:用户实体/权限码 5min;公开首页聚合 5min;天气/太阳位置按业务定。
    - **变更点必须显式 invalidate**:`ProfileController.update` → `invalidateUser`;`MemberController.setRole/remove` → `invalidatePerms`;`AuthService.switchFamily` → `invalidatePerms` + `invalidateUser`;`AuthService.joinFamily` → `invalidatePerms`;模块/照片变更 → `PublicController.invalidateHomeCache`。
    - **不变数据走内存缓存**:`sys_home_module` 全局模块 `@PostConstruct` 预热 + **懒加载兜底**(`globalLoaded` 双检锁——2026-09-01 生产启动 mapper 偶发未就绪致全局模块永久为空、导航只剩设置/运维;预热失败不再致命,首次查询自动加载),家庭模块按 familyId 缓存 `ConcurrentHashMap`,变更时 evict。**不引 Caffeine 等库**(数据量小,内存够用)。
    - **敏感数据不缓存**:成员视图的 `/public/home`(含 stats/photos)不缓存,只缓存非成员视图。
11. **UPDATE 不先 select**(强制):回写冗余字段(如 `like_count`)用 `LambdaUpdateWrapper.eq(id).set(field, value).update(null)`,不要 `selectById` 再 `updateById`(省一次查询)。参考 `ContentLikeService.syncCount`。
12. **文件上传流式**(强制):大文件(>1MB)禁止 `file.getBytes()` 全量入堆(生产 `-Xmx384m` 上传 200MB 即 OOM)。**用 `MultipartFile` 重载 + `transferTo` + `Files.copy` 兜底**。FileService 已提供 4 个流式重载(`upload`/`uploadVideo`/`uploadBook` 通用+图片+视频+电子书),Controller 必须传 `MultipartFile` 不调 `getBytes()`。
13. **JVM/连接池配置**(基线):`spring.threads.virtual.enabled: true`(JDK21 虚拟线程,Tomcat 自动用);HikariCP `maximum-pool-size: 20` + `minimum-idle: 5` + `connection-timeout: 3000`。
14. **日志规范**(强制,详见 `docs/日志规范.md`):
    - **三类文件**:access(客户端调接口,`AccessLogFilter` 自动)/server(内部流程+SQL+ERROR 堆栈,业务代码 `log.xxx()` 自动)/thirdparty(三方出站,`ThirdPartyHttp` 统一封装);按天滚动保留 `app.log-retention-days`(默认 7)天。
    - **六要素**:时间/级别/线程/[tid]/位置(logger)/内容;消息体覆盖 谁/做什么/入参/结果/耗时。
    - **tid 贯穿**:HTTP 自动(`TraceIdFilter`→MDC);@Async 自动(`AsyncConfig` MDC TaskDecorator);WS 每消息独立 tid;自管线程池必须配同款 TaskDecorator。
    - **三方调用一律走 `ThirdPartyHttp.get()`**(自动 thirdparty 日志+URL/头脱敏);流式下载参考 `StorageService.baiduOpen` 手动打 `Loggers.thirdParty()`。
    - **报错必须带堆栈**:`log.error("xx, param={}", p, e)`(e 恒为最后一个参数);禁止 `e.printStackTrace()` 和只打 `e.getMessage()`。
    - **级别**:ERROR=需人工介入(带堆栈)/WARN=可自动恢复需关注/INFO=关键业务节点/DEBUG=细节(SQL 恒 DEBUG 只进 server 文件)。
    - **业务操作日志双写**(强制):`@OperationLog` 切面在落库同时输出 server 日志一行 `[操作] MODULE.TYPE 描述 用户=xx#N 结果=SUCCESS/FAILED 耗时=Nms`——**所有写接口(POST/PUT/DELETE)必须加 @OperationLog**(module 大写/operationType 用 CREATE/UPDATE/DELETE 等标准词/description 中文短句;高频噪音端点如 token 刷新、已读标记除外),业务代码无需再手动打关键节点 INFO。
    - **新敏感字段进 `AccessLogFilter.SENSITIVE_JSON` 打码清单**(password/token/captcha 等)。
    - **运维「详细日志」**:`GET /ops/logs/trace?tid=` 按tid扫三类文件;操作日志 TID 列可点;前端 5xx 报错 toast 自带 `[tid:xxx]`。排查方法论见 `docs/日志问题分析方法.md`。

### 前端规范

1. **API 分组**:`api/index.js` 按模块导出 `xxxApi` 对象;统一走 `api/request.js`(axios+JWT+401 自动刷新)。
2. **状态管理**:Pinia;`stores/user.js`(登录+权限)、`stores/app.js`(首页聚合)。
3. **路由守卫**:`meta.public` 无需登录;`meta.ops` 需 `ops:view`;纯 OPS 账号只能访问 `/ops`。
4. **样式**:CSS 变量(`main.css`)+ 深色模式 `html.dark` 覆写;**不显式声明 serif 字体**,继承 body sans-serif。
5. **图标**:Element Plus `el-icon`(线性图标);**Setting/Monitor 图标用内联 SVG 替代**(复杂 path 在 100% 缩放触发子像素光栅化开销,见性能优化博客 id=18)。
6. **动画**:GSAP 入场;`transform: translateZ(0)` 隔离合成层;`contain: layout style` 隔离布局;避免 `background-attachment: fixed`(性能杀手)。
7. **毛玻璃**:`backdrop-filter: blur(24px) saturate(1.1)`;子元素 hover 用 `transform` 而非 `box-shadow`(避免触发 backdrop-filter 重算)。
8. **可拖拽面板**:`useDragResize` 组合式函数;5 个面板各自实例;位置/大小持久化 localStorage;**事件监听器按需挂载**(`onDragStart`/`onResizeStart` 时挂 `mousemove`/`mouseup`,`onMouseUp` 时移除,不要 `onMounted` 常驻——参考 `AvatarCropper.vue` 的写法)。
9. **光影层全局化**:`SunLightLayer` + `AppSidebar` + `SiteFooter` 在 `App.vue` 全局挂载;`useSunLight` provide/inject 共享状态。
10. **i18n**:所有用户可见文本用 `$t('key')`;中英双语;`utils/dict.js` 枚举映射。
11. **打包分块**(强制):`vite.config.js` 必须配 `build.rollupOptions.output.manualChunks` 拆分大 vendor(当前 `element-plus`/`gsap`/`vue-i18n`/`epubjs` 四块)。**public/ 下静态资源不得与 npm 包重复**(已删 `public/qweather-icons/`,改走 `node_modules/qweather-icons/font/`)。
12. **重型资源异步加载**(强制):字体包/CSS(如 `qweather-icons.css` 44.9KB)阻塞首屏的,必须 `import('...')` 异步加载,不要同步 `import`。
13. **动画优先级**(强制):持续型动画(钟摆/心跳/呼吸)优先级 **CSS `@keyframes` > GSAP 直接操作 DOM ref > `requestAnimationFrame` + 响应式 ref**。**禁止用 rAF 每帧写 Vue ref 触发响应式重渲染**(参考 `useSunLight.js` 钟摆已改 CSS `@keyframes lampSwing`)。
14. **并行请求**(强制):多个独立的 `await xxxApi.foo()` 必须改 `Promise.all([a, b, c])` 并行(参考 `Home.vue loadAll` + `stores/app.js init`)。串行只在真有依赖时用。
15. **computed 纯函数**(强制):`computed` 内禁止 `Math.random()`/`Date.now()`/副作用,否则每次访问重算且视觉跳动。需要随机/一次性计算用 `ref` + `watch(source, immediate)` 生成(参考 `Home.vue polaroidLayout`)。
16. **路由懒加载**:27 个路由全部 `() => import('./views/...')`,不写同步 `import Home from '@/views/Home.vue'`。
17. **全局 UI 样式统一**(强制):`el-dialog`/`ElMessageBox`/`ElMessage`/`el-popper`/`el-button`/`el-tag`/`el-badge`/`el-input` 及所有 EP 组件的配色、圆角、尺寸、z-index 一律由 `main.css` 全局覆写,**禁止在组件 scoped 内重复定义**。完整样式值(弹窗四档尺寸/遮罩/输入框/Toast 四色/完整 z-index 链)见 `docs/UI设计提示词.md` §11a 与 §3。
18. **按钮/标签/角标/图标/圆角统一**(强制,`main.css` 全局覆写,禁止 scoped 重复定义):按钮四类(主/次/幽灵/危险,浅色与深色**完全不同色值、不共用**)、`el-tag` 半透明磨砂、`el-badge` 半透明黑、`el-icon` `stroke-width:2px`、圆角统一(button 12px / input 10px / card+dialog 14px)。**完整色值见 `docs/UI设计提示词.md` §18a**。
19. **页面统一规范**(强制,所有功能页遵守):根容器 `class="page"`(禁止 scoped 覆写 max-width/margin/padding);页面级 H1/H2 移除(面包屑已体现标题),分区标题用 `.section-label`;工具栏统一 `class="page-toolbar card"`(`.tb-left` 筛选组件 `size="small"`,`tb-right` 操作按钮 `gap:8px`,下拉包裹需补 `:deep(.el-dropdown){margin-left:12px}`);多选交互统一 `.pick-badge` 对勾圆标 + 卡片描边(**禁左上 checkbox 角标**);设备映射来源角标 `设备名 + .status-dot`。详见 `docs/UI设计提示词.md` §11b。

### 性能规范(强制规则)

> **已踩坑清单**(100% 缩放卡顿 / backdrop-filter 滚动炸弹 / rAF 写 Vue ref / 常驻事件监听器 / 同步 import 阻塞首屏 / 入口 chunk 过大 / `getBytes()` OOM / SQL 日志同步 I/O / N+1)详见 `docs/变更归档.md`「性能优化」与 `docs/UI设计提示词.md` §19。由此固化的强制规则:

- **动画优先级**:持续型动画 CSS `@keyframes` > GSAP 直接操作 DOM ref > `requestAnimationFrame`;**禁止 rAF 每帧写 Vue ref**(见前端规范 13)。
- **事件监听器按需挂载**:`onDragStart`/`onResizeStart` 时挂 `mousemove`/`mouseup`,`onMouseUp` 时移除,不要 `onMounted` 常驻。
- **重型资源异步加载**:字体包/大 CSS(如 `qweather-icons.css`)必须 `import('...')` 异步,不要同步 `import`。
- **入口 chunk 分块**:`vite.config.js` 必须配 `manualChunks`(element-plus/gsap/vue-i18n/epubjs)。
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
- **ElMessageBox 动画未生效**:CSS 覆写写法正确但运行时未生效,疑 EP `Transition` persisted 模式所致,待 DevTools 排查。

#### 验证基线

- 后端编译:`cd backend; .\mvnw.cmd -B clean compile -DskipTests` → BUILD SUCCESS
- 前端构建:`cd frontend; npm run build` → 入口 chunk 208KB(基线 2026-08-31 实测;原 158KB 记录系 V8.0 移动端组件并入入口后过时)
- 接口测试:`cd autotest_framework; .venv\Scripts\python.exe -m pytest -m api` → 37 passed

## 已实现变更归档(已外置)

> 历史归档(86KB,30 个功能域子节:性能优化/首页仪表盘/音乐×2/光影/UI 规范/厨房/运维/UX/图书/移动端×2/播放器/博客/日记/相册×5/百度凭证/放映厅×2/生产发布/频闪排查/天气/日志追溯/户型图)已整体迁至 **`docs/变更归档.md`**,内容原样保留。含文件级改动表、设计决策、踩坑记录与 live DB 同步 SQL。
> **检索历史实现/设计决策/live DB 迁移 SQL 时读该文件;新的变更归档继续追加到该文件末尾**(新增 `#####` 子节),不要再写回 AGENTS.md。

## 文件存储策略

- **当前阶段(开发期)**:本地磁盘存储(`file.upload-dir`),零成本零内存,FileService 已实现,开箱即用。Nginx `/files/` 托管静态目录(注意负向断言正则 `location ~* ^/(?!files/).+\.(...)$` 排除 /files/)。
  - **路径配置**:`application.yml` 的 `file.upload-dir` 基线为生产路径 `/opt/ihomy/uploads`(Linux);开发环境通过 external.yml 覆盖为 Windows 路径 `D:\WorkSpace\ihomy\uploads`。DB 存的是相对 `/files/` 的完整 URL,与物理根无关,改路径只需改 yml + 移动 uploads 目录。
- **未来对接 NAS**:优先 NFS 挂载方案(把 NAS 共享目录挂到 `/opt/ihomy/uploads`,**代码零改动**)。前提是 NAS 与服务器同内网。详细步骤见 Linux 部署指导附录"对接 NAS 存储"。若 NAS 异地或要公网 CDN:再改 FileService 用 S3 兼容 SDK(NAS/MinIO/OSS 通用),用 `@ConditionalOnProperty` 切换实现,本地实现保留为默认。
- **不要主动改 FileService 的存储实现**,除非用户明确要求接 NAS/OSS。当前本地实现满足需求。
- **统一目录结构(分类目录,无 upload 中间层)**:上传按类型分目录——相册图片→`pictures/{相册名}/{相册ID}_{时间戳}_{文件名}`、视频与海报→`videos/`、音乐(audio/*)→`music/`、电子书→`books/{yyyyMM}/`、通用/头像→`files/{yyyyMM}/`。FileService 提供流式重载(`upload(MultipartFile...)`/`upload(Path,...)` 图片带相册名、`uploadVideo`、`uploadBook`);无相册名时图片平铺到 `pictures/`。DB 存 `/files/...` 完整 URL,与物理根解耦。
- **存储设备**:`sys_storage_device`(family_id 家庭级隔离,name/device_type SYSTEM|NAS|REMOTE|MOUNT|BAIDU/root_path/status/created_by)。`GET /storage/device/list` 首项恒为系统设备(id=0,type=SYSTEM);设备增删改/目录映射需 `@RequirePermission("storage:manage")`(OWNER)。**设备归属=家庭级独立配置**,互不可见。百度网盘已接入(凭证四件套/OAuth/xpan 浏览/dlink 中转,详见需求设计说明书 4.6.2);WebDAV/OSS/S3 暂缓。
- **资源管理器**:`GET /storage/browse?deviceId&path`(响应带 fsId)+ `GET /storage/file?deviceId&path&download`(流式中转:本地 PathResource 支持 Range/百度 InputStreamResource)。`StorageService.resolveSafe` 用 `normalize()+startsWith` 防路径遍历(越界返回 400,已实测)。
- **设备目录映射(替代旧"一键同步",旧 StorageSyncRunner 已退役)**:`POST /storage/map {deviceId, paths}` 勾选目录异步建层级相册+影子照片(不拷贝文件);进度 `GET /storage/sync/progress/{taskId}`(MapTaskRegistry 相册/视频共用);映射规则/签名 URL/缩略图缓存见需求设计说明书 4.3.3 与 4.6.2。
- **硬删除策略**:删除照片/相册/视频/图书时**物理删除 DB 记录 + 删除磁盘文件**。`FileService.deleteByUrl(url)` 按 `/files/` URL 解析物理路径删文件(外链/空跳过,失败仅告警,带 `normalize()+startsWith` 防越界,顺带删缩略图、尝试清空父目录)。照片删除走 `PhotoMapper.deletePhysicalById`(XML 物理删,绕过全局 logic-delete);相册删除连带照片记录+文件全删(`deletePhysicalByAlbumId`);视频删除**从软删改为硬删** `deletePhysicalById`,并删 `video_url`+`poster`;图书删除硬删 `ContentBookMapper.deletePhysicalById`,并删 `file_url`+`cover_url`;映射的影子照片/视频/曲目物理删但**设备文件永不动**。**关键坑**:MyBatis-Plus 全局配 `logic-delete-field: deleted`(`application.yml`),`deleteById` 实为 UPDATE 软删——要物理删必须用自定义 XML `DELETE` 语句。**覆盖范围**:照片/相册/视频/图书四处;博客封面、头像、家庭封面、背景音乐、家谱照片删除时**未**连带删文件(文件成孤儿,可接受,后续按需扩展)。

## 配置与加密

- **外挂配置**:`IHOMY_CONFIG_PATH` 环境变量指定 yml 路径,`ExternalConfigLoader`(EnvironmentPostProcessor)在 Spring Boot 启动早期加载,注入到 Environment 最高优先级(覆盖 application.yml + profile yml)。含:MySQL 密码、Redis 密码、邮件 SMTP、天气四件套。
- **DB/Redis/邮件密码明文**:避免鸡生蛋(DB 未连上无法读盐值解密)。
- **业务凭证(天气私钥)AES-GCM 加密**:`AesUtil`(PBKDF2WithHmacSHA256 派生密钥 100000 次 + 256bit + GCM 128bit tag);密文格式 `ENC(Base64(iv+cipher+tag))`;盐值 16 字节 Base64。
- **盐值存 DB**:`sys_parameter` 表(name/value),盐值 key=`aes-salt`,首次启动 `ParameterService.getAesSalt()` 自动生成并入库(优先环境变量 `IHOMY_AES_SALT`),之后缓存内存。
- **WeatherService 改造**:`loadCredential()` 读到的私钥若 `ENC(...)` 包裹,调 `parameterService.decrypt()` 解密;DB 和 yml 两条路径都支持。
- **OPS 加密接口**:`GET /api/ops/crypto/encrypt?plaintext=xxx` 生成密文,`GET /api/ops/crypto/decrypt?ciphertext=ENC(xxx)` 验证解密(均 @RequirePermission("ops:view"))。
- **外挂模板**:`backend/src/main/resources/external.yml.template`(复制为 external.yml 填真实凭证,设环境变量)。
- **profile 化(废弃)**:**不再用 application-dev.yml profile**(见 `scripts/start-all.ps1:9`)。`application.yml` 为**生产基线配置**(MySQL 6306/Redis 6379/密码 `Ihomy@2026` 占位/captcha 空/天气留空/`file.upload-dir: /opt/ihomy/uploads` Linux 路径/`spring.threads.virtual.enabled: true` 虚拟线程/HikariCP `maximum-pool-size: 20`/`mybatis.sql: warn` 静默 SQL 日志);**所有环境差异**(开发密码/Windows 路径/captcha=qwer/天气凭证/JWT 密钥/Redis 密码)统一走 `IHOMY_CONFIG_PATH` 指向的 external.yml 覆盖。external.yml 不入 git,手动维护,生产部署时也可用 external.yml 注入真实 secrets(密码/密钥)。

## 部署约定(Linux 2GB 求稳)

- **求稳方案(2GB 内存)**:MySQL 本机部署 + Redis 用 Docker。MySQL 调优后 ~180MB,本机部署无需 Docker daemon 为它常驻;Redis 轻量,Docker 化便于升级。
- **每应用一用户,权利分散**:后端 Spring Boot 以 `ihomy` 应用用户运行(systemd `User=ihomy`);MySQL 用 apt 自动创建的 `mysql` 用户;Redis 容器隔离;Nginx 用 `www-data`。
- **除关键步骤外不用 root**:装包、建用户、systemd 管理、`/etc/` 配置、防火墙、certbot、执行 schema.sql(数据库 root)需 root;代码获取/构建/编辑 application.yml 由 `ihomy` 用户操作。
- **JVM 调优**:systemd ExecStart 用 `-Xmx384m -XX:MaxMetaspaceSize=192m -XX:+UseSerialGC -Xss512k`。
- **MySQL 调优**:`config/mysql/my.cnf` → `cp` 到 `/etc/mysql/conf.d/ihomy.cnf`,关键项 `performance_schema=OFF`(省 80-100MB)。MySQL 端口 6306。
- **SSH 端口**:生产服务器 SSH 登录端口统一为 **19068**(禁止 22)。所有 ssh/scp 命令需加 `-p 19068`/`-P 19068`。防火墙放行 19068,关闭 22。
- **Docker 安装源**:Ubuntu 用阿里云镜像源(`mirrors.cloud.aliyuncs.com/docker-ce`),固定版本 29.7.0。
- **Redis 镜像**:`docker pull redis`(默认 latest)。**Git 克隆**:用 SSH 地址,ihomy 用户先生成 ed25519 key 并加到 GitHub。
- 详细步骤在 `Linux部署指导.md`。

## 规划事项(未实现)

> 完整规划清单(P1-P4)见 `docs/需求设计说明书.md` 第 9 章「规划事项」,此处只留最需注意的两条:
- **P1 放映厅 Jellyfin 集成**:方案已定稿,详见 docs/变更归档.md「放映厅 Jellyfin 集成方案」;**启动时先重读该归档小节**。
- **P3 物品定位-AI 语义**:3 期,依赖 AI API(决策已定,待 API 接入)。
- 优先级:P1 用户价值高且可行 / P2 锦上添花 / P3 结构性改动 / P4 依赖外部条件。实现新功能前先 `grep schema.sql + router/` 对照模块种子。

## 文档清单

- `README.md`(启动说明 + Windows 一键启动脚本用法); `Windows部署指导.md` / `Linux部署指导.md`(生产部署 NSSM/systemd/Nginx/Let's Encrypt/Docker Compose)
- `docs/需求设计说明书.md` — **完整功能需求唯一活文档**(功能模块清单+数据库设计 59 表+接口设计+规划事项+修订记录),随迭代持续更新
- `docs/需求规格说明书.docx` — 需求文档历史归档(V9.16 起冻结,由上方 .md 接棒,不再更新)
- `docs/变更归档.md` — 已实现变更归档(按功能域的文件级改动表+设计决策+踩坑+live DB 同步 SQL);新变更归档追加到该文件末尾
- `docs/UI设计提示词.md` — 沉浸式首页 UI 设计完整规格(可作为 AI 提示词重新生成)
- `docs/日志规范.md` — 日志开发规范(三类文件/六要素/tid 规则/级别标准/三方调用/脱敏清单)
- `docs/日志问题分析方法.md` — 报错排查方法论(拿 tid → 详细日志页 → 四步分析;面向运维/业务人员)
- `docs/户型图设计.md` — 物品定位-户型图(2期)完整设计(数据模型/家具随房间移动规则/编辑手势/吸附/尺寸标注/分期 S1-S5)+文末实现记录
- `scripts/start-all.ps1`(Windows 一键启动前后端,双击 `start.bat` 调用,设 `IHOMY_CONFIG_PATH` 环境变量)/ `start-db.ps1`(Docker 拉起 MySQL+Redis+自动导 schema.sql,端口 6306/6379,与生产一致); `config/mysql/my.cnf`(端口 6306,内存优化,仅 Linux 本机部署用)
- 完整接口清单:见 `docs/需求设计说明书.md` 第 7 章。代码事实以 `backend/src/main/java` + `resources/schema.sql` 为准,如需检索先 `grep` 再动手。

## 环境检查(参考)

本机已装:JDK 21、Node 20、MySQL 8、python-docx。Maven 用 Wrapper(或 temp 目录 3.9.9)。Redis 本机可能未装(可用 Docker 或 Memurai)。
