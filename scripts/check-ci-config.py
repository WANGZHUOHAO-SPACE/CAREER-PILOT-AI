"""Fail CI on accidentally committed local config or high-confidence secret patterns."""

import os
import re
import subprocess
import sys
from pathlib import Path


def git(*args: str) -> subprocess.CompletedProcess[bytes]:
    return subprocess.run(("git", *args), capture_output=True, check=False)


def main() -> int:
    for local_config in (".env", "frontend/.env"):
        if git("check-ignore", "--quiet", "--no-index", local_config).returncode != 0:
            print("CI config check failed: local environment files are not ignored.")
            return 1

    listed = git("ls-files", "-z", "--cached", "--others", "--exclude-standard")
    if listed.returncode != 0:
        print("CI config check failed: could not list repository files.")
        return 1

    forbidden_names = {"application-local.yml", "credentials.json", "id_rsa", "id_ed25519"}
    forbidden_suffixes = (".pem", ".p12", ".jks", ".keystore")
    patterns = (
        re.compile(rb"sk-[A-Za-z0-9_-]{16,}"),
        re.compile(rb"ghp_[A-Za-z0-9]{30,}"),
        re.compile(rb"github_pat_[A-Za-z0-9_]{30,}"),
        re.compile(rb"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
    )

    for raw_name in listed.stdout.split(b"\0"):
        if not raw_name:
            continue
        path = Path(os.fsdecode(raw_name))
        name = path.name.lower()
        if (name == ".env" or name.startswith(".env.") and name != ".env.example"
                or name in forbidden_names or name.endswith(forbidden_suffixes)):
            print("CI config check failed: a local credential file is included in the repository.")
            return 1
        if not path.is_file() or path.is_symlink():
            continue
        content = path.read_bytes()
        if b"\0" in content:
            continue
        if any(pattern.search(content) for pattern in patterns):
            print("CI config check failed: a possible credential pattern was found.")
            return 1

    print("CI config check passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
