# Mobile Use

Pocket Agent v0.3 has two native modes: **Coding** for project work, and
**Mobile Use** for tasks across Android apps. Both use the model connection in
Settings. Mobile Use should use a model that accepts images and tool calls.

## Start a task

1. Open **Mobile Use** and configure your BYOK connection in **Settings**.
2. Tap **Accessibility** and enable **Pocket Agent · Mobile Use** in Android.
3. Tap **Floating pill** and allow Pocket Agent to display over other apps.
4. Describe the task, tap **Start task**, and review the task disclosure.

Android requires you to enable these permissions yourself. On Android versions
that restrict accessibility for sideloaded apps, use the app's Android App info
menu to allow restricted settings before enabling accessibility.

Starting a task backgrounds the Pocket Agent activity. Its foreground controller
and local agent continue working, with a draggable rounded pill over other apps.
Tap the pill to expand progress, output, and controls. **Pause** stops device
actions; **Resume** continues from the current screen; **Stop** ends the task.
The notification also offers Pause and Stop.

When the agent needs information or a manual login, the panel expands with its
question. Device actions pause while it waits. Handle credentials in the target
app, then enter a reply such as "ready" and tap **Reply & continue**. Do not
enter passwords, PINs, API keys or payment secrets into the floating chat.

Results remain in the panel and the task history. Closing the controller pauses
an unfinished task. Closing/backgrounding the activity leaves the foreground
task controller running. Android force-stop, process death and device restart
interrupt execution; saved active tasks reopen as paused, with explicit Resume.
Android vendor battery restrictions may still terminate foreground processes.

## What runs on the phone

The APK includes the actual Artemis native accessibility helper, pinned to
[`google/artemis` commit 351ca842](https://github.com/google/artemis/tree/351ca8422f7b5b54e80a9c1ce03a222e02415b6b).
Its helper protocol and Flash observation/action prompt are adapted to Pocket
Agent's local TypeScript runner and existing BYOK adapters. No computer, ADB
connection, separate helper APK, Python installation, or remote Artemis server
is required for phone use.

The desktop Python runner, Pro multi-agent graph, desktop console, video capture,
OCR/explorer, benchmarks and unbounded continuous-monitoring modes are not
included. This is a native **Flash-style port**, not a claim of upstream benchmark
performance or full desktop feature parity. Original source, Apache-2.0 license,
pin and adaptation notes are in `third_party/artemis` and shipped in the APK.

The runner observes screenshots and accessibility elements, addresses current
element indices or explicitly described normalized coordinates, rejects stale
targets, and observes again to verify results. Available actions include taps,
long presses, text entry, scrolling, short tap sequences, app launching and
Back/Home/Recents. It has no ADB, shell, uninstall or force-stop tool. Completion
requires an explicit result tool after a fresh observation; an ordinary final
model message does not silently mark a task complete.

Only one mobile task controls the device at a time. A run pauses after 120 model
turns and can be resumed. Recent conversation groups and two screenshots are
kept in model context; persisted task history contains text, not a screenshot
archive. The model receives visible screen observations through your selected
provider. Task state and provider keys remain in private app storage.

Screenshots require Android 11+. Earlier supported Android versions provide
accessibility elements only. Protected/secure screens, unlabelled custom UI,
authentication, unsupported providers and inaccessible apps can require user
handoff. Real-world task reliability depends on the app, device and model.

## Developer transport and UI

The native helper listens on `127.0.0.1:18889`, separate from stock Artemis's
port. It accepts Pocket Agent's private token through `X-Artemis-Token` and
refuses screen/action requests when the foreground mobile controller has not
authorized device access. The action service is not an exported Android service.
Only the accessibility binding is exported, protected by Android's
`BIND_ACCESSIBILITY_SERVICE` permission. Both agent control ports are blocked
by the Dev Tunnels API.

The overlay is hidden during screenshot capture and gesture injection, and its
own window is excluded from the hierarchy. It does not become a target for the
agent. The app and pill use native Inter typography, zinc backgrounds, thin
borders, white primary actions and rounded surfaces, following the reference
`C:\AppsNew\form-filling-ai` sidebar styling. The Android screens remain native;
they do not embed that extension or a WebView.

Mobile execution is in `backend/src/mobile-tasks.ts`; action contracts and native
transport are in `mobile-tools.ts` and `artemis.ts`. The controller is
`MobileOverlayService.java`, with the mode/task UI in `MainActivity.java`.
Backend source edits now bundle into APK assets during Gradle builds when the
repository's npm dependencies are installed. Updating agent code does not
require rebuilding/unpacking the large Linux runtime image.
