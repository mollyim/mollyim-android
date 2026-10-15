import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('platforms', Path(__file__).with_name('select-test-platforms.py'))
platforms = importlib.util.module_from_spec(spec)
spec.loader.exec_module(platforms)
BASE = ' system-images;android-35;google_apis;x86_64 | 9 | Image'


class PlatformTest(unittest.TestCase):
    def test_missing_optional_image_does_not_claim_android17(self):
        self.assertEqual([x['api'] for x in platforms.select_images(BASE)], [35])

    def test_available_api37_image_is_selected_without_requiring_api37_platform(self):
        images = platforms.select_images(BASE + '\n system-images;android-37;google_apis;x86_64 | 5 | Image')
        self.assertEqual([x['api'] for x in images], [35, 37])
        self.assertEqual(images[1]['channel'], 'canary')

    def test_required_baseline_cannot_be_skipped(self):
        with self.assertRaises(ValueError):
            platforms.select_images('')

    def test_other_architectures_or_package_suffixes_do_not_match(self):
        images = platforms.select_images(BASE + '\nsystem-images;android-37;google_apis;x86_64_extra | 5\nsystem-images;android-37;google_apis;arm64-v8a | 5')
        self.assertEqual(len(images), 1)


if __name__ == '__main__':
    unittest.main()
