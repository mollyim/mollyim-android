# Molly as patches on a Signal base

The tracked application source in this repository is an exact Signal release.
Molly is stored as a set of patches (`patches/molly`) and rebuilt on demand in a
separate worktree. The rebuilt tree must equal the official Molly tree byte for
byte, so reviewing Molly means reviewing three separate things: the pipeline
(`tools/patches`), the official Molly snapshot, and any later changes to the
patches.

## Inputs

| Input | Immutable source |
| --- | --- |
| Signal 8.19.2 | `c47a74ba0961903eeaeda6d22347a990056c3e83` |
| Official Molly 8.19.2-4 | `0e202dda90785771ff8c87526eaaf30c655f54f3` |
| Official Molly source tree | `3cd0bb01cf840e4b702187085432020d163d80ce` |

`molly-build.json` selects the release. `patches/molly` holds the original
mailbox patches; their bytes, ordering, binary payloads, executable modes and
whitespace are kept as exported. The patch grouping was organized by AI and adds
no application logic. The pipeline and its tests are also AI-assisted. The
history of the pipeline is in [an earlier pull
request](https://github.com/0cwa/collieim-android/tree/cef8e044f55d1a47c5634f500e01b3956ee31f4f);
it is provenance only and nothing here depends on it.

## Repository files

`.github/**`, `README.md` and `CONTRIBUTING.md` belong to this repository, not to
Signal's source. They are compared with the pull request's base, never with
Signal. A pull request may add `.github/workflows/molly-patch-release.yml` and
edit the Molly workflows that run the pipeline (`release.yml`, `reprocheck.yml`,
`tagrelease.yml`, `test.yml`); any other change to these files is rejected. Signal's
own root `.github` files must not appear.

## Checks on a pull request

`tools/patches/scope.py --base REF` compares the base and head trees and decides
what kind of change this is. The branch name is never used.

| Scope | Detected when | Extra requirements |
| --- | --- | --- |
| `molly` | the base has no `patches/molly`, the head adds it | base differs from the official Molly tree only in repository files; head is Signal 8.19.2 plus pipeline paths |
| `port` | the base has `patches/molly` and no port, the head adds `patches/ports` | head equals the Signal commit named in `molly-build.json` outside pipeline paths |
| `change` | the base has `patches/molly`, and either no port is touched or the base already has one | head equals its pinned Signal commit outside pipeline paths |

`--scope` overrides detection. The base needs no ancestry relation to the head,
so shallow clones work. Every scope also runs the repository-file check above and
requires the head's Signal source to be untouched outside the pipeline's own
paths: an application edit cannot hide in a broad exemption.

## Reconstruct and prove the source

Git (with `merge-tree --write-tree` support) and Python 3.10+ are enough.

```sh
git fetch --no-tags https://github.com/mollyim/mollyim-android.git \
  0e202dda90785771ff8c87526eaaf30c655f54f3
python3 -m unittest discover -s tools/patches -p 'test_*.py' -v
python3 tools/patches/release.py --repo . --config molly-build.json \
  --worktree /tmp/molly-release --report /tmp/molly-source-proof.json
```

The destination must be new and outside the checkout. Without `--base`,
`release.py` accepts only pipeline paths and repository files as differences from
the pinned Signal commit. With `--base REF` it runs the scope check first.

The assembler rebuilds the official Molly tree from Signal and the patches,
replays both legal patch orders, checks provenance and path ownership, and
requires exact whole-tree equality, including the index and working files.

Locally, `tools/patches/preflight.sh proof --base REF` runs the tests and the
reconstruction, writing to `$MOLLY_PREFLIGHT_DIR`. `resources` adds Android
resource linking and `full` runs the profile's assemble and test tasks as CI
does; the Android stages need JDK 21 and `ANDROID_HOME`.

## Build gates and limits

`.github/workflows/molly-patch-release.yml` runs a source-proof job and a
dependent Android job. It takes the pull request's base commit (or `origin/main`
on a manual run) and the scope is detected from the trees. The Android job
rebuilds the verified source on its own runner, installs the pinned toolchain,
assembles the Website debug APK and runs the configured test tasks.

Source equality, compilation, tests, reproducible binaries, signing and device
behavior are distinct claims. A source-tree proof is not an APK validation, and
an earlier CI result is not a fresh one. Do not assume a native binary checksum,
a build success or device acceptance that was not actually run.

## Pinned upstream inputs

The tools refuse inputs that differ from the pinned releases, so upstream
commits, trees and source URLs are compiled in. `python3 tools/patches/pins.py`
lists each pin with the files that contain it and the upstream whose next
release changes it. `test_pins.py` fails when a tool or workflow contains an
unlisted pin or a listed pin is missing. Data pins in `molly-build.json` and the
series provenance change with each port.

## Making a change day to day

1. Edit the patch series, not the tracked Signal source. Add a patch to
   `patches/molly` (or, once a port exists, to its layer under `patches/ports`),
   or change a tool under `tools/patches`.
2. Run `tools/patches/preflight.sh proof --base origin/main`. It tells you the
   scope it detected and fails on any application edit outside the patches.
3. Run `resources` or `full` when the change touches resources or code.
4. Open the pull request against the base branch. CI repeats the same checks.
5. Never edit a mailbox only to refresh offsets, and never edit a hash by hand;
   regenerate them with the tools.

## Updating to new upstream releases

Signal and Molly release on their own schedules. Supported updates: a change on
the current inputs; a new Signal with the same Molly; a new Molly; both at once.
Any other combination is unsupported: stop and ask. Update Molly first, then
Signal, then our own changes. `pins.py` lists the pins that move.

### New Signal release (a port PR)

1. Create `patches/ports/signal-NEW/` and its configuration, change the Signal
   pins, and pin the new mechanical tree (`molly-build.json`, `port` entry).
2. Replay the existing patches in order on the new Signal. Keep each layer's
   application diff when it still applies and is still needed; regenerate its
   envelope (source commit, bundle, trees, hashes). Prefer Molly's own fix for a
   conflict when one exists, and record it.
3. Move the root tree to the new Signal commit in the same PR; the projection
   check compares it with the commit in `molly-build.json`.
4. Check source bundles (`verify_source_bundles.py`) and run `preflight.sh full`.
   Keep the old series until the new chain passes every gate.

### New Molly release

1. Export the snapshot with `export_complete.py` into a new forest and prove the
   byte-identical rebuild. The Molly pins in `scope.py` and `molly-build.json`
   change together; keep the previous forest until the new one passes.
2. Rebuild any port on top, under the same rule as above.
3. For each of our own changes, check whether Molly now has an equivalent and ask
   before dropping one.

### Our own change

Add it at the end of its group and regenerate the envelope from that point (the
same rule as a port). `reexport_current.py` re-exports a series once the source
commits exist; it does not create a port or insert layers. Put tests in the same
layer.

Stop and ask before choosing the input set, changing layer order, dropping a
layer because Molly has an equivalent, or changing a credit mode or author.
