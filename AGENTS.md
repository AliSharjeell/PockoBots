# AGENTS.md

How to build, test and release PockoBots. Read this before changing the Android
app, the backend, or cutting a release.

## What this repository is

- `android/` — the native Android app (Java + Kotlin + Compose). The Gradle
  project is the `android` directory; the repository root is the Node project.
- `backend/` — the TypeScript backend that runs inside the app's packaged Linux
  runtime. It serves the API the app talks to and holds the agent's tools.
- `runtime/` — the packaging script for the Linux tools the app unpacks.
- `third_party/` — vendored components: the Artemis helper, the PRoot loader,
  icons.

## Prerequisites

| Tool | Version |
| --- | --- |
| Node.js | 22.19 or newer |
| JDK | 17 (the Gradle wrapper rejects anything else) |
| Android SDK Platform | 36 |
| Android NDK | 27.2.12479018 (pinned by Gradle) |
| Gradle | 8.13, from the checked-in wrapper |

A tested JDK 17 lives outside the repository at
`C:\tmp\pocket-runtime-research\jdk17\jdk-17.0.20.1+1` on the development
machine. Set `JAVA_HOME` to it before invoking Gradle.

The packaged Linux runtime (`android/app/src/main/assets/runtime/*.zip`) is
generated, not committed. A fresh checkout needs it once, on Linux/WSL with
Docker, Python 3, curl, dpkg-deb and patchelf:

```bash
python3 runtime/package.py arm64   # use amd64 for an x86_64 emulator
```

Both `arm64-v8a` and `x86_64` runtime archives are needed for a universal APK.

## Build the APKs

The backend is bundled into the APK by Gradle's `bundleBackend` task, so a
backend edit needs a rebuild — no separate runtime repackaging.

### Standard edition

No accessibility service is declared, so a sideload is not treated as an
accessibility-capable app.

```powershell
$env:JAVA_HOME = 'C:\tmp\pocket-runtime-research\jdk17\jdk-17.0.20.1+1'
.\android\gradlew.bat -p android --no-daemon :app:assembleDebug
```

### Mobile Use edition

Same app, its own label and manifest. `-PenableMobileUse` swaps in
`android/app/src/main/AndroidManifest-mobileuse.xml`.

```powershell
$env:JAVA_HOME = 'C:\tmp\pocket-runtime-research\jdk17\jdk-17.0.20.1+1'
.\android\gradlew.bat -p android --no-daemon :app:assembleDebug -PenableMobileUse
```

**That manifest declares the Artemis accessibility service**, which is what
makes this edition the phone-control build. Declaring
`BIND_ACCESSIBILITY_SERVICE` is what Google Play Protect's fraud checks block,
so the release notes have to carry the way around it: turn off *"Scan apps with
Play Protect"* for the install, or use **Files > More details > Install
anyway**. The Standard manifest declares no accessibility service and installs
without any of that.

### One ABI, or both

Omitting `-PruntimeAbi` builds a universal APK with both runtimes, which
installs on any device and is what the release ships. Naming one ABI makes a
much smaller APK for day-to-day work:

```powershell
.\android\gradlew.bat -p android --no-daemon :app:assembleDebug -PruntimeAbi=x86_64
.\android\gradlew.bat -p android --no-daemon :app:assembleDebug -PruntimeAbi=arm64-v8a
```

Both write to the same output path, so copy the APK before building the other
ABI:

```powershell
Copy-Item .\android\app\build\outputs\apk\debug\app-debug.apk .\pockobots-v0.2-x86_64-debug.apk
```

## Install and run on the emulator

```powershell
.\scripts\start-pixel-dev.ps1 -Visible
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s emulator-5554 install -r .\android\app\build\outputs\apk\debug\app-debug.apk
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s emulator-5554 shell am start --display 0 -n dev.pocket.agent/dev.pocket.agent.BotsActivity
```

`scripts/run-pixel-session.ps1` does the whole sequence in one command.

The helper checks the AVD identity and the page size: the packaged Linux tools
need 4 KB pages, so an experimental 16 KB Intel image is restarted with
`page_shift=12`. Never use `-wipe-data` or clear the app's storage to get out
of a bad state; it holds the runtime, the projects and the settings.

## Checks

```powershell
npm run check     # TypeScript types
npm test          # the backend suite (node:test)
```

The backend suite is slow: the shell and npm-server journeys take minutes, and
two of them fail on Windows for reasons that have nothing to do with the code
(the POSIX permission bits in the private-settings test, and a locked temp
folder in the npm-server test). Treat those two as the known baseline.

Android instrumented tests need a connected emulator and a free device:

```powershell
.\android\gradlew.bat -p android --no-daemon :app:connectedDebugAndroidTest -PruntimeAbi=x86_64
```

## Cut a release

1. Bump `versionCode` and `versionName` in `android/app/build.gradle`.
2. Build both editions as universal APKs (above) and copy each one out before
   building the other, because they share the output path.
3. Name them `pockobots-v<version>-debug.apk` and
   `pockobots-mobileuse-v<version>-debug.apk`.
4. Hash both files (`Get-FileHash`/`sha256sum`) and write the checksums
   into the notes.
5. Write the notes: keep the shape of the previous release (the warning, which
   edition to install, the SHA-256 checksums, how to install) and add what
   changed.
6. Tag and publish:

```powershell
git tag -a v<version> -m "PockoBots v<version>"
git push origin main --tags
gh release create v<version> .\pockobots-v<version>-debug.apk .\pockobots-mobileuse-v<version>-debug.apk --title "PockoBots v<version>" --notes-file <notes-file>
```

The upload of two universal APKs takes a while, and the release stays a draft
until it finishes. Check it with `gh release view v<version>`.

Never sign a release with anything other than the development key, and never
put a vendor API key or a real owner credential into a build, a fixture or a
note.

## Conventions

- The app never claims more than it can do. A provider that did not answer
  shows no reading rather than a reading of zero, and a shipped model list is
  labelled as a fallback, not as the provider's own answer.
- A vendor CLI is asked over the protocol that CLI speaks. Do not add a flag
  that only one CLI would grow.
- Credentials stay in the vendor CLI's own storage on the phone. Never import
  them, never copy them into settings, never log them.
