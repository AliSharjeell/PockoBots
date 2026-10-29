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

Install downloads the vendor's CLI into the packaged Linux environment. Sign in
opens a real PTY and runs the vendor's own login. Complete the browser/code steps
yourself. Vendor credentials and token refresh remain in that CLI's account
storage on your phone. PockoBots does not copy credentials from desktop apps,
convert a subscription into an API key, or bypass a vendor's account checks.

The selected model is separate from the BYOK model. Use default to let the CLI
choose its own default, or enter a model identifier supported by that provider.
The vendor decides subscription eligibility, model access, usage and billing.
The app's Installed or authenticated state is not a guarantee of paid-plan access.

CLI adapters are an original implementation. OpenBot's provider architecture was
inspected read-only as a reference; its PolyForm-licensed code was not copied.

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

Antigravity desktop, Freebuff and CommandCode are shown as unavailable until a
supported CLI/protocol is verified. Gemini CLI is not presented as an Antigravity
subscription adapter. Installed vendor binaries still need compatibility with
the device's packaged Linux runtime and architecture.

Automated checks use a local fake vendor process and the real stdio/HTTP MCP
bridge. They verify tool dispatch, bridge expiry, argument checks, cancellation,
Gemini text assembly, error handling and chat persistence without an API key.
Live paid-account OAuth and actual vendor inference have not been tested by these
fixtures. Do not treat fixture success as evidence that every subscription or
Android CPU architecture is supported.

## Official references

- [Codex CLI commands and login](https://developers.openai.com/codex/cli/reference)
- [Claude Code programmatic execution](https://code.claude.com/docs/en/headless)
- [OpenCode CLI](https://opencode.ai/docs/cli/)
- [OpenCode local MCP configuration](https://opencode.ai/docs/mcp-servers/)
- [Gemini authentication, including cached headless credentials](https://geminicli.com/docs/get-started/authentication/)
- [Gemini headless events](https://geminicli.com/docs/cli/headless/)

References checked on 2026-10-29. Follow the current vendor instructions if CLI
flags or authentication requirements change.
