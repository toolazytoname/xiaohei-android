# 小黑 Android — 开发分支

这是从 Operit **v1.12.1**（`4faa5cd2ae0b5ee2ffa94f21d6e43c5ca011f84a`）衍生的小黑 Android 源码。保留上游历史和许可证；主体为 **LGPL-3.0-only**，不是产品入口仓库的 MIT。原始 README 的上游功能与下载声明不等于本衍生版承诺。

## 两个版本

| 构建 / 桌面名称 | 包名 | 边界 |
|---|---|---|
| `commonRelease` / **小黑** | `studio.weichao.xiaohei` | 普通安卓基础助手公开预览；默认不需要Root，不默认自主跨App无障碍操作 |
| `commonEnhancedRelease` / **小黑·增强（+徽记）** | `studio.weichao.xiaohei.common` | 正式签名公开预览；仅建议 OnePlus 8T 实验 |
| `common` / **小黑·增强（+徽记）** | `studio.weichao.xiaohei.common` | 工程调试包，debug 签名；与正式签名预览不能覆盖安装 |

两包可并存，不覆盖官方Operit或历史共同版。模型接口由用户自行配置；源码、官网、报告不包含个人端点和密钥。

## 当前状态（2026-10-03）

- 公开预览政策已写入普通/增强两份捆绑文本：运营者韦超，公开邮箱 lazywc@gmail.com，生效 2026-10-03。这是预览政策，不是商店批准。
- 增强正式签名变体为 `commonEnhancedRelease`；`common` 仍是 debug 工程包，签名未改，以免破坏旧安装。
- 没有商店正式发布，没有声称持续语音、自然插话、息屏DSP、16KB运行或全部许可证履行已经通过。
- 预览说明见 `docs/xiaohei-public-preview.md`。

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
- 目标官网：https://xiaohei.weichao.studio （官网已独立部署并核验HTTPS；不表示App已发布）
- 源码开发分支：`feat/common-base`

## 版本辨识与源码工具（2026-10-03 UTC）

普通版保留原图标；增强版增加琥珀色加号，桌面名称为“小黑·增强”。两者包名、签名、入口组件均保持不变，可直接覆盖更新，不应清数据。application/MainActivity独立指定名称，避免上游本地化字符串把它们重新标成Operit。

共同版默认角色采用自有名称/头像；只迁移已知默认值，保留自定义设置。角色名迁移同步处理按名绑定的会话（有同名自定义卡则不迁移）。语音面板新增“停止回答”（继续听）与“结束语音”（取消生成/播报及录音），不等于声学验收已通过。

- `tools/xiaohei-release/`：实际APK/AAB静态检查；增强版通过不表示其16KB通过。
- `tools/xiaohei-source-package/`：二进制图标可重建的源码补丁、固定子模块归档、六个本地AAR的实际哈希与配方。未声称干净环境可复现或全部许可证义务完成；不分发私有签名/模型凭据。
