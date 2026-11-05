"""Verify ordered, pinned whole-file deletion stages."""
import json
import hashlib
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import forest
import release
import prepare_port_report


class DeletionStages(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.git('init', '-q')
        self.git('config', 'user.name', 'Test')
        self.git('config', 'user.email', 'test@example.invalid')
        for name in ('one', 'two'):
            (self.root / name).write_text(name)
        self.git('add', '.')
        self.git('commit', '-qm', 'initial')
        self.before = self.git('rev-parse', 'HEAD^{tree}')
        self.source = self.git('rev-parse', 'HEAD')
        self.blobs = {n: self.git('rev-parse', 'HEAD:' + n) for n in ('one', 'two')}
        self.git('rm', '-q', 'one')
        self.middle = self.git('write-tree')
        self.git('rm', '-q', 'two')
        self.after = self.git('write-tree')
        self.git('reset', '--hard', '-q', 'HEAD')
        self.dir = self.root / 'stages'
        self.dir.mkdir()

    def git(self, *args):
        return subprocess.check_output(['git', '-C', str(self.root), *args], stderr=subprocess.STDOUT).decode().strip()

    def stage(self, name, before, after):
        return {'schema': 1, 'type': 'delete-files', 'source_commit': self.source,
                'before_tree': before, 'after_tree': after,
                'files': [{'path': name, 'mode': '100644', 'blob': self.blobs[name]}]}

    def test_manifest_rejects_malformed_duplicate_and_unsafe_entries(self):
        p = self.dir / 'stage.json'
        for raw in ('{"schema":1,"schema":1}', '{', ''):
            p.write_text(raw)
            with self.assertRaises(forest.ForestError):
                release.deletion_manifest(p, 'stage.json')
        for name in ('../bad', '.git/config', 'one'):
            data = self.stage('one', self.before, self.middle)
            data['files'][0]['path'] = name
            if name == 'one': data['files'].append(dict(data['files'][0]))
            p.write_text(json.dumps(data))
            with self.assertRaises(forest.ForestError):
                release.deletion_manifest(p, 'stage.json')

    def test_ordered_stages_and_changed_manifest_hash(self):
        for filename, data in [('one.json', self.stage('one', self.before, self.middle)),
                               ('two.json', self.stage('two', self.middle, self.after))]:
            (self.dir / filename).write_text(json.dumps(data))
        (self.dir / 'series').write_text('one.json\ntwo.json\n')
        records = release.resolution_series(self.root, 'stages/series')
        self.git('add', 'stages')
        self.git('commit', '-qm', 'metadata')
        # Use the original tree so metadata is absent from the materialized result.
        dest = self.root.parent / (self.root.name + '-result')
        self.addCleanup(lambda: subprocess.run(['git', '-C', str(self.root), 'worktree', 'remove', '--force', str(dest)], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL))
        changed, _ = release.apply_resolutions(self.root, dest, self.before, records)
        self.assertEqual(changed, {'one', 'two'})
        self.assertEqual(forest.git(dest, 'rev-parse', 'HEAD^{tree}'), self.after)
        (self.dir / 'one.json').write_text('{}')
        second = self.root.parent / (self.root.name + '-second')
        self.addCleanup(lambda: subprocess.run(['git', '-C', str(self.root), 'worktree', 'remove', '--force', str(second)], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL))
        with self.assertRaisesRegex(forest.ForestError, 'changed after validation'):
            release.apply_resolutions(self.root, second, self.before, records)

    def test_report_preparation_executes_and_checks_stages(self):
        stage = self.dir / 'one.json'
        stage.write_text(json.dumps(self.stage('one', self.before, self.middle)))
        (self.dir / 'series').write_text('one.json\n')
        config = {'signal': {'commit': self.source}, 'molly': {'target': self.source},
                  'port': {'mechanical_tree': self.before, 'series': 'stages/series'},
                  'target_tree': self.middle}
        provenance = {'origin_base': self.source, 'origin_target': self.source,
                      'signal': self.source, 'mechanical_tree': self.before,
                      'target_tree': self.middle,
                      'mailbox_sha256': {'one.json': hashlib.sha256(stage.read_bytes()).hexdigest()}}
        index = self.root.parent / (self.root.name + '-index')
        with patch.object(release, 'mechanical_merge', return_value=(self.before, set(), '')):
            self.assertEqual(prepare_port_report.materialize(self.root, config, provenance, self.root, index), self.middle)
            provenance['mailbox_sha256']['one.json'] = '0' * 64
            with self.assertRaisesRegex(ValueError, 'hash mismatch'):
                prepare_port_report.materialize(self.root, config, provenance, self.root, index)

    def test_disk_mode_and_blob_mismatch_rejected(self):
        data = self.stage('one', self.before, self.middle)
        data['files'][0]['blob'] = '0' * 40
        with self.assertRaisesRegex(forest.ForestError, 'preimage mismatch'):
            release.apply_deletions(self.root, self.root, data, forest.clean_git_env())
        data['files'][0]['blob'] = self.blobs['one']
        os.chmod(self.root / 'one', 0o755)
        with self.assertRaisesRegex(forest.ForestError, 'mode mismatch'):
            release.apply_deletions(self.root, self.root, data, forest.clean_git_env())
