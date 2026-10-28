param([string]$Serial = 'emulator-5554', [string]$Avd = 'Pixel_10_Pro_XL')
$ErrorActionPreference = 'Stop'
$pixelSdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$pixelAdb = Join-Path $pixelSdk 'platform-tools/adb.exe'
$pixelEmulator = Join-Path $pixelSdk 'emulator/emulator.exe'
$pixelName = (& $pixelAdb -s $Serial emu avd name | Select-Object -First 1).Trim()
if ($pixelName -ne $Avd) { throw "Start $Avd in Android Studio first, and pass its serial with -Serial. No emulator was changed." }
$pixelKernel = (& $pixelAdb -s $Serial shell cat /proc/cmdline).Trim()
if ($LASTEXITCODE -ne 0 -or !$pixelKernel.Contains('page_shift=')) { throw 'Cannot read the emulator kernel options. No emulator was changed.' }
$pixelKernel = $pixelKernel -replace '\bpage_shift=\d+', 'page_shift=12'
if ($pixelKernel.Contains('"')) { throw 'Unexpected kernel options. No emulator was changed.' }
Write-Host 'Restarting the same Pixel AVD in 4 KB mode. User data is preserved.'
& $pixelAdb -s $Serial emu kill
Start-Sleep -Seconds 3
$pixelPort = $Serial -replace '^emulator-', ''
if ($pixelPort -notmatch '^\d+$') { throw 'Invalid emulator serial' }
Start-Process -FilePath $pixelEmulator -WindowStyle Hidden -ArgumentList @('-avd', $Avd, '-port', $pixelPort,
    '-no-snapshot-load', '-no-snapshot-save', '-qt-hide-window', '-grpc-use-token', '-qemu', '-append', ('"' + $pixelKernel + '"'))
