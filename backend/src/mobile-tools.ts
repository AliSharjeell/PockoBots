import { Type } from "typebox";
import type { AgentTool } from "@earendil-works/pi-agent-core";
import { ArtemisBridge } from "./artemis.js";

const point = Type.Tuple([Type.Number({ minimum: 0, maximum: 1000 }), Type.Number({ minimum: 0, maximum: 1000 })]);
const target = Type.Union([Type.Integer({ minimum: 0 }), point]);
const observed = { screen_id: Type.String() };
const targeted = { ...observed, target, target_description: Type.Optional(Type.String()) };
const text = (value: unknown): any => ({ content: [{ type: "text", text: typeof value === "string" ? value : JSON.stringify(value) }], details: {} });

export function mobileTools(bridge: ArtemisBridge, ask: (question: string, signal?: AbortSignal) => Promise<string>,
  report: (status: "completed" | "failed", summary: string) => Promise<void>): AgentTool<any>[] {
  return [
    { name: "observe_screen", label: "Observe screen", description: "Get the current Android screenshot and indexed visible elements. Always observe before acting and again after an action to verify its outcome.",
      parameters: Type.Object({}), execute: async (_id, _args, signal) => {
        const screen = await bridge.observe(signal);
        const value = { screen_id: screen.id, package: screen.package, width: screen.width, height: screen.height,
          coordinate_scale: "0–1000", total_elements: screen.elements.length, truncated: screen.elements.length > 250,
          elements: screen.elements.slice(0, 250).map((e, index) => ({ index, text: e.password ? "[password]" : e.text,
            description: e["content-desc"], resource_id: e["resource-id"], bounds: e.bounds, clickable: e.clickable, editable: e.editable, enabled: e.enabled })),
          screenshot_error: screen.screenshotError };
        const result = text(value);
        if (screen.screenshot) result.content.push({ type: "image", data: screen.screenshot, mimeType: "image/jpeg" });
        return result;
      } },
    { name: "click", label: "Tap", description: "Tap a current observed element index, or an observed coordinate pair in 0–1000 scale. Coordinate targets require target_description. Never guess coordinates.",
      parameters: Type.Object(targeted), execute: async (_id, p: any, signal) => text(await bridge.target("tap", p.screen_id, p.target, p.target_description, {}, signal)) },
    { name: "long_press", label: "Long press", description: "Long press a freshly observed target.", parameters: Type.Object(targeted),
      execute: async (_id, p: any, signal) => text(await bridge.target("long_press", p.screen_id, p.target, p.target_description, { duration: 700 }, signal)) },
    { name: "input_text", label: "Enter text", description: "Tap and replace text in an observed editable field. Ask the user to handle passwords/authentication in the target app.",
      parameters: Type.Object({ ...targeted, text: Type.String({ maxLength: 4000 }) }),
      execute: async (_id, p: any, signal) => text(await bridge.type(p.screen_id, p.target, p.text, p.target_description, signal)) },
    { name: "swipe", label: "Scroll", description: "Swipe between observed points in 0–1000 scale; then observe again.",
      parameters: Type.Object({ ...observed, from: point, to: point }), execute: async (_id, p: any, signal) => text(await bridge.swipe(p.screen_id, p.from, p.to, signal)) },
    { name: "click_sequence", label: "Tap sequence", description: "Catch transient controls with 2–5 observed coordinate taps, 50ms apart. Supply a meaningful description for each point.",
      parameters: Type.Object({ ...observed, sequence: Type.Array(point, { minItems: 2, maxItems: 5 }), target_descriptions: Type.Array(Type.String()) }),
      execute: async (_id, p: any, signal) => text(await bridge.sequence(p.screen_id, p.sequence, p.target_descriptions, signal)) },
    { name: "manage_app", label: "Open app", description: "Launch an installed app by exact package ID or unambiguous launcher label. No force-stop, uninstall, ADB or shell access.",
      parameters: Type.Object({ ...observed, app_name: Type.String() }), execute: async (_id, p: any, signal) => text(await bridge.command(p.screen_id, "launch", { app_name: p.app_name }, signal)) },
    { name: "list_apps", label: "Find app", description: "List visible launcher app labels and packages.", parameters: Type.Object(observed),
      execute: async (_id, p: any, signal) => text(await bridge.command(p.screen_id, "apps", {}, signal)) },
    { name: "press_key", label: "Navigate", description: "Android Back, Home, Recents, or Notifications. Observe the resulting screen.",
      parameters: Type.Object({ ...observed, key: Type.Union([Type.Literal("back"), Type.Literal("home"), Type.Literal("recents"), Type.Literal("notifications")]) }),
      execute: async (_id, p: any, signal) => text(await bridge.command(p.screen_id, "global", { action: p.key }, signal)) },
    { name: "wait_for_delay", label: "Wait", description: "Wait 0–5 seconds for a transition, then observe again.",
      parameters: Type.Object({ milliseconds: Type.Integer({ minimum: 0, maximum: 5000 }) }), execute: async (_id, p: any, signal) => {
        await new Promise<void>((resolve, reject) => { const abort = () => { clearTimeout(timer); reject(Error("Cancelled")); };
          const timer = setTimeout(() => { signal?.removeEventListener("abort", abort); resolve(); }, p.milliseconds);
          if (signal?.aborted) abort(); else signal?.addEventListener("abort", abort, { once: true }); }); return text("Wait finished. Observe the screen."); } },
    { name: "ask_user", label: "Ask you", description: "Pause device actions and ask for missing information, a decision, or a manual login. Reply arrives through the floating panel. Do not ask for passwords or payment secrets in chat.",
      parameters: Type.Object({ question: Type.String({ minLength: 1, maxLength: 2000 }) }), execute: async (_id, p: any, signal) => text(await ask(p.question, signal)) },
    { name: "report_task_status", label: "Task result", description: "Report completed only after verifying the goal on a fresh screen. Report failed with the specific blocking reason.",
      parameters: Type.Object({ status: Type.Union([Type.Literal("completed"), Type.Literal("failed")]), summary: Type.String({ minLength: 1, maxLength: 6000 }) }),
      execute: async (_id, p: any) => { await report(p.status, p.summary); return text("Result recorded"); } },
  ];
}
