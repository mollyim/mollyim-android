#!/usr/bin/env python3
"""Check a pull request against its base using the contents of both trees.

The scope is detected from what the head adds to the base, never from branch names:

  molly   the base has no `patches/molly`; the head adds the tooling and Molly as patches on Signal
  port    the base has `patches/molly` and no port; the head adds one in `patches/ports`
  change  the base has `patches/molly`; the head changes patches, tools or an existing port

Every scope also requires that
  * the head's repository files (`.github/**`, README, CONTRIBUTING) equal the base's, except for the
    pipeline workflow and the Molly workflows the pipeline wraps;
  * outside the pipeline's own paths the head tree equals the Signal commit pinned in `molly-build.json`;
  * the base is a known state: Molly's official tree (molly scope) or a tree that already passed these
    checks (other scopes). The base needs no ancestry relation to the head.
"""
import argparse
import json
import subprocess
import sys

MOLLY = '0e202dda90785771ff8c87526eaaf30c655f54f3'
OLD = 'c47a74ba0961903eeaeda6d22347a990056c3e83'
MOLLY_TREE = '3cd0bb01cf840e4b702187085432020d163d80ce'
FOREST = '7a280ac23b61cef2c09009a6dbed02773fff3dfa'
SCOPES = ('molly', 'port', 'change')
OWN_WORKFLOW = '.github/workflows/molly-patch-release.yml'
# Repository files that may differ from the base: the pipeline workflow and the Molly workflows that run it.
REPO_FILE_CHANGES = {OWN_WORKFLOW, '.github/workflows/release.yml', '.github/workflows/reprocheck.yml',
                     '.github/workflows/tagrelease.yml', '.github/workflows/test.yml'}
# The composite action the Molly workflows use to reconstruct the source.
REPO_FILE_DIRS = ('.github/actions/molly-source/',)


def git(repo, *args, check=True):
    return subprocess.run(['git', '-C', str(repo), *args], stdout=subprocess.PIPE,
                          stderr=subprocess.PIPE, check=check).stdout


def repo_file(path):
    """Files that belong to the repository itself, not to the Signal source or the pipeline."""
    return path.startswith('.github/') or path in ('README.md', 'CONTRIBUTING.md')


def metadata(path):
    return (path.startswith('tools/patches/') or path.startswith('patches/') or
            path in {'molly-build.json', 'docs/PATCH-PIPELINE.md', 'docs/PORT-REPORT.md'} or
            repo_file(path))


def changed(repo, a, b):
    return [raw.decode('utf-8', 'surrogateescape')
            for raw in filter(None, git(repo, 'diff', '--no-renames', '--name-only', '-z', a, b).split(b'\0'))]


def exists(repo, ref, path):
    return subprocess.run(['git', '-C', str(repo), 'cat-file', '-e', ref + ':' + path],
                          stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0


def profile(repo, ref):
    raw = git(repo, 'show', ref + ':molly-build.json', check=False)
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        return None


def detect_scope(repo, base, head='HEAD'):
    if not exists(repo, base, 'patches/molly'):
        if exists(repo, head, 'patches/molly'):
            return 'molly'
        raise ValueError('cannot classify: the head adds no Molly patches to a base without them')
    lower = profile(repo, base)
    # Only a base without a port can take a new one; edits to an existing port are ordinary changes.
    base_port = lower.get('port') if isinstance(lower, dict) else None
    if base_port is None and any(path.startswith('patches/ports/') for path in changed(repo, base, head)):
        return 'port'
    return 'change'


def allowed(scope, path):
    """Pipeline-owned paths each scope may touch. Signal source paths are proved by the source projection."""
    if not metadata(path) or repo_file(path):  # repository files are checked against the base separately
        return True
    if scope == 'molly':
        return path.startswith('tools/patches/') or path.startswith('patches/molly/') or path in {'molly-build.json', 'docs/PATCH-PIPELINE.md'}
    if scope == 'port':
        return (path.startswith('patches/ports/') or path.startswith('tools/patches/') or
                path in {'molly-build.json', 'docs/PATCH-PIPELINE.md', 'docs/PORT-REPORT.md'})
    return True


def check_repo_files(repo, base, head):
    for path in changed(repo, base, head):
        if repo_file(path) and path not in REPO_FILE_CHANGES and not path.startswith(REPO_FILE_DIRS):
            raise ValueError('repository file differs from the base: ' + path)
    entry = git(repo, 'ls-tree', head, '--', OWN_WORKFLOW).decode()
    if not entry.startswith('100644 blob '):
        raise ValueError('pipeline workflow must be a regular 100644 file: ' + OWN_WORKFLOW)


def check_projection(repo, source, tree, label):
    """Outside pipeline paths and repository files, `tree` must equal `source`."""
    for path in changed(repo, source, tree):
        if not metadata(path):
            raise ValueError(label + ' source projection differs: ' + path)


def check_scope(repo, scope, base, head='HEAD'):
    scope = scope or detect_scope(repo, base, head)
    if scope not in SCOPES:
        raise ValueError('unknown scope')
    for path in changed(repo, base, head):
        if not allowed(scope, path):
            raise ValueError('path outside scope: ' + path)
    check_repo_files(repo, base, head)

    if scope == 'molly':
        if exists(repo, base, 'molly-build.json') or exists(repo, base, 'patches/molly'):
            raise ValueError('molly scope needs a base without Molly patches')
        # The base may differ from the official Molly tree only in repository files.
        for path in changed(repo, MOLLY, base):
            if not repo_file(path):
                raise ValueError('base differs from the official Molly tree outside repository files: ' + path)
    else:
        lower = profile(repo, base)
        if not lower or not exists(repo, base, 'patches/molly'):
            raise ValueError('base has no Molly patches or profile')
        try:
            lower_signal, lower_port = lower['signal']['commit'], lower['port']
        except (KeyError, TypeError) as exc:
            raise ValueError('invalid base profile') from exc
        check_projection(repo, lower_signal, base, 'base')
        if scope == 'port' and lower_port is not None:
            raise ValueError('port scope needs a base without a port')

    try:
        config = profile(repo, head)
        source = config['signal']['commit']
        pins_ok = config['molly']['target'] == MOLLY and config['molly']['tree'] == MOLLY_TREE
        port_ok = (config['port'] is None) == (scope == 'molly') if scope != 'change' else True
        signal_ok = source == OLD if scope == 'molly' else True
    except (KeyError, TypeError) as exc:
        raise ValueError('invalid profile') from exc
    if not (pins_ok and port_ok and signal_ok):
        raise ValueError('invalid profile pins')
    check_projection(repo, source, head, 'head')
    if scope != 'change':
        probe = subprocess.run(['git', '-C', str(repo), 'rev-parse', head + ':patches/molly'],
                               stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
        if probe.returncode or probe.stdout.decode().strip() != FOREST:
            raise ValueError('Molly forest does not match the official snapshot')
    return {'scope': scope, 'profile_present': True, 'port_present': config['port'] is not None}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--scope', choices=SCOPES, help='override the scope detected from the trees')
    parser.add_argument('--base', required=True)
    parser.add_argument('--head', default='HEAD')
    parser.add_argument('--repo', default='.')
    args = parser.parse_args()
    try:
        print(json.dumps(check_scope(args.repo, args.scope, args.base, args.head), sort_keys=True))
    except (ValueError, subprocess.CalledProcessError) as exc:
        print('scope check failed: ' + str(exc), file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
