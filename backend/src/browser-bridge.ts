export class BrowserBridge {
  constructor(private token: string, private base = "http://127.0.0.1:18890") {}

  private async request(path: string, body?: unknown, timeoutMs = 15000, signal?: AbortSignal) {
    const combinedSignal = signal
      ? AbortSignal.any([signal, AbortSignal.timeout(timeoutMs)])
      : AbortSignal.timeout(timeoutMs);

    const response = await fetch(this.base + path, {
      method: body ? "POST" : "GET",
      headers: {
        "Authorization": `Bearer ${this.token}`,
        "X-Bot-Token": this.token,
        "Content-Type": "application/json",
      },
      body: body ? JSON.stringify(body) : undefined,
      signal: combinedSignal,
    });

    const value = (await response.json()) as any;
    if (!response.ok || value.error) {
      throw Error(value.error || `Browser bridge request failed (${response.status})`);
    }
    return value;
  }

  async health() {
    try {
      const res = await fetch(this.base + "/ping", { signal: AbortSignal.timeout(3000) });
      return { ready: res.ok, ...(await res.json() as any) };
    } catch {
      return { ready: false, service: "pocket-bot-browser" };
    }
  }

  async state(botId: string) {
    return this.request(`/browser/${botId}/state`);
  }

  async navigate(botId: string, url: string, waitForLoad = true, signal?: AbortSignal, tab?: number) {
    return this.request(`/browser/${botId}/navigate`, { url, waitForLoad, tab }, 30000, signal);
  }

  async tabs(botId: string, signal?: AbortSignal) {
    return this.request(`/browser/${botId}/tabs`, undefined, 10000, signal);
  }

  async newTab(botId: string, url = "", signal?: AbortSignal) {
    return this.request(`/browser/${botId}/newtab`, { url }, 20000, signal);
  }

  async switchTab(botId: string, index: number, signal?: AbortSignal) {
    return this.request(`/browser/${botId}/switchtab`, { index }, 10000, signal);
  }

  async closeTab(botId: string, index: number, signal?: AbortSignal) {
    return this.request(`/browser/${botId}/closetab`, { index }, 10000, signal);
  }

  async observe(botId: string, signal?: AbortSignal) {
    return this.request(`/browser/${botId}/dom`, undefined, 15000, signal);
  }

  async screenshot(botId: string, width = 1080, height = 1920, signal?: AbortSignal) {
    return this.request(`/browser/${botId}/screenshot`, { width, height }, 15000, signal);
  }

  async click(botId: string, id?: number, x?: number, y?: number, signal?: AbortSignal) {
    return this.request(`/browser/${botId}/click`, { id, x, y }, 15000, signal);
  }

  async type(botId: string, id: number, text: string, clear = false, signal?: AbortSignal) {
    return this.request(`/browser/${botId}/type`, { id, text, clear }, 15000, signal);
  }

  async scroll(botId: string, direction: "up" | "down" = "down", signal?: AbortSignal) {
    return this.request(`/browser/${botId}/scroll`, { direction }, 10000, signal);
  }

  async askHuman(botId: string, reason: string, signal?: AbortSignal) {
    // 5 minutes timeout for human handoff
    return this.request(`/browser/${botId}/ask_human`, { reason, timeoutMs: 300000 }, 310000, signal);
  }

  async action(botId: string, cmd: "back" | "forward" | "reload", signal?: AbortSignal) {
    return this.request(`/browser/${botId}/action`, { cmd }, 10000, signal);
  }
}
