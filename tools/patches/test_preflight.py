"""Exercise preflight's trusted paths and resource-linking command."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


SCRIPT = Path(__file__).with_name('preflight.sh').resolve()


class PreflightEnvironmentTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='preflight-test-')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve()
        self.home = self.root / 'home'
        self.home.mkdir()
        self.alias = self.root / 'home-link'
        self.alias.symlink_to(self.home, target_is_directory=True)
        self.repo = self.root / 'repo'
        self.repo.mkdir()
        (self.repo / 'molly-build.json').write_text('{}')
        self.bin = self.root / 'bin'
        self.bin.mkdir()
        self.log = self.root / 'calls'
        self.executable('git', '''#!/bin/sh
if [ "$1" = rev-parse ]; then printf '%s\\n' "$TEST_REPO"; fi
exit 0
''')
        self.executable('python3', '''#!/usr/bin/python3
import json, os, pathlib, sys
args = sys.argv[1:]
if args and args[0] == '-c':
    if 'get("port")' in args[1]: sys.exit(1)
    print('fixture-target')
elif args[:2] == ['-m', 'unittest']:
    pathlib.Path(os.environ['TEST_LOG']).write_text(os.environ['TMPDIR'] + '\\n')
elif args and args[0].endswith('release.py'):
    work = pathlib.Path(args[args.index('--worktree') + 1]); work.mkdir()
    gradle = work / 'gradlew'
    gradle.write_text('#!/bin/sh\\nprintf "%s\\\\n" "$@" >> "$TEST_LOG"\\n')
    gradle.chmod(0o755)
    report = pathlib.Path(args[args.index('--report') + 1]); report.parent.mkdir()
    report.write_text(json.dumps({'status': 'PASS', 'tree': 'fixture'}))
elif args and args[0] == '-':
    print('source proof PASS')
''')
        self.executable('java', '#!/bin/sh\necho "    java.specification.version = 21" >&2\n')
        sdk = self.root / 'sdk/cmdline-tools/latest/bin'
        sdk.mkdir(parents=True)
        manager = sdk / 'sdkmanager'
        manager.write_text('#!/bin/sh\nexit 0\n')
        manager.chmod(0o755)
        self.env = dict(os.environ, HOME=str(self.alias), PATH=str(self.bin) + ':' + os.environ['PATH'],
                        TEST_REPO=str(self.repo), TEST_LOG=str(self.log),
                        ANDROID_HOME=str(self.root / 'sdk'), TMPDIR=str(self.alias))
        self.env.pop('MOLLY_PREFLIGHT_DIR', None)

    def executable(self, name, content):
        path = self.bin / name
        path.write_text(content)
        path.chmod(0o755)

    def run_preflight(self, *args):
        return subprocess.run([str(SCRIPT), *args], cwd=self.repo, env=self.env,
                              text=True, capture_output=True, check=True)

    def test_default_root_and_resource_command_with_symlinked_home(self):
        self.run_preflight('resources')
        lines = self.log.read_text().splitlines()
        self.assertEqual(lines[0], str(self.home / 'agent-scratch/mollyim-android/preflight/tmp'))
        self.assertIn('--max-workers=12', lines[1:])
        self.assertIn(':app:processProdWebsiteDebugResources', lines[1:])

    def test_explicit_symlinked_root_is_resolved(self):
        self.env['MOLLY_PREFLIGHT_DIR'] = str(self.alias / 'custom')
        self.run_preflight('proof')
        self.assertEqual(self.log.read_text().strip(), str(self.home / 'custom/tmp'))
        self.assertTrue((self.home / 'custom/release').is_dir())


if __name__ == '__main__':
    unittest.main()
