# Pocket Agent

An Android coding companion powered by [pi-agent-core](https://github.com/earendil-works/pi/tree/main/packages/agent). Chat in an Android app while the agent, workspace files, Git, shell, and npm development servers run locally in Termux on your phone.

Model requests go to the OpenAI, Anthropic, or compatible endpoint you configure. This is on-device agent execution, not bundled offline model inference. You can also connect an already-running, API-compatible model server on localhost without a key.

## Features

- Streaming chat, tool activity, multiple persistent conversations, and stop controls.
- Workspace file browsing, UTF-8 editing, and file creation with parent directories.
- Real pi agent tools for reading/writing files, bash, Git cloning, builds, and background servers.
- Terminal command output and managed development processes with stop controls and localhost previews.
- OpenAI Responses, OpenAI Chat Completions, and Anthropic Messages protocols.
- Custom base URL, model name, API key, token limits, and a real test request before saving.
- Microsoft Dev Tunnels login, port sharing, private access by default, and optional anonymous access.
- Android Keystore encryption for the app’s pairing token. Provider keys remain in Termux-private files and are never returned by the settings API.

## Install on your phone

Requires Android 8+ (API 26), a current Android System WebView, and [Termux from F-Droid](https://f-droid.org/packages/com.termux/) or [official Termux releases](https://github.com/termux/termux-app/releases). The Microsoft tunnel installer requires a 64-bit ARM or x86 device.

1. Install Termux and open it. For this private repository, authenticate GitHub on the phone:

   ```bash
   pkg update
   pkg install git gh
   gh auth login --hostname github.com --git-protocol https --web
   gh auth setup-git
   gh repo clone AliSharjeell/pocket-agent
   cd pocket-agent
   ```

2. Install and start the backend:

   ```bash
   bash scripts/install-termux.sh
   bash scripts/pair.sh
   ```

   The installer installs Node.js LTS (22.19+ required), Git, and bash; installs npm dependencies; builds the backend; and adds launcher scripts. It enables Termux’s `allow-external-apps` setting for the Android launcher. Keep the pairing token private.

3. Install `app-debug.apk` from a successful [GitHub Actions build](https://github.com/AliSharjeell/pocket-agent/actions): open the latest successful **Verify and build Android APK** run and download the **pocket-agent-debug-apk** artifact. Unzip it and install the APK. Android may ask you to allow installation from your browser or file manager. This is a development APK signed with a debug key, not a Play Store release.

4. Open Pocket Agent. Paste the pairing token and leave the port at **8787**. Tap **Connect**. Later, **Backend → Start backend in Termux** can start it. Grant Pocket Agent the Termux **Run commands** permission when asked. If the permission prompt is unavailable, use Android Settings → Apps → Pocket Agent → Permissions, or start the backend manually in Termux.

5. Open **Settings**. Choose provider and API protocol, enter the base URL, model, and API key, tap **Test API**, then **Save connection**. Testing makes a small inference request and may be billed by your provider. An empty key keeps the existing key for the same provider; switching providers clears it unless you supply a replacement. **Remove saved key** deletes it.

6. Start chatting. Agent shell commands ask for approval by default. You can allow commands for the current conversation in Settings. This trust setting resets when the backend restarts.

If GitHub access is not configured yet, the source can also be copied to a private Termux directory, then steps 2–6 apply.

## Provider examples

| Provider | API protocol | Base URL | Model example |
| --- | --- | --- | --- |
| OpenAI | OpenAI Responses | `https://api.openai.com/v1` | `gpt-4.1` |
| OpenAI-compatible service | OpenAI Chat Completions | Your service’s HTTPS `/v1` URL | Exact model ID from that service |
| Anthropic | Anthropic Messages | `https://api.anthropic.com` | `claude-sonnet-4-6` |
| Local model server | Matching OpenAI protocol | `http://127.0.0.1:11434/v1` | Installed model ID |

Compatibility includes tool calling and streaming; a text-only compatible endpoint can chat but cannot act as a coding agent. Use HTTPS for remote endpoints. Plain HTTP is allowed only for localhost. The UI currently saves one active connection; enter a new key when changing providers.

## Files, Git, and npm servers

The default workspace is `~/pocket-workspaces`. File paths and working directories in the UI are relative to it.

Examples in **Terminal**, with the working directory set to `.`:

```bash
git clone https://github.com/owner/project.git
```

Set the directory to `project` for subsequent commands:

```bash
npm install
npm run build
git status
git add src && git commit -m 'Update feature'
```

Set Git’s author in Termux once if you want to make commits:

```bash
git config --global user.name 'Your Name'
git config --global user.email 'you@example.com'
```

Use **Servers** for long-running processes. For a Vite project, choose directory `project`, port `3000`, and command:

```bash
npm run dev -- --host 127.0.0.1 --port 3000
```

Tap **Open** to preview in your phone’s browser. Process logs and stop controls remain in the app. The terminal is a command runner, not a PTY; interactive stdin, full-screen tools, and password prompts must be run directly in Termux. Background processes stop when the backend is shut down.

Android uses bionic rather than glibc: npm packages with native code may need Termux build packages or an Android-compatible binary. Android cannot automatically run every desktop executable or project dependency.

## Microsoft Dev Tunnels

Install the official CLI through a Debian proot because Microsoft’s Linux binary targets glibc:

```bash
cd ~/pocket-agent
bash scripts/install-devtunnel-termux.sh
~/.local/bin/pocket-agent-stop
~/.local/bin/pocket-agent-start
```

Open **Servers → Sign in**. The Terminal tab shows the device login URL and code; open the login link and finish authentication in your browser. Then enter your running server’s port and tap **Open tunnel**. Logs contain the generated HTTPS URL, and the app offers a link to open it.

**Allow anonymous visitors** makes the forwarded server accessible without login. Leave it off for private tunnel access. The agent control port is rejected by the tunnel API; share your project port, such as 3000. The tunnel requires internet access and remains running until stopped.

This integration uses the documented [Microsoft CLI commands](https://learn.microsoft.com/en-us/azure/developer/dev-tunnels/cli-commands). The ARM64/proot setup and authenticated tunnel creation still need validation on a physical Android phone. No Microsoft account was signed in during development. This build environment could not download the CLI from Microsoft’s short-link endpoint, so a real end-to-end tunnel was not tested here.

## Backend control and configuration

Start and stop manually:

```bash
~/.local/bin/pocket-agent-start
~/.local/bin/pocket-agent-stop
```

Logs: `~/.local/share/pocket-agent/backend.log`.

To change workspace or port for both manual and app launches, create `~/.config/pocket-agent/env`:

```bash
export POCKET_WORKSPACE="$HOME/my-projects"
export POCKET_PORT=8787
# Optional: export POCKET_STATE="$HOME/.local/share/pocket-agent"
```

Restart the backend after editing this file. If you change the port, change it in the app’s Backend screen too. To use Android shared storage, run `termux-setup-storage`, grant Termux storage access, and choose an accessible workspace such as `$HOME/storage/shared/Projects`. Keep the application checkout, npm dependencies, and Git working trees in Termux-private storage where possible: shared storage has filesystem limitations.

Disable battery optimization for Termux if Android stops development servers. The launcher requests a Termux wake lock, but Android may still kill background processes. Closing the UI alone does not shut down the backend.

Pairing and settings files live under `POCKET_STATE` with directory mode `0700` and file mode `0600`. Provider keys are protected by Android’s Termux app sandbox and filesystem permissions; they are not encrypted within Termux. Shell commands have full Termux account access, including shared storage when granted. The workspace path checks protect file tools from traversal and escaping symlinks, but the shell is not an OS sandbox.

## Develop and verify

Node.js 22.19+:

```bash
npm ci
npm run check
npm test
npm run build
npm start
# In a second terminal:
bash scripts/pair.sh
```

Open `http://127.0.0.1:8787` in a browser and pair. The backend listens only on loopback. Do not publish or tunnel the agent’s API or UI.

Browser flow checks:

```bash
npx playwright install chromium
npm run test:ui
```

Android APK: open `android/` in Android Studio, or install JDK 17, Android SDK platform/build-tools 35, and Gradle 8.11.1, then:

```bash
bash android/build.sh
```

APK output: `android/app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions runs backend checks, browser flows, Android compilation, and Android lint. It uploads an APK artifact for successful Android builds. Different debug signing keys across machines may require uninstalling the previous APK before installing a new build; uninstalling clears the app’s pairing data, while Termux files remain.

Integration tests run actual pi provider adapters against local mock OpenAI Chat Completions, OpenAI Responses, and Anthropic endpoints. They verify tool dispatch, approvals, cancellation, private settings, local Git cloning, and a real npm HTTP server. Browser checks cover mobile and desktop layouts. No paid provider keys or physical Android device were available during development.

## Project layout

- `backend/src`: authenticated Express API, pi agent sessions/tools, storage, workspace, and process manager.
- `web`: mobile UI served by the local backend; also usable in a browser.
- `android`: native Java application, Keystore pairing, WebView host, and Termux launcher.
- `scripts`: Termux installation, process control, pairing, and Dev Tunnels setup.
- `backend/test` and `ui-test`: integration and browser tests.

The source is MIT-licensed. pi-agent-core and pi-ai are maintained in [earendil-works/pi](https://github.com/earendil-works/pi) and are MIT-licensed; package notices and their licenses remain in npm dependencies. Microsoft Dev Tunnels is distributed separately under Microsoft’s terms.
