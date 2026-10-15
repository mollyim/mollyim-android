import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).with_name('choose-test-run.sh').resolve()


class CiRoutingTest(unittest.TestCase):
    def run_case(self, event, repos='', files='', error=False, requested='', branch='feature/example'):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            gh = root / 'gh'
            gh.write_text('''#!/usr/bin/env python3
import json, os, sys
from pathlib import Path
Path(os.environ['MOCK_ARGS']).write_text(json.dumps(sys.argv[1:]))
if os.environ['MOCK_ERROR'] == '1': sys.exit(1)
print(os.environ['MOCK_FILES'] if '/files' in sys.argv[2] else os.environ['MOCK_REPOS'])
''')
            gh.chmod(0o755)
            env = dict(os.environ, PATH=str(root) + os.pathsep + os.environ['PATH'],
                       GITHUB_EVENT_NAME=event, GITHUB_REPOSITORY='boncheolgu/mollyim-android',
                       GITHUB_REPOSITORY_OWNER='boncheolgu', GITHUB_REF_NAME=branch, PR_NUMBER='1',
                       REQUESTED_PROFILE=requested, RUNNER_TEMP=str(root),
                       GITHUB_OUTPUT=str(root / 'output'), GITHUB_STEP_SUMMARY=str(root / 'summary'),
                       MOCK_REPOS=repos, MOCK_FILES=files, MOCK_ERROR='1' if error else '0',
                       MOCK_ARGS=str(root / 'args'))
            result = subprocess.run(['bash', str(SCRIPT)], cwd=SCRIPT.parents[2], env=env, capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            outputs = dict(line.split('=', 1) for line in (root / 'output').read_text().splitlines())
            args = json.loads((root / 'args').read_text()) if (root / 'args').exists() else None
            return outputs, args

    def test_push_covered_by_same_repo_pr_is_skipped(self):
        output, args = self.run_case('push', repos='other/fork\nboncheolgu/mollyim-android')
        self.assertEqual(output['run_tests'], 'false')
        self.assertIn('head=boncheolgu:feature/example', args)
        self.assertIn('--paginate', args)

    def test_push_without_same_repo_pr_runs_full_checks(self):
        for repositories in ('', 'boncheolgu/another-fork'):
            output, _ = self.run_case('push', repos=repositories)
            self.assertEqual(output, {'run_tests': 'true', 'profile': 'full'})

    def test_default_branch_always_checks_and_warms_its_cache(self):
        output, args = self.run_case('push', repos='boncheolgu/mollyim-android', branch='main')
        self.assertEqual(output, {'run_tests': 'true', 'profile': 'full'})
        self.assertIsNone(args)

    def test_api_failures_run_full_checks(self):
        for event in ('push', 'pull_request'):
            output, _ = self.run_case(event, error=True)
            self.assertEqual(output, {'run_tests': 'true', 'profile': 'full'})

    def test_pr_scope(self):
        for files, expected in [('app/src/main/Example.kt', 'representative'),
                                ('app/build.gradle.kts', 'full'), ('plan.md', 'docs')]:
            output, _ = self.run_case('pull_request', files=files)
            self.assertEqual(output, {'run_tests': 'true', 'profile': expected})

    def test_manual_representative_and_scheduled_full(self):
        output, args = self.run_case('workflow_dispatch', requested='representative')
        self.assertEqual(output['profile'], 'representative')
        self.assertIsNone(args)
        output, args = self.run_case('schedule')
        self.assertEqual(output['profile'], 'full')
        self.assertIsNone(args)


if __name__ == '__main__':
    unittest.main()
