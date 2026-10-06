import { test } from "node:test";
import assert from "node:assert/strict";
import http from "node:http";
import { mkdtemp, rm, readFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { createApp } from "../src/server.js";
import { defaults } from "../src/storage.js";
import { testConnection } from "../src/models.js";
async function listen(server: http.Server) {
  await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", resolve));
  return (server.address() as any).port as number;
}
async function close(server: http.Server) {
  server.closeAllConnections();
  await new Promise<void>((resolve) => server.close(() => resolve()));
}
async function until<T>(
  fn: () => Promise<T>,
  predicate: (value: T) => boolean,
) {
  const stop = Date.now() + 15000;
  while (Date.now() < stop) {
    const v = await fn();
    if (predicate(v)) return v;
    await new Promise((r) => setTimeout(r, 25));
  }
  throw Error("Timed out waiting for state");
}
function mockOpenAI() {
  const requests: any[] = [];
  const server = http.createServer(async (req, res) => {
    let text = "";
    for await (const chunk of req) text += chunk;
    const request = JSON.parse(text);
    requests.push(request);
    assert.equal(req.url, "/v1/chat/completions");
    assert.equal(req.headers.authorization, "Bearer mock-key");
    res.setHeader("Content-Type", "text/event-stream");
    const last = request.messages.at(-1);
    const prompt =
      typeof last.content === "string"
        ? last.content
        : last.content?.map((c: any) => c.text || "").join("");
    const chunk = (delta: any, finish: any = null) =>
      res.write(
        "data: " +
          JSON.stringify({
            id: "mock",
            object: "chat.completion.chunk",
            created: 123,
            model: "mock-model",
            choices: [{ index: 0, delta, finish_reason: finish }],
          }) +
          "\n\n",
      );
    chunk({ role: "assistant" });
    if (last.role === "user" && prompt === "write a file") {
      chunk({
        tool_calls: [
          {
            index: 0,
            id: "call_write",
            type: "function",
            function: {
              name: "write_file",
              arguments: JSON.stringify({
                path: "agent-created.txt",
                content: "Created by real pi tool dispatch",
              }),
            },
          },
        ],
      });
      chunk({}, "tool_calls");
    } else if (last.role === "user" && prompt === "run a command") {
      chunk({
        tool_calls: [
          {
            index: 0,
            id: "call_bash",
            type: "function",
            function: {
              name: "bash",
              arguments: JSON.stringify({ command: "printf approved-command" }),
            },
          },
        ],
      });
      chunk({}, "tool_calls");
    } else {
      chunk({ content: "OK" });
      chunk({}, "stop");
    }
    res.end("data: [DONE]\n\n");
  });
  return { server, requests };
}
test("real pi OpenAI streaming dispatches tools and waits for cancellable shell approval", async () => {
  const temp = await mkdtemp(join(tmpdir(), "pocket-api-"));
  const mock = mockOpenAI();
  const mockPort = await listen(mock.server);
  const socket = http.createServer();
  const port = await listen(socket);
  await close(socket);
  const runtime = await createApp({
    stateDir: join(temp, "state"),
    workspace: join(temp, "workspace"),
    port,
  });
  const server = http.createServer(runtime.app);
  await new Promise<void>((r) => server.listen(port, "127.0.0.1", r));
  const base = "http://127.0.0.1:" + port;
  const request = async (path: string, method = "GET", body?: unknown) => {
    const res = await fetch(base + "/api" + path, {
      method,
      headers: {
        Authorization: "Bearer " + runtime.token,
        "Content-Type": "application/json",
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const data = (await res.json()) as any;
    assert.ok(res.ok, JSON.stringify(data));
    return data;
  };
  try {
    assert.equal((await fetch(base + "/api/state")).status, 401);
    assert.equal(
      (
        await fetch(base + "/api/state", {
          headers: {
            Authorization: "Bearer " + runtime.token,
            Origin: "https://malicious.example",
          },
        })
      ).status,
      403,
    );
    const badHost = http.request(base + "/api/state", {
      headers: { Host: "malicious.example" },
    });
    const hostStatus = await new Promise<number>((r) => {
      badHost.on("response", (res) => {
        res.resume();
        r(res.statusCode!);
      });
      badHost.end();
    });
    assert.equal(hostStatus, 403);
    const settings = {
      ...defaults,
      api: "openai-completions",
      baseUrl: "http://127.0.0.1:" + mockPort + "/v1",
      model: "mock-model",
      apiKey: "mock-key",
    };
    const saved = await request("/settings", "PUT", settings);
    assert.equal(saved.hasKey, true);
    assert.equal(saved.apiKey, undefined);
    const result = await request("/settings/test", "POST", {});
    assert.equal(result.text, "OK");
    const session = await request("/sessions", "POST", {});
    const events = await fetch(base + "/api/events", {
      headers: { Authorization: "Bearer " + runtime.token },
      signal: AbortSignal.timeout(15000),
    });
    assert.match(events.headers.get("content-type")!, /^text\/event-stream/);
    const reader = events.body!.getReader();
    const first = await reader.read();
    assert.match(new TextDecoder().decode(first.value), /connected/);
    await request("/sessions/" + session.id + "/chat", "POST", {
      text: "write a file",
    });
    const finished = await until(
      () => request("/sessions/" + session.id),
      (s) => !s.busy,
    );
    assert.equal(
      await readFile(join(temp, "workspace/agent-created.txt"), "utf8"),
      "Created by real pi tool dispatch",
    );
    assert.ok(
      finished.messages.some(
        (m: any) => m.role === "toolResult" && m.toolName === "write_file",
      ),
    );
    assert.ok(
      mock.requests.some((r) =>
        r.tools?.some((t: any) => t.function.name === "bash"),
      ),
    );
    await request("/sessions/" + session.id + "/chat", "POST", {
      text: "run a command",
    });
    const pending = await until(
      () => request("/sessions/" + session.id),
      (s) => s.approvals.length > 0,
    );
    assert.equal(runtime.processes.jobs.size, 0);
    await request("/approvals/" + pending.approvals[0].id, "POST", {
      allow: true,
    });
    const done = await until(
      () => request("/sessions/" + session.id),
      (s) => !s.busy,
    );
    assert.ok(
      done.messages.some(
        (m: any) =>
          m.role === "toolResult" &&
          JSON.stringify(m.content).includes("approved-command"),
      ),
    );
    // A second shell tool call can be aborted while its permission prompt is open.
    await request("/sessions/" + session.id + "/chat", "POST", {
      text: "run a command",
    });
    await until(
      () => request("/sessions/" + session.id),
      (s) => s.approvals.length > 0,
    );
    await request("/sessions/" + session.id + "/stop", "POST", {});
    await until(
      () => request("/sessions/" + session.id),
      (s) => !s.busy && s.approvals.length === 0,
    );
    await reader.cancel();
    const disk = JSON.parse(
      await readFile(join(temp, "state/sessions.json"), "utf8"),
    );
    assert.equal(disk[0].id, session.id);
    const tunnel = await fetch(base + "/api/tunnels", {
      method: "POST",
      headers: {
        Authorization: "Bearer " + runtime.token,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ port }),
    });
    assert.equal(tunnel.status, 400);
  } finally {
    runtime.close();
    await close(server);
    await close(mock.server);
    await rm(temp, { recursive: true, force: true });
  }
});
test("Anthropic API test uses the configured URL and key through the real pi adapter", async () => {
  const server = http.createServer(async (req, res) => {
    assert.equal(
      new URL(req.url!, "http://localhost").pathname,
      "/v1/messages",
    );
    assert.equal(req.headers["x-api-key"], "anthropic-test-key");
    let body = "";
    for await (const chunk of req) body += chunk;
    assert.equal(JSON.parse(body).model, "custom-claude");
    res.setHeader("Content-Type", "text/event-stream");
    const send = (type: string, data: any) =>
      res.write(
        "event: " +
          type +
          "\ndata: " +
          JSON.stringify({ type, ...data }) +
          "\n\n",
      );
    send("message_start", {
      message: {
        id: "msg_test",
        type: "message",
        role: "assistant",
        model: "custom-claude",
        content: [],
        stop_reason: null,
        stop_sequence: null,
        usage: { input_tokens: 3, output_tokens: 0 },
      },
    });
    send("content_block_start", {
      index: 0,
      content_block: { type: "text", text: "" },
    });
    send("content_block_delta", {
      index: 0,
      delta: { type: "text_delta", text: "OK" },
    });
    send("content_block_stop", { index: 0 });
    send("message_delta", {
      delta: { stop_reason: "end_turn", stop_sequence: null },
      usage: { output_tokens: 1 },
    });
    send("message_stop", {});
    res.end();
  });
  const port = await listen(server);
  try {
    const result = await testConnection({
      ...defaults,
      provider: "anthropic",
      api: "anthropic-messages",
      baseUrl: "http://127.0.0.1:" + port,
      model: "custom-claude",
      apiKey: "anthropic-test-key",
    });
    assert.equal(result.text, "OK");
  } finally {
    await close(server);
  }
});

test("OpenAI Responses adapter streams from a custom base URL", async () => {
  const server = http.createServer(async (req, res) => {
    assert.equal(req.url, "/v1/responses");
    assert.equal(req.headers.authorization, "Bearer responses-test-key");
    let body = "";
    for await (const chunk of req) body += chunk;
    assert.equal(JSON.parse(body).model, "custom-responses");
    res.setHeader("Content-Type", "text/event-stream");
    const send = (type: string, data: any) =>
      res.write(
        "event: " +
          type +
          "\ndata: " +
          JSON.stringify({ type, ...data }) +
          "\n\n",
      );
    const item = {
      id: "msg_1",
      type: "message",
      role: "assistant",
      status: "completed",
      content: [{ type: "output_text", text: "OK", annotations: [] }],
    };
    send("response.created", { response: { id: "resp_1" } });
    send("response.output_item.added", {
      output_index: 0,
      item: { ...item, status: "in_progress", content: [] },
    });
    send("response.output_text.delta", {
      output_index: 0,
      content_index: 0,
      item_id: "msg_1",
      delta: "OK",
    });
    send("response.output_item.done", { output_index: 0, item });
    send("response.completed", {
      response: {
        id: "resp_1",
        model: "custom-responses",
        status: "completed",
        output: [item],
        usage: {
          input_tokens: 3,
          output_tokens: 1,
          total_tokens: 4,
          input_tokens_details: { cached_tokens: 0 },
          output_tokens_details: { reasoning_tokens: 0 },
        },
      },
    });
    res.end();
  });
  const port = await listen(server);
  try {
    const result = await testConnection({
      ...defaults,
      baseUrl: "http://127.0.0.1:" + port + "/v1",
      model: "custom-responses",
      apiKey: "responses-test-key",
    });
    assert.equal(result.text, "OK");
  } finally {
    await close(server);
  }
});
