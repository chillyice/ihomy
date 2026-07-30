# ihomy — Windows 部署指导

> 适用项目：`ihomy`（Vue3 + Spring Boot 3 + MySQL + Redis）
> 部署形态：Windows Server 2019/2022 或 Windows 10/11 作为服务器
> 面向：生产/家庭长期运行

---

## 一、需要安装的软件清单

| # | 软件 | 版本要求 | 用途 | 是否必须 |
|---|------|----------|------|----------|
| 1 | JDK | 17 或 21（推荐 21 LTS） | 运行后端 Spring Boot | ✅ 必须 |
| 2 | Node.js | 18+（推荐 20 LTS） | 构建前端 | ✅ 必须（仅构建时） |
| 3 | MySQL | 8.0+ | 主数据库 | ✅ 必须 |
| 4 | Redis | 6+ | 缓存 / JWT 令牌 | ✅ 必须 |
| 5 | Nginx | 1.24+（Windows 版） | 托管前端 + 反向代理 + HTTPS | ✅ 生产推荐 |
| 6 | NSSM | 任意 | 把 jar 注册为 Windows 服务（开机自启） | ⭐ 推荐 |
| 7 | win-acme | 最新 | 申请/自动续期 Let's Encrypt 证书 | 🔒 HTTPS 时需要 |
| 8 | 7-Zip | 任意 | 解压软件包 | 可选 |

> 说明：Maven **无需单独安装**，项目自带 `mvnw.cmd`；Git 按需安装。

---

## 二、软件安装

### 2.1 安装 JDK 21

1. 下载 Temurin（OpenJDK）Windows 安装包：
   https://adoptium.net/temurin/releases/?version=21&os=windows&arch=x64
2. 运行 `.msi`，勾选 **"Set JAVA_HOME variable"** 和 **"Add to PATH"**。
3. 验证（**重开** PowerShell）：
   ```powershell
   java -version
   echo $env:JAVA_HOME
   ```

### 2.2 安装 Node.js 24

1. 下载 LTS：https://nodejs.org/zh-cn/download
2. 安装时勾选 "Add to PATH"。
3. 验证：
   ```powershell
   node -v
   npm -v
   ```
4. （可选）配置国内镜像加速：
   ```powershell
   npm config set registry https://registry.npmmirror.com
   ```

### 2.3 安装 MySQL 8.0

**方式 A：安装到磁盘**
1. 下载 MySQL Installer：https://dev.mysql.com/downloads/installer/
2. 安装时选择 **Server only**，认证方式选 **Use Legacy Authentication Method**（兼容性更好）或推荐 **Strong Password Encryption**。
3. 设置 root 密码并牢记。字符集选 **utf8mb4**。
4. 安装完成后 MySQL 服务 `MySQL80` 自动启动。
5. 验证：
   ```powershell
   mysql -uroot -p
   ```
**方式 B：使用docker安装**
1. 拉取镜像
   ```powershell
   docker pull mysql:8.0.44
   ```
2. 创建容器
   ```powershell
   docker run -d --name ihomy_mysql --restart always -p 3306:3306 -v C:\Users\chill\OneDrive\WorkStation\config\MySQL\conf:/etc/mysql/conf.d -v D:\WorkSpace\MySQL\data:/var/lib/mysql -v D:\WorkSpace\MySQL\logs:/var/log/mysql -e MYSQL_ROOT_PASSWORD=bW_fF65a -e TZ=Asia/Shanghai mysql:8.0.44 
   ```

### 2.4 安装 Redis

Windows 官方不提供 Redis，任选一种方式：

**方式 A：Memurai（推荐，Windows 原生 Redis 兼容服务）**
1. 下载：https://www.memurai.com/get-memurai
2. 安装后作为服务 `Memurai` 自动运行，监听 6379。
3. 验证：`memurai-cli ping` → `PONG`

**方式 B：Docker 运行 Redis（需 Docker Desktop）**
```powershell
docker run -d --name ihomy_redis --restart unless-stopped -p 6879:6879 -v C:\Users\chill\OneDrive\WorkStation\config\Redis\redis.conf:/etc/redis/redis.conf -v D:\WorkSpace\Redis\data:/data redis:latest redis-server /etc/redis/redis.conf
```

**方式 C：社区维护的 Windows Redis**
- 下载 `tporadowski/redis` releases（GitHub），解压后运行 `redis-server.exe`。

### 2.5 安装 Nginx（Windows 版）

1. 下载：https://nginx.org/en/download.html → 选 mainline 版 `zip`。
2. 解压到 `C:\nginx`。
3. 启动：
   ```powershell
   cd C:\nginx
   .\nginx.exe
   ```
4. 浏览器打开 `http://localhost` 看到 Welcome 页即成功。
5. 停止：`.\nginx.exe -s stop`；重载配置：`.\nginx.exe -s reload`。

### 2.6 安装 NSSM（服务化工具）

1. 下载：https://nssm.cc/download
2. 解压 `nssm.exe` 到 `C:\nssm\`（或任意目录并加入 PATH）。

---

## 三、项目部署

假设项目代码位于 `C:\app\ihomy`（路径可自行替换）。

### 3.1 获取代码

```powershell
# 若是 git 仓库
git clone <仓库地址> C:\app\ihomy
# 或直接把项目文件夹拷贝到 C:\app\ihomy
```

### 3.2 配置后端

编辑 `C:\app\ihomy\backend\src\main\resources\application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ihomy?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    # 使用专用应用账号 ihomy（schema.sql 已自动创建，仅 DML 权限），不要用 root 跑业务
    username: ihomy
    password: Ihomy@2026          # ← 改成你在 schema.sql 中设置的密码
  data:
    redis:
      host: localhost
      port: 6379
      # password: 你的Redis密码      # ← 如有密码

file:
  upload-dir: C:/app/ihomy/uploads    # ← 改成绝对路径，避免相对路径问题
  url-prefix: /files
```

> 应用使用专用账号 `ihomy` 连接数据库（仅 SELECT/INSERT/UPDATE/DELETE 权限，最小权限原则），不要用 root 跑业务。root 仅用于执行 schema.sql 初始化。

### 3.3 建库建表

```powershell
Get-Content C:\app\ihomy\backend\src\main\resources\schema.sql -Raw | mysql -uroot -p
```
该脚本由 root 执行一次，会创建 `ihomy` 库、6 张表（含系统操作日志表）、应用专用账号 `ihomy`（仅 DML 权限）、默认首页模块、管理员账号 `admin/admin123`。

### 3.4 构建后端

```powershell
cd C:\app\ihomy\backend
.\mvnw.cmd -B clean package -DskipTests
```
产物：`C:\app\ihomy\backend\target\ihomy-backend.jar`

> （可选）Maven 加速：在 `C:\Users\<你>\.m2\settings.xml` 配阿里云镜像（见项目 README）。

### 3.5 构建前端

```powershell
cd C:\app\ihomy\frontend
npm install
npm run build
```
产物：`C:\app\ihomy\frontend\dist`

### 3.6 测试运行（先跑通再服务化）

```powershell
# 终端1：后端
java -jar C:\app\ihomy\backend\target\ihomy-backend.jar

# 终端2：验证
curl http://localhost:8080/api/auth/me   # 返回 401 即正常（未带 token）
```

---

## 四、Nginx 配置（托管前端 + 反代后端）

编辑 `C:\nginx\conf\nginx.conf`，在 `http` 块内添加：

```nginx
server {
    listen       80;
    server_name  你的域名或IP;

    # 前端静态资源
    root  C:/app/ihomy/frontend/dist;
    index index.html;

    # 前端单页应用路由回退
    location / {
        try_files $uri $uri/ /index.html;
    }

    # 反向代理后端 API
    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # 上传文件大小
    client_max_body_size 20m;

    # 静态资源缓存
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff2)$ {
        expires 7d;
    }
}
```

> Windows 路径用正斜杠 `/`，且不要加盘符前的反斜杠。

重载：
```powershell
cd C:\nginx
.\nginx.exe -s reload
```
浏览器打开 `http://你的域名或IP` 即可访问。

---

## 五、HTTPS 证书（iOS PWA 必须）

iOS Safari 的 PWA "添加到主屏幕"要求 **HTTPS**。

### 5.1 使用 win-acme 申请免费证书

1. 下载：https://www.win-acme.com/
2. 解压到 `C:\win-acme\`，运行 `wacs.exe`。
3. 选择 `N: Create certificate (default settings)` → 选择你的 IIS/nginx 站点 → 验证方式选 HTTP-89。
4. win-acme 会自动下载证书并写入 `C:\win-acme\Certificates\`。
5. 在 nginx 配置 443：

```nginx
server {
    listen 443 ssl;
    http2 on;
    server_name 你的域名;

    ssl_certificate      C:/win-acme/Certificates/你的域名-chain.pem;
    ssl_certificate_key  C:/win-acme/Certificates/你的域名.key;
    ssl_protocols TLSv1.2 TLSv1.3;

    root C:/app/ihomy/frontend/dist;
    location / { try_files $uri $uri/ /index.html; }
    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
    client_max_body_size 20m;
}

# 80 跳转 443
server {
    listen 80;
    server_name 你的域名;
    return 301 https://$host$request_uri;
}
```

6. win-acme 默认会设置定时任务自动续期。

### 5.2 内网/无域名（自签证书）

PWA 在 iOS 上**不信任自签证书**，建议用域名 + Let's Encrypt。若仅内网测试可用自签，但 PWA 安装功能会受限。

---

## 六、服务化（开机自启）

### 6.1 后端注册为 Windows 服务（NSSM）

```powershell
nssm install IhomyBackend "C:\Program Files\Java\jdk-21\bin\java.exe"
nssm set IhomyBackend AppParameters "-jar C:\app\ihomy\backend\target\ihomy-backend.jar"
nssm set IhomyBackend AppDirectory "C:\app\ihomy\backend"
nssm set IhomyBackend AppStdout "C:\app\ihomy\backend\logs\out.log"
nssm set IhomyBackend AppStderr "C:\app\ihomy\backend\logs\err.log"
nssm set IhomyBackend Start SERVICE_AUTO_START
nssm start IhomyBackend
```

管理命令：
```powershell
nssm stop IhomyBackend      # 停止
nssm restart IhomyBackend   # 重启
nssm remove IhomyBackend    # 卸载服务
```

### 6.2 Nginx 开机自启

NSSM 同样可注册：
```powershell
nssm install IhomyNginx "C:\nginx\nginx.exe"
nssm set IhomyNginx AppDirectory "C:\nginx"
nssm start IhomyNginx
```

### 6.3 MySQL / Redis

- MySQL 安装时已注册为服务 `MySQL80`，自动启动。
- Memurai 安装时已注册为服务 `Memurai`，自动启动。
- 若用 Docker Redis，容器已加 `--restart unless-stopped`。

---

## 七、更新部署流程

代码更新后，重新构建并重启服务：

```powershell
# 后端
cd C:\app\ihomy\backend
git pull
.\mvnw.cmd -B clean package -DskipTests
nssm restart IhomyBackend

# 前端
cd C:\app\ihomy\frontend
git pull
npm install
npm run build
# 前端是静态文件，nginx 直接托管，无需重启（若缓存严重可 nginx -s reload）
```

---

## 八、验证清单

| 检查项 | 命令/方式 | 预期 |
|--------|-----------|------|
| 后端服务运行 | `nssm status IhomyBackend` | SERVICE_RUNNING |
| 后端接口 | 浏览器 `http://localhost:8080/api/auth/me` | 返回 401 JSON |
| 前端访问 | 浏览器 `https://你的域名` | 登录页 |
| 登录 | admin / admin123 | 进入首页 |
| 数据库 | `mysql -uihomy -p ihomy -e "show tables;"` | 6 张表 |
| Redis | `memurai-cli ping` | PONG |
| PWA 安装 | Chrome 地址栏右侧安装图标 | 可安装到桌面 |

---

## 九、常见问题

**Q1：`mvnw.cmd` 首次运行卡住下载 Maven？**
配阿里云 Maven 镜像（见项目 README "加速建议"），或手动装 Maven 后直接用 `mvn`。

**Q2：后端启动报数据库连接失败？**
检查 `application.yml` 的 `password`、MySQL 服务是否运行、`localhost:3306` 是否被占用。

**Q3：前端访问白屏 / 刷新 404？**
nginx 缺少 `try_files $uri $uri/ /index.html;` 单页回退配置。

**Q4：iOS 无法"添加到主屏幕"？**
必须 HTTPS + 有效证书，且用 Safari 打开。自签证书不行。

**Q5：上传图片失败？**
检查 `file.upload-dir` 目录是否存在且有写权限；nginx `client_max_body_size` 是否够大。

**Q6：端口被占用？**
- 8080：改 `application.yml` 的 `server.port`
- 80/443：检查 IIS 是否占用，停止 IIS 或 `iisreset /stop`

---

## 附：目录规划建议

```
C:\app\ihomy\                 # 项目代码
C:\app\ihomy\uploads\         # 上传文件（application.yml 指向）
C:\app\ihomy\backend\logs\    # 服务日志
C:\nginx\                     # Nginx
C:\nssm\                      # 服务工具
C:\win-acme\                  # 证书工具
```
