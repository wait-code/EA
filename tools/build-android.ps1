<#
  一键构建 Debug APK（用自己的 Android SDK）
  用法：
    .\tools\build-android.ps1                          # 用 $env:ANDROID_HOME
    .\tools\build-android.ps1 -Sdk "C:\Users\你\AppData\Local\Android\Sdk"
    .\tools\build-android.ps1 -Release                 # 另出 release（未签名）
    .\tools\build-android.ps1 -SkipContent             # 跳过内容重新打包
#>
param(
  [string]$Sdk = $env:ANDROID_HOME,
  [switch]$Release,
  [switch]$SkipContent,
  [switch]$Clean
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$android = Join-Path $repo 'android'
$framework = Join-Path $repo 'framework'

Write-Host "== 1/4 环境检查 ==" -ForegroundColor Cyan
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java.exe' }
try { & $java -version 2>&1 | Out-Null; Write-Host "  java: $java" } catch { throw "找不到 Java 17，请设置 JAVA_HOME" }
if (-not $Sdk) { throw "没有 Android SDK 路径：请设置 ANDROID_HOME 或传 -Sdk" }
if (-not (Test-Path (Join-Path $Sdk 'platforms'))) { throw "SDK 路径不像 Android SDK：$Sdk" }
Write-Host "  sdk : $Sdk"
$env:ANDROID_HOME = $Sdk
$env:ANDROID_SDK_ROOT = $Sdk

# 写 local.properties（命令行构建需要；Studio 会自动生成，不冲突）
$sdkProp = $Sdk -replace '\\', '\\' -replace ':', '\:'
Set-Content -LiteralPath (Join-Path $android 'local.properties') -Value "sdk.dir=$sdkProp" -Encoding ascii
Write-Host "  已写 android/local.properties"

if (-not $SkipContent) {
  Write-Host "`n== 2/4 重新编译内容包（content → assets/seed.epak） ==" -ForegroundColor Cyan
  $papers = Get-ChildItem -Path (Join-Path $repo 'content') -Recurse -Filter paper.json -ErrorAction SilentlyContinue
  if (-not $papers) {
    Write-Host "  content/ 下没有 paper.json，跳过（用现有 assets/seed.epak）" -ForegroundColor Yellow
  } elseif ($papers.Count -gt 1) {
    Write-Host "  发现多份试卷，只投放第一份；其余用 App 内「导入试卷包」" -ForegroundColor Yellow
    & node (Join-Path $framework 'bin\exam.mjs') build $papers[0].FullName --target android
  } else {
    & node (Join-Path $framework 'bin\exam.mjs') build $papers[0].FullName --target android
  }
} else {
  Write-Host "`n== 2/4 跳过内容编译 ==" -ForegroundColor DarkGray
}

Write-Host "`n== 3/4 Gradle 构建 ==" -ForegroundColor Cyan
Push-Location $android
try {
  $task = if ($Release) { 'assembleRelease' } else { 'assembleDebug' }
  $args = @($task, '--no-daemon')
  if ($Clean) { $args = @('clean') + $args }
  & .\gradlew.bat @args
  if ($LASTEXITCODE -ne 0) { throw "Gradle 构建失败（exit $LASTEXITCODE）" }
} finally {
  Pop-Location
}

Write-Host "`n== 4/4 产物 ==" -ForegroundColor Cyan
$apkDir = Join-Path $android 'app\build\outputs\apk'
$apks = Get-ChildItem -Path $apkDir -Recurse -Filter *.apk -ErrorAction SilentlyContinue |
  Sort-Object LastWriteTime -Descending
if (-not $apks) { throw "没找到 APK，检查上面的 Gradle 输出" }
foreach ($apk in $apks) {
  Write-Host ("  {0}  {1:N1} MB" -f $apk.FullName, ($apk.Length / 1MB)) -ForegroundColor Green
}
Write-Host "`n装到手机：adb install -r `"$($apks[0].FullName)`"" -ForegroundColor Green
