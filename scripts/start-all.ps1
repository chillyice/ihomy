<#
.SYNOPSIS
  ihomy Windows 一键启动脚本
.DESCRIPTION
  自动检查环境，依次启动后端(Spring Boot)与前端(Vite)，并打开浏览器。
  开发/生产差异配置通过外挂文件 external.yml 管理(环境变量 IHOMY_CONFIG_PATH 指向):
    D:\WorkSpace\ihomy\config\external.yml (Windows 开发)
    /opt/ihomy/config/external.yml (Linux 生产)
  不再用 application-dev.yml profile,所有差异项(密码/路径/验证码/天气凭证)走外挂文件。
  可选参数：
    -BackendOnly   仅启动后端
    -FrontendOnly  仅启动前端
    -NoBrowser     不自动打开浏览器
    -Build         先构建再启动（生产模式）
.EXAMPLE
  .\scripts\start-all.ps1
  .\scripts\start-all.ps1 -BackendOnly
  .\scripts\start-all.ps1 -Build
#>
[CmdletBinding()]
param(
  [switch]$BackendOnly,
  [switch]$FrontendOnly,
  [switch]$NoBrowser,
  [switch]$Build
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$Backend = Join-Path $Root 'backend'
$Frontend = Join-Path $Root 'frontend'

# 外挂配置文件路径(Windows 开发环境)
$ExternalConfig = Join-Path $env:USERPROFILE '..\..\..\WorkSpace\ihomy\config\external.yml'
# 兜底:项目同级目录下的 config
$AltConfig = 'D:\WorkSpace\ihomy\config\external.yml'

function Write-Step($msg) { Write-Host "`n==> $msg" -ForegroundColor Cyan }
function Write-Ok($msg)   { Write-Host "    [OK] $msg" -ForegroundColor Green }
function Write-Warn($msg) { Write-Host "    [!!] $msg" -ForegroundColor Yellow }
function Die($msg)        { Write-Host "    [ERR] $msg" -ForegroundColor Red; exit 1 }

# ---------- 环境检查 ----------
Write-Step '环境检查'

# JDK
$java = (Get-Command java -ErrorAction SilentlyContinue)
if (-not $java) { Die '未找到 java，请安装 JDK 17/21 并配置 JAVA_HOME' }
$javaVer = ((cmd /c "java -version 2>&1") | Select-Object -First 1)
Write-Ok "Java: $javaVer"

# Node
$node = (Get-Command node -ErrorAction SilentlyContinue)
if (-not $node) { Die '未找到 node，请安装 Node.js 18+' }
Write-Ok "Node: $(& node -v)"

# npm
$npm = (Get-Command npm -ErrorAction SilentlyContinue)
if (-not $npm) { Die '未找到 npm' }
Write-Ok "npm: $(& npm -v)"

# MySQL（仅提示，不强校验）
$mysql = (Get-Command mysql -ErrorAction SilentlyContinue)
if ($mysql) { Write-Ok "mysql 客户端: $(& mysql --version)" }
else { Write-Warn '未在 PATH 找到 mysql 客户端，请确保 MySQL 服务已运行' }

# Redis（仅提示，不强校验）
$redis = (Get-Command redis-cli -ErrorAction SilentlyContinue)
if ($redis) { Write-Ok "redis-cli: $(& redis-cli --version)" }
else { Write-Warn '未在 PATH 找到 redis-cli，请确保 Redis 服务已运行' }

# JAVA_HOME（mvnw 需要）
if (-not $env:JAVA_HOME) {
  $env:JAVA_HOME = (Get-Item (Get-Command java).Source).Directory.Parent.FullName
  Write-Warn "JAVA_HOME 未设置，临时设为 $env:JAVA_HOME"
}

# ---------- 后端 ----------
if (-not $FrontendOnly) {
  Write-Step '后端启动 (Spring Boot)'

  # 建库建表提示
  $schema = Join-Path $Backend 'src\main\resources\schema.sql'
  if (Test-Path $schema) {
    Write-Host "    若首次运行，请先建库建表："
    Write-Host "      mysql -uroot -p --default-character-set=utf8mb4 < `"$schema`""
  }

  if ($Build) {
    Write-Host '    [Build] 编译打包...'
    & (Join-Path $Backend 'mvnw.cmd') -B -q -f $Backend clean package -DskipTests
    if ($LASTEXITCODE -ne 0) { Die '后端构建失败' }
    Write-Ok '后端构建完成'
    $jar = Get-ChildItem (Join-Path $Backend 'target') -Filter 'ihomy-backend.jar' -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $jar) { Die '未找到后端 jar' }
    # 设环境变量 IHOMY_CONFIG_PATH 指向外挂配置文件
    $env:IHOMY_CONFIG_PATH = $AltConfig
    Start-Process -FilePath 'java' -ArgumentList "-jar", $jar.FullName -WorkingDirectory $Backend
    Write-Ok "后端已启动（java -jar $($jar.Name)），端口 8080，外挂配置: $AltConfig"
  } else {
    $env:IHOMY_CONFIG_PATH = $AltConfig
    Start-Process -FilePath 'powershell' -ArgumentList '-NoProfile','-NoExit','-Command',
      "`$env:IHOMY_CONFIG_PATH='$AltConfig'; Set-Location '$Backend'; & '.\mvnw.cmd' spring-boot:run"
    Write-Ok "后端已在新窗口启动（mvnw spring-boot:run），端口 8080，外挂配置: $AltConfig"
  }
}

# ---------- 前端 ----------
if (-not $BackendOnly) {
  Write-Step '前端启动 (Vite)'
  if (-not (Test-Path (Join-Path $Frontend 'node_modules'))) {
    Write-Host '    首次运行，安装依赖...'
    & npm install --prefix $Frontend --no-audit --no-fund
    if ($LASTEXITCODE -ne 0) { Die '前端依赖安装失败' }
    Write-Ok '依赖安装完成'
  }

  if ($Build) {
    Write-Host '    [Build] 生产构建...'
    & npm run build --prefix $Frontend
    if ($LASTEXITCODE -ne 0) { Die '前端构建失败' }
    Write-Ok '前端构建完成，产物在 frontend\dist（可用 Nginx 托管）'
    Start-Process -FilePath 'powershell' -ArgumentList '-NoProfile','-NoExit','-Command',
      "Set-Location '$Frontend'; npx vite preview --host"
    Write-Ok '前端预览已启动（vite preview）'
  } else {
    Start-Process -FilePath 'powershell' -ArgumentList '-NoProfile','-NoExit','-Command',
      "Set-Location '$Frontend'; npm run dev"
    Write-Ok '前端已在新窗口启动（npm run dev），端口 5173'
  }
}

# ---------- 打开浏览器 ----------
if (-not $NoBrowser) {
  Write-Step '打开浏览器'
  Start-Sleep -Seconds 3
  Start-Process 'http://localhost:5173'
  Write-Ok '已打开 http://localhost:5173'
}

Write-Host "`n启动完成。前端: http://localhost:5173  后端接口: http://localhost:8080/api" -ForegroundColor Cyan
Write-Host '默认账号: admin / admin123' -ForegroundColor Cyan
