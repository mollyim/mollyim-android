import unittest
from ci_profile import profile


class CiProfileTest(unittest.TestCase):
    def test_common_source_changes_keep_all_representative_tests(self):
        self.assertEqual(profile(["app/src/main/Example.kt", "core/ui/src/test/java/Example.kt"]), "representative")

    def test_flavor_and_toolchain_changes_need_full_checks(self):
        for path in ["app/build.gradle.kts", "app/src/main/AndroidManifest.xml", "gradle.properties.ci", "gradle/libs.versions.toml",
                     ".github/workflows/test.yml", "app/src/website/AndroidManifest.xml",
                     "app/src/staging/java/Example.kt", "app/src/release/AndroidManifest.xml", "buildSrc/src/main/Example.kt"]:
            with self.subTest(path=path):
                self.assertEqual(profile(["README.md", path]), "full")

    def test_documentation_only(self):
        self.assertEqual(profile(["plan.md", "docs/usage.md"]), "docs")

    def test_empty_file_list_fails_closed(self):
        self.assertEqual(profile([]), "full")

    def test_github_file_listing_limit_fails_closed(self):
        self.assertEqual(profile(["plan.md"] * 3000), "full")


if __name__ == "__main__":
    unittest.main()
