---
type: hardening-note
date: 2026-10-03
---

# 文本/HTML 转 PDF：去掉 iText，改用平台 PdfDocument

这是共同版内部转换实现的迭代，对外方法签名仍是 `Boolean` 成败，不是新的公共 API。

## 为什么改

`DocumentConversionUtil.convertFromHtml(..., "pdf")` 是仓库里唯一的 iText 调用。iTextG 5.5.10 的许可与 Helvetica 缺汉字，都不适合继续放在共同基础版里。

同一工具里的 `convertTextToPdf` 原先走 PDFBox + `PDType1Font.HELVETICA`。长文只写一页，中文码位无法进 Type1 字体。这两条路径现在共用同一套写入器。

## 现在怎么写

新写入器是 `TextPdfWriter`，分页算法是 `PdfPagination`。

- 用系统 `sans-serif` 与 `StaticLayout` 做平台塑形和按宽换行，汉字走系统 fallback，不另引 PDF 库
- 按整行切页；单行高度大于可用页高时抛错，转换函数记日志并返回 `false`，不会裁掉这一行继续写
- 空文档写出一页空白 PDF；源文本里的显式换行会进 layout，不会先被压成单行
- HTML 只经 `android.text.Html.fromHtml(..., FROM_HTML_MODE_LEGACY)` 解码成可读段落再排版，不承诺完整 HTML 布局
- 先写到目标同目录的临时文件，完整 `writeTo` 且关闭 `PdfDocument` 与输出流之后，再 `ATOMIC_MOVE` 替换目标。失败删除临时文件，已有目标不被截断覆盖

`convertTextToPdf` 与 `convertFromHtml` 的 pdf 分支都接到这套写入器。Word→PDF 仍先抽文本再走 `convertTextToPdf`，因此一并获得多页和中文。

## 依赖

`app/build.gradle.kts` 与 `gradle/libs.versions.toml` 只删除了 iTextG 条目。PDFBox 仍用于读 PDF / 抽文本，没有换成别的写入库。

关于页上的 iText 许可文案、NOTICE 全量刷新，不在这次改动范围。

## 测试

`app/src/test/.../PdfPaginationTest.kt` 覆盖：整页刚好排满、跨页、单行等于页高、空行表、行高/页高非法、超高行拒绝、末行另起一页。主代理执行宿主单测。

`app/src/androidTest/.../TextPdfWriterTest.kt` 已写成真机/仪器测试：生成真实 PDF，用 `PdfRenderer` 数页、用已有 PDFBox `PDFTextStripper` 核对中文。本 worker 按任务要求没有运行仪器测试，也没有编译。

## 未做

没有跑 Gradle、没有装包、没有改其它依赖或许可证清单、没有把 HTML 渲染成带 CSS 的版面。

主代理复核另修：绘制区裁剪到本页实际整行高度，避免余白中提前绘制下一页半行；移除应用内已不使用的iText条目。设备渲染测试仍未运行。
