#!/usr/bin/env python3
"""Every upstream pin compiled into the patch tools and workflows.

The tools refuse inputs that differ from the pinned releases, so moving to a new Signal or
Molly release means changing each pin below in every file it names, then the data
pins in `molly-build.json` and the series provenance. `python3 tools/patches/pins.py`
prints the table. `test_pins.py` fails when a tool or workflow contains an upstream commit,
tree, port directory or source URL missing from this list, or when a listed
pin is gone from a file it names. Files absent from a branch are skipped.
"""
import sys

SCOPE = 'tools/patches/scope.py'
PREPARE = 'tools/patches/prepare_port_report.py'
PREFLIGHT = 'tools/patches/preflight.sh'
RELEASE_WORKFLOW = '.github/workflows/molly-patch-release.yml'
SYNC_WORKFLOW = '.github/workflows/sync.yml'

# (value, meaning, upstream whose new release changes it, files that contain it)
PINS = [
    ('0e202dda90785771ff8c87526eaaf30c655f54f3', 'Molly 8.19.2-4 release commit, the Molly input', 'Molly',
     [SCOPE, RELEASE_WORKFLOW]),
    ('c47a74ba0961903eeaeda6d22347a990056c3e83', 'Signal 8.19.2 commit under Molly 8.19.2-4', 'Molly',
     [SCOPE]),
    ('3cd0bb01cf840e4b702187085432020d163d80ce', 'Molly 8.19.2-4 tree that the patches must rebuild', 'Molly', [SCOPE]),
    ('7a280ac23b61cef2c09009a6dbed02773fff3dfa', 'tree of the protected patches/molly forest', 'Molly', [SCOPE]),
    ('github.com/mollyim/mollyim-android.git', 'Molly source URL', 'none', [PREFLIGHT, PREPARE, RELEASE_WORKFLOW]),
    ('github.com/signalapp/Signal-Android.git', 'Signal source URL', 'none', [PREPARE, SYNC_WORKFLOW]),
]

# Layer IDs that tools name directly. A new layer in these positions means updating them.
LAYER_NAMES = []


def main():
    print('| Pin | Meaning | Changes with | Files |\n|---|---|---|---|')
    for value, meaning, upstream, files in PINS:
        print(f'| `{value}` | {meaning} | {upstream} | ' + ', '.join(f'`{f}`' for f in files) + ' |')
    print('\n| Layer named by tools | Role | Files |\n|---|---|---|')
    for value, meaning, files in LAYER_NAMES:
        print(f'| `{value}` | {meaning} | ' + ', '.join(f'`{f}`' for f in files) + ' |')
    return 0


if __name__ == '__main__':
    sys.exit(main())
