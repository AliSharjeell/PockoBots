# Included runtime components

- Debian Bookworm packages: original copyright and license files are retained
  under `/usr/share/doc` inside the runtime. Package source is available from
  https://sources.debian.org/ and the Debian source package mirrors. The Dockerfile
  records the installed development tools.
- Node.js 22 and npm: https://github.com/nodejs/node and https://github.com/npm/cli.
  Their original license files remain in the upstream Node Docker image and npm
  installation.
- pi-agent-core/pi-ai and npm dependencies: installed from the checked-in npm
  lockfile, with original license files retained.
- PRoot 5.1.107.96, GPL-2.0-or-later:
  https://github.com/termux/proot/tree/v5.1.107.96. Build recipe and patches:
  https://github.com/termux/termux-packages/tree/master/packages/proot.
- libtalloc 2.5.0: https://www.samba.org/ftp/talloc/talloc-2.5.0.tar.gz.
  Build recipe: https://github.com/termux/termux-packages/tree/master/packages/libtalloc.
- libandroid-shmem 0.7: https://github.com/termux/libandroid-shmem.
  Build recipe: https://github.com/termux/termux-packages/tree/master/packages/libandroid-shmem.
- Microsoft Dev Tunnels CLI: downloaded from Microsoft's official distribution
  endpoint. Documentation, source notices and terms:
  https://github.com/microsoft/dev-tunnels and
  https://learn.microsoft.com/en-us/azure/developer/dev-tunnels/.

`native-lock.json` pins Android package archive SHA-256 checksums. The packaging
script changes native library lookup paths for APK extraction and provides the
loader through `PROOT_LOADER`; it does not modify PRoot source. Debian executable
files run through PRoot, without root privileges or an installed Termux app.
