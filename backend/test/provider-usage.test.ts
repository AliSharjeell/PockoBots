import { test } from "node:test";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import { tmpdir } from "node:os";
import { parseUsageWindows, readUsage, usageWindowLabel } from "../src/provider-usage.js";

test("a provider's CLI is asked about the account's limits", async () => {
  const reading = await readUsage("opencode", "OpenCode", {
    command: process.execPath,
    args: [fileURLToPath(new URL("./fixtures/usage-cli.mjs", import.meta.url))],
    cwd: tmpdir(),
    env: process.env,
    timeoutMs: 8000,
  });
  assert.equal(reading.reported, true);
  assert.equal(reading.windows.length, 2);
  assert.equal(reading.windows[0].label, "5-hour limit");
  assert.equal(reading.windows[0].usedPercent, 42);
  assert.equal(reading.windows[1].label, "Weekly limit");
  assert.ok((reading.windows[0].resetsAt ?? 0) > 0);
});

test("a provider that cannot be asked reports no usage instead of a reading of zero", async () => {
  const reading = await readUsage("claude", "Claude", {
    command: process.execPath,
    cwd: tmpdir(),
    env: process.env,
  });
  assert.equal(reading.reported, false);
  assert.equal(reading.windows.length, 0);
  assert.match(reading.note, /does not report usage limits/);
});

test("a CLI that cannot be started says so", async () => {
  const reading = await readUsage("opencode", "OpenCode", {
    command: "pockobots-no-such-cli",
    cwd: tmpdir(),
    env: process.env,
    timeoutMs: 4000,
  });
  assert.equal(reading.reported, false);
  assert.match(reading.note, /Install the provider's CLI/);
});

test("the windows of a reading are read in the shapes providers answer with", () => {
  const camel = parseUsageWindows({
    rateLimits: {
      primary: { usedPercent: 10, windowDurationMins: 300, resetsAt: 1800 },
      secondary: { usedPercent: 90, windowDurationMins: 43200, resetsAt: 3600 },
    },
  });
  assert.equal(camel.length, 2);
  assert.equal(camel[0].label, "5-hour limit");
  assert.equal(camel[1].label, "Monthly limit");

  const snake = parseUsageWindows({
    rateLimits: { primary: { used_percent: 55, window_minutes: 1440, resets_in_seconds: 600 } },
  });
  assert.equal(snake.length, 1);
  assert.equal(snake[0].usedPercent, 55);
  assert.equal(snake[0].label, "Daily limit");
  assert.ok((snake[0].resetsAt ?? 0) > 0);

  const byId = parseUsageWindows({
    rateLimitsByLimitId: {
      codex: { primary: { usedPercent: 5, windowDurationMins: 300 } },
      gpt: { primary: { usedPercent: 7, windowDurationMins: 300 } },
    },
  });
  assert.equal(byId.length, 2);
});

test("a window is named the way a person would say it", () => {
  assert.equal(usageWindowLabel(300), "5-hour limit");
  assert.equal(usageWindowLabel(10080), "Weekly limit");
  assert.equal(usageWindowLabel(43200), "Monthly limit");
  assert.equal(usageWindowLabel(1440), "Daily limit");
  assert.equal(usageWindowLabel(60), "Hourly limit");
  assert.equal(usageWindowLabel(null), "Plan limit");
});
