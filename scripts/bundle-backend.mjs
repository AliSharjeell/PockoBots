import { build } from "esbuild";
import "./copy-runtime.mjs";
await build({
  entryPoints: ["backend/src/server.ts"],
  outfile: "dist/server.js",
  bundle: true,
  // pi's package metadata marks re-export initializers as side-effect free;
  // preserve them so lazy provider classes are initialized before first use.
  ignoreAnnotations: true,
  platform: "node",
  format: "esm",
  target: "node22",
  banner: { js: 'import { createRequire } from "node:module"; const require = createRequire(import.meta.url);' },
});
