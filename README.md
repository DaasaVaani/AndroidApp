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
