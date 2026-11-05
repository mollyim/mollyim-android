import copy
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
import unittest

import verify_source_bundles as checker


class SourceBundlesTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.source = self.root / 'source'
        self.repo = self.root / 'receiver'
        self.source.mkdir()
        self.repo.mkdir()
        for repo in (self.source, self.repo):
            self.git(repo, 'init', '-q')
            self.git(repo, 'config', 'user.name', 'Test')
            self.git(repo, 'config', 'user.email', 'test@example.invalid')
        self.git(self.source, 'commit', '--allow-empty', '-qm', 'molly')
        molly = self.git(self.source, 'rev-parse', 'HEAD')
        self.git(self.source, 'checkout', '--orphan', 'signal', '-q')
        (self.source / 'file').write_text('signal')
        self.git(self.source, 'add', 'file')
        self.git(self.source, 'commit', '-qm', 'signal')
        signal = self.git(self.source, 'rev-parse', 'HEAD')
        self.git(self.source, 'checkout', '-q', '-b', 'replay', molly)
        self.git(self.source, 'merge', '-q', '--allow-unrelated-histories', '--no-ff', '-m', 'initial', signal)
        initial = self.git(self.source, 'rev-parse', 'HEAD')
        initial_tree = self.tree(initial)
        layers = [dict(id='initial/sample', source_commit=initial, after_tree=initial_tree)]
        previous = initial
        previous_tree = initial_tree
        for name in ('one', 'two', 'three', 'four'):
            (self.source / 'file').write_text(name)
            self.git(self.source, 'add', 'file')
            self.git(self.source, 'commit', '-qm', name)
            commit = self.git(self.source, 'rev-parse', 'HEAD')
            tree = self.tree(commit)
            layers.append(dict(id='followups/' + name, source_commit=commit, before_tree=previous_tree, after_tree=tree))
            previous, previous_tree = commit, tree
        core = previous
        port = self.repo / 'patches/ports/test'
        port.mkdir(parents=True)
        (port / 'series').write_text('')
        self.git(self.source, 'update-ref', 'refs/heads/source-checkpoint/core', core)
        self.git(self.source, 'bundle', 'create', str(port / 'source-checkpoint.bundle'), 'refs/heads/source-checkpoint/core', '^' + molly, '^' + signal)
        base_path = port / 'source-checkpoint.bundle'
        base = dict(path=base_path.name, sha256=self.digest(base_path), bytes=base_path.stat().st_size, ref='refs/heads/source-checkpoint/core', source_commit=core, source_tree=previous_tree, prerequisites=[molly, signal])
        self.increment_paths = []
        for name in ('a', 'b', 'c', 'd'):
            before, parent = previous_tree, previous
            (self.source / 'file').write_text(name)
            self.git(self.source, 'add', 'file')
            self.git(self.source, 'commit', '-qm', name)
            previous = self.git(self.source, 'rev-parse', 'HEAD')
            previous_tree = self.tree(previous)
            ref = 'refs/heads/source-checkpoint/' + name
            self.git(self.source, 'update-ref', ref, previous)
            path = port / name / 'source-checkpoint.bundle'
            path.parent.mkdir()
            self.git(self.source, 'bundle', 'create', str(path), ref, '^' + parent)
            self.increment_paths.append(path)
            layers.append(dict(id='followups/' + name, source_commit=previous, before_tree=before, after_tree=previous_tree, source_bundle=name + '/source-checkpoint.bundle', source_bundle_prerequisite=parent, source_bundle_sha256=self.digest(path), source_bundle_bytes=path.stat().st_size))
        self.prov_path = port / 'provenance.json'
        self.prov = dict(origin_target=molly, signal=signal, initial_source=initial, target_tree=previous_tree, source_bundle_base=base, layers=layers)
        self.write_prov()
        (self.repo / 'molly-build.json').write_text(json.dumps(dict(schema=1, signal=dict(commit=signal), molly=dict(target=molly, tree=self.tree(molly)), port=dict(series='patches/ports/test/series'), target_tree=previous_tree)))
        self.git(self.repo, 'fetch', '-q', str(self.source), molly, signal)
        self.core = core

    @staticmethod
    def git(repo, *args):
        return subprocess.check_output(['git', '-C', str(repo), *args], text=True, stderr=subprocess.PIPE).strip()

    def tree(self, commit):
        return self.git(self.source, 'rev-parse', commit + '^{tree}')

    @staticmethod
    def digest(path):
        return hashlib.sha256(path.read_bytes()).hexdigest()

    def write_prov(self):
        self.prov_path.write_text(json.dumps(self.prov))

    def test_fresh_receiver_needs_base_then_imports_all(self):
        self.assertNotEqual(subprocess.run(['git', '-C', str(self.repo), 'cat-file', '-e', self.core], capture_output=True).returncode, 0)
        self.assertNotEqual(subprocess.run(['git', '-C', str(self.repo), 'bundle', 'verify', str(self.increment_paths[0])], capture_output=True).returncode, 0)
        checker.verify(self.repo)
        self.assertEqual(self.git(self.repo, 'rev-parse', 'refs/molly-pipeline/source-checkpoints/' + self.prov['layers'][-1]['source_commit']), self.prov['layers'][-1]['source_commit'])
        checker.verify(self.repo)

    def test_rejects_altered_bundle_header_with_matching_hash(self):
        path = self.repo / 'patches/ports/test/source-checkpoint.bundle'
        original = path.read_bytes()
        for label, replacement in (
            ('ref', original.replace(b'refs/heads/source-checkpoint/core', b'refs/heads/source-checkpoint/evil', 1)),
            ('prerequisite', original.replace(self.prov['origin_target'].encode(), b'0' * 40, 1)),
        ):
            with self.subTest(label=label):
                self.assertNotEqual(original, replacement)
                path.write_bytes(replacement)
                self.prov['source_bundle_base']['sha256'] = self.digest(path)
                self.prov['source_bundle_base']['bytes'] = path.stat().st_size
                self.write_prov()
                with self.assertRaisesRegex(ValueError, 'advertised ref|prerequisites mismatch'):
                    checker.verify(self.repo)
        path.write_bytes(original)

    def test_rejects_corrupt_pack_even_when_cached(self):
        checker.verify(self.repo)
        path = self.repo / 'patches/ports/test/source-checkpoint.bundle'
        data = bytearray(path.read_bytes())
        data[-1] ^= 1
        path.write_bytes(data)
        self.prov['source_bundle_base']['sha256'] = self.digest(path)
        self.write_prov()
        with self.assertRaises(RuntimeError):
            checker.verify(self.repo)

    def test_rejects_bad_metadata_and_bytes(self):
        original = copy.deepcopy(self.prov)
        cases = [
            ('bytes', lambda p: p['source_bundle_base'].__setitem__('bytes', 1)),
            ('ref', lambda p: p['source_bundle_base'].__setitem__('ref', 'refs/heads/wrong')),
            ('tree', lambda p: p['source_bundle_base'].__setitem__('source_tree', '0' * 40)),
            ('prerequisite', lambda p: p['layers'][5].__setitem__('source_bundle_prerequisite', '0' * 40)),
            ('path', lambda p: p['source_bundle_base'].__setitem__('path', '../escape')),
            ('parent', lambda p: p['layers'][1].__setitem__('source_commit', p['layers'][2]['source_commit'])),
        ]
        for name, mutate in cases:
            with self.subTest(name=name):
                self.prov = copy.deepcopy(original)
                mutate(self.prov)
                self.write_prov()
                with self.assertRaises((ValueError, RuntimeError)):
                    checker.verify(self.repo)
        self.prov = original
        self.write_prov()
        path = self.repo / 'patches/ports/test/source-checkpoint.bundle'
        path.write_bytes(path.read_bytes() + b'x')
        with self.assertRaisesRegex(ValueError, 'bytes or sha256'):
            checker.verify(self.repo)
