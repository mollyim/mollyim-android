#!/usr/bin/env python3
"""Plain git-format-patch forests: base, per-feature series, optional depends."""
import argparse
from dataclasses import dataclass
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile


class ForestError(RuntimeError):
    pass


SHA = re.compile(r'(?:[0-9a-f]{40}|[0-9a-f]{64})\Z')
NAME = re.compile(r'[a-z][a-z0-9-]*(?:/[a-z][a-z0-9-]*)*\Z')
PATCH_NAME = re.compile(r'[0-9]{4}-[A-Za-z0-9_.+-]+\.patch\Z')
ENVELOPE = re.compile(rb'^From ([0-9a-f]{40}|[0-9a-f]{64}) Mon Sep 17 00:00:00 2001$', re.M)
SOURCE = re.compile(rb'^Source-Commits?:[ \t]*(.*)$', re.M)


def lines(path):
    """Data only: one entry per line, with blank lines and # comments allowed."""
    result = []
    for number, line in enumerate(path.read_text(encoding='utf-8').splitlines(), 1):
        entry = line.split('#', 1)[0].strip()
        if not entry:
            continue
        if any(c.isspace() for c in entry) or entry in result:
            raise ForestError(f'{path}:{number}: duplicate entry or whitespace: {entry!r}')
        result.append(entry)
    return result


def mailbox(path, relative):
    """Derive provenance from the mailbox; never maintain a second patch catalog."""
    raw = path.read_bytes()
    envelopes = list(ENVELOPE.finditer(raw))
    if len(envelopes) != 1 or envelopes[0].start() != 0:
        raise ForestError(f'{relative}: expected exactly one git format-patch message')
    header = raw.split(b'\n\n', 1)[0]
    if any(not re.search(rb'^' + key + rb': .+', header, re.M) for key in (b'From', b'Date', b'Subject')):
        raise ForestError(f'{relative}: missing From, Date, or Subject header')
    if b'\ndiff --git ' not in raw:
        raise ForestError(f'{relative}: missing diff (cover letters and empty patches are not series entries)')
    # Diff payload must never be mistaken for commit-message provenance.
    message = raw.split(b'\ndiff --git ', 1)[0]
    sources = []
    for field in SOURCE.findall(message):
        refs = field.decode('ascii').split()
        if not refs or any(not SHA.fullmatch(ref) for ref in refs):
            raise ForestError(f'{relative}: malformed Source-Commit trailer')
        sources.extend(refs)
    return {'path': relative, 'sha256': hashlib.sha256(raw).hexdigest(),
            'mailbox_commit_sha': envelopes[0].group(1).decode(),
            'source_commit_shas': sources}


@dataclass(frozen=True)
class Feature:
    name: str
    patches: tuple[str, ...]
    dependencies: tuple[str, ...]


class Forest:
    def __init__(self, root):
        raw_root = Path(root).absolute()
        for path in (raw_root, *raw_root.parents):
            if path.is_symlink():
                raise ForestError(f'symlink component in patch root: {path}')
        self.root = raw_root.resolve(strict=True)
        # Reject links, including links to files within the forest, before reading data.
        for directory, dirs, files in os.walk(self.root, followlinks=False):
            for name in dirs + files:
                path = Path(directory) / name
                if path.is_symlink() or (path.is_file() is False and path.is_dir() is False):
                    raise ForestError(f'not a regular file/directory (symlink?): {path}')
        bases = lines(self.root / 'base')
        if len(bases) != 1 or not SHA.fullmatch(bases[0]):
            raise ForestError('base must contain exactly one full lowercase commit SHA')
        self.base = bases[0]
        self.features = {}
        self.metadata = {}
        for series in sorted(self.root.rglob('series')):
            name = series.parent.relative_to(self.root).as_posix()
            if not NAME.fullmatch(name) or not series.is_file():
                raise ForestError(f'invalid feature directory: {name}')
            entries = lines(series)
            if not entries:
                raise ForestError(f'{name}: empty series')
            patches = []
            for entry in entries:
                if not PATCH_NAME.fullmatch(entry):
                    raise ForestError(f'{series}: use local numbered .patch filenames, not paths or options: {entry!r}')
                relative = f'{name}/{entry}'
                path = self.root / relative
                if not path.is_file():
                    raise ForestError(f'missing patch: {relative}')
                self.metadata[relative] = mailbox(path, relative)
                patches.append(relative)
            parent = name.rpartition('/')[0]
            depends_file = series.parent / 'depends'
            dependencies = lines(depends_file) if depends_file.exists() else []
            if parent:
                if parent in dependencies:
                    raise ForestError(f'{name}: parent dependency is implicit; remove it from depends')
                dependencies.insert(0, parent)
            if any(not NAME.fullmatch(dep) for dep in dependencies):
                raise ForestError(f'{name}: dependencies must be forest-relative feature names')
            self.features[name] = Feature(name, tuple(patches), tuple(dependencies))
        if not self.features:
            raise ForestError('no feature series found')
        listed = set(self.metadata)
        actual = {p.relative_to(self.root).as_posix() for p in self.root.rglob('*.patch')}
        if listed != actual:
            raise ForestError(f'unlisted patches: {sorted(actual - listed)}')
        for depends in self.root.rglob('depends'):
            if depends.parent.relative_to(self.root).as_posix() not in self.features:
                raise ForestError(f'depends without a feature series: {depends}')
        for feature in self.features.values():
            for dependency in feature.dependencies:
                if dependency not in self.features:
                    raise ForestError(f'{feature.name}: unknown dependency/parent: {dependency}')
        self.order()  # Validate the entire graph, even when a caller selects one feature.

    def order(self, selected=None, reverse=False):
        """Dependency closure, then deterministic topological order; siblings are unordered."""
        pending = list(self.features if selected is None else selected)
        needed = set()
        while pending:
            name = pending.pop()
            if name not in self.features:
                raise ForestError(f'unknown feature: {name}')
            if name not in needed:
                needed.add(name)
                pending.extend(self.features[name].dependencies)
        ordered, done = [], set()
        while needed:
            ready = sorted((name for name in needed
                            if set(self.features[name].dependencies) <= done), reverse=reverse)
            if not ready:
                raise ForestError(f'dependency cycle: {", ".join(sorted(needed))}')
            name = ready[0]
            ordered.append(self.features[name])
            needed.remove(name)
            done.add(name)
        return ordered

    def patches(self, selected=None, reverse=False):
        return [p for feature in self.order(selected, reverse) for p in feature.patches]


GIT_LOCATION_ENV = frozenset({
    'GIT_DIR', 'GIT_WORK_TREE', 'GIT_COMMON_DIR', 'GIT_INDEX_FILE',
    'GIT_OBJECT_DIRECTORY', 'GIT_ALTERNATE_OBJECT_DIRECTORIES',
    'GIT_PREFIX', 'GIT_NAMESPACE', 'GIT_IMPLICIT_WORK_TREE',
    'GIT_GRAFT_FILE', 'GIT_SHALLOW_FILE',
})


def clean_git_env(env=None):
    """Copy the environment without inherited repository, index, or object overrides."""
    return {key: value for key, value in (os.environ if env is None else env).items()
            if key not in GIT_LOCATION_ENV}


def git(repo, *args, check=True):
    env = clean_git_env()
    env['GIT_LFS_SKIP_SMUDGE'] = '1'
    result = subprocess.run(['git', '-C', str(repo), '-c', 'core.hooksPath=/dev/null',
                             '-c', 'commit.gpgSign=false', '-c', 'rerere.enabled=false',
                             '-c', 'core.autocrlf=false',
                             '-c', 'core.sparseCheckout=false', '-c', 'core.sparseCheckoutCone=false',
                             '-c', 'apply.ignoreWhitespace=no',
                             '-c', 'user.name=Molly Replay Tool',
                             '-c', 'user.email=molly-replay-tool@example.invalid', *args],
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, env=env)
    if check and result.returncode:
        raise ForestError(f'git {" ".join(args)} failed:\n{result.stdout.decode(errors="replace")}')
    return result.stdout.decode(errors='replace').strip()


def destination(repo, path):
    path = Path(path).absolute()
    if '..' in path.parts:
        raise ForestError('REFUSE: worktree destination contains parent traversal')
    for component in (path, *path.parents):
        if component.is_symlink():
            raise ForestError(f'REFUSE: symlink component in worktree destination: {component}')
    if os.path.lexists(path):
        raise ForestError(f'REFUSE: worktree destination exists: {path}')
    if path.is_relative_to(Path(repo).resolve()):
        raise ForestError('REFUSE: worktree destination inside source checkout')
    return path


def add_worktree(repo, worktree, commit):
    """Create a full checkout without changing the caller's sparse configuration."""
    scoped = git(repo, 'config', '--bool', '--get', 'extensions.worktreeConfig',
                 check=False) == 'true'
    if not scoped:
        # Read the effective persistent setting without git()'s command-local
        # override. A shared sparse setting cannot be changed for just the new
        # worktree, so refuse instead of changing the caller's repository.
        setting = subprocess.run(['git', '-C', str(repo), 'config', '--bool',
                                  '--get', 'core.sparseCheckout'],
                                 stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                 env=clean_git_env())
        if setting.returncode not in (0, 1):
            raise ForestError('cannot inspect sparse checkout configuration: '
                              + setting.stderr.decode(errors='replace'))
        if setting.stdout.strip() == b'true':
            raise ForestError('sparse checkout with shared Git configuration requires '
                              'extensions.worktreeConfig=true before release assembly')
    git(repo, 'worktree', 'add', '--detach', str(worktree), commit)
    if scoped:
        # worktree add copies the source's per-worktree sparse configuration.
        # The command-local overrides materialize every file, but build tools
        # also need persistent dense settings after this command has exited.
        for key in ('core.sparseCheckout', 'core.sparseCheckoutCone', 'index.sparse'):
            git(worktree, 'config', '--worktree', key, 'false')


def apply(forest, repo, worktree, selected=None, reverse=False, *, expected_tree=None):
    """Replay with exact context (line offsets are allowed) in a new worktree.

    Git locates unchanged context without rewriting hunk line numbers. Changed
    context is an error: no fuzz, three-way merge, or whitespace-ignore fallback.
    Only an exact trusted tree permits inherited source whitespace.
    """
    if expected_tree is not None and (not isinstance(expected_tree, str) or not SHA.fullmatch(expected_tree)):
        raise ForestError('expected_tree must be a full lowercase tree SHA')
    worktree = destination(repo, worktree)
    order = forest.order(selected, reverse)
    if git(repo, 'cat-file', '-t', forest.base) != 'commit':
        raise ForestError('base must resolve to a commit')
    add_worktree(repo, worktree, forest.base)
    for feature in order:
        for relative in feature.patches:
            path = forest.root / relative
            if hashlib.sha256(path.read_bytes()).hexdigest() != forest.metadata[relative]['sha256']:
                raise ForestError(f'patch changed after validation: {relative}')
            try:
                git(worktree, 'am', '--no-3way', '--whitespace=nowarn', '--committer-date-is-author-date', str(path))
            except ForestError as exc:
                raise ForestError(f'{relative}: {exc}\nWorktree preserved at {worktree}; '
                                  'inspect with git am --show-current-patch; abort with git am --abort.') from exc
    if git(worktree, 'ls-files', '--', '.hangar'):
        raise ForestError('carrier .hangar state must not enter a release tree')
    tree_sha = git(worktree, 'rev-parse', 'HEAD^{tree}')
    if expected_tree is not None and tree_sha != expected_tree:
        raise ForestError(f'tree mismatch: expected {expected_tree}, got {tree_sha}')
    # A trusted source tree may contain historical whitespace errors. Preserve its
    # exact bytes and report them; ordinary feature replay still rejects errors.
    whitespace_report = git(worktree, 'diff', '--check', forest.base, 'HEAD',
                            check=expected_tree is None)
    result = {'status': 'PASS', 'base': forest.base, 'features': [f.name for f in order],
              'tree_sha': tree_sha,
              'patches': [forest.metadata[p] for f in order for p in f.patches]}
    if expected_tree is not None:
        result['whitespace_report'] = whitespace_report
    return result


def verify(forest, repo):
    """Test each declared closure alone and two legal full-forest orders."""
    result = {'status': 'PASS', 'closures': {}, 'full_orders': []}
    cases = [(name, [name], False) for name in sorted(forest.features)]
    cases += [('all', None, False), ('all-reverse-ready', None, True)]
    for name, selected, reverse in cases:
        with tempfile.TemporaryDirectory(prefix='patch-forest-') as temp:
            worktree = Path(temp) / 'worktree'
            try:
                record = apply(forest, repo, worktree, selected, reverse)
                if selected:
                    result['closures'][name] = record['tree_sha']
                else:
                    result['full_orders'].append(record['tree_sha'])
            except ForestError as exc:
                detail = str(exc).replace('Worktree preserved at', 'Temporary worktree (removed after verification) at')
                raise ForestError(f'{name}: {detail}') from exc
            finally:
                # Only this function's freshly allocated temporary worktree is removed.
                if worktree.exists():
                    git(repo, 'worktree', 'remove', '--force', str(worktree))
    if len(set(result['full_orders'])) != 1:
        raise ForestError('independent feature orders produce different trees; declare a dependency or split shared groundwork')
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=('check', 'series', 'apply', 'verify'))
    parser.add_argument('--patches', type=Path, default=Path('patches/molly'))
    parser.add_argument('--repo', type=Path, default=Path('.'))
    parser.add_argument('--feature', action='append', help='feature and its prerequisites, repeatable')
    parser.add_argument('--worktree', type=Path)
    args = parser.parse_args()
    try:
        forest = Forest(args.patches)
        if args.command == 'series':
            print('\n'.join(forest.patches(args.feature)))
            return 0
        if args.command == 'apply':
            if args.worktree is None:
                raise ForestError('apply requires --worktree (a new destination)')
            result = apply(forest, args.repo.resolve(), args.worktree, args.feature)
        elif args.command == 'verify':
            if args.feature:
                raise ForestError('verify checks the whole forest; do not pass --feature')
            result = verify(forest, args.repo.resolve())
        else:
            ordered = forest.order(args.feature)
            result = {'status': 'PASS', 'base': forest.base,
                      'features': {f.name: list(f.dependencies) for f in ordered},
                      'patch_count': len(forest.patches(args.feature))}
        print(json.dumps(result, indent=2))
        return 0
    except (ForestError, OSError, UnicodeError) as exc:
        print(f'patch forest: {exc}', file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
