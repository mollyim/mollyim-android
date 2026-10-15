"""Select a published Memoly baseline from GitHub's newest-first tag list."""
import re
import sys

PATTERN = re.compile(r'v[0-9][A-Za-z0-9._-]*-memoly\.[0-9]+')


def previous(tags, current):
    if not PATTERN.fullmatch(current):
        raise ValueError('Expected a Memoly release tag')
    return next((tag for tag in tags if tag != current and PATTERN.fullmatch(tag)), '')


if __name__ == '__main__':
    print(previous(sys.stdin.read().splitlines(), sys.argv[1]))
