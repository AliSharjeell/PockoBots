#!/usr/bin/env bash
set -euo pipefail
# This displays a secret deliberately for local pairing; do not share its output.
node --input-type=module <<'JS'
import {readFile} from 'node:fs/promises';
import {join} from 'node:path';
import {homedir} from 'node:os';
const path=join(process.env.POCKET_STATE || join(homedir(),'.local/share/pocket-agent'),'token.json');
try { console.log('Pairing token (keep private):\n'+JSON.parse(await readFile(path,'utf8'))); }
catch { console.error('Start the backend first with pocket-agent-start or npm start.');process.exitCode=1; }
JS
