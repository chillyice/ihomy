<#
.SYNOPSIS
  使用 Docker 一键启动 MySQL + Redis（Windows 本地开发）
.DESCRIPTION
  启动两个容器：ihomy-mysql / ihomy-redis，并自动导入建表脚本 schema.sql。
  端口：MySQL 6306、Redis 6379（与生产环境一致）。
  不挂载 my.cnf（Windows bind mount 权限 777 被 MySQL 拒绝），改用命令参数调优。
.EXAMPLE
  .\scripts\start-db.ps1
#>
[CmdletBinding()]
$ErrorActionPreference = 'Continue'
$Root = Split-Path -Parent $PSScriptRoot
$Schema = Join-Path $Root 'backend\src\main\resources\schema.sql'

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
  Write-Host '未安装 Docker，请先安装 Docker Desktop: https://www.docker.com/products/docker-desktop/' -ForegroundColor Red
  exit 1
}

# ---------- MySQL ----------
Write-Host '==> 启动 MySQL 8 容器...' -ForegroundColor Cyan
# 容器内用默认 3306 端口,宿主映射 6306(与生产一致);命令参数替代 my.cnf 调优(避开 Windows bind mount 权限问题)
docker run -d --name ihomy-mysql `
  -e MYSQL_ROOT_PASSWORD=root `
  -p 6306:3306 `
  -v "${Schema}:/docker-entrypoint-initdb.d/schema.sql" `
  -v ihomy-mysql-data:/var/lib/mysql `
  mysql:8.4.10 `
  --character-set-server=utf8mb4 `
  --collation-server=utf8mb4_unicode_ci `
  --default-time-zone=+08:00 `
  --performance_schema=OFF `
  --max_connections=30 `
  2>&1 | Out-Null

if ($LASTEXITCODE -ne 0) {
  Write-Host '    ihomy-mysql 可能已存在，尝试启动...' -ForegroundColor Yellow
  docker start ihomy-mysql | Out-Null
}

# ---------- Redis ----------
Write-Host '==> 启动 Redis 容器...' -ForegroundColor Cyan
docker run -d --name ihomy-redis -p 6379:6379 redis:latest 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
  Write-Host '    ihomy-redis 可能已存在，尝试启动...' -ForegroundColor Yellow
  docker start ihomy-redis | Out-Null
}

# ---------- 等待 MySQL 就绪 ----------
Write-Host '==> 等待 MySQL 就绪...' -ForegroundColor Cyan
$ok = $false
for ($i = 0; $i -lt 30; $i++) {
  $out = docker exec ihomy-mysql mysqladmin ping -uroot -proot 2>$null
  if ($out -match 'alive') { $ok = $true; break }
  Start-Sleep -Seconds 2
}
if ($ok) { Write-Host '    [OK] MySQL 就绪' -ForegroundColor Green }
else { Write-Host '    [!!] MySQL 未在 60s 内就绪，请检查 docker logs ihomy-mysql' -ForegroundColor Yellow }

Write-Host "`n容器已启动：" -ForegroundColor Green
Write-Host '  MySQL  ihomy-mysql  localhost:6306  (容器内 3306)'
Write-Host '    容器 root 密码: root （仅管理用）'
Write-Host '    应用连接账号:   ihomy / Ihomy@2026 （schema.sql 已自动创建，仅 DML 权限）'
Write-Host '    数据库:         ihomy'
Write-Host '  Redis  ihomy-redis  localhost:6379  (容器内 6379)'
Write-Host "`n停止: docker stop ihomy-mysql ihomy-redis"
Write-Host "删除: docker rm -f ihomy-mysql ihomy-redis"
Write-Host "删数据: docker volume rm ihomy-mysql-data"
