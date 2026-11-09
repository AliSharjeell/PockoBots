param([switch]$Visible)
$ErrorActionPreference = 'Stop'

Write-Host "Starting Pixel AVD via start-pixel-dev.ps1..."
& (Join-Path $PSScriptRoot 'start-pixel-dev.ps1') -Visible

$pixelSdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$pixelAdb = Join-Path $pixelSdk 'platform-tools\adb.exe'

Write-Host "Installing latest v0.1 APK..."
$apkPath = Join-Path $PSScriptRoot '..\android\app\build\outputs\apk\debug\app-debug.apk'
if (Test-Path $apkPath) {
    & $pixelAdb -s emulator-5554 install -r $apkPath
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "APK install returned code $LASTEXITCODE"
    } else {
        Write-Host "APK installed successfully."
    }
} else {
    Write-Warning "APK not found at $apkPath"
}

Write-Host "Opening PockoBots v0.1 on the emulator..."
& $pixelAdb -s emulator-5554 shell am start --display 0 -n dev.pocket.agent/dev.pocket.agent.BotsActivity

Write-Host "Emulator is running and visible! Keeping session active..."
$emuProcs = Get-Process -Name "*qemu-system*", "*emulator*" -ErrorAction SilentlyContinue
if ($emuProcs) {
    Wait-Process -Id ($emuProcs | Select-Object -ExpandProperty Id)
} else {
    while ($true) { Start-Sleep -Seconds 10 }
}
