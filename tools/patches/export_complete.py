#!/usr/bin/env python3
"""Export the complete Molly tree delta as grouped standard Git mailboxes.

This is a review organization, not a claim that groups are optional runtime
features. Whole-forest equality with the pinned target is the completeness gate.
"""
import argparse
from collections import defaultdict
import json
import os
from pathlib import Path, PurePosixPath
import subprocess
import sys
import tempfile

from forest import Forest, ForestError, SHA, clean_git_env
from verify_complete import PORT_CATEGORIES, origin_categories, validate_origin


MAX_BATCH_PATHS = 100
MAX_BATCH_BYTES = 1024 * 1024
RECONSTRUCTION_NAME = 'Molly Patch Reconstruction'
RECONSTRUCTION_EMAIL = 'molly-patch-reconstruction@example.invalid'
DIFF_OPTIONS = (
    '--no-renames', '--binary', '--full-index', '--no-ext-diff', '--no-textconv',
    '--no-color', '--no-relative', '--src-prefix=a/', '--dst-prefix=b/',
    '--diff-algorithm=myers', '--no-indent-heuristic', '--unified=3',
)


class ExportError(RuntimeError):
    pass

APP = 'app/src/main/java/org/thoughtcrime/securesms/'

# Files containing several feature integrations stay together. No hunk splitting.
SHARED_PATHS = {
    'app/src/main/AndroidManifest.xml',
    APP + 'AppInitialization.java',
    APP + 'ApplicationContext.java',
    APP + 'BaseActivity.java',
    APP + 'MainActivity.kt',
    APP + 'dependencies/AppDependencies.kt',
    APP + 'dependencies/ApplicationDependencies.java',
    APP + 'dependencies/NetworkDependenciesModule.kt',
    APP + 'keyvalue/AccountValues.kt',
    APP + 'keyvalue/SettingsValues.java',
    APP + 'keyvalue/SignalStore.kt',
    APP + 'util/TextSecurePreferences.java',
    APP + 'util/Environment.kt',
    APP + 'util/RemoteConfig.kt',
    APP + 'jobs/JobManagerFactories.java',
    APP + 'messages/IncomingMessageObserver.kt',
    APP + 'messages/MessageContentProcessor.kt',
    APP + 'messages/DataMessageProcessor.kt',
    APP + 'messages/SyncMessageProcessor.kt',
    APP + 'service/KeyCachingService.java',
    APP + 'database/SignalDatabase.kt',
    APP + 'database/DatabaseFactory.java',
    APP + 'push/AccountManagerFactory.java',
    APP + 'push/SignalServiceNetworkAccess.kt',
    'core/ui/src/main/java/org/signal/core/ui/CoreUiDependencies.kt',
    'core/util/src/main/java/org/signal/core/util/CoreUtilDependencies.kt',
    'lib/libsignal-service/src/main/java/org/whispersystems/signalservice/api/SignalServiceAccountManager.java',
    'lib/libsignal-service/src/main/java/org/whispersystems/signalservice/api/SignalServiceMessageSender.java',
    'lib/libsignal-service/src/main/java/org/whispersystems/signalservice/internal/push/PushServiceSocket.java',
    'lib/libsignal-service/src/main/java/org/whispersystems/signalservice/internal/configuration/SignalServiceConfiguration.kt',
}

GROUP_DESCRIPTIONS = {
    'security-and-locking': 'Passphrase/Argon2, encrypted storage and preferences, biometrics, memory wiping and lock-aware jobs.',
    'networking-and-proxies': 'SOCKS/Tor/Orbot, DNS and connectivity monitoring, proxy transport support and removals of replaced Signal proxy code.',
    'notifications-and-unifiedpush': 'UnifiedPush/MollySocket, optional push-service selection and notification delivery/presentation.',
    'registration-and-linking': 'Registration policies, Android linked-device support, provisioning and removal of superseded registration flows.',
    'backup-and-restore': 'Local/remote backup policy, scheduling, retention, compatibility and restore integration.',
    'app-updates': 'Molly APK updating and associated jobs/settings.',
    'privacy-controls': 'Unknown-contact controls, disappearing calls, privacy settings and optional/redacted debug logging.',
    'maps': 'OpenStreetMap/osmdroid and Google Maps API compatibility layer.',
    'google-services': 'FOSS Google service compatibility implementations and supporting processors.',
    'payments-and-donations': 'MobileCoin/billing removal, retained migration compatibility and donation support changes.',
    'appearance': 'Dynamic themes, view colors, wallpapers and UI theme helpers.',
    'resources-and-branding': 'Shared localized resources, icons, artwork, layouts, XML policy and resource deletions.',
    'build-and-release': 'Gradle/build conventions, dependency pins, reproducibility, native-library integration, lint, CI/release automation.',
    'module-pruning': 'Removed demos, benchmark/spinner/quickstart flavors and screenshot-test infrastructure.',
    'documentation': 'Molly user/build/legal documentation and removal of superseded upstream documentation.',
    'integration': 'Shared application/core/library integration, mixed-purpose files and remaining exact target delta.',
}


def group_for_path(path):
    """Assign any changed path exactly once, preserving the complete file delta."""
    parts = PurePosixPath(path).parts
    name = parts[-1]
    if path in SHARED_PATHS:
        return 'integration'
    if path.startswith(('demo/', 'baseline-profile/', 'benchmark/', 'microbenchmark/', 'lib/spinner/')):
        return 'module-pruning'
    if path.startswith(('app/src/spinner/', 'app/src/benchmark/', 'app/src/benchmarkShared/',
                        'app/src/quickstart/', 'app/src/canary/', 'app/src/perf/',
                        'app/src/nightly/', 'app/src/nightlyStaging/')) or any(
            part.startswith('screenshotTest') for part in parts):
        return 'module-pruning'
    if path.endswith('.md'):
        return 'documentation'
    if path.startswith('lib/billing/'):
        return 'payments-and-donations'
    if name in ('build.gradle', 'build.gradle.kts', 'settings.gradle', 'settings.gradle.kts', 'gradle.properties'):
        return 'build-and-release'
    if '/jni/' in path or '/jniLibs/' in path or '/proguard/' in path or name.endswith(('.mk', '.pro')):
        return 'build-and-release'
    if name in ('.gitignore', '.gitattributes', '.editorconfig'):
        return 'build-and-release'
    if path.startswith('core-gms/'):
        return 'google-services'
    if path.startswith('lib/donations/') or '/org/signal/billing/' in path:
        return 'payments-and-donations'
    if '/payments/' in path or '/conversation/ui/payment/' in path or any(
            token in name for token in ('Payment', 'Payments', 'MobileCoin')):
        return 'payments-and-donations'
    if path.startswith('lib/netcipher/'):
        return 'networking-and-proxies'
    if len(parts) == 1 or path.startswith(('.github/', '.idea/', 'build-logic/', 'buildSrc/',
                                        'gradle/', 'mk/', 'reproducible-builds/', 'wire-handler/', 'lintchecks/')):
        return 'build-and-release'
    if path.startswith('feature/registration/') or '/registration/' in path or '/linkdevice/' in path:
        return 'registration-and-linking'
    if name in ('DeviceId.java', 'LinkDeviceRequest.kt', 'MissingCapabilitiesException.java', 'PrimaryProvisioningCipher.java'):
        return 'registration-and-linking'
    if path.startswith('app/src/main/java/im/molly/unifiedpush/') or '/notifications/' in path or '/gcm/' in path:
        return 'notifications-and-unifiedpush'
    if 'UnifiedPush' in name or name == 'PushServiceEvent.kt':
        return 'notifications-and-unifiedpush'
    if '/com/google/android/gms/maps/' in path or '/osm/' in path:
        return 'maps'
    if '/res/' in path or '/assets/' in path:
        return 'resources-and-branding'
    if '/crypto/' in path or '/biometric/' in path or any(
            token in name for token in ('Passphrase', 'MasterSecret', 'WipeMemory', 'ScreenLock')):
        return 'security-and-locking'
    if name in ('SecurePreferenceManager.java', 'SharedPreferencesDataStore.java', 'PanicResponderListener.kt'):
        return 'security-and-locking'
    if '/backup/' in path or '/restore/' in path or 'Backup' in name:
        return 'backup-and-restore'
    if '/apkupdate/' in path or 'ApkUpdate' in name or '/settings/app/updates/' in path:
        return 'app-updates'
    if '/net/' in path or '/proxy/' in path or 'Proxy' in name or name in (
            'NetworkAvailableEvent.java', 'NetworkConnectionListener.kt', 'NetworkConnectionListenerTest.kt',
            'NetworkConstraintObserver.java', 'NetworkUtil.java'):
        return 'networking-and-proxies'
    if path.startswith('lib/network/') or '/internal/websocket/' in path:
        return 'networking-and-proxies'
    if '/blocked/' in path or '/calls/log/' in path or '/logging/' in path or '/debuglog/' in path:
        return 'privacy-controls'
    if name in ('DeletedCallEventManager.kt', 'CallTable.java') or '/settings/app/privacy/' in path:
        return 'privacy-controls'
    if '/wallpaper/' in path or '/color/' in path or name.startswith('Dynamic') and 'Theme' in name:
        return 'appearance'
    if path.startswith('core/ui/') or name in ('ThemeUtil.java', 'SplashScreenUtil.java'):
        return 'appearance'
    return 'integration'


def git(repo, *args, data=None, index_file=None, identity=None):
    """Run Git without inheriting a caller's repository or index overrides."""
    environment = clean_git_env()
    environment.update(GIT_CONFIG_NOSYSTEM='1', GIT_CONFIG_GLOBAL=os.devnull,
                       GIT_LFS_SKIP_SMUDGE='1', LC_ALL='C', TZ='UTC')
    if index_file is not None:
        environment['GIT_INDEX_FILE'] = str(index_file)
    if identity is not None:
        environment.update(identity)
    command = ['git', '--literal-pathspecs', '-C', str(repo),
               '-c', 'core.hooksPath=/dev/null', '-c', 'commit.gpgSign=false',
               '-c', 'core.autocrlf=false', '-c', 'core.quotePath=true',
               '-c', 'format.useAutoBase=false', '-c', 'format.attach=false',
               '-c', 'format.signOff=false', *args]
    result = subprocess.run(command, input=data, stdout=subprocess.PIPE,
                            stderr=subprocess.PIPE, env=environment)
    if result.returncode:
        detail = result.stderr.decode(errors='replace').strip()
        raise ExportError(f'git {args[0]} failed ({result.returncode}): {detail}')
    return result.stdout


def validate_source(repo, base, target, upstream_merge):
    """Bind explicit source pins to the upstream merge, without guessing by author."""
    for name, revision in (('base', base), ('target', target), ('upstream merge', upstream_merge)):
        if not SHA.fullmatch(revision):
            raise ExportError(f'{name} must be a full lowercase commit SHA')
        if git(repo, 'cat-file', '-t', revision).strip() != b'commit':
            raise ExportError(f'{name} must name a commit')
    parents = git(repo, 'rev-list', '--parents', '-n', '1', upstream_merge).decode().split()[1:]
    if len(parents) != 2 or parents[1] != base:
        raise ExportError('base must be the second parent of the recorded upstream merge')
    git(repo, 'merge-base', '--is-ancestor', upstream_merge, target)
    return git(repo, 'rev-parse', target + '^{tree}').decode().strip()


def new_output(path):
    """Create a new output directory; never overwrite a previous export."""
    output = Path(path).absolute()
    if '..' in output.parts:
        raise ExportError('output path must not contain parent traversal')
    for component in (output, *output.parents):
        if component.is_symlink():
            raise ExportError(f'output path contains a symlink: {component}')
    if os.path.lexists(output):
        raise ExportError(f'output already exists: {output}')
    output.mkdir(parents=True)
    return output


def dependencies_for(groups):
    """Keep shared integration after implementation groups, and build wiring last."""
    functional = set(groups) - {'integration', 'build-and-release', 'documentation'}
    dependencies = {group: [] for group in groups}
    if 'integration' in groups:
        dependencies['integration'] = sorted(functional)
    if 'build-and-release' in groups:
        dependencies['build-and-release'] = ['integration'] if 'integration' in groups else sorted(functional)
    return dependencies


def group_order(dependencies):
    """Use the same deterministic ready-group ordering as the forest reader."""
    pending, complete = set(dependencies), set()
    result = []
    while pending:
        ready = sorted(group for group in pending if set(dependencies[group]) <= complete)
        if not ready:
            raise ExportError('internal review-group dependency cycle')
        group = ready[0]
        result.append(group)
        complete.add(group)
        pending.remove(group)
    return result


def diff_batches(repo, base, target, paths):
    """Bound review size without dividing a file or losing binary/rename-side data."""
    def split(batch):
        difference = git(repo, 'diff', *DIFF_OPTIONS, base, target, '--', *batch)
        if not difference:
            raise ExportError('changed-path batch unexpectedly has an empty diff')
        if len(difference) > MAX_BATCH_BYTES and len(batch) > 1:
            middle = len(batch) // 2
            yield from split(batch[:middle])
            yield from split(batch[middle:])
        else:
            yield batch, difference

    for start in range(0, len(paths), MAX_BATCH_PATHS):
        yield from split(paths[start:start + MAX_BATCH_PATHS])


def export_batches(repo, base, target, paths, categories):
    """Keep legacy batches unchanged; optional port batches never mix categories."""
    if categories is None:
        for batch, difference in diff_batches(repo, base, target, paths):
            yield None, batch, difference
    else:
        for category in PORT_CATEGORIES:
            selected = [path for path in paths if categories[os.fsencode(path)] == category]
            for batch, difference in diff_batches(repo, base, target, selected):
                yield category, batch, difference


def export(repo, base, target, upstream_merge, output_path, *, origin_base=None, origin_target=None):
    """Build synthetic commit objects and mailboxes using only a private index."""
    repo = Path(repo).resolve()
    target_tree = validate_source(repo, base, target, upstream_merge)
    categories = None
    if origin_base is not None or origin_target is not None:
        validate_origin(repo, base, target, origin_base, origin_target)
        categories = origin_categories(repo, base, target, origin_base, origin_target)
    changed = git(repo, 'diff', '--no-renames', '--name-only', '-z', base, target)
    paths = [os.fsdecode(path) for path in sorted(changed.split(b'\0')) if path]
    if not paths:
        raise ExportError('source base and target have identical trees')
    groups = defaultdict(list)
    for path in paths:
        groups[group_for_path(path)].append(path)
    dependencies = dependencies_for(groups)
    timestamp = git(repo, 'log', '-1', '--format=%ct', target).decode().strip()
    identity = {
        'GIT_AUTHOR_NAME': RECONSTRUCTION_NAME,
        'GIT_AUTHOR_EMAIL': RECONSTRUCTION_EMAIL,
        'GIT_AUTHOR_DATE': f'@{timestamp} +0000',
        'GIT_COMMITTER_NAME': RECONSTRUCTION_NAME,
        'GIT_COMMITTER_EMAIL': RECONSTRUCTION_EMAIL,
        'GIT_COMMITTER_DATE': f'@{timestamp} +0000',
    }
    output = new_output(output_path)
    for filename, revision in (('base', base), ('target', target), ('upstream-merge', upstream_merge)):
        (output / filename).write_text(revision + '\n', encoding='utf-8')
    if categories is not None:
        (output / 'origin-base').write_text(origin_base + '\n', encoding='utf-8')
        (output / 'origin-target').write_text(origin_target + '\n', encoding='utf-8')
    summary = {'status': 'PASS', 'base': base, 'target': target, 'upstream_merge': upstream_merge,
               'target_tree': target_tree, 'changed_paths': len(paths), 'patch_count': 0,
               'groups': {}, 'output': str(output)}
    if categories is not None:
        summary.update(origin_base=origin_base, origin_target=origin_target)
    parent = base
    with tempfile.TemporaryDirectory(prefix='molly-complete-export-') as temp:
        index_file = Path(temp) / 'index'
        git(repo, 'read-tree', base, index_file=index_file)
        for group in group_order(dependencies):
            directory = output / group
            directory.mkdir()
            if dependencies[group]:
                (directory / 'depends').write_text('\n'.join(dependencies[group]) + '\n', encoding='utf-8')
            record = {'paths': len(groups[group]), 'patches': 0, 'mailbox_bytes': 0,
                      'oversized_single_paths': []}
            if categories is not None:
                record['partitions'] = {category: {'paths': 0, 'patches': 0}
                                        for category in PORT_CATEGORIES}
            batches = export_batches(repo, base, target, groups[group], categories)
            for number, (category, batch, difference) in enumerate(batches, start=1):
                if number > 9999:
                    raise ExportError(f'{group}: too many batches for four-digit series names')
                git(repo, 'apply', '--cached', '--binary', '--whitespace=nowarn',
                    data=difference, index_file=index_file)
                tree = git(repo, 'write-tree', index_file=index_file).decode().strip()
                partition_name = f' {category}' if category is not None else ''
                origin_note = ''
                origin_trailers = ''
                if category is not None:
                    if category == 'carryover':
                        origin_note = (
                            'For every path in this batch, the new base entry equals the origin\n'
                            'base entry, and the port target entry equals the origin target entry.\n'
                        )
                    else:
                        origin_note = (
                            'For every path in this batch, at least one endpoint differs from\n'
                            'its corresponding origin entry. This includes changed upstream\n'
                            'inputs, merged results and newly introduced port changes.\n'
                        )
                    origin_note += (
                        'Entries compare file mode, object type and object ID, including absence.\n'
                        'The category describes whole-file deltas, not conflict history or\n'
                        'build/runtime compatibility.\n\n'
                    )
                    origin_trailers = (
                        f'Origin-Base: {origin_base}\n'
                        f'Origin-Target: {origin_target}\n'
                        f'Port-Category: {category}\n'
                    )
                message = (
                    f'Reconstruct Molly {group}{partition_name}, part {number}\n\n'
                    f'{GROUP_DESCRIPTIONS[group]}\n\n'
                    f'Export the complete file deltas for this review batch from\n'
                    f'{base} to {target}.\n'
                    'This reconstructed snapshot retains the target tree, including merge\n'
                    'resolutions and retained backports. The group is a review partition,\n'
                    'not an independently optional runtime feature.\n\n'
                    'Source-Commit identifies the pinned source-tree snapshot, not the\n'
                    'historical author of these changes. The mailbox identity records\n'
                    'this mechanical reconstruction.\n\n'
                    f'{origin_note}'
                    f'Source-Commit: {target}\n'
                    f'{origin_trailers}'
                ).encode('utf-8')
                commit = git(repo, 'commit-tree', tree, '-p', parent, '-F', '-',
                             data=message, identity=identity).decode().strip()
                mailbox = git(repo, 'format-patch', '--stdout', '--no-stat', '--no-signature',
                              '--no-numbered', '--no-thread', '--no-cover-letter', '--no-notes',
                              '--subject-prefix=PATCH', *DIFF_OPTIONS, '-1', commit)
                filename = (f'{number:04d}-{category}.patch' if category is not None
                            else f'{number:04d}-target-snapshot.patch')
                (directory / filename).write_bytes(mailbox)
                with (directory / 'series').open('a', encoding='utf-8') as series:
                    series.write(filename + '\n')
                parent = commit
                record['patches'] += 1
                record['mailbox_bytes'] += len(mailbox)
                if category is not None:
                    record['partitions'][category]['paths'] += len(batch)
                    record['partitions'][category]['patches'] += 1
                if len(difference) > MAX_BATCH_BYTES:
                    record['oversized_single_paths'].append(batch[0])
            summary['groups'][group] = record
            summary['patch_count'] += record['patches']
        actual_tree = git(repo, 'write-tree', index_file=index_file).decode().strip()
    if actual_tree != target_tree:
        raise ExportError(f'export tree mismatch: expected {target_tree}, got {actual_tree}; output retained at {output}')
    forest = Forest(output)
    if len(forest.patches()) != summary['patch_count']:
        raise ExportError('exported forest does not list every generated mailbox')
    summary['tree_sha'] = actual_tree
    return summary


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', type=Path, default=Path('.'))
    parser.add_argument('--base', required=True, help='full reviewed upstream commit SHA')
    parser.add_argument('--target', required=True, help='full reviewed Molly source commit SHA')
    parser.add_argument('--upstream-merge', required=True, help='Molly merge whose second parent is the base')
    parser.add_argument('--origin-base', help='original upstream commit; requires --origin-target')
    parser.add_argument('--origin-target', help='original Molly commit; splits carryover/adapted batches')
    parser.add_argument('--output', type=Path, required=True, help='new directory; existing destinations are refused')
    args = parser.parse_args()
    try:
        result = export(args.repo, args.base, args.target, args.upstream_merge, args.output,
                        origin_base=args.origin_base, origin_target=args.origin_target)
    except (ExportError, ForestError, OSError, UnicodeError) as exc:
        print(f'complete export: {exc}\nAny partial export is retained at {args.output}.', file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
