<#
.SYNOPSIS
  ihomy 一键部署流水线(本地构建 → scp 上传 → 远程重启)
.DESCRIPTION
  在 Windows 本地编译后端 jar + 前端 dist,scp 上传到 Linux 服务器,远程重启 systemd 服务。
  服务器默认:ihomy.top,SSH 端口 19068,root 登录(已在服务器配置 SSH 公钥免密)。
  首次部署时自动从服务器源码复制 application.yml 到 target/(外部配置覆盖 jar 内开发配置)。
  之后每次部署只替换 jar + dist,不动配置。
.PARAMETER BackendOnly
  仅部署后端(跳过前端构建与上传)
.PARAMETER FrontendOnly
  仅部署前端(跳过后端构建与上传)
.PARAMETER SkipBuild
  跳过本地构建,直接用现有 target/ihomy-backend.jar 与 frontend/dist 部署
.PARAMETER Server
  服务器地址(默认 ihomy.top)
.PARAMETER Port
  SSH 端口(默认 19068)
.PARAMETER User
  SSH 登录用户(默认 root,需配置公钥免密)
.PARAMETER RemoteRoot
  服务器项目根目录(默认 /opt/ihomy)
.EXAMPLE
  .\scripts\deploy.ps1                  # 全量部署
  .\scripts\deploy.ps1 -BackendOnly     # 只更后端
  .\scripts\deploy.ps1 -SkipBuild       # 跳过构建直接部署(已 build 过)
#>
[CmdletBinding()]
param(
  [switch]$BackendOnly,
  [switch]$FrontendOnly,
  [switch]$SkipBuild,
  [string]$Server = 'ihomy.top',
  [int]$Port = 19068,
  [string]$User = 'root',
  [string]$RemoteRoot = '/opt/ihomy'
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$Backend = Join-Path $Root 'backend'
$Frontend = Join-Path $Root 'frontend'
$Temp = Join-Path $env:TEMP 'ihomy-deploy'

function Write-Step($msg) { Write-Host "`n==> $msg" -ForegroundColor Cyan }
function Write-Ok($msg)   { Write-Host "    [OK] $msg" -ForegroundColor Green }
function Write-Warn($msg) { Write-Host "    [!!] $msg" -ForegroundColor Yellow }
function Die($msg)        { Write-Host "    [ERR] $msg" -ForegroundColor Red; exit 1 }

# SSH/SCP 公共参数
$sshOpts = @('-p', "$Port", '-o', 'ConnectTimeout=15', '-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=accept-new')

# ---------- 前置检查 ----------
Write-Step '前置检查'

# JAVA_HOME
if (-not $env:JAVA_HOME) {
  $javaCmd = Get-Command java -ErrorAction SilentlyContinue
  if (-not $javaCmd) { Die '未找到 java,请安装 JDK 17/21 并配置 JAVA_HOME' }
  $env:JAVA_HOME = (Get-Item $javaCmd.Source).Directory.Parent.FullName
}
if (-not (Test-Path $env:JAVA_HOME)) { Die "JAVA_HOME 无效: $env:JAVA_HOME" }
Write-Ok "JAVA_HOME: $env:JAVA_HOME"

# 工具
foreach ($t in 'ssh','scp','tar') {
  if (-not (Get-Command $t -ErrorAction SilentlyContinue)) { Die "未找到 $t,Windows 10+ 自带 OpenSSH 与 tar" }
}
Write-Ok 'ssh / scp / tar 可用'

# SSH 连接(免密)
Write-Step "测试 SSH 连接 $User@${Server}:$Port"
& ssh @sshOpts "$User@$Server" 'echo ok' | Out-Null
if ($LASTEXITCODE -ne 0) {
  Write-Host ""
  Write-Host "    SSH 免密登录失败。首次使用请配置公钥:" -ForegroundColor Yellow
  Write-Host "      1) 本地生成密钥(已生成可跳过): ssh-keygen -t ed25519"
  Write-Host "      2) 上传公钥到服务器:"
  Write-Host "         Get-Content `$env:USERPROFILE\.ssh\id_ed25519.pub | ssh -p $Port $User@$Server 'mkdir -p ~/.ssh && cat >> ~/.ssh/authorized_keys'"
  Die 'SSH 免密未配置'
}
Write-Ok 'SSH 连接正常'

# ---------- 构建后端 ----------
if (-not $FrontendOnly) {
  Write-Step '构建后端 (mvnw clean package)'

  if (-not $SkipBuild) {
    # 提示:本地若有 spring-boot:run 在跑,可能锁 target 目录
    $javaProcs = Get-Process java -ErrorAction SilentlyContinue
    if ($javaProcs) {
      Write-Warn '检测到本地 java 进程运行中,若 mvnw clean 失败请先 Stop-Process -Name java -Force'
    }
    & (Join-Path $Backend 'mvnw.cmd') -B -q -f $Backend clean package -DskipTests
    if ($LASTEXITCODE -ne 0) { Die '后端构建失败' }
  }

  $jar = Join-Path $Backend 'target\ihomy-backend.jar'
  if (-not (Test-Path $jar)) { Die "未找到 jar: $jar(是否未构建?用 -SkipBuild 需先构建)" }
  $jarSize = [math]::Round((Get-Item $jar).Length / 1MB, 1)
  Write-Ok "后端 jar 就绪 ($jarSize MB)"
}

# ---------- 构建前端 ----------
if (-not $BackendOnly) {
  Write-Step '构建前端 (npm run build)'

  if (-not $SkipBuild) {
    if (-not (Test-Path (Join-Path $Frontend 'node_modules'))) {
      & npm install --prefix $Frontend --no-audit --no-fund
      if ($LASTEXITCODE -ne 0) { Die '前端依赖安装失败' }
    }
    & npm run build --prefix $Frontend
    if ($LASTEXITCODE -ne 0) { Die '前端构建失败' }
  }

  $dist = Join-Path $Frontend 'dist'
  if (-not (Test-Path $dist)) { Die "未找到 dist: $dist(是否未构建?)" }

  # 打包 dist 为 tar.gz(单文件传输比 scp -r 快很多)
  if (-not (Test-Path $Temp)) { New-Item -ItemType Directory -Path $Temp | Out-Null }
  $distTar = Join-Path $Temp 'frontend-dist.tar.gz'
  if (Test-Path $distTar) { Remove-Item $distTar -Force }
  & tar -czf $distTar -C $dist '.'
  if ($LASTEXITCODE -ne 0) { Die '打包 dist 失败' }
  $tarSize = [math]::Round((Get-Item $distTar).Length / 1MB, 1)
  Write-Ok "前端 dist 打包完成 ($tarSize MB)"
}

# ---------- 部署后端 ----------
if (-not $FrontendOnly) {
  Write-Step '部署后端:上传 jar'
  $jar = Join-Path $Backend 'target\ihomy-backend.jar'
  $remoteTarget = "$RemoteRoot/backend/target"
  $remoteJarNew = "$remoteTarget/ihomy-backend.jar.new"

  # 确保 target 目录存在
  & ssh @sshOpts "$User@$Server" "mkdir -p $remoteTarget"
  if ($LASTEXITCODE -ne 0) { Die '远程 mkdir target 失败' }

  # 上传到 .new 文件(不直接覆盖,原子替换)
  & scp -P $Port $jar "${User}@${Server}:$remoteJarNew"
  if ($LASTEXITCODE -ne 0) { Die '上传 jar 失败' }
  Write-Ok 'jar 上传完成'

  Write-Step '部署后端:外部配置检查 + 替换 + 重启'
  # 首次部署:target/application.yml 不存在时,从源码复制一份(生产配置),作为外部覆盖
  # 以后每次部署只换 jar,不动 target/application.yml
  $deployScript = @"
set -e
cd $remoteTarget

# 首次部署:外部 application.yml 不存在 → 从源码复制(源码 application.yml 是部署时编辑过的生产配置)
if [ ! -f application.yml ]; then
  if [ -f $RemoteRoot/backend/src/main/resources/application.yml ]; then
    cp $RemoteRoot/backend/src/main/resources/application.yml application.yml
    chown ihomy:ihomy application.yml
    echo 'EXTERNAL_CONFIG_INIT'
  else
    echo 'NO_SOURCE_CONFIG' >&2
    exit 2
  fi
fi

# 备份旧 jar → 替换 → 重启
if [ -f ihomy-backend.jar ]; then
  cp ihomy-backend.jar ihomy-backend.jar.bak
fi
mv ihomy-backend.jar.new ihomy-backend.jar
chown ihomy:ihomy ihomy-backend.jar
systemctl restart ihomy-backend

# 等待启动(最多 20 秒)
for i in {1..20}; do
  sleep 1
  if systemctl is-active --quiet ihomy-backend; then
    echo 'ACTIVE'
    exit 0
  fi
done
echo 'TIMEOUT' >&2
exit 1
"@

  $result = ($deployScript | & ssh @sshOpts "$User@$Server" 'bash -s' 2>&1)
  if ($LASTEXITCODE -ne 0) {
    Write-Warn "部署失败: $result"
    if ($result -match 'NO_SOURCE_CONFIG') {
      Write-Host ""
      Write-Host "    服务器源码 application.yml 不存在,无法初始化外部配置。" -ForegroundColor Yellow
      Write-Host "    请手动在 $remoteTarget/application.yml 放置生产配置后重试。" -ForegroundColor Yellow
    } else {
      Write-Warn '尝试回滚到上一个 jar...'
      & ssh @sshOpts "$User@$Server" "cd $remoteTarget && [ -f ihomy-backend.jar.bak ] && mv ihomy-backend.jar.bak ihomy-backend.jar && systemctl restart ihomy-backend && echo ROLLBACK_OK" 2>&1 | ForEach-Object { Write-Warn $_ }
    }
    Die '后端部署失败'
  }
  if ($result -match 'EXTERNAL_CONFIG_INIT') { Write-Ok '首次部署:已从源码复制 application.yml 作为外部配置' }
  Write-Ok '后端 jar 已替换,服务已重启'
}

# ---------- 部署前端 ----------
if (-not $BackendOnly) {
  Write-Step '部署前端:上传 dist.tar.gz'
  $remoteTar = '/tmp/ihomy-frontend-dist.tar.gz'
  & scp -P $Port $distTar "${User}@${Server}:$remoteTar"
  if ($LASTEXITCODE -ne 0) { Die '上传 dist 失败' }
  Write-Ok 'dist 上传完成'

  Write-Step '部署前端:替换 dist + reload nginx'
  $frontendScript = @"
set -e
cd $RemoteRoot/frontend

# 解压到 dist.new(避免覆盖期间 nginx 读到半截文件)
rm -rf dist.new
mkdir dist.new
tar -xzf $remoteTar -C dist.new
rm -f $remoteTar
chown -R ihomy:ihomy dist.new

# 原子替换:旧 dist 备份 → 新 dist 上位
if [ -d dist ]; then
  rm -rf dist.bak
  mv dist dist.bak
fi
mv dist.new dist

# nginx reload(配置不变,只刷静态文件;用 -s reload 避免 worker 重启断连)
nginx -t && systemctl reload nginx
echo 'DONE'
"@

  $result = ($frontendScript | & ssh @sshOpts "$User@$Server" 'bash -s' 2>&1)
  if ($LASTEXITCODE -ne 0) {
    Write-Warn "前端部署失败: $result"
    # 回滚:把 dist.bak 换回去
    & ssh @sshOpts "$User@$Server" "cd $RemoteRoot/frontend && [ -d dist.bak ] && rm -rf dist && mv dist.bak dist && systemctl reload nginx && echo ROLLBACK_OK" 2>&1 | ForEach-Object { Write-Warn $_ }
    Die '前端部署失败'
  }
  Write-Ok '前端 dist 已替换,nginx 已 reload'
}

# ---------- 健康检查 ----------
Write-Step '健康检查'
Start-Sleep -Seconds 3
$healthOk = $false
for ($i = 1; $i -le 5; $i++) {
  try {
    $resp = Invoke-WebRequest -Uri "https://$Server/api/public/home" -UseBasicParsing -TimeoutSec 8
    if ($resp.StatusCode -eq 200) {
      Write-Ok "API 健康检查通过(尝试 $i 次)"
      $healthOk = $true
      break
    }
  } catch {
    Write-Warn "第 $i 次检查失败,等待 3 秒后重试..."
    Start-Sleep -Seconds 3
  }
}
if (-not $healthOk) { Die 'API 健康检查未通过,请登录服务器查 journalctl -u ihomy-backend -n 50' }

Write-Host ""
Write-Host "==> 部署完成: https://$Server" -ForegroundColor Cyan
if (-not $FrontendOnly -and -not $BackendOnly) {
  Write-Host "    后端 + 前端均已更新" -ForegroundColor Gray
} elseif ($FrontendOnly) {
  Write-Host "    仅前端已更新" -ForegroundColor Gray
} else {
  Write-Host "    仅后端已更新" -ForegroundColor Gray
}
