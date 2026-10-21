# DaasaVaani Android

Native Kotlin/Compose/Material3 app targeting API26+, app -> pure Kotlin core.
Application ID in.daasavaani.app. **Current implementation is a native fixture demo,
not a production/offline release.** Explicit preparation verifies all synthetic
records before canonical Kannada reading and indexed search. Prepared data is
memory-only; there is no Room personal state, durable offline activation or complete
feature parity. Staged implementation is authorized; production gates remain.

Start with [AGENTS.md](AGENTS.md),
[requirements](https://github.com/DaasaVaani/DaasaVaani.github.io/blob/main/docs/requirements.md),
[current specification](https://github.com/DaasaVaani/shared/blob/main/docs/implementation-specification.md)
and [validation policy](https://github.com/DaasaVaani/DaasaVaani.github.io/blob/main/docs/testing-and-release.md).
Shared at shared/ is independently pinned contracts/fixtures/settings/UI resources,
not an app runtime. Clone recursively or initialize the submodule. Authorized pin
updates follow the [Shared-first publication order](https://github.com/DaasaVaani/DaasaVaani.github.io/blob/main/docs/testing-and-release.md#shared-submodule-updates):
publish and validate Shared before committing tested consumer pins.

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
  Current debug tooling uses JDK21/SDK36/Gradle9.1; the production/release and
  supported-device matrix remains incomplete.
- Root /open/ hash links with narrow association and signed-device verification.
  CI unsigned candidate -> maintainer local verify/sign -> protected verification/
  GitHub Releases. No signing key in CI/repository/logs; exact handoff still open.

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

Core JVM tests and a real Compose preparation-to-reading instrumentation test
exist. App JVM tests currently have no sources; do not count NO-SOURCE as a pass.
The debug APK allows cleartext only for the local fixture endpoint; release does not.
Install the debug APK from app/build/outputs/apk/debug/app-debug.apk for a local
simulator demo; it contains no lyrics. The current demo requires the local Website
server and explicitly discloses synthetic content and unavailable features.

CI builds all variants/core/JVM/lint; it does not claim a connected-device run.
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
