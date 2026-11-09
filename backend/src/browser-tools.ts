import { Type } from "typebox";
import type { AgentTool } from "@earendil-works/pi-agent-core";
import { BrowserBridge } from "./browser-bridge.js";

const text = (value: unknown): any => ({
  content: [{ type: "text", text: typeof value === "string" ? value : JSON.stringify(value) }],
  details: {},
});

export function browserTools(botId: string, bridge: BrowserBridge): AgentTool<any>[] {
  return [
    {
      name: "browser_navigate",
      label: "Navigate browser",
      description:
        "Open an HTTP or HTTPS webpage in this bot's isolated in-app browser. Sessions, cookies, and logins are preserved across visits. Always observe the page after navigation. This is the default way to do anything on a website; do not take over the owner's phone to browse. Pass 'tab' to open the page in another tab, so a form that is half filled in survives.",
      parameters: Type.Object({
        url: Type.String({ maxLength: 8000, description: "The full URL to navigate to (http:// or https://)" }),
        tab: Type.Optional(Type.Integer({ minimum: 0, description: "The tab to open the page in. The tab in front is used when this is left out." })),
      }),
      execute: async (_id, p: any, signal) => {
        const raw = String(p.url || "").trim();
        const target = raw.includes("://") ? raw : `https://${raw}`;
        const url = new URL(target);
        if (!["http:", "https:"].includes(url.protocol)) {
          throw Error("Use an HTTP/HTTPS URL");
        }
        const result = await bridge.navigate(botId, url.href, true, signal, typeof p.tab === "number" ? p.tab : undefined);
        return text(result);
      },
    },
    {
      name: "browser_list_tabs",
      label: "List browser tabs",
      description:
        "List the tabs of this bot's browser, with the page each one is on and which one is in front. Use it before opening another tab, so you know what is already open.",
      parameters: Type.Object({}),
      execute: async (_id, _p: any, signal) => {
        const result = await bridge.tabs(botId, signal);
        return text(result);
      },
    },
    {
      name: "browser_new_tab",
      label: "Open a new browser tab",
      description:
        "Open a new tab in this bot's browser and put it in front. The tab that was in front keeps its page, so a form that is half filled in is not lost. Give a URL to open a page in the new tab, or leave it out for an empty one.",
      parameters: Type.Object({
        url: Type.Optional(Type.String({ maxLength: 8000, description: "The page to open in the new tab" })),
      }),
      execute: async (_id, p: any, signal) => {
        const raw = String(p.url || "").trim();
        let target = "";
        if (raw) {
          const url = new URL(raw.includes("://") ? raw : `https://${raw}`);
          if (!["http:", "https:"].includes(url.protocol)) {
            throw Error("Use an HTTP/HTTPS URL");
          }
          target = url.href;
        }
        const result = await bridge.newTab(botId, target, signal);
        return text(result);
      },
    },
    {
      name: "browser_switch_tab",
      label: "Switch browser tab",
      description:
        "Bring another tab of this bot's browser to the front. The pages of the other tabs stay as they are.",
      parameters: Type.Object({
        index: Type.Integer({ minimum: 0, description: "The tab to bring to the front, as listed by browser_list_tabs" }),
      }),
      execute: async (_id, p: any, signal) => {
        const result = await bridge.switchTab(botId, Number(p.index), signal);
        return text(result);
      },
    },
    {
      name: "browser_close_tab",
      label: "Close browser tab",
      description: "Close a tab of this bot's browser. The last tab stays open.",
      parameters: Type.Object({
        index: Type.Integer({ minimum: 0, description: "The tab to close, as listed by browser_list_tabs" }),
      }),
      execute: async (_id, p: any, signal) => {
        const result = await bridge.closeTab(botId, Number(p.index), signal);
        return text(result);
      },
    },
    {
      name: "browser_observe",
      label: "Inspect browser page",
      description:
        "Inspect the current web page in this bot's in-app browser. Extracts all interactive buttons, links, inputs, and elements with their numeric IDs (id: 1, 2, 3...) and bounding coordinates, plus optionally captures an in-memory snapshot image.",
      parameters: Type.Object({
        include_screenshot: Type.Optional(Type.Boolean({ description: "Whether to include an image screenshot of the browser view" })),
      }),
      execute: async (_id, p: any, signal) => {
        const dom = await bridge.observe(botId, signal);
        const result = text(dom);
        if (p?.include_screenshot) {
          try {
            const shot = await bridge.screenshot(botId, 1080, 1920, signal);
            if (shot?.screenshot) {
              result.content.push({
                type: "image",
                data: shot.screenshot,
                mimeType: shot.mimeType || "image/jpeg",
              });
            }
          } catch (e: any) {
            (result.details as any).screenshotError = String(e.message || e);
          }
        }
        return result;
      },
    },
    {
      name: "browser_click",
      label: "Click element",
      description:
        "Click or tap an interactive element in the bot's browser by its numeric ID (from browser_observe), or tap specific coordinates (x, y).",
      parameters: Type.Object({
        id: Type.Optional(Type.Integer({ minimum: 1, description: "Numeric element ID from browser_observe" })),
        x: Type.Optional(Type.Number({ description: "Horizontal pixel coordinate" })),
        y: Type.Optional(Type.Number({ description: "Vertical pixel coordinate" })),
      }),
      execute: async (_id, p: any, signal) => {
        if (!p.id && (p.x === undefined || p.y === undefined)) {
          throw Error("Provide an element 'id' or coordinates 'x' and 'y'");
        }
        const result = await bridge.click(botId, p.id, p.x, p.y, signal);
        return text(result);
      },
    },
    {
      name: "browser_type",
      label: "Enter text into input",
      description:
        "Focus and enter text into an input field, search box, or textarea by its numeric ID.",
      parameters: Type.Object({
        id: Type.Integer({ minimum: 1, description: "Numeric element ID from browser_observe" }),
        text: Type.String({ maxLength: 4000, description: "Text to enter into the field" }),
        clear: Type.Optional(Type.Boolean({ description: "Clear existing text before typing (default false)" })),
      }),
      execute: async (_id, p: any, signal) => {
        const result = await bridge.type(botId, p.id, p.text, p.clear === true, signal);
        return text(result);
      },
    },
    {
      name: "browser_scroll",
      label: "Scroll page",
      description: "Scroll the browser page up or down.",
      parameters: Type.Object({
        direction: Type.Optional(Type.Union([Type.Literal("up"), Type.Literal("down")], { description: "Scroll direction (default down)" })),
      }),
      execute: async (_id, p: any, signal) => {
        const result = await bridge.scroll(botId, p.direction || "down", signal);
        return text(result);
      },
    },
    {
      name: "browser_ask_human",
      label: "Ask human to take control",
      description:
        "Pause automated browser actions and ask the human owner to take over the live browser view. Use this when encountering CAPTCHAs, Cloudflare bot checks, login credentials, two-factor authentication, or security confirmations. The owner will see the browser live, solve the prompt, and hand control back to you.",
      parameters: Type.Object({
        reason: Type.String({
          minLength: 1,
          maxLength: 2000,
          description: "Clear explanation of what needs human action (e.g. 'Please solve the CAPTCHA on the login screen and tap Hand back to bot')",
        }),
      }),
      execute: async (_id, p: any, signal) => {
        const result = await bridge.askHuman(botId, p.reason, signal);
        return text({
          status: "resumed",
          message: "Human completed the action in the live browser and handed control back to you. Observe the page again to continue.",
          handoff: result,
        });
      },
    },
    {
      name: "browser_action",
      label: "Browser navigation action",
      description: "Navigate back, forward, or reload the current webpage.",
      parameters: Type.Object({
        cmd: Type.Union([Type.Literal("back"), Type.Literal("forward"), Type.Literal("reload")], {
          description: "Browser command to execute",
        }),
      }),
      execute: async (_id, p: any, signal) => {
        const result = await bridge.action(botId, p.cmd, signal);
        return text(result);
      },
    },
  ];
}
