const $ = (id) => document.getElementById(id);
let token = sessionStorage.getItem("pocket-token") || "";
if (location.hash.startsWith("#token=")) {
  token = decodeURIComponent(location.hash.slice(7));
  history.replaceState(null, "", location.pathname);
  sessionStorage.setItem("pocket-token", token);
}
let state = { sessions: [], jobs: [] },
  current = null,
  selectedJob = "",
  streamController,
  reconnectTimer;
let toastTimer;
const welcome = $("messages").cloneNode(true);
function toast(message) {
  $("toast").textContent = message;
  $("toast").hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => ($("toast").hidden = true), 4500);
}
async function api(path, method = "GET", body) {
  const response = await fetch("/api" + path, {
    method,
    headers: {
      Authorization: "Bearer " + token,
      ...(body === undefined ? {} : { "Content-Type": "application/json" }),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const result = await response.json();
  if (!response.ok) {
    if (response.status === 401) {
      $("pairing").hidden = false;
      streamController?.abort();
    }
    throw Error(result.error || "Request failed");
  }
  return result;
}
function tab(name) {
  document
    .querySelectorAll(".panel")
    .forEach((x) => x.classList.toggle("active", x.id === name));
  document
    .querySelectorAll("nav button")
    .forEach((x) => x.classList.toggle("active", x.dataset.tab === name));
  $("page-title").textContent = {
    chat: "Let’s build something.",
    files: "Everything, in reach.",
    terminal: "Your tools, on device.",
    servers: "Keep your project running.",
    settings: "Make it yours.",
  }[name];
  if (name === "files") safe(loadFiles);
  if (name === "settings")
    $("trust-session").checked = current?.trusted || false;
}
function safe(fn) {
  return Promise.resolve()
    .then(fn)
    .catch((e) => toast(e.message));
}
function bind(id, event, fn) {
  $(id).addEventListener(event, (e) => {
    if (event === "submit") e.preventDefault();
    safe(() => fn(e));
  });
}
function el(tag, className, text) {
  const e = document.createElement(tag);
  if (className) e.className = className;
  if (text !== undefined) e.textContent = text;
  return e;
}
function action(text, fn, className) {
  const button = el("button", className, text);
  button.type = "button";
  button.onclick = () => safe(fn);
  return button;
}
function renderMessages() {
  const box = $("messages"),
    atBottom = box.scrollHeight - box.scrollTop - box.clientHeight < 100;
  box.replaceChildren();
  const messages = (current?.messages || []).filter((m) => m.role !== "system");
  if (current?.streaming) messages.push(current.streaming);
  if (!messages.length)
    box.replaceChildren(
      ...[...welcome.childNodes].map((x) => x.cloneNode(true)),
    );
  for (const message of messages) {
    const card = el("article", "message " + message.role);
    card.append(
      el(
        "div",
        "message-label",
        message.role === "assistant"
          ? "Pocket Agent"
          : message.role === "user"
            ? "You"
            : "Tool · " + (message.toolName || "result"),
      ),
    );
    const content =
      typeof message.content === "string"
        ? [{ type: "text", text: message.content }]
        : message.content || [];
    for (const c of content) {
      if (c.type === "text") card.append(el("pre", "", c.text));
      if (c.type === "toolCall")
        card.append(
          el(
            "div",
            "tool-call",
            "↳ " + c.name + "\n" + JSON.stringify(c.arguments, null, 2),
          ),
        );
    }
    if (message.errorMessage) card.append(el("pre", "", message.errorMessage));
    box.append(card);
  }
  $("stop-agent").hidden = !current?.busy;
  $("send").disabled = !!current?.busy;
  if (atBottom) box.scrollTop = box.scrollHeight;
}
function renderApprovals() {
  $("approvals").replaceChildren();
  for (const approval of current?.approvals || []) {
    const card = el("div", "approval");
    card.append(
      el("strong", "", "Allow " + approval.tool + "?"),
      el("pre", "", JSON.stringify(approval.args, null, 2)),
    );
    for (const allowed of [true, false])
      card.append(
        action(
          allowed ? "Allow command" : "Deny",
          async () => {
            await api("/approvals/" + approval.id, "POST", { allow: allowed });
            current.approvals = current.approvals.filter(
              (a) => a.id !== approval.id,
            );
            renderApprovals();
          },
          allowed ? "primary" : "subtle",
        ),
      );
    $("approvals").append(card);
  }
}
function renderSessions() {
  const select = $("session-select");
  select.replaceChildren();
  for (const session of state.sessions) {
    const option = el("option", "", session.title);
    option.value = session.id;
    select.append(option);
  }
  if (current) select.value = current.id;
}
async function refreshState() {
  state = await api("/state");
  $("workspace-path").textContent = state.workspace;
  renderSessions();
  renderJobs();
}
async function loadSession(id) {
  current = await api("/sessions/" + id);
  sessionStorage.setItem("pocket-session", id);
  renderSessions();
  renderMessages();
  renderApprovals();
  $("trust-session").checked = current.trusted;
}
async function newSession() {
  const created = await api("/sessions", "POST", {});
  await refreshState();
  await loadSession(created.id);
}
function showSettings(s) {
  $("provider").value = s.provider;
  $("api").value = s.api;
  $("base-url").value = s.baseUrl;
  $("model").value = s.model;
  $("context-window").value = s.contextWindow;
  $("max-tokens").value = s.maxTokens;
  $("api-key").value = "";
  $("clear-key").checked = false;
  $("key-status").textContent = s.hasKey
    ? "A key is saved. Leave blank to keep it."
    : "No key saved. Keyless localhost APIs are supported.";
  $("model-label").textContent = s.provider + " / " + s.model;
}
function settingsInput() {
  return {
    provider: $("provider").value,
    api: $("api").value,
    baseUrl: $("base-url").value,
    model: $("model").value,
    apiKey: $("api-key").value,
    clearKey: $("clear-key").checked,
    contextWindow: Number($("context-window").value),
    maxTokens: Number($("max-tokens").value),
  };
}
async function connect() {
  await refreshState();
  showSettings(state.settings);
  $("pairing").hidden = true;
  sessionStorage.setItem("pocket-token", token);
  const saved = sessionStorage.getItem("pocket-session");
  const session =
    state.sessions.find((s) => s.id === saved) || state.sessions.at(-1);
  if (session) await loadSession(session.id);
  else await newSession();
  events();
}
async function events() {
  clearTimeout(reconnectTimer);
  streamController?.abort();
  const controller = new AbortController();
  streamController = controller;
  try {
    const response = await fetch("/api/events", {
      headers: { Authorization: "Bearer " + token },
      signal: controller.signal,
    });
    if (!response.ok) throw Error("Event connection failed");
    const reader = response.body.getReader(),
      decoder = new TextDecoder();
    let buffer = "";
    $("connection").textContent = "Connected to Termux";
    document.body.classList.remove("offline");
    await refreshState();
    if (current) await loadSession(current.id);
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      let boundary;
      while ((boundary = buffer.indexOf("\n\n")) !== -1) {
        const frame = buffer.slice(0, boundary);
        buffer = buffer.slice(boundary + 2);
        for (const line of frame.split("\n"))
          if (line.startsWith("data: ")) handleEvent(JSON.parse(line.slice(6)));
      }
    }
  } catch (e) {
    if (controller.signal.aborted) return;
  }
  $("connection").textContent = "Reconnecting…";
  document.body.classList.add("offline");
  reconnectTimer = setTimeout(() => {
    if (!$("pairing").hidden) return;
    events();
  }, 2000);
}
function handleEvent(data) {
  if (data.type === "process") {
    const index = state.jobs.findIndex((j) => j.id === data.job.id);
    if (index === -1) state.jobs.push(data.job);
    else state.jobs[index] = data.job;
    renderJobs();
    return;
  }
  if (!current || data.sessionId !== current.id) return;
  if (data.type === "agent") {
    const e = data.event;
    if (e.type === "agent_start") current.busy = true;
    if (e.type === "message_update") current.streaming = e.message;
    if (e.type === "message_end") {
      current.streaming = null;
      const index = current.messages.findIndex(
        (m) =>
          m.role === e.message.role &&
          m.timestamp === e.message.timestamp &&
          m.toolCallId === e.message.toolCallId,
      );
      if (index === -1) current.messages.push(e.message);
      else current.messages[index] = e.message;
    }
    if (e.type === "tool_execution_start")
      $("model-label").textContent = "Running " + e.toolName + "…";
    renderMessages();
  }
  if (data.type === "approval") {
    current.approvals ||= [];
    current.approvals.push(data);
    renderApprovals();
  }
  if (data.type === "approval_resolved") {
    current.approvals = (current.approvals || []).filter(
      (a) => a.id !== data.id,
    );
    renderApprovals();
  }
  if (data.type === "error") toast(data.error);
  if (data.type === "idle") {
    current.busy = false;
    current.streaming = null;
    renderMessages();
    safe(async () => {
      await refreshState();
      await loadSession(current.id);
      $("model-label").textContent =
        state.settings.provider + " / " + state.settings.model;
    });
  }
}
async function loadFiles() {
  const path = $("directory").value || ".",
    files = await api("/files?path=" + encodeURIComponent(path));
  $("file-list").replaceChildren();
  if (!files.length)
    $("file-list").append(el("p", "hint", "This directory is empty."));
  for (const file of files) {
    const name =
      (path === "." ? "" : path.replace(/\/$/, "") + "/") + file.name;
    $("file-list").append(
      action(
        (file.directory ? "▸ " : file.symlink ? "↗ " : "· ") + file.name,
        async () => {
          if (file.directory) {
            $("directory").value = name;
            await loadFiles();
          } else {
            $("file-path").value = name;
            $("file-content").value = (
              await api("/file?path=" + encodeURIComponent(name))
            ).content;
          }
        },
      ),
    );
  }
}
function renderJobs() {
  for (const [container, kind] of [
    ["terminal-jobs", "command"],
    ["server-jobs", "servers"],
  ]) {
    const parent = $(container);
    parent.replaceChildren();
    for (const job of state.jobs
      .filter((j) =>
        kind === "command" ? j.kind === "command" : j.kind !== "command",
      )
      .slice()
      .reverse()) {
      const card = el("div", "job"),
        detail = el("div", "job-details");
      detail.append(
        el("h4", "", job.label),
        el(
          "p",
          "",
          job.status + (job.exitCode === null ? "" : " · exit " + job.exitCode),
        ),
      );
      card.append(detail);
      card.append(
        action("Logs", () => {
          selectedJob = job.id;
          renderJobs();
        }),
      );
      if (job.status === "running")
        card.append(
          action("Stop", () =>
            api("/processes/" + job.id + "/stop", "POST", {}),
          ),
        );
      if (job.kind === "server")
        card.append(
          action("Open ↗", () => {
            window.open(
              "http://127.0.0.1:" + job.port,
              "_blank",
              "noopener,noreferrer",
            );
          }),
        );
      parent.append(card);
    }
  }
  const job = state.jobs.find((j) => j.id === selectedJob);
  if (job) {
    const output = $(
      job.kind === "command" ? "terminal-output" : "server-output",
    );
    output.textContent = "$ " + job.command + "\n\n" + job.output;
    output.scrollTop = output.scrollHeight;
    const links = $(job.kind === "command" ? "terminal-links" : "server-links");
    links.replaceChildren();
    const urls = [
      ...new Set(
        job.output.match(/https:\/\/[a-z0-9.-]+(?:\/[a-z0-9_/?=&%.-]*)?/gi) ||
          [],
      ),
    ];
    for (const url of urls.slice(0, 5)) {
      const parsed = new URL(url);
      if (
        parsed.hostname.endsWith(".devtunnels.ms") ||
        ["github.com", "microsoft.com", "login.microsoftonline.com"].includes(
          parsed.hostname,
        )
      ) {
        links.append(
          action("Open " + parsed.hostname + " ↗", () =>
            window.open(url, "_blank", "noopener,noreferrer"),
          ),
        );
      }
    }
  }
}
async function startJob(path, body) {
  const job = await api(path, "POST", body);
  selectedJob = job.id;
  await refreshState();
  toast("Started " + job.label);
}
$("navigation").onclick = (e) => {
  const button = e.target.closest("[data-tab]");
  if (button) tab(button.dataset.tab);
};
$("messages").onclick = (e) => {
  const button = e.target.closest("[data-prompt]");
  if (button) {
    $("prompt").value = button.dataset.prompt;
    $("prompt").focus();
  }
};
bind("pair-form", "submit", async () => {
  token = $("token").value.trim();
  try {
    await connect();
    $("token").value = "";
    $("pair-error").textContent = "";
  } catch (e) {
    $("pair-error").textContent = e.message;
  }
});
bind("new-chat", "click", newSession);
bind("session-select", "change", (e) => loadSession(e.target.value));
bind("delete-chat", "click", async () => {
  if (current?.busy) throw Error("Stop the agent before deleting");
  if (!confirm("Delete this conversation?")) return;
  await api("/sessions/" + current.id, "DELETE");
  await refreshState();
  if (state.sessions.length) await loadSession(state.sessions.at(-1).id);
  else await newSession();
});
bind("chat-form", "submit", async () => {
  const text = $("prompt").value.trim();
  if (!text) return;
  await api("/sessions/" + current.id + "/chat", "POST", { text });
  $("prompt").value = "";
  current.busy = true;
  renderMessages();
});
$("prompt").addEventListener("keydown", (e) => {
  if (e.key === "Enter" && (e.ctrlKey || e.metaKey)) {
    $("chat-form").requestSubmit();
  }
});
bind("stop-agent", "click", () =>
  api("/sessions/" + current.id + "/stop", "POST", {}),
);
bind("trust-session", "change", async (e) => {
  await api("/sessions/" + current.id + "/trust", "PUT", {
    trust: e.target.checked,
  });
  current.trusted = e.target.checked;
  toast(
    current.trusted
      ? "Commands allowed for this conversation"
      : "Command approval enabled",
  );
});
bind("path-form", "submit", loadFiles);
bind("parent-dir", "click", () => {
  const parts = $("directory")
    .value.split("/")
    .filter((x) => x && x !== ".");
  parts.pop();
  $("directory").value = parts.join("/") || ".";
  return loadFiles();
});
bind("new-file", "click", () => {
  $("file-path").value =
    ($("directory").value === "." ? "" : $("directory").value + "/") +
    "new-file.txt";
  $("file-content").value = "";
  $("file-path").focus();
});
bind("editor-form", "submit", async () => {
  await api("/file", "PUT", {
    path: $("file-path").value,
    content: $("file-content").value,
  });
  toast("File saved");
  await loadFiles();
});
bind("command-form", "submit", () =>
  startJob("/commands", {
    command: $("command").value,
    cwd: $("command-cwd").value,
  }),
);
bind("server-form", "submit", () =>
  startJob("/servers", {
    command: $("server-command").value,
    cwd: $("server-cwd").value,
    port: Number($("server-port").value),
  }),
);
bind("tunnel-form", "submit", () =>
  startJob("/tunnels", {
    port: Number($("tunnel-port").value),
    anonymous: $("anonymous").checked,
  }),
);
bind("tunnel-login", "click", async () => {
  await startJob("/tunnels/login", {});
  tab("terminal");
});
bind("settings-form", "submit", async () => {
  const result = await api("/settings", "PUT", settingsInput());
  state.settings = result;
  showSettings(result);
  toast("Connection saved");
});
bind("test-api", "click", async () => {
  $("test-api").disabled = true;
  $("test-result").textContent = "Testing a real model request…";
  try {
    const result = await api("/settings/test", "POST", settingsInput());
    $("test-result").textContent =
      "Connected · " + result.latencyMs + " ms · " + result.text;
  } catch (e) {
    $("test-result").textContent = e.message;
  } finally {
    $("test-api").disabled = false;
  }
});
bind("provider", "change", () => {
  const anthropic = $("provider").value === "anthropic";
  $("api").value = anthropic ? "anthropic-messages" : "openai-responses";
  $("base-url").value = anthropic
    ? "https://api.anthropic.com"
    : "https://api.openai.com/v1";
  $("model").value = anthropic ? "claude-sonnet-4-6" : "gpt-4.1";
  $("api-key").value = "";
  $("key-status").textContent =
    "Enter a key for this provider; a previous provider’s key is not reused.";
});
bind("disconnect", "click", () => {
  streamController?.abort();
  clearTimeout(reconnectTimer);
  sessionStorage.removeItem("pocket-token");
  token = "";
  $("pairing").hidden = false;
});
if (token) safe(connect);
