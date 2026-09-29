"""Read-only publish gate. Prints paths and risk types, never matching values.

Scans tracked AND nonignored untracked files, so it works before the first commit.
--workspace additionally inventories ignored local secrets/logs (not publication failures).
This is a lightweight heuristic, not a guarantee that private data is absent.
"""
import argparse
import os
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
PATTERNS = {
    "provider credential": re.compile(rb"(?:sk-[A-Za-z0-9_-]{16,}|ghp_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,}|AKIA[A-Z0-9]{16})"),
    "JWT / bearer credential": re.compile(rb"eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{16,}"),
    "private key": re.compile(rb"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
    "credential in connection URL": re.compile(rb"(?:mysql|postgres(?:ql)?|https?)://[^\s/:]+:[^\s/@$]+@"),
}
SENSITIVE_NAME = re.compile(r"(?i)(?:password|secret|api[_-]?key|authorization|access[_-]?token)")
ASSIGNMENT = re.compile(rb'''(?im)["']?(?:jwt[_-]?secret|openai[_-]?api[_-]?key|(?:mysql|pgvector|postgres)[_-]?(?:root[_-]?)?password|password|authorization|token)["']?\s*[:=]\s*["']([^"'\r\n]+)["']''')
BUILD_PARTS = {"target", "node_modules", "dist", "__pycache__", ".idea", ".vscode"}
LOCAL_NAMES = {"application-local.yml", "credentials.json", "id_rsa", "id_ed25519"}

def git(root, *args):
    result = subprocess.run(["git", "-C", str(root), *args], capture_output=True, check=False)
    if result.returncode not in (0, 1):
        raise RuntimeError("Git check unavailable")
    return result

def placeholder(value):
    text = value.decode("utf-8", "replace").strip().lower()
    return (not text or text.startswith(("${", "test-only", "ci-only", "example", "replace_", "your_", "change_me"))
            or text in {"bearer <token>", "bearer ${token}", "password", "password_hash", "test-placeholder"}
            or "gettoken()" in text or "token()" in text)

def scan(root):
    risks = []
    for name in (".env", ".env.private-test", "frontend/.env", "frontend/.env.production"):
        if git(root, "check-ignore", "--quiet", "--no-index", name).returncode:
            risks.append((".gitignore", "local environment files are not ignored"))
    listed = git(root, "ls-files", "-z", "--cached", "--others", "--exclude-standard")
    for raw in sorted(set(listed.stdout.split(b"\0"))):
        if not raw: continue
        name = os.fsdecode(raw)
        path = root / name
        parts = set(Path(name).parts)
        basename = path.name.lower()
        local_env = (basename == ".env" or basename.startswith(".env.") and basename != ".env.example")
        if local_env or basename in LOCAL_NAMES or basename.endswith((".pem", ".p12", ".jks", ".keystore", ".db", ".sqlite", ".sqlite3")):
            risks.append((name, "local credentials / database file"))
        if parts & BUILD_PARTS or basename.endswith((".log", ".class", ".jar", ".pyc")) or "logs" in parts:
            risks.append((name, "build artifact / sensitive runtime log"))
        if path.is_symlink():
            risks.append((name, "symlink requires manual review")); continue
        if not path.is_file(): continue
        if path.stat().st_size > 10 * 1024 * 1024:
            risks.append((name, "file exceeds 10 MiB")); continue
        content = path.read_bytes()
        if b"\0" in content: continue
        for risk, pattern in PATTERNS.items():
            if pattern.search(content): risks.append((name, risk))
        for match in ASSIGNMENT.finditer(content):
            value = match.group(1)
            # An assignment-shaped string in a script may itself be source code.
            if len(value) >= 8 and not placeholder(value) and re.fullmatch(rb"[A-Za-z0-9_+/.=!@#%-]+", value):
                risks.append((name, "literal credential assignment requiring review"))
                break
    return sorted(set(risks))

def inventory(root):
    skipped = BUILD_PARTS | {".git", ".cache"}
    findings = []
    for directory, dirs, files in os.walk(root):
        dirs[:] = [d for d in dirs if d not in skipped]
        for name in files:
            path = Path(directory) / name
            relative = path.relative_to(root).as_posix()
            if git(root, "check-ignore", "--quiet", relative).returncode != 0: continue
            if name.startswith(".env") or name.endswith(".log") or name in LOCAL_NAMES:
                findings.append((relative, "ignored private environment / runtime evidence"))
                if not path.is_symlink() and path.stat().st_size <= 50 * 1024 * 1024:
                    content = path.read_bytes()
                    for risk, pattern in PATTERNS.items():
                        if pattern.search(content): findings.append((relative, risk + " (ignored local file)"))
    return findings

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--workspace", action="store_true")
    args = parser.parse_args()
    risks = scan(ROOT)
    for path, risk in risks: print(f"BLOCK: {path}: {risk}")
    if args.workspace:
        for path, risk in inventory(ROOT): print(f"LOCAL ONLY: {path}: {risk}")
    print(f"Pre-public check: {'FAIL' if risks else 'PASS'} ({len(risks)} publication risks). No files changed.")
    return 1 if risks else 0

if __name__ == "__main__":
    sys.exit(main())
