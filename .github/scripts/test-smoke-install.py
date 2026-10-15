"""Exercise the install gate using fake adb responses, without an emulator."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).with_name('smoke-install.sh')
ADB = r'''#!/usr/bin/env python3
import os, sys
from pathlib import Path
args = sys.argv[1:]
with open(os.environ['ADB_LOG'], 'a') as log:
    log.write(' '.join(args) + '\n')
if args == ['shell', 'getprop', 'ro.build.version.sdk']:
    print(os.environ.get('FAKE_API', '35'))
elif args[:3] == ['shell', 'pm', 'path']:
    print('package:/data/app/im.memoly.app/base.apk')
elif args[:4] == ['shell', 'cmd', 'package', 'resolve-activity']:
    print(os.environ.get('FAKE_COMPONENT', 'im.memoly.app/org.thoughtcrime.securesms.RoutingActivity'))
elif args[:3] == ['shell', 'am', 'start']:
    assert '-n' in args, 'Launcher must be explicit to avoid CATEGORY_DEFAULT resolution'
    print('Status: ok')
elif args[:2] == ['shell', 'pidof']:
    sys.exit(int(os.environ.get('FAKE_CRASH', '0')))
elif args[:1] == ['install'] and '-r' in args:
    sys.exit(int(os.environ.get('FAKE_REINSTALL_FAILURE', '0')))
'''


class SmokeTest(unittest.TestCase):
    def run_gate(self, **env):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            for name, content in [('adb', ADB), ('sleep', '#!/bin/sh\nexit 0\n')]:
                (root / name).write_text(content)
                (root / name).chmod(0o755)
            log = root / 'adb.log'
            result = subprocess.run(['bash', str(SCRIPT), 'Memoly.apk'], text=True, capture_output=True,
                                    env=dict(os.environ, PATH=folder + ':' + os.environ['PATH'],
                                             ADB_LOG=str(log), EXPECTED_ANDROID_API='35', **env))
            return result.returncode, log.read_text()

    def test_launcher_is_resolved_then_explicitly_started_and_reinstalled(self):
        code, log = self.run_gate()
        self.assertEqual(code, 0)
        self.assertIn('--query-flags 0', log)
        self.assertIn('am start -W -n im.memoly.app/org.thoughtcrime.securesms.RoutingActivity', log)
        self.assertIn('install --no-streaming -r Memoly.apk', log)

    def test_wrong_android_api_is_not_reported_as_tested(self):
        code, log = self.run_gate(FAKE_API='34')
        self.assertNotEqual(code, 0)
        self.assertNotIn('install', log)

    def test_missing_launcher_cannot_pass(self):
        code, log = self.run_gate(FAKE_COMPONENT='No activity found')
        self.assertNotEqual(code, 0)
        self.assertNotIn('am start', log)

    def test_startup_crash_or_reinstall_failure_cannot_pass(self):
        for env in [{'FAKE_CRASH': '1'}, {'FAKE_REINSTALL_FAILURE': '1'}]:
            with self.subTest(env=env):
                self.assertNotEqual(self.run_gate(**env)[0], 0)


if __name__ == '__main__':
    unittest.main()
