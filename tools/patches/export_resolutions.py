#!/usr/bin/env python3
"""Export only the difference between Git's merge and PR #7's port trees.

This development helper changes only loose objects and its private index.
The original Molly snapshot mailboxes are never edited or re-exported.
"""
import argparse
import collections
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile

import forest
import release

REPO = None
from export_complete import (DIFF_OPTIONS, GROUP_DESCRIPTIONS,
                             dependencies_for, diff_batches, git,
                             group_for_path, group_order)

OUTPUT = None
OLD_SIGNAL = MOLLY = SIGNAL = MERGE = None
FOLLOWUPS = []
SIGNAL_VERSION = MOLLY_VERSION = None

PURPOSE = {
    'security-and-locking': 'Integrate encrypted preferences, secret handling and lock-aware application behavior with the updated Signal classes.',
    'networking-and-proxies': 'Carry Molly SOCKS, Tor, DNS and transport choices through the updated Signal networking interfaces and NetCipher integration.',
    'notifications-and-unifiedpush': 'Retain MollySocket, UnifiedPush and push-selection behavior while resolving notification and delivery API changes.',
    'registration-and-linking': 'Adapt Molly registration and Android linked-device policies to the new Signal registration architecture.',
    'backup-and-restore': 'Resolve backup and restore API changes while retaining Molly backup preferences and serialization behavior.',
    'app-updates': 'Retain the Molly updater and update preferences on the updated Signal application structure.',
    'privacy-controls': 'Retain Molly privacy policies for calls, unknown contacts and logging in the updated upstream code.',
    'maps': 'Retain the Molly map implementation and compatibility layer in the upgraded tree.',
    'google-services': 'Carry the FOSS Google-services compatibility implementation through the upstream changes.',
    'payments-and-donations': 'Keep MobileCoin and Google billing excluded while adapting the remaining donation and migration code.',
    'appearance': 'Resolve dynamic colors, themes and moved UI APIs while retaining Molly appearance behavior.',
    'resources-and-branding': 'Resolve shared resource conflicts and relocated resources, retaining the port source\'s Molly branding and translations.',
    'module-pruning': 'Resolve modify/delete conflicts for modules Molly removes and remove new upstream files added beneath those excluded modules.',
    'integration': 'Resolve shared application, account, service and core integration files whose APIs or feature boundaries changed upstream.',
    'build-and-release': 'Integrate the new Signal build layout and toolchain with Molly dependency, native-fork and release policies.',
    'documentation': 'Resolve user, build and legal documentation according to the existing port source snapshot.',
}


def tree(rev):
    return git(REPO, 'rev-parse', rev + '^{tree}').decode().strip()


def paths(before, after):
    raw = git(REPO, 'diff', '--no-renames', '--name-only', '-z', before, after)
    return [os.fsdecode(path) for path in sorted(raw.split(b'\0')) if path]


def commit_identity(source, preserve_author=False):
    header = git(REPO, 'cat-file', 'commit', source).decode().split('\n\n', 1)[0]
    author = re.search(r'^author (.*) <(.*)> (\d+) ([+-]\d{4})$', header, re.M)
    committer = re.search(r'^committer (.*) <(.*)> (\d+) ([+-]\d{4})$', header, re.M)
    name, email, author_timestamp, author_zone = author.groups()
    _, _, committer_timestamp, committer_zone = committer.groups()
    return {
        'GIT_AUTHOR_NAME': name if preserve_author else 'Molly Patch Reconstruction',
        'GIT_AUTHOR_EMAIL': email if preserve_author else 'molly-patch-reconstruction@example.invalid',
        'GIT_AUTHOR_DATE': f'@{author_timestamp} {author_zone}',
        'GIT_COMMITTER_NAME': 'Molly Patch Reconstruction',
        'GIT_COMMITTER_EMAIL': 'molly-patch-reconstruction@example.invalid',
        'GIT_COMMITTER_DATE': f'@{committer_timestamp} {committer_zone}',
    }


def mailbox(commit):
    return git(REPO, 'format-patch', '--stdout', '--no-stat', '--no-signature',
               '--no-numbered', '--no-thread', '--no-cover-letter', '--no-notes', '--no-attach',
               '--subject-prefix=PATCH', *DIFF_OPTIONS, '-1', commit)


def main():
    global REPO, OUTPUT, OLD_SIGNAL, MOLLY, SIGNAL, MERGE, FOLLOWUPS, SIGNAL_VERSION, MOLLY_VERSION
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', type=Path, default=Path('.'))
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--origin-base', required=True)
    parser.add_argument('--molly', required=True)
    parser.add_argument('--signal', required=True)
    parser.add_argument('--initial-target', required=True)
    parser.add_argument('--signal-version', required=True)
    parser.add_argument('--molly-version', required=True)
    parser.add_argument('--followup', action='append', default=[], metavar='NAME:COMMIT')
    args = parser.parse_args()
    REPO, OUTPUT = args.repo.resolve(), args.output.absolute()
    OLD_SIGNAL, MOLLY, SIGNAL, MERGE = args.origin_base, args.molly, args.signal, args.initial_target
    SIGNAL_VERSION, MOLLY_VERSION = args.signal_version, args.molly_version
    for name, value in (('origin-base', OLD_SIGNAL), ('molly', MOLLY), ('signal', SIGNAL), ('initial-target', MERGE)):
        release.require_sha(value, name)
    for entry in args.followup:
        slug, separator, sha = entry.partition(':')
        if not separator or not re.fullmatch(r'[a-z][a-z0-9-]*', slug) or any(x[0] == slug for x in FOLLOWUPS):
            parser.error('--followup must contain a unique NAME:COMMIT')
        release.require_sha(sha, 'follow-up commit')
        FOLLOWUPS.append((slug, sha))
    if OUTPUT.exists():
        raise RuntimeError(f'Refusing to overwrite {OUTPUT}')
    OUTPUT.mkdir(parents=True)
    mechanical_tree, conflict_paths, merge_output = release.mechanical_merge(REPO, OLD_SIGNAL, MOLLY, SIGNAL)
    lines = merge_output.splitlines()
    conflict_messages = [line for line in lines if line.startswith('CONFLICT')]
    original_paths = set(paths(OLD_SIGNAL, MOLLY))
    automatic_changes = set(paths(SIGNAL, mechanical_tree))
    adaptation_paths = set(paths(mechanical_tree, MERGE))
    initial_groups = collections.defaultdict(list)
    for path in sorted(adaptation_paths):
        initial_groups[group_for_path(path)].append(path)
    order = group_order(dependencies_for(initial_groups))
    summary = {
        'version': 1,
        'status': 'PASS',
        'old_signal': OLD_SIGNAL,
        'molly': MOLLY,
        'signal': SIGNAL,
        'mechanical_tree': mechanical_tree,
        'merge_method': {
            'implementation': 'tools/patches/release.py:mechanical_merge',
            'fixed_refs': {'MOLLY': MOLLY, 'SIGNAL': SIGNAL},
            'merge_base': OLD_SIGNAL,
            'git_version': git(REPO, '--version').decode().strip(),
            'required_tree': mechanical_tree,
            'conflicts_are_expected': True,
        },
        'original_molly_changed_paths': len(original_paths),
        'mechanical_changed_paths_vs_new_signal': len(automatic_changes),
        'mechanical_conflict_paths': len(conflict_paths),
        'mechanical_conflict_messages': len(conflict_messages),
        'initial_resolution_paths': len(adaptation_paths),
        'initial_resolution_conflict_paths': len(adaptation_paths & conflict_paths),
        'initial_resolution_other_paths': len(adaptation_paths - conflict_paths),
        'conflict_resolutions_without_content_changes': [
            {'path': path, 'choice': 'Retain the modified Molly version despite upstream deletion.',
             'source_commit': MERGE, 'ai_generated': True}
            for path in sorted(conflict_paths - adaptation_paths)
        ],
        'mechanical_changes_retained_without_resolution': len(automatic_changes - adaptation_paths),
        'initial_target': MERGE,
        'initial_target_tree': tree(MERGE),
        'target': FOLLOWUPS[-1][1] if FOLLOWUPS else MERGE,
        'target_tree': tree(FOLLOWUPS[-1][1] if FOLLOWUPS else MERGE),
        'layers': [],
    }
    (OUTPUT / 'base-tree').write_text(mechanical_tree + '\n')
    (OUTPUT / 'target-tree').write_text(summary['target_tree'] + '\n')
    (OUTPUT / 'target').write_text(summary['target'] + '\n')
    (OUTPUT / 'conflict-paths').write_text('\n'.join(sorted(conflict_paths)) + '\n')
    all_series = []
    identity = commit_identity(MERGE)
    parent = git(REPO, 'commit-tree', mechanical_tree, '-p', SIGNAL, '-F', '-',
                 data=b'Deterministic merge of official Molly and the new Signal base\n',
                 identity=identity).decode().strip()
    with tempfile.TemporaryDirectory(prefix='molly-resolution-export-') as temp:
        index = Path(temp) / 'index'
        git(REPO, 'read-tree', mechanical_tree, index_file=index)
        before_tree = mechanical_tree
        for group in order:
            directory = OUTPUT / 'initial' / group
            directory.mkdir(parents=True)
            layer = {'id': 'initial/' + group, 'source_commit': MERGE,
                     'description': PURPOSE[group], 'ai_generated': True,
                     'before_tree': before_tree, 'paths': len(initial_groups[group]),
                     'conflict_paths': len(set(initial_groups[group]) & conflict_paths),
                     'patches': [], 'mailbox_bytes': 0}
            for number, (batch, difference) in enumerate(diff_batches(REPO, mechanical_tree, MERGE, initial_groups[group]), 1):
                git(REPO, 'apply', '--cached', '--binary', '--whitespace=nowarn',
                    data=difference, index_file=index)
                current_tree = git(REPO, 'write-tree', '--missing-ok', index_file=index).decode().strip()
                message = (
                    f'Port {group} to Signal {SIGNAL_VERSION}, part {number}\n\n'
                    f'{PURPOSE[group]}\n\n'
                    'These are AI-assisted compatibility changes from the recorded\n'
                    'source checkpoint, exported after Git has performed its deterministic\n'
                    f'three-way merge of the unchanged official Molly {MOLLY_VERSION} snapshot\n'
                    f'with Signal {SIGNAL_VERSION}. The original Molly mailboxes remain unchanged.\n'
                    'Offset-only movement and conflict-free automatic merges are already\n'
                    'in the parent tree and are not re-exported as patch rewrites.\n\n'
                    f'This batch owns {len(batch)} whole-file changes. It records AI-assisted\n'
                    'compatibility changes in the pinned source tree.\n'
                    'The source commit records a derived candidate, not an official\n'
                    'Molly release or a claim of upstream authorship.\n\n'
                    f'Source-Commit: {MERGE}\n'
                    f'Origin-Base: {OLD_SIGNAL}\n'
                    f'Origin-Target: {MOLLY}\n'
                    f'Signal-Base: {SIGNAL}\n'
                    f'Mechanical-Merge-Tree: {mechanical_tree}\n'
                    'AI-Generated: true\n'
                ).encode()
                parent = git(REPO, 'commit-tree', current_tree, '-p', parent, '-F', '-',
                             data=message, identity=identity).decode().strip()
                name = f'{number:04d}-resolve-{group}.patch'
                raw = mailbox(parent)
                (directory / name).write_bytes(raw)
                relative = (directory / name).relative_to(OUTPUT).as_posix()
                layer['patches'].append(relative)
                layer['mailbox_bytes'] += len(raw)
                all_series.append(relative)
            (directory / 'series').write_text(''.join(Path(p).name + '\n' for p in layer['patches']))
            layer['after_tree'] = current_tree
            summary['layers'].append(layer)
            before_tree = current_tree
        if before_tree != summary['initial_target_tree']:
            raise RuntimeError('initial resolution tree mismatch')
        source_before = MERGE
        for slug, source in FOLLOWUPS:
            directory = OUTPUT / 'followups' / slug
            directory.mkdir(parents=True)
            changed = paths(source_before, source)
            difference = git(REPO, 'diff', *DIFF_OPTIONS, source_before, source)
            git(REPO, 'apply', '--cached', '--binary', '--whitespace=nowarn',
                data=difference, index_file=index)
            current_tree = git(REPO, 'write-tree', '--missing-ok', index_file=index).decode().strip()
            if current_tree != tree(source):
                raise RuntimeError(f'follow-up tree mismatch: {slug}')
            source_message = git(REPO, 'cat-file', 'commit', source).decode().split('\n\n', 1)[1].rstrip()
            message = (source_message + '\n\n'
                       'Carry this AI-assisted source change as its own\n'
                       'reviewable patch, preserving the original source author and diff.\n'
                       'The official Molly snapshot and earlier resolution patches do\n'
                       'not receive regenerated provenance headers.\n\n'
                       f'Source-Commit: {source}\n'
                       'AI-Generated: true\n').encode()
            parent = git(REPO, 'commit-tree', current_tree, '-p', parent, '-F', '-',
                         data=message, identity=commit_identity(source, True)).decode().strip()
            raw = mailbox(parent)
            name = '0001-' + slug + '.patch'
            (directory / name).write_bytes(raw)
            (directory / 'series').write_text(name + '\n')
            relative = (directory / name).relative_to(OUTPUT).as_posix()
            summary['layers'].append({
                'id': 'followups/' + slug, 'source_commit': source,
                'description': source_message, 'ai_generated': True,
                'before_tree': before_tree, 'after_tree': current_tree,
                'paths': len(changed), 'patches': [relative], 'mailbox_bytes': len(raw),
            })
            all_series.append(relative)
            before_tree = current_tree
            source_before = source
    (OUTPUT / 'series').write_text('\n'.join(all_series) + '\n')
    summary['patch_count'] = len(all_series)
    summary['total_mailbox_bytes'] = sum(x['mailbox_bytes'] for x in summary['layers'])
    summary['final_export_commit'] = parent
    summary['patch_sha256'] = {p: hashlib.sha256((OUTPUT / p).read_bytes()).hexdigest() for p in all_series}
    (OUTPUT / 'provenance.json').write_text(json.dumps(summary, indent=2) + '\n')
    print(json.dumps({k: v for k, v in summary.items() if k not in ('layers', 'patch_sha256')}, indent=2))


if __name__ == '__main__':
    main()
