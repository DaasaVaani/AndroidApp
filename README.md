# DaasaVaani Android

Native Kotlin/Compose/Material3 app targeting API26+, app -> pure Kotlin core.
Application ID in.daasavaani.app. **Current implementation includes a native fixture
demo and separate read-only prelaunch lyric review, not a production/offline release.**
Explicit fixture preparation verifies all synthetic
records before canonical Kannada reading and indexed search. Prepared data is
memory-only. Favorites and UI language/theme use one durable Room database; durable
offline activation and complete feature parity remain unimplemented. Settings also
offers process-local minimal-list preview and complete file/link re-export without
saving lists or preparing/downloading lyrics.
Staged implementation is authorized; production gates remain.

Start with [AGENTS.md](AGENTS.md),
[requirements](https://github.com/DaasaVaani/DaasaVaani.github.io/blob/main/docs/requirements.md),
[current specification](https://github.com/DaasaVaani/shared/blob/main/docs/implementation-specification.md)
and [validation policy](https://github.com/DaasaVaani/DaasaVaani.github.io/blob/main/docs/testing-and-release.md).
Shared at shared/ is independently pinned contracts/fixtures/settings/UI resources,
not an app runtime. Clone recursively or initialize the submodule. Authorized pin
updates follow the [Shared-first publication order](https://github.com/DaasaVaani/DaasaVaani.github.io/blob/main/docs/testing-and-release.md#shared-submodule-updates):
publish and validate Shared before committing tested consumer pins.

## Prelaunch owner-supplied lyric review

The **Owner-supplied lyrics** entry is independent of fixture preparation, favorites,
Settings and list preview. Opening it does not download anything. Its explicit
**Prepare reading library** consent downloads and validates the complete canonical library
from the configured HTTPS descriptor at
`https://daasavaani.github.io/library/lyrics/latest.json`. No catalog browsing or
reading is available until every referenced item verifies. Selecting a prepared
title performs no network request. A title-substring filter is literal and case-insensitive,
not the fixture search index, a transliteration profile or portable Unicode folding.
Although the pinned Shared now supplies Unicode16 data, this reader does not adopt
personal-name keys or perform a Unicode/personal-data migration.
There is no video/image request, HTML rendering, link execution, generated text,
script switching or personal-save control on this surface.

This is the distinct
[Shared prelaunch contract](shared/schemas/lyric-publication.schema.json) and
[identity/layout definition](shared/docs/lyric-identity.md):
`lyric-preview-v1`, canonical Kannada, stable lyric UUIDs and explicit `line` versus
`layout` records. Real UUIDs never enter legacy `videoId` favorites/list records.
Text is rendered literally with spaces, tabs, blank layout records and line breaks
preserved. Visible bilingual notices disclose the prelaunch stage and incomplete
full-launch gates. It shares the existing UI locale and theme only.

`settings/android.yml` owns the endpoint and native safety ceilings: descriptor,
encoded/decoded manifest/item bytes, catalog count, line count, text code points,
tag count, title-query code points, JSON depth and aggregate encoded/decoded item
budgets checked before complete preparation. Network requests have bounded
connect/read/overall call timeouts, reject redirects/nonidentity HTTP encodings and stay on the configured
HTTPS origin with hash-derived object paths. The reader checks strict fields,
format/version/stage/capabilities, encoded SHA256 and exact encoded/decoded sizes,
UUID uniqueness, summary agreement, UTF-8/Unicode scalar validity, duplicate JSON
members and nesting limits before exposing content. Unsupported/oversized/invalid
data fails visibly; cancel/retry is explicit. Failed or cancelled preparation retains
the preceding completely validated set; partial catalogs/items are never activated.
Duplicate line identities are also rejected across the complete set.
Query rejection retains the last accepted query,
clears results and displays failure rather than truncating input.
Native byte ceilings are also checked against the pinned Shared portable role
maxima during configuration generation; they cannot silently raise portable bounds.

This data is process-local, read-only review state, **not durable offline preparation
or an activated snapshot**. Real-ID favorites/lists, durable snapshots, script/search
profiles, bookmarks, recents, density/font preferences and production linguistic/
release acceptance remain open. A pre-consent size preview and durable snapshot/
storage preparation remain later OFF-10 work; byte safety budgets and item progress
here are not claims of those features. Publication availability and actual real-content
native reading are separate gates; a missing feed is a blocker, never a fixture
fallback or evidence of successful owner-text review.

Partial requirement mapping: UX-01 (bounded canonical reader only), UX-03 (explicit
line/layout preservation without inferring verses), LANG-01 (no translation),
OFF-01/OFF-10 (explicit complete canonical preparation gate only),
I18N-01/02/03 and current theme preferences. This does not close their complete
acceptance criteria or authorize a personal-contract migration.

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

### Minimal list preview and re-export

Settings → List snapshot preview is reachable before preparation and regardless of
personal-storage availability. Choose a local document or explicitly paste a
complete HTTPS snapshot link. The picker grants access only to the selected document;
the app takes no persistent document permission. No incoming intent/App Link
association is added. Preview and re-export never write Room, fetch lyrics, mark
content ready, match text to catalog items or upload lists.

Pure Kotlin `core/ListTransfer.kt` implements the pinned
[minimal v1 contract](shared/docs/list-transfer.md). Names, placeholder text,
explicit user fallback, opaque reference IDs, repetitions and order remain literal.
Only the exact format/version/name/entries allowlist is accepted, with exact entry
allowlists. Strict JSON syntax, duplicate fields, malformed UTF8/scalars, C0/C1
controls and line separators are rejected. One optional leading BOM counts toward
the decoded byte limit. Compact exported UTF8 has no BOM; canonical Kannada synthetic
test text is not translated or normalized.

Owning `shared/settings/personal-transfer.yml` supplies encoded9MiB, decoded8MiB,
whole-link2048 ASCII, code-point and occurrence bounds through `npm run config`.
`settings/android.yml` independently owns canonical HTTPS base, open path, export
filename and compression level. The current canonical wire is
`https://daasavaani.github.io/open/#/list/<raw-deflate-base64url>`; the same HTTPS
origin's root path is also accepted. Credentials, query, whitespace, other paths,
noncanonical base64url/pad bits and trailing compressed data are errors. No link
is shortened, truncated or silently changed into another transport.

Gzip decoding parses the header and bounded optional fields, validates optional
header CRC, raw DEFLATE termination, CRC32 and unsigned length, and requires exactly
one member with no trailing byte. It does not use concatenation-accepting
`GZIPInputStream`. No compression-ratio rejection is invented. Input is counted
before retention past the encoded ceiling; inflation stops on decoded ceiling+1.
Parsing has fixed structural depth and bounds every string/entry before retention.
Work is O(encoded + decoded + occurrences), with memory bounded by the configured
input/output ceilings plus bounded parsed strings/entries. Fixed-size I/O/inflate/
deflate chunks, header/string/whitespace/entry loops check cancellation off the UI
thread. A cancelled document operation also closes its active stream.

Rows distinguish not-yet-resolved references before preparation, known references
against only the verified in-memory catalog, and absence from that available catalog.
Literal placeholders never become references. Known catalog titles are display-only
and never copied into transfer payloads; no offline availability is inferred.
Complete gzip export uses Android's Create Document adapter; complete canonical
links are displayed/selectable and can be explicitly copied. Oversized links explain
the lossless file fallback. System provider/clipboard errors are explicit, not
reported as success. A failed/cancelled document write attempts deletion of only its
new destination; providers can retain an incomplete copy, so destination cleanup and
retry remain necessary. No delivery guarantee, encryption, revocation or verified
OS share/App Link interoperability is claimed.

There is deliberately **no Save/import-to-library control**, list persistence or
automatic recovery of a preview after process death. This is partial
XFER-01/02/03/05/06/08 evidence, not full transfer acceptance, native feature parity,
full backup correctness or release readiness.

### Remaining approved slices

This batch does **not** implement persistent recitation lists/list-import Save, full personal
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

Core JVM tests cover search, first-use/override locale selection, prelaunch lyric
contracts/encoded verification and the transfer codec. Prelaunch vectors use only
synthetic text and cover strict fields/version/capabilities, duplicate UUIDs/JSON
members, malformed UTF-8/scalars, reference/path/size/hash limits, summary mismatch,
line/layout distinctions, literal whitespace, query bounds, cancellation and retry.
Native synthetic instrumentation also exercises the complete descriptor/manifest/item
chain, explicit complete-preparation gating, read-only UI/personal isolation,
filter/reader text and cancel/retry/reload-failure preservation. It is not a live
feed or real-content demo.
Transfer tests use actual9MiB/8MiB/2048 ceilings and ceiling+1 (including a
valid9MiB single gzip member, valid high-compression8MiB JSON and complete2048/
2049 canonical URLs), literal/repeated round trips, pinned Shared acceptance/
rejection fixtures and an independent Node/zlib deterministic fixture vector.
They reject malformed JSON/UTF8/Unicode, duplicate/unknown fields, unsupported
versions, gzip truncation/CRC/length/concatenation/trailing bytes and observe
cancellation. Instrumentation
uses real Room/SQLite and Compose: reopen persistence, unavailable IDs, limits,
stale writers, rejected records/schemas, future-version preservation, v1 migration,
settings/favorites before preparation and preparation-to-canonical-reading.
The native transfer journey covers pre-preparation link/file-stream preview,
no Save, cancellation, invalid input/provider failure, known/pending/unavailable
catalog states, complete link re-export and unchanged favorites/preferences/theme.
Document picker/provider interoperability across real devices, destination failure
semantics, signed App Links and OS sharing remain required later acceptance gates;
injected local streams do not establish those claims.
App JVM tests currently have no sources; do not count NO-SOURCE as a pass.
The debug APK allows cleartext only for the local fixture endpoint; release does not.
Install the debug APK from app/build/outputs/apk/debug/app-debug.apk for a local
simulator demo; it contains no lyrics. The fixture demo requires the local Website
server and explicitly discloses synthetic content and unavailable features. The
separate owner-supplied reader uses the configured live HTTPS publication instead.

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
