import { test } from "node:test";
import assert from "node:assert/strict";
import http from "node:http";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
const { createApp } = process.env.POCKET_TEST_BUNDLE
  ? await import("../../dist/server.js")
  : await import("../src/server.js");
const { Connect } = process.env.POCKET_TEST_BUNDLE
  ? await import("../../dist/connect.js")
  : await import("../src/connect.js");
const { Store } = process.env.POCKET_TEST_BUNDLE
  ? await import("../../dist/storage.js")
  : await import("../src/storage.js");

/**
 * A paired device reaches the phone through a tunnel, so it comes from another host. It carries a
 * key of its own and opens the chats and nothing else.
 */
test("a paired device reaches the chats from another host and nothing else", async () => {
  const temp = await mkdtemp(join(tmpdir(), "pocket-connect-"));
  const socket = http.createServer();
  await new Promise<void>((r) => socket.listen(0, "127.0.0.1", r));
  const port = (socket.address() as any).port;
  await new Promise<void>((r) => socket.close(() => r()));
  const runtime = await createApp({
    stateDir: join(temp, "state"),
    workspace: join(temp, "workspace"),
    port,
  });
  const server = http.createServer(runtime.app);
  await new Promise<void>((r) => server.listen(port, "127.0.0.1", r));
  const base = "http://127.0.0.1:" + port;

  // The request is made by hand, so the host can be the one the tunnel gives.
  const call = (path: string, key: string, host?: string) =>
    new Promise<{ status: number; body: any }>((resolve, reject) => {
      const request = http.request(
        {
          host: "127.0.0.1",
          port,
          path,
          method: "GET",
          headers: {
            Authorization: "Bearer " + key,
            Host: host || `127.0.0.1:${port}`,
          },
        },
        (response) => {
          let text = "";
          response.on("data", (chunk) => (text += chunk));
          response.on("end", () =>
            resolve({ status: response.statusCode || 0, body: text ? JSON.parse(text) : null }),
          );
        },
      );
      request.on("error", reject);
      request.end();
    });

  try {
    // The owner makes a device.
    const made = await (
      await fetch(base + "/api/clients", {
        method: "POST",
        headers: { Authorization: "Bearer " + runtime.token, "Content-Type": "application/json" },
        body: JSON.stringify({ name: "iPad" }),
      })
    ).json() as any;
    assert.ok(made.token, "the device carries a key of its own");
    assert.notEqual(made.token, runtime.token, "the key is not the owner's own");
    assert.equal(made.scope, "chat");

    // The device reaches the chats from the other side of the tunnel.
    const bots = await call("/api/bots", made.token, "phone.devtunnels.ms");
    assert.equal(bots.status, 200);
    assert.ok(Array.isArray(bots.body.bots));

    // It does not reach the terminal, the files or the model keys.
    const settings = await call("/api/settings/key", made.token, "phone.devtunnels.ms");
    assert.equal(settings.status, 403);
    const files = await call("/api/projects", made.token, "phone.devtunnels.ms");
    assert.equal(files.status, 403);

    // The owner's own key stays on the phone.
    const owner = await call("/api/bots", runtime.token, "phone.devtunnels.ms");
    assert.equal(owner.status, 403);

    // A key that is not one of ours opens nothing. From another host the request is turned away
    // before the key is even looked at.
    const stranger = await call("/api/bots", "not-a-key", "phone.devtunnels.ms");
    assert.equal(stranger.status, 403);
    const strangerAtHome = await call("/api/bots", "not-a-key");
    assert.equal(strangerAtHome.status, 401);

    // A browser on another origin is told it may read the answer, so a client on the web can.
    const preflight = await new Promise<{ status: number; headers: any }>((resolve, reject) => {
      const request = http.request(
        {
          host: "127.0.0.1",
          port,
          path: "/api/bots",
          method: "OPTIONS",
          headers: {
            Host: "phone.devtunnels.ms",
            Origin: "https://client.example",
            "Access-Control-Request-Method": "GET",
            "Access-Control-Request-Headers": "authorization",
          },
        },
        (response) => {
          response.resume();
          resolve({ status: response.statusCode || 0, headers: response.headers });
        },
      );
      request.on("error", reject);
      request.end();
    });
    assert.equal(preflight.status, 204);
    assert.equal(preflight.headers["access-control-allow-origin"], "https://client.example");
  } finally {
    server.closeAllConnections();
    await new Promise<void>((r) => server.close(() => r()));
    await rm(temp, { recursive: true, force: true });
  }
});

/** A device that is given the whole phone may use the rest of it. */
test("a device with the whole phone may use the rest of it", async () => {  const temp = await mkdtemp(join(tmpdir(), "pocket-connect-full-"));
  const socket = http.createServer();
  await new Promise<void>((r) => socket.listen(0, "127.0.0.1", r));
  const port = (socket.address() as any).port;
  await new Promise<void>((r) => socket.close(() => r()));
  const runtime = await createApp({
    stateDir: join(temp, "state"),
    workspace: join(temp, "workspace"),
    port,
  });
  const server = http.createServer(runtime.app);
  await new Promise<void>((r) => server.listen(port, "127.0.0.1", r));
  const base = "http://127.0.0.1:" + port;
  try {
    const made = await (
      await fetch(base + "/api/clients", {
        method: "POST",
        headers: { Authorization: "Bearer " + runtime.token, "Content-Type": "application/json" },
        body: JSON.stringify({ name: "My laptop", scope: "full" }),
      })
    ).json() as any;
    assert.equal(made.scope, "full");
    const response = await fetch(base + "/api/projects", {
      headers: { Authorization: "Bearer " + made.token, Host: "phone.devtunnels.ms" },
    });
    assert.equal(response.status, 200);

    // The device can be taken back.
    await fetch(base + "/api/clients/" + made.id, {
      method: "DELETE",
      headers: { Authorization: "Bearer " + runtime.token },
    });
    const gone = await fetch(base + "/api/projects", {
      headers: { Authorization: "Bearer " + made.token, Host: "phone.devtunnels.ms" },
    });
    assert.equal(gone.status, 401);
  } finally {
    server.closeAllConnections();
    await new Promise<void>((r) => server.close(() => r()));
    await rm(temp, { recursive: true, force: true });
  }
});

/**
 * The phone asks for the sign-in it needs before it makes a link.
 *
 * The Dev Tunnels CLI will not make a tunnel without an account, so the phone asks for one, shows
 * the code the owner has to enter on another device, and makes the link once the sign-in is done.
 */
test("the phone asks for the sign-in before it makes a link", async () => {
  const temp = await mkdtemp(join(tmpdir(), "pocket-connect-signin-"));
  const store = new Store(join(temp, "state"));
  await store.init();
  let signedIn = false;
  let loginOutput = "";
  const hosted: string[] = [];
  const connect = new Connect(
    store,
    {
      create: () => ({ id: "made", output: "", status: "running" }),
      host: (name: string) => {
        hosted.push(name);
        return { id: "host", output: "https://phone-1234.usw2.devtunnels.ms", status: "running" };
      },
      stop: () => {},
      output: () => loginOutput,
      status: () => "running",
      userShow: async () => {
        // The CLI takes seconds to start on the phone, so the page must not wait for it.
        await new Promise((r) => setTimeout(r, 200));
        return signedIn ? "Some Person\nuser@example.com" : "Not logged in.";
      },
      login: () => ({ id: "login", output: "", status: "running" }),
    },
    8787,
  );
  try {
    await connect.init();
    // The page is answered at once, without waiting for the CLI.
    const started = Date.now();
    let view = await connect.poll();
    assert.ok(Date.now() - started < 100, "the page is not held up by the CLI");
    // The phone is not signed in, so it says so.
    view = await connect.checkNow();
    assert.equal(view.signedIn, false);
    assert.equal(view.devices.length, 0);
    assert.equal(hosted.length, 0);

    // A device paired now waits for the sign-in.
    await connect.create("iPad");
    view = await connect.poll();
    assert.equal(view.devices.length, 1);
    assert.equal(view.devices[0].url, "", "the link waits for the sign-in");
    assert.equal(hosted.length, 0, "no tunnel is made before the sign-in");

    // The sign-in starts, and the code is read from what the CLI says.
    loginOutput = "Browse to https://github.com/login/device and enter the code: 3556-4454";
    await connect.signIn();
    view = await connect.checkNow();
    assert.equal(view.signedIn, false);
    assert.equal(view.login?.code, "3556-4454");
    assert.equal(view.login?.url, "https://github.com/login/device");

    // Once the sign-in is done, the link is made and the device carries it.
    signedIn = true;
    view = await connect.checkNow();
    assert.equal(view.signedIn, true);
    assert.equal(hosted.length, 1);
    assert.equal(view.devices[0].url, "https://phone-1234.usw2.devtunnels.ms");
  } finally {
    await rm(temp, { recursive: true, force: true });
  }
});
