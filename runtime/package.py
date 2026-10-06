#!/usr/bin/env python3
"""Create APK assets from a Docker root filesystem and verified Termux packages.

Runs on Linux (CI or WSL) with docker, curl, dpkg-deb, patchelf and Python 3.
Never removes projects, application state, or source files.
"""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tarfile
import tempfile
import urllib.request
import zipfile

repo = Path(__file__).resolve().parent.parent
arch = sys.argv[1] if len(sys.argv) > 1 else "arm64"
if arch not in ("arm64", "amd64"):
    raise SystemExit("Expected arm64 or amd64")
abi, debarch = ("arm64-v8a", "aarch64") if arch == "arm64" else ("x86_64", "x86_64")
assets = repo / "android/app/src/main/assets/runtime"
libs = repo / "android/app/src/main/jniLibs" / abi
assets.mkdir(parents=True, exist_ok=True)
libs.mkdir(parents=True, exist_ok=True)

def run(*args):
    subprocess.run(args, check=True, cwd=repo)

tag = f"pocket-agent-runtime:{arch}"
run("docker", "build", "--platform", f"linux/{arch}", "-f", "runtime/Dockerfile", "-t", tag, ".")
container = subprocess.check_output(["docker", "create", "--platform", f"linux/{arch}", tag], text=True).strip()
try:
    with tempfile.TemporaryDirectory(prefix="pocket-runtime-") as temp:
        temp = Path(temp)
        archive = temp / "rootfs.tar"
        run("docker", "export", "-o", str(archive), container)
        links = []
        modes = {}
        output = assets / f"{abi}.zip"
        with tarfile.open(archive) as source, zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as target:
            for entry in source:
                name = entry.name.lstrip("./")
                if not name or name == "etc/resolv.conf" or name.startswith(("dev/", "proc/", "sys/", "root/.npm/")):
                    continue
                if entry.issym():
                    # Relative host links also resolve correctly inside PRoot.
                    link = entry.linkname
                    if link.startswith("/"):
                        link = os.path.relpath(link.lstrip("/"), os.path.dirname(name) or ".")
                    links.append({"path": name, "target": link})
                elif entry.isdir():
                    target.writestr(name.rstrip("/") + "/", b"")
                elif entry.isfile() or entry.islnk():
                    stream = source.extractfile(entry)
                    if stream is not None:
                        target.writestr(name, stream.read())
                        if entry.mode & 0o111:
                            modes[name] = entry.mode & 0o777
            target.writestr(".pocket-links.json", json.dumps(links))
            target.writestr(".pocket-modes.json", json.dumps(modes))
            target.writestr("etc/resolv.conf", "nameserver 1.1.1.1\nnameserver 8.8.8.8\n")

        lock = json.loads((repo / "runtime/native-lock.json").read_text())[debarch]
        extracted = temp / "native"
        for package, (url, digest) in lock.items():
            deb = temp / f"{package}.deb"
            run("curl", "--fail", "--location", "--retry", "3", f"https://packages-cf.termux.dev/apt/termux-main/{url}", "-o", str(deb))
            if hashlib.sha256(deb.read_bytes()).hexdigest() != digest:
                raise RuntimeError(f"Checksum mismatch for {package}")
            run("dpkg-deb", "-x", str(deb), str(extracted))
        prefix = extracted / "data/data/com.termux/files/usr"
        import shutil
        shutil.copyfile(prefix / "bin/proot", libs / "libproot.so")
        shutil.copyfile(prefix / "libexec/proot/loader", libs / "libproot-loader.so")
        loader32 = prefix / "libexec/proot/loader32"
        if loader32.exists():
            shutil.copyfile(loader32, libs / "libproot-loader32.so")
        for name in ("libtalloc.so", "libandroid-shmem.so"):
            shutil.copyfile(prefix / "lib" / name, libs / name)
        for binary in (libs / "libproot.so", libs / "libtalloc.so", libs / "libandroid-shmem.so"):
            run("patchelf", "--set-rpath", "$ORIGIN", str(binary))
        # Termux talloc is versioned, while Android extracts only lib*.so names.
        needed = subprocess.check_output(["patchelf", "--print-needed", str(libs / "libproot.so")], text=True)
        for dependency in needed.splitlines():
            if dependency.startswith("libtalloc.so") and dependency != "libtalloc.so":
                run("patchelf", "--replace-needed", dependency, "libtalloc.so", str(libs / "libproot.so"))
        for file in libs.iterdir():
            file.chmod(0o755)
        metadata = {"abi": abi, "sha256": hashlib.sha256(output.read_bytes()).hexdigest(), "bytes": output.stat().st_size}
        (assets / f"{abi}.json").write_text(json.dumps(metadata))
        print(json.dumps(metadata))
finally:
    run("docker", "rm", container)

# Ship the exact upstream native source and build recipes with each APK.
# Debian package and npm license files remain within the runtime filesystem.
source_lock = json.loads((repo / "runtime/source-lock.json").read_text())
with tempfile.TemporaryDirectory(prefix="pocket-sources-") as source_dir:
    source_dir = Path(source_dir)
    licenses = repo / "android/app/src/main/assets/licenses"
    licenses.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(licenses / "native-sources.zip", "w", zipfile.ZIP_DEFLATED) as archive:
        for name, (url, digest) in source_lock.items():
            path = source_dir / name
            run("curl", "--fail", "--location", "--retry", "3", url, "-o", str(path))
            if hashlib.sha256(path.read_bytes()).hexdigest() != digest:
                raise RuntimeError(f"Source checksum mismatch: {name}")
            archive.write(path, name)
        recipes_commit = "decc2980bd7f3f2d1f1b51a5bb519c2f57bcfa70"
        for package in ("proot", "libtalloc", "libandroid-shmem"):
            url = f"https://raw.githubusercontent.com/termux/termux-packages/{recipes_commit}/packages/{package}/build.sh"
            with urllib.request.urlopen(url) as response:
                archive.writestr(f"recipes/{package}/build.sh", response.read())
        for name in ("Dockerfile", "package.py", "native-lock.json", "source-lock.json", "NOTICE.md"):
            archive.write(repo / "runtime" / name, f"pocket-agent/{name}")
    import shutil
    shutil.copyfile(repo / "runtime/NOTICE.md", licenses / "NOTICE.md")
    shutil.copyfile(repo / "android/terminal/LICENSE", licenses / "terminal-LICENSE")
