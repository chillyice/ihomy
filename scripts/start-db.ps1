<#
.SYNOPSIS
  使用 Docker 一键启动 MySQL + Redis（Windows）
.DESCRIPTION
  需要 Docker Desktop。启动两个容器：ihomy-mysql / ihomy-redis，
  并自动导入建表脚本 schema.sql。
#>
[CmdletBinding()]
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$Schema = Join-Path $Root 'backend\src\main\resources\schema.sql'

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
  Write-Host '未安装 Docker，请先安装 Docker Desktop: https://www.docker.com/products/docker-desktop/' -ForegroundColor Red
  exit 1
}

Write-Host '==> 启动 MySQL 8 容器...' -ForegroundColor Cyan
# MySQL 配置目录(含 my.cnf,端口 6306 + 内存调优)
$MysqlConf = "C:\Users\chill\OneDrive\WorkStation\config\MySQL\conf"
docker run -d --name ihomy-mysql `
  -e MYSQL_ROOT_PASSWORD=root `
  -p 38654:3306 `
  -v "${Schema}:/docker-entrypoint-initdb.d/schema.sql" `
  -v "${MysqlConf}:/etc/mysql/conf.d" `
  mysql:8.0 `
  2>$null | Out-Null

if ($LASTEXITCODE -ne 0) {
  Write-Host '    ihomy-mysql 可能已存在，尝试启动...' -ForegroundColor Yellow
  docker start ihomy-mysql | Out-Null
}

Write-Host '==> 启动 Redis 容器...' -ForegroundColor Cyan
docker run -d --name ihomy-redis -p 18469:6379 redis:7-alpine 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) {
  Write-Host '    ihomy-redis 可能已存在，尝试启动...' -ForegroundColor Yellow
  docker start ihomy-redis | Out-Null
}

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
Write-Host '  MySQL  ihomy-mysql  localhost:38654'
Write-Host '    容器 root 密码: root （仅管理用）'
Write-Host '    应用连接账号:   ihomy / Ihomy@2026 （schema.sql 已自动创建，仅 DML 权限）'
Write-Host '    数据库:         ihomy'
Write-Host '  Redis  ihomy-redis  localhost:18469'
Write-Host "`n停止: docker stop ihomy-mysql ihomy-redis"
Write-Host "删除: docker rm -f ihomy-mysql ihomy-redis"
