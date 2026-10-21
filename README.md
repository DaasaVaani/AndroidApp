# DaasaVaani Android

Native Kotlin/Compose/Material3 app targeting API26+, app -> pure Kotlin core.
Application ID in.daasavaani.app. **Bootstrap only:** no Gradle/app manifests,
application build or tests are implemented. Final design/coding approval pending.

Start with [AGENTS.md](AGENTS.md),
[requirements](https://github.com/DaasaVaani/DaasaVaani.github.io/blob/main/docs/requirements.md),
[current specification](https://github.com/DaasaVaani/shared/blob/main/docs/implementation-specification.md)
and [validation policy](https://github.com/DaasaVaani/DaasaVaani.github.io/blob/main/docs/testing-and-release.md).
Shared at shared/ is independently pinned contracts/fixtures/settings/UI resources,
not an app runtime. Clone recursively or initialize the submodule.

## Current boundaries

- No bundled lyrics. Explicit validated full preparation gates lyrics, not
  Settings/recovery/list preview. Preserve destinations without automatic saving.
- Prepared reads use active snapshot until explicit validated refresh; failures
  preserve it. No offline video/images. UIDT34+/FGS26-33 transfers with actual
  progress/cancel/restriction errors and verified-artifact resume.
- Kannada canonical, transliteration only, independent UI/script preferences,
  system-font limitations and explicit alternatives.
- One transactional personal Room database includes backed-up preferences, separate
  from content/control. Same-platform logical full backup replacement with revision/
  epoch/locale recovery; system backup best effort/personal-only, no false readiness.
- Configurable product values in settings, portable policy in Shared. Native
  dependency/toolchain manifests/catalogs/wrappers/locks own pins. AGP9 built-in
  Kotlin for app; core kotlin.jvm, Compose/serialization compatible Kotlin versions.
  Exact verified toolchain integration remains open.
- Root /open/ hash links with narrow association and signed-device verification.
  CI unsigned candidate -> maintainer local verify/sign -> protected verification/
  GitHub Releases. No signing key in CI/repository/logs; exact handoff still open.

## Validation

```powershell
python tools\check_repository.py
git --no-pager diff --check
git --no-pager diff --cached --check
```

CI also parses .github YAML. Include new files and all documentation links/fences.
These are bootstrap checks, not Android builds/tests. Focused incremental tests
are allowed; finalization/confirmation and every push require full applicable
builds/all suites. Documentation-only changes can use all available checks while
app tooling is absent, explicitly reporting absent builds/tests. Missing required
code checks block completion/push. Passing checks is not push authorization.

Update current docs alongside changes; archive past decisions/observations/check
records only in [HISTORY.md](HISTORY.md). Preserve unrelated work and Shared pins.
Use issue/PR templates and [security reporting](SECURITY.md). Software is
[Apache2.0](LICENSE); third-party content retains its rights.
