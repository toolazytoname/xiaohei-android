package com.ai.assistance.operit.util

/**
 * Splits pre-measured line heights onto pages at whole-line boundaries.
 *
 * A line taller than the usable page height is a hard error. Clipping or skipping
 * that line would drop glyphs without a caller-visible failure.
 */
object PdfPagination {
    data class PageRange(
        val startLine: Int,
        val endLineExclusive: Int,
    ) {
        val lineCount: Int
            get() = endLineExclusive - startLine
    }

    /**
     * @param lineHeights height of each already-wrapped layout line, in the same unit as
     *   [pageContentHeight]
     * @param pageContentHeight usable height inside page margins; must be finite and > 0
     * @return at least one page. An empty line list yields a single blank page range so
     *   empty documents still produce a PDF page.
     */
    fun paginate(lineHeights: List<Float>, pageContentHeight: Float): List<PageRange> {
        if (!pageContentHeight.isFinite() || pageContentHeight <= 0f) {
            throw IllegalArgumentException(
                "pageContentHeight must be finite and positive, was $pageContentHeight"
            )
        }
        if (lineHeights.isEmpty()) {
            return listOf(PageRange(0, 0))
        }

        lineHeights.forEachIndexed { index, height ->
            if (!height.isFinite() || height < 0f) {
                throw IllegalArgumentException(
                    "lineHeights[$index] must be finite and non-negative, was $height"
                )
            }
            if (height > pageContentHeight) {
                throw IllegalArgumentException(
                    "lineHeights[$index]=$height exceeds pageContentHeight=$pageContentHeight"
                )
            }
        }

        val pages = ArrayList<PageRange>()
        var start = 0
        var used = 0f
        for (index in lineHeights.indices) {
            val height = lineHeights[index]
            if (used > 0f && used + height > pageContentHeight) {
                pages.add(PageRange(start, index))
                start = index
                used = 0f
            }
            used += height
        }
        pages.add(PageRange(start, lineHeights.size))
        return pages
    }
}
