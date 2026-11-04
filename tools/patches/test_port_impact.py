"""Read-only impact analysis against small, real Git histories and mailboxes."""
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import export_complete
import forest
import port_impact
import git_utils


class PortImpactTests(unittest.TestCase):
    CARRY = 'app/src/main/java/org/thoughtcrime/securesms/crypto/Carry.java'
    CHANGED = 'app/src/main/java/org/thoughtcrime/securesms/net/Changed.kt'
    ABSORBED = 'app/src/main/java/im/molly/unifiedpush/Absorbed.kt'
    DELETED = 'app/src/main/java/org/thoughtcrime/securesms/net/Deleted.java'
    ADDED = 'app/src/main/java/org/thoughtcrime/securesms/crypto/Added.java'
    ODD = 'space and\nnewline.txt'

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.home = Path(self.temp.name)
        self.repo = self.home / 'repo'
        self.repo.mkdir()
        self.git('init', '-q')
        self.git('config', 'user.name', 'Impact Fixture')
        self.git('config', 'user.email', 'impact@example.invalid')
        for path in (self.CARRY, self.CHANGED, self.ABSORBED, self.DELETED,
                     'rename old.txt', 'demo/widget/old.txt', 'baseline-profile/keep.txt',
                     'app/src/screenshotTestPlayProdDebug/old.txt'):
            self.write(path, 'original ' + path + '\n')
        self.write('mode.sh', '#!/bin/sh\nexit 0\n')
        self.git('add', '.')
        self.git('commit', '-qm', 'Signal base')
        self.base = self.git('rev-parse', 'HEAD')
        molly_parent = self.git('commit-tree', self.base + '^{tree}', '-p', self.base,
                                '-m', 'Molly lineage')
        self.merge = self.git('commit-tree', self.base + '^{tree}', '-p', molly_parent,
                               '-p', self.base, '-m', 'Import Signal')
        self.git('checkout', '--detach', '-q', self.merge)
        for path in (self.CARRY, self.CHANGED, self.ABSORBED, self.ADDED, self.ODD):
            self.write(path, 'Molly ' + path + '\n')
        for path in (self.DELETED, 'demo/widget/old.txt', 'app/src/screenshotTestPlayProdDebug/old.txt'):
            (self.repo / path).unlink()
        (self.repo / 'mode.sh').chmod(0o755)
        (self.repo / 'rename old.txt').rename(self.repo / 'rename new.txt')
        self.git('add', '.')
        self.git('commit', '-qm', 'Molly target')
        self.target = self.git('rev-parse', 'HEAD')
        self.source = self.home / 'source-forest'
        export_complete.export(self.repo, self.base, self.target, self.merge, self.source)
        self.git('checkout', '--detach', '-q', self.base)
        self.write(self.CHANGED, 'new upstream implementation\n')
        self.write(self.ABSORBED, 'Molly ' + self.ABSORBED + '\n')
        self.write(self.ODD, 'different upstream addition\n')
        (self.repo / self.DELETED).unlink()
        (self.repo / 'rename old.txt').rename(self.repo / 'rename new.txt')
        for path in ('demo/widget/new.txt', 'app/src/screenshotTestPlayProdDebug/new.txt',
                     'baseline-profile/new.txt', 'unrelated-new.txt'):
            self.write(path, 'new upstream path\n')
        self.git('add', '.')
        self.git('commit', '-qm', 'New Signal base')
        self.new_base = self.git('rev-parse', 'HEAD')

    def git(self, *args, binary=False):
        environment = forest.clean_git_env()
        environment.update(GIT_CONFIG_NOSYSTEM='1', GIT_CONFIG_GLOBAL=os.devnull)
        result = git_utils.git(self.repo, '-c', 'core.hooksPath=/dev/null',
                            '-c', 'commit.gpgSign=false', *args, env=environment, binary=binary)
        return result.stdout if binary else result.stdout.strip()

    def write(self, path, text):
        destination = self.repo / path
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(text, encoding='utf-8')

    def analyze(self):
        return port_impact.analyze(self.repo, self.source, self.new_base)

    @staticmethod
    def paths(result, category):
        return {path for feature in result['features'].values()
                for path in feature['paths'][category]}

    def test_classifies_changes_additions_deletions_and_both_rename_sides(self):
        result = self.analyze()
        self.assertEqual(result['status'], 'ANALYZED')
        self.assertEqual(self.paths(result, 'unchanged-base'),
                         {self.CARRY, self.ADDED, 'mode.sh', 'demo/widget/old.txt',
                          'app/src/screenshotTestPlayProdDebug/old.txt'})
        self.assertEqual(self.paths(result, 'already-upstream'),
                         {self.ABSORBED, self.DELETED, 'rename old.txt', 'rename new.txt'})
        self.assertEqual(self.paths(result, 'upstream-changed'), {self.CHANGED, self.ODD})
        self.assertEqual(sum(result['counts'].values()), result['source_changed_paths'])
        self.assertEqual(result['features']['security-and-locking']['counts']['unchanged-base'], 2)
        self.assertEqual(result['source']['target'], self.target)
        self.assertEqual(result['source']['base'], self.base)
        self.assertEqual(result['new_base'], self.new_base)
        self.assertEqual(len(result['source']['mailbox_sha256']), len(forest.Forest(self.source).metadata))

    def test_mode_and_symlink_type_are_part_of_comparison(self):
        (self.repo / 'mode.sh').chmod(0o755)
        self.git('add', 'mode.sh')
        self.git('commit', '-qm', 'Adopt executable mode upstream')
        self.new_base = self.git('rev-parse', 'HEAD')
        self.assertIn('mode.sh', self.paths(self.analyze(), 'already-upstream'))
        (self.repo / 'mode.sh').unlink()
        (self.repo / 'mode.sh').symlink_to('unrelated-new.txt')
        self.git('add', 'mode.sh')
        self.git('commit', '-qm', 'Replace executable with symlink upstream')
        self.new_base = self.git('rev-parse', 'HEAD')
        self.assertIn('mode.sh', self.paths(self.analyze(), 'upstream-changed'))

    def test_native_rename_mailbox_reports_both_paths(self):
        data = forest.Forest(self.source)
        for name, metadata in data.metadata.items():
            if name.startswith('build-and-release/'):
                renamed = self.git('format-patch', '--stdout', '--binary', '--full-index',
                                   '--find-renames=100%', '-1', metadata['mailbox_commit_sha'], binary=True)
                self.assertIn(b'rename from rename old.txt', renamed)
                (self.source / name).write_bytes(renamed)
        result = self.analyze()
        self.assertTrue({'rename old.txt', 'rename new.txt'} <= self.paths(result, 'already-upstream'))

    def test_flags_only_new_paths_in_entirely_removed_module_roots(self):
        flagged = self.analyze()['upstream_additions_in_removed_modules']
        self.assertEqual(set(flagged['paths']),
                         {'demo/widget/new.txt', 'app/src/screenshotTestPlayProdDebug/new.txt'})
        self.assertEqual(flagged['count'], 2)
        self.assertEqual(set(flagged['roots']), {'demo/', 'app/src/screenshotTestPlayProdDebug/'})

    def test_poisoned_git_environment_cannot_change_callers_checkout_or_index(self):
        self.write(self.CARRY, 'staged caller edit\n')
        self.git('add', self.CARRY)
        self.write(self.CARRY, 'unstaged caller edit\n')
        self.write('untracked-caller-file', 'keep\n')
        index = (self.repo / '.git/index').read_bytes()
        head = self.git('rev-parse', 'HEAD')
        caller_index = self.home / 'caller-index'
        caller_index.write_bytes(b'not a Git index')
        overrides = {'GIT_DIR': str(self.home / 'wrong-repository'),
                     'GIT_WORK_TREE': str(self.home / 'wrong-worktree'),
                     'GIT_COMMON_DIR': str(self.home / 'wrong-common'),
                     'GIT_INDEX_FILE': str(caller_index),
                     'GIT_OBJECT_DIRECTORY': str(self.home / 'wrong-objects'),
                     'GIT_ALTERNATE_OBJECT_DIRECTORIES': str(self.home / 'wrong-alternates')}
        with patch.dict(os.environ, overrides):
            environment = dict(os.environ)
            self.assertEqual(self.analyze()['status'], 'ANALYZED')
            self.assertEqual(dict(os.environ), environment)
        self.assertEqual(self.git('rev-parse', 'HEAD'), head)
        self.assertEqual((self.repo / '.git/index').read_bytes(), index)
        self.assertEqual((self.repo / self.CARRY).read_text(), 'unstaged caller edit\n')
        self.assertEqual((self.repo / 'untracked-caller-file').read_text(), 'keep\n')
        self.assertEqual(caller_index.read_bytes(), b'not a Git index')

    def test_analysis_does_not_require_source_blob_objects(self):
        blobs = {entry[2] for revision in (self.base, self.target, self.new_base)
                 for entry in port_impact.tree_entries(self.repo, revision).values()
                 if entry[1] == b'blob'}
        for object_id in blobs:
            name = object_id.decode()
            (self.repo / '.git/objects' / name[:2] / name[2:]).unlink()
        self.assertEqual(self.analyze()['source_changed_paths'], 11)

    def test_missing_source_mailbox_is_rejected(self):
        directory = self.source / 'security-and-locking'
        for path in directory.iterdir():
            path.unlink()
        directory.rmdir()
        (self.source / 'build-and-release/depends').write_text('module-pruning\n')
        with self.assertRaisesRegex(forest.ForestError, 'source mailbox ownership differs'):
            self.analyze()


if __name__ == '__main__':
    unittest.main()
