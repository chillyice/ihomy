# ihomy

面向家庭内部成员的内容共享平台，支持电脑浏览器、安卓、iOS 三端访问。核心功能：用户登录、博客、日志，首页模块化可扩展。

技术栈：**Vue3 + Vite + PWA**（前端） + **Spring Boot 3 + MyBatis-Plus + MySQL + Redis**（后端）。

## 目录结构

```
.
├── docs/
│   └── 需求规格说明书.docx        # 完整需求文档
├── start.bat                      # Windows 一键启动（双击即可）
├── scripts/
│   ├── start-all.ps1              # Windows 一键启动前后端
│   └── start-db.ps1               # Docker 一键拉起 MySQL/Redis
├── backend/                       # Spring Boot 后端
│   ├── pom.xml
│   ├── mvnw / mvnw.cmd            # Maven Wrapper（无需单独装 Maven）
│   └── src/main/
│       ├── java/com/ihomy/
│       │   ├── IhomyApplication.java
│       │   ├── common/            # 统一响应、异常处理
│       │   ├── config/            # Security/CORS/MyBatisPlus/Knife4j
│       │   ├── security/          # JWT 工具、过滤器、登录上下文
│       │   ├── entity/            # 实体：Family/SysUser/Blog/Diary/HomeModule
│       │   ├── mapper/            # MyBatis-Plus Mapper
│       │   ├── service/           # 业务接口与实现
│       │   ├── controller/        # REST 控制器
│       │   └── dto/               # 请求 DTO
│       └── resources/
│           ├── application.yml    # 配置（数据库/Redis/JWT）
│           └── schema.sql         # 建表脚本 + 初始数据
└── frontend/                      # Vue3 + Vite 前端
    ├── package.json
    ├── vite.config.js             # 含 PWA + 代理 /api
    ├── index.html
    └── src/
        ├── main.js
        ├── api/                   # axios 封装 + 接口定义
        ├── stores/                # Pinia 用户状态
        ├── router/                # 路由 + 登录守卫
        ├── styles/main.css        # 全局样式 + 响应式
        └── views/                 # Login/Home/blog/diary/Member
```

## 环境要求

| 依赖 | 版本 |
|------|------|
| JDK | 17 或 21 |
| Node.js | 18+ |
| MySQL | 8.0+ |
| Redis | 6+ |
| （可选）Docker Desktop | 用于一键拉起 MySQL/Redis |

## Windows 一键启动（推荐）

项目自带 Windows 启动脚本，自动检查环境、启动前后端并打开浏览器。

### 方式一：双击启动（最简单）

双击项目根目录的 **`start.bat`**，脚本会：
1. 检查 JDK / Node / npm 环境
2. 新开窗口启动后端（`mvnw spring-boot:run`，端口 8080）
3. 新开窗口启动前端（`npm run dev`，端口 5173）
4. 自动打开浏览器 `http://localhost:5173`

### 方式二：PowerShell 命令

```powershell
# 在项目根目录执行
.\start.bat

# 仅启动后端
.\start.bat -BackendOnly

# 仅启动前端
.\start.bat -FrontendOnly

# 不自动打开浏览器
.\start.bat -NoBrowser

# 生产模式（先 build 再启动 jar / vite preview）
.\start.bat -Build
```

脚本实际调用 `scripts\start-all.ps1`，可单独查看：
```powershell
powershell -ExecutionPolicy Bypass -File scripts\start-all.ps1
```

### 用 Docker 一键拉起数据库（可选）

如果不想本地安装 MySQL/Redis，可用 Docker：
```powershell
powershell -ExecutionPolicy Bypass -File scripts\start-db.ps1
```
该脚本会启动 `ihomy-mysql`（6306，容器 root 密码 root，自动导入 `schema.sql`，脚本内会自动创建应用账号 `ihomy`）与 `ihomy-redis`（6379）两个容器。
停止：`docker stop ihomy-mysql ihomy-redis`；删除：`docker rm -f ihomy-mysql ihomy-redis`。

### Windows 环境变量提示

- 若提示 `JAVA_HOME 未设置`，脚本会自动从 `java` 路径推断并临时设置；为稳定起见建议手动配置：
  ```powershell
  [Environment]::SetEnvironmentVariable('JAVA_HOME', 'C:\Program Files\Java\jdk-21', 'User')
  ```
  设置后**重开** PowerShell 窗口生效。
- 若 `npm`/`node` 命令找不到，请安装 Node.js 18+（https://nodejs.org），安装时勾选 "Add to PATH"。
- 若 `mvnw.cmd` 首次运行联网下载 Maven 较慢，可配置国内镜像（见下方"加速建议"）。

## 通用启动步骤（跨平台）

### 1. 准备数据库与 Redis

Windows 下任选其一：

**A. Docker（推荐，省心）**
```powershell
powershell -ExecutionPolicy Bypass -File scripts\start-db.ps1
```

**B. 本地已安装 MySQL/Redis**

建库建表（含初始管理员账号、应用专用账号 ihomy），在项目根目录用 root 执行一次：
```powershell
Get-Content backend\src\main\resources\schema.sql -Raw | mysql -uroot -p
```
> 该脚本需 root 权限执行（用于建库、创建 ihomy 账号并授权）。业务运行时应用使用 `ihomy` 账号连接，不用 root。
或导入：用 Navicat / DBeaver 打开 `backend/src/main/resources/schema.sql` 执行。

确保 Redis 服务已启动（Windows 可用 `redis-server.exe` 或 Memurai）。

### 2. 配置后端连接信息

修改 `backend/src/main/resources/application.yml`：
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:6306/ihomy?...
    username: ihomy                # 使用专用应用账号，不要用 root
    password: Ihomy@2026           # 改成你在 schema.sql 中设置的密码
  data:
    redis:
      host: localhost
      port: 6379
```

### 3. 启动后端

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```
后端默认端口 `8080`，接口前缀 `/api`。接口文档（Knife4j）：浏览器打开 `http://localhost:8080/api/doc.html`

默认管理员账号：`admin` / `admin123`（BCrypt，登录后请尽快修改密码）。

### 4. 启动前端

```powershell
cd frontend
npm install      # 首次需要
npm run dev
```
默认端口 `5173`，已配置代理把 `/api` 转发到后端 `8080`。浏览器打开 `http://localhost:5173`。

生产构建：`npm run build`，产物在 `frontend/dist`，用 Nginx 托管即可（生产需配置 HTTPS，iOS 的 PWA 强制要求 HTTPS）。

## 加速建议（国内网络）

**Maven 加速**：在 `C:\Users\<你>\.m2\settings.xml` 添加阿里云镜像：
```xml
<settings>
  <mirrors>
    <mirror>
      <id>aliyun</id>
      <mirrorOf>central</mirrorOf>
      <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
  </mirrors>
</settings>
```

**npm 加速**：
```powershell
npm config set registry https://registry.npmmirror.com
```

## 多端访问（PWA）

- PC / 安卓 / iOS 浏览器打开站点后，使用浏览器“添加到主屏幕”即可像原生 App 一样使用。
- iOS 需用 Safari 操作，且服务必须 HTTPS。
- 后期若要上架应用商店，可用 Capacitor 套壳现有前端，无需重写。

## 可扩展性：新增首页模块

首页采用模块化设计，新增功能无需改动首页代码：

1. 后端开发新功能的接口与表（如“家庭相册”）。
2. 前端开发对应页面与路由（如 `/album`）。
3. 后端插入一条 `home_module` 记录，或调用 `POST /api/home/modules`：
   ```json
   { "code": "album", "title": "家庭相册", "icon": "icon-album",
     "path": "/album", "position": "right", "sortOrder": 2, "enabled": 1 }
   ```
4. 首页刷新后自动出现新入口卡片。

## 主要接口

| 模块 | 方法 | 路径 |
|------|------|------|
| 认证 | POST | /api/auth/login |
| 认证 | POST | /api/auth/register |
| 认证 | POST | /api/auth/logout |
| 首页 | GET | /api/home/modules |
| 首页 | PUT | /api/home/modules |
| 博客 | GET/POST/PUT/DELETE | /api/blog |
| 日志 | GET/POST/PUT/DELETE | /api/diary |
| 上传 | POST | /api/file/upload |

统一响应结构：`{ "code": 0, "message": "success", "data": ... }`，登录后请求头携带 `Authorization: Bearer <token>`。
