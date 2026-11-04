"""Focused synthetic tests for the current resolution-series exporter."""
import hashlib
import json
from pathlib import Path
import subprocess
import unittest
from unittest.mock import patch

import reexport_current as exporter
import test_source_bundles


class ReexportTest(unittest.TestCase):
    def setUp(self):
        fixture = test_source_bundles.SourceBundlesTest()
        fixture.setUp()
        self.addCleanup(fixture.doCleanups)
        self.__dict__.update({k: v for k, v in fixture.__dict__.items() if not k.startswith("_")})
        self.git, self.tree, self.write_prov = fixture.git, fixture.tree, fixture.write_prov
        initial = self.prov['initial_source']
        initial_tree = self.tree(initial)
        self.git(self.source, 'checkout', '-q', '--detach', initial)
        (self.source / 'discard').write_text('delete me\n')
        self.git(self.source, 'add', 'discard')
        self.git(self.source, 'commit', '-qm', 'Temporary addition\n\nSource-Commit: ' + initial)
        added_tree = self.git(self.source, 'rev-parse', 'HEAD^{tree}')
        blob = self.git(self.source, 'rev-parse', 'HEAD:discard')
        port = self.repo / 'patches/ports/test'
        first = port / 'initial.patch'
        first.write_bytes(subprocess.check_output(['git', '-C', str(self.source), 'format-patch', '--stdout', '-1']))
        deletion = port / 'delete.json'
        deletion.write_text(json.dumps(dict(schema=1, type='delete-files', source_commit=initial,
            before_tree=added_tree, after_tree=initial_tree,
            files=[dict(path='discard', mode='100644', blob=blob)])))
        names = ['initial.patch', 'delete.json']
        self.prov['layers'][0].update(before_tree=initial_tree, patches=names[:], mailbox_bytes=987654)
        for layer in self.prov['layers'][1:]:
            commit = layer['source_commit']
            raw = subprocess.check_output(['git', '-C', str(self.source), 'format-patch', '--stdout', '-1', commit])
            raw = raw.replace(b'\n---\n', b'\nSource-Commit: ' + commit.encode() + b'\n\n---\n', 1)
            name = layer['id'] + '.patch'
            target = port / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(raw)
            layer.update(patches=[name], mailbox_bytes=len(raw))
            names.append(name)
        (port / 'series').write_text('\n'.join(names) + '\n')
        self.prov['layers'][-1]['description'] = 'Keep prose.\n\nBased-On-Source-Commit: ' + self.prov['layers'][-2]['source_commit'] + '\n'
        self.prov.update(origin_base=self.prov['origin_target'], mechanical_tree=initial_tree,
            mailbox_sha256={name: hashlib.sha256((port / name).read_bytes()).hexdigest() for name in names})
        self.write_prov()
        config_path = self.repo / 'molly-build.json'
        config = json.loads(config_path.read_text())
        config['port']['mechanical_tree'] = initial_tree
        config_path.write_text(json.dumps(config))
        (self.repo / '.git/objects/info/alternates').write_text(str(self.source / '.git/objects') + '\n')
        self.initial_tree = initial_tree
        self.port = port

    def run_export(self, output, checkpoints=None, replacements=None):
        with patch.object(exporter.release, 'mechanical_merge', return_value=(self.initial_tree, set(), '')):
            exporter.reexport(self.repo, output, checkpoints or {}, replacements or {})

    def test_roundtrip_preserves_historical_bytes_and_caller_index(self):
        index = self.repo / '.git/index'
        before = index.read_bytes() if index.exists() else None
        out = self.root / 'result'
        self.run_export(out)
        prov = json.loads((out / 'patches/ports/test/provenance.json').read_text())
        self.assertEqual(prov['layers'][0]['mailbox_bytes'], 987654)
        self.assertEqual(prov['target_tree'], self.prov['target_tree'])
        self.assertEqual(index.read_bytes() if index.exists() else None, before)
        self.assertEqual((out / 'patches/ports/test/initial.patch').read_bytes(), (self.port / 'initial.patch').read_bytes())

    def test_rejects_deletion_preimage_and_removes_output(self):
        path = self.port / 'delete.json'
        data = json.loads(path.read_text())
        data['files'][0]['blob'] = '0' * 40
        path.write_text(json.dumps(data))
        self.prov['mailbox_sha256']['delete.json'] = hashlib.sha256(path.read_bytes()).hexdigest()
        self.write_prov()
        out = self.root / 'result'
        with self.assertRaisesRegex(ValueError, 'preimage mismatch'):
            self.run_export(out)
        self.assertFalse(out.exists())

    def test_rejects_unknown_checkpoint(self):
        with self.assertRaisesRegex(ValueError, 'unknown checkpoint'):
            self.run_export(self.root / 'result', {'0' * 40: '1' * 40})

    def test_bundled_followup_uses_new_commit_message_and_exact_diff(self):
        last = self.prov['layers'][-1]
        old = last['source_commit']
        parent = self.prov['layers'][-2]['source_commit']
        self.git(self.source, 'checkout', '-q', '--detach', parent)
        (self.source / 'file').write_text('before\n\n\nafter\n')
        self.git(self.source, 'add', 'file')
        self.git(self.source, 'commit', '-qm', 'Changed followup message')
        new = self.git(self.source, 'rev-parse', 'HEAD')
        raw = subprocess.check_output(['git', '-C', str(self.source), 'format-patch', '--stdout', '--abbrev=8', '-1', new])
        head, sep, body = raw.partition(b'\n---\n')
        expected = (head + b'\n\nBased-On-Source-Commit: ' + parent.encode() +
                    b'\nSource-Commit: ' + new.encode() + b'\n' + sep + body)
        out = self.root / 'changed'
        self.run_export(out, {old: new})
        result = out / 'patches/ports/test' / last['patches'][0]
        self.assertEqual(result.read_bytes(), expected)
        self.assertEqual(json.loads((out / 'molly-build.json').read_text())['target_tree'], self.tree(new))

    def test_wrong_checkpoint_parent_fails_closed(self):
        old = self.prov['layers'][-1]['source_commit']
        wrong = self.prov['layers'][-3]['source_commit']
        with self.assertRaises((ValueError, exporter.git_utils.GitError)):
            self.run_export(self.root / 'wrong', {old: wrong})
        self.assertFalse((self.root / 'wrong').exists())

    def test_rejects_output_under_source_through_symlink(self):
        link = self.root / 'source-link'
        link.symlink_to(self.repo, target_is_directory=True)
        with self.assertRaisesRegex(ValueError, 'outside source'):
            self.run_export(link / 'candidate')
        self.assertFalse((self.repo / 'candidate').exists())

    def test_changed_initial_patch_replays_downstream_deletion_and_checkpoints(self):
        old_initial = self.prov['initial_source']
        self.git(self.source, 'checkout', '-q', '--detach', old_initial)
        (self.source / 'keep').write_text('old content\n\n\n')
        self.git(self.source, 'add', 'keep')
        new_tree = self.git(self.source, 'write-tree')
        new_initial = self.git(self.source, 'commit-tree', new_tree, '-p', self.prov['origin_target'],
                               '-p', self.prov['signal'], '-m', 'new initial')
        self.git(self.source, 'reset', '--hard', '-q', old_initial)
        (self.source / 'keep').write_text('old content\n\n\n')
        (self.source / 'discard').write_text('delete me\n')
        self.git(self.source, 'add', 'keep', 'discard')
        self.git(self.source, 'commit', '-qm', 'changed initial\n\nSource-Commit: ' + new_initial)
        replacement = self.root / 'replacement.patch'
        replacement.write_bytes(subprocess.check_output(['git', '-C', str(self.source), 'format-patch', '--stdout', '-1']))
        self.git(self.source, 'checkout', '-q', '--detach', new_initial)
        checkpoints = {old_initial: new_initial}
        for layer in self.prov['layers'][1:]:
            old = layer['source_commit']
            content = subprocess.check_output(['git', '-C', str(self.source), 'show', old + ':file'])
            (self.source / 'file').write_bytes(content)
            if layer is self.prov['layers'][-1]:
                (self.source / 'keep').write_text('new content\n\n\n')
                self.git(self.source, 'add', 'keep')
            self.git(self.source, 'add', 'file')
            self.git(self.source, 'commit', '-qm', 'replayed ' + layer['id'])
            checkpoints[old] = self.git(self.source, 'rev-parse', 'HEAD')
        # Explicitly supply each replayed unbundled mailbox. Bundled mailboxes
        # are generated from their source commits by the exporter.
        replacements = {'initial.patch': str(replacement)}
        for layer in self.prov['layers'][1:]:
            if 'source_bundle' in layer:
                continue
            name = layer['patches'][0]
            new = checkpoints[layer['source_commit']]
            raw = subprocess.check_output(['git', '-C', str(self.source), 'format-patch', '--stdout', '-1', new])
            raw = raw.replace(b'\n---\n', b'\nSource-Commit: ' + new.encode() + b'\n\n---\n', 1)
            path = self.root / (layer['id'].split('/')[-1] + '.patch')
            path.write_bytes(raw)
            replacements[name] = str(path)
        out = self.root / 'changed-initial'
        self.run_export(out, checkpoints, replacements)
        original = json.loads((self.port / 'delete.json').read_text())
        result = json.loads((out / 'patches/ports/test/delete.json').read_text())
        self.assertEqual(result['files'], original['files'])
        self.assertEqual(result['after_tree'], new_tree)
        self.assertEqual(result['source_commit'], new_initial)
        prov = json.loads((out / 'patches/ports/test/provenance.json').read_text())
        self.assertEqual(prov['layers'][-1]['description'], 'Keep prose.\n\nBased-On-Source-Commit: ' + checkpoints[self.prov['layers'][-2]['source_commit']] + '\n')
        final_patch = (out / 'patches/ports/test' / self.prov['layers'][-1]['patches'][0]).read_bytes()
        self.assertIn(b'-old content\n+new content\n \n \n', final_patch)

    def translation_fixture(self):
        import copy
        import test_translation_stage
        fixture = test_translation_stage.TranslationStageTests()
        fixture.setUp()
        self.addCleanup(fixture.doCleanups)
        source = fixture.root
        data = copy.deepcopy(fixture.data)
        signal = fixture.git('rev-parse', 'HEAD^')
        molly = fixture.git('commit-tree', data['before_tree'], '-m', 'independent Molly')
        initial = fixture.git('commit-tree', data['after_tree'], '-p', molly, '-p', signal, '-m', 'initial')
        data['source_commit'] = initial
        receiver = self.root / 'translations'
        receiver.mkdir()
        self.git(receiver, 'init', '-q')
        (receiver / '.git/objects/info/alternates').write_text(str(source / '.git/objects') + '\n')
        port = receiver / 'patches/ports/test'
        port.mkdir(parents=True)
        (port / 'series').write_text('translate.json\n')
        stage = port / 'translate.json'
        stage.write_text(json.dumps(data))
        ref = 'refs/heads/source-checkpoint/core'
        self.git(source, 'update-ref', ref, initial)
        bundle = port / 'source-checkpoint.bundle'
        self.git(source, 'bundle', 'create', str(bundle), ref, '^' + molly, '^' + signal)
        base = dict(path=bundle.name, ref=ref, source_commit=initial, source_tree=data['after_tree'],
                    prerequisites=[molly, signal], sha256=hashlib.sha256(bundle.read_bytes()).hexdigest(),
                    bytes=bundle.stat().st_size)
        provenance = dict(origin_base=signal, origin_target=molly, signal=signal,
            mechanical_tree=data['before_tree'], initial_source=initial, target_tree=data['after_tree'],
            source_bundle_base=base, layers=[dict(id='initial/translations', source_commit=initial,
                before_tree=data['before_tree'], after_tree=data['after_tree'], patches=['translate.json'], mailbox_bytes=42)],
            mailbox_sha256={'translate.json': hashlib.sha256(stage.read_bytes()).hexdigest()})
        config = dict(schema=1, signal=dict(commit=signal), molly=dict(target=molly, tree=data['before_tree']),
            port=dict(mechanical_tree=data['before_tree'], series='patches/ports/test/series'), target_tree=data['after_tree'])
        (receiver / 'molly-build.json').write_text(json.dumps(config))
        (port / 'provenance.json').write_text(json.dumps(provenance))
        return receiver, port, data, provenance

    def test_translation_unchanged_and_rejects_changed_preimage_or_output(self):
        receiver, port, data, provenance = self.translation_fixture()
        def run(name):
            with patch.object(exporter.release, 'mechanical_merge', return_value=(data['before_tree'], set(), '')):
                exporter.reexport(receiver, self.root / name, {}, {})
        run('translation-ok')
        result = json.loads((self.root / 'translation-ok/patches/ports/test/translate.json').read_text())
        self.assertEqual(result['output_sha256'], data['output_sha256'])
        self.assertEqual(result['preimages'], data['preimages'])
        import copy
        for field in ('preimages', 'output_sha256'):
            altered = copy.deepcopy(data)
            key = next(iter(altered[field]))
            if field == 'preimages':
                altered[field][key]['blob'] = '0' * 40
            else:
                altered[field][key] = '0' * 64
            stage = port / 'translate.json'
            stage.write_text(json.dumps(altered))
            provenance['mailbox_sha256']['translate.json'] = hashlib.sha256(stage.read_bytes()).hexdigest()
            (port / 'provenance.json').write_text(json.dumps(provenance))
            with self.subTest(field=field), self.assertRaisesRegex(Exception, 'translation (preimage|output) mismatch'):
                run('translation-' + field)
            self.assertFalse((self.root / ('translation-' + field)).exists())

    def test_rejects_bad_description_identity(self):
        self.prov['layers'][-1]['description'] = 'Based-On-Source-Commit: invalid'
        self.write_prov()
        with self.assertRaisesRegex(ValueError, 'invalid description parent identity'):
            self.run_export(self.root / 'bad-description')
