# ihomy — Linux 部署指导

> 适用项目：`ihomy`（Vue3 + Spring Boot 3 + MySQL + Redis）
> 部署形态：Linux 服务器（Ubuntu 22.04/24.04 为主，附 CentOS/RHEL 差异）
> 面向：生产/家庭长期运行

---

## 一、需要安装的软件清单

| # | 软件 | 版本要求 | 用途 | 是否必须 |
|---|------|----------|------|----------|
| 1 | OpenJDK | 17 或 21（推荐 21 LTS） | 运行后端 | ✅ 必须 |
| 2 | Node.js | 18+（推荐 20 LTS） | 构建前端 | ✅ 必须（仅构建时） |
| 3 | MySQL | 8.0+ | 主数据库 | ✅ 必须 |
| 4 | Redis | 6+ | 缓存 / JWT 令牌 | ✅ 必须 |
| 5 | Nginx | 1.18+ | 托管前端 + 反向代理 + HTTPS | ✅ 生产推荐 |
| 6 | Certbot | 最新 | 申请/续期 Let's Encrypt 证书 | 🔒 HTTPS 时需要 |
| 7 | systemd | 系统自带 | 服务管理与开机自启 | ✅ 自带 |
| 8 | ufw / firewalld | 系统自带 | 防火墙 | ✅ 自带 |

> 说明：Maven **无需单独安装**，项目自带 `mvnw`；Git 按需安装。

---

## 二、软件安装

> 以下命令以 **Ubuntu 22.04/24.04** 为例，CentOS/RHEL 命令在每步下方单独给出。

### 2.1 系统准备

**Ubuntu：**
```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y curl wget git unzip vim
```
**CentOS/RHEL：**
```bash
sudo dnf install -y curl wget git unzip vim tar
```

### 2.2 安装 OpenJDK 21

**Ubuntu：**
```bash
sudo apt install -y openjdk-21-jdk
```
**CentOS/RHEL：**
```bash
sudo dnf install -y java-21-openjdk java-21-openjdk-devel
```
验证：
```bash
java -version
# openjdk version "21.x"
```

### 2.3 安装 Node.js 20（NodeSource 源）

```bash
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt install -y nodejs
```
**CentOS/RHEL：**
```bash
curl -fsSL https://rpm.nodesource.com/setup_20.x | sudo bash -
sudo dnf install -y nodejs
```
验证：
```bash
node -v && npm -v
```
加速（可选）：
```bash
npm config set registry https://registry.npmmirror.com
```

### 2.4 安装 MySQL 8.0

**Ubuntu：**
```bash
sudo apt install -y mysql-server
sudo systemctl enable --now mysql
sudo mysql_secure_installation   # 设置 root 密码、清理测试库
```
**CentOS/RHEL：**
```bash
sudo dnf install -y mysql-server
sudo systemctl enable --now mysqld
sudo mysql_secure_installation
```
验证：
```bash
sudo mysql -uroot -p
```

### 2.5 安装 Redis

**Ubuntu：**
```bash
sudo apt install -y redis-server
sudo systemctl enable --now redis-server
```
**CentOS/RHEL：**
```bash
sudo dnf install -y redis
sudo systemctl enable --now redis
```
验证：
```bash
redis-cli ping
# PONG
```

### 2.6 安装 Nginx

**Ubuntu：**
```bash
sudo apt install -y nginx
sudo systemctl enable --now nginx
```
**CentOS/RHEL：**
```bash
sudo dnf install -y nginx
sudo systemctl enable --now nginx
```

### 2.7 安装 Certbot（申请 HTTPS 证书）

**Ubuntu：**
```bash
sudo apt install -y certbot python3-certbot-nginx
```
**CentOS/RHEL：**
```bash
sudo dnf install -y certbot python3-certbot-nginx
```

---

## 三、项目部署

假设部署用户为 `deploy`，项目位于 `/opt/ihomy`。

### 3.1 创建部署用户与目录

```bash
sudo useradd -m -s /bin/bash deploy
sudo mkdir -p /opt/ihomy
sudo chown deploy:deploy /opt/ihomy
```

### 3.2 获取代码

```bash
sudo -u deploy git clone <仓库地址> /opt/ihomy
# 或上传代码包解压到 /opt/ihomy
```

### 3.3 配置后端

编辑 `/opt/ihomy/backend/src/main/resources/application.yml`：

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
  upload-dir: /opt/ihomy/uploads    # ← 绝对路径
  url-prefix: /files
```

> 应用使用专用账号 `ihomy` 连接数据库（仅 SELECT/INSERT/UPDATE/DELETE 权限，最小权限原则），不要用 root 跑业务。该账号由 schema.sql 自动创建并授权，无需手动建号。

### 3.4 建库建表

```bash
mysql -uroot -p < /opt/ihomy/backend/src/main/resources/schema.sql
```
该脚本由 root 执行一次，会创建 `ihomy` 库、6 张表（含系统操作日志表）、应用专用账号 `ihomy`（仅 DML 权限）、默认首页模块、管理员 `admin/admin123`。

### 3.5 构建后端

```bash
sudo -u deploy bash -c '
cd /opt/ihomy/backend
chmod +x mvnw
./mvnw -B clean package -DskipTests
'
```
产物：`/opt/ihomy/backend/target/ihomy-backend.jar`

> Maven 加速（可选）：编辑 `~/.m2/settings.xml` 配阿里云镜像（见项目 README）。

### 3.6 构建前端

```bash
sudo -u deploy bash -c '
cd /opt/ihomy/frontend
npm install
npm run build
'
```
产物：`/opt/ihomy/frontend/dist`

### 3.7 创建上传目录并授权

```bash
sudo mkdir -p /opt/ihomy/uploads /var/log/ihomy
sudo chown -R deploy:deploy /opt/ihomy/uploads /var/log/ihomy
```

---

## 四、systemd 服务（后端开机自启）

创建服务文件：

```bash
sudo tee /etc/systemd/system/ihomy-backend.service > /dev/null <<'EOF'
[Unit]
Description=Ihomy Family App Backend (Spring Boot)
After=network.target mysql.service redis-server.service

[Service]
Type=simple
User=deploy
WorkingDirectory=/opt/ihomy/backend
ExecStart=/usr/bin/java -Xms256m -Xmx512m -jar /opt/ihomy/backend/target/ihomy-backend.jar
SuccessExitStatus=143
Restart=on-failure
RestartSec=10
StandardOutput=append:/var/log/ihomy/backend.log
StandardError=append:/var/log/ihomy/backend.err.log

[Install]
WantedBy=multi-user.target
EOF
```

> CentOS 下 `After=` 的服务名是 `mysqld.service`、`redis.service`，按实际调整。

启用并启动：
```bash
sudo systemctl daemon-reload
sudo systemctl enable ihomy-backend
sudo systemctl start ihomy-backend
sudo systemctl status ihomy-backend
```

管理命令：
```bash
sudo systemctl restart ihomy-backend   # 重启
sudo systemctl stop ihomy-backend      # 停止
journalctl -u ihomy-backend -f         # 查看实时日志
tail -f /var/log/ihomy/backend.log     # 查看文件日志
```

---

## 五、Nginx 配置（托管前端 + 反代后端）

创建站点配置：

```bash
sudo tee /etc/nginx/conf.d/ihomy.conf > /dev/null <<'EOF'
server {
    listen 80;
    server_name 你的域名或IP;

    root  /opt/ihomy/frontend/dist;
    index index.html;

    client_max_body_size 20m;

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
        proxy_read_timeout 60s;
    }

    # 静态资源缓存
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff2)$ {
        expires 7d;
        add_header Cache-Control "public, immutable";
    }
}
EOF
```

测试并重载：
```bash
sudo nginx -t
sudo systemctl reload nginx
```
浏览器打开 `http://你的域名或IP` 即可访问。

---

## 六、HTTPS 证书（iOS PWA 必须）

iOS Safari 的 PWA "添加到主屏幕"要求 **HTTPS**。使用 Let's Encrypt 免费证书。

### 6.1 申请证书（需有域名，且 80 端口可公网访问）

```bash
sudo certbot --nginx -d 你的域名 -m 你的邮箱 --agree-tos --no-eff-email
```
Certbot 会自动：
- 申请证书
- 修改 nginx 配置（加 443 ssl、80 跳转 443）
- 设置 systemd timer 自动续期

### 6.2 手动续期测试

```bash
sudo certbot renew --dry-run
```

### 6.3 证书路径（手动配 nginx 时用）

- 证书：`/etc/letsencrypt/live/你的域名/fullchain.pem`
- 私钥：`/etc/letsencrypt/live/你的域名/privkey.pem`

---

## 七、防火墙配置

**Ubuntu (ufw)：**
```bash
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
sudo ufw status
```

**CentOS/RHEL (firewalld)：**
```bash
sudo firewall-cmd --permanent --add-service=ssh
sudo firewall-cmd --permanent --add-service=http
sudo firewall-cmd --permanent --add-service=https
sudo firewall-cmd --reload
```

> MySQL(3306)、Redis(6379) **不要**开放公网，仅本机访问。

---

## 八、更新部署流程

代码更新后，重新构建并重启服务：

```bash
sudo -u deploy bash -c '
cd /opt/ihomy
git pull

# 后端
cd /opt/ihomy/backend
./mvnw -B clean package -DskipTests

# 前端
cd /opt/ihomy/frontend
npm install
npm run build
'

sudo systemctl restart ihomy-backend
sudo systemctl reload nginx
```

---

## 九、验证清单

| 检查项 | 命令/方式 | 预期 |
|--------|-----------|------|
| 后端服务 | `sudo systemctl status ihomy-backend` | active (running) |
| 后端接口 | `curl http://localhost:8080/api/auth/me` | 返回 401 JSON |
| 前端访问 | 浏览器 `https://你的域名` | 登录页 |
| 登录 | admin / admin123 | 进入首页 |
| 数据库 | `mysql -uihomy -p ihomy -e "show tables;"` | 6 张表 |
| Redis | `redis-cli ping` | PONG |
| Nginx | `sudo nginx -t` | syntax ok |
| 证书 | `sudo certbot certificates` | 有效 |
| PWA 安装 | Chrome/Safari 地址栏安装图标 | 可安装到桌面 |
| 开机自启 | `sudo systemctl is-enabled ihomy-backend nginx` | enabled |

---

## 十、常见问题

**Q1：`./mvnw` 没有执行权限？**
```bash
chmod +x /opt/ihomy/backend/mvnw
```

**Q2：后端启动失败，日志 `Permission denied`？**
检查 `/opt/ihomy/uploads`、`/var/log/ihomy` 的属主是否为 `deploy`。

**Q3：MySQL 报 `Access denied for user 'root'@'localhost'`？**
Ubuntu 的 MySQL root 默认用 `auth_socket` 插件，需 `sudo mysql` 登录执行 schema.sql。应用本身用 `ihomy` 账号连接（不用 root），若 `ihomy` 账号连不上，确认已用 root 执行过 schema.sql（其中会创建并授权 `ihomy` 账号）。

**Q4：前端刷新 404？**
nginx 缺少 `try_files $uri $uri/ /index.html;`。

**Q5：iOS 无法"添加到主屏幕"？**
必须 HTTPS + 有效证书，Safari 打开。自签证书不被信任，PWA 无法安装。

**Q6：上传图片 413 Request Entity Too Large？**
nginx `client_max_body_size` 太小，调大到 `20m` 后 `reload`。

**Q7：端口被占用？**
```bash
sudo ss -tlnp | grep -E '8080|80|443'
```
改 `application.yml` 的 `server.port` 或停掉冲突服务。

**Q8：磁盘/内存不足？**
后端 JVM 至少 256M 内存；可用 `-Xmx512m` 控制。可用 `free -h`、`df -h` 查看。

---

## 附：目录规划

```
/opt/ihomy/                    # 项目代码（属主 deploy）
├── backend/
│   ├── target/ihomy-backend.jar
│   └── src/main/resources/application.yml
├── frontend/dist/             # 前端构建产物（nginx root）
└── uploads/                   # 上传文件
/var/log/ihomy/                # 服务日志
/etc/nginx/conf.d/ihomy.conf   # nginx 站点配置
/etc/systemd/system/ihomy-backend.service
/etc/letsencrypt/live/域名/    # HTTPS 证书
```

---

## 附：可选 — Docker Compose 一键部署

若不想逐个安装软件，可用 Docker Compose（需安装 Docker Engine + Docker Compose 插件）：

在项目根目录创建 `docker-compose.yml`：

```yaml
services:
  mysql:
    image: mysql:8.0
    container_name: ihomy-mysql
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: ihomy
    # 注：schema.sql 挂载到初始化目录，容器首次启动会自动执行，
    # 其中包含创建应用账号 ihomy 并授权的语句。容器 root 仅用于管理。
    volumes:
      - ./backend/src/main/resources/schema.sql:/docker-entrypoint-initdb.d/schema.sql
      - mysql-data:/var/lib/mysql
    ports:
      - "3306:3306"
    restart: unless-stopped

  redis:
    image: redis:7-alpine
    container_name: ihomy-redis
    ports:
      - "6379:6379"
    restart: unless-stopped

  backend:
    image: eclipse-temurin:21-jre
    container_name: ihomy-backend
    working_dir: /app
    volumes:
      - ./backend/target/ihomy-backend.jar:/app/app.jar
      - ./uploads:/app/uploads
    command: java -jar app.jar
    ports:
      - "8080:8080"
    depends_on: [mysql, redis]
    restart: unless-stopped

  nginx:
    image: nginx:stable
    container_name: ihomy-nginx
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./frontend/dist:/usr/share/nginx/html
      - ./nginx.conf:/etc/nginx/conf.d/default.conf
    depends_on: [backend]
    restart: unless-stopped

volumes:
  mysql-data:
```

启动：
```bash
cd /opt/ihomy/backend && ./mvnw -B clean package -DskipTests
cd /opt/ihomy/frontend && npm install && npm run build
cd /opt/ihomy && docker compose up -d
```
> 容器化部署时，`application.yml` 的数据库地址需改为服务名 `mysql`（host=`mysql`）、Redis 地址改为 `redis`。应用连接账号仍用 `ihomy / Ihomy@2026`（schema.sql 在容器初始化时已自动创建该账号并授权，对 `ihomy` 库有 DML 权限）。
