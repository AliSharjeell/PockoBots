# Backup model and bot effort

An owner can provision a secondary BYOK connection through the authenticated
`POST /api/settings/backup` endpoint. The profile is stored in the app's private
state with the same private-file permissions as ordinary model settings. No
backup credential is embedded in source, Git history or APK assets. Ordinary
state exposes availability and the model name, and redacts the saved backup key.

In Settings > Bring your own key, **Use MiniMax backup** switches all new bot
runs to the provisioned connection. Switching it off restores the original
BYOK or subscription configuration. The original settings are preserved.
Switching is blocked during active runs; it does not interrupt a task or
automatically retry failures. The toggle is off when first provisioned.

The two-line button at the top right of a bot chat opens a glass popup with
Floating bot and an Effort dropdown. Choices are Low, Medium, High, Extra high
and Max; saved effort applies to new tasks and the bot's Mobile Use runs.
Support depends on the selected model/provider. MiniMax M3.1 sends all five
levels through Anthropic `output_config.effort` and adaptive thinking.
Its CLI-style `[1m]` suffix is stripped from the API model ID while the configured
one-million-token context is retained. See the
[MiniMax protocol documentation](https://platform.minimax.io/docs/guides/text-generation).

Codex CLI receives its reasoning-effort configuration, with Max mapped to its
Extra high level. Claude CLI receives its effort option, with Extra high mapped
to High. Other CLI providers retain their native defaults. Models without
supported reasoning controls can ignore effort preferences.

Tests cover private backup provisioning, toggling/restart, key redaction and all
five MiniMax request payloads. The isolated Android popup regression repeatedly
opens, resizes and dismisses glass popups in dark/light mode without touching
owner conversations or taking screenshots.
