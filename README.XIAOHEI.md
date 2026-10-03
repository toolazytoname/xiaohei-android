# 小黑 Android — 开发分支

这是从 Operit **v1.12.1**（`4faa5cd2ae0b5ee2ffa94f21d6e43c5ca011f84a`）衍生的小黑 Android 源码。保留上游历史和许可证；主体为 **LGPL-3.0-only**，不是产品入口仓库的 MIT。原始 README 的上游功能与下载声明不等于本衍生版承诺。

## 两个版本

| 构建 | 包名 | 边界 |
|---|---|---|
| `commonRelease` | `studio.weichao.xiaohei` | 普通安卓基础助手；默认不需要Root，不默认自主跨App无障碍操作 |
| `common` | `studio.weichao.xiaohei.common` | 增强自用侧载/可调试；OnePlus系统助手/DSP独立验收 |

两包可并存，不覆盖官方Operit或历史共同版。模型接口由用户自行配置；源码、官网、报告不包含个人端点和密钥。

## 当前状态（2026-10-02，本地工作日）

- 已构建两版；新版首启说明的两个变体宿主测试各509项通过（同一套场景，不能加总成1018个独立场景）。
- OnePlus8T/Android14/4KB设备上，增强版用户自配GLM接口的单次文字回复及会话重启读取已观察。不是语音/DSP/升级/16KB运行通过。
- 首启运营说明仍为**未发布测试草稿**。个人运营者姓名、公开联系邮箱、生效日期与正式隐私政策待补。
- 没有商店正式发布，没有声称持续语音、自然插话、息屏DSP、全机型或全部许可证履行已经通过。

## 构建依赖与已知缺口

这是源代码开发检查点，**不是完整可复现发行包**。JDK21/Android SDK36/NDK27是当前App工具链；本地ONNX配方另用JDK17。签名由本机私有配置提供，仓库不含签名密钥。

本地构建还引用以下未随本次源码提交分发的AAR；许可及完整源码/配方履行仍在整理，不能靠这个检查点假装已完成：

- `app/libs/ffmpeg-kit-local.aar`（增强侧使用；商店原生裁剪）
- `app/libs/android-gif-drawable-1.2.28-16kb.aar`
- `app/libs/onnxruntime-android-1.29.0-arm64-cpu-16kb.aar`
- `app/libs/graphics-path-1.0.1-16kb-arm64.aar`
- `app/libs/filament-android-1.69.2-arm64-relro.aar`
- `app/libs/filament-utils-android-1.69.2-arm64-relro.aar`

terminal子模块固定`e4442bc6a047b6165bf59103721ad143149c620d`，本地16KB链接改动随`patches/terminal-16kb.patch`记录。干净子模块先`git -C terminal apply --check ../patches/terminal-16kb.patch`再apply；不要重复套补丁。子模块中用户原有未提交状态未被重置。

私有模型/OEM库、APK/AAB、设备日志、测试录音、凭据、缓存均不随本次提交。历史`docs/common-release-build.md`等文件保留原阶段记录，身份与分层以本页及当前源码为准。

## 导航

- 产品/设备入口：https://github.com/toolazytoname/xiaohei-phone-agent
- 目标官网：https://xiaohei.weichao.studio （部署另行核验，不表示App已发布）
- 源码开发分支：`feat/common-base`
