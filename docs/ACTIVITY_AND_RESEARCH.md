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
Results now pass a conservative keyword relevance check; unrelated feeds trigger
fallback instead of appearing as a successful search. Safe queries can reuse a
two-minute in-memory cache; time-sensitive queries do not. Requests are bounded
to eight seconds per provider, and errors distinguish verification, network and
relevance failures. This is a key-free online search tool, not an offline index.

Run `npx tsx scripts/check-search-live.ts` for an explicit network diagnostic.
The November 1, 2026 Pixel emulator run returned useful results for only one of
four queries: Pakistan passed; LocationManager documentation, AndroidLiquidGlass
and Lahore weather failed relevance or verification checks. These public sources
cannot currently be described as consistently reliable on this connection.

## Shared integrations and GitHub CLI

Every bot receives the installed integration inventory and the tools
`list_integrations`, `integration_tools` and `call_integration_tool`. Schemas are
discovered lazily, so connecting many apps does not fill every prompt or hit the
64-direct-tool limit. Disabled connections cannot be called. Tokens stay in
the private store and are redacted from results.

When explicitly asked by the owner, `add_integration` saves an HTTP or stdio MCP
configuration globally. Other bots can discover it immediately and it survives
restart. Supabase project-reference query parameters are supported; URL secrets
are rejected. OAuth approvals still require the owner to finish the browser
flow from Settings > Connections & sharing > Custom MCP connections.

GitHub uses the official checksum-verified Linux `gh` binary, not an unrelated
npm package. Sign in through Marketplace > GitHub, or choose **Enable GitHub CLI
for all bots** for a previously connected account. The private CLI account is
shared across bot workspaces and terminal sessions. Bots use `github_cli` without
reading credentials. Existing terminals can use **Restart terminal** to refresh
their environment. Terminal HTTP/HTTPS links are tappable, including wrapped
URLs. A PockoBots GitHub App registration is not required for this CLI flow.

## Incoming bot calls

Priority alerts use the bot avatar, large answer/decline controls and native
Android CallStyle notifications on supported versions. Answer reveals Markdown
and reads it with an installed offline voice; it is a local alert, not a carrier
call or live voice conversation. Full-screen intent and overlay permissions are
honored; Android still controls background launches. The call page opens directly
while the app is foreground, or under the owner-granted overlay/full-screen
exceptions, with the call notification as fallback. See
[Android activity-launch rules](https://developer.android.com/guide/components/activities/secure-bal).

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
