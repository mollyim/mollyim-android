import unittest
import port_report


class PortReportTests(unittest.TestCase):
    def test_current_snapshot_uses_original_patchset_even_when_incomplete(self):
        import pathlib
        import subprocess
        import tempfile
        from unittest.mock import patch
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory)
            def run(*args):
                return subprocess.check_output(['git', '-C', directory, *args])
            def tree():
                run('add', '.')
                return run('write-tree').decode().strip()
            run('init', '-q')
            run('config', 'user.name', 'Test')
            run('config', 'user.email', 'test@example.invalid')
            (path / 'file').write_text('old\n')
            run('add', '.')
            run('commit', '-qm', 'old')
            commit = run('rev-parse', 'HEAD').decode().strip()
            old = tree()
            (path / 'file').write_text('molly\nextra\n')
            molly = tree()
            (path / 'file').write_text('mechanical\nother\nmore\n')
            mechanical = tree()
            (path / 'file').write_text('changed\nother\nmore\n')
            target = tree()
            config = {'signal': {'commit': commit}, 'port': {'mechanical_tree': mechanical}, 'target_tree': target}
            evidence = {'hunks': {}, 'baseline_mechanical_tree': mechanical, 'baseline_target_tree': target, 'minimization_complete': False}
            with patch.object(port_report, 'git', side_effect=run), patch.object(port_report, 'OLD', old), patch.object(port_report, 'MOLLY', molly), patch.object(port_report, 'clean_contribution', return_value=((0, 0, 0), [])):
                report = port_report.render(config, evidence)
                self.assertIn('Current mechanical comparison: 1 added, 1 deleted, 2 total', report)
                self.assertIn('current Molly comparison: 3 added, 2 deleted, 5 total', report)
                self.assertIn('original Molly patchset: 2 / 3 added plus deleted text lines = 66.667%', report)
                self.assertIn('After minimization: unmeasured.', report)
                with patch.object(port_report, 'OLD', molly):
                    self.assertIn('original Molly patchset: undefined (zero denominator)', port_report.render(config, evidence))

    def test_header_like_changed_lines_are_hunk_content(self):
        patch = b'diff --git a/f b/f\n--- a/f\n+++ b/f\n@@ -1,2 +1,2 @@\n--- old\n+++ new\n context\n'
        hunks = port_report.parse_hunks(patch)
        self.assertEqual([(h[0], h[1]) for h in hunks], [(1, 1)])

    def test_truncated_hunk_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'truncated'):
            port_report.parse_hunks(b'@@ -1,2 +1,2 @@\n-old\n+new\n')

    def test_numstat_preserves_tabs_and_binary_paths(self):
        raw = b'2\t3\tfile\tname\0-\t-\tbinary\0'
        from unittest.mock import patch
        with patch.object(port_report, 'git', return_value=raw):
            self.assertEqual(port_report.numstat('a', 'b'), {b'file\tname': (2, 3), b'binary': None})

    def test_deletion_inventory_uses_git_modes_counts_and_validates_annotations(self):
        import pathlib
        import subprocess
        import tempfile
        from unittest.mock import patch
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory)
            def run(*args):
                return subprocess.check_output(['git', '-C', directory, *args])
            run('init', '-q')
            (path / 'text').write_bytes(b'one\ntwo')
            (path / 'text').chmod(0o755)
            (path / 'binary').write_bytes(b'\0binary')
            (path / 'modified').write_text('old\n')
            run('add', '.')
            before = run('write-tree').decode().strip()
            (path / 'text').unlink()
            (path / 'binary').unlink()
            (path / 'modified').write_text('new\n')
            run('add', '-A')
            target = run('write-tree').decode().strip()
            with patch.object(port_report, 'git', side_effect=run):
                annotations = {'text': {'classification': 'candidate', 'caveat': 'unverified'}}
                inventory = port_report.whole_file_deletions(before, target, before, annotations)
                self.assertEqual([(x[0], x[1], x[2], x[3]) for x in inventory],
                                 [('binary', '100644', None, True), ('text', '100755', 2, True)])
                report = '\n'.join(port_report.deletion_report(inventory))
                self.assertIn('2 paths: 1 text files, 1 binary files, 2 deleted text lines', report)
                self.assertIn('unclassified', report)
                for invalid in ({'modified': {'classification': 'x'}},
                                {'text': {'classification': ''}},
                                {'text': {'classification': 'x', 'gate': 'pass'}}):
                    with self.assertRaises(ValueError):
                        port_report.whole_file_deletions(before, target, before, invalid)

    def test_real_git_diff_needs_evidence_before_ready(self):
        import pathlib
        import subprocess
        import tempfile
        from unittest.mock import patch
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory)
            def run(*args):
                return subprocess.check_output(['git', '-C', directory, *args]).decode().strip()
            run('init', '-q')
            run('config', 'user.name', 'Test')
            run('config', 'user.email', 'test@example.invalid')
            (path / 'file').write_text('-- old\n')
            run('add', '.')
            run('commit', '-qm', 'old')
            old_commit = run('rev-parse', 'HEAD')
            old = run('rev-parse', 'HEAD^{tree}')
            (path / 'file').write_text('++ new\n')
            (path / 'file').chmod(0o755)
            (path / 'binary').write_bytes(b'\x00\xff')
            run('add', '.')
            run('commit', '-qm', 'new')
            new = run('rev-parse', 'HEAD^{tree}')
            def invoke(*args):
                return subprocess.check_output(['git', '-C', directory, *args])
            config = {'signal': {'commit': old_commit}, 'port': {'mechanical_tree': old}, 'target_tree': new}
            with patch.object(port_report, 'git', side_effect=invoke), patch.object(port_report, 'OLD', old), patch.object(port_report, 'MOLLY', new), patch.object(port_report, 'clean_contribution', return_value=((0, 0, 0), [])):
                preview = port_report.render(config, {'hunks': {}, 'baseline_mechanical_tree': old, 'baseline_target_tree': new})
                self.assertIn('unmeasured', preview)
                self.assertIn('| mode | 100644 | 100755 |', preview)
                self.assertIn('| binary | 000000 | 100644 |', preview)
                # Assign semantic types across kinds, leaving the mode row unmeasured.
                table_rows = [line.split(' | ') for line in preview.splitlines()
                              if line.startswith('| ') and len(line.split(' | ')) == 11]
                by_kind = {row[2]: row[0][2:] for row in table_rows}
                evidence = {'hunks': {by_kind['text']: {'type': 'conflict_resolution'},
                                      by_kind['binary']: {'type': 'behavior_preservation'}},
                            'baseline_mechanical_tree': old, 'baseline_target_tree': new}
                report = port_report.render(config, evidence)
                rows = [line.split(' | ') for line in report.splitlines()
                        if line.startswith('| ') and len(line.split(' | ')) == 11]
                for field, names in ((7, ('conflict_resolution', 'signal_api_compile_fix',
                                           'behavior_preservation', 'unmeasured')),
                                     (2, ('text', 'binary', 'mode'))):
                    for name in names:
                        matching = [row for row in rows if row[field] == name]
                        expected = (len(matching), sum(int(row[5]) for row in matching),
                                    sum(int(row[6]) for row in matching))
                        self.assertIn(f'| {name} | {expected[0]} | {expected[1]} | {expected[2]} |', report)
                self.assertIn('| signal_api_compile_fix | 0 | 0 | 0 |', report)
                self.assertIn('| unmeasured | 1 | 0 | 0 |', report)
                evidence['hunks'][by_kind['text']]['type'] = 'behavior_addition'
                addition_report = port_report.render(config, evidence)
                addition_row = next(line for line in addition_report.splitlines()
                                    if line.startswith('| ' + by_kind['text'] + ' |'))
                self.assertIn('| behavior_addition |', addition_row)
                self.assertIn('| behavior_addition | 1 | 1 | 1 |', addition_report)
                self.assertIn('| behavior_preservation | 1 | 0 | 0 |', addition_report)
                self.assertIn('| conflict_resolution | 0 | 0 | 0 |', addition_report)
                evidence['hunks'][by_kind['text']]['type'] = 'unknown'
                with self.assertRaisesRegex(ValueError, 'invalid hunk type'):
                    port_report.render(config, evidence)
                with self.assertRaisesRegex(ValueError, 'missing evidence'):
                    port_report.render(config, {'hunks': {}, 'baseline_mechanical_tree': old, 'baseline_target_tree': new}, ready=True)

    def test_evidence_survives_unrelated_change_and_duplicate_hunks_are_distinct(self):
        import pathlib
        import re
        import subprocess
        import tempfile
        from unittest.mock import patch
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory)
            def run(*args):
                return subprocess.check_output(['git', '-C', directory, *args]).decode().strip()
            run('init', '-q')
            run('config', 'user.name', 'Test')
            run('config', 'user.email', 'test@example.invalid')
            original = 'old\n' + 'context\n' * 10 + 'old\n'
            (path / 'duplicate').write_text(original)
            (path / 'other').write_text('old\n')
            (path / 'binary').write_bytes(b'\x00old')
            (path / 'mode').write_text('mode\n')
            run('add', '.')
            run('commit', '-qm', 'base')
            commit = run('rev-parse', 'HEAD')
            before = run('rev-parse', 'HEAD^{tree}')
            (path / 'duplicate').write_text(original.replace('old', 'new'))
            (path / 'other').write_text('new\n')
            (path / 'binary').write_bytes(b'\x00new')
            (path / 'mode').chmod(0o755)
            run('add', '.')
            first = run('write-tree')
            config = {'signal': {'commit': commit}, 'port': {'mechanical_tree': before}, 'target_tree': first}
            evidence = {'hunks': {}, 'baseline_mechanical_tree': before, 'baseline_target_tree': first}
            def invoke(*args):
                return subprocess.check_output(['git', '-C', directory, *args])
            def ids(report, name):
                return re.findall(r'^\| ([0-9a-f]{20}) \| ' + name + r' \|', report, re.M)
            with patch.object(port_report, 'git', side_effect=invoke), patch.object(port_report, 'OLD', before), patch.object(port_report, 'MOLLY', first), patch.object(port_report, 'clean_contribution', return_value=((0, 0, 0), [])):
                report = port_report.render(config, evidence)
                duplicates = ids(report, 'duplicate')
                self.assertEqual(len(set(duplicates)), 2)
                preserved = duplicates + ids(report, 'binary') + ids(report, 'mode')
                evidence['hunks'] = {key: {'reason': 'recorded evidence'} for key in preserved}
                (path / 'other').write_text('different\n')
                run('add', '.')
                config['target_tree'] = run('write-tree')
                updated = port_report.render(config, evidence)
                self.assertEqual(ids(updated, 'duplicate'), duplicates)
                self.assertEqual(ids(updated, 'binary') + ids(updated, 'mode'), preserved[2:])
                self.assertEqual(updated.count('recorded evidence'), 4)
                self.assertNotEqual(ids(report, 'other'), ids(updated, 'other'))
                (path / 'duplicate').write_text(original.replace('old', 'new', 1))
                run('add', '.')
                config['target_tree'] = run('write-tree')
                with self.assertRaisesRegex(ValueError, 'stale or overlapping metadata'):
                    port_report.render(config, evidence)


class StartTreeBundleTests(unittest.TestCase):
    def test_fresh_clone_imports_start_trees_and_rejects_missing_ones(self):
        import hashlib, json, os, subprocess, tempfile
        from pathlib import Path
        env = {k: v for k, v in os.environ.items() if not k.startswith('GIT_')}
        env.update(GIT_AUTHOR_NAME='t', GIT_AUTHOR_EMAIL='t@example.invalid', GIT_COMMITTER_NAME='t',
                   GIT_COMMITTER_EMAIL='t@example.invalid')
        with tempfile.TemporaryDirectory() as tmp:
            source, clone = Path(tmp, 'source'), Path(tmp, 'clone')
            def git(repo, *args):
                return subprocess.run(['git', '-C', str(repo), *args], env=env, check=True,
                                      stdout=subprocess.PIPE).stdout.decode().strip()
            source.mkdir(); git(source, 'init', '-q', '-b', 'main')
            (source / 'a').write_text('base\n'); git(source, 'add', 'a'); git(source, 'commit', '-qm', 'base')
            base = git(source, 'rev-parse', 'HEAD')
            subprocess.run(['git', 'clone', '-q', '--no-local', str(source), str(clone)], env=env, check=True)
            (source / 'a').write_text('evidence\n'); git(source, 'add', 'a')
            tree = git(source, 'write-tree')
            commit = git(source, 'commit-tree', tree, '-p', base, '-m', 'start tree')
            port = clone / 'patches/ports/p'; (port / 'report-evidence').mkdir(parents=True)
            ref = 'refs/heads/report-evidence/action-start-trees'
            git(source, 'update-ref', ref, commit)
            bundle = port / 'report-evidence/action-start-trees.bundle'
            git(source, 'bundle', 'create', '-q', str(bundle), ref, '^' + base)
            data = bundle.read_bytes()
            evidence = {'baseline_target_tree': '0' * 40, 'action_chains': [{'start_tree': tree}],
                        'start_tree_bundle': {'path': 'report-evidence/action-start-trees.bundle', 'ref': ref,
                                              'sha256': hashlib.sha256(data).hexdigest(), 'bytes': len(data),
                                              'commit': commit, 'prerequisites': [base]}}
            config = {'port': {'series': 'patches/ports/p/series'}}
            cwd = os.getcwd()
            os.chdir(clone)
            try:
                with self.assertRaises(subprocess.CalledProcessError):
                    git(clone, 'cat-file', '-e', tree)
                port_report.import_start_trees(config, evidence)
                self.assertEqual(git(clone, 'cat-file', '-t', tree), 'tree')
                evidence['action_chains'].append({'start_tree': 'f' * 40})
                with self.assertRaisesRegex(ValueError, 'lacks action chain trees'):
                    port_report.import_start_trees(config, evidence)
                port_report.import_start_trees(config, {'action_chains': []})
            finally:
                os.chdir(cwd)


class EvidenceModelTests(unittest.TestCase):
    def test_groups_require_explicit_nonoverlapping_ids_and_cannot_claim_gates(self):
        group = {'hunks': ['a'], 'description': {'reason': 'shared'}}
        self.assertEqual(port_report.expand_groups({'groups': {'one': group}, 'hunks': {'a': {'outcome': 'fail'}}})['a'], {'reason': 'shared', 'outcome': 'fail'})
        with self.assertRaisesRegex(ValueError, 'overlapping'):
            port_report.expand_groups({'groups': {'one': group, 'two': group}})
        with self.assertRaisesRegex(ValueError, 'invalid group'):
            port_report.expand_groups({'groups': {'one': {'hunks': ['a'], 'description': {'revert_gate': 'pass'}}}})

    def test_action_replay_rejects_stale_input(self):
        import os
        import pathlib
        import subprocess
        import tempfile
        with tempfile.TemporaryDirectory() as directory:
            old_cwd = os.getcwd()
            try:
                os.chdir(directory)
                def run(*args): return subprocess.check_output(['git', *args]).decode().strip()
                run('init', '-q')
                pathlib.Path('source').write_text('old\n')
                run('add', 'source')
                before = run('write-tree')
                blob = run('rev-parse', ':source')
                pathlib.Path('source').write_text('new\n')
                patch = subprocess.check_output(['git', 'diff', '--', 'source'])
                pathlib.Path('action.patch').write_bytes(patch)
                run('add', 'action.patch')
                run('add', 'source')
                after = run('rev-parse', ':source')
                action = {'path': 'source', 'input_blob': blob, 'output_blob': after, 'patch': 'action.patch', 'gate_input': 'tested source state', 'gate': 'compile', 'outcome': 'pass', 'evidence': 'log'}
                data = {'action_chains': [{'start_tree': before, 'actions': [action]}]}
                self.assertEqual(len(port_report.action_history(data)), 1)
                action['input_blob'] = after
                with self.assertRaisesRegex(ValueError, 'stale action input'):
                    port_report.action_history(data)
            finally:
                os.chdir(old_cwd)

    def test_actual_merge_conflicts_excluded_from_clean_count(self):
        import os
        import pathlib
        import subprocess
        import tempfile
        from unittest.mock import patch
        with tempfile.TemporaryDirectory() as directory:
            original = os.getcwd()
            try:
                os.chdir(directory)
                def run(*args): return subprocess.check_output(['git', *args]).decode().strip()
                run('init', '-q'); run('config', 'user.name', 'Test'); run('config', 'user.email', 'test@example.invalid')
                pathlib.Path('conflict').write_text('base\n')
                pathlib.Path('deleted').write_text('base\n')
                pathlib.Path('binary').write_bytes(b'\0base')
                run('add', '.'); run('commit', '-qm', 'base'); base = run('rev-parse', 'HEAD')
                pathlib.Path('conflict').write_text('molly\n'); pathlib.Path('deleted').unlink()
                pathlib.Path('binary').write_bytes(b'\0molly')
                pathlib.Path('clean').write_text('clean\n')
                run('add', '-A'); run('commit', '-qm', 'molly'); molly = run('rev-parse', 'HEAD')
                run('checkout', '-q', '--detach', base)
                pathlib.Path('conflict').write_text('signal\n'); pathlib.Path('deleted').write_text('signal\n')
                pathlib.Path('binary').write_bytes(b'\0signal')
                run('add', '-A'); run('commit', '-qm', 'signal'); signal = run('rev-parse', 'HEAD')
                tree, _, _ = port_report.release.mechanical_merge('.', base, molly, signal)
                with patch.object(port_report, 'OLD', base), patch.object(port_report, 'MOLLY', molly):
                    counts, conflicts = port_report.clean_contribution(signal, tree)
                self.assertEqual(set(conflicts), {'conflict', 'deleted', 'binary'})
                self.assertEqual(counts, (1, 0, 0))
            finally:
                os.chdir(original)
