## Summary

Explain the change, why it is needed and related issues or approved decisions.

## Android behavior and Shared compatibility

Describe device/API, accessibility, offline and language/script impacts.
If changing `shared/`, list old/new commit SHAs, contract/fixture compatibility
and app migration or conformance evidence. Pins are independent; do not assume
Website or Library updates. Use "not applicable" with a reason as needed.

## Validation

List each exact command and actual result, including
`python tools/check_repository.py` and `git diff --check`.
Describe actual build/device checks only if available; explain checks not run.
Bootstrap hygiene does not constitute Android tests or a security audit.

## Contributor checklist

- [ ] I kept this focused and cited approval for significant design choices.
- [ ] I updated related documentation and tests, or explained why not.
- [ ] I checked for secrets/private data and have rights to contributed material.
- [ ] I preserved reviewed content, attribution and script independence.
- [ ] I described material AI assistance, human review and remaining risks below.

## AI assistance and remaining gaps

Describe review, uncertainty and follow-up work, or explain why not applicable.
