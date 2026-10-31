import { spawn, execFile, type ChildProcess } from "node:child_process";
import { promisify } from "node:util";
import { createHash, randomUUID } from "node:crypto";
import { mkdir, writeFile, chmod, rename, rm, access } from "node:fs/promises";
import { join } from "node:path";

const execute = promisify(execFile);
const version = "2.103.3";
const hashes = {
  x64: "4c806978ffce1cec70853302a56d70ac6da4f19bae7adca4e1a8ab00633dd91d",
  arm64: "ee32df2323c8847f5fac6cb6b4b69ca75828e9329a8f22f677021f7b3306ce63",
};
type Status = { mode: "github_device"; status: "preparing" | "waiting" | "authorized" | "failed" | "cancelled"; userCode?: string; authorizationUrl?: string; message?: string };
type Attempt = { public: Status; abort: AbortController; child?: ChildProcess; timer?: ReturnType<typeof setTimeout> };

/** Run GitHub's own OAuth client in an isolated phone-owned account directory. */
export class GitHubLogin {
  private attempts = new Map<string, Attempt>();
  private installing?: Promise<string>;
  constructor(private root: string, private saveToken: (id: string, token: string) => Promise<void>, private fixture?: { command: string; args: string[] }) {}
  status(id: string): Status { return this.attempts.get(id)?.public || { mode: "github_device", status: "cancelled" }; }
  start(id: string) {
    const existing = this.attempts.get(id);
    if (existing && ["preparing", "waiting"].includes(existing.public.status)) return existing.public;
    const attempt: Attempt = { public: { mode: "github_device", status: "preparing", message: "Preparing GitHub's official sign-in tool on this phone..." }, abort: new AbortController() };
    this.attempts.set(id, attempt);
    attempt.timer = setTimeout(() => this.cancel(id), 15 * 60 * 1000); attempt.timer.unref();
    void this.run(id, attempt);
    return attempt.public;
  }
  cancel(id: string) {
    const attempt = this.attempts.get(id); if (!attempt) return;
    attempt.abort.abort(); clearTimeout(attempt.timer);
    if (attempt.child?.pid) { try { if (process.platform !== "win32") process.kill(-attempt.child.pid, "SIGKILL"); else attempt.child.kill(); } catch {} }
    attempt.public = { mode: "github_device", status: "cancelled" };
  }
  close() { for (const id of this.attempts.keys()) this.cancel(id); }
  private async binary() {
    if (this.fixture) return this.fixture.command;
    if (process.platform !== "linux" || !(process.arch in hashes)) throw Error("Unsupported phone runtime");
    const target = join(this.root, "gh-" + version);
    try { await access(target); return target; } catch {}
    if (!this.installing) this.installing = this.install(target).finally(() => { this.installing = undefined; });
    return this.installing;
  }
  private async install(target: string) {
    await mkdir(this.root, { recursive: true, mode: 0o700 });
    const directory = join(this.root, "download-" + randomUUID()); await mkdir(directory, { mode: 0o700 });
    try {
      const arch = process.arch === "x64" ? "amd64" : "arm64", name = `gh_${version}_linux_${arch}`;
      const response = await fetch(`https://github.com/cli/cli/releases/download/v${version}/${name}.tar.gz`, { signal: AbortSignal.timeout(120000) });
      if (!response.ok || !response.body) throw Error("GitHub CLI download failed");
      const chunks: Uint8Array[] = []; let size = 0;
      for await (const chunk of response.body) { size += chunk.length; if (size > 64 * 1024 * 1024) throw Error("Download exceeds limit"); chunks.push(chunk); }
      const archive = Buffer.concat(chunks);
      if (createHash("sha256").update(archive).digest("hex") !== hashes[process.arch as keyof typeof hashes]) throw Error("GitHub CLI checksum mismatch");
      const file = join(directory, "gh.tar.gz"); await writeFile(file, archive, { mode: 0o600 });
      await execute("tar", ["-xzf", file, "-C", directory, `${name}/bin/gh`], { timeout: 30000 });
      const executable = join(directory, name, "bin/gh"); await chmod(executable, 0o700); await rename(executable, target); return target;
    } finally { await rm(directory, { recursive: true, force: true }); }
  }
  private async run(id: string, attempt: Attempt) {
    const directory = join(this.root, "login-" + randomUUID());
    const active = () => !attempt.abort.signal.aborted && this.attempts.get(id) === attempt;
    try {
      const binary = await this.binary(); if (!active()) return;
      await mkdir(directory, { recursive: true, mode: 0o700 });
      if (!active()) return;
      const env = { ...process.env, GH_CONFIG_DIR: directory, GH_BROWSER: "true", BROWSER: "true", GH_PROMPT_DISABLED: "1", NO_COLOR: "1" };
      for (const name of ["GH_TOKEN", "GITHUB_TOKEN", "GH_ENTERPRISE_TOKEN", "GITHUB_ENTERPRISE_TOKEN"]) delete (env as NodeJS.ProcessEnv)[name];
      const prefix = this.fixture?.args || [];
      await new Promise<void>((resolve, reject) => {
        const child = spawn(binary, [...prefix, "auth", "login", "--web", "--hostname", "github.com", "--git-protocol", "https", "--insecure-storage"], { env, detached: process.platform !== "win32", stdio: ["pipe", "pipe", "pipe"] });
        attempt.child = child; let output = "";
        const append = (chunk: Buffer) => {
          output = (output + chunk.toString()).slice(-12000).replace(/\x1b\[[0-?]*[ -/]*[@-~]/g, "");
          const code = output.match(/\b[A-Z0-9]{4}-[A-Z0-9]{4}\b/);
          if (code && active()) attempt.public = { mode: "github_device", status: "waiting", userCode: code[0], authorizationUrl: "https://github.com/login/device" };
        };
        child.stdout.on("data", append); child.stderr.on("data", append); child.stdin.on("error", () => {}); child.stdin.end("\n");
        child.once("error", reject); child.once("close", code => code === 0 ? resolve() : reject(Error("GitHub authorization did not finish")));
      });
      attempt.child = undefined; if (!active()) return;
      const tokenResult = await execute(binary, [...prefix, "auth", "token", "--hostname", "github.com"], { env, timeout: 10000, maxBuffer: 16000, signal: attempt.abort.signal });
      const token = tokenResult.stdout.trim(); if (!token || token.length > 8000 || /\s/.test(token)) throw Error("Invalid GitHub token");
      if (!active()) return;
      await this.saveToken(id, token);
      if (active()) attempt.public = { mode: "github_device", status: "authorized" };
    } catch {
      if (active()) attempt.public = { mode: "github_device", status: "failed", message: "GitHub sign-in could not finish. Check connectivity or retry. You can also use a token in Configure." };
    } finally { clearTimeout(attempt.timer); attempt.child = undefined; await rm(directory, { recursive: true, force: true }); }
  }
}
