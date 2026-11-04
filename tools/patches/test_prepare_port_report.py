import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import prepare_port_report as prepare


class PrepareReportTests(unittest.TestCase):
    def test_fresh_repo_reconstructs_historical_binary_and_mode_and_rejects_bad_pins(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source, repo = root / 'source', root / 'repo'
            source.mkdir()
            def git(where, *args):
                return subprocess.check_output(['git', '-C', str(where), *args], stderr=subprocess.PIPE).decode().strip()
            git(source, 'init', '-q')
            git(source, 'config', 'user.name', 'Test')
            git(source, 'config', 'user.email', 'test@example.invalid')
            (source / 'app').write_text('old\n')
            git(source, 'add', 'app')
            git(source, 'commit', '-qm', 'base')
            base = git(source, 'rev-parse', 'HEAD')
            mechanical = git(source, 'rev-parse', 'HEAD^{tree}')
            (source / 'app').write_text('new\n')
            (source / 'app').chmod(0o755)
            (source / 'binary').write_bytes(b'\0\xff\x01')
            git(source, 'add', 'app', 'binary')
            git(source, 'commit', '-qm', 'change\n\nSource-Commit: ' + base)
            target = git(source, 'rev-parse', 'HEAD^{tree}')
            mailbox = subprocess.check_output(['git', '-C', str(source), 'format-patch', '--binary', '--stdout', '-1'])
            git(source, 'checkout', '-qb', 'metadata', base)
            port = source / 'patches/ports/test'
            port.mkdir(parents=True)
            (port / 'series').write_text('0001-change.patch\n')
            (port / '0001-change.patch').write_bytes(mailbox)
            config = {'schema': 1, 'signal': {'commit': base}, 'molly': {'target': base, 'tree': mechanical},
                      'port': {'mechanical_tree': mechanical, 'series': 'patches/ports/test/series'}, 'target_tree': target}
            provenance = {'origin_base': base, 'origin_target': base, 'signal': base, 'mechanical_tree': mechanical,
                          'target_tree': target, 'mailbox_sha256': {'0001-change.patch': hashlib.sha256(mailbox).hexdigest()}}
            (source / 'molly-build.json').write_text(json.dumps(config))
            (port / 'provenance.json').write_text(json.dumps(provenance))
            git(source, 'add', '.')
            git(source, 'commit', '-qm', 'historical profile')
            historical = git(source, 'rev-parse', 'HEAD')
            (port / 'series').write_text('')
            current_config = dict(config, target_tree=mechanical)
            current_provenance = dict(provenance, target_tree=mechanical, mailbox_sha256={})
            (source / 'molly-build.json').write_text(json.dumps(current_config))
            (port / 'provenance.json').write_text(json.dumps(current_provenance))
            (port / 'report-metadata.json').write_text(json.dumps({'baseline_profile_commit': historical,
                'baseline_target_tree': target, 'baseline_mechanical_tree': mechanical}))
            git(source, 'add', '.')
            git(source, 'commit', '-qm', 'current profile')
            git(root, 'clone', '-q', '--no-local', '--single-branch', '-b', 'metadata', str(source), str(repo))
            self.assertNotEqual(subprocess.run(['git', '-C', str(repo), 'cat-file', '-e', target], stderr=subprocess.DEVNULL).returncode, 0)
            with patch.object(prepare.release, 'mechanical_merge', return_value=(mechanical, set(), '')):
                prepare.prepare(repo)
            self.assertEqual(git(repo, 'cat-file', '-t', target), 'tree')
            (repo / 'patches/ports/test/series').write_text('0001-change.patch\n')
            bad = dict(provenance, mailbox_sha256={'0001-change.patch': '0' * 64})
            with patch.object(prepare.release, 'mechanical_merge', return_value=(mechanical, set(), '')):
                with self.assertRaisesRegex(ValueError, 'mailbox hash mismatch'):
                    prepare.materialize(repo, config, bad, repo, root / 'bad-index')
            metadata_path = repo / 'patches/ports/test/report-metadata.json'
            metadata = json.loads(metadata_path.read_text())
            metadata['baseline_target_tree'] = mechanical
            metadata_path.write_text(json.dumps(metadata))
            with self.assertRaisesRegex(ValueError, 'immutable report baseline'):
                prepare.prepare(repo)

    def test_preparation_precedes_each_scope_projection(self):
        root = Path(__file__).resolve().parents[2]
        workflow = (root / '.github/workflows/molly-patch-release.yml').read_text()
        scope, proof, android = workflow.split('  source-proof:', 1)[0], *workflow.split('  source-proof:', 1)[1].split('  android:', 1)
        self.assertLess(scope.index('python3 tools/patches/prepare_port_report.py'), scope.index('python3 tools/patches/scope.py'))
        self.assertLess(scope.index('python3 tools/patches/prepare_port_report.py'), scope.index('python3 tools/patches/verify_source_bundles.py'))
        for job in (proof, android):
            self.assertLess(job.index('python3 tools/patches/prepare_port_report.py'), job.index('python3 tools/patches/release.py'))
        preflight = (root / 'tools/patches/preflight.sh').read_text()
        self.assertLess(preflight.index('python3 tools/patches/prepare_port_report.py'), preflight.index('python3 tools/patches/scope.py'))
        self.assertLess(preflight.index('python3 tools/patches/prepare_port_report.py'), preflight.index('python3 tools/patches/release.py'))
