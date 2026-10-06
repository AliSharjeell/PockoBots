#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
# Microsoft's Linux CLI uses glibc, while native Termux uses Android bionic.
# Run the official CLI in a Debian proot. Network ports are shared with Termux.
case "$(uname -m)" in
  aarch64) tunnel_arch=arm64 ;;
  x86_64) tunnel_arch=x64 ;;
  *) echo 'Microsoft Dev Tunnels requires a 64-bit ARM or x86 device.' >&2; exit 1 ;;
esac
pkg install -y proot-distro
if ! proot-distro login debian -- true 2>/dev/null; then proot-distro install debian; fi
proot-distro login debian -- bash -c 'apt-get update && apt-get install -y ca-certificates curl libicu-dev'
proot-distro login debian -- bash -c "curl --fail --location --proto '=https' --tlsv1.2 'https://aka.ms/TunnelsCliDownload/linux-$tunnel_arch' -o /usr/local/bin/devtunnel && chmod 755 /usr/local/bin/devtunnel"
mkdir -p "$HOME/.local/bin"
cat > "$HOME/.local/bin/devtunnel" <<'SH'
#!/data/data/com.termux/files/usr/bin/bash
exec proot-distro login --shared-tmp debian -- /usr/local/bin/devtunnel "$@"
SH
chmod 700 "$HOME/.local/bin/devtunnel"
"$HOME/.local/bin/devtunnel" --version
echo 'Installed. Restart Pocket Agent so its PATH includes ~/.local/bin, then use Servers → Sign in.'
echo 'Manual login: ~/.local/bin/devtunnel user login -g -d'
