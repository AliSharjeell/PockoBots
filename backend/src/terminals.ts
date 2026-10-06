import { spawn, type ChildProcessWithoutNullStreams } from "node:child_process";
import { randomUUID } from "node:crypto";
import { createInterface } from "node:readline";
import { fileURLToPath } from "node:url";

// Python's standard-library PTY runs inside the packaged Linux runtime.
// No native npm addon or external Termux service is required.
export class Terminals {
  private terminals = new Map<string, { child: ChildProcessWithoutNullStreams; output: string; exited: boolean }>();
  constructor(private publish: (event: unknown) => void) {}
  start(cwd: string, cols = 80, rows = 24) {
    if (this.terminals.size >= 8) throw Error("Close a terminal before opening another");
    const id = randomUUID();
    const helper = fileURLToPath(new URL("../runtime/pty.py", import.meta.url));
    const child = spawn("python3", [helper, cwd, String(cols), String(rows)], { stdio: "pipe" });
    const terminal = { child, output: "", exited: false };
    this.terminals.set(id, terminal);
    const append = (data: string) => {
      terminal.output = (terminal.output + data).slice(-256000);
      this.publish({ type: "terminal", id, data });
    };
    createInterface({ input: child.stdout }).on("line", (line) => {
      try { append(Buffer.from(JSON.parse(line).data, "base64").toString("utf8")); }
      catch { append("\r\nInvalid terminal response\r\n"); }
    });
    child.stderr.on("data", (data) => append(data.toString()));
    child.on("error", (e) => append("\r\n" + e.message + "\r\n"));
    child.on("close", () => { terminal.exited = true; this.publish({ type: "terminal_exit", id }); });
    return { id };
  }
  state(id: string) {
    const t = this.terminals.get(id);
    if (!t) throw Error("Terminal not found");
    return { id, output: t.output, exited: t.exited };
  }
  input(id: string, data: unknown, cols?: number, rows?: number) {
    const t = this.terminals.get(id);
    if (!t || t.exited) throw Error("Terminal is closed");
    if (typeof data !== "string" || data.length > 64000) throw Error("Invalid terminal input");
    if (cols !== undefined && (!Number.isInteger(cols) || cols < 10 || cols > 500 ||
        !Number.isInteger(rows) || rows! < 2 || rows! > 200)) throw Error("Invalid terminal size");
    t.child.stdin.write(JSON.stringify({ data: Buffer.from(data).toString("base64"), cols, rows }) + "\n");
  }
  stop(id: string) { this.terminals.get(id)?.child.stdin.end(); this.terminals.delete(id); }
  stopAll() { for (const id of this.terminals.keys()) this.stop(id); }
}
