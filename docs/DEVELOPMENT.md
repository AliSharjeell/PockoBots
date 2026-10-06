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
or **Debug**. Android 14 / API 34 is the tested emulator configuration. Debug
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
| Agent, API, project management, Git, server tools | `backend/src/` | Repackage the x86_64 runtime, then Run again |
| PTY bridge | `backend/runtime/terminal_bridge.py` | Repackage the x86_64 runtime, then Run again |
| Included Linux tools | `runtime/Dockerfile`, `runtime/package.py` | Repackage the runtime, then Run again |

Native Java edits do not require rebuilding the Linux runtime. A normal IDE Run
keeps app data, projects, and settings; uninstalling or clearing app data removes
them. Changed runtime archives have a new checksum and are extracted at startup.
Old runtime directories are currently retained, so repeated runtime rebuilds may
require extra emulator disk space.

Backend development can also run independently on a Linux/WSL desktop:

```bash
npm ci
npm run dev
```

This watches TypeScript changes and serves the desktop backend/browser UI. It
does **not** automatically update the backend inside the Android app. Android
Studio's Java debugger does not attach to the Node backend.

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
