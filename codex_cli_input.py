#!/usr/bin/env python3
import argparse
import os
import shlex
import shutil
import subprocess
import sys
from pathlib import Path


def resolve_executable(bin_name: str) -> str:
    # Prefer exact name, then fall back to platform-specific variants.
    resolved = shutil.which(bin_name)
    if resolved:
        return resolved
    if os.name == "nt" and not os.path.splitext(bin_name)[1]:
        for ext in (".cmd", ".bat", ".exe"):
            candidate = shutil.which(bin_name + ext)
            if candidate:
                return candidate
    raise SystemExit(
        "Codex CLI not found. Install it or pass --bin with the full path "
        "(or add it to PATH). On Windows it may be named codex.cmd/codex.exe."
    )


def build_command(bin_name: str, bin_args: str) -> list[str]:
    cmd = [resolve_executable(bin_name)]
    if bin_args:
        cmd.extend(shlex.split(bin_args))
    return cmd


def read_commands(cmd_text: str | None, file_path: str | None) -> str:
    if cmd_text and file_path:
        raise SystemExit("Use only one of --cmd or --file.")
    if cmd_text:
        return cmd_text.strip() + "\n"
    if file_path:
        content = Path(file_path).read_text(encoding="utf-8")
        if not content.endswith("\n"):
            content += "\n"
        return content
    raise SystemExit("Provide --cmd or --file.")


def run_batch(cmd: list[str], commands: str) -> int:
    proc = subprocess.Popen(
        cmd,
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        encoding="utf-8",
    )
    out, err = proc.communicate(commands)
    if out:
        sys.stdout.write(out)
    if err:
        sys.stderr.write(err)
    return proc.returncode


def run_sequential(cmd: list[str], commands: str, repeat: int) -> int:
    last_rc = 0
    lines = [line.strip() for line in commands.splitlines() if line.strip()]
    for _ in range(max(1, repeat)):
        for line in lines:
            proc = subprocess.Popen(
                cmd,
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                encoding="utf-8",
            )
            out, err = proc.communicate(line + "\n")
            if out:
                sys.stdout.write(out)
            if err:
                sys.stderr.write(err)
            last_rc = proc.returncode
            if last_rc != 0:
                return last_rc
    return last_rc


def run_interactive(cmd: list[str]) -> int:
    return subprocess.call(cmd)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Send commands to codex CLI via stdin.",
    )
    parser.add_argument("--bin", default="codex", help="Codex CLI executable name.")
    parser.add_argument(
        "--bin-args",
        default=None,
        help=(
            "Extra args for the CLI. If omitted, uses 'exec -' for non-interactive "
            "runs and '--yolo' for interactive runs."
        ),
    )
    parser.add_argument("--cmd", help="Single command to send.")
    parser.add_argument("--file", help="File containing commands (one per line).")
    parser.add_argument(
        "--interactive",
        action="store_true",
        help="Run CLI interactively without pre-sending commands.",
    )
    parser.add_argument(
        "--sequential",
        action="store_true",
        help="Send prompts one-by-one, waiting for each to finish.",
    )
    parser.add_argument(
        "--repeat",
        type=int,
        default=1,
        help="Repeat the full prompt set N times (sequential only).",
    )
    args = parser.parse_args()

    if args.bin_args is None:
        bin_args = "--yolo" if args.interactive else "exec --skip-git-repo-check -"
    else:
        bin_args = args.bin_args

    cmd = build_command(args.bin, bin_args)
    if args.interactive:
        return run_interactive(cmd)

    commands = read_commands(args.cmd, args.file)
    if args.sequential:
        return run_sequential(cmd, commands, args.repeat)
    return run_batch(cmd, commands)


if __name__ == "__main__":
    raise SystemExit(main())
