package com.ai.assistance.operit.util

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Html
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Writes wrapped text to a multi-page PDF using platform [StaticLayout] shaping and
 * [PdfDocument] pages. Shared by text→PDF and HTML→PDF; HTML is decoded to readable
 * paragraphs, not a full HTML layout engine.
 */
object TextPdfWriter {
    const val PAGE_WIDTH_PT = 595
    const val PAGE_HEIGHT_PT = 842
    const val MARGIN_PT = 48f
    const val FONT_SIZE_PT = 12f
    const val LINE_SPACING_MULT = 1.15f

    val pageContentWidthPt: Int
        get() {
            val width = PAGE_WIDTH_PT - 2f * MARGIN_PT
            if (width < 1f) {
                throw IllegalStateException("PDF content width must be at least 1pt, was $width")
            }
            return width.toInt()
        }

    val pageContentHeightPt: Float
        get() = PAGE_HEIGHT_PT - 2f * MARGIN_PT

    fun writeHtml(html: String, targetFile: File) {
        val readable = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
        write(readable, targetFile)
    }

    fun write(text: CharSequence, targetFile: File) {
        val parent = targetFile.absoluteFile.parentFile
            ?: throw IOException("PDF target has no parent directory: $targetFile")
        if (!parent.exists() && !parent.mkdirs()) {
            throw IOException("Cannot create PDF target directory: $parent")
        }
        val tempFile = File(parent, ".${targetFile.name}.${System.nanoTime()}.part")
        try {
            writeCompletePdf(layoutText(text), tempFile)
            atomicReplace(tempFile, targetFile)
        } finally {
            // Incomplete temps must not remain; a finished temp is already renamed onto the target.
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    private fun layoutText(text: CharSequence): CharSequence {
        return if (text is String) {
            text.replace("\r\n", "\n").replace("\r", "\n")
        } else {
            text
        }
    }

    private fun writeCompletePdf(text: CharSequence, destFile: File) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = FONT_SIZE_PT
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        val layoutWidth = pageContentWidthPt
        val layout =
            StaticLayout.Builder.obtain(text, 0, text.length, paint, layoutWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, LINE_SPACING_MULT)
                .setIncludePad(true)
                .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                .build()

        val lineHeights =
            if (layout.lineCount == 0) {
                emptyList()
            } else {
                List(layout.lineCount) { index ->
                    (layout.getLineBottom(index) - layout.getLineTop(index)).toFloat()
                }
            }
        val pages = PdfPagination.paginate(lineHeights, pageContentHeightPt)

        val document = PdfDocument()
        try {
            pages.forEachIndexed { pageIndex, range ->
                val pageInfo =
                    PdfDocument.PageInfo.Builder(PAGE_WIDTH_PT, PAGE_HEIGHT_PT, pageIndex + 1)
                        .create()
                val page = document.startPage(pageInfo)
                try {
                    drawPage(page.canvas, layout, range)
                } finally {
                    document.finishPage(page)
                }
            }
            FileOutputStream(destFile).use { output ->
                document.writeTo(output)
            }
        } finally {
            document.close()
        }
    }

    private fun drawPage(
        canvas: Canvas,
        layout: StaticLayout,
        range: PdfPagination.PageRange,
    ) {
        canvas.drawColor(Color.WHITE)
        canvas.save()
        canvas.translate(MARGIN_PT, MARGIN_PT)
        canvas.clipRect(0f, 0f, pageContentWidthPt.toFloat(), pageContentHeightPt)
        if (range.lineCount > 0) {
            val startTop = layout.getLineTop(range.startLine)
            val endBottom = layout.getLineBottom(range.endLineExclusive - 1)
            val drawnHeight = (endBottom - startTop).toFloat()
            if (drawnHeight > pageContentHeightPt) {
                throw IllegalStateException(
                    "page would clip lines ${range.startLine} until ${range.endLineExclusive - 1}"
                )
            }
            // Clip to the selected whole-line range, not the page remainder: otherwise
            // the next page's first line can be partially drawn twice.
            canvas.clipRect(0f, 0f, pageContentWidthPt.toFloat(), drawnHeight)
            canvas.translate(0f, -startTop.toFloat())
            layout.draw(canvas)
        }
        canvas.restore()
    }

    private fun atomicReplace(tempFile: File, targetFile: File) {
        try {
            Files.move(
                tempFile.toPath(),
                targetFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (error: AtomicMoveNotSupportedException) {
            throw IOException(
                "Atomic replace is required so a failed PDF write cannot corrupt ${targetFile.path}",
                error,
            )
        }
    }
}
