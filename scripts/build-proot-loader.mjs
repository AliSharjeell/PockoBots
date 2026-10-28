import { execFileSync } from 'node:child_process';
import { mkdirSync } from 'node:fs';
import { join, dirname, resolve } from 'node:path';
const [ndk, output] = process.argv.slice(2);
if (!ndk || !output) throw Error('Expected NDK directory and output path');
const host = process.platform === 'win32' ? 'windows-x86_64' : process.platform === 'darwin' ? 'darwin-x86_64' : 'linux-x86_64';
const source = resolve('third_party/proot-loader/assets/proot-loader-source');
mkdirSync(dirname(output), { recursive: true });
execFileSync(join(ndk, 'toolchains/llvm/prebuilt', host, 'bin/clang' + (process.platform === 'win32' ? '.exe' : '')), [
  '--target=x86_64-linux-android26', '-I' + source, '-O2', '-fno-stack-protector', '-fno-builtin', '-nostdlib', '-static',
  '-Wl,-e,_start', '-Wl,-Ttext=0x600000000000', '-Wl,-z,max-page-size=16384', join(source, 'loader/loader.c'), '-o', output
], { stdio: 'inherit' });
