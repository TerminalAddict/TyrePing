"""Exercise release credential setup with a temporary Git repository and a fake GitHub CLI."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / ".github/scripts/configure-release-git.sh"


@unittest.skipUnless(shutil.which("git") and shutil.which("jq"), "git and jq are required")
class ReleaseSecurityTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        subprocess.run(["git", "init", "-q", str(self.root)], check=True)
        self.git("remote", "add", "origin", "https://github.com/upstream/project.git")
        (self.root / "secrets").mkdir()
        (self.root / "secrets/keys.json").write_text(json.dumps({"GITHUB_TOKEN": "test_only_token"}))
        (self.root / "bin").mkdir()
        gh = self.root / "bin/gh"
        gh.write_text('#!/bin/sh\n[ "$1" = api ] && [ "$2" = user ] || exit 1\nprintf \'{"login":"tester","id":42}\'\n')
        gh.chmod(0o700)
        self.env = dict(os.environ, GITHUB_REPOSITORY="fork/project", GITHUB_ENV=str(self.root / "env"),
                        PATH=str(self.root / "bin") + os.pathsep + os.environ["PATH"])

    def git(self, *args):
        return subprocess.check_output(["git", "-C", str(self.root), *args], text=True).strip()

    def run_script(self):
        return subprocess.run(["bash", str(SCRIPT)], cwd=self.root, env=self.env,
                              capture_output=True, text=True)

    def test_credential_is_masked_and_never_persisted_in_git_config(self):
        result = self.run_script()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("::add-mask::test_only_token", result.stdout)
        self.assertNotIn("test_only_token", (self.root / ".git/config").read_text())
        self.assertEqual("https://github.com/fork/project.git", self.git("remote", "get-url", "origin"))
        self.assertEqual("42+tester@users.noreply.github.com", self.git("config", "user.email"))
        self.assertEqual("GH_TOKEN=test_only_token\n", (self.root / "env").read_text())

    def test_repository_shell_metacharacters_are_rejected_before_any_git_change(self):
        self.env["GITHUB_REPOSITORY"] = "fork/project;touch injected"
        self.assertNotEqual(0, self.run_script().returncode)
        self.assertFalse((self.root / "injected").exists())
        self.assertEqual("https://github.com/upstream/project.git", self.git("remote", "get-url", "origin"))

    def test_multiline_credential_cannot_inject_workflow_environment_variables(self):
        (self.root / "secrets/keys.json").write_text(json.dumps({"GITHUB_TOKEN": "token\nINJECTED=true"}))
        self.assertNotEqual(0, self.run_script().returncode)
        self.assertFalse((self.root / "env").exists())


if __name__ == "__main__":
    unittest.main()
