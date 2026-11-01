"""Rebuild the offline Android reader bundle from hash-pinned upstream artifacts.

Run once with --update-lock to deliberately refresh dependency pins. Never installs
the unrelated `agent-reach` PyPI package. No build hooks or downloaded code run.
"""
import hashlib
import io
import json
from pathlib import Path
import sys
import tarfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / "backend/runtime"
LOCK = DEST / "agent-reach-lock.json"
COMMIT = "7a38047acf26a729cbb27851e3cb81639da86b29"


def download(url):
    with urllib.request.urlopen(url, timeout=90) as response:
        return response.read()


if "--update-lock" in sys.argv:
    artifacts = [{"name": "Agent-Reach", "version": "1.6.1", "url":
                  f"https://codeload.github.com/Panniantong/Agent-Reach/zip/{COMMIT}",
                  "kind": "source", "sha256": ""}]
    for name, version in [("requests", None), ("loguru", None), ("PyYAML", None),
                          ("yt-dlp", "2026.8.19"), ("urllib3", None), ("idna", None),
                          ("charset-normalizer", None), ("certifi", None)]:
        data = json.loads(download(f"https://pypi.org/pypi/{name}/" + (f"{version}/" if version else "") + "json"))
        files = data["urls"]
        artifact = next((f for f in files if f["filename"].endswith("none-any.whl")), None)
        kind = "wheel"
        if artifact is None:
            artifact = next(f for f in files if f["packagetype"] == "sdist")
            kind = "yaml-source"
        artifacts.append({"name": name, "version": data["info"]["version"], "url": artifact["url"],
                          "sha256": artifact["digests"]["sha256"], "kind": kind})
    artifacts[0]["sha256"] = hashlib.sha256(download(artifacts[0]["url"])).hexdigest()
    lock = {"repository": "https://github.com/Panniantong/Agent-Reach", "commit": COMMIT,
            "version": "1.6.1", "artifacts": artifacts}
else:
    lock = json.loads(LOCK.read_text(encoding="utf-8"))

entries = {}
for artifact in lock["artifacts"]:
    print(f"Bundling {artifact['name']} {artifact['version']}", flush=True)
    raw = download(artifact["url"])
    if hashlib.sha256(raw).hexdigest() != artifact["sha256"]:
        raise RuntimeError(f"Hash mismatch: {artifact['name']}")
    if artifact["kind"] in ("source", "wheel"):
        with zipfile.ZipFile(io.BytesIO(raw)) as archive:
            for item in archive.infolist():
                if item.is_dir():
                    continue
                name = item.filename
                if artifact["kind"] == "source":
                    name = name.split("/", 1)[1]
                    if name == "LICENSE":
                        name = "agent_reach-LICENSE"
                    elif not name.startswith("agent_reach/"):
                        continue
                if "__pycache__" not in name and not name.endswith(".pyc"):
                    entries[name] = archive.read(item)
    else:
        # PyYAML's pure Python implementation; the native accelerator is optional.
        with tarfile.open(fileobj=io.BytesIO(raw), mode="r:gz") as archive:
            for item in archive.getmembers():
                if not item.isfile():
                    continue
                name = item.name.split("/", 1)[1]
                if name.startswith("lib/yaml/"):
                    entries[name[4:]] = archive.extractfile(item).read()
                elif name == "LICENSE":
                    entries["PyYAML-LICENSE"] = archive.extractfile(item).read()

output = io.BytesIO()
with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
    for name, value in sorted(entries.items()):
        if name.startswith("/") or ".." in Path(name).parts:
            raise RuntimeError("Unsafe upstream archive path")
        info = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        archive.writestr(info, value)
bundle = output.getvalue()
lock["bundleSha256"] = hashlib.sha256(bundle).hexdigest()
DEST.mkdir(parents=True, exist_ok=True)
(DEST / "agent-reach-vendor.zip").write_bytes(bundle)
notices = [f"Agent-Reach {lock['version']}\n{lock['repository']}\nCommit: {lock['commit']}",
           "Bundled dependency versions:\n" + "\n".join(f"{a['name']} {a['version']}" for a in lock['artifacts'])]
for name, value in sorted(entries.items()):
    if any(token in Path(name).name.lower() for token in ("license", "copying", "notice")) and not name.endswith('.py'):
        notices.append(name + "\n" + value.decode('utf-8', errors='replace'))
(DEST / "agent-reach-notices.txt").write_text("\n\n----\n\n".join(notices) + "\n", encoding="utf-8")
LOCK.write_text(json.dumps(lock, indent=2) + "\n", encoding="utf-8")
print(f"Bundle: {len(bundle):,} bytes; {lock['bundleSha256']}")
