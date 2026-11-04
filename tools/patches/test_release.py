"""Release boundaries and deterministic assembly in small real Git repositories."""
import json
import os
from pathlib import Path
import shutil
import unittest
from unittest.mock import patch

import forest
import git_utils
import release
import test_complete
import verify_complete


class ReleaseTests(unittest.TestCase):
    def setUp(self):
        self.source = test_complete.CompleteTests()
        self.addCleanup(self.source.doCleanups)
        self.source.setUp()
        self.repo = self.source.repo
        self.home = self.source.home
        self.git = self.source.git
        snapshot = self.source.make_forest()
        self.git('checkout', '--detach', '-q', self.source.base)
        shutil.copytree(snapshot, self.repo / 'patches/molly')
        self.config = {
            'schema': 1,
            'signal': {'commit': self.source.base},
            'molly': {'patches': 'patches/molly', 'target': self.source.target,
                      'tree': self.source.target_tree},
            'target_tree': self.source.target_tree,
        }
        self.config_path = self.repo / 'molly-build.json'
        self.save_config()

    def save_config(self):
        self.config_path.write_text(json.dumps(self.config) + '\n')
        self.git('add', 'patches', 'molly-build.json')
        self.git('commit', '-qm', 'Patch pipeline metadata')

    def assemble(self, suffix='build'):
        return release.assemble(self.repo, self.config_path, self.home / suffix)

    def port_fixture(self):
        self.git('checkout', '--detach', '-q', self.source.base)
        (self.repo / 'existing.txt').write_text('new Signal implementation\n')
        # Molly made this executable; Signal removes it. Keeping Molly's file
        # requires a recorded decision even though no content patch is needed.
        (self.repo / 'mode.sh').unlink()
        self.git('add', '-u')
        self.git('commit', '-qm', 'New Signal source')
        signal = self.git('rev-parse', 'HEAD')
        self.mechanical, conflicts, _ = release.mechanical_merge(
            self.repo, self.source.base, self.source.target, signal)
        self.assertEqual(conflicts, {'existing.txt', 'mode.sh'})
        synthetic = self.git('commit-tree', self.mechanical, '-m', 'Mechanical merge fixture')
        resolution_worktree = self.home / 'author-resolution'
        forest.git(self.repo, 'worktree', 'add', '--detach', str(resolution_worktree), synthetic)
        (resolution_worktree / 'existing.txt').write_text('reviewed integration\n')
        forest.git(resolution_worktree, 'add', 'existing.txt')
        forest.git(resolution_worktree, 'commit', '-m',
                   'Adapt the changed Signal API\n\nSource-Commit: ' + self.source.target)
        target = forest.git(resolution_worktree, 'rev-parse', 'HEAD^{tree}')
        patch_bytes = git_utils.git(
            resolution_worktree, 'format-patch', '--stdout', '--binary', '--full-index',
            '-1', binary=True).stdout
        source_snapshot = self.source.make_forest(name='port-snapshot')
        shutil.copytree(source_snapshot, self.repo / 'patches/molly')
        directory = self.repo / 'patches/ports/next'
        directory.mkdir(parents=True)
        self.resolution = directory / '0001-api.patch'
        self.resolution.write_bytes(patch_bytes)
        (directory / 'series').write_text('0001-api.patch\n')
        self.config['signal']['commit'] = signal
        self.config['target_tree'] = target
        self.config['port'] = {
            'mechanical_tree': self.mechanical,
            'series': 'patches/ports/next/series',
            'keep_mechanical': {'mode.sh': 'Retain the executable Molly helper despite upstream deletion.'},
        }
        self.save_config()
        return patch_bytes

    def test_historical_assembly_is_exact_and_complete(self):
        result = self.assemble()
        self.assertEqual(result['tree'], self.source.target_tree)
        self.assertEqual((self.home / 'build/binary.bin').read_bytes(), b'\0new\xfe\xff')
        self.assertTrue((self.home / 'build/link').is_symlink())
        self.assertFalse((self.home / 'build/delete.txt').exists())
        self.assertEqual(len(result['historical']['replays']), 2)

    def test_source_edit_outside_pipeline_metadata_is_rejected(self):
        (self.repo / 'existing.txt').write_text('unreviewed source edit\n')
        self.git('add', 'existing.txt')
        self.git('commit', '-qm', 'Unexpected application edit')
        with self.assertRaisesRegex(forest.ForestError, 'unaccounted edits'):
            self.assemble()
        self.assertFalse((self.home / 'build').exists())

    def test_repository_files_are_not_edits_to_the_signal_base(self):
        for name in ('.github/workflows/release.yml', '.github/actions/molly-source/action.yml',
                     '.github/FUNDING.yml', 'README.md', 'CONTRIBUTING.md'):
            path = self.repo / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text('repository file\n')
        self.git('add', '.')
        self.git('commit', '-qm', 'Repository files')
        self.assertEqual(self.assemble()['tree'], self.source.target_tree)
        (self.repo / 'docs').mkdir()
        (self.repo / 'docs/other.md').write_text('not a repository file\n')
        self.git('add', '.')
        self.git('commit', '-qm', 'Other path')
        with self.assertRaisesRegex(forest.ForestError, 'unaccounted edits'):
            self.assemble('second')

    def test_historical_target_cannot_override_official_snapshot(self):
        self.config['target_tree'] = self.git('rev-parse', self.source.base + '^{tree}')
        self.save_config()
        with self.assertRaisesRegex(forest.ForestError, 'exact original Signal and Molly trees'):
            self.assemble()
        self.assertFalse((self.home / 'build').exists())

    def test_port_replays_resolution_and_explicit_keep_decision(self):
        original = self.port_fixture()
        result = self.assemble()
        self.assertEqual(result['tree'], self.config['target_tree'])
        self.assertEqual(result['mechanical_tree'], self.mechanical)
        self.assertEqual(result['conflict_paths'], 2)
        self.assertEqual((self.home / 'build/existing.txt').read_text(), 'reviewed integration\n')
        self.assertTrue((self.home / 'build/mode.sh').stat().st_mode & 0o111)
        self.assertEqual(self.resolution.read_bytes(), original)

    def test_mechanical_pin_mismatch_stops_before_resolution_checkout(self):
        self.port_fixture()
        self.config['port']['mechanical_tree'] = self.source.target_tree
        self.save_config()
        with self.assertRaisesRegex(forest.ForestError, 'mechanical merge changed'):
            self.assemble()
        self.assertFalse((self.home / 'build').exists())

    def test_unadapted_checkpoint_cannot_be_built(self):
        self.port_fixture()
        self.config['target_tree'] = None
        self.save_config()
        with self.assertRaisesRegex(forest.ForestError, 'unadapted review checkpoint'):
            self.assemble()
        self.assertFalse((self.home / 'build').exists())

    def test_conflicted_path_needs_a_patch_or_explicit_keep_reason(self):
        self.port_fixture()
        self.config['port']['keep_mechanical'] = {}
        self.save_config()
        with self.assertRaisesRegex(forest.ForestError, 'unreviewed merge conflicts: mode.sh'):
            self.assemble()

    def test_keep_decision_cannot_claim_a_path_changed_by_resolution(self):
        self.port_fixture()
        self.config['port']['keep_mechanical']['existing.txt'] = 'Keep the mechanical version.'
        self.save_config()
        with self.assertRaisesRegex(forest.ForestError, 'keep_mechanical.*changed'):
            self.assemble()

    def test_resolution_paths_cannot_escape_through_traversal_or_symlinks(self):
        self.port_fixture()
        directory = self.resolution.parent
        for entry in ('../0001-api.patch', str(self.resolution), 'link.patch'):
            with self.subTest(entry=entry):
                if entry == 'link.patch':
                    (directory / entry).symlink_to(self.resolution)
                (directory / 'series').write_text(entry + '\n')
                with self.assertRaises(forest.ForestError):
                    release.resolution_series(self.repo, 'patches/ports/next/series')

    def test_mechanical_merge_ignores_ambient_merge_rules(self):
        self.port_fixture()
        args = (self.repo, self.source.base, self.source.target, self.config['signal']['commit'])
        expected = release.mechanical_merge(*args)[:2]
        self.git('config', 'merge.default', 'union')
        try:
            with self.subTest(source='repository config'):
                self.assertEqual(release.mechanical_merge(*args)[:2], expected)
        finally:
            self.git('config', '--unset', 'merge.default')
        attributes = self.repo / '.git/info/attributes'
        attributes.write_text('existing.txt merge=union\n')
        try:
            with self.subTest(source='untracked attributes'):
                self.assertEqual(release.mechanical_merge(*args)[:2], expected)
        finally:
            attributes.unlink()
        with patch.dict(os.environ, {'GIT_CONFIG_PARAMETERS': "'merge.default=union'"}):
            with self.subTest(source='inherited Git config'):
                self.assertEqual(release.mechanical_merge(*args)[:2], expected)

    def test_stable_conflict_labels_allow_unchanged_resolution_after_line_shift(self):
        self.git('checkout', '--detach', '-q', self.source.base)
        lines = [f'unchanged line {number:02d}\n' for number in range(50)]
        (self.repo / 'existing.txt').write_text(''.join(lines))
        self.git('add', 'existing.txt')
        self.git('commit', '-qm', 'Old upstream context')
        base = self.git('rev-parse', 'HEAD')
        molly_lines = list(lines)
        molly_lines[25] = 'Molly API change\n'
        (self.repo / 'existing.txt').write_text(''.join(molly_lines))
        self.git('add', 'existing.txt')
        self.git('commit', '-qm', 'Molly API')
        molly = self.git('rev-parse', 'HEAD')
        self.git('checkout', '--detach', '-q', base)
        signal_lines = list(lines)
        signal_lines[25] = 'Signal API change\n'
        (self.repo / 'existing.txt').write_text(''.join(signal_lines))
        self.git('add', 'existing.txt')
        self.git('commit', '-qm', 'New Signal API')
        signal = self.git('rev-parse', 'HEAD')
        mechanical, conflicts, _ = release.mechanical_merge(self.repo, base, molly, signal)
        before = self.git('show', mechanical + ':existing.txt')
        self.assertIn('<<<<<<< MOLLY\n', before)
        self.assertIn('>>>>>>> SIGNAL\n', before)
        self.assertEqual(conflicts, {'existing.txt'})

        synthetic = self.git('commit-tree', mechanical, '-m', 'Mechanical conflict fixture')
        authored = self.home / 'author-offset-resolution'
        forest.git(self.repo, 'worktree', 'add', '--detach', str(authored), synthetic)
        resolved = list(lines)
        resolved[25] = 'Reviewed integrated API\n'
        (authored / 'existing.txt').write_text(''.join(resolved))
        forest.git(authored, 'add', 'existing.txt')
        forest.git(authored, 'commit', '-m', 'Resolve API conflict\n\nSource-Commit: ' + molly)
        mailbox = self.home / '0001-resolve.patch'
        original = git_utils.git(authored, 'format-patch', '--stdout', '--full-index',
                                 '-1', binary=True).stdout
        mailbox.write_bytes(original)

        prefix = ['unrelated upstream addition\n'] * 9
        (self.repo / 'existing.txt').write_text(''.join(prefix + signal_lines))
        self.git('add', 'existing.txt')
        self.git('commit', '-qm', 'Unrelated upstream lines before conflict')
        shifted_signal = self.git('rev-parse', 'HEAD')
        shifted_tree, shifted_conflicts, _ = release.mechanical_merge(
            self.repo, base, molly, shifted_signal)
        self.assertEqual(shifted_conflicts, conflicts)
        self.assertEqual(self.git('show', shifted_tree + ':existing.txt'), ''.join(prefix) + before)
        output = self.home / 'offset-build'
        release.apply_resolutions(self.repo, output, shifted_tree,
                                  [(mailbox, forest.mailbox(mailbox, mailbox.name))])
        self.assertEqual((output / 'existing.txt').read_text(), ''.join(prefix + resolved))
        self.assertEqual(mailbox.read_bytes(), original)
        verify_complete.clean_worktree(output)

    def test_mechanical_merge_honors_only_pinned_molly_attributes(self):
        self.port_fixture()
        signal = self.config['signal']['commit']
        self.git('checkout', '--detach', '-q', self.source.target)
        (self.repo / '.gitattributes').write_text('existing.txt merge=union\n')
        self.git('add', '.gitattributes')
        self.git('commit', '-qm', 'Pinned Molly merge attributes')
        molly = self.git('rev-parse', 'HEAD')
        # The committed rule must apply even though the caller's checkout has
        # since replaced it with the opposite rule.
        (self.repo / '.gitattributes').write_text('existing.txt -merge\n')
        _, conflicts, _ = release.mechanical_merge(self.repo, self.source.base, molly, signal)
        self.assertEqual(conflicts, {'mode.sh'})

    def test_missing_pinned_attribute_blob_cannot_silently_change_merge_rules(self):
        self.port_fixture()
        signal = self.config['signal']['commit']
        self.git('checkout', '--detach', '-q', self.source.target)
        (self.repo / '.gitattributes').write_text('existing.txt merge=union\n')
        self.git('add', '.gitattributes')
        self.git('commit', '-qm', 'Pinned Molly merge attributes')
        molly = self.git('rev-parse', 'HEAD')
        blob = self.git('rev-parse', molly + ':.gitattributes')
        # A partial clone can contain the tree entry without this blob. Git's
        # attribute lookup silently ignores it instead of failing the merge.
        (self.repo / '.git/objects' / blob[:2] / blob[2:]).unlink()
        with self.assertRaisesRegex(forest.ForestError, 'pinned merge attributes are unavailable'):
            release.mechanical_merge(self.repo, self.source.base, molly, signal)

    def test_sparse_source_still_materializes_every_target_file(self):
        self.git('sparse-checkout', 'set', '--no-cone', '/patches/', '/molly-build.json')
        self.assertFalse((self.repo / 'existing.txt').exists())
        source_config = (self.repo / '.git/config.worktree').read_bytes()
        result = self.assemble()
        output = Path(result['worktree'])
        for name in verify_complete.tree_entries(self.repo, self.source.target):
            path = output / os.fsdecode(name)
            self.assertTrue(path.exists() or path.is_symlink(), f'missing target path: {name!r}')
        self.assertEqual((self.repo / '.git/config.worktree').read_bytes(), source_config)
        self.assertEqual(self.git('config', '--bool', '--get', 'core.sparseCheckout'), 'true')
        for key in ('core.sparseCheckout', 'core.sparseCheckoutCone', 'index.sparse'):
            self.assertEqual(git_utils.git(output, 'config', '--bool', '--get', key).stdout.strip(),
                             'false')

    def test_sparse_port_output_has_persistent_dense_configuration(self):
        self.port_fixture()
        self.git('sparse-checkout', 'set', '--no-cone', '/patches/', '/molly-build.json')
        source_config = (self.repo / '.git/config.worktree').read_bytes()
        result = self.assemble()
        output = Path(result['worktree'])
        self.assertEqual(result['tree'], self.config['target_tree'])
        self.assertEqual(git_utils.git(output, 'config', '--bool', '--get',
                                      'core.sparseCheckout').stdout.strip(), 'false')
        self.assertEqual((self.repo / '.git/config.worktree').read_bytes(), source_config)
        for name in verify_complete.tree_entries(self.repo, self.config['target_tree']):
            path = output / os.fsdecode(name)
            self.assertTrue(path.exists() or path.is_symlink(), f'missing target path: {name!r}')

    def test_sparse_index_cannot_hide_missing_build_input(self):
        self.assemble()
        output = self.home / 'build'
        forest.git(output, 'update-index', '--skip-worktree', 'existing.txt')
        (output / 'existing.txt').unlink()
        with self.assertRaisesRegex(forest.ForestError, 'skip-worktree|sparse'):
            verify_complete.clean_worktree(output)


if __name__ == '__main__':
    unittest.main()
