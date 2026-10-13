import { createHash, randomUUID } from "node:crypto";

export type Target = number | [number, number];
export type Screen = { id: string; captured: number; package: string; width: number; height: number; rotation: number; elements: any[]; screenshot?: string; screenshotError?: string };
/** Artemis helper protocol v2, transported locally without ADB or a remote host. */
export class ArtemisBridge {
  last?: Screen;
  constructor(private token: string, private base = "http://127.0.0.1:18889") {}
  private async request(path: string, body?: unknown, signal?: AbortSignal) {
    const response = await fetch(this.base + path, {
      method: body ? "POST" : "GET",
      headers: { "X-Artemis-Token": this.token, "Content-Type": "application/json" },
      body: body ? JSON.stringify(body) : undefined,
      signal: signal ? AbortSignal.any([signal, AbortSignal.timeout(12000)]) : AbortSignal.timeout(12000),
    });
    const value = await response.json() as any;
    if (!response.ok || value.success === false) throw Error(value.error || "Android accessibility operation failed");
    return value;
  }
  async health() {
    try { return { ready: true, ...await this.request("/ping") }; }
    catch { return { ready: false, task_allowed: false, error: "Enable Pocket Agent in Android Accessibility settings" }; }
  }
  async observe(signal?: AbortSignal): Promise<Screen> {
    const raw = await this.request("/snapshot?fields=elements", undefined, signal);
    if (!Number.isFinite(raw.width) || !Number.isFinite(raw.height) || raw.width <= 0 || raw.height <= 0)
      throw Error("Invalid display dimensions from Artemis");
    this.last = { id: randomUUID(), captured: Date.now(), package: raw.package || "", width: raw.width,
      height: raw.height, rotation: raw.rotation, elements: raw.elements || [],
      screenshot: raw.has_screenshot ? raw.screenshot_base64 : undefined, screenshotError: raw.screenshot_error };
    return this.last;
  }
  private signature(element: any) {
    return JSON.stringify([element.package, element["resource-id"], element.class, element.text, element["content-desc"], element.bounds, element.enabled]);
  }
  private fingerprint(screen: any) {
    return createHash("sha256").update(JSON.stringify([screen.package, screen.width, screen.height, screen.rotation,
      (screen.elements || []).map((e: any) => this.signature(e))])).digest("hex");
  }
  private async fresh(screenId: string, signal?: AbortSignal) {
    if (!this.last || screenId !== this.last.id || Date.now() - this.last.captured > 60000)
      throw Error("Observation expired. Call observe_screen again before acting.");
    const current = await this.request("/dump?fields=elements", undefined, signal);
    if (current.package !== this.last.package || current.width !== this.last.width || current.height !== this.last.height)
      throw Error("The foreground app or display changed. Observe again.");
    return current;
  }
  private coordinates(target: Target, current: any, description?: string): [number, number] {
    const screen = this.last!;
    if (typeof target === "number") {
      if (!Number.isInteger(target) || target < 0 || target >= screen.elements.length) throw Error("Invalid element index");
      const element = screen.elements[target];
      if (element.password) throw Error("Use ask_user for passwords or authentication; do not inspect or enter credentials");
      if (!element.enabled || !current.elements.some((e: any) => this.signature(e) === this.signature(element)))
        throw Error("Target changed or is disabled. Observe again.");
      const bounds = element.parsed_bounds;
      if (!bounds || bounds.right <= bounds.left || bounds.bottom <= bounds.top) throw Error("Target has no visible bounds");
      return [(bounds.left + bounds.right) / 2, (bounds.top + bounds.bottom) / 2];
    }
    if (!description?.trim()) throw Error("Coordinate targets need a meaningful target_description");
    if (this.fingerprint(current) !== this.fingerprint(screen)) throw Error("Screen changed. Observe again before using coordinates.");
    if (!Array.isArray(target) || target.length !== 2 || target.some((n) => !Number.isFinite(n) || n < 0 || n > 1000))
      throw Error("Coordinates must be in the 0–1000 scale");
    return [Math.min(screen.width - 1, target[0] * screen.width / 1000), Math.min(screen.height - 1, target[1] * screen.height / 1000)];
  }
  async target(cmd: string, screenId: string, target: Target, description?: string, extra: any = {}, signal?: AbortSignal) {
    const current = await this.fresh(screenId, signal);
    const [x, y] = this.coordinates(target, current, description);
    await this.request("/action", { cmd, x, y, ...extra }, signal);
    this.last = undefined;
    return "Action executed. Call observe_screen to verify its result.";
  }
  async type(screenId: string, target: Target, text: string, description?: string, signal?: AbortSignal) {
    if (text.length > 4000) throw Error("Text is too long");
    await this.target("tap", screenId, target, description, {}, signal);
    await this.request("/action", { cmd: "type", text }, signal);
    return "Text entered. Observe and verify the field.";
  }
  async command(screenId: string, cmd: string, args: any = {}, signal?: AbortSignal) {
    await this.fresh(screenId, signal);
    const result = await this.request("/action", { cmd, ...args }, signal);
    this.last = undefined;
    return result;
  }
  async swipe(screenId: string, from: [number, number], to: [number, number], signal?: AbortSignal) {
    const current = await this.fresh(screenId, signal);
    const [x1, y1] = this.coordinates(from, current, "swipe start");
    const [x2, y2] = this.coordinates(to, current, "swipe end");
    return this.command(screenId, "swipe", { x1, y1, x2, y2, duration: 350 }, signal);
  }
  async sequence(screenId: string, points: [number, number][], descriptions: string[], signal?: AbortSignal) {
    if (points.length < 2 || points.length > 5 || descriptions.length !== points.length) throw Error("Use 2–5 described points");
    const current = await this.fresh(screenId, signal);
    const pixels = points.map((point, i) => this.coordinates(point, current, descriptions[i]));
    const result = await this.request("/action", { cmd: "tap_sequence", points: pixels }, signal);
    this.last = undefined; return result;
  }
}
