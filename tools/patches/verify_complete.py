#!/usr/bin/env python3
"""Reconstruct a complete pinned Molly tree, including every path and file mode."""
import argparse
import json
import os
from pathlib import Path
import re
import sys
import tempfile

import forest
import git_utils


PARTITION = re.compile(r'[0-9]{4}-(carryover|adapted)\.patch\Z')
PORT_CATEGORIES = ('carryover', 'adapted')


def pin(root, name):
    """Read one immutable commit ID from a forest's plain pin file."""
    values = forest.lines(root / name)
    if len(values) != 1 or not forest.SHA.fullmatch(values[0]):
        raise forest.ForestError(f'{name} must contain exactly one full lowercase commit SHA')
    return values[0]


def commit(repo, value, name):
    result = git_utils.git(repo, 'cat-file', '-t', value, check=False)
    if result.returncode or result.stdout.strip() != 'commit':
        raise forest.ForestError(f'{name} must resolve to a commit: {value}')


def changed_paths(repo, before, after):
    """Compare raw path names without rename heuristics or text decoding."""
    raw = git_utils.git(repo, 'diff-tree', '--no-commit-id', '--name-only',
                     '--no-renames', '-r', '-z', before, after, binary=True).stdout
    return set(raw.rstrip(b'\0').split(b'\0')) if raw else set()


def display_paths(paths):
    names = sorted(paths)
    shown = ', '.join(repr(os.fsdecode(path)) for path in names[:10])
    return shown + (f' (and {len(names) - 10} more)' if len(names) > 10 else '')


def validate_origin(repo, base, target, origin_base, origin_target):
    """Bind optional port origins to immutable commits in the recorded histories."""
    if origin_base is None or origin_target is None:
        raise forest.ForestError('origin-base and origin-target must be supplied together')
    for name, value in (('origin-base', origin_base), ('origin-target', origin_target)):
        if not isinstance(value, str) or not forest.SHA.fullmatch(value):
            raise forest.ForestError(f'{name} must be a full lowercase commit SHA')
        commit(repo, value, name)
    for ancestor, descendant, label in (
            (origin_base, origin_target, 'origin-base must be an ancestor of origin-target'),
            (origin_base, base, 'origin-base must be an ancestor of base'),
            (origin_target, target, 'origin-target must be an ancestor of target')):
        if git_utils.git(repo, 'merge-base', '--is-ancestor', ancestor, descendant, check=False).returncode:
            raise forest.ForestError(label)


def tree_entries(repo, revision):
    """Read raw path-to-(mode, type, object ID) entries without opening blobs."""
    raw = git_utils.git(repo, 'ls-tree', '-r', '-z', '--full-tree', revision, binary=True).stdout
    entries = {}
    for record in raw.split(b'\0'):
        if record:
            metadata, path = record.split(b'\t', 1)
            entries[path] = tuple(metadata.split())
    return entries


def origin_categories(repo, base, target, origin_base, origin_target):
    """Classify whole-file deltas; unchanged/absorbed paths have no mailbox."""
    original, molly, upstream, port = (
        tree_entries(repo, revision) for revision in (origin_base, origin_target, base, target)
    )
    return {
        path: ('carryover' if upstream.get(path) == original.get(path)
               and port.get(path) == molly.get(path) else 'adapted')
        for path in upstream.keys() | port.keys() if upstream.get(path) != port.get(path)
    }


def mailbox_partition(data, name, origin_base, origin_target):
    """Check the filename and origin trailers without interpreting diff contents."""
    match = PARTITION.fullmatch(Path(name).name)
    if match is None:
        raise forest.ForestError(f'{name}: port mailboxes must be numbered carryover or adapted batches')
    category = match.group(1)
    message = (data.root / name).read_bytes().split(b'\ndiff --git ', 1)[0]
    for key, expected in ((b'Origin-Base', origin_base), (b'Origin-Target', origin_target),
                          (b'Port-Category', category)):
        values = re.findall(rb'^' + key + rb':[ \t]*(.*)$', message, re.M)
        if values != [expected.encode('ascii')]:
            raise forest.ForestError(f'{name}: {key.decode()} must match the pinned port provenance')
    return category


def clean_worktree(worktree):
    """Require the materialized files and index to agree with the reconstructed commit."""
    # Ordinary diff/status ignores skip-worktree and assume-unchanged entries.
    # Such flags must not make omitted build inputs look like a complete tree.
    entries = git_utils.git(worktree, 'ls-files', '-v', '-z', binary=True).stdout
    hidden = {entry[2:] for entry in entries.split(b'\0')
              if entry and (entry[:1] == b'S' or entry[:1].islower())}
    if hidden:
        raise forest.ForestError('sparse/skip-worktree or assume-unchanged index entries: '
                                 + display_paths(hidden))
    for options, label in (((), 'tracked working files'), (('--cached',), 'index')):
        check = git_utils.git(worktree, 'diff', *options, '--quiet', '--', check=False)
        if check.returncode:
            raw = git_utils.git(worktree, 'diff', *options, '--name-only', '-z', '--', binary=True).stdout
            paths = set(raw.rstrip(b'\0').split(b'\0')) if raw else set()
            raise forest.ForestError(f'{label} differ from the reconstructed tree: {display_paths(paths)}')
    # Deliberately omit --exclude-standard: ignored build/cache files are extras too.
    raw = git_utils.git(worktree, 'ls-files', '--others', '-z', binary=True).stdout
    if raw:
        paths = set(raw.rstrip(b'\0').split(b'\0'))
        raise forest.ForestError('unexpected untracked files in reconstructed worktree: '
                                 f'{display_paths(paths)}')


def validate_identity(data, repo):
    """Bind snapshot exports to the target and its recorded Signal merge parent."""
    target = pin(data.root, 'target')
    upstream_merge = pin(data.root, 'upstream-merge')
    for name, value in (('base', data.base), ('target', target), ('upstream-merge', upstream_merge)):
        commit(repo, value, name)
    parents = git_utils.git(repo, 'rev-list', '--parents', '-n', '1', upstream_merge).stdout.split()[1:]
    if len(parents) != 2 or parents[1] != data.base:
        raise forest.ForestError('upstream-merge must have exactly two parents with base as its second parent')
    if git_utils.git(repo, 'merge-base', '--is-ancestor', upstream_merge, target, check=False).returncode:
        raise forest.ForestError('upstream-merge must be an ancestor of target')
    for name, metadata in data.metadata.items():
        if metadata['source_commit_shas'] != [target]:
            raise forest.ForestError(f'{name}: Source-Commit must name exactly the pinned target snapshot')
    identity = {'base': data.base, 'target': target, 'upstream_merge': upstream_merge,
                'expected_tree': git_utils.git(repo, 'rev-parse', f'{target}^{{tree}}').stdout.strip()}
    has_origin_base = (data.root / 'origin-base').exists()
    has_origin_target = (data.root / 'origin-target').exists()
    if has_origin_base != has_origin_target:
        raise forest.ForestError('origin-base and origin-target must be supplied together')
    if has_origin_base:
        origin_base = pin(data.root, 'origin-base')
        origin_target = pin(data.root, 'origin-target')
        validate_origin(repo, data.base, target, origin_base, origin_target)
        for name in data.metadata:
            mailbox_partition(data, name, origin_base, origin_target)
        identity.update(origin_base=origin_base, origin_target=origin_target)
    elif any(PARTITION.fullmatch(Path(name).name) for name in data.metadata):
        raise forest.ForestError('carryover/adapted mailboxes require origin-base and origin-target pins')
    return identity


def ownership(data, repo, head, expected_paths, origin=None):
    """Require unique feature ownership and complete coverage of the target delta."""
    commits = git_utils.git(repo, 'rev-list', '--reverse', '--first-parent',
                         f'{data.base}..{head}').stdout.split()
    if len(commits) != len(data.metadata):
        raise forest.ForestError('replay must create exactly one commit per mailbox')
    owners, features, mailbox_owners = {}, {}, {}
    index = 0
    before = data.base
    for feature in data.order():
        paths = set()
        partitions = {category: {'patches': 0, 'changed_paths': 0} for category in PORT_CATEGORIES}
        for name in feature.patches:
            after = commits[index]
            batch = changed_paths(repo, before, after)
            if origin is not None:
                category = PARTITION.fullmatch(Path(name).name).group(1)
                if not batch:
                    raise forest.ForestError(f'{name}: empty port batch')
                for path in batch:
                    if path in mailbox_owners:
                        raise forest.ForestError(f'port path belongs to multiple mailboxes: '
                                                f'{os.fsdecode(path)!r}')
                    mailbox_owners[path] = name
                wrong = {path for path in batch if origin.get(path) != category}
                if wrong:
                    raise forest.ForestError(f'{name}: {category} classification does not match '
                                            f'origin/base/target tree entries: {display_paths(wrong)}')
                partitions[category]['patches'] += 1
                partitions[category]['changed_paths'] += len(batch)
            paths.update(batch)
            before = after
            index += 1
        for path in paths:
            if path in owners:
                raise forest.ForestError(f'path belongs to both {owners[path]} and {feature.name}: '
                                         f'{os.fsdecode(path)!r}')
            owners[path] = feature.name
        features[feature.name] = {'patches': len(feature.patches), 'changed_paths': len(paths),
                                  'dependencies': list(feature.dependencies)}
        if origin is not None:
            features[feature.name]['partitions'] = partitions
    actual_paths = set(owners)
    if actual_paths != expected_paths:
        raise forest.ForestError('feature ownership differs from the complete base-to-target delta; '
                                 f'missing: {display_paths(expected_paths - actual_paths)}; '
                                 f'extra: {display_paths(actual_paths - expected_paths)}')
    return features


def verify(patches, repo, worktree=None):
    """Replay two legal orders and compare their whole Git trees to the source target."""
    repo = Path(repo).resolve()
    data = forest.Forest(patches)
    identity = validate_identity(data, repo)
    origin = (origin_categories(repo, data.base, identity['target'],
                                identity['origin_base'], identity['origin_target'])
              if 'origin_base' in identity else None)
    requested = forest.destination(repo, worktree) if worktree is not None else None
    expected_paths = changed_paths(repo, data.base, identity['target'])
    result = {'status': 'PASS', **identity, 'changed_paths': len(expected_paths),
              'patches': len(data.metadata), 'features': {}, 'replays': []}
    if requested is not None:
        result['worktree'] = str(requested)
    retained_head = None
    for reverse in (False, True):
        if reverse and requested is not None:
            # Keep the clean replay commit, releasing its worktree while checking
            # the other legal order. Restore the requested output afterward.
            forest.git(repo, 'worktree', 'remove', '--force', str(requested))
        order = 'reverse-ready' if reverse else 'ready'
        preserve = requested is not None and not reverse
        with tempfile.TemporaryDirectory(prefix='molly-complete-') as temporary:
            destination = requested if preserve else Path(temporary) / 'worktree'
            try:
                applied = forest.apply(data, repo, destination, reverse=reverse,
                                       expected_tree=identity['expected_tree'])
                clean_worktree(destination)
                if not reverse:
                    head = git_utils.git(destination, 'rev-parse', 'HEAD').stdout.strip()
                    if preserve:
                        retained_head = head
                    result['features'] = ownership(data, repo, head, expected_paths, origin=origin)
                result['replays'].append({'order': order, 'tree': applied['tree_sha'],
                                          'working_tree_clean': True,
                                          'whitespace_report': applied['whitespace_report']})
            except forest.ForestError as exc:
                detail = str(exc)
                if preserve and destination.exists():
                    detail += f'\nWorktree preserved at {destination}'
                elif not preserve:
                    detail = detail.replace('Worktree preserved at',
                                            'Temporary worktree (removed after verification) at')
                raise forest.ForestError(f'{order}: {detail}') from exc
            finally:
                if not preserve and destination.exists():
                    forest.git(repo, 'worktree', 'remove', '--force', str(destination))
                if reverse and requested is not None and retained_head is not None:
                    forest.add_worktree(repo, requested, retained_head)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--patches', type=Path, default=Path('patches/molly'))
    parser.add_argument('--repo', type=Path, default=Path('.'))
    parser.add_argument('--worktree', type=Path, help='retain the first replay at a new external destination')
    parser.add_argument('--report', type=Path)
    args = parser.parse_args()
    try:
        result = verify(args.patches, args.repo, args.worktree)
        code = 0
    except (forest.ForestError, git_utils.GitError, OSError, UnicodeError) as exc:
        result, code = {'status': 'FAIL', 'error': str(exc)}, 1
    serialized = json.dumps(result, indent=2) + '\n'
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(serialized)
    print(serialized, end='')
    return code


if __name__ == '__main__':
    raise SystemExit(main())
