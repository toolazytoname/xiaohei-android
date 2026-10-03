package com.ai.assistance.operit.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PdfPaginationTest {

    @Test
    fun exactPageFit_staysOnOnePage() {
        val pages = PdfPagination.paginate(listOf(20f, 30f, 50f), 100f)
        assertEquals(listOf(PdfPagination.PageRange(0, 3)), pages)
    }

    @Test
    fun exactSingleLineFit_fillsThePage() {
        val pages = PdfPagination.paginate(listOf(100f, 100f), 100f)
        assertEquals(
            listOf(PdfPagination.PageRange(0, 1), PdfPagination.PageRange(1, 2)),
            pages,
        )
    }

    @Test
    fun multiPages_breakAtWholeLines() {
        val pages = PdfPagination.paginate(listOf(40f, 40f, 40f, 40f, 10f), 80f)
        assertEquals(
            listOf(
                PdfPagination.PageRange(0, 2),
                PdfPagination.PageRange(2, 4),
                PdfPagination.PageRange(4, 5),
            ),
            pages,
        )
    }

    @Test
    fun lastLine_startsANewPageWhenItDoesNotFit() {
        val pages = PdfPagination.paginate(listOf(80f, 80f), 100f)
        assertEquals(2, pages.size)
        assertEquals(PdfPagination.PageRange(0, 1), pages[0])
        assertEquals(PdfPagination.PageRange(1, 2), pages[1])
    }

    @Test
    fun emptyLines_yieldABlankPageRange() {
        val pages = PdfPagination.paginate(emptyList(), 100f)
        assertEquals(listOf(PdfPagination.PageRange(0, 0)), pages)
        assertEquals(0, pages.single().lineCount)
    }

    @Test
    fun zeroHeightLine_isKeptOnTheCurrentPage() {
        val pages = PdfPagination.paginate(listOf(10f, 0f, 10f), 100f)
        assertEquals(listOf(PdfPagination.PageRange(0, 3)), pages)
    }

    @Test
    fun invalidPageHeight_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            PdfPagination.paginate(listOf(10f), 0f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PdfPagination.paginate(listOf(10f), -1f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PdfPagination.paginate(listOf(10f), Float.NaN)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PdfPagination.paginate(listOf(10f), Float.POSITIVE_INFINITY)
        }
    }

    @Test
    fun invalidLineHeight_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            PdfPagination.paginate(listOf(-1f), 100f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PdfPagination.paginate(listOf(Float.NaN), 100f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PdfPagination.paginate(listOf(Float.NEGATIVE_INFINITY), 100f)
        }
    }

    @Test
    fun overflowingLine_throwsInsteadOfDropping() {
        assertThrows(IllegalArgumentException::class.java) {
            PdfPagination.paginate(listOf(101f), 100f)
        }
    }
}
