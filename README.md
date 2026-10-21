# DaasaVaani Android

Native Kotlin/Compose/Material3 app targeting API26+, app -> pure Kotlin core.
Application ID in.daasavaani.app. **Current implementation is a native fixture demo,
not a production/offline release.** Explicit preparation verifies all synthetic
records before canonical Kannada reading and indexed search. Prepared data is
memory-only. Favorites and UI language/theme use one durable Room database; durable
offline activation and complete feature parity remain unimplemented.
Staged implementation is authorized; production gates remain.

Start with [AGENTS.md](AGENTS.md),
[requirements](https://github.com/DaasaVaani/DaasaVaani.github.io/blob/main/docs/requirements.md),
[current specification](https://github.com/DaasaVaani/shared/blob/main/docs/implementation-specification.md)
and [validation policy](https://github.com/DaasaVaani/DaasaVaani.github.io/blob/main/docs/testing-and-release.md).
Shared at shared/ is independently pinned contracts/fixtures/settings/UI resources,
not an app runtime. Clone recursively or initialize the submodule. Authorized pin
updates follow the [Shared-first publication order](https://github.com/DaasaVaani/DaasaVaani.github.io/blob/main/docs/testing-and-release.md#shared-submodule-updates):
publish and validate Shared before committing tested consumer pins.

## Current fixture behavior and boundaries

- No bundled lyrics. Explicit validated full preparation gates lyrics, not
  Settings/recovery/list preview. Preserve destinations without automatic saving.
- Complete preparation is memory-only; failures preserve the prepared publication
  in the running process. A new process requires preparation again. There are no
  offline video/images or persisted content-readiness flags.
- Kannada canonical, transliteration only, independent UI/script preferences,
  system-font limitations and explicit alternatives.
- One personal Room database contains only favorite video IDs and UI language/theme
  preferences, separate from downloaded content and preparation control. Favorites
  are accessible before preparation; unavailable IDs remain visible/removable and
  are never guessed or silently deleted. Reading still requires complete preparation.
- First-use UI language follows the ordered supported Android locales, otherwise
  English. A saved English/Kannada override is independent of canonical Kannada
  lyrics. Theme follows Android by default or retains an explicit light/dark choice.
  Warm is also available as an explicit retained theme. Settings remain reachable
  before preparation and after storage failure.
- Configurable product values in settings, portable policy in Shared. Native
  dependency/toolchain manifests/catalogs/wrappers/locks own pins. AGP9 built-in
  Kotlin for app; core kotlin.jvm, Compose/serialization compatible Kotlin versions.
  Current debug tooling uses JDK21/SDK36/Gradle9.1; the production/release and
  supported-device matrix remains incomplete.

### Personal schema, transactions and recovery

`personal.db` is the only personal database. Room schema2 has one required
`preferences` singleton (`id=1`, `language`, `theme`, nonnegative `revision`) and
`favorites` keyed by strict11-character video ID. Device language/theme are stored
as `device`, not a frozen copy of the current device settings. Room schemas are
committed under `app/schemas/`; Java annotation processing generates Room code.

The supported fixture v1 migration input has the same tables but no `theme` column.
The explicit v1→2 migration adds `theme=device` without replacing records. It is a
synthetic migration fixture, not a claim that an earlier production personal schema
was shipped. Before Room opens an existing file, read-only schema/record/version
validation refuses unknown tables/columns, missing/extra singletons, malformed
records and future versions. There is no destructive migration/downgrade fallback,
automatic reset or advice to uninstall/delete data. Failure preserves the original
records; retry is explicit and writing is disabled until storage reload succeeds.

Every write rechecks the expected revision and validates the whole next snapshot
inside one Room transaction. A stale writer must reload before retrying. The count
and deterministic UTF8 logical-envelope bounds come from pinned
`shared/settings/personal-transfer.yml`, including preference/revision/header
overhead. This envelope is storage accounting, not an implemented backup format.
No optimistic success is shown before commit. No preferences are duplicated in
SharedPreferences or a separate database.

Future-schema failures explicitly request a newer app while preserving data.
Malformed/unsupported storage explains preservation and supported-app recovery;
ordinary write failures expose explicit retry without advising data deletion.
Loading, follow-device, Warm and unavailable-favorite labels use the pinned
bilingual Shared catalog.

### Remaining approved slices

This batch does **not** implement recitation lists/list transfer, full personal
backup/restore, system backup, bookmarks/recents/learning state, persistent offline
snapshots, production migrations or full Android parity. Android system backup is
still disabled rather than backing up false content readiness. Remaining selected
directions include personal-only backup allowlists, same-platform recoverable full
replacement with revision/epoch/locale recovery, and truthful postcommit OS locale
effects. The current locale change affects this app's Compose resources, not Android
per-app locale settings or a backup-restore protocol.

Persistent content still needs active-version preservation, explicit validated
refresh, UIDT34+/FGS26-33 progress/cancel/restriction handling and verified-artifact
resume. Root `/open/` associations require narrow scope and signed-device
verification. Release direction remains CI unsigned candidate → maintainer local
verify/sign → protected verification/GitHub Releases; exact handoff is open and no
signing key belongs in CI/repository/logs.

Partial requirement coverage: LIB-01/LIB-04 (favorites only), I18N-01/02/03,
SET-04 (UI language/theme only), QUAL-05/06 (local-only state and explicit errors).
Canonical Kannada and required preparation remain unchanged. This is neither
complete requirement acceptance nor production/release readiness.

## Validation

```powershell
npm ci
npm run config
.\gradlew.bat :core:build :app:build :app:lintDebug :app:lintRelease :app:connectedDebugAndroidTest --no-daemon
python tools\check_repository.py
git --no-pager diff --check
git --no-pager diff --cached --check
```

Set JAVA_HOME to JDK21 and ANDROID_HOME to the SDK with platform/build-tools36.
Connected tests require an intended API36 emulator selected with ANDROID_SERIAL and
the Website demo at the endpoint in settings/android.yml. Configuration is generated
from owned settings and pinned Shared policy. Never target another user's device.
The Gradle wrapper distribution and wrapper JAR have checked SHA256 pins.

Core JVM tests cover search and first-use/override locale selection. Instrumentation
uses real Room/SQLite and Compose: reopen persistence, unavailable IDs, limits,
stale writers, rejected records/schemas, future-version preservation, v1 migration,
settings/favorites before preparation and preparation-to-canonical-reading.
App JVM tests currently have no sources; do not count NO-SOURCE as a pass.
The debug APK allows cleartext only for the local fixture endpoint; release does not.
Install the debug APK from app/build/outputs/apk/debug/app-debug.apk for a local
simulator demo; it contains no lyrics. The current demo requires the local Website
server and explicitly discloses synthetic content and unavailable features.

CI builds all variants/core/JVM/lint; it does not claim a connected-device run.
The identical named job runs on `validation/**` pushes before direct-main
publication and again on main. Wait for the actual **Bootstrap files and YAML
syntax** job, not just an aggregate workflow conclusion. The existing explicit
admin PR exception is not permission to weaken checks or force-push. Delete only
the known merged validation branch after main's actual job passes.
Its Ubuntu24.04 host and SDK bootstrap action are pinned; SDK availability is
provisioned explicitly, not inferred from a runner image or PATH.
Platform selection comes from Android settings; build-tools come from the native
version catalog used by Gradle. CI uses the current Android CLI and verifies the
installed platform/build-tools files before attempting compilation.
The configured connected suite is required locally before finalization/push.
Unsigned release compilation is not a signed release or complete release evidence.
CI also parses .github YAML. Include new files and all documentation links/fences.
Python/whitespace checks are hygiene, not Android builds/tests. Focused incremental tests
are allowed; finalization/confirmation and every push require full applicable
builds/all suites. Documentation-only changes are not automatically exempt now
that app tooling exists. Missing required
code checks block completion/push. Passing checks is not push authorization.

Update current docs alongside changes; archive past decisions/observations/check
records only in [HISTORY.md](HISTORY.md). Preserve unrelated work and Shared pins.
Use issue/PR templates and [security reporting](SECURITY.md). Software is
[Apache2.0](LICENSE); third-party content retains its rights.
