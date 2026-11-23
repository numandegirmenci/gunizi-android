# Windows: PowerShell ile çalıştırın: powershell -ExecutionPolicy Bypass -File .\build-apk.ps1
# Gradle dağıtımı yalnızca resmi URL'den alınır ve resmi SHA-256 ile doğrulanır.
$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $MyInvocation.MyCommand.Path
$version = '8.13'
$checksum = '20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78'
$cache = Join-Path $env:USERPROFILE '.gradle\belgeizi-bootstrap'
$home = Join-Path $cache "gradle-$version"
$zip = Join-Path $cache "gradle-$version-bin.zip"
if (-not (Test-Path $cache)) { New-Item -ItemType Directory -Force -Path $cache | Out-Null }
if (-not (Test-Path (Join-Path $home 'bin\gradle.bat'))) {
    Write-Host 'Gradle indiriliyor (ilk kurulum)...'
    Invoke-WebRequest -Uri "https://services.gradle.org/distributions/gradle-$version-bin.zip" -OutFile $zip
    $actual = (Get-FileHash $zip -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $checksum) { Remove-Item $zip -Force; throw 'SHA-256 dogrulamasi basarisiz. Kurulum iptal edildi.' }
    Expand-Archive $zip -DestinationPath $cache -Force
    Remove-Item $zip -Force
}
if (-not $env:ANDROID_HOME -and -not $env:ANDROID_SDK_ROOT) {
    $sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
    if (Test-Path $sdk) { $env:ANDROID_HOME = $sdk }
}
if (-not $env:JAVA_HOME) {
    $studioJbr = 'C:\Program Files\Android\Android Studio\jbr'
    if (Test-Path $studioJbr) { $env:JAVA_HOME = $studioJbr }
}
Push-Location $project
try {
    & (Join-Path $home 'bin\gradle.bat') --no-daemon assembleDebug
    if ($LASTEXITCODE -ne 0) { throw "Derleme basarisiz (kod: $LASTEXITCODE)" }
    $apk = Join-Path $project 'app\build\outputs\apk\debug\app-debug.apk'
    Write-Host "\nAPK olusturuldu: $apk" -ForegroundColor Green
} finally { Pop-Location }
