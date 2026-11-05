#!/usr/bin/env python3
"""Re-export a current resolution series using already replayed source commits."""
import argparse
import copy
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile

import forest
import git_utils
import prepare_port_report
import release
import translation_stage
import verify_complete
import verify_source_bundles


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write_json(path, data):
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + '\n')


def pairs(values, label):
    result = {}
    for value in values:
        if '=' not in value:
            raise ValueError(f'{label} must be OLD=NEW')
        old, new = value.split('=', 1)
        if not old or not new or old in result:
            raise ValueError(f'invalid or duplicate {label}: {old}')
        result[old] = new
    return result


def reexport(source, output, checkpoints, replacements):
    source, output = Path(source).resolve(strict=True), Path(output).absolute()
    common = Path(git_utils.git(source, 'rev-parse', '--path-format=absolute', '--git-common-dir').stdout.strip()).resolve()
    resolved_output = output.resolve()
    if resolved_output.is_relative_to(source) or resolved_output.is_relative_to(common):
        raise ValueError('output must be outside source and its Git common directory')
    if output.exists() or output.is_symlink():
        raise ValueError('output must not exist')
    config = release.load_config(source / 'molly-build.json')
    series_name = config['port']['series']
    series = release.inside(source, series_name, 'resolution series')
    port = series.parent
    provenance = json.loads((port / 'provenance.json').read_text())
    records = release.resolution_series(source, series_name)
    names = [p.relative_to(port).as_posix() for p, _ in records]
    layers = provenance['layers']
    if [p for layer in layers for p in layer['patches']] != names:
        raise ValueError('layer records do not match series order')
    commits = {layer['source_commit'] for layer in layers}
    if not set(checkpoints) <= commits:
        raise ValueError('unknown checkpoint mapping')
    for old, new in checkpoints.items():
        release.require_sha(old, 'old checkpoint')
        release.require_sha(new, 'new checkpoint')
    if not set(replacements) <= {name for name in names if name.endswith('.patch')}:
        raise ValueError('replacement must identify an existing mailbox')
    output.mkdir(parents=True)
    previous_tmp = tempfile.tempdir
    try:
        git_utils.git(output, 'init', '-q')
        tempfile.tempdir = str(output / '.git')
        (output / '.git/objects/info/alternates').write_text(str(common / 'objects') + '\n')
        out_series = release.inside(output, series_name, 'output series')
        out_port = out_series.parent
        out_port.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(series, out_series)
        shutil.copyfile(source / 'molly-build.json', output / 'molly-build.json')
        for path, _ in records:
            dest = out_port / path.relative_to(port)
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(path, dest)
        bundles = [provenance['source_bundle_base']['path']] + [x['source_bundle'] for x in layers if 'source_bundle' in x]
        for name in bundles:
            original = verify_source_bundles.bundle_path(source, port, name)
            dest = release.inside(output, (out_port / name).relative_to(output).as_posix(), 'output bundle')
            dest.parent.mkdir(parents=True, exist_ok=True)
            os.link(original, dest)
        write_json(out_port / 'provenance.json', provenance)
        # Both validators operate on an isolated repository and private index.
        verify_source_bundles.verify(output)
        index = output / '.git/reexport-index'
        prepare_port_report.materialize(output, config, provenance, output, index)
        index.unlink(missing_ok=True)
        git_utils.git(output, 'read-tree', provenance['mechanical_tree'], index_file=index)
        env = forest.clean_git_env()
        mapping = lambda sha: checkpoints.get(sha, sha)
        def tree():
            return git_utils.git(output, 'write-tree', index_file=index).stdout.strip()
        def checkpoint_tree(commit):
            return verify_source_bundles.commit_info(output, commit)[0]
        def replace_header(raw, old, new):
            # Restrict substitutions to the message, preserving diff bytes verbatim.
            head, sep, diff = raw.partition(b'\ndiff --git ')
            head = re.sub(rb'(?m)^(Source-Commit:|Based-On-Source-Commit:) ' + old.encode() + rb'$', lambda m: m.group(1) + b' ' + new.encode(), head)
            return head + sep + diff
        old_initial = provenance['initial_source']
        provenance['initial_source'] = mapping(old_initial)
        previous_old = old_initial
        for layer in layers:
            old_commit = layer['source_commit']
            new_commit = mapping(old_commit)
            release.require_sha(new_commit, 'new source commit')
            before = tree()
            layer['before_tree'] = before
            layer['source_commit'] = new_commit
            if not layer['id'].startswith('initial/') and 'description' in layer:
                description = layer['description']
                matches = list(re.finditer(r'(?m)^Based-On-Source-Commit:[^\r\n]*', description))
                expected = 'Based-On-Source-Commit: ' + previous_old
                if len(matches) > 1 or any(m.group() != expected for m in matches):
                    raise ValueError('invalid description parent identity: ' + layer['id'])
                if matches:
                    match = matches[0]
                    layer['description'] = description[:match.start()] + 'Based-On-Source-Commit: ' + mapping(previous_old) + description[match.end():]
            for name in layer['patches']:
                path = out_port / name
                original = next(meta for p, meta in records if p.relative_to(port).as_posix() == name)
                if original['source_commit_shas'] != [old_commit]:
                    raise ValueError('record source differs from layer checkpoint: ' + name)
                if 'deletion' in original or 'translation' in original:
                    key = 'deletion' if 'deletion' in original else 'translation'
                    data = copy.deepcopy(original[key])
                    data['source_commit'] = new_commit
                    data['before_tree'] = tree()
                    if key == 'deletion':
                        entries = verify_complete.tree_entries(output, data['before_tree'])
                        for item in data['files']:
                            expected = (item['mode'].encode(), b'blob', item['blob'].encode())
                            if entries.get(os.fsencode(item['path'])) != expected:
                                raise ValueError('deletion preimage mismatch: ' + item['path'])
                            git_utils.git(output, '--literal-pathspecs', 'update-index', '--force-remove', '--', item['path'], index_file=index)
                        data['after_tree'] = tree()
                    else:
                        data['after_tree'] = translation_stage.generate_index(output, data, env, str(index), dry_run=True)
                    write_json(path, data)
                else:
                    if 'source_bundle' in layer:
                        parent = mapping(previous_old)
                        raw = git_utils.git(output, 'format-patch', '--stdout', '--abbrev=8', '-1', new_commit, binary=True).stdout
                        head, separator, body = raw.partition(b'\n---\n')
                        if not separator or re.search(rb'(?m)^Source-Commits?:', head):
                            raise ValueError('invalid follow-up commit message: ' + name)
                        based = list(re.finditer(rb'(?m)^Based-On-Source-Commit: [^\n]*$', head))
                        trailers = b'Based-On-Source-Commit: ' + parent.encode() + b'\nSource-Commit: ' + new_commit.encode()
                        if len(based) > 1:
                            raise ValueError('duplicate follow-up parent trailer: ' + name)
                        if based:
                            match = based[0]
                            head = head[:match.start()] + trailers + head[match.end():]
                        else:
                            head += b'\n\n' + trailers + b'\n'
                        raw = head + separator + body
                        if name in replacements and Path(replacements[name]).read_bytes() != raw:
                            raise ValueError('replacement differs from canonical follow-up: ' + name)
                    else:
                        raw = Path(replacements[name]).read_bytes() if name in replacements else path.read_bytes()
                        raw = replace_header(raw, old_commit, new_commit)
                        if not layer['id'].startswith('initial/'):
                            raw = replace_header(raw, previous_old, mapping(previous_old))
                    path.write_bytes(raw)
                    meta = forest.mailbox(path, name)
                    if meta['source_commit_shas'] != [new_commit]:
                        raise ValueError('mailbox source commit mismatch: ' + name)
                    msg, diff = output / '.git/reexport-message', output / '.git/reexport-diff'
                    with path.open('rb') as f:
                        proc = subprocess.run(['git', '-C', str(output), 'mailinfo', str(msg), str(diff)], stdin=f, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
                    if proc.returncode:
                        raise ValueError('git mailinfo failed: ' + name)
                    git_utils.git(output, 'apply', '--cached', '--binary', '--whitespace=nowarn', str(diff), index_file=index)
                provenance['mailbox_sha256'][name] = digest(path)
            layer['after_tree'] = tree()
            if not layer['id'].startswith('initial/'):
                if checkpoint_tree(new_commit) != tree():
                    raise ValueError('follow-up tree differs from checkpoint: ' + layer['id'])
                layer['mailbox_bytes'] = sum((out_port / x).stat().st_size for x in layer['patches']) if 'source_bundle' in layer else layer['mailbox_bytes']
                previous_old = old_commit
        initial_layers = [x for x in layers if x['id'].startswith('initial/')]
        if not initial_layers or checkpoint_tree(provenance['initial_source']) != initial_layers[-1]['after_tree']:
            raise ValueError('initial tree differs from checkpoint')
        provenance['target_tree'] = config['target_tree'] = tree()
        base = provenance['source_bundle_base']
        base['source_commit'] = mapping(base['source_commit'])
        base['source_tree'] = checkpoint_tree(base['source_commit'])
        def bundle(path, ref, commit, prerequisites):
            git_utils.git(output, 'update-ref', ref, commit)
            path.unlink()
            git_utils.git(output, 'bundle', 'create', str(path), ref, *['^' + x for x in prerequisites])
            return digest(path), path.stat().st_size
        base['sha256'], base['bytes'] = bundle(out_port / base['path'], base['ref'], base['source_commit'], base['prerequisites'])
        for layer in layers:
            if 'source_bundle' in layer:
                layer['source_bundle_prerequisite'] = mapping(layer['source_bundle_prerequisite'])
                ref = 'refs/heads/source-checkpoint/' + layer['id'].split('/')[-1]
                layer['source_bundle_sha256'], layer['source_bundle_bytes'] = bundle(out_port / layer['source_bundle'], ref, layer['source_commit'], [layer['source_bundle_prerequisite']])
        write_json(out_port / 'provenance.json', provenance)
        write_json(output / 'molly-build.json', config)
        verify_source_bundles.verify(output)
        index.unlink(missing_ok=True)
        prepare_port_report.materialize(output, config, provenance, output, index)
        index.unlink(missing_ok=True)
    except BaseException:
        shutil.rmtree(output)
        raise
    finally:
        tempfile.tempdir = previous_tmp


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', required=True)
    parser.add_argument('--output', required=True)
    parser.add_argument('--checkpoint', action='append', default=[], metavar='OLD=NEW')
    parser.add_argument('--replacement', action='append', default=[], metavar='SERIES_PATH=MAILBOX')
    args = parser.parse_args()
    try:
        reexport(args.repo, args.output, pairs(args.checkpoint, 'checkpoint'), pairs(args.replacement, 'replacement'))
    except (ValueError, KeyError, TypeError, OSError, forest.ForestError, git_utils.GitError, subprocess.SubprocessError) as exc:
        print('re-export failed: ' + str(exc), file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
