# DaasaVaani Android

Bootstrap for the native Kotlin/Compose Android app, targeting API 26 and above.
The intended structure is an app plus pure Kotlin core modules, with hybrid
feature presentation and shared infrastructure. No application, application ID,
Gradle manifests or toolchain versions have been selected or generated here.

[Shared](https://github.com/DaasaVaani/shared) is a language-neutral source
dependency at `shared/`, not a shared application runtime. It owns normative
contracts, portable fixtures and reviewed YAML UI catalogs. This app pins its
Shared commit independently from Website and Library; updates are not lockstep.
UI catalog generator ownership remains undecided.

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
