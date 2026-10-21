# Documentation history

## Archive policy

This is the sole archive for past decisions, superseded guidance, dated
observations and validation records in this repository. It is not current
implementation guidance or evidence of current configuration. Start with
[AGENTS.md](AGENTS.md) and [README.md](README.md) for current instructions.

## 2026-10-21: current-only documentation migration

The owner requested root AI instructions and one history archive per
repository. Incremental checks may be focused; finalization and pushing
require complete applicable builds/tests. Documentation-only changes may
use all available documentation/bootstrap checks while app tooling is absent.
The snapshots below preserve documents before reconciliation, including
then-current approvals and unresolved questions. Their statements may
conflict; consult current documents, never use snapshots as approval.


## 2026-10-21: AI/documentation validation record

- Root AGENTS/HISTORY and current-only README established; original snapshot below.
- `python tools\check_repository.py`: passed16 tracked files, including new docs.
  `git --no-pager diff --check` and `git --no-pager diff --cached --check`: passed.
- Cross-repository Markdown audit passed37 files including links/anchors/fences
  and153 unchanged Website requirement IDs.
- Ruby/YAML syntax tooling unavailable locally, no workflow YAML changed. No
  Gradle/build/JVM/instrumentation tooling exists; no Android test/build pass claimed.
  The owner approved documentation-only all-available checks while tooling is absent.
- No code, dependency/Shared-pin, commit/push, release/signing or protection change.

## 2026-10-21: direct-main publication authorization

The owner requested committing and pushing all current/staged changes to main
without PRs. Scope: README, root AI instructions and history; no Android code,
Gradle tooling, release or Shared-pin update. Rerun all available documentation/
bootstrap checks before publication; absent Android builds/tests are not passes.

## 2026-10-21: coordinated Shared pin correction

- The owner authorized correcting the stale Shared pin from
  `5a2446c31ffb97c9899a7ba3a287e58b2b2eb490` to
  `dbaac0d4c5d255a0c6663d9c21fa2b918761e027`. That exact target was already
  published and passed Shared Repository hygiene run39576588515 before the
  consumer update. Intervening upstream changes are documentation only.
- Updated the gitlink and linked the Shared-first publication procedure; kept
  the submodule detached at the exact target, with no local modifications.
- `python tools\check_repository.py`: passed16 consumer files.
  `python shared\tools\check_repository.py`: passed15 pinned Shared files.
  `git --no-pager diff --check`: passed.
- `python <session-folder>\files\validate-shared-pins.py`: passed58 Markdown files
  including the three pinned checkouts, links/anchors/fences/encoding/whitespace,
  exact clean Shared revisions and153 unchanged Website requirement IDs.
- No Android build/JVM/instrumentation or contract runner exists; no such pass
  is claimed. No workflow YAML changed and local YAML tooling is unavailable.
  Scope is documentation/gitlink metadata under the bootstrap exception.

## Snapshot: README.md

# DaasaVaani Android

Bootstrap for the native Kotlin/Compose Android app, targeting API 26 and above.
The intended structure is an app plus pure Kotlin core modules, with hybrid
feature presentation and shared infrastructure. Application ID `in.daasavaani.app`
is selected; no application/Gradle manifests or verified toolchain integration
have been generated here.

[Shared](https://github.com/DaasaVaani/shared) is a language-neutral source
dependency at `shared/`, not a shared application runtime. It owns normative
contracts, portable fixtures and reviewed YAML UI catalogs. This app pins its
Shared commit independently from Website and Library; updates are not lockstep.
Shared owns the catalog generator and commits Android/web generated UI resources
with source changes. Exact schemas/generation remain unimplemented.

See the [approved implementation-design record](https://github.com/DaasaVaani/shared/blob/main/docs/implementation-specification.md)
for earlier review-pass choices, requirement/test traceability and open gates.
This documentation does not declare final completeness or authorize app coding.

## Approved Android implementation boundaries

- All configurable public identity/endpoints/UI/transfer/resource values originate
  in reviewed Android settings, with portable mappings/search policy owned in
  Shared settings. No signing secret, hidden default or personal state there.
- Keep every backup-covered personal record/preference in one transactional Room
  database, separate from content/control data. This supersedes splitting backed-up
  preferences into DataStore. Auto Backup includes only the personal database.
- Manual full personal restore is confirmed recoverable replacement. Before
  commit, failure/cancel changes nothing; after commit an OS-locale failure must
  report restored data plus the unapplied-language retry, not false no-change.
- No bundled lyrics. Explicit validated full preparation gates lyric use, not
  Settings/recovery/list management. Preserve pending links without auto-saving.
  Prepared online/offline reads keep the active snapshot until explicit update.
- UIDT on API 34+, foreground-service transfers on 26-33; verified-artifact resume,
  explicit restrictions and safe prior-snapshot preservation.
- Canonical app-opening links use the website's `/open/` hash entry, narrow path
  association and root certificate association file. Signed-device verification
  and browser/non-handoff recovery are still required.
- System fonts only; disclose confirmed unsupported renderings and offer an
  explicit alternate rather than silently switching or bundling fonts.

## Release handoff (not implemented)

CI builds an unsigned candidate; the maintainer verifies its build/source
association and signs locally with the backed-up release key. A protected
workflow verifies package/version/certificate/artifact association before
publishing the signed APK in this repository's GitHub Releases. No signing key
enters CI or logs. Exact verification commands/handoff and device evidence remain
gates; no successful build/sign/release is claimed.

The corrected researched Kotlin candidate is 2.4.20, specifically listed by
Koin compiler 1.2.1, not verified 2.4.21. AGP 9 uses built-in Kotlin in the app
(no separate kotlin.android plugin); core uses kotlin.jvm and Compose/
serialization track the chosen Kotlin version. This is not an integration build.

The [Website](https://daasavaani.github.io/) is an independently deployed
React/strict TypeScript/Vite static PWA. Android is not embedded in that checkout.

## Contributing and validation

Clone with `git clone --recurse-submodules https://github.com/DaasaVaani/AndroidApp.git`,
or run `git submodule update --init` after cloning.
Use the issue forms and PR template; get agreement before resolving open design
choices. Preserve accessibility, offline behavior and script independence.
Never commit credentials, signing keys or private data.

Run `python tools/check_repository.py` and `git diff --check` from this root.
CI also parses YAML syntax. These are repository-hygiene checks, not Android
builds, device tests, contract conformance tests or a security audit. Add Gradle
dependency updates and real app checks once the relevant manifests exist.

Project-authored software follows the existing DaasaVaani [Apache 2.0 license](LICENSE).
Third-party lyrics, descriptions, images, recordings and trademarks are not
relicensed by that software license; preserve rights, attribution and notices.
See [SECURITY.md](SECURITY.md) for private vulnerability reporting.
