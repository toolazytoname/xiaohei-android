---
fork: local workspace
---

# 共同版聊天/语音 UI 小步收尾


原本全屏语音页可以点头像中断、用图标返回或关闭，但没有带文字的停止入口。默认角色卡显示名仍是 Operit。本轮只改授权路径，把共同版默认显示名收到小黑，并补上真实停止控件。不宣称声学验收。



## 原本状况


- 默认角色卡 ID 为 `default_character`，显示名为 Operit，默认头像 URI 为 `file:///android_asset/operit.png`
- 全屏语音态停止依赖点头像或顶部图标，没有 48dp 带文字入口
- 底部静音芯片只静音 TTS，不能当作停止



## 意图与结果


共同版新装默认显示名为小黑。已有数据仅在默认卡 ID 且名称仍是 Operit 时改一次显示名。自定义名称、提示词、其他角色卡和非共同版不改。头像只在当前 URI 等于已知上游原值、并且策略里有明确品牌 URI 时才改；本树没有已知品牌角色卡头像，因此头像不改写。

语音态在共同版显示“停止回答”和“结束语音”。前者在 AI 正在生成或播报时调用 `onCenterAvatarClick` 的中断分支；中断后录音仍可能继续，所以同时显示“停止回答后仍会继续听”。后者调用 `exitWaveMode`，会停止 TTS 和录音并离开语音态。关闭悬浮窗仍走右上角 `cleanup` 加 `onClose`。静音控件未改。不自动开始录音。不改身份 ID，不改权限。



## 作用域文件


- `app/src/main/java/com/ai/assistance/operit/core/commonbase/XiaoheiIdentityPolicy.kt`
- `app/src/test/java/com/ai/assistance/operit/core/commonbase/XiaoheiIdentityPolicyTest.kt`
- `app/src/main/java/com/ai/assistance/operit/data/preferences/CharacterCardManager.kt`
- `app/src/main/java/com/ai/assistance/operit/ui/floating/ui/fullscreen/screen/FloatingFullscreenScreen.kt`
- `app/src/main/java/com/ai/assistance/operit/ui/floating/ui/fullscreen/components/VoiceSessionActionBar.kt`
- `app/src/main/res/values/xiaohei_ui.xml`
- `app/src/main/res/values-en/xiaohei_ui.xml`
- `docs/TODO/xiaohei-chat-voice-ui.md`



## 测试用例


`XiaoheiIdentityPolicyTest`：

- 共同版新装显示名为小黑
- 非共同版新装显示名仍为 Operit
- 默认 ID 加 Operit 改一次为小黑，再跑为空
- 已是小黑则不再写
- 自定义默认卡名称不改
- 其他角色卡名叫 Operit 不改
- 非共同版 Operit 不改
- 缺名或空名不当作上游默认
- 已知上游头像且传入品牌 URI 时改写，再跑为空
- 品牌 URI 未知时不改上游头像
- 自定义、空、未知头像不改
- 非共同版和其他角色卡头像不改

本 worker 未跑 Gradle、未装设备、未做声学验收。待主代理复验。



## 剩余边界


- 会话历史若按角色名“Operit”绑定，本次只改默认卡显示名，没有走 `updateCharacterCard` 的会话改名
- `CharacterCardBilingualData` 不在授权路径，默认提示词仍写 Operit
- 本树没有已知品牌角色卡头像资产，`brandedDefaultAvatarUri` 为 null，头像保持上游 `operit.png`
- `interruptAiTurnAndResumeCapture` 仍是 ViewModel 私有方法；屏幕用 `onCenterAvatarClick`，并仅在与中断条件对齐时启用“停止回答”
- 唤醒进入后的自动关窗仍是空闲超时路径；“结束语音”不调用 `onClose`
- 点头像、返回窗口、关闭悬浮窗、TTS 静音仍在，行为未改成假停止
- 非共同版不显示这两个按钮
- 声音插话、录音状态机、16KB 真机均未测

## 主代理审查后的调整（2026-10-02 本地工作日）
- 打包自有 xiaohei.png 为默认角色头像，仅替换默认卡的已知上游头像URI；自定义头像不动。
- 默认名迁移同时持久化待迁移标记，并通过既有 Room 方法更新会话的角色名绑定；成功后清标记。若其他卡同名 Operit/小黑，跳过默认改名，避免劫持自定义卡绑定。不迁移历史消息正文或用户提示词。
- “停止回答”改走显式 stopAnswering，避免AI恰好结束时，点击旧UI误触头像的模式切换分支。“结束语音”先取消生成/播报生产者，再停止录音和播报。
- 上述实现仍需集中语音与故障恢复验收，不能当声学通过。
