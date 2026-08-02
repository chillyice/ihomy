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
| 3 | MySQL | 8.0+ | 主数据库 | ✅ 必须（本机部署） |
| 4 | Redis | 6+ | 缓存 / JWT 令牌 | ✅ 必须（Docker 部署） |
| 5 | Docker Engine | 24+ | 运行 Redis 容器 | ✅ 必须（仅 Redis 用） |
| 6 | Nginx | 1.18+ | 托管前端 + 反向代理 + HTTPS | ✅ 生产推荐 |
| 7 | Certbot | 最新 | 申请/续期 Let's Encrypt 证书 | 🔒 HTTPS 时需要 |
| 8 | systemd | 系统自带 | 服务管理与开机自启 | ✅ 自带 |
| 9 | ufw / firewalld | 系统自带 | 防火墙 | ✅ 自带 |

> 说明：Maven **无需单独安装**，项目自带 `mvnw`；Git 按需安装。

> **部署策略（2GB 内存求稳方案）**：**MySQL 本机部署 + Redis 用 Docker**。
> 理由：MySQL 调优后约 180MB，本机部署无需 Docker daemon 常驻开销（50-100MB），配置直接生效、排障直接；Redis 仅 50MB 且轻量，Docker 化管理省心、升级方便。详见第十一节"资源优化"。

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

### 2.4 安装 MySQL 8.0（本机部署）

> 求稳方案：MySQL 本机部署，不走 Docker。调优配置直接放 `/etc/mysql/conf.d/`，无需挂载。

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

**应用内存调优配置（2GB 内存必做）：**
```bash
# 将项目提供的 my.cnf 放入配置目录（端口 6306 + 内存调优）
sudo cp /opt/ihomy/config/mysql/my.cnf /etc/mysql/conf.d/ihomy.cnf
sudo systemctl restart mysql     # CentOS: mysqld
```
> `my.cnf` 关键项：`port=6306`、`performance_schema=OFF`（省 80-100MB）、`max_connections=30`、`innodb_buffer_pool_size=128M`。详见第十一节。

### 2.5 安装 Docker Engine（用于运行 Redis 容器）

> 求稳方案仅需 Docker 跑 Redis，MySQL/Nginx/后端均为本机服务。Docker 安排在 Redis 之前，便于后续拉取 Redis 镜像。
> 以下使用阿里云镜像源安装 Docker CE 29.7.0（避免官方源在国内访问慢/找不到版本）。

**Ubuntu：**
```bash
# 更新包管理工具
sudo apt-get update
sudo apt-get -y install apt-transport-https ca-certificates curl software-properties-common

# 添加 Docker 软件包源（使用 keyrings 方式管理 GPG 密钥，走阿里云镜像）
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL http://mirrors.cloud.aliyuncs.com/docker-ce/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

ARCH=$(dpkg --print-architecture)
DISTRO=$(. /etc/os-release && echo "$VERSION_CODENAME")
sudo tee /etc/apt/sources.list.d/docker.list > /dev/null <<EOF
deb [arch=${ARCH} signed-by=/etc/apt/keyrings/docker.gpg] http://mirrors.cloud.aliyuncs.com/docker-ce/linux/ubuntu ${DISTRO} stable
EOF
sudo apt-get update

# 查询 docker 部署版本（阿里云源安装时找不到正确版本，用此方式确认 29.7.0 存在）
apt-cache show package docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin | grep 29.7.0

# 安装 Docker 社区版本、容器运行时 containerd.io，以及 Docker 构建和 Compose 插件（固定 29.7.0）
sudo apt-get -y install docker-ce-rootless-extras=5:29.7.0-1~ubuntu.26.04~resolute
sudo apt-get -y install docker-ce=5:29.7.0-1~ubuntu.26.04~resolute docker-ce-cli=5:29.7.0-1~ubuntu.26.04~resolute containerd.io docker-buildx-plugin docker-compose-plugin

# 启动并设置开机自启
sudo systemctl enable --now docker
# 让 ihomy 用户免 sudo 使用 docker（需重新登录生效；ihomy 用户在 3.1 节创建，若尚未创建可稍后再执行）
sudo usermod -aG docker ihomy
```

> **关于版本号**：`5:29.7.0-1~ubuntu.26.04~resolute` 是 Ubuntu 26.04 (Resolute) 的版本字符串。若你的 Ubuntu 版本不同（如 22.04 Jammy / 24.04 Noble），需替换对应代号，或先执行上面的 `apt-cache show` 查出的版本字符串。
> **CentOS/RHEL**：阿里云也提供 Docker 的 yum 源，参考 `https://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo`，命令类似。

验证：
```bash
docker --version
# Docker version 29.7.0
docker ps
# 空列表（无运行容器）
```

### 2.6 安装 Redis（Docker 部署）

> 求稳方案：Redis 用 Docker 运行，轻量且便于升级。Docker 已在 2.5 节安装。

**拉取 Redis 镜像：**
```bash
docker pull redis
```

**启动 Redis 容器：**
```bash
docker run -d --name ihomy-redis \
  -p 6379:6379 \
  --restart unless-stopped \
  redis
```
> 默认拉取 `redis:latest`。如需固定版本，用 `docker pull redis:7-alpine` 并在 `docker run` 时用 `redis:7-alpine`。

验证：
```bash
docker exec ihomy-redis redis-cli ping
# PONG
```
管理：
```bash
docker stop ihomy-redis      # 停止
docker start ihomy-redis     # 启动
docker restart ihomy-redis   # 重启
docker logs ihomy-redis      # 查看日志
docker pull redis && docker rm -f ihomy-redis && docker run -d --name ihomy-redis -p 6379:6379 --restart unless-stopped redis   # 升级
```

> 若不想装 Docker，Redis 也可本机安装：`sudo apt install -y redis-server`。但求稳方案推荐 Docker 化 Redis。

### 2.7 安装 Nginx

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

### 2.8 安装 Certbot（申请 HTTPS 证书）

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

### 权限分层模型（每应用一用户，最小权限）

| 应用 | 运行用户 | 来源 | 说明 |
|------|----------|------|------|
| 后端 Spring Boot | `ihomy` | 手动创建 | 应用服务账号，拥有 `/opt/ihomy` 代码、构建与运行 jar |
| MySQL | `mysql` | apt/dnf 安装自动创建 | 系统服务，无需手动建号 |
| Redis | 容器内 `redis` | Docker 镜像内置 | 主机无 redis 用户，容器隔离 |
| Nginx | `www-data`(Ubuntu) / `nginx`(CentOS) | apt/dnf 安装自动创建 | 系统服务 |

> **原则**：除下列关键步骤外，均不使用 root。
> **必须 root 的关键步骤**：安装软件包、创建用户、systemd 服务管理、写 `/etc/` 下配置、防火墙、certbot、执行 schema.sql（数据库 root）。
> **应用操作（代码/构建/配置 application.yml）**：由 `ihomy` 用户执行，不用 root。

### 3.1 创建应用用户与目录（root 操作一次）

```bash
# 创建应用服务账号 ihomy
sudo useradd -m -s /bin/bash ihomy
# 创建项目目录并归属 ihomy
sudo mkdir -p /opt/ihomy /var/log/ihomy
sudo chown -R ihomy:ihomy /opt/ihomy /var/log/ihomy
# 将 ihomy 加入 docker 组，使其免 sudo 运行 Redis 容器（需重新登录生效）
sudo usermod -aG docker ihomy
```

### 3.2 获取代码（ihomy 用户）

> **关于 /opt 权限**：`/opt` 默认属 root，但 3.1 节已 `mkdir -p /opt/ihomy` 并 `chown -R ihomy:ihomy /opt/ihomy`，所以 ihomy 用户对 `/opt/ihomy` 有完全读写权限，克隆无障碍。**注意路径是 `/opt/ihomy` 而非 `/opt`**，不要在 `/opt` 根目录直接 clone。

```bash
# 切换到应用用户
sudo su - ihomy

# 1) 生成 SSH key（用于 git@github.com 克隆，免输密码）
ssh-keygen -t ed25519 -C "chillyice@live.com"
# 一路回车即可（默认路径 ~/.ssh/id_ed25519，可设 passphrase 也可空）

# 2) 查看公钥，复制输出内容
cat ~/.ssh/id_ed25519.pub

# 3) 把公钥添加到 GitHub
#    浏览器登录 GitHub → Settings → SSH and GPG keys → New SSH key
#    Title 自定义，Key 粘贴上一步输出，保存

# 4) 测试 SSH 连接 GitHub（首次会提示是否信任，输入 yes）
ssh -T git@github.com
# 期望：Hi <你的用户名>! You've successfully authenticated...

# 5) 克隆代码（用 SSH 地址，不是 https）
git clone git@github.com:<你的用户名>/ihomy.git /opt/ihomy
```

> **若用 HTTPS 克隆**：无需 SSH key，但推送时可能要输 token。命令：`git clone https://github.com/<你的用户名>/ihomy.git /opt/ihomy`。
> **若代码包上传**：上传解压到 `/opt/ihomy` 后，root 执行 `sudo chown -R ihomy:ihomy /opt/ihomy` 修正属主。

### 3.3 配置后端（ihomy 用户，编辑项目内文件无需 root）

以 `ihomy` 身份编辑 `/opt/ihomy/backend/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:6306/ihomy?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
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

### 3.4 建库建表（root 操作，仅此一次）

```bash
# 退出 ihomy 用户回到有 sudo 权限的账号
exit
# 用数据库 root 执行 schema.sql（建库、建表、创建应用账号 ihomy 并授权）
mysql -uroot -p < /opt/ihomy/backend/src/main/resources/schema.sql
```
该脚本由数据库 root 执行一次，会创建 `ihomy` 库、6 张表（含系统操作日志表）、应用专用账号 `ihomy`（仅 DML 权限）、默认首页模块、管理员 `admin/admin123`。

### 3.5 构建后端（ihomy 用户）

```bash
sudo su - ihomy
cd /opt/ihomy/backend
chmod +x mvnw
./mvnw -B clean package -DskipTests
```
产物：`/opt/ihomy/backend/target/ihomy-backend.jar`

> Maven 加速（可选）：编辑 `~/.m2/settings.xml` 配阿里云镜像（见项目 README）。

### 3.6 构建前端（ihomy 用户）

```bash
cd /opt/ihomy/frontend
npm install
npm run build
```
产物：`/opt/ihomy/frontend/dist`

### 3.7 上传目录授权（root 操作一次）

```bash
exit   # 回到有 sudo 权限的账号
sudo mkdir -p /opt/ihomy/uploads
sudo chown -R ihomy:ihomy /opt/ihomy/uploads /var/log/ihomy
```

---

## 四、systemd 服务（后端开机自启，root 操作）

> 服务文件位于 `/etc/`，需 root 创建。后端进程以 `ihomy` 应用用户身份运行（`User=ihomy`），不暴露 root。

创建服务文件：

```bash
sudo tee /etc/systemd/system/ihomy-backend.service > /dev/null <<'EOF'
[Unit]
Description=Ihomy Family App Backend (Spring Boot)
After=network.target mysql.service redis-server.service

[Service]
Type=simple
User=ihomy
WorkingDirectory=/opt/ihomy/backend
ExecStart=/usr/bin/java -Xms256m -Xmx384m -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=192m -XX:+UseSerialGC -Xss512k -jar /opt/ihomy/backend/target/ihomy-backend.jar
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
> Redis 用 Docker 运行时，`After=` 可去掉 `redis-server.service`，改为 `docker.service`：`After=network.target mysql.service docker.service`。

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

## 七、防火墙与 SSH 配置

### 7.1 SSH 登录端口改为 19068（禁止 22 端口）

> 安全加固：将 SSH 端口从默认 22 改为 19068，避免自动扫描爆破。**防火墙先放行 19068，再改 sshd 配置，最后关闭 22**，顺序不能错，否则会把自己锁在外面。

**第一步：防火墙先放行 19068**

**Ubuntu (ufw)：**
```bash
sudo ufw allow 19068/tcp
```
**CentOS/RHEL (firewalld)：**
```bash
sudo firewall-cmd --permanent --add-port=19068/tcp
sudo firewall-cmd --reload
```

**第二步：修改 sshd 配置**

```bash
sudo vim /etc/ssh/sshd_config
# 找到 #Port 22 这行，改为：
#   Port 19068
# 保存退出
sudo systemctl restart ssh     # CentOS: sshd
```

**第三步：验证新端口可登录后，再关闭 22**

```bash
# 新开一个终端，用 19068 登录测试
ssh -p 19068 ihomy@服务器IP
# 能登录成功后，回到原终端关闭 22
sudo ufw deny 22/tcp           # CentOS: firewall-cmd --permanent --remove-service=ssh && firewall-cmd --reload
```

> **务必先验证 19068 能登录，再关 22**，否则会失去远程访问能力。
> 后续 SSH/SCP 命令都需加 `-p 19068`：`ssh -p 19068 ihomy@IP`、`scp -P 19068 文件 ihomy@IP:路径`。

### 7.2 开放 Web 端口

**Ubuntu (ufw)：**
```bash
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
sudo ufw status
```

**CentOS/RHEL (firewalld)：**
```bash
sudo firewall-cmd --permanent --add-service=http
sudo firewall-cmd --permanent --add-service=https
sudo firewall-cmd --reload
```

> MySQL(6306)、Redis(6379) **不要**开放公网，仅本机访问。
> SSH 端口 19068 必须放行，22 禁止。

---

## 八、更新部署流程

代码更新后，以 `ihomy` 用户构建（非 root），再以 root 重启系统服务：

```bash
# 1) 构建（ihomy 用户操作）
sudo su - ihomy
cd /opt/ihomy && git pull
cd /opt/ihomy/backend && ./mvnw -B clean package -DskipTests
cd /opt/ihomy/frontend && npm install && npm run build
exit

# 2) 重启服务（root 操作）
sudo systemctl restart ihomy-backend
sudo systemctl reload nginx
```

> 构建属于应用操作，用 `ihomy` 用户；重启 systemd 服务属于系统操作，用 root。权限分明。

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
以 `ihomy` 用户执行：`chmod +x /opt/ihomy/backend/mvnw`。

**Q2：后端启动失败，日志 `Permission denied`？**
检查 `/opt/ihomy/uploads`、`/var/log/ihomy` 的属主是否为 `ihomy:ihomy`（见 3.7）。systemd 服务 `User=ihomy`，目录必须归 ihomy 所有。

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
后端 JVM 已通过 systemd 配置限制为 `-Xmx384m`（见第四节）。MySQL 已通过 `my.cnf` 调优（见第十一节）。可用 `free -h`、`df -h` 查看。若仍紧张，参考第十一节"资源优化"。

---

## 十一、资源优化（2GB 内存方案）

> 目标：在 2GB 内存的服务器上稳定运行全套服务（后端 + MySQL + Redis + Nginx）。
> 适用：家庭规模（2-20 人，低并发）。
> 部署策略：**MySQL 本机部署 + Redis 用 Docker**（求稳方案）。
> 原理：JVM 调优 + MySQL 调优 + 组件就近原则，合计内存占用约 900MB，留 1GB 余量。

### 11.1 优化前后内存对比

| 组件 | 默认配置 | 优化后 | 节省 |
|------|----------|--------|------|
| Spring Boot | 512MB+ | ~300MB | 200MB+ |
| MySQL 8（本机） | 400MB+ | ~180MB | 220MB+ |
| Redis（Docker） | 50MB | 50MB | - |
| Docker daemon | 50-100MB | 50-100MB | 为 Redis 常驻 |
| Nginx | 20MB | 20MB | - |
| 系统 | 300MB | 300MB | - |
| **合计** | **~1.3GB+** | **~900MB** | **~400MB** |

> 说明：求稳方案仅 Redis 用 Docker，故 Docker daemon（50-100MB）仍需常驻。若连 Docker 也不装、Redis 本机 apt 安装，可再省 50-100MB，但失去容器化升级便利。权衡后推荐 Redis Docker。

### 11.2 JVM 调优(后端 Spring Boot)

systemd 服务文件已配置以下 JVM 参数(见第四节):

```
java -Xms256m -Xmx384m -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=192m -XX:+UseSerialGC -Xss512k -jar ihomy-backend.jar
```

| 参数 | 作用 |
|------|------|
| `-Xms256m -Xmx384m` | 堆内存 256-384MB(默认可能到 1GB) |
| `-XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=192m` | 元空间上限(默认无界) |
| `-XX:+UseSerialGC` | 单线程 GC,小堆表现好,省内存 |
| `-Xss512k` | 线程栈减半(默认 1MB) |

修改后重启生效:
```bash
sudo systemctl daemon-reload
sudo systemctl restart ihomy-backend
```

验证内存占用:
```bash
ps -o pid,rss,cmd -p $(pgrep -f ihomy-backend.jar)
# RSS 列为实际内存(KB),约 300000-350000 即 300-350MB
```

### 11.3 MySQL 调优

调优配置文件 `my.cnf` 关键项:

```ini
[mysqld]
# 连接数(默认 151,家庭场景降到 30)
max_connections = 30
thread_cache_size = 4

# InnoDB 缓冲(家庭数据小,128M 够用)
innodb_buffer_pool_size = 128M
innodb_log_buffer_size = 8M
innodb_log_file_size = 64M

# 表缓存(默认 4000,降到 200)
table_open_cache = 200
table_definition_cache = 200

# 会话级 buffer(每连接分配,调小)
sort_buffer_size = 128K
read_buffer_size = 128K
join_buffer_size = 128K
read_rnd_buffer_size = 128K

# 临时表
tmp_table_size = 16M
max_heap_table_size = 16M

# 关键!关闭性能监控,省 80-100MB
performance_schema = OFF
```

**部署方式（本机部署，求稳方案）：**

```bash
# 将项目提供的 my.cnf 放入配置目录
sudo cp /opt/ihomy/config/mysql/my.cnf /etc/mysql/conf.d/ihomy.cnf
sudo systemctl restart mysql     # CentOS: mysqld
```
> 完整 `my.cnf` 已随项目提供（`config/mysql/my.cnf`，含端口 6306 与全部调优项）。2.4 节安装时已执行过此步骤，此处为更新配置后重启。

验证内存占用：
```bash
ps -o pid,rss,cmd -p $(pgrep -f mysqld)
# RSS 约 150000-200000 即 150-200MB
```

### 11.4 Redis / Nginx 无需调优

- Redis（Docker 运行）默认极轻量（~50MB），家庭数据量小无需改。
- Nginx 托管静态文件，默认 ~20MB，无需改。
- Docker daemon 为运行 Redis 常驻（50-100MB），属求稳方案的必要开销；若极致省内存可改 Redis 本机 apt 安装并卸载 Docker。

### 11.5 优化效果验证

```bash
# 查看整体内存
free -h
#               total   used   free   available
# Mem:          1.9G    900M   1.0G   1.0G

# 查看各进程内存
ps -eo pid,rss,cmd --sort=-rss | grep -E 'java|mysql|redis|nginx|dockerd' | head
```

预期：总 used 约 850-950MB，available 留 1GB 左右。

---

## 附：目录规划

```
/opt/ihomy/                    # 项目代码（属主 ihomy:ihomy）
├── backend/
│   ├── target/ihomy-backend.jar
│   └── src/main/resources/application.yml
├── frontend/dist/             # 前端构建产物（nginx 读取）
├── uploads/                   # 上传文件（属主 ihomy）
└── config/mysql/my.cnf        # MySQL 调优配置（部署时 cp 到 /etc）
/var/log/ihomy/                # 服务日志（属主 ihomy）
/etc/nginx/conf.d/ihomy.conf   # nginx 站点配置（root 管理）
/etc/systemd/system/ihomy-backend.service   # root 管理，User=ihomy
/etc/letsencrypt/live/域名/    # HTTPS 证书（root 管理）
```

---

## 附：未来扩展 — 对接 NAS 存储

> 适用场景:有了 NAS(群晖/威联通/TrueNAS 等)后,希望把用户上传的文件存到 NAS 上,既节省服务器磁盘,又利用 NAS 的 RAID 冗余保护数据。
> **强烈推荐 NFS 挂载方案**:代码零改动,文件存储位置对应用透明。
> 前提条件:NAS 与服务器在同一内网,NFS 延迟低。

### 1. 在 NAS 上开启 NFS 共享

不同 NAS 品牌操作不同,核心是创建一个共享目录并允许服务器 IP 访问:

- **群晖 DSM**:控制面板 → 文件服务 → NFS → 启用;控制面板 → 共享文件夹 → 新建(如 `ihomy_files`)→ NFS 权限 → 添加服务器 IP,权限 `read/write`。
- **威联通 QTS**:控制台 → 网络与文件服务 → Win/Mac/NFS → 启用 NFS 服务;共享文件夹 → 编辑权限 → NFS 主机访问 → 添加服务器 IP。
- **TrueNAS**:Storage → Pools 创建数据集;Sharing → Unix (NFS) Shares → 添加,Authorized Hosts 填服务器 IP。

记下 NAS 的 NFS 导出路径,如:`192.168.1.100:/volume1/ihomy_files`。

### 2. 服务器安装 NFS 客户端(root 操作)

```bash
# Ubuntu
sudo apt install -y nfs-common
# CentOS/RHEL
sudo dnf install -y nfs-utils
```

### 3. 挂载 NAS 共享到 uploads 目录(root 操作)

```bash
# 停止后端(避免占用 uploads 目录)
sudo systemctl stop ihomy-backend

# 备份现有文件(若有)
sudo mv /opt/ihomy/uploads /opt/ihomy/uploads.bak

# 创建空目录并挂载 NAS
sudo mkdir -p /opt/ihomy/uploads
sudo mount -t nfs 192.168.1.100:/volume1/ihomy_files /opt/ihomy/uploads

# 验证挂载
df -h /opt/ihomy/uploads
# 应看到 NAS 的容量

# 授权给应用用户
sudo chown -R ihomy:ihomy /opt/ihomy/uploads

# 迁回旧文件(若有)
sudo cp -rp /opt/ihomy/uploads.bak/* /opt/ihomy/uploads/ 2>/dev/null
sudo rm -rf /opt/ihomy/uploads.bak

# 启动后端
sudo systemctl start ihomy-backend
```

### 4. 开机自动挂载(root 操作)

```bash
echo "192.168.1.100:/volume1/ihomy_files /opt/ihomy/uploads nfs defaults,_netdev 0 0" | sudo tee -a /etc/fstab

# 验证 fstab 配置正确(不会报错即 OK)
sudo mount -a
```

> `_netdev` 选项表示等网络就绪后再挂载,避免开机时因网络未起导致挂载失败。

### 5. 代码与配置改动

**无!** FileService 代码对存储位置透明,只要 `/opt/ihomy/uploads` 是可读写目录,上传/访问照常工作。Nginx 的 `/files/` location 也无需改动(仍指向 `/opt/ihomy/uploads/`)。

### 6. 验证

```bash
# 在服务器上传一个测试文件
sudo -u ihomy touch /opt/ihomy/uploads/nfs-test.txt

# 登录 NAS 文件管理器,应看到 ihomy_files 目录下出现 nfs-test.txt
# 反向验证:在 NAS 上删掉该文件,服务器上 ls 应消失
ls /opt/ihomy/uploads/nfs-test.txt
```

### 7. NAS 不可用时的风险与应对

- **风险**:NAS 宕机或网络中断时,文件上传会失败(写入报 IO 错误),已上传文件的访问也会 404。
- **应对**:NAS 的 NFS 服务要稳定;重要数据在 NAS 上做快照备份;服务器本地保留 `uploads.bak` 一段时间作为应急回退。

### 8. 其他对接方式(不推荐,供了解)

| 方式 | 说明 | 何时用 |
|------|------|--------|
| NFS 挂载(推荐) | 代码零改动,NAS 当本地目录 | NAS 在内网 |
| NAS 的 S3 兼容 API | 改 FileService 用 AWS SDK 连 NAS | NAS 异地,或想统一对象存储接口 |
| WebDAV | 改 FileService 用 WebDAV 客户端 | NAS 只暴露 WebDAV |
| 阿里云 OSS | 改 FileService 用 OSS SDK | 要公网 CDN 加速,或无 NAS |

> 若将来要走 S3 兼容方案(NAS/MinIO/OSS 通用),可在 `FileService` 增加一个 S3 实现,用 `@ConditionalOnProperty(name="file.storage", havingValue="s3")` 切换,本地实现保留为默认。届时再改造即可,当前无需动手。

---

## 附：可选 — Docker Compose 全容器化部署（≥4GB 内存推荐）

> ⚠️ **2GB 内存不建议用此方案**：全容器化会多一份 Docker daemon 开销 + 多个容器 runtime，内存余量偏紧。2GB 内存请用正文求稳方案（MySQL 本机 + Redis Docker）。本方案适合 4GB 及以上内存的服务器，或开发/测试环境快速拉起。

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
      - "6306:6306"
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
