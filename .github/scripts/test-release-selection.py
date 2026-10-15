import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('selection', Path(__file__).with_name('select-memoly-release.py'))
selection = importlib.util.module_from_spec(spec)
spec.loader.exec_module(selection)


class ReleaseSelectionTest(unittest.TestCase):
    def test_old_personal_molly_releases_do_not_define_memoly_upgrades(self):
        self.assertEqual(selection.previous(['v8.19.2-4-personal.3', 'v8.19.2-4'], 'v8.19.2-4-memoly.1'), '')

    def test_current_and_unrelated_tags_are_skipped(self):
        tags = ['v8.19.2-4-memoly.2', 'v8.19.2-4-personal.3', 'v8.19.2-4-memoly.1']
        self.assertEqual(selection.previous(tags, tags[0]), tags[-1])

    def test_invalid_tag_cannot_select_a_baseline(self):
        with self.assertRaises(ValueError):
            selection.previous([], 'v8.19.2-4-personal.3')


if __name__ == '__main__':
    unittest.main()
