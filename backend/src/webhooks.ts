import { randomBytes, timingSafeEqual, createHash } from 'node:crypto';
import type { Store } from './storage.js';
import type { Bots } from './bots.js';

export type WebhookConfig = {
  enabled: boolean;
  secret: string;
  allowLocalWithoutSecret: boolean;
};

export type WebhookTriggerRequest = {
  bot?: string;
  message?: string;
  text?: string;
  prompt?: string;
  action?: 'call' | 'message' | 'notify';
  call?: boolean;
  urgent?: boolean;
  secret?: string;
};

/**
 * Incoming webhooks for external apps/servers running on the phone (or local network).
 * Allows apps (e.g. computer-vision apps, local daemons, scripts) to trigger bot messages,
 * push notifications, or full-screen ringing calls by bot name or ID.
 */
export class Webhooks {
  private config: WebhookConfig = {
    enabled: true,
    secret: randomBytes(16).toString('hex'),
    allowLocalWithoutSecret: true,
  };

  constructor(private store: Store, private bots: Bots) {}

  async init() {
    const saved = await this.store.read<Partial<WebhookConfig>>('webhooks', {});
    this.config = {
      enabled: typeof saved.enabled === 'boolean' ? saved.enabled : true,
      secret: typeof saved.secret === 'string' && saved.secret ? saved.secret : randomBytes(16).toString('hex'),
      allowLocalWithoutSecret: typeof saved.allowLocalWithoutSecret === 'boolean' ? saved.allowLocalWithoutSecret : true,
    };
    await this.store.write('webhooks', this.config);
  }

  getConfig(port?: number) {
    const actualPort = port || 18878;
    return {
      ...this.config,
      url: `http://127.0.0.1:${actualPort}/api/webhooks/bot`,
      exampleCurl: `curl -X POST http://127.0.0.1:${actualPort}/api/webhooks/bot -H "Content-Type: application/json" -H "X-Webhook-Secret: ${this.config.secret}" -d '{"bot": "any bot name", "message": "Fire detected!", "call": true}'`,
    };
  }

  async updateConfig(update: Partial<WebhookConfig>) {
    if (typeof update.enabled === 'boolean') this.config.enabled = update.enabled;
    if (typeof update.secret === 'string' && update.secret.trim()) this.config.secret = update.secret.trim();
    if (typeof update.allowLocalWithoutSecret === 'boolean') this.config.allowLocalWithoutSecret = update.allowLocalWithoutSecret;
    await this.store.write('webhooks', this.config);
    return this.config;
  }

  async regenerateSecret() {
    this.config.secret = randomBytes(16).toString('hex');
    await this.store.write('webhooks', this.config);
    return this.config;
  }

  isAuthorized(candidateSecret: string | undefined, isLoopback: boolean): boolean {
    if (!this.config.enabled) return false;
    if (isLoopback && this.config.allowLocalWithoutSecret && !candidateSecret) return true;
    if (!candidateSecret) return false;
    const digest = (x: string) => createHash('sha256').update(x).digest();
    try {
      return timingSafeEqual(digest(candidateSecret), digest(this.config.secret));
    } catch {
      return false;
    }
  }

  async handleWebhook(targetBot: string, body: WebhookTriggerRequest) {
    if (!this.config.enabled) throw Error('External webhook mode is disabled');
    const bot = this.bots.find(targetBot);
    if (!bot) {
      const names = this.bots.list().map(b => b.name);
      throw Error(`Bot "${targetBot}" not found. Available bots: ${names.length ? names.join(', ') : '(none)'}`);
    }
    const message = (body.message || body.text || body.prompt || '').trim();
    if (!message) throw Error('Provide a webhook message, text or prompt');

    const shouldCall = body.call === true || body.urgent === true || body.action === 'call';
    const isNotify = body.action === 'notify' && !shouldCall;

    let alertResult: any = null;
    let messageResult: any = null;

    if (shouldCall) {
      alertResult = await this.bots.alert(bot.id, message, true, true);
      messageResult = await this.bots.sendOwnerMessage(bot.id, `[External Webhook Call]: ${message}`);
    } else if (isNotify) {
      alertResult = await this.bots.alert(bot.id, message, false, true);
      messageResult = await this.bots.sendOwnerMessage(bot.id, `[External Webhook Notification]: ${message}`);
    } else {
      messageResult = await this.bots.sendOwnerMessage(bot.id, `[External Webhook]: ${message}`);
    }

    return {
      ok: true,
      bot: { id: bot.id, name: bot.name },
      action: shouldCall ? 'call' : isNotify ? 'notify' : 'message',
      alert: alertResult,
      message: messageResult,
    };
  }
}
