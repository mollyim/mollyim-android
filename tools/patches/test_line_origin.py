import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

import line_origin


class Fixture:
    """A tiny history: old Signal -> Molly, old Signal -> new Signal, plus a port result."""

    def __init__(self, directory):
        self.path = Path(directory)
        self.run('init', '-q')
        self.run('config', 'user.name', 'Test')
        self.run('config', 'user.email', 'test@example.invalid')

    def run(self, *args, data=None):
        env = {k: v for k, v in os.environ.items() if not k.startswith('GIT_')}
        return subprocess.run(['git', '-C', str(self.path), *args], input=data, env=env, check=True,
                              stdout=subprocess.PIPE).stdout.decode().strip()

    def commit(self, files, parent=None, message='c'):
        self.run('read-tree', '--empty')
        for name, text in files.items():
            blob = self.run('hash-object', '-w', '--stdin', data=text.encode())
            self.run('update-index', '--add', '--cacheinfo', f'100644,{blob},{name}')
        tree = self.run('write-tree')
        args = ['commit-tree', tree, '-m', message] + (['-p', parent] if parent else [])
        return self.run(*args)


class LineOriginTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.objects = tempfile.TemporaryDirectory()
        self.fixture = Fixture(self.tmp.name)
        f = self.fixture
        shared = 'a\nb\nc\nd\ne\nf\ng\nh\n'
        self.old = f.commit({'app/src/main/A.kt': shared, 'app/src/main/Gone.kt': 'x\ny\n',
                             'app/src/main/res/values-de/strings.xml': 'de\n'})
        self.molly = f.commit({'app/src/main/A.kt': 'a\nMOLLY\nc\nd\ne\nf\ng\nh\n',
                               'app/src/main/Gone.kt': 'x\ny\nmolly-only\n',
                               'app/src/main/res/values-de/strings.xml': 'de\n'}, self.old)
        self.signal = f.commit({'app/src/main/A.kt': 'a\nb\nc\nd\ne\nf\ng\nSIGNAL\n',
                                'app/src/main/Gone.kt': 'x\ny\n',
                                'app/src/main/res/values-de/strings.xml': 'de\nneu\n'}, self.old)
        self.repo = line_origin.Repo(f.path, self.objects.name)

    def tearDown(self):
        self.tmp.cleanup()
        self.objects.cleanup()

    def result(self, files):
        commit = self.fixture.commit(files)
        return line_origin.combined(self.repo, commit, [self.molly, self.signal])

    def test_side_choices_count_nothing(self):
        files = self.result({'app/src/main/A.kt': 'a\nMOLLY\nc\nd\ne\nf\ng\nSIGNAL\n',
                             'app/src/main/Gone.kt': 'x\ny\nmolly-only\n',
                             'app/src/main/res/values-de/strings.xml': 'de\nneu\n'})
        self.assertEqual(files, {})

    def test_new_lines_and_lines_removed_from_both_parents(self):
        files = self.result({'app/src/main/A.kt': 'a\nMOLLY\nc\nOURS\ne\nf\ng\nSIGNAL\n',
                             'app/src/main/New.kt': 'one\ntwo\n',
                             'app/src/main/res/values-de/strings.xml': 'de\nneu\n'})
        self.assertEqual(files['app/src/main/A.kt'], [1, 1])
        self.assertEqual(files['app/src/main/New.kt'], [2, 0])
        self.assertEqual(files['app/src/main/Gone.kt'], [0, 2])
        summary = line_origin.summarize(files)
        self.assertEqual(summary['production'], {'files': 3, 'written': 3, 'removed': 3})

    def test_molly_lines_not_carried(self):
        index = line_origin.molly_lines(self.repo, self.old, self.molly)
        self.assertEqual(index['app/src/main/A.kt'], {b'MOLLY': 1})
        tree = self.fixture.commit({'app/src/main/A.kt': 'a\nb\nc\n'})
        result, files = line_origin.not_carried(self.repo, tree, index)
        self.assertEqual(result['production'], {'molly_lines': 2, 'not_carried': 2, 'in_removed_paths': 1})
        self.assertEqual(files, {'app/src/main/A.kt': [1, True], 'app/src/main/Gone.kt': [1, False]})

    def test_physical_diff_follows_renames(self):
        before = self.fixture.commit({'app/src/main/A.kt': 'a\nb\nc\nd\ne\nf\n', 'app/src/test/T.kt': 't\n'})
        after = self.fixture.commit({'app/src/main/B.kt': 'a\nb\nc\nd\ne\nF\n', 'app/src/test/T.kt': 't\nu\n'})
        result = line_origin.physical(self.repo, before, after)
        self.assertEqual(result['production'], {'files': 1, 'written': 1, 'removed': 1})
        self.assertEqual(result['tests'], {'files': 1, 'written': 1, 'removed': 0})

    def test_extra_parent_lines_are_not_counted(self):
        extra = self.fixture.commit({'app/src/main/A.kt': 'a\nb\nc\nd\nEXTRA\nf\ng\nSIGNAL\n',
                                    'app/src/main/Gone.kt': 'x\ny\n',
                                    'app/src/main/res/values-de/strings.xml': 'de\nneu\n'}, self.signal)
        commit = self.fixture.commit({'app/src/main/A.kt': 'a\nMOLLY\nc\nd\nEXTRA\nf\nOURS\nSIGNAL\n',
                                      'app/src/main/Gone.kt': 'x\ny\nmolly-only\n',
                                      'app/src/main/res/values-de/strings.xml': 'de\nneu\n'})
        two = line_origin.combined(self.repo, commit, [self.molly, self.signal])
        three = line_origin.combined(self.repo, commit, [self.molly, self.signal, extra])
        self.assertEqual(two['app/src/main/A.kt'], [2, 2])
        self.assertEqual(three['app/src/main/A.kt'], [1, 1])

    def test_repository_is_not_modified(self):
        commit = self.fixture.commit({'app/src/main/A.kt': 'new\n'})
        before = self.fixture.run('count-objects', '-v')
        line_origin.combined(self.repo, commit, [self.molly, self.signal])
        self.assertEqual(self.fixture.run('count-objects', '-v'), before)
        self.assertTrue(os.listdir(self.objects.name))

    def test_classification(self):
        cases = {
            'app/src/main/java/A.kt': 'production',
            'app/src/main/res/values/strings.xml': 'production',
            'app/src/main/res/values-night/themes.xml': 'production',
            'app/src/main/res/values-pt-rBR/strings.xml': 'translations',
            'feature/x/src/main/res/values-de/strings2.xml': 'translations',
            'app/src/test/java/T.kt': 'tests',
            'app/src/screenshotTestDebug/A.kt': 'tests',
            'gradle/verification-metadata.xml': 'generated',
            'patches/ports/x/series': 'repository',
            'README.md': 'repository',
        }
        self.assertEqual({p: line_origin.classify(p) for p in cases}, cases)

    def test_quoted_paths(self):
        self.assertEqual(line_origin.unquote(b'"a\\tb\\303\\251"'), 'a\tbé')
        self.assertEqual(line_origin.unquote(b'plain/path'), 'plain/path')

    def test_markdown_names_both_inputs(self):
        zero = {k: {'files': 0, 'written': 0, 'removed': 0} for k in line_origin.CLASSES}
        molly = {k: {'molly_lines': 0, 'not_carried': 0, 'in_removed_paths': 0} for k in line_origin.CLASSES}
        molly['production'].update(molly_lines=100, not_carried=5)
        classes = json.loads(json.dumps(zero)); classes['production'].update(written=7, removed=2)
        result = {'molly': 'm' * 40, 'signal': 's' * 40, 'old_signal': 'o' * 40, 'classes': line_origin.CLASS_NOTES,
                  'stages': {'PR3 compatibility': {'tree': 't' * 40, 'classes': classes, 'molly': molly,
                                                   'top_production_files': [], 'top_not_carried': []}},
                  'layers': [{'layer': 'followups/x', 'stage': 'PR4 behavior fixes',
                              'kind': 'physical diff from previous checkpoint',
                              'classes': dict(zero, production={'files': 1, 'written': 3, 'removed': 2})}]}
        text = line_origin.markdown(result)
        self.assertIn('Molly `mmmmmmmmmmmm` and Signal `ssssssssssss`', text)
        self.assertIn('| +7 / −2 | 7.0% | 5 (of which 0 in removed paths) |', text)
        self.assertIn('| `followups/x` | PR4 behavior fixes | +3 / −2 |', text)


if __name__ == '__main__':
    unittest.main()
