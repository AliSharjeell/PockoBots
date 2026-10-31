import { writeFile,readFile } from 'node:fs/promises';
import { join } from 'node:path';
if(process.argv.includes('--with-token')){
 let input='';for await(const chunk of process.stdin)input+=chunk;
 if(input.trim()!=='fixture-github-private-token'||process.env.GH_TOKEN||process.env.GITHUB_TOKEN)process.exit(3);
 await writeFile(join(process.env.GH_CONFIG_DIR,'hosts.yml'),input.trim());
}else if(process.argv.includes('repo')&&process.argv.includes('list')){
 if((await readFile(join(process.env.GH_CONFIG_DIR,'hosts.yml'),'utf8')).trim()!=='fixture-github-private-token')process.exit(4);
 console.log(JSON.stringify([{nameWithOwner:'owner/private-project',isPrivate:true}]));
}else if (process.argv.includes('token')) {
  console.log('fixture-github-private-token');
} else {
  if(!process.argv.includes('--scopes')||!process.argv.includes('repo,read:org'))process.exit(5);
  if (process.env.GH_TOKEN || process.env.GITHUB_TOKEN) process.exit(3);
  await writeFile(join(process.env.GH_CONFIG_DIR, 'hosts.yml'), 'fixture-github-private-token');
  console.error('! First copy your one-time code: ABCD-1234');
  console.error('Press Enter to open https://github.com/login/device');
  await new Promise(resolve => setTimeout(resolve, process.argv.includes('--slow') ? 30000 : 400));
}
