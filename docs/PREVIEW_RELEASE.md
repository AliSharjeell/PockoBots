Pocket Agent v0.2 is a native Android coding app with a packaged local development environment. Install the ARM64 APK, open the app, let it unpack its tools, add your BYOK model connection, and create or clone a project. No separate Termux installation or manual backend configuration is required.

Included: project conversations with agent file and shell tools, a native interactive terminal, Bash, Git, Node.js, npm, Python, build tools, file editing, managed development servers, local previews, and Microsoft Dev Tunnels controls. Model inference uses your configured provider; the APK does not contain an offline language model.

Android 8+ and approximately 1-2 GB of free storage are required. This preview uses a development debug signing key. First launch extracts the runtime already contained in the APK. Projects and settings persist in private app storage across runtime restarts.

The build workflow checks backend provider adapters and builds both ARM64 and x86_64 packages. Its Android emulator smoke test exercises first launch, agent file editing, Git cloning, interactive terminal input, npm servers, and restart persistence. See [verification details](https://github.com/AliSharjeell/pocket-agent/blob/main/docs/VERIFICATION.md) for completed checks and remaining physical device and authenticated tunnel coverage.
