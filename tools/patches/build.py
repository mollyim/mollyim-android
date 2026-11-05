#!/usr/bin/env python3
"""Build only the source tree proved by release.py, using the selected profile."""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path, PurePosixPath
import re
import shlex
import stat
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
import zipfile


class BuildError(RuntimeError):
    pass


def relative_file(repo: Path, value: str, label: str) -> Path:
    if not isinstance(value, str) or not value or Path(value).is_absolute():
        raise BuildError(f'{label} must be a repository-relative file')
    path = repo / value
    if '..' in Path(value).parts or not path.resolve().is_relative_to(repo):
        raise BuildError(f'{label} escapes the repository')
    if not path.is_file():
        raise BuildError(f'{label} does not exist: {path}')
    return path.resolve()


def gradle_tasks(value, label: str, *, tests: bool = False) -> list[str]:
    if isinstance(value, str):
        value = [value]
    if not isinstance(value, list) or not value or any(not isinstance(x, str) for x in value):
        raise BuildError(f'android.{label} must be a nonempty task/argument list')
    result, after_task, selector = [], False, False
    for entry in value:
        if selector:
            if not re.fullmatch(r'[A-Za-z0-9_.*$]+', entry):
                raise BuildError(f'invalid test selector: {entry!r}')
            selector = False
        elif tests and entry == '--tests' and after_task:
            selector = True
        elif re.fullmatch(r'(?::[A-Za-z0-9_.-]+)+', entry):
            after_task = True
        else:
            raise BuildError(f'invalid Gradle task/argument in android.{label}: {entry!r}')
        result.append(entry)
    if selector:
        raise BuildError('a final --tests argument has no class selector')
    return result


def local_sdk_valid(root: Path, sdk: str, build_tools: str, ndk: str) -> bool:
    """Check installed package identity and essential payloads before skipping installation."""
    packages = [
        (f'platforms;android-{sdk}', {'AndroidVersion.ApiLevel': sdk},
         ['android.jar', 'framework.aidl', 'build.prop'], [], ['android.jar']),
        (f'build-tools;{build_tools}', {'Pkg.Revision': build_tools},
         ['lib/d8.jar'], ['aapt', 'aapt2', 'zipalign', 'd8'], ['lib/d8.jar']),
        (f'ndk;{ndk}', {'Pkg.Revision': ndk}, [],
         ['toolchains/llvm/prebuilt/linux-x86_64/bin/clang'], []),
    ]
    try:
        for identity, expected, files, executables, archives in packages:
            directory = root.joinpath(*identity.split(';'))
            properties = {}
            for line in (directory / 'source.properties').read_text().splitlines():
                if '=' in line and not line.lstrip().startswith('#'):
                    key, value = line.split('=', 1)
                    properties[key.strip()] = value.strip()
            if any(properties.get(key) != value for key, value in expected.items()):
                return False
            xml = ET.parse(directory / 'package.xml').getroot()
            local = [element for element in xml if element.tag.split('}')[-1] == 'localPackage']
            if len(local) != 1 or local[0].get('path') != identity:
                return False
            revision = next((element for element in local[0] if element.tag.split('}')[-1] == 'revision'), None)
            if revision is None:
                return False
            parts = {element.tag.split('}')[-1]: element.text for element in revision}
            version = '.'.join(parts.get(name, '0') for name in ('major', 'minor', 'micro'))
            prop_version = properties.get('Pkg.Revision', '')
            if not re.fullmatch(r'[0-9]+(?:\.[0-9]+){0,2}', prop_version):
                return False
            if tuple(map(int, version.split('.'))) != tuple(map(int, (prop_version + '.0.0').split('.')[:3])):
                return False
            for name in files + executables:
                path = directory / name
                if not path.is_file() or path.stat().st_size == 0:
                    return False
                if name in executables and not os.access(path, os.X_OK):
                    return False
            for name in archives:
                with zipfile.ZipFile(directory / name) as archive:
                    if not archive.namelist() or archive.testzip() is not None:
                        return False
    except (OSError, ValueError, ET.ParseError, zipfile.BadZipFile, TypeError):
        return False
    return True


class Build:
    def __init__(self, args):
        self.args = args
        self.repo = args.repo.resolve()
        self.config_path = relative_file(self.repo, args.config, 'build configuration')
        self.config = json.loads(self.config_path.read_text())
        if self.config.get('schema') != 1:
            raise BuildError('unsupported build configuration schema')
        self.android = self.config['android']
        if not isinstance(self.android, dict):
            raise BuildError('android must be a build-profile object')
        self.app = args.worktree.resolve()
        self.reports = args.report_dir.resolve()
        self.reports.mkdir(parents=True, exist_ok=True)
        self.native_proof = self.reports / 'native'
        self.native_source = (args.native_source_dir or self.app.parent / 'molly-ringrtc-source').resolve()
        self.maven = (args.maven_dir or self.app.parent / 'molly-ringrtc-maven').resolve()

    def git(self, *args, check=True):
        # These are local source-inspection commands. Do not inherit another
        # repository, private index, object replacement or user-wide filters.
        env = {key: value for key, value in os.environ.items() if not key.startswith('GIT_')}
        env.update(GIT_CONFIG_NOSYSTEM='1', GIT_CONFIG_GLOBAL=os.devnull,
                   GIT_ATTR_NOSYSTEM='1', GIT_NO_REPLACE_OBJECTS='1',
                   GIT_OPTIONAL_LOCKS='0')
        result = subprocess.run(
            ['git', '-C', str(self.app), '-c', 'core.hooksPath=/dev/null',
             '-c', 'core.fsmonitor=false', *args], stdout=subprocess.PIPE,
            stderr=subprocess.PIPE, env=env)
        if check and result.returncode:
            raise BuildError('source inspection failed: ' + result.stderr.decode(errors='replace'))
        return result

    def native_verification_bytes(self):
        """Allow precisely the receipt-bound metadata appended by native integration."""
        if self.config.get('native') is None or not (self.native_proof / 'integration.json').exists():
            return None
        helper_path = relative_file(self.repo, 'tools/native/ringrtc.py', 'native build helper')
        spec = importlib.util.spec_from_file_location('molly_native_source_verifier', helper_path)
        if spec is None or spec.loader is None:
            raise BuildError('cannot load the native integration verifier')
        helper = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(helper)
        lock_path = relative_file(self.repo, self.config['native'], 'native dependency lock')
        try:
            lock = helper.read_lock(lock_path)
            receipt = helper.verify_integration(helper.Runner(self.native_proof, lock), self.app, self.maven)
        except helper.Failure as exc:
            raise BuildError('native integration no longer matches its proof: ' + str(exc)) from exc
        path = self.app / 'gradle/verification-metadata.xml'
        if path.is_symlink() or not path.is_file():
            raise BuildError('native verification metadata is not a regular file')
        expected = path.read_bytes()
        if hashlib.sha256(expected).hexdigest() != receipt['verification_sha256']:
            raise BuildError('native verification metadata changed after its proof')
        return expected

    def working_source_proof(self):
        """Check actual tracked bytes/modes, not Git's potentially cached clean status."""
        sparse = self.git('config', '--bool', 'core.sparseCheckout', check=False)
        if sparse.returncode not in (0, 1) or sparse.stdout.strip() == b'true':
            raise BuildError('sparse source checkouts cannot be used for a release build')
        flags = self.git('ls-files', '-v', '-z').stdout.split(b'\0')
        if any(entry and entry[:2] != b'H ' for entry in flags):
            raise BuildError('source index contains skip-worktree, assume-unchanged or unmerged entries')
        expected = {}
        for entry in self.git('ls-tree', '-r', '-z', '--full-tree', 'HEAD').stdout.split(b'\0'):
            if entry:
                metadata, path = entry.split(b'\t', 1)
                mode, kind, object_id = metadata.split()
                if kind != b'blob' or mode not in (b'100644', b'100755', b'120000'):
                    raise BuildError('unsupported tracked source entry: ' + os.fsdecode(path))
                expected[path] = (mode, object_id)
        index = {}
        for entry in self.git('ls-files', '--stage', '-z').stdout.split(b'\0'):
            if entry:
                metadata, path = entry.split(b'\t', 1)
                mode, object_id, stage = metadata.split()
                if stage != b'0' or path in index:
                    raise BuildError('unmerged source index entry: ' + os.fsdecode(path))
                index[path] = (mode, object_id)
        if index.keys() != expected.keys():
            raise BuildError('source index adds or removes tracked paths after reconstruction')
        # Gradle may create caches and module build outputs after the original
        # source proof. Only these directories beneath known Gradle projects
        # are allowed; an untracked .kt/.java/resource elsewhere is new source.
        project_roots = {''}
        for path in expected:
            parts = PurePosixPath(os.fsdecode(path)).parts
            if parts[-1] in ('build.gradle', 'build.gradle.kts', 'settings.gradle', 'settings.gradle.kts'):
                project_roots.add('/'.join(parts[:-1]))
        generated = {'build', '.gradle', '.kotlin', '.cxx'}
        for path in self.git('ls-files', '--others', '-z').stdout.split(b'\0'):
            if not path:
                continue
            parts = PurePosixPath(os.fsdecode(path)).parts
            if not any(part in generated and '/'.join(parts[:number]) in project_roots
                       for number, part in enumerate(parts[:-1])):
                raise BuildError('untracked source outside generated build directories: ' + os.fsdecode(path))
        algorithm = self.git('rev-parse', '--show-object-format').stdout.decode().strip()
        if algorithm not in ('sha1', 'sha256'):
            raise BuildError('unsupported Git object hash algorithm')

        def blob_id(data):
            digest = hashlib.new(algorithm)
            digest.update(f'blob {len(data)}\0'.encode())
            digest.update(data)
            return digest.hexdigest().encode()

        native_path = b'gradle/verification-metadata.xml'
        native = self.native_verification_bytes()
        if native is not None and native_path not in expected:
            raise BuildError('native integration refers to metadata absent from the source tree')
        directories = set()
        for path, (mode, object_id) in expected.items():
            allowed_id = blob_id(native) if native is not None and path == native_path else object_id
            allowed_index = {(mode, object_id), (mode, allowed_id)}
            if index[path] not in allowed_index:
                raise BuildError('staged source differs from the verified tree: ' + os.fsdecode(path))
            file = self.app / os.fsdecode(path)
            for parent in file.parents:
                if parent == self.app:
                    break
                if parent not in directories:
                    if parent.is_symlink() or not parent.is_dir():
                        raise BuildError('source directory changed after reconstruction: ' + str(parent))
                    directories.add(parent)
            try:
                file_stat = file.lstat()
                if mode == b'120000':
                    if not stat.S_ISLNK(file_stat.st_mode):
                        raise BuildError('source symlink changed type: ' + os.fsdecode(path))
                    actual_id = blob_id(os.fsencode(os.readlink(file)))
                else:
                    if not stat.S_ISREG(file_stat.st_mode):
                        raise BuildError('tracked source is not a regular file: ' + os.fsdecode(path))
                    executable = bool(file_stat.st_mode & stat.S_IXUSR)
                    if executable != (mode == b'100755'):
                        raise BuildError('source executable mode changed: ' + os.fsdecode(path))
                    digest = hashlib.new(algorithm)
                    digest.update(f'blob {file_stat.st_size}\0'.encode())
                    length = 0
                    with file.open('rb') as stream:
                        for block in iter(lambda: stream.read(1024 * 1024), b''):
                            digest.update(block)
                            length += len(block)
                    if length != file_stat.st_size:
                        raise BuildError('source changed while being verified: ' + os.fsdecode(path))
                    actual_id = digest.hexdigest().encode()
            except OSError as exc:
                raise BuildError('cannot verify tracked source ' + os.fsdecode(path) + ': ' + str(exc)) from exc
            if actual_id != allowed_id:
                raise BuildError('working source differs from the verified tree: ' + os.fsdecode(path))

    def source_proof(self):
        path = self.reports / 'source.json'
        if not path.is_file():
            raise BuildError('run release.py with --report <report-dir>/source.json before building')
        proof = json.loads(path.read_text())
        if proof.get('status') != 'PASS' or Path(proof['worktree']).resolve() != self.app:
            raise BuildError('source proof did not pass for this worktree')
        if (proof['signal'], proof['tree'], proof.get('native')) != (
                self.config['signal']['commit'], self.config['target_tree'], self.config.get('native')):
            raise BuildError('source proof belongs to a different release configuration')
        if self.git('rev-parse', 'HEAD^{tree}').stdout.decode().strip() != proof['tree']:
            raise BuildError('the materialized source commit differs from the verified release tree')
        self.working_source_proof()
        return proof

    def run(self, command: list[str], phase: str, *, cwd: Path | None = None):
        print('+ ' + shlex.join(command), flush=True)
        started = time.monotonic()
        log_path = self.reports / (phase + '.log')
        with log_path.open('w') as log:
            process = subprocess.Popen(command, cwd=cwd, stdout=subprocess.PIPE,
                                       stderr=subprocess.STDOUT, text=True,
                                       errors='replace', bufsize=1)
            assert process.stdout is not None
            for line in process.stdout:
                log.write(line)
                print(line, end='', flush=True)
            code = process.wait()
        result = {'status': 'PASS' if code == 0 else 'FAIL', 'phase': phase,
                  'exit_code': code, 'seconds': round(time.monotonic() - started, 3),
                  'command': command, 'log': str(log_path)}
        (self.reports / (phase + '.json')).write_text(json.dumps(result, indent=2) + '\n')
        if code:
            raise BuildError(f'{phase} failed with exit code {code}; see {log_path}')

    def sdk(self):
        self.source_proof()
        sdk = str(self.android['sdk'])
        build_tools = str(self.android['build_tools'])
        ndk = str(self.android['ndk'])
        if not re.fullmatch(r'[0-9]+(?:\.[0-9]+)?', sdk):
            raise BuildError('invalid android.sdk version')
        if any(not re.fullmatch(r'[0-9]+(?:\.[0-9]+)+', x) for x in (build_tools, ndk)):
            raise BuildError('invalid Android build-tools or NDK version')
        sdk_root = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
        if not sdk_root:
            raise BuildError('ANDROID_HOME or ANDROID_SDK_ROOT must identify the Android SDK')
        manager = Path(sdk_root) / 'cmdline-tools/latest/bin/sdkmanager'
        if local_sdk_valid(Path(sdk_root), sdk, build_tools, ndk):
            message = 'Pinned Android SDK packages validated locally; sdkmanager was not executed.\n'
            print(message, end='')
            (self.reports / 'sdk.log').write_text(message)
            (self.reports / 'sdk.json').write_text(json.dumps({
                'status': 'PASS', 'phase': 'sdk', 'command': [], 'executed': False,
                'method': 'local-validation', 'log': str(self.reports / 'sdk.log')
            }, indent=2) + '\n')
            return
        if getattr(self.args, 'accept_licenses', False):
            subprocess.run([str(manager), '--licenses'], input='y\n' * 1000, text=True,
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)
        self.run([str(manager), '--install', f'platforms;android-{sdk}',
                  f'build-tools;{build_tools}', f'ndk;{ndk}'], 'sdk')

    def native(self, phase: str):
        self.source_proof()
        lock_value = self.config.get('native')
        if lock_value is None:
            print('This release uses its verified published native dependencies; no native backport is configured.')
            (self.reports / (phase + '.json')).write_text(json.dumps({
                'status': 'NOT_REQUIRED', 'phase': phase,
                'reason': 'No native source backport is configured for this release.'}, indent=2) + '\n')
            return
        lock = relative_file(self.repo, lock_value, 'native dependency lock')
        helper = relative_file(self.repo, 'tools/native/ringrtc.py', 'native build helper')
        subcommand = phase.removeprefix('native-')
        command = [sys.executable, str(helper), subcommand, '--lock', str(lock),
                   '--proof-dir', str(self.native_proof)]
        if subcommand == 'integrate':
            command += ['--app-dir', str(self.app), '--maven-dir', str(self.maven)]
        else:
            command += ['--source-dir', str(self.native_source)]
            if subcommand == 'build':
                command += ['--maven-dir', str(self.maven)]
        self.run(command, phase, cwd=self.repo)
        if subcommand == 'integrate' and not (self.native_proof / 'ringrtc.init.gradle').is_file():
            raise BuildError('native integration did not produce its required Gradle init script')

    def gradle(self, phase: str):
        self.source_proof()
        if not re.fullmatch(r'[1-9][0-9]*[mg]', self.args.heap):
            raise BuildError('--heap must be a JVM memory size such as 5g')
        if phase == 'assemble-release':
            if 'CI_KEYSTORE_PATH' in os.environ:
                raise BuildError('unset CI_KEYSTORE_PATH: release assembly must be unsigned')
            version_name = getattr(self.args, 'release_version_name', None)
            version_code = getattr(self.args, 'release_version_code', None)
            if not version_name or not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._+-]{0,79}', version_name):
                raise BuildError('assemble-release requires a valid --release-version-name')
            if not version_code or not re.fullmatch(r'[1-9][0-9]{0,9}', version_code) or int(version_code) > 2100000000:
                raise BuildError('assemble-release requires --release-version-code from 1 to 2100000000')
            tasks = [':app:assembleProdWebsiteRelease']
        elif phase in ('instrumentation', 'instrumentation-imo'):
            tasks = [':app:connectedProdWebsiteDebugAndroidTest', '--console=plain']
            if phase == 'instrumentation':
                tasks += ['-Pandroid.testInstrumentationRunnerArguments.notPackage=org.thoughtcrime.securesms.messages.incomingmessageobserver']
            if phase == 'instrumentation-imo':
                tasks += ['-PimoTests', '-Pandroid.testInstrumentationRunnerArguments.package=org.thoughtcrime.securesms.messages.incomingmessageobserver']
        else:
            tasks = gradle_tasks(self.android['assemble' if phase == 'assemble' else 'tests'],
                                 'assemble' if phase == 'assemble' else 'tests', tests=phase == 'test')
        init = relative_file(self.repo, 'tools/patches/test-worker-heap.init.gradle', 'test-worker heap script')
        jvm_args = (f'-Xmx{self.args.heap} -Xms256m -XX:MaxMetaspaceSize=1g '
                    '-Dcom.android.tools.r8.threadCount=1 '
                    '-XX:+UnlockExperimentalVMOptions -XX:hashCode=3')
        command = ['./gradlew', '--no-daemon', '--no-parallel', '--no-configuration-cache',
                   '--max-workers=1', '--dependency-verification=strict',
                   '--init-script', str(init), '-Pkotlin.compiler.execution.strategy=in-process',
                   '-Dorg.gradle.jvmargs=' + jvm_args]
        if phase == 'assemble-release':
            version_init = relative_file(self.repo, 'tools/patches/release-version.init.gradle', 'release version script')
            command += ['--init-script', str(version_init), '-PreleaseVersionName=' + version_name,
                        '-PreleaseVersionCode=' + version_code]
        if self.config.get('native') is not None:
            native_init = self.native_proof / 'ringrtc.init.gradle'
            integration = self.native_proof / 'integration.json'
            if not native_init.is_file() or not integration.is_file():
                raise BuildError('native hotfix is required: prepare, build and integrate it before Gradle; no fallback is allowed')
            lock = json.loads(relative_file(self.repo, self.config['native'], 'native dependency lock').read_text())
            receipt = json.loads(integration.read_text())
            if (receipt.get('status'), receipt.get('source_tree'), receipt.get('application'), receipt.get('init_script')) != (
                    'prepared', lock['source_tree'], str(self.app), str(native_init)):
                raise BuildError('native integration receipt does not match this source, worktree and init script')
            command += ['--init-script', str(native_init)]
        self.run(command + tasks, phase, cwd=self.app)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('phase', choices=('sdk', 'assemble', 'assemble-release', 'test', 'instrumentation', 'instrumentation-imo',
                                         'native-prepare', 'native-build', 'native-integrate'))
    parser.add_argument('--repo', type=Path, default=Path('.'))
    parser.add_argument('--config', default='molly-build.json')
    parser.add_argument('--worktree', type=Path, required=True)
    parser.add_argument('--report-dir', type=Path, required=True)
    parser.add_argument('--native-source-dir', type=Path)
    parser.add_argument('--maven-dir', type=Path)
    parser.add_argument('--accept-licenses', action='store_true', help='accept SDK licenses if installation is needed')
    parser.add_argument('--heap', default='5g', help='Gradle heap; one 2 GiB test worker runs separately')
    parser.add_argument('--release-version-name')
    parser.add_argument('--release-version-code')
    args = parser.parse_args()
    try:
        build = Build(args)
        if args.phase == 'sdk':
            build.sdk()
        elif args.phase.startswith('native-'):
            build.native(args.phase)
        else:
            build.gradle(args.phase)
    except (BuildError, OSError, ValueError, KeyError, subprocess.CalledProcessError) as exc:
        print(f'build: {exc}', file=sys.stderr)
        args.report_dir.mkdir(parents=True, exist_ok=True)
        (args.report_dir / (args.phase + '-error.json')).write_text(json.dumps({
            'status': 'FAIL', 'phase': args.phase, 'error': str(exc)}, indent=2) + '\n')
        return 1
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
