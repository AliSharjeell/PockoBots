# Skills

A skill is a folder with a `SKILL.md` file, in the common open format: YAML
frontmatter with `name` and `description`, then Markdown instructions. Other
files in the folder (scripts, references, templates) travel with it. Existing
skills install unchanged. The idea follows Hermes Agent's public description of
skills (MIT); this implementation is our own.

```markdown
---
name: release-notes
description: Write release notes from the commits since the last tag.
allowed-tools: bash read_file
verify:
  - The notes list every commit since the tag
---
# Steps
...
```

- `name`: 1–64 lowercase letters, digits or hyphens.
- `description`: 1–1024 characters.
- `allowed-tools` (or `requires`/`tools`): the tools the skill expects. Recorded
  and shown; it never grants anything.
- `verify` (or `verification`): optional verification steps, recorded.

The frontmatter parser handles plain and quoted values, `[a, b]` lists, `- x`
block lists and `>`/`|` text blocks. Nested maps such as `metadata:` are skipped.

## Progressive loading

A bot's prompt lists only each skill's name and description (descriptions cut
to 200 characters, the whole list capped at 4000 characters). A skill that
needs a tool the bot lacks is marked `needs unavailable: ...`. The bot loads the
full `SKILL.md`, or any file in the skill folder, with `use_skill`:

| Action | Does |
| --- | --- |
| `list` | Names, descriptions, required tools, verification steps |
| `view` | `SKILL.md`, or `path` inside the folder, plus the file list |
| `propose` | Create or edit a skill; saved as a pending change |

## Review and history

Skills a bot writes or edits are pending until the owner accepts them. A
proposal sends `{path: content}` for the files it changes; other files carry
over from the current version. Accepting moves the previous version into
history (the last 10 are kept). A proposal made against an older version is
refused; reject it and ask again. A revert restores an earlier version and puts
the current one into history, so a revert can be undone too.

## API

All routes need the pairing token.

| Route | Does |
| --- | --- |
| `GET /api/skills` | Skills, pending changes, history |
| `POST /api/skills` `{path}` | Install from an absolute local folder |
| `GET /api/skills/:name?path=` | Metadata, file list and one file (default `SKILL.md`) |
| `POST /api/skills/:name/revert` `{version?}` | Restore a version (default: the previous one) |
| `DELETE /api/skills/:name` | Delete the skill, its history and its pending changes |
| `GET /api/skill-changes/:id` | A pending change with per-file diffs |
| `POST /api/skill-changes/:id/accept` | Activate it |
| `POST /api/skill-changes/:id/reject` | Discard it |

Files live under `<state>/skills/` (`active/`, `pending/`, `history/`), with
metadata in `<state>/skills.json`.

## Security

Skill content is untrusted text.

- Installing copies bytes only. Nothing runs, file modes are not kept, `.git` is
  skipped.
- Paths with `..`, absolute paths, backslashes, drive letters and control
  characters are refused. A symlink is followed only to a regular file inside
  the skill folder; anything else fails the install.
- Caps: 256 KiB per file, 2 MiB and 100 files per skill, 8 levels deep, 200
  skills, 50 pending changes.
- A skill cannot grant a tool or permission. Bots keep exactly the tools and
  approvals they already have; running a skill's script still goes through the
  bot's own shell tool and its approval setting.

## Not in this release

No Android screen yet, no per-bot skill selection (every accepted skill is
listed to every bot), no installing from a URL or archive, and a proposal cannot
delete a file from a skill.
