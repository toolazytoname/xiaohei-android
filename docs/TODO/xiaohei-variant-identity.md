---
fork: local workspace
---

# 双变体桌面名称与 launcher 图标

同一部手机上商店包与增强包都显示「小黑」，桌面无法分辨。包名、签名、用户配置和通知 smallIcon 保持不动。本文件记录接线与资源覆盖策略；APK 构建与安装由主代理复验。

## 原本状况

- `common` 与 `commonRelease` 都把 `app_name` 写成「小黑」
- 两变体共用 `app/src/common/AndroidManifest.xml`，application 的 `android:icon` / `android:roundIcon` 写死 `@mipmap/ic_launcher_simple(_round)`
- `src/common` 会被 `common` 自动收录，也会被 `commonRelease` 的 `sourceSets` 显式并入，因此不能在 `src/common/res` 里用同名资源区分图标
- 自有原图是五档 `drawable-*/ic_launcher_xiaohei_foreground.png`：108dp 画布、圆角方形主体约 16–92dp、天空底 `#02070F`，右上有星光

## 意图与结果

- 增强 `common`：桌面名「小黑·增强」，launcher 用带琥珀色「+」徽记的新 mipmap
- 商店 `commonRelease`：桌面名仍「小黑」，仍指向 `ic_launcher_simple(_round)`
- `debug` / `release` / `nightly` 仍走 `src/main/AndroidManifest.xml` 的原图标与各自 `app_name`
- 启动 Activity / 组件名不变

## 资源覆盖策略

1. 共享 overlay 只改占位符，不改资源名碰撞。`common` / `commonRelease` 的 `manifestPlaceholders` 分别填 `@mipmap/ic_launcher_xiaohei_enhanced(_round)` 与 `@mipmap/ic_launcher_simple(_round)`。
2. 增强资源放在 `src/main/res`，新名字 `ic_launcher_xiaohei_enhanced*`。商店包会带上这些文件，但 application 图标不引用它们；`shrinkResources` 对该变体为 false。
3. 应用内 `ic_launcher_xiaohei_foreground` 与通知 `ic_launcher_simple_foreground` 不改。
4. API 26+（`minSdk` 26）用 `mipmap-anydpi-v26` adaptive XML；密度目录 PNG 供给 legacy / 仍读 bitmap 的 launcher。square 与 round 各有 adaptive XML 和 PNG；圆角遮罩走系统 mask，legacy round 另做圆形 alpha。
5. 「+」画在原图前景上，圆心约 (65.8, 74.4)dp、含描边半径 10.5dp，落在 72dp 圆形视口（半径 36dp）内，不挡眼睛。形状是加号，不只靠换色。

## 作用域文件

- [app/build.gradle.kts](../../app/build.gradle.kts)
- [app/src/common/AndroidManifest.xml](../../app/src/common/AndroidManifest.xml)
- [app/src/main/res/mipmap-anydpi-v26/ic_launcher_xiaohei_enhanced.xml](../../app/src/main/res/mipmap-anydpi-v26/ic_launcher_xiaohei_enhanced.xml)
- [app/src/main/res/mipmap-anydpi-v26/ic_launcher_xiaohei_enhanced_round.xml](../../app/src/main/res/mipmap-anydpi-v26/ic_launcher_xiaohei_enhanced_round.xml)
- [app/src/main/res/drawable/ic_launcher_xiaohei_enhanced_background.xml](../../app/src/main/res/drawable/ic_launcher_xiaohei_enhanced_background.xml)
- 五档 `drawable-*/ic_launcher_xiaohei_enhanced_foreground.png`
- 五档 `mipmap-*/ic_launcher_xiaohei_enhanced.png` 与 `ic_launcher_xiaohei_enhanced_round.png`

## 待主代理

- 构建 `common` / `commonRelease`，核对 merged manifest 的 icon、roundIcon、label
- 确认 debug/release/nightly 未改
- 真机桌面：两包并存时名称与图标可分辨，方形与圆形 launcher 都不切掉「+」、不挡主体
- 不把本 worker 回复当作独立核验

[DONE] 源码与资源已按上列策略接好；构建与设备复验未做。

## 主代理审查补充
为避免 values-en 等上游本地化 app_name 把两个版本重新显示为 Operit，application 与 MainActivity 都使用变体专属 xiaoheiAppLabel 占位符。保持 activity/component 和 applicationId 不变。
