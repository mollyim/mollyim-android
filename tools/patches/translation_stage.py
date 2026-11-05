"""Pinned, byte-preserving translation generation for release assembly."""
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import tempfile
import xml.etree.ElementTree as ET
import xml.parsers.expat as expat

import forest
import git_utils
import verify_complete


def digest(data):
    return hashlib.sha256(data).hexdigest()


def transform(data, base, rel):
    names = {x.get('name') for x in ET.fromstring(base).iter()
             if x.tag in ('string', 'plurals') and x.get('mollyify') == 'true'}
    parser = expat.ParserCreate()
    stack, targets = [], []
    def start(tag, attrs):
        pos = data.index(b'>', parser.CurrentByteIndex) + 1
        parent = stack[-1] if stack else None
        selected = ((tag == 'string' and attrs.get('name') in names) or
                    (tag == 'item' and parent and parent['tag'] == 'plurals' and parent['name'] in names))
        stack.append(dict(tag=tag, name=attrs.get('name'), start=pos, text='', selected=selected, children=False))
        if parent:
            parent['children'] = True
    def chars(text):
        for node in stack:
            if node['selected']:
                node['text'] += text
    def end(tag):
        node = stack.pop()
        if node['tag'] != tag:
            raise ValueError('bad XML nesting')
        if node['selected']:
            old = node['text']
            targets.append((node['start'], parser.CurrentByteIndex, old,
                            old.replace('signal.org', 'molly.im').replace('Signal', 'Molly'), node['children']))
    parser.StartElementHandler, parser.EndElementHandler, parser.CharacterDataHandler = start, end, chars
    parser.Parse(data, True)
    if any(old != new for _, _, old, new, _ in targets):
        for a, z, old, new, children in sorted(targets, reverse=True):
            if old != new or children:
                replacement = new.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;').encode()
                data = data[:a] + replacement + data[z:]
        data = data.replace(b'<item quantity="one"></item>', b'<item quantity="one"/>')
        # DOM drops whitespace between document-level nodes and after the root.
        # Preserve the original declaration and comments, including their content.
        prolog = re.match(rb'(?:[ \t\r\n]*(?:<\?.*?\?>|<!--.*?-->))*[ \t\r\n]*', data, re.DOTALL)
        tokens = re.findall(rb'<\?.*?\?>|<!--.*?-->', prolog.group(), re.DOTALL)
        data = b''.join(tokens) + data[prolog.end():].rstrip(b' \t\r\n')
    ET.fromstring(data)
    return data


def _resource_root(content, label):
    try:
        root = ET.fromstring(content)
    except ET.ParseError as exc:
        raise forest.ForestError(f'{label}: malformed resources XML: {exc}') from exc
    if root.tag != 'resources':
        raise forest.ForestError(f'{label}: expected resources root')
    return root


def _validate_please_wait(translation, target, line, label):
    """Check the injected resource and both locale files by parsed resource key."""
    for content, path in ((translation, label + ' strings.xml'), (target, label + ' strings2.xml')):
        root = _resource_root(content, path)
        if any(child.tag in ('string', 'plurals') and child.get('name') == 'please_wait'
               for child in root):
            raise forest.ForestError(f'unexpected please_wait resource in {path}')

    try:
        wrapper = ET.fromstring(b'<resources>' + line + b'</resources>')
    except ET.ParseError as exc:
        raise forest.ForestError(f'{label}: malformed please_wait resource line: {exc}') from exc
    if (wrapper.tag != 'resources' or wrapper.attrib or (wrapper.text and wrapper.text.strip()) or
            len(wrapper) != 1):
        raise forest.ForestError(f'{label}: invalid please_wait resource line')
    resource = wrapper[0]
    if (resource.tag != 'string' or resource.get('name') != 'please_wait' or
            len(resource) != 0 or (resource.tail and resource.tail.strip())):
        raise forest.ForestError(f'{label}: invalid please_wait resource line')


def validate(data, label):
    import release
    keys = {'schema', 'type', 'source_commit', 'before_tree', 'after_tree', 'signal_tree',
            'reference_tree', 'base_inputs', 'translation_inputs', 'please_wait', 'output_sha256', 'preimages'}
    if not isinstance(data, dict) or set(data) != keys or data['schema'] != 1 or data['type'] != 'generate-translations':
        raise forest.ForestError(f'{label}: invalid translation manifest')
    for k in ('source_commit', 'before_tree', 'after_tree', 'signal_tree', 'reference_tree'):
        release.require_sha(data[k], f'{label} {k}')
    def valid_path(p):
        if (not isinstance(p, str) or not p or p.startswith('/') or '\\' in p or
            any(ord(c) < 32 or ord(c) == 127 for c in p) or
            any(x in ('', '.', '..', '.git') for x in p.split('/'))):
            raise forest.ForestError(f'{label}: unsafe path {p!r}')
    def valid_hash(h):
        if not isinstance(h, str) or not re.fullmatch('[0-9a-f]{64}', h):
            raise forest.ForestError(f'{label}: invalid SHA-256')
    for field in ('base_inputs', 'translation_inputs', 'output_sha256'):
        if not isinstance(data[field], dict) or not data[field]:
            raise forest.ForestError(f'{label}: invalid {field}')
        for p, h in data[field].items():
            valid_path(p); valid_hash(h)
    translations = set(data['translation_inputs'])
    bases = set(data['base_inputs'])
    if any(not re.fullmatch(r'.+/src/main/res/values-[^/]+/strings\.xml', p) for p in translations):
        raise forest.ForestError(f'{label}: invalid translation path')
    if {p.split('/src/main/res/')[0] + '/src/main/res/values/strings.xml' for p in translations} != bases:
        raise forest.ForestError(f'{label}: base coverage mismatch')
    waits = data['please_wait']
    if not isinstance(waits, dict) or set(waits) != {p for p in translations if p.startswith('app/src/main/res/values-')}:
        raise forest.ForestError(f'{label}: invalid please_wait inputs')
    targets = set()
    for p, item in waits.items():
        if not isinstance(item, dict) or set(item) != {'target', 'input_sha256', 'line'}:
            raise forest.ForestError(f'{label}: invalid please_wait entry')
        target = item['target']; valid_path(target); valid_hash(item['input_sha256'])
        if target != p[:-len('strings.xml')] + 'strings2.xml' or target in targets or not isinstance(item['line'], str):
            raise forest.ForestError(f'{label}: invalid please_wait target')
        targets.add(target)
    outputs = translations | targets
    if set(data['output_sha256']) != outputs or not isinstance(data['preimages'], dict) or set(data['preimages']) != outputs:
        raise forest.ForestError(f'{label}: output coverage mismatch')
    for p, item in data['preimages'].items():
        valid_path(p)
        if item is None:
            continue
        if not isinstance(item, dict) or set(item) != {'mode', 'blob'} or item['mode'] not in ('100644', '100755'):
            raise forest.ForestError(f'{label}: invalid preimage')
        release.require_sha(item['blob'], f'{label} preimage blob')
    return data


def manifest(path, relative):
    raw = path.read_bytes()
    try:
        def pairs(items):
            result = {}
            for k, v in items:
                if k in result:
                    raise ValueError('duplicate key: ' + k)
                result[k] = v
            return result
        data = validate(json.loads(raw, object_pairs_hook=pairs), relative)
    except (ValueError, TypeError, KeyError) as exc:
        raise forest.ForestError(f'{relative}: invalid translation manifest: {exc}') from exc
    return {'path': relative, 'sha256': digest(raw), 'source_commit_shas': [data['source_commit']], 'translation': data}


def generate(repo, worktree, data, env, *, dry_run=False):
    import release
    if forest.git(worktree, 'rev-parse', 'HEAD^{tree}') != data['before_tree']:
        raise forest.ForestError('translation before-tree mismatch')
    verify_complete.clean_worktree(worktree)
    entries = verify_complete.tree_entries(repo, data['before_tree'])
    signal_entries = verify_complete.tree_entries(repo, data['signal_tree'])
    signal_paths = {os.fsdecode(p) for p in signal_entries
                    if re.fullmatch(r'.+/src/main/res/values-[^/]+/strings\.xml', os.fsdecode(p))}
    if signal_paths != set(data['translation_inputs']):
        raise forest.ForestError('Signal translation path coverage mismatch')
    def read_local(p, expected):
        path = release.inside(worktree, p, 'translation input')
        if not path.is_file() or not stat.S_ISREG(path.stat().st_mode):
            raise forest.ForestError(f'not a regular translation input: {p}')
        b = path.read_bytes()
        if digest(b) != expected:
            raise forest.ForestError(f'translation input mismatch: {p}')
        return b
    bases = {p: read_local(p, h) for p, h in data['base_inputs'].items()}
    for p, item in data['preimages'].items():
        expected = None if item is None else (item['mode'].encode(), b'blob', item['blob'].encode())
        if entries.get(os.fsencode(p)) != expected:
            raise forest.ForestError(f'translation preimage mismatch: {p}')
        path = release.inside(worktree, p, 'translation output')
        if item is None:
            if path.exists() or path.is_symlink():
                raise forest.ForestError(f'unexpected translation output: {p}')
            continue
        if not path.is_file() or not stat.S_ISREG(path.stat().st_mode):
            raise forest.ForestError(f'not a regular translation output: {p}')
        if bool(path.stat().st_mode & 0o111) != (item['mode'] == '100755'):
            raise forest.ForestError(f'translation mode mismatch: {p}')
    # Use the same Git-blob computation as report preparation after checking
    # the materialized worktree's preimages and modes above.
    with tempfile.TemporaryDirectory(prefix='molly-translations-') as tmp:
        index = str(Path(tmp) / 'index')
        git_utils.git(worktree, 'read-tree', data['before_tree'], env=env, index_file=index)
        tree = generate_index(repo, data, env, index, dry_run=dry_run)
    if dry_run:
        return tree
    if tree != data['after_tree']:
        raise forest.ForestError(f'translation after-tree mismatch: {tree}')
    parent = forest.git(worktree, 'rev-parse', 'HEAD')
    commit = git_utils.git(worktree, '-c', 'commit.gpgSign=false', 'commit-tree', tree, '-p', parent,
                           '-m', 'Generate pinned translations', env=env).stdout.strip()
    git_utils.git(worktree, 'reset', '--quiet', '--hard', commit, env=env)
    verify_complete.clean_worktree(worktree)
    if forest.git(worktree, 'rev-parse', 'HEAD^{tree}') != tree:
        raise forest.ForestError('translation materialization tree mismatch')


def generate_index(repo, data, env, index, *, dry_run=False):
    """Apply a pinned translation stage to a private index without a checkout."""
    if git_utils.git(repo, 'write-tree', env=env, index_file=index).stdout.strip() != data['before_tree']:
        raise forest.ForestError('translation before-tree mismatch')
    entries = verify_complete.tree_entries(repo, data['before_tree'])
    signal_entries = verify_complete.tree_entries(repo, data['signal_tree'])
    signal_paths = {os.fsdecode(p) for p in signal_entries
                    if re.fullmatch(r'.+/src/main/res/values-[^/]+/strings\.xml', os.fsdecode(p))}
    if signal_paths != set(data['translation_inputs']):
        raise forest.ForestError('Signal translation path coverage mismatch')

    def read_entry(tree_entries, path, expected_hash):
        entry = tree_entries.get(os.fsencode(path))
        if not entry or entry[0] not in (b'100644', b'100755') or entry[1] != b'blob':
            raise forest.ForestError(f'not a regular translation input: {path}')
        content = git_utils.git(repo, 'cat-file', 'blob', entry[2].decode(), env={**env, 'GIT_NO_LAZY_FETCH': '1'}, binary=True).stdout
        if digest(content) != expected_hash:
            raise forest.ForestError(f'translation input mismatch: {path}')
        return content

    bases = {p: read_entry(entries, p, h) for p, h in data['base_inputs'].items()}
    for p, item in data['preimages'].items():
        expected = None if item is None else (item['mode'].encode(), b'blob', item['blob'].encode())
        if entries.get(os.fsencode(p)) != expected:
            raise forest.ForestError(f'translation preimage mismatch: {p}')
    output = {}
    for p, h in data['translation_inputs'].items():
        if signal_entries.get(os.fsencode(p), (None,))[0] != b'100644':
            raise forest.ForestError(f'missing Signal translation: {p}')
        content = read_entry(signal_entries, p, h)
        base = p.split('/src/main/res/')[0] + '/src/main/res/values/strings.xml'
        output[p] = transform(content, bases[base], p)
    for p, item in data['please_wait'].items():
        target = item['target']
        content = read_entry(entries, target, item['input_sha256'])
        line = item['line'].encode()
        _validate_please_wait(output[p], content, line, target)
        if content.count(b'</resources>') != 1:
            raise forest.ForestError(f'unexpected resources closing tag count: {target}')
        content = content.replace(b'</resources>', line + b'</resources>')
        ET.fromstring(content)
        output[target] = content
    for p, content in output.items():
        if digest(content) != data['output_sha256'][p]:
            raise forest.ForestError(f'translation output mismatch: {p}')
    for p, content in output.items():
        blob = subprocess.run(['git', '-C', str(repo), 'hash-object', '-w', '--stdin'], input=content,
                              stdout=subprocess.PIPE, stderr=subprocess.PIPE, env=env, check=True).stdout.decode().strip()
        mode = (data['preimages'][p] or {'mode': '100644'})['mode']
        git_utils.git(repo, 'update-index', '--add', '--cacheinfo', mode, blob, p, env=env, index_file=index)
    actual = git_utils.git(repo, 'write-tree', env=env, index_file=index).stdout.strip()
    if not dry_run and actual != data['after_tree']:
        raise forest.ForestError(f'translation after-tree mismatch: {actual}')
    return actual
