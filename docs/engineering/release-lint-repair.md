# commonRelease ExtraTranslation repair

Date: 2026-10-02.

Worker record only; not independent sign-off, not product/store readiness.

## Prior ExtraTranslation (stale vital report)

`app/build/reports/lint-results-commonRelease*` was absent. The previous fatal vital text report:

`app/build/intermediates/lint_vital_intermediate_text_report/commonRelease/lintVitalReportCommonRelease/lint-results-commonRelease.txt`

Matching definite incidents (file removed after the successful re-run):

`app/build/intermediates/lint_vital_partial_results/commonRelease/lintVitalAnalyzeCommonRelease/out/lint-definite.xml`

30 errors, all `ExtraTranslation`, all fatal. No other issue ids in that definite file.

Translations in `values-es`, `values-id`, `values-ko`, `values-ms`, `values-pt-rBR`, `values-ro` defined five keys that `values/` lacked (6 locales × 5 keys = 30). `values-en` did not define them, so it did not contribute ExtraTranslation.

## Default-locale keys (already in tree before this gate pass)

Added earlier to `app/src/main/res/values/strings.xml` (English equivalents; `memory_space_select` left as existing `选择记忆空间`). This gate pass did not edit `strings.xml`. Independent re-parse: XML ok; 7252 default strings; cross-locale name union vs `values/` had no extra keys; `%1$s` only on `memory_space_delete_warning`, matching es/id/ko/ms/pt-rBR/ro.

| Key | Default value | Placeholder |
|---|---|---|
| `memory_space_create` | Create a new memory space | none |
| `memory_space_rename` | Rename memory space | none |
| `memory_space_delete` | Delete memory space | none |
| `memory_space_name` | Memory space name | none |
| `memory_space_delete_warning` | Are you sure you want to delete "%1$s" and all memories contained in it? This action cannot be undone. | `%1$s` only |

No additional locale files were edited (not required for this vital run).

## Gate change

Removed the exact-name `lintVitalCommonRelease` `enabled = false` block from `app/build.gradle.kts`. Left the exact-name `assembleRelease` / `assembleNightly` rotation `finalizedBy` matchers unchanged. No other lint tasks were disabled.

## Gradle re-run (this pass)

Environment:

- `JAVA_HOME` = project Temurin 21.0.12.1+1 (`openjdk version "21.0.12.1"`)
- `ANDROID_HOME` / `ANDROID_SDK_ROOT` = `/Users/lazy/Library/Android/sdk`
- `ANDROID_NDK_HOME` = NDK 27.1.12297006
- `GRADLE_USER_HOME` = `integrations/operit-upstream-build/gradle-home`
- `--no-daemon --offline`; no `--refresh-dependencies`, no `clean`

Command (from `integrations/xiaohei-common-base`):

`./gradlew :app:lintVitalCommonRelease --no-daemon --offline`

Result:

- Gradle log: `BUILD SUCCESSFUL in 2m 10s` / `349 actionable tasks: 48 executed, 301 up-to-date`
- Wrapper shell later exited 1 on zsh read-only `status` after Gradle had already succeeded; not a lint failure
- Full wrapper log: `/tmp/xiaohei-lint-gate-finalize/gradle-lintVitalCommonRelease.log`
- Vital text report now: `No issues found.`
  - path: `app/build/intermediates/lint_vital_intermediate_text_report/commonRelease/lintVitalReportCommonRelease/lint-results-commonRelease.txt`
  - 17 bytes, SHA256 `a69ecb171bea9d40de22744c85220b4a4ea12f5a25ce7976d67f2c88c9195334`
- `app/build/reports/lint-results-commonRelease*` still absent (lintVital writes the intermediates text report)
- `lint-definite.xml` no longer present after the clean vital run

## Still not in this pass

- `values-en` still omits the five keys (MissingTranslation would be a non-vital warning if that check is run)
- `values-ro` `memory_space_delete_warning` still has a missing space after `„%1$s”` (not a vital incident)
- APK/AAB, device, Play listing, 16KB runtime, product release
