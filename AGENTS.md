# AGENTS.md — ihomy 项目规则

> 本文件供 opencode 跨会话加载,记录项目关键决策与约定。新会话启动时会自动读取,无需重复说明背景。
> 修改本文件后立即对所有新会话生效。

> **⚠ 路径拼写警示(遵守以防误写)**:
> - 工作目录绝对路径:`C:\Users\chill\OneDrive\WorkStation\Projects\ihomy`
> - 中间段是 **`WorkStation`(一个词,`W-o-r-k-S-t-a-t-i-o-n`)**,不是 `Work\Station`、不是 `WorkStudio`、也不是 `Work Station`。
> - 每次读/写/移动文件前先逐字核对路径;发现读不到文件时优先怀疑路径拼写而非文件不存在。
> - **⚠ 编码警示(必须遵守)**:含中文的源码/配置/SQL 一律走本工具的 Read/Write/Edit 读写,禁止用 PowerShell `Get-Content`/`Set-Content`/`WriteAllText` 读写(PS 5.1 默认 GBK 会破坏 UTF-8 中文,且 `[IO.File]::WriteAllText` 默认带 BOM 导致 javac 报非法字符)。PowerShell 仅用于:npm/mvn 构建、HTTP 冒烟。
> - **⚠ 数据库写中文警示**:向 MySQL 写入含中文的 SQL 时,**禁止**用 PowerShell 管道 `Get-Content file.sql | docker exec -i mysql mysql ...`(PS 5.1 管道编码非 UTF-8 导致中文乱码)。**正确方式**:① 用本工具 Write 写 SQL 文件(UTF-8 无 BOM)→ `docker cp file.sql ihomy-mysql:/tmp/` → `docker exec ihomy-mysql mysql --default-character-set=utf8mb4 ihomy -e "source /tmp/file.sql"` → 清理临时文件;② 纯 ASCII SQL 可直接 `docker exec mysql -e "..."`;③ 远程用 `scp -P 19068 file.sql root@ihomy.top:/tmp/` → SSH 执行 `mysql -e "source /tmp/file.sql"`。终端显示中文为 `?` 是 GBK 终端问题,不代表存储乱码,用 `python -c "import subprocess; ..."` 验证。

## 项目概述

- **应用名**:ihomy(家庭共用软件)。家庭内部内容共享平台,PC 浏览器 / 安卓 / iOS(均 PWA)。
- **核心功能**:登录注册、博客、日记、相册、纪念日、留言板、放映厅、聊天室、积分商城、任务悬赏、提醒、家庭计划、愿望单、记账、家谱、运维。首页模块化可扩展(后期新增功能只需插入一条 `sys_home_module` 记录)。
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
- **49 张表**,前缀分类:
  - `sys_`(系统/账号/权限/家庭设置/日志/参数/字典,21 张):`sys_user` / `sys_role` / `sys_auth` / `sys_user_role` / `sys_role_auth` / `sys_family_info` / `sys_home_module` / `sys_password_reset_token` / `sys_user_group` / `sys_user_group_member` / `sys_operation_log` / `sys_dict_item` / `sys_parameter` / `sys_storage_device` / `sys_weather_credential` / `sys_weather_location` / `sys_weather_log`
  - `family_`(家庭事务,17 张):`family_anniversary` / `family_notification` / `family_apply` / `family_invitation_code` / `family_checkin` / `family_points_record` / `family_points_product` / `family_points_order` / `family_task` / `family_reminder` / `family_plan` / `family_plan_task` / `family_book_record` / `family_chat_message` / `family_chat_read` / `family_user_label` / `family_tree` / `family_house` / `family_room` / `family_furniture` / `family_item` / `family_music`
  - `content_`(内容类,9 张):`content_blog` / `content_diary` / `content_album` / `content_photo` / `content_comment` / `content_visibility` / `content_like` / `content_video` / `content_video_wish` / `content_wish`
  - **命名规则**:家庭事务业务表一律 `family_` 前缀;内容数据 `content_` 前缀;账号/权限/配置/日志/天气/存储保留 `sys_`。新增表必须遵守。前缀取最顶层祖先类别;上下级关系体现在表名(如 `sys_user_role`)。
- **枚举不再用数字**:状态/类型字段一律大写英文单词(`PUBLISHED/DRAFT/PUBLIC/FAMILY/ACTIVE...`),含义存字典表 `sys_dict_item`,Java 常量集中于 `common/DictConst.java`,前端映射 `utils/dict.js`。**不要写回 0/1/2 判断**。
- **注意**:`content_blog/diary/photo/video/wish` 5 张内容表 `visibility` 列为 `VARCHAR(20) DEFAULT 'FAMILY'`(PRIVATE仅自己/FAMILY家庭可见/PUBLIC公开),schema.sql 与 live DB 已对齐(曾误写 TINYINT)。
- 权力 4 角色:OWNER/MEMBER/CHILD/GUEST + OPS(运维,不属任何家庭)。同一用户不同家庭可不同角色(`sys_user_role.family_id` 区别)。
- **新增带 `@RequirePermission` 接口前**:确保 auth_code 进 `sys_auth` + `sys_role_auth` 种子(OWNER 豁免,MEMBER 显式授权),否则 403。

## 代码结构

```
backend/ (Spring Boot 3, JDK 21, 包 com.ihomy)
  src/main/java/com/ihomy/
    IhomyApplication.java       # 主类 @MapperScan("com.ihomy.mapper")
    common/      # Result统一响应/ResultCode/BizException/GlobalExceptionHandler/DictConst(字典常量)/SolarUtil(NOAA太阳位置算法)/AesUtil(凭证加密)/UserNames
    config/      # SecurityConfig/CorsConfig/MybatisPlusConfig/Knife4jConfig/WebMvcConfig/WebSocketConfig/SqlStatementLog/ExternalConfigLoader(外挂配置加载)
    security/    # JwtUtils(JWT含familyId+role+permissions+isOps)/JwtAuthenticationFilter/LoginUser/SecurityHelper/OpsAccessFilter/TraceIdFilter
    annotation/  # @RequirePermission / @OperationLog
    aspect/      # RequirePermissionAspect(权限AOP) / OperationLogAspect(操作日志AOP)
    filter/      # WsHandshakeInterceptor(WebSocket JWT 验证)
    entity/      # 49张表对应实体(含 @TableLogic 软删/@TableId(type=INPUT)等)
    mapper/      # MyBatis-Plus BaseMapper 接口(自定义 SQL 全部放 resources/mapper/*.xml,接口不写 @Select/@Update 注解,参数统一 @Param)
    service/     # 32 个 @Service 类(单实现无接口层)
    controller/  # 30 个 Controller
    dto/         # 请求/响应 DTO
    websocket/   # ChatWebSocketHandler(原生 WebSocket 聊天室)
  src/main/resources/
    application.yml     # 端口8080, context-path=/api, 连接用 ihomy 账号; mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml; **生产配置**(MySQL 6306/Redis 6379/Linux 路径),jar 内嵌即生产,部署无需外部覆盖
    external.yml.template  # 外挂配置模板(IHOMY_CONFIG_PATH 指定路径,含 MySQL/Redis 密码 + JWT 密钥 + 天气凭证,ENC() 加密)
    mapper/*.xml        # 每个 Mapper 接口一个同名 XML(namespace=接口全限定名)
    schema.sql          # 建库+建号+建表(49张)+种子数据
  mvnw / mvnw.cmd       # Maven Wrapper,无需单独装 Maven
frontend/ (Vue3 + Vite + PWA + Element Plus + Pinia)
  src/
    api/request.js  # axios + JWT header + 401 自动刷新token
    api/index.js    # 全部模块 API 分组导出(28 个 Api 对象:public/auth/home/blog/diary/file/member/anniversary/album/photo/like/comment/notification/family/profile/video/points/task/reminder/plan/wish/music/book/ops/tree/chat/storage/item)
    stores/user.js  # 登录状态 + hasPerm/isOps/isPureOps; stores/app.js 首页聚合(family/modules/photos/stats)
    router/         # 登录守卫 + scrollBehavior(返回回顶部);含 /ops 运维护卫, /chat 需登录; 25 个路由
    i18n/           # vue-i18n 中英(applyLocale 切换)
    theme/          # applyTheme/loadTheme(明暗模式,只 light/dark)
    utils/dict.js   # 枚举词条中文映射(与后端 DictConst 对应)
    utils/windowLight.js  # getSunScene(sunInfo,slotIndex)+currentSlotIndex()+makeRays():体积光调色板/光束/阴影参数
    utils/useSunLight.js  # 全局光影状态(provide/inject):sunScene/lampMode/shadowEnabled/weatherEffectEnabled/blobsEnabled/lightTestMode/loadWeather
    utils/useDragResize.js # 可拖拽面板组合式函数(zIndex+bringToFront+边界clamp+localStorage持久化)
    components/     # AppSidebar(全局导航)/BackToTop/Breadcrumb/AvatarCropper/InstallPrompt/SiteFooter(备案号)/SunLightLayer(全局光影层)/SyncDialog(存储同步进度)
    styles/main.css # CSS 变量 + 全局样式 + 深色模式覆写 + ElMessage/ElNotification 增强
    views/          # 25 个页面:Home(沉浸式首页)/Login/Member/Settings/Anniversary/album(Album/AlbumDetail)/cinema/Cinema/diary/DiaryList/blog(BlogList/BlogDetail/BlogEdit)/points/Points/task/Task/reminder/Reminder/plan/Plan/wish/Wish/book/Book/chat/Chat/tree/Tree/cascade/Cascade/ops/Ops/storage/Storage/item/Item/kitchen/Kitchen
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
- 有 jar 锁先 `taskkill /F /IM java.exe` 再打包(运行中 java 锁定 logs/ihomy.log 导致 clean 失败)。
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
| 背景音乐 | MusicController | — | family_music | `sys_family_info.music_url/music_title`;家庭共享歌单 |

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
| 体积光 | `utils/windowLight.js` + `components/SunLightLayer.vue` | 丁达尔效应:7 条光束+光源辉光+窗框阴影(上下分层)+暗角+灰尘;dayProgress 驱动旋转/颜色/强度;灰阶 darken 幂等防叠加;夜间光柱 transparent |
| 台灯 | `utils/useSunLight.js` | 3 态(auto/on/off)+钟摆运动(8s 周期)+色温/亮度可调;mask 祛除阴影(GSAP 2s 补间);左上黄金分割点 |
| 天气特效 | `utils/useSunLight.js` + `SunLightLayer.vue` | `codeToPrecipLevel` 1-6 级;snowParticles/rainParticles;cloudFlicker GSAP 4-8s;weatherShadowOpacity;weatherMultiplier(晴 1.0/多云 0.55/雨 0.25/雪 0.4) |
| 天气代理 | `service/WeatherService.java` | 和风天气 JWT(Ed25519)身份认证(JDK 21 原生);凭证四件套(优先 DB sys_weather_credential);月度配额 49999(Redis 计数器);sys_weather_log 调用日志;Redis 缓存(now 30m/forecast 30m/warning 5m 等);`GET /public/weather` 简版+`/public/weather/detail` 聚合+`/ops/weather/quota` |
| 光照测试 | `utils/useSunLight.js` + `Home.vue` | `lightTestMode` 1 分钟循环 288 时隙;后退/暂停/前进/停止+天气控制+色温/亮度滑块;停止=重置真实时间 |
| 可拖拽面板 | `utils/useDragResize.js` | 5 个面板(feed/task/weather/anniversary/today);zIndex+bringToFront;边界 clamp+localStorage 持久化 |
| 和风图标 | `public/qweather-icons/` | npm 包 qweather-icons;`<i class="qi-{iconCode}">`;iconCode 来自和风 API now.icon |

### 8. 物品定位

| 模块 | Controller | Service | 关键表 | 要点 |
|------|-----------|---------|--------|------|
| 物品清单 | ItemController | ItemService | family_house/family_room/family_furniture/family_item | 五级粒度(家>房子>房间>家具>位置,多套房多楼层);CRUD+跨级搜索;2 期户型图/3 期 AI 语义待做 |

### 9. 国际化与主题

| 模块 | 文件 | 要点 |
|------|------|------|
| i18n | `i18n/` + `utils/dict.js` | vue-i18n 中英;applyLocale 切换;DictConst 后端常量对应 |
| 主题 | `theme/index.js` | 只 light/dark;applyTheme/loadTheme;双层伪元素背景 1s 过渡;手动切主题取消日出日落自动 |
| 身份标签 | ProfileController | family_user_label(user_id/family_id/label/color,每家庭一套) |
| 字典表 | — | sys_dict_item 16 组;状态/类型字段英文单词化 |

## 设计规范(统一实现,避免多种方式)

### 后端规范

1. **Controller-Service-Mapper 三层**:Controller 仅参数校验+调 Service+返回 Result;Service 单实现无接口层(V3.8 起);Mapper 接口仅 BaseMapper,自定义 SQL 全部放 `resources/mapper/*.xml`(接口不写 `@Select/@Update` 注解,参数统一 `@Param`)。
2. **统一响应**:`Result.ok(data)` / `Result.error(ResultCode.XXX)`;异常走 `BizException(ResultCode)` + `GlobalExceptionHandler`。
3. **权限**:`@RequirePermission("code")` + `RequirePermissionAspect`;OWNER 恒真;新增接口前确保 auth_code 进 `sys_auth`+`sys_role_auth` 种子。
4. **操作日志**:`@OperationLog` 注解 + `OperationLogAspect` 异步落库;含 traceId(`TraceIdFilter` 生成 16 位 UUID 短串,写入 MDC + 响应头 `X-Trace-Id`)。
5. **SQL 日志**:`mybatis-plus.log-impl=SqlStatementLog`,打印 SQL 语句+参数,过滤结果集行。
6. **软删**:`@TableLogic deleted`;**物理删必须用自定义 XML DELETE 语句**(MP `deleteById` 实为 UPDATE)。目前仅照片/相册/视频三处硬删。
7. **家庭隔离**:所有业务数据带 `family_id`;JWT familyId 为快照,refresh 时按优先级解析;跨家庭访问返回 NOT_FOUND。
8. **多家庭**:`sys_user_role.family_id` 区分;当前家庭存 Redis;`default_family_id` 用户设置的默认家庭。

### 前端规范

1. **API 分组**:`api/index.js` 按模块导出 `xxxApi` 对象;统一走 `api/request.js`(axios+JWT+401 自动刷新)。
2. **状态管理**:Pinia;`stores/user.js`(登录+权限)、`stores/app.js`(首页聚合)。
3. **路由守卫**:`meta.public` 无需登录;`meta.ops` 需 `ops:view`;纯 OPS 账号只能访问 `/ops`。
4. **样式**:CSS 变量(`main.css`)+ 深色模式 `html.dark` 覆写;**不显式声明 serif 字体**,继承 body sans-serif。
5. **图标**:Element Plus `el-icon`(线性图标);**Setting/Monitor 图标用内联 SVG 替代**(复杂 path 在 100% 缩放触发子像素光栅化开销,见性能优化博客 id=18)。
6. **动画**:GSAP 入场;`transform: translateZ(0)` 隔离合成层;`contain: layout style` 隔离布局;避免 `background-attachment: fixed`(性能杀手)。
7. **毛玻璃**:`backdrop-filter: blur(24px) saturate(1.1)`;子元素 hover 用 `transform` 而非 `box-shadow`(避免触发 backdrop-filter 重算)。
8. **可拖拽面板**:`useDragResize` 组合式函数;5 个面板各自实例;位置/大小持久化 localStorage。
9. **光影层全局化**:`SunLightLayer` + `AppSidebar` + `SiteFooter` 在 `App.vue` 全局挂载;`useSunLight` provide/inject 共享状态。
10. **i18n**:所有用户可见文本用 `$t('key')`;中英双语;`utils/dict.js` 枚举映射。

### 性能优化要点(已踩坑)

- **100% 缩放卡顿根因**:Element Plus `Setting`/`Monitor` 图标 SVG path 过于复杂,hover 时子像素光栅化开销大 → 用内联 SVG 替代(详见博客 id=18)。
- **backdrop-filter + overflow:auto 子元素**:毛玻璃父元素 + 子元素滚动 = 性能炸弹 → `transform: translateZ(0)` 隔离合层。
- **背景随滚动**:`.bg-blobs` 用 `position: fixed` 不随页面滚动;移除 `background-attachment: fixed`。
- **关闭效果时跳过定时器**:`flickerTimer` 在所有开关关闭时 return,避免持续触发 sunScene 重写。

## 文件存储策略

- **当前阶段(开发期)**:本地磁盘存储(`file.upload-dir`),零成本零内存,FileService 已实现,开箱即用。Nginx `/files/` 托管静态目录(注意负向断言正则 `location ~* ^/(?!files/).+\.(...)$` 排除 /files/)。
  - **本机 Windows 默认**:`D:/WorkSpace/ihomy/uploads`(application-dev.yml `file.upload-dir`);生产 Linux 用 `/opt/ihomy/uploads`(application.yml)。改路径只需改 yml + 移动 uploads 目录,DB 存的是相对 `/files/` 的完整 URL,与物理根无关。
- **未来对接 NAS**:优先 NFS 挂载方案(把 NAS 共享目录挂到 `/opt/ihomy/uploads`,**代码零改动**)。前提是 NAS 与服务器同内网。详细步骤见 Linux 部署指导附录"对接 NAS 存储"。若 NAS 异地或要公网 CDN:再改 FileService 用 S3 兼容 SDK(NAS/MinIO/OSS 通用),用 `@ConditionalOnProperty` 切换实现,本地实现保留为默认。
- **不要主动改 FileService 的存储实现**,除非用户明确要求接 NAS/OSS。当前本地实现满足需求。
- **统一目录结构(分类目录,无 upload 中间层)**:上传按类型分目录——相册图片→`pictures/{相册名}/{相册ID}_{时间戳}_{文件名}`、视频与海报→`videos/`、音乐(audio/*)→`music/`、通用/头像→`files/{yyyyMM}/`。FileService 提供 `upload(bytes,name,type,albumId,albumName)`(图片带相册名)、`uploadVideo`(影片/海报)、3 参 `upload`(通用)重载;无相册名时图片平铺到 `pictures/`。DB 存 `/files/...` 完整 URL,与物理根解耦。
- **存储设备**:`sys_storage_device`(family_id 家庭级隔离,name/device_type SYSTEM|NAS|REMOTE|MOUNT/root_path/status/created_by)。`GET /storage/device/list` 首项恒为系统设备(id=0,type=SYSTEM);设备增删改/一键同步需 `@RequirePermission("storage:manage")`(OWNER)。**设备归属=家庭级独立配置**,互不可见。**本期不做网盘**(WebDAV/OSS/S3 暂缓)。
- **资源管理器**:`GET /storage/browse?deviceId&path` + `GET /storage/file?deviceId&path&download`(返回 byte[],media type 猜;下载文件名 URL-encode)。`StorageService.resolveSafe` 用 `normalize()+startsWith` 防路径遍历(越界返回 400,已实测)。
- **一键同步**:`POST /storage/sync {deviceId,includeEmpty}` → `{taskId}`;`GET /storage/sync/progress/{taskId}`。`StorageSyncRunner`(@Async 独立 bean——自调用不生效):按顶层目录建相册(相册名=目录名),`content_photo.source_path`("设备:相对路径")去重防重复,复制到 upload/yyyyMM 结构,完成/失败走 family_notification。进度在内存 ConcurrentHashMap(重启丢失,可接受)。
- **硬删除策略**:删除照片/相册/视频时**物理删除 DB 记录 + 删除磁盘文件**。`FileService.deleteByUrl(url)` 按 `/files/` URL 解析物理路径删文件(外链/空跳过,失败仅告警,带 `normalize()+startsWith` 防越界,顺带尝试清空父目录)。照片删除走 `PhotoMapper.deletePhysicalById`(XML 物理删,绕过全局 logic-delete);相册删除连带照片记录+文件全删(`deletePhysicalByAlbumId`);视频删除**从软删改为硬删** `deletePhysicalById`,并删 `video_url`+`poster`。**关键坑**:MyBatis-Plus 全局配 `logic-delete-field: deleted`(`application.yml`),`deleteById` 实为 UPDATE 软删——要物理删必须用自定义 XML `DELETE` 语句。**覆盖范围**:仅照片/相册/视频三处;博客封面、头像、家庭封面、背景音乐、家谱照片删除时**未**连带删文件(文件成孤儿,可接受,后续按需扩展)。

## 配置与加密

- **外挂配置**:`IHOMY_CONFIG_PATH` 环境变量指定 yml 路径,`ExternalConfigLoader`(EnvironmentPostProcessor)在 Spring Boot 启动早期加载,注入到 Environment 最高优先级(覆盖 application.yml + profile yml)。含:MySQL 密码、Redis 密码、邮件 SMTP、天气四件套。
- **DB/Redis/邮件密码明文**:避免鸡生蛋(DB 未连上无法读盐值解密)。
- **业务凭证(天气私钥)AES-GCM 加密**:`AesUtil`(PBKDF2WithHmacSHA256 派生密钥 100000 次 + 256bit + GCM 128bit tag);密文格式 `ENC(Base64(iv+cipher+tag))`;盐值 16 字节 Base64。
- **盐值存 DB**:`sys_parameter` 表(name/value),盐值 key=`aes-salt`,首次启动 `ParameterService.getAesSalt()` 自动生成并入库(优先环境变量 `IHOMY_AES_SALT`),之后缓存内存。
- **WeatherService 改造**:`loadCredential()` 读到的私钥若 `ENC(...)` 包裹,调 `parameterService.decrypt()` 解密;DB 和 yml 两条路径都支持。
- **OPS 加密接口**:`GET /api/ops/crypto/encrypt?plaintext=xxx` 生成密文,`GET /api/ops/crypto/decrypt?ciphertext=ENC(xxx)` 验证解密(均 @RequirePermission("ops:view"))。
- **外挂模板**:`backend/src/main/resources/external.yml.template`(复制为 external.yml 填真实凭证,设环境变量)。
- **profile 化**:`application.yml` 即生产配置(MySQL 6306/Redis 6379/Linux 路径/captcha 空/天气留空),jar 内嵌即生产,部署无需外部配置覆盖;`IHOMY_CONFIG_PATH` 指向 external.yml 覆盖敏感配置。

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
| P1 | 存储管理-存量迁移 | 设备/文件浏览器/一键同步已完成;存量 `/uploads/` 旧文件重归档(移入 upload/yyyyMM 结构并更新 DB 路径)待做 |
| P2 | 物品定位-户型图 | 1期(物品清单+搜索)已完成;2期户型图:房间矩形绘制/物品相对坐标摆放,以 room.id 挂载(数据结构已预留) |
| P2 | 用户使用指导 | 新手引导弹窗+帮助页 |
| P2 | 家庭公告/广告位 | 自建家庭公告(不接第三方广告,隐私原因) |
| P3 | 多重人格 | 基于身份标签扩展,一账号多标签可切换发表,会话级 currentLabel(Redis 或前端状态),与家庭切换正交 |
| P3 | AI API 对接 | 统一对接大模型 API(聊天/内容生成),需配置 API Key 与服务商(OpenAI 兼容协议),待细化 |
| P3 | 物品定位-AI 语义 | 3期(待2期后):自然语言"找找我的工具箱"→AI 拆出名称+别名→服务器 SQL 查询;"把工具箱放到门口的柜子最上层抽屉"→AI 按 家/房子/房间/家具/位置 五级解析,缺层追问填满后返回粒度值→服务器拼 INSERT(决策已定,依赖 AI API) |
| P4 | 手机号注册 | 需短信服务商(阿里云等),未接入前不实现(sys_user.phone 字段已存在) |
| P4 | 商业化/多租户 | SaaS 订阅制评估,条件允许再做 |
| P4 | 广告模块 | 家庭私密场景接第三方广告转化低且有隐私争议,优先自建"家庭公告/赞助位" |

> 优先级含义:P1 用户价值高且技术上可行(依赖最少);P2 锦上添花;P3 结构性改动;P4 依赖外部条件(短信商/商业决策)。实现新功能前先 `grep schema.sql + router/` 对照模块种子。

## 文档清单

- `README.md`(启动说明 + Windows 一键启动脚本用法); `Windows部署指导.md` / `Linux部署指导.md`(生产部署 NSSM/systemd/Nginx/Let's Encrypt/Docker Compose)
- `docs/需求规格说明书.docx` — 完整需求文档(功能模块清单+实现方式+未实现规划+数据库设计+接口设计)
- `docs/UI设计提示词.md` — 沉浸式首页 UI 设计完整规格(可作为 AI 提示词重新生成)
- `scripts/start-all.ps1`(Windows 一键启动前后端,双击 `start.bat` 调用,设 `IHOMY_CONFIG_PATH` 环境变量)/ `start-db.ps1`(Docker 拉起 MySQL+Redis+自动导 schema.sql,端口 6306/6379,与生产一致); `config/mysql/my.cnf`(端口 6306,内存优化,仅 Linux 本机部署用)
- 完整接口清单:见 `docs/需求规格说明书.docx` 第 7 章与各功能小节。代码事实以 `backend/src/main/java` + `resources/schema.sql` 为准,如需检索先 `grep` 再动手。

## 环境检查(参考)

本机已装:JDK 21、Node 20、MySQL 8、python-docx。Maven 用 Wrapper(或 temp 目录 3.9.9)。Redis 本机可能未装(可用 Docker 或 Memurai)。
