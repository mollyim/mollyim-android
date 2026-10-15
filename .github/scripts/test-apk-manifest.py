import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('manifest', Path(__file__).with_name('verify-apk-manifest.py'))
manifest = importlib.util.module_from_spec(spec)
spec.loader.exec_module(manifest)

XML = '''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="im.memoly.app">
<permission android:name="im.memoly.app.ACCESS_SECRETS" android:protectionLevel="signature"/>
<application android:name="org.thoughtcrime.securesms.ApplicationContext">
<provider android:authorities="im.memoly.app.fileprovider"/>
<activity-alias android:name="org.thoughtcrime.securesms.RoutingActivity" android:targetActivity="org.thoughtcrime.securesms.MainActivity"/>
</application></manifest>'''


class ManifestTest(unittest.TestCase):
    def test_renamed_install_with_original_classes(self):
        manifest.verify(XML)

    def test_old_provider_authority_is_rejected(self):
        with self.assertRaises(ValueError):
            manifest.verify(XML.replace('im.memoly.app.fileprovider', 'im.molly.app.fileprovider'))

    def test_test_only_and_split_apks_are_rejected(self):
        for flag in ('testOnly', 'debuggable', 'isSplitRequired'):
            with self.subTest(flag=flag), self.assertRaises(ValueError):
                manifest.verify(XML.replace('<application ', f'<application android:{flag}="true" '))

    def test_renaming_classes_breaks_the_launcher(self):
        with self.assertRaises(ValueError):
            manifest.verify(XML.replace('org.thoughtcrime.securesms.MainActivity', 'im.memoly.app.MainActivity'))

    def test_weakened_secret_permission_is_rejected(self):
        with self.assertRaises(ValueError):
            manifest.verify(XML.replace('protectionLevel="signature"', 'protectionLevel="normal"'))


if __name__ == '__main__':
    unittest.main()
