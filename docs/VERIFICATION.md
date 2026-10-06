# Verification — 2026-10-06

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
