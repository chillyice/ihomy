<#
.SYNOPSIS
  ihomy 重新构建并重启(Windows):重建前后端,只重启后端。
.DESCRIPTION
  一条命令走完「停旧 → 重建 → 起后端」:
    1. 停旧服务:结束占用 8080(后端) 的 java 进程;同时清理历史遗留的 vite dev/preview
       服务及其外壳窗口(5173/4173),重启结束后桌面上只留后端一个窗口。
    2. 构建:后端 mvnw clean package -DskipTests;前端 npm run build。
    3. 启动:后端 java -jar target/ihomy-backend.jar(带外挂配置 IHOMY_CONFIG_PATH)
       在独立窗口运行,轮询到端口就绪后做一次接口冒烟。
  前端不再起常驻进程:dist 产物由本机 nginx 直接托管(静态根指向 frontend\dist,并反代 /api/),
  改版只需重新构建 + 浏览器硬刷新,不需要 vite dev/preview 窗口。
  供电源:咔哒(Kada)快捷键(Alt+I → 本脚本 -NoBrowser)、终端、ZCode 均可直接调用。
.PARAMETER SkipBuild
  跳过构建,只做「停旧 + 起后端」(想立刻重启时用,秒级完成)。
.PARAMETER NoBrowser
  不自动打开浏览器。
.EXAMPLE
  .\scripts\restart-all.ps1
  .\scripts\restart-all.ps1 -SkipBuild -NoBrowser
#>
[CmdletBinding()]
param(
  [switch]$SkipBuild,
  [switch]$NoBrowser
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$Backend = Join-Path $Root 'backend'
$Frontend = Join-Path $Root 'frontend'
$BackendPort = 8080
$WebPort = 80                                  # 本机 nginx 托管 frontend\dist
$LegacyFrontendPorts = @(5173, 4173)           # 历史遗留的 vite dev / preview 端口

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
# 只结束「确实是本项目的」进程：按端口反查属主，且进程名符合预期(java / node / shell)，
# 或命令行里带本仓库路径 / ihomy-backend.jar。避免误杀别的 Java/Node 程序。
# 注意两套名字口径：Get-Process 的 ProcessName 不带扩展名(java/node)，
# Win32_Process 的 Name 带扩展名(java.exe/node.exe)，混用会让「本项目的进程」被误判成别人的而跳过不杀。
function Get-NodeNames    { @('node', 'cmd', 'powershell', 'pwsh') }                 # ProcessName 口径
function Get-NodeExeNames { @('node.exe', 'cmd.exe', 'powershell.exe', 'pwsh.exe') } # Win32_Process.Name 口径

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

function Stop-ProcessTree([int]$ProcId) {
  # 结束进程及其子孙(外壳窗口 + npx + vite 一整条链)
  if (-not (Get-Process -Id $ProcId -ErrorAction SilentlyContinue)) { return $false }
  # PS 5.1 下原生命令写 stderr 会生成 ErrorRecord，遇到 $ErrorActionPreference='Stop' 直接中断；
  # 而 taskkill 对「刚随父进程一起被杀掉的 PID」必然报此错，故经 cmd 层 2>nul 吞掉，失败不影响流程
  $null = cmd /c "taskkill /F /T /PID $ProcId 2>nul"
  return $true
}

function Get-SelfChainIds {
  # 本脚本自身的进程链(含调用它的终端/咔哒外壳)：清扫遗留窗口时绝不碰它们
  $ids = New-Object System.Collections.Generic.List[int]
  $cur = $PID
  while ($cur) {
    $ids.Add([int]$cur)
    $p = Get-CimInstance Win32_Process -Filter "ProcessId=$cur" -ErrorAction SilentlyContinue
    if (-not $p) { break }
    $parent = [int]$p.ParentProcessId
    if (-not $parent -or $parent -eq $cur) { break }
    $cur = $parent
  }
  return $ids
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
  $null = Stop-ProcessTree $pid2

  # 等端口真正释放，否则紧接着的构建/启动仍会撞锁
  for ($i = 0; $i -lt 20; $i++) {
    if (-not (Get-PortOwnerPid $Port)) { Write-Ok "$Label 已停止，端口 $Port 已释放"; return }
    Start-Sleep -Milliseconds 500
  }
  Die "$Label (PID $pid2) 已发出结束指令但端口 $Port 仍被占用，请手动处理后再重试"
}

function Stop-LegacyFrontend {
  # 旧版脚本(以及 start-all.ps1)用 `powershell -NoExit -Command "… npx vite preview"` 起前端：
  # 只按端口杀 node 会留下那个空壳窗口，越重启越多。故先从端口属主顺着进程链往上找到链条
  # 顶端的同伙，一次性 /T 带走整条链(外壳窗口 + npx + vite)。
  foreach ($port in $LegacyFrontendPorts) {
    $owner = Get-PortOwnerPid $port
    if (-not $owner) { continue }
    if ((Get-NodeNames) -notcontains (Get-ProcessNameSafe $owner)) {
      Write-Warn "端口 $port 被 $(Get-ProcessNameSafe $owner) (PID $owner) 占用，不像前端服务进程，跳过不杀"
      continue
    }
    $top = $owner
    while ($true) {
      $p = Get-CimInstance Win32_Process -Filter "ProcessId=$top" -ErrorAction SilentlyContinue
      if (-not $p) { break }
      $parent = [int]$p.ParentProcessId
      if (-not $parent -or $parent -eq $top -or $SelfChain -contains $parent) { break }
      $pp = Get-CimInstance Win32_Process -Filter "ProcessId=$parent" -ErrorAction SilentlyContinue
      if (-not $pp -or (Get-NodeExeNames) -notcontains $pp.Name) { break }
      if (-not ($pp.CommandLine -match 'vite|npm')) { break }
      $top = $parent
    }
    Write-Host "    停止遗留前端服务 (PID $top，端口 $port)..." -ForegroundColor Gray
    if (Stop-ProcessTree $top) { Write-Ok "遗留前端服务已清理(端口 $port)" }
  }

  # 服务已停但 -NoExit 窗口还挂着的空壳(命令行仍指向本仓库前端且带 vite/npm)
  $shells = Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Where-Object {
    $_.CommandLine -and
    (Get-NodeExeNames) -contains $_.Name -and
    $_.CommandLine -like "*$Frontend*" -and
    $_.CommandLine -match 'vite|npm' -and
    $SelfChain -notcontains [int]$_.ProcessId
  }
  foreach ($s in $shells) {
    Write-Host "    关闭遗留前端窗口 ($($s.Name), PID $($s.ProcessId))..." -ForegroundColor Gray
    $null = Stop-ProcessTree $s.ProcessId
  }
}

$SelfChain = Get-SelfChainIds

Write-Step '停止旧服务'
Stop-PortOwner $BackendPort @('java', 'javaw') '后端' -Required
Stop-LegacyFrontend

# 端口都空但仍残留的本项目后端(启动中被杀、端口尚未绑定等)：按命令行精确匹配再补一刀
$stragglers = Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" -ErrorAction SilentlyContinue |
  Where-Object { $_.CommandLine -and ($_.CommandLine -like "*ihomy-backend.jar*" -or $_.CommandLine -like "*$Root*") }
foreach ($p in $stragglers) {
  if (Stop-ProcessTree $p.ProcessId) { Write-Warn "补杀残留后端进程 PID $($p.ProcessId)" }
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
  # 产物 frontend\dist 由本机 nginx 直接托管，不起 vite dev/preview(少一个常驻窗口)
  & npm run build --prefix $Frontend
  if ($LASTEXITCODE -ne 0) { Die '前端构建失败，已中止(后端未启动)' }
  Write-Ok "前端构建完成，产物 $Frontend\dist"
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
Write-Ok "已启动 java -jar $($jar | Split-Path -Leaf)，端口 $BackendPort（独立窗口，日志同时落盘）"

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

# ---------- 前端静态托管检查(nginx 由用户自行常驻，这里只核对是否在监听) ----------
Write-Step "检查前端静态托管 (nginx，端口 $WebPort)"
$webOwner = Get-PortOwnerPid $WebPort
$webIsNginx = $webOwner -and (Get-ProcessNameSafe $webOwner) -eq 'nginx'
if ($webIsNginx) {
  Write-Ok "nginx (PID $webOwner) 在监听 $WebPort，托管 $Frontend\dist 并反代 /api/"
} elseif ($webOwner) {
  Write-Warn "端口 $WebPort 被 $(Get-ProcessNameSafe $webOwner) (PID $webOwner) 占用，不是 nginx：前端页面可能打不开"
} else {
  Write-Warn "nginx 未监听 $WebPort：前端页面打不开，请启动本机 nginx(静态根须指向 $Frontend\dist)"
}

# ---------- 打开浏览器 ----------
if (-not $NoBrowser) {
  if ($webIsNginx) {
    Start-Process "http://localhost/"
    Write-Ok "已打开 http://localhost/"
  } else {
    Write-Warn 'nginx 未就绪，跳过打开浏览器'
  }
}

Write-Host "`n重启完成：页面 http://localhost/  (nginx 托管 dist)   后端接口 http://localhost:$BackendPort/api" -ForegroundColor Cyan
Write-Host '桌面上只保留后端窗口；前端已重新构建进 frontend\dist，浏览器 Ctrl+Shift+R 刷新即可生效。' -ForegroundColor Cyan
exit 0
