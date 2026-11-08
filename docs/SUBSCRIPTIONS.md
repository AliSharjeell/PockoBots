# Use your own subscription

PockoBots supports vendor-owned CLI login instead of requiring a BYOK API key for
all conversations. Select an execution provider in Settings. The BYOK connection
is retained when switching providers; existing bot chats and files are retained.

## Available adapters

| Provider | Phone-side executable | Login | Headless execution |
| --- | --- | --- | --- |
| Codex / ChatGPT | codex | codex login --device-auth | codex exec with JSON events |
| Claude Code | claude | claude auth login | claude -p with stream-json |
| OpenCode | opencode | opencode auth login | opencode run --format json |
| Gemini CLI | gemini | Start gemini, then choose Sign in with Google | gemini --prompt with stream-json and cached credentials |
| Grok CLI | grok | grok --no-auto-update login | grok agent --single with streaming-json |
| Cursor CLI | cursor-agent | cursor-agent login | cursor-agent -p with text output |

Install downloads the vendor's CLI into the packaged Linux environment. Sign in
opens a real PTY and runs the vendor's own login. Complete the browser/code steps
yourself. Vendor credentials and token refresh remain in that CLI's account
storage on your phone. PockoBots does not copy credentials from desktop apps,
convert a subscription into an API key, or bypass a vendor's account checks.

Grok and Cursor are installed from the vendors' own installers rather than from
npm, so the install route hands npm's flags only to npm.

OpenCode serves free models to anyone. It can be used as soon as it is
installed, and signing in is offered as the way to add the models a plan
includes. Every other provider hands out nothing until the owner has signed in
with the vendor.

## Choosing a model

The selected model is separate from the BYOK model. The models a provider can
run are the vendor's to say, so each CLI is asked for its own list over the
protocol that CLI already speaks: Codex answers a `model/list` request on its
app server, and an Agent Client Protocol agent answers `session/new` with the
models it can run. The screen says whether the list it is showing came from the
CLI or from the shipped one.

A shipped list stands in only when the CLI could not be asked. It is a safety
net, not a claim that a provider is ready. `default` lets the CLI choose its own
default. The vendor decides subscription eligibility, model access, usage and
billing. The app's Installed or authenticated state is not a guarantee of
paid-plan access.

The provider and the model can be changed from the subscriptions screen or from
the menu in the top right of a chat.

CLI adapters are an original implementation. OpenBot's provider architecture was
inspected read-only as a reference; its PolyForm-licensed code was not copied.
Its brand marks were taken as path data, which is recorded in
[BRAND_MARKS.md](BRAND_MARKS.md).

## The same phone tools

Each execution receives a temporary local MCP server that forwards only that
run's declared PockoBots tools: file/server tools, selected connectors, notes,
notifications, teammate messages and phone control. The bridge checks both the
normal backend bearer token and a fresh per-run secret, validates tool arguments,
and removes the run when the CLI exits. No raw provider reasoning events are
stored in the conversation. Text results are streamed into the same chat.

Subscription bot execution requires shell access in that bot's preferences.
Phone execution still requires Accessibility, floating-island permission and an
unlocked screen. The phone task uses the same CLI adapter and MCP phone tools,
including question/reply handoff and the repeated-navigation guard.

Cancel stops the CLI process group and its MCP children. A provider error remains
a failed/paused task; it is not presented as successful completion.

## Explicit limits

Antigravity desktop has no verified adapter here. Gemini CLI is not presented
as an Antigravity subscription adapter. Freebuff and CommandCode have official
CLIs, but their PockoBots headless tool adapters remain unavailable, so neither
is listed in the catalogue. You can still install them in Main terminal with
`npm install -g freebuff` or `npm install -g command-code`; CommandCode
documents `cmd login` and `cmd -p`. This is terminal access, not integrated
bot-chat or phone-control support. Installed vendor binaries still need
compatibility with the device's packaged Linux runtime and architecture.

The Grok and Cursor invocations are taken from those vendors' own
documentation. Their tool bridges are not wired, so they answer a chat but do
not yet reach the phone tools the other adapters have.

Automated checks use a local fake vendor process and the real stdio/HTTP MCP
bridge. They verify tool dispatch, bridge expiry, argument checks, cancellation,
Gemini text assembly, error handling and chat persistence without an API key.
The model catalogue is checked against fixtures that speak the same wire
formats as the real CLIs. Live paid-account OAuth and actual vendor inference
have not been tested by these fixtures. Do not treat fixture success as evidence
that every subscription or Android CPU architecture is supported.

## Official references

- [Codex CLI commands and login](https://developers.openai.com/codex/cli/reference)
- [Claude Code programmatic execution](https://code.claude.com/docs/en/headless)
- [OpenCode CLI](https://opencode.ai/docs/cli/)
- [OpenCode local MCP configuration](https://opencode.ai/docs/mcp-servers/)
- [Gemini authentication, including cached headless credentials](https://geminicli.com/docs/get-started/authentication/)
- [Gemini headless events](https://geminicli.com/docs/cli/headless/)
- [Grok CLI](https://x.ai/cli)
- [Cursor CLI](https://cursor.com/docs/cli/overview)

References checked on 2026-11-09. Follow the current vendor instructions if CLI
flags or authentication requirements change.
