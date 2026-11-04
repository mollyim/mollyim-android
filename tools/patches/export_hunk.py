#!/usr/bin/env python3
"""Export one port-report hunk as a forward patch without changing the repository."""
import argparse
import os
import subprocess
import sys

import port_report


def export(base, target, wanted, path_hint=None):
    if path_hint == '':
        raise ValueError('path must not be empty')
    stats = port_report.numstat(base, target, path_hint)
    modes = port_report.modes(base, target, path_hint)
    if path_hint is not None:
        # A Git pathspec can also match descendants; the hint is an exact path.
        selected = os.fsencode(path_hint)
        stats = {p: value for p, value in stats.items() if p == selected}
        modes = {p: value for p, value in modes.items() if p == selected}
    if set(stats) != set(modes):
        raise ValueError('raw diff paths do not match numstat')
    matches = []
    for path, counts in sorted(stats.items()):
        old_mode, new_mode, old_blob, new_blob = modes[path]
        if counts is None:
            key = port_report.hunk_identity(path, b'binary', old_blob, new_blob,
                                            old_mode.encode(), new_mode.encode())
            if key == wanted:
                matches.append((None, 'binary records are unsupported'))
            continue
        if old_mode != new_mode:
            key = port_report.hunk_identity(path, b'mode', old_mode.encode(), new_mode.encode())
            if key == wanted:
                matches.append((None, 'mode records are unsupported'))
        patch = port_report.git('diff', '--no-ext-diff', '--no-renames', '--unified=3',
                                base, target, '--', os.fsdecode(path))
        hunks = port_report.text_hunks(path, patch)
        if (sum(h[0] for h in hunks), sum(h[1] for h in hunks)) != counts:
            raise ValueError('hunk counts do not match numstat for ' + os.fsdecode(path))
        if not hunks:
            continue
        first = patch.find(hunks[0][2])
        if first < 0:
            raise ValueError('cannot locate first hunk')
        header = patch[:first]
        offset = first
        for _, _, body, key, _ in hunks:
            pos = patch.find(body, offset)
            if pos < 0:
                raise ValueError('cannot locate hunk')
            end = pos + len(body)
            # The report's historical ID excludes a marker following the final line.
            marker = b'\\ No newline at end of file\n'
            if patch.startswith(marker, end):
                end += len(marker)
            if key == wanted:
                if old_mode == '000000' or new_mode == '000000':
                    matches.append((None, 'added and deleted files are unsupported'))
                elif old_mode != new_mode or old_mode not in ('100644', '100755'):
                    matches.append((None, 'mode changes and non-regular files are unsupported'))
                else:
                    matches.append((header + patch[pos:end], None))
            offset = end
    if not matches:
        raise ValueError('unknown hunk ID: ' + wanted +
                         (' at path: ' + path_hint if path_hint is not None else ''))
    if len(matches) != 1:
        raise ValueError('ambiguous hunk ID: ' + wanted)
    result, error = matches[0]
    if error:
        raise ValueError(error)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', required=True)
    parser.add_argument('--base', required=True)
    parser.add_argument('--target', required=True)
    parser.add_argument('--hunk-id', required=True)
    parser.add_argument('--path', help='exact path of the hunk (optional)')
    args = parser.parse_args()
    try:
        os.chdir(args.repo)
        sys.stdout.buffer.write(export(args.base, args.target, args.hunk_id, args.path))
    except (ValueError, OSError, subprocess.CalledProcessError) as exc:
        print('hunk export failed: ' + str(exc), file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
