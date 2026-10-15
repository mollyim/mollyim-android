"""Choose a PR's verification scope; unknown or build-sensitive paths use full checks."""
import re
import sys
from pathlib import PurePosixPath


def profile(paths):
    paths = list(paths)
    if not paths or len(paths) >= 3000:
        return "full"
    if all(("/" not in path and path.endswith(".md")) or path.startswith("docs/") for path in paths):
        return "docs"
    for path in paths:
        filename = PurePosixPath(path).name
        if (path.startswith((".github/", "gradle/", "build-logic/", "buildSrc/", "mk/", "reproducible-builds/"))
                or filename in {"Dockerfile", "Makefile", "gradlew", "gradlew.bat", ".tool-versions", "lint.xml", "AndroidManifest.xml"}
                or filename.startswith('gradle.properties')
                or filename.endswith((".gradle", ".gradle.kts", ".properties", ".toml"))
                or re.search(r"(^|/)src/(?!main/|test/|testShared/)", path)):
            return "full"
    return "representative"


if __name__ == "__main__":
    print(profile(line.rstrip("\n") for line in sys.stdin))
