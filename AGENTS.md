# AI working instructions

## Start and scope

Read [README](README.md), the
[product requirements](https://github.com/DaasaVaani/DaasaVaani.github.io/blob/main/docs/requirements.md),
[current design](https://github.com/DaasaVaani/shared/blob/main/docs/implementation-specification.md)
and [validation policy](https://github.com/DaasaVaani/DaasaVaani.github.io/blob/main/docs/testing-and-release.md).
This is an Android bootstrap, not a working app. Final design approval and
separate implementation authorization remain pending. Ask before resolving gates.

- Kotlin/Compose/Material3, API26+, app -> pure Kotlin core. No web wrapper or
  bundled lyrics. Do not infer usable Gradle commands from ignored filenames.
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
- Available now: `python tools/check_repository.py`, workflow YAML syntax and
  whitespace checks described in [README](README.md#validation).
- Documentation-only work can finalize/push after every available documentation/
  bootstrap check passes while app tooling is absent. State explicitly that no
  Android build/JVM/instrumentation tests exist; never mark them passed.
- Once code tooling exists, run full configured build/lint/JVM/instrumentation
  suites. Missing device/tooling, skipped tests and failures block required
  completion/push; report blockers and request guidance. Do not weaken scope,
  create a fake build, or install an assumed toolchain to obtain a green result.
- Record exact commands/results and limitations in HISTORY, update command
  guidance when tooling changes, and rerun affected validation after final edits.
- Passing checks is not push authorization. Respect protections, preserve
  unrelated work/staging and separate Shared pins. Do not reset/amend user work.
  Report real changes, coverage, checks, remaining gates and commit/push state.
