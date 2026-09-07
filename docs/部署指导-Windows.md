# ihomy — Windows 部署指导

> 适用项目：`ihomy`（Vue3 + Spring Boot 3 + MySQL + Redis）
> 部署形态：Windows Server 2019/2022 或 Windows 10/11 作为服务器
> 面向：生产/家庭长期运行
> **本文件为入库脱敏版**（2026-09-07 自本地维护版拆分）：所有凭证位置以 `<占位符>` 表示。项目维护者的真实凭证台账在本地 `Linux部署指导.md` §〇（不入 git）；其他部署者请自行生成强密码并妥善保管。

---

## 一、需要安装的软件清单

| # | 软件 | 版本要求 | 用途 | 是否必须 |
|---|------|----------|------|----------|
| 1 | JDK | 17 或 21（推荐 21 LTS） | 运行后端 Spring Boot | ✅ 必须 |
| 2 | Node.js | 18+（推荐 20 LTS，与仓库开发环境一致） | 构建前端 | ✅ 必须（仅构建时） |
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

### 2.2 安装 Node.js 20

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

### 2.3 安装 MySQL 8.4.10

> **端口约定**：本文统一使用 **6306**（与 Linux 生产一致）。方式 A 本机安装默认端口 3306——要么在 `my.ini` 把 `port` 改为 6306，要么把后文连接串/命令里的 6306 全部换回 3306，二选一保持一致。方式 B Docker 映射 `6306:3306` 无此问题。

**方式 A：安装到磁盘**
1. 下载 MySQL Installer：https://dev.mysql.com/downloads/installer/
2. 安装时选择 **Server only**，认证方式选 **Use Legacy Authentication Method**（兼容性更好）或推荐 **Strong Password Encryption**。
3. 设置 root 密码并牢记（ `<你的MySQL root密码>` ，仅初始化管理用，不入任何仓库文件）。字符集选 **utf8mb4**。
4. 安装完成后 MySQL 服务 `MySQL80` 自动启动。
5. 验证：
   ```powershell
   mysql -uroot -p
   ```
**方式 B：使用docker安装**
1. 拉取镜像
   ```powershell
   docker pull mysql:8.4.10
   ```
2. 创建容器
   ```powershell
   docker run -d --name ihomy_mysql --restart always -p 6306:3306 -v <你的MySQL配置目录>:/etc/mysql/conf.d -v <你的数据目录>:/var/lib/mysql -v <你的日志目录>:/var/log/mysql -e MYSQL_ROOT_PASSWORD=<你的MySQLroot密码> -e TZ=Asia/Shanghai mysql:8.4.10
   ```

### 2.4 安装 Redis

Windows 官方不提供 Redis，任选一种方式：

**方式 A：Memurai（推荐，Windows 原生 Redis 兼容服务）**
1. 下载：https://www.memurai.com/get-memurai
2. 安装后作为服务 `Memurai` 自动运行，监听 6379。
3. 验证：`memurai-cli ping` → `PONG`

**方式 B：Docker 运行 Redis（需 Docker Desktop）**
```powershell
# 拉取 Redis 镜像
docker pull redis

# 启动 Redis 容器（端口 6379，与 Linux 生产环境一致）
docker run -d --name ihomy_redis --restart unless-stopped -p 6379:6379 -v <你的Redis配置目录>:/etc/redis/redis.conf -v <你的数据目录>:/data redis redis-server /etc/redis/redis.conf
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

### 3.2 配置后端（外挂配置 external.yml）

> **配置机制（与 Linux 一致：生产基线 + 外挂覆盖）**：源码 `application.yml` 是生产基线（DB 密码/JWT 密钥**留空**），必须由外挂配置 external.yml 提供——缺失时后端启动即失败（JwtUtils fail-closed，防误用默认密钥）。不要把密码写进 application.yml。

```powershell
# 1) 从模板复制外挂配置到项目 config\ 目录
mkdir C:\app\ihomy\config -Force
copy C:\app\ihomy\backend\src\main\resources\external.yml.template C:\app\ihomy\config\external.yml
notepad C:\app\ihomy\config\external.yml
```

external.yml 关键项（键与 application.yml 同名覆盖，完整清单见模板注释）：
```yaml
spring:
  datasource:
    password: <你的数据库密码>        # ihomy 应用账号（schema.sql 已自动创建，仅 DML 权限），不要用 root 跑业务
  data:
    redis:
      host: localhost
      port: 6379
      # password: <你的Redis密码>    # 如有
jwt:
  secret: <你的JWT密钥,至少32字符随机串>
file:
  upload-dir: C:/app/ihomy/uploads   # Windows 部署必须覆盖基线的 Linux 路径,用正斜杠
logging:
  file:
    path: C:/app/ihomy/logs          # 三类日志 access/server/thirdparty 的根目录
```

```powershell
# 2) 设置环境变量 IHOMY_CONFIG_PATH 指向该文件（控制台测试用；服务化走 6.1 的 NSSM AppEnvironmentExtra）
setx IHOMY_CONFIG_PATH "C:\app\ihomy\config\external.yml"
# setx 对已开的终端不生效,重开 PowerShell 后验证:
echo $env:IHOMY_CONFIG_PATH
```

> `ExternalConfigLoader` 在启动早期以最高优先级加载该文件并覆盖基线同键，Windows/Linux 机制完全一致。

### 3.3 建库建表

```powershell
# 方案 A（推荐）：把 schema.sql 复制到 MySQL 容器内执行，完全绕过 PowerShell 编码问题
docker cp C:\app\ihomy\backend\src\main\resources\schema.sql ihomy_mysql:/tmp/schema.sql
docker exec ihomy_mysql bash -c "mysql -uroot -p<root密码> --default-character-set=utf8mb4 < /tmp/schema.sql"
docker exec ihomy_mysql rm /tmp/schema.sql

# 方案 B：PowerShell 管道（必须加 -Encoding UTF8，否则中文 COMMENT 变 ??? 导致 ERROR 1064）
Get-Content C:\app\ihomy\backend\src\main\resources\schema.sql -Raw -Encoding UTF8 | mysql -uroot -p -P6306 --default-character-set=utf8mb4
```

> **编码坑（重要）**：schema.sql 含中文 COMMENT（如 `'BCrypt密码'`）和初始数据（如 `'我的家庭'`）。PowerShell 5.1 默认按 GBK 读取文件，不加 `-Encoding UTF8` 会导致中文写入数据库时变成 `?` 字符（不可逆），首页全部显示问号。**推荐用方案 A**（docker cp + 容器内执行），完全绕过 PowerShell 管道编码问题。

该脚本由 root 执行一次，会创建 `ihomy` 库、**61 张表**（`sys_` 系统与账号权限 / `family_` 家庭事务 / `content_` 内容数据三前缀，含 RBAC 权限模型）、应用专用账号 `ihomy`（仅 DML 权限）、5 个预设角色（OWNER/MEMBER/CHILD/GUEST/OPS）+ 31 个权限点 + 角色权限映射、默认首页模块、管理员账号 `admin`（自动绑定 OWNER 角色）与运维账号 `ops`（初始密码为开发安全版，见下方警告）。

> **⚠️ 生产必须改密（schema.sql 为开发安全版）**：入库版 schema.sql 内置的是本机 Docker 开发固定凭证（ihomy 账号开发密码 + admin/ops 开发专用 BCrypt 哈希，明文只在维护者本地文档）。生产建库后立即执行（MySQL 8 若启用 `validate_password` MEDIUM，密码须含特殊字符/数字/大小写各≥1）：
> ```sql
> ALTER USER 'ihomy'@'localhost' IDENTIFIED BY '<生产强密码>';
> ALTER USER 'ihomy'@'%'        IDENTIFIED BY '<生产强密码>';
> FLUSH PRIVILEGES;
> ```
> external.yml 的 `spring.datasource.password` 同步填同一新密码；admin/ops 首次登录后立即在页面改密。

> **⚠️ schema.sql 种子缺失**：`sys_home_module` 种子缺 `chat`（聊天室）行，全新部署后首页会缺聊天室入口。建库后补一行（注意列名是 `position`/`sort_order`）：
> ```sql
> INSERT INTO sys_home_module (code, title, icon, path, category, position, sort_order, enabled)
> VALUES ('chat', '聊天室', 'icon-chat', '/chat', 'life', 'left', 16, 1);
> ```

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
# 终端1：后端（setx 设置的环境变量对已开终端不生效，当场指定一次最稳）
$env:IHOMY_CONFIG_PATH = "C:\app\ihomy\config\external.yml"
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

    # 响应头声明字符集,确保浏览器按 UTF-8 解析
    charset utf-8;
    charset_types text/plain text/css text/javascript application/javascript application/json application/xml image/svg+xml;

    # 前端静态资源
    root  C:/app/ihomy/frontend/dist;
    index index.html;

    # 视频大文件上传（放映厅 500MB）
    client_max_body_size 500m;

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

    # WebSocket（聊天室）
    location /api/ws {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_read_timeout 3600s;
    }

    # 上传文件静态目录（注意 alias 末尾必须带斜杠）
    location /files/ {
        alias C:/app/ihomy/uploads/;
    }

    # 静态资源缓存（排除 /files/ 避免覆盖 alias）
    location ~* ^/(?!files/).+\.(js|css|png|jpg|jpeg|gif|ico|svg|woff2)$ {
        expires 7d;
        add_header Cache-Control "public, immutable";
    }
}
```

> Windows 路径用正斜杠 `/`，且不要加盘符前的反斜杠。
> `location /files/` 的 `alias` 末尾必须带 `/`，否则 404；图片扩展名缓存 location 用负向断言 `^/(?!files/)` 排除 `/files/`，避免 `root` 覆盖 `alias`（与 Linux 版第五节一致）。

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

    # 响应头声明字符集,确保浏览器按 UTF-8 解析
    charset utf-8;
    charset_types text/plain text/css text/javascript application/javascript application/json application/xml image/svg+xml;

    ssl_certificate      C:/win-acme/Certificates/你的域名-chain.pem;
    ssl_certificate_key  C:/win-acme/Certificates/你的域名.key;
    ssl_protocols TLSv1.2 TLSv1.3;

    root C:/app/ihomy/frontend/dist;
    client_max_body_size 500m;

    location / { try_files $uri $uri/ /index.html; }
    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
    # WebSocket 与上传文件目录同第四节(/api/ws 反代 + /files/ alias),此处不重复展开
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
nssm set IhomyBackend AppParameters "-Xms256m -Xmx384m -XX:+UseSerialGC -jar C:\app\ihomy\backend\target\ihomy-backend.jar"
nssm set IhomyBackend AppDirectory "C:\app\ihomy\backend"
# 关键:服务进程不继承用户环境变量,IHOMY_CONFIG_PATH 必须显式传给服务
nssm set IhomyBackend AppEnvironmentExtra "IHOMY_CONFIG_PATH=C:\app\ihomy\config\external.yml"
nssm set IhomyBackend AppStdout "C:\app\ihomy\backend\logs\out.log"
nssm set IhomyBackend AppStderr "C:\app\ihomy\backend\logs\err.log"
nssm set IhomyBackend Start SERVICE_AUTO_START
nssm start IhomyBackend
```

> `-Xms256m -Xmx384m` 为内存受限场景的 JVM 调优参数（与 Linux 版 systemd unit 一致），内存充裕可去掉。

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

> 项目自带的 `scripts\deploy.ps1` 一键流水线面向 **Linux 服务器**（ssh/scp 远程部署），Windows 服务器请手动执行以下步骤。

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

> 数据库结构变更：手动执行 `mysql -uroot -p -P6306 --default-character-set=utf8mb4 ihomy < 增量.sql`（Linux 侧的 deploy.ps1 会自动执行 migrations.sql，Windows 需手动）。

---

## 八、验证清单

| 检查项 | 命令/方式 | 预期 |
|--------|-----------|------|
| 后端服务运行 | `nssm status IhomyBackend` | SERVICE_RUNNING |
| 后端接口 | 浏览器 `http://localhost:8080/api/auth/me` | 返回 401 JSON |
| 前端访问 | 浏览器 `https://你的域名` | 登录页 |
| 登录 | admin + 密码（初始密码为开发安全版，生产部署后已按 3.3 改密则用新密码） | 进入首页 |
| 数据库 | `mysql -uihomy -p -P6306 ihomy -e "show tables;"` | 61 张表 |
| Redis | `memurai-cli ping` | PONG |
| 上传文件 | 浏览器访问 `/files/pictures/...` 图片 URL | 200 OK |
| WebSocket | 登录后进入聊天室 | 实时收发消息 |
| PWA 安装 | Chrome 地址栏右侧安装图标 | 可安装到桌面 |

---

## 九、常见问题

**Q1：`mvnw.cmd` 首次运行卡住下载 Maven？**
配阿里云 Maven 镜像（见项目 README "加速建议"），或手动装 Maven 后直接用 `mvn`。

**Q2：后端启动报数据库连接失败 / jwt.secret 缺失？**
依次检查：`IHOMY_CONFIG_PATH` 是否已设置且指向存在的 external.yml（服务化走 NSSM `AppEnvironmentExtra`）、external.yml 里 `spring.datasource.password`/`jwt.secret` 是否已填、MySQL 服务是否运行、`localhost:6306` 是否被占用。JwtUtils 对空 secret 启动即失败是 fail-closed 设计。

**Q3：前端访问白屏 / 刷新 404？**
nginx 缺少 `try_files $uri $uri/ /index.html;` 单页回退配置。

**Q4：iOS 无法"添加到主屏幕"？**
必须 HTTPS + 有效证书，且用 Safari 打开。自签证书不行。

**Q5：上传图片失败 / 上传后访问 404？**
检查 external.yml 的 `file.upload-dir` 目录是否存在且有写权限；nginx `client_max_body_size` 是否够大；`location /files/` 的 `alias` 是否末尾带 `/` 且指向同一目录。

**Q6：端口被占用？**
- 8080：改 `application.yml` 的 `server.port`
- 80/443：检查 IIS 是否占用，停止 IIS 或 `iisreset /stop`

**Q7：聊天室连不上？**
检查 nginx 是否配置了 `/api/ws` 的 WebSocket 反代（`proxy_http_version 1.1` + `Upgrade` 头），见第四节。

---

## 附：目录规划建议

```
C:\app\ihomy\                 # 项目代码
C:\app\ihomy\uploads\         # 上传文件（external.yml 的 file.upload-dir 指向）
C:\app\ihomy\logs\            # 三类日志 access/server/thirdparty（external.yml 的 logging.file.path 指向）
C:\app\ihomy\config\external.yml   # 外挂配置（DB 密码/JWT 密钥等，不入 git）
C:\nginx\                     # Nginx
C:\nssm\                      # 服务工具
C:\win-acme\                  # 证书工具
```

---

## 附：数据备份

> ihomy 删除照片/相册/视频时为**硬删除**(物理删 DB 记录 + 删除磁盘文件,无回收站),误删不可恢复。建议定期备份:

- **数据库**:`mysqldump -uroot -p ihomy > D:\backup\ihomy\ihomy-%date:~0,4%%date:~5,2%%date:~8,2%.sql`,任务计划程序每日执行,保留 14 天。
- **上传文件**:`C:\app\ihomy\uploads` 目录(含 pictures/videos/files/music 分类子目录),用 robocopy 或手动复制到备份盘,与数据库同周期。
- **恢复**:先恢复数据库,再恢复 uploads 目录;DB 里的文件 URL(`/files/...`)与物理路径解耦,目录还原后即可访问。
