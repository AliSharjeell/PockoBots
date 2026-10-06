import { mkdir, copyFile } from "node:fs/promises";
await mkdir("runtime", { recursive: true });
await copyFile("backend/runtime/terminal_bridge.py", "runtime/terminal_bridge.py");
