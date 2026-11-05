"""Release builds must consume the proved source, even after Gradle has run."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import tempfile
import textwrap
import unittest
import zipfile
from unittest.mock import patch

import build


class BuildSourceTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='molly-build-source-')
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve()
        self.repo = self.root / 'pipeline'
        self.app = self.root / 'application'
        self.reports = self.root / 'reports'
        self.repo.mkdir()
        self.app.mkdir()
        self.reports.mkdir()
        (self.app / 'gradle').mkdir()
        (self.app / 'module').mkdir()
        (self.app / 'application.kt').write_bytes(b'original\n')
        (self.app / 'build.gradle.kts').write_text('// source fixture\n')
        (self.app / 'module/build.gradle.kts').write_text('// module fixture\n')
        (self.app / 'gradle/verification-metadata.xml').write_bytes(b'original verification\n')
        self.git('init', '-q')
        self.git('config', 'user.name', 'Source Fixture')
        self.git('config', 'user.email', 'source-fixture@example.invalid')
        self.git('add', '.')
        self.git('commit', '-qm', 'Source fixture')
        self.config = {'schema': 1, 'signal': {'commit': 'a' * 40},
                       'target_tree': self.git('rev-parse', 'HEAD^{tree}'), 'android': {}}
        self.proof = {'status': 'PASS', 'signal': self.config['signal']['commit'],
                      'tree': self.config['target_tree'], 'worktree': str(self.app), 'native': None}
        self.save()
        self.args = argparse.Namespace(repo=self.repo, config='molly-build.json',
                                       worktree=self.app, report_dir=self.reports,
                                       native_source_dir=None, maven_dir=None, heap='5g')

    def git(self, *args):
        env = {key: value for key, value in os.environ.items() if not key.startswith('GIT_')}
        env.update(GIT_CONFIG_NOSYSTEM='1', GIT_CONFIG_GLOBAL=os.devnull)
        return subprocess.check_output(['git', '-C', str(self.app), *args],
                                       text=True, stderr=subprocess.PIPE, env=env).strip()

    def save(self):
        (self.repo / 'molly-build.json').write_text(json.dumps(self.config))
        (self.reports / 'source.json').write_text(json.dumps(self.proof))

    def test_gradle_subprocess_keeps_single_worker_limit(self):
        self.config['android'] = {'assemble': [':app:assembleDebug']}
        self.save()
        script = self.app / 'gradlew'
        script.write_text('#!/bin/sh\nprintf "%s\\n" "$@"\n')
        script.chmod(0o755)
        init = self.repo / 'tools/patches/test-worker-heap.init.gradle'
        init.parent.mkdir(parents=True)
        init.write_text('// fixture\n')
        runner = build.Build(self.args)
        with patch.object(runner, 'source_proof'):
            runner.gradle('assemble')
        self.assertIn('--max-workers=1', (self.reports / 'assemble.log').read_text().splitlines())

    def test_instrumentation_uses_proved_source_and_separate_imo_runner(self):
        init = self.repo / 'tools/patches/test-worker-heap.init.gradle'
        init.parent.mkdir(parents=True)
        init.write_text('// fixture\n')
        runner = build.Build(self.args)
        with patch.object(runner, 'source_proof') as proof, patch.object(runner, 'run') as run:
            runner.gradle('instrumentation')
            default = run.call_args.args[0]
            runner.gradle('instrumentation-imo')
            imo = run.call_args.args[0]
        self.assertEqual(proof.call_count, 2)
        self.assertIn(':app:connectedProdWebsiteDebugAndroidTest', default)
        self.assertNotIn('-PimoTests', default)
        self.assertIn('-PimoTests', imo)
        self.assertIn('-Pandroid.testInstrumentationRunnerArguments.package=org.thoughtcrime.securesms.messages.incomingmessageobserver', imo)

    def test_release_assembly_uses_release_task_and_rejects_ci_signing(self):
        self.save()
        init = self.repo / 'tools/patches/test-worker-heap.init.gradle'
        init.parent.mkdir(parents=True)
        init.write_text('// fixture\n')
        (init.parent / 'release-version.init.gradle').write_text('// fixture\n')
        self.args.release_version_name = '8.33.2-1'
        self.args.release_version_code = '177201'
        runner = build.Build(self.args)
        with patch.object(runner, 'source_proof'), patch.object(runner, 'run') as run:
            with patch.dict(os.environ, {}, clear=True):
                runner.gradle('assemble-release')
            self.assertEqual(run.call_args.args[0][-1], ':app:assembleProdWebsiteRelease')
            with patch.dict(os.environ, {'CI_KEYSTORE_PATH': '/key'}, clear=True):
                with self.assertRaisesRegex(build.BuildError, 'must be unsigned'):
                    runner.gradle('assemble-release')
            self.assertEqual(run.call_count, 1)

    def test_clean_source_and_generated_outputs_are_allowed(self):
        for path in ('module/build/generated/Generated.kt', 'build/outputs/app.apk',
                     '.gradle/cache/entry', '.kotlin/sessions/entry', 'module/.cxx/object.o'):
            file = self.app / path
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_bytes(b'generated output\n')
        self.assertEqual(build.Build(self.args).source_proof(), self.proof)

    def test_untracked_source_and_fake_build_package_are_rejected(self):
        for name in ('module/Added.kt', 'module/src/main/java/build/Added.java'):
            with self.subTest(name=name):
                path = self.app / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text('unproved source\n')
                with self.assertRaisesRegex(build.BuildError, 'untracked source'):
                    build.Build(self.args).source_proof()
                path.unlink()

    def test_changed_working_bytes_with_same_size_and_timestamp_are_rejected(self):
        path = self.app / 'application.kt'
        before = path.stat()
        self.git('update-index', '--refresh')
        path.write_bytes(b'tampered\n')
        os.utime(path, ns=(before.st_atime_ns, before.st_mtime_ns))
        with self.assertRaisesRegex(build.BuildError, 'working source differs'):
            build.Build(self.args).source_proof()

    def test_staged_edit_is_rejected_even_if_working_bytes_match_head(self):
        path = self.app / 'application.kt'
        path.write_bytes(b'staged change\n')
        self.git('add', 'application.kt')
        path.write_bytes(b'original\n')
        with self.assertRaisesRegex(build.BuildError, 'staged source differs'):
            build.Build(self.args).source_proof()

    def test_new_staged_source_path_is_rejected(self):
        (self.app / 'new.kt').write_text('new source\n')
        self.git('add', 'new.kt')
        with self.assertRaisesRegex(build.BuildError, 'adds or removes tracked paths'):
            build.Build(self.args).source_proof()

    def test_skip_worktree_and_assume_unchanged_are_rejected_even_when_clean(self):
        for flag, undo in (('--skip-worktree', '--no-skip-worktree'),
                           ('--assume-unchanged', '--no-assume-unchanged')):
            with self.subTest(flag=flag):
                self.git('update-index', flag, 'application.kt')
                with self.assertRaisesRegex(build.BuildError, 'skip-worktree, assume-unchanged'):
                    build.Build(self.args).source_proof()
                self.git('update-index', undo, 'application.kt')

    def test_sparse_checkout_setting_is_rejected_without_skip_flags(self):
        self.git('config', 'core.sparseCheckout', 'true')
        with self.assertRaisesRegex(build.BuildError, 'sparse source checkouts'):
            build.Build(self.args).source_proof()

    def test_executable_mode_change_is_rejected_even_if_git_ignores_it(self):
        self.git('config', 'core.filemode', 'false')
        (self.app / 'application.kt').chmod(0o755)
        with self.assertRaisesRegex(build.BuildError, 'executable mode changed'):
            build.Build(self.args).source_proof()

    def test_exact_native_metadata_is_allowed_but_any_extra_change_is_rejected(self):
        # Native verification independently proves receipts, artifact hashes,
        # XML rendering and the generated init script. Here its verified bytes
        # are the fixture input to the application's source-boundary check.
        native = b'original verification\nexact native coordinate and checksums\n'
        self.config['native'] = 'native-ringrtc.json'
        self.proof['native'] = 'native-ringrtc.json'
        self.save()
        runner = build.Build(self.args)
        metadata = self.app / 'gradle/verification-metadata.xml'
        with patch.object(runner, 'native_verification_bytes', return_value=native):
            metadata.write_bytes(native)
            self.assertEqual(runner.source_proof(), self.proof)
            self.git('add', 'gradle/verification-metadata.xml')
            self.assertEqual(runner.source_proof(), self.proof)
            metadata.write_bytes(native + b'extra trust rule\n')
            with self.assertRaisesRegex(build.BuildError, 'working source differs'):
                runner.source_proof()
            metadata.write_bytes(native)
            (self.app / 'application.kt').write_bytes(b'tampered\n')
            with self.assertRaisesRegex(build.BuildError, 'working source differs'):
                runner.source_proof()

    def test_verification_metadata_without_native_proof_has_no_exception(self):
        (self.app / 'gradle/verification-metadata.xml').write_bytes(b'changed verification\n')
        with self.assertRaisesRegex(build.BuildError, 'working source differs'):
            build.Build(self.args).source_proof()


class CachedSdkTests(BuildSourceTests):
    def setUp(self):
        super().setUp()
        self.config['android'] = {'sdk': '36.1', 'build_tools': '36.0.0', 'ndk': '28.0.13004108'}
        self.save()
        self.sdk_root = self.root / 'sdk'
        self.env = patch.dict(os.environ, {'ANDROID_HOME': str(self.sdk_root)})
        self.env.start()
        self.addCleanup(self.env.stop)
        for identity, version, properties, payloads, archives in (
            ('platforms;android-36.1', '1', 'AndroidVersion.ApiLevel=36.1',
             ['framework.aidl', 'build.prop'], ['android.jar']),
            ('build-tools;36.0.0', '36.0.0', '',
             ['aapt', 'aapt2', 'zipalign', 'd8'], ['lib/d8.jar']),
            ('ndk;28.0.13004108', '28.0.13004108', '',
             ['toolchains/llvm/prebuilt/linux-x86_64/bin/clang'], [])):
            directory = self.sdk_root.joinpath(*identity.split(';'))
            directory.mkdir(parents=True)
            parts = (version + '.0.0').split('.')[:3]
            (directory / 'package.xml').write_text(
                f'<repository><localPackage path="{identity}"><revision>'
                f'<major>{parts[0]}</major><minor>{parts[1]}</minor><micro>{parts[2]}</micro>'
                '</revision></localPackage></repository>')
            (directory / 'source.properties').write_text(f'Pkg.Revision={version}\n{properties}\n')
            for name in payloads:
                path = directory / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(b'payload')
                path.chmod(0o755)
            for name in archives:
                path = directory / name
                path.parent.mkdir(parents=True, exist_ok=True)
                with zipfile.ZipFile(path, 'w') as archive:
                    archive.writestr('payload', 'fixture')

    def test_valid_cache_skips_manager_and_records_actual_method(self):
        runner = build.Build(self.args)
        with patch.object(runner, 'run') as run:
            runner.sdk()
        run.assert_not_called()
        report = json.loads((self.reports / 'sdk.json').read_text())
        self.assertEqual((report['status'], report['method'], report['executed'], report['command']),
                         ('PASS', 'local-validation', False, []))
        self.assertIn('sdkmanager was not executed', (self.reports / 'sdk.log').read_text())

    def test_invalid_cache_calls_installer(self):
        cases = ('missing', 'malformed', 'identity', 'revision', 'payload', 'archive')
        for case in cases:
            with self.subTest(case=case):
                directory = self.sdk_root / 'build-tools/36.0.0'
                target = {'missing': 'source.properties', 'malformed': 'package.xml',
                          'identity': 'package.xml', 'revision': 'source.properties',
                          'payload': 'aapt2', 'archive': 'lib/d8.jar'}[case]
                path = directory / target
                original = path.read_bytes()
                if case == 'missing': path.unlink()
                elif case == 'malformed': path.write_text('<broken')
                elif case == 'identity': path.write_bytes(original.replace(b'build-tools;36.0.0', b'build-tools;35.0.0'))
                elif case == 'revision': path.write_text('Pkg.Revision=35.0.0\n')
                elif case == 'payload': path.write_bytes(b'')
                else: path.write_bytes(b'not a zip')
                runner = build.Build(self.args)
                with patch.object(runner, 'run') as run:
                    runner.sdk()
                run.assert_called_once_with(
                    [str(self.sdk_root / 'cmdline-tools/latest/bin/sdkmanager'), '--install',
                     'platforms;android-36.1', 'build-tools;36.0.0', 'ndk;28.0.13004108'], 'sdk')
                path.write_bytes(original)
                if case == 'payload': path.chmod(0o755)

    def test_cold_install_accepts_licenses_only_when_requested(self):
        (self.sdk_root / 'platforms/android-36.1/android.jar').unlink()
        self.args.accept_licenses = True
        runner = build.Build(self.args)
        with patch('build.subprocess.run') as licenses, patch.object(runner, 'source_proof'), \
             patch.object(runner, 'run') as install:
            runner.sdk()
        self.assertEqual(licenses.call_args.args[0][-1], '--licenses')
        install.assert_called_once()


if __name__ == '__main__':
    unittest.main()
