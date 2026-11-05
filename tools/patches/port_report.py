#!/usr/bin/env python3
"""Render physical Git diff measurements and explicitly supplied hunk evidence."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile

import line_origin
import release

TYPES = {'conflict_resolution', 'signal_api_compile_fix', 'behavior_preservation', 'behavior_addition'}
HUNK = re.compile(rb'^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@')
OLD = 'c47a74ba0961903eeaeda6d22347a990056c3e83'
MOLLY = '0e202dda90785771ff8c87526eaaf30c655f54f3'


def git(*args):
    return subprocess.run(['git', *args], check=True, stdout=subprocess.PIPE).stdout


def numstat(old, new, path=None):
    records = {}
    pathspec = ('--', ':(literal)' + path) if path is not None else ()
    for row in filter(None, git('diff', '--no-renames', '--numstat', '-z', old, new, *pathspec).split(b'\0')):
        added, deleted, path = row.split(b'\t', 2)
        records[path] = None if added == b'-' else (int(added), int(deleted))
    return records


def modes(old, new, path=None):
    pathspec = ('--', ':(literal)' + path) if path is not None else ()
    fields = git('diff', '--no-renames', '--raw', '--no-abbrev', '-z', old, new, *pathspec).split(b'\0')
    result = {}
    for i in range(0, len(fields) - 1, 2):
        if not fields[i]:
            continue
        header = fields[i].split()
        result[fields[i+1]] = (header[0][1:].decode(), header[1].decode(), header[2], header[3])
    return result


def whole_file_deletions(before, target, molly, annotations):
    """Inventory deleted paths using Git's own binary and line classification."""
    if not isinstance(annotations, dict):
        raise ValueError('deletion annotations must be an object')
    stats, changes = numstat(before, target), modes(before, target)
    molly_paths = set()
    for entry in filter(None, git('ls-tree', '-r', '-z', '--name-only', molly).split(b'\0')):
        molly_paths.add(entry)
    deleted = {os.fsdecode(path): (path, values) for path, values in changes.items()
               if values[1] == '000000'}
    allowed = {'classification', 'reason', 'molly_equivalent', 'evidence', 'caveat'}
    for path, item in annotations.items():
        if path not in deleted:
            raise ValueError('stale deletion annotation: ' + str(path))
        if not isinstance(item, dict) or set(item) - allowed or 'classification' not in item or any(
                not isinstance(value, str) or not value.strip() for value in item.values()):
            raise ValueError('invalid deletion annotation: ' + path)
    result = []
    for name, (path, (old_mode, _, _, _)) in sorted(deleted.items()):
        count = stats[path]
        if count is not None and count[0] != 0:
            raise ValueError('invalid deletion numstat: ' + name)
        result.append((name, old_mode, None if count is None else count[1], path in molly_paths,
                       annotations.get(name, {})))
    return result


def deletion_report(inventory):
    text = [row for row in inventory if row[2] is not None]
    absent = [row for row in inventory if not row[3]]
    absent_text = [row for row in absent if row[2] is not None]
    classes = {}
    for row in inventory:
        name = row[4].get('classification', 'unclassified')
        totals_by_class = classes.setdefault(name, [0, 0, 0, 0])
        totals_by_class[0] += 1
        totals_by_class[1 if row[2] is not None else 2] += 1
        totals_by_class[3] += row[2] or 0
    output = ['', '## Whole-file deletions from the mechanical merge', '',
              f'{len(inventory)} paths: {len(text)} text files, {len(inventory)-len(text)} binary files, {sum(row[2] for row in text)} deleted text lines.',
              f'Scenario excluding paths absent at the same path in Molly: {len(inventory)-len(absent)} of {len(inventory)} deleted paths and {sum(row[2] for row in text)-sum(row[2] for row in absent_text)} of {sum(row[2] for row in text)} deleted text lines remain; {len(absent)-len(absent_text)} of {len(inventory)-len(text)} binary paths are excluded. This is a tree-membership scenario, not authorship, justification, or proof of behavior preservation.',
              'Partial-file exclusions and deletion necessity remain unmeasured unless separately evidenced.', '',
              '| Classification | Paths | Text | Binary | Deleted text lines |', '|---|---:|---:|---:|---:|']
    for name, counts in sorted(classes.items()):
        output.append('| ' + ' | '.join(str(x).replace('\n', ' ').replace('|', '\\|') for x in (name, *counts)) + ' |')
    output += ['', '| Path | Old mode | Deleted text lines | Binary | Present in Molly | Classification | Reason | Molly equivalent | Evidence | Caveat |',
              '|---|---|---:|---|---|---|---|---|---|---|']
    for path, mode, count, present, item in inventory:
        fields = (path, mode, '-' if count is None else count, 'yes' if count is None else 'no',
                  'yes' if present else 'no', *(item.get(k, 'unclassified' if k == 'classification' else 'unmeasured')
                                                   for k in ('classification', 'reason', 'molly_equivalent', 'evidence', 'caveat')))
        output.append('| ' + ' | '.join(str(x).replace('\n', ' ').replace('|', '\\|') for x in fields) + ' |')
    return output


def require_tree(value):
    if not isinstance(value, str) or not re.fullmatch('[0-9a-f]{40}', value):
        raise ValueError('expected an immutable full tree ID')
    if git('cat-file', '-t', value).strip() != b'tree':
        raise ValueError('expected a tree object: ' + value)


def parse_hunks(patch):
    """Read actual hunk body counts, including lines beginning +++ and ---."""
    lines = patch.splitlines(keepends=True)
    result = []
    i = 0
    while i < len(lines):
        match = HUNK.match(lines[i])
        if not match:
            i += 1
            continue
        old_left = int(match.group(2) or 1)
        new_left = int(match.group(4) or 1)
        body = [lines[i]]
        added = deleted = 0
        i += 1
        while old_left or new_left:
            if i >= len(lines):
                raise ValueError('truncated hunk')
            line = lines[i]
            body.append(line)
            if line.startswith(b' '):
                old_left -= 1; new_left -= 1
            elif line.startswith(b'+'):
                new_left -= 1; added += 1
            elif line.startswith(b'-'):
                old_left -= 1; deleted += 1
            elif not line.startswith(b'\\'):
                raise ValueError('invalid hunk line')
            if old_left < 0 or new_left < 0:
                raise ValueError('hunk exceeds declared length')
            i += 1
        result.append((added, deleted, b''.join(body)))
    return result


def hunk_identity(path, *parts):
    return hashlib.sha256(b'\0'.join((path, *parts))).hexdigest()[:20]


def text_hunks(path, patch):
    """Return report IDs and parsed bodies without changing their historical IDs."""
    parsed = parse_hunks(patch)
    contents = [body.split(b'\n', 1)[1] for _, _, body in parsed]
    occurrences = {}
    result = []
    for (added, deleted, body), content in zip(parsed, contents):
        occurrences[content] = occurrences.get(content, 0) + 1
        key = hunk_identity(path, b'text', content, str(contents.count(content)).encode(),
                            str(occurrences[content]).encode())
        result.append((added, deleted, body, key, 'text'))
    return result


def totals(records):
    values = [v for v in records.values() if v is not None]
    return sum(v[0] for v in values), sum(v[1] for v in values), sum(v is None for v in records.values())


def clean_contribution(source, mechanical):
    """Count only paths without any merge conflict, including partial conflicts."""
    tree, conflicts, _ = release.mechanical_merge('.', OLD, MOLLY, source)
    if tree != mechanical:
        raise ValueError('mechanical merge tree differs from pinned tree')
    paths = {os.fsencode(path) for path in conflicts}
    return totals({p: count for p, count in numstat(source, mechanical).items() if p not in paths}), sorted(conflicts)


def expand_groups(evidence):
    """Expand explicitly enumerated descriptions; gates stay individual."""
    rows = evidence.get('hunks', {})
    groups = evidence.get('groups', {})
    if not isinstance(rows, dict) or not isinstance(groups, dict):
        raise ValueError('hunks and groups metadata must be objects')
    result = {key: dict(value) for key, value in rows.items()}
    assigned = set()
    allowed = {'type', 'reason', 'molly_equivalent', 'searched_branches'}
    for name, group in groups.items():
        ids = group.get('hunks')
        description = group.get('description')
        if not isinstance(ids, list) or not ids or not isinstance(description, dict) or not description or set(description) - allowed:
            raise ValueError('invalid group ' + name)
        for key in ids:
            if not isinstance(key, str) or key in assigned:
                raise ValueError('overlapping group hunk ' + str(key))
            assigned.add(key)
            item = result.setdefault(key, {})
            for field, value in description.items():
                if field in item:
                    raise ValueError('overlapping group field ' + key + ': ' + field)
                item[field] = value
    return result


def action_history(evidence):
    """Replay versioned one-file patches on pinned starting trees in private indexes."""
    chains = evidence.get('action_chains', [])
    if not isinstance(chains, list):
        raise ValueError('action_chains must be a list')
    output = []
    with tempfile.TemporaryDirectory(prefix='molly-report-actions-') as tmp:
        for n, chain in enumerate(chains):
            start = chain['start_tree']
            require_tree(start)
            index = str(Path(tmp) / str(n))
            env = dict(os.environ, GIT_INDEX_FILE=index)
            def run(*args):
                return subprocess.run(['git', *args], env=env, check=True, stdout=subprocess.PIPE).stdout
            run('read-tree', start)
            for action in chain['actions']:
                path = action['path']
                if not isinstance(path, str) or not path or path.startswith('/') or '..' in Path(path).parts:
                    raise ValueError('invalid action path')
                before = run('rev-parse', ':' + path).strip().decode()
                if before != action['input_blob']:
                    raise ValueError('stale action input: ' + path)
                patch = release.inside(Path('.').resolve(), action['patch'], 'action patch')
                git('ls-files', '--error-unmatch', '--', action['patch'])
                previous = run('write-tree').strip().decode()
                run('apply', '--cached', '--binary', '--whitespace=nowarn', str(patch))
                after = run('rev-parse', ':' + path).strip().decode()
                current = run('write-tree').strip().decode()
                changed = [x for x in run('diff', '--no-renames', '--name-only', '-z', previous, current).split(b'\0') if x]
                if after != action['output_blob'] or changed != [os.fsencode(path)]:
                    raise ValueError('action output or paths differ: ' + path)
                fields = [action[k] for k in ('gate_input', 'gate', 'outcome', 'evidence')]
                if any(not isinstance(x, str) or not x.strip() or x == 'unmeasured' for x in fields):
                    raise ValueError('incomplete action evidence: ' + path)
                output.append((path, before, after, action['patch'], *fields))
    return output


def render(config, evidence, ready=False, origin=None):
    port = config['port']
    before, target = port['mechanical_tree'], config['target_tree']
    baseline_before = evidence['baseline_mechanical_tree']
    baseline_target = evidence['baseline_target_tree']
    source = config['signal']['commit']
    for tree in (before, target, baseline_before, baseline_target):
        require_tree(tree)
    if baseline_before != before:
        raise ValueError('mechanical tree differs from immutable baseline')
    if not isinstance(source, str) or not re.fullmatch('[0-9a-f]{40}', source) or git('cat-file', '-t', source).strip() != b'commit':
        raise ValueError('expected an immutable full Signal commit ID')
    rows = expand_groups(evidence)
    actions = action_history(evidence)
    seen = set()
    stats = numstat(before, target)
    file_modes = modes(before, target)
    if set(file_modes) != set(stats):
        raise ValueError('raw diff paths do not match numstat')
    output = ['# Port report', '']
    if origin:
        output += origin.rstrip('\n').split('\n') + ['']
    output += ['## Raw physical Git diffs', '',
               'Physical Git diff lines; counts do not establish authorship or necessity. Comparisons with the '
               'mechanical tree include its conflict markers and both sides of every conflict, so they are '
               'bookkeeping for reviewers, not a measure of the AI-assisted compatibility changes.', '']
    (clean_a, clean_d, clean_b), conflicts = clean_contribution(source, before)
    output.append(f'- Clean merge contribution (conservative whole-file count): {clean_a} added, {clean_d} deleted, {clean_a+clean_d} total text lines; {clean_b} binary paths (`{source}` → `{before}`).')
    comparisons = [('Molly patchset', OLD, MOLLY), ('Full Signal to mechanical comparison', source, before),
                        ('AI-assisted compatibility changes before minimization', baseline_before, baseline_target),
                        ('Compared with Molly before minimization', MOLLY, baseline_target)]
    counts = {}
    for label, a, b in comparisons:
        adds, dels, binary = counts[label] = totals(numstat(a, b))
        output.append(f'- {label}: {adds} added, {dels} deleted, {adds+dels} total text lines; {binary} binary paths (`{a}` → `{b}`).')
    a, d, _ = counts['AI-assisted compatibility changes before minimization']
    ma, md, _ = counts['Molly patchset']
    ca, cd, cb = totals(stats)
    cm, cn, cmb = totals(numstat(MOLLY, target))
    current = f'Current mechanical comparison: {ca} added, {cd} deleted, {ca+cd} total, {cb} binary; current Molly comparison: {cm} added, {cn} deleted, {cm+cn} total, {cmb} binary.'
    after = 'After minimization: final.' if evidence.get('minimization_complete') else 'After minimization: unmeasured.'
    output += deletion_report(whole_file_deletions(before, target, MOLLY, evidence.get('deletions', {})))
    output += ['', f'Compatibility share: {a+d} / {ma+md} physical added plus deleted text lines = {(a+d)/(ma+md)*100:.3f}%.' if ma+md else 'Compatibility share: undefined (zero denominator).',
               current,
               f'Current gross physical comparison relative to original Molly patchset: {ca+cd} / {ma+md} added plus deleted text lines = {(ca+cd)/(ma+md)*100:.3f}%.' if ma+md else 'Current gross physical comparison relative to original Molly patchset: undefined (zero denominator).',
               'Gross physical counts include deletions and conflict bookkeeping; they do not establish authorship or necessity.',
               after, '', 'Molly exclusions, including partial-file exclusions: unmeasured.', '',
               f'Clean merge excludes all {len(conflicts)} conflicted paths, including clean portions within them: ' + (', '.join('`' + p.replace('`', '\\`') + '`' for p in conflicts) if conflicts else 'none') + '.', '',
               '| Hunk ID | Path | Kind | Old mode | New mode | Added | Deleted | Type | Reason | Molly equivalent | Revert gate and result |',
               '|---|---|---|---|---|---:|---:|---|---|---|---|']
    summary_position = len(output) - 2
    semantic = {name: [0, 0, 0] for name in ('conflict_resolution', 'signal_api_compile_fix', 'behavior_preservation', 'unmeasured')}
    if any(item.get('type') == 'behavior_addition' for item in rows.values()):
        semantic['behavior_addition'] = [0, 0, 0]
    structural = {name: [0, 0, 0] for name in ('text', 'binary', 'mode')}
    for path, expected in sorted(stats.items()):
        display = os.fsdecode(path).replace('|', '\\|').replace('\n', '\\n')
        old_mode, new_mode, old_blob, new_blob = file_modes[path]
        if expected is None:
            key = hunk_identity(path, b'binary', old_blob, new_blob, old_mode.encode(), new_mode.encode())
            hunks = [(0, 0, b'binary', key, 'binary')]
        else:
            patch = git('diff', '--no-ext-diff', '--no-renames', '--unified=3', before, target, '--', os.fsdecode(path))
            hunks = text_hunks(path, patch)
            if (sum(h[0] for h in hunks), sum(h[1] for h in hunks)) != expected:
                raise ValueError('hunk counts do not match numstat for ' + display)
        if expected is not None and old_mode != new_mode:
            key = hunk_identity(path, b'mode', old_mode.encode(), new_mode.encode())
            hunks.append((0, 0, b'mode', key, 'mode'))
        for added, deleted, _, key, kind in hunks:
            if key in seen: raise ValueError('duplicate hunk ID ' + key)
            seen.add(key); item = rows.get(key, {})
            category = item.get('type', '')
            if category and category not in TYPES: raise ValueError('invalid hunk type ' + key)
            if ready and (category not in TYPES or any(not item.get(k) or item[k] == 'unmeasured' for k in ('reason', 'molly_equivalent', 'searched_branches', 'revert_gate', 'gate_input', 'outcome', 'evidence'))):
                raise ValueError('missing evidence for ' + key)
            for summary in (semantic[category or 'unmeasured'], structural[kind]):
                summary[0] += 1
                summary[1] += added
                summary[2] += deleted
            fields = [key, display, kind, old_mode, new_mode, str(added), str(deleted), category or 'unmeasured', item.get('reason', 'unmeasured'), item.get('molly_equivalent', 'unmeasured') + ' (searched: ' + item.get('searched_branches', 'unmeasured') + ')', (item.get('revert_gate', 'unmeasured') + ' (input: ' + item.get('gate_input', 'unmeasured') + '): ' + item.get('outcome', 'unmeasured') + ' (evidence: ' + item.get('evidence', 'unmeasured') + ')')]
            output.append('| ' + ' | '.join(str(f).replace('\n', ' ').replace('|', '\\|') for f in fields) + ' |')
    summary_output = ['## Hunk summaries', '',
                      'Semantic type and structural kind are alternative breakdowns of the same rows; do not add them together. Binary and mode rows have zero physical text lines. Mode rows include creation and deletion metadata, so their count is not a count of permission changes.', '']
    for title, summary in (('Semantic type', semantic), ('Structural kind', structural)):
        summary_output += [f'| {title} | Rows | Added | Deleted |', '|---|---:|---:|---:|']
        summary_output += [f'| {name} | {count} | {added} | {deleted} |'
                           for name, (count, added, deleted) in summary.items()]
        summary_output.append('')
    output[summary_position:summary_position] = summary_output
    stale = set(rows) - seen
    if stale: raise ValueError('stale or overlapping metadata: ' + ', '.join(sorted(stale)))
    if ready and (not evidence.get('exclusions_complete') or not evidence.get('minimization_complete')):
        raise ValueError('Molly exclusion or minimization evidence is incomplete')
    if actions:
        output += ['', '## Measured minimization actions', '', '| Path | Input blob | Output blob | Versioned patch | Gate input | Gate | Outcome | Evidence |', '|---|---|---|---|---|---|---|---|']
        for action in actions:
            output.append('| ' + ' | '.join(str(x).replace('\n', ' ').replace('|', '\\|') for x in action) + ' |')
    return '\n'.join(output) + '\n'


def import_start_trees(config, evidence):
    """Load the historical start trees of measured action chains from their committed evidence bundle.

    Minimization evidence replays patches from trees that a fresh clone does not otherwise contain. The bundle
    holds one chain of synthetic commits whose trees are those start trees; its prerequisites are commits the
    verified source bundles already provide.
    """
    record = evidence.get('start_tree_bundle')
    if not record:
        return
    import verify_source_bundles
    root = Path('.').resolve()
    series_dir = root / Path(config['port']['series']).parent
    verify_source_bundles.import_bundle(root, root, series_dir, record['path'], record['sha256'], record['bytes'],
                                        record['ref'], record['commit'], record['prerequisites'])
    trees = set(git('log', '--format=%T', record['commit']).decode().split())
    missing = {chain['start_tree'] for chain in evidence.get('action_chains', [])} - trees - {evidence['baseline_target_tree']}
    if missing:
        raise ValueError('start tree bundle lacks action chain trees: ' + ', '.join(sorted(missing)))


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--config', default='molly-build.json'); p.add_argument('--metadata', default='patches/ports/signal-8.33.2/report-metadata.json')
    p.add_argument('--output', default='docs/PORT-REPORT.md'); p.add_argument('--check', action='store_true'); p.add_argument('--ready', action='store_true')
    args = p.parse_args()
    try:
        config = json.loads(Path(args.config).read_text())
        evidence = json.loads(Path(args.metadata).read_text())
        import_start_trees(config, evidence)
        with tempfile.TemporaryDirectory(prefix='molly-report-origin-') as objects:
            series_dir = Path(config['port']['series']).parent
            repo = line_origin.Repo('.', objects)
            origin = line_origin.markdown(line_origin.port(repo, series_dir))
        result = render(config, evidence, args.ready, origin)
        if args.check:
            if Path(args.output).read_text() != result: raise ValueError('port report is stale')
        else: Path(args.output).write_text(result)
    except (ValueError, KeyError, OSError, subprocess.CalledProcessError) as exc:
        print('port report failed: ' + str(exc), file=sys.stderr); return 1
    return 0


if __name__ == '__main__': sys.exit(main())
