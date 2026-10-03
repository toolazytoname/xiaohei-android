# Enhanced device capabilities (first slice)

Date: 2026-10-02.

## Flags

Both `common` and `commonRelease` keep `COMMON_BASE=true`. Native-library trim stays `COMMON_STORE=true` only on `commonRelease`.

New field: `BuildConfig.COMMON_ENHANCED`.

| Variant | COMMON_BASE | COMMON_STORE | COMMON_ENHANCED | `CommonBaseProfile.enhancedDevice` |
|---|---|---|---|---|
| default / original debug+release | false | false | false | false (fail closed) |
| `common` | true | false | true | true |
| `commonRelease` | true | true | false | false |
| illegal: enhanced without base | false | * | true | false |
| illegal: store + enhanced | true | true | true | false |

Do not set `COMMON_BASE=false` to “open everything”. Store tools, per-invocation confirmation, and JNI trim are unchanged.

## Tools

Store catalog (unchanged): `start_app`, `execute_intent`.

Enhanced catalog adds:

- `get_device_status` — battery percent, charging, media volume (percent + stream index), `PowerManager.isInteractive`, `KeyguardManager.isKeyguardLocked`. No serial, ANDROID_ID, app list, or other identifiers.
- `set_media_volume` — `percent` integer `0..100` via `AudioManager` `STREAM_MUSIC`. Set then read back the real stream index. Success only if readback equals the quantized target index. Result reports the quantized actual percent. Failures do not call shell/ADB.

Both new tools use the existing explicit-confirmation path (`forceExplicitConfirmation` / `CommonBaseGatedToolExecutor` / no JS-broadcast auto-allow). Store builds do not register them, do not prompt them, and deny direct calls.

## Wiring

Profile-selected catalog properties keep existing caller signatures (`SystemToolPrompts`, `SystemPromptConfig`, character-card access, `AIToolHandler.registerTool` gating). Registration of the two new tools is inside `registerCommonBaseTools` when `enhancedDevice` is true.

## Not verified here

Device/AudioManager runtime, Gradle assemble, 16KB devices, Play listing, DSP/root/assistant entry.
