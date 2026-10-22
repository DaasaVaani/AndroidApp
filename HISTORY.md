# Documentation history

## 2026-10-22: typed real references and durable native recitation lists

- Owner authorized Android-only parallel implementation and fully validated
  direct-main publication. Baseline was clean main
  `207a3ccad6c2f814b1d886b5b7f7f7cd1844a9d2`. Adopted independently published
  Shared `7ad993c8362231aec95bb1b980ebd3b386059488` from the preceding
  `8bd8793afb9f2fd43d52b62bc5436bbd4da1b0ce` pin after checking the nested tree.
  Shared main run `39706731354` was independently verified completed/success at
  that exact SHA. No Website, Shared source, Library or original lyric source
  was edited; tests use synthetic permitted text.
- Archived superseded native boundaries: schema2 stored only strict11-character
  video favorites and language/theme; the separate prelaunch reader had no
  personal-save actions; Settings exposed process-local v1 preview/re-export
  with deliberately no Save or durable lists. No durable name keys existed,
  so the preceding Unicode16 batch performed no database/key migration,
  duplicate-name UI or collision-driven rename/merge. Those statements remain
  historical evidence, not the current native behavior.
- Room schema3 structurally preserves the validated actual v1/v2 favorites and
  preferences, with old `ORDER BY videoId` retained as the initial explicit
  favorite order. Typed `(kind,ID)` favorite uniqueness, durable lists and
  independent occurrences now share one transactional database. Lists preserve
  raw names/text/order/repetitions; editing/replacement preserves occurrence IDs;
  explicit duplication/import creates fresh independent IDs. No media, aliases,
  bookmarks/verses or source mapping was invented. Official Unicode16 matching
  remains on demand with explicit same-name confirmation and no cached keys.
- The native logical storage envelope is independently versioned3, with compact
  UTF8 incremental accounting including preferences/revision/local IDs. Epochs
  are process-local write fences, refreshed when a store closes/reopens, not
  transport fields or backup/recovery claims. Every personal mutation requires
  its original epoch and monotonic revision. Migration validates both old and
  projected capacity before any structural change and rechecks actual old schema/
  records inside the migration transaction; no destructive fallback exists.
- v1 transport remains a frozen external shape, structurally adapted only to
  legacy references, including UUID-looking values. Original v1 previews can
  re-export v1; typed saved lists export v2 only. New-list-only confirmed import
  permits choosing the name and never appends, merges or overwrites. Complete
  gzip/link boundaries and error/cancellation behavior remain in force.
- Reader Favorite/Add to list, typed favorites and list resolution use only
  explicit complete memory-only canonical preparation. Opening an unresolved
  lyric retains the pending exact UUID/list destination without downloading.
  No legacy-to-real lookup is inferred. The synthetic favorite reader remains
  visibly separate. No signed release, persistent offline content, recitation
  mode/resume, full backup/system-backup parity or production linguistic proof
  is claimed.
- Incremental validation: `npm run config` passed.
  `.\gradlew.bat :core:test :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --console=plain`
  initially failed on the frozen v1 fixture that calls an empty version2
  envelope unsupported; the dual decoder now explicitly tests its supported
  empty-v2 adaptation and refuses version3. A subsequent failure exposed a test
  vector serialization replacing a lone surrogate before reaching the decoder;
  retained the escaped surrogate in the test. The same command then passed
  **32 core tests** and compiled both device APKs.
- First incremental `.\gradlew.bat :app:connectedDebugAndroidTest --no-daemon --console=plain`
  ran29 tests with2 failures: structural list validation occurred after the
  duplicate-name check, and an old preview-only assertion no longer described
  the actual Save-enabled UI. Structural validation now precedes collision
  checks and the UI assertion verifies preview-before-saving/no implicit writes.
  Failed runs are not counted as passing evidence.
- Targeted
  `.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=in.daasavaani.app.ListsStoreTest,in.daasavaani.app.ListsUiTest,in.daasavaani.app.LyricPreviewTest,in.daasavaani.app.TransferUiTest' --no-daemon --console=plain`
  ran18 cases with1 failure: the test selector matched both the import dialog title
  and its Save button. Required a clickable dialog node in the test; no app
  validation or suite coverage was weakened. Added actual configured collection/
  64MiB UTF8 boundary fixtures and preserving v2/default-schema checks afterward.
- Owner supplied actual production-built Website-worker exports of the mixed
  Shared v2 accepted vector for cross-client checking, not a Node-produced stand-in.
  Added an optional base64-argument Android instrumentation adapter with a pinned
  default vector, no permanent external/session-path dependency and no personal
  writes. Real native byte/link output can be decoded by the Website worker in
  the reverse direction; transport evidence is separate from OS sharing/App Links.
- Initial actual-peer instrumentation ran1 case and failed because the owner's
  `.url` evidence file included a final LF outside the wire URL. JSON and gzip
  decoding had already passed. The test-only evidence adapter now removes at
  most one terminal LF/CRLF file delimiter; production URL validation remains
  strict and all user names/text remain byte-for-byte unchanged.
- After that adapter correction, the same actual-peer Gradle selector passed
  **1 connected case**. Gradle's managed connected runner removes its installed
  test app afterward, so private output retrieval required explicitly installing
  the built debug/test APKs and rerunning
  `adb -s emulator-5556 shell am instrument -w -r -e class in.daasavaani.app.ListInteropTest -e webGzipBase64 <actual-file-base64> -e webLinkBase64 <actual-text-file-base64> -e webJsonBase64 <actual-json-file-base64> in.daasavaani.app.test/androidx.test.runner.AndroidJUnitRunner`;
  that native run also passed **1 case**. Binary-safe `adb exec-out run-as`
  retrieval and independent Node gzip/raw-deflate decoding matched the owner's
  exact expected object, including lyric/UUID-looking legacy distinction,
  repetition, spaces, emoji, combining text and literal markup.
- Input actual Website gzip:236 bytes, SHA256
  `daf71b2db19f862c0ee9edf29fe000658cd10af7240c1feb93990e761f75ad4a`.
  Actual native gzip:237 bytes, SHA256
  `f0acf892c49e0c8cdd9bbfba5cf69ed410ea8991c9d026b6567288165e2dd7e9`;
  native full link:333 bytes, SHA256
  `f94413a91fa6ddb9e0fd6e1d7e6e810f13dffcc83a3983fbcbdec67ba0e6b79f`.
  Owner-requested evidence files were delivered in the session evidence directory,
  not committed or used as permanent test dependencies. Reverse Website-worker/UI
  acceptance is owned by the Website coordinator and not claimed by this run.
- Final contract review separated structural field/count validation from
  transport byte serialization: a valid durable list is governed by the64MiB
  aggregate stored budget, not an accidental per-list8MiB transfer cap. Added
  core separation coverage and an actual configured >8MiB single-list native
  save/reopen test whose attempted export explicitly refuses without mutations.
- A later full run completed36 cases with1 UI-test failure: after retaining
  the list viewport, a repeated unscoped Edit label could select another composed
  occurrence. Scoped edit/move/remove test actions to the intended stable
  occurrence row and its clickable control, retaining every original state/
  ordering assertion. The real-reader return-to-list viewport assertion passed.
  This failed full run is not passing final validation.
- The subsequent4-case focused selector passed its3 UI/interop cases but exposed
  an accidentally nested new capacity-test method as a JUnit initialization
  error. Moved it to a proper instance test method; no case was dropped.
- Final local gate on existing JDK21/SDK36 and intended API36
  `emulator-5556`, with the unchanged fixture server verified HTTP200:
  `npm run config` and
  `.\gradlew.bat :core:build :app:build :app:lintDebug :app:lintRelease :app:connectedDebugAndroidTest --no-daemon --console=plain`
  passed. XML reports contain **33 core tests and37 connected tests, zero
  failures/errors/skips**. Debug/release builds and both explicit lint tasks
  passed; each lint report has0 errors/54 warnings. App JVM tasks remain
  NO-SOURCE, not test passes. Auxiliary inapplicable SKIPPED Gradle tasks are
  not skipped test cases. The existing local SDK XML-version warning remains
  nonfatal; no toolchain/dependency installation was performed.
- Repeated the actual Website gzip/link/JSON instrumentation against the final
  built APK: **1 case passed**, native re-export hashes unchanged, independent
  gzip/raw-deflate decoding still exactly equal. Delivered final actual native
  evidence files to the requested session directory. The whole native suite
  uses only synthetic/permitted text; no original lyric sources were consumed.
- Repository checks passed: `python tools\check_repository.py` (**69 tracked
  files**); strict Node `yaml.parseDocument(..., {uniqueKeys:true})` over all
  **5 .github YAML files**; existing actionlint1.7.12 with ShellCheck0.11.0,
  `.github\workflows\hygiene.yml`; `git --no-pager diff --check` and
  `git --no-pager diff --cached --check`. Native editor diagnostics were clear.
  Shared's nested working tree stayed clean at the independent7ad pin.
- Coverage remains bounded/partial LIB-01/02/03/04/05/08/09/10/11,
  XFER-01/02/03/04/05/06/08, existing UI/settings/I18N boundaries. Complete
  canonical content is still explicit memory-only preparation. Recitation mode/
  resume, bookmarks/learning, durable offline content, full backup/recovery,
  verified real-provider/device/App Link/OS-sharing acceptance, signed release
  and production linguistic conformance remain open. Only this Android batch
  was implemented; no next slice or other repository changes were started.

## 2026-10-22: official Unicode16 native personal-name consumer

- Started from clean native main `7258cbc8e537314d56d1c4e4edfa76ab55a469e1`
  with independently pinned Shared `8bd8793afb9f2fd43d52b62bc5436bbd4da1b0ce`;
  neither the pin nor Shared files changed. Published Shared main run
  `39679540067` was independently checked as completed/success at that exact
  revision. Website work and original lyric sources were outside this batch.
- Owner selected `official-unicode-16` with preserving adoption. Added the
  portable scalar-only native adapter and versioned on-demand `ListSnapshot.nameKey`
  for codec/file/link/process-local preview snapshots. Original names, entries,
  video IDs, real lyric UUIDs, display, export bytes/format and existing validation
  errors remain unchanged. Room schema2 contains only favorites/preferences:
  no durable lists or persisted name keys exist, so no database migration or
  collision-driven rename/merge was performed or invented. Durable list flows,
  duplicate-warning/reuse UI and future preserving migrations remain open.
- Core resource processing packages the exact committed generated Shared JSON,
  manifest and Unicode License V3. Native build guards verify selected metadata,
  pipeline, byte lengths/hashes, provenance and six original upstream identities;
  runtime rejects missing/corrupt/unsupported profile/data without a platform
  fallback. No new dependency, copied table or platform name normalization/fold.
- Full local validation on JDK21, configured SDK36 and the existing intended
  API36 `emulator-5556`, with the existing Website server responding HTTP200:
  `npm run config` and
  `.\gradlew.bat :core:build :app:build :app:lintDebug :app:lintRelease :app:connectedDebugAndroidTest --no-daemon`
  passed. XML reports contain **31 core tests and 21 connected tests, zero
  failures/errors/skips**. App JVM tasks remain NO-SOURCE, not test passes;
  Kotlin plugin-configuration checks are inapplicable SKIPPED tasks, not skipped
  test cases. Build/lint include both debug and release variants.
- Native tests execute all Shared independent vectors/comparisons/invalid UTF16,
  all **19,965** official normalization rows/equations and unlisted scalar
  invariants, and exhaustive C/F folding/identity for **1,112,064** scalars.
  Added supplementary/version/locale/canonical-ordering/composition/Hangul,
  input-limit/fold-expansion, literal file/link preservation and packaged
  resource/hash/profile tests; connected coverage verifies the actual Android
  packaged adapter. This establishes algorithm/packaging conformance, not
  linguistic approval, durable-list acceptance or production release readiness.
- Focused `.\gradlew.bat :core:test --tests '*PersonalNamesTest' --tests '*ListTransferTest' --no-daemon`
  passed after fixing an initial Kotlin compile failure caused by a bare `data`
  expression. The editor test tool found no native tests; actual configured
  Gradle runners supplied the evidence. No failed run was counted as passing.
- `node tools/verify-unicode.mjs`, `python tools\check_repository.py`,
  `git --no-pager diff --check` and `git --no-pager diff --cached --check` passed.
  All five `.github` YAML documents, including the workflow, parsed using the
  installed pinned `yaml` package with `uniqueKeys:true`; repository hygiene
  also checks immutable action pins. Main's branch-protection REST endpoint
  returned 404 (no legacy branch-protection object); the documented unchanged-SHA
  validation-branch preflight and actual required job still apply without bypass.

## 2026-10-22: bounded prelaunch stable-ID native reading

- Archived superseded README opening: "Current implementation is a native fixture
  demo, not a production/offline release." The fixture remains isolated and all
  prior preparation/reader/favorite/list-preview paths are retained; a separate
  read-only `lyric-preview-v1` catalog/reader is now implemented.
- Independently verified published Shared main `26a02b3423060d8efa7897cd92844a79ffdacc9c`
  with `gh run view 39672859078 --repo DaasaVaani/shared --json conclusion,headSha,status`:
  completed/success at exactly that SHA. Clean nested checkout was verified before
  adopting that independently pinned revision. No Library sources/raw Markdown were
  accessed, copied or generated. No other consumer pin changed.
- Added native-owned endpoint/bounds generation, strict bounded UUID catalog/item
  validation, encoded-gzip verification, explicit whole-library preparation/cancel/retry, literal
  title filtering and canonical line/layout reading. Existing Shared bilingual
  labels and existing locale/theme are reused; no actual content or generated
  variants are committed. Portable Unicode and durable lists remain separate
  policy/implementation blockers, not acceptance claims of this slice.
- Incremental validation: `npm run config` passed;
  `.\gradlew.bat :core:test :app:compileDebugKotlin --no-daemon --console=plain`
  passed; `.\gradlew.bat :core:test :app:compileDebugAndroidTestKotlin --no-daemon --console=plain`
  passed. These are incremental checks, not substitutes for the full gate.
- First full command
  `.\gradlew.bat :core:build :app:build :app:lintDebug :app:lintRelease :app:connectedDebugAndroidTest --no-daemon --console=plain`
  built both variants and ran both lints, but connected tests failed (18 run,
  2 failures, 0 skipped): the new test used a synthetic legacy favorite with an
  invalid length; the later locale journey also raised a Compose wrong-thread
  failure while the new test used an automatic host. Corrected the fixture to the
  existing valid unavailable ID and used the
  established empty-rule/explicit-activity pattern. No product validation was
  weakened and the failed run is not counted as passing evidence.
- Full rerun of that exact Gradle command passed with JDK21 and SDK36 on owned
  `emulator-5556` (API36, boot complete); fixture
  `http://localhost:5173/demo/latest.json` returned HTTP200 before execution.
  Core JVM:25 tests,0 failures/errors/skips. Connected Room/Compose/API36:19 tests,
  0 failures/errors/skips, including3 new synthetic lyric instrumentation tests.
  Debug/release builds passed. Both configured lints passed (debug0 errors/
  79 resource/dependency/manifest warnings, release0 errors/80 warnings).
  App JVM tasks are NO-SOURCE, not a passed app JVM suite. Plugin configuration
  placeholder/prebuild SKIPPED tasks are not omitted configured test suites.
- `python tools\check_repository.py` passed for59 tracked files; strict Node/yaml
  parsing passed for all5 GitHub YAML files; `git --no-pager diff --check` and
  `git --no-pager diff --cached --check` passed. Editor diagnostics on the new
  production core/native source and instrumentation file reported no errors.
  No dependencies or toolchains were installed.
- Library live publication/native real-content demonstration is pending. No main
  push is authorized until the parent confirms the feed is live and actual native
  reading is independently verified. Synthetic tests are not owner-text review,
  a durable offline claim or a public launch.
- The parent reaffirmed Android's complete-library consent requirement before
  publication. Replaced the initial uncommitted on-demand item-selection behavior:
  explicit preparation now verifies all canonical items and aggregate budgets
  before exposing any catalog/reader, selecting prepared content is network-free,
  and cancelled/failed replacement retains the complete prior set. The earlier
  successful checks above were incremental evidence before this gate correction;
  complete post-correction validation is required separately.
- Owner subsequently confirmed review of all published lyrics; this is the owner's
  text review, not a claim of full product/release acceptance. The parent reported
  Library deploy39676226551 and complete229-item/5450-line live integrity checks.
  Native source/reader scope remains canonical-only; source metadata placeholders
  and portable Unicode/durable-list migration are not implemented in this slice.
- Complete post-gate command
  `.\gradlew.bat :core:build :app:build :app:lintDebug :app:lintRelease :app:connectedDebugAndroidTest --no-daemon --console=plain`
  passed:25 core JVM tests and20 connected tests,0 failures/errors/skips, both
  debug/release builds and configured lints. Four synthetic native lyric tests
  cover complete preparation, aggregate budgets/cross-item duplicate lines,
  literal read-only UI/personal isolation and cancellation/replacement retention.
- On owned API36 emulator5556, installed the debug APK, woke/unlocked the
  test-run screen, verified no title filter/reader before explicit consent, and
  selected Prepare reading library against the live HTTPS feed. Complete native
  preparation succeeded. After clearing an ephemeral title query, selected a
  prepared published title and verified Back to lyrics, a multiline canonical
  Kannada body, and absence of the fixture canonical-test label/personal-save
  controls. No published lyric text was logged or committed. Initial automatic
  title selection hit navigation instead; the explicit corrected reader check,
  not that earlier attempt, is the successful demo evidence.
- The runtime still used the earlier Shared notice pending the parent's neutral
  prelaunch-label publication. This successful feed/reader check does not authorize
  main publication or replace final validation after adopting that revision.
- Adopted the final approved Shared
  `8bd8793afb9f2fd43d52b62bc5436bbd4da1b0ce` after independently checking
  `gh run view 39679248387 --repo DaasaVaani/shared --json conclusion,headSha,status`
  and `gh run view 39679540067 --repo DaasaVaani/shared --json conclusion,headSha,status`:
  both completed/success at exactly that SHA. Verified the clean nested checkout
  before adoption. Its neutral prelaunch notice now reflects completed owner text
  review; limits explicitly disclose whole-library consent and memory-only,
  nonpersistent preparation. Shared's Unicode16 files are not consumer migration
  authorization or implementation in this read-only slice.
- At that final Shared pin, `npm run config` and the complete configured Gradle
  command above passed again:25 core JVM tests,20 API36 connected tests,0
  failures/errors/skips; debug/release builds and both full lints passed. Native
  hygiene passed59 tracked files, strict parsing passed5 GitHub YAML files and
  staged/unstaged whitespace checks passed. Existing resource/dependency/manifest
  lint warnings and app JVM NO-SOURCE remain disclosed, not converted into passes.
- Reinstalled the final debug APK on owned emulator5556 and independently verified
  the neutral notice, explicit whole-library consent/memory-only limits, no
  browsing before preparation, complete live preparation and a selected multiline
  canonical Kannada reader with no fixture label or personal-save controls.
  No feed count/hash is hardcoded in product logic and no published text is
  committed/logged. Durable snapshots/lists, Unicode migration, pre-consent size
  preview and full production/release acceptance remain outside this slice.

## 2026-10-22: bounded native minimal-list preview

- Archived superseded README scope: "This batch does not implement recitation
  lists/list transfer". Minimal process-local preview/re-export is now implemented;
  list persistence/import Save, full backups, native offline parity, real OS
  interoperability and signed App Links remain open.
- Authorized scope is only Android and its independently pinned nested Shared.
  Parent supplied exact already published/validated Shared
  `88f15095339ea6a9617845fa2439084f62a64fe9` for native preview/cancellation/export
  labels (reported preflight39654732880/main39654821792). No owner Shared,
  Website or Library source was accessed/edited.
- Added pure Kotlin bounded strict single-member gzip and raw-DEFLATE canonical
  hash-link codecs, fixed-depth duplicate-aware JSON parsing, local preview and
  native document/copy adapters. All portable ceilings use pinned owner settings;
  native host/path/filename/compression settings mirror the parent-supplied
  approved Website wire without accessing Website source.
- Independent deterministic `core/src/test/resources/list-transfer-v1.json` uses
  Node built-in zlib level6 over literal Kannada synthetic minimal JSON. Kotlin
  tests require exact JSON, decode its raw-deflate link/gzip bytes and require
  deterministic native re-export. JVM/Node zlib block selection differs for this
  input, so compressed-byte equality is not a portable wire requirement.
  This is codec fixture interoperability, not real OS delivery or linguistic proof.
- Incremental `npm run config`, `.\gradlew.bat :core:test :app:assembleDebug --no-daemon --console=plain`
  passed. First connected run exposed localized Compose context losing the
  ActivityResultRegistryOwner (15 existing tests passed; new transfer test failed).
  Supplied the actual activity owner explicitly, then
  `.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=in.daasavaani.app.TransferUiTest --no-daemon --console=plain`
  passed (one real native Compose journey). Neither the failed run nor this focused
  rerun is substituted for the required final full-tree gate.
- Parent subsequently published the requested truthful partial-document failure/
  cancellation label at exact Shared
  `add95cb8f9beadc0f1250d73d710b1f656d4ca37` (reported preflight39656574060/
  main39656647990, Shared14 tests). Independently adopted only that revision.
  Its additive portable theme token catalog matches existing native
  light/dark/warm/device; native follow-device default is unchanged.
- The initial Node-vector test incorrectly required identical compressed bytes
  across different zlib implementations. Replaced that non-contract assertion
  with exact JSON plus deterministic native output, actual Kotlin decoding of
  the committed Node raw-deflate/gzip vectors, and actual Node built-in zlib
  decoding of Kotlin output. `.\gradlew.bat :core:test --no-daemon --console=plain`
  then passed14 tests, including all real configured transport ceilings.
- `npm run config` and
  `.\gradlew.bat :core:build :app:build :app:lintDebug :app:lintRelease :app:connectedDebugAndroidTest --no-daemon --console=plain`
  passed using existing JDK21/SDK36 and explicitly selected emulator-5556/API36:
  core14 tests and connected16 tests, zero failures/errors/skips. Debug/release
  lint had zero errors and79/80 warnings (including unused full Shared strings,
  existing icon/backup/toolchain/dependency guidance). App JVM tests remain
  NO-SOURCE, not passes. SDK XML-version/native-library tooling warnings remain.
  The complete gate is rerun on the final staged tree, including the additional
  actual clipboard assertion and equivalent HTTPS-origin/default-port checks,
  before publication; no focused run substitutes for it.
- `python tools/check_repository.py` passed53 tracked files. actionlint1.7.12
  with ShellCheck0.11.0 on `.github/workflows/hygiene.yml`, strict pinned Shared
  `strictYaml` parsing of all5 `.github` YAML files, a tracked Markdown local-link/
  anchor/fence audit of5 files, `git --no-pager diff --check` and
  `git --no-pager diff --cached --check` passed.
- Outside instrumentation, reinstalled the debug APK on the same owned emulator
  and used the actual Open Document picker to select only the generated synthetic
  `.dvlist.json.gz` from Downloads before preparation. Preview preserved literal
  Kannada, markup-looking placeholder, spaces and repeated unresolved references.
  Used actual Create Document to write `list.dvlist.json.gz`; pulled its complete
  bytes and independently verified exact minimal JSON with Node `gunzipSync`.
  Independently decoded the actual displayed complete canonical HTTPS link with
  Python raw zlib and compared exact fixture JSON. Screenshots:
  `app/build/native-transfer-preview.png` and `app/build/native-transfer-link.png`.
  This one emulator/provider demonstration is not supported-device/OS-interoperability
  acceptance. Final debug APK remains `app/build/outputs/apk/debug/app-debug.apk`;
  required explicit preparation is restored after the final connected run.

## 2026-10-21: durable fixture personal foundation

- Archived temporary current blocker: "Publication blocker: the pinned catalog
  currently gives generic personal-storage errors and labels the follow-device
  options with the platform name. Dedicated bilingual future-schema/recovery,
  follow-device and Warm labels have been requested from the Shared owner. Do not
  publish this batch until that upstream validated catalog is pinned, integrated
  and the complete native gate rerun."
- Parent published the requested six bilingual native labels at exact Shared
  `91d33100fce2f2bb7f1ea98a5ebc566fcf1690b5`; reported Shared13 tests and actual
  required hosted jobs passed in preflight39650044785 and main39650129581.
  Fetched and independently pinned only this clean nested checkout. Integrated
  actionable future/schema errors, loading, follow-device, Warm and unavailable
  favorite labels. Extended the Compose restart journey to retain Warm after
  proving the initial saved Dark choice.
- After the final catalog integration, `npm run config` and
  `.\gradlew.bat :core:build :app:build :app:lintDebug :app:lintRelease :app:connectedDebugAndroidTest --no-daemon --console=plain`
  passed again (BUILD SUCCESSFUL,3m44s): core4 tests and connected15 tests with zero
  failures/errors/skips on explicitly selected emulator-5556/API36; both lint
  variants have zero errors. App JVM tasks still have NO-SOURCE. Reinstalled the
  final debug APK and completed required preparation through the actual device UI;
  the synthetic demo and existing services remain available.

- Superseded README introduction: prepared data was memory-only and there was
  "no Room personal state, durable offline activation or complete feature parity."
  Preparation is still memory-only; only favorites/UI language/theme now persist.
- Superseded executable coverage described only core search tests and one Compose
  preparation-to-reading journey. The previous boundaries listed the selected
  Room/backup/epoch/locale and UIDT34+/FGS26-33 directions together with implemented
  fixture behavior. Those remain approved obligations, not completed features.
- Owner authorized one bounded Android durable-personal batch, independently
  updated published Shared pin, full local/hosted validation and direct-main
  publication under the existing admin exception. No protection change is made.
- The initial environment had no responsive emulator-5556 or localhost5173
  service. Created an isolated `app/build/owned-avd` configuration from the
  already installed API36 Google APIs image and started only port5556. Restored
  read-only serving of the existing Website `dist` artifacts on5173; no Website/
  Library files or unrelated user AVD were modified. No new toolchain was installed.
- Incremental `npm run config` and
  `.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon`
  passed. An intermediate full core/build/debug+release lint command passed.
  The first complete connected run exposed incorrectly nested JUnit test methods
  (one initialization error; the two real Compose journeys passed). The nesting
  was corrected. A second connected run caught the same nesting mistake in one
  newly added test; it was corrected and all13 repository tests plus the two
  Compose journeys subsequently passed (15 tests, zero failures/errors/skips).
  The failed runs are not finalization evidence.
- Disabled App Bundle locale splits because the in-app override must retain both
  committed UI catalogs. This addresses the new AppBundleLocaleChanges lint
  warning without a runtime language-download dependency. Remaining lint warnings
  include unused full Shared resources, baseline icon/backup-rule guidance and
  dependency/toolchain update notices; they are not production acceptance.
- Independently adopted clean published Shared
  `db5c49bd9e2dc830f4364862c3e4464584c0c3af`, from
  `b0950e2f01823c471b18fff8abb49ce176d8f26e`. Requested dedicated bilingual
  future-schema/recovery/follow-device/Warm labels from the Shared owner; no local
  Shared catalog changes or floating owner-repository pin were used.
- Final current-code local gate: `npm run config` then
  `.\gradlew.bat :core:build :app:build :app:lintDebug :app:lintRelease :app:connectedDebugAndroidTest --no-daemon --console=plain`
  passed (BUILD SUCCESSFUL). JDK21, SDK36 and owned emulator-5556/API36 were
  explicitly selected. Core:4 tests, connected:15 tests, all zero failures/errors/
  skips. Both lint variants have zero errors; the dynamic-locale warning is gone.
  App JVM suites have NO-SOURCE, not a claimed app-JVM pass. SDK XML/unstripped
  native-library warnings remain disclosed.
- `python tools/check_repository.py`, actionlint1.7.12 with ShellCheck0.11.0,
  Node/YAML parsing of every `.github` YAML file, tracked Markdown link/anchor/fence
  audit and unstaged/staged `git diff --check` passed. Room's standard generated
  schema JSON is validated as JSON/versioned metadata and is narrowly exempt from
  the terminal-newline rule; other text/whitespace checks remain unchanged.
- Restored `app/build/outputs/apk/debug/app-debug.apk` after instrumentation
  uninstalled it, launched only `in.daasavaani.app` and explicitly completed fixture
  preparation through its actual device UI. Synthetic browsing is visible.
  The owned emulator and read-only fixture server remain available.
- At the initial handoff publication was blocked on Shared catalog labels. No native
  commit/push or hosted preflight/main result is claimed. Production lists/backups/
  offline activation, OS-locale restore, supported-device matrix and release
  signing remain outside this bounded batch.

## 2026-10-21: native fixture simulator milestone

- The owner authorized the recommended staged implementation and real simulator
  demonstration. Restored official checksum-verified JDK21/Gradle9.1/SDK tools
  into user-local DaasaVaani tooling and created an owned hardware-accelerated
  API36 medium_phone AVD at emulator-5556. No other device was targeted.
- Implemented native Compose/Material3 preparation, full fixture validation before
  reading, real cancellable HTTP requests/search, canonical Kannada reader and
  pure Kotlin contracts/search core. UI/configuration resources use owned settings
  and committed Shared catalogs. No WebView or bundled lyrics.
- Adopted published, exact-CI-validated Shared
  `b0950e2f01823c471b18fff8abb49ce176d8f26e`. Generated the official Gradle wrapper;
  verified its JAR against the official9.1.0 SHA256 and added binary-safe checksum
  validation rather than weakening UTF8 checks for arbitrary files.
- `npm run config` and
  `.\gradlew.bat :core:build :app:build :app:lintDebug :app:lintRelease :app:connectedDebugAndroidTest --no-daemon --console=plain`:
  BUILD SUCCESSFUL. Debug/unsigned-release compilation and both lint variants
  passed; core test results recorded2 tests with0 failures/errors/skips. One real
  API36 Compose preparation-to-reading test passed with0 failures/errors/skips.
  App JVM tasks had NO-SOURCE, not a claimed test pass.
- Actual emulator UI preparation and canonical reading were exercised outside
  instrumentation and captured. Visual inspection identified low-contrast system
  status icons; system-bar appearance was derived from the configured background,
  then the complete native command above passed again.
- Expanded CI to restore declared dependencies, generate settings, build all
  variants and run core/JVM/both lint variants. CI has no connected-device pass;
  the real local connected suite remains required for finalization/push.
  Local actionlint1.7.12/ShellCheck0.11.0 passed. Repository hygiene checked40
  staged tracked files, including the verified wrapper; staged whitespace passed.
- SDK XML-version and unstripped native-library warnings were not hidden. API26/
  other devices, Room/personal parity, persistent offline snapshots, complete
  navigation/DI, linguistic profiles and release signing remain incomplete.
  The debug-only cleartext endpoint serves local fixtures; release forbids it.
  No signing keys, real content migration or production readiness was claimed.

## 2026-10-21: explicit hosted SDK provisioning

- The first published native CI run39591629209 failed at Android platform setup:
  `sdkmanager: command not found`. No successful hosted build was claimed.
  The ubuntu-latest migration made inherited SDK availability unreliable.
- Pinned the native host to Ubuntu24.04 and added explicit SDK bootstrap through
  android-actions/setup-android v4 at immutable revision
  `be39fa834029ff78f1a44aa3bb0819b8fc2bd8fd`. Its actual input defaults and Node24
  runtime were verified from that revision's action metadata. Platform selection
  still reads generated owning settings; no duplicated compile-SDK default.
- This changes CI dependency provisioning, not application content, package
  identity, signing policy or the requirement for local connected-device validation.
- Run39592875300 then reached SDK installation but the deprecated sdkmanager could
  not locate the legacy `platforms;36` identifier. Verified the actual current
  Android CLI help and used its `platforms/android-36` / `build-tools/36.0.0`
  package form. Centralized build-tools36.0.0 in the native version catalog for
  Gradle/CI and added concrete installed-file checks; no missing SDK is silently
  treated as a successful setup.

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
