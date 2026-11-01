#!/usr/bin/env python3
"""PockoBots' bundled Agent-Reach public-reader adapter (JSON stdin/stdout).

No setup, auto-updater, desktop browser, login import or paid transcription is
invoked. Source and dependency licenses are retained in the verified archive.
"""
import hashlib
import json
import os
from pathlib import Path
import shutil
import sys
import tempfile
import zipfile

HERE = Path(__file__).resolve().parent


def prepare():
    lock = json.loads((HERE / "agent-reach-lock.json").read_text(encoding="utf-8"))
    payload = (HERE / "agent-reach-vendor.zip").read_bytes()
    digest = hashlib.sha256(payload).hexdigest()
    if digest != lock["bundleSha256"]:
        raise RuntimeError("Agent-Reach bundle failed its integrity check; update PockoBots")
    base = Path(os.environ.get("POCKET_STATE", str(Path.home() / ".pockobots"))) / "agent-reach"
    base.mkdir(mode=0o700, parents=True, exist_ok=True)
    home = base / "home"
    home.mkdir(mode=0o700, exist_ok=True)
    # The reader has its own shared ledger, never imports a desktop owner's login.
    os.environ.update(HOME=str(home), USERPROFILE=str(home), REACH_API_SUPERVISED="1",
                      PYTHONIOENCODING="utf-8", PYTHONUTF8="1")
    destination = base / digest
    if not (destination / ".ready").is_file():
        temporary = Path(tempfile.mkdtemp(prefix="unpack-", dir=base))
        try:
            with zipfile.ZipFile(HERE / "agent-reach-vendor.zip") as archive:
                for item in archive.infolist():
                    target = (temporary / item.filename).resolve()
                    if not target.is_relative_to(temporary.resolve()):
                        raise RuntimeError("Unsafe Agent-Reach archive path")
                archive.extractall(temporary)
            (temporary / ".ready").write_text(digest, encoding="ascii")
            try:
                temporary.rename(destination)
            except OSError:
                if not (destination / ".ready").is_file():
                    raise
        finally:
            if temporary.exists():
                shutil.rmtree(temporary)
    sys.path.insert(0, str(destination))
    os.environ["PYTHONPATH"] = str(destination)
    return lock


def backend_for(channel, action):
    """Select only readers that work in Android's packaged Linux runtime."""
    labels = {label for label, method in action.backends}
    candidates = {"web": ("jina", "exa"), "github": ("github-api",),
                  "youtube": ("yt-dlp",), "v2ex": ("v2ex-api",),
                  "bilibili": ("bilibili-api",)}.get(channel.name, ())
    selected = next((label for label in candidates if label in labels), None)
    if selected:
        # Web's built-in Jina/Exa fallback is read-only and needs no extra tools.
        return None if channel.name == "web" else selected
    if action.public_web:
        return "public-web"
    return False


def bounded(value):
    if isinstance(value, str):
        return value if len(value) <= 10000 else value[:10000] + "\n[truncated]"
    if isinstance(value, list):
        return [bounded(item) for item in value[:20]]
    if isinstance(value, dict):
        return {key: bounded(item) for key, item in value.items() if key != "raw"}
    return value


def dispatch(request, lock):
    from agent_reach.channels import get_all_channels, get_channel
    from agent_reach.runtime.dispatch import run_action, validate_action
    from agent_reach.runtime.result import BAD_INPUT, NEED_SETUP, ReachError, envelope

    if request.get("operation") == "channels":
        return {"ok": True, "version": lock["version"], "commit": lock["commit"], "channels": [
            {"name": channel.name, "actions": [
                {"name": action.name, "description": action.help,
                 "available": backend_for(channel, action) is not False,
                 "mode": "public-web" if backend_for(channel, action) == "public-web" else "reader",
                 "parameters": [{"name": p.name, "description": p.help, "required": p.required,
                                 "type": p.type.__name__, "default": p.default} for p in action.params]}
                for action in channel.actions]} for channel in get_all_channels()],
            "note": "Public network readers, not offline search. Public-web routes use hosted Exa/Jina and can be limited. Browser/login-only and paid transcription actions need separate setup and are not enabled by this adapter."}
    if request.get("operation") != "run":
        raise ValueError("operation must be channels or run")
    channel = get_channel(request.get("channel"))
    if channel is None:
        raise ValueError("Unknown channel; use operation=channels first")
    name = request.get("action", "")
    params = request.get("arguments", {})
    try:
        action, clean = validate_action(channel, name, params, timeout=35)
        if any(isinstance(clean.get(key), int) and clean[key] > 20 for key in ("limit", "page")):
            raise ReachError(BAD_INPUT, "limit and page cannot exceed 20 in the bundled reader")
        backend = backend_for(channel, action)
        if backend is False:
            raise ReachError(NEED_SETUP, "This action requires additional desktop tools, account sign-in or paid transcription; it is not enabled in the Android reader. Use an installed integration or explicitly requested Mobile Use instead.")
        return bounded(run_action(channel, name, clean, timeout=35, only_backend=backend))
    except ReachError as error:
        return envelope(channel=channel.name, action=name, elapsed_ms=0, error=error)


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    try:
        request = json.loads(sys.stdin.buffer.read(65537))
        if not isinstance(request, dict):
            raise ValueError("Expected a JSON object")
        result = dispatch(request, prepare())
        # Keep an intact structured envelope even for very large upstream pages.
        while len(json.dumps(result, ensure_ascii=False)) > 110000 and result.get("items"):
            result["items"].pop()
            result["truncated"] = True
        print(json.dumps(result, ensure_ascii=False), flush=True)
    except Exception as error:
        print(json.dumps({"ok": False, "error": {"code": "READER_ERROR", "message": str(error)[:1500]}}), flush=True)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
