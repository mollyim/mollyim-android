#!/usr/bin/env python3
"""Materialize pinned report trees from committed profiles and resolution mailboxes."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile

import forest
import git_utils
import release
import translation_stage
import verify_complete

SIGNAL_URL = 'https://github.com/signalapp/Signal-Android.git'
MOLLY_URL = 'https://github.com/mollyim/mollyim-android.git'


def blob(repo, commit, path):
    return git_utils.git(repo, 'show', f'{commit}:{path}', binary=True).stdout


def materialize(repo, config, provenance, root, index):
    port = config['port']
    for field in ('origin_base', 'origin_target', 'signal', 'mechanical_tree', 'target_tree'):
        release.require_sha(provenance[field], field)
    if (provenance['origin_target'], provenance['signal'], provenance['mechanical_tree'], provenance['target_tree']) != (
            config['molly']['target'], config['signal']['commit'], port['mechanical_tree'], config['target_tree']):
        raise ValueError('profile and provenance pins differ')
    for sha, url in ((provenance['origin_base'], SIGNAL_URL), (provenance['signal'], SIGNAL_URL),
                     (provenance['origin_target'], MOLLY_URL)):
        if git_utils.git(repo, 'cat-file', '-e', sha + '^{commit}', check=False).returncode:
            git_utils.git(repo, 'fetch', '--no-tags', url, sha)
        git_utils.git(repo, 'cat-file', '-e', sha + '^{commit}')
    mechanical, _, _ = release.mechanical_merge(repo, provenance['origin_base'], provenance['origin_target'], provenance['signal'])
    if mechanical != port['mechanical_tree']:
        raise ValueError('mechanical merge tree differs from pinned tree')
    records = release.resolution_series(root, port['series'])
    hashes = provenance['mailbox_sha256']
    series_dir = release.inside(root, port['series'], 'resolution series').parent
    for patch, metadata in records:
        relative = patch.relative_to(series_dir).as_posix()
        if hashes.get(relative) != metadata['sha256'] or hashlib.sha256(patch.read_bytes()).hexdigest() != metadata['sha256']:
            raise ValueError('mailbox hash mismatch: ' + relative)
    signal_tree = forest.git(repo, 'rev-parse', provenance['signal'] + '^{tree}')
    if any(m['translation']['signal_tree'] != signal_tree for _, m in records if 'translation' in m):
        raise ValueError('translation stage Signal tree does not match the pinned Signal commit')
    env = forest.clean_git_env()
    git_utils.git(repo, 'read-tree', mechanical, index_file=index)
    for patch, metadata in records:
        if 'translation' in metadata:
            translation_stage.generate_index(repo, metadata['translation'], env, str(index))
        elif 'deletion' in metadata:
            stage = metadata['deletion']
            before = git_utils.git(repo, 'write-tree', index_file=index).stdout.strip()
            if before != stage['before_tree']:
                raise ValueError('deletion before-tree mismatch')
            entries = verify_complete.tree_entries(repo, before)
            for item in stage['files']:
                expected = (item['mode'].encode(), b'blob', item['blob'].encode())
                if entries.get(os.fsencode(item['path'])) != expected:
                    raise ValueError('deletion preimage mismatch: ' + item['path'])
                git_utils.git(repo, '--literal-pathspecs', 'update-index', '--force-remove', '--', item['path'], index_file=index)
            if git_utils.git(repo, 'write-tree', index_file=index).stdout.strip() != stage['after_tree']:
                raise ValueError('deletion after-tree mismatch')
        else:
            message = index.parent / 'message'
            diff = index.parent / 'diff'
            with patch.open('rb') as source:
                result = subprocess.run(['git', '-C', str(repo), 'mailinfo', str(message), str(diff)], stdin=source,
                                        stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            if result.returncode:
                raise ValueError('git mailinfo failed: ' + result.stderr.decode(errors='replace'))
            git_utils.git(repo, 'apply', '--cached', '--binary', '--whitespace=nowarn', str(diff), index_file=index)
    actual = git_utils.git(repo, 'write-tree', index_file=index).stdout.strip()
    if actual != config['target_tree']:
        raise ValueError(f'reconstructed tree differs: expected {config["target_tree"]}, got {actual}')
    return actual


def prepare(repo):
    repo = Path(repo).resolve()
    config = release.load_config(repo / 'molly-build.json')
    series = release.inside(repo, config['port']['series'], 'resolution series')
    metadata = json.loads((series.parent / 'report-metadata.json').read_text())
    historical = release.require_sha(metadata['baseline_profile_commit'], 'baseline profile commit')
    git_utils.git(repo, 'cat-file', '-e', historical + '^{commit}')
    provenance_path = (series.parent / 'provenance.json').relative_to(repo).as_posix()
    current_provenance = json.loads((repo / provenance_path).read_text())
    historical_config = json.loads(blob(repo, historical, 'molly-build.json'))
    historical_series = historical_config['port']['series']
    historical_provenance_path = str(Path(historical_series).parent / 'provenance.json')
    historical_provenance = json.loads(blob(repo, historical, historical_provenance_path))
    if historical_config['target_tree'] != metadata['baseline_target_tree'] or historical_config['port']['mechanical_tree'] != metadata['baseline_mechanical_tree']:
        raise ValueError('historical profile does not match immutable report baseline')
    with tempfile.TemporaryDirectory(prefix='molly-report-') as tmp:
        root = Path(tmp)
        series_path = release.inside(root, historical_series, 'historical series')
        series_path.parent.mkdir(parents=True, exist_ok=True)
        series_path.write_bytes(blob(repo, historical, historical_series))
        for entry in forest.lines(series_path):
            path = release.inside(series_path.parent, entry, 'historical mailbox')
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(blob(repo, historical, str(Path(historical_series).parent / entry)))
        index = root / 'index'
        materialize(repo, historical_config, historical_provenance, root, index)
        index.unlink(missing_ok=True)
        materialize(repo, config, current_provenance, repo, index)
    print('Port report trees prepared and verified')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', default='.')
    args = parser.parse_args()
    try:
        prepare(args.repo)
    except (forest.ForestError, git_utils.GitError, OSError, ValueError, KeyError, subprocess.SubprocessError) as exc:
        print('port report preparation failed: ' + str(exc), file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
