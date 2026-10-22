# Documentation history

## 2026-10-22: bounded native individual Recite and optional screen awake

- Owner independently authorized this Native-only REC-01/02 batch from
  `128e9ebfd2edd9debc1100e32b71895f6071953b` and only its Shared pin advance from
  `166b7af` to published `52564f6bc5954f077749a333cfb6e97bbea59b6f`.
  No other consumer/source, dependency, personal schema, Room migration or
  configurable default was changed. Shared's consumer-main publication gate
  remains the parent's responsibility; no Native main push is authorized.
- Archived superseded current claims: README said recitation mode/resume was
  entirely unimplemented, including the remaining-slices and list-reader notes,
  and excluded Recite from the typography slice. Explicit individual reader focus
  and optional scoped native view requests now exist; list traversal/Continue,
  Practice, durable offline and complete recitation/device support remain open.
- Parent granted the exclusive stable-dist/device/HTTP slot after its completed
  Website check (115 units/357 browser tests) and froze that dist. Before device
  work, the intended `emulator-5556` reported `device`, the existing5173 fixture
  descriptor returnedHTTP200 and protected userPID3840 remained running. No
  helper/server/emulator was created, stopped or reconfigured.
- Reused existing JDK21/SDK36/actionlint1.7.12/ShellCheck0.11.0 and dependencies.
  `npm run config` and
  `.\gradlew.bat :core:cleanTest :core:build :app:build :app:lintDebug :app:lintRelease :app:assembleDebugAndroidTest --no-daemon --console=plain`
  passed (4m35s), compiling all tests without executing connected tests. All48
  core cases passed; app JVM tasks were NO-SOURCE, not passing unit suites.
- Initial
  `.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=in.daasavaani.app.ReciteUiTest' --no-daemon --console=plain`
  ran all5 initial cases and failed2 (2m45s): the duplicate-occurrence list test
  addressed two identical Read buttons ambiguously, and explicit Exit released
  its native flag only on deferred composition disposal rather than immediately.
  The list test now explicitly chooses its first original occurrence (preserving
  both entries); Exit/Back synchronously release through the same scoped adapter,
  with disposal remaining a safety cleanup. No assertion or limit was weakened.
  The identical complete5-case command then passed (3m32s), zero skipped.
- A first complete-run helper was deliberately cancelled before completion to
  strengthen failed-release semantics: a still-owned request must retain its on
  switch when release fails, with an explicit failed caption and a usable release
  retry, not visually report off. Cancellation is not a passing full check. Added
  a sixth UI case for unsupported/denied/acquired/failed-release/retry captions
  and actual ownership, in addition to the direct adapter failure assertions.
- The next complete-run attempt exposed the unchanged existing `LaunchTest`
  first-visible-fixture-line assertion: an extra ordinary-reader Recite row had
  pushed that first line below the viewport. Stopped this already-failing run
  after32 of88 cases to fix the actual layout, not its assertion. The entry action
  now shares the existing fixture Back row; the focus-only controls occupy space
  only after explicit entry. Backgrounding without ever requesting awake now
  keeps the honest initial Off caption rather than claiming a request ended.
- Combined diagnostic
  `.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=in.daasavaani.app.LaunchTest,in.daasavaani.app.ReciteUiTest' --no-daemon --console=plain`
  ran all7 cases (6m34s): the original Launch case and all5 preceding Recite cases
  passed, but the added failure-UI case failed because overriding Compose's
  `LocalView` with a detached test view also broke Material ripple's real parent
  lookup. Narrowed injection to only the awake adapter's view argument, leaving
  the actual Compose view hierarchy intact and all UI assertions unchanged.
  `.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=in.daasavaani.app.ReciteUiTest#unavailableAndFailedCaptionsRetainReciteAndActualOwnedRequestUntilReleaseRetry' --no-daemon --console=plain`
  then passed that1 case (3m39s), zero failures/errors/skips. A diagnostic
  multiple-Kotlin-daemon-session warning followed the intentionally stopped run;
  it was not hidden or treated as failed-suite success.
- Final `npm run config` and the complete
  `.\gradlew.bat :core:cleanTest :core:build :app:build :app:lintDebug :app:lintRelease :app:connectedDebugAndroidTest --no-daemon --console=plain`
  passed (28m12s;136 tasks,23 executed/113 up-to-date). XML reports confirm
  all48 core and88 connected cases, including all6 Recite cases, with zero
  failures/errors/skips. The core suite actually re-executed after `cleanTest`.
  Both debug/release app variants built; debug lint has zero errors/58 warnings
  and release lint zero errors/59 warnings. The respective warning inventories
  are41/42 unused resources,7 Gradle dependency,6 newer-version,2 Android Gradle
  Plugin version,1 data-extraction-rules and1 missing-icon warning. No warning
  was suppressed. The existing SDK XML3-versus4 diagnostic remained. App JVM
  tasks are NO-SOURCE, not a passing application unit suite; conditional Gradle
  configuration/pre-AndroidTest/redundant lint-vital task skips are not skipped
  test cases. No dependency install, limit/timeout/retry relaxation or removed
  assertion/suite was used.
- Final `python tools\check_repository.py` passed for96 tracked files;
  `node -e "const fs=require('node:fs'),path=require('node:path'),yaml=require('yaml');for(const p of fs.readdirSync('.github',{recursive:true}).filter(p=>p.endsWith('.yml')||p.endsWith('.yaml'))){const d=yaml.parseDocument(fs.readFileSync(path.join('.github',p),'utf8'),{uniqueKeys:true});if(d.errors.length)throw d.errors[0];console.log(p)}"`
  parsed all5 workflow/forms YAML files. `actionlint .github\workflows\hygiene.yml`
  using existing ShellCheck0.11.0, `git --no-pager diff --check` and
  `git --no-pager diff --cached --check` passed. Existing Git CRLF-to-LF
  normalization notices were not whitespace-check failures. Final history edits
  were included in repeated hygiene/YAML/actionlint/whitespace checks.
- After the successful complete run, the existing5173 descriptor still
  returnedHTTP200, intended5556 remained a device, and protectedPID3840 remained
  running. All bounded helpers completed; only this batch's four diagnostic
  log files were removed. No server/emulator/tab, other repository, user process
  or other Shared pin was changed. Explicitly released the exclusive frozen-dist/
  device/HTTP slot before completing this Native-only local commit. The pin
  remains exactly525, not the parent's subsequent additive list-caption release.
  No main push or validation ref was created.
- This is synthetic/current-platform proof of focused individual REC-01/02,
  literal-reader preservation and narrow owned native-view requests only.
  List traversal/Continue REC-03/04, verse-based Practice, durable offline,
  real-content/linguistic, physical-device power-policy/accessibility acceptance
  and complete production/release readiness remain unimplemented or unproven.

## 2026-10-22: bounded prepared-catalog Show another lyric

- Owner authorized native EXP-02 work from clean published
  `334dab8a5b1303c115e41901e2e16ead83e849f2` and independently authorized only
  published/validated Shared `f4f8aecb2af5f7614ba81a3fb770048324b8850f`.
  This advances the native pin from `44c21b9d139239e3003c293dfe8058ad80393fa6`;
  the additive bilingual discovery captions are consumed without changing any
  dependency, native product setting, content source, schema or other repository.
- Archived superseded native behavior: the complete prepared canonical catalog
  supported explicit title selection only; no Show another lyric action existed.
  Reader choices now use one normal non-security platform random index over every
  eligible summary in that same complete verified session catalog, not the title
  filter/page subset. Current valid reader identity or last successfully exposed
  canonical session identity is excluded. No-candidate state is explicit and
  localized, with no navigation/history/personal mutation. The selector has no
  persistence, preparation, network, guessed taxonomy, personalization or legacy
  fixture identity path. Current documentation retains durable-offline and full
  requirement/release gates.
- Reused existing canonical reader, literal layout, typed Favorite/Add to list,
  preferences and one-shot successful-exposure receipt. Consecutive choices
  replace reader state, retaining original catalog focus/query/pages/viewport.
  List/Favorites/Recent/unsaved-preview origins and Settings return now retain truthful labels
  and one-Back context; pending list selection is not discarded. Previously the
  inner catalog arrow always appeared for external origins, Recently opened had
  no dedicated return origin, and Settings cleared collection origins.
- Added four pure core selection cases and six synthetic connected cases for
  complete candidate coverage/exclusion/index errors, preparation gating,
  filtered/page-tail selection, selector/request/write isolation, one-shot
  exposure/history semantics, literal text, sole/current unavailability,
  collection/typography controls, catalog viewport/focus and original collection
  origins through repeated choices/Settings/Back, including an unsaved transfer
  preview before preparation and after discovery without saving the snapshot.
- Reused the existing JDK21/SDK36 toolchain and dependencies; no install was
  needed. `npm run config` passed. Initial non-device
  `.\gradlew.bat :core:build :app:build :app:lintDebug :app:lintRelease --no-daemon --console=plain`
  passed (4m9s), followed by a complete core rerun with
  `.\gradlew.bat :core:cleanTest :core:build :app:build :app:lintDebug :app:lintRelease --no-daemon --console=plain`
  (3m10s): all44 core tests, zero failures/errors/skips. Both lint reports have
  zero errors and58 warnings; five additive adopted Shared shell captions are
  unused natively, in addition to the previous53 warnings. No suppression,
  dependency/policy change or assertion/suite/timeout/retry weakening was added.
- After final typed-origin and pending-preview changes,
  `.\gradlew.bat :core:build :app:build :app:lintDebug :app:lintRelease :app:assembleDebugAndroidTest --no-daemon --console=plain`
  passed (3m51s), compiling the complete six-case discovery test APK without
  executing device checks. App JVM tasks remain NO-SOURCE, not passing tests.
  `python tools\check_repository.py` passed for88 tracked files; the README's
  unique-key `node -e` YAML command parsed all5 workflow/forms YAML files;
  `actionlint .github\workflows\hygiene.yml` with existing ShellCheck0.11.0 and
  both `git --no-pager diff --check` / `git --no-pager diff --cached --check`
  passed. Owned build diagnostic logs were removed; no HTTP helper or emulator
  was started, stopped, queried or reconfigured.
- Initial handoff awaited the parent's Website/dist/browser/HTTP slot.
  The parent then explicitly granted it after successful Website validation,
  freezing dist throughout native connected checks. The existing configured5173
  fixture endpoint returnedHTTP200 and intended API36 `emulator-5556` was a
  device; no helper/emulator was created or reconfigured.
- Initial focused
  `.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=in.daasavaani.app.AnotherLyricUiTest' --no-daemon --console=plain`
  failed test-class initialization because the unsaved-preview test declaration
  had accidentally remained inside the preceding test method. Compilation alone
  had not exposed this JUnit discovery error. Moved it to a proper independent
  instance test without changing its assertions or weakening the configured
  suite, retry or timeout policy.
- The next full six-case focused run exposed three Settings-return failures:
  those test journeys injected Android Back immediately after clicking Settings,
  before asserting that Settings had actually rendered. Strengthened the shared
  test helper with an explicit displayed Settings-entry assertion, matching the
  actual user-visible transition before Back. No production navigation code,
  history/collection assertion, suite, retry or timeout was relaxed. The same
  exact focused Gradle command then passed all6 discovery cases, zero
  failures/errors/skips (2m59s).
- The first complete
  `.\gradlew.bat :core:cleanTest :core:build :app:build :app:lintDebug :app:lintRelease :app:connectedDebugAndroidTest --no-daemon --console=plain`
  executed all72 connected cases (14m22s) and exposed one existing Favorites
  rejection-render assertion: the model correctly retained the accepted query
  and marked rejection, but the displayed result count still showed the prior
  valid results rather than zero. Changed Favorites derivation to receive the
  exact Compose-collected lyric state used by its failure/count UI, instead of
  separately rereading hidden mutable flow state inside the derivation. This
  preserves the existing invalid-query contract; its original assertions remain
  unchanged. No failing case was removed, retried automatically or weakened.
- Corrected combined
  `.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=in.daasavaani.app.FavoritesBrowsingTest,in.daasavaani.app.AnotherLyricUiTest' --no-daemon --console=plain`
  passed all8 Favorites/discovery cases with zero failures/errors/skips (5m31s).
- Final `npm run config` and the complete
  `.\gradlew.bat :core:cleanTest :core:build :app:build :app:lintDebug :app:lintRelease :app:connectedDebugAndroidTest --no-daemon --console=plain`
  passed (14m19s). XML reports confirm all44 core and72 connected tests,
  zero failures/errors/skips, both app variants built and both lint variants
  with zero errors/58 warnings. The entire core suite actually re-executed;
  app JVM tasks remain NO-SOURCE. Required suites were neither narrowed nor
  skipped; Gradle's conditional no-source/vital tasks are not test suites.
- Final tracked-file hygiene passed for88 files; the README's exact unique-key
  YAML command parsed all5 `.github` YAML files; actionlint with existing
  ShellCheck0.11.0 and both staged/unstaged whitespace checks passed. All bounded
  Gradle helpers completed and owned discovery diagnostic logs were removed.
  Existing5173 still returnedHTTP200 and intended5556 remained a device.
  No server/emulator was started, stopped or reconfigured, and no Website file,
  dist output, other consumer pin or dependency was changed. The exclusive
  stable-dist/device/HTTP slot was explicitly released after these checks.
- These permitted synthetic tests establish the bounded session selector,
  literal reader, local-state isolation and navigation behavior only, not real
  lyric correctness, personalized recommendations, durable offline preparation,
  full EXP-02/OFF-12 conformance, physical-device/accessibility acceptance or a
  release-ready APK. Native main publication remains the parent's separately
  authorized follow-up; this batch creates a local discovery commit only.

## 2026-10-22: explicit native minimal-list link Sharesheet handoff

- Owner authorized this bounded native-only XFER-02/06/08 and QUAL-05 slice from
  main `cde5b199eb08a285e3fc441c90c4d5d00710f5f5`, retaining independent Shared
  `44c21b9d139239e3003c293dfe8058ad80393fa6`, the complete codec/file fallback,
  Room5 and unrelated work. No Root, Shared, Library or dependency change.
- Archived superseded README status: complete canonical links were displayed,
  selectable and explicitly copyable; complete gzip used Create Document, but
  native OS sharing was still open. Its old limitations said no verified OS
  share/App Link interoperability and listed OS sharing among later gates.
  Actual receiver/device interoperability and signed incoming App Links remain
  open; this batch does not close those acceptance gates.
- Added one explicit Share snapshot link action to the common successful
  link-export surface used by Settings v1/v2 document/link previews and saved
  typed-v2 lists. It passes only the exact already validated complete canonical
  link through ACTION_SEND/text/plain in Android's standard chooser. It adds no
  file/URI grant, FileProvider, temporary file, network, personal/history/revision
  write, preparation, automatic handoff or save.
- Reused pinned bilingual share/action/failure/handoff captions. Unavailable
  activity and security failures show the existing fallback caption, with no
  broad exception catch. Launch feedback claims only system-interface handoff,
  not recipient receipt/delivery. ACTION_SEND supplies no trustworthy delivery
  or chooser-cancellation receipt; dismissal never fabricates either result.
  Display/select/copy and complete Create Document fallback remain available,
  including after failure/dismissal. Oversized links never gain a Share action.
- Added five connected cases using only synthetic snapshots for exact
  v1/v2/2048-ASCII-character-link
  chooser/payload shape; narrow expected failures and unexpected-bug propagation;
  explicit link/file preview and saved-list actions, intercepted cancellation,
  visible failure, copy/document fallback and complete personal-row preservation;
  and over-cap refusal with complete gzip fallback. A separate emulator smoke
  case actually opens the system chooser and returns with Android Back,
  preserving the snapshot/link/fallbacks and personal state. Intercepted cases
  use Android instrumentation monitors; none establishes actual receiver
  delivery or real-device Sharesheet/provider interoperability.
- The initial unquoted PowerShell instrumentation selector was split into an
  invalid Gradle task before compilation; quoting the complete `-P...` argument
  corrected that command. The first compiled five-case run then correctly
  exposed two failures: the app's localized Compose context is a configuration
  context, not an Activity context, so direct `startActivity` required NEW_TASK.
  Corrected the cause by launching through the existing owning Activity result
  registry, without adding a new task/flag or guessing return-code meaning.
- Corrected focused
  `.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=in.daasavaani.app.ListLinkShareTest' --no-daemon --console=plain`
  passed all5 cases, zero failures/errors/skips (2m23s), including actual system
  chooser/Back. No assertion, suite, timeout or retry policy was weakened.
- Full validation uses the parent's exclusive connected/HTTP slot with unchanged
  validated Website installation dist
  `71ad82a6cdc51623e6eca1905963bde1bc7ac729`, the existing configured5173 server
  (HTTP200) and intended API36 `emulator-5556`. Existing JDK21/SDK36 and
  dependencies are reused, with
  `JAVA_HOME=C:\Users\pots\AppData\Local\DaasaVaani\toolchains\jdk` and
  `ANDROID_HOME=C:\Users\pots\AppData\Local\DaasaVaani\toolchains\android-sdk`;
  no dependency/toolchain install or Website rebuild is involved.
- Final full `npm run config` passed. Ran
  `.\gradlew.bat :core:cleanTest --no-daemon --console=plain` first so the
  unchanged complete core suite actually re-executed, then the documented
  `.\gradlew.bat :core:build :app:build :app:lintDebug :app:lintRelease :app:connectedDebugAndroidTest --no-daemon --console=plain`
  passed in13m49s: all40 core tests and66 connected tests, zero
  failures/errors/skips, both app variants built. XML reports confirm53 warnings
  and zero errors in each lint variant. App JVM tasks remain NO-SOURCE, not
  passing tests; Gradle conditional no-source/vital tasks are not test suites.
- Final `python tools\check_repository.py` passed for85 tracked files; the
  README's exact `node -e` unique-key YAML command parsed all5 `.github` YAML
  files; `actionlint .github\workflows\hygiene.yml` passed with existing
  ShellCheck0.11.0; both `git --no-pager diff --check` and
  `git --no-pager diff --cached --check` passed. No lint/test suppression,
  suite removal, skipped test or timeout/retry weakening was introduced.
- All bounded Gradle helpers completed; no HTTP server or emulator was started,
  stopped or reconfigured. Existing5173 still returnedHTTP200 and intended5556
  remained a device. Owned project-local diagnostic logs were removed and the
  exclusive connected/HTTP slot was released. Main publication awaits the
  parent's explicit same-task authorization; no native remote push/preflight
  was performed by this batch. Actual receiver/device/provider interoperability,
  incoming signed App Links and full transfer/release acceptance remain open.

## 2026-10-22: canonical title-catalog session continuity

- Owner authorized the bounded Android-only UX-13 slice from clean published main
  `33cd2eff7b34177d6e14b41f870712c206bfb9de`, preserving independent Shared
  `44c21b9d139239e3003c293dfe8058ad80393fa6`, Room5, typography and all unrelated
  personal rows. No Website, Shared, Library source or toolchain change belongs
  to this slice; connected/HTTP work requires the parent's stable-dist slot.
- Archived superseded behavior: the prepared canonical catalog retained its
  literal query in the process-local model, but exposed every matched title
  without configured expansion/count. Its local LazyListState was also used by
  the actual reader and disappeared when visiting Settings; title-return focus
  and Settings-to-canonical Android Back were absent. Favorites already had its
  own independent page/query/viewport session.
- Reused the existing bounded literal matcher, overflow-safe expansion helper,
  pinned page policy and bilingual count/more resources. The canonical catalog
  has independent expanded pages and outer session viewport; the reader uses a
  separate viewport. Arrow/Back restores the available originating title focus.
  Query changes reset pages/position, invalid queries retain accepted text without
  false no-match, and content replacement clamps unavailable context. Navigation
  and filters make no personal writes, preparation/index/network requests or
  additional successful-reader history opens.
- Added synthetic connected cases for actual configured boundaries, independent
  literal/query validation, complete tail reachability, reader/Settings/Back/focus,
  preference/history isolation, shortened/failed preparation and new-session
  reset. Existing fixture/Favorites behavior, typed identity, exact lyric layout,
  preparation consent and default typography remain unchanged. This is bounded
  UX-13/SEARCH-01 coverage, not Explore/full search/offline or release closure.
- Incremental `npm run config` and
  `.\gradlew.bat :core:build :app:build :app:lintDebug :app:lintRelease :app:assembleDebugAndroidTest --no-daemon --console=plain`
  passed with existing JDK21/SDK36 (final implementation rerun2m31s).
  All40 core tests passed with no failures/errors/skips; both lint variants
  retain55 warnings and zero errors. App JVM tasks are NO-SOURCE, not tests.
  New connected cases compile, but no connected/HTTP test has run before
  explicit parent stable-dist clearance. Repository hygiene, all5 .github YAML
  parses, actionlint with existing ShellCheck and working/index whitespace
  checks passed incrementally; final-tree connected/all-check gates and exact-SHA
  CI preflight/main publication remain required.
- Parent explicitly cleared the connected/HTTP slot against unchanged successful
  Website dist/main`1ee3dca067d0151e62b0ac6ca1215a5e7341f539`.
  The first complete Gradle command passed config/core/build/lints and60 of61
  connected cases, zero skipped; native title focus on reader Back failed.
  A layout-ready retry still failed focus and exposed an asynchronous test-only
  database-load/close race. Native Button touch-mode focus must be explicitly
  allowed for the returning title, after its actual layout attaches. Corrected
  that behavior, synchronized the test's Settings Back with its composed screen,
  and waited for each fresh-session Room load before closing it. One intermediate
  focused run confirmed reader focus but correctly failed the unsynchronized
  Settings Back before that test synchronization fix. No failed tree was pushed.
- Corrected
  `.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=in.daasavaani.app.LibraryBrowsingTest' --no-daemon --console=plain`
  passed all3 catalog cases, zero failures/errors/skips (2m44s). Their native
  focus assertions, exact viewport comparison, history receipt and unchanged
  personal-record assertions remain intact; no coverage was removed or weakened.
- Complete corrected final-tree validation passed:
  `npm run config`;
  `.\gradlew.bat :core:build :app:build :app:lintDebug :app:lintRelease :app:connectedDebugAndroidTest --no-daemon --console=plain`.
  All40 core and61 connected tests passed with zero failures/errors/skips;
  all variants built and both lint variants retain55 warnings/zero errors.
  App JVM tasks remain NO-SOURCE, not a passing test suite. The complete command
  elapsed11m40s on installed JDK21/SDK36 with `ANDROID_SERIAL=emulator-5556`.
  No toolchain/dependency install, server/emulator startup, shutdown or
  reconfiguration occurred. All connected/HTTP activity has finished and the
  parent's slot is explicitly released before Website validation resumes.
- Final staged `python tools\check_repository.py` passed83 tracked files;
  the README Node YAML command parsed all5 .github files with unique keys;
  existing `actionlint .github\workflows\hygiene.yml` with ShellCheck and both
  working/index `git --no-pager diff --check` passed. No Shared pin or Room/schema
  change occurred. These checks do not close real-device/accessibility,
  production/offline or release gates. Exact-SHA preflight/main publication is
  deliberately pending; no commit or push was attempted before slot release.

## 2026-10-22: independent native lyric font and density

- Owner authorized this bounded Android-only slice from clean main
  `7d754ea3f4e9a0a1b463cd153f8a6ad6f807c522`. Website, standalone Shared,
  Library, literal source text and the existing server/emulator are not owned
  by this slice. Shared publication/pin and stable-dist coordination are required
  before their dependent checks.
- Archived superseded current behavior: Room4/logical envelope4 stored
  language/theme/history and typed personal collections, but no lyric typography.
  Both native readers used 28sp font/48sp line height; fixture lines had 20dp
  separators (`spacingDp=8` times2.5). Settings had no font/density controls.
  Density/font preferences appeared among the open canonical-reader limitations.
- The bounded replacement centralizes the unchanged baseline plus 14–48sp/2sp
  bounds and 85/100/120-percent density in native settings/generation. Shared owns
  bilingual labels and default Normal/allowed choices. Both actual readers and
  Settings share independent persisted preferences; lyric text/layout/identity
  and UI/title/locale/theme remain separate. No inferred verses or cross-platform
  conversion is implemented.
- Room5/accounting5 strictly preserves all old values/identities/revision and adds
  only native font/default density. Frozen old schema/accounting adapters and
  prospective exact full UTF8 capacity checks precede atomic migration.
  Typography writes update only their columns/revision; no-op and stale fences
  are retained. Rapid intents serialize against preceding committed local state.
  Mounted exposure receipts do not depend on personal preference state, so peer
  history clears cannot be undone by typography/retry recomposition.
- Partial UX-02/03/14 and SET-04 only; Recite/Practice/helpers, offline snapshots,
  linguistic/device/accessibility and production/release acceptance remain open.
- Parent confirmed published Shared
  `44c21b9d139239e3003c293dfe8058ad80393fa6`, with exact preflight39731901423 and
  main39732154093 successful (30 contract tests). Independently adopted that
  exact consumer pin; no Shared source/resources were copied or modified.
- Incremental `npm run config` and
  `.\gradlew.bat :core:build :app:build :app:lintDebug :app:lintRelease :app:assembleDebugAndroidTest --no-daemon --console=plain`
  passed. All40 core tests passed with zero failures/errors/skips; instrumentation
  compiled. App JVM tasks remain NO-SOURCE, not counted as tests. The first build
  produced64 lint warnings/zero errors per variant; inspection identified unused
  generated native reading integers after moving consumers to generated core
  policy. Generation now emits those values only in their used core target,
  rather than duplicate unused Android resources. The same command on the
  corrected tree passed with55 warnings/zero errors per variant (Gradle/plugin/
  dependency update notices, existing icon/backup warnings and unused Shared/
  portable resources). No toolchain/dependency install was required.
- Parent explicitly cleared the connected/HTTP slot with Website dist unchanged.
  The first full
  `npm run config` followed by
  `.\gradlew.bat :core:build :app:build :app:lintDebug :app:lintRelease :app:connectedDebugAndroidTest --no-daemon --console=plain`
  passed config/core/build/lints and53 of58 connected cases (zero skipped).
  All five failures shared the new ReadingStoreTest seed: a tab in a personal
  placeholder correctly failed existing transfer validation. Corrected only that
  synthetic placeholder to permitted text; canonical lyric tabs remain unchanged
  and covered by actual reader tests. No failed tree was committed/pushed.
  `.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=in.daasavaani.app.ReadingStoreTest' --no-daemon --console=plain`
  then passed all5 focused cases, zero skipped (1m26s), confirming migration,
  exact accounting, no-op bounds, preserving writes and stale/rollback behavior.
- Corrected complete final-tree run passed:
  `npm run config`;
  `.\gradlew.bat :core:build :app:build :app:lintDebug :app:lintRelease :app:connectedDebugAndroidTest --no-daemon --console=plain`.
  All40 core and58 connected tests passed with zero failures/errors/skips;
  both lint variants had55 warnings and zero errors. App JVM remains NO-SOURCE,
  not a passing test suite. The complete command elapsed8m36s with installed
  JDK21/SDK36 and `ANDROID_SERIAL=emulator-5556`. No server/emulator was started,
  stopped or reconfigured. All connected/HTTP work finished before explicitly
  releasing the slot for Website builds; publication is a separate remaining gate.
- On the same-task publication resume, the tested implementation/settings/pin
  remained unchanged; only this completed validation evidence was added after the
  full run. Rechecked `npm run config`, `python tools\check_repository.py`
  (82 tracked files), strict YAML1.2 parsing for all5 workflow/form YAML files
  and owned settings, `actionlint .github\workflows\hygiene.yml` with installed
  ShellCheck, and both working/index `git --no-pager diff --check`; all passed.
  No connected/HTTP rerun was started against the parent's changing Website dist.
  Live main rules still required the exact CI context, with the owner-authorized
  temporary role5 exception and separate never-bypass force/deletion guards.

## 2026-10-22: bounded real Favorites title browsing

- Owner authorized this Android-only slice from clean main
  `c13171f6ed874aa5178c6c0cd0e5dc9dd3c09d0d`. Shared remains independently pinned
  at `9b8845a21bb529268bba49ffbf6a44dece45a9ed`; no Shared source/pin, Website
  build/output, Library source, demo server or emulator ownership was changed.
- Archived prior behavior: Favorites rendered an unfiltered unbounded saved-reference
  LazyColumn with no real-title filter or result-expansion state. Reader origin
  distinguished only saved lists, not Favorites. The fixture Library's existing
  limited results and isolated synthetic search remain unchanged.
- Reused pinned Shared `lyric_title_filter`, `show_more_results`, `result_count`
  and `favorites` resources, plus its configured search page size. No portable
  schema/setting/UI key was introduced. Literal case-insensitive real-title
  filtering remains distinct from Unicode16 list-name comparison, fixture search,
  UUID matching, aliases, tags and linguistic/transliteration acceptance.
- Favorites retains its independent accepted query/expansion in the lyric ViewModel
  and its viewport in the screen. Ordinary edits/clearing reset expansion/scroll;
  invalid queries retain accepted text but show failure with no stale results.
  Only verified real lyric titles match nonempty filters; empty filters retain all
  typed references, including unknowns before preparation and UUID-looking legacy
  values. Expandable pages reach every reference without a phantom final page.
  Pending/actual readers return through Android Back or arrow/Favorites, while
  explicit Owner-supplied lyrics navigation clears stale reader-origin context.
  Removal and complete catalog replacement retain a valid available context.
- No browsing operation calls personal writes or preparation. Existing successful
  reader text-exposure history remains initially-on, independently controllable;
  explicit favorite/list changes remain intentional writes. Room4/accounting
  envelope4, local UI locale/theme, reader/list/history behavior and unimplemented
  offline/script/backup/recitation/device/release gates remain unchanged.
- Added pure helper and connected synthetic coverage for page/two-page/
  two-page-plus-one reachability, typed unknowns, literal punctuation/case, no-match,
  invalid query, ordinary clear/reset, independent catalog query, section/reader
  return, pending Back, explicit removal and default-on history. This is partial
  SEARCH-01, UX-13 and LIB-01 evidence only; no reviewed lyric content was invented.
- Incremental `npm run config` passed. Initial
  `.\gradlew.bat :core:test :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --console=plain`
  exposed misplaced Kotlin scopes; compilation was corrected. The first focused
  connected invocation then exposed test-only syntax/equality mistakes; corrected.
  A subsequent two-case run passed the boundary journey but correctly failed the
  return assertion because its measurement preceded the test's final scroll.
  The measurement now records the actual pre-open viewport. A later pending-Back
  journey exposed a test dispatch before Compose installed the destination
  handler; the test now waits for the actual visible return action before Back.
  None of those failing intermediate trees was committed or pushed.
- After destination synchronization, focused
  `.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=in.daasavaani.app.FavoritesBrowsingTest' --no-daemon --console=plain`
  passed both connected cases with zero skipped. These injected synthetic sources
  make no HTTP request. Separate
  `npm run config` and
  `.\gradlew.bat :core:build :app:build :app:lintDebug :app:lintRelease --no-daemon --console=plain`
  passed with the installed JDK21/SDK36; all38 core tests passed (zero
  failures/errors/skips), both lint variants reported52 warnings and zero errors.
  App JVM tasks remain NO-SOURCE, not a claimed passing suite. Existing warning
  categories and unsigned-release limitations remain; no toolchain/dependency
  install was needed.
- `python tools\check_repository.py` passed76 tracked files.
  The README's exact Node/YAML parse command passed all5 `.github` YAML files.
  `actionlint .github\workflows\hygiene.yml` passed with installed ShellCheck;
  `git --no-pager diff --check` and `git --no-pager diff --cached --check` passed.
  Full connected finalization and publication are separate gates; these
  incremental results alone did not authorize or perform a push.
- The first complete finalization command
  `npm run config` followed by
  `.\gradlew.bat :core:build :app:build :app:lintDebug :app:lintRelease :app:connectedDebugAndroidTest --no-daemon --console=plain`
  passed config/core/build/lints but failed2 of50 connected tests (zero skipped).
  Existing history journeys correctly caught an unnecessary reader-selection
  reset when explicitly navigating to Owner-supplied lyrics: an already selected
  permitted background destination was lost. Navigation now clears only the
  stale origin flags, preserving established selection/exposure behavior.
  No failing tree was published. Further connected/HTTP checks paused for the
  parent's coordinated Website dist rebuild; the existing server/emulator stayed
  untouched.
- Corrected full run then passed49 of50 connected cases (zero skipped), including
  Favorites and the previously failing history cases. The remaining existing
  LaunchTest caught a wrong-thread Compose frame continuation resumed by an IO
  progress emission. Both fixture/real preparation progress callbacks now
  suspend to publish UI state on Main while downloads/decoding remain on IO,
  preserving ordered progress/cancellation. The synthetic configured-boundary
  journey additionally observes every progress emission through an unconfined
  collector and requires the Android main thread, making that thread-affinity
  regression deterministic without real content or network fixtures.
- Complete corrected-tree validation then passed:
  `npm run config`;
  `.\gradlew.bat :core:build :app:build :app:lintDebug :app:lintRelease :app:connectedDebugAndroidTest --no-daemon --console=plain`.
  All38 core and50 connected tests passed with zero failures/errors/skips;
  debug/release lint each had52 warnings and zero errors. App JVM tasks remain
  NO-SOURCE, not counted as tests. This included both formerly failing history
  journeys and actual fixture complete preparation/reading, without changing
  either existing server or emulator. Full command elapsed9m34s.
  Existing installed environment:
  `JAVA_HOME=C:\Users\pots\AppData\Local\DaasaVaani\toolchains\jdk`,
  `ANDROID_HOME=C:\Users\pots\AppData\Local\DaasaVaani\toolchains\android-sdk`,
  `ANDROID_SERIAL=emulator-5556`. The resulting owned batch retains the exact
  independent Shared9b pin, Room4 and logical envelope4. Exact-SHA local/CI
  preflight and main jobs remain publication gates, not inferred from that run.

## 2026-10-22: durable device-only Recently opened

- Owner authorized only this next Android slice in parallel with Website and
  fully validated exact-SHA direct-main batches. Started on clean main
  `4a89004c3c119fcab09099fc2d6482d176a5f640`. Independently adopted published Shared
  `9b8845a21bb529268bba49ffbf6a44dece45a9ed` (no floating submodule update).
  No Website, Shared source, Library, emulator ownership or existing demo server
  was changed.
- Archived superseded native limitations: schema3 had only typed favorites,
  lists/occurrences and language/theme, with logical accounting envelope3.
  Recently opened was unimplemented on both native readers and appeared in the
  README's open-work lists. Those boundaries are replaced by the bounded
  history slice below, not by full LIB-06/SET-04/QUAL-05 closure.
- Room schema4 creates an empty initially-on history on preserving supported
  v1/v2/v3 upgrades. Exact typed favorites, list/occurrence IDs, raw text/order,
  language/theme and revision are retained; no opens are reconstructed.
  Explicit saved false wins on every reopen. Exact accounting envelope4 includes
  the entire ordered history/enabled record and all existing personal overhead.
  History changes share revision/epoch and import/name-confirmation fences.
- Real and fixture reader text exposures acknowledge explicit request receipts
  once, without recording preparation/catalog/preview/background/unknown/failure
  routes. Already-first and disabled opens perform no write/revision increment.
  Clear/toggle never regenerate an open while a reader remains mounted. Errors
  remain visible without clearing readable content or prior personal data.
- Library Recently opened and Settings offer independent switch/Clear before
  preparation. Rows use exact already-verified typed maps; unresolved rows remain
  visible/clearable without downloads. No timestamps/title/text/query copies,
  inferred mappings, service payloads or minimal-transfer history were added.
  Canonical Kannada readers, disabled system backup, process-local content,
  unimplemented durable offline/script/backup/recitation features and production
  linguistic/device/release gates remain unchanged.
- Initial incremental `npm run config` and
  `.\gradlew.bat :core:test :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --console=plain`
  passed. The first full
  `.\gradlew.bat :core:build :app:build :app:lintDebug :app:lintRelease :app:connectedDebugAndroidTest --no-daemon --console=plain`
  was not a pass: connected instrumentation completed48 with5 failures.
  Independent envelope evidence caught misplaced history accounting; UI evidence
  caught duplicate/missing Library access controls, a synthetic-trigger test's
  premature fresh-store open, and a transfer preparation timeout. The accounting/
  navigation/harness defects were corrected before revalidation; no commit/push
  was made from that failing tree. Reader visibility was also tightened to the
  actual text viewport rather than lazy precomposition.
- Interim focused
  `.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=in.daasavaani.app.RecentHistoryStoreTest,in.daasavaani.app.RecentHistoryUiTest,in.daasavaani.app.TransferUiTest' --no-daemon --console=plain`
  completed12 with1 failure in combined-clear/enable refusal; the separate
  operation guard and its test were edited during that incremental compile.
  This mixed-build interim result was not accepted as validation of the final
  tree. The next full command rebuilds all changed inputs without concurrent edits.
- The next unchanged-input full command completed48 connected cases with1
  existing `PersonalUiTest` fixture-preparation timeout; all history cases passed.
  No cause for that timeout was established and no retry was added to the app or
  test. The existing journey now asserts preparation is visibly clickable,
  surfaces an actual preparation error instead of waiting only for a title, and
  waits for the intentional history write before its explicit Favorite action.
  `.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=in.daasavaani.app.PersonalUiTest' --no-daemon --console=plain`
  then passed1 connected test, zero skipped. No Website build/demo publication,
  dist/public rewrite, server change or emulator restart was invoked; native
  checks used the existing configured port5173 endpoint and emulator5556 only.
- Consistent final validation passed with the existing JDK21/SDK36 toolchains:
  `JAVA_HOME=C:\Users\pots\AppData\Local\DaasaVaani\toolchains\jdk`,
  `ANDROID_HOME=C:\Users\pots\AppData\Local\DaasaVaani\toolchains\android-sdk`,
  `ANDROID_SERIAL=emulator-5556`. No dependency/toolchain install was needed.
  `npm run config` passed.
  `.\gradlew.bat :core:build :app:build :app:lintDebug :app:lintRelease :app:connectedDebugAndroidTest --no-daemon --console=plain`
  passed (36 core tests and48 connected, zero failures/errors/skips).
  Both lint variants passed with54 warnings and zero errors; warnings concern
  dependency-update advice, unused catalog resources and existing manifest/icon
  guidance, not history source defects. App JVM tasks remain NO-SOURCE, not a
  claimed passing app JVM suite. Unsigned all-variant builds are not a signed release.
- Additional final-tree commands:
  `python tools\check_repository.py` passed74 tracked files;
  `node -e "const fs=require('node:fs'),path=require('node:path'),yaml=require('yaml');for(const p of fs.readdirSync('.github',{recursive:true}).filter(p=>p.endsWith('.yml')||p.endsWith('.yaml'))){const d=yaml.parseDocument(fs.readFileSync(path.join('.github',p),'utf8'),{uniqueKeys:true});if(d.errors.length)throw d.errors[0];console.log(p)}"`
  passed5 YAML files;
  `actionlint .github\workflows\hygiene.yml` passed with existing ShellCheck;
  `git --no-pager diff --check` and `git --no-pager diff --cached --check` passed.
  Shared exact-pin main run39714010247 was independently verified successful
  at `9b8845a21bb529268bba49ffbf6a44dece45a9ed`.

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
# Native local Learning slice (2026-10-22)

- Authorized exact independently adopted Shared pin:
  `166b7af343064f2190f2fe89aee98d42fef8e03a` (detached submodule).
  Read its learning record/schema/types/accepted, rejected and rejectedSemantic
  vectors plus existing personal/history/preparation/navigation contracts.
- Superseded README described Room schema5, with no learning table, v1→5
  preserving migrations and the compact accounting envelope
  `{formatVersion:5,revision,preferences:{language,theme,lyricFontSizeSp,readingDensity},favorites:[typed-reference],lists:[{id,name,entries:[typed-entry-with-id]}],recentHistory:{enabled,entries:[typed-reference]}}`.
  It classified bookmarks/learning state together as unimplemented. Schema6 now
  adds only explicit device-local labels; bookmarks and reviewed verse Practice/
  resume, full backups and durable content readiness remain unimplemented.
- Incremental `npm run config` and
  `.\gradlew.bat :core:test :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --console=plain`
  passed before adding the complete Learning connected cases. Existing Kotlin
  reified-intersection warnings remain in preference migration bind arrays.
- First non-device full attempt:
  `npm run config` passed;
  `.\gradlew.bat :core:clean :core:build :app:build :app:lintDebug :app:lintRelease :app:assembleDebugAndroidTest --no-daemon --console=plain`
  failed lint's `ViewModelConstructorInComposable` on the new Learning test harness.
  Moved construction outside composition; no production assertion was weakened.
  Device/HTTP/Website slot remains owned by the parent; no connected tests or
  Website/dist/server/device operations have been run by this slice yet.
- The second non-device attempt used the same command and failed core JUnit
  discovery because the new Shared-fixture test was accidentally nested in another
  test. Moved it to a normal public test method; retained all assertions. The next
  complete non-device run passed48 actual core tests, all app variants and both
  lints, plus connected APK compilation. App JVM tests were NO-SOURCE.
- Observed lint reports: debug57/release58 warnings, zero errors (unused
  resources40/41, dependency/update notices15, missing app icon1 and data-extraction
  rules1). These remain explicit limitations, not ignored errors or release acceptance.
- Final staged non-device rerun after pending-origin fixes:
  `npm run config` and
  `.\gradlew.bat :core:clean :core:build :app:build :app:lintDebug :app:lintRelease :app:assembleDebugAndroidTest --no-daemon --console=plain`
  passed (48 actual core tests, zero failures; all variants; both lints with the
  above57/58 warnings and zero errors; connected APK compiled only; app JVM
  NO-SOURCE). No connected test has run yet (82 staged `@Test` methods).
  `python tools\check_repository.py` passed94 tracked files; the README's exact
  Node strict/unique-key YAML command passed all5 workflow/issue/dependabot YAML
  files. `actionlint .github\workflows\hygiene.yml` passed with the installed
  ShellCheck0.11.0 on PATH; both `git --no-pager diff --check` and
  `git --no-pager diff --cached --check` passed. Owned intermediate logs were
  removed; no server/device helper was started. Remaining required completion
  gate: parent authorization of stable tested Website dist/HTTP/device slot,
  then full final configured validation including every connected test. No
  commit or push yet; this is staged implementation, not native parity/release acceptance.
- After the parent's explicit frozen-dist/device/HTTP grant, verified the existing
  user5173 fixture descriptor returned HTTP200 and the already running
  `emulator-5556` was API36. Used JDK21/SDK36 and
  `ANDROID_SERIAL=emulator-5556`; did not restart/replace either process, run
  Website builds/checks or create a server.
- First granted final command:
  `npm run config` passed; full
  `.\gradlew.bat :core:clean :core:build :app:build :app:lintDebug :app:lintRelease :app:connectedDebugAndroidTest --no-daemon --console=plain`
  failed: all82 connected cases executed, six failures, zero errors/skips.
  Launch/Personal reader text and one history-failure text-visibility assertion
  exposed the real added-control viewport regression. Put compact learning and
  favorite actions in one row and moved the honest Practice explanation into the
  reader menu (it remains visible in Learning). Full localized label/value remains
  accessible; long compact captions ellipsize rather than steal lyric height.
  Three new Learning Settings/Back cases also needed mounted-Settings and
  settled-Back synchronization; retained every state/history/origin assertion.
- Targeted regression command:
  `.\gradlew.bat :core:test :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=in.daasavaani.app.LaunchTest,in.daasavaani.app.LearningUiTest,in.daasavaani.app.PersonalUiTest,in.daasavaani.app.RecentHistoryUiTest,in.daasavaani.app.ReadingUiTest' --no-daemon --console=plain`
  executed12 connected cases:11 passed, one failure, zero errors/skips. All
  original viewport cases and the other new navigation cases passed; the
  preparation-free unresolved fixture case exposed a genuine duplicated pending
  return/unavailable branch. Kept one pending/preparation gate and moved the
  unavailable branch to the completely prepared catalog. Added coverage for
  unresolved fixture Settings/Back after preparation; no assertion was weakened.
- `.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=in.daasavaani.app.LearningUiTest' --no-daemon --console=plain`
  then passed all3 actual Learning UI cases, zero failures/errors/skips, including
  both unresolved pending gates and prepared unavailable Settings/Back. The prior
  targeted `:core:test` also passed48 actual core tests, zero failures.
- Final preserving-migration review found a tightly coupled integer-narrowing
  hazard: SQLite singleton ID4294967297 could wrap to Room ID1, and a live ordinal
  4294967296 could wrap to position0. Changed legacy/current singleton checks to
  exact longs and refused out-of-range live ordinals before accepting a snapshot.
  Extended the existing Learning storage cases with old-v5 non-destructive ID
  refusal and live-v6 ID/ordinal refusals plus exact-state recovery after repairing
  only the synthetic corruption. Valid records and migration defaults are unchanged.
  A complete fresh validation is required after this final fence hardening.
- The complete post-viewport/pending-fix command above finished successfully:
  all82 connected tests passed, zero failures/errors/skips, along with the full
  configured build/lint/core tasks. Because the additional integer-fence hardening
  was prepared while that prior APK was running, it is not treated as validation
  of the latest final source; the same complete command is rerun on that source.
- Final exact-source validation after the integer-fence hardening:
  `npm run config` and
  `.\gradlew.bat :core:clean :core:build :app:build :app:lintDebug :app:lintRelease :app:connectedDebugAndroidTest --no-daemon --console=plain`
  passed (BUILD SUCCESSFUL,19m6s). Actual final XML:48 core tests and82 connected
  tests, each with zero failures/errors/skips. All app variants and both required
  lint suites passed; debug57/release58 warnings, zero lint errors. The two
  pre-existing migration-array compiler warnings emitted twice each (four
  emissions across debug/release). App JVM test tasks were NO-SOURCE, not a
  passing application JVM suite.
- Final ancillary checks on the same source passed:
  `python tools\check_repository.py` (94 tracked files);
  the exact README Node strict/unique-key YAML command (all5 YAML files);
  `actionlint .github\workflows\hygiene.yml` with installed ShellCheck0.11.0;
  `git --no-pager diff --check` and `git --no-pager diff --cached --check`.
  The final history-only evidence update receives the same ancillary checks.
  All owned intermediate logs are removed after extracting these actual results;
  normal Gradle reports remain under ignored build outputs. No helper/server was
  started, user5173 and emulator5556 remain running and unchanged, and no Website
  build/dist/check, Library source or other consumer pin was modified by this slice.
- Coverage remains partial LEARN-04/SET-04/QUAL-05: optional self-assigned device-local
  labels, not assessment or reviewed verse Practice/resume, persistent content
  readiness, full backup/restore or production/release acceptance. Synthetic
  API36 instrumentation is not real-device/font-shaping/accessibility acceptance.
  No main push is performed; parent owns publication gates.
