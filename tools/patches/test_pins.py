from pathlib import Path
import re
import unittest

import pins

ROOT = Path(__file__).resolve().parents[2]
PATTERN = re.compile(r'\b[0-9a-f]{40}\b|\bsignal-\d+\.\d+\.\d+\b|\b\d+\.\d+\.\d+(?:\.\d+)?-FOSS\b'
                     r'|github\.com/[A-Za-z0-9_-]+/[A-Za-z0-9_.-]+')
IGNORED = {'github.com/JakeWharton/diffuse'}  # a build tool, not an upstream source


def scanned():
    files = [p for p in (ROOT / 'tools/patches').glob('*.py') if not p.name.startswith('test_') and p.name != 'pins.py']
    files += list((ROOT / 'tools/patches').glob('*.sh')) + list((ROOT / '.github/workflows').glob('*.yml'))
    found = {}
    for path in files:
        rel = path.relative_to(ROOT).as_posix()
        for line in path.read_text(errors='surrogateescape').splitlines():
            if re.match(r'\s*-?\s*uses:', line):  # GitHub Action pins are not upstream releases
                continue
            for value in PATTERN.findall(line):
                value = value.rstrip('.')
                if value not in IGNORED:
                    found.setdefault(value, set()).add(rel)
    return found


class PinTests(unittest.TestCase):
    def test_every_compiled_pin_is_listed_with_its_files(self):
        listed = {value: set(files) for value, _, _, files in pins.PINS}
        for value, files in scanned().items():
            with self.subTest(value=value):
                self.assertIn(value, listed, 'add this pin to tools/patches/pins.py')
                self.assertLessEqual(files, listed[value], 'pins.py is missing a file for this pin')

    def test_listed_pins_and_layers_are_present(self):
        for value, _, _, files in pins.PINS:
            for name in files:
                path = ROOT / name
                if path.exists():
                    with self.subTest(value=value, file=name):
                        self.assertIn(value, path.read_text(errors='surrogateescape'))
        for value, _, files in pins.LAYER_NAMES:
            for name in files:
                path = ROOT / name
                if path.exists():
                    with self.subTest(layer=value, file=name):
                        self.assertIn(value, path.read_text(errors='surrogateescape'))


if __name__ == '__main__':
    unittest.main()
