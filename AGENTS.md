# AGENTS.md — ihomy 项目规则

> 本文件供 opencode 跨会话加载,记录项目关键决策与约定。新会话启动时会自动读取,无需重复说明背景。
> 修改本文件后立即对所有新会话生效。

## 项目概述

- **应用名**:ihomy(家庭共用软件)。面向家庭内部成员的内容共享平台,支持 PC 浏览器 / 安卓 / iOS 三端。
- **核心功能**:用户登录、博客、生活日志,首页模块化可扩展(后期新增功能只需插入一条 `home_module` 记录)。
- **技术栈**:前端 Vue3 + Vite + PWA;后端 Spring Boot 3 + MyBatis-Plus + MySQL 8 + Redis;对象存储可选 OSS/MinIO。

## 两个工作目录(重要)

项目存在两份,改动需保持同步:

| 目录 | 用途 |
|------|------|
| `C:\Users\chill\Documents\Default Project` | 工作目录(代码编辑 + 构建验证在此进行) |
| `C:\Users\chill\OneDrive\WorkStation\Projects\ihomy` | OneDrive 副本(含部署文档,实际项目位置) |

- 默认在 **Default Project** 目录编辑代码、跑编译/构建验证。
- 改动源码或文档后,需同步到 OneDrive 副本(尤其 schema.sql / application.yml / README / 部署指导)。
- 两份部署指导文档 `Windows部署指导.md`、`Linux部署指导.md` 只存在于 OneDrive 目录。

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
- **6 张表**:`family` / `sys_user` / `blog` / `diary` / `home_module` / `sys_operation_log`。
- `sys_operation_log`(系统操作日志表):**当前只建表,未绑定 Java 层**。表结构后续会优化重构,设计思路未定。在确定方案前,不要主动给它加实体/Mapper/AOP 切面 —— 除非用户明确要求。
- 默认管理员:`admin / admin123`(BCrypt),登录后应改密。

## 代码结构

```
backend/  (Spring Boot 3, JDK 17/21, 包 com.ihomy)
  src/main/java/com/ihomy/
    IhomyApplication.java        # 主类(@MapperScan("com.ihomy.mapper"))
    common/        # Result 统一响应、ResultCode、BizException、GlobalExceptionHandler
    config/        # SecurityConfig, CorsConfig, MybatisPlusConfig, Knife4jConfig, WebMvcConfig
    security/      # JwtUtils, JwtAuthenticationFilter, LoginUser, SecurityHelper, UserDetailsServiceImpl
    entity/        # Family, SysUser, Blog, Diary, HomeModule
    mapper/        # MyBatis-Plus BaseMapper
    service/       # 接口 + impl
    controller/    # AuthController, HomeController, BlogController, DiaryController, FileController
    dto/           # LoginDTO, RegisterDTO, BlogDTO, DiaryDTO, HomeModuleDTO
  src/main/resources/
    application.yml    # 端口8080, context-path=/api, 连接用 ihomy 账号
    schema.sql         # 建库+建号+建表+初始数据
  mvnw / mvnw.cmd      # Maven Wrapper,无需单独装 Maven

frontend/  (Vue3 + Vite + PWA)
  src/
    main.js, App.vue
    api/           # request.js(axios+JWT拦截+自动刷新), index.js(各模块API)
    stores/user.js # Pinia 用户状态
    router/        # 登录守卫
    styles/main.css
    views/         # Login, Home(动态模块化), blog/(List/Detail/Edit), diary/DiaryList, Member
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

## 文档清单(OneDrive 目录)

- `README.md` — 启动说明 + Windows 一键启动脚本用法
- `Windows部署指导.md` / `Linux部署指导.md` — 生产部署(NSSM/systemd、Nginx 反代、Let's Encrypt、Docker Compose)
- `docs/需求规格说明书.docx` — 完整需求文档(9 章节,Word 版)
- `scripts/start-all.ps1` — Windows 一键启动前后端(双击 `start.bat` 调用)
- `scripts/start-db.ps1` — Docker 拉起 MySQL+Redis(自动导入 schema.sql,自动建 ihomy 账号)
- `config/mysql/my.cnf` — MySQL 调优配置(端口 6306 + 内存调优,2GB 内存方案)

## 部署约定(Linux)

- **求稳方案(2GB 内存)**:MySQL 本机部署 + Redis 用 Docker。MySQL 调优后 ~180MB,本机部署无需 Docker daemon 为它常驻;Redis 轻量,Docker 化便于升级。
- **每应用一用户,权利分散**:后端 Spring Boot 以 `ihomy` 应用用户运行(systemd `User=ihomy`);MySQL 用 apt 自动创建的 `mysql` 用户;Redis 容器隔离;Nginx 用 `www-data`。
- **除关键步骤外不用 root**:装包、建用户、systemd 管理、`/etc/` 配置、防火墙、certbot、执行 schema.sql(数据库 root)需 root;代码获取/构建/编辑 application.yml 由 `ihomy` 用户操作。
- **JVM 调优**:systemd ExecStart 用 `-Xmx384m -XX:MaxMetaspaceSize=192m -XX:+UseSerialGC -Xss512k`。
- **MySQL 调优**:`config/mysql/my.cnf` → `cp` 到 `/etc/mysql/conf.d/ihomy.cnf`,关键项 `performance_schema=OFF`(省 80-100MB)。
- **端口**:MySQL 用 6306(非默认 3306),application.yml 与 my.cnf 一致。

## 关键约束

- **不要用 root 连业务库**;改 application.yml 时 username 保持 `ihomy`。
- **不要主动给 `sys_operation_log` 加 Java 层**,除非用户明确要求(表结构待重构)。
- 改完代码,**两份目录都要同步**(尤其 schema.sql / application.yml / README / 部署文档)。
- 新增功能走首页模块化:开发页面+路由 → 插 `home_module` 记录 → 首页自动出现入口,**不要改首页代码**。
- iOS PWA 必须 HTTPS + 有效证书(自签不行)。
- 不要给代码加注释,除非用户要求。
- 不要主动 commit,除非用户明确要求。

## 环境检查(参考)

本机已装:JDK 21、Node 20、MySQL 8、python-docx。Maven 用 Wrapper 或临时目录的 3.9.9。Redis 本机可能未装(可用 Docker 或 Memurai)。
