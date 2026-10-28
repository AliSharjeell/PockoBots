# Local bots

Pocket Agent 0.4 opens as a native messaging inbox. Create separate coding,
phone, or browser bots with their own names, instructions, workspace and chat.
Set your BYOK connection from Model settings. The runtime and tools execute on
the phone; inference still goes to your configured provider.

## Start a bot

Tap +, choose its type, and enter its instructions. Send a task in its chat and
leave the app. Tasks queue in order for each bot. Up to two bots run at once;
phone and browser tasks take turns controlling the device. Pause, resume or
cancel tasks from their conversation. Coding bots have independent workspaces;
Open workspace provides the existing editor, terminal, Git, npm and servers.
Shell commands require approval unless you explicitly enable trusted shell for
that bot. MCP tools inherit the access of their selected server.

Keep bots running enables the foreground runtime notification and restart on
boot. Queues, chats, roles and concise bot memory survive restart. An interrupted
running task becomes paused for review; already queued tasks remain queued.
Android can stop background work after force-stop, shutdown, battery restrictions
or low memory. This is best-effort continuous operation, not guaranteed 24/7 uptime.

## Proactive checks

Edit a bot to set a routine task, an interval (15 minutes to 7 days), and daily
run limit (default 24, max 144). Run routine now tests it immediately. Limits
count all starts, including ordinary messages and resumed tasks, on UTC days.
Missed intervals coalesce into one check instead of replaying every missed run.
The routine skips when that bot already has unfinished work. NO_UPDATE suppresses
unnecessary messages. Bots can retain short notes and delegate bounded tasks to
other bots; delegation depth is limited to three.

## Phone and browser tasks

Enable Android Accessibility and display-over-other-apps permissions in Mobile
Use setup. Keep the phone unlocked for automation. The floating island displays
progress, questions and results, and provides pause/stop/reply controls. The
native controller must be attached before the backend starts phone actions.
Waiting for a reply suspends access to device actions.

Browser bots use the phone's visible browser while Pocket Agent is in the
background. They do not create an invisible browser or bypass the lock screen.
Screenshots and selected screen text go to your configured model provider.

## Notifications and ringing alerts

Allow Android notifications for results and inline Reply. Replies queue real bot
tasks; failed delivery preserves a local draft to retry from the conversation.

Enable Allow priority ringing alerts separately for each bot. The bot can then
use notify_user with urgent=true. An urgent alert rings and vibrates for at most
60 seconds, with a private incoming-call-style screen when Android permits it,
including on the lock screen. Answer stops ringing, reveals the message and
starts Android TTS with an installed offline voice. Without a working voice,
the screen displays a clear text fallback. Install voice data through the bot's
Android voice setup button. No carrier call or voice conversation is involved.

On Android 14+, allow full-screen alerts using the bot settings button. Without
that access, use the priority notification's Answer action. Android notification
settings, alarm volume and Do Not Disturb still apply. Each bot can ring at most
once per ten minutes. Ordinary messages use notification replies without ringing.

## MCP connections

Open MCP connections to add a Streamable HTTP server (HTTPS, or localhost HTTP)
with optional bearer token, or a local stdio command with a JSON argument array.
Test the connection, then select it for each bot that should use its tools.
Local commands run inside the packaged Linux environment. Pause tasks before
changing connections; detach a deleted connection from any bot that used it.
Tokens stay in private app storage and are removed from public state and logs.
OAuth login, legacy SSE servers, resource/prompt browsing and custom stdio
environment-variable forms are not implemented in this release.

The implementation is original. See [OpenBot research](OPENBOT_RESEARCH.md)
for the inspected upstream revision, design comparison and license boundary.
