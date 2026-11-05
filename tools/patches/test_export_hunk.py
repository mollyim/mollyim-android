from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

import port_report


SCRIPT = Path(__file__).resolve().with_name('export_hunk.py')


class ExportHunkTests(unittest.TestCase):
    def test_cli_exports_only_selected_hunk_and_rejects_unsupported_records(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            repo = root / 'repo'
            repo.mkdir()
            def git(*args, input=None):
                return subprocess.check_output(['git', '-C', str(repo), *args], input=input)
            git('init', '-q')
            git('config', 'user.name', 'Test')
            git('config', 'user.email', 'test@example.invalid')
            original = b'old first\n' + b'context\n' * 12 + b'old last\n'
            target = original.replace(b'old', b'new')
            (repo / 'file').write_bytes(original)
            (repo / 'binary').write_bytes(b'\0old')
            (repo / 'mode').write_bytes(b'mode\n')
            git('add', '.')
            git('commit', '-qm', 'base')
            base = git('rev-parse', 'HEAD^{tree}').decode().strip()
            (repo / 'file').write_bytes(target)
            (repo / 'binary').write_bytes(b'\0new')
            (repo / 'mode').chmod(0o755)
            (repo / 'added').write_bytes(b'added\n')
            git('add', '.')
            git('commit', '-qm', 'target')
            end = git('rev-parse', 'HEAD^{tree}').decode().strip()
            patch = git('diff', '--no-ext-diff', '--no-renames', '--unified=3', base, end, '--', 'file')
            hunks = port_report.text_hunks(b'file', patch)
            self.assertEqual(len(hunks), 2)
            def cli(key, path=None):
                return subprocess.run([sys.executable, str(SCRIPT), '--repo', str(repo), '--base', base,
                                       '--target', end, '--hunk-id', key] + ([] if path is None else ['--path', path]), cwd=root,
                                      stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            index_before = (repo / '.git/index').read_bytes()
            refs_before = git('for-each-ref')
            output = cli(hunks[0][3])
            self.assertEqual(output.returncode, 0, output.stderr)
            hinted = cli(hunks[0][3], 'file')
            self.assertEqual(hinted.returncode, 0, hinted.stderr)
            self.assertEqual(hinted.stdout, output.stdout)
            wrong = cli(hunks[0][3], 'added')
            self.assertEqual(wrong.returncode, 1)
            self.assertIn(b'unknown hunk ID', wrong.stderr)
            self.assertIn(b'at path: added', wrong.stderr)
            self.assertTrue(output.stdout.endswith(b'\n'))
            self.assertEqual((repo / 'file').read_bytes(), target)
            self.assertEqual((repo / '.git/index').read_bytes(), index_before)
            self.assertEqual(git('for-each-ref'), refs_before)
            git('apply', '--reverse', '-', input=output.stdout)
            self.assertEqual((repo / 'file').read_bytes(), original.replace(b'old last', b'new last'))
            self.assertEqual((repo / '.git/index').read_bytes(), index_before)
            self.assertEqual(git('write-tree').decode().strip(), end)
            self.assertEqual(git('for-each-ref'), refs_before)
            self.assertEqual(cli('unknown').returncode, 1)
            self.assertIn(b'unknown hunk ID', cli('unknown').stderr)
            raw = git('diff', '--no-renames', '--raw', '--no-abbrev', '-z', base, end).split(b'\0')
            binary_header = next(raw[i].split() for i in range(0, len(raw) - 1, 2) if raw[i+1] == b'binary')
            binary_id = port_report.hunk_identity(b'binary', b'binary', binary_header[2], binary_header[3], b'100644', b'100644')
            self.assertIn(b'binary records are unsupported', cli(binary_id).stderr)
            added_patch = git('diff', '--no-ext-diff', '--no-renames', '--unified=3', base, end, '--', 'added')
            added_id = port_report.text_hunks(b'added', added_patch)[0][3]
            self.assertIn(b'added and deleted files are unsupported', cli(added_id).stderr)


if __name__ == '__main__':
    unittest.main()
