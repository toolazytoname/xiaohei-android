# Enhanced DSP / system-assistant bridge

Date: 2026-10-02.

This slice wires the enhanced-device build (`CommonBaseProfile.enhancedDevice`) to the existing OnePlus 8T DSP Companion. It is engineering only: no device, emulator, acoustic, or 16 KB runtime run was performed here. It is **not** unlock-free screen-off Q&A, and it is not a daily-use acceptance.

## What this slice does

- DSP entry is compile-gated by `enhancedDevice`. Store/commonRelease does not register the receiver, does not arm, and the settings card does not render.
- User switch defaults **off** and can be turned off at any time. Turning it off disarms and does not re-arm.
- “选择系统助手” opens `Settings.ACTION_VOICE_INPUT_SETTINGS`. The app does not write the system default, does not root-grant, and does not start CPU always-on wake.
- Enablement requires: Companion installed, `CLIENT_PACKAGE` metadata **exactly** equal to this app, `PackageManager.checkSignatures` match, signature permission usable on both packages, and this app’s `OperitVoiceInteractionService` selected as the system VoiceInteractionService. Missing install, old Companion (no metadata), mismatch, or missing permission return a specific error. The app never pretends success and never auto-installs Companion.
- `VoiceInteractionService.onReady` registers a **dynamic** receiver with the legacy signature permission `io.github.toolazytoname.xiaohei.permission.WAKEWORD_EVENT` and `RECEIVER_EXPORTED` (same-signature Companion, not an arbitrary broadcast). No new exported service or manifest-exported naked receiver is added.
- Wake extras must be the closed v1 set: `schema_version=wakeword-event.v1`, fixed `keyword_id=xiaobuxiaobu.0220.0828`, `confidence` 0..100, `capture_available=false`, `event_id` ASCII token with limited dedup. No text/PCM path. Disabled, not-ready, busy, duplicate, or invalid events do not start a second capture. Logs omit extras/event ids/Companion detail.
- Wake and manual `startListening` (when the user switch is on) call the signature-gated stop provider `content://io.github.toolazytoname.xiaohei.dsp.stop` `disarm` on a serial IO queue and require `ok && state=DETACHED` before `VoiceInteractionService.showSession` or ASR. A stop command alone is not treated as release. Failure keeps capture disabled; there is no retry loop and no fallback `startActivity`.
- Settings toggle and `prepareCapture` are suspend/async: they post onto the serial IO queue and return to the caller without `Future.get` / `CountDownLatch` on the main thread. The switch shows a busy state. Timed-out work is cancelled so a queued task does not run later. `showSession` is posted to the main looper and re-checks VIS, user switch, handoff generation, and the show gate at execution time; a late callback after timeout or cancel does not start capture. Exceptions in that runnable are swallowed so they cannot crash the main thread.
- Disable and VIS shutdown only record `DISARMED` when the provider result is verified `ok && DETACHED`. A false result or thrown provider error leaves `UNKNOWN` and does not re-arm. The user preference can still turn off.
- Session cleanup uses the token returned by `requestSessionExit()`, captured at the closure site and passed back to `onAudioSettled(token)`. A late callback with an old token cannot settle a newer session. ASR cancel and TTS stop must both succeed before settle/re-arm; failure keeps the session disarmed/unknown and reports `Failed`.
- Lockscreen: hide/cancel/destroy invalidates the unlock watcher and this pending enter so a later unlock cannot start recording. A duplicate `onShow` does not register a second receiver. Floating-start failure abandons only this session token and clears busy/pending state.
- Re-arm re-evaluates current enablement (selected assistant, signatures, permission, opt-in, VIS ready) from a fresh probe. Stale `visReady` alone is not enough.
- One assistant session covers multiple ASR/TTS turns. DSP is not re-armed on `stopListening` / utterance end. Re-arm happens only after the floating session actually exits **and** ASR cancel + TTS stop have completed, and only if the switch is still on and the session generation matches. Stale cleanup does not arm. VIS shutdown/destroy disarms and does not re-arm.
- `ARM` service start is recorded as `ARM_REQUESTED` only. `ARMED` requires a protected status payload / status-provider read whose state is actually `ACTIVE…`.
- Lockscreen: enhanced builds show “需要解锁” in the existing `OperitAssistActivity` / voice session UI and wait for the system unlock. History, auto-listen, and tools are not started before unlock. This is not a bypass of Keyguard.

## What this slice explicitly is not

- Not screen-off, still-locked spoken Q&A. The user must unlock with the system lock.
- Not a change to ASR, echo cancellation, stop-word matching, or models.
- Not a change to Companion auto-rearm (750 ms). Client `disarm`/detach after a callback is what prevents that re-arm; Companion code is not modified here.
- Not device/acoustic/power/16 KB acceptance, Play listing, or a claim that DSP is ready for daily use.

## Ports and tests

Pure policy lives under `core/devicebridge` without Android imports. Host tests drive a fake `DspCompanionPorts` and assert the live call list `disarm → verified → show`, not source-string matching.
