# Changelog

## 2026-11-21

- Refined the shared Material component shapes for the built-in light, dark,
  black, and system themes.
- Improved the main file browser bars, touch targets, and path truncation.
- Enabled Android RTL layout support and exposed all currently translated
  app languages in the system per-app language settings.
- Aligned the launch theme with the app's shared theme.
- Kept private signing keystores and signing passwords out of the distributable archive.

### Validation

- XML parsing, locale/resource consistency, and `git diff --check` passed.
- Gradle resource compilation could not run because no Android SDK is installed
  in the environment.
