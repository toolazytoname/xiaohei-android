# 小黑对应源码快照工具

此工具从固定 Operit v1.12.1 基线加当前差异生成本地源码材料；不会上传或分发 APK。

```sh
python3 tools/xiaohei-source-package/make_source_package.py --apk /absolute/path/app-common-release.apk
python3 -m unittest discover -s tools/xiaohei-source-package/tests
```

用 `SOURCE_BUNDLE_DATE=2026-10-02-identity-ui` 选择不重复的输出目录；已存在目录会拒绝覆盖。输出在工具目录的 `out/source-bundle/`（Git忽略）。默认当前Android仓库与随工具提供的 `recipes/`；可以用 `SOURCE_PACKAGE_REPO`、`SOURCE_PACKAGE_RECIPES_ROOT` 指向本地输入。

- 对图标使用可恢复的 `git diff --binary --full-index`，不能用只有“Binary files differ”的普通差异。
- 上游与固定 terminal 子模块分别归档。展开后还要应用 `patches/terminal-16kb.patch`。
- 未跟踪源码文件按NUL分隔枚举，不遗漏嵌套目录或中文/空格名称；外部符号链接及 `_verify_work` 不导出。
- 本地AAR只记录实际哈希和配方，不自动发布二进制。补充了GIF、ONNX、graphics-path、Filament的白名单配方。
- 配方来自当前工作区，部分仍是macOS特定工具链路径；运行前按配方覆盖NDK/SDK/CMAKE/AAR等环境变量。**未完成从公开干净环境重建验收。**
- `strict` 只表示调用者给出的构建时源码指纹匹配，不证明全部二进制依赖的源码/许可齐全；不要用事后生成的同一指纹冒充构建时证据。
- 私有签名、接口配置、设备数据不属于源码包，不应添加到版本控制。当前仍需独立审查iText、FFmpeg及全部传递依赖许可。
