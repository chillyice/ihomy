# ihomy Android 客户端发版:构建 → 校验 → 上传 → 回验,一条命令跑完。
#
# 口径:接口 /api/public/app/latest 以 uploads/app 里实际存在的最高版本包为准(AndroidReleaseService),
# 所以本脚本只要把包按 ihomy-V<主>.<次>.apk 传上去就生效——不用改 external.yml、不用重启后端,
# 也就不会出现「网页发了新版本、客户端还提示上次版本」的断层。
#
# 用法(仓库根):
#   powershell -ExecutionPolicy Bypass -File scripts/release-app.ps1
#   powershell -ExecutionPolicy Bypass -File scripts/release-app.ps1 -SkipBuild   # 复用已构建产物,只校验+上传
#   powershell -ExecutionPolicy Bypass -File scripts/release-app.ps1 -SkipWebRestore  # 不恢复网页 dist(一般别用)
#
# 注意:app 模式构建会覆盖 frontend/dist(抹掉 PWA 产物),脚本结束默认重新 npm run build 复原,
# 免得之后用 deploy.ps1 -SkipBuild 把「无 Service Worker 的网页」发上线。
param(
    [switch]$SkipBuild,
    [switch]$SkipWebRestore,
    [string]$RemoteHost = 'ihomy.top',
    [int]$SshPort = 19068,
    [string]$RemoteUser = 'root',
    [string]$UploadDir = '/opt/ihomy/uploads',
    [string]$SiteUrl = 'https://ihomy.top'
)

$ErrorActionPreference = 'Stop'

# 覆盖安装必须是同一个签名(换签名老用户装不上),与历史包对齐
$ExpectedCertSha256 = '93634d1b24e4a1ec1d9f4b8863f5feb8134d3a2233b9ffd276cf573ea4dd1c98'

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$frontend = Join-Path $root 'frontend'
$android = Join-Path $frontend 'android'
$version = (Get-Content (Join-Path $root 'VERSION') -Raw).Trim()
$apk = Join-Path $android 'app\build\outputs\apk\release\app-release.apk'

function Step($text) { Write-Host "==> $text" -ForegroundColor Cyan }
function Fail($text) { Write-Host "!!  $text" -ForegroundColor Red; exit 1 }

Step "目标版本 $version"

if (-not $SkipBuild) {
    Step '前端按壳模式构建 + cap sync(会覆盖 frontend/dist)'
    Push-Location $frontend
    try {
        npm run app:sync
        if ($LASTEXITCODE -ne 0) { Fail 'app:sync 失败' }
    } finally { Pop-Location }

    Step '打包 release APK'
    Push-Location $android
    try {
        $env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
        .\gradlew.bat :app:assembleRelease
        if ($LASTEXITCODE -ne 0) { Fail 'assembleRelease 失败' }
    } finally { Pop-Location }
}

if (-not (Test-Path $apk)) { Fail "找不到安装包:$apk(先去掉 -SkipBuild 构建一次)" }

Step '校验版本号与签名'
$buildTools = Get-ChildItem (Join-Path $env:LOCALAPPDATA 'Android\Sdk\build-tools') -Directory |
    Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
if (-not $buildTools) { Fail '找不到 Android build-tools(SDK 未安装?)' }
$aapt = Join-Path $buildTools.FullName 'aapt2.exe'
if (-not (Test-Path $aapt)) { $aapt = Join-Path $buildTools.FullName 'aapt.exe' }
$apksigner = Join-Path $buildTools.FullName 'apksigner.bat'

$nameLine = & $aapt dump badging $apk | Select-String -Pattern "versionName='([^']+)'" | Select-Object -First 1
if (-not $nameLine) { Fail '读不到 APK versionName' }
$apkVersion = $nameLine.Matches[0].Groups[1].Value
if ($apkVersion -ne $version) { Fail "APK 版本 $apkVersion 与 VERSION($version)不一致(是不是 -SkipBuild 复用了旧产物?)" }

$certLine = & $apksigner verify --print-certs $apk | Select-String -Pattern 'SHA-256 digest: (\w+)' | Select-Object -First 1
if (-not $certLine) { Fail 'apksigner 未输出证书指纹' }
$cert = $certLine.Matches[0].Groups[1].Value
if ($cert -ne $ExpectedCertSha256) { Fail "签名指纹不匹配:$cert(覆盖安装会失败)" }

$size = (Get-Item $apk).Length
Write-Host ("    版本 {0} / 签名 OK / {1} MB" -f $apkVersion, [math]::Round($size / 1MB, 1))

$apkName = "ihomy-$version.apk"
$remotePath = "$UploadDir/app/$apkName"
Step "上传到 ${RemoteHost}:$remotePath"
scp -P $SshPort $apk "${RemoteUser}@${RemoteHost}:/tmp/$apkName"
if ($LASTEXITCODE -ne 0) { Fail 'scp 上传失败' }
$remoteSize = ssh -p $SshPort "${RemoteUser}@${RemoteHost}" "mkdir -p $UploadDir/app && mv /tmp/$apkName $remotePath && chown ihomy:ihomy $remotePath && chmod 644 $remotePath && stat -c %s $remotePath"
if ($LASTEXITCODE -ne 0) { Fail '上传落地失败(目录/权限?)' }
if ("$remoteSize".Trim() -ne "$size") { Fail "服务端大小 $remoteSize 与本地 $size 不一致" }

Step '回验更新接口与下载地址'
Start-Sleep -Seconds 2
$latestRaw = curl.exe -s -m 20 "$SiteUrl/api/public/app/latest"
if (-not $latestRaw) { Fail '取 /api/public/app/latest 失败' }
$latest = $latestRaw | ConvertFrom-Json
if ($latest.data.version -ne $version) {
    Fail "接口仍返回 $($latest.data.version),期望 $version(上传后最多 60 秒缓存过期,稍后重跑 -SkipBuild 验证)"
}
if ($latest.data.url -ne "/files/app/$apkName") { Fail "接口 url 不对:$($latest.data.url)" }

$head = (curl.exe -sI -m 30 "$SiteUrl/files/app/$apkName" | Out-String)
if ($head -notmatch 'HTTP/\S+ 200') { Fail '安装包直链不是 200' }
if ($head -notmatch "Content-Length: $size") { Fail "直链 Content-Length 与本地大小不一致" }

Write-Host '    客户端启动时会收到更新提示;更新说明(latest-notes)按需改生产 external.yml 后重启后端,不改也能发。'
Write-Host ('    min-supported-version = {0}' -f $latest.data.minSupported)

if (-not $SkipWebRestore) {
    Step '恢复网页构建(壳模式构建覆盖了 frontend/dist,复原 PWA 产物)'
    Push-Location $frontend
    try {
        npm run build
        if ($LASTEXITCODE -ne 0) { Fail '网页构建失败' }
    } finally { Pop-Location }
}

Step "完成:$version 的安装包已可被客户端检查到"
