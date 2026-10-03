# 小黑公开预览说明（草稿）

状态：工程草稿，供主代理构建、独立核验和对应源码打包。本文不是商店批准、不是稳定版、也不是已公开发布声明。

生效日期：2026-10-03。运营者：个人韦超。公开联系：lazywc@gmail.com。

## 给谁用

- 普通版建议一般用户。包名 `studio.weichao.xiaohei`，构建类型 `commonRelease`，产物 `app-common-release.apk`。默认不需要 Root，不把跨应用无障碍自主执行当作本预览功能。
- 增强版仅建议在 OnePlus 8T 上实验。包名 `studio.weichao.xiaohei.common`，正式签名构建类型 `commonEnhancedRelease`，产物 `app-common-enhanced-release.apk`。不要把它当成全机型产品。

两包包名不同，可以并存。模型接口由你自行配置。仓库、官网和本说明都不包含个人密钥或服务端点。

## 能力边界（公开预览）

两个公开预览包都不提供：终端环境（busybox / bash / proot / sudo）、FFmpeg、OCR、媒体转换。这些 native 不会打进 `commonRelease` / `commonEnhancedRelease`。入口应不可用；库文件不在包内不等于对应代码路径已在真机负向验收。

增强公开包仍是增强身份：受控设备工具实验（电量/充电/锁屏交互、媒体音量）保留，须当场确认。不要把它写成商店版，也不要把 native 裁剪说成取消了增强。

DSP：公开预览增强包使用正式 `commonStore` 签名，与旧 DSP Companion 的签名权限不匹配，不能承诺 DSP 可用。清单里出现权限不等于息屏唤醒已经通过。

无自动 Root。不改动已经安装的旧包；签名不同时不能覆盖安装，先备份，由你决定是否保留旧 `common` 工程包。

工程调试 `assembleCommon` 仍可包含终端与自制 FFmpeg JNI，那不是公开预览承诺。

## 未验收

下列能力均未完整验收，安装本预览不等于已经通过：

- 麦克风连续语音、自然插话、可听播报听感
- DSP 唤醒、息屏发声、拔线待机
- 16KB 页面真机运行
- Android 15/16 全机型、数据库升级、锁屏与重启
- 商店审核、国内上架资格、完整许可证履行

存在停止按钮或本机转写，不等于声学验收已经完成。增强版静态检查通过不等于 16KB 通过。

## 签名迁移：旧增强 debug 包

当前已安装的增强工程包来自 `common`：debug 签名，且 `debuggable`。公开预览增强包改用 `commonStore` 正式签名，包名不变，因此**不能覆盖安装**。

不要盲目卸载。先备份并核验备份可读，再由你决定是否迁移。

建议顺序：

1. 若你还要用旧会话和模型配置，先在应用内导出聊天备份，并自行抄下接口地址、模型名。密钥只写在你自己的私有记录里，不要发到公开地方。
2. 由你决定是否保留旧 `common` 工程包。本仓库继续保留 `assembleCommon`，用于调试，不是要删掉旧安装。
3. 同包名但签名不同，系统会拒绝覆盖安装。只有你确认备份并主动卸载旧增强包后，才能全新安装 `commonEnhancedRelease`；卸载会删除旧应用私有数据，数据不会自动迁移。
4. 普通版 `commonRelease` 的包名和签名路线不变，不走这次增强签名切换。

没有签名配置时，预览增强包应保持 unsigned，不得改用 debug 签名。主代理负责实际签名与核验。

## 政策与首启

普通版捆绑 `xiaohei-privacy-policy.zh-CN.md`。增强版捆绑 `xiaohei-privacy-policy-enhanced.zh-CN.md`。不要用普通版政策冒充增强版政策。

首启与欢迎文案标明：公开预览、模型自配、语音与 DSP 未完整验收。密钥按明文写入本机应用配置，没有另行加密。普通版拒绝明文 HTTP；增强版允许明文 HTTP 并信任用户证书。

## 源码许可

主体仍是 Operit v1.12.1 衍生，LGPL-3.0-only。对应源码包、NOTICE 与传递依赖履行由主代理核验，本草稿不声称已经完成。

## 构建提示（不在本 worker 执行）

```
./gradlew :app:assembleCommonRelease
./gradlew :app:assembleCommonEnhancedRelease
```

工程调试增强包仍是 `./gradlew :app:assembleCommon`。不要把 `assembleRelease` / `assembleNightly` 当成小黑预览产物。
