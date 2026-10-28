# Persistent local bots: research baseline

Reference clone: `https://github.com/nightly-labs/openbot`, inspected at
`75c2800d7ba0b6ebcaad41b9e26e5eed67197ff1` on 2026-10-28.
The sparse checkout is at `C:\tmp\pocket-openbot-source`.

Reviewed the README, agent drain/routine schedulers, queue control routes,
and MCP input validation. The useful product patterns are independent bot
identities and conversations, durable FIFO deliveries, coalesced missed
schedule occurrences, explicit pause/resume, and per-agent connector selection.

OpenBot's license is PolyForm Noncommercial 1.0.0. Pocket Agent uses an original
implementation of these general concepts; no OpenBot source is copied into this
MIT project. The implementation runs in Pocket Agent's existing phone-owned
Node runtime and uses Artemis for visible Android app and browser control.

Android foreground services support work while the activity is closed. They do
not guarantee execution after force-stop, battery restrictions, or shutdown.
Phone UI tasks share one device and therefore run serially. A browser operated
through Artemis is visible on the phone while Pocket Agent's activity stays in
the background; it is not an invisible second Android screen.
