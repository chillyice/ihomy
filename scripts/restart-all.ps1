<#
.SYNOPSIS
  ihomy 重新构建并重启前后端(Windows)。
.DESCRIPTION
  一条命令走完「停旧 → 重建 → 起新」：
    1. 先停服务：检测占用 8080(后端) / 5173(开发服务器) / 4173(预览服务器) 的进程并结束。
       运行中的 java 会锁住 jar 与日志文件，不先停会让 mvnw clean package 失败，
       旧进程不释放端口也会让新进程起不来。
    2. 构建：后端 mvnw clean package -DskipTests；前端 npm run build。
    3. 启动：后端 java -jar target/ihomy-backend.jar(带外挂配置 IHOMY_CONFIG_PATH)，
       前端 vite(默认 preview 托管 frontend\dist，-FrontendMode dev 则起开发服务器)，
       并轮询到后端端口就绪才继续。
  供电源：咔哒(Kada)快捷键、终端、ZCode 均可直接调用。
.PARAMETER FrontendMode
  preview(默认) 起 vite preview 服务已构建的 frontend\dist(端口 4173)；
  dev 起 vite 开发服务器(端口 5173，热重载)。
.PARAMETER SkipBuild
  跳过构建，只做「停旧 + 起新」(想立刻重启时用，秒级完成)。
.PARAMETER NoBrowser
  不自动打开浏览器。
.EXAMPLE
  .\scripts\restart-all.ps1
  .\scripts\restart-all.ps1 -FrontendMode dev -NoBrowser
  .\scripts\restart-all.ps1 -SkipBuild
#>
[CmdletBinding()]
param(
  [ValidateSet('preview', 'dev')]
  [string]$FrontendMode = 'preview',
  [switch]$SkipBuild,
  [switch]$NoBrowser
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$Backend = Join-Path $Root 'backend'
$Frontend = Join-Path $Root 'frontend'
$BackendPort = 8080
$FrontendPort = if ($FrontendMode -eq 'dev') { 5173 } else { 4173 }
$OtherFrontendPort = if ($FrontendMode -eq 'dev') { 4173 } else { 5173 }

function Write-Step($msg) { Write-Host "`n==> $msg" -ForegroundColor Cyan }
function Write-Ok($msg)   { Write-Host "    [OK] $msg" -ForegroundColor Green }
function Write-Warn($msg) { Write-Host "    [!!] $msg" -ForegroundColor Yellow }
function Die($msg)        { Write-Host "`n[ERR] $msg" -ForegroundColor Red; exit 1 }

# ---------- 外挂配置解析(与 start-all.ps1 同口径) ----------
if ($env:IHOMY_CONFIG_PATH -and (Test-Path $env:IHOMY_CONFIG_PATH)) {
  $ExternalConfig = $env:IHOMY_CONFIG_PATH
} elseif (Test-Path (Join-Path $Root 'config\external.yml')) {
  $ExternalConfig = Join-Path $Root 'config\external.yml'
} elseif (Test-Path 'D:\WorkSpace\ihomy\config\external.yml') {
  $ExternalConfig = 'D:\WorkSpace\ihomy\config\external.yml'
  Write-Warn "使用旧版配置 $ExternalConfig，建议运行 .\scripts\setup.ps1 迁移到仓库内 config\external.yml"
} else {
  Die '未找到外挂配置 external.yml，请先运行 .\scripts\setup.ps1'
}

Write-Host "ihomy 重建重启  $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" -ForegroundColor White
Write-Ok "工作目录: $Root"
Write-Ok "外挂配置: $ExternalConfig"

# ---------- 停旧服务 ----------
# 只结束「确实是本项目的」进程：按端口反查属主，且进程名符合预期(java / node)，
# 或命令行里带本仓库路径 / ihomy-backend.jar。避免误杀别的 Java/Node 程序。
function Get-PortOwnerPid([int]$Port) {
  $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
          Select-Object -First 1
  if ($conn) { return [int]$conn.OwningProcess }
  return $null
}

function Get-ProcessNameSafe([int]$ProcId) {
  # 注意：参数名不能叫 $Pid——那是 PowerShell 只读自动变量，绑定时会抛「无法覆盖变量」
  $p = Get-Process -Id $ProcId -ErrorAction SilentlyContinue
  if ($p) { return $p.ProcessName }
  return ''
}

function Stop-PortOwner([int]$Port, [string[]]$ExpectNames, [string]$Label, [switch]$Required) {
  $pid2 = Get-PortOwnerPid $Port
  if (-not $pid2) { Write-Ok "$Label 未在运行(端口 $Port 空闲)"; return }

  $name = Get-ProcessNameSafe $pid2
  if ($ExpectNames -notcontains $name) {
    $cmdline = (Get-CimInstance Win32_Process -Filter "ProcessId=$pid2" -ErrorAction SilentlyContinue).CommandLine
    if ($Required) {
      Die "端口 $Port 被 $name (PID $pid2) 占用，不像 ihomy 的进程，已中止以免误杀。请自行确认：`n      $cmdline"
    }
    Write-Warn "$Label 端口 $Port 被 $name (PID $pid2) 占用，非本项目进程，跳过不杀"
    return
  }

  Write-Host "    停止 $Label ($name, PID $pid2)..." -ForegroundColor Gray
  & taskkill /F /T /PID $pid2 *> $null

  # 等端口真正释放，否则紧接着的构建/启动仍会撞锁
  for ($i = 0; $i -lt 20; $i++) {
    if (-not (Get-PortOwnerPid $Port)) { Write-Ok "$Label 已停止，端口 $Port 已释放"; return }
    Start-Sleep -Milliseconds 500
  }
  Die "$Label (PID $pid2) 已发出结束指令但端口 $Port 仍被占用，请手动处理后再重试"
}

Write-Step '停止旧服务'
Stop-PortOwner $BackendPort @('java', 'javaw') '后端' -Required
Stop-PortOwner $FrontendPort @('node', 'powershell', 'cmd') '前端'
if ($OtherFrontendPort -ne $FrontendPort) {
  Stop-PortOwner $OtherFrontendPort @('node', 'powershell', 'cmd') '前端(另一模式)' 
}

# 端口都空但仍残留的本项目后端(启动中被杀、端口尚未绑定等)：按命令行精确匹配再补一刀
$stragglers = Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" -ErrorAction SilentlyContinue |
  Where-Object { $_.CommandLine -and ($_.CommandLine -like "*ihomy-backend.jar*" -or $_.CommandLine -like "*$Root*") }
foreach ($p in $stragglers) {
  Write-Warn "补杀残留后端进程 PID $($p.ProcessId)"
  & taskkill /F /T /PID $p.ProcessId *> $null
}

# ---------- 构建 ----------
if ($SkipBuild) {
  Write-Step '跳过构建(-SkipBuild)'
} else {
  if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = (Get-Item (Get-Command java).Source).Directory.Parent.FullName
    Write-Warn "JAVA_HOME 未设置，临时设为 $env:JAVA_HOME"
  }

  Write-Step '构建后端 (mvnw clean package)'
  # mvnw.cmd 在「当前目录」找 .mvn/wrapper（仓库根没有 .mvn，wrapper 在 backend 下），
  # 故必须切到 backend 再调，否则报「找不到 maven-wrapper.properties / 主类 MavenWrapperMain」
  Push-Location -LiteralPath $Backend
  try {
    & '.\mvnw.cmd' -B clean package -DskipTests
    if ($LASTEXITCODE -ne 0) { Die '后端构建失败，已中止(后端未启动)' }
  } finally { Pop-Location }
  Write-Ok '后端构建完成'

  Write-Step '构建前端 (npm run build)'
  if (-not (Test-Path (Join-Path $Frontend 'node_modules'))) {
    Write-Host '    首次运行，安装依赖...' -ForegroundColor Gray
    & npm install --prefix $Frontend --no-audit --no-fund
    if ($LASTEXITCODE -ne 0) { Die '前端依赖安装失败' }
  }
  & npm run build --prefix $Frontend
  if ($LASTEXITCODE -ne 0) { Die '前端构建失败，已中止(后端未启动)' }
  Write-Ok '前端构建完成'
}

# ---------- 启动后端 ----------
Write-Step '启动后端 (Spring Boot)'
$jar = Join-Path $Backend 'target\ihomy-backend.jar'
if (-not (Test-Path $jar)) { Die "未找到后端 jar：$jar（先去掉 -SkipBuild 构建一次）" }

$dbUp = docker ps --filter 'name=ihomy-mysql' --filter 'status=running' --format '{{.Names}}' 2>$null
if ("$dbUp" -eq 'ihomy-mysql') { Write-Ok '数据库容器 ihomy-mysql 运行中' }
else { Write-Warn 'ihomy-mysql 未运行，先执行 .\scripts\start-db.ps1 再重启' }

# 用完整路径启动，避免 javapath launcher 与 JDK 双实例分流 8080 请求(偶发 401/404/500)
$javaExe = if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
  Join-Path $env:JAVA_HOME 'bin\java.exe'
} else { (Get-Command java).Source }

$env:IHOMY_CONFIG_PATH = $ExternalConfig
Start-Process -FilePath $javaExe -ArgumentList @('-jar', $jar) -WorkingDirectory $Backend
Write-Ok "已启动 java -jar $($jar | Split-Path -Leaf)，端口 $BackendPort"

# 等端口就绪(最多 90 秒)；就绪后再打一次 HTTP 冒烟，只看能不能连上，不判业务码
$ready = $false
for ($i = 0; $i -lt 90; $i++) {
  try {
    $client = New-Object System.Net.Sockets.TcpClient
    $client.Connect('127.0.0.1', $BackendPort)
    $client.Close()
    $ready = $true
    break
  } catch { Start-Sleep -Seconds 1 }
}
if (-not $ready) { Die "后端 $BackendPort 端口 90 秒内未就绪，请看后端窗口/日志(日志目录见 external.yml 的 logging.file.path)" }
Write-Ok "后端端口就绪(约 $i 秒)"

try {
  $smoke = Invoke-WebRequest -Uri "http://localhost:$BackendPort/api/auth/captcha" -UseBasicParsing -TimeoutSec 5
  Write-Ok "接口冒烟 GET /api/auth/captcha → HTTP $($smoke.StatusCode)"
} catch {
  Write-Warn "接口冒烟未通过：$($_.Exception.Message)（端口已监听，可能是验证码依赖的 Redis 未就绪）"
}

# ---------- 启动前端 ----------
Write-Step "启动前端 (vite $FrontendMode)"
if ($FrontendMode -eq 'dev') {
  Start-Process -FilePath 'powershell' -ArgumentList '-NoProfile', '-NoExit', '-Command',
    "Set-Location '$Frontend'; npm run dev"
  Write-Ok "前端已启动(vite dev)，端口 $FrontendPort"
} else {
  Start-Process -FilePath 'powershell' -ArgumentList '-NoProfile', '-NoExit', '-Command',
    "Set-Location '$Frontend'; npx vite preview --host"
  Write-Ok "前端已启动(vite preview 托管 dist)，端口 $FrontendPort"
}

$feReady = $false
for ($i = 0; $i -lt 30; $i++) {
  try {
    $c = New-Object System.Net.Sockets.TcpClient
    $c.Connect('127.0.0.1', $FrontendPort)
    $c.Close()
    $feReady = $true
    break
  } catch { Start-Sleep -Seconds 1 }
}
if ($feReady) { Write-Ok "前端端口就绪(约 $i 秒)" } else { Write-Warn "前端 $FrontendPort 端口 30 秒内未就绪，请看前端窗口" }

# ---------- 打开浏览器 ----------
if (-not $NoBrowser) {
  Start-Process "http://localhost:$FrontendPort"
  Write-Ok "已打开 http://localhost:$FrontendPort"
}

Write-Host "`n重启完成：前端 http://localhost:$FrontendPort   后端接口 http://localhost:$BackendPort/api" -ForegroundColor Cyan
exit 0
