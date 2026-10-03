# Common-base chat plugin-init slice status (2026-09-15)

## This round (MNN kFdChunk → assembleCommon)

- Copied portable apply script / hashes / review diff into `cmake/patches/mnn-kfdchunk/` (no fixtures).
- `llm/mnn/CMakeLists.txt`: default MNN ref is `451006a22d059db759eb036585f2ad6b711feaeb` (not `master`). Explicit `OPERIT_MNN_GIT_REF` cache/command-line to any other ref is a configure fatal, not a silent retarget. Backends/optimizations unchanged.
- After `operit_prepare_git_source` for MNN and before `add_subdirectory`, `include` of `apply_kFdChunk_odr.cmake` (missing file or apply refuse is fatal).
- Fixture apply + idempotent of the two clean OpenCL files, then `:app:assembleCommon`, recorded in `integrations/operit-upstream-build/common-assemble.{log,json,md}`.
- Worker `:app:assembleCommon` wrapper 77563 rc=0 in 15s (240 tasks, 24 executed / 216 up-to-date). APK `app/build/outputs/apk/common/app-common.apk` 411068976 bytes sha256 `e8e9f5cea98462306bd412ba84737329cd723711ed8d80deed3bd994d9a41bb8`; apksigner v2 Verifies; aapt package `com.ai.assistance.operit.common` versionName 1.12.1 versionCode 46 minSdk 26 targetSdk 34. APK mtime 03:36:25Z is before this wrapper 03:36:43Z (`packageCommon`/`assembleCommon` UP-TO-DATE). CMake configure_stdout 03:33:54Z: `mnn-link-fix: applied` and OpenCL ON; cache `OPERIT_MNN_GIT_REF=451006a22d059db759eb036585f2ad6b711feaeb`.
- Worker record only. No install/ADB/commit/push.

## Previous round (chat plugin-init)

- Common `EnhancedAIService` no longer constructs `PackageManager` at member init. Original false still calls `PackageManager.getInstance` when the property is read.
- `CharacterCardToolAccessResolver.resolve` in common mode loads the card config only, then `CommonBaseCharacterCardToolAccess`: catalog ∩ card allow-list ∩ global visibility. It does not call `PackageManager`, SkillRepository, or MCP. Original false still requires `PackageManager` and enumerates packages/skills/MCP; null PM throws instead of skipping permissions.
- `ToolExecutionManager` accepts nullable `PackageManager`. Common inject-context and unavailable-tool diagnosis use `CommonBasePackageLookup` so `getAvailablePackages` / `ensureInitialized` are not invoked. Original false still looks up packages and propagates lookup failures.
- First-chat prompt path (`ConversationService` / `SystemPromptConfig`) skips enabled-package, MCP, and skill enumeration in common mode.
- Tool Call list in common mode also applies role-card builtin allow/deny after catalog filter. Guard duplicate-parameter check and same-`AITool` confirmation binding are unchanged.
- Extracted `ResolvedCharacterCardToolAccess` to its own file (same package); behavior of the data class is unchanged.

## Tests

- Added `CommonBaseChatPluginInitTest.kt`: lookup lambda is not invoked when common-enabled; original false still invokes and does not swallow errors; custom card keeps `start_app`, rejects `execute_intent`/`use_package`/plugin names with empty package/MCP sets; confirmed duplicate `package_name` on the same `AITool` is still denied.
- Isolated kotlinc (not Gradle) compiled production commonbase + `AITool`/`ResolvedCharacterCardToolAccess` with the same BuildConfig/ToolExecutionManager/result stubs as the prior guard check; JUnit 4.13.2: 16 tests OK (`CommonBaseExecutionGuardTest` + `CommonBasePackageLookupTest` + `CommonBaseCharacterCardToolAccessTest`). Does not verify Android UI, first-chat APK logs, or full assemble.
- This round: two-file fixture apply + idempotent via SDK CMake 3.22.1 `-P` on the copied apply script; assembleCommon evidence in `common-assemble.*`.

## git diff --check

- Ran after edits; result recorded in worker reply.

## Not verified

- Install / ADB / Play upload.
- No full first-chat log on a common APK proving PackageManager stays uninitialized.
- `ToolRegistration.resolveCurrentRoleCardToolAccess` still calls `getOrCreatePackageManager` on the original registerAllTools path; common `registerAllTools` returns before that nested function.

## Boundaries

- Guard / capability policy evaluation rules not rewritten.
- Original `source/` / old 小黑 / board / Gradle / global settings / terminal / git metadata untouched this round except allowed assemble logs/scripts under `integrations/operit-upstream-build/`.
- Third-worker ignore-dependency assets not modified.
- Other dependency versions / targetSdk / business code not changed this round.
