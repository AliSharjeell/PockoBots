import { mkdir, copyFile } from "node:fs/promises";
await mkdir("runtime", { recursive: true });
export const runtimePayloads = ['terminal_bridge.py', 'agent_reach_launcher.py', 'agent-reach-lock.json', 'agent-reach-vendor.zip'];
for (const name of runtimePayloads) await copyFile('backend/runtime/' + name, 'runtime/' + name);
