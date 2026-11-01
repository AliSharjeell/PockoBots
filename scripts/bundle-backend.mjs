import { build } from "esbuild";
import { mkdir, copyFile, readFile, readdir, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import {runtimePayloads} from "./copy-runtime.mjs";
const result = await build({
  entryPoints: ["backend/src/server.ts"],
  outfile: "dist/server.js",
  bundle: true,
  metafile: true,
  // pi's package metadata marks re-export initializers as side-effect free;
  // preserve them so lazy provider classes are initialized before first use.
  ignoreAnnotations: true,
  platform: "node",
  format: "esm",
  target: "node22",
  banner: { js: 'import { createRequire } from "node:module"; const require = createRequire(import.meta.url);' },
});
// Include notices for the actual packages shipped in the JavaScript bundle.
const roots = new Set();
for (const input of Object.keys(result.metafile.inputs)) {
  if (!input.includes("node_modules/")) continue;
  let directory = dirname(resolve(input));
  while (directory !== dirname(directory)) {
    try {
      const pkg = JSON.parse(await readFile(join(directory, "package.json"), "utf8"));
      if (pkg.name && pkg.version) { roots.add(directory); break; }
    } catch {}
    directory = dirname(directory);
  }
}
const notices = [];
for (const root of [...roots].sort()) {
  const pkg = JSON.parse(await readFile(join(root, "package.json"), "utf8"));
  const files = (await readdir(root)).filter(name => /^(licen[cs]e|copying|notice)(\.|$)/i.test(name));
  notices.push(`${pkg.name} ${pkg.version}\nLicense: ${JSON.stringify(pkg.license || "See package source")}\n` +
    (await Promise.all(files.map(async name => {
      try { return `${name}\n${await readFile(join(root, name), "utf8")}`; } catch { return ""; }
    }))).join("\n"));
}
await mkdir("android/app/src/main/assets/licenses", { recursive: true });
await writeFile("android/app/src/main/assets/licenses/backend-third-party.txt", notices.join("\n\n----\n\n"));
await mkdir("android/app/src/main/assets/backend", { recursive: true });
await copyFile("dist/server.js", "android/app/src/main/assets/backend/server.js");
for (const name of runtimePayloads) await copyFile('backend/runtime/' + name, 'android/app/src/main/assets/backend/' + name);
// Reader dependencies and their licenses travel together in the verified bundle.
await copyFile('backend/runtime/agent-reach-lock.json', 'android/app/src/main/assets/licenses/agent-reach-bundle.json');
await copyFile('backend/runtime/agent-reach-notices.txt', 'android/app/src/main/assets/licenses/agent-reach-third-party.txt');
