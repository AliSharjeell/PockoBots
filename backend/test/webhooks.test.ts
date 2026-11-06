import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, rm } from 'node:fs/promises';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { createApp } from '../src/server.js';

test('external webhooks mode can receive calls and messages from external apps by bot name or ID', async () => {
  const root = await mkdtemp(join(tmpdir(), 'pocket-webhooks-'));
  const port = 18991;
  const runtime = await createApp({ stateDir: join(root, 'state'), workspace: join(root, 'workspace'), port });
  const server = runtime.app.listen(port, '127.0.0.1');

  try {
    const fireBot = await runtime.bots.create({ name: 'Fire Guard', role: 'Watch for fires', allowUrgent: true });
    const chatBot = await runtime.bots.create({ name: 'Chat Bot', role: 'Helpful friend', allowUrgent: false });

    // 1. Get and update webhook config with pairing token
    const configRes = await fetch(`http://127.0.0.1:${port}/api/webhooks/config`, {
      headers: { Authorization: `Bearer ${runtime.token}` },
    });
    assert.equal(configRes.status, 200);
    const config = await configRes.json();
    assert.equal(config.enabled, true);
    assert.ok(config.secret.length > 10);
    assert.equal(config.allowLocalWithoutSecret, true);

    // 2. Discover bots via GET /api/webhooks/bot
    const discoverRes = await fetch(`http://127.0.0.1:${port}/api/webhooks/bot`, {
      headers: { 'X-Webhook-Secret': config.secret },
    });
    assert.equal(discoverRes.status, 200);
    const discovery = await discoverRes.json();
    assert.equal(discovery.ok, true);
    assert.equal(discovery.bots.length, 2);

    // 3. External app webhooks bot by name with call: true (e.g. CV app detecting fire)
    const callRes = await fetch(`http://127.0.0.1:${port}/api/webhooks/bot`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Webhook-Secret': config.secret,
      },
      body: JSON.stringify({
        bot: 'fire guard', // Case-insensitive matching
        message: 'Fire detected in living room camera feed!',
        call: true,
      }),
    });
    assert.equal(callRes.status, 200);
    const callBody = await callRes.json();
    assert.equal(callBody.ok, true);
    assert.equal(callBody.bot.name, 'Fire Guard');
    assert.equal(callBody.action, 'call');
    assert.ok(callBody.alert);
    assert.equal(callBody.alert.urgent, true);
    assert.match(callBody.alert.text, /Fire detected/);

    // Verify urgent alert is pending in runtime
    assert.equal(runtime.bots.pendingAlerts().filter(a => a.urgent).length, 1);

    // 4. Repeat urgent call bypasses normal 10-minute rate limit for emergency webhooks
    const repeatCallRes = await fetch(`http://127.0.0.1:${port}/api/webhooks/bot/Fire%20Guard`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Webhook-Secret': config.secret,
      },
      body: JSON.stringify({
        message: 'Fire flare-up detected!',
        action: 'call',
      }),
    });
    assert.equal(repeatCallRes.status, 200);
    const repeatBody = await repeatCallRes.json();
    assert.equal(repeatBody.ok, true);
    assert.equal(repeatBody.alert.urgent, true);

    // 5. Normal notification action via action: "notify"
    const notifyRes = await fetch(`http://127.0.0.1:${port}/api/webhooks/bot`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${config.secret}`,
      },
      body: JSON.stringify({
        bot: fireBot.id, // Target by bot ID
        message: 'Smoke dissipated',
        action: 'notify',
      }),
    });
    assert.equal(notifyRes.status, 200);
    const notifyBody = await notifyRes.json();
    assert.equal(notifyBody.action, 'notify');
    assert.equal(notifyBody.alert.urgent, false);

    // 6. Regular message action (default)
    const msgRes = await fetch(`http://127.0.0.1:${port}/api/webhooks/bot?bot=Chat%20Bot&message=Hello%20bot`, {
      method: 'POST',
      headers: { 'X-Webhook-Secret': config.secret },
    });
    assert.equal(msgRes.status, 200);
    const msgBody = await msgRes.json();
    assert.equal(msgBody.action, 'message');
    assert.equal(msgBody.bot.name, 'Chat Bot');

    // 7. Error cases: unknown bot
    const unknownRes = await fetch(`http://127.0.0.1:${port}/api/webhooks/bot`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Webhook-Secret': config.secret,
      },
      body: JSON.stringify({
        bot: 'NonExistentBot',
        message: 'Test',
      }),
    });
    assert.equal(unknownRes.status, 400);
    const unknownBody = await unknownRes.json();
    assert.match(unknownBody.error, /Bot "NonExistentBot" not found/);

    // 8. Error cases: disabled webhook mode
    await runtime.webhooks.updateConfig({ enabled: false });
    const disabledRes = await fetch(`http://127.0.0.1:${port}/api/webhooks/bot`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Webhook-Secret': config.secret,
      },
      body: JSON.stringify({
        bot: 'Fire Guard',
        message: 'Test',
      }),
    });
    assert.equal(disabledRes.status, 403);
    assert.match((await disabledRes.json()).error, /disabled/);

    // Re-enable and test invalid secret when allowLocalWithoutSecret is false
    await runtime.webhooks.updateConfig({ enabled: true, allowLocalWithoutSecret: false });
    const badSecretRes = await fetch(`http://127.0.0.1:${port}/api/webhooks/bot`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Webhook-Secret': 'wrong-secret',
      },
      body: JSON.stringify({
        bot: 'Fire Guard',
        message: 'Test',
      }),
    });
    assert.equal(badSecretRes.status, 401);
  } finally {
    server.close();
    await runtime.close();
    await rm(root, { recursive: true, force: true });
  }
});
