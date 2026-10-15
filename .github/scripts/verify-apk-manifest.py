"""Check the decoded release manifest before distributing a signed APK."""
import sys
import xml.etree.ElementTree as ET

ANDROID = '{http://schemas.android.com/apk/res/android}'
PACKAGE = 'im.memoly.app'


def verify(xml):
    root = ET.fromstring(xml)
    if root.get('package') != PACKAGE:
        raise ValueError('Expected Memoly package')
    app = root.find('application')
    if app is None or app.get(ANDROID + 'name') != 'org.thoughtcrime.securesms.ApplicationContext':
        raise ValueError('Application class must retain its code namespace')
    for flag in ('debuggable', 'testOnly', 'isSplitRequired'):
        if app.get(ANDROID + flag, 'false') != 'false':
            raise ValueError('Release must not enable ' + flag)
    for provider in app.findall('provider'):
        authorities = provider.get(ANDROID + 'authorities', '').split(';')
        if not all(authority.startswith(PACKAGE + '.') for authority in authorities):
            raise ValueError('Provider authority is not scoped to Memoly')
    permissions = {p.get(ANDROID + 'name'): p for p in root.findall('permission')}
    access = permissions.get(PACKAGE + '.ACCESS_SECRETS')
    protection = access.get(ANDROID + 'protectionLevel', '') if access is not None else ''
    if protection != 'signature' and int(protection or '0', 0) != 2:
        raise ValueError('App secret permission must remain signature protected')
    for permission in root.findall('permission'):
        if not permission.get(ANDROID + 'name', '').startswith(PACKAGE + '.'):
            raise ValueError('App-defined permission is not scoped to Memoly')
    aliases = {a.get(ANDROID + 'name'): a for a in app.findall('activity-alias')}
    launcher = aliases.get('org.thoughtcrime.securesms.RoutingActivity')
    if launcher is None or launcher.get(ANDROID + 'targetActivity') != 'org.thoughtcrime.securesms.MainActivity':
        raise ValueError('Launcher must target the existing MainActivity class')


if __name__ == '__main__':
    try:
        verify(sys.stdin.read())
    except (ValueError, ET.ParseError) as error:
        raise SystemExit(str(error))
    print('Verified Memoly manifest: namespace, authorities, permissions and release flags')
