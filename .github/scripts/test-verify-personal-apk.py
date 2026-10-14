"""Exercise release rejection paths without Android SDK or private keys."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


SCRIPT = Path(__file__).with_name("verify-personal-apk.sh").resolve()
CERT = "a" * 64


class VerifyPersonalApkTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        tools = self.root / "build-tools" / "test"
        tools.mkdir(parents=True)
        for tool, code in {
            "apksigner": """import json,sys
d=json.load(open(sys.argv[-1]))
if not d.get('signed',True): sys.exit(1)
print('Signer #1 certificate SHA-256 digest: '+d.get('cert','a'*64))
if d.get('extra_signer'): print('Signer #2 certificate SHA-256 digest: '+'a'*64)
""",
            "aapt": """import json,sys
d=json.load(open(sys.argv[-1]))
if d.get('bad_metadata'): sys.exit(1)
print("package: name='%s' versionCode='%s'" % (d.get('package','im.molly.app'),d.get('version',101)))
if d.get('debug'): print('application-debuggable')
""",
        }.items():
            path = tools / tool
            path.write_text("#!/usr/bin/env python3\n" + code)
            path.chmod(0o755)
        self.env = dict(os.environ, ANDROID_HOME=str(self.root),
                        BUILD_TOOLS_VERSION="test", EXPECTED_CERT_SHA256=CERT)

    def verify(self, new, old=None):
        new_file = self.root / "new.apk"
        new_file.write_text(json.dumps(new))
        args = ["bash", str(SCRIPT), str(new_file)]
        if old is not None:
            old_file = self.root / "old.apk"
            old_file.write_text(json.dumps(old))
            args.append(str(old_file))
        return subprocess.run(args, env=self.env, capture_output=True, text=True)

    def test_first_signed_release(self):
        self.assertEqual(self.verify({}).returncode, 0)

    def test_upgrade_same_key_higher_version(self):
        self.assertEqual(self.verify({"version": 102}, {"version": 101}).returncode, 0)

    def test_equal_or_lower_version_rejected(self):
        for version in (100, 101):
            with self.subTest(version=version):
                self.assertNotEqual(self.verify({"version": version}, {"version": 101}).returncode, 0)

    def test_invalid_signature_rejected(self):
        self.assertNotEqual(self.verify({"signed": False}).returncode, 0)

    def test_wrong_new_key_rejected(self):
        self.assertNotEqual(self.verify({"cert": "b" * 64}).returncode, 0)

    def test_changed_key_from_previous_release_rejected(self):
        self.assertNotEqual(self.verify({"version": 102}, {"cert": "b" * 64}).returncode, 0)

    def test_debug_apk_rejected(self):
        self.assertNotEqual(self.verify({"debug": True}).returncode, 0)

    def test_wrong_package_rejected(self):
        self.assertNotEqual(self.verify({"package": "im.molly.app.staging"}).returncode, 0)

    def test_invalid_version_rejected(self):
        self.assertNotEqual(self.verify({"version": ""}).returncode, 0)

    def test_broken_metadata_rejected(self):
        self.assertNotEqual(self.verify({"bad_metadata": True}).returncode, 0)

    def test_multiple_signers_rejected(self):
        self.assertNotEqual(self.verify({"extra_signer": True}).returncode, 0)


if __name__ == "__main__":
    unittest.main()
