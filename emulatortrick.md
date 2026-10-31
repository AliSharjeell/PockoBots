---
name: emulatortrick
description: Open PockoBots' existing Pixel Android emulator in a standalone floating Windows window, preserving app data and the required 4 KB runtime mode.
---

# Open the floating emulator

Use these instructions when the user wants to see the emulator while iterating on PockoBots. This file is the repository-local skill requested by the user; read it directly rather than assuming it is registered in the global skills catalog.

## Start the existing AVD

From the repository root (`C:\AppsNew\pocket-agent`):

```powershell
.\scripts\start-pixel-dev.ps1 -Visible
```

From its `android` subdirectory, use `..\scripts\start-pixel-dev.ps1 -Visible` instead. Copy only the command, without a PowerShell prompt or a leading `+` from an error transcript.

The helper finds the SDK using `ANDROID_HOME`, falling back to `%LOCALAPPDATA%\Android\Sdk`. Its defaults are the existing AVD `Pixel_10_Pro_XL` and serial `emulator-5554`. It reuses a connected emulator, waits for one already starting, and launches a stopped AVD as a standalone emulator window when `-Visible` is supplied. It does not detach an emulator embedded in Android Studio or foreground an existing window by itself.

The installed experimental Intel image normally boots with 16 KB pages. PockoBots' packaged Linux tools need 4 KB pages on this emulator. The helper checks the running AVD identity and kernel options, and restarts that same AVD with `page_shift=12` if necessary. It preserves user data and disables snapshot loading/saving. Do not use `-wipe-data`, clear app storage, or create a replacement AVD just to show the window.

## Open PockoBots

After the helper reports `Ready`:

```powershell
$pixelSdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$pixelAdb = Join-Path $pixelSdk 'platform-tools\adb.exe'
& $pixelAdb -s emulator-5554 shell am start --display 0 -n dev.pocket.agent/dev.pocket.agent.BotsActivity
```

If a different AVD/port is requested, pass `-Avd` and `-Serial` to the helper and use that same serial for ADB. Do not assume port 5554 belongs to the intended AVD without the helper's identity check.

## Leave desktop control to the user

Launching with `-Visible` opened the standalone floating Pixel window successfully on this PC; the user confirmed they could see it. The working flow is the PowerShell helper followed by the ADB app-launch command above.

Do not use Windows Computer Use, desktop screenshots, mouse/keyboard injection, or window activation to inspect, move, resize or foreground the emulator. The user handles its window and checks the UI. If it is behind another app, tell the user it is running and let them select it from the taskbar. Starting the emulator is not authorization to operate other desktop apps or phone accounts.

## UI iteration in this repository

For requested app UI changes, build the x86_64 debug APK and install it over the existing app, preserving data:

```powershell
.\android\gradlew.bat -p android --no-daemon :app:assembleDebug -PruntimeAbi=x86_64
if ($LASTEXITCODE -ne 0) { throw 'Build failed; do not install a stale APK.' }
& $pixelAdb -s emulator-5554 install -r .\android\app\build\outputs\apk\debug\app-debug.apk
if ($LASTEXITCODE -ne 0) { throw 'APK installation failed.' }
& $pixelAdb -s emulator-5554 shell am start --display 0 -n dev.pocket.agent/dev.pocket.agent.BotsActivity
```

Use a configured JDK 17 for Gradle. The user currently prefers to inspect UI changes themselves: build, install and show the app, without taking screenshots or performing visual QA unless asked. Commit and push the requested changes in small coherent commits, as authorized in this repository's ongoing task.

## If startup fails

Check `adb devices`, the helper's error, and `%TEMP%\pocket-Pixel_10_Pro_XL-*.err.log`. Wait for a starting/offline emulator rather than launching a duplicate. If ADB is hung, inspect the exact emulator process and AVD before considering a restart; do not kill unrelated Android Studio or QEMU processes. Report the actual failure if the helper cannot establish a healthy 4 KB device.
