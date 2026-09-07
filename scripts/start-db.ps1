<#
.SYNOPSIS
  使用 Docker Compose 一键启动 MySQL + Redis（Windows 本地开发）
.DESCRIPTION
  读取仓库根目录 docker-compose.yml 启动 ihomy-mysql / ihomy-redis，端口 6306/6379（与生产一致）。
  首次启动（空数据卷）由 MySQL 容器自动导入 schema.sql（建库建表+种子）。
  检测到旧版 docker run 创建的同名容器（无 compose 标签）会自动迁移，数据卷 ihomy-mysql-data 保留。
  幂等：每次执行都会把 ihomy 账号密码对齐到外挂配置 external.yml 的 spring.datasource.password。
.EXAMPLE
  .\scripts\start-db.ps1
#>
[CmdletBinding()]
$ErrorActionPreference = 'Continue'
$Root = Split-Path -Parent $PSScriptRoot
$ComposeFile = Join-Path $Root 'docker-compose.yml'

function Write-Step($msg) { Write-Host "`n==> $msg" -ForegroundColor Cyan }
function Write-Ok($msg)   { Write-Host "    [OK] $msg" -ForegroundColor Green }
function Write-Warn($msg) { Write-Host "    [!!] $msg" -ForegroundColor Yellow }
function Die($msg)        { Write-Host "    [ERR] $msg" -ForegroundColor Red; exit 1 }

# ---------- 外挂配置解析（优先级：环境变量 IHOMY_CONFIG_PATH > 仓库内 config\external.yml > 旧版 D:\WorkSpace 路径） ----------
if ($env:IHOMY_CONFIG_PATH -and (Test-Path $env:IHOMY_CONFIG_PATH)) {
  $ExternalConfig = $env:IHOMY_CONFIG_PATH
} elseif (Test-Path (Join-Path $Root 'config\external.yml')) {
  $ExternalConfig = Join-Path $Root 'config\external.yml'
} elseif (Test-Path 'D:\WorkSpace\ihomy\config\external.yml') {
  $ExternalConfig = 'D:\WorkSpace\ihomy\config\external.yml'
  Write-Warn "使用旧版配置 $ExternalConfig，建议运行 .\scripts\setup.ps1 迁移到仓库内 config\external.yml"
} else {
  $ExternalConfig = $null
}

# ---------- Docker 检查 ----------
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
  Die '未安装 Docker，请先安装 Docker Desktop: https://www.docker.com/products/docker-desktop/'
}
docker info *> $null
if ($LASTEXITCODE -ne 0) { Die 'Docker Desktop 未运行，请先启动后重试' }

# ---------- 启动（含旧容器自动迁移） ----------
# 先尝试 compose up；若因同名容器冲突失败（旧版 docker run 创建的容器无 compose 标签），
# 删除旧容器后重建（数据卷 ihomy-mysql-data 保留，数据不丢）。
# 不用 docker inspect --format 查标签预判：PS 5.1 向原生命令传参时会吃掉模板里的内嵌双引号，探测必然失败
Write-Step '启动 MySQL + Redis (docker compose)'
docker compose -f $ComposeFile up -d 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
  Write-Warn '检测到同名旧容器（旧版 docker run 创建），自动迁移到 compose 管理（数据卷保留）...'
  foreach ($name in @('ihomy-mysql','ihomy-redis')) { docker rm -f $name 2>$null | Out-Null }
  docker compose -f $ComposeFile up -d
  if ($LASTEXITCODE -ne 0) { Die 'docker compose 启动失败，请检查上方输出' }
}

# ---------- 等待 MySQL 就绪 ----------
# 用 TCP（-h 127.0.0.1）而非 socket：首次启动导入 schema.sql 期间的临时服务器只开 socket，
# socket ping 会在导入完成前误报就绪；TCP 就绪 = 正式服务器已起、schema 已导入
Write-Step '等待 MySQL 就绪（首次启动导入 schema.sql 约需 1-2 分钟）...'
$ok = $false
for ($i = 0; $i -lt 60; $i++) {
  # ⚠ -h127.0.0.1 必须带引号:PS 5.1 参数模式会把不带引号的 -h127.0.0.1 在点号处拆词,mysqladmin 实际收到主机 "127"
  $out = docker exec ihomy-mysql mysqladmin ping '-h127.0.0.1' -uroot -proot 2>$null
  if ("$out" -match 'alive') { $ok = $true; break }
  Start-Sleep -Seconds 2
}
if ($ok) { Write-Ok 'MySQL 就绪' }
else { Die 'MySQL 未在 120s 内就绪，请查看 docker logs ihomy-mysql' }

# ---------- 应用账号密码与 external.yml 对齐 ----------
# schema.sql 的 ihomy 账号密码为开发固定值；若 external.yml 配了其他密码则在此对齐（幂等，每次启动都执行）
if ($ExternalConfig) {
  $raw = Get-Content $ExternalConfig -Raw -Encoding UTF8
  $m = [regex]::Match($raw, '(?s)datasource:\s*password:\s*([A-Za-z0-9_@#$%^&*+!.-]+)')
  if ($m.Success -and $m.Groups[1].Value -notmatch '^CHANGE_ME') {
    $appPwd = $m.Groups[1].Value
    # 纯 ASCII SQL，直接 docker exec 执行（符合编码规范）
    docker exec ihomy-mysql mysql -uroot -proot -e "ALTER USER 'ihomy'@'localhost' IDENTIFIED BY '$appPwd'; ALTER USER 'ihomy'@'%' IDENTIFIED BY '$appPwd'; FLUSH PRIVILEGES;" 2>$null
    if ($LASTEXITCODE -eq 0) { Write-Ok "应用账号 ihomy 密码已与 $ExternalConfig 对齐" }
    else { Write-Warn '应用账号密码对齐失败，请手动 docker exec ALTER USER' }
  } else {
    Write-Warn '未从 external.yml 解析到应用密码，请确认 spring.datasource.password 已配置真实值'
  }
} else {
  Write-Warn '未找到外挂配置 external.yml（请先运行 .\scripts\setup.ps1）；跳过密码对齐，新库沿用 schema.sql 内置开发密码'
}

Write-Host "`n容器已启动：" -ForegroundColor Green
Write-Host '  MySQL  ihomy-mysql  localhost:6306  (容器内 3306，root 密码 root 仅本机管理用)'
Write-Host '  Redis  ihomy-redis  localhost:6379'
Write-Host '  数据库: ihomy（应用账号 ihomy，密码与 external.yml 对齐）'
Write-Host "`n停止: docker compose down        重置数据库(删数据): docker compose down -v"
