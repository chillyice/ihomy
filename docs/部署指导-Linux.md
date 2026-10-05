# ihomy — Linux 部署指导

> 适用项目：`ihomy`（Vue3 + Spring Boot 3 + MySQL + Redis）
> 部署形态：Linux 服务器（Ubuntu 22.04/24.04 为主，附 CentOS/RHEL 差异）
> 面向：生产/家庭长期运行
> **本文件为入库脱敏版**（2026-09-07 自本地维护版拆分）：所有凭证位置以 `<占位符>` 表示——部署本就靠 external.yml 外挂注入，流程本身无秘密。项目维护者的真实凭证台账在本地 `Linux部署指导.md` §〇（不入 git）；其他部署者请自行生成强密码并妥善保管。

---

## 一、需要安装的软件清单

| # | 软件 | 版本要求 | 用途 | 是否必须 |
|---|------|----------|------|----------|
| 1 | OpenJDK | 21 LTS（`pom.xml` 已对齐 21） | 运行后端 | ✅ 必须 |
| 2 | Node.js | 18+（推荐 20 LTS） | 构建前端 | ✅ 必须（仅构建时） |
| 3 | MySQL | 8.0+ | 主数据库 | ✅ 必须（本机部署） |
| 4 | Redis | 6+ | 缓存 / JWT 令牌 | ✅ 必须（Docker 部署） |
| 5 | Docker Engine | 24+ | 运行 Redis 容器 | ✅ 必须（仅 Redis 用） |
| 6 | Nginx | 1.18+ | 托管前端 + 反向代理 + HTTPS | ✅ 生产推荐 |
| 7 | Certbot | 最新 | 申请/续期 Let's Encrypt 证书 | 🔒 HTTPS 时需要 |
| 8 | systemd | 系统自带 | 服务管理与开机自启 | ✅ 自带 |
| 9 | ufw / firewalld | 系统自带 | 防火墙 | ✅ 自带 |
| 10 | rsync | 最新 | 每日备份镜像 `uploads` 目录（见「附:数据备份」） | 🔒 备份时需要 |
| 11 | fail2ban / unattended-upgrades | 最新 | SSH 防爆破 / 自动安全更新（见 §7.3/§7.4） | 🔒 加固推荐 |

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

### 2.4 安装 MySQL 8.4.10（本机部署）

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

# 查询 29.7.0 的完整版本字符串（因 Ubuntu 版本不同后缀不同：jammy/noble 等）
apt-cache madison docker-ce | grep 29.7.0
# 输出类似：docker-ce | 5:29.7.0-1~ubuntu.24.04~noble | http://...
# 复制完整的版本字符串（5:29.7.0-1~ubuntu.xx.xx~代号），用于下方安装命令

# 安装 Docker CE 29.7.0 + containerd + 插件（将 <版本字符串> 替换为上一步查出的值）
sudo apt-get -y install docker-ce=<版本字符串> docker-ce-cli=<版本字符串> containerd.io docker-buildx-plugin docker-compose-plugin

# 启动并设置开机自启
sudo systemctl enable --now docker
# 让 ihomy 用户免 sudo 使用 docker（需重新登录生效；ihomy 用户在 3.1 节创建，若尚未创建可稍后再执行）
sudo usermod -aG docker ihomy
```

> **关于版本字符串**：`apt-cache madison` 查出的完整字符串含 Ubuntu 代号后缀（如 `5:29.7.0-1~ubuntu.22.04~jammy` 或 `5:29.7.0-1~ubuntu.24.04~noble`），不同 Ubuntu 版本后缀不同，**必须用 madison 查出的实际值**，不能照抄。
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
# 强密码：openssl rand -base64 24（记入本地台账 §〇，并写进 external.yml 的 spring.data.redis.password）
docker run -d --name ihomy-redis \
  -p 127.0.0.1:6379:6379 \
  --restart unless-stopped \
  redis redis-server --requirepass '<Redis密码>'
```
> 默认拉取 `redis:latest`。如需固定版本，用 `docker pull redis:7-alpine` 并在 `docker run` 时用 `redis:7-alpine`。
> **只绑 `127.0.0.1`**：Redis 只给本机后端用，公网/局域网一律不给；Docker 能绕过 ufw，绑定回环才是真门闩（V10.6）。`--requirepass` 与 external.yml 的 `spring.data.redis.password` 必须同值。

验证：
```bash
docker exec ihomy-redis redis-cli -a '<Redis密码>' --no-auth-warning ping
# PONG
docker exec ihomy-redis redis-cli ping
# NOAUTH Authentication required.   ← 不带密码被拒，说明密码生效
```
管理：
```bash
docker stop ihomy-redis      # 停止
docker start ihomy-redis     # 启动
docker restart ihomy-redis   # 重启
docker logs ihomy-redis      # 查看日志
docker pull redis && docker rm -f ihomy-redis && docker run -d --name ihomy-redis -p 127.0.0.1:6379:6379 --restart unless-stopped redis redis-server --requirepass '<Redis密码>'   # 升级
```
> ⚠️ 重建容器会**清空全部 Redis 数据**（无持久化卷）：刷新令牌黑名单、验证码、登录限流计数、当前家庭选择。顺序永远是「先改 external.yml → 停后端 → 重建 Redis → 起后端 → 冒烟」，详见 §2.6.1。

> 若不想装 Docker，Redis 也可本机安装：`sudo apt install -y redis-server`。但求稳方案推荐 Docker 化 Redis。

#### 2.6.1 生产 Redis 加固 / 改密码运行手册（V10.6）

老实例是 `-p 6379:6379`（0.0.0.0 全网卡）且无密码——公网若放行 6379 就是裸奔，且能绕过 ufw。按下面顺序一次性加固或换密（**顺序不可颠倒**，反了会让验证码、令牌刷新、登录限流全线认证失败，表现为登录不了/每 2 小时被登出）：

```bash
pw="$(openssl rand -base64 24)"     # 1) 生成强密码，先记进本地台账（不入 git）
echo "$pw"                          # 2) 一会儿抄进 external.yml

# 3) 改服务器外挂配置（/opt/ihomy/config/external.yml）
#    spring.data.redis.password: <与 --requirepass 完全同值>

sudo systemctl stop ihomy-backend   # 4) 先停后端，避免中间窗口反复 WRONGPASS

docker stop ihomy-redis && docker rm ihomy-redis        # 5) 重建（旧容器无卷，数据本就易失）
docker run -d --name ihomy-redis \
  -p 127.0.0.1:6379:6379 \
  --restart unless-stopped \
  redis redis-server --requirepass '<Redis密码>'

sudo systemctl start ihomy-backend  # 6) 起后端（配置启动早期即加载新密码）

# 7) 冒烟
docker exec ihomy-redis redis-cli -a '<Redis密码>' --no-auth-warning ping   # PONG
curl -s http://localhost:8080/api/public/home | head -c 80                   # 正常 JSON
sudo systemctl is-active ihomy-backend                                       # active
# 外网验证：从别的机器 telnet/nc <公网IP> 6379 应「拒绝」(绑定回环)
```

**副作用（可接受，但要知道）**：Redis 无持久化，重建即清空——此前已吊销的刷新令牌会重新有效（最长 7 天，仅当令牌本身已泄露才有影响）、验证码/登录限流计数/当前家庭选择丢失（用户下次自选家庭即可）。家庭场景风险低，窗口期内正常访问不受影响（访问令牌是无状态 JWT，2 小时内照常有效）。

**改密后手工命令都要带 `-a`**：`docker exec ihomy-redis redis-cli -a '<Redis密码>' --no-auth-warning ...`（或先 `export REDISCLI_AUTH='<Redis密码>'`）。`--requirepass` 写在 `docker run` 参数里，`docker inspect` 可见，故密码只放台账与 external.yml，不写进任何入库文件。

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
> **应用操作（代码/构建/配置）**：由 `ihomy` 用户执行，不用 root。

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
ssh-keygen -t ed25519 -C "<你的邮箱>"
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

> **配置机制（生产基线 + 外挂覆盖）**：源码 `application.yml` 已是生产基线（MySQL 6306/Redis 6379/Linux 路径/captcha 空/天气留空），jar 内嵌即生产。**DB 密码、Redis 密码与 JWT 密钥在基线中留空/缺席，必须由外挂配置 external.yml 提供**——缺失时 JwtUtils 启动即失败（fail-closed，防误用默认密钥）；开发环境差异同样走 external.yml 覆盖，不使用 profile。

**1) 从模板复制外挂配置并填写：**
```bash
mkdir -p /opt/ihomy/config
cp /opt/ihomy/backend/src/main/resources/external.yml.template /opt/ihomy/config/external.yml
vim /opt/ihomy/config/external.yml
# 必填：spring.datasource.password（DB 强密码）、jwt.secret（≥32 字符随机串）、
#        spring.data.redis.password（与 Redis 容器 --requirepass 同值，见 2.6.1）
# 按需：邮件 SMTP、天气四件套、上传/日志路径覆盖、captcha 固定码（生产留空）
chmod 640 /opt/ihomy/config/external.yml
```

**2) 让后端找到它**：设置环境变量 `IHOMY_CONFIG_PATH=/opt/ihomy/config/external.yml`——`ExternalConfigLoader` 在启动早期以最高优先级加载该文件并覆盖基线同键。systemd unit 的写法见第四节；一键部署脚本 `-UploadExternal` 上传外挂配置时会自动检查并补写该环境变量。

application.yml 关键配置（生产基线，密文项留空由 external.yml 提供）：
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:6306/ihomy?...     # MySQL 6306(本机部署)
    username: ihomy                                 # 专用应用账号(仅 DML)
    password:                                       # 留空,由 external.yml 提供
  data:
    redis:
      host: localhost
      port: 6379                                    # Redis(Docker 映射,只绑 127.0.0.1)
      password:                                     # 留空,由 external.yml 提供(必填,同 --requirepass)
file:
  upload-dir: /opt/ihomy/uploads                    # Linux 绝对路径
app:
  captcha-fixed-code:                               # 生产留空=随机验证码
  weather-api-host:                                 # 天气凭证留空,从 DB 读
  weather-private-key:                              # 私钥不入 git,部署后 UPDATE DB
```

> **⚠️ JWT 密钥与天气私钥安全（风险最小化）**：
> - **git 仓库不含任何私钥**（schema.sql 种子 `private_key=NULL`，application.yml 留空）
> - 和风天气公钥可入库（公开信息），私钥只在服务器上手动填入
> - 生产 jwt.secret 建议 `ENC(...)`（AES-GCM）包装：密文用运维接口 `GET /api/ops/crypto/encrypt?plaintext=xxx`（OPS 角色）生成，盐值经环境变量 `IHOMY_AES_SALT` 注入（systemd unit 加一行 Environment，盐值妥善保管）；DB 密码必须明文（启动早期无盐可用，鸡生蛋）
> - **部署后执行 SQL 填入天气私钥**（凭证从和风天气控制台获取；不要编辑 yml，改 DB 更安全）：
> ```bash
> mysql -uroot -p ihomy -e "UPDATE sys_weather_credential SET private_key='-----BEGIN PRIVATE KEY-----\n<和风控制台生成的Ed25519私钥单行base64>\n-----END PRIVATE KEY-----' WHERE env='prod';"
> # 切换启用环境:把 test 改 0,prod 改 1
> mysql -uroot -p ihomy -e "UPDATE sys_weather_credential SET status=0 WHERE env='test'; UPDATE sys_weather_credential SET status=1 WHERE env='prod';"
> ```
> - **月度配额**：每月最多调用 49999 次 API，超限后本月自动停止调用（返回 null，前端降级），次月自动恢复

> 应用使用专用账号 `ihomy` 连接数据库（仅 SELECT/INSERT/UPDATE/DELETE 权限，最小权限原则），不要用 root 跑业务。该账号由 schema.sql 自动创建并授权，无需手动建号。

### 3.4 建库建表（root 操作，仅此一次）

```bash
# 退出 ihomy 用户回到有 sudo 权限的账号
exit
# 用数据库 root 执行 schema.sql（建库、建表、创建应用账号 ihomy 并授权）
mysql -uroot -p --default-character-set=utf8mb4 < /opt/ihomy/backend/src/main/resources/schema.sql
```
该脚本由数据库 root 执行一次，会创建 `ihomy` 库、**81 张表**（`sys_` 系统与账号权限 / `report_` 报表日志 / `family_` 家庭事务 / `content_` 内容数据四前缀）、应用专用账号 `ihomy`（仅 DML 权限）、默认首页模块（含聊天室 `chat` 行）、管理员 `admin` 与运维账号 `ops`（初始密码为开发安全版，见下方警告）。

> **⚠️ 生产必须改密（schema.sql 为开发安全版）**：入库版 schema.sql 内置的是本机 Docker 开发固定凭证（ihomy 账号开发密码 + admin/ops 开发专用 BCrypt 哈希，明文只在维护者本地文档）。生产部署后立即执行：
> ```bash
> mysql -uroot -p ihomy -e "ALTER USER 'ihomy'@'localhost' IDENTIFIED BY '<生产强密码>'; ALTER USER 'ihomy'@'%' IDENTIFIED BY '<生产强密码>'; FLUSH PRIVILEGES;"
> ```
> - 生产 MySQL 启用 `validate_password` MEDIUM 策略（特殊字符/数字/大小写各≥1，长度≥8）：生成密码必须含特殊字符（建议 `!@%^&*-_+=.`，避开 `' " \ $ |` 转义雷区），否则 `ALTER USER` 报 1819
> - external.yml 的 `spring.datasource.password` 同步填同一新密码
> - admin/ops 首次登录后立即在页面改密

> 校验建表结果：`mysql -uroot -p ihomy -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='ihomy';"` 应为 **81**。

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
> **`Environment=IHOMY_CONFIG_PATH` 必须设置**（指向 external.yml），否则后端启动即失败（JwtUtils fail-closed，见 3.3）。

创建服务文件：

```bash
sudo tee /etc/systemd/system/ihomy-backend.service > /dev/null <<'EOF'
[Unit]
Description=Ihomy Family App Backend (Spring Boot)
After=network.target mysql.service docker.service

[Service]
Type=simple
User=ihomy
Environment=IHOMY_CONFIG_PATH=/opt/ihomy/config/external.yml
# Environment=IHOMY_AES_SALT=<Base64盐值>   # jwt.secret 用 ENC(...) 包装时提供(见 3.3)
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

> CentOS 下 `After=` 的服务名是 `mysqld.service`，按实际调整。

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
# 80 → 443 强制跳转
server {
    listen 80;
    server_name ihomy.top www.ihomy.top;
    # certbot 验证用（证书续期时需保留）
    location /.well-known/acme-challenge/ {
        root /var/www/html;
    }
    location / {
        return 301 https://$host$request_uri;
    }
}

# HTTPS 主站点
server {
    listen 443 ssl;
    http2 on;
    server_name ihomy.top www.ihomy.top;

    # 响应头声明字符集,确保浏览器按 UTF-8 解析
    charset utf-8;
    charset_types text/plain text/css text/javascript application/javascript application/json application/xml image/svg+xml;

    # 证书（certbot --nginx 自动生成，路径如下）
    ssl_certificate     /etc/letsencrypt/live/ihomy.top/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/ihomy.top/privkey.pem;

    # SSL 优化
    ssl_session_cache shared:SSL:10m;
    ssl_session_timeout 1d;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;
    ssl_prefer_server_ciphers on;

    # HSTS（首次访问后强制 HTTPS，1 年）
    add_header Strict-Transport-Security "max-age=31536000" always;

    root  /opt/ihomy/frontend/dist;
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
        proxy_read_timeout 60s;
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

    # 上传文件：一律反代给后端做读取鉴权（登录态 / PUBLIC 反查，V10.7），
    # 千万别改回 alias 直出——直出等于把「拿到 URL 就能读」的老问题放回来
    location /files/ {
        proxy_pass http://127.0.0.1:8080/api/files/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 3600s;   # 视频/电子书大文件下载别被默认 60s 掐断
    }

    # 静态资源缓存（排除 /files/；本 location 有 add_header，必须自带 HSTS，否则被继承规则吃掉）
    location ~* ^/(?!files/).+\.(js|css|png|jpg|jpeg|gif|ico|svg|woff2)$ {
        expires 7d;
        add_header Cache-Control "public, immutable";
        add_header Strict-Transport-Security "max-age=31536000" always;
    }
}
EOF
```

> **注意**：
> - `location /files/` 必须**反代到后端**（`proxy_pass .../api/files/`）。改回 `alias` 静态直出 = 绕过读取鉴权，拿到 URL 的任何人都能读私有文件（V10.7 起文件访问由后端判定，见踩坑速查 §7.8）。
> - nginx 有条硬规则：**location 里只要写了一条 `add_header`，server 级的同类头就不再继承**——所以图片/JS 缓存 location 必须自己带一行 HSTS（上面已带），否则 HSTS 只对 HTML 生效。
> - 图片扩展名缓存 location 用负向断言 `^/(?!files/)` 排除 `/files/`，避免其 `root`/`add_header` 影响文件路径。
> - `/.well-known/acme-challenge/` 必须保留，certbot 续期时要用。
> - WebSocket 的 `proxy_read_timeout 3600s` 防止长连接被 nginx 默认 60s 超时断开。
> - **必须显式配 `gzip_types`**（见下）：Ubuntu 自带的 `/etc/nginx/nginx.conf` 只写了 `gzip on;`，
>   `gzip_types` 整行是注释状态，默认仅压缩 `text/html` —— JS/CSS/API JSON 全部以原始体积传输。

**gzip 压缩（2026-09-24 发现线上未生效；**同日已在生产补上并实测生效**，新部署照抄本节即自带）**：在上面 `server {}` 块内加：

```nginx
    gzip on;
    gzip_types text/css application/javascript application/json image/svg+xml;
    gzip_min_length 1024;
    gzip_vary on;
```

`gzip_types` 可在 `http`/`server`/`location` 上下文设置，故直接放进站点 conf 即可，无需改发行版
`nginx.conf`。`gzip_vary on` 补 `Vary: Accept-Encoding`（给中间缓存用）。`text/html` 无需列出
（nginx 恒压缩）。**2026-09-24 实测（V9.91）**：入口 JS 343730 → **143202B**、主 CSS 174224 →
**38302B**、`/api/public/home` JSON 7216 → **1813B**，首屏少传约 336KB（改前的预测值 341.9KB→~127KB
偏乐观，实际压缩率按默认 `gzip_comp_level 1` 计）。生产改动前旧配置备份为
`/etc/nginx/conf.d/ihomy.conf.bak-20260924`，回滚即 `mv` 回来 + `nginx -t && systemctl reload nginx`。

验证（前两条要出现 `Content-Encoding: gzip`，第三条本就压缩、作对照；JS/CSS 路径用 `dist/assets/` 里
带 hash 的文件名）：
```bash
curl -sI -H 'Accept-Encoding: gzip' https://ihomy.top/assets/index-<hash>.js   # JS
curl -sI -H 'Accept-Encoding: gzip' https://ihomy.top/api/public/home          # API JSON
curl -sI -H 'Accept-Encoding: gzip' https://ihomy.top/                         # HTML（对照）
```

测试并重载：
```bash
sudo nginx -t
sudo systemctl reload nginx
```

**申请证书**（先确保 DNS A 记录已指向本机 IP，且 80 端口可公网访问）：
```bash
sudo certbot --nginx -d ihomy.top -d www.ihomy.top -m <你的邮箱> --agree-tos --no-eff-email
```
certbot 会自动：申请证书 → 修改 nginx 配置（加上 443 ssl）→ 设置 systemd timer 自动续期。证书路径：
- `/etc/letsencrypt/live/ihomy.top/fullchain.pem`
- `/etc/letsencrypt/live/ihomy.top/privkey.pem`

续期测试：
```bash
sudo certbot renew --dry-run
```

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
> Redis 已用 `-p 127.0.0.1:6379:6379` 只绑回环（Docker 发布端口会绕过 ufw，防火墙不是唯一防线）+ `--requirepass` 双保险，见 §2.6.1。
> SSH 端口 19068 必须放行，22 禁止。

### 7.3 SSH 防爆破(fail2ban,root)

改端口只能避开无差别扫描，挡不住针对端口的持续爆破；再用 fail2ban 按来源 IP 自动封禁。

```bash
sudo apt install -y fail2ban
sudo cp /opt/ihomy/config/fail2ban/ihomy-sshd.local /etc/fail2ban/jail.d/ihomy-sshd.local
sudo systemctl enable --now fail2ban
sudo systemctl restart fail2ban
sudo fail2ban-client status sshd        # 查看当前封禁统计(banned IP 列表)
```

> 仓库配置 `config/fail2ban/ihomy-sshd.local`:10 分钟内失败 5 次即封禁 1 小时;`port` 必须写改后的 **19068**;`backend = systemd` 直接读 journal,无需给 sshd 单独配日志文件。
> CentOS/RHEL 用 `sudo dnf install -y fail2ban` + `sudo systemctl enable --now fail2ban`,配置文件路径同为 `/etc/fail2ban/jail.d/`。
> 解封:`sudo fail2ban-client set sshd unbanip <IP>`。

### 7.4 自动安全更新(unattended-upgrades,root)

Ubuntu 默认装有 `unattended-upgrades`,只开「安全源」自动升级即可,既补漏洞又不引入功能性重启风险;需要重启内核时会落 `/var/run/reboot-required`。

```bash
sudo apt install -y unattended-upgrades
sudo dpkg-reconfigure -plow unattended-upgrades     # 交互选 Yes(启用自动更新)
# 非交互环境可直接落配置:
sudo tee /etc/apt/apt.conf.d/20auto-upgrades > /dev/null <<'EOF'
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
EOF
systemctl status unattended-upgrades --no-pager     # 确认服务在跑
cat /var/run/reboot-required 2>/dev/null            # 存在则说明需要择机重启
```

> CentOS/RHEL 用 `sudo dnf install -y dnf-automatic` + 编辑 `/etc/dnf/automatic.conf`(`upgrade_type = security`,`apply_updates = yes`)+ `sudo systemctl enable --now dnf-automatic.timer`。
> **重启由人决定**:自动更新只装补丁不自动重启(默认行为),需要重启时按 §11 的低峰时段手动执行并复核「九、验证清单」。

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
> 更便捷的方式见下节「一键部署流水线」。

---

## 八·补、一键部署流水线（本地构建 → scp 上传 → 远程重启）

> 日常迭代懒得每次 SSH 进服务器敲命令？用项目自带的 `scripts/deploy.ps1`，在 Windows 本地一条命令完成「构建 → 上传 → 迁移 → 重启 → 健康检查」。
> 适合场景：服务器已按前文完成首次部署（源码已克隆、systemd 服务已配、nginx 已跑、external.yml 已就位），后续只是更新代码。

### 8·补.1 前置：配置 SSH 免密登录

脚本用 `ssh`/`scp` 连服务器（端口 19068、root 登录），必须配置公钥免密，否则每次要输密码。

```powershell
# 1) 本地生成密钥（已生成可跳过）
ssh-keygen -t ed25519

# 2) 上传公钥到服务器（会提示输一次密码）
Get-Content $env:USERPROFILE\.ssh\id_ed25519.pub | ssh -p 19068 root@ihomy.top "mkdir -p ~/.ssh && cat >> ~/.ssh/authorized_keys"

# 3) 验证免密（不应再要密码）
ssh -p 19068 root@ihomy.top 'echo ok'
```

### 8·补.2 使用

```powershell
# 在项目根目录执行
powershell -ExecutionPolicy Bypass -File scripts\deploy.ps1

# 常用参数
.\scripts\deploy.ps1                    # 全量部署（后端+前端，不动外挂配置）
.\scripts\deploy.ps1 -BackendOnly       # 只更后端
.\scripts\deploy.ps1 -FrontendOnly      # 只更前端
.\scripts\deploy.ps1 -SkipBuild         # 跳过本地构建，直接用现有 jar/dist 部署
.\scripts\deploy.ps1 -UploadExternal    # 上传外挂配置 external.yml（首次部署或配置变更时）
.\scripts\deploy.ps1 -UploadExternal -ExternalConfig D:\path\to\external.yml   # 指定本地外挂配置路径
.\scripts\deploy.ps1 -Server 1.2.3.4    # 指定其他服务器（默认 ihomy.top）
```

### 8·补.3 脚本做了什么

1. **前置检查**：JAVA_HOME、ssh/scp 可用、tar 固定用 Windows 自带 bsdtar（从 Git Bash 调起时 PATH 里 GNU tar 优先，会把 `C:\` 路径的冒号解析成远程主机名导致打包失败）、SSH 免密连通。
2. **本地构建**：`mvnw clean package` 打 jar（jar 内嵌 application.yml 为共同基线）；`npm run build` 出 dist；dist 打成 tar.gz 单文件传输（比 `scp -r` 快很多）。
3. **上传外挂配置（仅 `-UploadExternal` 时；默认不上传，避免覆盖生产配置）**：scp 上传到 `/opt/ihomy/config/external.yml.new` → 备份服务器旧文件为 `external.yml.bak` → 原子替换并 `chmod 640`；若 systemd unit 尚无 `IHOMY_CONFIG_PATH` 环境变量，自动补写并 `daemon-reload`。
4. **数据库迁移**：`backend/src/main/resources/migrations.sql` 存在可执行语句时，自动上传并经 `mysql --default-character-set=utf8mb4` 执行（含中文数据，不带此参数会写乱码）。
5. **后端部署**：scp 上传 jar 到 `.new` → 清理早期部署遗留的 `target/application.yml` 外部覆盖（差异项统一走 external.yml）→ 备份旧 jar → 原子替换 → `systemctl restart` → 轮询 `is-active` 确认启动（最多 30 秒，Spring Boot 在 2GB 服务器约需 30 秒）。启动失败自动回滚到 `.bak`。
6. **前端部署**：scp 上传 tar.gz → 远程解压到 `dist.new` → 旧 `dist` 改名 `dist.bak` → `dist.new` 改名 `dist` → `nginx -t && systemctl reload nginx`。失败自动回滚。
7. **健康检查**：`curl https://ihomy.top/api/public/home` 期望 200，最多重试 12 次（约 60 秒，覆盖 Spring Boot ~30 秒启动），失败自动拉取后端日志尾部 40 行。

### 8·补.4 注意事项

- **配置分层**：jar 内嵌 application.yml 是共同基线（端口/URL/mybatis/jwt/logging）；测试/生产差异配置（MySQL/Redis 密码、上传路径、验证码、天气凭证）全部放外挂文件 external.yml，`IHOMY_CONFIG_PATH` 指向，`ExternalConfigLoader` 以最高优先级加载。**首次部署或配置变更时用 `-UploadExternal` 上传**（本地从 `external.yml.template` 复制填写）。
- **不替代首次部署**：服务器需已按前文第三节完成首次部署（源码克隆 + 建库建表 + systemd 配置 + nginx 配置 + external.yml 就位）。本脚本只做「更新」。
- **数据库变更**：结构性变更写进 `migrations.sql`（幂等段），部署时自动执行；临时修复也可手动 `mysql -uroot -p ihomy < 增量.sql`。
- **回滚**：失败时脚本自动回滚 jar/dist。若需手动回滚，登录服务器 `mv ihomy-backend.jar.bak ihomy-backend.jar && systemctl restart ihomy-backend`。

---

## 九、验证清单

| 检查项 | 命令/方式 | 预期 |
|--------|-----------|------|
| 后端服务 | `sudo systemctl status ihomy-backend` | active (running) |
| 后端健康 | `curl -i http://localhost:8080/api/public/health` | HTTP 200，body `data.status=UP`（依赖不可用时 503/DEGRADED） |
| 后端接口 | `curl http://localhost:8080/api/public/home` | 返回 JSON（含 modules） |
| 前端访问 | 浏览器 `https://你的域名` | 登录页 |
| 登录 | 邮箱 + 密码 + 图形验证码 | 进入首页 |
| 运维登录 | `ops` + 密码（初始密码为开发安全版，生产部署后已按 3.4 改密则用新密码） | 进入运维页 |
| 数据库 | `mysql -uihomy -p ihomy -e "show tables;"` | 81 张表 |
| Redis | `docker exec ihomy-redis redis-cli -a '<Redis密码>' --no-auth-warning ping`(不带 `-a` 应报 `NOAUTH`) | PONG |
| Redis 端口 | 从外网 `nc -vz <公网IP> 6379` | 拒绝连接(只绑回环) |
| Nginx | `sudo nginx -t` | syntax ok |
| 证书 | `sudo certbot certificates` | 有效 |
| PWA 安装 | Chrome/Safari 地址栏安装图标 | 可安装到桌面 |
| 开机自启 | `sudo systemctl is-enabled ihomy-backend nginx` | enabled |
| 上传文件 | 浏览器访问 `/files/pictures/...` 图片 URL | 200 OK |
| WebSocket | 浏览器登录后进入聊天室 | 实时收发消息 |
| 视频上传 | 后台放映厅上传 >100MB 视频 | 上传成功 |

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

**Q6：上传图片/视频 413 Request Entity Too Large？**
nginx `client_max_body_size` 已配置为 `500m`（放映厅视频大文件必需）。若仍报错，检查 nginx 配置是否已重载，以及是否有反向代理层（如 CDN）也需调大。

**Q7：上传文件访问 404？**
检查 nginx `location /files/` 是否是**反代到后端**（`proxy_pass http://127.0.0.1:8080/api/files/;`）。若还是 `alias` 静态直出，私有文件会绕过鉴权（V10.7 起判定在后端，见踩坑速查 §7.8）；另外确认图片扩展名缓存 location 用负向断言 `^/(?!files/)` 排除了 `/files/`。

**Q8：聊天室连不上？**
检查 nginx 是否配置了 `/api/ws` 的 WebSocket 反代（`proxy_http_version 1.1` + `Upgrade` 头）。前端握手 URL 为 `wss://域名/api/ws/chat?token=...`（HTTPS）或 `ws://域名/api/ws/chat?token=...`（HTTP）。

**Q9：端口被占用？**
```bash
sudo ss -tlnp | grep -E '8080|80|443'
```
改 `application.yml` 的 `server.port` 或停掉冲突服务。

**Q10：磁盘/内存不足？**
后端 JVM 已通过 systemd 配置限制为 `-Xmx384m`（见第四节）。MySQL 已通过 `my.cnf` 调优（见第十一节）。可用 `free -h`、`df -h` 查看。若仍紧张，参考第十一节"资源优化"。

**Q11：DNS 不生效，certbot 申请证书失败 `no valid A records`？**
域名未指向服务器 IP。先去域名注册商 DNS 面板添加 A 记录（主机 `@`，值=服务器公网 IP），等 5-10 分钟用 `dig 你的域名` 验证能解析后再重跑 certbot。

**Q12：后端起不来，日志提示 jwt.secret 缺失 / IHOMY_CONFIG_PATH？**
external.yml 缺失或未生效（见 3.3）：确认文件存在、`IHOMY_CONFIG_PATH` 已在 systemd unit 设置（见第四节）、`daemon-reload` 后重启。这是 fail-closed 设计，防误用默认密钥。

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

## 十二、放映厅媒体引擎（NAS 上的 Jellyfin，可选）

> 放映厅的影片由**媒体服务器**（Jellyfin 10.9.x）提供：刮削信息、播放令牌、观看进度由 ihomy 后端与它交互，**视频流量由客户端直连媒体服务器**，不经过应用服务器。所以媒体服务器放 NAS 或家里另一台常开的机器上最合适——这台 2GB 应用服务器不适合跑转码。
> 对应数据是一行家庭级配置 `sys_media_server`（引擎类型/服务器地址/播放地址/账号/密码），由设置页-放映厅-「放映厅引擎」读写，密码加密存库。

### 12.1 部署位置与容器

放在 NAS（Docker）或另一台常开机器上，**不要装在应用服务器上**（转码吃 CPU 与内存，与求稳方案冲突；`docker-compose.yml` 里的 jellyfin profile 只是本机联调用）。

NAS 上的 compose 片段，版本与开发环境对齐（固定版本，别用 latest）：

```yaml
services:
  jellyfin:
    image: jellyfin/jellyfin:10.9.11
    container_name: jellyfin
    restart: unless-stopped
    ports:
      - "8096:8096"
    volumes:
      - /volume1/docker/jellyfin/config:/config
      - /volume1/docker/jellyfin/cache:/cache
      - /volume1/media/Movies:/media/Movies:ro     # 电影库（只读即可）
      - /volume1/media/Shows:/media/Shows:ro       # 剧集库（只读即可）
```

- 媒体目录给容器**只读**挂载；`/config`、`/cache` 需可写。
- 首次访问 `http://<NAS 地址>:8096` 走完初始化向导：建管理员账号，加「电影」「剧集」两个媒体库分别指向 `/media/Movies`、`/media/Shows`（与本地联调一致，放映厅按库读取）。
- 元数据由媒体服务器刮削，NAS 需能出网访问 TMDB。
- **为每位成员各建一个账号**（可选）：在 ihomy 侧填进「我的播放档案」即可各自续看（见 12.4）。

### 12.2 两个地址与网络（最容易踩的一条）

| 设置项 | 填什么 | 谁在用 |
|--------|--------|--------|
| 服务器地址 | 应用服务器**能访问到**的地址（如 `http://192.168.1.10:8096` 或隧道地址） | 后端：取元数据、发播放令牌、读写观看进度 |
| 播放地址 | **客户端（手机/平板/TV）能直连**的地址；留空表示与服务器地址相同 | 浏览器/播放器：直接拉视频流 |

**⚠ 混合内容拦截（HTTPS 站点必看）**：生产站点是 `https://`，若播放地址填 `http://…`，浏览器会把视频请求按混合内容拦掉——**能直出的影片与需要转码的影片都放不出来**，且现象不明显（播放器只是报错或一直转圈）。两种可行做法：

**做法 A（推荐）：在同一个 nginx 上加子域名，反代到 NAS**

```bash
sudo tee /etc/nginx/conf.d/ihomy-media.conf > /dev/null <<'EOF'
server {
    listen 443 ssl;
    http2 on;
    server_name media.ihomy.top;

    ssl_certificate     /etc/letsencrypt/live/media.ihomy.top/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/media.ihomy.top/privkey.pem;

    location / {
        proxy_pass http://192.168.1.10:8096;     # NAS 上的 Jellyfin
        proxy_http_version 1.1;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Upgrade           $http_upgrade;
        proxy_set_header Connection        "upgrade";
        proxy_buffering   off;                   # 视频流不要缓冲
        proxy_read_timeout  3600s;
        proxy_send_timeout  3600s;
    }
}
EOF

sudo nginx -t && sudo systemctl reload nginx

# 子域名解析到本机的 443 后签证书（详见第六章）
sudo certbot --nginx -d media.<你的域名> -m <你的邮箱> --agree-tos --no-eff-email
```

「播放地址」填 `https://media.<你的域名>`。DNS 里把子域名解析到**应用服务器**即可，NAS 的 8096 **不必对公网开放**（只要应用服务器能连到它）——把 Jellyfin 直接暴露到公网等于把整个媒体库敞开。

**做法 B：只在家里看**
内网访问走 `http://192.168.x.x`，页面本身是 HTTP，不存在拦截；但外网 HTTPS 访问时播放依旧放不出来。适合「只在家看」的用法。

**防火墙（仅做法 A 之外的直连场景）**：若坚持让客户端直连 NAS，放行端口并限制来源网段：

```bash
sudo ufw allow from 192.168.1.0/24 to any port 8096 proto tcp
```

### 12.3 媒体服务器侧准备

- 媒体库两个：电影（类型「电影」）、剧集（类型「电视剧」）。
- 建一个管理员账号供 ihomy 后端使用（后端用它出播放令牌）。
- 成员账号（可选，用于各自续看）。
- **限制并发转码数**：NAS 性能弱时多人同看会卡，在 Jellyfin 控制台限制同时转码数量；启用硬解（若 NAS 支持）能显著降负载。

### 12.4 ihomy 侧配置

1. **家长**：设置页 → 放映厅 → 「放映厅引擎」，填引擎类型（Jellyfin / Emby）、服务器地址、播放地址、账号、密码 → 保存 → 点「测试连接」，应回报服务器版本与媒体库计数。
2. **每位成员（可选）**：设置页 → 放映厅 → 「我的播放档案」，填自己在媒体服务器上的账号 → 生效后各自续看；未填写或账号失效时**回落家庭账号**（进度变成全家共用的那一份）。

### 12.5 上线核对

```bash
# 应用服务器能连到媒体服务器（后端走的就是这个地址）
curl -s -o /dev/null -w '%{http_code}\n' http://192.168.1.10:8096/System/Info/Public
# 期望 200

# 接口检查（在能访问后端的机器上跑，密码用测试账号）
IHOMY_TEST_PWD=<密码> python test/automation/media_engine_check.py --base http://localhost:8080
```

浏览器上再确认三件事：① 列表与详情能出海报；② 能直出的影片点开即播；③ **放不了的编码（HEVC/10bit 等）能自动走转码播起来**；控制台不应出现混合内容被拦的报错。

### 12.6 Emby

「引擎类型」选 `EMBY`，地址与账号口径完全相同。Emby 与 Jellyfin 接口同源，但版本差异会影响转码与字幕行为，**切换后重跑 12.5**。

---

## 附：目录规划

```
/opt/ihomy/                    # 项目代码（属主 ihomy:ihomy）
├── backend/
│   ├── target/ihomy-backend.jar
│   └── src/main/resources/application.yml
├── frontend/dist/             # 前端构建产物（nginx 读取）
├── uploads/                   # 上传文件（属主 ihomy）
├── logs/                      # 三类日志 access/server/thirdparty（logback 写入，属主 ihomy）
├── scripts/                   # prod-backup.sh / prod-restore.sh 等运维脚本
└── config/
    ├── mysql/my.cnf           # MySQL 调优配置（部署时 cp 到 /etc）
    ├── external.yml           # 外挂配置（DB 密码/JWT 密钥等，chmod 640，不入 git）
    ├── fail2ban/ihomy-sshd.local   # SSH 防爆破配置（部署时 cp 到 /etc/fail2ban/jail.d/）
    └── systemd/ihomy-backup.{service,timer}   # 每日备份定时器（部署时 cp 到 /etc/systemd/system/）
/var/backups/ihomy/            # 每日备份：db/*.sql.gz（保留 14 天）+ uploads/ 只增不删镜像 + .last-success
/var/log/ihomy/                # systemd 标准输出日志（属主 ihomy）
/etc/nginx/conf.d/ihomy.conf   # nginx 站点配置（root 管理）
/etc/systemd/system/ihomy-backend.service   # root 管理，User=ihomy + IHOMY_CONFIG_PATH
/etc/systemd/system/ihomy-backup.{service,timer}   # root 管理，每日 03:00 备份（安装见「附:数据备份」）
/etc/systemd/system/multi-user.target.wants/ihomy-backup.timer   # enable 后生成
/etc/letsencrypt/live/ihomy.top/    # HTTPS 证书（root 管理）
```

---

## 附:数据备份

> **误删兜底分两层**:①内容误删(照片/相册/视频/图书)先进 `/recycle` 回收站,7 天内可自行恢复(V10.10);②回收站超期清理、整库/整机级故障,靠本节备份。两层职责不同,都不可省。
> 备份脚本与 systemd 单元已随仓库提供(`scripts/prod-backup.sh`、`config/systemd/ihomy-backup.{service,timer}`),装机时安装一次即可每日自动执行。

### 1. 安装每日定时备份(root,一次)

```bash
sudo apt install -y rsync          # 备份用 rsync 镜像 uploads(已装可跳过)
sudo cp /opt/ihomy/config/systemd/ihomy-backup.service /opt/ihomy/config/systemd/ihomy-backup.timer /etc/systemd/system/
sudo chmod +x /opt/ihomy/scripts/prod-backup.sh /opt/ihomy/scripts/prod-restore.sh
sudo systemctl daemon-reload
sudo systemctl enable --now ihomy-backup.timer
systemctl list-timers ihomy-backup.timer       # 确认下次触发时间
sudo systemctl start ihomy-backup.service      # 立即手动跑一次,验证通路
journalctl -u ihomy-backup.service -n 50       # 查看本次结果
```

脚本做什么:`mysqldump --single-transaction` 逻辑备份(含存储过程/触发器/事件)→ `/var/backups/ihomy/db/ihomy-<日期>_<时分>.sql.gz`,随即 `gzip -t` 校验;`rsync -a` **只增不删**地把 `/opt/ihomy/uploads` 镜像到 `/var/backups/ihomy/uploads/`(故意不加 `--delete`:源目录误删时备份里仍留有历史文件);数据库转储保留 14 天,每次成功写 `/var/backups/ihomy/.last-success`,便于巡检判断是否按期执行。备份是重 I/O 任务,单元设了 `Nice=10` + `IOSchedulingClass=idle`,不与后端争资源。

> 定时点默认 **每日 03:00**(`OnCalendar=*-*-* 03:00:00`,`Persistent=true` 服务器停机后开机补跑,`RandomizedDelaySec=10m` 避开整点尖峰)。改时间/保留天数:编辑 timer 的 `OnCalendar` 或 service 的 `IHOMY_BACKUP_RETENTION_DAYS` 后 `systemctl daemon-reload && systemctl restart ihomy-backup.timer`。
> 备份落在同一台机器上,只防「误删/库损坏」不防「整机故障」。重要数据请再异地同步一份(如 NAS,见文末)。

### 2. 恢复(先演练再用)

**恢复前务必先演练**,确认转储可用;演练导入临时库,不碰生产:

```bash
# 演练:导入 ihomy_drill,核对表数与关键行数后删除
sudo /opt/ihomy/scripts/prod-restore.sh /var/backups/ihomy/db/ihomy-2026-10-05_0300.sql.gz ihomy_drill
mysql -uroot -N -B -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='ihomy_drill';"   # 应为 81
mysql -uroot -e "DROP DATABASE ihomy_drill;"

# 真恢复:目标库写 ihomy(脚本会先把当前库自动导出到 pre-restore-*.sql.gz 再覆盖)
sudo /opt/ihomy/scripts/prod-restore.sh /var/backups/ihomy/db/ihomy-<日期>_<时分>.sql.gz ihomy

# uploads 目录恢复(目录级同步,恢复前先确认目标目录内容)
sudo rsync -a --delete /var/backups/ihomy/uploads/ /opt/ihomy/uploads/
sudo chown -R ihomy:ihomy /opt/ihomy/uploads
sudo systemctl restart ihomy-backend
```

DB 里的文件 URL(`/files/...`)与物理路径解耦,目录还原后即可访问。恢复后按「九、验证清单」复核。

### 3. 恢复演练记录

| 日期 | 范围 | 结论 | 证据 |
|------|------|------|------|
| 2026-10-05 | 本机开发库(81 表/近 500KB 转储) | 通过:演练库表数 81、抽查 `sys_user`/`sys_auth`/`sys_role_auth`/`sys_home_module`/`content_blog`/`report_system`/`sys_parameter`/`family_item` 行数与源库一致,`admin` 账号值一致 | `test/reports/2026-10-05_V10.18_回归_测试报告.md` |

> **建议**:每次改动脚本或更换服务器后重跑一次演练,并把结论追加到上表。

---

## 附：Windows 开发环境 ↔ Linux 上线:路径转换清单

> 日常在 Windows 上开发验证,上线 Linux 时的路径处理如下。
> 核心结论:**源码 `application.yml` 是生产基线(MySQL 6306/Redis 6379/Linux 路径),开发差异由 external.yml(`IHOMY_CONFIG_PATH` 指向)覆盖。代码零改动,DB 里的文件 URL 零改动,上线零配置改动**。

| 触点 | Windows(开发,external.yml 覆盖) | Linux(生产,application.yml 基线) | 谁负责 |
|------|-------------------------------------|------------------------------|--------|
| 上传根目录 `file.upload-dir` | 本机路径(旧机器 `D:/WorkSpace/ihomy/uploads`,setup.ps1 新装机器为仓库 `data/uploads`) | `/opt/ihomy/uploads` | external.yml 覆盖,无需手动改 |
| DB 里的文件 URL(`/files/...`) | 相对 URL 与物理根解耦 | 不变,原样用 | 无需动作 |
| 存储设备 `sys_storage_device.root_path`(DB 数据) | 配的 Windows 盘路径 | 需改成 Linux 路径(如 `/mnt/nas/photo`) | 上线后在存储管理页重新添加/编辑设备,或 SQL UPDATE |
| 日志路径 | 本机路径(旧机器 `D:\WorkSpace\ihomy\logs`,新装机器为仓库 `data\logs`;external.yml 覆盖) | `/opt/ihomy/logs/{access,server,thirdparty}/`(三类分流,按天滚动) | external.yml 覆盖,无需手动改 |
| Nginx `/files/` | 开发由 vite 代理到 8080(同样走后端鉴权) | `proxy_pass http://127.0.0.1:8080/api/files/`(读取鉴权在后端,勿改回 alias 直出) | 部署时 nginx 配置(一次性) |

代码侧已验证平台无关,无需改动:`Paths.get`/`Files` 全平台自适应;上传文件名的清洗正则兼容 UTF-8 中文;
同步去重键 `source_path` 与防遍历校验(`resolveSafe`)均反斜杠归一,Win/Linux 行为一致。

**上线迁移步骤**:
1. 上传目录迁移:`rsync -a <开发机上传目录>/ /opt/ihomy/uploads/`(DB 的 URL 不用改);
2. 重新添加家庭存储设备(Linux 侧根路径),旧设备记录可删;
3. 验证:`/files/upload/...`、`/files/pictures/...`、`/files/music/...`、`/files/videos/...` 均可访问。

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
      MYSQL_ROOT_PASSWORD: <容器root密码,仅初始化管理用>
      MYSQL_DATABASE: ihomy
      LANG: C.UTF-8
    command: --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci
    # 注：schema.sql 挂载到初始化目录，容器首次启动会自动执行，
    # 其中包含创建应用账号 ihomy 并授权的语句。容器 root 仅用于管理。
    volumes:
      - ./backend/src/main/resources/schema.sql:/docker-entrypoint-initdb.d/schema.sql
      - mysql-data:/var/lib/mysql
    ports:
      - "6306:3306"      # 容器内仍是 3306,宿主机映射 6306 与生产一致
    restart: unless-stopped

  redis:
    image: redis:7-alpine
    container_name: ihomy-redis
    ports:
      - "127.0.0.1:6379:6379"   # 只绑回环(V10.6);密码与 external.yml 的 spring.data.redis.password 同值
    command: redis-server --requirepass <Redis密码>
    restart: unless-stopped

  backend:
    image: eclipse-temurin:21-jre
    container_name: ihomy-backend
    working_dir: /app
    environment:
      LANG: C.UTF-8
      IHOMY_CONFIG_PATH: /app/config/external.yml
    volumes:
      - ./backend/target/ihomy-backend.jar:/app/app.jar
      - ./config/external.yml:/app/config/external.yml
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
      - ./uploads:/opt/ihomy/uploads
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
> 容器化部署时，数据库/Redis 地址、上传目录经 **external.yml 覆盖**为容器内视角（host=`mysql`/`redis`，upload-dir=`/app/uploads`）。应用连接账号仍用 `ihomy`（schema.sql 开发安全版内置开发固定密码；**生产环境初始化后必须 `ALTER USER` 改强密码**，新密码经 external.yml 注入；该账号对 `ihomy` 库有 DML 权限）。
>
> **nginx.conf**：Docker Compose 的 `./nginx.conf` 需包含与第五节相同的 `location /files/`（**反代** `proxy_pass http://127.0.0.1:8080/api/files/;`，别写成 alias 直出，见踩坑速查 §7.8）、`location /api/ws`（WebSocket 反代）、`client_max_body_size 500m` 等配置，反代目标改为 `http://backend:8080`（容器服务名）。
>
> 容器化时 backend 需要在 `command` 中带上 JVM 调优参数（2GB 内存方案）：`command: java -Xms256m -Xmx384m -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=192m -XX:+UseSerialGC -Xss512k -jar app.jar`。
