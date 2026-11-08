// A stand-in for the Codex app server. It answers a `model/list` request the way `codex app-server`
// does, so the discovery path can be tested without the real CLI.
import { createInterface } from "node:readline";

const lines = createInterface({ input: process.stdin });
lines.on("line", (line) => {
  let message;
  try {
    message = JSON.parse(line);
  } catch {
    return;
  }
  if (message.method === "model/list") {
    process.stdout.write(
      JSON.stringify({
        id: message.id,
        result: {
          data: [
            { model: "gpt-6-luna", displayName: "GPT-6 Luna", description: "Fast and efficient." },
            { model: "gpt-5.6-sol", displayName: "GPT-5.6 Sol" },
          ],
        },
      }) + "\n"
    );
    return;
  }
  if (message.method === "shutdown") {
    process.exit(0);
  }
});
