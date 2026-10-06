import { Type, type TSchema } from "typebox";
import type { AgentTool } from "@earendil-works/pi-agent-core";
import type { Workspace } from "./workspace.js";
import type { Processes } from "./processes.js";
import { validPort } from "./processes.js";
function define<T extends TSchema>(tool: AgentTool<T>): AgentTool<T> {
  return tool;
}
const output = (text: string) => ({
  content: [{ type: "text" as const, text }],
  details: {},
});
export function agentTools(
  workspace: Workspace,
  processes: Processes,
): AgentTool<any>[] {
  return [
    define({
      name: "list_files",
      label: "List files",
      description:
        "List a workspace directory. Paths are relative to the workspace.",
      parameters: Type.Object({ path: Type.Optional(Type.String()) }),
      execute: async (_id, p) =>
        output(JSON.stringify(await workspace.list(p.path))),
    }),
    define({
      name: "read_file",
      label: "Read file",
      description: "Read a UTF-8 workspace file, up to 1 MiB.",
      parameters: Type.Object({ path: Type.String() }),
      execute: async (_id, p) => output(await workspace.read(p.path)),
    }),
    define({
      name: "write_file",
      label: "Write file",
      description:
        "Create or replace a UTF-8 workspace file. Creates parent directories.",
      parameters: Type.Object({ path: Type.String(), content: Type.String() }),
      execute: async (_id, p) => {
        await workspace.write(p.path, p.content);
        return output("Saved " + p.path);
      },
    }),
    define({
      name: "bash",
      label: "Run command",
      description:
        "Execute a bash command in the workspace (Git, npm, search, builds). Has full Termux account access; not an OS sandbox. Commands time out at 120 seconds. For long-running servers use start_server.",
      parameters: Type.Object({
        command: Type.String(),
        cwd: Type.Optional(Type.String()),
      }),
      execute: async (_id, p, signal) => {
        const result = await processes.run(
          p.command,
          await workspace.path(p.cwd),
          signal,
        );
        return output(JSON.stringify(result));
      },
    }),
    define({
      name: "start_server",
      label: "Start server",
      description:
        "Start a background development server. Return a job ID; use list_processes for logs. Choose a port explicitly in the command.",
      parameters: Type.Object({
        command: Type.String(),
        port: Type.Integer({ minimum: 1024, maximum: 65535 }),
        cwd: Type.Optional(Type.String()),
      }),
      execute: async (_id, p) =>
        output(
          JSON.stringify(
            processes.start(
              p.command,
              await workspace.path(p.cwd),
              "server",
              validPort(p.port),
            ),
          ),
        ),
    }),
    define({
      name: "list_processes",
      label: "Server logs",
      description: "List managed processes and their recent logs.",
      parameters: Type.Object({}),
      execute: async () => output(JSON.stringify([...processes.jobs.values()])),
    }),
    define({
      name: "stop_process",
      label: "Stop process",
      description: "Stop a managed process and its child processes by job ID.",
      parameters: Type.Object({ id: Type.String() }),
      execute: async (_id, p) => {
        processes.stop(p.id);
        return output("Stop requested");
      },
    }),
  ];
}
