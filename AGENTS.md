# AGENTS.md — ihomy 项目规则

> 本文件供 opencode 跨会话加载,记录项目关键决策与约定。新会话启动时会自动读取,无需重复说明背景。
> 修改本文件后立即对所有新会话生效。

> **⚠ 路径拼写警示(遵守以防误写)**:
> - 工作目录绝对路径:`C:\Users\chill\OneDrive\WorkStation\Projects\ihomy`
> - 中间段是 **`WorkStation`(一个词,`W-o-r-k-S-t-a-t-i-o-n`)**,不是 `Work\Station`、不是 `WorkStudio`、也不是 `Work Station`。
> - 每次读/写/移动文件前先逐字核对路径;发现读不到文件时优先怀疑路径拼写而非文件不存在。
> - **⚠ 编码警示**:含中文的源码/配置/SQL 一律走本工具的 Read/Write/Edit 读写,禁止用 PowerShell `Get-Content`/`Set-Content`/`WriteAllText` 读写(PS 5.1 默认 GBK 会破坏 UTF-8 中文,且 `[IO.File]::WriteAllText` 默认带 BOM 导致 javac 报非法字符)。PowerShell 仅用于:建表 SQL 文件落盘后 `mysql < file` 导入、npm/mvn 构建、HTTP 冒烟。

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
- **39 张表**,前缀分类(**V4.1 起执行全面重命名**):
  - `sys_`(系统/账号/权限/家庭设置,22 张):`sys_user` / `sys_role` / `sys_auth` / `sys_user_role` / `sys_role_auth` / `sys_family_info` / `sys_home_module` / `sys_password_reset_token` / `sys_user_group` / `sys_user_group_member` / `sys_operation_log` / `sys_dict_item` / `content_wish`(刻意外)等
  - `family_`(家庭事务,17 张):`family_anniversary` / `family_notification` / `family_apply` / `family_invitation_code` / `family_checkin` / `family_points_record` / `family_points_product` / `family_points_order` / `family_task` / `family_reminder` / `family_plan` / `family_plan_task` / `family_book_record` / `family_chat_message` / `family_chat_read` / `family_user_label` / `family_tree`
  - `content_`(内容类,10 张):`content_blog` / `content_diary` / `content_album` / `content_photo` / `content_comment` / `content_visibility` / `content_like` / `content_video` / `content_video_wish` / `content_wish`
  - **命名规则(重命名后)**:家庭事务业务表一律 `family_` 前缀(原 sys_);内容数据 `content_` 前缀;账号/权限/配置/日志保留 `sys_`。新增表必须遵守。前缀取最顶层祖先类别;上下级关系体现在表名(如 `sys_user_role`)。
- **枚举不再用数字**:V3.9 起状态/类型字段一律大写英文单词(`PUBLISHED/DRAFT/PUBLIC/FAMILY/PRIVATE/ACTIVE...`),含义存字典表 `sys_dict_item`(16 组),Java 常量集中于 `common/DictConst.java`,前端映射 `utils/dict.js`。**不要写回 0/1/2 判断**。
- **注意**:`content_blog/diary/photo/video/wish` 5 张内容表 `visibility` 列为 `VARCHAR(20) DEFAULT 'FAMILY'`(PRIVATE仅自己/FAMILY家庭可见/PUBLIC公开),schema.sql 与 live DB 已对齐(曾误写 TINYINT)。
- 权力 4 角色:OWNER/MEMBER/CHILD/GUEST + OPS(运维,不属任何家庭)。同一用户不同家庭可不同角色(`sys_user_role.family_id` 区别)。
- **新增带 `@RequirePermission` 接口前**:确保 auth_code 进 `sys_auth` + `sys_role_auth` 种子(OWNER 豁免,MEMBER 显式授权),否则 403。

## 已实施功能详情(V2.2 起,按版本归档)

- **家庭纪念日(V2.2)**:独立表 `family_anniversary`,`user_id` 关联用户(可空=家庭级纪念日),`calendar` 字段区分 `solar`(阳历)/`lunar`(农历),农历转公历用 Hutool `ChineseDate`(`getGregorianMonth()` 为 0-based,需 +1)。倒计时由 `HomeStatsService` 计算,`/public/home` 的 `stats.upcomingEvents` 返回最近 3 个。
- **纪念日管理(V2.2)**:`AnniversaryController`(`GET /anniversary/list` 访客可读、增删改需登录)。全员可增删改本家庭纪念日;支持阳历/农历+闰月+关联成员(可空)+每年重复。
- **相册/照片管理(V2.2)**:`content_album` + `content_photo`(软删 `deleted`)。`AlbumController`(`/album/list`、`/album/{id}` 详情、增删改);`PhotoController`(批量上传 `POST /album/{id}/photos` 字段 `files`,改备注 `PUT /photo/{id}`,删除 `DELETE /photo/{id}`)。**权限**:成员可上传/管理自己创建或上传的相册/照片,OWNER 可管理任何;访客仅可读**默认家庭**的 `public` 相册(默认家庭由 `FamilyMapper.selectDefault()` 取)。`MemberController`(`GET /member/list`)返回成员+`role_code`,供纪念日/相册绑定下拉。
- **导航方案(V2.2)**:`AppHeader.vue` 已全局化到 `App.vue`(原在 Home.vue 内),新增全局 `BackToTop.vue` 回到顶部、`Breadcrumb.vue` 面包屑(首页/列表/当前),`router/index.js` 配置 `scrollBehavior`(返回时回顶部)。首页数据集中到 `stores/app.js`(family/modules/photos/stats + init/reset),Home.vue 只读 store 不再各自拉取。
- **V3.0 核心能力(已实施)**:
  - **统一内容点赞/评论**:`content_like`(content_type+content_id+user_id+family_id,UNIQUE 防重复)+ `content_comment`(parent_id 回复树)。`LikeController`(`POST /like/toggle`、`GET /like/state`)、`CommentController`(`GET /comment/list` 树、`POST /comment`、`DELETE /comment/{id}`)。点赞/评论仅限同家庭内容(跨家庭返回 NOT_FOUND)。删除评论:OWNER 或作者本人。
  - **站内通知**:`family_notification`。`NotificationService`(create/list/unreadCount/markRead/markAllRead),评论/回复自动通知被回复者与内容作者。`NotificationController`。前端 `AppHeader.vue` 铃铛下拉+已读。
  - **成员管理(邀请制)**:`MemberController`(`GET /member/list` 成员+角色;`PUT /member/{id}/role` 改角色;`DELETE /member/{id}` 移出;`POST /member/invite` 生成码;`GET /member/invite` 码列表)。**加入家庭走注册带邀请码**:`RegisterDTO.inviteCode` 可选,`AuthServiceImpl.register` 检测到邀请码则直接加入受邀家庭(预设角色,不新建家庭),否则自建家庭并绑定 OWNER。
  - **家庭设置 / 个人资料 / 操作日志**:`FamilyController`(`GET`/`PUT /family`,@family:manage)、`ProfileController`(`GET`/`PUT /profile`)、`LogController`(`GET /log` 分页,@log:view)。`sys_user`(birthday,gender)、`sys_family_info`(description,is_public)、`content_photo`(taken_at,location) 均已落库。
  - **权限注解**:`@RequirePermission("code")` + AOP `RequirePermissionAspect`(不通过抛 FORBIDDEN)。`SecurityHelper.hasPermission` 对 OWNER 恒真,其余查 `SysRoleMapper.selectAuthCodesByUserAndFamily`。`sys_auth` 种子含 `comment:create/delete`、`invite:create`、`log:view`、`family:manage`、`user:manage` 等。
  - **JWT 携带 familyId**:`JwtUtils.generateAccessToken(userId, username, role, familyId)` 写入 claim;`JwtAuthenticationFilter` 用它构造 `LoginUser(familyId)`(缺失时回退查库),保证 MEMBER 权限码按家庭解析正确。
- **放映厅(V3.2)**:`content_video` + `content_video_wish`。`content_video` 字段含豆瓣式属性:`media_type`(movie/series/other)、`genres`(题材,逗号分隔)、`region`、`year`、`language`、`duration`(分钟)/`episodes`(剧集数)、`director`、`actors`、`rating`(0-10 一位小数)、`intro`、`poster`、`video_url`(经 FileService 上传)、`uploader_id`、`family_id`、`visibility`、软删。`content_video_wish`(想看):`title`/`genres`/`reason`/`status`(0 待入库 1 已入库)/`requester_id`/`family_id`。`VideoController`(`GET /video/list` 按 family 过滤+keyword/mediaType 搜索;`POST /video/upload` 上传文件返回 `{url}`;`POST /video`/`PUT/DELETE /video/{id}` 增删改,改删校验上传者或 OWNER;`POST /video/wish` 提交想看、`GET /video/wish/list`、`PUT /video/wish/{id}/done` 标记入库、`DELETE /video/wish/{id}`)。前端 `/cinema` 页(`views/cinema/Cinema.vue`):视频库(海报网格+内嵌播放)+ 想看列表两个标签页,上传对话框含豆瓣属性表单,想看可选题材标签(`genresOptions` 30 项豆瓣常见分类)。**上传限制**:后端 `multipart max-file-size 500MB` + nginx `client_max_body_size 500m`(视频大文件必需,已调)。
- **模块重命名(V3.2)**:博客/日记本/相册/纪念日(原"家庭博客/生活日志/家庭相册/家庭纪念日"),DB `sys_home_module.title` 与前端页面标题已同步;`Anniversary.vue` 内部"家庭纪念日"业务文案保留。
- **默认可见范围=全家可看(V3.3)**:博客/日记发布默认 `visibility=FAMILY`(家庭可见),前后端一致(BlogServiceImpl/DiaryServiceImpl null→FAMILY,BlogEdit.vue/DiaryList.vue 默认 FAMILY 并提供 PRIVATE 仅自己/FAMILY 家庭可见 选择)。历史数据已统一 UPDATE。放映厅固定 FAMILY;照片按相册类型 public→PUBLIC/private→FAMILY。**注意**:visibility 枚举 `PRIVATE仅自己/MEMBERS指定成员/GROUPS指定群组/FAMILY家庭可见/PUBLIC公开`,前端不要再出现"1=家庭可见"这类旧数字映射。
- **家庭封面导航入口已移除(V3.3)**:`sys_home_module` 中 cover 模块 `enabled=0`(schema.sql 种子已同步),AppHeader 导航不再显示"家庭封面";首页顶部大封面区(`family.coverImage`)保留,家庭设置页仍可配置封面。
- **RBAC 权限模型**:用户-角色-权限三层(`sys_user` → `sys_user_role` → `sys_role` → `sys_role_auth` → `sys_auth`)。`sys_user` 无 `role` 字段,角色通过关联表查询。预设 4 角色:OWNER/MEMBER/CHILD/GUEST。同一用户在不同家庭可有不同角色(`sys_user_role.family_id` 区分)。
- `sys_operation_log`(系统操作日志表):**已实现**(V2.0)AOP + `@OperationLog` 注解自动记录。记录范围:登录/登出、增删改内容、成员管理、配置变更。仅家长可查询(`LogController` GET `/log`,@log:view)。
- **链路追踪(V3.0.1)**:`TraceIdFilter`(servlet 首个过滤器)为每个请求生成 `traceId`(16 位 UUID 短串),写入 MDC `traceId` + 响应头 `X-Trace-Id`(也接受入站 `X-Trace-Id` 透传)。日志模式为 `%X{traceId:-}`,控制台与文件(`./logs/ihomy.log`)均带 traceId。`sys_operation_log` 新增 `trace_id` 字段(AOP 在该线程取 MDC 落库),保证一次操作在日志与 DB 里可跨模块/跨步骤串联。
- **SQL 日志(V3.0.2)**:`mybatis-plus.log-impl` 用自定义 `com.ihomy.config.SqlStatementLog`:打印 SQL 语句与参数(`==> Preparing / Parameters`),**过滤结果集行**(`<== Columns/Row/Total`,按 `s.startsWith("<== ")` 丢弃),既保留 SQL 调试信息又减小日志占用。
- **上传文件访问(nginx)**:`nginx.conf` 增加 `location /files/ { alias <仓库>/uploads/; }`。**注意**:图片扩展名正则 location 会用 `root` 覆盖 `/files/` 别名(导致路径解析错),必须写成 `location ~* ^/(?!files/).+\.(...)$`,用负向断言排除 `/files/`,否则上传图片 404。
- **头像/封面走上传**:`Settings.vue` 个人头像(`profile.avatar`)与家庭封面(`family.coverImage`)由 URL 输入改为 `el-upload`+`http-request` 调 `POST /file/upload`(返回 `{url}`),保存时随 `PUT /profile`、`PUT /family` 落库。后台 `avatar`/`coverImage` 仍为 URL 字符串字段,无需改接口。
- 默认管理员:`admin / admin123`(BCrypt),登录后应改密。初始化时自动绑定 OWNER 角色。
- **多家庭方案定稿(V3.6,已实施)**:
  - **家庭双概念**:`sys_user.family_id` = 主家庭(注册归属/默认);**当前家庭** = 会话级概念,存 Redis `user:curfamily:{userId}`(登录/切换写入,refresh 读取回填),JWT familyId 为快照。登录后下拉切换,所有接口按 JWT familyId 取数。
  - **我的家庭列表**:`GET /auth/families` 从 `sys_user_role` 查全部家庭绑定(含主家庭),返回 `{familyId,name,role,isPrimary,isCurrent}`。
  - **切换家庭**:`POST /auth/family/switch {familyId}` 校验该用户在目标家庭有角色绑定 → 更新 Redis 当前家庭 → 重签 access token。支持 `{familyId, setDefault}`(setDefault=true 时同时写 `sys_user.default_family_id`);`GET /auth/families` 返回 `isDefault`;AppHeader 家庭切换下拉底部有"将当前家庭设为默认"项。
  - **默认家庭概念(已实施)**:`sys_user.default_family_id` = 用户设置的默认家庭(可空=主家庭 family_id)。**当前家庭解析优先级**:Redis `user:curfamily:{userId}` > `default_family_id` > `family_id`(主家庭)。
  - **加入方式四条线**:①分享注册链接=邀请码 URL 化(注册页 `?invite=` 自动带 code);②`GET /family/search?keyword=` 搜索公开家庭(is_public=1,id/名称模糊) + `POST /family/apply {familyId,message}` 申请,OWNER 审核 `GET /family/apply/list` + `PUT /family/apply/{id}?action=approve|reject`(审核通过绑定 MEMBER,申请与结果走 family_notification,通知 type=system content_type=family_apply sourceId=申请ID);③注册页邀请码(已有);④已登录用户 `POST /auth/join {inviteCode}` 复用邀请码核销加入(不自动切换)。
  - **演示家庭**:`sys_family_info.is_demo` 标记(1 号家庭"ihomy 演示家庭"),`sys_user.is_fake` 标记假用户;**is_fake 用户禁止登录**(FORBIDDEN);假数据:demo_owner(OWNER)/demo_member(MEMBER)/demo_child(CHILD) + 3 博客/2 日记/1 纪念日。
  - **家庭访问与隐私**:`GET /public/home?home_id=N` 与 `/public/feed` 支持指定家庭(参数名 **home_id**,不是 homeId):成员→完整数据;非成员/访客→仅 `is_public=1` 的公开内容(family/modules/photos,stats 置空),`is_public=0` 或不存在→NOT_FOUND。前端 URL `?home_id=` 透传(Home.vue/ActivityFeed),homeId 存在时跳过 dashboard 侧栏。
  - **前端(已实现)**:Login.vue 注册分流(创建家庭/邀请码加入 radio,`?invite=` 自动填码)、AppHeader 家庭切换下拉(>1 家庭显示)、Member.vue 加入新家庭搜索弹窗 + OWNER 入家申请审核列表。
- **混淆 ID 访问(已实施,V3.7 第 2 项落地)**:`sys_family_info.share_token` 16 位随机 token(UUID 截取),注册创建家庭时生成并随登录/注册接口返回(响应 `shareToken`);`GET /public/home`、`/public/feed` 支持 `?hid=<share_token>`(**优先于** `?home_id=`,校验顺序 hid > home_id > 当前家庭/默认演示家庭);Settings.vue 家庭设置页展示"家庭分享链接"(`/?hid=`)+ 复制按钮。**新家庭默认 `is_public=0` 私有**(schema 默认已改),访客访问私有家庭 hid → NOT_FOUND;演示家庭 share_token 固定 `98a06619927f11f1`。旧 `home_id` 参数保留兼容。
- **聊天室(V3.3,已实施)**:`family_chat_message`(family_id/sender_id/content/created_at)+ `family_chat_read`(用户未读数)。原生 WebSocket(非 STOMP),握手 `?token=` 验 JWT,按家庭分房间广播,发送即落库+广播,重连拉历史消息。`ChatController`(GET history/已读数、POST 标记已读)。前端 `views/chat/Chat.vue`(气泡列表+自动滚动+在线提示),路由 /chat,首页模块种子 `chat`/聊天室/life/sort 12(注意:schema.sql 种子缺 chat 行,live DB 有,建新库需补)。
- **积分商城(V3.4,已实施)**:4 表——`family_checkin`(user_id+checkin_date UNIQUE 每日一次)、`family_points_record`(change_type CHECKIN/REWARD/REDEEM,balance=变动后余额,`change` 字段实体已反引号转义)、`family_points_product`(stock=-1 不限量、per_limit=0 不限次)、`family_points_order`(product_name 快照,status 0待核销/1已核销)。签到规则:基础 5 分 + 连续加成 `5+(streak-1)%7`(7 天一轮回),昨日签过则 streak+1,断签重计;重复签到 1007。内容奖励:博客 +10 / 日记 +8 / 照片每张 +2(批量一次流水,备注"上传照片 ×N")/ 视频 +15。兑换校验:积分不足 1008、兑完或超限兑 1009;`PointsService.redeem` 低并发无锁(ponytail: 需防超卖时改 UPDATE ... WHERE stock>0)。接口 `PointsController`(/points):`GET stats`、`POST checkin`、`GET products`(附当前用户 redeemedCount)、`POST/PUT/DELETE products(/id)` 与 `PUT orders/{id}/taken` 和 `GET orders/all` 需 `@RequirePermission("points:manage")`(仅有 OWNER 绑定,种子已同步 live DB)。前端 `views/points/Points.vue`(签到卡/商品网格/兑换/家长上架编辑下架核销),路由 /points,首页模块种子(`points`/积分商城/life/sort 6,front/fixed 已落库)。
- **任务悬赏(V3.4,已实施)**:`family_task`(family_id/title/description/reward_type 0无1积分2物品/reward_points/reward_item/status 0待领取 1进行中 2待确认 3已完成 4已取消/created_by/assignee_id)。状态机:发布→他人领取(发布者不可自领)→领取人完成申报→发布者确认结算(reward_type=1 实时走 `pointsService.addRecord` 入账领取人)→完成;放弃(领取人)→待领取;取消(发布者)→已取消。接口 `TaskController`(/task):`GET list`(附 creatorName/assigneeName)、`POST`、`POST {id}/claim|abandon|finish|confirm|cancel`,仅登录校验无新权限码。前端 `views/task/Task.vue`(全部/我发布/我领取 tab + 按身份渲染操作按钮),路由 /task,首页模块种子(`task`/任务悬赏/life/sort 7)。
- **提醒事项(V3.4,已实施)**:`family_reminder`(title/content/remind_at/repeat 0一次性/1每日/2每周/3每月/family_id/created_by/status/done)。`ReminderController`(/reminder list/create/update/delete/toggleDone)。前端 `views/reminder/Reminder.vue`,种子 `reminder`/今日提醒/life/sort 8。
- **家庭计划(V3.4,已实施)**:`family_plan`(title/description/target_date/progress/status)+ `family_plan_task`(plan_id/title/assignee_id/done/due_date)。`PlanController`(/plan CRUD + task CRUD,task 可指派成员)。前端 `views/plan/Plan.vue`,种子 `plan`/家庭计划/life/sort 9。
- **通用愿望单(V3.4,已实施)**:`content_wish`(title/reason/category/status 0待实现 1已实现 2放弃/requester_id/family_id/visibility/achieved_at)。`WishController`(/wish CRUD + toggleDone)。前端 `views/wish/Wish.vue`,种子 `wish`/愿望单/life/sort 10。
- **记账(V3.5,已实施)**:`family_book_record`(type 0支出/1收入/2转账,amount/category/note/recorded_by/recorded_at/family_id)。`BookController`(/book list/create/update/delete + 月度统计)。前端 `views/book/Book.vue`,种子 `book`/记账本/life/sort 11。
- **家谱(V3.5,已实施)**:`family_tree`(father_id/mother_id/spouse_id 自关联,spouse 双向共用;generation 世代 0=祖先逐代+1;@TableLogic deleted;软删):
  - 后端:家人 `TreeController`(`/tree` list/create/update/delete,带 @OperationLog)+ `FamilyTreeService`(新增:世代=父/母 max generation+1,配偶双向绑定 linkSpouse;**编辑:全量提交,null 字段即清空,须用 LambdaUpdateWrapper 显式 SET NULL——MP updateById 会忽略 null 字段导致解绑/清空不生效**;删除:清空他人父/母/配偶引用后再逻辑删)。
  - 前端:`views/tree/Tree.vue`(按 generation 分组、夫妻单元并排、孩子挂父母下方、成员卡片点击编辑、dialog 增删改+照片上传 el-upload→fileApi),路由 `/tree`,Home.vue/More.vue iconMap 加 `icon-tree: '🌳'`,i18n `tree.*` 23 键(zh/en)。
  - 种子:`('tree','家谱','icon-tree','/tree','life','left',14,1)`(schema.sql + live DB 已落库);冒烟 smoke_tree.py 全绿(世代推导/双向配偶/解绑/删除清引用/清空)。
- **背景音乐(V3.5,已实施)**:`sys_family_info` 增加 `music_url`/`music_title`(Family 实体+DTO+Controller 已接,schema.sql+live DB 已 ALTER);`MusicPlayer.vue` 全局组件(App.vue 挂载,左下角悬浮,localStorage `ihomy-music` 续播,切换家庭重拉);Settings.vue「家庭设置」大类内 OWNER 专属音乐区(audio/* 上传走 FileService / 外链 / 试听);i18n `music.*` 12 键。
- **照片瀑布(V3.4,已实施)**:`CascadeController`(`GET /photo/cascade` 随机,`views/cascade/Cascade.vue` 3D 翻转飘落/hover 翻正/全屏浏览(键盘+触摸),纯 CSS+JS 无依赖)。种子 `cascade`/照片瀑布/life/sort 13。**可见性过滤(2026-08,V4.1)**:`selectCascadeByFamily` 按观看者过滤——成员可见 `PUBLIC`+`FAMILY`,`PRIVATE` 仅作者本人,未登录更只可见 `PUBLIC`(Controller 传当前 userId,SQL `p.visibility IN ('PUBLIC','FAMILY') OR (p.visibility='PRIVATE' AND p.author_id=#{userId})`);同时修正 schema.sql 5 张内容表 `visibility` 列类型 `TINYINT→VARCHAR(20) DEFAULT 'FAMILY'`(与 live DB/实体 String 对齐)。
- **身份标签(V3.9,已实施)**:`family_user_label`(user_id/family_id/label/color,每家庭一套)。`ProfileController`(`GET /profile/label`、`PUT /profile/label` 保存、`DELETE /profile/label`);`SysUserMapper.xml` 成员列表 LEFT JOIN 返回 `label`(前端成员列表/动态作者展示)。未做"多重人格"会话级人格切换(V3.7 第 4 项仍规划中)。
- **国际化(V3.7 第 1 项,已实施)**:前端 vue-i18n(zh/en 双语言全量覆盖,452+ 键/文件,`check_i18n.py` 校验),语言偏好前端 localStorage。后端文案暂未资源化。
- **运维管理员(已实施,V3.8)**:账号 `ops / ops@ihomy.local` 密码 admin123(OPS 角色,仅 ops/auth 白名单);`OpsAccessFilter`:OPS 角色只放行 `/api/ops/**` 与 `/api/auth/**`(访问其它业务接口 403),非 OPS 访问 `/api/ops/**` 一律 403;router.beforeEach 对 OPS 非 /ops 页面重定向 /ops;App.vue 对 OPS 隐藏 SideTabs/MusicPlayer,AppHeader 运维态隐藏模块导航/通知铃铛,品牌显示"运维管理"点击回 /ops;`views/ops/Ops.vue` 仅系统级信息(内存/磁盘/接口健康/操作日志/异常统计),不返回任何用户隐私数据。
- **右侧半隐藏标签+每日内容(V4.1)**:`SideTabs.vue` 首页右侧固定标签(悬停拉出放大,点击 el-drawer 展开);每日一图(`DailyController` 代理 Bing 公开接口+当日缓存)、每日知识(内置 4 类×5 条,按 `GET /public/daily-knowledge?types=` 随机);设置页「每日内容」区配置开关+知识分类,偏好存 `localStorage('ihomy-daily')`(纯前端不落库)。
- **背景音乐与设置页分类(V4.1)**:`Settings.vue` 重构为左侧 menu 三大类(个人资料/家庭设置/每日内容,`settings.cat.*` i18n),右侧对应小类,移动端横排菜单;背景音乐见 V3.5 小节,仅 OWNER 可见上传区。
- **运维用户独立视图(V4.1)**:OPS 登录后路由守卫生效(任何非 /ops 页面重定向 /ops);AppHeader 运维态隐藏模块导航/通知铃铛,品牌显示"运维管理"指向 /ops;App.vue 对 OPS 隐藏 SideTabs/MusicPlayer。

## 注册/登录约定(V3.8 重做,已实施)

- 注册必填:家庭名称(创建模式)/邀请码(加入模式)、**注册邮箱**(=登录账号,全局唯一 uk_email,重复 1005)、密码 + **确认密码**(不一致 400)、**图形验证码**。**不再输入用户名/昵称**:username 后端自动取 email(满足唯一约束),昵称默认邮箱前缀,可在个人设置修改。
- 图形验证码:`GET /auth/captcha` 返回 `{captchaId,image(base64)}`,Redis `captcha:{id}` 存 5 分钟,一次性校验,错误/重用 1006;**开发/测试环境固定为 `qwer`**(`app.captcha-fixed-code` 配置,图片同步绘制该值可直接抄录;生产留空则随机)。实现:`CaptchaService`(Java2D 4 位字符+干扰线/噪点)。
- **登录用邮箱**(`POST /auth/login {email,password,captchaId,captchaCode}`,大小写不敏感,用户名登录无效)。**注册成功不自动登录**:跳转登录页,登录后才能访问自己家庭(新家庭默认私有)。
- OPS 运维账号 `ops / ops@ihomy.local` 初始密码同 admin,登录即改。
- **登出保持 URL(已实施)**:AppHeader 退出登录后 `location.reload()` 保持当前 URL(公开页变访客视图,受保护页由路由守卫跳登录),不再强制回首页。
- **操作日志不再对普通用户展示**:Settings.vue 已移除操作日志区块与 logApi 调用;后端 LogController 保留(供运维页面使用),权限收紧为 OPS。

## 代码结构

```
backend/ (Spring Boot 3, JDK 17/21, 包 com.ihomy)
  src/main/java/com/ihomy/
    IhomyApplication.java       # 主类 @MapperScan("com.ihomy.mapper")
    common/      # Result统一响应/ResultCode/异常/DictConst(字典常量)
    config/      # SecurityConfig/CorsConfig/MybatisPlusConfig/Knife4jConfig/WebAppConfig/WebSocketConfig/SqlStatementLog
    security/    # JwtUtils(JWT含familyId+role)/JwtAuthenticationFilter/LoginUser/SecurityHelper/OpsAccessFilter/TraceIdFilter
    annotation/aspect/  # @RequirePermission + Aspect;@OperationLog + Aspect
    entity/      # SysUser/Family/Blog/Diary/Album/Photo/Comment/ContentLike/Notification/Anniversary/Video/VideoWish/Reminder/Plan/Task/Points*/Chat/Wish/BookRecord/UserLabel/InvitationCode/FamilyTree...
    mapper/      # MyBatis-Plus BaseMapper 接口(自定义 SQL 全部放 resources/mapper/*.xml,接口不写 @Select/@Update 注解,参数统一 @Param)
    service/     # 具体 @Service 类(V3.8 起单实现无接口层:Blog/Diary/File/HomeModule/Auth 无接口层)
    controller/  # Auth/Public/File/Home/Blog/Diary/Anniversary/Album/Photo/Member/Like/Comment/Notification/Family/Profile/Log/Video/Points/Wish/Reminder/Plan/Book/Chat/Ops/Order/Task/Tree/Cascade...
    dto/         # Login/Register(inviteCode)/Blog(tags)/Diary/.../Task/Role/Family/Profile/Video/VideoWish/TreeMember 等
  src/main/resources/
    application.yml     # 端口8080, context-path=/api, 连接用 ihomy 账号; mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml
    mapper/*.xml        # 每个 Mapper 接口一个同名 XML(namespace=接口全限定名)
    schema.sql          # 建库+建号+建表(39张)+种子数据
  mvnw / mvnw.cmd       # Maven Wrapper,无需单独装 Maven
frontend/ (Vue3 + Vite + PWA + Element Plus + Pinia)
  src/
    api/request.js  # axios + JWT header + 401 自动刷新token
    api/index.js    # 全部模块 API 分组导出(public/home/blog/diary/file/member/anniversary/album/photo/like/comment/notification/family/profile/log/video/points/chat/ops/tree...)
    stores/user.js  # 登录状态; stores/app.js 首页聚合(family/modules/photos/stats)
    router/         # 登录守卫 + scrollBehavior(返回回顶部);含 /ops 运维护卫, /chat 需登录; /anniversary /album /album/:id /settings /cinema(public) /tree
    utils/dict.js   # 枚举词条中文映射(与后端 DictConst 对应)
    components/     # AppHeader(通知铃铛+设置下拉)/BackToTop/Breadcrumb/AlbumCarousel/HomeStatsBar/ActivityFeed/MusicPlayer/SideTabs
    styles/main.css
    views/          # Home(读 appStore)/Login/Member/Settings/More/Anniversary/album/Album/album/AlbumDetail/cinema/Cinema/diary/DiaryList/blog/(List/Detail/Edit)/points/Points/task/Task/reminder/plan/wish/book/chat/Chat/tree/Tree/cascade/Cascade/ops/Ops...
    App.vue 全局 AppHeader(家庭切换+通知铃铛)+ router-view + BackToTop
  vite.config.js   # PWA + 代理 /api -> :8080 + ElementPlus 按需
  public/favicon.svg
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
- **OPS 隔离**:OpsAccessFilter 只放行 OPS 到 `/api/ops/**`+`/api/auth/**`,其余 403;非 OPS 访问 /ops/** 一律 403。
- 点赞/评论/通知严格同家庭:`validateTarget` 校验内容 family_id 与用户一致,跨家庭返回 NOT_FOUND。

## 文档清单

- `README.md`(启动说明 + Windows 一键启动脚本用法); `Windows部署指导.md` / `Linux部署指导.md`(生产部署 NSSM/systemd/Nginx/Let's Encrypt/Docker Compose)
- `docs/需求规格说明书.docx` — 完整需求文档(9 章,Word 版,含已实现/规划功能索引)
- `scripts/start-all.ps1`(Windows 一键启动前后端,双击 `start.bat` 调用)/ `start-db.ps1`(Docker 拉起 MySQL+Redis+自动导 schema.sql,端口 38654/18469); `config/mysql/my.cnf`(端口 6306,内存优化)
- 完整接口清单:见 `docs/需求规格说明书.docx` 第 7 章与各功能小节。代码事实以 `backend/src/main/java` + `resources/schema.sql` 为准,如需检索先 `rg` 再动手。

## 文件存储策略

- **当前阶段(开发期)**:本地磁盘存储(`file.upload-dir`),零成本零内存,FileService 已实现,开箱即用。Nginx `/files/` 托管静态目录(注意负向断言正则,见上"上传文件访问")。
  - **本机 Windows 默认**:`D:/WorkSpace/ihomy/uploads`(application.yml `file.upload-dir`,用户指定的 Windows 默认盘;生产 Linux 用 `/opt/ihomy/uploads`)。改路径只需改 yml + 移动 uploads 目录,DB 存的是相对 `/files/` 的完整 URL,与物理根无关。
- **未来对接 NAS**:优先 NFS 挂载方案(把 NAS 共享目录挂到 `/opt/ihomy/uploads`,**代码零改动**)。前提是 NAS 与服务器同内网。详细步骤见 Linux 部署指导附录"对接 NAS 存储"。若 NAS 异地或要公网 CDN:再改 FileService 用 S3 兼容 SDK(NAS/MinIO/OSS 通用),用 `@ConditionalOnProperty` 切换实现,本地实现保留为默认。
- **不要主动改 FileService 的存储实现**,除非用户明确要求接 NAS/OSS。当前本地实现满足需求。
- **存储管理(V4.1,已实施)**:
  - **统一目录结构(分类目录,无 upload 中间层)**:上传按类型分目录——相册图片→`pictures/{相册名}/{相册ID}_{时间戳}_{文件名}`、视频与海报→`videos/`、音乐(audio/*)→`music/`、通用/头像→`files/{yyyyMM}/`。FileService 提供 `upload(bytes,name,type,albumId,albumName)`(图片带相册名)、`uploadVideo`(影片/海报)、3 参 `upload`(通用)重载;无相册名时图片平铺到 `pictures/`。DB 存 `/files/...` 完整 URL,与物理根解耦(详见「附:Windows 开发↔Linux 上线路径转换清单」)。
  - **存储设备**:`sys_storage_device`(family_id 家庭级隔离,name/device_type SYSTEM|NAS|REMOTE|MOUNT/root_path/status/created_by)。`GET /storage/device/list` 首项恒为系统设备(id=0,type=SYSTEM);设备增删改/一键同步需 `@RequirePermission("storage:manage")`(OWNER)。**设备归属=家庭级独立配置**,互不可见。**本期不做网盘**(WebDAV/OSS/S3 暂缓)。
  - **资源管理器**:`GET /storage/browse?deviceId&path` + `GET /storage/file?deviceId&path&download`(返回 byte[],media type 猜;下载文件名 URL-encode)。`StorageService.resolveSafe` 用 `normalize()+startsWith` 防路径遍历(越界返回 400,已实测)。前端 `/storage` 页(views/storage/Storage.vue):设备表格 + 文件浏览(面包屑/双击/预览 img·video/下载)。
  - **一键同步**:`POST /storage/sync {deviceId,includeEmpty}` → `{taskId}`;`GET /storage/sync/progress/{taskId}`。`StorageSyncRunner`(@Async 独立 bean——自调用不生效):按顶层目录建相册(相册名=目录名),`content_photo.source_path`(“设备:相对路径”)去重防重复,复制到 upload/yyyyMM 结构,完成/失败走 family_notification(create(receiverId,'system',content,null,'storage',null))。进度在内存 ConcurrentHashMap(重启丢失,可接受)。实测:2 目录 2 图同步成 2 相册 2 照片,空目录按 includeEmpty 跳过,二次同步全去重。
  - **不做自动同步**:上传只保留页面自主上传、创建相册上传、直接传 NAS 三条线。
  - **硬删除策略(V5.1,已实施)**:删除照片/相册/视频时**物理删除 DB 记录 + 删除磁盘文件**。`FileService.deleteByUrl(url)` 按 `/files/` URL 解析物理路径删文件(外链/空跳过,失败仅告警,带 `normalize()+startsWith` 防越界,顺带尝试清空父目录)。照片删除走 `PhotoMapper.deletePhysicalById`(XML 物理删,绕过全局 logic-delete);相册删除连带照片记录+文件全删(`deletePhysicalByAlbumId`);视频删除**从软删改为硬删** `deletePhysicalById`,并删 `video_url`+`poster`。**关键坑**:MyBatis-Plus 全局配 `logic-delete-field: deleted`(`application.yml`),`deleteById` 实为 UPDATE 软删——要物理删必须用自定义 XML `DELETE` 语句。**覆盖范围**:仅照片/相册/视频三处;博客封面、头像、家庭封面、背景音乐、家谱照片删除时**未**连带删文件(文件成孤儿,可接受,后续按需扩展)。

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

## 已实现功能索引(详情见「需求规格说明书」对应章节)

| 功能 | 版本 | 关键点 |
|------|------|--------|
| 注册/登录/找回 | V3.8 重做 | 邮箱=账号(uk_email),密码+确认密码+图形验证码(dev 固定 qwer);注册可以带邀请码入家庭或创建新家庭;注册成功不自动登录 |
| 家庭与成员 | V3.0/V3.6 | 多家庭(主家庭+当前家庭,Redis `user:curfamily:{id}`;`POST /auth/family/switch`)+ 邀请码(`?invite=`)/公开家庭搜索/入家申请/默认家庭 |
| 博客/日记 | V2.0 | 标签、可见范围(默认 FAMILY 全家)、评论、点赞、首页 feed |
| 相册/照片 | V2.2 | 批量上传、软删、按相册类型 public/private 映射可见范围 |
| 家庭纪念日 | V3.2 | 阳历/阴历,支持季月,生日关联成员,首页倒计时 |
| 放映厅 | V3.2 | 视频库+想看,大文件上传(500MB) |
| 评论/点赞/通知 | V3.0 | 同家庭隔离,评论树,站内通知铃铛 |
| 操作日志 | V2.0/V3.8 | AOP `@OperationLog` + traceId 链路;运维页可检索 |
| 积分/签到/兑换 | V3.4 | 日日签 5 分+连续加成,发布内容给积分 |
| 任务悬赏 | V3.4 | 状态机 待领取→进行中→待确认→完成,可积分/物品 |
| 提醒事项 | V3.4 | 一次性/每日/每周/每月 |
| 家庭计划+子任务 | V3.4 | 进度联动 |
| 愿望单 | V3.4 | 分类/达成/放弃 |
| 记账本 | V3.5 | 支出/收入/转账,月度统计+分类榜 |
| 聊天室 | V3.3 | WebSocket 分家庭房间,Redis 未读数 |
| 家谱 | V3.5 | family_tree 世代树,双向配偶,软删 |
| 背景音乐 | V3.5/V4.1 | `sys_family_info.music_url/music_title`,全局 MusicPlayer,设置页 OWNER 区 |
| 照片瀑布 | V3.4/V4.1 | `/photo/cascade` 随机飘落,可见性过滤 |
| 运维(OPS) | V3.8/V4.1 | 资源统计/日志检索,行列隔离,独立运维视图 |
| 字典表/枚举单词化 | V3.9 | `sys_dict_item`+`DictConst`+`utils/dict.js` |
| 身份标签 | V3.9 | `family_user_label` 每家庭一套,成员列表/动态展示 |
| 国际化 i18n | V3.7/V4.0 | vue-i18n 全页中英,顶栏+设置页切换 |
| 页面主题 | V4.0 | 明暗模式+主题色(html.dark + CSS 变量,localStorage) |
| 移动端 PWA | V4.0 | 安装引导、刘海屏安全区、触控 44px |
| 每日内容 | V4.1 | SideTabs 每日一图+每日知识,localStorage 配置 |
| 混淆 ID | V3.7 | share_token + `?hid=`,新家庭默认私有 |
| 存储管理 | V4.1 | 家庭级设备+文件浏览器+一键同步(见文件存储策略小节);存量迁移待做 |
| 物品定位 | V5.0 | 1期已上线:五级粒度(家>房子>房间>家具>位置,支持多套房+多楼层),房子/房间/家具/物品 CRUD+跨级搜索;2期户型图/3期 AI 语义待做(见规划事项) |
| 文件硬删除 | V5.1 | 删照片/相册/视频→物理删 DB 行 + 删磁盘文件(`FileService.deleteByUrl`);视频从软删改硬删;自定义 XML DELETE 绕过全局 logic-delete;仅覆盖照片/相册/视频,博客封面/头像/音乐等未连带删 |

## 国际化约定(V4.0)

- 语言文件只有 `frontend/src/i18n/zh-CN.js` + `en.js`,**新增页面/文案必须两文件同步加键**(结构一致,键不齐会被 check_i18n.py 检出)。
- 模板用 `$t('module.key')`,script 内 `const { t } = useI18n()`;ElMessage/ElMessageBox 文案同样走 t。语言名(中文/English)、身份标签值(爸爸/妈妈)、用户生成内容不翻译。
- 偏好存 `localStorage('ihomy-lang')`,默认跟随浏览器语言;`applyLocale()` 切换并同步 Element Plus 组件文案(App.vue `el-config-provider`)。
- 按功能划分键组(settings/blog/diary/album/...),公共文案放 `common`(确定按钮/取消/删除/加载中)。

## 规划事项(未实现,排序按推荐优先级)

| 优先级 | 规划 | 版本来源 | 要点 |
|--------|------|---------|------|
| P1 | 存储管理-存量迁移 | V4.1 | 设备/文件浏览器/一键同步已完成;存量 `/uploads/` 旧文件重归档(移入 upload/yyyyMM 结构并更新 DB 路径)待做 |
| P2 | 物品定位-户型图 | V5.0 | 1期(物品清单+搜索)已完成;2期户型图:房间矩形绘制/物品相对坐标摆放,以 room.id 挂载(数据结构已预留) |
| P2 | 用户使用指导/帮助 | V3.5 | 新手引导弹窗+帮助页 |
| P2 | 家庭公告/广告位 | V3.5 | 自建家庭公告(不接第三方广告,隐私原因) |
| P3 | UI 重新设计 | V3.7 | 设计语言/过渡动效/加载骨架屏/暗色模式完善,先首页导航再逐页 |
| P3 | 多重人格 | V3.7 | 基于身份标签扩展,一账号多标签可切换发表,会话级 currentLabel(Redis 或前端状态),与家庭切换正交 |
| P3 | AI API 对接 | V4.1 | 统一对接大模型 API(聊天/内容生成),需配置 API Key 与服务商(OpenAI 兼容协议),待细化 |
| P3 | 物品定位-AI 语义 | V5.0 | 3期(待2期后):自然语言"找找我的工具箱"→AI 拆出名称+别名→服务器 SQL 查询;"把工具箱放到门口的柜子最上层抽屉"→AI 按 家/房子/房间/家具/位置 五级解析,缺层追问填满后返回粒度值→服务器拼 INSERT(决策已定,依赖 AI API) |
| P3 | 物品定位+户型图 | V4.1 | 已拆分分期落地:1期(物品清单+搜索)已完成,2期户型图、3期 AI 语义见上两行 |
| P4 | 手机号注册 | V3.8 | 需短信服务商(阿里云等),未接入前不实现(sys_user.phone 字段已存在) |
| P4 | 商业化/多租户 | V3.5 | SaaS 订阅制评估,条件允许再做 |
| P4 | 广告模块 | V3.5 | 家庭私密场景接第三方广告转化低且有隐私争议,优先自建"家庭公告/赞助位" |

> 优先级含义:P1 用户价值高且技术上可行(依赖最少);P2 锦上添花;P3 结构性改动;P4 依赖外部条件(短信商/商业决策)。实现新功能前先 `grep schema.sql + router/` 对照模块种子。

## 环境检查(参考)

本机已装:JDK 21、Node 20、MySQL 8、python-docx。Maven 用 Wrapper(或 temp 目录 3.9.9)。Redis 本机可能未装(可用 Docker 或 Memurai)。