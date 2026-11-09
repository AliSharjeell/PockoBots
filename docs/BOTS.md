# Unified local bots

Pocket Agent 0.5 uses one native bot interface. Create a bot using only its name
and instructions. Every bot can code, use phone apps, browse, run shell commands,
use selected MCP tools, remember notes and send notifications. There is no
Coding/Mobile Use/Browser mode selector. Existing bots retain their workspace,
chat and queue when migrated to the unified tool set.

## First launch

The Setup & permissions checklist appears once after updating to this interface.
It shows notification, full-screen alert, Accessibility and floating-island
access and opens the corresponding Android settings. Install an offline Android
voice using Android voice data. Granting these OS permissions requires your own
taps; the app does not bypass Android consent. Phone tasks need an unlocked
screen. Coding remains available when phone permissions are missing.

Shell commands without per-command prompts and priority ringing alerts are on
by default for new and migrated bots, as requested. Change either in Bot profile
> Tools, alerts & routines. BYOK settings apply to every bot; save a connection
before sending tasks. The app preserves a draft when a model connection is missing.

## One conversation and workspace

Chat renders Markdown, tables, code and clickable links. Chat / Terminal / Files
stay inside the bot interface. The file area uses directory rows and opens a file
editor. The terminal is a real PTY in that bot's project directory, with input,
resize, paste, control keys and persistent shells.

The sidebar contains all bots, Settings, MCP connections, a bot's MCP tool
selector, Servers & sharing, Setup & permissions and Main terminal. Main terminal
starts in /workspace and shares the packaged environment with every bot. Use it
for installing CLIs or managing repositories. Installed tools use phone storage
and network access; no separate Termux app is needed.

Bots run in the background by default, with no Keep bots running toggle or
default daily run cap. Their durable queues retain order. Two bot runs can run
at once; phone control is serialized across bots. Pause/resume/cancel controls
appear for unfinished work. Old failed jobs stay in history but do not clutter
the conversation with permanent failed-job cards. Force-stop, shutdown, battery
restrictions and low memory can still interrupt Android services. Interrupted
work becomes paused for review after runtime restart.

## Phone and browser work

The model chooses use_phone for a concrete phone/browser task, then continues
in the same conversation with the verified result. The native floating island
attaches automatically once permissions are enabled and the screen is unlocked.
The bot asks questions in chat and the island; waiting for a reply suspends
phone actions. A paused controller can reattach after permissions are enabled.

Browser work happens in the phone's visible browser while Pocket Agent is in
the background. Screenshots and selected screen text go to your configured
model provider. It is not an invisible browser or an offline language model.
A repeated navigation guard pauses after three attempts to open the same app
or URL within one minute, instead of looping through a welcome/sign-in screen.
Complete the browser's setup if necessary, then resume or ask the bot to retry.

## Browser tabs

A bot's browser has tabs, so a lookup no longer costs the page in front. A bot
opens one with `browser_new_tab`, looks at what is open with
`browser_list_tabs`, brings another one to the front with `browser_switch_tab`
and closes one with `browser_close_tab`. `browser_navigate` takes a `tab` as
well, so a page can be opened in a tab of its own.

The owner sees the same tabs in the browser screen: the strip under the address
bar shows every tab, a tap brings one to the front, the mark on the tab in
front closes it, and the plus sign opens another one. A tab in the back keeps
running, so a form that is half filled in survives a lookup.

## Previews and public links

Local preview links such as http://127.0.0.1:3000 open on the device running the
server. To access an emulator server from a separate phone, open Servers & sharing,
sign in to Microsoft Dev Tunnels, and share the server's port. The logs show the
actual clickable public URL. Allow anyone with the link is an explicit sharing
choice. Agents can also use share_port and report its actual URL or login blocker;
they are instructed not to invent public links. External sharing needs network
access and the tunnel provider's authentication. Public account login was not
performed by the automated local-fixture checks.

## Routines, notifications and ringing

Optional proactive routines are in Bot profile > Tools, alerts & routines. Set a
task and interval (15 minutes to 7 days); 0 disables the routine. Missed intervals
coalesce into one check. NO_UPDATE suppresses unnecessary notifications.

Normal notifications support inline Reply. Priority alerts ring/vibrate for at
most 60 seconds and show a private incoming-call-style screen when Android permits,
including while locked. Answer stops ringing, reveals the message and uses an
installed offline Android TTS voice. Voice failure or slow initialization shows
a text fallback. Each bot can ring at most once per ten minutes. Android alarm
volume, Do Not Disturb, notification settings and full-screen permissions apply.
This is an app alert, not a carrier phone call or voice conversation.

## MCP tools

Add Streamable HTTP connections (HTTPS, or localhost HTTP) with optional bearer
token, or local stdio commands with JSON argument arrays. Test the connection and
select its tools for a bot in the sidebar. Tokens remain in private app storage.
Pause tasks before changing a connection. OAuth login, legacy SSE, resource/prompt
browsing and custom stdio environment forms are outside this release.

See [OpenBot research](OPENBOT_RESEARCH.md) for the inspected reference and original
implementation's license boundary.


## One filesystem for every bot

Every bot uses the same `/workspace` directory on Android. Files shows that
one main directory, with no Shared/Bot files switch. Bot terminals start there
as well, using separate shell sessions. New bots do not create isolated project
directories. Existing project folders, including older `bot-*` folders and the
older `shared` folder, stay intact and are accessible to all bots.

Bots should work inside named project folders and coordinate overlapping edits.
The `shared_workspace` compatibility tool now points to this same main directory;
reads return a revision and writes may include `expectedRevision` to reject
stale edits. Its shell commands retain the bot's shell approval setting.

## Scheduled calls and reminders

Bots can use `schedule_reminder` to create, list or cancel saved reminders. ?Call me in 10 minutes? saves a one-time call; ?call me daily at 5 PM? saves a daily 17:00 call in the phone timezone. An explicit IANA timezone can override this. Calls use the existing bot avatar and incoming-call screen; `urgent: false` sends a notification instead. The model finishes after saving and does not sleep until the reminder is due.

Schedules and their delivery state persist across runtime restarts. Missed occurrences coalesce into one reminder when the runtime resumes. Daily reminders keep local calendar time across daylight-saving changes, skipping nonexistent times and avoiding duplicate calls on the same local date. Delivery requires the on-device runtime to remain running; Android notification, ringing and full-screen permissions still apply. Pausing an individual bot's task does not cancel its saved reminders. Ask the bot to list or cancel reminders explicitly.

## External Webhooks mode

Other applications or background servers running on the phone (such as computer vision Python scripts, OpenCV detection models, or local automation daemons) can send webhooks to trigger any bot by name or ID.

### Webhook configuration
Open **Settings > Connections & sharing > External webhooks** to review or change:
- **Webhook URL:** `http://127.0.0.1:18878/api/webhooks/bot` (or `.../api/webhooks/bot/:nameOrId`)
- **Webhook secret:** Optional or required token, passable in header `X-Webhook-Secret`, `Authorization: Bearer <token>`, or query `?secret=<token>`
- **Allow phone apps without secret:** When enabled, scripts running on `127.0.0.1` do not need the secret token.

### Triggering a bot call from an external app
For example, a computer vision fire detector script can trigger an immediate ringing bot call:

```bash
curl -X POST http://127.0.0.1:18878/api/webhooks/bot \
  -H "Content-Type: application/json" \
  -d '{
    "bot": "Fire Guard",
    "message": "Fire detected in camera feed!",
    "call": true
  }'
```

Or using Python from an external app on the phone:
```python
import requests

requests.post("http://127.0.0.1:18878/api/webhooks/bot", json={
    "bot": "any bot name",
    "message": "Fire detected!",
    "call": True
})
```

Supported JSON fields:
- `bot`: Any bot name (case-insensitive) or bot ID.
- `message` (or `text` / `prompt`): The event description or prompt.
- `call` or `urgent` (boolean) or `action: "call"`: Immediately rings the phone's full-screen bot call/alert screen and posts the message to the bot's conversation.
- `action: "notify"`: Triggers a high-priority Android notification and posts the message.
- `action: "message"` (default): Delivers the message directly to the bot chat to start processing.
