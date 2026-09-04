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
- **59 张表**,前缀分类:
  - `sys_`(系统/账号/权限/家庭设置/日志/参数/字典/天气/存储,18 张):`sys_user` / `sys_role` / `sys_auth` / `sys_user_role` / `sys_role_auth` / `sys_family_info` / `sys_home_module` / `sys_password_reset_token` / `sys_user_group` / `sys_user_group_member` / `sys_operation_log` / `sys_dict_item` / `sys_parameter` / `sys_storage_device` / `sys_baidu_credential` / `sys_weather_credential` / `sys_weather_location` / `sys_weather_log`
  - `family_`(家庭事务,22 张):`family_anniversary` / `family_notification` / `family_apply` / `family_invitation_code` / `family_checkin` / `family_points_record` / `family_points_product` / `family_points_order` / `family_task` / `family_reminder` / `family_plan` / `family_plan_task` / `family_book_record` / `family_chat_message` / `family_chat_read` / `family_user_label` / `family_tree` / `family_house` / `family_room` / `family_furniture` / `family_item` / `family_recipe`
  - `content_`(内容类,19 张):`content_blog` / `content_blog_category` / `content_diary` / `content_photo_album` / `content_photo` / `content_comment` / `content_visibility` / `content_like` / `content_video` / `content_video_wish` / `content_wish` / `content_music` / `content_music_playlist` / `content_music_playlist_track` / `content_book` / `content_book_borrow` / `content_book_category` / `content_book_category_rel` / `content_book_bookmark`
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
    common/      # Result统一响应/ResultCode/BizException/GlobalExceptionHandler/DictConst(字典常量)/SolarUtil(NOAA太阳位置算法)/AesUtil(凭证加密)/UserNames/Loggers(三类日志logger入口)/Ips(真实IP解析)/ThirdPartyHttp(三方API统一封装,出站日志+脱敏)
    config/      # SecurityConfig/CorsConfig(暴露X-Trace-Id)/MybatisPlusConfig/Knife4jConfig/WebMvcConfig/WebSocketConfig/SqlStatementLog(SQL日志,内部类分流mybatis.sql.internal)/ExternalConfigLoader(外挂配置加载)/WsHandshakeInterceptor(WebSocket JWT 验证)/AsyncConfig(@Async虚拟线程+MDC TaskDecorator传播tid)
    security/    # JwtUtils(JWT含familyId+role+permissions+isOps)/JwtAuthenticationFilter(认证+操作人放请求属性)/LoginUser/SecurityHelper/OpsAccessFilter
    annotation/  # @RequirePermission / @OperationLog
    aspect/      # RequirePermissionAspect(权限AOP) / OperationLogAspect(操作日志AOP)
    filter/      # TraceIdFilter(链路ID生成,写入 MDC + 响应头 X-Trace-Id) / AccessLogFilter(接口访问日志→access文件) / CaptureRequestWrapper(请求体截断捕获) / CaptureResponseWrapper(响应体截断捕获)
    entity/      # 52 个实体类(8 张关联/字典表无实体:sys_auth/sys_role_auth/sys_user_group/sys_user_group_member/sys_password_reset_token/sys_dict_item/content_visibility/content_book_category_rel)
    mapper/      # MyBatis-Plus BaseMapper 接口(自定义 SQL 全部放 resources/mapper/*.xml,接口不写 @Select/@Update 注解,参数统一 @Param)
    service/     # 43 个 @Service 类(单实现无接口层)
    controller/  # 32 个 Controller
    dto/         # 请求/响应 DTO
    websocket/   # ChatWebSocketHandler(原生 WebSocket 聊天室,每消息独立tid+access日志)
  src/main/resources/
    application.yml     # 端口8080, context-path=/api, 连接用 ihomy 账号; mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml; **基线配置**(MySQL 6306/Redis 6379/captcha 空/天气留空);`file.upload-dir` 基线 `/opt/ihomy/uploads`(Linux),开发通过 external.yml 覆盖为 Windows 路径;`logging.file.path` 基线 `/opt/ihomy/logs`,开发覆盖为 `D:\WorkSpace\ihomy\logs`;`app.log-retention-days: 7`
    logback-spring.xml  # 三类日志文件分流(access/server/thirdparty,按天滚动+总量上限+异步);六要素 pattern `[tid:%X{traceId}]`;SQL只进server文件
    external.yml.template  # 外挂配置模板(IHOMY_CONFIG_PATH 指定路径,覆盖 MySQL/Redis 密码 + JWT 密钥 + 上传路径 + captcha + 天气凭证,ENC() 加密)—— 唯一的开发/生产差异机制,**不再用 application-dev.yml profile**(见 scripts/start-all.ps1)
    mapper/*.xml        # 每个 Mapper 接口一个同名 XML(namespace=接口全限定名)
    schema.sql          # 建库+建号+建表(59张)+种子数据
  mvnw / mvnw.cmd       # Maven Wrapper,无需单独装 Maven
frontend/ (Vue3 + Vite + PWA + Element Plus + Pinia)
  src/
    api/request.js  # axios + JWT header + 401 自动刷新token
    api/index.js    # 全部模块 API 分组导出(30 个 Api 对象:public/auth/home/blog/diary/file/member/anniversary/album/photo/like/comment/notification/family/profile/video/points/task/reminder/plan/wish/music/book/ops/tree/chat/storage/item/kitchen/library)
    stores/user.js  # 登录状态 + hasPerm/isOps/isPureOps; stores/app.js 首页聚合(family/modules/photos/stats)
    router/         # 登录守卫 + scrollBehavior(返回回顶部);含 /ops 运维护卫, /chat 需登录; 36 个路由
    i18n/           # vue-i18n 中英(applyLocale 切换)
    theme/          # applyTheme/loadTheme(明暗模式,只 light/dark)
    utils/dict.js   # 枚举词条中文映射(与后端 DictConst 对应)
    utils/diary.js  # 日记纸张排版共享常量(LINE_H=28/LINES_PER_PAGE=18/PAGE_H=504)+心情天气枚举+measureDiaryLines 离屏测行数(编辑页与翻书页共用,改动须两边同步)
    utils/doodle.js # 日记信纸涂鸦矢量笔画引擎:笔型渲染(签字笔/铅笔/蜡笔/荧光笔/画笔)+橡皮擦(像素切断/整笔删除)+parseDoodle/setupCanvas;荧光笔走 multiply 专用画布
    utils/windowLight.js  # getSunScene(sunInfo,slotIndex)+currentSlotIndex()+makeRays():体积光调色板/光束/阴影参数;windowAngle(窗角)+hasDirectLight 门控
    utils/useSunLight.js  # 全局光影状态(provide/inject):sunScene/lampMode/shadowEnabled/weatherEffectEnabled/blobsEnabled/lightTestMode/testSpeed/loadWeather
    utils/useDragResize.js # 可拖拽面板组合式函数(zIndex+bringToFront+边界clamp+localStorage持久化)
    composables/useDevice.js  # 设备检测(UA+matchMedia 768px,全局单例 isMobile ref,matchMedia change 监听)
    components/     # AppSidebar(全局导航)/BackToTop/Breadcrumb/AvatarCropper/InstallPrompt/SiteFooter(备案号)/SunLightLayer(全局光影层)/LightTestConsole(光照测试控制台)/SyncDialog(存储同步进度)/MobileTabBar/MobileHeader/MobileHomeFeed/MobileMoreGrid/MobileMePage(移动端组件)
    layouts/MobileLayout.vue  # 移动端壳:首页三Tab模式 / 子页面返回栏模式
    styles/main.css # CSS 变量 + 全局样式 + 深色模式覆写 + ElMessage/ElNotification 增强 + 移动端 @media 适配
    views/          # 28 个页面:Home(沉浸式首页)/Login/Member/Settings/Anniversary/album(Album/AlbumDetail)/cinema/Cinema/diary(DiaryList/DiaryBook/DiaryPage/DoodleTray/DiaryEdit)/blog(BlogList/BlogDetail/BlogEdit)/points/Points/task/Task/reminder/Reminder/plan/Plan/wish/Wish/book/Book/chat/Chat/tree/Tree/cascade/Cascade/ops/Ops/storage/Storage/item/Item/kitchen/Kitchen/library/LibraryList/LibraryDetail/LibraryEdit
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

> 完整功能描述(Controller/Service/关键表/要点/接口清单)见 **docs/需求设计说明书.md** 第 4 章;本节仅作导航索引。

| 域 | 模块 | Controller / Service | 关键表 | 一句话要点 |
|----|------|---------------------|--------|-----------|
| 账号 | 注册/登录/验证码/密码找回 | AuthController / AuthService | sys_user 等 | 邮箱=账号;JWT 双 token(2h/7d 滑动续期,HTTP 401 触发续期);开发验证码固定 qwer;演示账号 demo@ihomy.local 等/guest123 |
| 账号 | 个人资料/身份标签 | ProfileController | sys_user / family_user_label | nickname/avatar/birthday/gender;标签每家庭一套 |
| 家庭 | 家庭管理/多家庭切换 | FamilyController, AuthController / MultiFamilyService | sys_family_info | share_token 分享;Redis 当前家庭;4 种加入方式;天气地区偏好;气象预警推送开关 |
| 家庭 | 成员管理/邀请码/入家申请 | MemberController, FamilyController | sys_user_role / family_invitation_code / family_apply | 角色变更/移除/邀请码;公开家庭搜索+申请审批 |
| 内容 | 博客 | BlogController / BlogService | content_blog(+category) | 标签+分类树(el-cascader);新建家庭注入 9 默认分类;未分类兜底 |
| 内容 | 日记 | DiaryController / DiaryService | content_diary | 书架+翻书视图(18行×28px 分页);信纸涂鸦 doodle JSON(doodle.js 笔型引擎);date 兼容 yyyy-MM-dd HH:mm |
| 内容 | 相册/照片 | AlbumController, PhotoController / AlbumService | content_photo_album / content_photo | 硬删;层级相册+设备目录映射(影子照片+签名 URL+缩略图缓存);自定义封面优先级;分享 token+Knuth 混淆;多选批删 |
| 内容 | 放映厅 | VideoController / VideoService(+VideoMapService) | content_video(+wish) | 豆瓣式属性;硬删;设备映射(平铺+播放现签+Range 流式);想看列表;Jellyfin 集成规划 P1 |
| 内容 | 照片瀑布 | CascadeController | content_photo | 随机+可见性过滤;落叶动效;缩略图 |
| 内容 | 愿望单 | WishController / WishService | content_wish | 待实现/已实现/放弃 |
| 内容 | 书架 | LibraryController / LibraryService | content_book×5(borrow/category/category_rel/bookmark) | EPUB/PDF/TXT/MOBI;分类树+批量删除/移动+阅读状态+书签;在线阅读(阅读器全屏);硬删 |
| 互动 | 点赞/评论 | LikeController, CommentController | content_like / content_comment | toggle 语义;二级树;严格同家庭 |
| 互动 | 通知 | NotificationController / NotificationService | family_notification | 评论/入家申请/同步完成/气象预警通知 |
| 互动 | 聊天室 | ChatController + ChatWebSocketHandler / ChatService | family_chat_message / family_chat_read | 原生 WS(握手 ?token=);按家庭房间;每消息独立 tid |
| 生活 | 纪念日 | AnniversaryController / AnniversaryService | family_anniversary | 阳历/农历+闰月+每年重复;Hutool ChineseDate 月份 0-based 需+1 |
| 生活 | 提醒/计划/任务/记账/家谱 | ReminderController 等 / 各 Service | family_reminder / plan×2 / task / book_record / tree | 提醒 4 频率;计划+子任务进度联动;任务状态机(积分结算走 pointsService);记账单表;家谱 null 字段须 LambdaUpdateWrapper 显式 SET |
| 生活 | 签到积分商城 | PointsController / PointsService | family_checkin / family_points_* | 日签 5 分+连续加成;内容奖励(博客10/日记8/照片2/视频15);兑换校验 1008/1009 |
| 生活 | 背景音乐 | MusicController / MusicService(+MusicMapService) | content_music×3 | mp3agic ID3 解析;歌单 is_background 每家庭 1 条;设备映射(纯文件名平铺);播放现签 |
| 基础 | 文件上传 | FileController / FileService | 磁盘 | 分类目录;流式重载(禁 getBytes);deleteByUrl 防路径越界 |
| 基础 | 存储管理 | StorageController / StorageService+AlbumMapService+ThumbnailService+SignedUrlService | sys_storage_device / sys_baidu_credential | 设备 CRUD+文件浏览;目录映射(不拷贝文件);HMAC 签名 URL;百度 OAuth+dlink 中转;缩略图缓存 |
| 基础 | 首页聚合 | HomeController + PublicController | sys_home_module | 模块化插入即扩展;动态流 8 类型;非成员视图 Redis 缓存 5min |
| 基础 | 运维 | OpsController / OpsService | sys_operation_log / sys_weather_log | OPS 隔离(OpsAccessFilter);资源/日志/天气/访问量统计;tid 检索详细日志 |
| 基础 | 每日内容 | DailyController | — | 每日一图(代理 Bing)+每日知识 |
| 基础 | 操作日志/日志追溯 | LogController + @OperationLog AOP | sys_operation_log | 约 130 处写接口覆盖;三类日志文件(access/server/thirdparty);tid 全链路(详见 docs/日志规范.md) |
| 基础 | 系统参数 | ParameterService | sys_parameter | 键值对;AES 盐值首启入库 |
| 光影 | 太阳位置/体积光/台灯/天气特效 | SolarUtil + SunService / windowLight.js + SunLightLayer.vue | — | NOAA 288 时隙;窗角门控直射光;CSS keyframes 钟摆;天气 multiplier;suspend/restore 播放器沉浸 |
| 光影 | 天气代理/预警推送 | WeatherService | sys_weather_* | 和风 v1 迁移+适配层(前端零改动);配额 50000 本地统计;30min 预警推送(家庭级开关) |
| 光影 | 天气详情页/光照测试台 | views/weather/Weather.vue + LightTestConsole.vue | — | 9 指标实况+预警+24h 折线+10 天横条;288 时隙循环 5 档速度 |
| 光影 | 首页仪表盘 | Home.vue + useWidgetDrag.js | localStorage | 12×9 栅格;编辑模式拖拽缩放增删;8 默认组件;布局持久化 |
| 物品 | 物品定位+户型图 | ItemController / ItemService | family_house / family_room / family_furniture / family_item | 五级粒度+跨级搜索;**户型图 2 期全部完成**(多边形房间/家具库/裁剪粘合/尺寸标定/吸附/撤销,详见 docs/户型图设计.md);3 期 AI 语义待做 |
| 厨房 | 菜单/菜谱/食材 | RecipeController / RecipeService | family_recipe | 菜单按类别+时间推荐;ingredients/equipment/steps JSON;食材页复用 itemApi type=INGREDIENT |
| 系统 | i18n/主题/字典 | i18n/ + theme/ + utils/dict.js | sys_dict_item | 中英双语;只 light/dark;枚举英文单词化(禁数字) |
| 移动端 | 设备自适应(V8.0/V9.13) | useDevice.js + MobileLayout.vue + Mobile* 组件 | — | 单代码库运行时自适应;首页三 Tab;子页面返回栏;特效首次访问默认关 |

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
    - **工具栏筛选组件尺寸**:`.tb-left` 的 `el-input`/`el-select` 统一 `size="small"`(24px 高,相册/放映厅/音乐三页已对齐);`.tb-right` 按钮用默认尺寸(32px 高)。
    - **工具栏按钮间距**(强制):`.tb-right` 为 `flex gap:8px`,普通按钮间另有 EP `.el-button+.el-button` 12px 兄弟边距(合计 20px);**按钮被 `el-dropdown` 包裹时(如「上传音乐」下拉)吃不到该兄弟边距,必须 scoped 补 `.tb-right :deep(.el-dropdown) { margin-left: 12px }`**,否则间距不一致。
    - **多选交互统一**(强制,相册/放映厅/音乐同款):仅用 `.pick-badge` 右上对勾圆标(`.on` 时 `#b88c6e` 实底)+卡片 `outline: 2px solid #b88c6e` 描边;**禁止再加左上 checkbox 角标**(双选择效果);选择态点击卡片即勾选;常规态/选择态按钮组在工具栏 tb-right 互斥切换,选择态=选中计数(`.select-count`)+取消+删除所选(danger)。
    - **设备映射来源角标**(统一,相册/放映厅/音乐同款):卡片封面左/右上 `设备名 + .status-dot` 状态点(VALID 绿/OFFLINE 灰/MISSING 红),半透明白底圆角小标签。
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

- **Edge 硬件加速整页频闪(遗留,环境/驱动问题,非应用代码)**:Edge+硬件加速下页面加载后整页频闪;**切走再切回浏览器窗口(或开关一次硬件加速+重启 Edge)即恢复**;Chrome 不复现,关硬件加速不复现。根因为显卡驱动/MPO 合成层 bug。用户侧处置(按序):开关一次硬件加速+重启 Edge(临时复位)→ 更新显卡驱动(根治)→ 注册表禁用 MPO(`HKLM\SOFTWARE\Microsoft\Windows\Dwm` → `OverlayTestMode`=5)→ 应用内 Settings 关"毛玻璃"(no-glass 全局禁 backdrop-filter,应用侧唯一规避开关)。完整排查过程与已保留/已撤销的修复清单见 docs/变更归档.md「首页频闪排查与修复」(frontend_performance 分支,2026-08-31)。
- **ElMessageBox 动画未生效**:`main.css` 中 `.fade-in-linear-*` + `.el-overlay-message-box` 的 CSS 覆写写法正确(transition name=`fade-in-linear`,class=`el-overlay-message-box` 已从 EP 源码确认),但实际运行时动画未生效。可能原因:EP 内部 `Transition` 的 `persisted` 模式导致 CSS transition 不触发,或 EP 的 `msgbox-fade-in` keyframes 优先级覆盖。待排查:用 DevTools 确认渲染时实际 class 和 transition 是否被正确应用。`closeOnClickModal: true` 已全部加上(点击遮罩关闭已生效)。

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

## 规划事项(未实现,排序按推荐优先级)

| 优先级 | 规划 | 要点 |
|--------|------|------|
| P1 | 放映厅 Jellyfin 集成 | 方案已定稿(2026-08-31),详见 docs/变更归档.md「放映厅 Jellyfin 集成方案」:ihomy 做脸(海报墙/筛选/家庭层)+ Jellyfin 做引擎(刮削/转码/TV 客户端);S1 本地 spike 验证 API → S2 后端 → S3 前端;现有 content_video 本地库降级为次级 tab 保留;**启动时先重读该归档小节** |
| P2 | 物品定位-户型图 剩余优化 | 2 期全部完成(完整能力清单见 docs/需求设计说明书.md 4.8);剩余:物品头像图、家具类型图标、搜索定位放大居中 |
| P2 | 用户使用指导 | 新手引导弹窗+帮助页 |
| P2 | 家庭公告/广告位 | 自建家庭公告(不接第三方广告,隐私原因) |
| P2 | 设置页-存储管理设计统一 | 存储管理 tab(Storage.vue)用 `.card.section`+`.page-toolbar` 与 profile/family/daily/light 其他 tab 的 `.card.settings-card`+`.section-label` 风格不一致;统一为 section-label 标题+卡片规范;移动端隐藏次要列消除横向溢出一并处理 |
| P2 | 首页组件自适应展示 | 首页栅格(12列×9行)组件按尺寸分级展示:h=1 仅核心数据(标题消失)/h=2-3 精简列表/h≥4 完整列表/w≤2 单列/w≥4 多列网格;每组件 compact/normal/expanded 三档模板。组件清单:feed(5×5)/task(4×2)/today(4×4)/weather(3×4)/anni(1×4)/recipe(4×2)/wish(4×3)/finance(4×3)/album(4×5)/music(4×3) |
| P3 | 多重人格 | 基于身份标签扩展,一账号多标签可切换发表,会话级 currentLabel(Redis 或前端状态),与家庭切换正交 |
| P3 | AI API 对接 | 统一对接大模型 API(聊天/内容生成),OpenAI 兼容协议,待细化 |
| P3 | 物品定位-AI 语义 | 3 期:自然语言"找找我的工具箱"→AI 拆名称+别名→SQL 查询;"放到门口柜子最上层抽屉"→AI 按五级粒度解析,缺层追问→拼 INSERT(决策已定,依赖 AI API) |
| P3 | 播放器解码器评估 | 原生 `<audio>`/`<video>` 已覆盖主流格式(硬件解码最优);不建议 ffmpeg.wasm(200KB+ 包体+CPU 解码慢);未来如需引入优先 video.js+hls.js |
| P3 | Apple Live Photos 支持 | EXIF/ContentIdentifier 标记实况照片,JPEG 封面+MOV 关联(`content_photo` 加 `live_video_url`);前端上传+后端解析+展示组件三方协同 |
| P3 | 72h 预报延展 / 月相晨昏 / 空气预报 | 72h 走 v1 daily hourly 数组;月相晨昏纯天文计算;空气预报需订阅版(无预算可用历史趋势推测) |
| P4 | 手机号注册 | 需短信服务商(阿里云等),未接入前不实现(sys_user.phone 字段已存在) |
| P4 | 商业化/多租户 | SaaS 订阅制评估,条件允许再做 |
| P4 | 广告模块 | 家庭私密场景接第三方广告转化低且有隐私争议,优先自建"家庭公告/赞助位" |

> 优先级含义:P1 用户价值高且技术上可行(依赖最少);P2 锦上添花;P3 结构性改动;P4 依赖外部条件(短信商/商业决策)。实现新功能前先 `grep schema.sql + router/` 对照模块种子。

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
