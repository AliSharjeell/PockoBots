import { createInterface } from 'node:readline';
createInterface({ input: process.stdin }).on('line', line => {
  const message = JSON.parse(line); if (!('id' in message)) return;
  const result = message.method === 'initialize' ? { protocolVersion: message.params.protocolVersion, capabilities: { tools: {} }, serverInfo: { name: 'stdio-fixture', version: '1' } }
    : message.method === 'tools/list' ? { tools: [{ name: 'local_note', description: 'Read a local fixture note', inputSchema: { type: 'object', properties: {} } }] }
    : message.method === 'tools/call' ? { content: [{ type: 'text', text: 'stdio transport ran on this machine' }] } : {};
  process.stdout.write(JSON.stringify({ jsonrpc: '2.0', id: message.id, result }) + '\n');
});
