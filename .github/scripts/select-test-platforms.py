"""Pick available emulator images independently of the installed compile SDK."""
import json
import os
import re
from pathlib import Path


def select_images(catalog):
    available = set(re.findall(r'system-images;android-\d+;google_apis;x86_64(?=\s|\||$)', catalog))
    if 'system-images;android-35;google_apis;x86_64' not in available:
        raise ValueError('Required API 35 emulator image is absent from SDK catalog')
    images = [{'api': 35, 'image': '35', 'channel': 'stable'}]
    if 'system-images;android-37;google_apis;x86_64' in available:
        images.append({'api': 37, 'image': '37', 'channel': 'canary'})
    return images


if __name__ == '__main__':
    images = select_images(Path(os.environ['SDK_CATALOG']).read_text())
    apis = ', '.join(str(x['api']) for x in images)
    if len(images) == 1:
        print('::warning::API 37 emulator image is not available in the SDK catalog; Android 17 requires device validation.')
    with open(os.environ['GITHUB_OUTPUT'], 'a') as output:
        output.write('matrix=' + json.dumps(images) + '\n')
        output.write('apis=' + apis + '\n')
    with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as output:
        output.write('Available install checks: ' + apis + '. Android 17 is not considered tested when its image is unavailable.\n')
