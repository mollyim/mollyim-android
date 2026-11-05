#!/usr/bin/env python3
"""Verify and import the current port's pinned source checkpoint chain."""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import sys

import forest
import git_utils
import release


def sha(value, label):
    return release.require_sha(value, label)


def bundle_path(root, directory, value):
    if not isinstance(value, str) or not value or '\\' in value:
        raise ValueError('invalid bundle path')
    path = PurePosixPath(value)
    if path.is_absolute() or any(part in ('', '.', '..') for part in value.split('/')):
        raise ValueError('unsafe bundle path')
    target = release.inside(root, (directory / value).relative_to(root).as_posix(), 'source bundle')
    resolved = target.resolve(strict=True)
    if not resolved.is_relative_to(root) or not target.is_file():
        raise ValueError('unsafe bundle path')
    return resolved


def import_bundle(repo, root, directory, path, digest, size, ref, commit, prerequisites):
    sha(commit, 'bundle source commit')
    if not isinstance(digest, str) or not re.fullmatch(r'[0-9a-f]{64}', digest):
        raise ValueError('invalid bundle sha256')
    if type(size) is not int or size < 1:
        raise ValueError('invalid bundle byte count')
    file = bundle_path(root, directory, path)
    data = file.read_bytes()
    if len(data) != size or hashlib.sha256(data).hexdigest() != digest:
        raise ValueError('bundle bytes or sha256 mismatch: ' + path)
    header = data.split(b'\n\n', 1)[0].splitlines()
    if not header or header[0] not in (b'# v2 git bundle', b'# v3 git bundle'):
        raise ValueError('invalid bundle header')
    refs, required = [], []
    for line in header[1:]:
        if line.startswith(b'@'):
            continue
        if line.startswith(b'-'):
            required.append(line[1:].split(b' ', 1)[0].decode('ascii'))
        else:
            refs.append(line.decode('ascii').split(' ', 1))
    if refs != [[commit, ref]]:
        raise ValueError('bundle advertised ref or commit mismatch')
    if len(required) != len(prerequisites) or set(required) != set(prerequisites):
        raise ValueError('bundle prerequisites mismatch')
    git_utils.git(repo, 'bundle', 'verify', str(file))
    cache = 'refs/molly-pipeline/source-checkpoints/' + commit
    existing = git_utils.git(repo, 'rev-parse', '--verify', cache, check=False)
    if existing.returncode == 0 and existing.stdout.strip() != commit:
        raise ValueError('immutable source checkpoint cache ref differs')
    # Unbundle even when cached, so a corrupt pack cannot pass on a warm cache.
    # Atomic create prevents overwriting another process's or user's refs.
    git_utils.git(repo, 'bundle', 'unbundle', str(file))
    if existing.returncode:
        git_utils.git(repo, 'update-ref', cache, commit, '0' * 40)
    if git_utils.git(repo, 'rev-parse', '--verify', cache).stdout.strip() != commit:
        raise ValueError('source checkpoint import mismatch')


def commit_info(repo, commit):
    sha(commit, 'source commit')
    fields = git_utils.git(repo, 'show', '-s', '--format=%T %P', commit).stdout.strip().split()
    return fields[0], fields[1:]


def verify(repo):
    root = Path(repo).resolve()
    config = release.load_config(root / 'molly-build.json')
    if config['port'] is None:
        print('Source bundle verification skipped: profile has no port')
        return
    series = release.inside(root, config['port']['series'], 'resolution series')
    directory = series.parent
    provenance = json.loads((directory / 'provenance.json').read_text())
    base = provenance['source_bundle_base']
    molly = sha(config['molly']['target'], 'Molly target')
    signal = sha(config['signal']['commit'], 'Signal commit')
    if provenance['origin_target'] != molly or provenance['signal'] != signal:
        raise ValueError('profile and provenance source pins differ')
    prerequisites = base['prerequisites']
    if not isinstance(prerequisites, list) or len(prerequisites) != 2 or set(prerequisites) != {molly, signal}:
        raise ValueError('base prerequisites differ from pinned upstream commits')
    if base['path'] != 'source-checkpoint.bundle' or base['ref'] != 'refs/heads/source-checkpoint/core':
        raise ValueError('incorrect base bundle path or ref')
    import_bundle(root, root, directory, base['path'], base['sha256'], base['bytes'], base['ref'], base['source_commit'], prerequisites)
    layers = provenance['layers']
    initial = sha(provenance['initial_source'], 'initial source')
    initial_layers = [layer for layer in layers if layer['id'].startswith('initial/')]
    if not initial_layers or any(layer['source_commit'] != initial for layer in initial_layers):
        raise ValueError('initial layer source commits differ')
    tree, parents = commit_info(root, initial)
    if parents != [molly, signal] or tree != initial_layers[-1]['after_tree']:
        raise ValueError('initial source parents or tree differ')
    previous, previous_tree = initial, tree
    bundled = False
    for layer in layers:
        if layer['id'].startswith('initial/'):
            if previous != initial or bundled:
                raise ValueError('initial layers out of order')
            continue
        if not layer['id'].startswith('followups/'):
            raise ValueError('unknown source layer')
        commit = sha(layer['source_commit'], 'follow-up source commit')
        if 'source_bundle' in layer:
            if not bundled:
                if previous != base['source_commit'] or previous_tree != base['source_tree']:
                    raise ValueError('base source does not match unbundled chain')
                bundled = True
            if layer['source_bundle_prerequisite'] != previous:
                raise ValueError('follow-up prerequisite differs from previous source')
            ref = 'refs/heads/source-checkpoint/' + layer['id'].split('/')[-1]
            import_bundle(root, root, directory, layer['source_bundle'], layer['source_bundle_sha256'], layer['source_bundle_bytes'], ref, commit, [previous])
        elif bundled or previous == base['source_commit']:
            raise ValueError('unbundled follow-up after base source')
        tree, parents = commit_info(root, commit)
        if parents != [previous] or layer['before_tree'] != previous_tree or layer['after_tree'] != tree:
            raise ValueError('follow-up source parent or tree differs: ' + layer['id'])
        previous, previous_tree = commit, tree
    if not bundled and (previous != base['source_commit'] or previous_tree != base['source_tree']):
        raise ValueError('base source does not match unbundled chain')
    if previous_tree != config['target_tree'] or previous_tree != provenance['target_tree']:
        raise ValueError('final source tree differs from profile or provenance')
    print('Source checkpoint bundles verified: ' + previous)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', default='.')
    args = parser.parse_args()
    try:
        verify(args.repo)
    except (ValueError, KeyError, TypeError, OSError, UnicodeError, forest.ForestError, git_utils.GitError) as exc:
        print('source bundle verification failed: ' + str(exc), file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
