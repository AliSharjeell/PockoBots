# Durable tasks

`backend/src/tasks.ts` keeps tasks that survive a crash or restart. It holds
state only; a worker (on the phone or the server) does the work and reports
back. Everything is saved to `tasks.json` in the state directory through the
atomic `Store`, before each call returns.

## Model

- **Task**: `id`, `title`, `owner` (`"phone"` or `"server"`, one at a time),
  `status`, `steps`, `requests`, `lease`, `error`.
- **Step**: `title`, `external` (it acts on the outside world), `status`
  (`pending`, `running`, `done`, `failed`, `uncertain`), `progress`, `receipt`.
- **Request**: an input question or an approval for one step. `open`,
  `answered` or `stale`.

| From | To |
| --- | --- |
| queued | running (claim), paused, cancelled |
| running | waiting_input, waiting_approval, paused, completed, failed, cancelled, queued (lease expired) |
| waiting_input | queued (answered), paused, cancelled |
| waiting_approval | queued (approved), failed (declined), paused, cancelled |
| paused | queued (resume), completed (reconciled), cancelled |
| failed | queued (retry), completed (reconciled), cancelled |
| cancelled | queued (retry) |

Anything else is rejected with 409.

## Rules

- Steps run in order. `startStep` saves the step as `running` *before* the
  worker acts.
- An `external` step starts only with an approved, unused approval request for
  that step. Each approval is consumed once.
- Only the one open request can be answered, once. Pause and cancel make open
  requests `stale`.
- `finishStep` saves a receipt. Retry resets failed steps and skips steps
  that already have a receipt.
- A step interrupted while `running` (pause, cancel, expired lease, or
  `failStep(..., uncertain=true)`) becomes `pending` if it is internal, or
  `uncertain` if it is external. While a step is uncertain the task cannot
  resume, retry or be claimed until the user calls `reconcile` (it happened →
  `done` with receipt; it did not → `pending`, needing a fresh approval).
- A `running` task holds a lease (`leaseMs`, 1–600 s, default 60 s). Every
  worker call names the `leaseId`. When the lease expires, `recover()` (run at
  startup, on `claim` and on `GET /api/tasks`) puts the task back in the
  queue, or pauses it if an external step was caught in flight. Workers renew
  well before expiry.
- Internal steps may run twice after a crash, so they must be safe to repeat.

## API

```ts
const tasks = new Tasks(store, publish); await tasks.init();
// owner
create({title, owner?, steps:[{title, external?}]}), list(), get(id), remove(id)
pause(id), resume(id), cancel(id), retry(id), handoff(id, owner)
answer(id, requestId, value /* string for input, boolean for approval */)
reconcile(id, stepId, happened: boolean, note?)
// worker
claim(id, owner, leaseMs?) -> {leaseId, task}; renew(id, leaseId, leaseMs?)
ask(id, leaseId, stepId, "input" | "approval", prompt) -> request
startStep(id, leaseId, stepId); progress(id, leaseId, stepId, note)
finishStep(id, leaseId, stepId, result); failStep(id, leaseId, stepId, error, uncertain?)
recover()
```

Each change publishes `{type: "task", task}` on `/api/events`.

## HTTP (pairing token required)

| Method | Path | Body |
| --- | --- | --- |
| GET | `/api/tasks`, `/api/tasks/:id` | |
| POST | `/api/tasks` | `{title, owner, steps}` |
| DELETE | `/api/tasks/:id` | finished tasks only |
| POST | `/api/tasks/:id/pause` · `resume` · `cancel` · `retry` | |
| PUT | `/api/tasks/:id/owner` | `{owner}` |
| POST | `/api/tasks/:id/requests/:request` | `{value}` |
| POST | `/api/tasks/:id/steps/:step/reconcile` | `{happened, note?}` |
| POST | `/api/tasks/:id/claim` | `{owner, leaseMs?}` |
| POST | `/api/tasks/:id/lease` | `{leaseId, leaseMs?}` |
| POST | `/api/tasks/:id/steps/:step/ask` | `{leaseId, kind, prompt}` |
| POST | `/api/tasks/:id/steps/:step/start` · `progress` · `finish` · `fail` | `{leaseId, note? , result?, error?, uncertain?}` |

## Limits

One `tasks.json` is rewritten on every change: at most 200 tasks, 50 steps
and 100 requests per task, one backend process. Beyond that, move to one file
per task or `node:sqlite`.
