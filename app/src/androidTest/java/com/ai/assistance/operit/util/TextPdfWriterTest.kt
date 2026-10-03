package com.ai.assistance.operit.util

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TextPdfWriterTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var workDir: File

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(context)
        workDir = File(context.cacheDir, "text-pdf-writer-${System.nanoTime()}")
        assertTrue(workDir.mkdirs())
    }

    @After
    fun tearDown() {
        if (::workDir.isInitialized) {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun write_emptyText_createsSinglePagePdf() {
        val target = File(workDir, "empty.pdf")
        TextPdfWriter.write("", target)
        assertTrue(target.isFile)
        assertEquals(1, pageCount(target))
    }

    @Test
    fun write_preservesExplicitBlankLineMarkers() {
        val target = File(workDir, "newlines.pdf")
        TextPdfWriter.write("中文探针甲\n\n中文探针乙", target)
        val extracted = extractText(target)
        assertTrue(extracted.contains("中文探针甲"))
        assertTrue(extracted.contains("中文探针乙"))
    }

    @Test
    fun write_chineseLongText_spansMultiplePages() {
        val lastLine = "分页末行探针"
        val text = buildString {
            repeat(80) { index ->
                append("这是第${index + 1}段中文长文本，用于验证换行与分页，包含汉字、标点和数字123。")
                append('\n')
            }
            append(lastLine)
        }
        val target = File(workDir, "chinese-multipage.pdf")
        TextPdfWriter.write(text, target)

        val pages = pageCount(target)
        assertTrue("expected multiple pages, was $pages", pages >= 2)
        assertEquals(pages, pdfBoxPageCount(target))
        val extracted = extractText(target)
        assertTrue(extracted.contains("中文长文本"))
        assertTrue(extracted.contains(lastLine))
    }

    @Test
    fun writeHtml_decodesParagraphsAndChinese() {
        val html = buildString {
            append("<html><body>")
            repeat(50) { index ->
                append("<p>第${index + 1}段：中文HTML段落，这不是完整HTML排版承诺。</p>")
            }
            append("<p>HTML末行探针</p>")
            append("</body></html>")
        }
        val target = File(workDir, "html.pdf")
        TextPdfWriter.writeHtml(html, target)

        val pages = pageCount(target)
        assertTrue("expected multiple HTML pages, was $pages", pages >= 2)
        val extracted = extractText(target)
        assertTrue(extracted.contains("中文HTML段落"))
        assertTrue(extracted.contains("HTML末行探针"))
    }

    @Test
    fun convertTextToPdf_writesChineseAndReplacesExistingTarget() {
        val source = File(workDir, "source.txt")
        val target = File(workDir, "from-text.pdf")
        target.writeText("stale-target")
        source.writeText("转换探针中文\n第二行内容")

        assertTrue(DocumentConversionUtil.convertTextToPdf(context, source, target))
        assertTrue(pageCount(target) >= 1)
        val extracted = extractText(target)
        assertTrue(extracted.contains("转换探针中文"))
        assertTrue(extracted.contains("第二行内容"))
        assertFalse(String(target.readBytes()).contains("stale-target"))
    }

    @Test
    fun convertFromHtml_writesChinesePdf() {
        val source = File(workDir, "source.html")
        val target = File(workDir, "from-html.pdf")
        source.writeText("<p>HTML转换探针</p><p>第二段中文</p>")

        assertTrue(DocumentConversionUtil.convertFromHtml(context, source, target, "pdf"))
        val extracted = extractText(target)
        assertTrue(extracted.contains("HTML转换探针"))
        assertTrue(extracted.contains("第二段中文"))
    }

    @Test
    fun convertTextToPdf_missingSource_leavesExistingTarget() {
        val target = File(workDir, "keep.pdf")
        target.writeText("keep-bytes")
        val missing = File(workDir, "missing.txt")

        assertFalse(DocumentConversionUtil.convertTextToPdf(context, missing, target))
        assertEquals("keep-bytes", target.readText())
    }

    @Test
    fun convertFromHtml_missingSource_leavesExistingTarget() {
        val target = File(workDir, "keep-html.pdf")
        target.writeText("keep-html")
        val missing = File(workDir, "missing.html")

        assertFalse(DocumentConversionUtil.convertFromHtml(context, missing, target, "pdf"))
        assertEquals("keep-html", target.readText())
    }

    private fun pageCount(file: File): Int {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                val count = renderer.pageCount
                for (index in 0 until count) {
                    renderer.openPage(index).close()
                }
                return count
            }
        }
    }

    private fun pdfBoxPageCount(file: File): Int {
        PDDocument.load(file).use { document ->
            return document.numberOfPages
        }
    }

    private fun extractText(file: File): String {
        PDDocument.load(file).use { document ->
            return PDFTextStripper().getText(document)
        }
    }
}
