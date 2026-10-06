#!/usr/bin/env python3
"""An interactive Linux PTY; framed input/output keeps escape sequences intact."""
import base64
import fcntl
import json
import os
import pty
import select
import signal
import struct
import sys
import termios
import time

pid, fd = pty.fork()
if pid == 0:
    os.chdir(sys.argv[1])
    os.environ.update(TERM="xterm-256color", COLORTERM="truecolor")
    os.execv("/bin/bash", ["bash", "--login"])

def resize(cols, rows):
    fcntl.ioctl(fd, termios.TIOCSWINSZ, struct.pack("HHHH", rows, cols, 0, 0))

resize(int(sys.argv[2]), int(sys.argv[3]))
pending = b""
try:
    while True:
        ready, _, _ = select.select([fd, 0], [], [])
        if fd in ready:
            try:
                data = os.read(fd, 65536)
            except OSError:
                break
            if not data:
                break
            print(json.dumps({"data": base64.b64encode(data).decode()}), flush=True)
        if 0 in ready:
            data = os.read(0, 65536)
            if not data:
                break
            pending += data
            while b"\n" in pending:
                line, pending = pending.split(b"\n", 1)
                message = json.loads(line)
                if message.get("cols") is not None:
                    resize(message["cols"], message["rows"])
                payload = base64.b64decode(message["data"])
                while payload:
                    written = os.write(fd, payload)
                    payload = payload[written:]
finally:
    try:
        os.killpg(os.tcgetpgrp(fd), signal.SIGHUP)
    except (ProcessLookupError, OSError):
        pass
    os.close(fd)
    try:
        os.killpg(pid, signal.SIGHUP)
    except ProcessLookupError:
        pass
    deadline = time.monotonic() + 2
    while time.monotonic() < deadline:
        waited, _ = os.waitpid(pid, os.WNOHANG)
        if waited:
            break
        time.sleep(0.05)
    else:
        try:
            os.kill(pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        os.waitpid(pid, 0)
