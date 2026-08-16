# MollyKids

**MollyKids** is a downstream fork of [Molly](https://github.com/mollyim/mollyim-android) — a
security-hardened Signal client — that adds parental controls for family use. It lets children
communicate with pre-approved family members via Signal-compatible messaging on semi-locked-down
tablets, while keeping parents in full control of who the child can reach.

---

## Why MollyKids?

Signal is excellent for secure family communication, but it has no concept of a restricted
child account. MollyKids adds a PIN-gated parental layer that:

- Shows children **only the group chats parents explicitly allow** — no contacts list, no new
  conversations
- **Blocks outbound messaging** to any thread not on the allow-list
- **Suppresses notifications** from non-allowed threads so nothing leaks through
- **Hides Stories, account settings, and linked-device management** from the child
- Lets parents manage the allow-list and change the PIN via a PIN-gated Settings panel on the
  device itself

Registration uses **free Google Voice numbers** (one per child) so children never expose their
own phone numbers. After setup, communication happens via Signal usernames — no numbers visible
to the child at all.

---

## How It Works (Architecture)

All parental-control code lives behind a **`kids` Gradle build flavor**. The standard `prod`
and `staging` flavors are completely unaffected — zero changes to shared Signal protocol,
crypto, database, or network code.

Key additions:

| File / Package | What it does |
|---|---|
| `app/src/kids/` | Flavor-specific overrides |
| `app/src/main/.../keyvalue/ParentalControlValues.kt` | PIN storage (SHA-256 + random salt) and allowed-thread list |
| `app/src/main/.../parental/` | Parent Settings UI — Activity, ViewModel, Fragments, PIN dialogs |
| Guard clauses in `ConversationListFragment`, `MainActivity`, `NotificationController`, etc. | `if (SignalStore.parental().parentalModeEnabled)` checks that activate restrictions |

The `kids` flavor is **opt-in at build time**. A family builds the `kidsRelease` APK; a privacy
researcher builds the standard `prodRelease` APK. Both come from the same source tree.

---

## Build Instructions

**Prerequisites:** Android Studio, JDK 17+, standard Molly build dependencies
(see [Molly's README](README.md)).

```bash
# Build the kids flavor APK
./gradlew assembleKidsRelease

# Run parental-control unit tests
./gradlew testKidsDebugUnitTest
```

The kids flavor APK can be sideloaded onto a child's device. After installation, parents
enable Parental Mode from within the app using the PIN-gated Parent Settings screen.

---

## Implementation Status

| Phase | Feature | Status |
|---|---|---|
| 0 | Kids Gradle flavor scaffolding | Done |
| 1 | `ParentalControlValues` — PIN + allow-list storage | Done |
| 2 | Conversation list filtering | Done |
| 3 | Block new outbound conversations | Done |
| 4 | Gate group invites behind PIN | Done |
| 5 | Suppress notifications for non-allowed threads | Done |
| 6 | Hide Stories tab and lock account settings | Done |
| 7 | Parent Settings UI (PIN-gated panel on device) | Done |
| 8 | Registration guide and first-run setup flow | Not started |

62 unit tests cover the parental control layer; all pass on the current build.

---

## Upstream Compatibility

MollyKids tracks upstream Molly releases. The parental-controls branch is kept rebased/merged
against Molly's main; the most recent sync was **Molly v8.7.3-2** (2026-04-29) with zero merge
conflicts.

When Molly releases a new version, the merge process is:

```bash
git fetch upstream
git merge upstream/main   # or the relevant release tag
# Resolve any conflicts (historically: none in parental files)
```

---

## License

AGPLv3, same as Molly and Signal. If you distribute this app (outside your own household),
the source must remain publicly available — which it is, right here.

---

## Relationship to Molly

MollyKids is a **downstream consumer** of Molly, not a competing project. Molly's security
hardening (encrypted database, app lock, no Google tracking) is exactly why it's a good
foundation for a children's device. We rely on Molly's security properties and contribute
nothing back that changes them.

If the Molly project ever wants to include parental controls as an opt-in build flavor,
we're happy to discuss upstreaming this work.
