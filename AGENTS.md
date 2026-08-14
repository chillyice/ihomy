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
- **41 张表**,前缀分类(**V4.1 起执行全面重命名**):
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
- **V3.0 核心能力(已实施)**:
  - **统一内容点赞/评论**:`content_like`(content_type+content_id+user_id+family_id,UNIQUE 防重复)+ `content_comment`(parent_id 回复树)。`LikeController`(`POST /like/toggle`、`GET /like/state`)、`CommentController`(`GET /comment/list` 树、`POST /comment`、`DELETE /comment/{id}`)。点赞/评论仅限同家庭内容(跨家庭返回 NOT_FOUND)。删除评论:OWNER 或作者本人。
  - **站内通知**:`family_notification`。`NotificationService`(create/list/unreadCount/markRead/markAllRead),评论/回复自动通知被回复者与内容作者。`NotificationController`。
  - **成员管理(邀请制)**:`MemberController`(`GET /member/list` 成员+角色;`PUT /member/{id}/role` 改角色;`DELETE /member/{id}` 移出;`POST /member/invite` 生成码;`GET /member/invite` 码列表)。**加入家庭走注册带邀请码**:`RegisterDTO.inviteCode` 可选,`AuthServiceImpl.register` 检测到邀请码则直接加入受邀家庭(预设角色,不新建家庭),否则自建家庭并绑定 OWNER。
  - **家庭设置 / 个人资料 / 操作日志**:`FamilyController`(`GET`/`PUT /family`,@family:manage)、`ProfileController`(`GET`/`PUT /profile`)、`LogController`(`GET /log` 分页,@log:view)。`sys_user`(birthday,gender)、`sys_family_info`(description,is_public)、`content_photo`(taken_at,location) 均已落库。
  - **权限注解**:`@RequirePermission("code")` + AOP `RequirePermissionAspect`(不通过抛 FORBIDDEN)。`SecurityHelper.hasPermission` 对 OWNER 恒真,其余查 `SysRoleMapper.selectAuthCodesByUserAndFamily`。`sys_auth` 种子含 `comment:create/delete`、`invite:create`、`log:view`、`family:manage`、`user:manage` 等。
  - **JWT 携带 familyId**:`JwtUtils.generateAccessToken(userId, username, role, familyId)` 写入 claim;`JwtAuthenticationFilter` 用它构造 `LoginUser(familyId)`(缺失时回退查库),保证 MEMBER 权限码按家庭解析正确。
- **放映厅(V3.2)**:`content_video` + `content_video_wish`。`content_video` 字段含豆瓣式属性:`media_type`(movie/series/other)、`genres`(题材,逗号分隔)、`region`、`year`、`language`、`duration`(分钟)/`episodes`(剧集数)、`director`、`actors`、`rating`(0-10 一位小数)、`intro`、`poster`、`video_url`(经 FileService 上传)、`uploader_id`、`family_id`、`visibility`、软删。`content_video_wish`(想看):`title`/`genres`/`reason`/`status`(0 待入库 1 已入库)/`requester_id`/`family_id`。`VideoController`(`GET /video/list` 按 family 过滤+keyword/mediaType 搜索;`POST /video/upload` 上传文件返回 `{url}`;`POST /video`/`PUT/DELETE /video/{id}` 增删改,改删校验上传者或 OWNER;`POST /video/wish` 提交想看、`GET /video/wish/list`、`PUT /video/wish/{id}/done` 标记入库、`DELETE /video/wish/{id}`)。**上传限制**:后端 `multipart max-file-size 500MB` + nginx `client_max_body_size 500m`(视频大文件必需,已调)。
- **默认可见范围=全家可看(V3.3)**:博客/日记发布默认 `visibility=FAMILY`(家庭可见),前后端一致(BlogServiceImpl/DiaryServiceImpl null→FAMILY)。历史数据已统一 UPDATE。放映厅固定 FAMILY;照片按相册类型 public→PUBLIC/private→FAMILY。visibility 枚举 `PRIVATE仅自己/MEMBERS指定成员/GROUPS指定群组/FAMILY家庭可见/PUBLIC公开`。
- **RBAC 权限模型**:用户-角色-权限三层(`sys_user` → `sys_user_role` → `sys_role` → `sys_role_auth` → `sys_auth`)。`sys_user` 无 `role` 字段,角色通过关联表查询。预设 4 角色:OWNER/MEMBER/CHILD/GUEST。同一用户在不同家庭可有不同角色(`sys_user_role.family_id` 区分)。
- `sys_operation_log`(系统操作日志表):**已实现**(V2.0)AOP + `@OperationLog` 注解自动记录。记录范围:登录/登出、增删改内容、成员管理、配置变更。仅家长可查询(`LogController` GET `/log`,@log:view)。
- **链路追踪(V3.0.1)**:`TraceIdFilter`(servlet 首个过滤器)为每个请求生成 `traceId`(16 位 UUID 短串),写入 MDC `traceId` + 响应头 `X-Trace-Id`(也接受入站 `X-Trace-Id` 透传)。日志模式为 `%X{traceId:-}`,控制台与文件(`./logs/ihomy.log`)均带 traceId。`sys_operation_log` 新增 `trace_id` 字段(AOP 在该线程取 MDC 落库),保证一次操作在日志与 DB 里可跨模块/跨步骤串联。
- **SQL 日志(V3.0.2)**:`mybatis-plus.log-impl` 用自定义 `com.ihomy.config.SqlStatementLog`:打印 SQL 语句与参数(`==> Preparing / Parameters`),**过滤结果集行**(`<== Columns/Row/Total`,按 `s.startsWith("<== ")` 丢弃),既保留 SQL 调试信息又减小日志占用。
- **上传文件访问(nginx)**:`nginx.conf` 增加 `location /files/ { alias <仓库>/uploads/; }`。**注意**:图片扩展名正则 location 会用 `root` 覆盖 `/files/` 别名(导致路径解析错),必须写成 `location ~* ^/(?!files/).+\.(...)$`,用负向断言排除 `/files/`,否则上传图片 404。
- **头像/封面走上传**:`profile.avatar` 与 `family.coverImage` 走 `POST /file/upload`(返回 `{url}`),保存时随 `PUT /profile`、`PUT /family` 落库。后台 `avatar`/`coverImage` 仍为 URL 字符串字段,无需改接口。
- 默认管理员:`admin / admin123`(BCrypt),登录后应改密。初始化时自动绑定 OWNER 角色。
- **多家庭方案定稿(V3.6,已实施)**:
  - **家庭双概念**:`sys_user.family_id` = 主家庭(注册归属/默认);**当前家庭** = 会话级概念,存 Redis `user:curfamily:{userId}`(登录/切换写入,refresh 读取回填),JWT familyId 为快照。登录后下拉切换,所有接口按 JWT familyId 取数。
  - **我的家庭列表**:`GET /auth/families` 从 `sys_user_role` 查全部家庭绑定(含主家庭),返回 `{familyId,name,role,isPrimary,isCurrent}`。
  - **切换家庭**:`POST /auth/family/switch {familyId}` 校验该用户在目标家庭有角色绑定 → 更新 Redis 当前家庭 → 重签 access token。支持 `{familyId, setDefault}`(setDefault=true 时同时写 `sys_user.default_family_id`);`GET /auth/families` 返回 `isDefault`。
  - **默认家庭概念(已实施)**:`sys_user.default_family_id` = 用户设置的默认家庭(可空=主家庭 family_id)。**当前家庭解析优先级**:Redis `user:curfamily:{userId}` > `default_family_id` > `family_id`(主家庭)。
  - **加入方式四条线**:①分享注册链接=邀请码 URL 化;②`GET /family/search?keyword=` 搜索公开家庭(is_public=1,id/名称模糊) + `POST /family/apply {familyId,message}` 申请,OWNER 审核 `GET /family/apply/list` + `PUT /family/apply/{id}?action=approve|reject`(审核通过绑定 MEMBER,申请与结果走 family_notification,通知 type=system content_type=family_apply sourceId=申请ID);③注册页邀请码(已有);④已登录用户 `POST /auth/join {inviteCode}` 复用邀请码核销加入(不自动切换)。
  - **演示家庭**:`sys_family_info.is_demo` 标记(1 号家庭"ihomy 演示家庭"),`sys_user.is_fake` 标记假用户;**is_fake 用户禁止登录**(FORBIDDEN);假数据:demo_owner(OWNER)/demo_member(MEMBER)/demo_child(CHILD) + 3 博客/2 日记/1 纪念日。
  - **家庭访问与隐私**:`GET /public/home?home_id=N` 与 `/public/feed` 支持指定家庭(参数名 **home_id**,不是 homeId):成员→完整数据;非成员/访客→仅 `is_public=1` 的公开内容(family/modules/photos,stats 置空),`is_public=0` 或不存在→NOT_FOUND。
- **混淆 ID 访问(已实施,V3.7 第 2 项落地)**:`sys_family_info.share_token` 16 位随机 token(UUID 截取),注册创建家庭时生成并随登录/注册接口返回(响应 `shareToken`);`GET /public/home`、`/public/feed` 支持 `?hid=<share_token>`(**优先于** `?home_id=`,校验顺序 hid > home_id > 当前家庭/默认演示家庭);**新家庭默认 `is_public=0` 私有**(schema 默认已改),访客访问私有家庭 hid → NOT_FOUND;演示家庭 share_token 固定 `98a06619927f11f1`。旧 `home_id` 参数保留兼容。
- **聊天室(V3.3,已实施)**:`family_chat_message`(family_id/sender_id/content/created_at)+ `family_chat_read`(用户未读数)。原生 WebSocket(非 STOMP),握手 `?token=` 验 JWT,按家庭分房间广播,发送即落库+广播,重连拉历史消息。`ChatController`(GET history/已读数、POST 标记已读)。首页模块种子 `chat`/聊天室/life/sort 12(注意:schema.sql 种子缺 chat 行,live DB 有,建新库需补)。
- **积分商城(V3.4,已实施)**:4 表——`family_checkin`(user_id+checkin_date UNIQUE 每日一次)、`family_points_record`(change_type CHECKIN/REWARD/REDEEM,balance=变动后余额,`change` 字段实体已反引号转义)、`family_points_product`(stock=-1 不限量、per_limit=0 不限次)、`family_points_order`(product_name 快照,status 0待核销/1已核销)。签到规则:基础 5 分 + 连续加成 `5+(streak-1)%7`(7 天一轮回),昨日签过则 streak+1,断签重计;重复签到 1007。内容奖励:博客 +10 / 日记 +8 / 照片每张 +2(批量一次流水,备注"上传照片 ×N")/ 视频 +15。兑换校验:积分不足 1008、兑完或超限兑 1009;`PointsService.redeem` 低并发无锁(ponytail: 需防超卖时改 UPDATE ... WHERE stock>0)。接口 `PointsController`(/points):`GET stats`、`POST checkin`、`GET products`(附当前用户 redeemedCount)、`POST/PUT/DELETE products(/id)` 与 `PUT orders/{id}/taken` 和 `GET orders/all` 需 `@RequirePermission("points:manage")`(仅有 OWNER 绑定,种子已同步 live DB)。首页模块种子(`points`/积分商城/life/sort 6,front/fixed 已落库)。
- **任务悬赏(V3.4,已实施)**:`family_task`(family_id/title/description/reward_type 0无1积分2物品/reward_points/reward_item/status 0待领取 1进行中 2待确认 3已完成 4已取消/created_by/assignee_id)。状态机:发布→他人领取(发布者不可自领)→领取人完成申报→发布者确认结算(reward_type=1 实时走 `pointsService.addRecord` 入账领取人)→完成;放弃(领取人)→待领取;取消(发布者)→已取消。接口 `TaskController`(/task):`GET list`(附 creatorName/assigneeName)、`POST`、`POST {id}/claim|abandon|finish|confirm|cancel`,仅登录校验无新权限码。首页模块种子(`task`/任务悬赏/life/sort 7)。
- **提醒事项(V3.4,已实施)**:`family_reminder`(title/content/remind_at/repeat 0一次性/1每日/2每周/3每月/family_id/created_by/status/done)。`ReminderController`(/reminder list/create/update/delete/toggleDone)。种子 `reminder`/今日提醒/life/sort 8。
- **家庭计划(V3.4,已实施)**:`family_plan`(title/description/target_date/progress/status)+ `family_plan_task`(plan_id/title/assignee_id/done/due_date)。`PlanController`(/plan CRUD + task CRUD,task 可指派成员)。种子 `plan`/家庭计划/life/sort 9。
- **通用愿望单(V3.4,已实施)**:`content_wish`(title/reason/category/status 0待实现 1已实现 2放弃/requester_id/family_id/visibility/achieved_at)。`WishController`(/wish CRUD + toggleDone)。种子 `wish`/愿望单/life/sort 10。
- **记账(V3.5,已实施)**:`family_book_record`(type 0支出/1收入/2转账,amount/category/note/recorded_by/recorded_at/family_id)。`BookController`(/book list/create/update/delete + 月度统计)。种子 `book`/记账本/life/sort 11。
- **家谱(V3.5,已实施)**:`family_tree`(father_id/mother_id/spouse_id 自关联,spouse 双向共用;generation 世代 0=祖先逐代+1;@TableLogic deleted;软删):
  - 后端:家人 `TreeController`(`/tree` list/create/update/delete,带 @OperationLog)+ `FamilyTreeService`(新增:世代=父/母 max generation+1,配偶双向绑定 linkSpouse;**编辑:全量提交,null 字段即清空,须用 LambdaUpdateWrapper 显式 SET NULL——MP updateById 会忽略 null 字段导致解绑/清空不生效**;删除:清空他人父/母/配偶引用后再逻辑删)。
  - 种子:`('tree','家谱','icon-tree','/tree','life','left',14,1)`(schema.sql + live DB 已落库);冒烟 smoke_tree.py 全绿(世代推导/双向配偶/解绑/删除清引用/清空)。
- **背景音乐(V3.5,已实施)**:`sys_family_info` 增加 `music_url`/`music_title`(Family 实体+DTO+Controller 已接,schema.sql+live DB 已 ALTER)。
- **照片瀑布(V3.4,已实施)**:`CascadeController`(`GET /photo/cascade` 随机)。种子 `cascade`/照片瀑布/life/sort 13。**可见性过滤(2026-08,V4.1)**:`selectCascadeByFamily` 按观看者过滤——成员可见 `PUBLIC`+`FAMILY`,`PRIVATE` 仅作者本人,未登录更只可见 `PUBLIC`(Controller 传当前 userId,SQL `p.visibility IN ('PUBLIC','FAMILY') OR (p.visibility='PRIVATE' AND p.author_id=#{userId})`);同时修正 schema.sql 5 张内容表 `visibility` 列类型 `TINYINT→VARCHAR(20) DEFAULT 'FAMILY'`(与 live DB/实体 String 对齐)。
- **身份标签(V3.9,已实施)**:`family_user_label`(user_id/family_id/label/color,每家庭一套)。`ProfileController`(`GET /profile/label`、`PUT /profile/label` 保存、`DELETE /profile/label`);`SysUserMapper.xml` 成员列表 LEFT JOIN 返回 `label`。未做"多重人格"会话级人格切换(V3.7 第 4 项仍规划中)。
- **运维管理员(已实施,V3.8)**:账号 `ops / ops@ihomy.local` 密码 admin123(OPS 角色,仅 ops/auth 白名单);`OpsAccessFilter`:OPS 角色只放行 `/api/ops/**` 与 `/api/auth/**`(访问其它业务接口 403),非 OPS 访问 `/api/ops/**` 一律 403;运维接口仅返回系统级信息(内存/磁盘/接口健康/操作日志/异常统计),不返回任何用户隐私数据。
- **每日内容后端(V4.1)**:每日一图(`DailyController` 代理 Bing 公开接口+当日缓存)、每日知识(内置 4 类×5 条,按 `GET /public/daily-knowledge?types=` 随机);偏好存 `localStorage('ihomy-daily')`(纯前端不落库)。

## 注册/登录约定(V3.8 重做,已实施)

- 注册必填:家庭名称(创建模式)/邀请码(加入模式)、**注册邮箱**(=登录账号,全局唯一 uk_email,重复 1005)、密码 + **确认密码**(不一致 400)、**图形验证码**。**不再输入用户名/昵称**:username 后端自动取 email(满足唯一约束),昵称默认邮箱前缀,可在个人设置修改。
- 图形验证码:`GET /auth/captcha` 返回 `{captchaId,image(base64)}`,Redis `captcha:{id}` 存 5 分钟,一次性校验,错误/重用 1006;**开发/测试环境固定为 `qwer`**(`app.captcha-fixed-code` 配置,图片同步绘制该值可直接抄录;生产留空则随机)。实现:`CaptchaService`(Java2D 4 位字符+干扰线/噪点)。
- **登录用邮箱**(`POST /auth/login {email,password,captchaId,captchaCode}`,大小写不敏感,用户名登录无效)。**注册成功不自动登录**:跳转登录页,登录后才能访问自己家庭(新家庭默认私有)。
- OPS 运维账号 `ops / ops@ihomy.local` 初始密码同 admin,登录即改。
- **操作日志仅 OPS 可见**:后端 LogController 权限收紧为 OPS。

## 代码结构

```
backend/ (Spring Boot 3, JDK 17/21, 包 com.ihomy)
  src/main/java/com/ihomy/
    IhomyApplication.java       # 主类 @MapperScan("com.ihomy.mapper")
    common/      # Result统一响应/ResultCode/异常/DictConst(字典常量)/SolarUtil(NOAA太阳位置算法)
    config/      # SecurityConfig/CorsConfig/MybatisPlusConfig/Knife4jConfig/WebAppConfig/WebSocketConfig/SqlStatementLog
    security/    # JwtUtils(JWT含familyId+role)/JwtAuthenticationFilter/LoginUser/SecurityHelper/OpsAccessFilter/TraceIdFilter
    annotation/aspect/  # @RequirePermission + Aspect;@OperationLog + Aspect
    entity/      # SysUser/Family/Blog/Diary/Album/Photo/Comment/ContentLike/Notification/Anniversary/Video/VideoWish/Reminder/Plan/Task/Points*/Chat/Wish/BookRecord/UserLabel/InvitationCode/FamilyTree...
    mapper/      # MyBatis-Plus BaseMapper 接口(自定义 SQL 全部放 resources/mapper/*.xml,接口不写 @Select/@Update 注解,参数统一 @Param)
    service/     # 具体 @Service 类(V3.8 起单实现无接口层:Blog/Diary/File/HomeModule/Auth 无接口层);SunService(IP定位+NOAA时隙)/WeatherService(和风天气代理)
    controller/  # Auth/Public(File/Home/Blog/Diary/Anniversary/Album/Photo/Member/Like/Comment/Notification/Family/Profile/Log/Video/Points/Wish/Reminder/Plan/Book/Chat/Ops/Order/Task/Tree/Cascade...(Public 含 /sun-info+/weather+/home+/feed)
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
    router/         # 登录守卫 + scrollBehavior(返回回顶部);含 /ops 运维护卫, /chat 需登录; /anniversary /album /album/:id /settings /cinema(public) /tree /light-test(光照测试)
    utils/dict.js   # 枚举词条中文映射(与后端 DictConst 对应)
    utils/windowLight.js  # getSunScene(sunInfo,slotIndex)+currentSlotIndex()+makeRays():体积光调色板/光束/阴影参数
    components/     # AppHeader/BackToTop/Breadcrumb/AlbumCarousel/HomeStatsBar/ActivityFeed/MusicPlayer/SideTabs/TopBarExtras(时钟+天气)/RoomParticles(粒子层)
    styles/main.css
    views/          # Home(沉浸式首页方案B)/Login/Member/Settings/More/Anniversary/album/Album/album/AlbumDetail/cinema/Cinema/diary/DiaryList/blog/(List/Detail/Edit)/points/Points/task/Task/reminder/plan/wish/book/chat/Chat/tree/Tree/cascade/Cascade/ops/Ops/lighttest/LightTest...
    App.vue
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
  - **资源管理器**:`GET /storage/browse?deviceId&path` + `GET /storage/file?deviceId&path&download`(返回 byte[],media type 猜;下载文件名 URL-encode)。`StorageService.resolveSafe` 用 `normalize()+startsWith` 防路径遍历(越界返回 400,已实测)。
  - **一键同步**:`POST /storage/sync {deviceId,includeEmpty}` → `{taskId}`;`GET /storage/sync/progress/{taskId}`。`StorageSyncRunner`(@Async 独立 bean——自调用不生效):按顶层目录建相册(相册名=目录名),`content_photo.source_path`(“设备:相对路径”)去重防重复,复制到 upload/yyyyMM 结构,完成/失败走 family_notification(create(receiverId,'system',content,null,'storage',null))。进度在内存 ConcurrentHashMap(重启丢失,可接受)。实测:2 目录 2 图同步成 2 相册 2 照片,空目录按 includeEmpty 跳过,二次同步全去重。
  - **不做自动同步**:上传只保留页面自主上传、创建相册上传、直接传 NAS 三条线。
  - **硬删除策略(V5.1,已实施)**:删除照片/相册/视频时**物理删除 DB 记录 + 删除磁盘文件**。`FileService.deleteByUrl(url)` 按 `/files/` URL 解析物理路径删文件(外链/空跳过,失败仅告警,带 `normalize()+startsWith` 防越界,顺带尝试清空父目录)。照片删除走 `PhotoMapper.deletePhysicalById`(XML 物理删,绕过全局 logic-delete);相册删除连带照片记录+文件全删(`deletePhysicalByAlbumId`);视频删除**从软删改为硬删** `deletePhysicalById`,并删 `video_url`+`poster`。**关键坑**:MyBatis-Plus 全局配 `logic-delete-field: deleted`(`application.yml`),`deleteById` 实为 UPDATE 软删——要物理删必须用自定义 XML `DELETE` 语句。**覆盖范围**:仅照片/相册/视频三处;博客封面、头像、家庭封面、背景音乐、家谱照片删除时**未**连带删文件(文件成孤儿,可接受,后续按需扩展)。
- **博客自定义分类(V5.2,已实施)**:`content_blog.category VARCHAR(50)`(schema.sql + live DB 已 ALTER)。`BlogController` 增 `GET /blog/categories` 返回 `SELECT DISTINCT category WHERE family_id=?`(空 NULL 排除);博客列表/编辑/详情均带 category 字段。前端博客列表页顶部水平分类筛选条(全部 + 各分类 tag,点击切换),编辑器 `el-select filterable allow-create` 选或建分类;i18n `blog.category/categoryPlaceholder` 中英。无新权限码,登录即可用。
- **天气代理(V5.2 → V5.6 重构)**:`WeatherService.java` 用和风天气 **JWT(Ed25519)身份认证**(JDK 21 原生 `EdDSA`,无新依赖),凭证四件套 `app.weather-api-host`/`weather-project-id`/`weather-key-id`/`weather-private-key`(PEM 私钥,YAML `|` 块标量)。**全部留空=禁用**(`/isEnabled()` 返回 false,接口返回 null,前端降级)。**凭证优先从 `sys_weather_credential` 表读 `status=1` 的记录(多环境账本,DB 空 fallback yml)**:Win 测试种子 status=1,Linux 生产种子 status=0(上线时 SQL 翻转)。**私钥不入 git**(schema.sql 种子 `private_key=NULL`,application.yml 留空;部署后手动 UPDATE 填入;`credentials.local.sql` 模板在 resources 下,.gitignore 忽略)。**月度配额 49999 次/月**(`isQuotaExceeded()` Redis 计数器 `ihomy:weather:quota:{yearMonth}` + DB fallback;超限返回 null 前端降级,次月自动恢复)。**每次 API 调用记录日志**(`sys_weather_log` 表:api_type/location_id/status/cost_ms/response/error_msg;天气数据公开可存 response,quota 接口响应可能含账号信息不存)。Redis 缓存:now 30m / forecast 30m / warning 5m / indices 30m / air 30m / minutely 10m / location 6h。`GET /api/public/weather` 返回 `{temp,condition,text}`(顶栏简版);`GET /api/public/weather/detail` 返回聚合 `{now,daily,hourly,warning,indices,air,minutely}`(首页天气面板点击展开)。`GET /api/ops/weather/quota` 控制台 API 用量统计(OPS 运维页新增"和风天气 API"标签页,watch tab 懒加载)。前端 `TopBarExtras.vue`/`Home.vue` 天气面板点击展开详情(7d 预报+预警+生活指数+空气+分钟降水)。**光照测试页天气控制(V5.6)**:`LightTest.vue` 控制台新增 ☀️/☁️/🌧️/❄️ 4 按钮切换天气模式,通过 `weatherMultiplier`(晴 1.0/多云 0.55/雨 0.25/雪 0.4)衰减 `lightOpacity`/`rays`/`reflectionOpacity`,默认晴天高亮。
- **太阳位置系统(V5.2,已实施)**:
  - **SolarUtil.java**(NOAA 算法,纯数学无外部依赖):给定 lat/lng/timezone/date → 96 个 15 分钟时隙(00:00-23:45)的太阳高度角/方位角 + 日出日落/月相 + 月出月落。核心方法 `buildSlots(lat,lng,tzOffsetMin,date)` 返回 `List<Map>`(time/altitude/azimuth),`sunAltAz(lat,dec,haDeg)` 计算单点高度+方位。
  - **关键算法修复**:① 时角 `ha` 归一化到 -180~180°(`while (ha>180) ha-=360; while (ha<-180) ha+=360`),修复凌晨 `utcMin - solarNoonMin > 180°` 导致 `cos(ha)` 正负反转、方位角算反;② 方位角用 `atan2(-sin(ha), (sin(dec)-sin(lat)*sin(alt))/(cos(lat)*cos(alt)))` 替代 `acos(cosAz) + if(ha>0) 360-az`,修复夏季高纬度日出东北方时 `acos` 返回值象限歧义(07:45 az=269°→08:00 az=92° 跳变)。修复后全天平滑:日出 76°(东偏北)→ 正午 176°(正南)→ 日落 288°(西偏北)。
  - **SunService.java**:IP 定位(ip-api.com,`/getIp` 取 client IP)→ lat/lng/timezone,Redis 缓存 6h 位置(`ihomy:sun:loc:`)+ 12h 时隙表(`ihomy:sun:slots:{date}`,按日缓存避免重复计算)。本地/开发 IP 走默认坐标(北京)。
  - **接口**:`GET /api/public/sun-info` 公开,返回 `{date,location:{lat,lng,city},sunrise,sunset,moonrise,moonset,moonPhase,slots:[{time,altitude,azimuth}×96]}`。
- **沉浸式首页(V5.2,已实施)**:Home 路由 `meta: { immersive: true }`,`App.vue` 检测 `isImmersive` 隐藏全局 AppHeader/BackToTop/SideTabs/MusicPlayer(页面自带沉浸式 UI)。**方案 B「展开的相册」**:牛皮纸托底(`.album-base` inset -30px)+ 中央照片轮播主舞台 + 左侧毛玻璃面板(动态 feed 微信风:头像独立行 + feed-content 包裹 nick+bubble)+ 右侧毛玻璃面板(时间天气 + 纪念日倒计时)+ 黑胶唱片播放器(`.vinyl-wrap` fixed 右下,半藏右边界 `margin-right: -60px`,hover 滑出 + scale 1.08)+ 顶栏(头像下拉 个人/设置/退出 + 语言切换 applyLocale + 消息铃铛 el-popover+el-badge + 更多导航 navPrimary 5 个 + navSecondary 下拉 + 光照测试链接)+ 背景色块(blur 60px opacity 0.4,色值 #9CD0B5/#EDDB8C/#ECC0AC/#A8C9DE/#C0D8A8,背景米白 #EDE4D3→#E2D8C4→#D6CBB4)+ GSAP 入场动画(面板/相册,光柱不入场直接显示当前状态)。毛玻璃样式:`rgba(255,255,255,0.25)` + `blur(30px)` + `border: 1px solid rgba(255,255,255,0.5)` + `border-radius: 28px`(无 mask 渐变);两侧面板宽度均 380px,left/right: 24px。
- **体积光系统(V5.2,已实施)**:基于真实太阳位置的丁达尔效应,分层 z-index(从底到顶):bg-blobs(1)→ ~~ambient-layer(2,multiply 暖染,已删除)~~→ album-stage/panels(30/40)→ **window-shadow(35,窗框内阴影,在内容上方)**→ vignette(44)→ dust-layer(46,screen)→ **light-layer(48,screen,最顶层)**→ top-bar(50)。
  - **光束(7 条羽毛状)**:`makeRays(opacityScale)` 返回 7 条宽度 50-110px、偏移 -210~+210px、blur 30-55px 的光柱;光源水平位置 `sourceX = (az-90)/180*100`(东=左 5%,南=中 50%,西=右 95%);旋转 `rotation = (az-180)*0.55`(clamp ±55°,transform-origin center,正旋转=顺时针=顶部转右;光源在左→光柱指向右下,与光源位置一致)。
  - **光源辉光**:`.light-bloom` 700px 圆形,`radial-gradient(bloom→mid→transparent)` + `blur(60px)`。
  - **窗框内阴影**(`.window-shadow` z-index 35,multiply 变暗):竖直条 `.shadow-v`(top -30%、height 160%、left 50% 居中、transform-origin top center、rotation = `azDev*0.5` 即太阳在东→阴影偏左,太阳在西→阴影偏右)+ 横向条 `.shadow-h`(top = `80-alt*0.8`,太阳高→近顶部,太阳低→远;跟随竖直条同角度旋转)。
  - **暗角 + 灰尘粒子**:40 个灰尘粒子(screen 发光,随机位置 + 漂浮动画)。
  - **windowLight.js**:`getSunScene(sunInfo, slotIndex)` 基于高度角分 4 档(黄金时刻 alt<6° 暖橙/晨昏 <15° 暖黄/日间 <60° 暖白/正午 ≥60° 冷白),每档 bloom/core/mid/ambient/shadow 调色板 + rayOpacity(1.3/1.15/1.05/0.95);夜晚(alt<-6°)按月相 `moonBrightness`(0~1,新月 0 满月 1)决定月光强度(bloom/ray/shadow 系数)。`currentSlotIndex(sunInfo)` 按当前时间取 15 分钟时隙索引。
  - **光照测试页**:`/light-test` 路由(LightTest.vue),1 分钟循环 96 时隙(625ms/段),信息面板显示时间/高度/方位/日出/日落/月相 + 进度条,用于调试验证全天光照过渡。
- **nginx alias + FileService urlPrefix 健壮性(V5.2,已实施)**:nginx.conf `location /files/` 的 `alias D:/WorkSpace/ihomy/uploads;` 末尾缺斜杠导致 404,改为 `alias D:/WorkSpace/ihomy/uploads/;`。`FileService.saveTo` 和 `deleteByUrl` 统一 `urlPrefix.replaceAll("/+$", "")` 去尾斜杠,无论 yml 写 `/files` 还是 `/files/` 都正确拼接。同时 nginx 图片扩展名 location 用负向断言 `location ~* ^/(?!files/).+\.(...)$` 排除 `/files/`,避免 root 覆盖 alias 导致 404。

- **光照生命周期重构(V5.3,已实施)**:旋转/颜色/强度全部由日出日落时间驱动(不再由方位角/高度角直接映射),windowLight.js `getSunScene` 重写:
  - **日昼进度** `dayProgress`:`(currentMin - sunriseMin) / (sunsetMin - sunriseMin)`,0=日出→1=日落,夜间 hold 在端点(日出前=0,日落后=1)
  - **旋转生命周期**:阴影框+光柱 `rotation = (dayProgress - 0.5) * 180` → 日出 -90°→ 正午 0°→ 日落 +90°,夜间 hold;光柱角度 = 阴影角度(同向)
  - **阴影强度**:夜间 1(最深)→ 正午 0.3(最浅),`1 - sin(π·dayProgress) × 0.7`
  - **灰阶防叠加**:bar 用不透明灰 `rgb(G,G,G)`,G=`(1-intensity)*255`,`mix-blend-mode: darken` → `min(backdrop, G)` 幂等,跨层重叠永不叠加(替代旧 layer opacity 方案——两层 opacity 复合会变深)
  - **亮斑图层**(z=32,multiply):夜黑(0.7)→凌晨黄→清晨白→日间透明→傍晚橙→日落红→夜黑,颜色 lerp 过渡
  - **反光层**(z=42,soft-light):内容组件被阳光照亮的轻微高光,`radial-gradient` 跟随光源位置,夜间 0,日间 `sin(π·dayProgress) × 0.22`
  - **夜间光柱不发光**:夜间 palette 全 transparent(bloom/core/mid/ambient),rayBaseOpacity=0
  - **时隙 5 分钟**:`SolarUtil.buildSlots` 96→288(15→5 分钟),`currentSlotIndex` `/5`,LightTest 循环 208ms/格

- **阴影分层重构(V5.3,已实施)**:阴影拆为上下两层,光柱穿过中间:
  - **下层** `.window-shadow-lower`(z=35):shadow-v + shadow-h + frame-h-top + frame-h-bottom
  - **光柱层** `.light-layer`(z=48):bloom + rays
  - **上层** `.window-shadow-upper`(z=49):frame-v-left + frame-v-right(最顶层,盖住光柱)
  - **竖直 bar 原点对齐**:三条 bar(shadow-v/frame-v-left/frame-v-right)原点全部对齐到 (页面 50% X, 页面 -10vh Y);`top:-50vh; height:337.5vh; transform-origin Y:40vh`(页面 Y = -50+40 = -10vh);X 对齐:shadow-v `50%`(中心)、frame-v-left `100%`(右边缘)、frame-v-right `0%`(左边缘)
  - **左右框延长 50%**:height 225%→337.5vh
  - **内竖框减 20%**:width 140px→112px(margin-left -70→-56)
  - **顶框**:底边在 y=-10vh,`top: calc(-10vh - 140px)`,不旋转

- **光照测试页增强(V5.3,已实施)**:`/light-test` 增加日期选择器(默认 2026-06-21 夏至,`@change` 触发 `/api/public/sun-info?date=YYYY-MM-DD`);加入首页内容组件(相册舞台+左侧动态/任务面板+右侧天气/纪念日面板)用于观察反光效果;信息面板含日期/时间/高度/方位/日出/日落/正午/月相+进度条+阶段标签(日出待命/日出/清晨/日间/傍晚/日落/日落待命)。

- **太阳信息 date 参数(V5.3,已实施)**:`SunService.getSunInfo(ip, LocalDate date)` 重载,`date` 为 null 取当日;`GET /api/public/sun-info?date=YYYY-MM-DD` 可选参数;缓存 key 含日期(`ihomy:sun:slots:{date}`),不同日期不冲突。

- **光照效果完善(V5.3,已实施)**:
  - **光源日出日落渐隐**:`lightOpacity = sin(π·dayProgress)`,日出=0→正午=1→日落=0,与阴影同步;`.light-layer` 整体 `opacity: lightOpacity`,bloom 辉光和光束统一渐隐;光源位置 `y: '-15%'`(页面外上方,用户看不到光源本体);光柱 `height: 200vh`(从页面外延伸到底部);移除 clip-path/mask(光源在页面外自然不可见)。
  - **0点跳变瞬间完成**:夜间 `dayProgress` 统一为 0(不再区分日出前=0/日落后=1)→ 日落后立刻重置到 -90°;夜间 `--bar-transition: 0s`(transition 瞬间)→ +90°→-90° 跳变无动画,不会扫光;日间 `--bar-transition: 3s ease`(平滑过渡)。
  - **阴影强度修复**:公式从 `1 - sin(π·p) × 0.7`(日出日落=1.0 完全黑)改为 `0.7 - sin(π·p) × 0.4`(日出日落=0.3,正午=0.3,夜间=0.7)。
  - **亮斑渐变修复**:傍晚→日落不透明度从 `t * 0.0`(=0)改为 `t * 0.7`(0→0.7 平滑过渡);日出日落起终点颜色统一为深蓝黑 `rgb(8,12,28)` + 70% 不透明度。
  - **图层层级调整**:内容组件(相册/左侧面板/右侧面板)从 z=30/40 降到 z=10/20,在阴影层(z=35)之下 → 阴影投射到内容上(更真实);album-base z=25→5,album-spine z=26→6。
  - **灰尘亮度适配**:`.dust-layer` `opacity: lightOpacity` → 夜间 0(不可见),日间正弦过渡(正午最亮),与光柱同步。
  - **光照测试页控件**:暂停/播放按钮(`togglePause` 清除/重建 timer)、前进/后退按钮(`prevSlot`/`nextSlot` ±1 时隙,mod 288);`.controls` 按钮组在信息面板底部。

- **光照系统 V5.4 重构(已实施)**:
  - **阴影顶框下移**:三条竖直 bar 的 `transform-origin` Y 从 `57vh` → `60vh`,`frame-h-top` 的 `top` 从 `7vh-140px` → `10vh-140px`(页面 Y = -50+60 = 10vh),顶框整体下移 3vh。
  - **顶框随太阳高度微移**:`windowLight.js` 加 `frameTopOffset = (alt - 45) × 0.045`(±2vh),`frame-h-top` 的 `top` 加 `var(--frame-top-offset)`,太阳越高顶框越低(窗口视觉变高)。
  - **跳变扫光修复(2 点重置方案)**:日落后 `dayProgress=1`(hold 在日落位置 +90°),凌晨 2 点 reset 到 `dayProgress=0`(-90°)。2 点无人看,跳变无感;夜间 `isNight=true` → `--bar-transition: 0s` → 瞬间跳变无扫光。回退了之前的全屏阴影遮罩方案。
  - **台灯 mask 祛除阴影**:`.window-shadow`/`.bright-spot`/`.vignette` 三层都加 `mask-image: var(--lamp-mask)`,台灯开启时 mask 中心透明挖洞祛除阴影,关闭时 `mask: none`(阴影完整)。mask 渐变中心跟随钟摆 X 坐标。
  - **台灯 3 态开关**:`lampMode` = auto(夜间自动)/on(常开)/off(关),按钮图标 🌑/💡/⬛ 循环切换。`lampStrength`:auto=`sunScene.lampOpacity`、on=1、off=0。
  - **台灯开关时机**:`lampOpacity = (isNight || dayProgress ≥ 0.9 || dayProgress ≤ 0.1) ? 1 : 0`——傍晚开始(dayProgress≥0.9)开灯,清晨结束(dayProgress>0.1)关灯,夜间常亮,日间正午关闭。突变非渐变。
  - **台灯位置**:左上黄金分割点 `(38.2%, 38.2%)`,光圈直径 `144vw`(半径 72vw,页面 4/5),z=100 最顶层,`filter: blur(20px)`。
  - **台灯色温可调**:`lampTemp` 0-100 滑块,warm `rgba(255,180,100)` → cool `rgba(220,230,255)`,`lampColor` computed 动态生成 radial-gradient。
  - **台灯亮度可调**:`lampBrightness` 0-100 滑块,控制 mask 透明区域比例:`lampMaskAlpha = 0.03 + 0.97 × (brightness/100)`,透明半径 `tr = alpha × 72vw`(亮度 0→2vw 几乎不祛除,亮度 100→72vw 大面积祛除)。mask 边缘始终 `black`(阴影完整),只有中心透明区域大小随亮度变化。不透明度固定 `lampStrength × 0.3`,不受亮度影响。
  - **台灯钟摆运动**:`requestAnimationFrame` 驱动,周期 8 秒。`lampPendulumX = sin(phase) × 5vw`(横向 ±5%,总 10% 页宽),`lampPendulumScaleX = 1 - |sin| × 0.2`(中间 1.0 圆形,两侧 0.8 椭圆)。台灯 div 的 `left` 和 mask 中心 X 都跟随 `lampPendulumX`。`watchEffect` 监听 `lampStrength > 0` → 开灯启动钟摆,关灯停止。
  - **导航栏重设计(浅色主题)**:温暖磨砂玻璃 `backdrop-filter: blur(24px) saturate(1.1)` + 暖奶油渐变背景;家庭名 hover 暖色下划线;模块导航胶囊容器;统一 36px 圆形触感按钮;用户头像胶囊;全色基于暖棕 `#3A2E22` + 暖琥珀 `#C9A876`/`#B8956A`。
  - **夜间深色背景**:`html.dark .home-page` / `.light-test-page` 背景从硬编码 `#EDE4D3` 改为深褐黑 `#1A1410→#2A2018`;`html.dark .blob` opacity 0.4→0.08 色块夜间几乎不可见。修复了夜间背景太亮的问题(原硬编码亮色底色 + multiply 压暗仍偏灰)。
  - **IP 定位默认济南**:`SunService.resolveLocation` IP 定位失败时默认坐标从北京 `39.9042,116.4074` 改为济南 `36.6512,117.1201`(时区 `Asia/Shanghai`)。
  - **LightTest 深浅模式按钮**:控制栏加 ☀️/🌙 按钮,`toggleDark` 调 `applyTheme({ dark: !dark })`,测试深色/浅色模式下的光照效果。
  - **LightTest 控制台优化**:宽度 260px→220px 变窄;按钮+滑块从一行改为两区(按钮区 flex-wrap + 滑块区垂直堆叠带标签);整体向上伸展。

- **首页相册模块重构 V5.4(已实施)**:
  - **删除**:中央 `album-stage` 轮播大图、`slides`/`slideIdx`/`currentSlide`/`slideTimer`/`buildSlides`/`parseImages`、`album-fade` 过渡、牛皮纸基底/书脊、5s 轮播定时器。
  - **新增**:右下角 `.album-corner`(fixed, 25vw × 25vw,right 5vw bottom 5vh)。
  - **散落拍立得堆**(近 7 天有新照片):`recentPhotos` 过滤 `createdAt < 7天` 最多 7 张;每张 `.polaroid` 220px 白边相纸(`padding: 10px 10px 38px`)、随机旋转 ±15°、随机偏移 dx±120 dy±60、投影;hover 时 z-index 99 + 旋转归零 + scale 1.08(抽出感);GSAP stagger 入场。
  - **闭合相册**(近 7 天无新照片):平躺木色封面 `linear-gradient(#8B6F47,#6B5435)` 280×210、家庭名称 + "家庭相册"、随机斜放、hover 抬正放大;点击跳 `/album`。
  - **点击拍立得 → `el-image-viewer`** 查看近期照片全屏大图浏览(`viewerUrls` = 近期照片 URL 列表)。
  - **后端零改动**:`/public/home` 已返回 `photos`(20 条,已按 visibility 过滤),前端筛 7 天即可。

- **UI 精简与可拖拽面板 V5.5(已实施)**:
  - **Top bar 去底色边框**:移除 `background`/`backdrop-filter`/`border-bottom`/`box-shadow`,纯透明导航栏。
  - **深色主题深蓝背景**:`main.css` 暗色变量从深褐 `#221A14` 改为深蓝 `#0F1A2E`/`#162238`/`#1A2540`;Home/LightTest 深色背景同步;`html.dark .blob` opacity 5%→40% + box-shadow 荧光 5%→40%。
  - **深色模式字体浅色**:`main.css` 加 `html.dark` 全局覆写,硬编码 `#3A2E22` 等深色字体统一改为 `#E8DCC8`;`--color-text-secondary` 加亮;毛玻璃面板深色模式 `background: rgba(30,40,65,0.55)` + `border-color: rgba(255,255,255,0.12)`;feed-bubble 深色模式 `rgba(255,255,255,0.08)`。
  - **只留浅色/深色主题**:`theme/index.js` 重写,`THEMES` 只剩 light/dark 两项;移除 preset 查找和 CSS 变量 `--color-primary`/`--color-accent` 设置;Home 顶栏主题下拉改为直接点击 ☀️/🌙 切换(`onTheme({ dark: !theme.dark })`)。
  - **可拖拽面板**:`utils/useDragResize.js` 组合式函数(拖动+调整大小,mousemove/mouseup 全局监听,最小 200×120px);4 个面板(feed/task/weather/anniversary)改为 `.draggable-panel`,各自独立 `useDragResize` 实例;顶部 `.drag-handle`(24px,中间 40×4px 拖动条 `handle-grip`),内容下移到 `.panel-body`;右下角 `.resize-handle`(20×20px 斜角渐变 `nwse-resize`)。
  - **album-corner 扩大 10%**:`25vw → 27.5vw`,`min-height: 280px → 308px`。
  - **album-closed 增大 0.5 倍**:`280×210 → 420×315`。

- **站点底部备案号(V5.6,已实施)**:`SiteFooter.vue` 左下角 fixed,工信部 ICP 备案号(链接 beian.miit.gov.cn)+ 公安备案占位"公安备案 待登记"(灰色无链接,拿到后改正式号 + 链接 beian.gov.cn)。样式与顶栏一致(磨砂玻璃 `backdrop-filter: blur(8px)` + 小字 11px + `var(--color-card)`),沉浸式页(首页)隐藏。`App.vue` 引入,所有非沉浸式页面显示。

- **首页 UI 优化(V5.6 续,已实施)**:
  - **顶栏齿轮 popover**:删除顶栏的光照测试链接/台灯开关/色温亮度滑块,替换为齿轮图标(`Setting` icon)popover。popover 内含:台灯 3 态按钮(自动/常开/关闭)、色温滑块、亮度滑块、重置面板布局按钮、光照测试页入口。
  - **时钟合并进顶栏**:删除独立 `clock-panel`,顶栏右侧(齿轮前)加 `.topbar-clock`(时间 18px + 日期 11px),移动端隐藏日期。
  - **天气面板缩减**:默认高度 180px(只显示城市+温度+图标+状况),点击 `.weather-main` 切换 `weatherExpanded`,展开到 440px 显示完整详情,带箭头旋转动画。
  - **今日面板**:新增可拖拽面板"今日"(左侧第 3 块,`todayDrag`):积分余额+连续天数+签到按钮(`pointsApi.stats/checkin`)+ 今日待办提醒前 3 条(`reminderApi.list`,过滤 `done!==1`)。登录可见,小屏隐藏。
  - **字体统一**:删 `.home-page` 的 `Georgia` serif 和 `.draggable-panel` 的 `PingFang` 显式声明,全页继承 body 的 sans-serif(`-apple-system, BlinkMacSystemFont, 'Segoe UI', 'Microsoft YaHei', sans-serif`)。
  - **台灯 mask JS 补间**:mask-image 不支持 CSS transition,改用 GSAP 2s 补间驱动 `lampAnim = reactive({ v })`。`watch(lampStrength)` 触发 `gsap.to(lampAnim, { v: nv, duration: 2, ease: 'power2.out' })`。`lampDivOpacity` 和 `lampMask` 全部由动画值 `lampStrengthAnim` 计算。mask 挖洞半径 `tr = maskAlpha * r * s` 随强度缩放:开灯洞从 0 放大,关灯洞缩到 0。移除 `.lamp-light` 的 CSS transition(避免双重驱动)。Home + LightTest 同步。
  - **主题 1s 过渡**:`.home-page`/`.light-test-page` 的 `linear-gradient` 背景不支持 CSS transition 插值,改为双层伪元素 `::before`(浅色渐变)/`::after`(深色渐变),`html.dark` 时 opacity 交叉淡入淡出 1s。`.bg-blobs` z-index 0→1 确保在伪元素之上。`.draggable-panel`/`.glass-panel`/`.blob` 加 `transition: background-color/border-color/color/opacity 1s ease`。`body, #app` 加全局过渡。
  - **灰尘 40→20**:`dustParticles` 从 40 减到 20(Home + LightTest)。
  - **resize 修复**:模板里 5 个 `.resize-handle` 都没绑定 `@mousedown="xxx.onResizeStart"`——已全部补上。
  - **拖拽边界 + anchorRight**:`useDragResize` 内置 clamp——左右不越出页面、顶部最低到导航栏下方(`minY=72`)、底部不越界;resize 时也限制不超出视口。位置/大小持久化到 localStorage(`ihomy:panel:{feed,task,weather,anniversary,today}`),`reset()` 清持久化恢复初始值。**`anchorRight` 参数**:right 定位面板(weather/anniversary)的 `pos.x` 为离右边缘距离(正数),拖动方向取反;旧持久化值(负数)通过 `Math.abs()` 自动迁移。
  - **深色色块亮度 10%**:`html.dark .blob { opacity: 0.1 }`(Home + LightTest)。
  - **深色铃铛颜色**:`html.dark .msg-icon { color: rgba(232,220,200,0.8) }`(main.css)。
  - **深色 `--color-primary` 覆写**:`main.css` 的 `html.dark` 加 `--color-primary: #E8DCC8`(全局修复,影响所有用 `var(--color-primary)` 的 ~20 处标题,含 Settings 页卡片标题)。
  - **删除 ambient 层**:V5.6 续初版加的 ambient-layer(黄金时刻暖染 multiply)已删除——效果太淡(0.05~0.12)且增加复杂度,bright-spot 层已覆盖前景染色。Home.vue + LightTest.vue 删除 ambient-layer(div/CSS/computed/开关按钮/watchEffect),齿轮 popover 去掉 ambient 开关行,UI设计提示词.md 同步删除第 2 节。
  - **手动切主题取消自动**:Home.vue 的 `onTheme` 设 `autoMode: false` + `ElMessage.info` 提示"已切换到手动主题,日出日落自动切换已暂停(可在设置中恢复)"。`applyAutoTheme` 检测 `autoMode=false` 自动跳过。解决:夜晚手动切浅色后,5 分钟光影切换时又被切回深色的问题。Settings 页"日出日落自动切换"开关可恢复。

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
| 注册/登录/找回 | V3.8 重做 | 邮箱=账号(uk_email),密码+确认密码+图形验证码(dev 固定 qwer);注册可以带邀请码入家庭或创建新家庭;注册成功不自动登录。**邀请码状态校验**:`AuthService.register`/`joinFamily` 校验 `status==INVITE_UNUSED`才放行(曾误写 `INVITE_USED` 导致所有邀请码返回 NOT_FOUND,已修复) |
| 家庭与成员 | V3.0/V3.6 | 多家庭(主家庭+当前家庭,Redis `user:curfamily:{id}`;`POST /auth/family/switch`)+ 邀请码(`?invite=`)/公开家庭搜索/入家申请/默认家庭;**`POST /family` 创建新家庭**(已登录用户,绑定 OWNER+切换当前家庭) |
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
| 背景音乐 | V3.5/V4.1 | `sys_family_info.music_url/music_title` |
| 照片瀑布 | V3.4/V4.1 | `/photo/cascade` 随机,可见性过滤 |
| 运维(OPS) | V3.8/V4.1 | 资源统计/日志检索,行列隔离 |
| 字典表/枚举单词化 | V3.9 | `sys_dict_item`+`DictConst`+`utils/dict.js` |
| 身份标签 | V3.9 | `family_user_label` 每家庭一套 |
| 国际化 i18n | V3.7/V4.0 | vue-i18n 中英 |
| 页面主题 | V4.0 | 明暗模式+主题色 |
| 移动端 PWA | V4.0 | PWA 安装 |
| 每日内容 | V4.1 | 每日一图+每日知识 |
| 混淆 ID | V3.7 | share_token + `?hid=`,新家庭默认私有 |
| 存储管理 | V4.1 | 家庭级设备+文件浏览器+一键同步(见文件存储策略小节);存量迁移待做 |
| 物品定位 | V5.0 | 1期已上线:五级粒度(家>房子>房间>家具>位置,支持多套房+多楼层),房子/房间/家具/物品 CRUD+跨级搜索;2期户型图/3期 AI 语义待做(见规划事项) |
| 文件硬删除 | V5.1 | 删照片/相册/视频→物理删 DB 行 + 删磁盘文件(`FileService.deleteByUrl`);视频从软删改硬删;自定义 XML DELETE 绕过全局 logic-delete;仅覆盖照片/相册/视频,博客封面/头像/音乐等未连带删 |
| 博客自定义分类 | V5.2 | `content_blog.category`,`GET /blog/categories` 返回 DISTINCT;前端水平分类筛选条 + 编辑器 allow-create |
| 天气代理 | V5.2/V5.6 | `WeatherService` 和风天气 **JWT(Ed25519)身份认证**(JDK 21 原生);凭证四件套(api-host/project-id/key-id/private-key);**凭证优先从 `sys_weather_credential` 表读 status=1(DB 空 fallback yml)**,Win 测试 status=1 / Linux 生产 status=0;**私钥不入 git**(schema.sql 种子 NULL + yml 留空,部署后手动 UPDATE);**月度配额 49999 次/月**(Redis 计数器 + DB fallback,超限停止调用);**每次调用记 `sys_weather_log`**(天气数据存 response,quota 不存);Redis 缓存 now 30m/forecast 30m/warning 5m/indices 30m/air 30m/minutely 10m/location 6h;`GET /public/weather` 顶栏简版,`GET /public/weather/detail` 首页聚合(7d+24h+预警+指数+空气+分钟降水);`GET /ops/weather/quota` OPS 用量统计;前端 TopBarExtras/Home 天气面板点击展开;LightTest 控制台 ☀️/☁️/🌧️/❄️ 天气切换(weatherMultiplier 衰减光强) |
| 太阳位置系统 | V5.2 | `SolarUtil`(NOAA 96 时隙)+ `SunService`(IP 定位 + Redis 6h/12h 缓存),`GET /public/sun-info`;时角归一化 + atan2 方位角修复 |
| 沉浸式首页 | V5.2 | Home `meta.immersive`,App.vue 隐藏全局组件;方案 B 展开的相册:牛皮纸托底+照片轮播+左右毛玻璃(动态/任务+天气/纪念日)+黑胶播放器+顶栏+背景色块+GSAP |
| 体积光系统 | V5.2 | 丁达尔效应:7 条光束 + 光源辉光 + 窗框内阴影(竖直+横向)+ 暗角 + 40 灰尘粒子;分层 z-index 0/2/30/35/44/46/48;windowLight.js 4 档调色板 + 月相夜间 |
| 光照测试页 | V5.2 | `/light-test` 1 分钟循环 96 时隙,信息面板(时间/高度/方位/日出/日落/月相+进度条) |
| nginx alias 修复 | V5.2 | `/files/` alias 末尾加 `/` + 图片扩展名 location 负向断言排除 `/files/`;FileService urlPrefix 去尾斜杠 |
| 光照生命周期 | V5.3 | 旋转/颜色/强度全由日出日落驱动;dayProgress 0=日出→1=日落;灰阶 darken 幂等防叠加;亮斑 multiply;反光 soft-light;夜间光柱 transparent;时隙 96→288(5 分钟) |
| 阴影分层 | V5.3 | 拆下层(z=35 内框+横框)+光柱(z=48)+上层(z=49 左右框);三条竖直 bar 原点对齐(页面 50%, -10vh);左右框延长 50%;内竖框减 20% |
| 光照测试页增强 | V5.3 | 日期选择器(夏至模拟)+首页内容组件(相册/面板)+阶段标签(日出待命→日落待命) |
| 太阳信息 date 参数 | V5.3 | `SunService.getSunInfo(ip, date)` 重载;`GET /public/sun-info?date=`;缓存 key 含日期 |
| 光照效果完善 | V5.3 | 光源渐隐(sin(π·p))+0点跳变瞬间(夜间 dayProgress=0,transition 0s)+阴影强度修复(0.7-sin×0.4)+亮斑渐变修复+层级调整(内容 z10/20<阴影 z35)+灰尘亮度适配+测试页控件(暂停/前进/后退) |
| 光照系统 V5.4 | V5.4 | 阴影顶框下移 3vh+顶框随太阳高度微移;2点重置跳变扫光;台灯 mask 祛除阴影(3 层 mask+钟摆跟随);3 态开关(auto/on/off)+傍晚开清晨关;黄金分割点位置+色温/亮度可调+钟摆运动(椭圆变形);导航栏温暖磨砂玻璃;夜间深色背景;IP 默认济南;LightTest 深浅模式按钮+控制台优化 |
| 首页相册重构 | V5.4 | 右下角拍立得堆(近 7 天照片,白边+随机旋转+hover 抽出)/闭合相册(无新照片,木色封面);点击拍立得→el-image-viewer 大图浏览;后端零改动 |
| UI 精简与可拖拽面板 | V5.5 | Top bar 去底色边框;深色主题深蓝背景+blobs 40%荧光;深色模式字体浅色;只留浅色/深色主题;可拖拽面板(useDragResize+drag-handle+resize-handle);album-corner 扩大 10%;album-closed 增大 0.5 倍 |
| 站点底部备案号 | V5.6 | `SiteFooter.vue` 左下角 ICP+公安占位;磨砂玻璃小字;沉浸式页隐藏 |
| 首页 UI 优化 | V5.6 续 | 顶栏齿轮 popover(色温/亮度/重置布局);时钟合并进顶栏;天气面板默认 180px 点击展开;今日面板(积分+提醒);字体统一 sans-serif;台灯 mask GSAP 2s 补间(opacity+mask 同步);主题双层伪元素背景 1s 过渡;灰尘 40→20;resize 修复;拖拽边界 clamp+持久化+anchorRight;深色色块 10%;深色 --color-primary 覆写;删除 ambient 层;手动切主题取消自动+提示 |
| 创建新家庭 | V5.6 续 | `POST /family`(FamilyController)已登录用户创建新家庭组,绑定 OWNER + 切换当前家庭;Settings 页"创建新家庭"卡片(`ElMessageBox.prompt` 输入名称→`familyApi.create`→reload) |

## 规划事项(未实现,排序按推荐优先级)

| 优先级 | 规划 | 版本来源 | 要点 |
|--------|------|---------|------|
| P1 | 存储管理-存量迁移 | V4.1 | 设备/文件浏览器/一键同步已完成;存量 `/uploads/` 旧文件重归档(移入 upload/yyyyMM 结构并更新 DB 路径)待做 |
| P2 | 物品定位-户型图 | V5.0 | 1期(物品清单+搜索)已完成;2期户型图:房间矩形绘制/物品相对坐标摆放,以 room.id 挂载(数据结构已预留) |
| P2 | 用户使用指导/帮助 | V3.5 | 新手引导弹窗+帮助页 |
| P2 | 家庭公告/广告位 | V3.5 | 自建家庭公告(不接第三方广告,隐私原因) |
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