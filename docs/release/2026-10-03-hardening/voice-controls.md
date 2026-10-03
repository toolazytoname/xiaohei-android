---
date: 2026-10-03
scope: common-profile voice stop/end UI lifecycle
---

# 共同版语音停止/结束控件收尾

本文件记录全屏语音态「停止回答」与「结束语音」的生命周期修补。不是声学验收，也不是自然插话已解决。未改 ASR 阈值、回声策略或停止词。本 worker 未跑 Gradle、未接设备、未开麦克风。

## 原本状况

停止回答走 `stopAnswering` → `interruptAiTurnAndResumeCapture`：取消当前回答和 TTS，恢复听写。结束语音走 `endVoiceSession`：先 `onCancelMessage` 和取消 `aiStreamJob` / 最新 `ttsSpeakJob`，再 `exitWaveMode` 停麦。

实际仍有几条会在结束后把播报或采集拉起来的路径：

- `ttsSpeakJob` 只指向 join 链上最后一段。只 cancel 这一段时，更早的分段仍会在 `voiceService.stop()` 之后 `speak`
- `interruptAiTurnAndResumeCapture` 的 `startVoiceCapture` 在未跟踪的 `launch` 里，且不看语音态是否还开着。先停止回答再马上结束，延迟 job 会把麦重新打开
- `exitWaveMode` 本身不抬代。点头像或空闲超时离开后，同一条延迟 job 同样会重启采集
- 同一条已取消的 `contentStream` 若再进入 `processAndSpeakAiMessage`，`activeAiStreamIdentity` 已被清空，会重新挂 collector 并排队 TTS
- 动作条 `maxLines = 1` 加 `TextOverflow.Ellipsis`。窄悬浮窗或大字号时，「Stop answering」一类文案被裁成省略号，48dp 最小高度仍在

非共同版不显示这两个按钮。头像中断和 `exitWaveMode` 仍共用 ViewModel，所以上述采集/TTS 竞态是共同的，修在 ViewModel 里。

底部按住说话仍走公开的 `startVoiceCapture()`，不经 `allowsCaptureStart`，也不看 `waveOpen`。听写投递看 `captureSession`：开麦记下当时的 `session`，`stopVoiceCapture(cancel)`、`exitWaveMode` 和 `cleanup` 清掉。结束后残留 ASR 因 `captureSession` 为空不 `onSendMessage`；离开语音态后再按住底部说话仍可投递。若用 `waveOpen` 挡结果，底部听写会在非语音态被误杀。

## 意图与结果

停止回答：同一 `session`，听写可以恢复；抬 `speech` 代，已排队 TTS 不得再 `speak`。结束语音：先记下并丢弃当前流、抬 `session` 与 `speech`、关掉 `waveOpen`，再取消生产者、再停麦。过期 job 在 `speak` / 开麦前核对代数，对不上就停。新的文字轮次用新的 `speech` 代，仍可播报。

窄窗（`maxWidth < 340.dp`）或 `fontScale >= 1.2` 时两个按钮改成纵向排列，全文可换行，不用单行省略号，最小 48dp。

## 作用域

	docs/release/2026-10-03-hardening/
	app/src/main/java/com/ai/assistance/operit/ui/floating/ui/fullscreen/components/VoiceSessionActionBar.kt
	app/src/main/java/com/ai/assistance/operit/ui/floating/ui/fullscreen/viewmodel/FloatingFullscreenModeViewModel.kt
	app/src/main/java/com/ai/assistance/operit/ui/floating/ui/fullscreen/viewmodel/VoiceSessionEpoch.kt
	app/src/test/java/com/ai/assistance/operit/ui/floating/ui/fullscreen/viewmodel/VoiceSessionEpochTest.kt

## 核对（代码路径，不是运行通过）

停止回答仍要求 `isWaveActive` 且中断条件成立，然后走 interrupt。结束语音仍先 `endSession` 再 `onCancelMessage` / cancel jobs / `exitWaveMode`。`exitWaveMode.leaveWave` 在已经 `endSession` 时是恒等，不会二次开麦。

`enqueueSpeak` 在 `previousJob.join()` 之后检查 `allowsQueuedSpeak`。`startWaveCaptureIfCurrent` 检查 `allowsCaptureStart`。`onSpeechResult` 只在 `captureSession` 非空且仍是当前 `session` 时 `onSendMessage`。

`processAndSpeakAiMessage` 对 AI 流先核对 `allowsStreamCollect` 与 `activeAiStreamIdentity == identity`，对不上或重复 replay 直接 return，再 `stopCurrentTtsPlayback` 和 `invalidateSpeech`。同一条 live stream 被 compose 再拉起来时，若先抬 `speech` 代再发现重复，会把正在播的 join 链静音。

未改 `VoiceBargeInPolicy` 停止词、抑制窗口或 ASR 阈值。按钮仍只在共同版语音态显示。

## 测试（已写，未跑）

`VoiceSessionEpochTest` 直接断言代数，不靠源码字符串：

- 停止回答保持 `session`，同一波可恢复采集；旧 `speech` 不得播；被丢弃的流不得再 collect
- 结束语音抬两代、关掉 `waveOpen`，旧采集/旧 TTS 都被拒绝
- 结束后再 `leaveWave` 恒等
- 点头像离开同样关掉 wave，拒绝旧 `session`
- 结束后再进入新波，允许新采集和新流
- `reset` 清掉丢弃流并拒绝旧 job
- `invalidateSpeech` 不挡住同一 `session` 的采集，也不改 `allowsStreamCollect` / 已丢弃流
- 先停止再结束：停止时仍允许的 listen `session`，结束之后被拒绝

## 未验证

真机按钮、波形 UI、ASR、停止后继续听、结束后麦保持关闭、TTS 是否听得见、窄窗/大字号排版、自然插话、DSP。首轮 worker 超时后主代理已改 ViewModel 接线；本文件只对照最终代码改文档与纯代数单测。待主代理集中验收。
