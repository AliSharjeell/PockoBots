# Memory, goals and page checks

Backend only for now; the Android screens come later. Every route sits behind
the pairing token like the rest of `/api`.

## Memory (`backend/src/memory.ts`)

- **Personal context**: the owner's name and preferred tone.
  `PUT /api/memory/context {name?, tone?}`.
- **Facts**: short statements the owner (or a bot) wants remembered. Each fact
  records its `source` (`user` or `model`, with the bot's id and name),
  `validFrom`, and `supersededAt` once a correction replaces it.

| Route | Does |
| --- | --- |
| `GET /api/memory?history=true&q=text` | context and current facts; `history` adds superseded wording, `q` is a plain-text search |
| `POST /api/memory/facts {text}` | add a fact |
| `PUT /api/memory/facts/:id {text}` | correct a current fact; the old wording stays as history |
| `DELETE /api/memory/facts/:id` | delete the fact and its whole history |
| `GET /api/memory/export` | everything stored, as a JSON download |

Deleting is complete: the fact and every earlier wording leave the file, the
export and the prompts.

Every bot prompt (BYOK, subscription CLI and Mobile Use, all built in
`Bots.profile`) ends with an "Owner context" section: name, tone and the facts
whose words overlap the task most, then the most recent ones, within 3000
characters. The section says it is data, not instructions. Facts a bot saved
are labelled "saved by bot X, unconfirmed".

Bots get two tools: `memory_save` (stored as `source: model`, passed through the
usual secret redaction) and `memory_search`. Search is plain text: no
embeddings, no graph. The older per-bot `remember` notes are unchanged.

## Goals (`backend/src/tracking.ts`)

`GET/POST /api/goals`, `PUT/DELETE /api/goals/:id` (`title`, `status`:
active, paused or done, `milestones`), and
`PUT /api/goals/:id/milestones/:milestone {done}`.

## Page checks

`POST /api/watches {botId, url, kind, text?, below?, above?, intervalMinutes?}`

- `changed`: alert when the page's visible text changes.
- `text`: alert when `text` appears.
- `price`: alert when the first US-dollar price (after `text`, if given) goes
  below or above the threshold.

`PUT /api/watches/:id {status: active|paused}`, `GET /api/watches`,
`DELETE /api/watches/:id`. Interval 15 minutes to 7 days, default 60.

Checks run from the bots' existing one-second schedule tick (no second timer)
and alert through the bot's normal notification (`Bots.alert`, non-ringing).
A condition alerts once when it becomes true, not on every check while it
stays true. A failing check backs off (doubling, at most a day) and sends one
"keeps failing" alert after three failures. Checks of a deleted bot are
dropped. While the bot engine is off, nothing is checked.

### Fetch guard

The URL is the owner's, so `safeFetchText` treats it as hostile:

- http and https only, no credentials in the URL;
- private, loopback, link-local, CGNAT, multicast and reserved addresses are
  refused, both for IP literals and for every address a name resolves to; the
  check runs inside the connection's DNS lookup, so a rebinding answer cannot
  slip past it;
- redirects are followed by hand (at most five), and each target is checked
  again;
- the body is capped at 1 MB and the whole fetch times out after 15 seconds.

Fetched text never reaches a model. It is reduced to a hash, a yes/no or a
number, and alerts use fixed wording with the URL only.

## Left out

- Android UI.
- Memory in plain (non-bot) sessions; only bot prompts carry it.
- Goal progress in prompts and agent tools for goals and page checks.
- CSS selectors for prices: the first price on the page (or after the anchor
  text) is used.
