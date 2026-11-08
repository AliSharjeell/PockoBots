// A stand-in for an Agent Client Protocol agent. It answers `initialize` and `session/new` the way
// OpenCode, Cursor and the Grok CLI do, so the discovery path can be tested without the real CLIs.
import { createInterface } from "node:readline";

const lines = createInterface({ input: process.stdin });
lines.on("line", (line) => {
  let message;
  try {
    message = JSON.parse(line);
  } catch {
    return;
  }
  if (message.method === "initialize") {
    process.stdout.write(JSON.stringify({ jsonrpc: "2.0", id: message.id, result: { protocolVersion: 1 } }) + "\n");
    return;
  }
  if (message.method === "session/new") {
    process.stdout.write(
      JSON.stringify({
        jsonrpc: "2.0",
        id: message.id,
        result: {
          sessionId: "session-1",
          models: {
            availableModels: [
              { modelId: "vendor/model-one", name: "Vendor Model One", description: "The first model." },
              { modelId: "vendor/model-two", name: "Vendor Model Two" },
              { modelId: "vendor/model-one", name: "A repeat that must not appear twice" },
            ],
          },
        },
      }) + "\n"
    );
    return;
  }
  if (message.method === "shutdown") {
    process.exit(0);
  }
});
