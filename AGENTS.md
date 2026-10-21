# AI working instructions

## Start and scope

Read [README](README.md), the
[product requirements](https://github.com/DaasaVaani/DaasaVaani.github.io/blob/main/docs/requirements.md),
[current design](https://github.com/DaasaVaani/shared/blob/main/docs/implementation-specification.md)
and [validation policy](https://github.com/DaasaVaani/DaasaVaani.github.io/blob/main/docs/testing-and-release.md).
Staged implementation is authorized. The current native fixture preparation/
reading/search demo is not production design closure, durable offline support or
complete personal/Android parity. Do not silently close those remaining gates.

- Kotlin/Compose/Material3, API26+, app -> pure Kotlin core. No web wrapper or
  bundled lyrics. Use actual configured Gradle commands in README.
- Keep synthetic content and in-memory preparation visibly labeled. Never claim
  a process-local cache survives restarts or constitutes an activated offline snapshot.
- Pin Shared independently. Shared owns contracts/fixtures/portable settings/UI
  catalogs and committed generated resources; Library owns sources and publisher.
- All backup-covered personal state/preferences use one Room database, separate
  from content/transfer control. Preserve typed references and stale-writer fencing.
- Full backups are Android-to-Android, confirmed recoverable replacement; locale
  is a truthful postcommit side effect. System backup is best effort with personal-
  only allowlists; never restore false content readiness.
- Explicit complete preparation gates lyrics, not Settings/recovery/list preview.
  Keep active content until explicit validated replacement; failures preserve it.
  No offline video/images, automatic refresh, guessed reanchoring or silent scripts.
- Kannada is canonical, transliterate never translate. UI locale and lyric script
  are independent; use system fonts and disclose unsupported rendering.
- Product knobs belong in `settings/`; portable policy belongs in Shared.
  Dependency/toolchain pins belong in native manifests/catalogs/wrappers/locks.
  Signing keys stay outside CI, repository and logs.

## Documentation and history

This root file is the single AI entrypoint. Update current README, contracts,
commands, compatibility and release guidance alongside changes. Keep only
current approved rules, limitations and open work outside [HISTORY.md](HISTORY.md).
Move superseded choices, past alternatives, chronology, dated observations and
completed validation records into HISTORY before removal. Preserve current
approved details; history is never implementation authorization or present evidence.

## Finalization and pushing

- Incremental changes may use focused checks; finalization/confirmation and every
  push require complete applicable builds and all configured tests/checks of the
  final change set, not only targeted tests. Include dependent contract evidence.
- Available now: settings generation, core JVM tests, all-variant build/lint, connected
  Room/Compose tests, `python tools/check_repository.py`, workflow YAML/whitespace checks
  described in [README](README.md#validation). App JVM tests have NO-SOURCE.
- Documentation-only work is not automatically exempt now that tooling exists.
  State unimplemented/untested production and device coverage explicitly.
- Once code tooling exists, run full configured build/lint/JVM/instrumentation
  suites. Missing device/tooling, skipped tests and failures block required
  completion/push; report blockers and request guidance. Do not weaken scope,
  create a fake build, or install an assumed toolchain to obtain a green result.
- Record exact commands/results and limitations in HISTORY, update command
  guidance when tooling changes, and rerun affected validation after final edits.
- Passing checks is not push authorization. Respect protections, preserve
  unrelated work/staging and separate Shared pins. Do not reset/amend user work.
  Report real changes, coverage, checks, remaining gates and commit/push state.
