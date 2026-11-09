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

  constructor(
    private store: Store,
    private tunnel: {
      create: (name: string, port: number) => { id: string; output: string; status: string };
      host: (name: string, port: number) => { id: string; output: string; status: string };
      stop: (id: string) => void;
      output: (id: string) => string;
    },
    private port: number,
  ) {}

  async init() {
    this.devices = await this.store.read<PairedDevice[]>("clients", []);
    for (const device of this.devices) if (device.url) device.url = device.url.replace(/\/+$/, "");
    // A device that was paired before the phone restarted gets its tunnel back, under the same
    // name, so the link it was given still works.
    for (const device of this.devices) if (device.tunnel) this.openTunnel(device);
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
    this.openTunnel(device);
    return { ...device };
  }

  async remove(id: string): Promise<void> {
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

  /** Opens the tunnel that carries a device, and reads the link it was given. */
  private openTunnel(device: PairedDevice) {
    const name = device.tunnel || `pockobots-${randomBytes(4).toString("hex")}`;
    device.tunnel = name;
    // The tunnel is made once and kept, so the link does not change when the phone restarts.
    const created = device.url
      ? { id: "", output: "", status: "running" }
      : this.tunnel.create(name, this.port);
    const hosted = this.tunnel.host(name, this.port);
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

  /** What the phone shows on its Connect device page. */
  view() {
    return this.devices.map((device) => ({
      id: device.id,
      name: device.name,
      scope: device.scope,
      created: device.created,
      url: device.url || "",
      token: device.token,
      lastSeen: device.lastSeen || 0,
    }));
  }

  async seen(token: string) {
    const device = this.find(token);
    if (device) device.lastSeen = Date.now();
  }

  private async save() {
    await this.store.write("clients", this.devices);
  }
}
