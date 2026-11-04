#!/usr/bin/env python3
"""Classify an existing Molly forest against a newer upstream tree without applying it."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

import forest
import git_utils
import verify_complete


CATEGORIES = ('unchanged-base', 'already-upstream', 'upstream-changed')
PRUNING_ROOTS = (
    b'demo/', b'baseline-profile/', b'benchmark/', b'microbenchmark/', b'lib/spinner/',
    b'app/src/spinner/', b'app/src/benchmark/', b'app/src/benchmarkShared/',
    b'app/src/quickstart/', b'app/src/canary/', b'app/src/perf/',
    b'app/src/nightly/', b'app/src/nightlyStaging/',
)


def git(repo, *args, data=None):
    """Inspect Git metadata or mailbox statistics without touching the caller's index."""
    environment = forest.clean_git_env()
    environment.update(GIT_NO_LAZY_FETCH='1', GIT_OPTIONAL_LOCKS='0')
    result = subprocess.run(['git', '--literal-pathspecs', '-C', str(repo), *args],
                            input=data, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                            env=environment)
    if result.returncode:
        raise forest.ForestError(f'git {args[0]} failed ({result.returncode}): '
                                f'{result.stderr.decode(errors="replace").strip()}')
    return result.stdout


def tree_entries(repo, revision):
    """Read raw path-to-(mode, type, object ID) entries; never open file blobs."""
    raw = git(repo, 'ls-tree', '-r', '-z', '--full-tree', revision)
    entries = {}
    for record in raw.split(b'\0'):
        if record:
            metadata, path = record.split(b'\t', 1)
            entries[path] = tuple(metadata.split())
    return entries


def mailbox_paths(repo, contents):
    """Ask Git for both sides of mailbox changes, including quoted rename paths."""
    paths = set()
    for options in ((), ('--reverse',)):
        raw = git(repo, 'apply', '--numstat', '-z', *options, data=contents)
        for record in raw.split(b'\0'):
            if record:
                fields = record.split(b'\t', 2)
                if len(fields) != 3 or not fields[2]:
                    raise forest.ForestError('unexpected Git mailbox numstat output')
                paths.add(fields[2])
    return paths


def source_ownership(data, repo, expected_paths):
    """Bind ownership to checked mailbox bytes and require the complete source delta."""
    owners, features = {}, {}
    for feature in data.order():
        paths = set()
        for name in feature.patches:
            contents = (data.root / name).read_bytes()
            if hashlib.sha256(contents).hexdigest() != data.metadata[name]['sha256']:
                raise forest.ForestError(f'patch changed after validation: {name}')
            paths.update(mailbox_paths(repo, contents))
        for path in paths:
            if path in owners:
                raise forest.ForestError(f'path belongs to both {owners[path]} and {feature.name}: '
                                        f'{os.fsdecode(path)!r}')
            owners[path] = feature.name
        features[feature.name] = paths
    actual = set(owners)
    if actual != expected_paths:
        raise forest.ForestError('source mailbox ownership differs from the complete source delta; '
                                f'missing: {verify_complete.display_paths(expected_paths - actual)}; '
                                f'extra: {verify_complete.display_paths(actual - expected_paths)}')
    return features


def removed_module_additions(original, molly, upstream):
    """Flag new upstream files inside known module roots removed entirely by Molly."""
    roots = set(PRUNING_ROOTS)
    for path in original:
        parts = path.split(b'/')
        for index, part in enumerate(parts[:-1]):
            if part.startswith(b'screenshotTest'):
                roots.add(b'/'.join(parts[:index + 1]) + b'/')
    removed = [root for root in sorted(roots)
               if any(path.startswith(root) for path in original)
               and not any(path.startswith(root) for path in molly)]
    additions = set(upstream) - set(original) - set(molly)
    matches = {os.fsdecode(root): [os.fsdecode(path) for path in sorted(additions)
                                 if path.startswith(root)] for root in removed}
    matches = {root: paths for root, paths in matches.items() if paths}
    all_paths = sorted({path for paths in matches.values() for path in paths})
    return {'count': len(all_paths), 'paths': all_paths, 'roots': matches}


def analyze(repo, source_forest, new_base):
    """Report structural impact; no three-way merge or candidate compatibility is claimed."""
    repo = Path(repo).resolve()
    data = forest.Forest(source_forest)
    identity = verify_complete.validate_identity(data, repo)
    if not isinstance(new_base, str) or not forest.SHA.fullmatch(new_base):
        raise forest.ForestError('new base must be a full lowercase commit SHA')
    verify_complete.commit(repo, new_base, 'new base')
    git(repo, 'merge-base', '--is-ancestor', data.base, new_base)
    original = tree_entries(repo, data.base)
    molly = tree_entries(repo, identity['target'])
    upstream = tree_entries(repo, new_base)
    changed = {path for path in original.keys() | molly.keys()
               if original.get(path) != molly.get(path)}
    ownership = source_ownership(data, repo, changed)
    totals = dict.fromkeys(CATEGORIES, 0)
    features = {}
    for feature in data.order():
        paths = {category: [] for category in CATEGORIES}
        for path in sorted(ownership[feature.name]):
            if upstream.get(path) == original.get(path):
                category = 'unchanged-base'
            elif upstream.get(path) == molly.get(path):
                category = 'already-upstream'
            else:
                category = 'upstream-changed'
            paths[category].append(os.fsdecode(path))
            totals[category] += 1
        features[feature.name] = {'counts': {key: len(value) for key, value in paths.items()},
                                 'paths': paths, 'source_patches': list(feature.patches),
                                 'dependencies': list(feature.dependencies)}
    return {
        'status': 'ANALYZED',
        'source': {'base': data.base, 'target': identity['target'],
                   'upstream_merge': identity['upstream_merge'],
                   'target_tree': identity['expected_tree'],
                   'mailbox_sha256': {name: record['sha256']
                                      for name, record in sorted(data.metadata.items())}},
        'new_base': new_base,
        'new_base_tree': git(repo, 'rev-parse', new_base + '^{tree}').decode().strip(),
        'source_changed_paths': len(changed),
        'counts': totals,
        'features': features,
        'upstream_additions_in_removed_modules': removed_module_additions(original, molly, upstream),
        'scope': ('Comparison of raw Git mode/type/blob IDs and source mailbox ownership only. '
                  'Unchanged-base paths retain their original input; already-upstream paths '
                  'match the Molly snapshot exactly. Upstream-changed paths require merge '
                  'and semantic review. No patch application, merge-conflict detection, '
                  'build, runtime test, or completed port is asserted.'),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', type=Path, default=Path('.'))
    parser.add_argument('--source-forest', type=Path, default=Path('patches/molly'))
    parser.add_argument('--base', required=True, help='full commit SHA of the new upstream base')
    parser.add_argument('--report', type=Path, help='optional JSON artifact outside the source checkout')
    args = parser.parse_args()
    try:
        if args.report is not None and args.report.resolve().is_relative_to(args.repo.resolve()):
            raise forest.ForestError('keep generated impact reports outside the source checkout')
        result = analyze(args.repo, args.source_forest, args.base)
        code = 0
    except (forest.ForestError, git_utils.GitError, OSError, ValueError) as exc:
        result, code = {'status': 'FAIL', 'error': str(exc)}, 1
    serialized = json.dumps(result, indent=2) + '\n'
    if args.report is not None and not args.report.resolve().is_relative_to(args.repo.resolve()):
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(serialized, encoding='utf-8')
    print(serialized, end='')
    return code


if __name__ == '__main__':
    raise SystemExit(main())
