#!/usr/bin/env python3
"""Count the lines of a reconstructed source that come from none of its inputs.

The reconstructed tree is recorded as a synthetic merge whose parents are the inputs
(Molly's released source and the Signal release).
Git's combined diff then reports exactly two kinds of line:

* ``written``: lines present in no parent (combined-diff prefix of all ``+``);
* ``removed``: lines present in every parent but absent from the result (all ``-``).

Lines taken verbatim from any one parent, including either side of a merge conflict,
are not counted. Paths whose result equals one parent's version are omitted by Git.
Renames are detected with Git's default similarity threshold. The synthetic commit is
written to a private object directory; the repository is never modified.

``not_carried`` separately counts Molly's own non-blank lines (old Signal to Molly)
that no longer appear in the file at the same path. It is a textual comparison and
does not distinguish dropped code from code that was rewritten or moved elsewhere.
"""
import argparse
import collections
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile

CLASSES = ('production', 'translations', 'tests', 'generated', 'repository')
CLASS_NOTES = {
    'production': 'app and library source, default-locale resources and build scripts that ship in the APK',
    'translations': 'localized strings (res/values-<locale>/strings*.xml), generated or relocated',
    'tests': 'test, screenshot-test and benchmark source sets and benchmark modules',
    'generated': 'gradle/verification-metadata.xml dependency hashes',
    'repository': 'patch series, pipeline tools, documentation and CI',
}
TESTS = re.compile(r'(^|/)src/(test|androidTest|testShared|testFixtures|screenshotTest\w*|benchmark\w*)/'
                   r'|^(benchmark|microbenchmark|baseline-profile)/')
TRANSLATIONS = re.compile(r'(^|/)res/values-[a-z]{2,3}(-r[A-Z]{2,3})?/strings[^/]*\.xml$')
GENERATED = re.compile(r'^gradle/verification-metadata\.xml$')
REPOSITORY = re.compile(r'^(patches|tools|docs|\.github)/|^[^/]+\.md$|^(molly-build|native-ringrtc)\.json$')
FIXED_DATE = '@0 +0000'
STAGE = 'PR3 compatibility'


def classify(path):
    """Assign a path to exactly one reporting class."""
    if REPOSITORY.search(path):
        return 'repository'
    if TESTS.search(path):
        return 'tests'
    if TRANSLATIONS.search(path):
        return 'translations'
    if GENERATED.search(path):
        return 'generated'
    return 'production'


def unquote(raw):
    """Decode a C-style quoted Git path (core.quotePath=false still quotes specials)."""
    if not raw.startswith(b'"'):
        return raw.decode('utf-8', 'surrogateescape')
    body, out, i = raw[1:-1], bytearray(), 0
    escapes = {ord('n'): 10, ord('t'): 9, ord('"'): 34, ord('\\'): 92, ord('a'): 7, ord('b'): 8,
               ord('f'): 12, ord('r'): 13, ord('v'): 11}
    while i < len(body):
        if body[i] == 92 and i + 1 < len(body):
            if body[i + 1] in escapes:
                out.append(escapes[body[i + 1]]); i += 2; continue
            out.append(int(body[i + 1:i + 4], 8)); i += 4; continue
        out.append(body[i]); i += 1
    return bytes(out).decode('utf-8', 'surrogateescape')


class Repo:
    """Run read-only Git commands; synthetic commits go to a private object directory."""

    def __init__(self, path, objects):
        self.path = str(path)
        common = subprocess.run(['git', '-C', self.path, 'rev-parse', '--path-format=absolute', '--git-common-dir'],
                                check=True, stdout=subprocess.PIPE).stdout.decode().strip()
        env = {k: v for k, v in os.environ.items() if not k.startswith('GIT_')}
        env.update(GIT_OBJECT_DIRECTORY=str(objects), GIT_ALTERNATE_OBJECT_DIRECTORIES=os.path.join(common, 'objects'),
                   GIT_NO_LAZY_FETCH='1', GIT_OPTIONAL_LOCKS='0', GIT_CONFIG_NOSYSTEM='1', GIT_CONFIG_GLOBAL=os.devnull,
                   GIT_AUTHOR_NAME='line-origin', GIT_AUTHOR_EMAIL='line-origin@invalid', GIT_AUTHOR_DATE=FIXED_DATE,
                   GIT_COMMITTER_NAME='line-origin', GIT_COMMITTER_EMAIL='line-origin@invalid',
                   GIT_COMMITTER_DATE=FIXED_DATE)
        self.env = env

    def git(self, *args, data=None):
        result = subprocess.run(['git', '-C', self.path, '-c', 'core.quotePath=false', *args], input=data,
                                env=self.env, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        if result.returncode:
            raise ValueError(f'git {args[0]} failed: {result.stderr.decode(errors="replace").strip()}')
        return result.stdout

    def tree(self, revision):
        return self.git('rev-parse', '--verify', revision + '^{tree}').decode().strip()

    def commit(self, revision):
        return self.git('rev-parse', '--verify', revision + '^{commit}').decode().strip()


def combined(repo, tree, parents):
    """Return {path: [written, removed]} for a tree measured against all parents."""
    if len(parents) < 2:
        raise ValueError('a combined measurement needs at least two parents')
    args = ['commit-tree', repo.tree(tree)]
    for parent in parents:
        args += ['-p', repo.commit(parent)]
    synthetic = repo.git(*args, '-m', 'line-origin measurement').decode().strip()
    output = repo.git('diff-tree', '--cc', '-M', '-r', '--no-commit-id', '--no-ext-diff', '--no-textconv', synthetic)
    width, counts, path, in_hunk = len(parents), {}, None, False
    written, removed, hunk = b'+' * width, b'-' * width, b'@' * (width + 1) + b' '
    for line in output.split(b'\n'):
        if line.startswith((b'diff --cc ', b'diff --combined ')):
            path, in_hunk = unquote(line.split(b' ', 2)[2]), False
            counts.setdefault(path, [0, 0])
        elif line.startswith(hunk):
            in_hunk = True
        elif in_hunk and line[:width] == written:
            counts[path][0] += 1
        elif in_hunk and line[:width] == removed:
            counts[path][1] += 1
    return {p: c for p, c in counts.items() if c != [0, 0]}


def summarize(files):
    """Aggregate per-path counts into the fixed reporting classes."""
    result = {name: {'files': 0, 'written': 0, 'removed': 0} for name in CLASSES}
    for path, (written, removed) in files.items():
        row = result[classify(path)]
        row['files'] += 1; row['written'] += written; row['removed'] += removed
    return result


def physical(repo, before, after):
    """Physical Git diff by class between two trees, with renames: the lines a layer changes."""
    raw = repo.git('diff', '-M', '--numstat', '-z', '--no-ext-diff', '--no-textconv', before, after)
    fields, files, i = raw.split(b'\0'), {}, 0
    while i < len(fields) and fields[i]:
        added, deleted, path = fields[i].split(b'\t', 2)
        if not path:
            path, i = fields[i + 2], i + 3
        else:
            i += 1
        if added != b'-':
            files[path.decode('utf-8', 'surrogateescape')] = [int(added), int(deleted)]
    return summarize(files)


def molly_lines(repo, old, molly):
    """Molly's own non-blank added lines per resulting path (old Signal to Molly, with renames)."""
    output = repo.git('diff', '-M', '-U0', '--no-ext-diff', '--no-textconv', old, molly)
    result, path = collections.defaultdict(collections.Counter), None
    for line in output.split(b'\n'):
        if line.startswith(b'+++ '):
            target = line[4:]
            path = None if target == b'/dev/null' else unquote(target)[2:]
        elif line.startswith(b'diff --git '):
            path = None
        elif path is not None and line.startswith(b'+') and line[1:].strip():
            result[path][line[1:].strip()] += 1
    return result


def not_carried(repo, tree, lines):
    """Count Molly lines absent from the same path in a tree, by class."""
    present = set(unquote(p) for p in repo.git('ls-tree', '-r', '--name-only', tree).split(b'\n') if p)
    paths = sorted(p for p in lines if p in present and '\n' not in p)
    blobs = {}
    if paths:
        request = b''.join(f'{tree}:{p}\n'.encode('utf-8', 'surrogateescape') for p in paths)
        data, offset = repo.git('cat-file', '--batch', data=request), 0
        for p in paths:
            header_end = data.index(b'\n', offset)
            header = data[offset:header_end].split()
            if header[-1] == b'missing':
                offset = header_end + 1
                continue
            size = int(header[2])
            blobs[p] = data[header_end + 1:header_end + 1 + size]
            offset = header_end + 2 + size
    result = {name: {'molly_lines': 0, 'not_carried': 0, 'in_removed_paths': 0} for name in CLASSES}
    files = {}
    for path, wanted in lines.items():
        row = result[classify(path)]
        have = collections.Counter(x.strip() for x in blobs.get(path, b'').split(b'\n'))
        missing = sum((wanted - have).values())
        row['molly_lines'] += sum(wanted.values()); row['not_carried'] += missing
        if path not in present:
            row['in_removed_paths'] += missing
        if missing:
            files[path] = [missing, path in present]
    return result, files


def measure(repo, tree, parents, molly_index=None, top=15):
    """Measure one tree; optionally include Molly lines not carried."""
    files = combined(repo, tree, parents)
    result = {'tree': repo.tree(tree), 'parents': [repo.commit(p) for p in parents], 'classes': summarize(files),
              'top_production_files': sorted(([p, *c] for p, c in files.items() if classify(p) == 'production'),
                                             key=lambda r: (-(r[1] + r[2]), r[0]))[:top]}
    if molly_index is not None:
        result['molly'], dropped = not_carried(repo, tree, molly_index)
        result['top_not_carried'] = sorted(([p, *v] for p, v in dropped.items() if classify(p) == 'production'),
                                           key=lambda r: (-r[1], r[0]))[:top]
    result['_files'] = files
    return result


def mailbox_paths(repo, series_dir, patches):
    """Paths a layer's mailboxes and deletion records touch."""
    paths = set()
    for entry in patches:
        file = series_dir / entry
        if file.suffix == '.json':
            record = json.loads(file.read_text())
            paths.update(item['path'] for item in record.get('files', []))
            paths.update(record.get('output_sha256', {}))
            continue
        for options in ((), ('--reverse',)):
            for record in repo.git('apply', '--numstat', '-z', *options, data=file.read_bytes()).split(b'\0'):
                fields = record.split(b'\t', 2)
                if len(fields) == 3 and fields[2]:
                    paths.add(fields[2].decode('utf-8', 'surrogateescape'))
    return paths


def port(repo, series_dir):
    """Measure every checkpoint of a port series and attribute changes to layers."""
    provenance = json.loads((series_dir / 'provenance.json').read_text())
    old, molly, signal = provenance['origin_base'], provenance['origin_target'], provenance['signal']
    parents = [molly, signal]
    index = molly_lines(repo, old, molly)
    initial = [layer for layer in provenance['layers'] if layer['id'].startswith('initial/')]
    followups = [layer for layer in provenance['layers'] if not layer['id'].startswith('initial/')]
    checkpoints = [('initial', initial[-1]['after_tree'], STAGE)] + [
        (layer['id'], layer['after_tree'], STAGE) for layer in followups]
    measured = [measure(repo, tree, parents, index) for _, tree, _ in checkpoints]
    owners = {}
    for layer in initial:
        for path in mailbox_paths(repo, series_dir, layer['patches']):
            owners[path] = layer['id']
    layers = collections.OrderedDict((layer['id'], {'stage': STAGE, 'files': {}}) for layer in initial)
    for path, counts in measured[0]['_files'].items():
        owner = owners.get(path, 'mechanical merge (paths no AI layer touched)')
        layers.setdefault(owner, {'stage': STAGE, 'files': {}})['files'][path] = counts
    rows = []
    for name, item in layers.items():
        rows.append({'layer': name, 'stage': item['stage'], 'kind': 'share of initial result',
                     'classes': summarize(item['files'])})
    for i, (name, tree, stage) in enumerate(checkpoints[1:], 1):
        rows.append({'layer': name, 'stage': stage, 'kind': 'physical diff from previous checkpoint',
                     'classes': physical(repo, checkpoints[i - 1][1], tree)})
    stages = collections.OrderedDict()
    for (name, _, stage), result in zip(checkpoints, measured):
        stages[stage] = dict(result, checkpoint=name)
    for result in measured:
        result.pop('_files')
    return {'schema': 1, 'molly': molly, 'old_signal': old, 'signal': signal,
            'classes': CLASS_NOTES, 'stages': stages, 'layers': rows}


def fmt(written, removed):
    return f'+{written:,} / −{removed:,}'


SHOWN = ('production', 'translations', 'tests', 'generated')


def markdown(result):
    """Render the headline section of the port report."""
    stages = list(result['stages'].items())
    molly_total = stages[-1][1]['molly']['production']['molly_lines'] if stages else 0
    out = ['## Lines written by the AI-assisted compatibility changes', '',
           'Each stage\'s reconstructed source is compared with both of its inputs, Molly '
           f'`{result["molly"][:12]}` and Signal `{result["signal"][:12]}`. **Written** lines appear in neither '
           'input. **Removed** lines appear in both inputs but not in the result. Lines taken unchanged from '
           'either input, including either side of a merge conflict, are not counted. These are physical lines; '
           'they do not establish necessity.', '',
           f'Molly itself adds {molly_total:,} non-blank production lines to Signal `{result["old_signal"][:12]}`. '
           '**Molly lines not carried** counts those lines that are missing from the same path; code that was '
           'rewritten or moved counts as missing.', '',
           '| Stage (cumulative) | Production written / removed | Written as % of Molly\'s production lines '
           '| Molly lines not carried | Translations | Tests | Generated |',
           '|---|---:|---:|---:|---:|---:|---:|']
    for stage, item in stages:
        c, mol = item['classes'], item['molly']['production']
        share = f'{c["production"]["written"] / molly_total * 100:.1f}%' if molly_total else 'n/a'
        out.append(f'| {stage}, tree `{item["tree"][:12]}` | {fmt(c["production"]["written"], c["production"]["removed"])} '
                   f'| {share} | {mol["not_carried"]:,} (of which {mol["in_removed_paths"]:,} in removed paths) | '
                   + ' | '.join(fmt(c[k]['written'], c[k]['removed']) for k in SHOWN[1:]) + ' |')
    out += ['', 'Classes: ' + '; '.join(f'**{k}**: {v}' for k, v in result['classes'].items()) + '.', '',
            '### By layer', '',
            'Initial layers own the paths their mailboxes touch, so their rows split the initial result by path and '
            'show lines written / removed. Each follow-up row shows the lines that follow-up changes: its physical '
            'diff from the previous checkpoint, added / deleted.', '',
            '| Layer | Stage | Production | Translations | Tests | Generated |', '|---|---|---:|---:|---:|---:|']
    for row in result['layers']:
        c = row['classes']
        if all(c[k]['written'] == 0 and c[k]['removed'] == 0 for k in SHOWN):
            continue
        out.append(f'| `{row["layer"]}` | {row["stage"]} | '
                   + ' | '.join(fmt(c[k]['written'], c[k]['removed']) for k in SHOWN) + ' |')
    if stages:
        last = stages[-1][1]
        out += ['', f'### Largest production files at {stages[-1][0]}', '',
                '| Path | Written | Removed |', '|---|---:|---:|']
        out += [f'| `{p}` | {w:,} | {r:,} |' for p, w, r in last['top_production_files']]
        out += ['', f'### Most Molly production lines not carried at {stages[-1][0]}', '',
                '| Path | Lines | Path still exists |', '|---|---:|---|']
        out += [f'| `{p}` | {n:,} | {"yes" if exists else "no"} |' for p, n, exists in last['top_not_carried']]
    return '\n'.join(out) + '\n'


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--repo', default='.')
    sub = parser.add_subparsers(dest='command', required=True)
    p = sub.add_parser('port', help='measure every checkpoint of a port series')
    p.add_argument('--series-dir', default='patches/ports/signal-8.33.2')
    p.add_argument('--markdown', action='store_true')
    t = sub.add_parser('tree', help='measure one tree against explicit parents')
    t.add_argument('tree')
    t.add_argument('--parent', action='append', required=True, help='repeat; order is reported')
    t.add_argument('--molly-old', nargs=2, metavar=('OLD_SIGNAL', 'MOLLY'), help='also count Molly lines not carried')
    args = parser.parse_args()
    try:
        with tempfile.TemporaryDirectory(prefix='line-origin-') as objects:
            repo = Repo(args.repo, objects)
            if args.command == 'port':
                result = port(repo, Path(args.repo) / args.series_dir)
                if args.markdown:
                    print(markdown(result), end='')
                else:
                    print(json.dumps(result, indent=1))
            else:
                index = molly_lines(repo, *args.molly_old) if args.molly_old else None
                result = measure(repo, args.tree, args.parent, index)
                result.pop('_files')
                print(json.dumps(result, indent=1))
    except (ValueError, KeyError, OSError, subprocess.SubprocessError) as exc:
        print('line origin failed: ' + str(exc), file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
