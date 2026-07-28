# 家庭共用软件

面向家庭内部成员的内容共享平台，支持电脑浏览器、安卓、iOS 三端访问。核心功能：用户登录、博客、日志，首页模块化可扩展。

技术栈：**Vue3 + Vite + PWA**（前端） + **Spring Boot 3 + MyBatis-Plus + MySQL + Redis**（后端）。

## 目录结构

```
.
├── docs/
│   └── 需求规格说明书.docx        # 完整需求文档
├── backend/                       # Spring Boot 后端
│   ├── pom.xml
│   ├── mvnw / mvnw.cmd            # Maven Wrapper（无需单独装 Maven）
│   └── src/main/
│       ├── java/com/family/
│       │   ├── FamilyApplication.java
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

## 后端启动

1. 建库建表（含初始管理员账号）：
   ```bash
   mysql -uroot -p < backend/src/main/resources/schema.sql
   ```
2. 修改 `backend/src/main/resources/application.yml` 中的数据库与 Redis 连接信息。
3. 启动：
   ```powershell
   cd backend
   .\mvnw.cmd spring-boot:run
   ```
   后端默认端口 `8080`，接口前缀 `/api`。
4. 接口文档（Knife4j）：浏览器打开 `http://localhost:8080/api/doc.html`

默认管理员账号：`admin` / `admin123`（BCrypt，登录后请尽快修改密码）。

## 前端启动

```bash
cd frontend
npm install
npm run dev
```
默认端口 `5173`，已配置代理把 `/api` 转发到后端 `8080`。浏览器打开 `http://localhost:5173`。

生产构建：`npm run build`，产物在 `frontend/dist`，用 Nginx 托管即可（生产需配置 HTTPS，iOS 的 PWA 强制要求 HTTPS）。

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
