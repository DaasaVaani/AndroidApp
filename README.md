# DaasaVaani Android

Native Kotlin/Compose/Material3 app targeting API26+, app -> pure Kotlin core.
Application ID in.daasavaani.app. **Current implementation includes a native fixture
demo and separate prelaunch canonical lyric reader, not a production/offline release.**
Explicit fixture preparation verifies all synthetic
records before canonical Kannada reading and indexed search. Prepared data is
memory-only. Typed lyric/legacy favorites, recitation lists/independent occurrences
and UI language/theme/lyric reading preferences use one durable Room database. Settings offers process-local
v1/v2 minimal-list preview, confirmed new-list-only import and complete file/link
export. None of these operations prepares/downloads lyrics. Durable offline
activation, recitation playback/resume and complete feature parity remain unimplemented.
Device-only Recently opened records the latest configured distinct successful
explicit reader opens, independently of favorites/lists and content preparation.
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

The **Owner-supplied lyrics** entry is independent of fixture preparation,
Settings and list preview. Opening it does not download anything. Its explicit
**Prepare reading library** consent downloads and validates the complete canonical library
from the configured HTTPS descriptor at
`https://daasavaani.github.io/library/lyrics/latest.json`. No catalog browsing or
reading is available until every referenced item verifies. Selecting a prepared
title performs no network request. A title-substring filter is literal and case-insensitive,
not the fixture search index, a transliteration profile or portable Unicode folding.
Title filtering remains distinct from portable Unicode16 personal-list-name comparison.
The prepared canonical title catalog initially shows the pinned Shared search page
size. **Show more results** expands by that size through the complete result set,
with the existing localized shown/total count. Its literal query, expanded pages
and browse viewport are independent of Favorites and survive canonical reader/
Settings navigation within the current session. The reader arrow and Android Back
return to that catalog context; Settings entered from the canonical catalog/reader
also supports Android Back. An available visible originating title regains native
focus. The reader has a separate viewport, so opening it does not consume the
catalog position. Query edits/ordinary clear reset pages and position; invalid
queries retain accepted text, clear results and show failure, not a false no-match.
Explicit replacement clamps shortened results to a valid context; it cannot
promise an unavailable old title/position. A failed preparation retains the active
catalog and expansion. Session browsing state and prepared content are not saved
to Room or restored after an app/process restart. Navigation/filtering/paging
neither prepares content nor writes personal data or creates a fixture index.
Returning or changing reading preferences does not create another history open.
Favorites has its own independently retained literal title filter over only the
already verified real catalog. It never matches lyric UUIDs, tags, unavailable
references or legacy fixture titles. With an empty filter, every saved typed
reference remains available, including unresolved and legacy references before
preparation. Filtering does not initiate preparation.

Favorites initially shows the pinned Shared search page size; **Show more results**
expands by that same size, with a localized shown/total count and no unreachable
tail at exact page boundaries. Editing or ordinarily clearing the title field
resets expansion and scroll; rejected oversized/invalid queries retain the last
accepted text, clear results and show failure rather than a false no-match.
The real-catalog title query is independent. Switching sections and returning
from a favorite reader retains the Favorites query, expansion and list viewport.
Android Back and the arrow/Favorites action return to that context, including a
pending unprepared reference; removal/content changes clamp to available rows
rather than promise an obsolete exact position. These are process-local browsing
states, not persisted search history. Filtering, paging and navigation do not
write personal data or fetch content. Successful explicit reader exposure still
records Recently opened when its existing initially-on preference is enabled;
explicit Favorite/Add to list actions retain their normal transactional writes.
Title matching scans the bounded verified summaries, then uses exact typed-ID
membership while preserving saved-reference order. Working reference storage is
O(catalog items + favorites), within their configured ceilings; page expansion
uses overflow-safe arithmetic and never copies lyric bodies.

There is no video/image request, HTML rendering, link execution, generated text,
script switching on this surface. Reader Favorite and Add to a saved list operate
on a typed real lyric UUID, without inventing a video association.

This is the distinct
[Shared prelaunch contract](shared/schemas/lyric-publication.schema.json) and
[identity/layout definition](shared/docs/lyric-identity.md):
`lyric-preview-v1`, canonical Kannada, stable lyric UUIDs and explicit `line` versus
`layout` records. Real UUIDs never enter legacy `videoId` favorites/list records.
Text is rendered literally with spaces, tabs, blank layout records and line breaks
preserved. Visible bilingual notices disclose the prelaunch stage and incomplete
full-launch gates. It shares the existing UI locale, theme and independent lyric font/density preferences.

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

Prepared lyric content is process-local review state, **not durable offline preparation
or an activated snapshot**. Durable snapshots, script/search
profiles, bookmarks and production linguistic/
release acceptance remain open. A pre-consent size preview and durable snapshot/
storage preparation remain later OFF-10 work; byte safety budgets and item progress
here are not claims of those features. Publication availability and actual real-content
native reading are separate gates; a missing feed is a blocker, never a fixture
fallback or evidence of successful owner-text review.

Partial requirement mapping: UX-01 (bounded canonical reader only), UX-03 (explicit
line/layout preservation without inferring verses), LANG-01 (no translation),
OFF-01/OFF-10 (explicit complete canonical preparation gate only),
I18N-01/02/03, UX-13 (bounded canonical catalog/session return only)
and current theme preferences. This does not close their complete
acceptance criteria. Typed personal references and lists have the separate bounded
implementation described below; this does not establish offline parity.

## Current fixture behavior and boundaries

### Independent lyric typography

Settings and both actual canonical/fixture readers provide localized A−/A+ lyric
font controls, the current native `sp` value, and three distinct Compact/Normal/
Relaxed density choices. `sp` respects OS font scaling; title/navigation/UI font
sizes do not change. UI language/theme and canonical lyric text remain independent.
No cross-platform font-unit conversion, script choice or inferred verses are added.

`settings/android.yml` owns the native 28sp initial font, 14–48sp inclusive bounds,
2sp step, 48sp normal line height, 20dp normal fixture line separator and
85/100/120-percent density multipliers. Normal preserves the preceding appearance
exactly. Line height scales with the chosen font relative to the initial font and
with density; fixture separators scale with density. Canonical readers retain the
same single literal string with every line/layout separator, space and tab.
Fixture readers retain their separate identified line records and wrapping.
No line is collapsed, trimmed, reidentified or treated as a verse.
Pinned Shared defaults own initial Normal and the supported density order.
Configuration generation rejects missing, invalid, off-step or unsupported values;
there is no hidden runtime fallback for invalid saved preferences.

Room5/logical accounting5 adds only `lyricFontSizeSp` and `readingDensity` to the
existing preferences. Frozen old schema/accounting adapters validate both old and
prospective complete UTF8 personal envelopes before atomic migration. Favorites,
lists, repeated occurrences, typed IDs, history, locale/theme and revision are
preserved; future, invalid and over-capacity state is refused without reset.
Reading changes update only their preference columns plus revision, never rewrite
other personal tables or store lyric text. Same-density/bound actions are no-ops.
Queued rapid actions use actual preceding local commits; peer-writer revision/
epoch conflicts remain explicit stale refusals until retry, never silent rebases.
Loading/rendering uses the existing singleton without a preference mutation or
revision increment.

Preference changes neither prepare content nor create another reader-open receipt.
A still-mounted reader stays history-cleared after another writer clears history,
including after explicit stale recovery and typography changes. Existing query,
page, reader-return and list-origin state is retained. This is partial UX-02/03/14
and SET-04 coverage, not Recite/Practice/helpers, offline support, full accessibility
or real-device/release acceptance.

- No bundled lyrics. Explicit validated full preparation gates lyrics, not
  Settings/recovery/list preview. Preserve destinations without automatic saving.
- Complete preparation is memory-only; failures preserve the prepared publication
  in the running process. A new process requires preparation again. There are no
  offline video/images or persisted content-readiness flags.
- Kannada canonical, transliteration only, independent UI/script preferences,
  system-font limitations and explicit alternatives.
- One personal Room database contains typed lyric/legacy favorites, saved lists and
  independent occurrences, plus UI language/theme preferences, separate from downloaded content
  and preparation control. Favorites and lists
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

`personal.db` is the only personal database. Room schema5 has one required
`preferences` singleton (`id=1`, `language`, `theme`, nonnegative `revision`,
`recentHistoryEnabled`, `lyricFontSizeSp`, `readingDensity`) and
`favorites` keyed by `(kind,value)` with explicit positions, ordered `lists` and
ordered `occurrences`, plus ordered typed `recents`. Lyric references require canonical lowercase UUIDv4;
`legacyVideo` preserves the opaque case-sensitive ASCII1–128 domain. An old value
that resembles a UUID remains legacy; no mapping or media association is inferred.
Device language/theme are stored
as `device`, not a frozen copy of the current device settings. Room schemas are
committed under `app/schemas/`; Java annotation processing generates Room code.

The supported fixture v1 migration input has only strict legacy favorites and
language/revision preferences, without a `theme` column.
The explicit v1→2 migration adds `theme=device`. The v2→3 migration structurally
adapts each old favorite to `legacyVideo` and preserves its value, old query order,
preferences and revision. Old favorites had no saved ordinal: their existing
`ORDER BY videoId` is retained once; new typed favorites retain insertion order.
The v3→4 migration preserves every exact favorite/list/occurrence, language/theme
and revision, and creates empty history with the pinned Shared initial preference.
No prior opens are reconstructed. The v4→5 migration adds configured native font
and Shared density defaults without changing prior values or revision.
Supported v1/v2 inputs chain through these
same migrations. Each reopened store has a fresh process-local epoch; history
transactions participate in the existing revision/epoch fences, including pending
imports and duplicate-name confirmations.
The actual old schema and records (strict11-character IDs only for v1/v2;
exact typed IDs for v3/v4) are validated before opening
Room and rechecked within the migration transaction. Both old accounting and the
projected new envelope must fit before migration. These are
synthetic migration fixtures, not a claim that an earlier production personal schema
was shipped. Before Room opens an existing file, read-only schema/record/version
validation refuses unknown tables/columns, missing/extra singletons, malformed
records and future versions. There is no destructive migration/downgrade fallback,
automatic reset or advice to uninstall/delete data. Failure preserves the original
records; retry is explicit and writing is disabled until storage reload succeeds.

Every write rechecks the expected revision and process-local store epoch and validates the whole next snapshot
inside one Room transaction. A stale writer must reload before retrying. The count
and deterministic UTF8 logical-envelope bounds come from pinned
`shared/settings/personal-transfer.yml`, including preference/revision/header
overhead. The compact UTF8 accounting envelope is exactly
`{formatVersion:5,revision,preferences:{language,theme,lyricFontSizeSp,readingDensity},favorites:[typed-reference],
lists:[{id,name,entries:[typed-entry-with-id]}],recentHistory:{enabled,entries:[typed-reference]}}`
in that field order, using
`kotlinx.serialization` JSON string quoting and no spaces/BOM. Entries emit
`kind`, present `videoId`/`lyricId`, present `fallback`/`text`, then `id`.
The epoch is not persisted/accounted and changes when the store closes/reopens.
Accounting counts UTF8 incrementally without constructing a whole backup string.
This envelope is storage accounting, not an implemented backup format.
No optimistic success is shown before commit. No preferences are duplicated in
SharedPreferences or a separate database.

Room reads group occurrences once by list rather than scanning all occurrences
per list. Reading changes validate/account the complete snapshot but update only
the reading preference columns/revision. Other writes retain whole-snapshot
replacement. Validation/accounting and replacement are linear in
favorites + lists + occurrences + recents + bounded JSON/text bytes, apart from SQLite
ordering/index maintenance and the separately described Unicode name-key work.
Memory includes the bounded personal snapshot and one bounded list JSON at a time;
capacity checks precede replacement and aggregate row-count checks precede loading.
The process-local successful-open queue is bounded by the configured recent-entry
ceiling, with explicit failure rather than silent loss if full.

Future-schema failures explicitly request a newer app while preserving data.
Malformed/unsupported storage explains preservation and supported-app recovery;
ordinary write failures expose explicit retry without advising data deletion.
Loading, follow-device, Warm and unavailable-favorite labels use the pinned
bilingual Shared catalog.

### Device-only Recently opened

Library → Recently opened and Settings provide an independent recording switch
and Clear, usable before content preparation. The
[Shared history contract](shared/docs/recent-history.md) owns the reference
shape, exact identity and mutation rules. `shared/settings/defaults.yml`
owns the initial-on preference; a saved false always wins.
`shared/settings/personal-transfer.yml` owns the latest50 distinct-entry ceiling.
`npm run config` generates the native/core policy directly from those pinned files.

Only a successful explicitly requested real or fixture reader exposure records an
open. The real surface acknowledges the request when its verified text enters the
reader viewport (not lazy-list prefetch);
the fixture reader acknowledges its verified selection. ViewModel-held one-shot
request receipts survive recomposition/screen remounts and are consumed even when
recording is disabled or fails. Preparation, catalogs, search results, list
previews, background selection and unavailable/failed destinations do not record.
An explicit pending real destination can record only after complete preparation
and actual reader exposure. Selecting a prepared record never downloads anything.

Entries contain only kind/exact ID and explicit most-recent-first order: no times,
titles, queries, text, script or invented verse metadata. Reopening an already-first
entry and disabled recording are no-ops, including no revision increment.
Disabling retains entries; reenabling retains their order. Clear removes only
history entries, preserving the enabled preference and every other collection/
preference. Mutating history uses the same atomic capacity and stale-writer fences
as lists. An explicit history-storage failure leaves both readable content and the
preceding usable personal snapshot intact; Retry reloads personal state, not a
manufactured new reader open.

Rows resolve only by kind/exact ID against already completely verified available
maps. Unknown/unprepared rows remain visible and clearable; this surface neither
implicitly prepares content nor claims offline readiness. UUID-looking legacy
IDs never resolve to real lyric UUIDs. Fixture references remain visibly synthetic.
The current real and fixture readers remain canonical Kannada only; this slice
adds no script switching. History is device-only, absent from all minimal-list
transfers and service requests; Android system backup remains disabled.
Partial LIB-06/SET-04/QUAL-05 coverage does not close full personal, offline,
privacy/release or supported-device acceptance.

### Durable lists and minimal v1/v2 transfer

Lists is reachable before preparation. Create, rename, duplicate and confirmed
delete operate on independent UUIDv4 lists. Add/edit/move/remove occurrences and
manual placeholder/reference replacement retain occurrence identity and position;
every duplicate/import instead allocates fresh list and occurrence UUIDs.
All repeated references/placeholders, raw names, user fallback text and order are
preserved. Names are not keys, no suffix is invented, and matching never merges.
Create/rename/duplicate/import warnings use the pinned official Unicode16 key
below and require explicit same-name reuse against the original revision/epoch.
Cancelling leaves Room unchanged. Draft errors retain input; no optimistic save
success is shown. There are no cached name keys to migrate.

Each transaction checks all favorites/lists, unique list IDs and globally unique occurrence IDs,
per-list/aggregate counts and deterministic stored-byte capacity before committing.
Bounds come from Shared: 1,000 lists, 10,000 occurrences/list, 50,000 aggregate,
10,000 favorites, names128/user text512 code points and 64MiB logical stored bytes.
The decoded8MiB/encoded9MiB ceilings apply to import/export transports, not to
durable list storage. A larger valid local list remains usable within the stored
budget, but export explicitly refuses it without changing or truncating the list. Invalid,
future-version, exhausted-revision, stale-epoch/revision and capacity failures
preserve the old usable state. No ordinary catalog titles/content or URL fields, readiness flags or guessed
bookmarks/verse metadata are automatically added. User-supplied text stays literal.

Real references resolve only against the explicit fully prepared canonical set.
Before preparation, references remain visible and saveable; Open retains a
process-local pending lyric/list destination and shows the explicit preparation
consent screen without any network request. Successful complete preparation opens
that exact UUID if available. Failure/cancel preserves personal state and the
prior validated set; unavailable UUIDs remain visible. Back to list returns to the
same list and process-local list viewport. Legacy IDs are never matched to real lyrics. The separately labeled
fixture favorite reader retains its old synthetic lookup, isolated from real
list resolution. User text stays plain text. Recitation mode/resume is not implemented.

Settings → List snapshot preview is reachable before preparation and regardless of
personal-storage availability. Choose a local document or explicitly paste a
complete HTTPS snapshot link. The picker grants access only to the selected document;
the app takes no persistent document permission. No incoming intent/App Link
association is added. Preview and re-export never write Room, fetch lyrics, mark
content ready, match text to catalog items or upload lists.

Pure Kotlin `core/ListTransfer.kt` implements the pinned
[minimal v1/v2 contracts](shared/docs/list-transfer.md). Names, placeholder text,
explicit user fallback, opaque reference IDs, repetitions and order remain literal.
v1 `item` entries structurally decode to typed `legacyVideo`, even for UUID-looking
values. Original-v1 previews can re-export v1; saved typed lists always export v2,
never disguise a lyric UUID as `videoId`, drop entries or silently downgrade.
v2 supports only `lyric`, `legacyVideo` and `placeholder`. Only the exact
format/version/name/entries allowlist is accepted, with exact entry
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

Save to My Library opens a separate confirmation, permits editing the imported
name and applies the same duplicate-name/revision/epoch fences. Each confirmed
import creates only a new independent list; it never appends/merges/replaces.
Preview, drafts and pending reader destinations do not survive process death.
This is partial XFER-01/02/03/04/05/06/08 evidence, not full transfer acceptance, native feature parity,
full backup correctness or release readiness.

### Portable personal/list-name comparison

Native personal-name comparison selects **official-unicode-16**, using the pinned
[Shared contract](shared/docs/unicode-name-matching.md):
`NFC16(default-full-C/F-fold16(NFC16(original)))`, profile
`unicode16-default-full-nfc-v1`. `PersonalNames.key` returns an explicitly versioned
derived `PersonalNameKey`; every `ListSnapshot` (JSON/file/link decoding and the
process-local preview model) exposes `nameKey` on demand. This is comparison only:
the codec, preview display and re-exports preserve exact original names and entries.
No trimming, compatibility normalization, translation, platform ICU/Normalizer,
lowercasing or host Unicode-property tables participate in these keys.
The existing single-line/scalar/count validation and error reasons are unchanged;
the normalization primitive itself also accepts empty strings/noncharacters and
explicitly rejects unpaired surrogates.
Recursive decomposition and stable insertion ordering use O(d) working storage
and O(d²) worst-case ordering time for d decomposed scalars; indexed folding and
composition are linear. Existing transfer name limits bound consumer inputs.

Core packages the exact committed Shared generated JSON/manifest and Unicode
License V3, not copied/versionless native tables or a new runtime dependency.
Every resource-processing build runs `node tools/verify-unicode.mjs`, checking
profile/pipeline, artifact/vector lengths and SHA256, metadata provenance and all
six upstream source identities against Shared settings. Runtime loading verifies
the packaged profile and data hash; missing/corrupt/unsupported resources fail
explicitly, with no platform fallback.

Room schema5 stores raw list names, not persisted name keys. Create, rename,
duplicate and import use this profile and explicit independent-reuse confirmation.
No list is automatically renamed/merged; no cached keys require migration.
A future persisted-key/profile change still needs a preserving migration.
Fixture lyric search and the separate prelaunch literal title filter keep their
existing policies; raw real lyric UUIDs and legacy IDs are never normalized.
Partial mapping: LIB-01/02/03/04/05/08/09/10/11, XFER-01/03/04/05/06/08
and SET-01; not full requirement acceptance.

### Remaining approved slices

This batch does **not** implement recitation mode/resume, full personal
backup/restore, system backup, bookmarks/learning state, persistent offline
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

Partial requirement coverage: LIB-01/02/03/04/05/08/09/10/11,
XFER-01/02/03/04/05/06/08, I18N-01/02/03,
SET-04 (UI language/theme/lyric typography only), UX-02/03/14 (bounded lyric
typography/layout only), QUAL-05/06 (local-only state and explicit errors).
Canonical Kannada and required preparation remain unchanged. This is neither
complete requirement acceptance nor production/release readiness.

## Validation

```powershell
npm ci
npm run config
.\gradlew.bat :core:build :app:build :app:lintDebug :app:lintRelease :app:connectedDebugAndroidTest --no-daemon
python tools\check_repository.py
node -e "const fs=require('node:fs'),path=require('node:path'),yaml=require('yaml');for(const p of fs.readdirSync('.github',{recursive:true}).filter(p=>p.endsWith('.yml')||p.endsWith('.yaml'))){const d=yaml.parseDocument(fs.readFileSync(path.join('.github',p),'utf8'),{uniqueKeys:true});if(d.errors.length)throw d.errors[0];console.log(p)}"
actionlint .github\workflows\hygiene.yml
git --no-pager diff --check
git --no-pager diff --cached --check
```

Set JAVA_HOME to JDK21 and ANDROID_HOME to the SDK with platform/build-tools36.
Use the existing actionlint/ShellCheck binaries on PATH for workflow checking.
Connected tests require an intended API36 emulator selected with ANDROID_SERIAL and
the Website demo at the endpoint in settings/android.yml. Configuration is generated
from owned settings and pinned Shared policy. Never target another user's device.
The Gradle wrapper distribution and wrapper JAR have checked SHA256 pins.

Reading preference core/Room/Compose cases cover configured default/bounds/step,
all three density states, no-op revisions, persistence/restart, exact old/current
UTF8 accounting and migration capacity, preserving v4 migration, invalid/future
refusal, rollback and stale fences, queued local actions, localized accessible
labels and actual canonical/fixture literal text with unaffected other personal
state. Peer history clearing while a reader remains mounted is tested across
stale refusal/retry and subsequent typography changes. Synthetic instrumentation
does not establish real-device/font-shaping or full accessibility acceptance.

Core JVM tests cover search, first-use/override locale selection, portable
personal-name comparison, prelaunch lyric
contracts/encoded verification and the transfer codec. Prelaunch vectors use only
synthetic text and cover strict fields/version/capabilities, duplicate UUIDs/JSON
members, malformed UTF-8/scalars, reference/path/size/hash limits, summary mismatch,
line/layout distinctions, literal whitespace, query bounds, cancellation and retry.
Native synthetic instrumentation also exercises the complete descriptor/manifest/item
chain, explicit complete-preparation gating, pre-consent personal isolation,
typed reader Favorite/Add to list, pending list destinations without implicit
downloads, filter/reader text and cancel/retry/reload-failure preservation. It is not a live
feed or real-content demo.
Favorites core/connected synthetic tests cover the actual configured page,
two-page and two-page-plus-one boundaries, complete tail reachability, literal/
case-insensitive matching, no-match and invalid-query distinction, ordinary
clear/reset, independent catalog queries, section/reader-return viewport,
preparation-free pending Back, typed unknown/UUID-looking legacy retention,
explicit removal and valid shortened results. They distinguish unchanged personal
records during browsing from approved default-on successful-reader history writes.
This is bounded SEARCH-01/UX-13/LIB-01 evidence, not full search/offline parity.
Canonical catalog connected synthetic tests separately cover configured one-page,
two-page and two-page-plus-one boundaries, literal/independent title queries,
invalid/oversized query rejection, complete tail reachability, query reset,
catalog/reader viewport separation, arrow/Android Back and available native focus,
Settings return, preference changes without repeat history opens, failed/shortened
explicit preparation, fresh-session reset and unchanged unrelated Room records.
The existing core expansion helper supplies overflow-safe paging for both surfaces.
Recent-history core/Room/Compose tests exercise exact typed distinct ordering,
rolling eviction, the actual configured50/+1 storage limit, initial-on, saved
false/restart, independent Clear, no-op revisions, actual real/fixture reader
exposure receipts, disabled and remounted readers, no preparation/background/
unknown/failed-open writes, no additional network requests, visible storage-write
failure with preserved reading, unresolved rows and stale import/name confirmations.
Actual v3→4 migration fixtures preserve all existing personal records and refuse
projected-capacity/invalid/future states without reset. Exact aggregate UTF8
ceiling/+1 tests include a full history record.
Transfer tests use actual9MiB/8MiB/2048 ceilings and ceiling+1 (including a
valid9MiB single gzip member, valid high-compression8MiB JSON and complete2048/
2049 canonical URLs), literal/repeated round trips, pinned Shared acceptance/
rejection fixtures and an independent Node/zlib deterministic fixture vector.
They reject malformed JSON/UTF8/Unicode, duplicate/unknown fields, unsupported
versions, gzip truncation/CRC/length/concatenation/trailing bytes and observe
cancellation. Instrumentation
uses real Room/SQLite and Compose: reopen persistence, unavailable IDs, limits,
stale revisions/epochs, exhausted revisions, rejected records/schemas,
future-version preservation, actual v1/v2 structural migrations,
settings/favorites/lists before preparation and preparation-to-canonical-reading.
Durable-list tests cover independent/repeated occurrences, replacement/moves/removal,
duplication/deletion, Unicode duplicate confirmation, chosen import names,
transaction rollback, actual configured list/entry/aggregate and UTF8 stored-byte
ceilings with one-extra refusal, and fresh-ID v1-import/v2-export preservation.
`ListInteropTest` normally executes the pinned mixed-v2 vector and can receive
complete actual peer gzip/link/JSON bytes as base64 instrumentation arguments
`webGzipBase64`, `webLinkBase64` and `webJsonBase64`. All three must be supplied
or none. It validates exact typed values/text/repetitions and emits real native
gzip/link re-exports in target-app private test files. No external/session path is
an application or permanent test dependency; this adapter never writes personal
data or downloads content. The evidence adapter removes at most one terminal
LF/CRLF from a peer URL text file, not from the URL codec or user fields.
The native transfer journey covers pre-preparation link/file-stream preview,
no implicit save, confirmed new-list-only Save, cancellation, invalid input/provider failure, known/pending/unavailable
catalog states, complete link re-export and unchanged favorites/preferences/theme.
Document picker/provider interoperability across real devices, destination failure
semantics, signed App Links and OS sharing remain required later acceptance gates;
injected local streams do not establish those claims.
Personal-name tests execute the identical Shared independent integer vectors,
all official Unicode16 NFC/NFD equations and unlisted scalar invariants, and
exhaustive default C/F folding/identity for every scalar. They cover supplementary
characters, recursive decomposition, canonical ordering/blocking/exclusions,
Hangul, stage order, locale-independent/version-specific differences, malformed
UTF16, packaged byte/hash/profile guards and original name limits despite fold
expansion. A connected test exercises the actual packaged tables and unchanged
file/link snapshot round trips on Android; separate Room/Compose list tests cover
the bounded durable-list slice, not full recitation/offline acceptance.
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
