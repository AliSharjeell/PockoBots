import { randomUUID } from "node:crypto";
import { mkdir, readdir } from "node:fs/promises";
import { join } from "node:path";
import { Workspace } from "./workspace.js";
import type { Store } from "./storage.js";

export type Project = { id: string; name: string; directory: string; created: number };
export class Projects {
  private records: Project[] = [];
  constructor(private store: Store, private root: Workspace) {}
  async init() {
    this.records = await this.store.read<Project[]>("projects", []);
    // Adopt existing directories without moving user files.
    for (const entry of await readdir(this.root.root, { withFileTypes: true })) {
      if (entry.isDirectory() && !entry.name.startsWith(".") &&
          !this.records.some((p) => p.directory === entry.name)) {
        this.records.push({ id: randomUUID(), name: entry.name, directory: entry.name, created: Date.now() });
      }
    }
    await this.store.write("projects", this.records);
  }
  list() { return this.records.map((p) => ({ ...p })); }
  get(id: string) {
    const project = this.records.find((p) => p.id === id);
    if (!project) throw Error("Project not found");
    return project;
  }
  async create(name: unknown) {
    if (typeof name !== "string" || !/^[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}$/.test(name))
      throw Error("Use 1–64 letters, numbers, underscores or hyphens for the project name");
    if (this.records.some((p) => p.directory === name)) throw Error("Project already exists");
    // Non-recursive mkdir ensures existing untracked directories are never overwritten.
    await mkdir(join(this.root.root, name));
    const project = { id: randomUUID(), name, directory: name, created: Date.now() };
    this.records.push(project);
    await this.store.write("projects", this.records);
    return project;
  }
  async workspace(id?: string) {
    if (!id) return this.root; // Preserve legacy browser conversations.
    const workspace = new Workspace(await this.root.path(this.get(id).directory));
    await workspace.init();
    return workspace;
  }
}
