#!/usr/bin/env bash
# Run the CI gates locally before pushing a patch-series change.
#
#   tools/patches/preflight.sh            # proof: patch-tool tests + source reconstruction (~2 min)
#   tools/patches/preflight.sh resources  # proof + Android resource linking (catches missing @color/@string refs)
#   tools/patches/preflight.sh full       # proof + the profile's assemble and regression tests (as CI)
#
# The reconstructed source lands in $MOLLY_PREFLIGHT_DIR (default: ~/agent-scratch/mollyim-android/preflight)
# and is replaced on each run. Android stages need JDK 21 and ANDROID_HOME.
set -euo pipefail

stage=proof
scope=
scope_base=
while (($#)); do
  case "$1" in
    proof|resources|full) stage=$1; shift ;;
    --scope) scope=${2:?missing scope}; shift 2 ;;
    --base) scope_base=${2:?missing base}; shift 2 ;;
    *) echo "usage: $0 [proof|resources|full] [--base REF [--scope molly|port|change]]" >&2; exit 2 ;;
  esac
done
base=${MOLLY_PREFLIGHT_DIR:-$HOME/agent-scratch/mollyim-android/preflight}
mkdir -p "$base"
base=$(realpath -e -- "$base")
# Temporary forests must have canonical paths; inherited TMPDIR may contain symlinks.
mkdir -p "$base/tmp"
export TMPDIR
TMPDIR=$(realpath -e -- "$base/tmp")
[[ $TMPDIR == "$base/"* ]] || { echo 'preflight temporary directory escapes its root' >&2; exit 1; }
worktree="$base/release"
reports="$base/reports"


repo=$(git rev-parse --show-toplevel)
cd "$repo"
if [[ -f molly-build.json ]] && python3 -c 'import json; d=json.load(open("molly-build.json")); raise SystemExit(d.get("port") is None)'; then
  echo '== prepare port report trees'
  python3 tools/patches/prepare_port_report.py
fi

if [[ -n $scope && -z $scope_base ]]; then echo '--scope requires --base' >&2; exit 2; fi
if [[ -n $scope_base ]]; then
  # The scope is detected from the base and head trees unless --scope overrides it.
  python3 tools/patches/scope.py --base "$scope_base" ${scope:+--scope "$scope"}
fi

molly_target=$(python3 -c 'import json; print(json.load(open("molly-build.json"))["molly"]["target"])')

echo '== patch-tool tests'
python3 -m unittest discover -s tools/patches -p 'test_*.py'

if ! git cat-file -e "$molly_target^{commit}" 2>/dev/null; then
  echo "== fetching Molly oracle $molly_target"
  git fetch --no-tags --filter=blob:none https://github.com/mollyim/mollyim-android.git "$molly_target"
fi

# release.py refuses an existing destination; drop the previous run's worktree.
if [[ -e "$worktree" ]]; then
  git worktree remove --force "$worktree" 2>/dev/null || {
    echo "$worktree exists and is not a worktree of this repository; remove it or set MOLLY_PREFLIGHT_DIR" >&2
    exit 1
  }
fi
git worktree prune

echo '== source reconstruction and tree proof'
stack_args=()
if [[ -n $scope_base ]]; then stack_args=(--base "$scope_base"); fi
if [[ -n $scope ]]; then stack_args+=(--scope "$scope"); fi
python3 tools/patches/release.py --repo . --config molly-build.json \
  --worktree "$worktree" --report "$reports/source.json" "${stack_args[@]}" >/dev/null
python3 - "$reports/source.json" <<'PY'
import json, sys
report = json.load(open(sys.argv[1]))
if report.get('status') != 'PASS':
    sys.exit(f"source proof failed: {report.get('error')}")
print(f"source proof PASS, tree {report['tree']}")
PY

if python3 -c 'import json; d=json.load(open("molly-build.json")); raise SystemExit(d.get("port") is None)'; then
  echo '== source checkpoint bundles'
  python3 tools/patches/verify_source_bundles.py
  if [[ -f tools/patches/port_report.py ]]; then
    echo '== port report freshness'
    python3 tools/patches/port_report.py --check
  fi
fi

[[ $stage == proof ]] && exit 0

: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-}}"
if [[ -z $ANDROID_HOME ]]; then echo 'ANDROID_HOME must point at the Android SDK' >&2; exit 1; fi
export ANDROID_HOME
java_major=$(java -XshowSettings:properties -version 2>&1 | sed -n 's/.*java.specification.version = //p')
if [[ $java_major != 21 ]]; then echo "JDK 21 required (found ${java_major:-none}); set JAVA_HOME/PATH" >&2; exit 1; fi

build() { python3 tools/patches/build.py "$1" --worktree "$worktree" --report-dir "$reports"; }

echo '== pinned Android SDK components'
python3 tools/patches/build.py sdk --worktree "$worktree" --report-dir "$reports" --accept-licenses >/dev/null

if [[ $stage == resources ]]; then
  echo '== Android resource linking'
  (cd "$worktree" && ./gradlew --no-configuration-cache --max-workers=12 --dependency-verification=strict \
     :app:processProdWebsiteDebugResources)
  exit 0
fi

echo '== assemble'
build assemble
echo '== regression tests'
build test
