import { randomBytes, randomUUID } from "node:crypto";

import type { Store } from "./storage.js";

/**
 * A device that may talk to this phone.
 *
 * The phone keeps its own key for itself. A device that is paired gets a key of its own, which
 * opens the chats and nothing else unless the owner says otherwise. The key is never the owner's
 * own, so a device that is lost does not open the terminal, the files or the model keys.
 */
export type PairedDevice = {
  id: string;
  name: string;
  token: string;
  /** When it was paired. */
  created: number;
  /** What it may do: the chats only, or everything the owner can do. */
  scope: "chat" | "full";
  /** The name of the tunnel that carries it, so the link does not change. */
  tunnel?: string;
  /** The public link it is reached at. */
  url?: string;
  lastSeen?: number;
};

/**
 * The paths a paired device may use.
 *
 * A device with the chat scope reads and writes the conversations and nothing else. The terminal,
 * the files, the webhooks and the model keys stay on the phone.
 */
const CHAT_ROUTES: RegExp[] = [
  /^\/api\/bots$/,
  /^\/api\/bots\/alerts$/,
  /^\/api\/bots\/alerts\/[A-Za-z0-9_-]+\/ack$/,
  /^\/api\/bots\/[A-Za-z0-9_-]+$/,
  /^\/api\/bots\/[A-Za-z0-9_-]+\/messages$/,
  /^\/api\/bots\/[A-Za-z0-9_-]+\/read$/,
  /^\/api\/groups$/,
  /^\/api\/groups\/[A-Za-z0-9_-]+$/,
  /^\/api\/groups\/[A-Za-z0-9_-]+\/messages$/,
  /^\/api\/events$/,
];

export function allowsChatRoute(path: string): boolean {
  return CHAT_ROUTES.some((pattern) => pattern.test(path));
}

export class Connect {
  private devices: PairedDevice[] = [];
  /** Whether the Dev Tunnels CLI on this phone is signed in. */
  private signedIn: boolean | null = null;
  private checkedAt = 0;
  private checking: Promise<boolean> | null = null;
  /** The sign-in the phone is in the middle of. */
  private login: { id: string; output: string; status: string } | null = null;
  /** The tunnels that are open now, by the device they carry. */
  private tunnels = new Map<string, { host: { id: string; status: string }; at: number }>();

  constructor(
    private store: Store,
    private tunnel: {
      create: (name: string, port: number) => { id: string; output: string; status: string };
      host: (name: string, port: number) => { id: string; output: string; status: string };
      stop: (id: string) => void;
      output: (id: string) => string;
      status: (id: string) => string;
      /** What the CLI says about the account it is signed in with. */
      userShow: () => Promise<string>;
      /** Starts the sign-in that gives the CLI an account. */
      login: () => { id: string; output: string; status: string };
    },
    private port: number,
  ) {}

  async init() {
    this.devices = await this.store.read<PairedDevice[]>("clients", []);
    for (const device of this.devices) if (device.url) device.url = device.url.replace(/\/+$/, "");
    // The sign-in is checked in the background, so the phone is ready straight away. A device
    // that was paired before the phone restarted gets its tunnel back under the same name, so
    // the link it was given still works.
    void this.checkSignIn().then((signedIn) => {
      if (signedIn) for (const device of this.devices) if (device.tunnel) this.openTunnel(device);
    });
  }

  list(): PairedDevice[] {
    return this.devices.map((device) => ({ ...device }));
  }

  /** The device a key belongs to, or null when the key is not one of ours. */
  find(token: string): PairedDevice | null {
    if (!token) return null;
    return this.devices.find((device) => device.token === token) || null;
  }

  async create(name: string, scope: "chat" | "full" = "chat"): Promise<PairedDevice> {
    const device: PairedDevice = {
      id: randomUUID(),
      name: (name || "A device").trim().slice(0, 60),
      token: randomBytes(32).toString("hex"),
      created: Date.now(),
      scope: scope === "full" ? "full" : "chat",
      tunnel: `pockobots-${randomBytes(4).toString("hex")}`,
    };
    this.devices.push(device);
    await this.save();
    if (this.signedIn) this.openTunnel(device);
    return { ...device };
  }

  async remove(id: string): Promise<void> {
    const open = this.tunnels.get(id);
    if (open) this.tunnel.stop(open.host.id);
    this.tunnels.delete(id);
    this.devices = this.devices.filter((device) => device.id !== id);
    await this.save();
  }

  async setScope(id: string, scope: "chat" | "full"): Promise<PairedDevice | null> {
    const device = this.devices.find((entry) => entry.id === id);
    if (!device) return null;
    device.scope = scope === "full" ? "full" : "chat";
    await this.save();
    return { ...device };
  }

  /**
   * Whether the Dev Tunnels CLI on this phone is signed in.
   *
   * A tunnel cannot be made without it. The CLI says so in one line, and that line is what this
   * looks for. A CLI that is not there at all counts as not signed in, so the phone says so
   * rather than waiting for a link that never comes.
   */
  private async checkSignIn(force = false): Promise<boolean> {
    if (this.checking) return this.checking;
    if (!force && Date.now() - this.checkedAt < 3000) return this.signedIn !== false;
    this.checking = (async () => {
      this.checkedAt = Date.now();
      try {
        const text = (await this.tunnel.userShow()).toLowerCase();
        const signedOut =
          text.includes("not logged in") ||
          text.includes("not signed in") ||
          text.includes("expired") ||
          text.includes("command not found") ||
          text.includes("is not recognized");
        this.signedIn = !signedOut;
      } catch {
        this.signedIn = false;
      }
      this.checking = null;
      return this.signedIn;
    })();
    return this.checking;
  }

  /**
   * Starts the sign-in, if the phone is not signed in yet.
   *
   * The CLI prints the page to open and the code to type into it. The code is read from there
   * and shown on the phone, so the sign-in can be done on another device.
   */
  async signIn() {
    if (!this.login || this.login.status !== "running") {
      const job = this.tunnel.login();
      this.login = { id: job.id, output: job.output || "", status: job.status };
    }
    return this.view();
  }

  /**
   * Looks at the phone now, without waiting for the last look to age.
   *
   * This is what the owner presses when the sign-in was finished on another device.
   */
  async checkNow() {
    await this.checkSignIn(true);
    return this.poll();
  }

  /** The page to open and the code to type into it, when the phone is signing in. */
  private loginDetails() {
    const text = this.login?.output || "";
    const code = text.match(/enter the code:\s*([A-Za-z0-9-]+)/i)?.[1] || "";
    const url = text.match(/https:\/\/\S+/)?.[0]?.replace(/[.,;)]+$/, "") || "";
    return { url, code, pending: this.login?.status === "running" };
  }

  /** Opens the tunnel that carries a device, and reads the link it was given. */
  private openTunnel(device: PairedDevice) {
    const name = device.tunnel || `pockobots-${randomBytes(4).toString("hex")}`;
    device.tunnel = name;
    // The tunnel is made once and kept, so the link does not change when the phone restarts.
    const created = device.url
      ? { id: "", output: "", status: "running" }
      : this.tunnel.create(name, this.port);
    const hosted = this.tunnel.host(name, this.port);
    this.tunnels.set(device.id, { host: hosted, at: Date.now() });
    const read = () => {
      const text = `${created.output}\n${hosted.output}`;
      const url = text.match(/https:\/\/[a-zA-Z0-9.-]+\.devtunnels\.ms/i)?.[0];
      if (url && device.url !== url) {
        device.url = url;
        void this.save();
      }
    };
    const timer = setInterval(read, 1000);
    timer.unref();
    setTimeout(() => clearInterval(timer), 120000).unref();
    read();
  }

  /**
   * Looks at the phone now: whether the CLI is signed in, and the state of every tunnel.
   *
   * This is called when the Connect page is opened and while it is open, so a sign-in that is
   * finished on another device is picked up, and the tunnels are made as soon as it is.
   */
  async poll() {
    const now = Date.now();
    await this.checkSignIn();
    if (this.login) {
      const output = this.tunnel.output(this.login.id);
      if (output) this.login.output = output;
      this.login.status = this.tunnel.status(this.login.id);
    }
    if (this.signedIn) {
      for (const device of this.devices) {
        if (!device.tunnel) continue;
        const open = this.tunnels.get(device.id);
        // A tunnel that died is made again, but not too often.
        if (open && open.host.status === "exited" && now - open.at < 30000) continue;
        if (open && open.host.status === "running") continue;
        this.openTunnel(device);
      }
    }
    return this.view();
  }

  /** What the phone shows on its Connect device page. */
  view() {
    return {
      signedIn: this.signedIn !== false,
      login: this.signedIn === false ? this.loginDetails() : null,
      devices: this.devices.map((device) => ({
        id: device.id,
        name: device.name,
        scope: device.scope,
        created: device.created,
        url: device.url || "",
        token: device.token,
        lastSeen: device.lastSeen || 0,
      })),
    };
  }

  async seen(token: string) {
    const device = this.find(token);
    if (device) device.lastSeen = Date.now();
  }

  private async save() {
    await this.store.write("clients", this.devices);
  }
}
