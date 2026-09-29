"""Self-test the read-only gate using an isolated temporary Git repository."""
import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("gate", Path(__file__).with_name("pre-public-check.py"))
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)

class GateTest(unittest.TestCase):
    def test_ignored_private_files_and_publish_candidates(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            subprocess.run(["git", "init", "-q", str(root)], check=True)
            (root / ".gitignore").write_text(".env\n.env.*\n!.env.example\nfrontend/.env\nfrontend/.env.*\n")
            (root / ".env").write_text("private local data")
            (root / ".env.example").write_text("JWT_SECRET=\n")
            self.assertEqual([], gate.scan(root))
            (root / "bad.txt").write_text("sk-" + "a" * 24)
            self.assertIn(("bad.txt", "provider credential"), gate.scan(root))
            subprocess.run(["git", "-C", str(root), "add", "-f", ".env"], check=True)
            self.assertIn((".env", "local credentials / database file"), gate.scan(root))
            (root / "target").mkdir()
            (root / "target/build.txt").write_text("generated")
            self.assertTrue(any(name == "target/build.txt" for name, _ in gate.scan(root)))
            self.assertTrue((root / ".env").exists())

if __name__ == "__main__": unittest.main()
