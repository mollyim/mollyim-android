"""Small real-Git fixtures for complete source reconstruction and its negative cases."""
import hashlib
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import forest
import export_complete
import git_utils
import verify_complete


class CompleteTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.home = Path(self.temp.name)
        self.repo = self.home / 'repo'
        self.repo.mkdir()
        self.git('init', '-q')
        self.git('config', 'user.name', 'Fixture Author')
        self.git('config', 'user.email', 'fixture@example.invalid')
        (self.repo / 'existing.txt').write_text('old text\n')
        (self.repo / 'delete.txt').write_text('remove me\n')
        (self.repo / 'mode.sh').write_text('#!/bin/sh\nexit 0\n')
        (self.repo / 'binary.bin').write_bytes(b'\0old\xff')
        self.git('add', '.')
        self.git('commit', '-qm', 'common ancestor')
        self.ancestor = self.git('rev-parse', 'HEAD')
        (self.repo / 'upstream.txt').write_text('Signal input\n')
        self.git('add', '.')
        self.git('commit', '-qm', 'Signal base')
        self.base = self.git('rev-parse', 'HEAD')
        molly_parent = self.git('commit-tree', f'{self.ancestor}^{{tree}}',
                                '-p', self.ancestor, '-m', 'Molly lineage')
        self.merge = self.git('commit-tree', f'{self.base}^{{tree}}',
                               '-p', molly_parent, '-p', self.base, '-m', 'Import Signal')
        self.git('checkout', '--detach', '-q', self.merge)
        (self.repo / 'existing.txt').write_text('target text\n')
        (self.repo / 'new.txt').write_text('target whitespace retained  \n')
        (self.repo / 'delete.txt').unlink()
        (self.repo / 'mode.sh').chmod(0o755)
        (self.repo / 'binary.bin').write_bytes(b'\0new\xfe\xff')
        (self.repo / 'link').symlink_to('existing.txt')
        self.odd_path = 'space and\nnewline.txt'
        (self.repo / self.odd_path).write_text('path names are data\n')
        self.git('add', '.')
        self.git('commit', '-qm', 'Molly target')
        self.target = self.git('rev-parse', 'HEAD')
        self.target_tree = self.git('rev-parse', 'HEAD^{tree}')

    def git(self, *args, binary=False, index_file=None):
        env = forest.clean_git_env()
        env.update(GIT_CONFIG_NOSYSTEM='1', GIT_CONFIG_GLOBAL=os.devnull)
        result = git_utils.git(self.repo, '-c', 'core.hooksPath=/dev/null',
                            '-c', 'commit.gpgSign=false', *args,
                            env=env, binary=binary, index_file=index_file)
        return result.stdout if binary else result.stdout.strip()

    def make_forest(self, groups=None, name='patches'):
        groups = groups or [('text', ['existing.txt', 'new.txt', self.odd_path]),
                            ('assets', ['delete.txt', 'mode.sh', 'binary.bin', 'link'])]
        root = self.home / name
        root.mkdir()
        for filename, value in (('base', self.base), ('target', self.target),
                                ('upstream-merge', self.merge)):
            (root / filename).write_text(value + '\n')
        before = self.base
        for feature, paths in groups:
            index_file = self.home / f'{name}-{feature}-index'
            self.git('read-tree', before, index_file=index_file)
            for path in paths:
                entry = self.git('ls-tree', '-z', self.target, '--', path, binary=True)
                if entry:
                    metadata = entry.split(b'\t', 1)[0].split()
                    self.git('update-index', '--add', '--cacheinfo',
                             f'{metadata[0].decode()},{metadata[2].decode()},{path}',
                             index_file=index_file)
                else:
                    self.git('update-index', '--force-remove', '--', path, index_file=index_file)
            tree = self.git('write-tree', index_file=index_file)
            exported = self.git('commit-tree', tree, '-p', before, '-m',
                                f'Export {feature} snapshot\n\nSource-Commit: {self.target}')
            directory = root / feature
            directory.mkdir()
            (directory / '0001-snapshot.patch').write_bytes(
                self.git('format-patch', '--binary', '--full-index', '--stdout', '-1', exported, binary=True))
            (directory / 'series').write_text('0001-snapshot.patch\n')
            before = exported
        return root

    def make_port_source(self):
        """Create a real two-parent port with carryovers, adaptations and absorption."""
        origin_base, origin_target = self.base, self.target
        self.git('checkout', '--detach', '-q', origin_base)
        # Mode-only upstream changes must prevent a false carryover classification.
        (self.repo / 'existing.txt').chmod(0o755)
        (self.repo / 'binary.bin').write_bytes(b'\0upstream\xfe')
        (self.repo / 'new.txt').write_text('target whitespace retained  \n')
        (self.repo / 'upstream-only.txt').write_text('new upstream path pruned by port\n')
        self.git('add', '.')
        self.git('commit', '-qm', 'New upstream')
        new_base = self.git('rev-parse', 'HEAD')
        self.git('checkout', '--detach', '-q', origin_target)
        # An unchanged base does not make a changed port endpoint a carryover.
        (self.repo / self.odd_path).unlink()
        (self.repo / self.odd_path).symlink_to('existing.txt')
        appearance = self.repo / 'core/ui/ported-theme.kt'
        appearance.parent.mkdir(parents=True)
        appearance.write_text('new port integration\n')
        self.git('add', '.')
        tree = self.git('write-tree')
        target = self.git('commit-tree', tree, '-p', origin_target, '-p', new_base,
                          '-m', 'Port Molly to new Signal')
        self.git('checkout', '--detach', '-q', target)
        return {'base': new_base, 'target': target, 'upstream_merge': target,
                'origin_base': origin_base, 'origin_target': origin_target}

    def test_port_batches_classify_whole_entries_and_preserve_caller_state(self):
        source = self.make_port_source()
        expected = {
            b'delete.txt': 'carryover', b'mode.sh': 'carryover', b'link': 'carryover',
            b'existing.txt': 'adapted', b'binary.bin': 'adapted',
            os.fsencode(self.odd_path): 'adapted', b'upstream-only.txt': 'adapted',
            b'core/ui/ported-theme.kt': 'adapted',
        }
        actual = verify_complete.origin_categories(
            self.repo, source['base'], source['target'], source['origin_base'], source['origin_target'])
        self.assertEqual(actual, expected)
        self.assertNotIn(b'new.txt', actual)  # Already absorbed by the new upstream.
        (self.repo / 'existing.txt').write_text('caller staged state\n')
        self.git('add', 'existing.txt')
        (self.repo / 'existing.txt').write_text('caller unstaged state\n')
        original_index = (self.repo / '.git/index').read_bytes()
        original_status = self.git('status', '--porcelain=v1', '-z', binary=True)
        caller_index = self.home / 'port-caller-index'
        caller_index.write_bytes(b'caller-owned bytes')
        outputs = [self.home / 'port-one', self.home / 'port-two']
        overrides = {'GIT_DIR': str(self.repo / '.git'), 'GIT_WORK_TREE': str(self.repo),
                     'GIT_INDEX_FILE': str(caller_index), 'GIT_COMMON_DIR': str(self.repo / '.git'),
                     'GIT_OBJECT_DIRECTORY': str(self.repo / '.git/objects')}
        with patch.dict(os.environ, overrides), patch.object(export_complete, 'MAX_BATCH_PATHS', 2):
            environment = dict(os.environ)
            exported = export_complete.export(self.repo, output_path=outputs[0], **source)
            export_complete.export(self.repo, output_path=outputs[1], **source)
            result = verify_complete.verify(outputs[0], self.repo)
            self.assertEqual(dict(os.environ), environment)
        snapshots = [{path.relative_to(root).as_posix(): path.read_bytes()
                      for path in root.rglob('*') if path.is_file()} for root in outputs]
        self.assertEqual(snapshots[0], snapshots[1])
        self.assertEqual(exported['changed_paths'], 8)
        self.assertEqual(exported['patch_count'], 5)
        self.assertEqual(exported['groups']['build-and-release']['partitions'], {
            'carryover': {'paths': 3, 'patches': 2}, 'adapted': {'paths': 4, 'patches': 2}})
        self.assertEqual(result['features']['appearance']['partitions'], {
            'carryover': {'changed_paths': 0, 'patches': 0},
            'adapted': {'changed_paths': 1, 'patches': 1}})
        self.assertEqual(result['origin_target'], source['origin_target'])
        self.assertEqual([entry['tree'] for entry in result['replays']],
                         [self.git('rev-parse', source['target'] + '^{tree}')] * 2)
        data = forest.Forest(outputs[0])
        self.assertEqual(data.features['build-and-release'].patches, (
            'build-and-release/0001-carryover.patch', 'build-and-release/0002-carryover.patch',
            'build-and-release/0003-adapted.patch', 'build-and-release/0004-adapted.patch'))
        self.assertTrue(all(record['source_commit_shas'] == [source['target']]
                            for record in data.metadata.values()))
        self.assertEqual(self.git('rev-parse', 'HEAD'), source['target'])
        self.assertEqual((self.repo / '.git/index').read_bytes(), original_index)
        self.assertEqual(self.git('status', '--porcelain=v1', '-z', binary=True), original_status)
        self.assertEqual(caller_index.read_bytes(), b'caller-owned bytes')

    def test_port_category_cannot_be_relabelled_even_when_target_tree_matches(self):
        source = self.make_port_source()
        root = self.home / 'mislabelled-port'
        export_complete.export(self.repo, output_path=root, **source)
        feature = root / 'appearance'
        original = feature / '0001-adapted.patch'
        (feature / '0001-carryover.patch').write_bytes(
            original.read_bytes().replace(b'Port-Category: adapted', b'Port-Category: carryover'))
        original.unlink()
        (feature / 'series').write_text('0001-carryover.patch\n')
        with self.assertRaisesRegex(forest.ForestError, 'carryover classification does not match'):
            verify_complete.verify(root, self.repo)

    def test_port_pins_and_trailers_are_required_and_must_agree(self):
        source = self.make_port_source()
        root = self.home / 'provenance-port'
        export_complete.export(self.repo, output_path=root, **source)
        for names in (('origin-base',), ('origin-base', 'origin-target')):
            with self.subTest(missing=names):
                saved = {name: (root / name).read_bytes() for name in names}
                for name in names:
                    (root / name).unlink()
                with self.assertRaisesRegex(forest.ForestError, 'origin-base and origin-target'):
                    verify_complete.verify(root, self.repo)
                for name, contents in saved.items():
                    (root / name).write_bytes(contents)
        mailbox = root / 'appearance/0001-adapted.patch'
        contents = mailbox.read_bytes()
        mailbox.write_bytes(contents.replace(
            f'Origin-Target: {source["origin_target"]}'.encode(),
            f'Origin-Target: {source["target"]}'.encode()))
        with self.assertRaisesRegex(forest.ForestError, 'Origin-Target must match'):
            verify_complete.verify(root, self.repo)
        mailbox.write_bytes(contents)
        (root / 'origin-target').write_text(self.target_tree + '\n')
        with self.assertRaisesRegex(forest.ForestError, 'origin-target must resolve to a commit'):
            verify_complete.verify(root, self.repo)

    def test_port_origin_validation_precedes_output_creation(self):
        source = self.make_port_source()
        output = self.home / 'invalid-origin-port'
        for missing in ('origin_base', 'origin_target'):
            args = dict(source)
            del args[missing]
            with self.assertRaisesRegex(forest.ForestError, 'must be supplied together'):
                export_complete.export(self.repo, output_path=output, **args)
            self.assertFalse(output.exists())
        unrelated = self.git('commit-tree', self.target_tree, '-m', 'Unrelated source')
        args = dict(source, origin_target=unrelated)
        with self.assertRaisesRegex(forest.ForestError, 'origin-base must be an ancestor'):
            export_complete.export(self.repo, output_path=output, **args)
        self.assertFalse(output.exists())

    def test_port_path_cannot_be_split_between_mailboxes(self):
        source = self.make_port_source()
        root = self.home / 'split-port'
        export_complete.export(self.repo, output_path=root, **source)
        name = 'core/ui/ported-theme.kt'
        final_contents = (self.repo / name).read_bytes()
        self.git('checkout', '--detach', '-q', source['base'])
        path = self.repo / name
        path.parent.mkdir(parents=True, exist_ok=True)
        message = (f'Port intermediate\n\nSource-Commit: {source["target"]}\n'
                   f'Origin-Base: {source["origin_base"]}\n'
                   f'Origin-Target: {source["origin_target"]}\nPort-Category: adapted')
        for number, contents in enumerate((b'intermediate port state\n', final_contents), start=1):
            path.write_bytes(contents)
            self.git('add', name)
            self.git('commit', '-qm', message)
            (root / f'appearance/{number:04d}-adapted.patch').write_bytes(
                self.git('format-patch', '--binary', '--full-index', '--stdout', '-1', 'HEAD', binary=True))
        (root / 'appearance/series').write_text('0001-adapted.patch\n0002-adapted.patch\n')
        with self.assertRaisesRegex(forest.ForestError, 'port path belongs to multiple mailboxes'):
            verify_complete.verify(root, self.repo)

    def test_exact_tree_covers_all_change_types_and_preserves_source(self):
        root = self.make_forest()
        (self.repo / 'existing.txt').write_text('local staged change\n')
        self.git('add', 'existing.txt')
        (self.repo / 'existing.txt').write_text('local unstaged change\n')
        (self.repo / 'untracked').write_text('leave this alone\n')
        index = self.repo / '.git/index'
        original_status = self.git('status', '--porcelain=v1', '-z', binary=True)
        original_index = hashlib.sha256(index.read_bytes()).hexdigest()
        private_index = self.home / 'caller-index'
        private_index.write_bytes(b'caller-owned private index')
        overrides = {'GIT_DIR': str(self.repo / '.git'), 'GIT_WORK_TREE': str(self.repo),
                     'GIT_COMMON_DIR': str(self.repo / '.git'), 'GIT_INDEX_FILE': str(private_index),
                     'GIT_OBJECT_DIRECTORY': str(self.repo / '.git/objects')}
        with patch.dict(os.environ, overrides):
            result = verify_complete.verify(root, self.repo)
        self.assertEqual([entry['tree'] for entry in result['replays']], [self.target_tree] * 2)
        self.assertEqual(result['changed_paths'], 7)
        self.assertEqual(set(result['features']), {'assets', 'text'})
        self.assertIn('trailing whitespace', result['replays'][0]['whitespace_report'])
        self.assertEqual(self.git('rev-parse', 'HEAD'), self.target)
        self.assertEqual(hashlib.sha256(index.read_bytes()).hexdigest(), original_index)
        self.assertEqual(self.git('status', '--porcelain=v1', '-z', binary=True), original_status)
        self.assertEqual(private_index.read_bytes(), b'caller-owned private index')
        self.assertEqual(self.git('worktree', 'list', '--porcelain').count('worktree '), 1)

    def test_requested_worktree_is_retained_with_the_exact_target(self):
        root = self.make_forest()
        destination = self.home / 'complete-output'
        original_index = (self.repo / '.git/index').read_bytes()
        result = verify_complete.verify(root, self.repo, destination)
        self.assertEqual(result['worktree'], str(destination))
        self.assertTrue(destination.is_dir())
        self.assertEqual(git_utils.git(destination, 'rev-parse', 'HEAD^{tree}').stdout.strip(),
                         self.target_tree)
        self.assertEqual((self.repo / '.git/index').read_bytes(), original_index)
        self.assertEqual(self.git('worktree', 'list', '--porcelain').count('worktree '), 2)

    def test_requested_output_never_overlaps_a_temporary_replay(self):
        root = self.make_forest()
        destination = self.home / 'complete-output'
        existing = self.home / 'existing-builder'
        self.git('worktree', 'add', '--detach', str(existing), self.base)
        original = forest.apply
        counts = []

        def observed(*args, **kwargs):
            result = original(*args, **kwargs)
            counts.append(self.git('worktree', 'list', '--porcelain').count('worktree '))
            return result

        with patch.object(forest, 'apply', side_effect=observed):
            result = verify_complete.verify(root, self.repo, destination)
        self.assertEqual(counts, [3, 3])
        self.assertEqual([row['order'] for row in result['replays']], ['ready', 'reverse-ready'])
        self.assertEqual(git_utils.git(destination, 'rev-parse', 'HEAD^{tree}').stdout.strip(), self.target_tree)

    def test_export_is_repeatable_and_does_not_use_the_callers_index(self):
        (self.repo / 'existing.txt').write_text('staged caller edit\n')
        self.git('add', 'existing.txt')
        (self.repo / 'existing.txt').write_text('unstaged caller edit\n')
        original_status = self.git('status', '--porcelain=v1', '-z', binary=True)
        original_index = (self.repo / '.git/index').read_bytes()
        private_index = self.home / 'export-caller-index'
        private_index.write_bytes(b'not an index for the exporter')
        first, second = self.home / 'export-one', self.home / 'export-two'
        with patch.dict(os.environ, {'GIT_DIR': str(self.repo / '.git'),
                                     'GIT_INDEX_FILE': str(private_index)}):
            with patch.object(export_complete, 'MAX_BATCH_PATHS', 2):
                one = export_complete.export(self.repo, self.base, self.target, self.merge, first)
                export_complete.export(self.repo, self.base, self.target, self.merge, second)
        snapshots = [{path.relative_to(root).as_posix(): path.read_bytes()
                      for path in root.rglob('*') if path.is_file()} for root in (first, second)]
        self.assertEqual(snapshots[0], snapshots[1])
        self.assertGreater(one['patch_count'], 1)
        with self.assertRaisesRegex(export_complete.ExportError, 'output already exists'):
            export_complete.export(self.repo, self.base, self.target, self.merge, first)
        self.assertEqual(verify_complete.verify(first, self.repo)['expected_tree'], self.target_tree)
        self.assertEqual(self.git('rev-parse', 'HEAD'), self.target)
        self.assertEqual((self.repo / '.git/index').read_bytes(), original_index)
        self.assertEqual(self.git('status', '--porcelain=v1', '-z', binary=True), original_status)
        self.assertEqual(private_index.read_bytes(), b'not an index for the exporter')

    def test_existing_requested_destination_is_refused_and_left_intact(self):
        root = self.make_forest()
        destination = self.home / 'existing'
        destination.mkdir()
        marker = destination / 'keep'
        marker.write_text('caller data\n')
        with self.assertRaisesRegex(forest.ForestError, 'destination exists'):
            verify_complete.verify(root, self.repo, destination)
        self.assertEqual(marker.read_text(), 'caller data\n')
        self.assertEqual(self.git('worktree', 'list', '--porcelain').count('worktree '), 1)

    def test_ignored_extra_file_is_rejected_and_requested_output_is_preserved(self):
        root = self.make_forest()
        destination = self.home / 'extra-output'
        ignores = self.home / 'ignore-patterns'
        ignores.write_text('unexpected-cache\n')
        self.git('config', 'core.excludesFile', str(ignores))
        original_apply = forest.apply

        def with_extra(*args, **kwargs):
            result = original_apply(*args, **kwargs)
            (Path(args[2]) / 'unexpected-cache').write_text('extra bytes\n')
            self.assertEqual(git_utils.git(args[2], 'status', '--porcelain').stdout, '')
            return result

        with patch.object(forest, 'apply', side_effect=with_extra):
            with self.assertRaisesRegex(forest.ForestError, 'unexpected untracked files.*unexpected-cache'):
                verify_complete.verify(root, self.repo, destination)
        self.assertEqual((destination / 'unexpected-cache').read_text(), 'extra bytes\n')

    def test_tracked_worktree_and_index_changes_are_rejected(self):
        root = self.make_forest()
        original_apply = forest.apply
        for staged in (False, True):
            with self.subTest(staged=staged):
                def with_change(*args, **kwargs):
                    result = original_apply(*args, **kwargs)
                    (Path(args[2]) / 'existing.txt').write_text('unexpected edit\n')
                    if staged:
                        git_utils.git(args[2], 'add', 'existing.txt')
                    return result

                with patch.object(forest, 'apply', side_effect=with_change):
                    with self.assertRaisesRegex(forest.ForestError, 'differ from the reconstructed tree.*existing.txt'):
                        verify_complete.verify(root, self.repo)
                self.assertEqual(self.git('worktree', 'list', '--porcelain').count('worktree '), 1)

    def test_missing_deletion_cannot_pass_complete_replay(self):
        root = self.make_forest([('text', ['existing.txt', 'new.txt', self.odd_path]),
                                 ('assets', ['mode.sh', 'binary.bin', 'link'])])
        with self.assertRaisesRegex(forest.ForestError, 'tree mismatch'):
            verify_complete.verify(root, self.repo)
        self.assertEqual(self.git('worktree', 'list', '--porcelain').count('worktree '), 1)
        destination = self.home / 'failed-output'
        with self.assertRaisesRegex(forest.ForestError, 'Worktree preserved at'):
            verify_complete.verify(root, self.repo, destination)
        self.assertTrue(destination.is_dir())
        self.assertEqual(self.git('worktree', 'list', '--porcelain').count('worktree '), 2)

    def test_mode_and_binary_changes_are_required(self):
        for missing in ('mode.sh', 'binary.bin'):
            with self.subTest(missing=missing):
                root = self.make_forest([('all', ['existing.txt', 'new.txt', self.odd_path,
                                                  'delete.txt', 'link',
                                                  *[p for p in ('mode.sh', 'binary.bin') if p != missing]])],
                                        name=missing)
                with self.assertRaisesRegex(forest.ForestError, 'tree mismatch'):
                    verify_complete.verify(root, self.repo)

    def test_ancestor_base_must_be_the_recorded_signal_merge_parent(self):
        root = self.make_forest()
        (root / 'base').write_text(self.ancestor + '\n')
        with self.assertRaisesRegex(forest.ForestError, 'second parent'):
            verify_complete.verify(root, self.repo)

    def test_target_and_patch_provenance_must_agree(self):
        root = self.make_forest()
        alternate = self.git('commit-tree', self.target_tree, '-p', self.target, '-m', 'Another snapshot')
        (root / 'target').write_text(alternate + '\n')
        with self.assertRaisesRegex(forest.ForestError, 'Source-Commit'):
            verify_complete.verify(root, self.repo)

    def test_pin_must_be_a_commit_and_merge_must_have_two_parents(self):
        root = self.make_forest()
        (root / 'target').write_text(self.target_tree + '\n')
        with self.assertRaisesRegex(forest.ForestError, 'target must resolve to a commit'):
            verify_complete.verify(root, self.repo)
        (root / 'target').write_text(self.target + '\n')
        (root / 'upstream-merge').write_text(self.base + '\n')
        with self.assertRaisesRegex(forest.ForestError, 'exactly two parents'):
            verify_complete.verify(root, self.repo)

    def test_cross_feature_overlap_is_rejected_even_when_final_tree_matches(self):
        root = self.make_forest([('first', ['existing.txt']),
                                 ('second', ['new.txt', self.odd_path, 'delete.txt', 'mode.sh', 'binary.bin', 'link'])])
        # Both features ultimately touch existing.txt, while their combined tree remains exact.
        first = root / 'first/0001-snapshot.patch'
        first.write_bytes(first.read_bytes().replace(b'+target text\n', b'+intermediate text\n'))
        second = root / 'second'
        before = self.git('commit-tree', self.target_tree, '-p', self.base, '-m', 'Snapshot before overlap')
        self.git('checkout', '--detach', '-q', before)
        (self.repo / 'existing.txt').write_text('intermediate text\n')
        self.git('add', 'existing.txt')
        self.git('commit', '-qm', 'Intermediate')
        intermediate = self.git('rev-parse', 'HEAD')
        end = self.git('commit-tree', self.target_tree, '-p', intermediate,
                       '-m', f'Restore target text\n\nSource-Commit: {self.target}')
        (second / '0002-shared.patch').write_bytes(
            self.git('format-patch', '--binary', '--full-index', '--stdout', '-1', end, binary=True))
        (second / 'series').write_text('0001-snapshot.patch\n0002-shared.patch\n')
        (second / 'depends').write_text('first\n')
        self.git('checkout', '--detach', '-q', self.target)
        with self.assertRaisesRegex(forest.ForestError, 'path belongs to both'):
            verify_complete.verify(root, self.repo)


if __name__ == '__main__':
    unittest.main()
