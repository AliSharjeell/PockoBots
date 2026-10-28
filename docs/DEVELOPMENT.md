# Develop Pocket Agent in Android Studio

Open the source project at `C:\AppsNew\pocket-agent\android` using **File > Open**.
The repository root is the Node/backend project; the `android` directory is the
Android Studio project. The checked-in Gradle wrapper selects Gradle 8.11.1 and
verifies the downloaded distribution checksum.

In **Settings > Build, Execution, Deployment > Build Tools > Gradle**, choose
JDK 17 as the Gradle JDK. Install Android SDK Platform 35 through SDK Manager
if requested, then let Gradle sync finish. On this workspace, a tested JDK 17
is available at `C:\tmp\pocket-runtime-research\jdk17\jdk-17.0.20.1+1`.

Select the `app` run configuration and an x86_64 emulator, then click **Run**
or **Debug**. Android 14 / API 34 and the Pixel 10 Pro XL API 37 in 4 KB mode have been tested. Debug
supports Java breakpoints and variable inspection. Each Run builds and installs
the current source; you do not need to drag a prebuilt APK into the emulator.

The runtime archives and native libraries are already generated in this local
workspace. They are ignored by Git. A fresh clone needs the one-time runtime
packaging step described in the README before its first standalone app build.
Run it from the repository root in Linux/WSL:

```bash
python3 runtime/package.py amd64
```

That packaging command requires Linux/WSL, Docker, Python, curl, dpkg-deb, and
patchelf. It packages the Linux tools for the emulator; it is not a phone-side
installation step. The Android emulator does not need Termux.

## Where to make changes

| Change | Source | Update the emulator |
| --- | --- | --- |
| Native project/chat/settings/file/server screens | `android/app/src/main/java/dev/pocket/agent/MainActivity.java` | Run or Debug again |
| Native terminal input/rendering | `android/app/src/main/java/dev/pocket/agent/TerminalCanvas.java` | Run or Debug again |
| Runtime setup and lifecycle | `RuntimeInstaller.java`, `RuntimeService.java` in the same Java directory | Run or Debug again |
| Agent, API, Mobile Use, project management, Git, server tools | `backend/src/` | Run again; Gradle bundles the backend when npm dependencies are installed |
| PTY bridge | `backend/runtime/terminal_bridge.py` | Run again; bundled alongside the backend |
| Included Linux tools | `runtime/Dockerfile`, `runtime/package.py` | Repackage the runtime, then Run again |

Run `npm ci` once from the repository root with Node.js 22+ installed. Gradle's
`bundleBackend` task then rebuilds TypeScript changes into the small APK backend
assets. The runtime packager also generates those assets for Docker/CI builds.
The installed Linux runtime is reused across backend edits; the new backend is
bound into the guest runtime from a separate versioned private directory.

Native Java and backend edits do not require rebuilding the Linux runtime. A normal IDE Run
keeps app data, projects, and settings; uninstalling or clearing app data removes
them. Changed runtime archives have a new checksum and are extracted at startup.
Old runtime directories are currently retained, so repeated runtime rebuilds may
require extra emulator disk space.

Backend development can also run independently on a Linux/WSL desktop:

```bash
npm ci
npm run dev
```

This watches TypeScript changes and serves the desktop backend/browser UI.
For the Android app, Run builds the new backend into the APK; it is not a live
connection to `npm run dev`. Android Studio's Java debugger does not attach to
the Node backend.

## Logs and checks

Use Android Studio **Logcat** for native Java exceptions. The Node/PRoot log is
`/data/data/dev.pocket.agent/files/runtime.log`, accessible through Device
Explorer for a debug installation or this PowerShell command:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell run-as dev.pocket.agent cat files/runtime.log
```

To build only the emulator architecture from PowerShell:

```powershell
cd C:\AppsNew\pocket-agent\android
.\gradlew.bat :app:assembleDebug -PruntimeAbi=x86_64
```

To run the packaged-runtime integration test with an x86_64 emulator connected:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -PruntimeAbi=x86_64
```

Use a dedicated test emulator for that integration test. It expects no saved
real provider key and changes its test project's settings while exercising a
local provider fixture. For ordinary development, use Run/Debug and your own
project instead.

`NativeUiSmokeTest` also exercises the history drawer, provider switch, API key
visibility, and both composers. UI fixtures save PNG previews in the test app's
private `files` directory. Gradle's connected-test runner removes its installation
afterward; to retain previews, install the debug and androidTest APKs with `adb
install -r`, then run `adb shell am instrument -w
dev.pocket.agent.test/androidx.test.runner.AndroidJUnitRunner` directly. Retrieve
the PNG bytes with `adb exec-out run-as dev.pocket.agent cat files/ui-chat.png`
using a binary-safe subprocess stream. Reviewed captures are also checked into
[the UI gallery](UI.md).


## Pixel 10 Intel emulator startup

The experimental Android 17/API 37.2 ps16k x86_64 image fails with the packaged
4 KB Linux runtime. This caused Environment stopped (255). For this existing
Pixel AVD, run this from the repository root (the script also starts it when off):

```powershell
.\scripts\start-pixel-dev.ps1
```

The script starts the selected AVD if needed, verifies its name, restarts it with
page_shift=12 when required, and waits for Android to boot with 4096-byte pages.
It preserves user data and leaves an already working 4 KB emulator running.
Select that running device and Run in Android Studio. From a terminal inside
the android directory, use `..\scripts\start-pixel-dev.ps1` instead.
It starts without snapshots, so repeat this after launching the experimental AVD
normally. An ordinary 4 KB x86_64 image is also suitable. The runtime now shows an
actionable setup error for experimental 16 KB Intel images. This does not establish
support for physical ARM64 devices using 16 KB pages.

SDK Manager also needs NDK 27.2.12479018; Gradle pins it for the small native host
compatibility shim. Sources and GPL notices for the modified x86_64 PRoot loader
are bundled in the APK. No Linux runtime repackaging is needed for bot UI or
backend changes.

BotsActivity is the native inbox/profile/thread UI; BotNotifications,
BotReplyReceiver, BotAlertService and BotAlertActivity handle reply and ringing
flows. backend/src/bots.ts provides durable queues and routines;
backend/src/connectors.ts provides MCP stdio and Streamable HTTP clients.
BotsSmokeTest exercises these together on an emulator using local provider
fixtures. It requires an empty bot inbox and no real model key, restores provider
and accessibility settings, and leaves four sample bots after success.
