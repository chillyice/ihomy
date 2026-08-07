# AGENTS.md — ihomy 项目规则

> 本文件供 opencode 跨会话加载,记录项目关键决策与约定。新会话启动时会自动读取,无需重复说明背景。
> 修改本文件后立即对所有新会话生效。

## 项目概述

- **应用名**:ihomy(家庭共用软件)。面向家庭内部成员的内容共享平台,支持 PC 浏览器 / 安卓 / iOS 三端。
- **核心功能**:用户登录、博客、生活日志,首页模块化可扩展(后期新增功能只需插入一条 `home_module` 记录)。
- **技术栈**:前端 Vue3 + Vite + PWA;后端 Spring Boot 3 + MyBatis-Plus + MySQL 8 + Redis;对象存储可选 OSS/MinIO。

## 工作目录

项目位于单一目录(已从双目录合并为单目录):

| 目录 | 用途 |
|------|------|
| `C:\Users\chill\OneDrive\WorkStation\Projects\ihomy` | 唯一工作目录(代码编辑 + 构建验证 + 部署文档) |

- 所有代码编辑、编译、构建验证都在此目录进行。
- 部署指导文档 `Windows部署指导.md`、`Linux部署指导.md` 也在此目录。
- Nginx 静态根指向 `frontend/dist`,构建后直接生效(Ctrl+Shift+R 刷新浏览器)。
- 旧目录 `C:\Users\chill\Documents\Default Project` 已废弃,不再使用。

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
- **23 张表(V3.2)**,按前缀分类:
  - `sys_`(系统管理类,14 张):`sys_user` / `sys_role` / `sys_auth` / `sys_user_role` / `sys_role_auth` / `sys_family_info` / `sys_home_module` / `sys_invitation_code` / `sys_password_reset_token` / `sys_user_group` / `sys_user_group_member` / `sys_anniversary` / `sys_notification` / `sys_operation_log`
  - `content_`(内容类,9 张):`content_blog` / `content_diary` / `content_album` / `content_photo` / `content_comment` / `content_visibility` / `content_like` / `content_video` / `content_video_wish`
- **家庭纪念日(V2.2)**:独立表 `sys_anniversary`,`user_id` 关联用户(可空=家庭级纪念日),`calendar` 字段区分 `solar`(阳历)/`lunar`(农历),农历转公历用 Hutool `ChineseDate`(`getGregorianMonth()` 为 0-based,需 +1)。倒计时由 `HomeStatsService` 计算,`/public/home` 的 `stats.upcomingEvents` 返回最近 3 个。
- **纪念日管理(V2.2)**:`AnniversaryController`(`GET /anniversary/list` 访客可读、增删改需登录)。全员可增删改本家庭纪念日;支持阳历/农历+闰月+关联成员(可空)+每年重复。
- **相册/照片管理(V2.2)**:`content_album` + `content_photo`(软删 `deleted`)。`AlbumController`(`/album/list`、`/album/{id}` 详情、增删改);`PhotoController`(批量上传 `POST /album/{id}/photos` 字段 `files`,改备注 `PUT /photo/{id}`,删除 `DELETE /photo/{id}`)。**权限**:成员可上传/管理自己创建或上传的相册/照片,OWNER 可管理任何;访客仅可读**默认家庭**的 `public` 相册(默认家庭由 `FamilyMapper.selectDefault()` 取)。`MemberController`(`GET /member/list`)返回成员+`role_code`,供纪念日/相册绑定下拉。
- **导航方案(V2.2)**:`AppHeader.vue` 已全局化到 `App.vue`(原在 Home.vue 内),新增全局 `BackToTop.vue` 回到顶部、`Breadcrumb.vue` 面包屑(首页/列表/当前),`router/index.js` 配置 `scrollBehavior`(返回时回顶部)。首页数据集中到 `stores/app.js`(family/modules/photos/stats + init/reset),Home.vue 只读 store 不再各自拉取。
- **V3.0 核心能力(已实施)**:
  - **统一内容点赞/评论**:`content_like`(content_type+content_id+user_id+family_id,UNIQUE 防重复)+ `content_comment`(parent_id 回复树)。`LikeController`(`POST /like/toggle`、`GET /like/state`)、`CommentController`(`GET /comment/list` 树、`POST /comment`、`DELETE /comment/{id}`)。点赞/评论仅限同家庭内容(跨家庭返回 NOT_FOUND)。删除评论:OWNER 或作者本人。
  - **站内通知**:`sys_notification`。`NotificationService`(create/list/unreadCount/markRead/markAllRead),评论/回复自动通知被回复者与内容作者。`NotificationController`。前端 `AppHeader.vue` 铃铛下拉+已读。
  - **成员管理(邀请制)**:`MemberController`(`GET /member/list` 成员+角色;`PUT /member/{id}/role` 改角色;`DELETE /member/{id}` 移出;`POST /member/invite` 生成码;`GET /member/invite` 码列表)。**加入家庭走注册带邀请码**:`RegisterDTO.inviteCode` 可选,`AuthServiceImpl.register` 检测到邀请码则直接加入受邀家庭(预设角色,不新建家庭),否则自建家庭并绑定 OWNER。
  - **家庭设置 / 个人资料 / 操作日志**:`FamilyController`(`GET`/`PUT /family`,@family:manage)、`ProfileController`(`GET`/`PUT /profile`)、`LogController`(`GET /log` 分页,@log:view)。`sys_user`(birthday,gender)、`sys_family_info`(description,is_public)、`content_photo`(taken_at,location) 均已落库。
  - **权限注解**:`@RequirePermission("code")` + AOP `RequirePermissionAspect`(不通过抛 FORBIDDEN)。`SecurityHelper.hasPermission` 对 OWNER 恒真,其余查 `SysRoleMapper.selectAuthCodesByUserAndFamily`。`sys_auth` 种子含 `comment:create/delete`、`invite:create`、`log:view`、`family:manage`、`user:manage` 等。
  - **JWT 携带 familyId**:`JwtUtils.generateAccessToken(userId, username, role, familyId)` 写入 claim;`JwtAuthenticationFilter` 用它构造 `LoginUser(familyId)`(缺失时回退查库),保证 MEMBER 权限码按家庭解析正确。
- **表命名规则**:前缀区分类别;上下级关系体现在表名(如 `sys_user_role`);前缀取最顶层祖先类别。新增表必须遵守此规则。
- **放映厅(V3.2)**:`content_video` + `content_video_wish`。`content_video` 字段含豆瓣式属性:`media_type`(movie/series/other)、`genres`(题材,逗号分隔)、`region`、`year`、`language`、`duration`(分钟)/`episodes`(剧集数)、`director`、`actors`、`rating`(0-10 一位小数)、`intro`、`poster`、`video_url`(经 FileService 上传)、`uploader_id`、`family_id`、`visibility`、软删。`content_video_wish`(想看):`title`/`genres`/`reason`/`status`(0 待入库 1 已入库)/`requester_id`/`family_id`。`VideoController`(`GET /video/list` 按 family 过滤+keyword/mediaType 搜索;`POST /video/upload` 上传文件返回 `{url}`;`POST /video`/`PUT/DELETE /video/{id}` 增删改,改删校验上传者或 OWNER;`POST /video/wish` 提交想看、`GET /video/wish/list`、`PUT /video/wish/{id}/done` 标记入库、`DELETE /video/wish/{id}`)。前端 `/cinema` 页(`views/cinema/Cinema.vue`):视频库(海报网格+内嵌播放)+ 想看列表两个标签页,上传对话框含豆瓣属性表单,想看可选题材标签(`genresOptions` 30 项豆瓣常见分类)。**上传限制**:后端 `multipart max-file-size 500MB` + nginx `client_max_body_size 500m`(视频大文件必需,已调)。
- **模块重命名(V3.2)**:博客/日记本/相册/纪念日(原"家庭博客/生活日志/家庭相册/家庭纪念日"),DB `sys_home_module.title` 与前端页面标题已同步;`Anniversary.vue` 内部"家庭纪念日"业务文案保留。
- **默认可见范围=全家可看(V3.3)**:博客/日记发布默认 `visibility=3`(家庭可见),前后端一致(BlogServiceImpl/DiaryServiceImpl null→3,BlogEdit.vue/DiaryList.vue 默认 3 并提供 0 仅自己/3 家庭可见 选择)。历史数据已统一 UPDATE 为 3。放映厅固定 3;照片按相册类型 public→4/private→3。**注意**:visibility 枚举 `0仅自己/1指定成员/2指定群组/3家庭可见/4公开`,前端不要再出现"1=家庭可见"这类错误映射(旧 DiaryList 曾用 1,已修正为 3)。
- **家庭封面导航入口已移除(V3.3)**:`sys_home_module` 中 cover 模块 `enabled=0`(schema.sql 种子已同步),AppHeader 导航不再显示"家庭封面";首页顶部大封面区(`family.coverImage`)保留,家庭设置页仍可配置封面。
- **RBAC 权限模型**:用户-角色-权限三层(`sys_user` → `sys_user_role` → `sys_role` → `sys_role_auth` → `sys_auth`)。`sys_user` 无 `role` 字段,角色通过关联表查询。预设 4 角色:OWNER/MEMBER/CHILD/GUEST。同一用户在不同家庭可有不同角色(`sys_user_role.family_id` 区分)。
- `sys_operation_log`(系统操作日志表):**已实现**(V2.0)AOP + `@OperationLog` 注解自动记录。记录范围:登录/登出、增删改内容、成员管理、配置变更。仅家长可查询(`LogController` GET `/log`,@log:view)。
- **链路追踪(V3.0.1)**:`TraceIdFilter`(servlet 首个过滤器)为每个请求生成 `traceId`(16 位 UUID 短串),写入 MDC `traceId` + 响应头 `X-Trace-Id`(也接受入站 `X-Trace-Id` 透传)。日志模式为 `%X{traceId:-}`,控制台与文件(`./logs/ihomy.log`)均带 traceId。`sys_operation_log` 新增 `trace_id` 字段(AOP 在该线程取 MDC 落库),保证一次操作在日志与 DB 里可跨模块/跨步骤串联。
- **SQL 日志(V3.0.2)**:`mybatis-plus.log-impl` 用自定义 `com.ihomy.config.SqlStatementLog`:打印 SQL 语句与参数(`==> Preparing / Parameters`),**过滤结果集行**(`<== Columns/Row/Total`,按 `s.startsWith("<== ")` 丢弃),既保留 SQL 调试信息又减小日志占用。
- **上传文件访问(nginx)**:`nginx.conf` 增加 `location /files/ { alias <仓库>/uploads/; }`。**注意**:图片扩展名正则 location 会用 `root` 覆盖 `/files/` 别名(导致的路径解析),必须写成 `location ~* ^/(?!files/).+\.(...)$`,用负向断言排除 `/files/`,否则上传图片 404。
- **头像/封面走上传**:`Settings.vue` 个人头像(`profile.avatar`)与家庭封面(`family.coverImage`)由 URL 输入改为 `el-upload`+`http-request` 调 `POST /file/upload`(返回 `{url}`),保存时随 `PUT /profile`、`PUT /family` 落库。后台 `avatar`/`coverImage` 仍为 URL 字符串字段,无需改接口。
- 默认管理员:`admin / admin123`(BCrypt),登录后应改密。初始化时自动绑定 OWNER 角色。

## 代码结构

```
backend/  (Spring Boot 3, JDK 17/21, 包 com.ihomy)
  src/main/java/com/ihomy/
    IhomyApplication.java        # 主类(@MapperScan("com.ihomy.mapper"))
    common/        # Result 统一响应、ResultCode、BizException、GlobalExceptionHandler
    config/        # SecurityConfig, CorsConfig, MybatisPlusConfig, Knife4jConfig, WebMvcConfig
    security/      # JwtUtils(JWT含familyId claim), JwtAuthenticationFilter, LoginUser, SecurityHelper(hasPermission), UserDetailsServiceImpl
    annotation/    # RequirePermission, OperationLog
    aspect/        # RequirePermissionAspect, OperationLogAspect
    entity/        # Family, SysUser, Blog, Diary, HomeModule, Anniversary, Album, Photo, Comment, ContentLike, Notification, InvitationCode, Video, VideoWish
    mapper/        # MyBatis-Plus BaseMapper 接口（自定义 SQL 一律放 resources/mapper/*.xml，接口不写 @Select/@Update 注解，参数统一 @Param）
    service/       # 接口 + 具体类（Blog/Diary 为 接口+impl；Album/Anniversary/HomeStats/Comment/Notification/MemberManagement/ContentLike/Video 为具体 @Service 类实现）
    controller/    # Auth, Public, File, Home, Blog, Diary, Anniversary, Album, Photo, Member, Like, Comment, Notification, Family, Profile, Log, Video
    dto/           # Login/Register(inviteCode)/Blog(tags)/Diary/HomeModule/Anniversary/Album/Photo/Comment/Role/Family/Profile/Video/VideoWish
  src/main/resources/
    application.yml    # 端口8080, context-path=/api, 连接用 ihomy 账号; mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml
    mapper/            # MyBatis XML（每个 Mapper 接口一个同名 XML,namespace=接口全限定名）
    schema.sql         # 建库+建号+建表+初始数据
  mvnw / mvnw.cmd      # Maven Wrapper,无需单独装 Maven

frontend/  (Vue3 + Vite + PWA)
  src/
    main.js, App.vue (全局 AppHeader + router-view + BackToTop)
    api/           # request.js(axios+JWT拦截+自动刷新), index.js(各模块API: public/home/blog/diary/file/member/anniversary/album/photo/like/comment/notification/family/profile/log/video)
    stores/        # user.js(Pinia 用户状态), app.js(首页聚合: family/modules/photos/stats)
    router/        # 登录守卫 + scrollBehavior;新增 /anniversary /album /album/:id /settings /cinema(public)
    components/    # AppHeader(通知铃铛+设置下拉), BackToTop, Breadcrumb, AlbumCarousel, HomeStatsBar, ActivityFeed
    styles/main.css
    views/         # Login, Home(读 appStore), blog/(List/Detail/Edit——Detail含点赞+评论树+标签), diary/DiaryList(多图), Member(成员管理+邀请码),
                   # Settings(资料+家庭设置+OWNER操作日志), Anniversary, album/Album, album/AlbumDetail(照片墙+元信息), cinema/Cinema(放映厅), More(模块分类)
  vite.config.js   # PWA + 代理 /api -> :8080 + ElementPlus/Vant 按需
  public/favicon.svg
```

## 构建与验证命令

后端(在 backend 目录):
```powershell
.\mvnw.cmd -B clean package -DskipTests      # 编译+打jar,产物 target/ihomy-backend.jar
.\mvnw.cmd -B clean compile -DskipTests       # 仅编译验证
.\mvnw.cmd spring-boot:run                     # 开发运行(端口8080)
```
- 临时 Maven(本机未装 mvn):`C:\Users\chill\AppData\Local\Temp\opencode\apache-maven-3.9.9\bin\mvn.cmd`
- JAVA_HOME:`C:\Program Files\Java\jdk-21`(JDK 21 已装)

前端(在 frontend 目录):
```powershell
npm install        # 首次
npm run dev        # 开发(端口5173,代理/api到8080)
npm run build      # 生产构建,产物 dist/,含 PWA service worker
```

## 统一响应与鉴权

- 响应结构:`{ code: 0, message: "success", data: ... }`(`code=0` 为成功)。
- 登录下发 access token(2h) + refresh token(7d,存 Redis 黑名单实现登出失效)。
- 请求头:`Authorization: Bearer <token>`。前端 axios 拦截器自动续期。
- 接口前缀 `/api`(如 `/api/auth/login`)。Knife4j 文档:`http://localhost:8080/api/doc.html`。

## 文档清单

- `README.md` — 启动说明 + Windows 一键启动脚本用法
- `Windows部署指导.md` / `Linux部署指导.md` — 生产部署(NSSM/systemd、Nginx 反代、Let's Encrypt、Docker Compose)
- `docs/需求规格说明书.docx` — 完整需求文档(9 章节,Word 版)
- `scripts/start-all.ps1` — Windows 一键启动前后端(双击 `start.bat` 调用)
- `scripts/start-db.ps1` — Docker 拉起 MySQL+Redis(自动导入 schema.sql,自动建 ihomy 账号)
- `config/mysql/my.cnf` — MySQL 调优配置(端口 6306 + 内存调优,2GB 内存方案)

## 文件存储策略

- **当前阶段(开发期)**:本地磁盘存储(`file.upload-dir=/opt/ihomy/uploads`),零成本零内存,FileService 已实现,开箱即用。Nginx `/files/` 托管静态目录。
- **未来对接 NAS**:优先 NFS 挂载方案(把 NAS 共享目录挂到 `/opt/ihomy/uploads`,**代码零改动**)。前提是 NAS 与服务器同内网。详细步骤见 Linux 部署指导附录"对接 NAS 存储"。
- **若 NAS 异地或要公网 CDN**:再改 FileService 用 S3 兼容 SDK(NAS/MinIO/OSS 通用),用 `@ConditionalOnProperty` 切换实现,本地实现保留为默认。
- **不要主动改 FileService 的存储实现**,除非用户明确要求接 NAS/OSS。当前本地实现满足需求。

## 部署约定(Linux)

- **求稳方案(2GB 内存)**:MySQL 本机部署 + Redis 用 Docker。MySQL 调优后 ~180MB,本机部署无需 Docker daemon 为它常驻;Redis 轻量,Docker 化便于升级。
- **每应用一用户,权利分散**:后端 Spring Boot 以 `ihomy` 应用用户运行(systemd `User=ihomy`);MySQL 用 apt 自动创建的 `mysql` 用户;Redis 容器隔离;Nginx 用 `www-data`。
- **除关键步骤外不用 root**:装包、建用户、systemd 管理、`/etc/` 配置、防火墙、certbot、执行 schema.sql(数据库 root)需 root;代码获取/构建/编辑 application.yml 由 `ihomy` 用户操作。
- **JVM 调优**:systemd ExecStart 用 `-Xmx384m -XX:MaxMetaspaceSize=192m -XX:+UseSerialGC -Xss512k`。
- **MySQL 调优**:`config/mysql/my.cnf` → `cp` 到 `/etc/mysql/conf.d/ihomy.cnf`,关键项 `performance_schema=OFF`(省 80-100MB)。
- **端口**:MySQL 用 6306(非默认 3306),application.yml 与 my.cnf 一致。
- **SSH 端口**:生产服务器 SSH 登录端口统一为 **19068**(禁止 22)。所有 ssh/scp 命令需加 `-p 19068`/`-P 19068`。防火墙放行 19068,关闭 22。
- **Docker 安装源**:Ubuntu 用阿里云镜像源(`mirrors.cloud.aliyuncs.com/docker-ce`),固定版本 29.7.0(apt 源找不到正确版本,需 `apt-cache show ... | grep 29.7.0` 查询后指定)。Docker 安装顺序在 Redis 之前。
- **Redis 镜像**:`docker pull redis`(默认 latest),与 Windows 开发机一致。
- **Git 克隆**:用 SSH 地址(`git@github.com:...`),ihomy 用户先生成 ed25519 key 并加到 GitHub。

## 关键约束

- **不要用 root 连业务库**;改 application.yml 时 username 保持 `ihomy`。
- **操作日志已实现**(V2.0)AOP + `@OperationLog` 注解,`LogController`(`GET /log`)仅 OWNER 可查,按需求文档 4.9 节。
- **权限码须在 `sys_auth`/`sys_role_auth` 种子中**:新增带 `@RequirePermission` 的接口前,确保对应 auth_code 存在且绑定到目标角色(OWNER 豁免;MEMBER 需显式授权),否则 MEMBER 会 403。
- **点赞/评论/通知严格同家庭**:`validateTarget` 校验内容 family_id 与用户一致,跨家庭返回 NOT_FOUND。
- **新用户加入家庭一律走注册带 `inviteCode`**(否则注册自建家庭成 OWNER,无法并入他人家庭)。
- 改完代码,**构建后刷新浏览器即可生效**(Nginx 指向 frontend/dist,单目录无需同步)。
- 新增功能走首页模块化:开发页面+路由 → 插 `home_module` 记录 → 首页自动出现入口,**不要改首页代码**。
- **快捷入口扩展(V3.1)**:`sys_home_module.category` 字段分类(枚举 `content`内容创作/`album`相册/`life`生活/`social`家庭互动/`system`系统管理),`addModule` 未传时默认 `content`。首页侧栏最多显示 8 个,超出自动出现"更多功能"入口 → `/more` 页(`More.vue`)按 category 分组展示全部启用模块。**新增模块必须填 category**,否则会归到"内容创作"组。
- **放映厅模块种子**:`sys_home_module` 已有 `('cinema','放映厅','icon-cinema','/cinema','life','left',5,1)`(schema.sql + live DB 均已同步)。
- **V3.3 规划(已确认待开发)**:
  1. **即时聊天室(WebSocket 可行)**:Spring Boot 3 原生 WebSocket(`WebSocketHandler`)/或 STOMP over WebSocket,认证复用 JWT(握手 `Authorization` 头或 `?token=`),按 `family_id` 分房间广播,聊天记录落库 + Redis 未读数,三端 PWA 均支持。低并发家庭场景原生 WebSocket 足够,无需引入 STOMP 复杂度。
  2. **优化注册用户逻辑**:当前"有邀请码进家庭(MEMBER/预设角色)否则自建家庭成 OWNER"。用户认为不明确,拟改为:邀请码必填引导为主线,明确"注册即加入他人家庭"与"创建新家庭"两条入口的选择流程,可加邮箱验证。
  3. **优化创建家庭逻辑**:自建家庭时引导填家庭名(已有 familyName 参数)、初始化示例内容(纪念日等),避免一次性产生空家庭垃圾数据(现有测试家庭 6,13,15,16,17,18,19,20,21,22,23 均为空,可清理)。
- **V3.4 规划(用户已确认,待开发)**:
  1. **签到+积分+积分商城**:`sys_checkin`(user_id/family_id/checkin_date/points/streak 连续天数,UNIQUE 每日一次);`sys_points_record`(user_id/change/type 签到/发布奖励/兑换支出/balance,积分流水);`sys_points_product`(家长上架:名称/图标/所需积分/库存上限/每人限兑次数);`sys_points_order`(兑换记录/状态)。签到规则:基础分 + 连续签到加成(如连续第 N 天递增,7 天一轮回)。积分来源:每日签到、发布内容奖励(博客/日记/照片/视频)。商城商品为家庭虚拟物品(如"洗碗券""陪伴券")。首页模块"每日签到"(life)+ "积分商城"页。
  2. **愿望单**:通用化"想看"(`content_wish`:title/reason/category/status 0待实现 1已实现 2放弃/requester_id/family_id/visibility/achieved_at),家庭成员可见可标记达成。放映厅的"想看"(content_video_wish)视为其视频特例,后续可迁移合并。
  3. **提醒事项**:`sys_reminder`(title/content/remind_at/repeat 一次性/每日/每周/每月/family_id/created_by/status/完成标记)。触发方式:站内通知(现有 sys_notification 复用)+ 到期列表;PWA 后台推送受限(iOS 不可靠),规划以站内提醒为主。首页模块"今日提醒"(life)。
  4. **家庭计划**:中长期目标(如"全家旅行计划""阅读计划")。`sys_family_plan`(title/description/target_date/progress/status/family_id/created_by)+ `sys_plan_task`(plan_id/title/assignee_id/done/due_date)。首页模块"家庭计划"(life)。
  5. **随机照片瀑布(动效页面)**:入口为首页模块,数据源 `content_photo`(同家庭可见范围)。交互:照片从屏幕最上方以卡牌翻转姿态缓慢飘落(3D 翻转 rotateX/rotateY + 下落 translateY,随机水平位置/角度/延迟,循环生成);鼠标悬停 → 该照片停止降落并翻正,照片信息(描述/拍摄者/日期/地点)以右下角小字展示;点击照片 → 进入全屏照片查看模式,可前后翻看随机顺序的照片流(左右箭头/键盘/移动端滑动)。技术:纯 CSS @keyframes + JS 定时/`requestAnimationFrame` 生成卡片,组件 `PhotoCascade.vue`,移动端适配 touch。动效可后续优化。
- **V3.5 规划(已确认待开发)**:
  1. **用户使用指导**:新用户引导/帮助页,含:首次注册创建家庭的引导流程、各功能使用说明、常见问题。入口:首页模块"使用帮助"(system)+ 首次登录弹窗引导。
  2. **商业化部署与盈利模式**:生产多租户(SaaS 多家庭)部署方案 + 盈利模式讨论中(订阅制优先:免费版 1 家庭/存储限额/基础功能,高级版多家庭+大存储+主题+音乐;增值存储扩容包;广告模块见下)。部署:目前单机 2GB 方案,若商用需评估带宽/磁盘/备份。
  3. **广告模块**:首页广告位(免费用户展示)或家庭公告位。注意:家庭私密场景接第三方广告网络(AdSense 等)转化低且有隐私争议,优先自建"家庭公告/赞助位"或仅对公开演示家庭投放。
  4. **家谱**:`sys_family_tree`(person_id/name/gender/birth_date/photo/mother_id/father_id/partner_id/spouse/note/family_id),树形结构,支持世代视图。首页模块"家谱"(life)。
  5. **记账**:`sys_book_account`/`sys_book_record`(类型 支出/收入/转账,金额/分类/记录人/日期/备注/账单明细),家庭共享账本,月度统计。首页模块"记账"(life)。
  6. **页面主题与纪念日主题**:主题系统(`sys_theme` 或配置项):明暗模式、主题色、纪念日特别主题(生日/节日自动切换页头横幅+卡片动效)。`sys_anniversary` 表增加:节日类型(`festival_type`:生日/结婚纪念/节日/自定义)、节日周期(`recurring` 已有 0一次/1每年,可扩展 每N年/农历闰月)。
  7. **背景音乐**:全局背景音乐开关(可静音),设置页可选曲目(本地/预置音乐文件),仅登录家庭使用,自动续播。
- **V3.6 多家庭方案(已实施)**:见下节"多家庭方案定稿"。
- **多家庭方案定稿(V3.6,已实施)**:
  - **家庭双概念**:`sys_user.family_id` = 主家庭(注册归属/默认);**当前家庭** = 会话级概念,存 Redis `user:curfamily:{userId}`(登录/切换写入,refresh 读取回填),JWT familyId 为快照。登录后下拉切换,所有接口按 JWT familyId 取数。
  - **我的家庭列表**:`GET /auth/families` 从 `sys_user_role` 查全部家庭绑定(含主家庭),返回 `{familyId,name,role,isPrimary,isCurrent}`。
  - **切换家庭**:`POST /auth/family/switch {familyId}` 校验该用户在目标家庭有角色绑定 → 更新 Redis 当前家庭 → 重签 access token。
  - **加入方式四条线**:①分享注册链接=邀请码 URL 化(注册页 `?invite=` 自动带 code);②`GET /family/search?keyword=` 搜索公开家庭(is_public=1,id/名称模糊) + `POST /family/apply {familyId,message}` 申请,OWNER 审核 `GET /family/apply/list` + `PUT /family/apply/{id}?action=approve|reject`(审核通过绑定 MEMBER,申请与结果走 sys_notification,通知 type=system content_type=family_apply sourceId=申请ID);③注册页邀请码(已有);④已登录用户 `POST /auth/join {inviteCode}` 复用邀请码核销加入(不自动切换)。
  - **演示家庭**:`sys_family_info.is_demo` 标记(1 号家庭"ihomy 演示家庭"),`sys_user.is_fake` 标记假用户;**is_fake 用户禁止登录**(FORBIDDEN);假数据:demo_owner(OWNER)/demo_member(MEMBER)/demo_child(CHILD) + 3 博客/2 日记/1 纪念日。
  - **家庭访问与隐私**:`GET /public/home?home_id=N` 与 `/public/feed` 支持指定家庭(参数名 **home_id**,不是 homeId):成员→完整数据;非成员/访客→仅 `is_public=1` 的公开内容(family/modules/photos,stats 置空),`is_public=0` 或不存在→NOT_FOUND。前端 URL `?home_id=` 透传(Home.vue/ActivityFeed),homeId 存在时跳过 dashboard 侧栏。自定义路径访问暂缓。
  - **前端(已实现)**:Login.vue 注册分流(创建家庭/邀请码加入 radio,`?invite=` 自动填码)、AppHeader 家庭切换下拉(>1 家庭显示)、Member.vue 加入新家庭搜索弹窗 + OWNER 入家申请审核列表。
- **V3.7 规划(已确认待开发)**:
  1. **国际化(i18n)**:前端 vue-i18n(默认中文,支持英文,跟随浏览器语言或用户设置切换);后端错误消息/通知文案资源化(ResultCode message、NotificationService 文案),DB 中可展示文本(如模块标题)暂不国际化;语言偏好存 `sys_user`(lang 字段)或前端 localStorage,登录态下以后端为准。PWA 三端同步。
  2. **URL 家庭 ID 混淆加密**:`?home_id=` 裸 ID 暴露家庭自增主键,易被恶意遍历。改为签名/加密 token(如 HMAC-SHA256 签名 + 时间戳防重放,或 AES 加密),前端只拿 `hid=<token>`,后端解密校验家庭存在性后走原权限逻辑;分享链接同时带 `?invite=` 与 `?hid=`。旧 `home_id` 参数保留兼容或直接替换(待定)。
  3. **用户身份标签**:家庭内成员可设置身份标签(如"爸爸""妈妈""老大"),数据结构:复用 `sys_user` 加字段或新表 `sys_user_label`(user_id/family_id/label/color,每家庭一套)。展示在头像旁/动态作者/评论者/成员列表。
  4. **多重人格模式**:基于第 3 项身份标签扩展——一个账号可拥有多个"人格"(身份标签),操作(发博客/日记/评论/照片)时选择当前人格,内容记录人格 ID 并展示人格名与颜色;不换账号、不重复注册,切换人格零成本(会话级 `currentLabel`,存 Redis 或前端状态)。与当前家庭切换正交。
  5. **UI 重新设计(美观+动效)**:统一设计语言(圆角/阴影/间距 token 化)、页面过渡动效(fade/slide/列表交错)、卡片悬停动效、加载骨架屏、暗色模式(配合 V3.5 主题系统规划)、移动端手势与触控优化。先做首页与导航,再逐页推进。
- **V3.8 规划(用户已确认,待开发)**:
  1. **手机号注册**:短信验证码登录或密码登录(双通道);短信验证码注册的账号登录后可在设置页设置密码。需短信服务商(阿里云短信等),未接入前可先落地字段与流程(sys_user 已有 phone 字段可复用)。
  2. **运维管理页面 + 运维管理员**:单独页面 `/ops`,独立运维管理员角色(如 OPS),仅能查看系统级信息——操作日志(sync 现有 LogController)、服务器运行状态(内存/磁盘/接口健康检查)、异常统计;**不能看到任何用户隐私**(用户资料、内容、家庭数据一律不返回)。入口:AppHeader 管理员菜单(仅 OPS 角色可见)。
- **混淆 ID 访问(已实施,V3.7 第 2 项落地)**:`sys_family_info.share_token` 16 位随机 token(UUID 截取),注册创建家庭时生成并随登录/注册接口返回(响应 `shareToken`);`GET /public/home`、`/public/feed` 支持 `?hid=<share_token>`(**优先于** `?home_id=`,校验顺序 hid > home_id > 当前家庭/默认演示家庭);Settings.vue 家庭设置页展示"家庭分享链接"(`/?hid=`)+ 复制按钮。**新家庭默认 `is_public=0` 私有**(schema 默认已改),访客访问私有家庭 hid → NOT_FOUND;演示家庭 share_token 固定 `98a06619927f11f1`。旧 `home_id` 参数保留兼容。
- **默认家庭概念(已实施)**:`sys_user.default_family_id` = 用户设置的默认家庭(可空=主家庭 family_id)。**当前家庭解析优先级**:Redis `user:curfamily:{userId}`(会话切换) > `default_family_id`(用户设置) > `family_id`(主家庭)。`POST /auth/family/switch` 支持 `{familyId, setDefault}`(setDefault=true 时同时写 default_family_id);`GET /auth/families` 返回 `isDefault`;AppHeader 家庭切换下拉底部有"将当前家庭设为默认"项,列表显示"默认"标记。
- **注册流程(已实施,V3.8 前置)**:注册必填 家庭名称(创建模式)/邀请码(加入模式)、**注册邮箱**(=登录账号,全局唯一 uk_email,重复 1005)、密码 + **确认密码**(不一致 400)、**图形验证码**。**不再输入用户名/昵称**:username 后端自动取 email(满足唯一约束),昵称默认邮箱前缀,可在个人设置修改。图形验证码:`GET /auth/captcha` 返回 `{captchaId,image(base64)}`,Redis `captcha:{id}` 存 5 分钟,一次性校验,错误/重用 1006;**开发/测试环境固定为 `qwer`**(`app.captcha-fixed-code` 配置,图片同步绘制该值可直接抄录;生产留空则随机)。**登录用邮箱**(`POST /auth/login {email,password,captchaId,captchaCode}`,大小写不敏感,用户名登录无效)。**注册成功不自动登录**:跳转登录页,登录后才能访问自己家庭(新家庭默认私有)。图形验证码实现:`CaptchaService`(Java2D 4 位字符+干扰线/噪点)。
- **登出保持 URL(已实施)**:AppHeader 退出登录后 `location.reload()` 保持当前 URL(公开页变访客视图,受保护页由路由守卫跳登录),不再强制回首页。
- **操作日志不再对普通用户展示(已实施)**:Settings.vue 已移除操作日志区块与 logApi 调用;后端 LogController 保留(供 V3.8 运维页面使用),权限后续收紧为 OPS。
- iOS PWA 必须 HTTPS + 有效证书(自签不行)。
- **代码注释规则:默认加注释**(用户已确认):新代码默认添加中文注释,说明类/方法职责、关键逻辑与分支意图;注释要简洁、有价值,不写废话、不为每行加注。以下两类可省略:极简单的自解释代码、与逻辑无关的样板。规则同样适用于 schema.sql/XML/前端 Vue/JS。
- 不要主动 commit,除非用户明确要求。

## 环境检查(参考)

本机已装:JDK 21、Node 20、MySQL 8、python-docx。Maven 用 Wrapper 或临时目录的 3.9.9。Redis 本机可能未装(可用 Docker 或 Memurai)。
