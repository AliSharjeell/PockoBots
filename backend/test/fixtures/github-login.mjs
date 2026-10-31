import { writeFile } from 'node:fs/promises';
import { join } from 'node:path';
if (process.argv.includes('token')) {
  console.log('fixture-github-private-token');
} else {
  if (process.env.GH_TOKEN || process.env.GITHUB_TOKEN) process.exit(3);
  await writeFile(join(process.env.GH_CONFIG_DIR, 'hosts.yml'), 'fixture-github-private-token');
  console.error('! First copy your one-time code: ABCD-1234');
  console.error('Press Enter to open https://github.com/login/device');
  await new Promise(resolve => setTimeout(resolve, process.argv.includes('--slow') ? 30000 : 400));
}
