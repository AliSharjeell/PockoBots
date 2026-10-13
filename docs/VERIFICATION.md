# Verification

## Native sidebar and floating island v0.3.1 — 2026-10-13

All three Android instrumentation tests passed on the Android 14 x86_64 emulator
(140.44 seconds including first-launch runtime extraction). Coverage includes
the packaged coding runtime, real Artemis screenshots and gestures, background
task execution, island collapse/expand, and the actual floating Reply button.
The native UI journey also checks history navigation, provider switching while
advanced options are collapsed, API-key visibility, and both mode composers.
After the final timestamp-alignment adjustment, NativeUiSmokeTest passed again
in 13.852 seconds. Model requests use local fixtures, without paid credentials.

The [reviewed screenshots](UI.md) show the actual chat, drawer, BYOK screen,
Mobile Use composer, collapsed orb island, and expanded question panel.
The solving-orb engine and text shimmer are native Canvas implementations;
the thinking-orbs MIT attribution is included in both APKs.

Both architecture-specific builds pass. Final Android lint reports zero errors
and 33 warnings. Both APK signatures verify, and packaging includes the selected
runtime/native ABI, current backend, fonts, and attribution. Generated binaries
remain ignored build artifacts. Physical ARM64 devices and real provider accounts
remain outside the completed coverage.

| Debug APK | Bytes | SHA-256 |
| --- | ---: | --- |
| ARM64 v0.3.1 | 252354360 | `26fd31ee22b06b40333bb609c04278cc10d96ba11a9aadd691084399cf555e9b` |
| x86_64 v0.3.1 | 272675439 | `e5be26120b4f6799aa7dcc7f81a1c553b52f4cfab7a7b942d8b2302be486cf23` |

## Native Mobile Use v0.3 — 2026-10-13

The pinned Artemis native helper and adapted Flash-style task loop run inside
Pocket Agent. Android 14 x86_64 emulator coverage exercised real screenshots,
app launch, a grounded tap into Settings > Battery, background operation,
question handoff, native action suspension while waiting, reply, fresh screen
observation, and completion. The provider fixture received screenshot images;
no paid provider account was used.

Both Android instrumentation tests passed (32.358 seconds with the runtime
already extracted): MobileTaskSmokeTest and RuntimeSmokeTest. The latter also
checks the packaged coding agent, PTY, Git, npm server, and restart persistence.
All 11 backend integration tests passed against both the ordinary and
Android-bundled builds in Linux. TypeScript checks and both build targets passed.
Both architecture-specific APK builds and Android lint passed; lint reports
zero errors and 25 warnings.

The native mode switch, coding screen, and floating question panel were visually
checked against the requested zinc/Inter sidebar styling. The captured panel is
shown in [the emulator screenshot](images/mobile-use-panel.png). Android Settings
hides application overlays, so the question panel was captured on Home. Permissions
were restored after the instrumentation fixture.

Both APK signatures verify. Each APK includes only its selected native ABI,
the packaged runtime, the current small backend bundle, and attribution assets.
Generated APKs and runtime archives remain ignored build artifacts.

| Debug APK | Bytes | SHA-256 |
| --- | ---: | --- |
| ARM64 v0.3.0 | 252337681 | `6298422c6a18f323b21b188677c85fef47016add2a634593f170555a93f7ac5e` |
| x86_64 v0.3.0 | 272658760 | `624d846e1343fff133bd212ed3d6ee69483fe258f162c2f674f05b66d44ad0b9` |

Remaining coverage includes physical ARM64 phones, vendor battery/background
policies, real vision-provider credentials, and authenticated tunnel hosting.
The integration ports the native helper and Flash-style loop; it does not include
Artemis's desktop Python/ADB runner, Pro orchestration, OCR/explorer, or benchmarks.

## Historical standalone native Android v0.2 — 2026-10-06

The v0.2 implementation replaces the Android WebView/Termux pairing flow with native Java screens and an app-owned PRoot runtime. Both ARM64 and x86_64 runtime archives were built locally from `runtime/Dockerfile`, with checksum-verified Android libraries and upstream native source archives.

Completed backend checks: TypeScript compilation, ordinary Node build, Android backend bundle, and all nine integration tests against both backend builds. Tests dispatch real pi adapters against local OpenAI Chat Completions, OpenAI Responses, and Anthropic fixtures, exercise project isolation, and verify a real Linux PTY with resizing and Ctrl-C. These fixtures do not use paid provider accounts.

Android SDK 35 compilation and lint pass with JDK 17 and Gradle 8.11.1. Final lint reports zero errors and ten warnings, including vendored terminal reflection, programmatic view accessibility/constructors, localization, and the foreground service wake lock. APK v2 signing verification passes for both architecture-specific APKs.

On an Android 14 x86_64 emulator, a fresh APK installation automatically extracted the included runtime and launched its own backend without Termux. The packaged agent edited a project file using a local provider fixture. The native runtime executed Node 22.23.3, npm 10.9.9, Git 2.39.5, an interactive Bash PTY, a local Git clone, Ctrl-C, and an npm HTTP preview. Microsoft Dev Tunnels CLI 1.0.2094 launched and reported service metadata; authenticated tunnel hosting was not exercised.

The full packaged-app smoke test passed on the Android 14 emulator in 76 seconds from a fresh installation, including automatic runtime extraction, agent tool dispatch, terminal resizing and interactive input, Git cloning, npm HTTP serving, shutdown, restart, and persisted project/conversation state. PRoot shutdown uses SIGQUIT because its normal handler ignores SIGTERM; runtime launches record their own PID and wait for the previous process to exit.

Architecture-specific Gradle packaging was checked to include only the selected runtime archive and native-library ABI. The x86_64 debug APK is 271,249,519 bytes. Generated APKs and runtime payloads are build artifacts ignored by Git; all implementation changes are committed separately.

The ARM64 debug APK is 250,928,440 bytes. Its included runtime archive was checked against its packaged SHA-256 metadata, and APK signing verification passed. APK SHA-256: `c27dec66103d5af3ba52ae8902e4a6b119d89ab00d64e647a2a48a3824138553`. The x86_64 APK SHA-256 is `955bb5011b8977e91bf6aabdb5075154d8e2e29018ffee32b1c3b744f1244841`.

Manual emulator UI verification created `native-ui-check` through the native project dialog, opened its chat and terminal screens, and typed/executed `echo NATIVE_CANVAS_OK` through the terminal view with the phone keyboard open. The project prompt, wrapped command, output, cursor, and keyboard layout rendered correctly without a WebView or installed Termux app.

Remaining coverage: physical ARM64 phone installation; Android vendor battery/background behavior; real provider credentials; authenticated Dev Tunnels login/hosting; compatibility of individual third-party npm packages and native addons on Android. The ARM64 runtime is built, but execution coverage in this workspace is x86_64 Android emulation. Model inference is remote BYOK; project files, agent tools, terminals, and development servers execute on the device.

## Historical external-Termux v0.1 verification

The following record concerns the previous v0.1 release. Its APK and setup instructions are not the standalone v0.2 implementation.

Validated in the Linux build workspace:

- `npm run check`: passed with TypeScript 5.9.3.
- `npm test`: 7 integration tests passed. Actual pi adapters used against local mock OpenAI Chat Completions, OpenAI Responses, and Anthropic APIs.
- `npm run build`: passed.
- `npm run test:ui`: Android-mobile and desktop browser journeys passed.
- Shell syntax checks: all installation and launcher scripts passed `bash -n`.
- `:app:assembleDebug :app:lintDebug`: passed with JDK 17, Gradle 8.11.1, and Android SDK 35. Lint: 0 errors, 1 warning about the base adaptive icon’s monochrome tag; a separate Android 13+ icon provides that tag.
- `apksigner verify`: passed; APK has a v2 signature.
- APK metadata: `dev.pocket.agent`, version `0.1.0`, minimum API 26, target API 35.

The process tests perform a local Git clone and start a real npm HTTP server, fetch its response, stop the process group, and confirm the port is closed. Agent tests dispatch real file tools and verify shell approval and cancellation. Browser tests pair the UI, make a provider test request, stream chat, create/edit a file, run a command, and reload persisted conversations.

Not verified here: installation on a physical phone; Android background survival; Termux RUN_COMMAND permission behavior on each Android vendor; paid provider accounts; ARM64 Debian/proot Dev Tunnels execution; authenticated tunnel creation. Microsoft’s CLI download short link returned HTTP 403 in this workspace. The README explains the required phone setup and the tunnel integration uses Microsoft’s documented CLI commands.

GitHub access is now verified: the ChatGPT Codex Connector is installed for all repositories under `AliSharjeell`, with contents and workflows write permissions. `AliSharjeell/pocket-agent` is private and grants push access. The project history preserves the repository’s initial README commit through a merge.


## GitHub build and preview release

- GitHub Actions run [37416741177](https://github.com/AliSharjeell/pocket-agent/actions/runs/37416741177) passed backend and Android checks after fixing the obsolete Android SDK package in the setup action.
- The manual preview run [37417208402](https://github.com/AliSharjeell/pocket-agent/actions/runs/37417208402) passed backend, Android, and release jobs at commit `0fb40c643838ab2f25620d6617b212b290e62555`.
- The private [v0.1.0 preview release](https://github.com/AliSharjeell/pocket-agent/releases/tag/v0.1.0) is published, with `pocket-agent-debug.apk` attached from the verified GitHub build. The release tag points to the tested commit.
- Workspace-to-GitHub asset upload was rejected by the network path, so the APK was published by GitHub Actions using its repository-scoped token. Release publishing is available through manual workflow dispatch, after both build jobs succeed.
