import { mkdir, readFile, writeFile, rename, chmod } from 'node:fs/promises';
import { randomBytes } from 'node:crypto';
import { join } from 'node:path';
export class Store {
  private writes = new Map<string, Promise<void>>();
  constructor(readonly dir: string) {}
  async init() { await mkdir(this.dir, { recursive: true, mode: 0o700 }); await chmod(this.dir, 0o700); }
  async read<T>(name: string, fallback: T): Promise<T> {
    try { return JSON.parse(await readFile(join(this.dir, name + '.json'), 'utf8')); }
    catch (e: any) { if (e.code === 'ENOENT') return fallback; throw e; }
  }
  async write(name: string, value: unknown) {
    const data = JSON.stringify(value);
    const prior = this.writes.get(name) || Promise.resolve();
    const operation = prior.catch(() => {}).then(async () => {
      const path = join(this.dir, name + '.json');
      const temp = path + '.' + randomBytes(6).toString('hex') + '.tmp';
      await writeFile(temp, data, { mode: 0o600 });
      await rename(temp, path); await chmod(path, 0o600);
    });
    this.writes.set(name, operation);
    try { await operation; } finally { if (this.writes.get(name) === operation) this.writes.delete(name); }
  }
  async token() {
    let token = await this.read<string>('token', '');
    if (!token) { token = randomBytes(32).toString('hex'); await this.write('token', token); }
    return token;
  }
}
export type Settings = {
  provider: 'openai' | 'anthropic'; api: 'openai-completions' | 'openai-responses' | 'anthropic-messages';
  baseUrl: string; model: string; apiKey: string; contextWindow: number; maxTokens: number;
};
export const defaults: Settings = { provider: 'openai', api: 'openai-responses', baseUrl: 'https://api.openai.com/v1', model: 'gpt-4.1', apiKey: '', contextWindow: 128000, maxTokens: 8192 };
export function validateSettings(input: any, previous: Settings): Settings {
  const s = { ...previous, ...input, apiKey: input.apiKey === undefined || input.apiKey === '' ? (input.provider && input.provider !== previous.provider ? '' : previous.apiKey) : input.apiKey };
  if (input.clearKey === true) s.apiKey = '';
  if (!['openai', 'anthropic'].includes(s.provider) || !['openai-completions', 'openai-responses', 'anthropic-messages'].includes(s.api)) throw Error('Unsupported provider or API');
  if ((s.provider === 'anthropic') !== (s.api === 'anthropic-messages')) throw Error('Provider and API protocol must match');
  const url = new URL(s.baseUrl);
  if (url.username || url.password || url.search || url.hash) throw Error('Base URL must not contain credentials, query, or fragment');
  if (url.protocol !== 'https:' && !(url.protocol === 'http:' && ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname))) throw Error('Use HTTPS, or HTTP for localhost');
  if (typeof s.model !== 'string' || !s.model.trim() || s.model.length > 200 || typeof s.apiKey !== 'string') throw Error('Model and key must be strings');
  for (const k of ['contextWindow', 'maxTokens'] as const) if (!Number.isInteger(s[k]) || s[k] < 128 || s[k] > 2000000) throw Error('Invalid token limits');
  if (s.maxTokens > s.contextWindow) throw Error('Output limit exceeds context window');
  return {provider:s.provider,api:s.api,baseUrl:s.baseUrl.replace(/\/$/, ''),model:s.model.trim(),apiKey:s.apiKey,contextWindow:s.contextWindow,maxTokens:s.maxTokens};
}
export function publicSettings(s: Settings) { const {apiKey, ...rest} = s; return {...rest, hasKey: !!apiKey}; }
