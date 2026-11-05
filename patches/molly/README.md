# Complete pinned Molly patch forest

This forest reconstructs the complete tracked source tree of **Molly 8.19.2-4**
from its merged **Signal 8.19.2** base. It includes every added, modified and
deleted path in that pinned comparison, including binary files, file modes,
resources, build configuration and user documentation.

The separate [`patches/827`](../827) forest remains the adapted launcher-artwork
and UnifiedPush pilot. The complete forest starts from Signal directly; it is
not applied on top of the pilot and does not replace the pilot's adaptations or
regression tests.

## Source pins

| Input | Pinned object |
| --- | --- |
| Signal 8.19.2 base (`base`) | `c47a74ba0961903eeaeda6d22347a990056c3e83` |
| Molly's upstream merge (`upstream-merge`) | `3e0860a84b4e13c33c11adbaaabbe4cab8f10c63` |
| Molly 8.19.2-4 target (`target`) | `0e202dda90785771ff8c87526eaaf30c655f54f3` |
| Complete target tree | `3cd0bb01cf840e4b702187085432020d163d80ce` |

Molly's [upstream merge](https://github.com/mollyim/mollyim-android/commit/3e0860a84b4e13c33c11adbaaabbe4cab8f10c63)
has the recorded Signal base as its second parent and is an ancestor of the
[target commit](https://github.com/mollyim/mollyim-android/commit/0e202dda90785771ff8c87526eaaf30c655f54f3).
The target's `app/build.gradle.kts` records version `8.19.2` and Molly revision `4`.
These pins describe a specific source snapshot, not the latest release or a port
onto a newer Signal version.

## Review groups

The forest contains 65 ordinary binary-capable `git format-patch` mailboxes and plain
`series`/`depends` files. Each changed path belongs to exactly one review group.
Files stay whole; selected shared integration files and otherwise unclassified
paths belong to `integration`. The comparison contains
3,178 paths when renames are represented as a deletion and an addition.

| Group | Paths | Contents |
| --- | ---: | --- |
| `security-and-locking` | 35 | Passphrase/Argon2, encrypted preferences, biometrics, secret handling, memory wiping and lock-aware jobs |
| `networking-and-proxies` | 41 | SOCKS/Tor/Orbot, DNS, connectivity, proxy transports and replaced Signal proxy code |
| `notifications-and-unifiedpush` | 41 | UnifiedPush/MollySocket, push-service selection and notifications |
| `registration-and-linking` | 37 | Registration policies, Android linked devices and provisioning |
| `backup-and-restore` | 27 | Backup scheduling, retention, compatibility and restore integration |
| `app-updates` | 12 | APK updater, jobs and settings |
| `privacy-controls` | 15 | Unknown-contact controls, disappearing calls and optional debug logging |
| `maps` | 15 | OpenStreetMap/osmdroid and Maps API compatibility |
| `google-services` | 81 | FOSS Google-services compatibility implementations and processors |
| `payments-and-donations` | 270 | MobileCoin/billing removal, migration compatibility and donation support |
| `appearance` | 26 | Dynamic colors, themes, wallpapers and UI helpers |
| `resources-and-branding` | 1,085 | Shared translations, artwork, layouts, icons, XML policies and resource removals |
| `module-pruning` | 851 | Removed demos, build flavors, benchmarks and screenshot-test infrastructure |
| `integration` | 519 | Shared application/core/library integration and mixed-purpose changes |
| `build-and-release` | 116 | Gradle conventions, dependency pins, native integration, lint, reproducibility and CI/release automation |
| `documentation` | 7 | User/build/legal documentation and superseded documentation removal |

These are review and maintenance groups, not independently buildable or optional
features. The `depends` files put all implementation, resource and pruning groups
before `integration`, followed by `build-and-release`. Documentation has no
declared dependencies.
Disjoint paths make ownership auditable; they do not establish runtime
independence. The complete forest is the supported reconstruction.

### Mailbox provenance

Each mailbox records `Source-Commit:` pointing to the pinned target snapshot.
Its `From` commit ID identifies the newly exported snapshot change, not an
original upstream feature commit. This grouping preserves the final maintained
tree, including merge-resolution work, rather than replaying every historical
development commit.

The target includes Molly changes, upstream fixes/backports, microG-derived
implementations, NetCipher imports and build-policy changes. Source attribution
and license headers remain in their files. A snapshot source trailer does not
claim that Molly authored every line or that every changed path is a distinct
feature.

## Inspect and verify

Run from the repository root with Git and Python 3.10+. The local repository
must contain the pinned base, upstream merge and target history, along with the
objects needed to check out and compare their trees. If the Molly history is
missing, fetch the pinned target from its source repository:

```sh
git fetch --no-tags https://github.com/mollyim/mollyim-android.git \
  0e202dda90785771ff8c87526eaaf30c655f54f3
```

Inspect the forest and run its complete-source verifier:

```sh
python3 tools/827/forest.py check --patches patches/molly
python3 tools/827/forest.py series --patches patches/molly
python3 tools/827/verify_complete.py --repo . --patches patches/molly
```

To retain the JSON result as a local or CI artifact:

```sh
python3 tools/827/verify_complete.py --repo . --patches patches/molly \
  --report /tmp/molly-complete-verification.json
```

To retain the reconstructed source for inspection or an Android build, choose a
new destination outside the source checkout:

```sh
python3 tools/827/verify_complete.py --repo . --patches patches/molly \
  --worktree /tmp/molly-complete --report /tmp/molly-complete-verification.json
```

The verifier checks the source pins and merge relationship, mailbox provenance,
disjoint ownership of actual applied changes, and coverage of every path in the
base-to-target delta. It replays the full forest in two legal ready-node orders.
Both results must equal the target's complete Git tree **without path or
metadata exclusions**. Added and deleted paths, binary content, executable modes
and symlinks are part of that equality check.

It also checks the materialized working files and index for changes and rejects
untracked files, including ignored files. A matching commit tree alone is not
enough if the checkout contains leftover or altered source files.

By default, verification creates temporary detached worktrees and removes them
on success or failure. With `--worktree`, the first reconstruction is retained
at the requested new path; the second replay remains temporary. An existing
destination is refused. A failed application at an explicitly requested path is
retained for inspection and must not be treated as a verified tree. The source
checkout's branch, index and uncommitted files are preserved.

### Original whitespace

The pinned Molly tree contains inherited whitespace findings. The complete
verifier preserves those exact bytes, requires the trusted target-tree match,
and reports the whitespace findings separately. The generic `forest.py apply`
and `forest.py verify` commands retain their strict final whitespace check and
can reject this snapshot. Use `verify_complete.py` for complete reconstruction;
normalizing the snapshot's whitespace would change the expected source tree.

## What verification establishes

A passing result establishes that every tracked file in this pinned Molly tree
can be reconstructed from its recorded Signal base using these mailboxes. It
does not establish Android compilation, test success, APK reproducibility,
runtime/security behavior or compatibility with a newer Signal release. Git LFS
pointer content is included in tree equality; downloading and verifying the
referenced payloads is a separate build prerequisite.

For Android builds, use the build instructions and toolchain in the reconstructed
target's `BUILDING.md` and `reproducible-builds/` directory. The #827 pilot runbook
describes the adapted pilot's build variants and is not the complete target's
build procedure.

## Regenerating the snapshot exports

`export_complete.py` derives the same review groups from the two pinned trees,
including both sides of renames. It uses a private index, a fixed reconstruction
identity, and the target commit's timestamp. It splits large groups into ordered
mailboxes without splitting individual file changes. Existing output directories
are refused; the source checkout's branch, index and working files are preserved.

```sh
python3 tools/827/export_complete.py --repo . \
  --base "$(cat patches/molly/base)" \
  --target "$(cat patches/molly/target)" \
  --upstream-merge "$(cat patches/molly/upstream-merge)" \
  --output /tmp/molly-forest-export
python3 tools/827/verify_complete.py --repo . --patches /tmp/molly-forest-export
```

The generated directory contains the pins, mailboxes, `series` and `depends`
files. The README and mailbox whitespace attributes are maintained alongside
those inputs. An upgrade changes the reviewed source pins and may require
adjusting the path grouping; verify the entire new target before replacing the
checked-in exports.

The [maintenance guide](../../docs/827/PATCH-FOREST.md) explains the shared patch
format and Git safeguards. [PARITY.md](../../docs/827/PARITY.md) distinguishes
complete-source equality from the adapted pilot's narrower assertions. Further
source-oracle and validation work is tracked in
[issue #3](https://github.com/0cwa/mollyim-android/issues/3).
