import { test } from "node:test";
import assert from "node:assert/strict";
import { createServer } from "node:http";
import { BrowserBridge } from "../src/browser-bridge.js";
import { browserTools } from "../src/browser-tools.js";

test("browser tools declare all required bot browser actions", () => {
  const bridge = new BrowserBridge("test-token");
  const tools = browserTools("bot-123", bridge);
  const names = tools.map((t) => t.name);

  assert.ok(names.includes("browser_navigate"));
  assert.ok(names.includes("browser_observe"));
  assert.ok(names.includes("browser_click"));
  assert.ok(names.includes("browser_type"));
  assert.ok(names.includes("browser_scroll"));
  assert.ok(names.includes("browser_ask_human"));
  assert.ok(names.includes("browser_action"));
});

test("browser tools execute actions and validate inputs via mock bridge", async () => {
  const calls: { method: string; args: any[] }[] = [];
  const mockBridge: any = {
    navigate: async (botId: string, url: string, waitForLoad: boolean) => {
      calls.push({ method: "navigate", args: [botId, url, waitForLoad] });
      return { success: true, url, title: "Example Domain" };
    },
    observe: async (botId: string) => {
      calls.push({ method: "observe", args: [botId] });
      return {
        url: "https://example.com",
        title: "Example Domain",
        totalElements: 2,
        elements: [
          { id: 1, tag: "input", type: "text", text: "Search" },
          { id: 2, tag: "button", type: "submit", text: "Submit" },
        ],
      };
    },
    screenshot: async (botId: string, width: number, height: number) => {
      calls.push({ method: "screenshot", args: [botId, width, height] });
      return { success: true, screenshot: "base64image...", mimeType: "image/jpeg" };
    },
    click: async (botId: string, id?: number, x?: number, y?: number) => {
      calls.push({ method: "click", args: [botId, id, x, y] });
      return { success: true, id };
    },
    type: async (botId: string, id: number, val: string, clear: boolean) => {
      calls.push({ method: "type", args: [botId, id, val, clear] });
      return { success: true, id };
    },
    scroll: async (botId: string, direction: "up" | "down") => {
      calls.push({ method: "scroll", args: [botId, direction] });
      return { success: true, direction };
    },
    askHuman: async (botId: string, reason: string) => {
      calls.push({ method: "askHuman", args: [botId, reason] });
      return { success: true, userNotes: "User solved captcha", url: "https://example.com/dashboard" };
    },
    action: async (botId: string, cmd: string) => {
      calls.push({ method: "action", args: [botId, cmd] });
      return { success: true, cmd };
    },
  };

  const tools = browserTools("bot-test", mockBridge);
  const tool = (name: string) => tools.find((t) => t.name === name)!;

  // 1. Navigate
  const navRes = await tool("browser_navigate").execute("1", { url: "https://example.com" });
  assert.match(JSON.stringify(navRes), /Example Domain/);
  assert.equal(calls[0].method, "navigate");
  assert.equal(calls[0].args[1], "https://example.com/");

  // Rejects invalid protocol
  await assert.rejects(
    tool("browser_navigate").execute("2", { url: "ftp://example.com" }),
    /HTTP\/HTTPS/
  );

  // 2. Observe with screenshot
  const obsRes = await tool("browser_observe").execute("3", { include_screenshot: true });
  assert.match(JSON.stringify(obsRes), /Search/);
  assert.ok(obsRes.content.some((c: any) => c.type === "image" && c.data === "base64image..."));

  // 3. Click
  const clickRes = await tool("browser_click").execute("4", { id: 2 });
  assert.match(JSON.stringify(clickRes), /success/);

  // 4. Type
  const typeRes = await tool("browser_type").execute("5", { id: 1, text: "testing", clear: true });
  assert.match(JSON.stringify(typeRes), /success/);

  // 5. Scroll
  const scrollRes = await tool("browser_scroll").execute("6", { direction: "down" });
  assert.match(JSON.stringify(scrollRes), /direction/);

  // 6. Ask Human
  const humanRes = await tool("browser_ask_human").execute("7", {
    reason: "Please solve the hCaptcha on the login screen",
  });
  assert.match(JSON.stringify(humanRes), /User solved captcha/);
  assert.match(JSON.stringify(humanRes), /resumed/);

  // 7. Action
  const actRes = await tool("browser_action").execute("8", { cmd: "reload" });
  assert.match(JSON.stringify(actRes), /reload/);
});

test("BrowserBridge communicates over HTTP with loopback server and token", async () => {
  let receivedAuth = "";
  let receivedCustomToken = "";
  let receivedUrl = "";

  const server = createServer((req, res) => {
    receivedAuth = req.headers["authorization"] || "";
    receivedCustomToken = (req.headers["x-bot-token"] as string) || "";
    receivedUrl = req.url || "";

    if (req.url === "/ping") {
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ success: true, service: "pocket-bot-browser" }));
      return;
    }

    if (req.url === "/browser/bot-abc/dom") {
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ url: "https://example.com", elements: [{ id: 1, text: "OK" }] }));
      return;
    }

    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ success: true }));
  });

  await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", resolve));
  const port = (server.address() as any).port;
  const bridge = new BrowserBridge("secret-token-123", `http://127.0.0.1:${port}`);

  try {
    // Health ping
    const health = await bridge.health();
    assert.equal(health.ready, true);

    // Dom observe
    const dom = await bridge.observe("bot-abc");
    assert.equal(dom.url, "https://example.com");
    assert.equal(receivedAuth, "Bearer secret-token-123");
    assert.equal(receivedCustomToken, "secret-token-123");
    assert.equal(receivedUrl, "/browser/bot-abc/dom");
  } finally {
    await new Promise<void>((resolve) => server.close(() => resolve()));
  }
});
