import { spawn, type ChildProcess } from "node:child_process";
import { randomUUID } from "node:crypto";
export type Job = {
  id: string;
  label: string;
  command: string;
  cwd: string;
  kind: "command" | "server" | "tunnel";
  port?: number;
  status: "running" | "exited";
  exitCode: number | null;
  output: string;
  created: number;
};
export class Processes {
  jobs = new Map<string, Job>();
  private children = new Map<string, ChildProcess>();
  constructor(private publish: (event: unknown) => void) {}
  start(
    command: string,
    cwd: string,
    kind: Job["kind"] = "command",
    port?: number,
    label = command,
  ): Job {
    if (!command.trim() || command.length > 16000)
      throw Error("Enter a command of at most 16000 characters");
    if (
      [...this.jobs.values()].filter((x) => x.status === "running").length >= 12
    )
      throw Error("At most 12 processes can run at once");
    const job: Job = {
      id: randomUUID(),
      label,
      command,
      cwd,
      kind,
      port,
      status: "running",
      exitCode: null,
      output: "",
      created: Date.now(),
    };
    const child = spawn("bash", ["-c", command], {
      cwd,
      detached: true,
      stdio: ["ignore", "pipe", "pipe"],
      env: { ...process.env, TERM: "dumb", FORCE_COLOR: "0" },
    });
    this.jobs.set(job.id, job);
    this.children.set(job.id, child);
    const append = (chunk: Buffer | string) => {
      job.output = (job.output + chunk.toString()).slice(-128000);
      this.publish({ type: "process", job });
    };
    child.stdout?.on("data", append);
    child.stderr?.on("data", append);
    child.on("error", (e) => append(e.message));
    child.on("close", (code) => {
      job.status = "exited";
      job.exitCode = code;
      this.children.delete(job.id);
      this.publish({ type: "process", job });
    });
    this.publish({ type: "process", job });
    // Retain a bounded history of completed jobs.
    if (this.jobs.size > 60)
      for (const [id, j] of this.jobs) {
        if (j.status === "exited") {
          this.jobs.delete(id);
          break;
        }
      }
    return job;
  }
  stop(id: string) {
    const child = this.children.get(id);
    if (!child?.pid) return;
    const pid = child.pid;
    try {
      process.kill(-pid, "SIGTERM");
    } catch {}
    const timer = setTimeout(() => {
      try {
        process.kill(-pid, "SIGKILL");
      } catch {}
    }, 2000);
    timer.unref();
  }
  stopAll() {
    for (const id of this.children.keys()) this.stop(id);
  }
  async run(
    command: string,
    cwd: string,
    signal?: AbortSignal,
    timeout = 120000,
  ) {
    if (signal?.aborted) throw Error("Cancelled");
    const job = this.start(command, cwd);
    const child = this.children.get(job.id)!;
    const abort = () => this.stop(job.id);
    signal?.addEventListener("abort", abort, { once: true });
    let timedOut = false;
    const timer = setTimeout(() => {
      timedOut = true;
      abort();
    }, timeout);
    await new Promise<void>((resolve) => child.once("close", () => resolve()));
    clearTimeout(timer);
    signal?.removeEventListener("abort", abort);
    return {
      exitCode: job.exitCode,
      output: job.output,
      timedOut,
      cancelled: !!signal?.aborted,
    };
  }
}
export function validPort(port: unknown): number {
  const n = Number(port);
  if (!Number.isInteger(n) || n < 1024 || n > 65535)
    throw Error("Port must be between 1024 and 65535");
  if (n === 8787 || n === 18889) throw Error("Do not tunnel an agent control port");
  return n;
}
export function tunnelCommand(port: number, anonymous: boolean) {
  return `devtunnel host -p ${validPort(port)}${anonymous ? " --allow-anonymous" : ""}`;
}
