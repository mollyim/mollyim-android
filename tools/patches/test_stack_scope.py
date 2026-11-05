import json
import pathlib
import subprocess
import tempfile
import unittest
from unittest import mock

import scope

WORKFLOW = '.github/workflows/molly-patch-release.yml'


class ScopeFixture(unittest.TestCase):
    """Molly's tree (MOLLY), a main that differs only in a repository file (base), Signal's tree
    (OLD) and a head that is Signal plus the pipeline, Molly's patches and Molly's repository files."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.repo = pathlib.Path(self.tmp.name)
        self.git('init', '-q')
        self.git('config', 'user.email', 'test@example.invalid')
        self.git('config', 'user.name', 'Test')
        self.write('app.txt', 'molly\n')
        self.write('.github/workflows/sync.yml', 'sync 1\n')
        self.write('README.md', 'readme\n')
        self.molly = self.commit('molly')
        self.write('.github/workflows/sync.yml', 'sync 2\n')
        self.base = self.commit('main')
        self.git('checkout', '-q', '--orphan', 'signal')
        self.git('rm', '-rfq', '.')
        self.write('app.txt', 'signal\n')
        self.write('.github/FUNDING.yml', 'signal funding\n')
        self.signal = self.commit('signal')
        self.config = {'signal': {'commit': self.signal}, 'molly': {'target': self.molly, 'tree': 'tree'}, 'port': None}
        self.head = self.head_on(self.base)
        self.forest = self.git('rev-parse', 'HEAD:patches/molly').strip()
        patches = [mock.patch.object(scope, 'MOLLY', self.molly), mock.patch.object(scope, 'OLD', self.signal),
                   mock.patch.object(scope, 'MOLLY_TREE', 'tree'), mock.patch.object(scope, 'FOREST', self.forest)]
        for patch in patches:
            patch.start()
            self.addCleanup(patch.stop)

    def head_on(self, base):
        """Signal's source plus the pipeline and Molly's patches, over the base's repository files."""
        self.reset(base)
        self.write('app.txt', 'signal\n')
        self.write('tools/patches/tool.py', 'tool\n')
        self.write('patches/molly/p', 'patch\n')
        self.write(WORKFLOW, 'name: Molly\n')
        self.write('molly-build.json', json.dumps(self.config))
        return self.commit('head')

    def git(self, *args):
        return subprocess.check_output(['git', '-C', str(self.repo), *args], text=True)

    def write(self, name, content):
        path = self.repo / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)

    def commit(self, message):
        self.git('add', '-A')
        self.git('commit', '-qm', message)
        return self.git('rev-parse', 'HEAD').strip()

    def reset(self, ref):
        self.git('checkout', '-q', '--detach', ref)


class ScopeDetectionTests(ScopeFixture):
    def test_scope_comes_from_the_trees_not_from_names(self):
        self.assertEqual(scope.detect_scope(self.repo, self.base), 'molly')
        self.assertEqual(scope.check_scope(self.repo, None, self.base)['scope'], 'molly')

    def test_base_with_patches_and_a_changed_port_is_a_port_pr(self):
        self.write('patches/ports/x/series', 'a\n')
        self.assertEqual(scope.detect_scope(self.repo, self.head, self.commit('port')), 'port')

    def test_base_with_patches_and_no_port_is_a_change(self):
        self.write('tools/patches/tool.py', 'tool 2\n')
        self.assertEqual(scope.detect_scope(self.repo, self.head, self.commit('tool')), 'change')

    def test_editing_an_existing_port_is_a_change(self):
        self.config['port'] = {'name': 'x'}
        self.write('molly-build.json', json.dumps(self.config))
        self.write('patches/ports/x/series', 'a\n')
        ported = self.commit('port')
        self.write('patches/ports/x/series', 'b\n')
        self.assertEqual(scope.detect_scope(self.repo, ported, self.commit('edit')), 'change')

    def test_head_without_patches_cannot_be_classified(self):
        with self.assertRaisesRegex(ValueError, 'cannot classify'):
            scope.detect_scope(self.repo, self.base, self.base)

    def test_explicit_scope_overrides_detection(self):
        with self.assertRaisesRegex(ValueError, 'base has no Molly patches'):
            scope.check_scope(self.repo, 'change', self.base)

    def test_change_scope_accepts_a_tool_edit(self):
        self.write('tools/patches/tool.py', 'tool 2\n')
        head = self.commit('tool')
        result = scope.check_scope(self.repo, None, self.head, head)
        self.assertEqual(result['scope'], 'change')


class BaseAcceptanceTests(ScopeFixture):
    def test_molly_and_molly_with_repo_file_changes_are_accepted(self):
        for base in (self.molly, self.base):
            head = self.head_on(base)
            self.assertEqual(scope.check_scope(self.repo, None, base, head)['scope'], 'molly')

    def test_base_needs_no_ancestry(self):
        # The signal commit is unrelated history; a base that is not an ancestor is still judged by content.
        self.git('checkout', '-q', '--orphan', 'other')
        self.git('rm', '-rfq', '.')
        self.write('app.txt', 'molly\n')
        self.write('.github/workflows/sync.yml', 'sync 3\n')
        unrelated = self.commit('unrelated main')
        head = self.head_on(unrelated)
        twin = self.git('commit-tree', unrelated + '^{tree}', '-m', 'same tree, unrelated history').strip()
        self.assertEqual(scope.check_scope(self.repo, None, twin, head)['scope'], 'molly')

    def test_base_with_an_app_source_change_is_rejected(self):
        self.reset(self.base)
        self.write('app.txt', 'edited\n')
        edited = self.commit('app edit')
        head = self.head_on(edited)
        with self.assertRaisesRegex(ValueError, 'base differs from the official Molly tree outside repository files: app.txt'):
            scope.check_scope(self.repo, None, edited, head)


class RepoFileTests(ScopeFixture):
    def test_repo_files_must_equal_the_base(self):
        for name in ('README.md', '.github/workflows/sync.yml', '.github/dependabot.yml'):
            self.reset(self.head)
            self.write(name, 'changed\n')
            head = self.commit('edit ' + name)
            with self.assertRaisesRegex(ValueError, 'repository file differs from the base: ' + name.replace('.', r'\.')):
                scope.check_scope(self.repo, 'molly', self.base, head)

    def test_the_molly_workflows_may_be_edited(self):
        for name in ('release.yml', 'reprocheck.yml', 'tagrelease.yml', 'test.yml'):
            self.write('.github/workflows/' + name, 'edited\n')
        self.assertEqual(scope.check_scope(self.repo, 'molly', self.base, self.commit('wrap'))['scope'], 'molly')

    def test_the_molly_source_action_may_be_added_but_no_other_action(self):
        self.write('.github/actions/molly-source/action.yml', 'action\n')
        self.assertEqual(scope.check_scope(self.repo, 'molly', self.base, self.commit('action'))['scope'], 'molly')
        self.write('.github/actions/other/action.yml', 'other\n')
        with self.assertRaisesRegex(ValueError, 'repository file differs from the base: .github/actions/other/action.yml'):
            scope.check_scope(self.repo, 'molly', self.base, self.commit('other action'))

    def test_signal_root_files_are_rejected(self):
        self.write('.github/FUNDING.yml', 'signal funding\n')
        with self.assertRaisesRegex(ValueError, 'FUNDING'):
            scope.check_scope(self.repo, 'molly', self.base, self.commit('funding'))

    def test_pipeline_workflow_must_be_regular_file(self):
        (self.repo / WORKFLOW).unlink()
        with self.assertRaisesRegex(ValueError, 'regular 100644'):
            scope.check_scope(self.repo, 'molly', self.base, self.commit('delete'))


class ProjectionTests(ScopeFixture):
    def test_head_source_must_equal_signal(self):
        self.write('app.txt', 'unreviewed\n')
        with self.assertRaisesRegex(ValueError, 'head source projection differs: app.txt'):
            scope.check_scope(self.repo, None, self.base, self.commit('edit'))

    def test_forest_must_match_the_snapshot(self):
        self.write('patches/molly/p', 'tampered\n')
        with self.assertRaisesRegex(ValueError, 'forest does not match'):
            scope.check_scope(self.repo, None, self.base, self.commit('tamper'))

    def test_pipeline_paths_outside_scope_are_rejected(self):
        self.write('patches/ports/x/series', 'a\n')
        with self.assertRaisesRegex(ValueError, 'outside scope'):
            scope.check_scope(self.repo, 'molly', self.base, self.commit('port'))


if __name__ == '__main__':
    unittest.main()
