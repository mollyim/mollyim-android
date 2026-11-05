"""Hermetic tests: real format-patch/am in tiny repositories; no network or SDK."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import forest


class ForestTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.home = Path(self.temp.name)
        self.repo = self.home / 'repo'
        self.repo.mkdir()
        self.root = self.home / 'patches'
        self.root.mkdir()
        self.git('init', '-q')
        self.git('config', 'user.name', 'Original Author')
        self.git('config', 'user.email', 'author@example.invalid')
        (self.repo / 'common').write_text('base\n')
        self.git('add', '.')
        self.git('commit', '-qm', 'base')
        self.base = self.git('rev-parse', 'HEAD')
        (self.root / 'base').write_text(self.base + '\n')
        self.patch_number = {}

    def git_env(self):
        env = forest.clean_git_env()
        env.update(GIT_CONFIG_NOSYSTEM='1', GIT_CONFIG_GLOBAL=os.devnull)
        return env

    def git(self, *args, binary=False):
        raw = subprocess.check_output(['git', '-C', str(self.repo), *args], env=self.git_env(), stderr=subprocess.PIPE)
        return raw if binary else raw.decode().strip()

    def add_feature(self, name, parent=None, depends=(), filename=None, content=None, message=None):
        self.git('checkout', '--detach', '-q', parent or self.base)
        filename = filename or name.replace('/', '-')
        target = self.repo / filename
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(content if content is not None else (name + '\n').encode())
        self.git('add', '.')
        self.git('commit', '-qm', message or ('Add ' + name))
        commit = self.git('rev-parse', 'HEAD')
        directory = self.root / name
        directory.mkdir(parents=True, exist_ok=True)
        n = self.patch_number.get(name, 0) + 1
        self.patch_number[name] = n
        patch_name = f'{n:04d}-change.patch'
        (directory / patch_name).write_bytes(self.git('format-patch', '--binary', '--full-index', '--stdout', '-1', binary=True))
        with (directory / 'series').open('a') as stream:
            stream.write(patch_name + '\n')
        if depends:
            (directory / 'depends').write_text('\n'.join(depends) + '\n')
        return commit

    def names(self, selected=None, reverse=False):
        return [f.name for f in forest.Forest(self.root).order(selected, reverse)]

    def test_independent_siblings_and_stable_orders(self):
        self.add_feature('beta'); self.add_feature('alpha')
        self.assertEqual(self.names(), ['alpha', 'beta'])
        self.assertEqual(self.names(reverse=True), ['beta', 'alpha'])
        self.assertEqual(self.names(['beta']), ['beta'])

    def test_series_order_not_filename_sort(self):
        self.add_feature('alpha')
        self.add_feature('alpha')
        (self.root / 'alpha/series').write_text('0002-change.patch\n0001-change.patch\n')
        self.assertEqual([Path(p).name for p in forest.Forest(self.root).patches()],
                         ['0002-change.patch', '0001-change.patch'])

    def test_nested_feature_inherits_entire_parent_series(self):
        first = self.add_feature('core')
        second = self.add_feature('core', parent=first, filename='second')
        self.add_feature('core/child', parent=second)
        self.add_feature('unrelated')
        f = forest.Forest(self.root)
        self.assertEqual(self.names(['core/child']), ['core', 'core/child'])
        self.assertEqual(len(f.patches(['core/child'])), 3)
        self.assertEqual(self.names(['core']), ['core'])
        result = forest.apply(f, self.repo, self.home / 'out', ['core/child'])
        self.assertEqual(result['status'], 'PASS')

    def test_diamond_dependency_deduplicates_shared_prefix(self):
        self.add_feature('common'); self.add_feature('left', depends=['common'])
        self.add_feature('right', depends=['common']); self.add_feature('integration', depends=['left','right'])
        self.assertEqual(self.names(['integration']), ['common','left','right','integration'])

    def test_cross_tree_edge(self):
        self.add_feature('first'); self.add_feature('second', depends=['first'])
        self.assertEqual(self.names(['second']), ['first','second'])

    def test_missing_dependency_and_missing_parent(self):
        self.add_feature('one', depends=['missing'])
        with self.assertRaisesRegex(forest.ForestError, 'unknown'): forest.Forest(self.root)
        (self.root / 'one/depends').unlink()
        self.add_feature('absent/child')
        with self.assertRaisesRegex(forest.ForestError, 'unknown'): forest.Forest(self.root)

    def test_cycles_including_implicit_parent(self):
        self.add_feature('parent', depends=['parent/child'])
        self.add_feature('parent/child')
        with self.assertRaisesRegex(forest.ForestError, 'cycle'): forest.Forest(self.root)

    def test_duplicate_dependency_and_redundant_parent(self):
        self.add_feature('parent'); self.add_feature('parent/child', depends=['parent'])
        with self.assertRaisesRegex(forest.ForestError, 'implicit'): forest.Forest(self.root)
        (self.root / 'parent/child/depends').write_text('parent\nparent\n')
        with self.assertRaisesRegex(forest.ForestError, 'duplicate'): forest.Forest(self.root)

    def test_comments_blank_lines_and_unknown_selection(self):
        self.add_feature('one')
        (self.root/'one/series').write_text('# ordered series\n\n0001-change.patch # keep this patch\n')
        self.assertEqual(self.names(), ['one'])
        with self.assertRaisesRegex(forest.ForestError, 'unknown feature'): self.names(['missing'])

    def test_empty_or_duplicate_series(self):
        self.add_feature('one')
        for content in ('# nothing\n', '0001-change.patch\n0001-change.patch\n'):
            (self.root/'one/series').write_text(content)
            with self.assertRaises(forest.ForestError): forest.Forest(self.root)

    def test_unlisted_patch_and_orphan_depends(self):
        self.add_feature('one')
        stray = self.root/'one/0002-stray.patch'
        shutil.copyfile(self.root/'one/0001-change.patch', stray)
        with self.assertRaisesRegex(forest.ForestError, 'unlisted'): forest.Forest(self.root)
        stray.unlink(); (self.root/'orphan').mkdir(); (self.root/'orphan/depends').write_text('one\n')
        with self.assertRaisesRegex(forest.ForestError, 'without'): forest.Forest(self.root)

    def test_series_paths_reject_traversal_absolute_paths_and_options(self):
        self.add_feature('one')
        for name in ('../0001-outside.patch','/tmp/0001-outside.patch','--abort','a/0001-other.patch','a\\b.patch'):
            (self.root/'one/series').write_text(name + '\n')
            with self.subTest(name=name), self.assertRaises(forest.ForestError): forest.Forest(self.root)

    def test_base_requires_full_commit_id(self):
        self.add_feature('one')
        for base in ('HEAD','abc123','0'*40+'\n'+'1'*40,'../bad'):
            (self.root/'base').write_text(base+'\n')
            with self.assertRaises(forest.ForestError): forest.Forest(self.root)

    def test_symlink_root_file_directory_and_broken_link_rejected(self):
        self.add_feature('one')
        alias = self.home/'alias'; alias.symlink_to(self.root, target_is_directory=True)
        with self.assertRaisesRegex(forest.ForestError, 'symlink'): forest.Forest(alias)
        for target in (self.root/'one', self.root/'one/series', self.home/'missing'):
            link = self.root/'link'; link.symlink_to(target)
            with self.assertRaisesRegex(forest.ForestError, 'symlink'): forest.Forest(self.root)
            link.unlink()

    def test_mailbox_single_message_and_required_headers(self):
        self.add_feature('one')
        p = self.root/'one/0001-change.patch'; raw=p.read_bytes()
        for bad in (b'not a mailbox', raw+raw, raw.replace(b'Subject:',b'Other:')):
            p.write_bytes(bad)
            with self.assertRaises(forest.ForestError): forest.Forest(self.root)

    def test_source_provenance_is_derived_from_message_not_diff(self):
        self.add_feature('one', message='Add one\n\nSource-Commit: '+self.base,
                         content=('Source-Commit: '+ '0'*40+'\n').encode())
        f=forest.Forest(self.root)
        self.assertEqual(next(iter(f.metadata.values()))['source_commit_shas'], [self.base])

    def test_invalid_source_provenance_rejected(self):
        self.add_feature('one', message='Add one\n\nSource-Commit: HEAD')
        with self.assertRaisesRegex(forest.ForestError, 'Source-Commit'): forest.Forest(self.root)

    def offset_fixture(self):
        """Two distant hunks, with enough unchanged text to shift either direction."""
        lines = [f'unique line {number:02d}\n' for number in range(60)]
        self.git('checkout', '--detach', '-q', self.base)
        (self.repo / 'common').write_text(''.join(lines))
        self.git('add', 'common')
        self.git('commit', '-qm', 'Context for offset tests')
        self.base = self.git('rev-parse', 'HEAD')
        (self.root / 'base').write_text(self.base + '\n')
        changed = list(lines)
        changed[20] = 'Molly first change\n'
        changed[40] = 'Molly second change\n'
        self.add_feature('feature', filename='common', content=''.join(changed).encode())
        mailbox = self.root / 'feature/0001-change.patch'
        original_mailbox = mailbox.read_bytes()
        self.git('checkout', '--detach', '-q', self.base)
        return lines, mailbox, original_mailbox

    def set_shifted_base(self, lines):
        (self.repo / 'common').write_text(''.join(lines))
        self.git('add', 'common')
        self.git('commit', '-qm', 'Upstream context change')
        shifted = self.git('rev-parse', 'HEAD')
        (self.root / 'base').write_text(shifted + '\n')
        return shifted

    def assert_unchanged_mailbox_applies(self, lines, mailbox, original_mailbox):
        self.set_shifted_base(lines)
        expected = ''.join(lines).replace('unique line 20\n', 'Molly first change\n')
        expected = expected.replace('unique line 40\n', 'Molly second change\n')
        out = self.home / 'out'
        result = forest.apply(forest.Forest(self.root), self.repo, out)
        self.assertEqual(result['status'], 'PASS')
        self.assertEqual((out / 'common').read_text(), expected)
        self.assertEqual(mailbox.read_bytes(), original_mailbox)
        self.assertEqual(forest.git(out, 'status', '--porcelain'), '')

    def test_positive_line_offsets_keep_original_mailbox_bytes(self):
        lines, mailbox, original_mailbox = self.offset_fixture()
        # Distinct offsets for the two hunks exercise context-based relocation,
        # not a global line-number adjustment.
        shifted = ['new upstream preamble\n'] * 12 + lines[:30]
        shifted += ['new upstream middle\n'] * 7 + lines[30:]
        self.assert_unchanged_mailbox_applies(shifted, mailbox, original_mailbox)

    def test_negative_line_offsets_keep_original_mailbox_bytes(self):
        lines, mailbox, original_mailbox = self.offset_fixture()
        shifted = lines[9:28] + lines[33:]
        self.assert_unchanged_mailbox_applies(shifted, mailbox, original_mailbox)

    def test_changed_context_is_not_discarded_as_fuzz(self):
        lines, mailbox, original_mailbox = self.offset_fixture()
        # This is the first context line of the first hunk. A fuzzy patcher
        # could drop it and apply, but Git must require the complete context.
        lines[17] = 'different upstream context\n'
        shifted = self.set_shifted_base(lines)
        out = self.home / 'out'
        with self.assertRaisesRegex(forest.ForestError, 'Worktree preserved'):
            forest.apply(forest.Forest(self.root), self.repo, out)
        self.assertEqual((out / 'common').read_text(), ''.join(lines))
        self.assertEqual(forest.git(out, 'rev-parse', 'HEAD'), shifted)
        self.assertIn('Molly first change', forest.git(out, 'am', '--show-current-patch'))
        self.assertEqual(mailbox.read_bytes(), original_mailbox)

    def test_ambient_threeway_cannot_resolve_changed_context(self):
        lines, mailbox, original_mailbox = self.offset_fixture()
        lines[17] = 'different upstream context\n'
        shifted = self.set_shifted_base(lines)
        self.git('config', 'am.threeWay', 'true')
        self.git('config', 'rerere.enabled', 'true')
        # Prove the fixture can merge cleanly with three-way enabled. The
        # runner must still stop, so a local setting cannot hide adaptation.
        merged = self.home / 'manual-threeway'
        forest.git(self.repo, 'worktree', 'add', '--detach', str(merged), shifted)
        forest.git(merged, 'am', '--3way', '--whitespace=nowarn', str(mailbox))
        self.assertIn('Molly first change', (merged / 'common').read_text())
        out = self.home / 'out'
        with self.assertRaisesRegex(forest.ForestError, 'Worktree preserved'):
            forest.apply(forest.Forest(self.root), self.repo, out)
        self.assertEqual((out / 'common').read_text(), ''.join(lines))
        self.assertEqual(mailbox.read_bytes(), original_mailbox)

    def test_ambient_ignore_whitespace_cannot_relax_context_matching(self):
        lines, mailbox, original_mailbox = self.offset_fixture()
        lines[17] = 'unique   line 17\n'
        self.set_shifted_base(lines)
        self.git('config', 'apply.ignoreWhitespace', 'change')
        out = self.home / 'out'
        with self.assertRaisesRegex(forest.ForestError, 'Worktree preserved'):
            forest.apply(forest.Forest(self.root), self.repo, out)
        self.assertEqual((out / 'common').read_text(), ''.join(lines))
        self.assertEqual(mailbox.read_bytes(), original_mailbox)

    def test_ambient_whitespace_fix_cannot_silently_rewrite_patch_bytes(self):
        self.add_feature('one', content=b'trailing space \n')
        self.git('config', 'apply.whitespace', 'fix')
        out = self.home / 'out'
        # The final whitespace check must fail, not accept an auto-fixed patch.
        with self.assertRaisesRegex(forest.ForestError, 'trailing whitespace'):
            forest.apply(forest.Forest(self.root), self.repo, out)
        self.assertEqual((out / 'one').read_bytes(), b'trailing space \n')

    def test_exact_expected_tree_preserves_and_reports_source_whitespace(self):
        commit = self.add_feature('one', content=b'trailing space \n')
        expected_tree = self.git('rev-parse', commit + '^{tree}')
        self.git('config', 'apply.whitespace', 'fix')
        out = self.home / 'out'
        result = forest.apply(forest.Forest(self.root), self.repo, out,
                              expected_tree=expected_tree)
        self.assertEqual(result['tree_sha'], expected_tree)
        self.assertIn('trailing whitespace', result['whitespace_report'])
        self.assertEqual((out / 'one').read_bytes(), b'trailing space \n')

    def test_expected_tree_mismatch_rejects_source_whitespace(self):
        self.add_feature('one', content=b'trailing space \n')
        expected_tree = self.git('rev-parse', self.base + '^{tree}')
        with self.assertRaisesRegex(forest.ForestError, 'tree mismatch'):
            forest.apply(forest.Forest(self.root), self.repo, self.home / 'out',
                         expected_tree=expected_tree)

    def test_expected_tree_requires_full_sha_before_creating_worktree(self):
        self.add_feature('one')
        out = self.home / 'out'
        for expected_tree in ('HEAD^{tree}', self.base[:7], ''):
            with self.subTest(expected_tree=expected_tree), self.assertRaisesRegex(forest.ForestError, 'full lowercase tree SHA'):
                forest.apply(forest.Forest(self.root), self.repo, out, expected_tree=expected_tree)
            self.assertFalse(out.exists())

    def test_apply_preserves_author_and_binary_bytes(self):
        data = bytes(range(256)) * 3
        commit = self.add_feature('binary', filename='data.bin', content=data)
        f=forest.Forest(self.root); result=forest.apply(f,self.repo,self.home/'out')
        self.assertEqual((self.home/'out/data.bin').read_bytes(),data)
        self.assertEqual(result['tree_sha'],self.git('rev-parse',commit+'^{tree}'))
        author=subprocess.check_output(['git','-C',str(self.home/'out'),'show','-s','--format=%an <%ae>'],
                                       text=True,env=self.git_env()).strip()
        self.assertEqual(author,'Original Author <author@example.invalid>')

    def test_apply_twice_exact_tree_without_identity_with_ambient_signing(self):
        self.add_feature('one')
        self.git('config','--unset','user.name'); self.git('config','--unset','user.email')
        self.git('config','commit.gpgSign','true'); self.git('config','gpg.program','/bin/false')
        f=forest.Forest(self.root)
        a=forest.apply(f,self.repo,self.home/'a'); b=forest.apply(f,self.repo,self.home/'b')
        self.assertEqual(a['tree_sha'],b['tree_sha'])
        self.assertEqual(self.git('config','commit.gpgSign'),'true')

    def test_does_not_change_dirty_source_head_index_or_working_files(self):
        self.add_feature('one'); before=self.git('rev-parse','HEAD')
        (self.repo/'common').write_text('staged\n'); self.git('add','common')
        (self.repo/'untracked').write_text('keep me\n')
        index=self.git('write-tree'); status=self.git('status','--porcelain')
        forest.apply(forest.Forest(self.root),self.repo,self.home/'out')
        self.assertEqual(self.git('rev-parse','HEAD'),before)
        self.assertEqual(self.git('write-tree'),index)
        self.assertEqual(self.git('status','--porcelain'),status)

    def test_apply_ignores_inherited_repository_and_index_overrides(self):
        expected = self.add_feature('one')
        expected_tree = self.git('rev-parse', expected + '^{tree}')
        self.git('checkout', '--detach', '-q', self.base)
        (self.repo / 'common').write_text('staged\n')
        self.git('add', 'common')
        (self.repo / 'common').write_text('unstaged\n')
        (self.repo / 'untracked').write_text('keep me\n')
        head = self.git('rev-parse', 'HEAD')
        index = (self.repo / '.git/index').read_bytes()
        status = self.git('status', '--porcelain')
        redirected_index = self.home / 'caller.index'
        redirected_index.write_bytes(index)
        overrides = {
            'GIT_DIR': str(self.repo / '.git'),
            'GIT_WORK_TREE': str(self.repo),
            'GIT_COMMON_DIR': str(self.repo / '.git'),
            'GIT_INDEX_FILE': str(redirected_index),
        }
        with patch.dict(os.environ, overrides):
            environment = dict(os.environ)
            result = forest.apply(forest.Forest(self.root), self.repo, self.home / 'out')
            self.assertEqual(dict(os.environ), environment)
        self.assertEqual(result['tree_sha'], expected_tree)
        self.assertEqual((self.home / 'out/one').read_text(), 'one\n')
        self.assertEqual(self.git('rev-parse', 'HEAD'), head)
        self.assertEqual((self.repo / '.git/index').read_bytes(), index)
        self.assertEqual(redirected_index.read_bytes(), index)
        self.assertEqual(self.git('status', '--porcelain'), status)
        self.assertEqual((self.repo / 'common').read_text(), 'unstaged\n')
        self.assertEqual((self.repo / 'untracked').read_text(), 'keep me\n')

    def test_verify_ignores_inherited_object_and_history_overrides(self):
        expected = self.add_feature('one')
        expected_tree = self.git('rev-parse', expected + '^{tree}')
        overrides = {
            'GIT_OBJECT_DIRECTORY': str(self.home / 'missing-objects'),
            'GIT_ALTERNATE_OBJECT_DIRECTORIES': str(self.home / 'missing-alternates'),
            'GIT_GRAFT_FILE': str(self.home / 'missing-grafts'),
            'GIT_SHALLOW_FILE': str(self.home / 'missing-shallow'),
            'GIT_PREFIX': 'caller/',
            'GIT_NAMESPACE': 'caller',
            'GIT_IMPLICIT_WORK_TREE': '0',
            'GIT_LFS_SKIP_SMUDGE': '1',
            'GIT_CONFIG_NOSYSTEM': '1',
            'GIT_CONFIG_GLOBAL': os.devnull,
        }
        with patch.dict(os.environ, overrides):
            environment = dict(os.environ)
            result = forest.verify(forest.Forest(self.root), self.repo)
            self.assertEqual(dict(os.environ), environment)
            self.assertEqual(forest.clean_git_env()['GIT_LFS_SKIP_SMUDGE'], '1')
            self.assertEqual(forest.clean_git_env()['GIT_CONFIG_GLOBAL'], os.devnull)
        self.assertEqual(result['closures'], {'one': expected_tree})
        self.assertEqual(result['full_orders'], [expected_tree, expected_tree])
        self.assertEqual(self.git('worktree', 'list', '--porcelain').count('worktree '), 1)

    def test_destination_guards(self):
        self.add_feature('one'); f=forest.Forest(self.root)
        existing=self.home/'existing';existing.mkdir();(existing/'keep').write_text('keep')
        alias=self.home/'alias';alias.symlink_to(self.home/'missing')
        for dest in (existing,alias,self.repo/'inside',self.home/'x/../outside'):
            with self.subTest(dest=dest),self.assertRaisesRegex(forest.ForestError,'REFUSE'):
                forest.apply(f,self.repo,dest)
        self.assertEqual((existing/'keep').read_text(),'keep')

    def test_shared_sparse_config_is_not_changed_to_materialize_a_worktree(self):
        self.add_feature('one')
        self.git('config', 'core.sparseCheckout', 'true')
        before = (self.repo / '.git/config').read_bytes()
        out = self.home / 'out'
        with self.assertRaisesRegex(forest.ForestError, 'extensions.worktreeConfig=true'):
            forest.apply(forest.Forest(self.root), self.repo, out)
        self.assertFalse(out.exists())
        self.assertEqual((self.repo / '.git/config').read_bytes(), before)

    def test_patch_changed_after_validation_refused(self):
        self.add_feature('one'); f=forest.Forest(self.root)
        p=self.root/'one/0001-change.patch';p.write_bytes(p.read_bytes()+b'changed\n')
        with self.assertRaisesRegex(forest.ForestError,'changed after validation'):
            forest.apply(f,self.repo,self.home/'out')

    def test_strict_conflict_leaves_am_state_for_inspection(self):
        self.add_feature('alpha',filename='common',content=b'alpha\n')
        self.add_feature('beta',filename='common',content=b'beta\n')
        self.git('config','am.threeWay','true'); self.git('config','rerere.enabled','true')
        out=self.home/'out'
        with self.assertRaisesRegex(forest.ForestError,'Worktree preserved'):
            forest.apply(forest.Forest(self.root),self.repo,out)
        shown=subprocess.run(['git','-C',str(out),'am','--show-current-patch'],
                             capture_output=True,env=self.git_env())
        self.assertEqual(shown.returncode,0)
        self.assertEqual((out/'common').read_text(),'alpha\n')

    def test_verify_isolated_closures_and_reverse_full_order(self):
        self.add_feature('alpha'); self.add_feature('beta'); self.add_feature('beta/child')
        result=forest.verify(forest.Forest(self.root),self.repo)
        self.assertEqual(len(result['closures']),3)
        self.assertEqual(result['full_orders'][0],result['full_orders'][1])
        self.assertEqual(self.git('worktree','list','--porcelain').count('worktree '),1)

    def test_verify_detects_hidden_dependency_and_cleans_own_worktrees(self):
        first=self.add_feature('alpha',filename='common',content=b'alpha\n')
        self.add_feature('beta',parent=first,filename='common',content=b'beta\n')
        with self.assertRaises(forest.ForestError): forest.verify(forest.Forest(self.root),self.repo)
        self.assertEqual(self.git('worktree','list','--porcelain').count('worktree '),1)
        (self.root/'beta/depends').write_text('alpha\n')
        self.assertEqual(forest.verify(forest.Forest(self.root),self.repo)['status'],'PASS')

    def test_carrier_state_cannot_enter_release(self):
        self.add_feature('one',filename='.hangar/state',content=b'carrier\n')
        with self.assertRaisesRegex(forest.ForestError,'hangar'):
            forest.apply(forest.Forest(self.root),self.repo,self.home/'out')

    def test_real_repository_patch_catalog_is_complete(self):
        root=Path(__file__).resolve().parents[2]/'patches/molly'
        if not root.exists(): self.skipTest('no checkout patch fixture')
        f=forest.Forest(root)
        self.assertEqual(f.base, 'c47a74ba0961903eeaeda6d22347a990056c3e83')
        self.assertEqual(len(f.patches()),65)
        self.assertEqual(len(f.features),16)


if __name__ == '__main__':
    unittest.main()
