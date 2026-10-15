"""Report actual test execution counts by module and variant."""
import os
from collections import defaultdict
from pathlib import Path
import xml.etree.ElementTree as ET

counts = defaultdict(lambda: [0, 0, 0, 0])
for path in Path('.').glob('**/build/test-results/**/TEST-*.xml'):
    suite = ET.parse(path).getroot()
    module, variant = str(path).split('/build/test-results/', 1)
    key = (module, variant.split('/')[0])
    for index, field in enumerate(['tests', 'failures', 'errors', 'skipped']):
        counts[key][index] += int(suite.get(field, '0'))
lines = ['| Module / test task | Tests | Failures | Errors | Skipped |', '|---|---:|---:|---:|---:|']
for (module, variant), totals in sorted(counts.items()):
    lines.append(f'| {module} / {variant} | ' + ' | '.join(map(str, totals)) + ' |')
text = '\n'.join(lines) if counts else 'No JUnit reports were produced.'
print(text)
if os.environ.get('GITHUB_STEP_SUMMARY'):
    with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as target:
        target.write('\n' + text + '\n')
