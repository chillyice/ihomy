<#
.SYNOPSIS
  ihomy 一键部署流水线(本地构建 → scp 上传 → 远程重启)
.DESCRIPTION
  在 Windows 本地编译后端 jar + 前端 dist,scp 上传到 Linux 服务器,远程重启 systemd 服务。
  服务器默认:ihomy.top,SSH 端口 19068,root 登录(已在服务器配置 SSH 公钥免密)。
  jar 内嵌的 application.yml 为共同基线(端口/URL/mybatis/jwt/logging)。
  测试/生产差异配置(MySQL/Redis 密码、上传路径、验证码、天气凭证)放外挂文件 external.yml,
  通过环境变量 IHOMY_CONFIG_PATH 指向,ExternalConfigLoader 以最高优先级加载。
  默认不覆盖服务器外挂文件;需上传时用 -UploadExternal。
.PARAMETER BackendOnly
  仅部署后端(跳过前端构建与上传)
.PARAMETER FrontendOnly
  仅部署前端(跳过后端构建与上传)
.PARAMETER SkipBuild
  跳过本地构建,直接用现有 target/ihomy-backend.jar 与 frontend/dist 部署
.PARAMETER UploadExternal
  上传外挂配置文件到服务器(默认不上传,避免覆盖生产配置)
  上传时自动备份服务器旧文件为 external.yml.bak,再原子替换
  需配合 -ExternalConfig 指定本地 external.yml 路径
.PARAMETER ExternalConfig
  本地外挂配置文件路径(配合 -UploadExternal 使用)
  默认:backend/src/main/resources/external.yml(若存在)
.PARAMETER Server
  服务器地址(默认 ihomy.top)
.PARAMETER Port
  SSH 端口(默认 19068)
.PARAMETER User
  SSH 登录用户(默认 root,需配置公钥免密)
.PARAMETER RemoteRoot
  服务器项目根目录(默认 /opt/ihomy)
.EXAMPLE
  .\scripts\deploy.ps1                  # 全量部署(不动外挂配置)
  .\scripts\deploy.ps1 -BackendOnly     # 只更后端
  .\scripts\deploy.ps1 -SkipBuild       # 跳过构建直接部署(已 build 过)
  .\scripts\deploy.ps1 -UploadExternal  # 上传外挂配置(首次部署或配置变更时)
  .\scripts\deploy.ps1 -UploadExternal -ExternalConfig D:\path\to\external.yml
#>
[CmdletBinding()]
param(
  [switch]$BackendOnly,
  [switch]$FrontendOnly,
  [switch]$SkipBuild,
  [switch]$UploadExternal,
  [string]$ExternalConfig,
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

# 通过 base64 把 bash 脚本传到远程执行,避免 PowerShell 管道 CRLF/编码问题
function Invoke-RemoteBash($script) {
  $lf = $script -replace "`r`n", "`n"
  $b64 = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($lf))
  & ssh @sshOpts "$User@$Server" "echo '$b64' | base64 -d | bash" 2>&1
}

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
    # mvnw.cmd 在当前目录找 .mvn/wrapper,必须先切到 backend 目录
    Push-Location -LiteralPath $Backend
    try {
      & '.\mvnw.cmd' -B -q clean package -DskipTests
      if ($LASTEXITCODE -ne 0) { Die '后端构建失败' }
    } finally { Pop-Location }
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

# ---------- 上传外挂配置(可选)----------
if ($UploadExternal) {
  Write-Step '上传外挂配置文件 external.yml'

  # 确定本地 external.yml 路径
  if (-not $ExternalConfig) {
    $ExternalConfig = Join-Path $Backend 'src\main\resources\external.yml'
  }
  if (-not (Test-Path $ExternalConfig)) {
    Die "外挂配置文件不存在: $ExternalConfig(用 -ExternalConfig 指定路径,或从 external.yml.template 复制填写)"
  }
  Write-Ok "本地外挂配置: $ExternalConfig"

  # 服务器路径
  $remoteConfigDir = "$RemoteRoot/config"
  $remoteExternalNew = "$remoteConfigDir/external.yml.new"
  $remoteExternal = "$remoteConfigDir/external.yml"

  # 确保 config 目录存在
  & ssh @sshOpts "$User@$Server" "mkdir -p $remoteConfigDir"
  if ($LASTEXITCODE -ne 0) { Die '远程 mkdir config 失败' }

  # 上传到 .new(原子替换)
  & scp -P $Port $ExternalConfig "${User}@${Server}:$remoteExternalNew"
  if ($LASTEXITCODE -ne 0) { Die '上传 external.yml 失败' }
  Write-Ok 'external.yml 上传完成'

  # 远程:备份旧文件 → 替换;确保 systemd unit 有 IHOMY_CONFIG_PATH
  $externalScript = @"
set -e
cd $remoteConfigDir

# 备份旧文件(若存在)
if [ -f external.yml ]; then
  cp external.yml external.yml.bak
  echo 'BACKED_UP'
fi
mv external.yml.new external.yml
chown ihomy:ihomy external.yml
chmod 640 external.yml

# 检查 systemd unit 是否已设 IHOMY_CONFIG_PATH
UNIT_FILE=/etc/systemd/system/ihomy-backend.service
if [ -f "`$UNIT_FILE" ] && ! grep -q 'IHOMY_CONFIG_PATH' "`$UNIT_FILE"; then
  # 在 [Service] 段追加 Environment 行(若已有则不动)
  sed -i '/^\[Service\]/a Environment=IHOMY_CONFIG_PATH=$remoteExternal' "`$UNIT_FILE"
  systemctl daemon-reload
  echo 'UNIT_UPDATED'
fi
echo 'DONE'
"@

  $result = Invoke-RemoteBash $externalScript
  if ($LASTEXITCODE -ne 0) {
    Write-Warn "外挂配置上传失败: $result"
    Die '外挂配置部署失败'
  }
  if ($result -match 'BACKED_UP') { Write-Ok '已备份旧 external.yml → external.yml.bak' }
  if ($result -match 'UNIT_UPDATED') { Write-Ok '已为 systemd unit 添加 IHOMY_CONFIG_PATH 环境变量' }
  Write-Ok "外挂配置已部署到 $remoteExternal(重启后端后生效)"
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

  Write-Step '部署后端:替换 jar + 重启'
  # jar 内嵌的 application.yml 为共同基线(端口/URL/mybatis/jwt/logging)
  # 测试/生产差异配置由外挂文件 external.yml 覆盖(IHOMY_CONFIG_PATH 指向)
  # 若 target/ 下有旧的外部 application.yml(早期部署遗留),删除它避免混淆
  $deployScript = @"
set -e
cd $remoteTarget

# 清理旧的外部配置(如有):jar 内 application.yml 是共同基线,差异项走 external.yml
if [ -f application.yml ]; then
  rm -f application.yml
  echo 'EXTERNAL_CONFIG_REMOVED'
fi

# 备份旧 jar → 替换 → 重启
if [ -f ihomy-backend.jar ]; then
  cp ihomy-backend.jar ihomy-backend.jar.bak
fi
mv ihomy-backend.jar.new ihomy-backend.jar
chown ihomy:ihomy ihomy-backend.jar
systemctl restart ihomy-backend

# 等待启动(最多 30 秒,Spring Boot 在 2GB 服务器约需 30 秒)
for i in {1..30}; do
  sleep 1
  if systemctl is-active --quiet ihomy-backend; then
    echo 'ACTIVE'
    exit 0
  fi
done
echo 'TIMEOUT' >&2
exit 1
"@

  $result = Invoke-RemoteBash $deployScript
  if ($LASTEXITCODE -ne 0) {
    Write-Warn "部署失败: $result"
    Write-Warn '尝试回滚到上一个 jar...'
    & ssh @sshOpts "$User@$Server" "cd $remoteTarget && [ -f ihomy-backend.jar.bak ] && mv ihomy-backend.jar.bak ihomy-backend.jar && systemctl restart ihomy-backend && echo ROLLBACK_OK" 2>&1 | ForEach-Object { Write-Warn $_ }
    Die '后端部署失败'
  }
  if ($result -match 'EXTERNAL_CONFIG_REMOVED') { Write-Ok '已清理旧外部 application.yml(改用 external.yml 外挂)' }
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
# nginx -t 的诊断信息走 stderr,2>&1 合并到 stdout 避免 PowerShell 把它当错误
nginx -t 2>&1 && systemctl reload nginx
echo 'DONE'
"@

  $result = Invoke-RemoteBash $frontendScript
  if ($LASTEXITCODE -ne 0) {
    Write-Warn "前端部署失败: $result"
    # 回滚:把 dist.bak 换回去
    & ssh @sshOpts "$User@$Server" "cd $RemoteRoot/frontend && [ -d dist.bak ] && rm -rf dist && mv dist.bak dist && systemctl reload nginx && echo ROLLBACK_OK" 2>&1 | ForEach-Object { Write-Warn $_ }
    Die '前端部署失败'
  }
  Write-Ok '前端 dist 已替换,nginx 已 reload'
}

# ---------- 健康检查 ----------
# Spring Boot 在 2GB 服务器启动较慢,is-active 只表示进程在跑,不代表 HTTP 已就绪
Write-Step '健康检查'
Start-Sleep -Seconds 5
$healthOk = $false
for ($i = 1; $i -le 12; $i++) {
  try {
    $resp = Invoke-WebRequest -Uri "https://$Server/api/public/home" -UseBasicParsing -TimeoutSec 8
    if ($resp.StatusCode -eq 200) {
      Write-Ok "API 健康检查通过(尝试 $i 次)"
      $healthOk = $true
      break
    }
  } catch {
    Write-Warn "第 $i 次检查失败,等待 5 秒后重试..."
    Start-Sleep -Seconds 5
  }
}
if (-not $healthOk) {
  Write-Warn '健康检查未通过,自动拉取服务器后端日志尾部 40 行:'
  Write-Host '----- /var/log/ihomy/backend.log (tail 40) -----' -ForegroundColor DarkGray
  & ssh @sshOpts "$User@$Server" 'tail -n 40 /var/log/ihomy/backend.log 2>/dev/null; echo "----- /var/log/ihomy/backend.err.log (tail 20) -----"; tail -n 20 /var/log/ihomy/backend.err.log 2>/dev/null'
  Die 'API 健康检查未通过(见上方日志)'
}

Write-Host ""
Write-Host "==> 部署完成: https://$Server" -ForegroundColor Cyan
if (-not $FrontendOnly -and -not $BackendOnly) {
  Write-Host "    后端 + 前端均已更新" -ForegroundColor Gray
} elseif ($FrontendOnly) {
  Write-Host "    仅前端已更新" -ForegroundColor Gray
} else {
  Write-Host "    仅后端已更新" -ForegroundColor Gray
}
