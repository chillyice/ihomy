# AGENTS.md — ihomy 项目规则

> 本文件供 opencode 跨会话加载,记录项目关键决策与约定。新会话启动时会自动读取,无需重复说明背景。
> 修改本文件后立即对所有新会话生效。

> **⚠ Git 规定(必须遵守)**:非人工指令,不得主动提交代码(`git commit`/`git add -A`/`git push` 一律禁止)。`git add` 只能指定具体文件路径,禁止 `git add -A`/`git add .`。

> **⚠ 路径拼写警示(遵守以防误写)**:
> - 工作目录绝对路径:`C:\Users\chill\OneDrive\WorkStation\Projects\ihomy`
> - 中间段是 **`WorkStation`(一个词,`W-o-r-k-S-t-a-t-i-o-n`)**,不是 `Work\Station`、不是 `WorkStudio`、也不是 `Work Station`。
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
- **53 张表**,前缀分类:
  - `sys_`(系统/账号/权限/家庭设置/日志/参数/字典/天气/存储,17 张):`sys_user` / `sys_role` / `sys_auth` / `sys_user_role` / `sys_role_auth` / `sys_family_info` / `sys_home_module` / `sys_password_reset_token` / `sys_user_group` / `sys_user_group_member` / `sys_operation_log` / `sys_dict_item` / `sys_parameter` / `sys_storage_device` / `sys_weather_credential` / `sys_weather_location` / `sys_weather_log`
  - `family_`(家庭事务,21 张):`family_anniversary` / `family_notification` / `family_apply` / `family_invitation_code` / `family_checkin` / `family_points_record` / `family_points_product` / `family_points_order` / `family_task` / `family_reminder` / `family_plan` / `family_plan_task` / `family_book_record` / `family_chat_message` / `family_chat_read` / `family_user_label` / `family_tree` / `family_house` / `family_room` / `family_furniture` / `family_item`
  - `content_`(内容类,15 张):`content_blog` / `content_diary` / `content_album` / `content_photo` / `content_comment` / `content_visibility` / `content_like` / `content_video` / `content_video_wish` / `content_wish` / `content_music` / `content_music_playlist` / `content_music_playlist_track` / `content_book` / `content_book_borrow`
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
    IhomyApplication.java       # 主类 @MapperScan("com.ihomy.mapper")
    common/      # Result统一响应/ResultCode/BizException/GlobalExceptionHandler/DictConst(字典常量)/SolarUtil(NOAA太阳位置算法)/AesUtil(凭证加密)/UserNames
    config/      # SecurityConfig/CorsConfig/MybatisPlusConfig/Knife4jConfig/WebMvcConfig/WebSocketConfig/SqlStatementLog/ExternalConfigLoader(外挂配置加载)/WsHandshakeInterceptor(WebSocket JWT 验证)
    security/    # JwtUtils(JWT含familyId+role+permissions+isOps)/JwtAuthenticationFilter/LoginUser/SecurityHelper/OpsAccessFilter
    annotation/  # @RequirePermission / @OperationLog
    aspect/      # RequirePermissionAspect(权限AOP) / OperationLogAspect(操作日志AOP)
    filter/      # TraceIdFilter(链路ID生成,写入 MDC + 响应头 X-Trace-Id)
    entity/      # 44 个实体类(7 张关联/字典表无实体:sys_auth/sys_role_auth/sys_user_group/sys_user_group_member/sys_password_reset_token/sys_dict_item/content_visibility)
    mapper/      # MyBatis-Plus BaseMapper 接口(自定义 SQL 全部放 resources/mapper/*.xml,接口不写 @Select/@Update 注解,参数统一 @Param)
    service/     # 33 个 @Service 类(单实现无接口层)
    controller/  # 31 个 Controller
    dto/         # 请求/响应 DTO
    websocket/   # ChatWebSocketHandler(原生 WebSocket 聊天室)
  src/main/resources/
    application.yml     # 端口8080, context-path=/api, 连接用 ihomy 账号; mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml; **基线配置**(MySQL 6306/Redis 6379/captcha 空/天气留空);`file.upload-dir` 基线 `/opt/ihomy/uploads`(Linux),开发通过 external.yml 覆盖为 Windows 路径;`logging.file.name` 基线 `/opt/ihomy/logs/ihomy.log`,开发覆盖为 `D:\WorkSpace\ihomy\logs\ihomy.log`
    external.yml.template  # 外挂配置模板(IHOMY_CONFIG_PATH 指定路径,覆盖 MySQL/Redis 密码 + JWT 密钥 + 上传路径 + captcha + 天气凭证,ENC() 加密)—— 唯一的开发/生产差异机制,**不再用 application-dev.yml profile**(见 scripts/start-all.ps1)
    mapper/*.xml        # 每个 Mapper 接口一个同名 XML(namespace=接口全限定名)
    schema.sql          # 建库+建号+建表(53张)+种子数据
  mvnw / mvnw.cmd       # Maven Wrapper,无需单独装 Maven
frontend/ (Vue3 + Vite + PWA + Element Plus + Pinia)
  src/
    api/request.js  # axios + JWT header + 401 自动刷新token
    api/index.js    # 全部模块 API 分组导出(29 个 Api 对象:public/auth/home/blog/diary/file/member/anniversary/album/photo/like/comment/notification/family/profile/video/points/task/reminder/plan/wish/music/book/ops/tree/chat/storage/item/kitchen/library)
    stores/user.js  # 登录状态 + hasPerm/isOps/isPureOps; stores/app.js 首页聚合(family/modules/photos/stats)
    router/         # 登录守卫 + scrollBehavior(返回回顶部);含 /ops 运维护卫, /chat 需登录; 27 个路由
    i18n/           # vue-i18n 中英(applyLocale 切换)
    theme/          # applyTheme/loadTheme(明暗模式,只 light/dark)
    utils/dict.js   # 枚举词条中文映射(与后端 DictConst 对应)
    utils/windowLight.js  # getSunScene(sunInfo,slotIndex)+currentSlotIndex()+makeRays():体积光调色板/光束/阴影参数;windowAngle(窗角)+hasDirectLight 门控
    utils/useSunLight.js  # 全局光影状态(provide/inject):sunScene/lampMode/shadowEnabled/weatherEffectEnabled/blobsEnabled/lightTestMode/testSpeed/loadWeather
    utils/useDragResize.js # 可拖拽面板组合式函数(zIndex+bringToFront+边界clamp+localStorage持久化)
    composables/useDevice.js  # 设备检测(UA+matchMedia 768px,全局单例 isMobile ref,matchMedia change 监听)
    components/     # AppSidebar(全局导航)/BackToTop/Breadcrumb/AvatarCropper/InstallPrompt/SiteFooter(备案号)/SunLightLayer(全局光影层)/LightTestConsole(光照测试控制台)/SyncDialog(存储同步进度)/MobileTabBar/MobileHeader/MobileHomeFeed/MobileMoreGrid/MobileMePage(移动端组件)
    layouts/MobileLayout.vue  # 移动端壳:首页三Tab模式 / 子页面返回栏模式
    styles/main.css # CSS 变量 + 全局样式 + 深色模式覆写 + ElMessage/ElNotification 增强 + 移动端 @media 适配
    views/          # 27 个页面:Home(沉浸式首页)/Login/Member/Settings/Anniversary/album(Album/AlbumDetail)/cinema/Cinema/diary/DiaryList/blog(BlogList/BlogDetail/BlogEdit)/points/Points/task/Task/reminder/Reminder/plan/Plan/wish/Wish/book/Book/chat/Chat/tree/Tree/cascade/Cascade/ops/Ops/storage/Storage/item/Item/kitchen/Kitchen/library/LibraryList/LibraryDetail/LibraryEdit
    App.vue
  vite.config.js   # PWA + 代理 /api -> :8080 + ElementPlus 按需
  public/favicon.svg
  public/qweather-icons/  # 和风天气字体图标(woff2/woff/ttf + CSS)
```

## 构建与验证命令

后端(在 backend 目录):
```powershell
.\mvnw.cmd -B clean package -DskipTests      # 编译+打jar,产物 target/ihomy-backend.jar
.\mvnw.cmd -B clean compile -DskipTests       # 仅编译验证
.\mvnw.cmd spring-boot:run                     # 开发运行(端口8080)
```
- 有 jar 锁先 `taskkill /F /IM java.exe` 再打包(运行中 java 锁定日志文件导致 clean 失败)。日志路径:生产 `/opt/ihomy/logs/ihomy.log`,开发 `D:\WorkSpace\ihomy\logs\ihomy.log`(external.yml 覆盖)。
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
- 登录:access token(2h)+ refresh(7d,Redis 黑名单登出失效)。请求头 `Authorization: Bearer <token>`,axios 自动续期。
- 接口前缀 `/api`;Knife4j 文档 `http://localhost:8080/api/doc.html`。
- **权限模型**:`buildTokens` 返回 `permissions` 数组 + `isOps` 标志;前端 `userStore.hasPerm(code)`/`isOps`/`isPureOps`。OWNER 恒真,其余查 `SysRoleMapper.selectAuthCodesByUserAndFamily`。
- **OPS 隔离**:`OpsAccessFilter` 只放行 OPS 到 `/api/ops/**`+`/api/auth/**`,其余 403;非 OPS 访问 /ops/** 一律 403;支持复合角色(OWNER+OPS)访问 `/api/ops/**`(查 OPS 角色绑定+5 分钟缓存)。
- 点赞/评论/通知严格同家庭:`validateTarget` 校验内容 family_id 与用户一致,跨家庭返回 NOT_FOUND。

## 功能模块清单(按业务域归档)

> 完整功能详情见 `docs/需求规格说明书.docx`。代码事实以 `backend/src/main/java` + `resources/schema.sql` 为准,如需检索先 `grep` 再动手。

### 1. 认证与账号

| 模块 | Controller | Service | 关键表 | 要点 |
|------|-----------|---------|--------|------|
| 注册/登录 | AuthController | AuthService | sys_user/sys_user_role/sys_family_info | 邮箱=账号(uk_email 唯一);注册必填邮箱+密码+确认密码+图形验证码;注册可带邀请码加入家庭或创建新家庭(新家庭默认私有 is_public=0);注册成功不自动登录跳登录页;**邀请码状态校验:`status==INVITE_UNUSED` 才放行**(曾误写 INVITE_USED) |
| 图形验证码 | AuthController | CaptchaService | Redis captcha:{id} | `GET /auth/captcha` 返回 {captchaId,image(base64)};5 分钟 TTL,一次性校验(错误/重用 1006);开发环境固定 `qwer`(`app.captcha-fixed-code`) |
| JWT 双 token | JwtUtils | — | Redis 黑名单 | access 2h + refresh 7d;JWT claim 含 userId/username/role/familyId;refresh 时按优先级解析当前家庭(Redis>default_family_id>family_id) |
| 个人资料 | ProfileController | — | sys_user | `GET/PUT /profile`(nickname/avatar/birthday/gender);`GET/PUT/DELETE /profile/label`(身份标签) |
| 密码找回 | AuthController | AuthService | sys_password_reset_token | 邮箱自助找回,token 30 分钟过期 |
| 运维账号 | AuthController | AuthService | sys_user_role(family_id 可空) | `ops / ops@ihomy.local` 初始密码 admin123;OPS 角色绑定 family_id=NULL;`buildTokens` 返回 `isOps` 标志 |
| 演示(访客)账号 | AuthController | AuthService | sys_user | `demo@ihomy.local` / `demo2@ihomy.local` / `demo3@ihomy.local` 密码均为 `guest123`;演示家庭 OWNER/MEMBER/CHILD;`is_fake=0`(可登录);登录页默认预填 `demo@ihomy.local` / `guest123` / 验证码 `qwer` |

### 2. 家庭与成员

| 模块 | Controller | Service | 关键表 | 要点 |
|------|-----------|---------|--------|------|
| 家庭管理 | FamilyController | MultiFamilyService | sys_family_info | `GET/PUT /family`(@family:manage);`POST /family` 创建新家庭(绑定 OWNER+切换当前家庭);`share_token` 16 位随机(注册时生成);`is_demo` 演示家庭标记;`music_url/music_title` 背景音乐;`weather_lat/weather_lng/weather_city` 天气地区偏好(空=IP 自动定位);`is_public` 访客公开开关 |
| 多家庭切换 | AuthController | MultiFamilyService | Redis user:curfamily:{id} | `GET /auth/families` 返回 {familyId,name,role,isPrimary,isDefault,isCurrent};`POST /auth/family/switch {familyId,setDefault?}` 校验角色绑定后更新 Redis+重签 token;`POST /auth/join {inviteCode}` 凭码加入(不自动切换) |
| 成员管理 | MemberController | MemberManagementService | sys_user_role/family_invitation_code | `GET /member/list`(含 role_code+label);`PUT /member/{id}/role`;`DELETE /member/{id}`;`POST /member/invite`(生成码);`GET /member/invite`(码列表) |
| 公开家庭搜索 | FamilyController | — | family_apply | `GET /family/search?keyword=`;`POST /family/apply`;`GET /family/apply/list`;`PUT /family/apply/{id}?action=approve|reject`(通过绑 MEMBER+通知) |
| 家庭分享 | PublicController | — | sys_family_info.share_token | `?hid=<share_token>` 优先于 `?home_id=`;访客访问私有家庭 hid → NOT_FOUND;演示家庭 share_token 固定 `98a06619927f11f1` |

### 3. 内容创作

| 模块 | Controller | Service | 关键表 | 要点 |
|------|-----------|---------|--------|------|
| 博客 | BlogController | BlogService | content_blog | 标签(逗号分隔)+ `category VARCHAR(50)` 自定义分类;`GET /blog/categories` DISTINCT;默认 visibility=FAMILY;marked v18 renderer h1→h2;列表/编辑/详情均带 category |
| 日记 | DiaryController | DiaryService | content_diary | 多图(JSON images 最多 9 张)+mood+weather;默认 visibility=FAMILY |
| 相册 | AlbumController | AlbumService | content_album | type=public/private;public→PUBLIC/private→FAMILY;软删 |
| 照片 | PhotoController | AlbumService | content_photo | 批量上传 `POST /album/{id}/photos`;`taken_at/location`;**硬删除**(`PhotoMapper.deletePhysicalById` XML DELETE 绕过全局 logic-delete)+ `FileService.deleteByUrl` 删磁盘文件;`source_path`(设备:相对路径)去重 |
| 放映厅 | VideoController | VideoService | content_video/content_video_wish | 豆瓣式属性(media_type/genres/region/year/duration/episodes/director/actors/rating/intro/poster/video_url);`POST /video/upload` 500MB;**硬删除**(DB+video_url+poster);想看列表 CRUD |
| 照片瀑布 | CascadeController | — | content_photo | `GET /photo/cascade` 随机;可见性过滤(成员 PUBLIC+FAMILY,PRIVATE 仅作者,未登录仅 PUBLIC) |
| 愿望单 | WishController | WishService | content_wish | title/reason/category/status(待实现/已实现/放弃)/visibility/achieved_at |
| 书架 | LibraryController | LibraryService | content_book/content_book_borrow | 家庭电子书架(EPUB/PDF/TXT/MOBI);上传/分类/在线阅读;**硬删除**(DB+file_url+cover_url);阅读状态跟踪(WANT_READ/READING/FINISHED);在线阅读:PDF iframe/EPUB epub.js(异步加载)/TXT 分页(2000字/页)/MOBI 仅下载;文件存 `books/{yyyyMM}/`;可见性与博客一致 |

### 4. 互动

| 模块 | Controller | Service | 关键表 | 要点 |
|------|-----------|---------|--------|------|
| 点赞 | LikeController | ContentLikeService | content_like | content_type+content_id+user_id+family_id UNIQUE;toggle 语义;跨家庭 NOT_FOUND |
| 评论 | CommentController | CommentService | content_comment | 二级树(parent_id+reply_to_user_id);`GET /comment/list` 树;删除:OWNER 或作者 |
| 通知 | NotificationController | NotificationService | family_notification | 评论/回复自动通知;入家申请结果通知(type=system,content_type=family_apply);`GET /notification/list`、`/unread-count`、`PUT /{id}/read`、`PUT /read-all` |
| 聊天室 | ChatController + ChatWebSocketHandler | ChatService | family_chat_message/family_chat_read | 原生 WebSocket(非 STOMP);握手 `?token=` 验 JWT;按家庭分房间广播;发送即落库;`GET /chat/history`、`/unread`、`POST /chat/read` |

### 5. 家庭生活

| 模块 | Controller | Service | 关键表 | 要点 |
|------|-----------|---------|--------|------|
| 纪念日 | AnniversaryController | AnniversaryService | family_anniversary | 阳历/农历(calendar solar/lunar)+闰月(is_leap)+关联成员(user_id 可空)+每年重复(recurring);农历转公历 Hutool ChineseDate(`getGregorianMonth()` 0-based 需 +1);首页倒计时 stats.upcomingEvents |
| 提醒 | ReminderController | ReminderService | family_reminder | 一次性/每日/每周/每月;站内通知(family_notification) |
| 家庭计划 | PlanController | FamilyPlanService | family_plan/family_plan_task | 计划+子任务+完成度联动进度;子任务可指派成员 |
| 任务悬赏 | TaskController | TaskService | family_task | 状态机:待领取→进行中→待确认→已完成/已取消;reward_type=1 积分结算走 `pointsService.addRecord`;发布者不可自领 |
| 记账 | BookController | BookService | family_book_record | type(支出/收入/转账)+月度统计+分类榜;单表无账户 |
| 家谱 | TreeController | FamilyTreeService | family_tree | father_id/mother_id/spouse_id 自关联;spouse 双向绑定;generation 世代;**编辑全量提交,null 字段须用 LambdaUpdateWrapper 显式 SET NULL**(MP updateById 忽略 null);删除清空他人引用后逻辑删 |
| 签到积分 | PointsController | PointsService | family_checkin/family_points_record/family_points_product/family_points_order | 日签 5 分+连续加成(7 天轮回);内容奖励(博客+10/日记+8/照片+2/视频+15);兑换校验(积分不足 1008/兑完 1009);商品管理+核销需 `@RequirePermission("points:manage")` |
| 背景音乐 | MusicController | MusicService | content_music/content_music_playlist/content_music_playlist_track | 曲库(单曲/专辑上传,mp3agic 解析 ID3v2 元数据:标题/艺术家/专辑/时长/比特率/内嵌封面);歌单 CRUD(家庭维度独立);`is_background` 标记当前背景音乐歌单(每家庭最多 1 条);批量删除(`DELETE /music/batch` 按 ID 列表、`DELETE /music/album/{album}` 按专辑名);播放器 `GET /music/background` 获取歌单+曲目;`PUT /music/playlist/{id}/set-background` 设为背景;Settings 页「背景音乐设置」与导航栏「音乐」功能独立;MusicPlayer 仅在有背景歌单且曲目数>0 时渲染(`v-if="playlist.length"`),`z-index:55`(光影层下方);位置重置合并到 Settings「恢复默认面板布局」(清 `ihomy:music:pos`);临时文件名用纯 ASCII 后缀(含中文/斜杠的原始文件名导致 `File.createTempFile` IOException) |

### 6. 基础设施

| 模块 | Controller | Service | 关键表 | 要点 |
|------|-----------|---------|--------|------|
| 文件上传 | FileController | FileService | 磁盘 | 分类目录:pictures/{相册名}/、videos/、music/、files/{yyyyMM}/;`upload(bytes,name,type,albumId,albumName)`;`deleteByUrl(url)` 按 /files/ URL 解析物理路径删文件(`normalize()+startsWith` 防越界);DB 存 `/files/...` 完整 URL |
| 存储管理 | StorageController | StorageService/StorageSyncRunner | sys_storage_device | 家庭级设备(SYSTEM/NAS/REMOTE/MOUNT);文件浏览器 `GET /storage/browse?deviceId&path`;一键同步 `POST /storage/sync`(@Async 独立 bean,按顶层目录建相册,content_photo.source_path 去重);`@RequirePermission("storage:manage")` |
| 首页聚合 | HomeController + PublicController | HomeModuleService/HomeStatsService/ActivityFeedService | sys_home_module | `GET /public/home?hid=&home_id=` 返回 {family/modules/photos/stats};`GET /public/feed` 动态流;模块化(sys_home_module 插入即扩展) |
| 运维 | OpsController | OpsService | sys_operation_log/sys_weather_log | OPS 角色专属;资源统计/服务器状态/操作日志检索/和风天气 API 用量;不返回用户隐私 |
| 每日内容 | DailyController | — | — | 每日一图(代理 Bing)+每日知识(4 类×5 条);`GET /public/daily-knowledge?types=` |
| 操作日志 | LogController(@OperationLog AOP) | OperationLogService | sys_operation_log | AOP `@OperationLog` 注解自动记录;含 traceId 链路;仅 OPS 可查 |
| 系统参数 | — | ParameterService | sys_parameter | 键值对;AES 盐值(aes-salt)首启自动生成入库;`getAesSalt()` 优先环境变量 |

### 7. 沉浸式光影系统

| 模块 | 文件 | 要点 |
|------|------|------|
| 太阳位置 | `common/SolarUtil.java` + `service/SunService.java` | NOAA 算法纯数学;288 时隙(5 分钟);IP 定位(ip-api.com)+ Redis 6h 位置/12h 时隙;`GET /public/sun-info?date=`;时角归一化+atan2 方位角修复;默认济南 |
| 体积光 | `utils/windowLight.js` + `components/SunLightLayer.vue` | 丁达尔效应:7 条光束+光源辉光+窗框阴影(上下分层)+暗角+灰尘;**窗角(windowAngle=90-|az-180|)门控直射光**(az 90°→270° 旋转,窗角≤0 无直射光);方位角驱动旋转(az-180);灰阶 darken 幂等防叠加;夜间光柱 transparent |
| 台灯 | `utils/useSunLight.js` | 3 态(auto/on/off)+钟摆运动(8s 周期)+色温/亮度可调;mask 祛除阴影(GSAP 2s 补间);左上黄金分割点 |
| 天气特效 | `utils/useSunLight.js` + `SunLightLayer.vue` | `codeToPrecipLevel` 1-6 级;snowParticles/rainParticles;cloudFlicker GSAP 4-8s;weatherShadowOpacity;weatherMultiplier(晴 1.0/多云 0.55/雨 0.25/雪 0.4) |
| 天气代理 | `service/WeatherService.java` | 和风天气 JWT(Ed25519)身份认证(JDK 21 原生);凭证四件套(优先 DB sys_weather_credential);月度配额 49999(Redis 计数器);sys_weather_log 调用日志;Redis 缓存(now 30m/forecast 30m/warning 5m 等);`GET /public/weather` 简版+`/public/weather/detail` 聚合+`/ops/weather/quota` |
| 光照测试 | `utils/useSunLight.js` + `LightTestConsole.vue` | `lightTestMode` 循环 288 时隙;**testSpeed** 5 档(0.5/1/2/4/8x);窗角/方位/高度/地区/日期/日出日落显示;9 段时段标签;后退/暂停/前进/停止+天气控制+图层开关(阴影/环境光)+台灯模式+色温/亮度滑块;停止=重置真实时间 |
| 可拖拽面板 | `utils/useDragResize.js` | 5 个面板(feed/task/weather/anniversary/today);zIndex+bringToFront;边界 clamp+localStorage 持久化 |
| 和风图标 | `public/qweather-icons/` | npm 包 qweather-icons;`<i class="qi-{iconCode}">`;iconCode 来自和风 API now.icon |

### 8. 物品定位

| 模块 | Controller | Service | 关键表 | 要点 |
|------|-----------|---------|--------|------|
| 物品清单 | ItemController | ItemService | family_house/family_room/family_furniture/family_item | 五级粒度(家>房子>房间>家具>位置,多套房多楼层);CRUD+跨级搜索;`image_url/type/quantity/unit` 4 字段(V7.0,type: KITCHENWARE/INGREDIENT/DAILY/CLOTHES/TOOL/OTHER 走 item_type 字典);`furniture_id` 可空(散放物品);`GET /item/list?type=` 按类型过滤;2 期户型图/3 期 AI 语义待做 |
| 厨房(菜单+菜谱) | RecipeController | RecipeService | family_recipe | 菜单页按类别分组+时间推荐(早 6-10/午 11-14/晚 17-20);菜谱 CRUD;ingredients/equipment/steps 为 JSON 字段;首页模块 kitchen(position=17) |
| 食材页 | ItemController | ItemService | family_item | `/kitchen/ingredients` 横条列表(左图透明渐变+名称+数量单位+存放位置);录入表单:图片/名称/数量/单位选择框(个斤瓶袋...)/存放位置 el-cascader 三级(house>room>furniture,默认选含"厨房"的 room);复用 itemApi type=INGREDIENT,无独立后端 |
| 厨房 i18n | — | — | — | **教训:RecipeDetail/RecipeEdit 曾有 `const $t = (k) => k` stub 遮蔽 vue-i18n(所有文案显示原始 key)**;新页面禁止此写法,统一 `const { t: $t } = useI18n()` |

### 9. 国际化与主题

| 模块 | 文件 | 要点 |
|------|------|------|
| i18n | `i18n/` + `utils/dict.js` | vue-i18n 中英;applyLocale 切换;DictConst 后端常量对应 |
| 主题 | `theme/index.js` | 只 light/dark;applyTheme/loadTheme;双层伪元素背景 1s 过渡;手动切主题取消日出日落自动 |
| 身份标签 | ProfileController | family_user_label(user_id/family_id/label/color,每家庭一套) |
| 字典表 | — | sys_dict_item 18 组;状态/类型字段英文单词化 |

### 10. 移动端兼容性(V8.0)

| 模块 | 文件 | 要点 |
|------|------|------|
| 设备检测 | `composables/useDevice.js` | UA + matchMedia(768px) 双信号检测;全局单例 `isMobile` ref;matchMedia change 监听横竖屏切换 |
| 移动布局 | `layouts/MobileLayout.vue` | 首页路由 `/` → 三 Tab 模式(底部 TabBar);其余路由 → 子页面模式(顶部返回栏 + router-view) |
| 底部 TabBar | `components/MobileTabBar.vue` | 三 Tab:首页/更多/我的;fixed 底部 + safe-area-inset-bottom |
| 子页面返回栏 | `components/MobileHeader.vue` | fixed 顶部 + safe-area-inset-top;返回按钮 + 标题 + 右侧 slot |
| 首页动态流 | `components/MobileHomeFeed.vue` | 顶部横向滚动筛选栏(全部/博客/日记/照片)+ 卡片信息流;复用 publicApi/homeApi getFeed |
| 更多功能 | `components/MobileMoreGrid.vue` | 按分类(内容/生活/成员/系统)4 列图标网格;点击跳转对应路由;复用 appStore.modules |
| 我的页面 | `components/MobileMePage.vue` | 用户信息 + 家庭切换(展开列表)+ 主题/光影/语言开关 + 设置/成员/资料入口 + 退出 |
| 特效门控 | `App.vue` + `useSunLight.js` | `watch(isMobile)` immediate:移动端默认关闭所有光影特效(shadow/weather/blobs/lamp/glass);"我的"Tab 可手动开启 |
| App.vue | `App.vue` | `isMobile` 条件渲染:移动端 → `<MobileLayout>`(不含 SunLightLayer/AppSidebar/LightTestConsole);桌面端不变 |

**设计决策**:采用**单代码库 + 运行时设备自适应**(非子域名 m.ihomy.top 方案)。理由:避免 JWT 跨域共享/CORS/双构建双部署/PWA 分裂;同一 URL 响应式适配,localhost 测试无需额外配置。

**测试方式**:`localhost:5173` + Chrome DevTools 设备模拟;真机 `http://<局域网IP>:5173`(vite host:0.0.0.0)。

**第一期适配范围**:首页(三 Tab 重设计)+ 子页面顶部返回栏 + 20 个功能页响应式 CSS 增强(Blog/Diary/Album/Chat/Login/Settings/Member/Book/Task/Plan/Wish/Points/Reminder/Tree/Item/Library)。后续迭代:进一步触摸手势优化+字体大小+性能验证。

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
    - **不变数据走内存缓存**:`sys_home_module` 全局模块 `@PostConstruct` 加载 `volatile List`,家庭模块按 familyId 缓存 `ConcurrentHashMap`,变更时 evict。**不引 Caffeine 等库**(数据量小,内存够用)。
    - **敏感数据不缓存**:成员视图的 `/public/home`(含 stats/photos)不缓存,只缓存非成员视图。
11. **UPDATE 不先 select**(强制):回写冗余字段(如 `like_count`)用 `LambdaUpdateWrapper.eq(id).set(field, value).update(null)`,不要 `selectById` 再 `updateById`(省一次查询)。参考 `ContentLikeService.syncCount`。
12. **文件上传流式**(强制):大文件(>1MB)禁止 `file.getBytes()` 全量入堆(生产 `-Xmx384m` 上传 200MB 即 OOM)。**用 `MultipartFile` 重载 + `transferTo` + `Files.copy` 兜底**。FileService 已提供 4 个流式重载(`upload`/`uploadVideo`/`uploadBook` 通用+图片+视频+电子书),Controller 必须传 `MultipartFile` 不调 `getBytes()`。
13. **JVM/连接池配置**(基线):`spring.threads.virtual.enabled: true`(JDK21 虚拟线程,Tomcat 自动用);HikariCP `maximum-pool-size: 20` + `minimum-idle: 5` + `connection-timeout: 3000`;`mybatis.sql: warn`(生产静默 SQL 日志)。

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
17. **模态弹窗规范**(强制,全局统一,所有 `el-dialog` + `ElMessageBox` 共享 `main.css` 全局覆写,禁止在各组件 scoped 内重复定义):
    - **容器**:圆角 14px;阴影 `0 3px 12px rgba(0,0,0,0.07)`;背景 `#fcf8f0` + `backdrop-filter: blur(12px) saturate(1.1)`;`padding: 0`(header/body/footer 各自管 padding)。
    - **尺寸规则**(按业务场景,禁止全部弹窗同一宽度,禁止写死固定 height):
      - `dialog-sm`(简短确认/单行输入):420px,高度自适应。
      - `dialog-md`(选择器/简单表单):520px,`max-height:520px`,body 内部滚动。
      - `dialog-lg`(复杂多字段表单):640px,`max-height:640px`,body 内部滚动。
      - `dialog-xl`(详情/媒体预览):`82vw`(max 900px),`max-height:85vh`,body 内部滚动。
      - 弹窗容器 `flex-direction:column`;body `flex:1 + overflow-y:auto`;容器本身不滚动。
    - **遮罩**:`rgba(0,0,0,0.20)` + `blur(2px)`,不过度压暗。ESC + 点击遮罩关闭(默认开启)。
    - **标题**:左侧 4px 暖棕装饰竖线;`font-size: 15px; font-weight: 600`;标题与说明文字间距 8px。
    - **关闭按钮**:`el-dialog__headerbtn` 显示 X(28×28px,圆角 8px,hover 浅米底色 `rgba(58,46,34,0.06)`)。`ElMessageBox` 关闭按钮 `display:none`。
    - **弹窗内 Tab**:选中态低饱和暖棕文字 `#5c4c3d` + `#c4a884` 下划线 `opacity:0.7`;禁止蓝色高亮。
    - **弹窗内 checkbox**:选中色 `#b88c6e` 暖棕(禁用原生蓝色)。
    - **输入框**:圆角 10px;边框 `#e4ddd0`;背景 `#fffdf8`;min-height 38px;focus 暖棕光晕 `rgba(184,140,110,0.12)`;placeholder 弱化 `#c4b8a8 opacity:0.7`。
    - **按钮**:统一 34px 高 / 10px 圆角 / 13px 字号;主操作 `#b88c6e` 暖棕;次级 `#f3eee6` 深褐文字;危险 `#f4e0dc` 低饱和暗红 `#b04a3a`;禁用/loading `#e4ddd0` 灰底。
    - **footer**:无顶分割线;`padding: 0 20px 18px`;输入框距底部按钮区 20px;右下角对齐。
    - **动画**:覆盖 EP 默认 `animation`(杀 `dialog-fade` 的 `animation` 再用 `transition`);`scale(0.94)` + opacity 淡入 0.25s。
    - **ElMessageBox**:同风格;`max-width: 360px; min-width: 300px`;无装饰竖线;footer 无分割线;关闭按钮隐藏。
    - **暗色模式**:弹窗背景 `#1E2A48`;次级按钮 `rgba(232,220,200,0.1)`;placeholder `rgba(232,220,200,0.35)`。
    - **禁止**:在组件 scoped CSS 里写 `el-dialog`/`el-message-box` 样式覆写;新增弹窗只管业务逻辑,样式由全局兜底。
19. **ElMessage Toast 规范**(强制,`main.css` 全局覆写):
    - **位置**:右上角 `right:24px`(避让导航栏)。
    - **配色**(禁止高饱和绿/红/黄):
      - success:暖米底 `rgba(243,238,230,0.95)` + 褐字 `#5c4c3d` + 暖棕细边框 `rgba(184,140,110,0.25)`。
      - warning:暖米底 + 暗金文字 `#8a6d3b` + 金棕边框。
      - error:浅红底 `rgba(249,236,234,0.95)` + 暗红文字 `#b04a3a` + 细红边框。
      - info:深褐底 + 白字。
    - **暗色模式**:success/warning 弹窗背景 `rgba(30,42,72,0.92)` + 浅米文字。
20. **Popper/Dropdown z-index 规范**(强制):
    - `.el-popper.is-light`(含 dropdown/tooltip)`z-index:61`(高于 AppSidebar=60,低于光影层 bright-spot=65)。
    - MusicPlayer `z-index:62`(popper 上方,光影层下方)。
    - SiteFooter `z-index:70`;BackToTop/InstallPrompt `z-index:200`;ElMessage `z-index:3000`(EP 内置,不覆写)。
    - **完整 z-index 层级**(从高到低):`lamp-light(100) > LightTestConsole(80) > lightning(79) > light-layer(78) > snow/rain(77) > dust(76) > vignette(74) > reflection(72) > SiteFooter(70) > window-shadow(68) > bright-spot(65) > Popper/dropdown/select(64) > ElMessage(63) > el-overlay/dialog/message-box(63) > MusicPlayer(62) > AppSidebar(60) > draggable-panel(20→60) > main-content(10) > glass-bg(2) > bg-blobs(1)`。
    - **光影层(65-100)为最高层**,弹窗遮罩(`el-overlay`)+`ElMessage`=63(低于光影层),Popper/dropdown/select=64(高于弹窗,低于光影层);任何新增组件 z-index 不得超 100(台灯 100 除外)。
18. **按钮统一样式**(强制,全局 4 类按钮,`main.css` 统一覆写,禁止 scoped 重复定义):
    - **圆角**:所有 `el-button` 12px;small 10px。
    - **主按钮**(`type="primary"`):背景 `#b88c6e` 白字;hover `#a87c5e`;用于保存/添加/确认等正向操作。**禁止亮蓝**。
    - **次级按钮**(无 type / 默认):背景 `#f3eee6` 深褐 `#5c4c3d` 文字;hover `#e8e0d2`;用于取消/次级入口。
    - **幽灵按钮**(`.ghost-btn` class):透明底 + `var(--color-border)` 边框 + 褐字;hover `#f3eee6` 底;用于复制链接等低权重操作。
    - **危险按钮**(`type="danger"`):背景 `#f9ecea` 暗红 `#b96058` 文字;hover `#f0dedb`;**禁止亮红**。`text`/`link` 类型保持透明底,hover 浅红 `rgba(185,96,88,0.08)`。
    - **尺寸**:默认 32px / small 28px / large 38px。表单底部保存按钮用默认或 large;列表行内删除用 small。
    - **交互**:hover `translateY(-1px)` + `0 2px 8px rgba(0,0,0,0.06)` 微阴影;禁用/loading 置灰 `#e4ddd0` 去掉上浮。
    - **摆放规则**:表单提交按钮 → `.form-footer { display: flex; justify-content: flex-end }` 右下角;模块独立功能入口 → 靠左次级按钮;Modal footer → 次级左主按钮右;列表删除 → 行最右 small danger。
    - **暗色模式**(与浅色完全不同色值,不共用):
      - 主按钮:背景 `#d4b298`(提亮浅暖棕),文字 `#2a2018`(深色),hover `#e0c2aa`;**禁止与浅色同色**。
      - 次级按钮:背景 `rgba(255,255,255,0.12)`(半透明白色磨砂),文字 `#E8DCC8`(浅米白),hover `rgba(255,255,255,0.18)`。
      - 幽灵按钮:透明底 + `rgba(255,255,255,0.15)` 边框 + `#E8DCC8` 文字;hover `rgba(255,255,255,0.08)`。
      - 危险按钮:背景 `rgba(201,116,116,0.15)`(半透明),文字 `#c97474`(低饱和暗红),hover `rgba(201,116,116,0.25)`;禁用 `rgba(232,220,200,0.06)` + `rgba(232,220,200,0.3)`。
      - Tab active-bar:`#d4b298` opacity 0.5。
21. **标签(el-tag)配色规范**(强制,`main.css` 全局覆写):
    - 圆角 8px;半透明磨砂背景;禁止高饱和实色块。
    - 浅色:默认 `rgba(184,140,110,0.08)` 褐字;success `rgba(107,155,107,0.1)` 绿字;warning `rgba(138,109,59,0.1)` 金字;danger `rgba(185,96,88,0.1)` 红字;info `rgba(58,46,34,0.06)` 褐字。
    - 深色:默认 `rgba(212,178,152,0.25)` 浅暖棕字;success `rgba(125,186,125,0.15)` 绿字;danger `rgba(201,116,116,0.15)` 暗红字 `#c97474`;info `rgba(255,255,255,0.08)` 浅米字。
22. **数量角标(el-badge)规范**:
    - 浅色:`rgba(58,46,34,0.7)` 半透明黑磨砂背景;深色:`rgba(0,0,0,0.5)` 浅米白字;不使用纯黑实色块。
23. **图标线条规范**(强制):
    - 所有 `el-icon` SVG `stroke-width: 2px`;图标颜色跟随文字层级:主要图标=主文字色,次要图标=辅助灰色,危险图标=低饱和暗红。
24. **圆角全局统一**(强制,`main.css` 全局覆写):
    - `el-button` 12px(small 10px);`el-input__wrapper`/`el-select__wrapper`/`el-cascader .el-input__wrapper`/`el-cascader .el-select__wrapper` 10px;`el-card` 14px;`el-dialog` 14px。
25. **页面统一规范**(强制,所有功能页遵守):
    - 根容器统一 `class="page"`(全局 `.page`: `max-width:1100px; margin:0 auto; padding:16px`),**禁止 scoped 覆写** max-width/margin/padding/border。
    - 页面级 H2/H1 标题全部移除(面包屑已体现页面标题);内容分区标题用 `.section-label`(16px/600/左 3px 暖棕竖线)。
    - **工具栏**:统一 `class="page-toolbar card"`,全局 `padding: 10px 16px !important`(不被 `.card` 的 20px 覆盖);`.tb-left` 放搜索/筛选/排序,`.tb-right` 放操作按钮。**禁止 scoped 定义 `.list-header`/`.toolbar`/`.header-actions` 等旧工具栏类**。
    - **工具栏按钮**(`.write-btn`/`.ghost-btn`/`.danger-btn`/`.view-toggle`/`.vt-btn`):全局定义在 `main.css`,height:32px,**禁止 scoped 重复定义**。
    - `.page-header`/`.page-title`/`.list-header` 全局统一定义在 `main.css`,禁止 scoped 重复。
    - Breadcrumb `#right` slot 放置页面操作按钮(添加/加入等)。

### 性能规范(已踩坑 + 强制规则)

#### 已踩坑(必读)

- **100% 缩放卡顿根因**:Element Plus `Setting`/`Monitor` 图标 SVG path 过于复杂,hover 时子像素光栅化开销大 → 用内联 SVG 替代(详见博客 id=18)。
- **backdrop-filter + overflow:auto 子元素**:毛玻璃父元素 + 子元素滚动 = 性能炸弹 → `transform: translateZ(0)` 隔离合层。
- **背景随滚动**:`.bg-blobs` 用 `position: fixed` 不随页面滚动;移除 `background-attachment: fixed`。
- **关闭效果时跳过定时器**:`flickerTimer` 在所有开关关闭时 return,避免持续触发 sunScene 重写。
- **rAF 写 Vue ref**:原 `useSunLight.js` 钟摆用 `requestAnimationFrame` 每帧写 `lampPendulumX.value`/`lampPendulumScaleX.value`,触发 `SunLightLayer.vue` 每帧重渲染 → 改 CSS `@keyframes lampSwing` 完全绕过响应式。
- **常驻事件监听器**:原 `useDragResize.js` 每实例 `onMounted` 挂 `mousemove`/`mouseup`,5 面板 = 10 常驻 listener → 改 `onDragStart` 时挂、`onMouseUp` 时移除。
- **同步 import 阻塞首屏**:原 `main.js` 同步 `import 'qweather-icons/...'`(44.9KB CSS)→ 改 `import('...')` 异步。
- **入口 chunk 过大**:原 `vite.config.js` 无 `manualChunks`,入口 553KB → 加 `manualChunks` 拆 `element-plus`/`gsap`/`vue-i18n`,入口降到 158KB(-71%)。
- **`getBytes()` OOM**:原 `FileController`/`PhotoController`/`VideoController` 用 `file.getBytes()` 全量入堆,生产 `-Xmx384m` 上传 200MB 视频即 OOM → 改 `MultipartFile` + `transferTo` 流式。
- **SQL 日志同步 I/O**:原 `SqlStatementLog` 用 `System.out.println` 同步打印每条 SQL,生产环境拖累 → 改 SLF4J,由 `logging.level.mybatis.sql` 控制(默认 `warn` 静默)。
- **N+1 列表查询**:原 `ActivityFeedService`/`CommentService`/`AnniversaryService`/`VideoService` 在循环里 `selectById` 取用户名 → 改 `selectBatchIds` 批量查 + 内存 Map 回填。

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

- **ElMessageBox 动画未生效**:`main.css` 中 `.fade-in-linear-*` + `.el-overlay-message-box` 的 CSS 覆写写法正确(transition name=`fade-in-linear`,class=`el-overlay-message-box` 已从 EP 源码确认),但实际运行时动画未生效。可能原因:EP 内部 `Transition` 的 `persisted` 模式导致 CSS transition 不触发,或 EP 的 `msgbox-fade-in` keyframes 优先级覆盖。待排查:用 DevTools 确认渲染时实际 class 和 transition 是否被正确应用。`closeOnClickModal: true` 已全部加上(点击遮罩关闭已生效)。

#### 验证基线

- 后端编译:`cd backend; .\mvnw.cmd -B clean compile -DskipTests` → BUILD SUCCESS
- 前端构建:`cd frontend; npm run build` → 入口 chunk ≤ 200KB(当前 158KB)
- 接口测试:`cd autotest_framework; .venv\Scripts\python.exe -m pytest -m api` → 37 passed

#### 已实现变更归档(按功能域分类)

> 以下为已完成的改动索引,按功能域而非时间排列,便于检索。

##### 性能优化

| 文件 | 改动 |
|------|------|
| `schema.sql` | `content_photo` 加 `idx_family_created`,`content_blog` 加 `idx_family_status_created`,`content_diary` 加 `idx_family_created` |
| `application.yml` | HikariCP `maximum-pool-size:20`+`minimum-idle:5`+`connection-timeout:3000`;`spring.threads.virtual.enabled: true`(JDK21 虚拟线程) |
| `config/SqlStatementLog.java` | `System.out.println` 改 SLF4J,由 `logging.level.mybatis.sql` 控制(默认 warn 静默) |
| `service/FileService.java` + 3 Controller | 新增 3 个 `MultipartFile` 流式重载(`transferTo` + `Files.copy` 兜底),避免 `getBytes()` OOM |
| `service/{ActivityFeed,Comment,Anniversary,Video}Service.java` | 列表 N+1 消除:`selectBatchIds` 批量查 + 内存 Map 回填 |
| `service/HomeStatsService.java` | todayEvent + upcomingEvents 合并为一次查询 |
| `service/HomeModuleService.java` | `@PostConstruct` 加载全局模块 + ConcurrentHashMap 家庭缓存 |
| `service/ContentLikeService.java` | `syncCount` 改 `LambdaUpdateWrapper.set` 单 UPDATE(不先 select) |
| `controller/PublicController.java` | `/public/home` 非成员视图整包缓存 Redis 5min + 失效点 |
| `security/SecurityHelper.java` | `currentUser()`/`permissionCodes()` 走 Redis TTL 5min |
| `frontend/vite.config.js` | `manualChunks` 拆 `element-plus`/`gsap`/`vue-i18n`,入口 553→158KB |
| `frontend/src/main.js` | `qweather-icons.css` 改异步 `import()` |
| `frontend/src/utils/useDragResize.js` | onDragStart 挂/onMouseUp 移(不再 onMounted 常驻) |
| `utils/useSunLight.js` + `SunLightLayer.vue` + `main.css` | 删 lampRaf rAF,改 CSS `@keyframes lampSwing` |
| `frontend/src/views/Home.vue` | `loadAll()` 改 `Promise.all`;`polaroidLayout` 从 computed 改 `ref`+`watch` |
| `frontend/src/stores/app.js` | `init()` 改 `Promise.all` |
| `frontend/public/qweather-icons/` | 删整目录(365KB 冗余,走 node_modules) |

**live DB 同步**(已有库需手动执行):
```sql
ALTER TABLE content_photo  ADD INDEX idx_family_created (family_id, deleted, created_at);
ALTER TABLE content_blog   ADD INDEX idx_family_status_created (family_id, status, deleted, created_at);
ALTER TABLE content_diary  ADD INDEX idx_family_created (family_id, deleted, created_at);
```

##### 首页仪表盘

| 文件 | 改动 |
|------|------|
| `views/Home.vue` | 12列×9行栅格系统,编辑模式可拖拽/缩放/增删组件;`useWidgetDrag.js` 单例从侧边栏拖入;栅格自适应屏幕;grid-cell 出现动画;h=1 标题消失;点击置顶;拍立得溢出+散乱;编辑模式禁用内部交互(`pointer-events:none`);GAP=40px,四边 margin=32/40/40/260(侧边栏220+40) |
| `stores/app.js` | `homeEditMode` 状态 + `toggleHomeEditMode()` |
| `components/AppSidebar.vue` | 编辑模式按钮(EditPen 图标,foot-user-row 最右);编辑模式 nav-item 向右下偏移+虚线框占位;拖拽时右侧气泡融合;`useWidgetDrag` 启动 |
| `utils/useWidgetDrag.js` | 跨组件拖拽单例:AppSidebar 启动→Home.vue 接收;ghost 由小变大动画;drop 创建 4×5 组件 |
| `controller/BookController.java` + `BookService.java` | `GET /book/summary` 本月收支聚合 |
| `views/Home.vue` | 8 个默认组件:feed(5×5)/anni(3×4)/weather(3×2)/today(3×2)/recipe(4×4)/wish(3×2)/album(5×3)/finance(2×2);布局持久化 localStorage `ihomy:dashboard:layout` |

##### 音乐系统

| 文件 | 改动 |
|------|------|
| `schema.sql` | `family_music`→`content_music`;新增 `content_music_playlist`+`content_music_playlist_track`;`sys_family_info` 加 `background_playlist_id` |
| `pom.xml` | 加 `com.mpatric:mp3agic:0.9.1` |
| `MusicService.java` | 曲库 CRUD + mp3agic ID3v2 元数据提取 + 歌单 CRUD + 设为背景 + 获取背景歌单;`extractMetadata` 固定 `.mp3` 后缀(避免中文文件名 IOException);`batchDeleteMusic`/`batchDeleteByAlbum` |
| `MusicController.java` | 曲库(upload/upload-album/list/albums/add/delete/batch/album)+ 歌单(playlist CRUD/tracks/set-background/get-background) |
| `views/music/Music.vue` | 3 Tab(全部曲目/按专辑/歌单);多选批量删除;歌单卡片重构;弹窗统一 dialog-sm/md |
| `components/MusicPlayer.vue` | z-index:62(光影层下方);无歌单不渲染;watch familyId 重置 |
| `views/Settings.vue` | 背景音乐设置页 |
| `views/Home.vue` | 音乐组件常驻,无歌单显示空状态 |

**关键业务**:歌单是背景音乐最小单元;`is_background=1` 每家庭最多 1 条;MP3 元数据用 mp3agic 解析;内嵌封面存 `/files/music/covers/`。

**live DB 同步**:
```sql
ALTER TABLE sys_family_info ADD COLUMN background_playlist_id BIGINT DEFAULT NULL AFTER music_title;
DROP TABLE IF EXISTS family_music;
CREATE TABLE content_music (...);  -- 见 schema.sql
CREATE TABLE content_music_playlist (...);
CREATE TABLE content_music_playlist_track (...);
```

##### 光影系统

| 文件 | 改动 |
|------|------|
| `utils/windowLight.js` | `windowAngle=90-\|az-180\|`+`hasDirectLight` 门控;方位角驱动旋转;灰阶 darken 幂等 |
| `utils/useSunLight.js` | 台灯 3 态+CSS `@keyframes lampSwing`;天气特效(snow/rain/cloud);`testSpeed` 5 档;`loadSunInfoForDate`;overcast/fog 天气模式;夜间亮斑层不因天气禁用 |
| `components/SunLightLayer.vue` | 7 条光束+辉光+窗框阴影+暗角+灰尘;窗角门控直射光 |
| `components/LightTestConsole.vue` | 可拖动;日期选择;速度 5 档;天气按钮;图层开关;台灯模式;9 段时段标签 |
| 删除 | `composables/useLightLab.js`+`LightLabLayer.vue`+`LightLabConsole.vue`+`views/lightlab/LightLab.vue`+`/lightlab` 路由(LightLab 合并到生产系统) |

##### 深色模式 + UI 规范

| 文件 | 改动 |
|------|------|
| `styles/main.css` | 深色模式全面重构:primary `#d4b298`/次级半透明白/危险 `#c97474`;el-tag 半透明磨砂;el-badge;`stroke-width:2px`;圆角统一(button 12px/input 10px/card 14px/dialog 14px);弹窗规范 4 档(sm/md/lg/xl)+关闭按钮+Tab 暖棕+checkbox 暖棕;ElMessage 暖色调;popper z-index:61 |
| z-index 层级 | ElMessage(3000)>BackToTop(200)>lamp-light(100)>LightTestConsole(80)>light-layer(78)>snow/rain(77)>dust(76)>vignette(74)>reflection(72)>SiteFooter(70)>window-shadow(68)>bright-spot(65)>MusicPlayer(62)>Popper(61)>AppSidebar(60)>draggable-panel(20→60)>main-content(10) |
| 页面规范 | 全局 `.page`/`.page-header`/`.list-header`/`.section-label`;所有页面 H1/H2 移除(面包屑替代);Breadcrumb `#right` slot 放操作按钮 |
| 工具栏规范 | 全局 `.page-toolbar`(padding `10px 16px !important`,不被 `.card` 20px 覆盖);`.tb-left` 放搜索/筛选,`.tb-right` 放按钮;`.write-btn`/`.ghost-btn`/`.danger-btn`/`.view-toggle`/`.vt-btn` 全局定义 height:32px,禁止 scoped 重复 |
| 圆角规范 | `el-input__wrapper`/`el-select__wrapper`/`el-cascader` wrapper 全局 10px;`el-button` 12px;`el-card`/`el-dialog` 14px |
| 分类级联 | 博客+图书分类选择/筛选统一用 `el-cascader`(`checkStrictly + emitPath: false`),支持多级树、`filterable` 搜索;编辑时排除当前及后代防环 |

##### 厨房 + 物品

| 文件 | 改动 |
|------|------|
| `views/kitchen/Ingredient.vue` | 上传改 `:http-request`;body key `imageUrl`(camelCase);级联 `houseId`/`roomId`(camelCase);`checkStrictly:true`;bar 高 84px 图片 1/3 宽 |
| `views/kitchen/Kitchen.vue` | 对齐标准页 `.page`;v-loading;全局 `.card` |
| `views/kitchen/RecipeDetail.vue` | Bug 修复:`userStore.user?.id`→`userInfo.id`(非 OWNER 作者无法编辑) |
| `views/item/Item.vue` | `el-tag` 类型标签;对齐标准页;v-loading |
| `i18n/zh-CN.js`+`en.js` | 合并重复 `dict` key;新增 `item_type` 字典 |

##### 运维 + 天气 API

| 文件 | 改动 |
|------|------|
| `controller/OpsController.java` | `GET /ops/weather/finance`+`GET /ops/weather/stats` |
| `service/WeatherService.java` | `getFinance()`/`getStats()` 去掉 `resp.path("data")`(和风 Console API 扁平 JSON);`parseApiType` 加 finance/metrics;`logCall` 跳过敏感响应体 |
| `views/ops/Ops.vue` | 天气 Tab 三区块(用量/财务/24h);`Promise.allSettled` |

##### UX 修复

| 文件 | 改动 |
|------|------|
| `views/album/AlbumDetail.vue` | 照片全屏预览;上传进度条;触屏常显操作;loading ref |
| `views/blog/BlogDetail.vue` | 评论删除确认框 |
| `views/plan/Plan.vue` | 子任务删除确认框 |
| `views/Member.vue` | 邀请码一键复制 |
| `FileService.java` | 图片缩略图 `_thumb.jpg`(maxWidth 480);`deleteByUrl` 顺带删 |
| `utils/image.js` | `thumbUrl`/`onThumbError` 工具;4 页面列表用缩略图 |
| `views/blog/BlogList.vue` | "加载更多"按钮;封面缩略图 |
| `views/diary/DiaryList.vue` | `el-image-viewer` 应用内预览 |
| i18n | Home/Settings 60+ 硬编码中文改 `$t()`;中英双语 key |
| 移动端 | `@media (max-width:960px)` 面板从 `display:none` 改文档流堆叠 |

##### 电子图书(家庭书架)

| 文件 | 改动 |
|------|------|
| `schema.sql` | 新增 `content_book`+`content_book_borrow` 两表;`sys_auth` 加 `library:manage`;`sys_role_auth` MEMBER/CHILD 授权;`sys_home_module` 加 library(position=19);`sys_dict_item` 加 book_format/borrow_status |
| `entity/ContentBook.java` + `entity/BookBorrow.java` | 电子书实体+阅读状态实体 |
| `dto/LibraryDTO.java` | 表单 DTO(title+fileUrl 必填) |
| `mapper/ContentBookMapper.java` + `ContentBookMapper.xml` | BaseMapper + 自定义 SQL(incrViewCount/selectCategoriesByFamily/renameCategory/clearCategory/deletePhysicalById) |
| `mapper/BookBorrowMapper.java` | BaseMapper(阅读状态 CRUD) |
| `service/LibraryService.java` | CRUD + 分类管理 + 可见性过滤(同博客) + 阅读状态跟踪 + 硬删+文件清理 + 格式自动检测 |
| `controller/LibraryController.java` | 列表/分类/上传/增删改/阅读状态 12 个接口 |
| `service/FileService.java` | 新增 `uploadBook(MultipartFile)` 流式重载,存 `books/{yyyyMM}/` |
| `common/DictConst.java` | 新增 FMT_EPUB/PDF/TXT/MOBI + BORROW_WANT/READING/FINISHED 常量 |
| `frontend/src/api/index.js` | 新增 `libraryApi`(list/detail/create/update/delete/categories/upload/borrow) |
| `frontend/src/router/index.js` | 3 路由:/library(list)、/library/:id(detail)、/library/edit/:id?(edit) |
| `frontend/src/views/library/LibraryList.vue` | 书架网格(auto-fill 150px)+ 工具栏级联分类筛选(`el-cascader`)+ 格式角标 + 卡片 hover 上浮 + 下拉菜单 + 分类管理弹窗(`el-cascader` 选父级) |
| `frontend/src/views/library/LibraryDetail.vue` | 详情卡片 + 在线阅读器全屏覆盖(PDF iframe/EPUB epub.js 异步加载/TXT 分页/MOBI 仅下载)+ 阅读状态按钮 |
| `frontend/src/views/library/LibraryEdit.vue` | 表单(书名/作者/文件上传/封面上传/简介/分类/标签/可见范围) |
| `frontend/src/components/AppSidebar.vue` | 导航加 library(Reading 图标);ICON_MAP + NAV_PATHS |
| `frontend/src/i18n/zh-CN.js` + `en.js` | library.* 约 55 条中英双语;dict.book_format/borrow_status |
| `frontend/vite.config.js` | manualChunks 加 epubjs 拆分 |
| `frontend/package.json` | 新增 epubjs 依赖 |

**live DB 同步**:
```sql
-- 见 backend/src/main/resources/library_migration.sql
CREATE TABLE content_book (...);
CREATE TABLE content_book_borrow (...);
INSERT INTO sys_auth ... 'library:manage';
INSERT INTO sys_role_auth ...;
INSERT INTO sys_home_module ... 'library';
INSERT INTO sys_dict_item ... book_format/borrow_status;
```

##### 移动端兼容性

| 文件 | 改动 |
|------|------|
| `composables/useDevice.js` | 新建:UA + matchMedia(768px) 双信号检测;全局单例 `isMobile` ref;matchMedia change 监听横竖屏切换 |
| `layouts/MobileLayout.vue` | 新建:首页路由 `/` → 三 Tab 模式(底部 TabBar);其余路由 → 子页面模式(顶部 MobileHeader + router-view);含 BackToTop/InstallPrompt/MusicPlayer |
| `components/MobileTabBar.vue` | 新建:三 Tab(首页/更多/我的);fixed 底部 + safe-area-inset-bottom |
| `components/MobileHeader.vue` | 新建:子页面顶部返回栏;fixed + safe-area-inset-top;返回按钮 + 标题 + 右侧 slot |
| `components/MobileHomeFeed.vue` | 新建:首页 Tab;横向滚动筛选栏(全部/博客/日记/照片)+ 卡片信息流;复用 publicApi/homeApi getFeed |
| `components/MobileMoreGrid.vue` | 新建:更多 Tab;按分类 4 列图标网格;复用 appStore.modules + NAV_PATHS/ICON_MAP |
| `components/MobileMePage.vue` | 新建:我的 Tab;用户信息 + 家庭切换 + 主题/光影/语言开关 + 设置/成员/资料入口 + 退出 |
| `App.vue` | `isMobile` 条件渲染:移动端 → `<MobileLayout>`(不挂载 SunLightLayer/AppSidebar/LightTestConsole/SiteFooter);`watch(isMobile, immediate)` 关闭所有特效 |
| `i18n/zh-CN.js` + `en.js` | 新增 `mobile.*` 文案(home/more/me/language/members) |
| `styles/main.css` | `@media (max-width:768px)` 增强:`.page` 全宽 + safe-area;移动端隐藏 `.light-test-console` |

**设计决策**:单代码库 + 运行时设备自适应(非子域名 m.ihomy.top)。理由:避免 JWT 跨域共享/CORS/双构建双部署/PWA 分裂;同一 URL 响应式适配,localhost 测试无需额外配置。

**测试方式**:`localhost:5173` + Chrome DevTools 设备模拟;真机 `http://<局域网IP>:5173`(vite host:0.0.0.0)。

**第一期适配范围**:首页(三 Tab 重设计)+ 子页面顶部返回栏 + 20 个功能页响应式 CSS 增强(Blog/Diary/Album/Chat/Login/Settings/Member/Book/Task/Plan/Wish/Points/Reminder/Tree/Item/Library)。后续迭代:进一步触摸手势优化+字体大小+性能验证。

##### 播放器沉浸模式 + 多家庭修复

| 文件 | 改动 |
|------|------|
| `utils/useSunLight.js` | `suspendEffects()`/`restoreEffects()`:播放器启动时保存并关闭天气/灯光/毛玻璃/色块,关闭后恢复;特效开关状态持久化 localStorage(`ihomy:effects`);suspend 期间跳过持久化避免覆盖原状态 |
| `components/PhotoViewer.vue` | `<Teleport to="body">` + z-index:201(高于导航栏60和光影层100);`v-model:visible` watch 时 suspend/restore |
| `views/library/LibraryReader.vue` | `<Teleport to="body">`;全屏模式浮动关闭按钮(z-index:210);`onMounted` suspend + `onBeforeUnmount` restore |
| `views/cinema/Cinema.vue` | 视频弹窗 `watch(player.visible)` suspend/restore |
| `views/Home.vue` | 拍立得改用 `PhotoViewer`(替换 `el-image-viewer`);点击拍立得播放全部近7天照片(`sevenDayPhotos`),拍立得仅展示7张随机;相册封面改为容器85%+透视厚度+花纹(`::before`/`::after`) |
| `security/SecurityHelper.java` | `currentUser()` 返回前用 JWT 当前家庭覆盖 `familyId`(修复切换家庭后所有控制器拿到主家庭 ID 的 bug) |
| `service/AuthService.java` | `switchFamily` 增加 `invalidateUser(userId)`(修复切换默认家庭不生效的缓存 bug) |
| `service/AlbumService.java` | `detail`/`create`/`addPhoto` 增加 `currentFamilyId` 参数(防御性,配合 SecurityHelper 修复) |
| `App.vue` | 全局页面过渡从 `slide-down` 改为 `fade`(0.5s,无 transform 避免 `position:fixed` 卡片偏移) |
| `styles/main.css` | `.el-overlay` z-index:63(低于光影层 65,高于 AppSidebar 60);`.el-popper` z-index:64 |
| `views/album/AlbumDetail.vue` | 编辑/删除按钮 `@click.stop` 阻止冒泡;`.album-head-actions` 横向排列 |
| `components/SunLightLayer.vue` | 台灯 `lampSwing` keyframes 加 `translateY(-50%)` 修复位置偏移 |
| `vite.config.js` + `package.json` | `vite-plugin-pwa` 升级 0.20.5→1.3.0 修复 `workbox-build` ESM 兼容;移除 `cross-env`/`build:fast` |

##### UI 交互优化

| 文件 | 改动 |
|------|------|
| `styles/main.css` | ElMessageBox 动画:`fade-in-linear-*` transition 覆盖 EP 默认,`.el-overlay-message-box` 加 scale(0.94)+opacity 淡入(与 el-dialog 一致);`.fade-in-linear-enter-active .el-overlay-message-box` 杀 EP 默认 `msgbox-fade-in` keyframes |
| 31 个 `ElMessageBox.confirm` 调用(20 个文件) | 全部加 `closeOnClickModal: true`(点击遮罩关闭) |
| `views/diary/DiaryEdit.vue` | 重写:移除多 textarea 分页,改为单个 `paper-textarea`+`autoResize()` 按整页(18行×28px=504px)自适应增长;新增 `.page-break-bg` 层每 504px 一条深色实线标记分页;日期/时间选择器上下排列(`header-left` flex-column),日期与心情底端对齐、时间与天气底端对齐;picker overlay z-index 降到 61(光影层 65 之下,编辑框之上) |
| 24 个 `el-dialog`(18 个文件) | 全部加 `append-to-body`(修复弹窗遮罩未覆盖导航栏——弹窗渲染在组件内部被 stacking context 困住) |
| `views/music/Music.vue` | 三种上传方式(单曲/专辑文件夹/外链)合并为「上传音乐」下拉菜单;新建歌单+多选按钮移入 `el-tabs__nav-scroll` 内部右侧(absolute 定位) |
| `views/Home.vue` | 首页任务组件过滤 `status !== 'CANCELLED'`(已取消任务不展示;status 是字符串非数字) |
| `views/blog/BlogList.vue` | 代码块 `pre` 深色背景+`white-space: pre-wrap` 自动换行;表格斑马纹+`display:block; overflow-x:auto`;行间距 `line-height: 2.0` |

##### 博客列表迭代

| 文件 | 改动 |
|------|------|
| `BlogController.java` + `BlogService.java` + `BlogMapper.xml` + `BlogMapper.java` | `GET /blog/categories/counts` 按权限全量统计;分类 CRUD 全部基于 `content_blog_category` 表(`parent_id` 自引用树);`renameCategory(id, name, parentId)` 支持改名+移父级+防环+级联更新子分类博客路径;`deleteCategory(id, mode)` 递归删除子分类 |
| `entity/BlogCategory.java` + `mapper/BlogCategoryMapper.java` | 新增实体+Mapper(BaseMapper) |
| `views/blog/BlogList.vue` | 工具栏(搜索→分类级联→标签可搜索→排序图标→统计→写博客);≥1400px 左侧常驻分类面板(独立滚动),<1400px 顶部 `el-cascader`;分类树多级递归(后端返回扁平树);卡片三行布局;hover 编辑/删除;草稿标记;分类弹窗 `el-cascader` 选父级(编辑时排除当前及后代防环) |
| `views/blog/BlogEdit.vue` | 分类选择改 `el-cascader`;新建分类弹窗支持选父级 |
| `api/index.js` | `addCategory(name, parentId)` / `renameCategory(id, name, parentId)` / `deleteCategory(id, mode)` |
| `i18n/zh-CN.js` + `en.js` | 新增 `sortRecent`/`sortViews`/`articlesUnit`/`parentCategory`/`rootCategory` |

**博客分类树规则**:`content_blog_category` 表用 `parent_id` 自引用(NULL=顶级),`name` 只存本级名称;后端 `categories()` 返回扁平树 `{id, name, parentId, path, depth, childCount}`;`content_blog.category` 列存全路径(如 `技术/前端`)用于筛选;`renameCategory(id, name, parentId)` 可改名+移父级(防环校验),级联更新子分类博客路径。前端用 `el-cascader`(`checkStrictly + emitPath: false`)做分类选择/筛选,编辑时排除当前分类及其后代防环。

## 文件存储策略

- **当前阶段(开发期)**:本地磁盘存储(`file.upload-dir`),零成本零内存,FileService 已实现,开箱即用。Nginx `/files/` 托管静态目录(注意负向断言正则 `location ~* ^/(?!files/).+\.(...)$` 排除 /files/)。
  - **路径配置**:`application.yml` 的 `file.upload-dir` 基线为生产路径 `/opt/ihomy/uploads`(Linux);开发环境通过 external.yml 覆盖为 Windows 路径 `D:\WorkSpace\ihomy\uploads`。DB 存的是相对 `/files/` 的完整 URL,与物理根无关,改路径只需改 yml + 移动 uploads 目录。
- **未来对接 NAS**:优先 NFS 挂载方案(把 NAS 共享目录挂到 `/opt/ihomy/uploads`,**代码零改动**)。前提是 NAS 与服务器同内网。详细步骤见 Linux 部署指导附录"对接 NAS 存储"。若 NAS 异地或要公网 CDN:再改 FileService 用 S3 兼容 SDK(NAS/MinIO/OSS 通用),用 `@ConditionalOnProperty` 切换实现,本地实现保留为默认。
- **不要主动改 FileService 的存储实现**,除非用户明确要求接 NAS/OSS。当前本地实现满足需求。
- **统一目录结构(分类目录,无 upload 中间层)**:上传按类型分目录——相册图片→`pictures/{相册名}/{相册ID}_{时间戳}_{文件名}`、视频与海报→`videos/`、音乐(audio/*)→`music/`、电子书→`books/{yyyyMM}/`、通用/头像→`files/{yyyyMM}/`。FileService 提供 `upload(bytes,name,type,albumId,albumName)`(图片带相册名)、`uploadVideo`(影片/海报)、`uploadBook`(电子书)、3 参 `upload`(通用)重载;无相册名时图片平铺到 `pictures/`。DB 存 `/files/...` 完整 URL,与物理根解耦。
- **存储设备**:`sys_storage_device`(family_id 家庭级隔离,name/device_type SYSTEM|NAS|REMOTE|MOUNT/root_path/status/created_by)。`GET /storage/device/list` 首项恒为系统设备(id=0,type=SYSTEM);设备增删改/一键同步需 `@RequirePermission("storage:manage")`(OWNER)。**设备归属=家庭级独立配置**,互不可见。**本期不做网盘**(WebDAV/OSS/S3 暂缓)。
- **资源管理器**:`GET /storage/browse?deviceId&path` + `GET /storage/file?deviceId&path&download`(返回 byte[],media type 猜;下载文件名 URL-encode)。`StorageService.resolveSafe` 用 `normalize()+startsWith` 防路径遍历(越界返回 400,已实测)。
- **一键同步**:`POST /storage/sync {deviceId,includeEmpty}` → `{taskId}`;`GET /storage/sync/progress/{taskId}`。`StorageSyncRunner`(@Async 独立 bean——自调用不生效):按顶层目录建相册(相册名=目录名),`content_photo.source_path`("设备:相对路径")去重防重复,复制到 upload/yyyyMM 结构,完成/失败走 family_notification。进度在内存 ConcurrentHashMap(重启丢失,可接受)。
- **硬删除策略**:删除照片/相册/视频/图书时**物理删除 DB 记录 + 删除磁盘文件**。`FileService.deleteByUrl(url)` 按 `/files/` URL 解析物理路径删文件(外链/空跳过,失败仅告警,带 `normalize()+startsWith` 防越界,顺带尝试清空父目录)。照片删除走 `PhotoMapper.deletePhysicalById`(XML 物理删,绕过全局 logic-delete);相册删除连带照片记录+文件全删(`deletePhysicalByAlbumId`);视频删除**从软删改为硬删** `deletePhysicalById`,并删 `video_url`+`poster`;图书删除硬删 `ContentBookMapper.deletePhysicalById`,并删 `file_url`+`cover_url`。**关键坑**:MyBatis-Plus 全局配 `logic-delete-field: deleted`(`application.yml`),`deleteById` 实为 UPDATE 软删——要物理删必须用自定义 XML `DELETE` 语句。**覆盖范围**:照片/相册/视频/图书四处;博客封面、头像、家庭封面、背景音乐、家谱照片删除时**未**连带删文件(文件成孤儿,可接受,后续按需扩展)。

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

## 规划事项(未实现,排序按推荐优先级)

| 优先级 | 规划 | 要点 |
|--------|------|------|
| P2 | 物品定位-户型图 | 1期(物品清单+搜索)已完成;2期户型图:房间矩形绘制/物品相对坐标摆放,以 room.id 挂载(数据结构已预留) |
| P2 | 用户使用指导 | 新手引导弹窗+帮助页 |
| P2 | 家庭公告/广告位 | 自建家庭公告(不接第三方广告,隐私原因) |
| P2 | 首页组件自适应展示 | 首页栅格(12列×9行)组件按尺寸分级展示。每个功能组件有默认大小(4×5 等),不同尺寸呈现不同信息密度:**h=1**(标题消失,仅展示核心数据:天气仅温度+图标,收支仅结余数字,任务仅数量角标);**h=2-3**(标题+精简列表 2-3 条);**h≥4**(标题+完整列表+详情);**w≤2**(单列窄布局:垂直堆叠条目);**w≥4**(多列网格:相册瀑布/菜谱卡片)。需为每个组件定义 compact/normal/expanded 三档展示模板,CSS 媒体查询+JS 判断 w/h 切换。组件清单:feed(默认4×6)/task(4×2)/today(4×4)/weather(3×4)/anni(1×4)/recipe(4×2)/search(4×3)/wish(4×3)/finance(4×3)/album(4×5)/music(4×3) |
| P3 | 多重人格 | 基于身份标签扩展,一账号多标签可切换发表,会话级 currentLabel(Redis 或前端状态),与家庭切换正交 |
| P3 | AI API 对接 | 统一对接大模型 API(聊天/内容生成),需配置 API Key 与服务商(OpenAI 兼容协议),待细化 |
| P3 | 物品定位-AI 语义 | 3期(待2期后):自然语言"找找我的工具箱"→AI 拆出名称+别名→服务器 SQL 查询;"把工具箱放到门口的柜子最上层抽屉"→AI 按 家/房子/房间/家具/位置 五级解析,缺层追问填满后返回粒度值→服务器拼 INSERT(决策已定,依赖 AI API) |
| P3 | 播放器解码器评估 | 当前音乐/视频用浏览器原生 `<audio>`/`<video>` 标签(硬件解码,性能最优)。**分析结论**:不建议引入 ffmpeg.wasm/howler.js/video.js — 原生方案已覆盖 MP3/AAC/MP4/WebM 主流格式,JS 解码库增加 200KB+ 包体且 CPU 解码远慢于硬件。仅在需要播放原生不支持的格式(如 FLAC/OGG/APE)或需要逐帧分析时才考虑。未来如需引入,优先 video.js(UI 统一)+ hls.js(HLS 流),不引 ffmpeg.wasm |
| P3 | Apple Live Photos 支持 | 上传时读取 EXIF/ContentIdentifier 元数据标记实况照片,数据层关联 JPEG 封面+MOV 短视频(`content_photo` 加 `live_video_url` 字段)。照片展示左上角加实况图标(BADGE),点击查看时播放关联视频(非实况仅展示照片)。iOS Safari 上传时自动拆分 JPEG+MOV;Android/非实况无图标。需前端上传处理+后端元数据解析+展示组件三方协同 |
| P4 | 手机号注册 | 需短信服务商(阿里云等),未接入前不实现(sys_user.phone 字段已存在) |
| P4 | 商业化/多租户 | SaaS 订阅制评估,条件允许再做 |
| P4 | 广告模块 | 家庭私密场景接第三方广告转化低且有隐私争议,优先自建"家庭公告/赞助位" |

> 优先级含义:P1 用户价值高且技术上可行(依赖最少);P2 锦上添花;P3 结构性改动;P4 依赖外部条件(短信商/商业决策)。实现新功能前先 `grep schema.sql + router/` 对照模块种子。

## 文档清单

- `README.md`(启动说明 + Windows 一键启动脚本用法); `Windows部署指导.md` / `Linux部署指导.md`(生产部署 NSSM/systemd/Nginx/Let's Encrypt/Docker Compose)
- `docs/需求规格说明书.docx` — 完整需求文档(功能模块清单+实现方式+未实现规划+数据库设计+接口设计+修订记录)。**唯一正式版**,原 `.md` 已合并删除(内容以业务域重新组织,修正表数量为 53 张/实体 44 个,含移动端兼容性 V8.0 §4.10)
- `docs/UI设计提示词.md` — 沉浸式首页 UI 设计完整规格(可作为 AI 提示词重新生成)
- `scripts/start-all.ps1`(Windows 一键启动前后端,双击 `start.bat` 调用,设 `IHOMY_CONFIG_PATH` 环境变量)/ `start-db.ps1`(Docker 拉起 MySQL+Redis+自动导 schema.sql,端口 6306/6379,与生产一致); `config/mysql/my.cnf`(端口 6306,内存优化,仅 Linux 本机部署用)
- 完整接口清单:见 `docs/需求规格说明书.docx` 第 7 章与各功能小节。代码事实以 `backend/src/main/java` + `resources/schema.sql` 为准,如需检索先 `grep` 再动手。

## 环境检查(参考)

本机已装:JDK 21、Node 20、MySQL 8、python-docx。Maven 用 Wrapper(或 temp 目录 3.9.9)。Redis 本机可能未装(可用 Docker 或 Memurai)。
