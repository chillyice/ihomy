<#
.SYNOPSIS
  强制清理 ihomy 后端构建产物目录(默认 backend\target)。
.DESCRIPTION
  OneDrive 工作树会给构建产物盖上 ReadOnly 属性与 reparse point,mvn clean 走普通删除,
  会遇到「Failed to delete ...\backend\target\surefire-reports」这类失败(与进程占用无关)。
  本脚本:清属性 → 递归强删 → 重试 → cmd rd 兜底 → 仍不行就把目录改名,让 Maven 重建 target。
  restart-all.ps1 / deploy.ps1 / start-all.ps1 都在 mvnw clean 之前调用它;也可单独跑
  (咔哒快捷键 Alt+Shift+I),用于 IDE 构建或手工构建撞上同一个锁。
.PARAMETER Path
  要清理的目录,默认 <仓库根>\backend\target。
.PARAMETER Retries
  删除重试次数,默认 5 次。
.EXAMPLE
  .\scripts\clean-build-target.ps1
#>
[CmdletBinding()]
param(
  [string]$Path,
  [int]$Retries = 5
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
if (-not $Path) { $Path = Join-Path $Root 'backend\target' }

function Write-Info($msg) { Write-Host "    $msg" -ForegroundColor Gray }
function Write-Ok($msg)   { Write-Host "    [OK] $msg" -ForegroundColor Green }
function Write-Warn($msg) { Write-Host "    [!!] $msg" -ForegroundColor Yellow }
function Write-Err($msg)  { Write-Host "    [ERR] $msg" -ForegroundColor Red }

if (-not (Test-Path -LiteralPath $Path)) {
  Write-Ok "无需清理(目录不存在): $Path"
  exit 0
}

$Path = (Resolve-Path -LiteralPath $Path).ProviderPath

# 后端在跑时 jar 被持有:只提示,不杀进程(杀后端是 restart-all.ps1 的职责,这里可能被单独调用)
$holders = Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" -ErrorAction SilentlyContinue |
  Where-Object { $_.CommandLine -and $_.CommandLine -like '*ihomy-backend.jar*' }
if ($holders) {
  $pids = ($holders | ForEach-Object { $_.ProcessId }) -join ', '
  Write-Warn "检测到后端 java 进程(PID $pids)占用 target\ihomy-backend.jar,删除可能失败;请先停后端"
}

function Clear-ReadOnly($target) {
  # reparse point 目录改不了属性,逐项 best-effort;目标只是去掉 ReadOnly 让删除能过
  Get-ChildItem -LiteralPath $target -Recurse -Force -ErrorAction SilentlyContinue | ForEach-Object {
    try {
      if ($_.Attributes -band [IO.FileAttributes]::ReadOnly) {
        $_.Attributes = $_.Attributes -band (-bnot [IO.FileAttributes]::ReadOnly)
      }
    } catch { }
  }
  try {
    $item = Get-Item -LiteralPath $target -Force
    if ($item.Attributes -band [IO.FileAttributes]::ReadOnly) {
      $item.Attributes = $item.Attributes -band (-bnot [IO.FileAttributes]::ReadOnly)
    }
  } catch { }
}

Write-Info "清理 $Path"

for ($i = 1; $i -le $Retries; $i++) {
  Clear-ReadOnly $Path
  Remove-Item -LiteralPath $Path -Recurse -Force -ErrorAction SilentlyContinue
  if (-not (Test-Path -LiteralPath $Path)) {
    Write-Ok "已删除(第 $i 次尝试)"
    exit 0
  }
  Start-Sleep -Milliseconds (300 * $i)
}

# 兜底 1:cmd rd /s /q 对只读文件与深路径的处理与 PowerShell 不同,常常能过
$null = cmd /c "rd /s /q `"$Path`" 2>nul"
if (-not (Test-Path -LiteralPath $Path)) {
  Write-Ok '已删除(rd 兜底)'
  exit 0
}

# 兜底 2:改名。Maven 只会重建 target 目录,旧目录留着不影响本次构建(下次再清)
$stale = "$Path.stale-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
try {
  Rename-Item -LiteralPath $Path -NewName (Split-Path -Leaf $stale) -Force
  Write-Warn "删不掉,已改名为 $(Split-Path -Leaf $stale);Maven 会重建 target,旧目录可稍后手动删除"
  exit 0
} catch {
  Write-Err "无法删除也无法改名: $Path"
  Write-Err "仍被占用的条目(前 10 条):"
  Get-ChildItem -LiteralPath $Path -Recurse -Force -ErrorAction SilentlyContinue |
    Select-Object -First 10 -ExpandProperty FullName |
    ForEach-Object { Write-Host "      $_" -ForegroundColor DarkGray }
  Write-Err "请关闭 IDE 里的构建/运行、停掉本地后端后重试"
  exit 1
}
