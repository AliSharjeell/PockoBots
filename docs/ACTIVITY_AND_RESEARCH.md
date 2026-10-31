# Activity, research and teammate updates

Working avatars breathe, tilt and blink. Working labels shimmer, and elapsed
timers advance independently of incoming tokens. The current activity panel
stays open while a run is active; completed tool calls are compact, with full
inputs and outputs available on tap. Earlier run details remain accessible.
The floating phone-control island uses the same animated bot avatar. Home uses
the avatar alone to indicate work, keeping timestamps and unread badges separate.
The native UI takes interaction inspiration from
[Beautiful UI](https://www.beautifului.dev/), particularly its loading states,
expandable traces and compact tool chips. Its website code is not imported.

Provider thinking is displayed only when the provider supplies it. Sessions
checkpoint partial text, thinking and tool activity. Restarted runs restore
paused; they are not represented as still executing after their process died.

## Code changes

File writes and shell tools compare actual before/after file contents. Activity
shows file names with green added and red removed line counts; tap to read a
colored patch. Shell snapshots exclude generated/dependency directories, binary
files and symlinks, and are bounded to 128 text files, 4 MB and 4,096 entries.
Individual snapshot files are limited to 256 KB. Files outside this snapshot
are omitted rather than assigned invented counts. A tool captures up to 12 file
changes, with a 24,000-character patch preview per file; full counts remain.
External vendor CLI commands that do not pass through these tools do not report
these patches. Large/pathological diffs may be omitted if computation times out.

Files is named after the bot and resets to its project root when opening a bot.

Some providers can return a normal stop containing thinking but no answer.
PockoBots now requests continuation for those turns, up to three times. Repeated
empty replies and turn-limit exits become explicit failures with saved progress,
rather than successful tasks with a generic empty-response message.

## Teammate status and steering

`get_bot_status` reads a teammate's task, state, current tool and recent public
response. It does not send a message, stop a session or modify the target queue.
`steer_bot` is for an owner-requested change, not a progress check. Owner messages
sent while a BYOK agent is running use its existing steering queue at a turn/tool
boundary. Pending updates are saved and removed when consumed. CLI providers
that lack this live steering interface receive a queued follow-up, without
aborting their current process. Group tools retain their membership restrictions.

## Web search

All bot profiles include `web_search`. It requests public Bing search feeds,
with DuckDuckGo Lite as an alternate source, and returns actual titles, URLs,
snippets, provider and retrieval time. No search API key is required. Availability
and indexing depend on these external services; verification/rate-limit pages
are not presented as results. Configured search MCP connectors remain available.
Results are untrusted data, not instructions. No phone-browser control is needed.

## Approximate location

Enable **Settings > Permissions & background > Approximate location** and grant
Android's permission yourself. Sharing is off by default. It runs with its own
foreground notification and can be switched off from the same row. Android
background-location permission is not requested.

`get_current_location` reads a recent approximate fix only for a location-specific
question. It includes accuracy, timestamp and timezone. A bot may send that
context to its selected model for the requested local task. Missing permission,
no fix or a fix older than ten minutes results in an explanation asking for a
city or a fresh fix. No IP-based guess is substituted. Emulator/mock locations
are identified; they do not establish the physical owner's location.

Android references:
[location permissions](https://developer.android.com/develop/sensors-and-location/location/permissions)
and [LocationManager](https://developer.android.com/reference/android/location/LocationManager).

Regression tests cover empty-turn recovery, real-loop steering, read-only status
checks, progress recovery, search parsing/provider failure, and location opt-in,
freshness and emulator handling. They do not grant device permissions or perform
paid inference with the owner's key.
