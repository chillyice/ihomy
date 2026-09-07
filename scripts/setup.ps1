<#
.SYNOPSIS
  ihomy 新成员一键环境初始化（一台机器只需运行一次，幂等可重复执行）
.DESCRIPTION
  1. 前置软件检查（JDK 21 / Node 18+ / Docker Desktop），缺失时给出 winget 安装命令
  2. 生成外挂配置 config\external.yml（仓库内，已 gitignore）：
     已存在则跳过；检测到旧版 D:\WorkSpace 配置则自动沿用；全新生成时 DB 密码用
     schema.sql 内置开发密码、JWT 密钥本机随机生成、上传/日志目录指向仓库内 data\
  3. 启动 MySQL+Redis（docker compose，首次启动自动导入 schema.sql 建库建表+种子）
  4. 校验数据库（表数量 + admin 账号）
  5. 前端依赖安装 + 生成 frontend\.env.development.local（登录页演示账号回填）
  完成后日常开发只需双击 start.bat。
  admin/ops 初始密码见本地 docs\新人上手指南.md（不入 git，由维护者提供）。
.EXAMPLE
  .\scripts\setup.ps1
#>
[CmdletBinding()]
$ErrorActionPreference = 'Continue'
$Root = Split-Path -Parent $PSScriptRoot
$Backend = Join-Path $Root 'backend'
$Frontend = Join-Path $Root 'frontend'
$RepoConfig = Join-Path $Root 'config\external.yml'

# schema.sql 内置的本机开发 DB 密码（与建库脚本保持一致，勿单独改动）
$DevDbPassword = 'ihomy-dev-2026!'

function Write-Step($msg) { Write-Host "`n==> $msg" -ForegroundColor Cyan }
function Write-Ok($msg)   { Write-Host "    [OK] $msg" -ForegroundColor Green }
function Write-Warn($msg) { Write-Host "    [!!] $msg" -ForegroundColor Yellow }
function Die($msg)        { Write-Host "    [ERR] $msg" -ForegroundColor Red; exit 1 }

# ---------- 步骤 1：前置软件检查 ----------
Write-Step '步骤 1/5 前置软件检查'

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
  Write-Host '    [ERR] 未找到 java。安装 JDK 21（管理员 PowerShell）：' -ForegroundColor Red
  Write-Host '      winget install --id EclipseAdoptium.Temurin.21.JDK -e'
  Die '安装后重开终端再运行本脚本'
}
$javaVer = ((cmd /c "java -version 2>&1") | Select-Object -First 1)
if ("$javaVer" -match 'version "(\d+)') {
  $javaMajor = [int]$Matches[1]
  if ($javaMajor -ne 21) { Write-Warn "JDK 主版本为 $javaMajor，项目基线是 21（虚拟线程），建议对齐" }
  else { Write-Ok "JDK: $javaVer" }
} else { Write-Warn "无法解析 JDK 版本： $javaVer" }
if (-not $env:JAVA_HOME) { Write-Warn 'JAVA_HOME 未设置（start-all.ps1 会临时推断；建议配置到系统环境变量）' }

if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
  Write-Host '    [ERR] 未找到 node。安装 Node.js 20 LTS（管理员 PowerShell）：' -ForegroundColor Red
  Write-Host '      winget install --id OpenJS.NodeJS.LTS -e'
  Die '安装后重开终端再运行本脚本'
}
$nodeVer = (& node -v)
if ("$nodeVer" -match 'v(\d+)') {
  $nodeMajor = [int]$Matches[1]
  if ($nodeMajor -lt 18) { Die "Node $nodeMajor 过旧，需要 18+（建议 20 LTS）" }
  Write-Ok "Node: $nodeVer"
} else { Write-Warn "无法解析 Node 版本： $nodeVer" }

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
  Write-Host '    [ERR] 未找到 docker。安装 Docker Desktop（管理员 PowerShell）：' -ForegroundColor Red
  Write-Host '      winget install --id Docker.DockerDesktop -e'
  Die '安装并启动 Docker Desktop 后重跑本脚本'
}
docker info *> $null
if ($LASTEXITCODE -ne 0) { Die 'Docker Desktop 未运行，请先启动（任务栏鲸鱼图标变绿）后重跑本脚本' }
Write-Ok "Docker: $(docker --version)"

# ---------- 步骤 2：外挂配置 config\external.yml ----------
Write-Step '步骤 2/5 生成外挂配置 config\external.yml'
if (Test-Path $RepoConfig) {
  Write-Ok "已存在，跳过： $RepoConfig"
} else {
  $legacy = 'D:\WorkSpace\ihomy\config\external.yml'
  if (Test-Path $legacy) {
    Copy-Item $legacy $RepoConfig
    Write-Ok "已沿用旧版配置（保留你的真实密码/路径）： $legacy -> $RepoConfig"
  } else {
    # 全新生成开发配置：DB 密码与 schema.sql 一致；JWT 密钥本机随机；目录指到仓库内 data\
    $chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789'
    $secret = -join (1..48 | ForEach-Object { $chars[(Get-Random -Maximum $chars.Length)] })
    $uploadDir = ((Join-Path $Root 'data\uploads') -replace '\\','/')
    $logDir = ((Join-Path $Root 'data\logs') -replace '\\','/')
    $yml = @"
# ihomy 开发外挂配置（由 scripts/setup.ps1 生成，已 gitignore，勿提交）
# 机制：环境变量 IHOMY_CONFIG_PATH 指向本文件，ExternalConfigLoader 以最高优先级加载
spring:
  datasource:
    password: $DevDbPassword
  data:
    redis:
      password:

# JWT 签名密钥（本机随机生成，仅开发用）
jwt:
  secret: $secret

# 文件上传/日志目录（仓库内 data\，首次使用自动创建）
file:
  upload-dir: $uploadDir
  url-prefix: /files
logging:
  file:
    path: $logDir

# 开发固定验证码，方便登录测试（生产必须留空）
app:
  captcha-fixed-code: qwer
"@
    # UTF-8 无 BOM 写入（避免 PS 5.1 默认编码带 BOM/GBK 问题）
    [System.IO.File]::WriteAllText($RepoConfig, $yml, (New-Object System.Text.UTF8Encoding($false)))
    Write-Ok "已生成： $RepoConfig"
  }
}

# ---------- 步骤 3：数据库（docker compose 起 MySQL+Redis，首次自动导入 schema.sql） ----------
Write-Step '步骤 3/5 启动 MySQL + Redis 并初始化数据库'
& (Join-Path $PSScriptRoot 'start-db.ps1')

# ---------- 步骤 4：数据库校验 ----------
Write-Step '步骤 4/5 校验数据库'
$tables = "$(docker exec ihomy-mysql mysql -uroot -proot -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='ihomy';" 2>$null)".Trim()
if (-not ($tables -match '^\d+$')) { Die '数据库校验失败，请查看 docker logs ihomy-mysql' }
if ([int]$tables -eq 0) {
  Die 'ihomy 库为空：schema.sql 未导入。请执行 docker compose down -v 重置数据卷后重跑本脚本'
} elseif ([int]$tables -lt 61) {
  Write-Warn "表数量 $tables 少于当前版本 61 张（旧版本数据卷），建议对照 schema.sql 增量补表"
} else {
  Write-Ok "表数量 $tables（预期 61）"
}
$admin = "$(docker exec ihomy-mysql mysql -uroot -proot -N -e "SELECT COUNT(*) FROM ihomy.sys_user WHERE username='admin';" 2>$null)".Trim()
if ($admin -eq '1') { Write-Ok 'admin 账号存在' }
else { Write-Warn "admin 账号异常（查询结果： $admin）" }

# ---------- 步骤 5：前端依赖与演示凭证 ----------
Write-Step '步骤 5/5 前端依赖与演示凭证'
if (-not (Test-Path (Join-Path $Frontend 'node_modules'))) {
  & npm install --prefix $Frontend --no-audit --no-fund
  if ($LASTEXITCODE -ne 0) { Die '前端依赖安装失败' }
  Write-Ok '前端依赖安装完成'
} else {
  Write-Ok 'node_modules 已存在，跳过'
}
$demoEnv = Join-Path $Frontend '.env.development.local'
if (-not (Test-Path $demoEnv)) {
  $envContent = "VITE_DEMO_EMAIL=demo@ihomy.local`nVITE_DEMO_PASSWORD=guest123`n"
  [System.IO.File]::WriteAllText($demoEnv, $envContent, (New-Object System.Text.UTF8Encoding($false)))
  Write-Ok "已生成 $demoEnv（登录页演示账号回填，仅 vite dev 加载）"
} else {
  Write-Ok "$demoEnv 已存在，跳过"
}

# ---------- 汇总 ----------
Write-Host ""
Write-Host '================================================' -ForegroundColor Cyan
Write-Host ' 环境初始化完成！' -ForegroundColor Green
Write-Host '================================================' -ForegroundColor Cyan
Write-Host ' 日常开发：双击 start.bat（前端 5173 / 后端 8080）'
Write-Host ' 前端: http://localhost:5173    接口文档: http://localhost:8080/api/doc.html'
Write-Host ' 账号: admin@ihomy.local（OWNER，初始密码见本地 docs\新人上手指南.md）'
Write-Host '       demo@ihomy.local / guest123（演示家庭，仅供体验）'
Write-Host ' 完整上手说明：docs\新人上手指南.md（本地文件，不入 git）'
Write-Host '================================================' -ForegroundColor Cyan
