# Pocket Agent

A native Android coding app with its own packaged development environment.
Install one APK, let it unpack its included tools, add your model connection,
create or clone a project, and start coding in chat. An installed Termux app,
manual backend setup, and pairing tokens are not required.

The agent, project files, Bash, Git, Node.js, npm, Python, build tools and
development servers run on your phone. Model requests go to your BYOK provider;
this does not bundle an offline language model. Microsoft Dev Tunnels provides
optional internet sharing for project ports.

## Use on your phone

1. Install the standalone debug APK for your device: `arm64-v8a` for most current
   phones, or `x86_64` for a matching emulator/device. Android 8+ is required.
   The old **v0.1.0 APK still requires Termux**; use a build of v0.2.0 or later.
2. Open Pocket Agent. First launch unpacks the coding environment from the APK
   into private app storage. Allow several minutes and approximately 1–2 GB of
   free storage for the tools and your projects. Setup does not download a runtime.
3. Open **Settings**, choose your API protocol, and enter your base URL, model and
   key. **Test connection** makes a small inference request; **Save connection**
   stores it. An empty key retains the saved key for the same provider.
4. Open **Projects → Create project / clone repository**. Enter a project name and
   optionally a Git URL. A clone opens the native terminal, including credential
   prompts for private repositories.
5. Use **Chat** to build or change your project. File tools operate relative to
   the selected project. Shell and server tools ask for approval unless you enable
   trust for the conversation. Trust resets when the runtime restarts.

**Terminal** opens an interactive Bash PTY in the selected project directory.
Use Git, npm, SSH, nano and other included tools. It supports input, resizing,
colors, cursor control, scrollback, paste, arrow keys, Tab, Escape, Ctrl-C and
Ctrl-D. Use the **Ctrl** toggle for other shortcuts, such as nano's Ctrl-O and
Ctrl-X. Long-press the terminal to copy its transcript. Long-running commands
can run here; **Servers** also offers managed
development processes with logs, previews and stop controls.

**Files** browses and edits project files. **Conversations** reopens saved chats
for the selected project. Configure your Git name and email in **Settings**
before committing.

For internet previews, start your server in **Servers**, choose its port, and use
**Sign in** followed by **Share this port**. Complete the displayed device login
in your browser. Tunnel access is private unless you enable anonymous visitors.
The agent control port cannot be shared through this API. The Dev Tunnels CLI is
included; an account and internet access are needed for tunnel hosting.

The runtime runs as an Android foreground service with a notification. Closing
the UI keeps development processes running. **Stop coding environment** or the
notification's stop action shuts them down. Reopen the app to restart. Android
may still stop processes under memory or battery pressure.

Project files, chats, keys and Git configuration persist in private app storage
across runtime restarts and upgrades. Uninstalling the app removes them. Provider
keys are protected by the app sandbox and private file permissions; the backend
stores them without additional file encryption. Shell commands have full access
to the app's runtime account. External model calls send the conversation and tool
results to the configured provider.

## Build the standalone APK

Requirements: Linux or WSL, Docker (with ARM64 emulation for cross builds), Python
3, curl, dpkg-deb, patchelf, JDK 17, Gradle 8.11.1 and Android SDK 35.

```bash
python3 runtime/package.py arm64   # phone runtime, or amd64 for x86_64
cd android
gradle --no-daemon :app:assembleDebug :app:lintDebug
```

Output: `android/app/build/outputs/apk/debug/app-debug.apk`. Runtime packaging
uses a Debian/Node image and checksum-pinned Android PRoot packages. The APK
contains the runtime archive and executable libraries; no phone-side installer
commands are needed. A build fails if the runtime is missing. `-PcompileOnly` is
available for source compilation/lint during development, and must not be used
to distribute an APK.

If both runtime architectures are generated, the default APK contains both.
Use `gradle :app:assembleDebug -PruntimeAbi=arm64-v8a` (or `x86_64`) for a smaller
APK containing only the selected architecture. Rename or copy the resulting APK
before building another architecture, since both builds use the same output path.

GitHub Actions builds separate ARM64 and x86_64 APK artifacts and runs the
packaged runtime smoke test in an Android emulator. To test locally with a
connected x86_64 emulator and its matching runtime:

```bash
cd android
gradle --no-daemon :app:connectedDebugAndroidTest
```

Backend checks (run in Linux for POSIX shell and PTY tests):

```bash
npm ci
npm run check
npm test
npm run build
```

The browser UI and standalone Node backend remain available for development.
The Android UI is native Java and does not load a WebView. Previous external
Termux installation instructions are archived in [docs/LEGACY_TERMUX.md](docs/LEGACY_TERMUX.md).
See [docs/VERIFICATION.md](docs/VERIFICATION.md) for validation and remaining
device coverage.

Pocket Agent source is MIT licensed. Terminal emulator/renderer sources retain
their upstream license and notices under `android/terminal`. Runtime components
retain their own licenses, including Debian package copyright files and npm
package licenses. PRoot and its libraries are separately licensed; see
[runtime/NOTICE.md](runtime/NOTICE.md) for upstream sources and versions.
