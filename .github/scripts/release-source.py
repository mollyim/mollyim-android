"""Resolve a release source, optionally recovering its already signed artifact."""
import json
import os
import re
import subprocess

TAG = re.compile(r'v[0-9][A-Za-z0-9._-]*-memoly\.[0-9]+')
RECOVERY = re.compile(r'ci-recover-(v[0-9][A-Za-z0-9._-]*-memoly\.[0-9]+)-run([0-9]+)')


def parse_reference(reference):
    if TAG.fullmatch(reference):
        return reference, None
    match = RECOVERY.fullmatch(reference)
    if match:
        return match[1], int(match[2])
    raise ValueError('Expected a Memoly release tag or ci-recover-<tag>-run<id>')


def verified_artifact(run, jobs, artifacts, tag, sha):
    if run['head_sha'] != sha or run['head_branch'] != tag:
        raise ValueError('Producer source does not match the immutable target tag')
    if run['path'].split('@')[0] != '.github/workflows/release.yml':
        raise ValueError('Producer must be the release workflow')
    required = {'Test and build unsigned APK once', 'Sign verified APK without rebuilding'}
    passed = {j['name'].split(' / ')[-1] for j in jobs if j['conclusion'] == 'success'}
    if not required.issubset(passed):
        raise ValueError('Producer build, tests, lint and signing must have passed')
    candidates = [a for a in artifacts if a['name'] == 'signed-memoly-apk' and not a['expired']]
    if len(candidates) != 1:
        raise ValueError('Expected one unexpired signed artifact')
    return str(candidates[0]['id'])


def api(path):
    return json.loads(subprocess.check_output(['gh', 'api', path], text=True))


def resolve(reference, repository):
    tag, producer = parse_reference(reference)
    obj = api(f'repos/{repository}/git/ref/tags/{tag}')['object']
    while obj['type'] == 'tag':
        obj = api(f'repos/{repository}/git/tags/{obj["sha"]}')['object']
    if obj['type'] != 'commit':
        raise ValueError('Release tag must resolve to a commit')
    values = {'tag': tag, 'sha': obj['sha'], 'reuse': 'false', 'artifact': '', 'producer_run': ''}
    if producer is not None:
        run = api(f'repos/{repository}/actions/runs/{producer}')
        jobs = api(f'repos/{repository}/actions/runs/{producer}/jobs?per_page=100')['jobs']
        artifacts = api(f'repos/{repository}/actions/runs/{producer}/artifacts?per_page=100')['artifacts']
        values.update(reuse='true', producer_run=str(producer), artifact=verified_artifact(run, jobs, artifacts, tag, obj['sha']))
    return values


if __name__ == '__main__':
    result = resolve(os.environ['REQUESTED_REF'], os.environ['GITHUB_REPOSITORY'])
    with open(os.environ['GITHUB_OUTPUT'], 'a') as output:
        for key, value in result.items():
            output.write(f'{key}={value}\n')
    print(json.dumps(result))
