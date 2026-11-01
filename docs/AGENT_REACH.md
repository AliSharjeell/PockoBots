# Agent-Reach in PockoBots

Every existing and new bot receives `agent_reach` automatically, with no
Marketplace selection, pip installation or separate download. BYOK runs and
subscription CLI runs use the same tool through the existing tool bridge.
This is the real [Agent-Reach](https://github.com/Panniantong/Agent-Reach)
reader implementation, pinned to version 1.6.1, commit
`7a38047acf26a729cbb27851e3cb81639da86b29`.

## Using it

Bots discover available actions and their arguments with:

```json
{"operation":"channels"}
```

Then call a reader, for example:

```json
{"operation":"run","channel":"web","action":"search","arguments":{"query":"Python documentation","limit":3}}
```

```json
{"operation":"run","channel":"github","action":"read","arguments":{"target":"Panniantong/Agent-Reach"}}
```

The default Android bundle includes:

- Web search and page reading through upstream's Exa/Jina backends.
- Public GitHub repository search, README, issue and PR reading.
- YouTube metadata, search and available captions through bundled yt-dlp.
- V2EX readers and Bilibili public metadata/trending readers.
- Upstream public-web routes for supported actions on other platforms, labelled
  as public-web results rather than authenticated platform access.

Discovery lists all 14 upstream channels and reports availability per action.
Instagram, Xueqiu and Xiaoyuzhou currently have no enabled reader action in this
Android adapter. Desktop-browser actions, account import/login, Bilibili's extra
CLI, media downloads and paid Whisper transcription are not auto-installed or
enabled. Use an owner-authorized integration for private account information;
`github_cli` remains the preferred tool for the owner's GitHub repositories.

Readers execute locally, but **search and websites require the internet**.
Exa/Jina are hosted third-party services and receive submitted queries/URLs;
their free access and upstream sites can be limited, blocked or unavailable.
The adapter preserves real errors and source URLs and never invents results.
Retrieved page text is untrusted input, not an instruction source. This tool
does not open or control Android apps.

## Packaging and state

The APK's versioned backend overlay contains the launcher, pinned dependency
lock and a roughly 4 MB pure-Python archive. It uses the runtime's existing
Python, so existing app installations do not need to unpack Linux again.
First use verifies the archive's SHA-256, extracts it atomically and creates a
shared reader ledger under `POCKET_STATE/agent-reach`. All bots share that
installation; their project directories are untouched. The reader's separate
home does not import a desktop owner's browser sessions or credential files.
There is no automatic Agent-Reach setup, browser installation or update step.

Tool arguments travel over stdin, never through a shell. The wrapper bounds
input/output, cancels the Linux process group, and applies a 45-second hard
deadline around upstream's 35-second action budget. Large page text is truncated
and marked; discovery is available offline. Captions remain subject to actual
video availability and YouTube verification limits. Optional native yt-dlp
extras are not included; supported Node.js video metadata/caption reads use the
packaged runtime and pure-Python dependencies.

## Updating and verification

```powershell
python scripts/vendor-agent-reach.py
npm run check
npx tsx --test backend/test/agent-reach.test.ts backend/test/bots.test.ts backend/test/providers.test.ts backend/test/research-tools.test.ts
npm run build:android
```

The first command rebuilds the same archive from hash-pinned artifacts without
executing downloaded build hooks. Only `--update-lock` refreshes dependency
versions; changing Agent-Reach itself also requires updating the explicit Git
commit/version in the script. Never install `agent-reach` from PyPI: it is an
unrelated package. Review updated dependencies and all channel availability
before changing the pins. Keep Python compatibility with Debian's packaged
runtime (Python 3.11).

The upstream MIT license and dependency licenses remain inside the archive and
are also collected in `backend/runtime/agent-reach-notices.txt`, copied into the
APK's license assets. All artifact origins, exact versions and SHA-256 hashes
are in `backend/runtime/agent-reach-lock.json`.

Verified in Linux during integration: discovery of 14 channels, GitHub repository
reading, Exa web search, Jina page reading and YouTube video metadata. Regression
tests cover default tool registration, concurrent first use, corrupted bundles,
unsupported setup-dependent actions, argument validation, shell-free arguments,
timeouts, cancellation and missing Python. Live service availability can change.
