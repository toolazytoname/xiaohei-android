# 2026-10-03 工程收尾记录

这轮沿用两版包名、签名与数据，不调整用户模型、自定义角色、默认助手或DSP。不公开分发APK，不提交商店。

## 工程范围

- PDF转换：移除iText，中文/长文排版与分页、失败时保留原输出。详见 `pdf-conversion.md`。
- 语音停止/结束：收束异步工作，窄窗/大字体按钮。详见 `voice-controls.md`。
- 原生源码配方：显式工具链和输入，不依赖作者home路径或本地代理。详见 `../../../tools/xiaohei-source-package/recipes/README.md`。
- GitHub `Xiaohei release tooling`：在源码推送/PR时运行源码包工具、原生输入与APK检查器的宿主测试。它不是Android APK构建、设备验收或许可证结论。

## 验证边界

真实音频、中文PDF设备渲染、升级数据、Android15/16及16KB运行、DSP/锁屏/重启仍需集中验收。自然声音插话没有因按钮工程变动而成为已解决。

个人运营已确认；对外姓名、公开联系邮箱及生效日期不能由工程代填。许可闭包、原生依赖重建与商店账号资格未完成前，不能称可公开上线。

## 独立验证结果

- 首次编译发现Android `PdfDocument`不实现Closeable，已改显式try/finally并重新完整构建成功；未绕过编译门禁。
- Common、CommonRelease分别543宿主测试，0失败/错误/跳过；两profile静态检查通过。仅store要求16KB LOAD/RELRO、非debug及商店裁剪。
- 两APK与AAB的DEX均无iText命名空间；本轮NOTICE直接条目134，TODO0，不代表传递许可闭包。
- source工具23项与release verifier25项宿主测试通过；GIF新目录重建arm64库与生产一致。
- 本轮没有安装Android新包、没有设备/声学/PDF运行验收。当前手机仍是上一批已验收文字候选。
- 原生配方worker两轮超时后由主代理收束，未将超时标为完成。ONNX/Filament整体重建与FFmpeg来源义务仍待核验。
