# Report action receipts

These records describe the actual checks, not independent reruns on each
report profile. Replay proves recorded file input and output blobs. It does not
prove the full tested source tree was identical to a report profile.

## Formatting checks

Sixteen changes were checked in a PR4-derived source context at tree
`70f2eba69967c4a3ae82dd9204999feb1bd70f9b` with the tested candidate actions
applied; the complete tested tree is not pinned. This is not a resource-link
run on PR3. Each associated XML-event log records `XML_EVENTS_EQUAL`. Each
associated resource log shows `:app:processProdWebsiteDebugResources` executed
and `BUILD SUCCESSFUL`, exit 0. The file blob identities and sequence are in
report metadata and the versioned action patches.

The command used JDK 21, the configured Android SDK, and `agent-heavy --
./gradlew --no-daemon --no-parallel --no-configuration-cache --max-workers=1
--dependency-verification=strict --init-script <test-worker-heap.init.gradle>
-Pkotlin.compiler.execution.strategy=in-process
'-Dorg.gradle.jvmargs=-Xmx5g -Xms256m -XX:MaxMetaspaceSize=1g -Dcom.android.tools.r8.threadCount=1 -XX:+UnlockExperimentalVMOptions -XX:hashCode=3'
:app:processProdWebsiteDebugResources`. Checks 15 and 16 used `--rerun`; their
original resource logs showed `UP-TO-DATE` and are superseded by the forced
executions. Check 15 was tested with change 16 reversed; check 16 was then
reapplied and checked separately. Source records in task scratch are
`xml-format-gates.tsv`, `xml-format-gates-notes.md`, and the numbered XML and
resource logs named by the metadata. Passing removal checks do not establish
necessity of any current hunk.

## Lexical checks

Seventy baseline files and 74 sequential restorations were individually
compiled using `aapt2 compile -o <output-dir> <relative-resource-path>` with
AAPT2 `9.4.0-15978811-linux`. Each recorded command exited 0 with empty output.
The inputs are the file blobs in the metadata; the baseline and final file
blobs match both report profiles. These are file-scoped compilation checks,
not full resource-link checks. The source record in task scratch is
`XML-LEXICAL-CHECK.tsv`, whose phase, hunk name, path and input blob identify
each command. The intermediate file blobs were reconstructed from the exact
recorded transformations and verified against that record; the versioned
patches make their replay independent of local dangling objects.

## Whole-file deletion inventory

The following tree-membership check classifies whole-file deletions from mechanical tree `759ef1c309d93aa7e4ac7217b144c04545562f11` to both PR3 source tree `129bd88f3f04dfb6b80a93b96ec026f6a5cd3010` and PR4 source tree `2b0ba46b23fd27974b8cefbd36e0d91791b525bd`. Inputs are old Signal `c47a74ba0961903eeaeda6d22347a990056c3e83`, new Signal `6e9dddc01f1b26e608ac683807c51e139c141ed2`, and Molly `0e202dda90785771ff8c87526eaaf30c655f54f3`. After running `python3 tools/patches/prepare_port_report.py` on both stack 3 and stack 4 so both target trees are available in the shared object store, reproduce the inventory and classification check from the repository root:

```sh
python3 - <<'PY'
import collections, json, subprocess
from pathlib import Path

def git(*args):
    return subprocess.check_output(['git', *args])
def paths(ref):
    return set(filter(None, git('ls-tree', '-r', '-z', '--name-only', ref).split(b'\0')))
old, new, molly, mechanical = map(paths, (
    'c47a74ba0961903eeaeda6d22347a990056c3e83',
    '6e9dddc01f1b26e608ac683807c51e139c141ed2',
    '0e202dda90785771ff8c87526eaaf30c655f54f3',
    '759ef1c309d93aa7e4ac7217b144c04545562f11'))
targets = [paths(x) for x in ('129bd88f3f04dfb6b80a93b96ec026f6a5cd3010',
                              '2b0ba46b23fd27974b8cefbd36e0d91791b525bd')]
deleted = mechanical - targets[0]
assert deleted == mechanical - targets[1]
annotations = json.loads(Path('patches/ports/signal-8.33.2/report-metadata.json').read_text())['deletions']
assert deleted == {p.encode() for p in annotations}
absent_roots = (b'demo/media-keyboard/', b'demo/list-detail/', b'demo/device-transfer/', b'lib/billing/')
assert all(not any(p.startswith(root) for p in target) for root in absent_roots for target in targets)
counts = collections.Counter()
for p in deleted:
    if p in old and p not in new:
        cls = 'Signal path removed'
    elif p in old and p in new and p not in molly:
        cls = 'Both Signal trees; absent in Molly'
    elif p not in old and p in new and p not in molly:
        cls = 'New Signal path under absent root' if p.startswith(absent_roots) else 'New Signal path under retained root'
    else:
        assert p in molly
        cls = 'Molly-present additional path'
    if cls == 'New Signal path under retained root':
        root = b'/'.join(p.split(b'/')[:2]) + b'/'
        assert all(any(q.startswith(root) for q in target) for target in targets)
    assert annotations[p.decode()]['classification'] == cls
    counts[cls] += 1
print(dict(sorted(counts.items())))
raw = git('diff', '--no-renames', '--numstat', '-z', '759ef1c309d93aa7e4ac7217b144c04545562f11', '129bd88f3f04dfb6b80a93b96ec026f6a5cd3010')
rows = [r.split(b'\t', 2) for r in raw.split(b'\0') if r]
selected = [(a, d, p) for a, d, p in rows if p in deleted]
print('paths, text, binary, deleted lines:', len(selected), sum(a != b'-' for a, _, _ in selected), sum(a == b'-' for a, _, _ in selected), sum(int(d) for a, d, _ in selected if a != b'-'))
raw = git('diff', '--no-renames', '--raw', '--no-abbrev', '-z', '759ef1c309d93aa7e4ac7217b144c04545562f11', '129bd88f3f04dfb6b80a93b96ec026f6a5cd3010').split(b'\0')
print('old modes:', dict(collections.Counter(raw[i].split()[0][1:].decode() for i in range(0, len(raw)-1, 2) if raw[i+1] in deleted)))
PY
```

Observed output: `Both Signal trees; absent in Molly: 250`, `Molly-present additional path: 6`, `New Signal path under absent root: 85`, `New Signal path under retained root: 321`, `Signal path removed: 111`; 773 paths, 745 text, 28 binary, 45,285 deleted text lines, all old mode `100644`. The four absent roots contain no target paths; retained roots have surviving target paths. The category predicates classify paths, not why they disappeared. Molly path absence does not prove an intentional exclusion; old Signal path removal does not prove behavior preservation; a retained root does not establish an individual file's intent. The six Molly-present paths include tests and possible relocated files, whose full behavior remains unverified. Screen lock, proxy, and shake-report paths merit further caller and policy checks; this inventory does not prove runtime equivalence or deletion necessity. Partial-file exclusions remain unmeasured.

## Current PR3 Settings negative checks


Source commit 6bf19c561f1c123269fdeb5d693bff154684998a, tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010. SettingsValues.kt blob ec0c3b469a2137adede241c82a0e249dc78fb7aa, SHA-256 34d0b24a6318a7a630d562e37fcfca33171da0f8f0d8ab989b0f44aae5824544; LegacySharedPrefs.kt blob 7b339e26b7c3aa43d5310ea79b2418cf5dd7091b, SHA-256 64365414e8310f6b929eedb9f416a1a3329e74733ad570e4a85a91d0677ca48c.

Test input SHA-256: MollySettingsMigrationTest.kt 683ca2adbd9acfd0260a5c8e9dfb2195a5c5ca8724a0f5217ac95e7b52e91e47; MollyBackupPreferencesTest.kt 171017a051794cf25c3dcb7ee93ee6822baf36516ac4c7caf2ba0ef7e13c5f2e; MollyLanguageBackupTest.kt 6afc286c7e3f0e98cd344bdf6ccb3cf965e770c7c504721da05574211adc2e0b. PR3 backup preferences input differs from PR4.

Baseline Gradle exit 0: 10 tests passed, 0 skipped (6 migration, 3 backup preferences, 1 language backup). Command, environment and selectors are recorded in run.sh, matching the baseline; agent-heavy used one Gradle worker, in-process Kotlin, strict dependency verification, JDK 21 and existing build directory. Disk check OK before each candidate. Each candidate was applied independently and restored before the next; all compiled and reached tests.

- 1c87d9e382a99fab53ad: migration copy removal, migration.patch SHA-256 e4747cdeaae8f845eaa9d13bfaa6d0f2db58034b0ef7b553cd9c453d69c00631. Exit 1, 1/10 failed: language backup on upgrade expected de, got null.
- 9c616143ddc96d74a2bb: partial setter removal of only the additional key-value write, preserving the remainder of the larger hunk, setter.patch SHA-256 82ac2920ee8e13f19ecb4636020b295957e592fb701b773f0b5225778b52c24e. Exit 1, 1/10 failed: language backup after change expected fr, got de.
- 66d760f60b510768d21a: encrypted reader replaced by fully qualified androidx.preference.PreferenceManager default reader, a semantic substitution rather than a literal inverse to avoid touching a separate import hunk. reader.patch SHA-256 ee689534b0f88b5557fafddf689cfa3dc0fa5942a08524da189531c4898f0581. Exit 1, 3/10 failed: language backup expected de, got zz; migration language expected de, got en; encrypted preference migration assertion failed.

All runs had 0 skipped. Logs, XML-derived failure summaries and exact candidate diffs are adjacent. These targeted unit checks do not establish end-to-end encrypted backup behavior. No source change retained. After checks, original PR4 commit bf34633283d6cd01c8f6bc615ecf2367bd92d780, tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd restored; status, unstaged diff and staged diff clean.

The candidate patches were applied independently. With JDK 21 and the configured Android SDK, the gate was `agent-heavy -- ./gradlew --no-daemon --no-parallel --no-configuration-cache --max-workers=1 --dependency-verification=strict --init-script <test-worker-heap.init.gradle> -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx5g -Xms256m -XX:MaxMetaspaceSize=1g -Dcom.android.tools.r8.threadCount=1 -XX:+UnlockExperimentalVMOptions -XX:hashCode=3' :app:testProdWebsiteDebugUnitTest --tests org.thoughtcrime.securesms.keyvalue.MollySettingsMigrationTest --tests org.thoughtcrime.securesms.backup.MollyBackupPreferencesTest --tests org.thoughtcrime.securesms.backup.MollyLanguageBackupTest`.

### PR3 migration candidate patch

```diff
diff --git a/app/src/main/java/org/thoughtcrime/securesms/keyvalue/SettingsValues.kt b/app/src/main/java/org/thoughtcrime/securesms/keyvalue/SettingsValues.kt
index ec0c3b469a..1e19d5a1bd 100644
--- a/app/src/main/java/org/thoughtcrime/securesms/keyvalue/SettingsValues.kt
+++ b/app/src/main/java/org/thoughtcrime/securesms/keyvalue/SettingsValues.kt
@@ -590,9 +590,6 @@ class SettingsValues internal constructor(store: KeyValueStore, private val cont
 
     val writer = store.beginWrite()
 
-    if (!store.containsKey(LANGUAGE)) {
-      writer.putString(LANGUAGE, LegacySharedPrefs.getString(context, "pref_language", "zz"))
-    }
     if (!store.containsKey(THEME)) {
       writer.putString(THEME, LegacySharedPrefs.getString(context, "pref_theme", if (DynamicTheme.systemThemeAvailable()) "system" else "light"))
     }
```

### PR3 setter candidate patch

```diff
diff --git a/app/src/main/java/org/thoughtcrime/securesms/keyvalue/SettingsValues.kt b/app/src/main/java/org/thoughtcrime/securesms/keyvalue/SettingsValues.kt
index ec0c3b469a..ff42d698a9 100644
--- a/app/src/main/java/org/thoughtcrime/securesms/keyvalue/SettingsValues.kt
+++ b/app/src/main/java/org/thoughtcrime/securesms/keyvalue/SettingsValues.kt
@@ -251,7 +251,6 @@ class SettingsValues internal constructor(store: KeyValueStore, private val cont
   var language: String
     get() = TextSecurePreferences.getLanguage(context)
     set(value) {
-      putString(LANGUAGE, value)
       TextSecurePreferences.setLanguage(context, value)
       configurationSettingChanged.postValue(LANGUAGE)
     }
```

### PR3 reader candidate patch

```diff
diff --git a/app/src/main/java/org/thoughtcrime/securesms/keyvalue/LegacySharedPrefs.kt b/app/src/main/java/org/thoughtcrime/securesms/keyvalue/LegacySharedPrefs.kt
index 7b339e26b7..b7f56283db 100644
--- a/app/src/main/java/org/thoughtcrime/securesms/keyvalue/LegacySharedPrefs.kt
+++ b/app/src/main/java/org/thoughtcrime/securesms/keyvalue/LegacySharedPrefs.kt
@@ -68,5 +68,5 @@ object LegacySharedPrefs {
     }
   }
 
-  private fun prefs(context: Context) = SecurePreferenceManager.getSecurePreferences(context)
+  private fun prefs(context: Context) = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
 }
```

## Twelve-path Signal equality audit


Read-only source review, 2026-11-01; existing test logs were subsequently reviewed. No new build, runtime test, patch-forest inspection, or minimization gate was performed by this reviewer. Paths below are relative to `app/src/main/java/org/thoughtcrime/securesms/` unless fully qualified. Old Signal S0 = `c47a74ba0961903eeaeda6d22347a990056c3e83`; old Molly M = `0e202dda90785771ff8c87526eaaf30c655f54f3`; new Signal S = `6e9dddc01f1b26e608ac683807c51e139c141ed2`; original source trees O3 = `2533afec2fe2d7f9fc682f699fc21d7624521889`, O4 = `59fa2b2af25d82ffd11d0198d5e9e8f5b5b3eadf`; current source trees C3 = `129bd88f3f04dfb6b80a93b96ec026f6a5cd3010`, C4 = `2b0ba46b23fd27974b8cefbd36e0d91791b525bd`.

Reproduce identity for each exact path: `git ls-tree <S|O3|O4|C3|C4> -- <path>`; compare mode and full blob ID. Reproduce old delta: `git diff --no-ext-diff --unified=2 <S0> <M> -- <path>`. All twelve are mode 100644 and O3/O4 match S. Eleven also match S in C3/C4; the monitor does not (S blob c199e4a9ec4db956600cac9ea126c07fe5ce525f; both current trees bb4aa1d105c53e92f853210ab689e97e94ffd521). For current implementation evidence use `git show <C4>:<path>` and `git diff <S> <C4> -- <path>`. C3 and C4 have identical blobs for all twelve, but that identity does not establish identity of their supporting paths. The source call-site observations below were checked against C4; C3 support paths need independent verification before asserting identical behavior. Molly branch `origin/signal-8.20.4` at `5551f3023e6093fbb65214acdcc2e568b7b63132` retains old Molly blobs for all twelve (historical audit); it does not prove later refactor equivalence.

1. `net/InternetConnectivityMonitor.kt`: Molly added the old 284-line monitor relative to S0. The original Signal-equal outcome is now changed in C3/C4. In C4 `messages/IncomingMessageObserver.kt:115-124,256,280` starts OFFLINE and gates on `isOnline`; `net/ConnectivityStateCalculator.kt:47-51` ranks blocked above unvalidated, and `net/InternetConnectivityMonitor.kt:422` also uses `isOnline` for debounce. The two previously identified policy differences—fail-open observer gating and unvalidated-over-blocked precedence—are fixed in source. Record these changes as a stated reason. Existing focused run `network-positive-retry.log` succeeded; final profile runs `pr3-final-full-retry.log` and `pr4-final-full-new.log` included both test classes and succeeded. C4 `app/src/test/java/org/thoughtcrime/securesms/messages/IncomingMessageObserverConnectivityTest.kt` has two Robolectric tests with mocked sockets/monitor: initial, unavailable and unvalidated states do not connect, ONLINE connects, and loss of validation calls disconnect on both sockets. `app/src/test/java/org/thoughtcrime/securesms/net/ConnectivityStateCalculatorTest.kt` has twelve unit tests, including blocked versus unvalidated in both orders and modeled VPN/underlying-network states. Existing `network-negative-static.log` records three expected assertion failures when the changes were reversed: both observer tests and blocked precedence. These do not measure actual device VPN routing, real monitor failure/recovery or real websocket reconnection. Do not claim all twelve remain Signal-equal or full network equivalence.
2. `reactions/ReactionsBottomSheetDialogFragment.java`: old Molly replaced fixed surface navigation bar color with `colorSurfaceContainerLow`; C4 removed the explicit override. No source proof establishes equivalent navigation bar appearance. Unresolved: runnable light/dark, gesture/three-button, pre-35/35+, collapsed/expanded checks; a stated reason may describe the removal, not claim preservation.
3. `feature/camera/src/main/java/org/signal/camera/hud/StandardCameraHud.kt`: old Molly changed the badge from #2C6BED to #7152FF; the old badge is absent from C4 HUD. `feature/media-send/src/main/java/org/signal/mediasend/screens/capture/MediaCaptureScreen.kt:158-168` supplies the next button/count; fixed purple is not established. Unresolved whether fixed color is required; exercise multi-capture/count/next and lifecycle. A stated reason can record the changed UI only.
4. `components/settings/app/account/AccountSettingsFragment.kt`: old Molly removed PIN confirmation for disabling reminders and restricted linked-device PIN, change-number and transfer controls, except transfer while unregistered. C4 `components/settings/app/account/AccountSettingsViewModel.kt:167-170` directly sets the toggle; `components/settings/app/AppSettingsActivity.kt:85` routes linked devices to separate settings. Record these facts, but linked-device restrictions across direct links and the unregistered exception need a runnable check; do not claim full preservation.
5. `components/settings/app/appearance/appicon/AppIconSelectionFragment.kt`: old Molly rendered adaptive drawable previews via bitmap; C4 `feature/app-settings/src/main/java/org/signal/appsettings/appicon/AppIconSelectionScreen.kt:215` converts `preset.loadIcon(context)` to bitmap, and `AppIconPreset.kt:34-37` loads disabled aliases. Record source-level move; run alias/fallback preview checks before asserting runtime equivalence.
6. `main/MainNavigation.kt`: old Molly set theme attributes on bar/rail and selected bar text. C4 `main/MainNavigationBar.kt:73,89-90,180,204` applies corresponding attributes and selected text color. Suitable stated source reason; light/dark bar/rail rendering remains untested.
7. `components/SharedContactView.java`: old Molly changed the action label to the generic message string. C4 `components/SharedContactView.java:317` uses `SharedContactView_message_button`; `app/src/main/res/values/strings.xml:2613` says Message. Suitable stated reason for English only; locale wording needs translation comparison and, if required, UI check.
8. `components/settings/app/subscription/receipts/detail/DonationReceiptDetailFragment.kt`: old Molly removed the Signal logo splash. C4 delegates to `components/settings/app/subscription/receipts/detail/DonationReceiptDetailScreen.kt:57`, whose receipt content has no logo. Suitable stated source reason; receipt rendering was not run.
9. `giph/mp4/GiphyMp4Repository.java`: old Molly used the app OkHttp client builder and `ContentProxySelector`; C4 `giph/mp4/GiphyMp4Repository.java:32` retains that construction. Suitable stated construction reason, not an end-to-end routing guarantee; proxy/DNS routing needs runnable verification if required.
10. `mediasend/v2/review/AddMessageDialogFragment.kt`: old Molly removed the forced dark inflater. C4 also has no such override and derives from `components/KeyboardEntryDialogFragment.kt`. Suitable stated reason for absence of forced dark inflater; resolved appearance/contrast needs light/dark runtime verification.
11. `util/DynamicLanguage.java`: old Molly read language from `TextSecurePreferences`; C4 `util/DynamicLanguage.java:49` reads `PlainTextKeyValueStore.getLanguage`, rather than the same call. Prior source research identifies the shared preference-backed pathway, but startup/restore/migration timing requires a runnable check; do not assert complete equivalence.
12. `util/PowerManagerCompat.java`: old Molly extracted the ignore-battery-optimization intent builder and used its result in the existing method. C4 `util/PowerManagerCompat.java:23,27` retains that exact helper (indeed S and M have the same blob). Suitable stated reason; prompt/result behavior on device was not tested.

## Libsignal independent reversion receipts

Historical PR3 input `d756c962296d51e6d910d9821a8df44b0cbd0578`, tree `3a20fd39d1f0e444bcc4711a2909635d8762b67e`; PR4 input `1923f7f681b790fdf53006fa943b61de473a5ef5`, tree `f25a1de0fc87f0f862cc270f823d240857878bc5`. Mechanical tree `759ef1c309d93aa7e4ac7217b144c04545562f11`. Historical baseline module compiles passed for both inputs. Each forward patch was independently reverse applied, gated and restored; each inverse gate exited 1 on both inputs. Gate not rerun on current target.

For reproduction, set `JAVA_HOME`, `ANDROID_HOME`, `TMPDIR` and `PATH` for the local toolchain, and `REPORT_WORKTREE` to this repository checkout. From the historical source checkout, for each ID use:

```sh
git apply --reverse "$REPORT_WORKTREE/patches/ports/signal-8.33.2/report-evidence/hunk-$ID.patch"
agent-heavy -- ./gradlew --offline --no-daemon --no-parallel --no-configuration-cache --max-workers=1 --dependency-verification=strict --init-script "$REPORT_WORKTREE/tools/patches/test-worker-heap.init.gradle" -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx5g -Xms256m -XX:MaxMetaspaceSize=1g -Dcom.android.tools.r8.threadCount=1 -XX:+UnlockExperimentalVMOptions -XX:hashCode=3' :lib:libsignal-service:compileJava
git apply "$REPORT_WORKTREE/patches/ports/signal-8.33.2/report-evidence/hunk-$ID.patch"
```

PR3 and PR4 forward patches were byte-identical per ID. SHA-256: `92b4aeb23e3a9479eae3` = `451ad530e099fa080e0f30953f54afd32f901474a4240b841d2791776b12682e`; `889e6d1e15e5bdc65692` = `8e31ef9f0faff09bbbbf65e03798c60f772624a4fda59ea2feedcb29a5420b65`; `a194a470661695b89387` = `85a3c601342474786ee7b724715ff4e5c2c84560761588c95461857d662f7cc1`; `b735539990cfa3e6bdb8` = `9d2403f2d23273b78b20b3704ae912797b30ab0e99796068a70c92e0d24f5941`.

Current source commits PR3 `1500ad2a9c9ac9500a22ba77893171730a7f88f8` (tree `c50fa18dab3c5420e93719dae3040b7a4058795f`) and PR4 `cac99e421c14af3fae0251b64a922e235e3708c9` (tree `2a5ccdccacb3e7b77e7f4719dff4db75ff28b2b9`) have the same respective full blobs as both historical inputs for the first three paths: `932e209b0781e43dd8c3088abce07e790df90503`, `6803653710d662c148df6d0b87fff5dad913cd57`, `53d38ec128d36a40e447d92f01138a0a9e3418ce`. PushServiceSocket historical blob `be4becdd684b0afa671949baf203f1b2482eb6cd` differs from both current blobs `224f7cf88878d39b1cc62ad9528c6070ce16bfed` only by the accepted JsonProperty import restoration; the tested hunk body and context are unchanged. This transfers hunk identity, not the historical gate to the current trees.

The registration inverse reported missing DeviceAttributes and RegisterAsSecondaryDeviceResponse; both imports together are required by the existing implementation, but each was not tested alone. The other three inverses restored literal conflict markers and failed parsing (Java, Kotlin, Java respectively). These failures do not measure import policy, semantic choices or Svr3Credentials removal; app and full regression gates were not measured by these checks.

## Import restorations

Eight per-file partial restorations add 12 imports originally present in both the mechanical merge and Molly. Each action covers only the listed imports, not a complete compatibility hunk; filenames identify one proven restoration diff hunk, and the IDs below list all restoration diff hunks included in each patch. No one-hunk-at-a-time gate is claimed. No body occurrence of the 12 names or wildcard import was found in these files; this is a source-resolution check, not a compiler-plugin proof.

PR3 baseline 1500ad2a9c9ac9500a22ba77893171730a7f88f8 compiled successfully. Each PR3 case below and the PR3 combined tree 142dc8e37ad49cc97cac1fc99f6759bdecd5e11a passed the actual app compile. Kotlin executed for all cases; Java executed except for the DataMessageProcessor case, where Java was UP-TO-DATE. PR4 combined tree 4689da748e7ffb4b52d5d8fcbd9a1ebaf905f911 passed with Kotlin and Java executed; PR4 individual cases and PR4 baseline were not run. Gradle cache and prior outputs were retained, and no full build is claimed.

Gate command (each candidate checked out in the shared source worktree): `env JAVA_HOME=/var/home/x/.local/opt/jdk-21.0.13+9 ANDROID_HOME=/home/x/Android/Sdk agent-heavy -- ./gradlew --offline --no-daemon --no-parallel --no-configuration-cache --max-workers=1 --dependency-verification=strict --init-script /var/home/x/agent-scratch/mollyim-android/pr-stack/worktree/tools/patches/test-worker-heap.init.gradle -Pkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs="-Xmx5g -Xms256m -XX:MaxMetaspaceSize=1g -Dcom.android.tools.r8.threadCount=1 -XX:+UnlockExperimentalVMOptions -XX:hashCode=3" :app:compileProdWebsiteDebugKotlin :app:compileProdWebsiteDebugJavaWithJavac`.

All nine source checkpoints were independently checked by applying the candidate patch and by overlaying guarded import insertions in separate Git indexes; corresponding trees matched, with exactly 12 insertions in eight files and no deletions or mode changes. The later ConversationItem code changes remain present.

- `app/src/main/java/org/thoughtcrime/securesms/conversation/ConversationItem.java`: candidate `7f47e8d352d1212787fb5d196291de9947876be3` tree `345fdf47a388decc36e5d70ab9017e2107d61dea`; restoration IDs 67832bd595fbc116d5c9, a62b3651d3829be23031; BUILD SUCCESSFUL in 3m 16s, Kotlin EXECUTED, Java EXECUTED. Former current IDs 14b41a52a784480ff935, c73d116d4324245b6141; replacement current IDs none. None of the former IDs had hunk records or group assignments.

- `app/src/main/java/org/thoughtcrime/securesms/dependencies/CoreUiDependenciesProvider.kt`: candidate `3d28fe1b04957a65d4db42d40082261e2bba09fa` tree `479b63ad7db5fdbd1a802ce3cc3952c540abba7e`; restoration IDs a315df295c7834ff887e; BUILD SUCCESSFUL in 3m 13s, Kotlin EXECUTED, Java EXECUTED. Former current IDs 57c3f630741a4d6c46ac; replacement current IDs 0775a4537273b497f149. None of the former IDs had hunk records or group assignments.

- `app/src/main/java/org/thoughtcrime/securesms/glide/ChunkedImageUrlLoader.java`: candidate `9160c745bfba68633e46208ab7a9211ed34fc5b5` tree `f050073e299516207985ad437d21e062defc7c80`; restoration IDs b77e19bc0951cc903901; BUILD SUCCESSFUL in 3m 19s, Kotlin EXECUTED, Java EXECUTED. Former current IDs 8cfab22505ce579e1df9; replacement current IDs 02b95fd448af0bfb9d96. None of the former IDs had hunk records or group assignments.

- `app/src/main/java/org/thoughtcrime/securesms/keyvalue/AccountValues.kt`: candidate `22d000a03d8fab4c804a01d8f4d713e934d19f93` tree `ff4e297e15a2230079a9df0a9b49aa24146b58d2`; restoration IDs 89d2c0a1a74024cddd3d; BUILD SUCCESSFUL in 3m 28s, Kotlin EXECUTED, Java EXECUTED. Former current IDs 8f786732aadabe171ee5; replacement current IDs c1db604132e2c77751f7. None of the former IDs had hunk records or group assignments.

- `app/src/main/java/org/thoughtcrime/securesms/maps/PlacePickerActivity.java`: candidate `e29b197f1b418e8b73951e2bee232fe55196877d` tree `99fdc3cad67c8ab1bfd3e102b78b8bed123ef61e`; restoration IDs cc36c3786d0379aca969; BUILD SUCCESSFUL in 3m 17s, Kotlin EXECUTED, Java EXECUTED. Former current IDs 5600ed33e766ce909d67; replacement current IDs none. None of the former IDs had hunk records or group assignments.

- `app/src/main/java/org/thoughtcrime/securesms/mediasend/v3/MediaSendV3ForwardFragment.kt`: candidate `1bbb86678c854f8a454301041220e237bb62c9a4` tree `5ac193c2e22ac2b7a699ab5032b5497a3ac16650`; restoration IDs a10bbcea5de460447caf; BUILD SUCCESSFUL in 3m 13s, Kotlin EXECUTED, Java EXECUTED. Former current IDs ef533cae4a2bd288ce5c; replacement current IDs 143a0a1e561c975ed9f3. None of the former IDs had hunk records or group assignments.

- `app/src/main/java/org/thoughtcrime/securesms/messages/DataMessageProcessor.kt`: candidate `bc2f5a0884d48d4659068e0ce62e6e47a4c6cbe1` tree `2b487ba18c343ffba2e2135404d19326fb8b3d40`; restoration IDs 84bc91c45c0636a0562d, 757521f616948c304df1, 5cb94163ea5bb41fae5f; BUILD SUCCESSFUL in 3m 15s, Kotlin EXECUTED, Java UP-TO-DATE. Former current IDs 358e7ee8816a29ef8454, 533c3696666d688608c2, 7cfde89b8cc04aa53bb9; replacement current IDs none. None of the former IDs had hunk records or group assignments.

- `app/src/main/java/org/thoughtcrime/securesms/mms/AttachmentManager.java`: candidate `639524c00b6bc1dfdc7b315105ce44703344e602` tree `02f4ca8cca4fe424b8f605e2f2944e760a6d7e26`; restoration IDs f9f9edb32f3882f72c3f; BUILD SUCCESSFUL in 3m 20s, Kotlin EXECUTED, Java EXECUTED. Former current IDs f30277315359fadada35; replacement current IDs 2e9a4d6aa4c122488df0. None of the former IDs had hunk records or group assignments.

## Backup policy checks

The following checks used cached build outputs and offline Gradle, not a clean or full build. The command below uses `$REPORT_WORKTREE` for this checkout and `$SOURCE` for a checkout of each named candidate; set `JAVA_HOME` to JDK 21 and `ANDROID_HOME` to the Android SDK. Run in `$SOURCE`:

```sh
PATH="$JAVA_HOME/bin:$PATH" agent-heavy -- ./gradlew --offline --no-daemon --no-parallel --no-configuration-cache --max-workers=1 --dependency-verification=strict --init-script "$REPORT_WORKTREE/tools/patches/test-worker-heap.init.gradle" -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx5g -Xms256m -XX:MaxMetaspaceSize=1g -Dcom.android.tools.r8.threadCount=1 -XX:+UnlockExperimentalVMOptions -XX:hashCode=3' :app:testProdWebsiteDebugUnitTest --tests "$SELECTOR"
```

PR3 original baseline `1500ad2a9c9ac9500a22ba77893171730a7f88f8` tree `c50fa18dab3c5420e93719dae3040b7a4058795f` passed FullBackupExporterTest and MollyBackupPreferencesTest. Signal-threshold supplement `b48bf7098597fa0e9756a4037c8895fba73069de` tree `7be61956c1d4a535ce05098b5408424a5ef7d979` passed all 11 exporter tests: the 24-hour fixture did not distinguish the predicates. Long/timeout-only inverse `0766a50e4cf379090fbefbe5ad42a7ef35cb6d18` tree `da7138800cc93c2c96916d5ed964dc814b302c29` failed 2 of 3 MollyBackupPreferencesTest tests: long value expected 2147483767 but was 0, and Integer cannot be cast to Long. Baseline importer blob `2c0a6cab56e8e19f890439e596ef47c832ee83b4` and preference test blob `aba1a441a587da06fff2dc76547cd6c729115248` match packaged PR3 before the fixture change; these are historical runs, not runs at the new packaged head. The partial inverse patch is `hunk-18d50f852eca12a4e423.patch`; it does not reverse the entire report hunk.

The selected seven-day test is `org.thoughtcrime.securesms.backup.FullBackupExporterTest.started disappearing messages are excluded even with substantial time remaining`. Baseline `da3131fde031e09017e778c736f7d34f8b5a7600` tree `d4b5d14b580bc2faa8af989c48d34e77cada3ca4` passed one test; Signal-threshold supplement `99014fba9d5df76f07d76ac74257c991be7f159a` tree `a23dd5ea87c7eabfdec564ef543a723aa1a57eaf` failed that test's assertion. The supplement changes the terminal predicate while leaving the conflict marker removed (`hunk-1b2207dcd3236258f280.patch`); it is not a literal full-hunk inverse. The one-line fixture change is `hunk-e987574c43c572243a5d.patch`. Initial checkpoint owns this test. Each of the nine new checkpoints independently differed from its previous graph counterpart by exactly this path, one insertion and one deletion. PR3 final graph tree equals the tested baseline tree; this changes no production code. Molly `0e202dda90785771ff8c87526eaaf30c655f54f3` exporter lines 593–596 uses the same terminal predicate as the retained policy. The local tip search covered only the named branches listed in the report, not all Molly history.

## Executed translation task witness

At source PR4 commit `cf3146bb3fcc29eb3059528df52a5f304a6b379a` (tree `96ce7bb4a50dcf1da4952fe07ff59e48ca5fd985`), the pinned Signal `6e9dddc01f1b26e608ac683807c51e139c141ed2` supplied 816 localized `strings.xml` inputs. The 816 source blobs and modes, all default `values/strings.xml` files, and `build-logic/plugins/src/main/java/mollyify.gradle.kts` match PR3 source `842e226be91f6b9d664df3b92ef460d07e6bde14`. The transformation script is byte-identical to old Molly `0e202dda90785771ff8c87526eaaf30c655f54f3`.

The executed command was `env JAVA_HOME=/var/home/x/.local/opt/jdk-21.0.13+9 ANDROID_HOME=/home/x/Android/Sdk PATH=/var/home/x/.local/opt/jdk-21.0.13+9/bin:$PATH agent-heavy -- ./gradlew --offline --no-daemon --no-parallel --no-configuration-cache --console=plain --info --max-workers=1 --dependency-verification=strict -Pkotlin.compiler.execution.strategy=in-process "-Dorg.gradle.jvmargs=-Xmx5g -Xms256m -XX:MaxMetaspaceSize=1g -Dcom.android.tools.r8.threadCount=1 -XX:+UnlockExperimentalVMOptions -XX:hashCode=3" updateTranslationsAll`. It exited 0. All thirteen module tasks executed: `:app`, `:core:ui`, `:core:util`, `:feature:app-settings`, `:feature:chat-settings`, `:feature:media-keyboard`, `:feature:media-send`, `:feature:media-viewer`, `:feature:registration`, `:lib:device-transfer`, `:lib:emoji`, `:lib:password-manager`, and `:lib:signal-login` (each `:updateTranslations`); the root aggregate had no actions. The exact task and output inventory is in `translation-task-witness.tsv`.

Comparing task output with the baseline across all 816 files found identical parsed string/plurals values, attributes, child markup and tails, except 68 baseline-only app `please_wait` entries. All 68 baseline elements match old Molly at the XML element level; the task does not create them. There were no other mismatches or parse errors. Comments and root indentation are outside this comparison. Only 552/816 task-output and baseline files were byte-identical; the other 264 differ in bytes, so this does not establish formatting provenance or whole-file identity. The selected Signal, task-output and baseline files each had 204 SPDX files and zero literal `&#160;` or `&#8212;` occurrences; this limited selection does not supersede broader XML reviews. The actual execution strengthens the earlier model-based receipts; individual inverse outcomes and their limits remain as recorded.

## Residual partial inverses

Each result below measured a partial resolved-reference inverse mapped to a raw report hunk by target interval, not a complete raw-hunk inversion. The PR3 and PR4 target file blobs match, but their source trees and gates were independently tested. No reduction was accepted. The forward patches restore the corresponding complete parent tree; private commit and tree identifiers and forward patch SHA-256 values follow.

PR3 parent `842e226be91f6b9d664df3b92ef460d07e6bde14`, tree `d4b5d14b580bc2faa8af989c48d34e77cada3ca4`; PR4 parent `cf3146bb3fcc29eb3059528df52a5f304a6b379a`, tree `96ce7bb4a50dcf1da4952fe07ff59e48ca5fd985`. Each private counter is a child of its respective parent. The resolved-reference baseline is heuristic and is not a correctness claim.

| Branch | Residual ID | Raw ID | Experimental commit | Tree | Forward patch SHA256 |
|---|---|---|---|---|---|
| pr3 | `05f8c29fbf4bfc24fed5` | `8dd2f3b524d96720db2f` | `9237bbb2cf44994620849fb54d875f29dd975331` | `448d49e1b1582322b3e5477d7333c42971b5f981` | `a323fb70883dc5f728af6f622157acb7d856b07a50e20ff49c6a1252bdbc7dd3` |
| pr3 | `7660ef164061ffc93a96` | `7660ef164061ffc93a96` | `5557b3a5bec0f00eb75ca79b970f0746d9a5e56a` | `0fd53da31078a32e35d5241a5b8aba54a3fdb1d4` | `2f84057f649ed1284133d299f948a547186a45e2fcaef4486bcec65602c8d213` |
| pr3 | `7a1001390e195c747030` | `7a1001390e195c747030` | `7bd5b4e9966c6e0d956181e34474ec9b1830b620` | `dc829d9ddc866575f0aad2ccc62728158d5db286` | `6dab280032e28784260de2725a81314c9823699da929e41ccb894d89a576b684` |
| pr3 | `c10dc6afae3768148078` | `61a49a1d5fe0b4bc6f4b` | `d7dc70d0de983d91086604c2cf6238d852dc0976` | `e3ca80b640a515e9c2c6bf45ab25c73ccb37f1cd` | `b3f2937164bb97851e6f3107c19c7516e059fa0821cd143b9b44666caef43084` |
| pr3 | `d8d7bb6195b2ada6285c` | `d8d7bb6195b2ada6285c` | `6ef91f752a756dc9e3589361c9c877bd49d4146d` | `dfa8aa590dce63661d00a83accab6153d9831282` | `c1fcc4b8de6d83ba7de58f52a37f9d42c0dbfc145b8d7ba2b4a1a9f967fb694d` |
| pr3 | `e7331e58449f2fac31d3` | `e7331e58449f2fac31d3` | `e5df7cd85fff499bf9f1e40ffcf4107d3899c6e1` | `e5663a2112deca6fb05647b689f7aae23eaba357` | `a5123c3899cfcec9485f306b05bb2357b5b6ac200a96a3478ef6a627d562dea1` |
| pr4 | `05f8c29fbf4bfc24fed5` | `8dd2f3b524d96720db2f` | `89e83f9d58ff1c0da1ca9a81f5370210542278f7` | `66dd3b16446dcc3e6291400ec9a720aee8e9c7e4` | `a323fb70883dc5f728af6f622157acb7d856b07a50e20ff49c6a1252bdbc7dd3` |
| pr4 | `7660ef164061ffc93a96` | `7660ef164061ffc93a96` | `9be4648688c440eafd911efa6d49d20898f0a089` | `e121ef8d2ead8d1a1fcbad51f7372427893bc365` | `2f84057f649ed1284133d299f948a547186a45e2fcaef4486bcec65602c8d213` |
| pr4 | `7a1001390e195c747030` | `7a1001390e195c747030` | `35a672b6ec7524835f0b49268ec8626072e9039c` | `15e6965f89b1ebe1ce3dc724d44d5444f39f839b` | `6dab280032e28784260de2725a81314c9823699da929e41ccb894d89a576b684` |
| pr4 | `c10dc6afae3768148078` | `61a49a1d5fe0b4bc6f4b` | `96b1544741c9c235194a07f0058fabbbc13d0423` | `c7238d161c4d5f5cc543ff5a0a3694bf7e0327f0` | `b3f2937164bb97851e6f3107c19c7516e059fa0821cd143b9b44666caef43084` |
| pr4 | `d8d7bb6195b2ada6285c` | `d8d7bb6195b2ada6285c` | `060832cbb2d74e1eb519cb4607d984578f28a0fd` | `985a483e08f437c399313d2c8730c88c31a284a1` | `c1fcc4b8de6d83ba7de58f52a37f9d42c0dbfc145b8d7ba2b4a1a9f967fb694d` |
| pr4 | `e7331e58449f2fac31d3` | `e7331e58449f2fac31d3` | `95dc93fbf54e085f0d650ff278d72b91d675067c` | `70eafeae625dda64fb8577b5779cf7599361d6b5` | `a5123c3899cfcec9485f306b05bb2357b5b6ac200a96a3478ef6a627d562dea1` |


On each branch the screen baseline passed and its inverse failed four assertions out of ten; the view-model baseline passed 11 tests, the badge baseline passed three, the proxy baseline passed both selected classes (PR4: 13 and two tests), and registration and Material baseline compiles passed. Each of those five inverse gates failed compilation in the changed file: view-model action handling, `autoUpdate` and Long/Duration; registration `makeGooglePlayServicesAvailable`; badge references; proxy `fromProxy`; Material constructor candidates. These failures establish consistency with surrounding code, not necessity of every line. Registration behavior and visual equivalence remain unmeasured. Whole update, badge and proxy inverses lose Molly behavior or policy and are not eligible reductions. PR4 Material baseline reused the successful PR4 view-model baseline app compile.

Commands below ran offline in the corresponding source checkout with JDK 21 and the existing SDK and build outputs. The first PR3 screen baseline was UP-TO-DATE; the separately executed PR3 baseline and the PR4 baseline both used `--rerun` and passed. The named test tasks used `--rerun`; baseline and counter commands otherwise match. PR4 Material baseline used the view-model command.

- screen-baseline-executed: `agent-heavy -- ./gradlew --offline --no-daemon --no-parallel --no-configuration-cache --max-workers=1 --dependency-verification=strict --init-script /var/home/x/agent-scratch/mollyim-android/pr-stack/worktree/tools/patches/test-worker-heap.init.gradle -Pkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs=-Xmx5g\ -Xms256m\ -XX:MaxMetaspaceSize=1g\ -Dcom.android.tools.r8.threadCount=1\ -XX:+UnlockExperimentalVMOptions\ -XX:hashCode=3 :feature:app-settings:testDebugUnitTest --rerun --tests org.signal.appsettings.updates.AppUpdatesSettingsScreenTest`
- viewmodel-counter: `agent-heavy -- ./gradlew --offline --no-daemon --no-parallel --no-configuration-cache --max-workers=1 --dependency-verification=strict --init-script /var/home/x/agent-scratch/mollyim-android/pr-stack/worktree/tools/patches/test-worker-heap.init.gradle -Pkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs=-Xmx5g\ -Xms256m\ -XX:MaxMetaspaceSize=1g\ -Dcom.android.tools.r8.threadCount=1\ -XX:+UnlockExperimentalVMOptions\ -XX:hashCode=3 :app:testProdWebsiteDebugUnitTest --rerun --tests org.thoughtcrime.securesms.components.settings.app.updates.AppUpdatesSettingsViewModelTest`
- registration-counter: `agent-heavy -- ./gradlew --offline --no-daemon --no-parallel --no-configuration-cache --max-workers=1 --dependency-verification=strict --init-script /var/home/x/agent-scratch/mollyim-android/pr-stack/worktree/tools/patches/test-worker-heap.init.gradle -Pkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs=-Xmx5g\ -Xms256m\ -XX:MaxMetaspaceSize=1g\ -Dcom.android.tools.r8.threadCount=1\ -XX:+UnlockExperimentalVMOptions\ -XX:hashCode=3 :feature:registration:compileDebugKotlin`
- badge-counter: `agent-heavy -- ./gradlew --offline --no-daemon --no-parallel --no-configuration-cache --max-workers=1 --dependency-verification=strict --init-script /var/home/x/agent-scratch/mollyim-android/pr-stack/worktree/tools/patches/test-worker-heap.init.gradle -Pkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs=-Xmx5g\ -Xms256m\ -XX:MaxMetaspaceSize=1g\ -Dcom.android.tools.r8.threadCount=1\ -XX:+UnlockExperimentalVMOptions\ -XX:hashCode=3 :app:testProdWebsiteDebugUnitTest --rerun --tests org.thoughtcrime.securesms.components.settings.conversation.shared.ConversationHeaderBadgeTest`
- proxy-counter: `agent-heavy -- ./gradlew --offline --no-daemon --no-parallel --no-configuration-cache --max-workers=1 --dependency-verification=strict --init-script /var/home/x/agent-scratch/mollyim-android/pr-stack/worktree/tools/patches/test-worker-heap.init.gradle -Pkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs=-Xmx5g\ -Xms256m\ -XX:MaxMetaspaceSize=1g\ -Dcom.android.tools.r8.threadCount=1\ -XX:+UnlockExperimentalVMOptions\ -XX:hashCode=3 :core:network:test --rerun --tests org.signal.network.config.ProxyConfigTest --tests org.signal.network.config.LibSignalProxyPolicyTest`
- material-counter: `agent-heavy -- ./gradlew --offline --no-daemon --no-parallel --no-configuration-cache --max-workers=1 --dependency-verification=strict --init-script /var/home/x/agent-scratch/mollyim-android/pr-stack/worktree/tools/patches/test-worker-heap.init.gradle -Pkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs=-Xmx5g\ -Xms256m\ -XX:MaxMetaspaceSize=1g\ -Dcom.android.tools.r8.threadCount=1\ -XX:+UnlockExperimentalVMOptions\ -XX:hashCode=3 :app:compileProdWebsiteDebugKotlin`

Full preflight passed at the previous outer heads PR3 `23ff849b799fd09f25751a7ddebbf83f95ed39cc` and PR4 `c964a912eb92f8de0f5c0b785479e49583c79043`; those runs compiled the unchanged source trees above. The report-only outer heads require their applicable preflight before push. CI has not run on them.

### Proxy configuration policy controls

A separate focused counter changes `Proxy.Type.SOCKS` conversion from source-aware `SOCKS_REMOTE_DNS` for users to `SOCKS` for all sources. PR3 counter commit `4222415a7278dc73f2e57141e5e230d97b51735f`, tree `32e88a931a5723f09be149a79a308350b15334ac`, has the PR3 parent above; PR4 counter commit `3da3c017797744941c0a44b3e2cd28a8a4dbcf89`, tree `b3f4dc4b794f811d34444e43bc4264936f0d6c6c`, has the PR4 parent above. Each baseline passed 13 tests; each counter executed 13 tests and failed exactly two assertions, with no compile failures, errors or skips. Both failures expected `SOCKS_REMOTE_DNS` and received `SOCKS`: `resolve preserves a user proxy when censorship circumvention is enabled` and `resolve prefers a user SOCKS proxy with remote DNS over the system proxy`. This checks configuration policy, not actual DNS traffic or necessity of the entire raw hunk. No reduction was accepted.

The exact baseline and counter command on each branch, in its source checkout, was:

```sh
JAVA_HOME=/var/home/x/.local/opt/jdk-21.0.13+9 ANDROID_HOME=/home/x/Android/Sdk TMPDIR=/var/home/x/agent-scratch/mollyim-android/pr-stack/tmp PATH=$JAVA_HOME/bin:$PATH
agent-heavy -- ./gradlew --offline --no-daemon --no-parallel --no-configuration-cache --max-workers=1 --dependency-verification=strict --init-script /var/home/x/agent-scratch/mollyim-android/pr-stack/worktree/tools/patches/test-worker-heap.init.gradle -Pkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs=-Xmx5g\ -Xms256m\ -XX:MaxMetaspaceSize=1g\ -Dcom.android.tools.r8.threadCount=1\ -XX:+UnlockExperimentalVMOptions\ -XX:hashCode=3 :core:network:test --rerun --tests org.signal.network.config.ProxyConfigTest
```

## Resource policy partial inverses

These are partial checks, not complete raw-hunk inverses; no reduction was accepted. The raw report IDs map by target interval: `2e0a56c1a6f9ca207ecb` removes only the network action row in `app/src/main/res/navigation/app_settings_with_change_number.xml`; `3a3380306f801f12ff95` removes only five items in `app/src/main/res/values/themes.xml`. Current respective blob IDs are `8e662237d29975cb374283d3752d1d3544f16294` and `a1619af7ea78f12ee2a6d2868973f07b8f681a45`, matching PR3 and PR4 source parents and prior prepared PR3 parent `1500ad2a9c9ac9500a22ba77893171730a7f88f8`. Old Molly `0e202dda90785771ff8c87526eaaf30c655f54f3` navigation lines 95–102 defines the network destination and four fragment open/close animations; themes lines 227–231 defines `dynamic_colors=false`, navigation and status bar colors as `molly_surface_light`, and both light system bar flags as true. Its `core/ui/src/main/res/values/molly_colors.xml` line 63 defines `#F6F6FF`. Removing the network action removes old Molly policy and is ineligible as a reduction.

PR3 source parent `842e226be91f6b9d664df3b92ef460d07e6bde14`, tree `d4b5d14b580bc2faa8af989c48d34e77cada3ca4`; PR4 source parent `cf3146bb3fcc29eb3059528df52a5f304a6b379a`, tree `96ce7bb4a50dcf1da4952fe07ff59e48ca5fd985`. Corrected fixture baselines and inverse commits are private experimental commits, with trees shown below:

| Branch and case | Commit | Tree | Result |
|---|---|---|---|
| PR3 baseline | `643d3067ef4ed3e80f4ad6745b1ef005446faf11` | `3778143bebe74501a67ba78458c15ced04ef99a2` | Four tests executed and passed |
| PR3 network | `5aa8a7cc902a5231e2c49c5cf3367806e01862b1` | `575ca1912ae06ca37b6603d44bc7cf2b5f498133` | App Kotlin compile failed; no test ran |
| PR3 theme | `6dae1ea777708a626edfeae9d84f50da44229954` | `baeea45be4a076f9eb88120f77bb28c8e4aca2a4` | Four tests executed; two theme failures, two network passes |
| PR4 baseline | `b7efc8fa9e997e31e29b4b1c727105f443b5d691` | `535c1e1569a1f652f78ae97a660bf7b0feaffc95` | Four tests executed and passed |
| PR4 network | `516f9f707e8fb020b8142757685914e5105a0cbc` | `bf5050960161a35435dc0a19391bd2aa8727ad8e` | App Kotlin compile failed; no test ran |
| PR4 theme | `38da02825905ffa1596d2d065d1b2546491c9ecb` | `f7400722a20a3791924ba8db65ee134d3c07c4ea` | Four tests executed; two theme failures, two network passes |

The two methods were `settingsNetworkActionMatchesMolly` and `baseLightResolvesMollyValues`, each on API 28 and 31. XML names append `[28]` for API 28; unlabelled names correspond to API 31. Both network inverses failed at `AppSettingsFragment.kt:110` on the unresolved action ID; this establishes a dependency in surrounding code, not runtime navigation behavior or global minimality. Both theme inverses failed at the first `dynamic_colors` resolution, expected true but was false (fixture line 28 called from line 32). The following four attribute resolutions, rendering, dynamic themes and other APIs remain unmeasured. The initial fixture setup commit `65c785cb648a9da9a5b7a85c69d1d3cca4db8b9f`, tree `2ff726b4b56bf183cb9c677cb15678b65cd8f5be`, failed fixture compilation because `dynamic_colors` belongs to core UI's R namespace; no tests ran. A test-only namespace correction produced the passing baselines above.

All six cases and the failed initial setup used this exact command in the corresponding source checkout, using cached offline dependencies:

```sh
export JAVA_HOME=/var/home/x/.local/opt/jdk-21.0.13+9 ANDROID_HOME=/home/x/Android/Sdk TMPDIR=/var/home/x/agent-scratch/mollyim-android/pr-stack/tmp
export PATH="$JAVA_HOME/bin:$PATH"
agent-heavy -- ./gradlew --offline --no-daemon --no-parallel --no-configuration-cache --max-workers=1 --dependency-verification=strict --init-script /var/home/x/agent-scratch/mollyim-android/pr-stack/worktree/tools/patches/test-worker-heap.init.gradle -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx5g -Xms256m -XX:MaxMetaspaceSize=1g -Dcom.android.tools.r8.threadCount=1 -XX:+UnlockExperimentalVMOptions -XX:hashCode=3' :app:testProdWebsiteDebugUnitTest --rerun --tests org.thoughtcrime.securesms.ResourcePolicyProbeTest
```

## RadioListRow partial inverses

Six independent core UI compiles executed against exact PR3 and PR4 parents. Prior full preflights executed this compile on identical source trees; no new baseline was run. Raw mechanical IDs map to resolved-reference partial inverses below. Each patch applies to both targets and yields its measured counter tree; patch bytes match between branches. The six checks used `agent-heavy -- ./gradlew --offline --no-daemon --no-parallel --no-configuration-cache --max-workers=1 --dependency-verification=strict --init-script /var/home/x/agent-scratch/mollyim-android/pr-stack/worktree/tools/patches/test-worker-heap.init.gradle -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx5g -Xms256m -XX:MaxMetaspaceSize=1g -Dcom.android.tools.r8.threadCount=1 -XX:+UnlockExperimentalVMOptions -XX:hashCode=3' :core:ui:compileDebugKotlin --rerun` with JDK 21 and cached SDK. Compiles establish binding with current surrounding code, not full semantic necessity or UI behavior. Old Molly uses the warning icon in NotificationsSettingsFragment; removing forwarding is ineligible.

- `28e7c9ec6e96541eb7bd` / resolved `df5cfd6244c149d95e13`: SHA-256 `5bafe7acf8011900cc03902a91c038e11b3c7ea7727cc3bf856b71488d8d475b`; both FAIL: unresolved trailingIcon and overload errors.
- `6445214c064774fbc5bf` / resolved `ce80f3dd5c60088c5815`: SHA-256 `11aa03734460ebdb5a894a544859093a557b1be67ba9c44c02d63c9b0a23b731`; both PASS: removing forwarding disconnects Molly warning icon and is ineligible.
- `8ee81bb2368c6a09e0b9` / resolved `0269c6aef9f0c55f0c1c`: SHA-256 `16fa45ceac5e5a33c17b9c9099d3361e84fe44e0e5d78883f5f8a8acba690913`; both FAIL: unresolved trailingIcon and overload inference errors.

## Credential provider probe

P4 only: the actual temporary Robolectric probe below passed API 28 and 31. Production merged manifest and Robolectric service metadata both lacked androidx.credentials.CREDENTIAL_PROVIDER_KEY. Enabled Autofill made isSupported true while AndroidX 1.6.0 direct get and create threw provider-configuration exceptions. The AndroidX factory reads ServiceInfo metadata, not ApplicationInfo. Enabled Autofill does not establish device IME or native Autofill behavior. P3 relevant inputs match, but P3 was not tested. Old Molly pins AndroidX 1.6.0 and uses the same predicate and direct calls; its merged manifest and runtime remain unverified, so a new port regression is not established. No inverse was measured and no change is nominated. The Google default label is an Autofill heuristic, not provider verification; the API 34 passkey guard excludes the Play bridge by policy. Passkey-only UnknownError UX is separate and out of scope.

The exact temporary test source has SHA-256 `fa158f68d06d3979958553bed14105e409207db82d7a1331bf933a62633be8b4`. Place it at app/src/test/java/org/thoughtcrime/securesms/CredentialProviderProbeTest.kt in an isolated reconstructed P4 checkout; adjust its absolute manifest path to that checkout’s prodWebsiteDebug merged manifest. The test asserts parsed service metadata matches Robolectric before checking behavior.

```kotlin
package org.thoughtcrime.securesms

import android.app.Application
import android.content.pm.PackageManager
import android.view.autofill.AutofillManager
import androidx.credentials.CredentialManager
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.exceptions.CreateCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.signal.passwordmanager.SignalCredentialManager
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28, 31])
class CredentialProviderProbeTest {
  @Test fun enabledAutofillWithoutProvider() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val key = "androidx.credentials.CREDENTIAL_PROVIDER_KEY"
    val manifest = File("/var/home/x/agent-scratch/mollyim-android/pr-stack/preflight/release/app/build/intermediates/merged_manifest/prodWebsiteDebug/processProdWebsiteDebugMainManifest/AndroidManifest.xml")
    val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(manifest)
    val nodes = doc.getElementsByTagName("service")
    val actual = (0 until nodes.length).flatMap { i ->
      val children = nodes.item(i).childNodes
      (0 until children.length).mapNotNull { j ->
        val node = children.item(j)
        val attrs = node.attributes ?: return@mapNotNull null
        if (node.nodeName == "meta-data" && attrs.getNamedItemNS("http://schemas.android.com/apk/res/android", "name")?.nodeValue == key)
          attrs.getNamedItemNS("http://schemas.android.com/apk/res/android", "value")?.nodeValue else null
      }
    }.sorted()
    val info = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_SERVICES or PackageManager.GET_META_DATA)
    val runtime = info.services.orEmpty().mapNotNull { it.metaData?.getString(key) }.sorted()
    assertEquals("merged application provider metadata", emptyList<String>(), actual)
    assertEquals("Robolectric must match merged application provider metadata", actual, runtime)
    val autofill = app.getSystemService(AutofillManager::class.java)
    shadowOf(autofill).setAutofillSupported(true)
    shadowOf(autofill).setEnabled(true)
    assertTrue(autofill.isAutofillSupported)
    assertTrue(autofill.isEnabled)
    assertTrue(SignalCredentialManager.isSupported(app))
    val manager = CredentialManager.create(app)
    val getError = try { manager.getCredential(app, GetCredentialRequest(listOf(GetPasswordOption()))); null } catch (e: Exception) { e }
    assertTrue("get: $getError", getError is GetCredentialProviderConfigurationException)
    val createError = try { manager.createCredential(app, CreatePasswordRequest("fixture-user", "fixture-password")); null } catch (e: Exception) { e }
    assertTrue("create: $createError", createError is CreateCredentialProviderConfigurationException)
  }
}
```



```sh
export JAVA_HOME=/var/home/x/.local/opt/jdk-21.0.13+9 ANDROID_HOME=/home/x/Android/Sdk TMPDIR=/var/home/x/agent-scratch/mollyim-android/pr-stack/tmp
export PATH="$JAVA_HOME/bin:$PATH"
agent-heavy -- ./gradlew --offline --no-daemon --no-parallel --no-configuration-cache --max-workers=1 --dependency-verification=strict --init-script /var/home/x/agent-scratch/mollyim-android/pr-stack/worktree/tools/patches/test-worker-heap.init.gradle -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx5g -Xms256m -XX:MaxMetaspaceSize=1g -Dcom.android.tools.r8.threadCount=1 -XX:+UnlockExperimentalVMOptions -XX:hashCode=3' :app:testProdWebsiteDebugUnitTest --rerun --tests org.thoughtcrime.securesms.CredentialProviderProbeTest
```

## Logger and direct network inverses

# AndroidLogger whole-hunk inverse

Both private candidates restore the exact old Molly logger blob and both targeted Kotlin compilation tasks executed and passed. Compilation does not assess logcat behavior, OEM View-tag noise, privacy at runtime, or any device behavior.

Path: `core/util/src/main/java/org/signal/core/util/logging/AndroidLogger.kt`, mode 100644. PR3 source `842e226be91f6b9d664df3b92ef460d07e6bde14`, tree `d4b5d14b580bc2faa8af989c48d34e77cada3ca4`; PR4 source `cf3146bb3fcc29eb3059528df52a5f304a6b379a`, tree `96ce7bb4a50dcf1da4952fe07ff59e48ca5fd985`. Both original blobs `b1f56e285a3e4952e96bbf1041885a6cf2778a56`. Old Molly `0e202dda90785771ff8c87526eaaf30c655f54f3`, mechanical tree `759ef1c309d93aa7e4ac7217b144c04545562f11`, resolved tree `71497b00763044ed070d694329265922d2d79d22`, and both candidates all have exact blob `ab98fa250442b44260128fcbd7eeb8b85e8cdfd3`. Trees are trees, not commits. Signal `6e9dddc01f1b26e608ac683807c51e139c141ed2` has different logger blob `b0d37d7b95f44d847ab50cbceb267772a9702112`. Cached `origin/signal-8.20.4` has old Molly blob; `origin/upstream-stable` and `origin/upstream-main` have Signal blob. No network fetch.

One hunk against both mechanical and resolved reference, `7c3d3009695ca2c58110` from `tools/patches/port_report.py` text_hunks on unified-three Git diff: comment `// Some OEM builds log a View message for every draw pass.` and `exec(arrayOf("logcat", "-d", "View:S", "*:V"))` replace `exec("logcat -d")`. The candidate reverses the complete hunk, preserving all remaining Molly logger bytes and mode. Prior source trees `2533afec2fe2d7f9fc682f699fc21d7624521889` and `59fa2b2af25d82ffd11d0198d5e9e8f5b5b3eadf` have the same logger as current respective sources. Historical commit `86a6f90e1140b298b702068904fb28180d834dfa` records the filter as part of AI-generated compatibility resolutions from earlier PR #7 port source; historical `9fd7b272e2b546c5fc706315df09c5778590f1fb` explicitly removed the port-only View filter to retain Molly collection. Neither named commit documents an essential workaround; the comment states the noise rationale.

| Candidate | Private ref | Commit | Tree | Gate |
|---|---|---|---|---|
| PR3 | `refs/agent/android-logger-minimization/pr3` | `d2bacf744b706b7152866dac564eea391d9b3877` | `edba85a25dd843fbe31e00cc36ac4a2a1015781f` | PASS |
| PR4 | `refs/agent/android-logger-minimization/pr4` | `f6f8f742d190c2de4cd3b9a59c51d47b1abe86a5` | `be2e6675d04a03ba4db5bb02b6f1b6a05ea820ac` | PASS |

Both exact inverse patches are adjacent (`pr3.patch`, `pr4.patch`), SHA-256 `c321c6f917f0e1438fcc74dec39c73ee62e09bbae65bdf9d1208237508577e2d`. Both logs (`pr3.log`, `pr4.log`) SHA-256 `5e0a479378c33fafc9451ec37e2eb6da22e5f7c234b30499c3240721e4c9cf80`; each reports `:core:util:compileDebugKotlin` executed, BUILD SUCCESSFUL, one executed and 30 up-to-date. Existing exact-parent full preflight logs `full-preflight-current/p3-sdk-cache-fix.log` and `p4-sdk-cache-fix.log` also show this task executed. No focused AndroidLogger unit test found in module test sources. Two disk checks: OK free=247G unallocated=2G metadata=79%.

Each candidate ran, with separate checkout and log:

```sh
export JAVA_HOME=/var/home/x/.local/opt/jdk-21.0.13+9 ANDROID_HOME=/home/x/Android/Sdk PATH=/var/home/x/.local/opt/jdk-21.0.13+9/bin:$PATH
agent-heavy -- ./gradlew --offline --no-daemon --no-parallel --no-configuration-cache --max-workers=1 --dependency-verification=strict --init-script /var/home/x/agent-scratch/mollyim-android/pr-stack/worktree/tools/patches/test-worker-heap.init.gradle -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx5g -Xms256m -XX:MaxMetaspaceSize=1g -Dcom.android.tools.r8.threadCount=1 -XX:+UnlockExperimentalVMOptions -XX:hashCode=3' :core:util:compileDebugKotlin --rerun
```

Physical additions/deletions (`git diff --no-renames --numstat`, all text files):

| Branch | Base | Before | Candidate |
|---|---|---|---|
| PR3 | mechanical | 7407 / 98058 | 7405 / 98057 |
| PR3 | old Molly | 396668 / 239850 | 396666 / 239849 |
| PR4 | mechanical | 7800 / 98068 | 7798 / 98067 |
| PR4 | old Molly | 397035 / 239834 | 397033 / 239833 |

The file itself changes from +2/-1 to +0/-0 against each reference. Restored source checkout is clean at PR4 `cf3146bb3fcc29eb3059528df52a5f304a6b379a`, tree `96ce7bb4a50dcf1da4952fe07ff59e48ca5fd985`; build retained and source lease released.

# Direct network call inverse checks

No reduction nominated. Four independent inverses changed only one direct-client call per candidate to the resolved-reference call; all four selected Kotlin compile tasks executed and failed. These failures show binding incompatibility with the current helper and configuration, not semantic necessity of the complete routing implementation or DNS safety. No runtime, network provider, account, emulator, download, or behavioral test was used.

Source: PR3 `842e226be91f6b9d664df3b92ef460d07e6bde14` tree `d4b5d14b580bc2faa8af989c48d34e77cada3ca4`; PR4 `cf3146bb3fcc29eb3059528df52a5f304a6b379a` tree `96ce7bb4a50dcf1da4952fe07ff59e48ca5fd985`. Their `lib/network/src/main/java/org/signal/network/rest/SignalRestClient.kt` blob is `a4d1ff5c28853e9f144b8e4514b5dcdfec772455`. Resolved reference tree `71497b00763044ed070d694329265922d2d79d22` blob `ab91ec2c721a85e0038360b219d8229213342ef5`; raw mechanical tree `759ef1c309d93aa7e4ac7217b144c04545562f11` blob `1ec4fd53cfde8c02e287001d8688b90bc900b29f`. These are trees, not commits. Both hunk IDs computed using `tools/patches/port_report.py` text_hunks over unified-three Git diffs have identical IDs for the mechanical and resolved references: `a3edbbf20cc72326e4be` direct CDN; `ee3eba752c78552bc3f0` direct storage. Both reference calls pass `uncensoredConfiguration.signalProxy`; current calls pass `uncensoredConfiguration.socketFactory` and `.proxySelector`. Current and resolved-reference helper expects `Dns`, `SocketFactory`, and `ProxySelector`; the reference has no conflict markers in either call. Each patch file contains the precise inverse; same call patch is byte-identical across branches.

| Branch / ID | Private commit | Tree | Candidate file blob | Exit |
|---|---|---|---|---|
| PR3 / a3edbbf20cc72326e4be | `2f87e0461545efa8a463cd5039f26963f3c2173d` | `ae7999c40884ab9f1991f8f43e9de70c84e6016a` | `756ca87d7cf1f9fc7b09327165b1a06b98da57f1` | 1 |
| PR3 / ee3eba752c78552bc3f0 | `9abd14e4cf8c498d5b8b806ff5b67cd8813f2179` | `be94caea0aa9ad1a9462ee0546d99cfdd6757297` | `94f6702177c218ed93d6f168d2b1d50e2fc0f55e` | 1 |
| PR4 / a3edbbf20cc72326e4be | `ad89afc8bbe914e2b17496d2839c5206f355be15` | `1e58cd97e5dbd875dff7db2ffb59a1285c0bea02` | `756ca87d7cf1f9fc7b09327165b1a06b98da57f1` | 1 |
| PR4 / ee3eba752c78552bc3f0 | `933f12b131dda9f85ba6ac294e9b7d85d2a14f0c` | `f8412574e58e74de0261acdee82d3e09a9210d0b` | `94f6702177c218ed93d6f168d2b1d50e2fc0f55e` | 1 |

Refs are `refs/agent/direct-network-call-cohort/{pr3,pr4}/<ID>`. CDN inverse patch SHA-256 `0af19dccdb38a23660ae1de58a2a2020f7a1206aaa465e161d24b0d9051bead0`; storage `96b107a49de31399f196d2ce15bec1d407116ca8a02561fd3e306dd17f35ce55`. `cases.tsv`, `results.tsv`, patch files, and logs are adjacent. Every task was `:lib:network:compileKotlin FAILED`, first compiler cause unresolved `signalProxy`; subsequent errors report `OkHttpClient?` where `ProxySelector` expected and no `clientOverride`. The module applies `org.jetbrains.kotlin.jvm`, so no debug variant applies. Existing full-preflight logs `full-preflight-current/p3-sdk-cache-fix.log` and `p4-sdk-cache-fix.log` show the task executed at the exact source trees above. Four disk checks returned `OK free=247G unallocated=2G metadata=79%`.

Each candidate checked out its private commit in the existing source build tree and ran:

```sh
export JAVA_HOME=/var/home/x/.local/opt/jdk-21.0.13+9 ANDROID_HOME=/home/x/Android/Sdk PATH=/var/home/x/.local/opt/jdk-21.0.13+9/bin:$PATH
agent-heavy -- ./gradlew --offline --no-daemon --no-parallel --no-configuration-cache --max-workers=1 --dependency-verification=strict --init-script /var/home/x/agent-scratch/mollyim-android/pr-stack/worktree/tools/patches/test-worker-heap.init.gradle -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx5g -Xms256m -XX:MaxMetaspaceSize=1g -Dcom.android.tools.r8.threadCount=1 -XX:+UnlockExperimentalVMOptions -XX:hashCode=3' :lib:network:compileKotlin --rerun
```

Old Molly `0e202dda90785771ff8c87526eaaf30c655f54f3`, local `origin/signal-8.20.4` `5551f3023e6093fbb65214acdcc2e568b7b63132`, and `origin/fix/socks-proxy-dns-leak` `aa81fd6312bf1c7a71f5efda1930d23b05bdb739` share client blob `5f014e6fcece49586509617300a29cecfc76c0da`. They contain the matching helper and configure normal clients with socketFactory/proxySelector and DNS; their NetworkAccess constructs configurations using `Networking.socketFactory`, `Networking.proxySelectorForSocks`, and `Networking.dns`. Their client has no direct CDN or direct storage feature, so no exact old Molly equivalent was found in those checked local refs. New Signal `6e9dddc01f1b26e608ac683807c51e139c141ed2` and local upstream-main `515db93fb48206d63406cbfb48c5816f6af1d9e2` share client blob `ad79ff65eb2d4696228c652d31d50dcb2f0976e4`; both have the original direct calls and Signal proxy-based helper. Searches used `GIT_NO_LAZY_FETCH=1` and existing blobs only; no other history was searched.

Per-file physical additions/deletions against mechanical tree: baseline +16/-10, either inverse +8/-9; against old Molly: baseline +138/-44, either inverse +131/-44. These are unaccepted counterfactual counts. Source restored clean at PR4 parent and tree above; build retained and lease released. No changes integrated.

The network inverse trees above are historical inputs, before the logger change; no new counterfactual network tree is claimed.


## Ten import restorations

Each case below restores a subset of an original raw diff hunk; the patch filename uses the first 20 hex digits of the patch SHA-256, not a raw hunk ID. Independent PR3 and PR4 candidates were gated on source parents `34f0a76bcd67a7f0551a9f95067dcf0d1e9a7384` and `bc954052836c79d40322d4f81bc4283f173fb44d` respectively. Both individual gates passed for each case.

| Case | Path | PR3 candidate | Patch SHA-256 |
|---|---|---|---|
| main-activity | `app/src/main/java/org/thoughtcrime/securesms/MainActivity.kt` | `23c7c3b2a2891f46b9da5848782de30fa941f7dc` | `41eb884ca0dbef09bc61342e209e8a14b4b20ec1b23b2e1c3808af1b0ee987e8` |
| add-message | `app/src/main/java/org/thoughtcrime/securesms/mediasend/v2/review/AddMessageDialogFragment.kt` | `8852e2651697beb09d1f79c548ddf4c6ff884bc5` | `18e837587699c2ccdb07667e5ad80a0c47b64c88d8d642de7a368d43dec8bbe6` |
| network-access | `app/src/main/java/org/thoughtcrime/securesms/push/SignalServiceNetworkAccess.kt` | `482fc9adcfeded9a83ad508229055cb472470aeb` | `87ce6907dd34cd8a2f4876c788013e1f504ad1846afaf6792e7fff4876a34fec` |
| application-context-incoming-message-observer | `app/src/main/java/org/thoughtcrime/securesms/ApplicationContext.java` | `c392b04b024861cfed4db211f43130c1ed3317d3` | `a898450028093f6dbe47a95dba13533adc5bc94e706dd2979979c1e1b51fa381` |
| application-context-communication-actions | `app/src/main/java/org/thoughtcrime/securesms/ApplicationContext.java` | `0a6d50be5df97c1cffabb080427171ec80581ddf` | `a3356913785cf5890f1daf1d002fd538b89aefb4b6101deb0538e4d59594f344` |
| application-context-support-email-util | `app/src/main/java/org/thoughtcrime/securesms/ApplicationContext.java` | `779ac5a0dfe37cff2b739ced7cb50147e70112f9` | `519b1bae9259228d11789fffe7d881b991941803a62f1f48cb4bb4dbabad763e` |
| main-activity-android-fragment | `app/src/main/java/org/thoughtcrime/securesms/MainActivity.kt` | `f8bced165faae067d61f1083359a036c2d88ae14` | `c99169709ac2738fe432eed7dff619ea8d2fda3515f2b457c53ac1d3a8a8e6f3` |
| main-activity-transition-specs | `app/src/main/java/org/thoughtcrime/securesms/MainActivity.kt` | `a4dffbf5495149e65070cdaa098bb262bb4de30e` | `b66bfdb0d1545eb6aeafcdf5c89224d7878db0ae09da79453f500b76af635b8e` |
| shortcut-launcher-activity-task-stack-builder | `app/src/main/java/org/thoughtcrime/securesms/ShortcutLauncherActivity.java` | `9190cffac33d8ec563539aae5f39d8648d7b6219` | `cf2626f0fef0a77480cd7e5d10dce7e300e83239aeee9270f6890658d4d9045d` |
| apk-update-refresh-listener-text-secure-preferences | `app/src/main/java/org/thoughtcrime/securesms/apkupdate/ApkUpdateRefreshListener.java` | `5d8ee7aad84fbec894171b3c562ac569701c488b` | `981f51dd421773a0989270ad8f0562e1013adc5044d8b24ead27e60e0274f8d6` |

The combined candidate trees `dbfcd0cb03ec7ee164da222069249076819590c0` (PR3) and `da0dd13a3715105c65119054a26ab7a8a294d4f6` (PR4) both passed with Kotlin and Java tasks executed. Command: `agent-heavy -- ./gradlew --offline --no-daemon --no-parallel --no-configuration-cache --no-build-cache --max-workers=1 --dependency-verification=strict --init-script tools/patches/test-worker-heap.init.gradle -Pkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs="-Xmx5g -Xms256m -XX:MaxMetaspaceSize=1g -Dcom.android.tools.r8.threadCount=1 -XX:+UnlockExperimentalVMOptions -XX:hashCode=3" :app:compileProdWebsiteDebugKotlin :app:compileProdWebsiteDebugJavaWithJavac` with JDK 21 and Android SDK 37. The init script was supplied by absolute builder path.

Independent old-source counterchecks for `ConversationTransitionState`, `MediaSendActivityContract`, and `org.greenrobot.eventbus.Subscribe` failed on both source parents at Kotlin compilation because the corresponding symbol or package was unresolved. These counters did not run on either combined tree. Static UnifiedPush producer and consumer review found the event migration preserved; runtime behavior was not measured. Restoring `KeyEvent` does not restore keypress behavior.


### Individual compiler gate records

These 26 independent cases ran before the combined imports. PR3 source parent `34f0a76bcd67a7f0551a9f95067dcf0d1e9a7384` had tree `edba85a25dd843fbe31e00cc36ac4a2a1015781f`; PR4 source parent `bc954052836c79d40322d4f81bc4283f173fb44d` had tree `be2e6675d04a03ba4db5bb02b6f1b6a05ea820ac`. Each row gives the actual candidate commit, tree, full file path, resulting blob, exact one-line import and patch hash. The passing cases have the versioned individual patches listed above. To reproduce a failed case, add its exact import from the row to its named file at its respective parent; the failed candidates were not applied to the combined trees. The exact individual command is in each row, as executed from the source checkout with JDK 21 and Android SDK 37; `requested_executed=True` records execution of the requested compiler, and `failed_task` and `diagnostics` record the first failure. Blank diagnostics mean no recorded failure.

The first two PR4 Kotlin commands use `--rerun`. Later commands use `--no-build-cache` and no `--rerun`; the PR4 IncomingMessageObserver command also uses `--info`. The SHA-256 `9cb4e057d73ab4722afda52fd8ee531767a055254234729416167a601d3655fc` labels its previously filtered compact log; the original verbose log hash was overwritten on resume and is unavailable. No result below claims execution on either later combined tree.

```tsv
branch	candidate	commit	tree	file	blob	patch_sha256	import	command	requested_task	exit	requested_executed	failed_task	outcome	diagnostics	upstream_headers
pr4	main-activity	8ee5044a506d0bc789463f47d3c4fe530e263861	318a6ac677e7b035df002af0b19fbecc8606133d	app/src/main/java/org/thoughtcrime/securesms/MainActivity.kt	4641434ada28a7804881caf12c35420021f691f1	41eb884ca0dbef09bc61342e209e8a14b4b20ec1b23b2e1c3808af1b0ee987e8	import android.app.Activity	agent-heavy -- ./gradlew --offline --no-daemon --no-parallel --no-configuration-cache --max-workers=1 --dependency-verification=strict --init-script /var/home/x/agent-scratch/mollyim-android/pr-stack/worktree/tools/patches/test-worker-heap.init.gradle -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx5g -Xms256m -XX:MaxMetaspaceSize=1g -Dcom.android.tools.r8.threadCount=1 -XX:+UnlockExperimentalVMOptions -XX:hashCode=3' :app:compileProdWebsiteDebugKotlin --rerun	:app:compileProdWebsiteDebugKotlin	0	True		PASS		origin/signal-8.20.4:present; origin/upstream-stable:present; origin/upstream-main:present; origin/main:present
pr4	add-message	44d1e3e9e37a3e3ae0e4aa4fc155ac91f16aa137	46b591010e65bd727dcc1a5dd96547a31f99b674	app/src/main/java/org/thoughtcrime/securesms/mediasend/v2/review/AddMessageDialogFragment.kt	cb3b7be6888113b8f6c0d061db0765513ea9fd1e	18e837587699c2ccdb07667e5ad80a0c47b64c88d8d642de7a368d43dec8bbe6	import android.view.KeyEvent	agent-heavy -- ./gradlew --offline --no-daemon --no-parallel --no-configuration-cache --max-workers=1 --dependency-verification=strict --init-script /var/home/x/agent-scratch/mollyim-android/pr-stack/worktree/tools/patches/test-worker-heap.init.gradle -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx5g -Xms256m -XX:MaxMetaspaceSize=1g -Dcom.android.tools.r8.threadCount=1 -XX:+UnlockExperimentalVMOptions -XX:hashCode=3' :app:compileProdWebsiteDebugKotlin --rerun	:app:compileProdWebsiteDebugKotlin	0	True		PASS		origin/signal-8.20.4:present; origin/upstream-stable:absent; origin/upstream-main:absent; origin/main:present
pr4	network-access	0ef08a6b4c666476730489c56941bf2932404dab	15491320e94c8c6deeb9ba04d8888d18b846c49a	app/src/main/java/org/thoughtcrime/securesms/push/SignalServiceNetworkAccess.kt	a07d65ec8e944349992fcceb128c6d45f6ff9ce9	87ce6907dd34cd8a2f4876c788013e1f504ad1846afaf6792e7fff4876a34fec	import java.util.Optional	agent-heavy -- ./gradlew --offline --no-daemon --no-parallel --no-configuration-cache --max-workers=1 --dependency-verification=strict --init-script /var/home/x/agent-scratch/mollyim-android/pr-stack/worktree/tools/patches/test-worker-heap.init.gradle -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx5g -Xms256m -XX:MaxMetaspaceSize=1g -Dcom.android.tools.r8.threadCount=1 -XX:+UnlockExperimentalVMOptions -XX:hashCode=3' --no-build-cache :app:compileProdWebsiteDebugKotlin	:app:compileProdWebsiteDebugKotlin	0	True		PASS		origin/signal-8.20.4:present; origin/upstream-stable:present; origin/upstream-main:present; origin/main:present
pr4	shortcut-launcher-activity-task-stack-builder	5ea21b92e446af0c254b44e7d03d38bc8bad8cd5	bd52dff665d8e42b95e06a1d6d1a7ed4d184a235	app/src/main/java/org/thoughtcrime/securesms/ShortcutLauncherActivity.java	ec7d48d92ea3b0d12f9a50fa96b1f9656d451160	cf2626f0fef0a77480cd7e5d10dce7e300e83239aeee9270f6890658d4d9045d	import androidx.core.app.TaskStackBuilder;	agent-heavy -- ./gradlew --offline --no-daemon --no-parallel --no-configuration-cache --max-workers=1 --dependency-verification=strict --init-script /var/home/x/agent-scratch/mollyim-android/pr-stack/worktree/tools/patches/test-worker-heap.init.gradle -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx5g -Xms256m -XX:MaxMetaspaceSize=1g -Dcom.android.tools.r8.threadCount=1 -XX:+UnlockExperimentalVMOptions -XX:hashCode=3' --no-build-cache :app:compileProdWebsiteDebugJavaWithJavac	:app:compileProdWebsiteDebugJavaWithJavac	0	True		PASS		origin/signal-8.20.4:present; origin/upstream-stable:absent; origin/upstream-main:absent; origin/main:present
pr4	apk-update-refresh-listener-text-secure-preferences	e36472e64b861c03f893755bef40a85d09a2101d	1943abbf1446cc712d35f886a97cf39b642961cb	app/src/main/java/org/thoughtcrime/securesms/apkupdate/ApkUpdateRefreshListener.java	c48215ed4aec49c983a02bf19076541c3198d361	981f51dd421773a0989270ad8f0562e1013adc5044d8b24ead27e60e0274f8d6	import org.thoughtcrime.securesms.util.TextSecurePreferences;	agent-heavy -- ./gradlew --offline --no-daemon --no-parallel --no-configuration-cache --max-workers=1 --dependency-verification=strict --init-script /var/home/x/agent-scratch/mollyim-android/pr-stack/worktree/tools/patches/test-worker-heap.init.gradle -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx5g -Xms256m -XX:MaxMetaspaceSize=1g -Dcom.android.tools.r8.threadCount=1 -XX:+UnlockExperimentalVMOptions -XX:hashCode=3' --no-build-cache :app:compileProdWebsiteDebugJavaWithJavac	:app:compileProdWebsiteDebugJavaWithJavac	0	True		PASS		origin/signal-8.20.4:present; origin/upstream-stable:absent; origin/upstream-main:absent; origin/main:present
pr4	application-context-communication-actions	35f93603b63176f9c027219c2b30d13b5a04e3ea	7c23c8f4cf5a2d84f7b26e8ec3950baf9f24dbe9	app/src/main/java/org/thoughtcrime/securesms/ApplicationContext.java	186c82e4dc7f69b297b7a43a573c3327d3f438d3	a3356913785cf5890f1daf1d002fd538b89aefb4b6101deb0538e4d59594f344	import org.thoughtcrime.securesms.util.CommunicationActions;	agent-heavy -- ./gradlew --offline --no-daemon --no-parallel --no-configuration-cache --max-workers=1 --dependency-verification=strict --init-script /var/home/x/agent-scratch/mollyim-android/pr-stack/worktree/tools/patches/test-worker-heap.init.gradle -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx5g -Xms256m -XX:MaxMetaspaceSize=1g -Dcom.android.tools.r8.threadCount=1 -XX:+UnlockExperimentalVMOptions -XX:hashCode=3' --no-build-cache :app:compileProdWebsiteDebugJavaWithJavac	:app:compileProdWebsiteDebugJavaWithJavac	0	True		PASS		origin/signal-8.20.4:present; origin/upstream-stable:absent; origin/upstream-main:absent; origin/main:present
pr4	application-context-incoming-message-observer	16e8b3d646d57a81fcbc804c1ce62b70b64c6482	49f1819f7cd0f5829df15ee81a6f8ccb1e1cc316	app/src/main/java/org/thoughtcrime/securesms/ApplicationContext.java	7767f8132650249eaa0b4dfad792e9e6e2fcbfb1	a898450028093f6dbe47a95dba13533adc5bc94e706dd2979979c1e1b51fa381	import org.thoughtcrime.securesms.messages.IncomingMessageObserver;	agent-heavy -- ./gradlew --offline --no-daemon --no-parallel --no-configuration-cache --max-workers=1 --dependency-verification=strict --init-script /var/home/x/agent-scratch/mollyim-android/pr-stack/worktree/tools/patches/test-worker-heap.init.gradle -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx5g -Xms256m -XX:MaxMetaspaceSize=1g -Dcom.android.tools.r8.threadCount=1 -XX:+UnlockExperimentalVMOptions -XX:hashCode=3' --no-build-cache --info :app:compileProdWebsiteDebugJavaWithJavac	:app:compileProdWebsiteDebugJavaWithJavac	0	True		PASS		origin/signal-8.20.4:present; origin/upstream-stable:present; origin/upstream-main:present; origin/main:present
pr4	application-context-support-email-util	b58f140d73c7f7d64b9448caa5c4d514b400d3f3	b62205eaa8e9d2457dd9ca7d13f2a758f11cf5f3	app/src/main/java/org/thoughtcrime/securesms/ApplicationContext.java	eec60b5393aa7b0abf6392a33c3720d6eba503b8	519b1bae9259228d11789fffe7d881b991941803a62f1f48cb4bb4dbabad763e	import org.thoughtcrime.securesms.util.SupportEmailUtil;	agent-heavy -- ./gradlew --offline --no-daemon --no-parallel --no-configuration-cache --max-workers=1 --dependency-verification=strict --init-script /var/home/x/agent-scratch/mollyim-android/pr-stack/worktree/tools/patches/test-worker-heap.init.gradle -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx5g -Xms256m -XX:MaxMetaspaceSize=1g -Dcom.android.tools.r8.threadCount=1 -XX:+UnlockExperimentalVMOptions -XX:hashCode=3' --no-build-cache :app:compileProdWebsiteDebugJavaWithJavac	:app:compileProdWebsiteDebugJavaWithJavac	0	True		PASS		origin/signal-8.20.4:present; origin/upstream-stable:absent; origin/upstream-main:absent; origin/main:present
pr4	main-activity-android-fragment	cd2296e1e66a0d8621f0af5e6b1afc3312021b70	820b126cdda797674383091515febfbb9f648022	app/src/main/java/org/thoughtcrime/securesms/MainActivity.kt	a259dc0711a66237d009bd140e5abd70b708d7b5	c99169709ac2738fe432eed7dff619ea8d2fda3515f2b457c53ac1d3a8a8e6f3	import androidx.fragment.compose.AndroidFragment	agent-heavy -- ./gradlew --offline --no-daemon --no-parallel --no-configuration-cache --max-workers=1 --dependency-verification=strict --init-script /var/home/x/agent-scratch/mollyim-android/pr-stack/worktree/tools/patches/test-worker-heap.init.gradle -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx5g -Xms256m -XX:MaxMetaspaceSize=1g -Dcom.android.tools.r8.threadCount=1 -XX:+UnlockExperimentalVMOptions -XX:hashCode=3' --no-build-cache :app:compileProdWebsiteDebugKotlin	:app:compileProdWebsiteDebugKotlin	0	True		PASS		origin/signal-8.20.4:present; origin/upstream-stable:absent; origin/upstream-main:absent; origin/main:present
pr4	main-activity-conversation-transition-state	77356a5413acc1a5b3f4e7d8ffab3920ede5b58a	fc9f3c87b501c9e104e9a3cf27adb077bdd575a2	app/src/main/java/org/thoughtcrime/securesms/MainActivity.kt	2e77d77795c4a5e494deb781265da3ccd177f126	85dada31bef8e817b51fdd2e0680864c292b1cd7b1f4f1989f5e8e527189b9d4	import org.thoughtcrime.securesms.chats.ConversationTransitionState	agent-heavy -- ./gradlew --offline --no-daemon --no-parallel --no-configuration-cache --max-workers=1 --dependency-verification=strict --init-script /var/home/x/agent-scratch/mollyim-android/pr-stack/worktree/tools/patches/test-worker-heap.init.gradle -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx5g -Xms256m -XX:MaxMetaspaceSize=1g -Dcom.android.tools.r8.threadCount=1 -XX:+UnlockExperimentalVMOptions -XX:hashCode=3' --no-build-cache :app:compileProdWebsiteDebugKotlin	:app:compileProdWebsiteDebugKotlin	1	True	:app:compileProdWebsiteDebugKotlin	FAIL	e: file:///var/home/x/agent-scratch/mollyim-android/pr-stack/preflight/release/app/src/main/java/org/thoughtcrime/securesms/MainActivity.kt:107:41 Unresolved reference 'ConversationTransitionState'. | > Task :app:compileProdWebsiteDebugKotlin FAILED | * What went wrong: | Execution failed for task ':app:compileProdWebsiteDebugKotlin' (registered by plugin 'com.android.internal.application').	origin/signal-8.20.4:present; origin/upstream-stable:absent; origin/upstream-main:absent; origin/main:present
pr4	main-activity-media-send-activity-contract	39f69bba817f29028bd345f1eb4b3209bed6d6f4	69a0160d571a068c03392aa5536ec88b06ea6993	app/src/main/java/org/thoughtcrime/securesms/MainActivity.kt	8d64b3d9f97e8399c0278978b35a247822dbc5ff	230888145e160e8b137cc2dfb81957bc4dd1248422769ba12dbd052f2d940105	import org.signal.mediasend.MediaSendActivityContract	agent-heavy -- ./gradlew --offline --no-daemon --no-parallel --no-configuration-cache --max-workers=1 --dependency-verification=strict --init-script /var/home/x/agent-scratch/mollyim-android/pr-stack/worktree/tools/patches/test-worker-heap.init.gradle -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx5g -Xms256m -XX:MaxMetaspaceSize=1g -Dcom.android.tools.r8.threadCount=1 -XX:+UnlockExperimentalVMOptions -XX:hashCode=3' --no-build-cache :app:compileProdWebsiteDebugKotlin	:app:compileProdWebsiteDebugKotlin	1	True	:app:compileProdWebsiteDebugKotlin	FAIL	e: file:///var/home/x/agent-scratch/mollyim-android/pr-stack/preflight/release/app/src/main/java/org/thoughtcrime/securesms/MainActivity.kt:99:29 Unresolved reference 'MediaSendActivityContract'. | > Task :app:compileProdWebsiteDebugKotlin FAILED | * What went wrong: | Execution failed for task ':app:compileProdWebsiteDebugKotlin' (registered by plugin 'com.android.internal.application').	origin/signal-8.20.4:present; origin/upstream-stable:absent; origin/upstream-main:absent; origin/main:present
pr4	main-activity-transition-specs	f8b5e20fb491fc7ca2df7a363e2d7b9005740633	4d344ff9544771e84b3f121a0bb4f3bd8ea02c2d	app/src/main/java/org/thoughtcrime/securesms/MainActivity.kt	f2480e01ff66ca9fdbcafeab94f9da78917af71c	b66bfdb0d1545eb6aeafcdf5c89224d7878db0ae09da79453f500b76af635b8e	import org.signal.core.ui.navigation.TransitionSpecs	agent-heavy -- ./gradlew --offline --no-daemon --no-parallel --no-configuration-cache --max-workers=1 --dependency-verification=strict --init-script /var/home/x/agent-scratch/mollyim-android/pr-stack/worktree/tools/patches/test-worker-heap.init.gradle -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx5g -Xms256m -XX:MaxMetaspaceSize=1g -Dcom.android.tools.r8.threadCount=1 -XX:+UnlockExperimentalVMOptions -XX:hashCode=3' --no-build-cache :app:compileProdWebsiteDebugKotlin	:app:compileProdWebsiteDebugKotlin	0	True		PASS		origin/signal-8.20.4:present; origin/upstream-stable:absent; origin/upstream-main:absent; origin/main:present
pr4	unified-push-settings-fragment-subscribe	72eb5bb2cc8bf497437e8e95449cfe192b16a284	c32fb03eb8b3d42c093bc2f60ceef1a8724e3f12	app/src/main/java/im/molly/unifiedpush/components/settings/app/notifications/UnifiedPushSettingsFragment.kt	f103365916f12ba3865e27949fb4163434c30c1e	24b1250afc462570635eb53ffd5c94d91cabe01ade719de548cd9af78bf5a617	import org.greenrobot.eventbus.Subscribe	agent-heavy -- ./gradlew --offline --no-daemon --no-parallel --no-configuration-cache --max-workers=1 --dependency-verification=strict --init-script /var/home/x/agent-scratch/mollyim-android/pr-stack/worktree/tools/patches/test-worker-heap.init.gradle -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx5g -Xms256m -XX:MaxMetaspaceSize=1g -Dcom.android.tools.r8.threadCount=1 -XX:+UnlockExperimentalVMOptions -XX:hashCode=3' --no-build-cache :app:compileProdWebsiteDebugKotlin	:app:compileProdWebsiteDebugKotlin	1	True	:app:compileProdWebsiteDebugKotlin	FAIL	e: file:///var/home/x/agent-scratch/mollyim-android/pr-stack/preflight/release/app/src/main/java/im/molly/unifiedpush/components/settings/app/notifications/UnifiedPushSettingsFragment.kt:10:12 Unresolved reference 'greenrobot'. | > Task :app:compileProdWebsiteDebugKotlin FAILED | * What went wrong: | Execution failed for task ':app:compileProdWebsiteDebugKotlin' (registered by plugin 'com.android.internal.application').	origin/signal-8.20.4:present; origin/upstream-stable:blob unavailable; origin/upstream-main:blob unavailable; origin/main:present
pr3	main-activity	23c7c3b2a2891f46b9da5848782de30fa941f7dc	e2bb48d187399c9ae16b11bf8a738800ec9780dd	app/src/main/java/org/thoughtcrime/securesms/MainActivity.kt	4641434ada28a7804881caf12c35420021f691f1	41eb884ca0dbef09bc61342e209e8a14b4b20ec1b23b2e1c3808af1b0ee987e8	import android.app.Activity	agent-heavy -- ./gradlew --offline --no-daemon --no-parallel --no-configuration-cache --max-workers=1 --dependency-verification=strict --init-script /var/home/x/agent-scratch/mollyim-android/pr-stack/worktree/tools/patches/test-worker-heap.init.gradle -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx5g -Xms256m -XX:MaxMetaspaceSize=1g -Dcom.android.tools.r8.threadCount=1 -XX:+UnlockExperimentalVMOptions -XX:hashCode=3' --no-build-cache :app:compileProdWebsiteDebugKotlin	:app:compileProdWebsiteDebugKotlin	0	True		PASS		origin/signal-8.20.4:present; origin/upstream-stable:present; origin/upstream-main:present; origin/main:present
pr3	add-message	8852e2651697beb09d1f79c548ddf4c6ff884bc5	a00b959b7bbe54429392697d08bae27804c65029	app/src/main/java/org/thoughtcrime/securesms/mediasend/v2/review/AddMessageDialogFragment.kt	cb3b7be6888113b8f6c0d061db0765513ea9fd1e	18e837587699c2ccdb07667e5ad80a0c47b64c88d8d642de7a368d43dec8bbe6	import android.view.KeyEvent	agent-heavy -- ./gradlew --offline --no-daemon --no-parallel --no-configuration-cache --max-workers=1 --dependency-verification=strict --init-script /var/home/x/agent-scratch/mollyim-android/pr-stack/worktree/tools/patches/test-worker-heap.init.gradle -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx5g -Xms256m -XX:MaxMetaspaceSize=1g -Dcom.android.tools.r8.threadCount=1 -XX:+UnlockExperimentalVMOptions -XX:hashCode=3' --no-build-cache :app:compileProdWebsiteDebugKotlin	:app:compileProdWebsiteDebugKotlin	0	True		PASS		origin/signal-8.20.4:present; origin/upstream-stable:absent; origin/upstream-main:absent; origin/main:present
pr3	network-access	482fc9adcfeded9a83ad508229055cb472470aeb	da0c9e800e6dec0a145385c911fafd482c7abca1	app/src/main/java/org/thoughtcrime/securesms/push/SignalServiceNetworkAccess.kt	a07d65ec8e944349992fcceb128c6d45f6ff9ce9	87ce6907dd34cd8a2f4876c788013e1f504ad1846afaf6792e7fff4876a34fec	import java.util.Optional	agent-heavy -- ./gradlew --offline --no-daemon --no-parallel --no-configuration-cache --max-workers=1 --dependency-verification=strict --init-script /var/home/x/agent-scratch/mollyim-android/pr-stack/worktree/tools/patches/test-worker-heap.init.gradle -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx5g -Xms256m -XX:MaxMetaspaceSize=1g -Dcom.android.tools.r8.threadCount=1 -XX:+UnlockExperimentalVMOptions -XX:hashCode=3' --no-build-cache :app:compileProdWebsiteDebugKotlin	:app:compileProdWebsiteDebugKotlin	0	True		PASS		origin/signal-8.20.4:present; origin/upstream-stable:present; origin/upstream-main:present; origin/main:present
pr3	shortcut-launcher-activity-task-stack-builder	9190cffac33d8ec563539aae5f39d8648d7b6219	afe340ecb053bbb0282af4235393e166c2fb61e6	app/src/main/java/org/thoughtcrime/securesms/ShortcutLauncherActivity.java	ec7d48d92ea3b0d12f9a50fa96b1f9656d451160	cf2626f0fef0a77480cd7e5d10dce7e300e83239aeee9270f6890658d4d9045d	import androidx.core.app.TaskStackBuilder;	agent-heavy -- ./gradlew --offline --no-daemon --no-parallel --no-configuration-cache --max-workers=1 --dependency-verification=strict --init-script /var/home/x/agent-scratch/mollyim-android/pr-stack/worktree/tools/patches/test-worker-heap.init.gradle -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx5g -Xms256m -XX:MaxMetaspaceSize=1g -Dcom.android.tools.r8.threadCount=1 -XX:+UnlockExperimentalVMOptions -XX:hashCode=3' --no-build-cache :app:compileProdWebsiteDebugJavaWithJavac	:app:compileProdWebsiteDebugJavaWithJavac	0	True		PASS		origin/signal-8.20.4:present; origin/upstream-stable:absent; origin/upstream-main:absent; origin/main:present
pr3	apk-update-refresh-listener-text-secure-preferences	5d8ee7aad84fbec894171b3c562ac569701c488b	57791ab84cd0522df9ce9c171767ce3766eeca35	app/src/main/java/org/thoughtcrime/securesms/apkupdate/ApkUpdateRefreshListener.java	c48215ed4aec49c983a02bf19076541c3198d361	981f51dd421773a0989270ad8f0562e1013adc5044d8b24ead27e60e0274f8d6	import org.thoughtcrime.securesms.util.TextSecurePreferences;	agent-heavy -- ./gradlew --offline --no-daemon --no-parallel --no-configuration-cache --max-workers=1 --dependency-verification=strict --init-script /var/home/x/agent-scratch/mollyim-android/pr-stack/worktree/tools/patches/test-worker-heap.init.gradle -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx5g -Xms256m -XX:MaxMetaspaceSize=1g -Dcom.android.tools.r8.threadCount=1 -XX:+UnlockExperimentalVMOptions -XX:hashCode=3' --no-build-cache :app:compileProdWebsiteDebugJavaWithJavac	:app:compileProdWebsiteDebugJavaWithJavac	0	True		PASS		origin/signal-8.20.4:present; origin/upstream-stable:absent; origin/upstream-main:absent; origin/main:present
pr3	application-context-communication-actions	0a6d50be5df97c1cffabb080427171ec80581ddf	0f76ac8cc0d9d1d21704b4c57f64d438d9367ebe	app/src/main/java/org/thoughtcrime/securesms/ApplicationContext.java	186c82e4dc7f69b297b7a43a573c3327d3f438d3	a3356913785cf5890f1daf1d002fd538b89aefb4b6101deb0538e4d59594f344	import org.thoughtcrime.securesms.util.CommunicationActions;	agent-heavy -- ./gradlew --offline --no-daemon --no-parallel --no-configuration-cache --max-workers=1 --dependency-verification=strict --init-script /var/home/x/agent-scratch/mollyim-android/pr-stack/worktree/tools/patches/test-worker-heap.init.gradle -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx5g -Xms256m -XX:MaxMetaspaceSize=1g -Dcom.android.tools.r8.threadCount=1 -XX:+UnlockExperimentalVMOptions -XX:hashCode=3' --no-build-cache :app:compileProdWebsiteDebugJavaWithJavac	:app:compileProdWebsiteDebugJavaWithJavac	0	True		PASS		origin/signal-8.20.4:present; origin/upstream-stable:absent; origin/upstream-main:absent; origin/main:present
pr3	application-context-incoming-message-observer	c392b04b024861cfed4db211f43130c1ed3317d3	1e364188b382a2a385a496ddcd313008e5ea0e13	app/src/main/java/org/thoughtcrime/securesms/ApplicationContext.java	7767f8132650249eaa0b4dfad792e9e6e2fcbfb1	a898450028093f6dbe47a95dba13533adc5bc94e706dd2979979c1e1b51fa381	import org.thoughtcrime.securesms.messages.IncomingMessageObserver;	agent-heavy -- ./gradlew --offline --no-daemon --no-parallel --no-configuration-cache --max-workers=1 --dependency-verification=strict --init-script /var/home/x/agent-scratch/mollyim-android/pr-stack/worktree/tools/patches/test-worker-heap.init.gradle -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx5g -Xms256m -XX:MaxMetaspaceSize=1g -Dcom.android.tools.r8.threadCount=1 -XX:+UnlockExperimentalVMOptions -XX:hashCode=3' --no-build-cache :app:compileProdWebsiteDebugJavaWithJavac	:app:compileProdWebsiteDebugJavaWithJavac	0	True		PASS		origin/signal-8.20.4:present; origin/upstream-stable:present; origin/upstream-main:present; origin/main:present
pr3	application-context-support-email-util	779ac5a0dfe37cff2b739ced7cb50147e70112f9	0721c736626df7579eb5e1ae9f35512d4ae3185c	app/src/main/java/org/thoughtcrime/securesms/ApplicationContext.java	eec60b5393aa7b0abf6392a33c3720d6eba503b8	519b1bae9259228d11789fffe7d881b991941803a62f1f48cb4bb4dbabad763e	import org.thoughtcrime.securesms.util.SupportEmailUtil;	agent-heavy -- ./gradlew --offline --no-daemon --no-parallel --no-configuration-cache --max-workers=1 --dependency-verification=strict --init-script /var/home/x/agent-scratch/mollyim-android/pr-stack/worktree/tools/patches/test-worker-heap.init.gradle -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx5g -Xms256m -XX:MaxMetaspaceSize=1g -Dcom.android.tools.r8.threadCount=1 -XX:+UnlockExperimentalVMOptions -XX:hashCode=3' --no-build-cache :app:compileProdWebsiteDebugJavaWithJavac	:app:compileProdWebsiteDebugJavaWithJavac	0	True		PASS		origin/signal-8.20.4:present; origin/upstream-stable:absent; origin/upstream-main:absent; origin/main:present
pr3	main-activity-android-fragment	f8bced165faae067d61f1083359a036c2d88ae14	34d417df03c9e4d66f5d9e617cf0ddf8fe3ee2e0	app/src/main/java/org/thoughtcrime/securesms/MainActivity.kt	a259dc0711a66237d009bd140e5abd70b708d7b5	c99169709ac2738fe432eed7dff619ea8d2fda3515f2b457c53ac1d3a8a8e6f3	import androidx.fragment.compose.AndroidFragment	agent-heavy -- ./gradlew --offline --no-daemon --no-parallel --no-configuration-cache --max-workers=1 --dependency-verification=strict --init-script /var/home/x/agent-scratch/mollyim-android/pr-stack/worktree/tools/patches/test-worker-heap.init.gradle -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx5g -Xms256m -XX:MaxMetaspaceSize=1g -Dcom.android.tools.r8.threadCount=1 -XX:+UnlockExperimentalVMOptions -XX:hashCode=3' --no-build-cache :app:compileProdWebsiteDebugKotlin	:app:compileProdWebsiteDebugKotlin	0	True		PASS		origin/signal-8.20.4:present; origin/upstream-stable:absent; origin/upstream-main:absent; origin/main:present
pr3	main-activity-conversation-transition-state	a27f2ed0f2f956eb91639cd9d63d45b9249b3248	b492beafb8bec304b5f46cb135e82dfbb86fb8d2	app/src/main/java/org/thoughtcrime/securesms/MainActivity.kt	2e77d77795c4a5e494deb781265da3ccd177f126	85dada31bef8e817b51fdd2e0680864c292b1cd7b1f4f1989f5e8e527189b9d4	import org.thoughtcrime.securesms.chats.ConversationTransitionState	agent-heavy -- ./gradlew --offline --no-daemon --no-parallel --no-configuration-cache --max-workers=1 --dependency-verification=strict --init-script /var/home/x/agent-scratch/mollyim-android/pr-stack/worktree/tools/patches/test-worker-heap.init.gradle -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx5g -Xms256m -XX:MaxMetaspaceSize=1g -Dcom.android.tools.r8.threadCount=1 -XX:+UnlockExperimentalVMOptions -XX:hashCode=3' --no-build-cache :app:compileProdWebsiteDebugKotlin	:app:compileProdWebsiteDebugKotlin	1	True	:app:compileProdWebsiteDebugKotlin	FAIL	e: file:///var/home/x/agent-scratch/mollyim-android/pr-stack/preflight/release/app/src/main/java/org/thoughtcrime/securesms/MainActivity.kt:107:41 Unresolved reference 'ConversationTransitionState'. | > Task :app:compileProdWebsiteDebugKotlin FAILED | * What went wrong: | Execution failed for task ':app:compileProdWebsiteDebugKotlin' (registered by plugin 'com.android.internal.application').	origin/signal-8.20.4:present; origin/upstream-stable:absent; origin/upstream-main:absent; origin/main:present
pr3	main-activity-media-send-activity-contract	e83b8b27e67a29c3da7eb710ee5da67b6590a374	cbc3ccaf5a3a8b5cdfa4fd27736129f7c941dd44	app/src/main/java/org/thoughtcrime/securesms/MainActivity.kt	8d64b3d9f97e8399c0278978b35a247822dbc5ff	230888145e160e8b137cc2dfb81957bc4dd1248422769ba12dbd052f2d940105	import org.signal.mediasend.MediaSendActivityContract	agent-heavy -- ./gradlew --offline --no-daemon --no-parallel --no-configuration-cache --max-workers=1 --dependency-verification=strict --init-script /var/home/x/agent-scratch/mollyim-android/pr-stack/worktree/tools/patches/test-worker-heap.init.gradle -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx5g -Xms256m -XX:MaxMetaspaceSize=1g -Dcom.android.tools.r8.threadCount=1 -XX:+UnlockExperimentalVMOptions -XX:hashCode=3' --no-build-cache :app:compileProdWebsiteDebugKotlin	:app:compileProdWebsiteDebugKotlin	1	True	:app:compileProdWebsiteDebugKotlin	FAIL	e: file:///var/home/x/agent-scratch/mollyim-android/pr-stack/preflight/release/app/src/main/java/org/thoughtcrime/securesms/MainActivity.kt:99:29 Unresolved reference 'MediaSendActivityContract'. | > Task :app:compileProdWebsiteDebugKotlin FAILED | * What went wrong: | Execution failed for task ':app:compileProdWebsiteDebugKotlin' (registered by plugin 'com.android.internal.application').	origin/signal-8.20.4:present; origin/upstream-stable:absent; origin/upstream-main:absent; origin/main:present
pr3	main-activity-transition-specs	a4dffbf5495149e65070cdaa098bb262bb4de30e	c6c8afdae730c0d61798a86914119373b6acad6e	app/src/main/java/org/thoughtcrime/securesms/MainActivity.kt	f2480e01ff66ca9fdbcafeab94f9da78917af71c	b66bfdb0d1545eb6aeafcdf5c89224d7878db0ae09da79453f500b76af635b8e	import org.signal.core.ui.navigation.TransitionSpecs	agent-heavy -- ./gradlew --offline --no-daemon --no-parallel --no-configuration-cache --max-workers=1 --dependency-verification=strict --init-script /var/home/x/agent-scratch/mollyim-android/pr-stack/worktree/tools/patches/test-worker-heap.init.gradle -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx5g -Xms256m -XX:MaxMetaspaceSize=1g -Dcom.android.tools.r8.threadCount=1 -XX:+UnlockExperimentalVMOptions -XX:hashCode=3' --no-build-cache :app:compileProdWebsiteDebugKotlin	:app:compileProdWebsiteDebugKotlin	0	True		PASS		origin/signal-8.20.4:present; origin/upstream-stable:absent; origin/upstream-main:absent; origin/main:present
pr3	unified-push-settings-fragment-subscribe	d9bb87c0bc0b049a8ca37285669fde5d7f2fe389	c075071378b3254367011d37a95b63ab96ef7e69	app/src/main/java/im/molly/unifiedpush/components/settings/app/notifications/UnifiedPushSettingsFragment.kt	f103365916f12ba3865e27949fb4163434c30c1e	24b1250afc462570635eb53ffd5c94d91cabe01ade719de548cd9af78bf5a617	import org.greenrobot.eventbus.Subscribe	agent-heavy -- ./gradlew --offline --no-daemon --no-parallel --no-configuration-cache --max-workers=1 --dependency-verification=strict --init-script /var/home/x/agent-scratch/mollyim-android/pr-stack/worktree/tools/patches/test-worker-heap.init.gradle -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx5g -Xms256m -XX:MaxMetaspaceSize=1g -Dcom.android.tools.r8.threadCount=1 -XX:+UnlockExperimentalVMOptions -XX:hashCode=3' --no-build-cache :app:compileProdWebsiteDebugKotlin	:app:compileProdWebsiteDebugKotlin	1	True	:app:compileProdWebsiteDebugKotlin	FAIL	e: file:///var/home/x/agent-scratch/mollyim-android/pr-stack/preflight/release/app/src/main/java/im/molly/unifiedpush/components/settings/app/notifications/UnifiedPushSettingsFragment.kt:10:12 Unresolved reference 'greenrobot'. | > Task :app:compileProdWebsiteDebugKotlin FAILED | * What went wrong: | Execution failed for task ':app:compileProdWebsiteDebugKotlin' (registered by plugin 'com.android.internal.application').	origin/signal-8.20.4:present; origin/upstream-stable:blob unavailable; origin/upstream-main:blob unavailable; origin/main:present
```

For every Java-requested individual case, the app Kotlin compiler also executed as a dependency, followed by the requested app Java compiler. Every Kotlin-requested case executed the app Kotlin compiler; the six failing rows failed in that compiler.

### Combined compiler gate records

These two later combined checks are separate from the 26 individual commands. PR3 used candidate commit `ee8e73ad9fb6395c448d50635e60467b4f5ee152` and tree `dbfcd0cb03ec7ee164da222069249076819590c0`; PR4 used candidate commit `9e126af8352f742c040b83a0dd9aac0d8e606c8a` and tree `da0dd13a3715105c65119054a26ab7a8a294d4f6`. Both commands returned 0, and both requested app Kotlin and Java compiler tasks executed. PR3 completed in 3m18s and PR4 in 3m02s. Each used the following exact command from the corresponding source checkout, with `JAVA_HOME=/var/home/x/.local/opt/jdk-21.0.13+9`, its `bin` on `PATH`, and `ANDROID_HOME=/var/home/x/Android/Sdk`:

```sh
agent-heavy -- ./gradlew --offline --no-daemon --no-parallel --no-configuration-cache --no-build-cache --max-workers=1 --dependency-verification=strict --init-script /var/home/x/agent-scratch/mollyim-android/pr-stack/worktree/tools/patches/test-worker-heap.init.gradle -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx5g -Xms256m -XX:MaxMetaspaceSize=1g -Dcom.android.tools.r8.threadCount=1 -XX:+UnlockExperimentalVMOptions -XX:hashCode=3' :app:compileProdWebsiteDebugKotlin :app:compileProdWebsiteDebugJavaWithJavac
```

## Historical annotations for replaced locale hunks

These annotations describe hunks in the previous PR3 source tree; generated locale output changes the current hunks, so the old measurements do not prove the new hunks. The historical records are retained verbatim below.

```json
{
  "source_tree": "a88d1e244cda745b090bf10fd12235222c8fa224",
  "annotations": {
    "0056feb01fbb2b28b267": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob d802bb39878c40f8ee021592af5cdd94781a205d; inverse fragment SHA-256 a3926e09ebb421592be30ae838f621bb713a5edfb3bd6b693d3e21b77fa22fb2, reverted blob 8c1cab07b8b9d7c620629cbbbb1ac8e3655a5faf",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "04d7877749ce229a351e": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 63b2428954aedd946423a0ca62c3e9cf15823770; inverse fragment SHA-256 d5c21e244362d9ca6079df595076a72e46977da9d6350069a9882c47e0d602cf, reverted blob db25e19dc8752e3596aa3fec52c9806fe0ba1fbb",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "0553fd9230117bd166c5": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 76647750dabb808c18360c59c8f9c333caf81800; inverse fragment SHA-256 298903a698f329e6d86a89a86938843e8c853fc9f7aec96341ec7f2e0abc5b4c, reverted blob 5f3a664b52a8dd561a64e272de0237fe1e288e43",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "055b8ff2bdc75e21ab82": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob e8beb6e58f2b2002cdb8a1aef68e8697d90f067e; inverse fragment SHA-256 b7e7d3e9727b78febf1f065627c1fba95e51a8928f940d1681630eeb5050c6c4, reverted blob 845ba2a12e65ca7e3d1bece07d531d5673f73320",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "0658db093c2a369c3c07": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 5259e905089ce3384f4810c79ac37be7e3ef2209; inverse fragment SHA-256 948c8b6c4905a781a405f2e21939d020c47debc6eb86288f48d6351c8aed2584, reverted blob 17d4ba4f1b09d8ce71c865908cacd3a6d6fb8601",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "09d8c4bd8e72a4ab4de3": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 269b2bdc186bf0e350e04c737c10e5fab201eef8; inverse fragment SHA-256 c7f49358d44fee379886a812803b1b4468fab51b7826ec4650203647a701ff19, reverted blob a06f8c0862d3560c28d957adbe6fc9d373d61d00",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "0aaad7f995aee6216a53": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob fcacf3a22625697428bd5763285a039a3de615bd; inverse fragment SHA-256 25792aeb0e6abc935e3ee9b7b81898d350cbef1f9cc1efd59ad1f536063b7da0, reverted blob 0bcd2e7754d56284e1af324e3b34ff816eb720d6",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "0c3effa12dbd98c8991e": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 82abde1c9f9cfe9acb4122c90e5d0177d25a622c; inverse fragment SHA-256 556385cde3eb47094b08f7174bd072fa997a648c7ef47fa2229e26c3720473bf, reverted blob 5c0ebf1a9ea838f2609b64ab09a7573ea296f0df",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "0dd9f95a3b26fdc04d09": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 005bdce197cba3f7181ab311a9351e99dd00b9c3; inverse fragment SHA-256 4e904c340d69ccdb6ed7c6f5c57ac9a094102cc42aa5f7c21b3c5278440d29f8, reverted blob afa135da82dd9e5e82b5e1d9d1feb9f7e101bb6b",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "0ef6b96ddb2b6a12d036": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile; minimal please_wait resource-table compile, link and dump",
      "gate_input": "PR4 source tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd; PR3 identical file blob and hunk; inverse fragment SHA-256 ea11203ded21c166902c37b0f70b68a86663f773eb7c7cadfddedc51595c0a6f, reverted blob c6b95808ca3a3a9ce7be0093637b00a172d81509",
      "outcome": "baseline and inverse compile exit 0; minimal probe links, but inverse loses the old Molly locale entry",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-revert-cohort.tsv and locale-revert-cohort.patch; locale-resource-probe.md"
    },
    "125d284155dd4fd93662": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 2d4ad2e00f6687271438a0bfabec455b1845c142; inverse fragment SHA-256 cc2840772c13e74de03fb60b8d009ed32b9566331ceb6199ba6647f8fe925708, reverted blob f5db0e797753dcd715b64ce06da244186824ccb6",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "165fe3e31845fc26d03f": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 5c90fabce0906274dc0c2cd64c9f4195cbd8ab31; inverse fragment SHA-256 ffdc26bf0b3caad601e8796ac2fef0112ecb4ec9661ce627af7d3893227e5009, reverted blob 29a1a783240694b5f276effe472f1b83ea0bb85e",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "1aad4231d7a1949777c9": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 937562767cce4a4de915762487f20977cd2c7b66; inverse fragment SHA-256 c18fcac1867922cc67191598cde1ad9d925a34e109b93d1470f69e7de58901c9, reverted blob a9b3a3bc9fdba52f0dcb1834fee6cc1e64a2dfa3",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "1b0c2e283e4bcbf45694": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile; minimal please_wait resource-table compile, link and dump",
      "gate_input": "PR4 source tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd; PR3 identical file blob and hunk; inverse fragment SHA-256 b8101c713c6a157c7145ccb472f380e3c88c60b45a0b5fe03570cdfb97951ec2, reverted blob b23d455007145d0eb75c6c462eb1ff5cfd5c9eb8",
      "outcome": "baseline and inverse compile exit 0; minimal probe links, but inverse loses the old Molly locale entry",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-revert-cohort.tsv and locale-revert-cohort.patch; locale-resource-probe.md"
    },
    "1ce2c0db43335218eb90": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob ba9d7ea2bfe4fc8d7fc499aa7486a5fe6260db90; inverse fragment SHA-256 e96c64a4be40f84ca28f30f6dae2a7a7041a5e0c9d46320914808c162c2ebe32, reverted blob 37cb66db79dcf7828c86079d055fc67ea27cc74b",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "1d63b0306acf8919d0ab": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob cb1198e438707a48b35133819499cc57c3ce96e2; inverse fragment SHA-256 0a1397b27845028598dc813af63c79e257f1a15cd280dddc9f911b54801ba2e9, reverted blob eb77ccfe413d31a27cbec608edef1e81d4781d28",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "20d1686920d1309781ae": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob c12955ca5087a632e940279e9ac4d1b247462681; inverse fragment SHA-256 8261850fa817f40e00161e8ff2b707e920208c2050d8af17d2a3dd7b0303a94c, reverted blob a8d547bc8bc8486b430dc416d9fa786368887ac1",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "2512e063a4bcd8515d19": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 384d234898a56314740ffcf963e0a1aedb212820; inverse fragment SHA-256 b250e79d64749b2d4dd21885ed390d90433818e6e4599aebc992ebe55bcaae9a, reverted blob d08b65efa7cc25496b2066564ada36c2132721db",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "26a3b126d41c1227168e": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile",
      "gate_input": "PR4 source tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd; PR3 identical file blob and hunk; inverse fragment SHA-256 cfd50e876ef4884e5e53a8456c6c27a268a3fe3174b882e7b1c2d1415140b73e, reverted blob b0c1601bdf5a44522d901c64b59c14920fae368b",
      "outcome": "baseline compile exit 0; inverse file compile exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-revert-cohort.tsv and locale-revert-cohort.patch"
    },
    "26f173b2506c685b1dd2": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob d5d09b0a2771973d66f832c21bd28cbce835eb50; inverse fragment SHA-256 3644d488b200d0b1665c633d19e4c9d8a086147f0d8bd898398df5f4321b561b, reverted blob 5bfd1ff7b43017184fe1d9379db9c3140359fbfc",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "273678d268b9f9b9d434": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 3c4c607c8226a303a3dcd9cfdd901dd439842c5e; inverse fragment SHA-256 3b7563eced4e3bb8d82648ee1a9faed1001cdde060fef7f8e5d2255a477f11aa, reverted blob 7484280b5112708412b207de9e0093fbec4c9d33",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "2774b4432907ba73228f": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 03d8d0b1ef8d597ab9090a040e328fc22b24bed0; inverse fragment SHA-256 fd3ad911edf3c1d82639f751610f5ca2c44709972b8e19f4ba6c37383a6e9998, reverted blob e607fa9ff07b98dbb599abd2159b223896e79a83",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "27750b9d7d8f75b822f5": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 269b2bdc186bf0e350e04c737c10e5fab201eef8; inverse fragment SHA-256 9b1450ea06c98e91c6d3d3bdab20dd120dea38d89a93b27ec660a718595b1041, reverted blob 5f8304acde1d6485f3751fc8b730f18c230d2a03",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "27ae8175989f9283fc99": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 144211467a5e9247e9bb282e65d8f2d678c985b6; inverse fragment SHA-256 bb95da665f30cde3e8e22fa86f11f88c96b6aea860123c3b05ca598493f95f67, reverted blob 62e5480567158e0ce670e2bdf941f698b66d3343",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "29500e8c358447f60bf0": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 92324ea8b092100062093b55394bf2febfbdb05c; inverse fragment SHA-256 3dbf911a25c0bc13019aecd21d4c02ac6f014756fd4db03ca5602ff8ce68b89b, reverted blob 01fbf8bcbd51f92b9accc9722bf76f9b21c39cbd",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "2a094a856601fab248e4": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 86f72802f1768ed4c64254754e9da6fc1ef57eb0; inverse fragment SHA-256 e622847941ff66764b087ed636001b7569425f6907df0fd0417d913b5db3cd86, reverted blob 217a2ccbb66397c932821af0fdf130f7948cb147",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "2ec3c157330aef0b10bd": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 91916a86bfcd4a113b7cc3787dc659de5c543760; inverse fragment SHA-256 9cd49ba848bb872055e5c763da8b5e77352da617e839d04882617973257afdfa, reverted blob b93062a6384b517f94a9a6266453c5054ee1af93",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "2ff5d9e50d3f089e5bc9": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 570eed87d4b76feb98bf7604f91a388ab2b25c1c; inverse fragment SHA-256 0a49e4d4706014db55e507dabf9351ed699002c02c500416bbbd128d1e99658c, reverted blob b665f3a92a40bf791b06a56158aa39444891457d",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "3232f1c2b2465acf0e7f": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 544918c62e69bc1dce1562c08dc20ba48a2d1fac; inverse fragment SHA-256 536c5c2c326cecca32bc5e9acf6ef51b4d062cb3989832d0f642c4c833039bf2, reverted blob 0e8d8dc132c325ce645df9d1d284d8d33ee6c993",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "335aa1a1dcb1b35e0dc3": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 37118c0ef690252add04b1330b5f870e5d23db91; inverse fragment SHA-256 c1f2509a40ad86c649e6163ae8968435b43e100f827b4067523dc6d0f8df1274, reverted blob decf2de3de3a55789697288d39f9c773fdd22bdd",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "36c6917db4dc5f73b7fe": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 8f97a9d14bcb00b36c176e7e5ed3e5cacc06e6ea; inverse fragment SHA-256 dd4f49f4db52d5987d54c697fd1c4a1f60adbb9a76ac86638de5139b5018829d, reverted blob a12b0d6b7c568781b6171f80dc52541bfd26ab0e",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "36f98223dd1e03d0bf77": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob b1f6240754edb93ecab4a6ef578424012bd72657; inverse fragment SHA-256 6293b9c07c7d5063e6f53fc4f0d52d3595e8b496430bc31fa13835c74e83112a, reverted blob e667be9a7cf98628625e63c67cdefb15c3bf3f60",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "3960bc6f713fd4f583c9": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile; minimal please_wait resource-table compile, link and dump",
      "gate_input": "PR4 source tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd; PR3 identical file blob and hunk; inverse fragment SHA-256 afa8f9e019b4979cdcd0148fb0d3f50d439037da25978966b087a5a8175e237e, reverted blob 2be693482726e78a56e036e74662dc4d4f958442",
      "outcome": "baseline and inverse compile exit 0; minimal probe links, but inverse loses the old Molly locale entry",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-cohort-two-cohort.tsv and locale-cohort-two-cohort.patch; locale-cohort-two-readme.md and locale-cohort-two-resource.log"
    },
    "3a459134084fac71a23d": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob e9e3204fcfb81495194e522b73f23ad061a8aef9; inverse fragment SHA-256 353d42fceca50346246e9d5ec76fedda79986385212c59f8ca8c6c3b19f48715, reverted blob e2f1a93360e5c53d17acf6f72627aec4652d6015",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "3c7ba584d61789c0a958": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 2d43fa6f100443e513224b4f313d1fedac567520; inverse fragment SHA-256 1189edc21f2af7088622050543ad081e2243c2b1bd1ef37dbdd647067b05207a, reverted blob f6c31d02bdeb1418d111e06cb804fdb97c6f8621",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "42e00ba35d0d21fa0fce": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 76647750dabb808c18360c59c8f9c333caf81800; inverse fragment SHA-256 2483400a6bedd40ae60e4c8d8c5e6fdb0e67964fdcb5a0edd28f3234c0be2fec, reverted blob 39f5713d7cbf7d61b374484a62929fe598552b6f",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "43040b3b924f19263686": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 4868bdfe59e80149068c0054da1f61a1bbb3bace; inverse fragment SHA-256 aeb43c1e7ed0ebf7c93a01d3a4133e666775715b4f377cf2f21db80af8d96b4a, reverted blob 09cc2d2f0d0b73fef8f0c685d0084f5a37b85a5e",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "449fc44366e309c842dc": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 13a078b288847ea6cf233ea6956ca5427e965659; inverse fragment SHA-256 e400e453e36d2da88038684216d8a7d3ad539de5d6e91d256012ef7f57c83157, reverted blob 5ae26747a70e9e087eb89b809f7a2671be92d3b3",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "45a524f407bd83e8f381": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile",
      "gate_input": "PR4 source tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd; PR3 identical file blob and hunk; inverse fragment SHA-256 12c194663098fd98b78757fdc221984b9668b0d949947e63ea761c958c47c1e4, reverted blob 6a37db1fa65b3729c9f83e325f00a2077cdf9f13",
      "outcome": "baseline compile exit 0; inverse file compile exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-revert-cohort.tsv and locale-revert-cohort.patch"
    },
    "47c1df2bae06f5c015f8": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 144211467a5e9247e9bb282e65d8f2d678c985b6; inverse fragment SHA-256 7816515e601e281257ce0c867903f6fb64afb738c16385f4eb8f2b5a1e9ec203, reverted blob 781d1bf39d8fe7762d22cea3afd4b9d3cb8e5e92",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "48a936829af37db386f6": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 98e162c37b9875b0dd07feba14096b7a9aad8ce5; inverse fragment SHA-256 412a560aed485d3365c7c401667a39bb3d7828bbb6ad59a09e80bb8fb5d0a73d, reverted blob cb4c207ec7d482c03d60d82338e80cbb56985a42",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "4943a9063702a7004583": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 80f74e765c5258b6368001f6273f29f388419cce; inverse fragment SHA-256 5d5de245f5d8bf365989abcd890c75c97a895b0e3072d89be5b6e4d9fae82f2e, reverted blob 84da10b8fc5a84cd80b7fe8d362c4441a4788e8c",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "4b9bd21aa6622022f025": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob cf0813f1eb5fcadca6f77ec14f79ae4140563f4d; inverse fragment SHA-256 1465215d01d5e94483181c18f126823917f30a94e447d2dff6e3b8756d4e5573, reverted blob 3eb773cf601b6109717c06afb587f85341949b3e",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "4e5622f062f04b0fd380": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 544918c62e69bc1dce1562c08dc20ba48a2d1fac; inverse fragment SHA-256 61275388307b3e1fd825e36e612c19384df2058c5e6fdae786d6f9afba542423, reverted blob 4a651b36ebaed3dffb0e0fa830b7d44d2cd28a31",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "4f9c3af9c3c25445405c": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile; minimal please_wait resource-table compile, link and dump",
      "gate_input": "PR4 source tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd; PR3 identical file blob and hunk; inverse fragment SHA-256 d65784f9b2f6fb01d7e1baf00db4737bea768a196b92b99b3281ed9f2f96f53d, reverted blob 7e8f18a55c7cd6446acc6a82c77889ffc73cc5f8",
      "outcome": "baseline and inverse compile exit 0; minimal probe links, but inverse loses the old Molly locale entry",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-revert-cohort.tsv and locale-revert-cohort.patch; locale-resource-probe.md"
    },
    "51cbaadef925c20776ee": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 8f97a9d14bcb00b36c176e7e5ed3e5cacc06e6ea; inverse fragment SHA-256 b0e7171ca425e22ce6dafa3e89308fed8dc2712cbb9aca1cf919f06943beef0d, reverted blob 8d66fbf4495daacc94dcfb068b0c30657f6efa31",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "5234164af222129d89aa": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile; minimal please_wait resource-table compile, link and dump",
      "gate_input": "PR4 source tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd; PR3 identical file blob and hunk; inverse fragment SHA-256 81c736308f70237ae32353e289a1366a5cf06623e251a17ccbc0a260e293a884, reverted blob ae4e9444c7118edd528b4495197749cc58ad709e",
      "outcome": "baseline and inverse compile exit 0; minimal probe links, but inverse loses the old Molly locale entry",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-cohort-two-cohort.tsv and locale-cohort-two-cohort.patch; locale-cohort-two-readme.md and locale-cohort-two-resource.log"
    },
    "54f84ad6ec1ebea21bdd": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 688049e2ce13d8dcebd35d32999f64b253559ac2; inverse fragment SHA-256 2c64f2640fad336fb76b32273015ce78d02381383fd5999ebef24c87bde48f15, reverted blob c33904c1590065a3468685488498e1a00d0f3e39",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "551357f0d6bf9bdf68e9": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob f48306bbf0d1b5007ee0673c6b2c3a26ebcae79a; inverse fragment SHA-256 65772ff1ac412cc36cb08efb87e647bde8b9975ca0d8bd8c1eaf372796c28f10, reverted blob ba9d69874631f4e34d791cfb70e743c22090f87e",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "55f91abafbab90cecf00": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 2c538873421441763af319ce738849dd1a2e3084; inverse fragment SHA-256 08004c9381648c14919a0b85d6c44404f2498436528e9402a2d250742dd4c39d, reverted blob ae8087c737941f58902b30f6051bcfd2e499bba4",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "591f99e966870caa563d": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile",
      "gate_input": "PR4 source tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd; PR3 identical file blob and hunk; inverse fragment SHA-256 8e067e2c60df4953bd3e996fdb7415db284de1ef40614f0e6597daa3866ad7e8, reverted blob d10c0ca57d00edc072d3618530709c2c81a1921b",
      "outcome": "baseline compile exit 0; inverse file compile exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-cohort-two-cohort.tsv and locale-cohort-two-cohort.patch"
    },
    "5a836f26f1e5f6086cf5": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile",
      "gate_input": "PR4 source tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd; PR3 identical file blob and hunk; inverse fragment SHA-256 980d3e12817550f297879d8043fa2c45a6cf85bf58d1a70bbfbfd25c5b8c701c, reverted blob b1f3fbb96a37514d760f0e3fcacc8c5dabd4cffc",
      "outcome": "baseline compile exit 0; inverse file compile exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-revert-cohort.tsv and locale-revert-cohort.patch"
    },
    "5baf9e7a17956a1a10c2": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 3db127522b3abd9e78cf473dfbd06e5e45ba493e; inverse fragment SHA-256 8cda45f6fed43095f4ce1e5dbbd507bcb9f0e5673d84d02ebd7b369d8c9c46ae, reverted blob 4f0f01fd817d1ecbf9ee45c8a167b8a4cfed9999",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "5e33d38563e47c988995": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 226e4b219aa97621099ff4c72dae427370e9a98b; inverse fragment SHA-256 fdc10d0e91ad1a2646c47bf91f5e5e68d06b47d34f8b2cd793f876ace849d906, reverted blob 28b80f4105a1bec07e747c3c8d73d0c359a3c4a6",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "5e6a9a7600265c8fb69b": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 80f74e765c5258b6368001f6273f29f388419cce; inverse fragment SHA-256 a9956e9261ac026e3c1f927f62ad8fd364cb6d27230f36179b86836ba87180f2, reverted blob cd153a6eb044b2246256027dfb3d73dd33eca6b4",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "610b87319d5fed232134": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile",
      "gate_input": "PR4 source tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd; PR3 identical file blob and hunk; inverse fragment SHA-256 4e732609bd23ccd5b9b4f81fc43e3a7b2e8675e737f9dbf0b3375bb4048ad968, reverted blob 6e2711cb109351e4a289f8dd55a00680270bce09",
      "outcome": "baseline compile exit 0; inverse file compile exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-revert-cohort.tsv and locale-revert-cohort.patch"
    },
    "6160367579f427a96913": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 3db127522b3abd9e78cf473dfbd06e5e45ba493e; inverse fragment SHA-256 e4b8b9a8ae80799aa3b561e39232a6bd318f33c467c57189d51da63745f49c56, reverted blob a266af08a3bb67063e5a4157b7c46bf9dde65ff8",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "62a31e4696206f426c51": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob d802bb39878c40f8ee021592af5cdd94781a205d; inverse fragment SHA-256 ddb2e5d747dcb00c5d9ca6bc7f521781b17699180fa4b8d80ea831df8b8b2242, reverted blob a647ea15c685451a726da29424754b4044662933",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "6552a12dcf1470fc34bf": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 2c538873421441763af319ce738849dd1a2e3084; inverse fragment SHA-256 caafda87d6369364e13c9f9858dbf25fe0d586125f8d2e5a02c131ded203c39b, reverted blob 45441dd113fec29e5324073708be7807d0b2a467",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "6631dab5fc59927c6cc7": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 1d9b565274a425ffc1890f8d3a42e3108457d453; inverse fragment SHA-256 29a1ae0d762236ea93abba5b67f79a0fa99de384b3f514f88e9a2f7494b28abd, reverted blob f687f632422d891010a1779af51d76f9ab1cc7e8",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "665943dbbc33d171d55d": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 226e4b219aa97621099ff4c72dae427370e9a98b; inverse fragment SHA-256 4689013b54753f3c076e502aba00fcad0df0b57bbc8b2344a47b1227b13b135e, reverted blob 32de48f1200d1e932d4e51c21b5ff2d52340b4a5",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "6bb6caa504bda4e4e906": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 005bdce197cba3f7181ab311a9351e99dd00b9c3; inverse fragment SHA-256 c0e357f0d78db31bff616a5926e24ad59846ce0102f284e2fc66c8a4ca6c24dd, reverted blob f27ed3de8366d950de71269cf6f45fa810e1e96e",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "6be022290e27010001df": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob cb1198e438707a48b35133819499cc57c3ce96e2; inverse fragment SHA-256 e83f41c8170ea4a02a299e68f5d0f27754da1d051d5f16e259f05a6fdb0a194c, reverted blob 6a0d07df0fb9861fe2efa2e8bc1af816697fc7f9",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "6fb27f612fb14269b688": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile",
      "gate_input": "PR4 source tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd; PR3 identical file blob and hunk; inverse fragment SHA-256 1e48ee5ae938c5235b950a9047acd5afb0d86eec3747490893566a19ef06d662, reverted blob 1a94d2cc479e17f1701a2372a8b4d1e111ad27ae",
      "outcome": "baseline compile exit 0; inverse file compile exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-cohort-two-cohort.tsv and locale-cohort-two-cohort.patch"
    },
    "708ca7d6886f90b5a95f": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 5c90fabce0906274dc0c2cd64c9f4195cbd8ab31; inverse fragment SHA-256 1937c42a7c9e6dfd90984210c7f4aa3bd2ca8e4e0582d531e5b37cf6ff71e765, reverted blob 5cf4f60ba1d3951d34c612a7ef47226864e4b995",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "74bc7aa72c0f6c84cbff": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 51db2996c85a41d8f3cc8613d822eca91e50dd4e; inverse fragment SHA-256 0b300e566b42e8cb8dbf867fbcb67150d7e8f8b89f0ff72faf2f344f2580f9aa, reverted blob 479ac8ce79e4226a7c7c7cdd45531b292a3c1207",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "75eef1c7ac71d77900c0": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 530de8c2ee2617e266ebdb63451bb444994e310b; inverse fragment SHA-256 ff609b049085504197aaa7c0634d0190aaddfd2c3014044ba3bc87115a2d638d, reverted blob 9ee8f03992f0c9c99a456050bd8278ffd2200bb3",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "7658f6dad33356af690e": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 8616a82b791a342b567761636471a16af14983f1; inverse fragment SHA-256 9409e619c82bf6809f0f1a764d65f6f04c81ad51a5ad52a9d4193eb3f9ff5327, reverted blob 346f28c8e8d71e97731a3fac729f72b99c34e186",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "770f02bf2c0a3a419b64": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 570eed87d4b76feb98bf7604f91a388ab2b25c1c; inverse fragment SHA-256 e4811e9ecd488a3bf35382ce16d39b961f8c32a279bca63de5eeca4c758ed656, reverted blob 07874a77d0b823599fe1b28175d1d59002ac88f3",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "799f539808dd87c116e1": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 91916a86bfcd4a113b7cc3787dc659de5c543760; inverse fragment SHA-256 594ee5d2ab546f6aa7692b69461e10114b0750a2e140e1d417001926b4baa2dd, reverted blob af357a860d959486615a557ce6c18c3f37199f1d",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "7aae8de4da9946dd2c48": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob ba0a41246e06c588fed6d358626d40af66cb5f97; inverse fragment SHA-256 bc15e8ee815cd5e70c1b9ea355d34a783ca217087cde9b09e0d83ceb5362dd1d, reverted blob f27ea31b24bf66cf9c61ef93882c5c3772df417c",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "7b90e531fac1be99056f": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob f48306bbf0d1b5007ee0673c6b2c3a26ebcae79a; inverse fragment SHA-256 ac9f01d76105165957e496a2872eb207c8a24ee36cb4505e24257ebfecadee38, reverted blob 0662a0a2e5365e52014a8cff45842447ae693994",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "7bc861d77a50acc7856e": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile; minimal please_wait resource-table compile, link and dump",
      "gate_input": "PR4 source tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd; PR3 identical file blob and hunk; inverse fragment SHA-256 f4a3d51a1de6b9cd5d6def7cf783ff4218300b04d2425def4fd0cfee3e4c4d4e, reverted blob caba8ed604a7e3f4a654c69bcd9a26d106fbb0ee",
      "outcome": "baseline and inverse compile exit 0; minimal probe links, but inverse loses the old Molly locale entry",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-cohort-two-cohort.tsv and locale-cohort-two-cohort.patch; locale-cohort-two-readme.md and locale-cohort-two-resource.log"
    },
    "7e70dc24067e2fedbe07": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob cf0813f1eb5fcadca6f77ec14f79ae4140563f4d; inverse fragment SHA-256 42b567348d35e01f9f82fcc7cee86f6a77a109c3d31f86403e88738918e15def, reverted blob e884570ab657d5a7af20b862936eb96a281a6afe",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "8080b27cf35752b109de": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile; minimal please_wait resource-table compile, link and dump",
      "gate_input": "PR4 source tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd; PR3 identical file blob and hunk; inverse fragment SHA-256 f722dfffe5a69050db816822698c475da06381815e398c8954ca1b58e288b9a3, reverted blob 7a807b2c92abac2780ae9033ca292e31f3b1d2c5",
      "outcome": "baseline and inverse compile exit 0; minimal probe links, but inverse loses the old Molly locale entry",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-revert-cohort.tsv and locale-revert-cohort.patch; locale-resource-probe.md"
    },
    "81e77b6a6a343a361755": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 75f19461c318b210bddad54d620788f42f0e157d; inverse fragment SHA-256 7041b5fb971403dda5ed2af71dd6ad6750ece9ad76c14b479714089813027d81, reverted blob 0b848b03f264567f593ec1789f8f3b6e19b93b0d",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "867970f3de06dcba66fb": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob e8beb6e58f2b2002cdb8a1aef68e8697d90f067e; inverse fragment SHA-256 61d52d600dcce46783ac32bc104e067e11f21ed16c0ae50b1c12969a6e607ca1, reverted blob 344fbcc283aec85094c75006ce685632362ef547",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "88cbe607d29195391932": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 98e162c37b9875b0dd07feba14096b7a9aad8ce5; inverse fragment SHA-256 7351707d5bb8b63d50358e45a61df8d8dd51b201de3baa2af132f8c8e192015e, reverted blob 453771c6c29655e72a42fe14ca4b178f0f65c817",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "88d6d029e71de6894166": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 530de8c2ee2617e266ebdb63451bb444994e310b; inverse fragment SHA-256 8eed8c67460527450dc69ce7ee9df260a17ae395697b5fccef4a8f74b2fb5d61, reverted blob 1361904afc48667a92ea9633d3cc44bdd4af8b3b",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "8956ef3a832c1ed9aeea": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 5c66c93be64efc82b29e11fc7a6d5d92e84daa6a; inverse fragment SHA-256 b8dae6682f5a09d0accd84a9868f3e40d04a74bda54affe73bd9c524d56ea0b1, reverted blob 693b9957c5e34daa51ea17a6ab8d8ec49ff2bc68",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "8a02dfcef1a6106b81b1": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob fe271b2d7c9b9f9b756f9c1e74b596bd4254f412; inverse fragment SHA-256 597ebf41b9be1e2202a5d7fcdf5cd3bf9f195cadd9139bcce33286b66af4a006, reverted blob 1abf74e5aa57d9fae50f1cf6e6197fdfdd453ace",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "8c803fc214a5484f84b3": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob ae469101ad679f742ec87c42765136432b84a72c; inverse fragment SHA-256 3b6f924ed1b84e56de680764081de1d279ad58c32b272cb10d579fe37aa6f67f, reverted blob cd52ac5944651b9be0425cc832f71ded67a1fb99",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "916b2809d199f1998cd3": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 384d234898a56314740ffcf963e0a1aedb212820; inverse fragment SHA-256 14ba47565afe634486f9d20daa403e0488f2b619ce4bc2acd5c34240d6342ba7, reverted blob 69fcb99c3357508ba7f475f0ac22a773716ae095",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "95adda1ec37fb5f169ff": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob fcacf3a22625697428bd5763285a039a3de615bd; inverse fragment SHA-256 868b86fc6ddc7232192845685a8bd72a9fc38866ae19a5df27d606c45ee12087, reverted blob 35e89f703cda85973520eae4860c34efbfb62bf8",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "9613d2af95367184ec6c": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 8616a82b791a342b567761636471a16af14983f1; inverse fragment SHA-256 8299a48fafe362ec7b1d5005e650fb1bcc06a433e56e0971934aa525733931f5, reverted blob 102431e9564b46d3b930c3de04b2e96bba1f8a1d",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "993bc52b36eeb1cc9c6c": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 3c4c607c8226a303a3dcd9cfdd901dd439842c5e; inverse fragment SHA-256 94f8085d6e9dff5f8c667e752058f71d160e81479458008166d90a6f26de2b5d, reverted blob 9dd04956a5d425c34b80ba6357ba5e71802ce682",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "9b4f8e1a7fcb40951dae": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 955f2b3e45dabe53bc6a0ecbb6d24eb1603db2b5; inverse fragment SHA-256 3e936df8a74342626311fea51114cff677036699b672a0d8d722b6d4d4be5fce, reverted blob 6a1c69637ea88007f0dcddbdfe7e8c6be4143fc0",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "9c58d84f31d46a7fda1e": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob b1f6240754edb93ecab4a6ef578424012bd72657; inverse fragment SHA-256 ae000f2dbd138b0a149f295486c2bcedb779a1dcb55f5c45097a1a81b3d6066f, reverted blob 788fb8d5111a3ea6dbc0cb290cb81c89f51a3654",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "9d523a44cd5788113d16": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 688049e2ce13d8dcebd35d32999f64b253559ac2; inverse fragment SHA-256 17dcb876e13cc6fa648850a62a4ddb41d1d9b53bc1d1ee403db0c041eb4bdbf5, reverted blob 7c7e7496218504453e948dc6c371723b08e179cf",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "9e71bfca6602011e73c9": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile; minimal please_wait resource-table compile, link and dump",
      "gate_input": "PR4 source tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd; PR3 identical file blob and hunk; inverse fragment SHA-256 8ee2ba92d4115ebf2d5ec7b70446d6a7e012916cf49e15d9af814634500827b1, reverted blob 2e4d764cf16c6aabbc699e04c7955cbab87c24b9",
      "outcome": "baseline and inverse compile exit 0; minimal probe links, but inverse loses the old Molly locale entry",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-cohort-two-cohort.tsv and locale-cohort-two-cohort.patch; locale-cohort-two-readme.md and locale-cohort-two-resource.log"
    },
    "a036a0693aba553128d3": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob aa7572a6400790ef8762623c2610270dca45a90f; inverse fragment SHA-256 273d485968399d97dfff551300dda67cb25886115a83d4d52de3b978f3b552b0, reverted blob 8d9fead63c04b67f7b42243c6f43a17eb202e9f5",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "a093da930d7ab1f77e67": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob c7d615c349f90c0dd0dcd40318ae6df4a5c7fc7b; inverse fragment SHA-256 5dc9916a0d34766d2f068b9a085af731fd45688c3db453f37ca9593779556ea9, reverted blob 354fe0a400e6957e2744b0f4d699ab7fcb8b5c05",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "a111f5f4e955e583a336": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob acbe9d798dc317303525e6079360524d518fb0c8; inverse fragment SHA-256 ce941e019b598f1d51ba78366be1a563179d56372d1a3f97f4bedba19795b5c5, reverted blob bac65507f88d6e11924d28154671d6c4c90fb40d",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "a201eb895f5b20a5f549": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile",
      "gate_input": "PR4 source tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd; PR3 identical file blob and hunk; inverse fragment SHA-256 00f00e9c7bc68b5326580a495b34938c78584081e7bb79d606fdd6e4b488d48e, reverted blob 66511c75d28d538d90ff7f3dcc3d898bd5782aff",
      "outcome": "baseline compile exit 0; inverse file compile exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-revert-cohort.tsv and locale-revert-cohort.patch"
    },
    "a5090b8267946964c05e": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob fe271b2d7c9b9f9b756f9c1e74b596bd4254f412; inverse fragment SHA-256 4cf8651e650f38a13ab15912ae37348be292ae8f42a17802c5f646b10e6b7fb3, reverted blob 996acb6927c54a3317cb715bd6e4e1739ba601a1",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "a655a87c6c60c5f3c749": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob e9e3204fcfb81495194e522b73f23ad061a8aef9; inverse fragment SHA-256 84274a0ffe0fdd02423afb9ac10ad01ced21ee8aa0f2ac64704f34ce716b41a7, reverted blob f45913e8c70cd8f33569179f8fe90900f2945f5b",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "a7cad578a2568c0ea67b": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 4e0919cfb5e81099add61abef189326118cd2818; inverse fragment SHA-256 07c9f735bcd00a5330be3e98130aeedbd3f3e61715a686e8b5e11db8ce071fa1, reverted blob 5683354ea98d9bc7339e80828d10f30e4e1acdbb",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "aa95e6ac555faf8d66ed": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob c7d87f7d05795cc954ccdce4d242548c6d8a9f39; inverse fragment SHA-256 7ce1fec726376528649ce243614333d28b0fae5c43b105b5cd4957cf7538c6f2, reverted blob d602f67ba7fab8fde9a33144a149e40ffb7f521a",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "abf663ecf129117f2270": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob b2c84ebb74de44da3108f2fc686f2258f1e2c290; inverse fragment SHA-256 9a44473663e58020e13516b6abb00cf0a6f5c0cc023bbe6770957d05c0a40c1f, reverted blob d535f8f8411a24b7c58fa35e5bd4942c573ffd5a",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "adf2a9c5cde787280bd7": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile",
      "gate_input": "PR4 source tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd; PR3 identical file blob and hunk; inverse fragment SHA-256 31cae62d38a631c38d87ffca55c60b33a8a85245df08bfc5b37392fa4256b81e, reverted blob 04f627ed5f8771bac01ff0e1c3282158edc9ec12",
      "outcome": "baseline compile exit 0; inverse file compile exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-cohort-two-cohort.tsv and locale-cohort-two-cohort.patch"
    },
    "af13ab58ae9c57798508": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 2d4ad2e00f6687271438a0bfabec455b1845c142; inverse fragment SHA-256 a9a49cb80f7af3d8f4bfa14f7e2722315b47aff907e336a87ed7ac5d2cb83ab6, reverted blob 0192b4a29c19d3b69675ebf57a700319a05908dc",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "b0a04c63f4e66e528e5b": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 5c66c93be64efc82b29e11fc7a6d5d92e84daa6a; inverse fragment SHA-256 a5b3ca138f863cd0225d6862fb07980a20d7616e55ae7c738c48dc1ecffdaab9, reverted blob cf8b9d92a8da252b6715fa4a15711901bbf6f9d9",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "b2349b4ce2984adf8380": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob aa7572a6400790ef8762623c2610270dca45a90f; inverse fragment SHA-256 5d9c71d203a532ad59ea35653d483bb8cb8b47a38bef40d2b6f5e94fdf3c1c07, reverted blob b8648925a3744a04f0c471162fb4fa648b671793",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "b29fc944d115cbf22047": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 8b3db188fd08dbdbdce76994a1eccb7618159d65; inverse fragment SHA-256 ddd7c9211defa7ead54d3e7a84b5d2cce39f2d55d3d66a538cbe7bf475e9329f, reverted blob eade076ef34badef5aa6d968640b56972445ba7c",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "b356b76b49e99b707d0d": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 2d43fa6f100443e513224b4f313d1fedac567520; inverse fragment SHA-256 ecbc7e22457aeeadc6fa403d834d51b369d48e421816ffab255c39fea00ed894, reverted blob 83a09282f5f19809e9aef614e830f4172fef639d",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "b44d5dd4be44c6e2bc42": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob b2c84ebb74de44da3108f2fc686f2258f1e2c290; inverse fragment SHA-256 499eb0a1f7287942103a6630959ccc6f9cdba28e3378ee6b61f5dbbcf5776b86, reverted blob 3788388a5c1db3da9a8a0e56e8bbbb7790a80a95",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "b7b3686917421fabc086": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob af7493450bb924a79885d6ea1406e2a31432cc82; inverse fragment SHA-256 3c0b389be8d75880b2f92db274381177c3175c5ec66894e1ee1a4322b1f9ca45, reverted blob 51011ffa8e9df6d5165e5e152307aae166912ee3",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "baa0a3815e0c75053ff8": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 82abde1c9f9cfe9acb4122c90e5d0177d25a622c; inverse fragment SHA-256 7efa4ccb2fdab9933b6290eda24cdc4d5ac9c65974385b4a88789398ebbaea75, reverted blob 55be02155d6396cba8997b698309b84cfc1cbb9f",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "bd475ba8bbd8936c5458": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob ae469101ad679f742ec87c42765136432b84a72c; inverse fragment SHA-256 9b0666ef2f687a1f797da3ce2c22214c53878e00db70ffe81ed0cd2aae58ee53, reverted blob 163e3630b53c7008c90bcfa9ac89da66e64895de",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "c404e0bf15aa887531ed": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 1d9b565274a425ffc1890f8d3a42e3108457d453; inverse fragment SHA-256 0a672e10dcbfa0158ebd8e5e145462f0629665a687f80d6c3182e975dfd71fc3, reverted blob f88d8c39901e8509e348013df9eb611492fdf9bb",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "c8cccfba343ea12471d3": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob c7d615c349f90c0dd0dcd40318ae6df4a5c7fc7b; inverse fragment SHA-256 c53d45f523abe8d4c5eb6aabc983f33609e97cd9287b4dc1d64c4e346eda7282, reverted blob 56ee70a868337f54faa50dba9a2bf084197bbc1b",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "cfde56a33b147116f1b9": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob af7493450bb924a79885d6ea1406e2a31432cc82; inverse fragment SHA-256 322a856ea44433ea9c8dbf8b3f5aed8519690f12982c147c18e0546e8a84586b, reverted blob c75032fb31135890d8679c0056b00e36dd1ba4a8",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "d0892322247e07b66744": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 03d8d0b1ef8d597ab9090a040e328fc22b24bed0; inverse fragment SHA-256 90a53dfb80bcffbfeeefcd37d933277d997ac57000aa4c2ad53f9db43a336fe6, reverted blob 9892c6eaafa36d38a792c7c3c986b289786f3e80",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "d22ebbbc082029787b44": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile",
      "gate_input": "PR4 source tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd; PR3 identical file blob and hunk; inverse fragment SHA-256 bf6eae7d1247d712e6bf0aad89b6968e07f9987f3ac48234072b8e30d8e3abcd, reverted blob d0e00e95fc67212a1ab19ac1d6140a7efa70919a",
      "outcome": "baseline compile exit 0; inverse file compile exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-cohort-two-cohort.tsv and locale-cohort-two-cohort.patch"
    },
    "d43d693b6e60505ae3b0": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 63b2428954aedd946423a0ca62c3e9cf15823770; inverse fragment SHA-256 6982498ec83a593c8e290935a9f5cc758eb7c3578827db46a95ac557d23b6848, reverted blob ffecbb0250d2f3ba20265d6a906cc3b8b2100df0",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "d763d4772536d02a5b00": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 937562767cce4a4de915762487f20977cd2c7b66; inverse fragment SHA-256 41d8f7281995545ae04b2f01818832c88095a0abdcb593b8b5f8b8993c0b1dbd, reverted blob 5c945fee2f29ffe87ae13c7f04ee661bdc0128be",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "de0bd17666760259439a": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob c12955ca5087a632e940279e9ac4d1b247462681; inverse fragment SHA-256 b6c05427a1216d7391d7fd60250ef07f8a49a261a64a566ca6c3b0e37d87e29f, reverted blob 179739c1b12bcb1e6877b752192e0c550d93461e",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "deb7e97308692b75d4fd": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob c7d87f7d05795cc954ccdce4d242548c6d8a9f39; inverse fragment SHA-256 f9390b80132d70e8cb305938808b475f533af755cbe1e7727b0ba0784fdf4473, reverted blob c537b3d868890fc939101d3e073ffbeb0d1f0b27",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "e04cf3531e494d17fc08": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob ba9d7ea2bfe4fc8d7fc499aa7486a5fe6260db90; inverse fragment SHA-256 78b29d1b59963ef7c4fbe75502091c3a340fbeb467e60de15240a7aafa120e6b, reverted blob cda791a4c93583d5d5019bf26809c8c507945608",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "e088342e21f9a22c63e1": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 6dbf595f704cd1325a4d4c03e6ed22e2bfa8dd2f; inverse fragment SHA-256 08b8517054d05da6ae0a831f7843386aaceb335f46db5c4247dee0daf39fe08d, reverted blob 56a08fa249b8b9a19038c914bda465e5d91e7b92",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "e34ac29eac5407b3de9f": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 5702fbe4e5d4a601a048c3cb4262692b83014da4; inverse fragment SHA-256 3b6977e714b8635cfb0156009a9db6bef6a9f3d89e6624971ff4ad8f8b1bdafd, reverted blob 0da60afc6925227c8977672cf79e2cde07a2c4d9",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "e9d19b9838feacabf125": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 13a078b288847ea6cf233ea6956ca5427e965659; inverse fragment SHA-256 782f641c38d98344fae404426d0a2d30d7c9cb34ec61430c68c091f0b2a77abe, reverted blob 1a75bf2f034a9020cf53d6b168d59b19fc37e1b4",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "ee1aa39389a22da73651": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 8b3db188fd08dbdbdce76994a1eccb7618159d65; inverse fragment SHA-256 a5da05bd10836e49ecf89a52644aa91609250dd55d398ca1376d002f76e697ef, reverted blob 1615c4103dc75c3e1506c46b42e9129854aa2154",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "ef29df73604a765c3fdd": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 5259e905089ce3384f4810c79ac37be7e3ef2209; inverse fragment SHA-256 4321d51392205308e0a2708341b87197043d421ca8e4656bfdfd5ac5f821e932, reverted blob 4e26ab4ddc810c7cd43d30ec30d0114300f9e731",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "efcffcf7e63055440010": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob d5d09b0a2771973d66f832c21bd28cbce835eb50; inverse fragment SHA-256 640555d9ce475e896345e6d0a58c36aaac47e5bda3950ff84e4aa7028876f093, reverted blob 92abc00a9cc0002169db95555707ae4a636cc7ec",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "efd0d0fc1029ed4674b5": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 4868bdfe59e80149068c0054da1f61a1bbb3bace; inverse fragment SHA-256 3d6a8c50eb783a0708052ea458bb20347d3c753067dab79faed3e6c382b655e5, reverted blob 8a8c66895c8761ba95f9227600a2a6f557af9113",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "f105c40017de5ce88907": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 5702fbe4e5d4a601a048c3cb4262692b83014da4; inverse fragment SHA-256 c688361e10bcefa02b94988c7e94f3e23c2f096ac80e1ccf84eb819e1c4a6430, reverted blob 33ace2b58b281d317dcd82a7a345bfc8929cf378",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "f431183720e9fb7deb19": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 4e0919cfb5e81099add61abef189326118cd2818; inverse fragment SHA-256 61f18fbe58816c4e868426262aca4d01e0b6dce2d97b96d46c4536497bc21e19, reverted blob e5d1fd33b3c1c94b4a1d8a39f6706cd1cc30cbb0",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "f7305182c5ab6c95dade": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 92324ea8b092100062093b55394bf2febfbdb05c; inverse fragment SHA-256 b9e98be2fe77e8a2f7f8fe528a4cc575e9e1f11caf05ac3057e0b716a3f1892e, reverted blob 314ec841a38ad6cfbdb56e24538ae0444f13cc6f",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "f76cbbd34d8a41cc7efe": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 75f19461c318b210bddad54d620788f42f0e157d; inverse fragment SHA-256 4a46a6ce13e6772795b154e087b60a92c7f8a8a2fa7915267dcd428d5e0f7152, reverted blob ebc348b191b48fc22f61b17bbeb2196a04b20d07",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "f8565c5e81221723d493": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 955f2b3e45dabe53bc6a0ecbb6d24eb1603db2b5; inverse fragment SHA-256 e4dbaff81194766a6c45fd89b16abd7789b4ff65c279a7eeb88f3a5cd66940dd, reverted blob 47a6b3c6535f6bf1bd623ef7a87553a07f6afff3",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "f8c7da6daddc859ebb2b": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob acbe9d798dc317303525e6079360524d518fb0c8; inverse fragment SHA-256 b53254e0c3cfad3273ec923263cd75c386ce45ad6e79f2f34acf96fcdc10b365, reverted blob 52f8180f5fe8814fc56f3c7c4809183a6ed45eb4",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "fa236b7bc3de3898a072": {
      "revert_gate": "AAPT2 single-file compile",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 6dbf595f704cd1325a4d4c03e6ed22e2bfa8dd2f; inverse fragment SHA-256 690062fe1f9445608c952f93fe137f22de7940b187a5c1e6d3423ee126b5e4d4, reverted blob 6f6907717fbf5473aa72bf38fad7eb09a9ed6fa7",
      "outcome": "baseline exit 0; inverse exit 1",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "fb470f339c96f7f48fcf": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob ba0a41246e06c588fed6d358626d40af66cb5f97; inverse fragment SHA-256 63e3a0db98208f135ceca741c413defe3d115e3e18aa5a38ecc95186b17f9714, reverted blob e6e0eb4dc216d724333eabb356e4a9cd1f6e9507",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "fbdbc74d1ccb7eb80efe": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 86f72802f1768ed4c64254754e9da6fc1ef57eb0; inverse fragment SHA-256 bfb4480397e5d4eae9d5e37580f49d2c7bda21d239d20a4364780620b6d23f11, reverted blob b902ee207b91538cb370fe200d3dea9ba4f7578d",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    },
    "ff6707e7e2b4521f3b8f": {
      "revert_gate": "AAPT2 minimal resource compile/link and old Molly value assertion",
      "gate_input": "PR3 tree 129bd88f3f04dfb6b80a93b96ec026f6a5cd3010 and PR4 tree 2b0ba46b23fd27974b8cefbd36e0d91791b525bd identical target blob 51db2996c85a41d8f3cc8613d822eca91e50dd4e; inverse fragment SHA-256 dfd714862ed416e51bf260aa6ead6f1b2e0eefa2ff70fb64d5636fcc6ce10e32, reverted blob 80a4ae79d76d4b04eccd0d7c4513c25c0a18ad47",
      "outcome": "inverse file compile and fixture link exit 0; exact old Molly locale value absent",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/locale-remaining-readme.md and locale-remaining-cohort.tsv and locale-remaining-cohort.patch"
    }
  },
  "groups": {
    "locale-file-compile-failures": {
      "description": {
        "type": "conflict_resolution",
        "reason": "Inverse fails file compile; executed build-logic/plugins/src/main/java/mollyify.gradle.kts yields baseline resource values. Individual hunk intent and formatting remain unmeasured.",
        "molly_equivalent": "Executed Molly translation rule from 0e202dda90785771ff8c87526eaaf30c655f54f3 yields baseline resource values from pinned Signal inputs; old localized values and individual hunk intent remain unmeasured (translation-task-witness.tsv; receipts.md).",
        "searched_branches": "Molly 0e202dda90785771ff8c87526eaaf30c655f54f3 translation rule; upstream branches unmeasured"
      },
      "hunks": [
        "26a3b126d41c1227168e",
        "610b87319d5fed232134",
        "a201eb895f5b20a5f549",
        "45a524f407bd83e8f381",
        "5a836f26f1e5f6086cf5",
        "d22ebbbc082029787b44",
        "6fb27f612fb14269b688",
        "adf2a9c5cde787280bd7",
        "591f99e966870caa563d"
      ]
    },
    "locale-please-wait-preservation": {
      "description": {
        "type": "behavior_preservation",
        "reason": "Retain the old Molly localized please_wait entry in the resource table",
        "molly_equivalent": "Exact XML element matches old Molly 0e202dda90785771ff8c87526eaaf30c655f54f3",
        "searched_branches": "Old Molly tree 0e202dda90785771ff8c87526eaaf30c655f54f3; upstream branches unmeasured"
      },
      "hunks": [
        "8080b27cf35752b109de",
        "1b0c2e283e4bcbf45694",
        "0ef6b96ddb2b6a12d036",
        "4f9c3af9c3c25445405c",
        "3960bc6f713fd4f583c9",
        "9e71bfca6602011e73c9",
        "5234164af222129d89aa",
        "7bc861d77a50acc7856e"
      ]
    },
    "remaining-file-compile": {
      "description": {
        "type": "conflict_resolution",
        "reason": "Individual inverse fails file compile. Executed build-logic/plugins/src/main/java/mollyify.gradle.kts yields baseline resource values; individual hunk intent and formatting remain unmeasured.",
        "molly_equivalent": "Executed Molly translation rule from 0e202dda90785771ff8c87526eaaf30c655f54f3 yields baseline resource values from pinned Signal inputs; old localized values and individual hunk intent remain unmeasured (translation-task-witness.tsv; receipts.md).",
        "searched_branches": "Molly 0e202dda90785771ff8c87526eaaf30c655f54f3 translation rule; upstream branches unmeasured"
      },
      "hunks": [
        "9b4f8e1a7fcb40951dae",
        "74bc7aa72c0f6c84cbff",
        "fa236b7bc3de3898a072",
        "b0a04c63f4e66e528e5b",
        "7658f6dad33356af690e",
        "29500e8c358447f60bf0",
        "6be022290e27010001df",
        "4e5622f062f04b0fd380",
        "ee1aa39389a22da73651",
        "273678d268b9f9b9d434",
        "26f173b2506c685b1dd2",
        "a7cad578a2568c0ea67b",
        "f8c7da6daddc859ebb2b",
        "d43d693b6e60505ae3b0",
        "0553fd9230117bd166c5",
        "55f91abafbab90cecf00",
        "4943a9063702a7004583",
        "0aaad7f995aee6216a53",
        "43040b3b924f19263686",
        "a5090b8267946964c05e",
        "125d284155dd4fd93662",
        "88d6d029e71de6894166",
        "d0892322247e07b66744",
        "09d8c4bd8e72a4ab4de3",
        "799f539808dd87c116e1",
        "48a936829af37db386f6",
        "b44d5dd4be44c6e2bc42",
        "7aae8de4da9946dd2c48",
        "055b8ff2bdc75e21ab82",
        "bd475ba8bbd8936c5458",
        "9c58d84f31d46a7fda1e",
        "baa0a3815e0c75053ff8",
        "b2349b4ce2984adf8380",
        "ef29df73604a765c3fdd",
        "c8cccfba343ea12471d3",
        "2a094a856601fab248e4",
        "6160367579f427a96913",
        "770f02bf2c0a3a419b64",
        "deb7e97308692b75d4fd",
        "62a31e4696206f426c51",
        "1aad4231d7a1949777c9",
        "a655a87c6c60c5f3c749",
        "2512e063a4bcd8515d19",
        "f76cbbd34d8a41cc7efe",
        "b7b3686917421fabc086",
        "e34ac29eac5407b3de9f",
        "3c7ba584d61789c0a958",
        "4b9bd21aa6622022f025",
        "e04cf3531e494d17fc08",
        "de0bd17666760259439a",
        "6631dab5fc59927c6cc7",
        "0dd9f95a3b26fdc04d09",
        "51cbaadef925c20776ee",
        "449fc44366e309c842dc",
        "27ae8175989f9283fc99",
        "54f84ad6ec1ebea21bdd",
        "5e33d38563e47c988995",
        "708ca7d6886f90b5a95f",
        "551357f0d6bf9bdf68e9"
      ]
    },
    "remaining-please-wait": {
      "description": {
        "type": "behavior_preservation",
        "reason": "Reverse removes an old Molly localized please_wait value from a minimal linked fixture.",
        "molly_equivalent": "Exact XML element matches old Molly 0e202dda90785771ff8c87526eaaf30c655f54f3",
        "searched_branches": "Old Molly tree; upstream branches unmeasured"
      },
      "hunks": [
        "335aa1a1dcb1b35e0dc3",
        "f8565c5e81221723d493",
        "ff6707e7e2b4521f3b8f",
        "e088342e21f9a22c63e1",
        "8956ef3a832c1ed9aeea",
        "9613d2af95367184ec6c",
        "f7305182c5ab6c95dade",
        "1d63b0306acf8919d0ab",
        "3232f1c2b2465acf0e7f",
        "b29fc944d115cbf22047",
        "993bc52b36eeb1cc9c6c",
        "efcffcf7e63055440010",
        "f431183720e9fb7deb19",
        "a111f5f4e955e583a336",
        "04d7877749ce229a351e",
        "42e00ba35d0d21fa0fce",
        "6552a12dcf1470fc34bf",
        "5e6a9a7600265c8fb69b",
        "95adda1ec37fb5f169ff",
        "efd0d0fc1029ed4674b5",
        "8a02dfcef1a6106b81b1",
        "af13ab58ae9c57798508",
        "75eef1c7ac71d77900c0",
        "2774b4432907ba73228f",
        "27750b9d7d8f75b822f5",
        "2ec3c157330aef0b10bd",
        "88cbe607d29195391932",
        "abf663ecf129117f2270",
        "fb470f339c96f7f48fcf",
        "867970f3de06dcba66fb",
        "8c803fc214a5484f84b3",
        "36f98223dd1e03d0bf77",
        "0c3effa12dbd98c8991e",
        "a036a0693aba553128d3",
        "0658db093c2a369c3c07",
        "a093da930d7ab1f77e67",
        "fbdbc74d1ccb7eb80efe",
        "5baf9e7a17956a1a10c2",
        "2ff5d9e50d3f089e5bc9",
        "aa95e6ac555faf8d66ed",
        "0056feb01fbb2b28b267",
        "d763d4772536d02a5b00",
        "3a459134084fac71a23d",
        "916b2809d199f1998cd3",
        "81e77b6a6a343a361755",
        "cfde56a33b147116f1b9",
        "f105c40017de5ce88907",
        "b356b76b49e99b707d0d",
        "7e70dc24067e2fedbe07",
        "1ce2c0db43335218eb90",
        "20d1686920d1309781ae",
        "c404e0bf15aa887531ed",
        "6bb6caa504bda4e4e906",
        "36c6917db4dc5f73b7fe",
        "e9d19b9838feacabf125",
        "47c1df2bae06f5c015f8",
        "9d523a44cd5788113d16",
        "665943dbbc33d171d55d",
        "165fe3e31845fc26d03f",
        "7b90e531fac1be99056f"
      ]
    }
  }
}
```

## Native translation output hashes

Signal input commit `6e9dddc01f1b26e608ac683807c51e139c141ed2`; native task base `a715cb9771ff8e74dea7ad8d07238739b42f5b53`; retained native output tree `5b917461f1bf426b6e20472b56010525cfe83936`; plugin blob `266df84b06b09453f47b7c0dd59561a8001d88af` (SHA-256 `09d9968fb3b93e41297711e32a4abaa529981888996d038c8c81bfcc508d05dc`). The 12 default inputs in the TSV match the native task input SHA-256 values. All 816 generated strings.xml files match native Git blob IDs and SHA-256 values; selected paths and per-file input/output pins are in `native-translation-byte-witness.tsv`. The 68 old-Molly `please_wait` values relocated to strings2.xml are outside the native task proof; they were separately compared with old Molly and the prior PR3 source. The generator validates `reference_tree` as a SHA but does not read it; the verified old-Molly values are supplied explicitly in the manifest. Comparing the previous PR3 tree with the regenerated target found 264 changed locale strings.xml files and 68 changed strings2.xml files, with no other changed paths. Parsed resource keys, attributes, values, nested markup and child tails match across each locale pair; root attributes, resource order (excluding relocated please_wait), and top-level comment text/order also match.

Native task command (executed in the source checkout):

```sh
env JAVA_HOME=/var/home/x/.local/opt/jdk-21.0.13+9 ANDROID_HOME=/var/home/x/Android/Sdk PATH=/var/home/x/.local/opt/jdk-21.0.13+9/bin:$PATH agent-heavy -- ./gradlew --offline --no-daemon --no-parallel --no-configuration-cache --console=plain --info --max-workers=1 --dependency-verification=strict -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx5g -Xms256m -XX:MaxMetaspaceSize=1g -Dcom.android.tools.r8.threadCount=1 -XX:+UnlockExperimentalVMOptions -XX:hashCode=3' updateTranslationsAll
```

## Username API inverse compiler measurement

Only the property `usernameApi` was changed to `username` in `app/src/main/java/org/thoughtcrime/securesms/jobs/RefreshOwnProfileJob.kt:308`; all other context and mode 100644 were preserved. The portable inverse patch is included below. This measures one line, not the whole 0/122 raw report hunk 45dbc1792099f68a9160. SignalNetwork declares `usernameApi`, and UsernameApi provides the called method. No Molly port equivalent was verified; no history fetch was performed.

| Source | Base commit | Base tree | Counter commit (private ref) | Counter tree | Baseline | Counter |
|---|---|---|---|---|---|---|
| PR3 | 20ed69ce4b62767253a9470308a2636c710f57a5 | a88d1e244cda745b090bf10fd12235222c8fa224 | 0ac07f21d8afdf339931697350feae5efc32359c (`refs/agent/username-api-inverse/pr3`) | c9fa2e4d0e9807b9a173f356e1fcf8925b8ebef2 | exit 0, 9m17s; 342 executed | exit 1, 1m26s; 1 executed, 341 up-to-date |
| PR4 | a715cb9771ff8e74dea7ad8d07238739b42f5b53 | 1db9d411742f0fbca434de252085db0543b958b7 | 2c13412f89661c3b99420150fa13287111a73822 (`refs/agent/username-api-inverse/pr4`) | aeea934771b09a40a9f429c0673747d9e580eae6 | exit 0, 2m41s; 8 executed, 334 up-to-date | exit 1, 1m24s; 1 executed, 341 up-to-date |

All four app Kotlin tasks executed, not UP-TO-DATE. Both counterexamples failed at `RefreshOwnProfileJob.kt:308:51 Unresolved reference 'username'`; subsequent type inference errors also appear in the logs. The result establishes this property is required for the tested compile, not runtime behavior or necessity of the complete hunk.

PR5 was not compiled separately. PR3, PR4 and PR5 share identical 100644 blobs for RefreshOwnProfileJob.kt (`67d66343288c9dc074cb704540d12788407ebc4a`), SignalNetwork.kt (`69e8e042551782af7940d4097d0be62b9b08b7c4`), and UsernameApi.kt (`8408a2c2e89e699969985cbe95f2ea8c6c4981ca`). This supports transferring the symbol-level finding, but is not a PR5 build result. Both counter blobs are `4a15f1ded622784f3e1697d64e7ab9faa8e3a688`, mode 100644.

Command used in each source checkout, with logs recorded during the runs:

```sh
export JAVA_HOME=/var/home/x/.local/opt/jdk-21.0.13+9 ANDROID_HOME=/var/home/x/Android/Sdk TMPDIR=/var/home/x/agent-scratch/mollyim-android/pr-stack/tmp PATH=/var/home/x/.local/opt/jdk-21.0.13+9/bin:$PATH
agent-heavy -- ./gradlew --offline --no-daemon --no-parallel --no-configuration-cache --no-build-cache --max-workers=1 --dependency-verification=strict --init-script /var/home/x/agent-scratch/mollyim-android/pr-stack/worktree/tools/patches/test-worker-heap.init.gradle -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx5g -Xms256m -XX:MaxMetaspaceSize=1g -Dcom.android.tools.r8.threadCount=1 -XX:+UnlockExperimentalVMOptions -XX:hashCode=3' :app:compileProdWebsiteDebugKotlin
```

Cold PR3 source/build started at 157M; disk check OK free=251G unallocated=3G metadata=78%. Reused source build for remaining gates; offline and no build cache, Gradle incremental outputs were reused as shown by task counts. No infrastructure correction was required. Source restored clean to PR4 base; build retained for the next source lease.

The following patch uses zero context and can be applied with `git apply --unidiff-zero` to the recorded base.

```diff
diff --git a/app/src/main/java/org/thoughtcrime/securesms/jobs/RefreshOwnProfileJob.kt b/app/src/main/java/org/thoughtcrime/securesms/jobs/RefreshOwnProfileJob.kt
index 67d6634328..4a15f1ded6 100644
--- a/app/src/main/java/org/thoughtcrime/securesms/jobs/RefreshOwnProfileJob.kt
+++ b/app/src/main/java/org/thoughtcrime/securesms/jobs/RefreshOwnProfileJob.kt
@@ -308 +308 @@ class RefreshOwnProfileJob private constructor(parameters: Parameters) : BaseJob
-    when (val usernameFetchResult = SignalNetwork.usernameApi.getDecryptedUsernameFromLinkServerIdAndEntropy(localUsernameLink.serverId, localUsernameLink.entropy)) {
+    when (val usernameFetchResult = SignalNetwork.username.getDecryptedUsernameFromLinkServerIdAndEntropy(localUsernameLink.serverId, localUsernameLink.entropy)) {
```


## Additional historical annotations for native-format locale hunks

These prior-tree hunk annotations are historical and do not measure the regenerated hunks.

```json
{
  "source_tree": "a88d1e244cda745b090bf10fd12235222c8fa224",
  "annotations": {
    "08aa84d09480b833ec46": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 96261c7cfe0e645d3e02ea660942ce362aa07ef289bbe2f749af7d3f9e53401e, reverted blob 4904d9a31521ec9d3dbdbb7cd7dcbf7007dd3017",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "08cbd68b8379846387d7": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 2b042aaa52add2cdeb114ea4f9d51bf0e0d0e452556b824cb401315fa31de0bc, reverted blob 6dbaf954487249c1ac4889adb422a4e43b9e65ae",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "0d10484a303e073bb2c8": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 c42a7c9895b6fd23b9d1b9c7e4df4a984e20e5a5bfaa1431a1b00a363c621292, reverted blob a948bc9eb3387bd9db5df1d6d49dc6153c4eec17",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "12183e1e04d30fc63708": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 d53670c182278dd523933ea74daafd83166fa339cce7f3858afa90fa3838a825, reverted blob 05f62d7a365515542faec0ce2f28022f9d9e5ecb",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "1316149900b57e5e04e9": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 3172d231ef7c7911e36aec3d306fa137c398b6883ad266a8d22a16064d8e87c6, reverted blob 97208a5b9b9febbb1d731c11942518b9be37364e",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "1af2f18213a9e18ee302": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 7f468a8ea6be70df1581751fdb1c93a354c28cb04f9768cdf11844bfc4725911, reverted blob 75a8c6adb7e859a741368aa1469968e99f441215",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "1c4821c59dc1d4743fd1": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 c048bc58ebed154bf61a1b31d5a9881ad7195b499d14669672d08e23d9531887, reverted blob c70fdbd804dc4ac95568517beb0d97caa564bbbc",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "1d7e7b5dbe6b6ac019ab": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 67b8944016e4d08903f58319e04cef975e6097a68c1cb63d7ca2b66606bcea77, reverted blob 77d1a5ca57fffd3fa93c310b12b29ebf4af6b4d2",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "1f41c689b68f8f5f7e9b": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 ce147b736d634a3e2b91d6706b3d2766ee87b45a7c70d62d6bb780ec80afbb64, reverted blob 4cd24939a98323e150ae047f8089f10fd172e956",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "2343af65919dc59e0317": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 e8512957c14b705afeada8b6317e2f51fc97a7f049437a02ebfb6e732030a1e6, reverted blob b117d30e145ef7c43203bc73f71603949b80d31e",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "23628e297343439730a1": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 487a2524fbde77b6b7bee18a0324d46b62e3c3d78ce4179f8eeedf64d89ea954, reverted blob c65afcf8685f5a3ff30a24e9235d346e2f15f7d3",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "2eec53567625ae821ac8": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 120c01af0692d19217bee07d62c48adfbf3f185316292d75e30e4fa34470199a, reverted blob dcce9589637d6ad5af04597f7288476b0eae93bf",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "2fa1d46717f273a02e12": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 4e4d0fed235a4d0df77109d497b2af686678e2ba9d417d22d508286ffcf07e2e, reverted blob 667cf5ada4ae2fb7a788527ef3e1999a741e3e44",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "32e3f03dbd6aa4f4d93d": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 5a51ba47b3b163ada1b7f94443cce1d96f0be3820e3010017c3a2f45dd144930, reverted blob 3153b56f28bdab693f077653b8de7712e6b93a64",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "342f33eb6de645a658d4": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 aad91c93b15bbbe39b7ac1fdeb9dddeacde93ec6d58e65155da1f089559c60ad, reverted blob 2b4ae849c3472116832ec34cc421852576536759",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "419cd4963659a0705fe7": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 16347ff6359a2ed08dc4962f9a8cf1bd084deed893c7468adb39811583dd61ac, reverted blob 541c7d85035d7d365ad76d2cf4d8ea18a4cb5c0f",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "422d3bbfaf61cb4cd5cf": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 0feca363dc087aef37381791858f06d8a77334c83151f0a3ea913d7ec0e973ac, reverted blob e3a13d4af023a614b66af80413bef2fc05576b18",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "45e327cd2aeefb91ce37": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 697f58cf2a7812088dc2de255e597c107ae5d2fb01f84f2bad944dc73ba350da, reverted blob fe16be66deca083041271a9f60524d3d8d39710f",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "49e303f061737d6d3610": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 93b2024a1eda880345c1da7c7954e07901ec8ee572f04ed5a45abc1327de1bd3, reverted blob aa42e88762f5a7d519f6dcd1b67459185a4cf6c4",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "4b3c412d4ad6ade46268": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 352c8d48b789771ed4e16e6c7c9411c99868d3e0733d43d9f60d51cee2c60b49, reverted blob 9e6f9eab6e775243a53f7d4b5542eacbb86f5417",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "4e4db7d3044b0db9217a": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 4206bd29d90c26fdd7124326788e93ff146aa72d220b59a1b7c6e907e9405ec5, reverted blob 9f24ff896e3d5f9a0446608583a3389bc1abd1b6",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "50b0c0b339a3f53a0299": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 cbee21cfbc390a3dfd1f58e5e1e3e04245d2b66781a285f907a660d3ff03cac5, reverted blob c46174f02c3b78af6b707bace1136d70876adc4c",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "58bf5c0467be1fb26edd": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 60b80dcc4f68b6d9c933eae1eee22f892b051211082c96252e55c8292dd493ac, reverted blob 2a7452445ab652249ee92e6f19acfa0ce6eee25e",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "58bf65eafbf0aa72b461": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 8d376fa8aa0890a7e91e8ed2ad5755c2b65256f906f8f2d13d54576b34c85cb9, reverted blob 53928be721d174bcc35e0c5856788834b2ee6c63",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "58bfa9f6a72d85f43d94": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 9cdbf0558f4b1d23db0401c3c799907c514293be95af499e02890c2ba9488766, reverted blob 47cd15de25e582e8720b2e007c6a1ebb1b13dfb0",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "5b776960862a95bdffba": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 acf399760d0b51753ad90baf0a400e6447d85add08763d54cf354e84b50e9338, reverted blob 962a4384619ce78ced21014fa8e1fdec9f75a41e",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "645d14da14aee16e90df": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 09bde7de4ea45983445854449626e9c7b28784e62c6a32ae5560d4a62f79aa2e, reverted blob dea92ea3c15285fe8cdde0705333fbefbf1cf32a",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "7111be3f6d824b440792": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 2c2c99fe69cb9b493c4d4f14f60070e9eb38a493b684c0616a7568f9556893ab, reverted blob 4ade8cbfd88bac1ef1361b174b974793a3ecc202",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "71ad5f4dceb9c12fad9e": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 3bd47bf4cca943dfba8faf59f246909dec623bc1c53e429b2606625f4612fb60, reverted blob c795dc74e2e6acf8bfd5e232354b2475cfc4f9db",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "73627c9954211b40efee": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 f98e84c3bbf8951722fe95d2ce0efd626d9505933dc590be70680a9d07b803f1, reverted blob b241d2ac76397a68fe148b1d7d2a2a3677754f70",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "77b8fe8a5c2ce294e9a3": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 3865b278385e9f51e41391912eedab95c456b2165ee930bddbf5bc82e815ae23, reverted blob 174661faa2928a8280ac823d61e07bf19afb07f0",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "7a273c0a84eb0805316b": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 bf1a440f76e78f54186ac7d47c65a872790dcf6c6427783763e1527220035100, reverted blob a94cd7e1752fae158399235b4ce3a50fc4b2c7da",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "7e7ea440b6a59a2d729a": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 4c1fc1b4e4d71c30c29bf47de28bf1b6d25c6c7d063d3d6092108e833d214d85, reverted blob e7290432ad069e6787a6b01dbc95d253176b9296",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "85fbcfb2d71a0be92f48": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 097c9a9af3d601ac8ce1059f719a7ad5a967e09f7fdb313e4c8e412e682ed2d7, reverted blob 381b33f3b99c6e19ad2e9fe9a08209140c44d347",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "8eedfa7a162b8de671c8": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 7012c837d77489b84e4bc510a7dbc4a00896a81ce27cb682744088aee80ab195, reverted blob 2b6763cc6a339d8280fd68d9f141187d3cca6dd3",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "92c79bb3699ceef71d8c": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 eff7c32b039572a62e18069a0cb671bbf40c1666edfe8d18ab90fe5bdb1f7095, reverted blob d9783c48d0b3655ea07b5b933fa5ed61bd43a8d3",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "93b08e797df6a2dc186c": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 798ab152e369ecd09505059ff96c03d6f07ba14b773cb98a053a45899338cdc6, reverted blob 06d42dc13d01a13b798c3678c5b8e6627e99b985",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "95c539fee77d3946886d": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 7e121683fc355083d68223e4cd2b1f355c8ac9fd9405f5d85abd5217d580ca28, reverted blob be50f766488c7163ffbdacc8a33ccc59e898b219",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "99328c7ab76e2ed62200": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 eb3494dcd6bb93983968bdb441dcbeec37e3c38bef0093be5d5c033545c4198b, reverted blob bdb7aa95671a89bcf06a515822f3d28f9c5dec6c",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "9996d0bee4cc9abd2bb1": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 69458b1e18d0a23a6a08a764284b7c8557f332d2edeea87cc67c4ff41a7283eb, reverted blob 378c532a01d8d5b7f3aa493edb623140d0a68e74",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "9d0210c3846434250ecb": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 0e91c63e392a9885524344daf7005bf85d3c9bbf9d1092e65ab232c5e2683ee1, reverted blob a13a817c8a7092743652a27a30c8785cc4ec1cbe",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "9d735c9c13930b8bde85": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 2baf5acf3ee202b7d8919ab04c54991aaf6a1babc5039488f88bdba33c759825, reverted blob b952c18a1a3e052f03539f50e30af785a268f089",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "a03ed72a9441d561b52b": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 3ca3ac5ec90dcb74601d3155134ef569de0d35fc02c5f106679d05d5fe92eddd, reverted blob cba725faac6a241c34555fc3536b4cd40f19aadd",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "a415af67b95eba32f342": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 6f902fb42371423ab6ba2238d0fbfad14978e8c5b0cb4f69186199dbe23c1995, reverted blob b8749927b87f93e31abd8eb82e41217a5ba55504",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "abf24ceab0ec9e0c6bb3": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 b997361820ee6d496a68ac0e91cbad76856384d5262b6128e57e24574a2dcbf2, reverted blob a57422d92cedb4631813853c61305714d0571691",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "acdb773a5eda235a9591": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 757b2fa0dbeebdd29db17b3e2c07e2d1213de0c6c5c1bf94dfa24a9bdd1f01f6, reverted blob a976c123aae3002792c6d2b756d10469c8081fec",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "ae4f2d6cefe1945d3284": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 67cc6570ce010c7a6efb23507322b1f8d84c8292f147838d30ce0cf4744d8118, reverted blob 4c4cc0bbaea90102a0b2e17822f19c4a8beb7b4a",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "b3cf312ab9c39d68c63f": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 c29edade94eaceb685d757c33132eecd1a8f0c6787b2f6e91e3fea861935c3d8, reverted blob 06e3f4e998f028edd02744c53c2c8c3e65ebbabb",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "bb52c8b43b0245491d3c": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 d6cb6d99906c253a879175b5e32c0c12d5bd62cc769a5d33f5ae80b26402aab1, reverted blob bdb0464744d467bb0c2452195916a5c0633743f9",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "c1b64fb2327a8a66bfa9": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 f502aa0c7d9a0cfd9f8ffdf50df42dc002c12f3ae5752faa5e60d3a4531533ea, reverted blob f406f2e7fa04babe421f1dd6b9a47a752e8b2492",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "c24c7321e603968019d6": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 a12998422a0659f5e965a28b666eea1e98c47841271f970525c5eebd06f11091, reverted blob 2a35ccce0c01e3cb2db8206b3cd31f7345d5e0be",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "c29c89a7329aea8efe00": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 cd378ea5e8d5cfa924b9a3cbfd49a2cf8c2e3fdf9b718e3e7a4a630b636542d5, reverted blob c5127ddcb38b1fc97d820e262ebe3625a3eb0687",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "c349fe856b97f242c602": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 54ae0485c5e5d3efda84e9ab6b43d2d1c0d67ba94297d38e95fe07ae8a730426, reverted blob 81049845cd56aa939171246577903e4ca0cb4969",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "c467cdb7ed5922af44be": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 ab1260b8cf221ef7e8bac38d5769aa6ffd4e7bdf1531110a6b17fa86784b7293, reverted blob b06744fdc3cdcd8edccfa65413f9babcdfb934b1",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "cca216112b9de0ca8893": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 3fcaafef18fcf74279c2e375ed44d17b275ed43f417a1f0edf707c5e01daadb7, reverted blob f910ef10c4987ac81e74ea1fa8ade9f2428696f8",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "d4ec3ea74b7030ff7acc": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 409fbf5d3fe3c7c23fb049d911733bd0a71a8b269a39986eb3be1a2e07bb13f9, reverted blob d245842f774510b73544559f06805c7b0932d886",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "d661eae897fe47a6ebd8": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 5e753972c65107155d2f46a396c2df49623e57c8c0dd0489d2e98d45f5b774f8, reverted blob cfe3b289f8653ab303ab6bcd6f32d264499c15fc",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "d8cacbf00236acdfbf83": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 103766f3d6a3939ce528bbc89c4ea0382bb2260d168e52160a50e3031bc036cc, reverted blob 650af4ba75ab659c47c79ecb39dc46200afa7f72",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "da56d5a2374327c0f46a": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 fe8978eb65221644d155a1b797d426a87026e6f159c69c75ed38ccd38cd5457f, reverted blob 6b4afab851b0bf2deddc3769cb81e6e74084ee20",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "e05721ba303a51ba5013": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 0ebf79660aacc84e8de16cba7e728a7cb809d77fdc68c8eac3f64a168462eb0a, reverted blob 760d6037bca9ddc9d7aaa27021c6494151930c9a",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "ee750fff594fa081dab9": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 df65cb4f4dea35d46e4709aff7fe43ced8a61e01f6b216f1173ff6cd0c67721d, reverted blob 20cb15ec5ca6590c36224d33901e765708daec7a",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "f0cd2e77ab4805cc84f0": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 fdff41cec9c6780f67be22f482c7a1aac5180ddd93969119847c7aac8541157f, reverted blob 636b99da1a260ef3aa6b1ad71f27bdaadda05c63",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "f7f7ddc7c8548fd3e5a5": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 64ca3ffea2f83dcaea867245f5094a30de32f13bad948f152e1016ff38bf2083, reverted blob c324d4f0ec951a0dbfd7a64bdc49000819d7be88",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "f9c66545a3ba64a36a45": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 d44da5f9b94ddd7f20b73316a48fae7e3093c5a1b24c017499a5cd1550537f72, reverted blob ca0294f686eb3a0653e395dec61571dc1295633a",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "fb3ff5bd35789cc73a54": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 15987bde2a1882e1dc12947d4e4a3d08154838d67b507e104cd71e2ccba785ee, reverted blob d8345bbfc1794b4088e485655b870894b8fceba1",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    },
    "fbcc878a8c4da2341c19": {
      "revert_gate": "AAPT2 2.20-15978811 single-file compile, XML integrity and pinned translation model",
      "gate_input": "PR3 source tree 3a20fd39d1f0e444bcc4711a2909635d8762b67e; PR4 source tree f25a1de0fc87f0f862cc270f823d240857878bc5 has identical file blob and hunk; inverse fragment SHA-256 95515ba59b823eb926525a24001f8e765bf7c91bf00787d3e9692071fd2af191, reverted blob c4ad83ab61996543ef3dcd39b7de82a573c27f49",
      "outcome": "baseline compile exit 0; inverse compile exit 0; restored_anchored_conflict_marker; model is not Gradle execution, device behavior or proof of old Molly values",
      "evidence": "patches/ports/signal-8.33.2/report-evidence/post-format-results.tsv and post-format-cohort.patch"
    }
  },
  "groups": {
    "remaining-conflict-marker-cleanup": {
      "description": {
        "type": "conflict_resolution",
        "reason": "Inverse restores a conflict marker; executed build-logic/plugins/src/main/java/mollyify.gradle.kts yields baseline resource values, separate from marker and formatting choices.",
        "molly_equivalent": "Executed Molly translation rule from 0e202dda90785771ff8c87526eaaf30c655f54f3 yields baseline resource values from pinned Signal inputs; old localized values and individual hunk intent remain unmeasured (translation-task-witness.tsv; receipts.md).",
        "searched_branches": "Molly 0e202dda90785771ff8c87526eaaf30c655f54f3 translation rule; upstream branches unmeasured"
      },
      "hunks": [
        "4e4db7d3044b0db9217a",
        "c467cdb7ed5922af44be",
        "c29c89a7329aea8efe00",
        "73627c9954211b40efee",
        "fbcc878a8c4da2341c19",
        "abf24ceab0ec9e0c6bb3",
        "da56d5a2374327c0f46a",
        "cca216112b9de0ca8893",
        "1c4821c59dc1d4743fd1",
        "9d0210c3846434250ecb",
        "85fbcfb2d71a0be92f48",
        "f9c66545a3ba64a36a45",
        "d8cacbf00236acdfbf83",
        "50b0c0b339a3f53a0299",
        "fb3ff5bd35789cc73a54",
        "0d10484a303e073bb2c8",
        "1d7e7b5dbe6b6ac019ab",
        "5b776960862a95bdffba",
        "2eec53567625ae821ac8",
        "32e3f03dbd6aa4f4d93d",
        "99328c7ab76e2ed62200",
        "e05721ba303a51ba5013",
        "49e303f061737d6d3610",
        "9d735c9c13930b8bde85",
        "c24c7321e603968019d6",
        "58bfa9f6a72d85f43d94",
        "ae4f2d6cefe1945d3284",
        "f7f7ddc7c8548fd3e5a5",
        "1f41c689b68f8f5f7e9b",
        "2fa1d46717f273a02e12",
        "1af2f18213a9e18ee302",
        "93b08e797df6a2dc186c",
        "c1b64fb2327a8a66bfa9",
        "acdb773a5eda235a9591",
        "a415af67b95eba32f342",
        "342f33eb6de645a658d4",
        "d661eae897fe47a6ebd8",
        "58bf65eafbf0aa72b461",
        "08cbd68b8379846387d7",
        "4b3c412d4ad6ade46268",
        "71ad5f4dceb9c12fad9e",
        "c349fe856b97f242c602",
        "95c539fee77d3946886d",
        "422d3bbfaf61cb4cd5cf",
        "12183e1e04d30fc63708",
        "d4ec3ea74b7030ff7acc",
        "645d14da14aee16e90df",
        "08aa84d09480b833ec46",
        "b3cf312ab9c39d68c63f",
        "f0cd2e77ab4805cc84f0",
        "7111be3f6d824b440792",
        "23628e297343439730a1",
        "2343af65919dc59e0317",
        "9996d0bee4cc9abd2bb1",
        "92c79bb3699ceef71d8c",
        "58bf5c0467be1fb26edd",
        "8eedfa7a162b8de671c8",
        "7e7ea440b6a59a2d729a",
        "419cd4963659a0705fe7",
        "1316149900b57e5e04e9",
        "77b8fe8a5c2ce294e9a3",
        "a03ed72a9441d561b52b",
        "45e327cd2aeefb91ce37",
        "7a273c0a84eb0805316b",
        "bb52c8b43b0245491d3c",
        "ee750fff594fa081dab9"
      ]
    }
  }
}
```

## Binding evidence v1

These AI-assisted compatibility checks ran on reconstructed profile 5 SOURCE commit `3a81e04ead24cccd43406d952f73b5368e887a99`, tree `68fa08fd69809463be3aa60ccd232bb50f02e547`. Source commits 3 `4e2a3cdb5884437745613e1e17b0511f6c465dff`, 4 `05d17fb96d86ee1719c76ef435cb167cd1ac3a62`, and 5 `b9f7f076e5c84433ecee890741c2c044b75336f1` have identical relevant file blobs. Transfer to 3/4 is conditional; neither profile was measured. Mechanical tree `759ef1c309d93aa7e4ac7217b144c04545562f11`. These isolated real-file javac checks are not full-module or runtime checks and do not establish every changed line is necessary. No counter was accepted, no production code changed, and no action chain was extended.

Old Molly and human ports `5551f3023e6093fbb65214acdcc2e568b7b63132` and `aa81fd6312bf1c7a71f5efda1930d23b05bdb739` preserve old implementations; no new-API Molly equivalent was verified. Observer old blob `0736adfbf10f16abe75186ccd2f4ba72bcee5fc4`; KeyCachingService old `8a369ef31c76b21c47c168b112005cb880028fea`; manager old `16a01f9f3ebc8b085bb930ea01f1d51c4ff521a5`, socket old `1daa76c8d9f51743e01209d91f19fe1564b7e378`. Signal uses `RegistrationApi.registerAsSecondaryDevice`; keep 409 MissingCapabilities policy. The event producer uses Signal bus; the sticky-event guard is retained. Neither observation proves runtime behavior.

The service export initially failed twice before any counters: session 97339 exit 1, root task resolved Android JDK image without project lock; session 26070 exit 1, included build lacked the requested project. Those are setup failures, not counter outcomes. Corrected owner-scoped export session 52464 exit 0; observer export session 61642 exit 0. Observer runner session 10113 exit 0; service runner session 97163 exit 0. Baseline and counter results below are individual javac exits.

- `d314327781b510373bf0`: Whole import hunk reversed: Signal EventBus and ThreadMode replaced by all three old greenrobot imports. Baseline `19b8ef8ca9c345f38b199e78ddf93abfb15b83a1` exit 0; counter `c70a2fc98c7bc923d7d791a29a61c6edd2dd759c` exit 1: greenrobot package absent and EventBus/ThreadMode unresolved. Original patch SHA-256 `5ddf7908b9ea3cd02b249096b32be84e4c89de30946c77cc00cf49d0eb658005`; portable patch SHA-256 `ad449b9afdf0f1195057cd1ad0899d4c22527cea5f9201d2d2e1a91835ca6f18`.
- `23fbf7e6ed4bd45b4dbd`: Historical one-token subscribe to register probe only; the whole callback inverse was unmeasured at this stage. Baseline `19b8ef8ca9c345f38b199e78ddf93abfb15b83a1` exit 0; counter `d0374b64abc40726db3238701536de50179112e4` exit 1: no matching Signal EventBus register method. Original patch SHA-256 `e85d86e15bb68d12d0df3afc63128a3ae9fc74c4d46c9721f641fa5ebadb3bb4`; portable patch SHA-256 `08f6025d22dd28aa581967f0361b6ec64e15941b7be7a098de2fa23701b038b2`.
- `efe95a23f05268a99f97`: Whole one-line EventBus import inverse; sticky TransferStatus guard retained. Baseline `8b392dc3ba3adf6b6abb6f0125ab766ca537994a` exit 0; counter `be99680a42a2ef40a5e67fd0c7ddf599cb5752b5` exit 1: greenrobot package absent and EventBus unresolved. Original patch SHA-256 `2da8563f2fe6c3ded6c1bc33f380c6a9064af5de023a8389a48a31aa493e7afd`; portable patch SHA-256 `2da8563f2fe6c3ded6c1bc33f380c6a9064af5de023a8389a48a31aa493e7afd`.
- `7d2f8e90ce08e18c8f21`: Whole 16-addition/2-deletion method-body hunk reversed to old two-line delegate; existing attributes parameter used. Baseline `932e209b0781e43dd8c3088abce07e790df90503` exit 0; counter `ce05d451dc6477366a470e0657ddf2bcacd74de9` exit 1: PushServiceSocket has no matching finishNewDeviceRegistration method. Original patch SHA-256 `6efd50532d353b68e0309f13e4ddfb70f8b626ee6b0e4787b73dae46a60d14d0`; portable patch SHA-256 `6efd50532d353b68e0309f13e4ddfb70f8b626ee6b0e4787b73dae46a60d14d0`.

The observer method-name probe retains receiver, lambda, arguments and checks; it does not restore the old anonymous Object, register call and @Subscribe callback. The whole callback inverse was unmeasured at that stage; see v2 below for a later whole-hunk check. The observer imports inverse restores all old imports in order. The manager method body inverse matches the complete raw 16+/2- hunk; separate imports remain.

### Reproduction recipe

Use a clean reconstructed profile 5 source with its built outputs. Set `JAVA_HOME=/var/home/x/.local/opt/jdk-21.0.13+9`, `ANDROID_HOME=/var/home/x/Android/Sdk`; use `agent-heavy -- env JAVA_HOME="$JAVA_HOME" ANDROID_HOME="$ANDROID_HOME" ./gradlew --offline --no-daemon --max-workers=1 --no-configuration-cache -I <init.gradle> :app:exportObserverProbeConfig :lib:libsignal-service:exportServiceProbeConfig`. The actual app export used max-workers=12 and only the app selector. Export owner task properties in a project-local task, guarding missing projects in included builds, as in the following Groovy. Replace output paths with scratch files and instantiate the block for both pairs; `kotlinOutput` is `classes/kotlin/main` for service and app uses the actual output path below.

```groovy
import groovy.json.JsonOutput
gradle.projectsEvaluated {
  [[':app','compileProdWebsiteDebugJavaWithJavac','exportObserverProbeConfig','APP_CONFIG'], [':lib:libsignal-service','compileJava','exportServiceProbeConfig','SERVICE_CONFIG']].each { spec ->
    def p = gradle.rootProject.findProject(spec[0]); if (p == null) return
    def t = p.tasks.named(spec[1]).get()
    p.tasks.register(spec[2]) { doLast {
      def o = t.options
      new File(spec[3]).text = JsonOutput.prettyPrint(JsonOutput.toJson([task:t.path, sourceCompatibility:t.sourceCompatibility, targetCompatibility:t.targetCompatibility, encoding:o.encoding, release:o.release.orNull, compiler:t.javaCompiler.orNull?.executablePath?.asFile?.absolutePath, compilerArgs:o.compilerArgs, argumentProviders:o.compilerArgumentProviders.collect { it.asArguments().toList() }, bootstrapClasspath:o.bootstrapClasspath?.files?.collect { it.absolutePath }, annotationProcessorPath:o.annotationProcessorPath?.files?.collect { it.absolutePath }, classpath:t.classpath.files.collect { it.absolutePath }, javaOutput:t.destinationDirectory.get().asFile.absolutePath]))
    } }
  }
}
```

For each file, copy the complete baseline into a private source directory, compile it, then apply its portable patch to another copy and compile again. Use exported compiler and classpath, append owner Java output and Kotlin output, and pass `-processorpath <empty-dir> -sourcepath <private-source-dir> -classpath <exported-plus-outputs> -d <private-classes-dir> <private-source-file>`. Preserve every exported compiler argument and argument-provider value. App actual arguments were `-source 21 -target 21 -encoding UTF-8 -XDstringConcat=inline --system /var/home/x/.gradle/caches/9.6.0/transforms/ac7ebc01a1efefae22c644559f3a2c4c/transformed/output/jdkImage`; 271 classpath entries, empty processor path, no explicit release or bootstrap. App Kotlin output was `app/build/intermediates/built_in_kotlinc/prodWebsiteDebug/compileProdWebsiteDebugKotlin/classes`, not the initially guessed tmp path. Service actual arguments were `-source 21 -target 21`; 35 classpath entries, including two absent Kotlin-only dependency Java-output paths passed unchanged, empty processor path, no encoding, compiler arguments, providers, release or bootstrap override. Service Kotlin output was `lib/libsignal-service/build/classes/kotlin/main`. Both used `/var/home/x/.local/opt/jdk-21.0.13+9/bin/javac`. Empty processor path was preserved; Java and Kotlin owner outputs were added to resolve same-module dependencies.

## Whole callback binding evidence v2

Profile 5 source tree `68fa08fd69809463be3aa60ccd232bb50f02e547`, baseline blob `19b8ef8ca9c345f38b199e78ddf93abfb15b83a1`; the identical profile 3/4 blobs permit conditional transfer only. The portable inverse patch `hunk-23fbf7e6ed4bd45b4dbd.patch` has SHA-256 `6bcd39d673e543b9fe4a6d820a402e2cf846113c265eca155fac3df8af995bfe`; applying it to the baseline yields blob `068a1b05cdf40c911c4acda6aafa44f45e0a98d4`. The changed lines exactly reverse the raw 3-addition/6-deletion callback hunk, restoring the old anonymous subscriber. Current Signal imports and dependencies remain unchanged. Git baseline mode is 100644; private filesystem source and applied file modes were 0640. No production change was accepted.

The previous v1 one-token probe is historical: its portable patch SHA-256 `08f6025d22dd28aa581967f0361b6ec64e15941b7be7a098de2fa23701b038b2`, source blob `19b8ef8ca9c345f38b199e78ddf93abfb15b83a1`, counter blob `d0374b64abc40726db3238701536de50179112e4`. Its original pre-portable patch SHA-256 was `e85d86e15bb68d12d0df3afc63128a3ae9fc74c4d46c9721f641fa5ebadb3bb4`. It retained the current lambda and all arguments and changed only `subscribe` to `register`; it did not test the old callback. Its exact portable bytes are preserved below as base64; decode with `base64 -d` to reconstruct the patch:

```text
LS0tIGEvYXBwL3NyYy9tYWluL2phdmEvb3JnL3Rob3VnaHRjcmltZS9zZWN1cmVzbXMvam9ibWFuYWdlci9pbXBsL05ldHdvcmtDb25zdHJhaW50T2JzZXJ2ZXIuamF2YQorKysgYi9hcHAvc3JjL21haW4vamF2YS9vcmcvdGhvdWdodGNyaW1lL3NlY3VyZXNtcy9qb2JtYW5hZ2VyL2ltcGwvTmV0d29ya0NvbnN0cmFpbnRPYnNlcnZlci5qYXZhCkBAIC0zNiw3ICszNiw3IEBACiAgICAgICB9CiAgICAgfSwgbmV3IEludGVudEZpbHRlcihDb25uZWN0aXZpdHlNYW5hZ2VyLkNPTk5FQ1RJVklUWV9BQ1RJT04pKTsKIAotICAgIEV2ZW50QnVzLmdldERlZmF1bHQoKS5zdWJzY3JpYmUoTmV0d29ya0F2YWlsYWJsZUV2ZW50LmNsYXNzLCBUaHJlYWRNb2RlLk1BSU4sIGV2ZW50IC0+IHsKKyAgICBFdmVudEJ1cy5nZXREZWZhdWx0KCkucmVnaXN0ZXIoTmV0d29ya0F2YWlsYWJsZUV2ZW50LmNsYXNzLCBUaHJlYWRNb2RlLk1BSU4sIGV2ZW50IC0+IHsKICAgICAgIGlmIChOZXR3b3JrQ29uc3RyYWludC5pc01ldChhcHBsaWNhdGlvbikpIHsKICAgICAgICAgbm90aWZpZXIub25Db25zdHJhaW50TWV0KFJFQVNPTik7CiAgICAgICB9Cg==
```

For the whole inverse, actual baseline javac exit 0; inverse exit 1 with unresolved `Subscribe` and no Signal `EventBus.register(<anonymous Object>)`. The single-file check uses profile 5 build outputs, current imports, existing dependencies and the app owner Java task configuration described in the v1 reproduction recipe. It is not proof of independent line necessity, full-module compilation, runtime delivery, unique implementation or global minimality.

Reproduce using the v1 owner-scoped Gradle export of `:app:compileProdWebsiteDebugJavaWithJavac`: check out the baseline file privately, compile it once, apply this portable inverse and compile the resulting file once with identical flags and classpath. The actual JDK 21 javac flags were `-source 21 -target 21 -encoding UTF-8 -XDstringConcat=inline --system /var/home/x/.gradle/caches/9.6.0/transforms/ac7ebc01a1efefae22c644559f3a2c4c/transformed/output/jdkImage`, plus empty `-processorpath`, private `-sourcepath` and `-d`, exported 271-entry classpath and existing app Java and Kotlin output directories (the actual exported classpath already contained the Kotlin directory, which was also appended). No release or bootstrap override. The original config SHA-256 was `ffab777e0561a434e16d6784e457c09cdd051fbba92010566bb7069095a44718`. The source and classes were private and removed after the check.

## Kotlin indentation reversions

On PR5 source tree `68fa08fd69809463be3aa60ccd232bb50f02e547`, the baseline `:feature:app-settings:compileDebugKotlin` task was up to date and the build exited zero. The individual toggle, divider, and header inverses and their combined inverse each executed that task and exited zero. The command was `agent-heavy -- ./gradlew :feature:app-settings:compileDebugKotlin --max-workers=12 --console=plain`, with JDK 21.0.13+9 and Android SDK at `/var/home/x/Android/Sdk`. The combined patch has SHA-256 `e5c6baa3b440385f366f6398349e427d847191fd3ab4a837c1374cd4e54dea11`.

The tested input file blob `73d154a0b9277bdf16288b3fe7603d0f7e844235` also occurs in the previous PR3 and PR4 source trees; those branches were not compiled in these checks. The inverse changes only indentation; the whitespace-insensitive diff is empty. Against the mechanical file the physical count changed from 27 additions and 25 deletions to 12 additions and 10 deletions. Against the Molly source both counts are 296 additions and no deletions. Old Molly and `origin/signal-8.20.4` use `DataAndStorageSettingsFragment.kt` and lack these rows; `origin/upstream-stable` and `origin/upstream-main` contain the same rows at original indentation. This evidence covers only those three indentation reversions, not the remaining semantic content of the old aggregate hunk or global minimality.

Historical aggregate hunk `e0cd16838d2a30101790` splits after the indentation reversions into current hunks `be7d1e54b9b8f4b1ad49` and `1123d1a889fe94679d7d`. The semantic remainder of both current hunks is unmeasured; the three indentation checks above are partial evidence only.

## Socket and settings whole-hunk inverses

On original PR5 source `b9f7f076e5c84433ecee890741c2c044b75336f1`, tree `68fa08fd69809463be3aa60ccd232bb50f02e547`, the configured offline Kotlin build succeeded with its task UP-TO-DATE. Each portable forward patch below was reversed individually, restoring both files between cases. The command in each report row ran with JDK 21.0.13+9 and the Android SDK; the four inverse builds failed with exit 1. This was a cached build, not a clean build or full preflight. The original PR3 and PR4 source files have identical blobs, but these gates ran only on original PR5, not the newly replayed heads.

| Hunk | Original file blob | Inverse blob | Compiler failure |
|---|---|---|---|
| `0a71dc5fd4885bc8ea81` | `2404bc4683658f8234fef9fce31a3fe8a9c0c636` | `7606e4642d5cc4d78642168ca6651d1635778650` | suspend `removeDevice` outside coroutine |
| `2144342eb29427cab628` | `2404bc4683658f8234fef9fce31a3fe8a9c0c636` | `d9689366112e3fb9e1c29befd9c393bf23a3f89a` | suspend `loadDevices` outside coroutine |
| `bbe02e6ede61938f98c1` | `e9c5c0a040d80754b8e72019f5f7d6f89cbd2f8a` | `be34629b9f6cb01c22e4f07e68e335b4378b135a` | greenrobot imports and current subscription symbols unresolved |
| `42393357b34645095fb1` | `e9c5c0a040d80754b8e72019f5f7d6f89cbd2f8a` | `b286a9325d18d204d08927d45a0973dff2d31b2a` | `getDefault` and `Subscribe` unresolved |

The socket failures establish a coroutine call constraint, not the necessity of the annotation, assertion, or false `IOException`. The settings failures are conditional on the other current settings hunk remaining in place; they do not establish runtime or lifecycle behavior, retention, or uniqueness. These failures did not accept a source change.
