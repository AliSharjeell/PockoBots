param([string]$Serial = 'emulator-5554', [string]$Avd = 'Pixel_10_Pro_XL')
$ErrorActionPreference = 'Stop'
$pixelSdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$pixelAdb = Join-Path $pixelSdk 'platform-tools/adb.exe'
$pixelEmulator = Join-Path $pixelSdk 'emulator/emulator.exe'
if (!(Test-Path -LiteralPath $pixelAdb) -or !(Test-Path -LiteralPath $pixelEmulator)) { throw 'Android SDK tools are missing. Set ANDROID_HOME to your Android SDK directory.' }
if ($Serial -notmatch '^emulator-(\d+)$') { throw 'Use an emulator serial such as emulator-5554.' }
$pixelPort = [int]$Matches[1]
if ($pixelPort -lt 5554 -or $pixelPort -gt 5682 -or $pixelPort % 2) { throw 'Emulator port must be even and between 5554 and 5682.' }
if ($Avd -notmatch '^[a-zA-Z0-9_.-]+$') { throw 'Use the AVD identifier from emulator -list-avds.' }
$pixelAvds = @(& $pixelEmulator -list-avds)
if ($LASTEXITCODE -ne 0 -or $pixelAvds -notcontains $Avd) { throw "AVD '$Avd' was not found. Available AVDs: $($pixelAvds -join ', ')" }

function Test-PixelConnected {
    $pixelDevices = @(& $pixelAdb devices)
    return [bool]($pixelDevices | Where-Object { $_ -match ('^' + [regex]::Escape($Serial) + '\s+device\s*$') })
}
function Wait-Pixel([bool]$Booted) {
    $pixelDeadline = (Get-Date).AddSeconds(120)
    do {
        if (Test-PixelConnected) {
            if (!$Booted) { return }
            $pixelBoot = (& $pixelAdb -s $Serial shell getprop sys.boot_completed | Out-String).Trim()
            if ($pixelBoot -eq '1') { return }
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $pixelDeadline)
    throw "Emulator startup timed out. See $env:TEMP\pocket-$Avd-*.err.log and check Android Studio Device Manager."
}
function Start-Pixel([string]$Stage, [string]$Kernel = '') {
    $pixelArguments = @('-avd', $Avd, '-port', $pixelPort, '-no-snapshot-load', '-no-snapshot-save', '-grpc-use-token')
    if ($Kernel) { $pixelArguments += @('-qemu', '-append', ('"' + $Kernel + '"')) }
    Start-Process -FilePath $pixelEmulator -WindowStyle Hidden -ArgumentList $pixelArguments `
        -RedirectStandardOutput "$env:TEMP/pocket-$Avd-$Stage.out.log" `
        -RedirectStandardError "$env:TEMP/pocket-$Avd-$Stage.err.log"
}

if (!(Test-PixelConnected)) {
    # Wait for an offline device instead of launching a duplicate on the port.
    $pixelDevices = @(& $pixelAdb devices)
    if (!($pixelDevices | Where-Object { $_ -match ('^' + [regex]::Escape($Serial) + '\s+') })) {
        Write-Host "Starting $Avd. User data is preserved."
        Start-Pixel 'bootstrap'
    } else { Write-Host 'Waiting for the emulator already starting on this port...' }
    Wait-Pixel $false
}
$pixelName = (& $pixelAdb -s $Serial emu avd name | Select-Object -First 1 | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $pixelName -ne $Avd) { throw "The emulator on $Serial is '$pixelName', not '$Avd'. Pass the correct -Serial; no emulator was restarted." }
$pixelKernel = (& $pixelAdb -s $Serial shell cat /proc/cmdline | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or !$pixelKernel.Contains('page_shift=')) { throw 'Cannot read the emulator kernel options. No emulator was restarted.' }
if ($pixelKernel.Contains('"')) { throw 'Unexpected kernel options. No emulator was restarted.' }
$pixelPages = (& $pixelAdb -s $Serial shell getconf PAGE_SIZE | Out-String).Trim()
if ($pixelPages -ne '4096') {
    $pixelKernel = $pixelKernel -replace '\bpage_shift=\d+', 'page_shift=12'
    Write-Host 'Restarting the same Pixel AVD in 4 KB mode. User data is preserved.'
    & $pixelAdb -s $Serial shell sync
    if ($LASTEXITCODE -ne 0) { throw 'Could not flush emulator storage. No emulator was restarted.' }
    & $pixelAdb -s $Serial emu kill
    if ($LASTEXITCODE -ne 0) { throw 'Could not stop the emulator. Close it in Device Manager and retry.' }
    Start-Sleep -Seconds 4
    Start-Pixel '4k' $pixelKernel
}
Write-Host 'Waiting for Android to finish booting...'
Wait-Pixel $true
$pixelPages = (& $pixelAdb -s $Serial shell getconf PAGE_SIZE | Out-String).Trim()
if ($pixelPages -ne '4096') { throw "Emulator still reports page size '$pixelPages'. See the emulator logs in $env:TEMP." }
Write-Host "Ready: $Avd on $Serial, page size 4096. Select this running device in Android Studio and click Run."
