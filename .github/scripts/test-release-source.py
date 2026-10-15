import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('source', Path(__file__).with_name('release-source.py'))
source = importlib.util.module_from_spec(spec)
spec.loader.exec_module(source)
TAG = 'v8.19.2-4-memoly.2'
SHA = 'a' * 40
RUN = {'head_sha': SHA, 'head_branch': TAG, 'path': '.github/workflows/release.yml'}
JOBS = [{'name': n, 'conclusion': 'success'} for n in ['Test and build unsigned APK once', 'Sign verified APK without rebuilding']]
ARTIFACTS = [{'id': 123, 'name': 'signed-memoly-apk', 'expired': False}]


class SourceTest(unittest.TestCase):
    def test_normal_and_recovery_refs(self):
        self.assertEqual(source.parse_reference(TAG), (TAG, None))
        self.assertEqual(source.parse_reference('ci-recover-' + TAG + '-run456'), (TAG, 456))

    def test_other_refs_are_rejected(self):
        for ref in ['main', 'v8.19.2-4-personal.3', 'ci-recover-' + TAG, TAG + '\n']:
            with self.subTest(ref=ref), self.assertRaises(ValueError):
                source.parse_reference(ref)

    def test_trusted_signed_build_can_be_retested_without_rebuilding(self):
        self.assertEqual(source.verified_artifact(RUN, JOBS, ARTIFACTS, TAG, SHA), '123')

    def test_different_source_or_pr_workflow_cannot_be_recovered(self):
        for change in [{'head_sha': 'b' * 40}, {'head_branch': 'feature/example'}, {'path': '.github/workflows/test.yml'}]:
            with self.subTest(change=change), self.assertRaises(ValueError):
                source.verified_artifact(dict(RUN, **change), JOBS, ARTIFACTS, TAG, SHA)

    def test_failed_build_or_signing_cannot_be_recovered(self):
        for index in range(2):
            jobs = [dict(j) for j in JOBS]
            jobs[index]['conclusion'] = 'failure'
            with self.assertRaises(ValueError):
                source.verified_artifact(RUN, jobs, ARTIFACTS, TAG, SHA)

    def test_missing_expired_or_ambiguous_artifacts_are_rejected(self):
        for artifacts in [[], [dict(ARTIFACTS[0], expired=True)], ARTIFACTS * 2]:
            with self.subTest(artifacts=artifacts), self.assertRaises(ValueError):
                source.verified_artifact(RUN, JOBS, artifacts, TAG, SHA)


if __name__ == '__main__':
    unittest.main()
