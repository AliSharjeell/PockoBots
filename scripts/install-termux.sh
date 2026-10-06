#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
if [[ ! -d /data/data/com.termux/files/usr ]]; then
  echo 'Run this installer inside Termux on Android.' >&2; exit 1
fi
repo_dir="$(cd "$(dirname "$0")/.." && pwd)"
pkg update -y
pkg install -y nodejs-lts git bash termux-tools
cd "$repo_dir"
npm ci
npm run build
mkdir -p "$HOME/.config/pocket-agent" "$HOME/.local/bin" "$HOME/.termux"
printf '%s\n' "$repo_dir" > "$HOME/.config/pocket-agent/source"
install -m 700 scripts/pocket-agent-start scripts/pocket-agent-stop "$HOME/.local/bin/"
# Required by Termux RUN_COMMAND. The Android app still needs the RUN_COMMAND permission.
if ! grep -Eq '^allow-external-apps[[:space:]]*=[[:space:]]*true[[:space:]]*$' "$HOME/.termux/termux.properties" 2>/dev/null; then
  printf '\nallow-external-apps = true\n' >> "$HOME/.termux/termux.properties"
fi
termux-reload-settings || true
if [[ ":$PATH:" != *":$HOME/.local/bin:"* ]]; then
  printf '\nexport PATH="$HOME/.local/bin:$PATH"\n' >> "$HOME/.bashrc"
fi
"$HOME/.local/bin/pocket-agent-start"
echo 'Installed. Run bash scripts/pair.sh to get the pairing token.'
echo 'Install the Android APK, paste the token, and connect to port 8787.'
echo 'For shared files, run termux-setup-storage and choose an accessible POCKET_WORKSPACE before starting.'
