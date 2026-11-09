// A vendor CLI that answers a request about the account's limits, the way an Agent Client
// Protocol agent and the Codex app server do.
import readline from "node:readline";

const resetsAt = Math.floor(Date.now() / 1000) + 3600;
const reader = readline.createInterface({ input: process.stdin });
reader.on("line", (line) => {
  let message;
  try {
    message = JSON.parse(line);
  } catch {
    return;
  }
  if (!("id" in message)) return;
  let result;
  if (message.method === "initialize") {
    result = { protocolVersion: 1, agentCapabilities: {} };
  } else if (message.method === "account/rateLimits/read") {
    result = {
      rateLimits: {
        limitId: "opencode",
        primary: { usedPercent: 42, windowDurationMins: 300, resetsAt },
        secondary: { usedPercent: 18, windowDurationMins: 10080, resetsAt: resetsAt + 86400 * 2 },
      },
    };
  } else {
    process.stdout.write(JSON.stringify({ jsonrpc: "2.0", id: message.id, error: { code: -32601, message: "Unknown method" } }) + "\n");
    return;
  }
  process.stdout.write(JSON.stringify({ jsonrpc: "2.0", id: message.id, result }) + "\n");
});
