# Result cards and the Jev helper

The backend can show structured cards in a conversation. The Android app does
not draw them yet; this is the backend half.

## Cards

There are five card types and no others: **clarification** and **comparison**
(choice cards), and **progress**, **file** and **approval** (read-only cards).
The agent makes them with two tools:

- `present_choices` asks the owner to pick one of 1-12 prepared options.
  A comparison option needs a detail and an HTTP or HTTPS source.
- `show_card` shows a progress bar, a file in the current project, or a
  description of an approval request.

Each card is checked against a fixed schema. Extra fields are refused, so the
model cannot send markup, scripts, handlers or any other executable UI. Links
must be HTTP or HTTPS without credentials. A file card must name an existing
file inside the project.

Cards are saved with the session and come back after a restart.

**A card never approves or performs anything.** An approval card only describes
a request. Shell commands, servers and the other gated tools still raise the
existing approval prompt, and choosing an option grants no trust.

### Selecting an option

```
GET  /api/sessions/:id/cards
POST /api/sessions/:id/cards/:card/select   {"optionId":"opt-1"}
```

Both routes need the pairing token, like every other `/api/` route.
Option ids come from position (`opt-0`, `opt-1`, ...), not from labels. The
selection route returns:

- `404` for an unknown session or card
- `400` for a missing option, or one that is not on the card
- `409` for a card that has been answered, a card that a newer card or a new
  owner turn has made stale, or a read-only card

A selection can be claimed only once, even when two arrive together. The
backend then writes the selection message. It quotes the label as data, says
that the selection approves nothing, and sends it to the agent:

- an idle session starts a new turn
- a running session is steered
- a subscription CLI run takes it with its next prompt
- a bot's session goes through the bot's queue

If delivery fails, the card opens again.

## Jev (optional)

[Jev](https://docs.typesafe.ai/models) is a **hosted service** run by
TypeSafe. It is off by default. When it is on, `present_choices` asks Jev
whether the prepared card fits, and in which order to show the options.
PockoBots does not change the options themselves.

```
GET /api/settings/jev                     {"mode":"off","hasKey":false,"model":"jev-1.14.0"}
PUT /api/settings/jev   {"mode":"off"|"sample"|"live","apiKey":"...","clearKey":true}
```

- **off** sends nothing.
- **sample** makes no network call. It keeps the agent's control and order,
  so a sample result is never presented as Jev's judgment.
- **live** calls `POST https://api.typesafe.ai/v1/systemone` with the model
  `jev-1.14.0`. It needs a `TYPESAFE_API_KEY` that you supply. The key is
  stored in the app's private store (`jev.json`, mode 0600), like the other
  provider keys. It is never returned by the API, and it is redacted from
  state and events.

What is sent: the `context` the agent wrote for the card, and each option's
label, details and sources. Nothing else from the conversation, workspace or
files is sent. Options are referred to by position in the questions, so a label
taken from an untrusted page is never placed in the instructions.

What comes back is checked:

- The control must be one of the allowed ones: the prepared card type, the
  other card type where it is valid, or "answer in prose".
- Every option must be scored exactly once, in range.
- An answer with less than 0.5 confidence keeps the agent's prepared control.

The only effects are the card's type, its order, or answering in prose instead
of showing a card.

**Jev's score never affects permissions, spending or action authorization.**
The score stops inside `jev.ts`, and only an order leaves it.

Each call is limited to 5 seconds and follows the run's abort signal. If Jev
times out, is aborted, gets an HTTP error or gives an invalid answer, the card
is shown as the agent prepared it, and chat and tools keep working. Request
bodies are never logged.

TypeSafe's terms apply to the data it receives. See its
[data handling notes](https://docs.typesafe.ai/models#data-handling).

## Left out

- No Android UI. The SSE `card` event and the routes above are ready for it.
- No refining an earlier card with the same candidates, and no evidence
  checks of comparison excerpts against fetched pages. OpenMuse has both.
- No retries for Jev. One call keeps the 5-second limit simple. TypeSafe's
  errors page recommends retrying only 429 and 529.

The ranking and validation approach is adapted from
[OpenMuse](../THIRD_PARTY_NOTICES.md) (MIT).
