import { mkdir, realpath, readFile, writeFile, readdir, stat } from 'node:fs/promises';
import { resolve, relative, dirname, isAbsolute } from 'node:path';
export class Workspace {
  root = '';
  constructor(private requested: string) {}
  async init() { await mkdir(this.requested, {recursive:true}); this.root = await realpath(this.requested); }
  private within(path: string) { const r = relative(this.root, path); if (r === '..' || r.startsWith('../') || isAbsolute(r)) throw Error('Path is outside workspace'); return path; }
  async path(name = '.', create = false): Promise<string> {
    if (typeof name !== 'string' || name.includes('\0')) throw Error('Invalid path');
    const path = this.within(resolve(this.root, name));
    try { return this.within(await realpath(path)); }
    catch (e: any) {
      if (!create || e.code !== 'ENOENT') throw e;
      // Resolve all existing ancestors before creating directories; reject escaping symlinks.
      let parent = dirname(path);
      while (true) {
        try { this.within(await realpath(parent)); break; }
        catch (err: any) { if (err.code !== 'ENOENT') throw err; parent = dirname(parent); }
      }
      await mkdir(dirname(path), {recursive:true});
      this.within(await realpath(dirname(path)));
      return path;
    }
  }
  async read(name: string) { const p = await this.path(name); if ((await stat(p)).size > 1024 * 1024) throw Error('File exceeds 1 MiB editor limit'); return readFile(p, 'utf8'); }
  async write(name: string, content: string) { if (typeof content !== 'string' || Buffer.byteLength(content) > 1024 * 1024) throw Error('File exceeds 1 MiB editor limit'); await writeFile(await this.path(name, true), content); }
  async list(name = '.') {
    const items = await readdir(await this.path(name), {withFileTypes:true});
    return items.map(x => ({name:x.name, directory:x.isDirectory(), symlink:x.isSymbolicLink()})).sort((a,b) => Number(b.directory)-Number(a.directory) || a.name.localeCompare(b.name));
  }
}
